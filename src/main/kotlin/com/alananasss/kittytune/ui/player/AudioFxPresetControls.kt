@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.alananasss.kittytune.ui.player

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.EscapableAlertDialog
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.core.trackTextInput

@Composable
internal fun AudioFxPresetControls(
    viewModel: PlayerViewModel,
    presetToDelete: String?,
    onDismissDelete: () -> Unit,
) {
    var presetToSave by remember { mutableStateOf<AudioFxPreset?>(null) }

    FilledTonalButton(
        onClick = { presetToSave = viewModel.currentAudioFxPreset },
        modifier = Modifier.fillMaxWidth(),
        shapes = ButtonDefaults.shapes(),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Icon(
            Icons.Rounded.BookmarkAdd,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(str("audio_fx_save_preset"))
    }

    presetToSave?.let { preset ->
        var name by remember { mutableStateOf("") }
        val focusRequester = remember { FocusRequester() }
        val duplicate = viewModel.audioFxPresets.keys.any { it.equals(name.trim(), ignoreCase = true) }
        val canSave = name.isNotBlank() && !duplicate
        val save: () -> Unit = {
            if (canSave && viewModel.saveAudioFxPreset(name, preset)) presetToSave = null
        }
        EscapableAlertDialog(
            onDismissRequest = { presetToSave = null },
            icon = { Icon(Icons.Rounded.BookmarkAdd, contentDescription = null) },
            title = { Text(str("audio_fx_save_preset")) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .trackTextInput()
                        .onPreviewKeyEvent { event ->
                            if (event.key == Key.Enter && event.type == KeyEventType.KeyDown && canSave) {
                                save()
                                true
                            } else false
                        },
                    label = { Text(str("audio_fx_preset_name")) },
                    singleLine = true,
                    isError = duplicate,
                    supportingText = if (duplicate) {
                        { Text(str("audio_fx_preset_name_used")) }
                    } else null,
                    shape = RoundedCornerShape(16.dp),
                )
                LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
            },
            confirmButton = {
                Button(onClick = save, enabled = canSave, shapes = ButtonDefaults.shapes()) {
                    Text(str("btn_save"))
                }
            },
            dismissButton = {
                TextButton(onClick = { presetToSave = null }, shapes = ButtonDefaults.shapes()) {
                    Text(str("btn_cancel"))
                }
            },
        )
    }

    presetToDelete?.let { name ->
        AudioFxPresetDeleteDialog(name, viewModel, onDismissDelete)
    }
}

@Composable
internal fun AudioFxPresetDeleteDialog(name: String, viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str("audio_fx_delete_preset")) },
        text = { Text(name) },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.deleteAudioFxPreset(name)
                    onDismiss()
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(str("btn_delete"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(str("btn_cancel"))
            }
        },
    )
}
