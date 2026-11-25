package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.PerformanceGpu
import com.alananasss.kittytune.core.PerformanceOptions
import com.alananasss.kittytune.core.PerformancePreset
import com.alananasss.kittytune.core.PerformanceRenderer
import com.alananasss.kittytune.core.PerformanceSettings
import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Composable
private fun savedPerformanceOptions(): PerformanceOptions {
    val entries by Prefs.flow.collectAsState()
    return remember(entries) {
        PerformanceSettings.read { key -> (entries[key] as? JsonPrimitive)?.contentOrNull }
    }
}

@Composable
internal fun PerformancePresetsPage() {
    val options = savedPerformanceOptions()
    val selectedPreset = PerformancePreset.matching(options)
    PerformanceNote(str("performance_presets_note"))
    if (selectedPreset == null) {
        PerformanceNote(str("performance_presets_custom"))
    }
    SettingsGroup(items = PerformancePreset.entries.map { preset ->
        { shape ->
            val selected = preset == selectedPreset
            SettingsItem(
                shape = shape,
                title = str(preset.titleKey),
                subtitle = str(preset.descriptionKey),
                icon = when (preset) {
                    PerformancePreset.RESOURCE_SAVER -> Icons.Rounded.Eco
                    PerformancePreset.BALANCED -> Icons.Rounded.Balance
                    PerformancePreset.SMOOTH_BROWSING -> StackedAlbumCovers
                },
                trailingContent = {
                    Icon(
                        imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = if (selected) str("desc_selected") else null,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp).size(22.dp),
                    )
                },
                highlightKey = preset.titleKey,
                onClick = { PerformanceSettings.applyPreset(preset) },
            )
        }
    })
    SettingsGroup(items = listOf({ shape ->
        SettingsItem(
            shape = shape,
            title = str("performance_reset"),
            icon = Icons.Rounded.RestartAlt,
            highlightKey = "performance_reset",
            onClick = PerformanceSettings::reset,
        )
    }))
    PerformanceRestartNotice(options)
}

private val StackedAlbumCovers = ImageVector.Builder(
    name = "StackedAlbumCovers",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    val ink = SolidColor(Color.Black)
    path(stroke = ink, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
        // The back sleeve is visible above and to the right of the front sleeve.
        moveTo(7f, 3f)
        horizontalLineTo(19f)
        curveTo(20.1f, 3f, 21f, 3.9f, 21f, 5f)
        verticalLineTo(17f)

        moveTo(5f, 7f)
        horizontalLineTo(15f)
        curveTo(16.1f, 7f, 17f, 7.9f, 17f, 9f)
        verticalLineTo(19f)
        curveTo(17f, 20.1f, 16.1f, 21f, 15f, 21f)
        horizontalLineTo(5f)
        curveTo(3.9f, 21f, 3f, 20.1f, 3f, 19f)
        verticalLineTo(9f)
        curveTo(3f, 7.9f, 3.9f, 7f, 5f, 7f)
        close()
    }
    path(stroke = ink, strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round) {
        moveTo(10f, 10f)
        curveTo(12.2f, 10f, 14f, 11.8f, 14f, 14f)
        curveTo(14f, 16.2f, 12.2f, 18f, 10f, 18f)
        curveTo(7.8f, 18f, 6f, 16.2f, 6f, 14f)
        curveTo(6f, 11.8f, 7.8f, 10f, 10f, 10f)
        close()
        moveTo(9.8f, 14f)
        horizontalLineTo(10.2f)
    }
}.build()

@Composable
internal fun PerformanceGraphicsPage() {
    val options = savedPerformanceOptions()
    val os = remember { System.getProperty("os.name") }
    var openError by remember { mutableStateOf(false) }

    SettingsGroup(items = buildList {
        add { shape ->
            PerformanceChoice(
                shape = shape,
                titleKey = "performance_renderer",
                value = options.renderer,
                choices = PerformanceSettings.renderers(os).map { renderer ->
                    renderer to when (renderer) {
                        PerformanceRenderer.AUTO -> str("performance_auto")
                        PerformanceRenderer.DIRECT3D -> "Direct3D"
                        PerformanceRenderer.OPENGL -> "OpenGL"
                        PerformanceRenderer.SOFTWARE_COMPAT -> str("performance_software")
                        PerformanceRenderer.METAL -> "Metal"
                    }
                },
                subtitleKey = "performance_renderer_note",
            ) { Prefs.putString(PerformanceSettings.RENDERER, it.name) }
        }
        add { shape ->
            PerformanceChoice(
                shape = shape,
                titleKey = "performance_gpu",
                value = options.gpu,
                choices = listOf(
                    PerformanceGpu.AUTO to str("performance_auto"),
                    PerformanceGpu.INTEGRATED to str("performance_integrated"),
                    PerformanceGpu.DISCRETE to str("performance_discrete"),
                ),
                subtitleKey = "performance_gpu_note",
                enabled = PerformanceSettings.gpuPreferenceSupported(options.renderer, os),
            ) { Prefs.putString(PerformanceSettings.GPU, it.name) }
        }
        add { shape ->
            SettingsItem(
                shape = shape,
                title = str("performance_vsync"),
                subtitle = str("performance_vsync_note"),
                highlightKey = "performance_vsync",
                hasSwitch = true,
                switchState = options.vsync,
                onSwitchChange = { Prefs.putBoolean(PerformanceSettings.VSYNC, it) },
            )
        }
        if (os.startsWith("Windows", ignoreCase = true)) {
            add { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("performance_windows_gpu"),
                    subtitle = str("performance_windows_gpu_note"),
                    highlightKey = "performance_windows_gpu",
                    onClick = {
                        openError = runCatching {
                            ProcessBuilder("explorer.exe", "ms-settings:display-advancedgraphics").start()
                        }.isFailure
                    },
                )
            }
        }
    })
    if (openError) {
        PerformanceNote(str("performance_windows_gpu_error"), color = MaterialTheme.colorScheme.error)
    }
    PerformanceRestartNotice(options)
}

@Composable
internal fun PerformanceCachesPage() {
    val options = savedPerformanceOptions()
    SettingsGroup(items = listOf(
        { shape -> CacheLimitChoice(shape, PerformanceSettings.IMAGE_CACHE, options.imageCacheMiB, PerformanceSettings.imageCacheSizes) },
        { shape -> CacheLimitChoice(shape, PerformanceSettings.GPU_CACHE, options.gpuCacheMiB, PerformanceSettings.gpuCacheSizes) },
        { shape -> CacheLimitChoice(shape, PerformanceSettings.DISK_CACHE, options.diskCacheMiB, PerformanceSettings.diskCacheSizes) },
    ))
    PerformanceNote(str("performance_cache_note"))
    PerformanceRestartNotice(options)
}

@Composable
internal fun PerformanceRestartNotice(options: PerformanceOptions = savedPerformanceOptions(), showHint: Boolean = true) {
    val pending = options != PerformanceSettings.active
    if (pending || showHint) {
        PerformanceNote(
            text = str(if (pending) "performance_pending" else "performance_restart"),
            color = if (pending) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun PerformanceNote(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = color,
    )
}

@Composable
private fun CacheLimitChoice(shape: Shape, key: String, value: Int, sizes: List<Int>) {
    PerformanceChoice(shape, key, value, sizes.map { it to "$it MiB" }) { Prefs.putInt(key, it) }
}

@Composable
private fun <T> PerformanceChoice(
    shape: Shape,
    titleKey: String,
    value: T,
    choices: List<Pair<T, String>>,
    subtitleKey: String? = null,
    enabled: Boolean = true,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val title = str(titleKey)
    SettingsItem(
        shape = shape,
        title = title,
        subtitle = subtitleKey?.let { str(it) },
        highlightKey = titleKey,
        trailingText = choices.firstOrNull { it.first == value }?.second,
        enabled = enabled,
        onClick = { expanded = true },
    )
    if (expanded) {
        ChoiceDialog(
            title = title,
            options = choices,
            selected = value,
            onSelect = onSelect,
            onDismiss = { expanded = false },
        )
    }
}
