package com.alananasss.kittytune.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.audio.SourceAudioFormat
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.KittyModalBottomSheet
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * The live audio path, source to output (desktop port of Android PR #83's diagnostics sheet).
 *
 * Desktop decodes every stream with FFmpeg into the engine's 44.1 kHz stereo pipeline and plays it through the
 * system mixer, so the path is "direct" when the source already runs at 44.1 kHz and "resampled" otherwise.
 * There is no bit-perfect output on desktop, so that state never shows.
 */
enum class AudioPathStatus { RESAMPLED, DIRECT }

data class AudioPathSnapshot(
    val format: SourceAudioFormat?,
    val busRateHz: Int,
    val eqEnabled: Boolean,
    val eqBands: Int,
    val normalizeEnabled: Boolean,
    val normalizeTargetLufs: String?,
    val outputName: String?,
    val stages: List<Pair<String, Boolean>>,
) {
    val status: AudioPathStatus
        get() = if (format != null && format.sampleRateHz > 0 && format.sampleRateHz != busRateHz) AudioPathStatus.RESAMPLED
        else AudioPathStatus.DIRECT
}

object AudioPathColors {
    val good = Color(0xFF43A047)
    val warn = Color(0xFFF9A825)
    val neutral = Color(0xFF8E9AA6)

    fun statusColor(status: AudioPathStatus): Color = when (status) {
        AudioPathStatus.RESAMPLED -> warn
        AudioPathStatus.DIRECT -> neutral
    }
}

/** The snapshot, refreshed twice a second while shown: effects and stages change as the listener plays. */
@Composable
fun rememberAudioPathSnapshot(playerViewModel: PlayerViewModel): AudioPathSnapshot =
    rememberAudioPathSnapshot(playerViewModel.effectsState, playerViewModel.equalizerState)

@Composable
fun rememberAudioPathSnapshot(effects: AudioEffectsState, eq: EqualizerState): AudioPathSnapshot {
    val format by MusicManager.player.currentAudioFormat.collectAsState()
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            tick++
        }
    }
    return remember(format, effects, eq, tick) {
        AudioPathSnapshot(
            format = format,
            busRateHz = MusicManager.player.activeEngine.outputRateHz,
            eqEnabled = eq.isEnabled,
            eqBands = com.alananasss.kittytune.audio.EqualizerAudioProcessor.NUM_BANDS,
            normalizeEnabled = effects.isNormalizationEnabled,
            normalizeTargetLufs = if (effects.isNormalizationEnabled) when (effects.normalizationLevel) {
                NormalizationLevel.QUIET -> "−19 LUFS"
                NormalizationLevel.LOUD -> "−11 LUFS"
                else -> "−14 LUFS"
            } else null,
            outputName = PlayerPreferences().getAudioDevice().takeIf { it.isNotBlank() },
            stages = MusicManager.player.activeEngine.dspStages(),
        )
    }
}

private fun khz(rate: Int): String = if (rate <= 0) "—" else String.format(Locale.US, "%.1f", rate / 1000.0) + " kHz"

private fun channelsLabel(count: Int): String = when (count) {
    1 -> "Mono 1.0"
    2 -> "Stereo 2.0"
    6 -> "Surround 5.1"
    8 -> "Surround 7.1"
    else -> if (count > 0) "$count ch" else "—"
}

private fun outputDisplayName(snapshot: AudioPathSnapshot): String =
    snapshot.outputName ?: str("diag_out_system_default")

private fun statusText(status: AudioPathStatus): String = when (status) {
    AudioPathStatus.RESAMPLED -> str("diag_badge_resampled")
    AudioPathStatus.DIRECT -> str("diag_badge_direct")
}

/** "OPUS 48kHz → Speakers [Resampled]". */
fun audioPathHeadline(snapshot: AudioPathSnapshot): String {
    val f = snapshot.format ?: return str("diag_hero_sub_waiting")
    val input = listOfNotNull(
        f.codec,
        f.bitDepth?.let { "$it-bit" },
        f.sampleRateHz.takeIf { it > 0 }?.let { SourceAudioFormat.khzCompact(it) + "kHz" },
    ).joinToString(" ")
    return "$input → ${outputDisplayName(snapshot)} [${statusText(snapshot.status)}]"
}

private enum class ChipTone { NEUTRAL, GOOD, WARN }

private data class DiagChipData(val label: String, val value: String, val tone: ChipTone)

/** The entry card in the audio settings: the path in one line, and a button to open the details. */
@Composable
fun AudioPathHeroCard(playerViewModel: PlayerViewModel, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val snapshot = rememberAudioPathSnapshot(playerViewModel)
    ElevatedCard(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(str("diag_hero_title"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(
                    audioPathHeadline(snapshot),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(str("diag_hero_open"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

/** Source → processing → output → the live DSP stages. */
@Composable
fun AudioPathDiagnosticSheet(visible: Boolean, onDismiss: () -> Unit, playerViewModel: PlayerViewModel) {
    if (!visible) return
    val snapshot = rememberAudioPathSnapshot(playerViewModel)
    KittyModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        AudioPathDiagnosticContent(snapshot)
    }
}

/** The sheet's content, on its own so it can be rendered without a window. */
@Composable
fun AudioPathDiagnosticContent(snapshot: AudioPathSnapshot) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(str("diag_title"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                StatusBadge(snapshot.status)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                audioPathHeadline(snapshot),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(18.dp))
            PathSection(Icons.Rounded.MusicNote, str("diag_section_input"), snapshot.format?.codec, inputChips(snapshot))
            PathConnector()
            PathSection(Icons.Rounded.Tune, str("diag_section_dsp"), null, dspChips(snapshot))
            PathConnector()
            PathSection(Icons.Rounded.Speaker, str("diag_section_output"), null, outputChips(snapshot))
            PathConnector()
            PathSection(
                Icons.Rounded.GraphicEq, str("diag_section_routing"), null,
                snapshot.stages.map { (name, on) -> DiagChipData(name, if (on) "✓" else "—", if (on) ChipTone.GOOD else ChipTone.NEUTRAL) },
            )
        }
}

private fun inputChips(snapshot: AudioPathSnapshot): List<DiagChipData> {
    val f = snapshot.format
    return listOf(
        DiagChipData(str("diag_chip_rate"), khz(f?.sampleRateHz ?: 0), ChipTone.NEUTRAL),
        DiagChipData(str("diag_chip_depth"), f?.bitDepth?.let { "$it-bit" } ?: "—", ChipTone.NEUTRAL),
        DiagChipData(str("diag_chip_bitrate"), f?.bitrateBps?.takeIf { it > 0 }?.let { "${it / 1000} kbps" } ?: "—", ChipTone.NEUTRAL),
        DiagChipData(str("diag_chip_channels"), channelsLabel(f?.channels ?: 0), ChipTone.NEUTRAL),
    )
}

private fun dspChips(snapshot: AudioPathSnapshot): List<DiagChipData> {
    val resampled = snapshot.status == AudioPathStatus.RESAMPLED
    val resampler = if (resampled) str("diag_resampler_src", khz(snapshot.format?.sampleRateHz ?: 0), khz(snapshot.busRateHz))
    else str("diag_resampler_native")
    val eq = if (snapshot.eqEnabled) str("diag_eq_active", snapshot.eqBands) else str("diag_eq_off")
    val norm = if (snapshot.normalizeEnabled) str("diag_norm_on", snapshot.normalizeTargetLufs ?: "−14 LUFS") else str("diag_norm_off")
    return listOf(
        DiagChipData(str("diag_resampler"), resampler, if (resampled) ChipTone.WARN else ChipTone.GOOD),
        DiagChipData(str("diag_equalizer"), eq, if (snapshot.eqEnabled) ChipTone.GOOD else ChipTone.NEUTRAL),
        DiagChipData(str("diag_normalizer"), norm, if (snapshot.normalizeEnabled) ChipTone.GOOD else ChipTone.NEUTRAL),
    )
}

private fun outputChips(snapshot: AudioPathSnapshot): List<DiagChipData> = listOf(
    DiagChipData(str("diag_output_device"), outputDisplayName(snapshot), ChipTone.NEUTRAL),
    DiagChipData(str("diag_output_mode"), str("diag_output_mode_mixer"), ChipTone.NEUTRAL),
    DiagChipData(str("diag_bus_format"), "${khz(snapshot.busRateHz)} · 16-bit · Stereo", ChipTone.NEUTRAL),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PathSection(icon: ImageVector, title: String, subtitle: String?, chips: List<DiagChipData>) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (!subtitle.isNullOrEmpty()) {
                        Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.forEach { DiagChip(it) }
            }
        }
    }
}

@Composable
private fun DiagChip(chip: DiagChipData) {
    val toneColor = when (chip.tone) {
        ChipTone.GOOD -> AudioPathColors.good
        ChipTone.WARN -> AudioPathColors.warn
        ChipTone.NEUTRAL -> null
    }
    Surface(shape = RoundedCornerShape(14.dp), color = toneColor?.copy(alpha = 0.14f) ?: MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (toneColor != null) Box(Modifier.size(7.dp).background(toneColor, CircleShape))
            Column {
                Text(chip.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    chip.value,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = toneColor ?: MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PathConnector() {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(2.dp).height(8.dp).background(MaterialTheme.colorScheme.outlineVariant))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(22.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.ArrowDownward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
                }
            }
            Box(Modifier.width(2.dp).height(8.dp).background(MaterialTheme.colorScheme.outlineVariant))
        }
    }
}

@Composable
private fun StatusBadge(status: AudioPathStatus) {
    val color = AudioPathColors.statusColor(status)
    Surface(shape = RoundedCornerShape(14.dp), color = color.copy(alpha = 0.14f)) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(7.dp).background(color, CircleShape))
            Text(statusText(status), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
