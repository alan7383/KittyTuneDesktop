package com.alananasss.kittytune.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SettingsSuggest
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.audio.OutputDevice
import com.alananasss.kittytune.audio.listOutputDevices
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.PlayerPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wraps a speaker button so a right click on it lists the output devices, for moving the sound from one pair of
 * headphones to another, or to the speakers, without going through the settings (issue #66).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun OutputDevicePicker(
    onSelect: (deviceId: String) -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        content(Modifier.onClick(matcher = PointerMatcher.mouse(PointerButton.Secondary)) { expanded = true })
        OutputDeviceMenu(expanded = expanded, onDismiss = { expanded = false }, onSelect = {
            expanded = false
            onSelect(it)
        })
    }
}

@Composable
private fun OutputDeviceMenu(expanded: Boolean, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    // Asked for each time the menu opens: headphones come and go.
    val devices by produceState<List<OutputDevice>?>(initialValue = null, expanded) {
        value = if (expanded) withContext(Dispatchers.IO) { listOutputDevices() } else null
    }
    val current = remember(expanded) { PlayerPreferences().getAudioDevice() }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.widthIn(min = 240.dp, max = 360.dp),
    ) {
        Text(
            text = str("pref_audio_device_title"),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        DeviceItem(
            label = str("pref_audio_device_default"),
            icon = Icons.Rounded.SettingsSuggest,
            isCurrent = current.isEmpty(),
            onClick = { onSelect("") },
        )
        val list = devices
        if (!list.isNullOrEmpty()) HorizontalDivider(Modifier.padding(vertical = 4.dp))
        list?.forEach { device ->
            DeviceItem(
                label = device.label,
                icon = if (device.isHeadphones) Icons.Rounded.Headphones else Icons.Rounded.Speaker,
                isCurrent = device.id == current,
                onClick = { onSelect(device.id) },
            )
        }
    }
}

@Composable
private fun DeviceItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isCurrent: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    DropdownMenuItem(
        text = {
            Text(
                label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isCurrent) scheme.primary else scheme.onSurface,
            )
        },
        leadingIcon = { Icon(icon, null, tint = if (isCurrent) scheme.primary else scheme.onSurfaceVariant) },
        trailingIcon = if (isCurrent) {
            { Icon(Icons.Rounded.Check, null, tint = scheme.primary, modifier = Modifier.size(18.dp)) }
        } else null,
        onClick = onClick,
    )
}
