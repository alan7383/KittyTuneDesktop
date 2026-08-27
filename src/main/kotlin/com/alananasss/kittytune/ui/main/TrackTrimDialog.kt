package com.alananasss.kittytune.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.audio.TrackTrim
import com.alananasss.kittytune.audio.TrimMode
import com.alananasss.kittytune.audio.TrimSegment
import com.alananasss.kittytune.core.EscapableAlertDialog
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import com.alananasss.kittytune.ui.player.PlayerViewModel

/**
 * Trimming the track that is playing, by ear (issue #33).
 *
 * There is no waveform here and that is deliberate. The request came from people who re-upload edited versions
 * of songs to drop a guest verse or a long intro, and the way anyone actually finds those boundaries is by
 * listening for them. So the editor's one gesture is *mark it here*: you let the track play, and when the part
 * you want gone starts, you press the button. A waveform would be a nicer picture and a worse tool — a guest
 * verse does not look like anything.
 *
 * The two modes are not two ways of saying the same thing:
 *
 * - **Cut** removes the spans and plays the rest. Right for a verse in the middle.
 * - **Keep** plays only the spans and *ends the track* after them. Right for trimming an intro and an outro at
 *   once, which cutting cannot express without knowing where the song really ends.
 *
 * Nothing is written to the audio. Clearing the trim gives the original back.
 */
@Composable
fun TrackTrimDialog(viewModel: PlayerViewModel) {
    if (!viewModel.showTrimDialog) return
    val track = viewModel.currentTrack ?: return

    // Edited as a draft and committed on save, so half-built spans never reach playback — a start with no end
    // yet would otherwise be a cut running to the end of the track, applied the moment it was typed.
    var mode by remember(track.id) { mutableStateOf(viewModel.currentTrim.mode) }
    var segments by remember(track.id) { mutableStateOf(viewModel.currentTrim.segments) }

    val duration = viewModel.duration.coerceAtLeast(0L)
    val position = viewModel.currentPosition.coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
    val draft = TrackTrim.of(mode, segments)

    EscapableAlertDialog(
        onDismissRequest = { viewModel.showTrimDialog = false },
        icon = { Icon(Icons.Rounded.ContentCut, null) },
        title = { Text(str("trim_title"), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    str("trim_sub"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val modes = listOf(
                    TrimMode.CUT to str("trim_mode_cut"),
                    TrimMode.KEEP to str("trim_mode_keep"),
                )
                ExpressiveConnectedButtonGroup(
                    options = modes,
                    selectedOption = modes.first { it.first == mode },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    onOptionSelected = { (picked, _) -> mode = picked },
                    labelProvider = { (_, label) ->
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    str(if (mode == TrimMode.CUT) "trim_mode_cut_sub" else "trim_mode_keep_sub"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                if (segments.isEmpty()) {
                    Text(
                        str("trim_empty"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                segments.forEachIndexed { index, segment ->
                    TrimSegmentRow(
                        segment = segment,
                        position = position,
                        onStartHere = {
                            segments = segments.toMutableList().also {
                                it[index] = segment.copy(startMs = position)
                            }
                        },
                        onEndHere = {
                            segments = segments.toMutableList().also {
                                it[index] = segment.copy(endMs = position)
                            }
                        },
                        onStart = { ms ->
                            segments = segments.toMutableList().also {
                                it[index] = segment.copy(startMs = ms)
                            }
                        },
                        onEnd = { ms ->
                            segments = segments.toMutableList().also {
                                it[index] = segment.copy(endMs = ms)
                            }
                        },
                        onRemove = {
                            segments = segments.toMutableList().also { it.removeAt(index) }
                        },
                    )
                }

                FilledTonalButton(
                    onClick = {
                        // Opens at the playhead and runs to the end, which is the shape you want when you
                        // have just heard the part start: press once, keep listening, then mark the end.
                        val end = if (duration > position) duration else position + 1_000L
                        segments = segments + TrimSegment(position, end)
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(str("trim_add_here", formatMs(position)))
                }

                if (!draft.isEmpty && duration > 0) {
                    Text(
                        str("trim_plays_for", formatMs(draft.playedDurationMs(duration)), formatMs(duration)),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.saveCurrentTrim(draft)
                viewModel.showTrimDialog = false
            }) { Text(str("btn_save")) }
        },
        dismissButton = {
            Row {
                if (!viewModel.currentTrim.isEmpty) {
                    TextButton(onClick = {
                        viewModel.clearCurrentTrim()
                        segments = emptyList()
                        viewModel.showTrimDialog = false
                    }) { Text(str("trim_clear")) }
                }
                TextButton(onClick = { viewModel.showTrimDialog = false }) { Text(str("btn_cancel")) }
            }
        },
    )
}

@Composable
private fun TrimSegmentRow(
    segment: TrimSegment,
    position: Long,
    onStartHere: () -> Unit,
    onEndHere: () -> Unit,
    onStart: (Long) -> Unit,
    onEnd: (Long) -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${formatMs(segment.startMs)} → ${formatMs(segment.endMs)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                // Stated rather than left to be worked out from the two timestamps: the length is the thing
                // you are judging when you decide whether the marks are right.
                Text(
                    formatMs(segment.durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = onRemove, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        Icons.Rounded.Delete,
                        str("trim_remove_segment"),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            // Two ways to set a mark, because they suit different jobs. Marking from the playhead is how you
            // find a boundary you have to *hear* — a guest verse does not look like anything. Typing it is how
            // you enter one you already know, or nudge one you got slightly wrong by two seconds. Only having
            // the first was the "I can't specify the timestamps numerically" report (issue #33).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TimeField(
                    label = str("trim_field_start"),
                    valueMs = segment.startMs,
                    onValueMs = onStart,
                    modifier = Modifier.weight(1f),
                )
                TimeField(
                    label = str("trim_field_end"),
                    valueMs = segment.endMs,
                    onValueMs = onEnd,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onStartHere) { Text(str("trim_set_start", formatMs(position))) }
                TextButton(onClick = onEndHere) { Text(str("trim_set_end", formatMs(position))) }
            }
        }
    }
}

/** "1:07" — the form a player shows everywhere else, so the marks read against the seek bar. */
private fun formatMs(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

/**
 * One timestamp, typed (issue #33).
 *
 * Accepts what a person actually writes for a time in a song: `1:07`, `67`, `1:07.5`, `0:07`. Anything
 * unparseable is left in the field and simply not committed, so a half-typed `1:` does not become a
 * mark at zero and then fight the next keystroke — the text is local state and only the parsed value
 * travels outward.
 *
 * Re-seeded from [valueMs] whenever it changes from outside, which is what keeps it in step with the
 * "mark from the playhead" buttons sitting right below it.
 */
@Composable
private fun TimeField(
    label: String,
    valueMs: Long,
    onValueMs: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(valueMs) { mutableStateOf(formatMs(valueMs)) }
    val parsed = remember(text) { parseTime(text) }

    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            text = typed
            parseTime(typed)?.let(onValueMs)
        },
        label = { Text(label) },
        singleLine = true,
        isError = text.isNotBlank() && parsed == null,
        modifier = modifier,
    )
}

/**
 * @return the time [raw] names, in milliseconds, or null when it is not a time yet.
 *
 * `m:ss` and `m:ss.t` are the forms the app shows, so they are the forms it has to read back. A bare
 * number is read as seconds, because that is what someone types when the mark is inside the first
 * minute.
 */
private fun parseTime(raw: String): Long? {
    val text = raw.trim()
    if (text.isEmpty()) return null

    val parts = text.split(':')
    if (parts.size > 2) return null

    return if (parts.size == 1) {
        parts[0].toDoubleOrNull()?.takeIf { it >= 0 }?.let { (it * 1000).toLong() }
    } else {
        val minutes = parts[0].toLongOrNull()?.takeIf { it >= 0 } ?: return null
        val seconds = parts[1].toDoubleOrNull()?.takeIf { it >= 0 && it < 60 } ?: return null
        minutes * 60_000 + (seconds * 1000).toLong()
    }
}
