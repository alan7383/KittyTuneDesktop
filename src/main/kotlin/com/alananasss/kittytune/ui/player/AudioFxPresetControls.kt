@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.alananasss.kittytune.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.EscapableAlertDialog
import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.core.trackTextInput
import com.alananasss.kittytune.ui.common.Tip

/**
 * The saved presets, as cards under the speed: a click brings a sound back, another click puts back
 * what played before. A right-click (or holding a card) offers to rename it, overwrite it with the
 * current sound, or delete it. The first card saves the current sound.
 *
 * [onNeedsEarrapeWarning] runs when the preset would turn on the loud-volume effect before its
 * warning was ever accepted; nothing has changed then.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AudioFxPresetsSection(
    viewModel: PlayerViewModel,
    onNeedsEarrapeWarning: (String) -> Unit,
    showSaveDialog: Boolean,
    onDismissSaveDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var presetToRename by remember { mutableStateOf<String?>(null) }
    var presetToDelete by remember { mutableStateOf<String?>(null) }
    val presets = viewModel.audioFxPresets
    val activeName = viewModel.activeAudioFxPresetName

    // Nothing saved: no header and no hint taking room. The dialogs below still work, for the Save button.
    if (presets.isNotEmpty()) Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Bookmark, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            // The title takes what the button leaves; the count stays right after it.
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    str("audio_fx_saved_presets"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (presets.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Text(
                            "${presets.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }

        run {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                presets.keys.reversed().chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { name ->
                            androidx.compose.runtime.key(name) {
                                AudioFxPresetCard(
                                    name = name,
                                    preset = presets.getValue(name),
                                    isActive = name == activeName || viewModel.isAudioFxPresetActive(name),
                                    onToggle = { if (!viewModel.toggleAudioFxPreset(name)) onNeedsEarrapeWarning(name) },
                                    onRename = { presetToRename = name },
                                    onUpdate = { viewModel.updateAudioFxPreset(name) },
                                    onDelete = { presetToDelete = name },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        AudioFxPresetNameDialog(
            title = str("audio_fx_save_preset"),
            initialName = "",
            confirmLabel = str("btn_save"),
            nameError = { viewModel.audioFxPresetNameError(it) },
            onConfirm = { name -> if (viewModel.saveAudioFxPreset(name)) onDismissSaveDialog() },
            onDismiss = onDismissSaveDialog,
        )
    }
    presetToRename?.let { name ->
        AudioFxPresetNameDialog(
            title = str("audio_fx_rename_preset"),
            initialName = name,
            confirmLabel = str("audio_fx_rename"),
            nameError = { viewModel.audioFxPresetNameError(it, except = name) },
            onConfirm = { newName -> if (viewModel.renameAudioFxPreset(name, newName)) presetToRename = null },
            onDismiss = { presetToRename = null },
        )
    }
    presetToDelete?.let { name ->
        AudioFxPresetDeleteDialog(name, viewModel) { presetToDelete = null }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AudioFxPresetCard(
    name: String,
    preset: AudioFxPreset,
    isActive: Boolean,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(if (isActive) scheme.secondaryContainer else scheme.surfaceContainerHigh, label = "presetContainer")
    val content by animateColorAsState(if (isActive) scheme.onSecondaryContainer else scheme.onSurface, label = "presetContent")
    val corner by animateDpAsState(
        if (isActive) 32.dp else 24.dp,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "presetCorner",
    )
    val spin by animateFloatAsState(if (isActive) 360f else 0f, spring(stiffness = Spring.StiffnessVeryLow), label = "presetSpin")

    Box(modifier) {
        Tip(audioFxPresetSummary(preset)) {
            Surface(
                shape = RoundedCornerShape(corner),
                color = container,
                contentColor = content,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PRESET_CARD_HEIGHT)
                    .clip(RoundedCornerShape(corner))
                    .combinedClickable(
                        role = Role.Button,
                        onClick = onToggle,
                        onLongClick = { menuOpen = true },
                    )
                    .onClick(matcher = PointerMatcher.mouse(PointerButton.Secondary)) { menuOpen = true }
                    .semantics { selected = isActive },
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .graphicsLayer { rotationZ = spin }
                                .clip(if (isActive) MaterialShapes.Cookie9Sided.toShape() else CircleShape)
                                .background(if (isActive) scheme.primary else scheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (isActive) Icons.Rounded.Check else Icons.Rounded.BookmarkBorder,
                                contentDescription = null,
                                tint = if (isActive) scheme.onPrimary else scheme.primary,
                                modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = -spin },
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        AnimatedVisibility(isActive, enter = scaleIn(), exit = scaleOut()) {
                            Surface(shape = CircleShape, color = scheme.primary, contentColor = scheme.onPrimary) {
                                Text(
                                    str("audio_fx_preset_on"),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        audioFxPresetSummary(preset),
                        style = MaterialTheme.typography.bodySmall,
                        color = content.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, shape = RoundedCornerShape(20.dp)) {
            DropdownMenuItem(
                text = { Text(str(if (isActive) "audio_fx_preset_turn_off" else "audio_fx_preset_apply")) },
                leadingIcon = { Icon(if (isActive) Icons.Rounded.BookmarkBorder else Icons.Rounded.Bookmark, null) },
                onClick = { menuOpen = false; onToggle() },
            )
            DropdownMenuItem(
                text = { Text(str("audio_fx_update_preset")) },
                leadingIcon = { Icon(Icons.Rounded.Save, null) },
                enabled = !isActive,
                onClick = { menuOpen = false; onUpdate() },
            )
            DropdownMenuItem(
                text = { Text(str("audio_fx_rename")) },
                leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) },
                onClick = { menuOpen = false; onRename() },
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = { Text(str("btn_delete"), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { menuOpen = false; onDelete() },
            )
        }
    }
}

/** "3 effects · 1.25×": what a preset changes, at a glance. */
internal fun audioFxPresetSummary(preset: AudioFxPreset): String {
    val count = preset.activeEffectCount()
    val effects = when (count) {
        0 -> str("audio_fx_preset_no_effects")
        1 -> str("audio_fx_preset_one_effect")
        else -> str("audio_fx_preset_effects", count)
    }
    val speed = preset.effects.speed
    if (speed == 1f) return effects
    val text = String.format(Strings.locale(), "%.2f", speed).trimEnd('0').trimEnd('.', ',')
    return "$effects · $text×"
}

/** Asks for a preset name, saying as it is typed why one cannot be used. Enter saves, Escape cancels. */
@Composable
internal fun AudioFxPresetNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    nameError: (String) -> AudioFxPresetNameError?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var field by remember { mutableStateOf(TextFieldValue(initialName, TextRange(0, initialName.length))) }
    val focusRequester = remember { FocusRequester() }
    val error = nameError(field.text)
    // An empty field is simply not ready yet; it is not worth a red message.
    val shownError = error.takeIf { it != AudioFxPresetNameError.EMPTY }
    val canSave = error == null && field.text.trim() != initialName
    val save: () -> Unit = { if (canSave) onConfirm(field.text) }
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.BookmarkAdd, contentDescription = null) },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = field,
                onValueChange = { if (it.text.length <= AudioFxPreset.MAX_NAME_LENGTH + 8) field = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .trackTextInput()
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.Enter && event.type == KeyEventType.KeyDown) {
                            save()
                            true
                        } else false
                    },
                label = { Text(str("audio_fx_preset_name")) },
                singleLine = true,
                isError = shownError != null,
                supportingText = {
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            when (shownError) {
                                AudioFxPresetNameError.TAKEN -> str("audio_fx_preset_name_used")
                                AudioFxPresetNameError.TOO_LONG -> str("audio_fx_preset_name_too_long")
                                else -> ""
                            },
                            modifier = Modifier.weight(1f),
                        )
                        Text("${field.text.trim().length}/${AudioFxPreset.MAX_NAME_LENGTH}")
                    }
                },
                shape = RoundedCornerShape(16.dp),
            )
            LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
        },
        confirmButton = {
            Button(onClick = save, enabled = canSave, shapes = ButtonDefaults.shapes()) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(str("btn_cancel")) }
        },
    )
}

@Composable
internal fun AudioFxPresetDeleteDialog(name: String, viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
        title = { Text(str("audio_fx_delete_preset")) },
        text = { Text(str("audio_fx_delete_preset_body", name)) },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.deleteAudioFxPreset(name)
                    onDismiss()
                },
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text(str("btn_delete")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(str("btn_cancel")) }
        },
    )
}

private val PRESET_CARD_HEIGHT = 112.dp
