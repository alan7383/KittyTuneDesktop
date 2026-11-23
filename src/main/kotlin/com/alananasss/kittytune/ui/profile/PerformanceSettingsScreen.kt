package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.*
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem

@Composable
internal fun PerformanceSettingsScreen() {
    val entries by Prefs.flow.collectAsState()
    val options = remember(entries) { PerformanceSettings.read() }
    val os = remember { System.getProperty("os.name") }
    var openError by remember { mutableStateOf(false) }
    Text(str("performance_restart"), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    if (options != PerformanceSettings.active) {
        Text(str("performance_pending"), modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.primary)
    }
    SettingsGroup(items = listOf(
        { shape ->
            PerformanceChoice(shape, "performance_renderer", options.renderer.name,
                PerformanceSettings.renderers(os).map { it.name to when (it) {
                    PerformanceRenderer.AUTO -> str("performance_auto")
                    PerformanceRenderer.SOFTWARE_COMPAT -> str("performance_software")
                    else -> it.name.replace("DIRECT3D", "Direct3D").replace("OPENGL", "OpenGL").replace("METAL", "Metal")
                } }, "performance_renderer_note") { Prefs.putString(PerformanceSettings.RENDERER, it) }
        },
        { shape ->
            PerformanceChoice(shape, "performance_gpu", options.gpu.name,
                listOf("AUTO" to str("performance_auto"), "INTEGRATED" to str("performance_integrated"),
                    "DISCRETE" to str("performance_discrete")), "performance_gpu_note",
                enabled = PerformanceSettings.gpuPreferenceSupported(options.renderer, os)) {
                Prefs.putString(PerformanceSettings.GPU, it)
            }
        },
        { shape -> SettingsItem(shape = shape, title = str("performance_vsync"), subtitle = str("performance_vsync_note"),
            hasSwitch = true, switchState = options.vsync, onSwitchChange = { Prefs.putBoolean(PerformanceSettings.VSYNC, it) }) },
    ))
    if (os.startsWith("Windows", true)) {
        SettingsGroup(items = listOf({ shape ->
            SettingsItem(shape = shape, title = str("performance_windows_gpu"), subtitle = str("performance_windows_gpu_note"), onClick = {
                openError = runCatching { ProcessBuilder("explorer.exe", "ms-settings:display-advancedgraphics").start() }.isFailure
            })
        }))
        if (openError) Text(str("performance_windows_gpu_error"), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
    }
    SettingsGroup(title = str("performance_caches"), items = listOf(
        { shape -> PerformanceChoice(shape, "performance_image_cache", options.imageCacheMiB.toString(),
            PerformanceSettings.imageCacheSizes.map { it.toString() to "$it MiB" }, "performance_cache_note") {
            Prefs.putInt(PerformanceSettings.IMAGE_CACHE, it.toInt())
        } },
        { shape -> PerformanceChoice(shape, "performance_gpu_cache", options.gpuCacheMiB.toString(),
            PerformanceSettings.gpuCacheSizes.map { it.toString() to "$it MiB" }, "performance_cache_note") {
            Prefs.putInt(PerformanceSettings.GPU_CACHE, it.toInt())
        } },
        { shape -> PerformanceChoice(shape, "performance_disk_cache", options.diskCacheMiB.toString(),
            PerformanceSettings.diskCacheSizes.map { it.toString() to "$it MiB" }, "performance_cache_note") {
            Prefs.putInt(PerformanceSettings.DISK_CACHE, it.toInt())
        } },
        { shape -> SettingsItem(shape = shape, title = str("performance_reset"), onClick = { PerformanceSettings.reset() }) },
    ))
}

@Composable
private fun PerformanceChoice(
    shape: Shape,
    titleKey: String,
    value: String,
    choices: List<Pair<String, String>>,
    subtitleKey: String,
    enabled: Boolean = true,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    SettingsItem(shape = shape, title = str(titleKey), subtitle = str(subtitleKey),
        trailingText = choices.firstOrNull { it.first == value }?.second,
        enabled = enabled, onClick = { expanded = true })
    if (expanded) {
        AlertDialog(onDismissRequest = { expanded = false }, title = { Text(str(titleKey)) }, text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                choices.forEach { (id, label) ->
                    Row(Modifier.fillMaxWidth().selectable(selected = id == value, role = Role.RadioButton,
                        onClick = { onSelect(id); expanded = false }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = id == value, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label)
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { expanded = false }) { Text(str("btn_cancel")) } })
    }
}
