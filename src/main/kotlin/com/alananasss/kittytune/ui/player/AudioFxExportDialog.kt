@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.alananasss.kittytune.ui.player

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import com.alananasss.kittytune.core.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.audio.AudioFxExporter
import com.alananasss.kittytune.core.EscapableAlertDialog
import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.core.Toaster
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.core.trackTextInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** One thing the export will apply, as the dialog lists it: "Earrape · 10 %". */
internal data class AppliedFx(val label: String, val value: String?, val icon: ImageVector)

/** The main setting of the effects that have one, shown as a percentage beside their name. */
private val primaryIntensity: Map<String, (AudioEffectsState) -> Float> = mapOf(
    "bass_boost" to { it.bassBoostIntensity },
    "earrape" to { it.earrapeIntensity },
    "reverb" to { it.reverbIntensity },
    "muffled" to { it.muffledIntensity },
    "eight_d" to { it.eightDSpeed },
    "vocal_remover" to { it.vocalRemoverLevel },
    "vocal_boost" to { it.vocalBoostIntensity },
    "flanger" to { it.flangerIntensity },
    "vintage_mp3" to { it.vintageMp3Compression },
    "sub_octaver" to { it.subOctaverLevel },
    "tape_saturation" to { it.tapeWarmth },
    "super_wide" to { it.superWideWidth },
    "chorus" to { it.chorusDepth },
)

/** The ambient soundscape plays from a player of its own, beside the music: it is not in the file. */
private const val AMBIENT_ID = "rain"

internal fun speedText(speed: Float): String =
    String.format(Strings.locale(), "%.2f", speed).trimEnd('0').trimEnd('.', ',') + "×"

/** Everything the export changes, in the panel's order: speed first, then each effect, then the equalizer. */
internal fun appliedFx(state: AudioEffectsState, equalizer: EqualizerState, effects: List<AudioFxDefinition>): List<AppliedFx> {
    val list = mutableListOf<AppliedFx>()
    if (state.speed != 1f) {
        list += AppliedFx(
            str("fx_export_speed"),
            speedText(state.speed) + " · " + str(if (state.isPitchEnabled) "fx_export_with_pitch" else "fx_export_pitch_kept"),
            Icons.Rounded.Speed,
        )
    }
    effects.filter { it.id != AMBIENT_ID && it.isActive(state) }.forEach { fx ->
        val value = primaryIntensity[fx.id]?.invoke(state)?.let { "${(it * 100).toInt()} %" }
        list += AppliedFx(str(fx.titleKey), value, fx.icon)
    }
    if (equalizer.isEnabled) list += AppliedFx(str("fx_export_equalizer"), equalizer.selectedPreset.takeIf { it.isNotBlank() }, Icons.Rounded.GraphicEq)
    return list
}

/** "Earrape 10 % · Bass boost 20 % · 0.9× with pitch": the card's one-line summary. */
internal fun appliedFxSummary(applied: List<AppliedFx>): String =
    applied.joinToString(" · ") { fx -> listOfNotNull(fx.label, fx.value).joinToString(" ") }

/**
 * The entry point in the effects panel: says what an export would contain, and shows a running export's
 * progress in place, so leaving the dialog never loses track of it.
 */
@Composable
internal fun AudioFxExportCard(viewModel: PlayerViewModel, effects: List<AudioFxDefinition>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val state by AudioFxExporter.state.collectAsState()
    val applied = appliedFx(viewModel.effectsState, viewModel.equalizerState, effects)
    val hasTrack = viewModel.currentTrack != null
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onOpen,
        enabled = hasTrack || state !is AudioFxExporter.State.Idle,
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(scheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.SaveAlt, null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(str("fx_export_title"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                val running = state as? AudioFxExporter.State.Running
                Text(
                    when {
                        running != null -> str("fx_export_running_short", (running.progress * 100).toInt())
                        state is AudioFxExporter.State.Done -> str("fx_export_done_short")
                        !hasTrack -> str("fx_export_no_track")
                        applied.isEmpty() -> str("fx_export_none_short")
                        else -> appliedFxSummary(applied)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                AnimatedVisibility(running != null) {
                    LinearWavyProgressIndicator(
                        progress = { running?.progress ?: 0f },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.ChevronRight, null, tint = scheme.onSurfaceVariant)
        }
    }
}

/**
 * The export, as a sheet-like dialog in the style of the settings it sits beside: a hero card that
 * carries the track and, once started, the progress; grouped rows for what is applied, the format and
 * the file; a connected group of actions at the bottom.
 */
@Composable
internal fun AudioFxExportDialog(viewModel: PlayerViewModel, effects: List<AudioFxDefinition>, onDismiss: () -> Unit) {
    val state by AudioFxExporter.state.collectAsState()
    DisposableEffect(Unit) {
        AudioFxExporter.dialogOpen = true
        onDispose { AudioFxExporter.dialogOpen = false }
    }
    // What is exported is fixed when the dialog opens: changing an effect meanwhile does not change it.
    val track = remember { viewModel.currentTrack }
    val effectsState = remember { viewModel.effectsState }
    val equalizer = remember { viewModel.equalizerState }
    val applied = remember { appliedFx(effectsState, equalizer, effects) }
    var format by remember { mutableStateOf(AudioFxExporter.lastFormat) }
    var directory by remember { mutableStateOf(AudioFxExporter.directory) }
    var name by remember { mutableStateOf(track?.let(AudioFxExporter::suggestedName).orEmpty()) }
    val scope = rememberCoroutineScope()
    val close = {
        AudioFxExporter.dismiss()
        onDismiss()
    }
    val durationMs = track?.durationMs ?: 0L
    val outputMs = if (effectsState.speed > 0f) (durationMs / effectsState.speed).toLong() else durationMs

    BackHandler(onBack = close)
    BasicAlertDialog(
        onDismissRequest = close,
        modifier = Modifier.padding(24.dp).widthIn(max = 560.dp).fillMaxWidth(),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(shape = RoundedCornerShape(36.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().heightIn(max = 820.dp).padding(16.dp)) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ExportHero(
                    state = state,
                    title = track?.title.orEmpty(),
                    artist = track?.displayArtist.orEmpty(),
                    durationText = when {
                        durationMs <= 0 -> null
                        outputMs != durationMs -> "${clock(durationMs)} → ${clock(outputMs)}"
                        else -> clock(durationMs)
                    },
                )

                val file = (state as? AudioFxExporter.State.Done)?.file
                AnimatedContent(state::class, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "fxExportBody") { step ->
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        when (step) {
                            AudioFxExporter.State.Running::class -> {
                                InfoCard(Icons.Rounded.Info, str("fx_export_background_hint"))
                            }
                            AudioFxExporter.State.Done::class -> {
                                Section(null) {
                                    GroupItem {
                                        ItemRow(
                                            icon = Icons.Rounded.AudioFile,
                                            title = file?.name.orEmpty(),
                                            subtitle = file?.parentFile?.absolutePath,
                                            trailing = file?.length()?.takeIf { it > 0 }?.let {
                                                { ValuePill(String.format(Strings.locale(), "%.1f MB", it / (1024.0 * 1024.0))) }
                                            },
                                        )
                                    }
                                }
                            }
                            AudioFxExporter.State.Failed::class -> {
                                val failed = state as? AudioFxExporter.State.Failed
                                InfoCard(
                                    Icons.Rounded.ErrorOutline,
                                    when (failed?.failure) {
                                        AudioFxExporter.Failure.PROTECTED -> str("fx_export_failed_protected")
                                        AudioFxExporter.Failure.UNAVAILABLE -> str("fx_export_failed_unavailable")
                                        AudioFxExporter.Failure.WRITE -> str("fx_export_failed_write", failed.detail.orEmpty())
                                        else -> str("fx_export_failed_other")
                                    },
                                )
                            }
                            else -> {
                                Section(str("fx_export_applied")) {
                                    if (applied.isEmpty()) {
                                        GroupItem { ItemRow(Icons.Rounded.Tune, str("fx_export_none"), null) }
                                    } else {
                                        applied.forEach { fx ->
                                            // A short value reads best as a pill; a sentence ("0.9× · with pitch") as a subtitle.
                                            val short = fx.value?.takeIf { it.length <= 7 }
                                            GroupItem {
                                                ItemRow(
                                                    fx.icon, fx.label, fx.value.takeIf { short == null },
                                                    trailing = short?.let { { ValuePill(it) } },
                                                )
                                            }
                                        }
                                    }
                                }
                                if (effectsState.isRainEnabled) InfoCard(Icons.Rounded.Info, str("fx_export_ambient_note"))

                                Section(str("fx_export_format")) {
                                    GroupItem {
                                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                            val sizeMb = outputMs / 1000.0 * format.bytesPerSecond / (1024 * 1024)
                                            ItemRow(
                                                icon = Icons.Rounded.AudioFile,
                                                title = format.name,
                                                subtitle = str("fx_export_format_${format.name.lowercase()}"),
                                                trailing = if (outputMs > 0) {
                                                    { ValuePill(str("fx_export_size", String.format(Strings.locale(), "%.0f", sizeMb.coerceAtLeast(1.0)))) }
                                                } else null,
                                                padded = false,
                                            )
                                            ExpressiveChoiceGroup(
                                                options = AudioFxExporter.Format.entries,
                                                selected = format,
                                                label = { it.name },
                                                onSelect = { format = it },
                                            )
                                        }
                                    }
                                }

                                Section(str("fx_export_file")) {
                                    GroupItem {
                                        OutlinedTextField(
                                            value = name,
                                            onValueChange = { if (it.length <= 150) name = it },
                                            singleLine = true,
                                            label = { Text(str("fx_export_file_name")) },
                                            suffix = { Text(".${format.extension}") },
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.fillMaxWidth().padding(16.dp).trackTextInput(),
                                        )
                                    }
                                    GroupItem(onClick = {
                                        scope.launch {
                                            val picked = withContext(Dispatchers.IO) { pickExportFolder(str("fx_export_choose_folder"), directory) }
                                            if (picked != null) directory = picked
                                        }
                                    }) {
                                        ItemRow(
                                            icon = Icons.Rounded.Folder,
                                            title = str("fx_export_folder"),
                                            subtitle = directory.absolutePath,
                                            trailing = { ValuePill(str("fx_export_change")) },
                                        )
                                    }
                                }

                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            when (state) {
                is AudioFxExporter.State.Running ->
                    ActionGroup(
                        secondary = str("fx_export_stop") to { AudioFxExporter.cancel() },
                        primary = str("fx_export_hide") to onDismiss,
                        primaryIcon = null,
                        secondaryIsDanger = true,
                    )
                is AudioFxExporter.State.Done ->
                    ActionGroup(
                        secondary = str("btn_close") to close,
                        primary = str("fx_export_show_in_folder") to {
                            (state as? AudioFxExporter.State.Done)?.file?.let(::revealExportedFile)
                            close()
                        },
                        primaryIcon = Icons.Rounded.FolderOpen,
                    )
                is AudioFxExporter.State.Failed ->
                    if ((state as? AudioFxExporter.State.Failed)?.failure == AudioFxExporter.Failure.PROTECTED) {
                        // Trying again cannot change a licence: only offer to close.
                        ActionGroup(
                            secondary = str("fx_export_retry") to { AudioFxExporter.dismiss() },
                            primary = str("btn_close") to close,
                            primaryIcon = null,
                        )
                    } else {
                        ActionGroup(
                            secondary = str("btn_cancel") to close,
                            primary = str("fx_export_retry") to { AudioFxExporter.dismiss() },
                            primaryIcon = Icons.Rounded.Refresh,
                        )
                    }
                else ->
                    ActionGroup(
                        secondary = str("btn_cancel") to close,
                        primary = str("fx_export_start") to {
                            if (track != null && name.isNotBlank()) {
                                AudioFxExporter.export(track, effectsState, equalizer, format, directory, name)
                            }
                        },
                        primaryIcon = Icons.Rounded.SaveAlt,
                        primaryEnabled = track != null && name.isNotBlank(),
                    )
            }
            }
        }
    }
}

/**
 * The top of the dialog: the track, and what is happening to it. Its shape turns while the export runs,
 * the progress lives inside it, and its colour says how it ended.
 */
@Composable
private fun ExportHero(state: AudioFxExporter.State, title: String, artist: String, durationText: String?) {
    val scheme = MaterialTheme.colorScheme
    val running = state as? AudioFxExporter.State.Running
    val failed = state is AudioFxExporter.State.Failed
    val done = state is AudioFxExporter.State.Done
    val container = com.alananasss.kittytune.ui.theme.animatePaletteColor(
        selected = failed,
        selectedColor = scheme.errorContainer,
        unselectedColor = scheme.primaryContainer,
        animationSpec = spring(),
        label = "heroContainer",
    )
    val content = com.alananasss.kittytune.ui.theme.animatePaletteColor(
        selected = failed,
        selectedColor = scheme.onErrorContainer,
        unselectedColor = scheme.onPrimaryContainer,
        animationSpec = spring(),
        label = "heroContent",
    )
    val badge = com.alananasss.kittytune.ui.theme.animatePaletteColor(
        selected = failed,
        selectedColor = scheme.error,
        unselectedColor = scheme.onPrimaryContainer,
        animationSpec = spring(),
        label = "heroBadge",
    )
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(running != null) {
        if (running != null) {
            while (true) rotation.animateTo(rotation.value + 360f, tween(6000, easing = LinearEasing))
        } else {
            rotation.animateTo(rotation.value + 45f, spring(dampingRatio = 0.5f, stiffness = 120f))
        }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(container).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier.size(76.dp).graphicsLayer { rotationZ = rotation.value }.clip(MaterialShapes.Cookie9Sided.toShape()).background(badge),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when {
                        done -> Icons.Rounded.Check
                        failed -> Icons.Rounded.ErrorOutline
                        else -> Icons.Rounded.SaveAlt
                    },
                    null,
                    tint = if (failed) scheme.onError else scheme.primaryContainer,
                    modifier = Modifier.size(34.dp).graphicsLayer { rotationZ = -rotation.value },
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    str(
                        when (state) {
                            is AudioFxExporter.State.Running -> "fx_export_running_title"
                            is AudioFxExporter.State.Done -> "fx_export_done_title"
                            is AudioFxExporter.State.Failed -> "fx_export_failed_title"
                            else -> "fx_export_title"
                        },
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = content,
                )
                Text(
                    listOf(title, artist).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = content.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        AnimatedVisibility(
            visible = running != null,
            enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = 400f)) + fadeIn(),
            exit = shrinkVertically(spring(dampingRatio = 0.9f, stiffness = 400f)) + fadeOut(),
        ) {
            val progress by animateFloatAsState(running?.progress ?: 1f, label = "heroProgress")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${(progress * 100).toInt()}",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = content,
                    )
                    Text(" %", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = content.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 4.dp))
                    Spacer(Modifier.weight(1f))
                    running?.format?.let { ValuePill(it.name, container = content.copy(alpha = 0.12f), content = content) }
                }
                LinearWavyProgressIndicator(
                    progress = { progress },
                    color = content,
                    trackColor = content.copy(alpha = 0.18f),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (durationText != null && running == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ValuePill(durationText, icon = Icons.Rounded.Schedule, container = content.copy(alpha = 0.12f), content = content)
            }
        }
    }
}

/** A titled group in the Android 16 style: large corners outside, small ones between rows. */
@Composable
private fun Section(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) {
            Text(
                title,
                modifier = Modifier.padding(start = 16.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
    }
}

/** A row of a group: its corners swell while pressed, the M3 Expressive shape morph. */
@Composable
private fun GroupItem(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(if (pressed) 22.dp else 6.dp, spring(dampingRatio = 0.6f, stiffness = 700f), label = "itemCorner")
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceBright)
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick) else Modifier),
    ) { content() }
}

@Composable
private fun ItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    trailing: (@Composable () -> Unit)? = null,
    padded: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().then(if (padded) Modifier.padding(horizontal = 16.dp, vertical = 14.dp) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(scheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun ValuePill(
    text: String,
    icon: ImageVector? = null,
    container: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primaryContainer,
    content: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Row(
        Modifier.clip(CircleShape).background(container).padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = content, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = content, maxLines = 1)
    }
}

/** The expressive connected group: the chosen option becomes a pill, wider than its neighbours. */
@Composable
private fun <T> ExpressiveChoiceGroup(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEachIndexed { i, option ->
            val isSelected = option == selected
            val inner by animateDpAsState(if (isSelected) 26.dp else 8.dp, spring(dampingRatio = 0.6f, stiffness = 500f), label = "choiceInner")
            val weight by animateFloatAsState(if (isSelected) 1.3f else 1f, spring(dampingRatio = 0.7f, stiffness = 400f), label = "choiceWeight")
            val bg = com.alananasss.kittytune.ui.theme.animatePaletteColor(
                selected = isSelected,
                selectedColor = scheme.primary,
                unselectedColor = scheme.secondaryContainer,
                animationSpec = spring(),
                label = "choiceBg",
            )
            val fg = com.alananasss.kittytune.ui.theme.animatePaletteColor(
                selected = isSelected,
                selectedColor = scheme.onPrimary,
                unselectedColor = scheme.onSecondaryContainer,
                animationSpec = spring(),
                label = "choiceFg",
            )
            val start = if (i == 0) 26.dp else inner
            val end = if (i == options.lastIndex) 26.dp else inner
            Box(
                Modifier
                    .weight(weight)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end))
                    .background(bg)
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedVisibility(isSelected, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
                        Row {
                            Icon(Icons.Rounded.Check, null, tint = fg, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                    }
                    Text(label(option), color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun InfoCard(icon: ImageVector, text: String, error: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val bg = if (error) scheme.errorContainer else scheme.secondaryContainer
    val fg = if (error) scheme.onErrorContainer else scheme.onSecondaryContainer
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(bg).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = fg)
    }
}

/** The dialog's two actions as one connected group, the main one wider. */
@Composable
private fun ActionGroup(
    secondary: Pair<String, () -> Unit>,
    primary: Pair<String, () -> Unit>,
    primaryIcon: ImageVector?,
    primaryEnabled: Boolean = true,
    secondaryIsDanger: Boolean = false,
) {
    val leading = ButtonGroupDefaults.connectedLeadingButtonShapes()
    val trailing = ButtonGroupDefaults.connectedTrailingButtonShapes()
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        FilledTonalButton(
            onClick = secondary.second,
            shapes = ButtonShapes(leading.shape, leading.pressedShape),
            colors = if (secondaryIsDanger) {
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            } else ButtonDefaults.filledTonalButtonColors(),
            modifier = Modifier.weight(1f).height(56.dp),
        ) { Text(secondary.first, style = MaterialTheme.typography.titleSmall, maxLines = 1) }
        Button(
            onClick = primary.second,
            enabled = primaryEnabled,
            shapes = ButtonShapes(trailing.shape, trailing.pressedShape),
            modifier = Modifier.weight(1.6f).height(56.dp),
        ) {
            if (primaryIcon != null) {
                Icon(primaryIcon, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(primary.first, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
    }
}

/** Tells the listener an export finished while its dialog was closed. Composed once, in the effects panel. */
@Composable
internal fun AudioFxExportNotifier() {
    val state by AudioFxExporter.state.collectAsState()
    LaunchedEffect(state) {
        if (AudioFxExporter.dialogOpen) return@LaunchedEffect
        when (val s = state) {
            is AudioFxExporter.State.Done -> Toaster.confirm(str("fx_export_done_toast", s.file.name))
            is AudioFxExporter.State.Failed -> Toaster.show(str("fx_export_failed_title"))
            else -> Unit
        }
    }
}

private fun clock(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

private fun pickExportFolder(title: String, initial: File): File? {
    if (com.alananasss.kittytune.core.NativeFileDialog.isWindowsHost) return com.alananasss.kittytune.core.NativeFileDialog.openFolder(title)
    val chooser = javax.swing.JFileChooser().apply {
        dialogTitle = title
        fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
        currentDirectory = initial.takeIf { it.isDirectory } ?: initial.parentFile
    }
    return if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

private fun revealExportedFile(file: File) {
    Thread {
        runCatching {
            val os = System.getProperty("os.name").lowercase()
            val desktop = if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop() else null
            when {
                os.contains("win") -> ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start()
                desktop?.isSupported(java.awt.Desktop.Action.BROWSE_FILE_DIR) == true -> desktop.browseFileDirectory(file)
                else -> com.alananasss.kittytune.core.openDirectoryInFileManager(file.parentFile)
            }
        }
    }.start()
}
