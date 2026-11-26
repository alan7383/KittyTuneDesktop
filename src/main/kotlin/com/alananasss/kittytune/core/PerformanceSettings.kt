package com.alananasss.kittytune.core

import kotlinx.serialization.json.JsonPrimitive

enum class PerformanceRenderer { AUTO, DIRECT3D, OPENGL, SOFTWARE_COMPAT, METAL }
enum class PerformanceGpu { AUTO, INTEGRATED, DISCRETE }

data class PerformanceOptions(
    val renderer: PerformanceRenderer = PerformanceRenderer.AUTO,
    val gpu: PerformanceGpu = PerformanceGpu.AUTO,
    val vsync: Boolean = true,
    val imageCacheMiB: Int = 32,
    val gpuCacheMiB: Int = 64,
    val diskCacheMiB: Int = 256,
    val gpuId: String? = null,
    val gpuName: String? = null,
)

internal enum class PerformancePreset(val titleKey: String, val options: PerformanceOptions) {
    RESOURCE_SAVER("performance_preset_saver", PerformanceOptions(imageCacheMiB = 16, gpuCacheMiB = 32, diskCacheMiB = 128)),
    BALANCED("performance_preset_balanced", PerformanceOptions()),
    SMOOTH_BROWSING("performance_preset_smooth", PerformanceOptions(imageCacheMiB = 64, gpuCacheMiB = 128, diskCacheMiB = 512));

    val descriptionKey: String get() = "${titleKey}_sub"

    companion object {
        fun matching(options: PerformanceOptions): PerformancePreset? = entries.firstOrNull { it.options == options }
    }
}

object PerformanceSettings {
    const val RENDERER = "performance_renderer"
    const val GPU = "performance_gpu"
    const val GPU_ID = "performance_gpu_id"
    const val GPU_NAME = "performance_gpu_name"
    const val VSYNC = "performance_vsync"
    const val IMAGE_CACHE = "performance_image_cache"
    const val GPU_CACHE = "performance_gpu_cache"
    const val DISK_CACHE = "performance_disk_cache"
    val keys = listOf(RENDERER, GPU, GPU_ID, GPU_NAME, VSYNC, IMAGE_CACHE, GPU_CACHE, DISK_CACHE)
    val imageCacheSizes = listOf(16, 32, 64, 128)
    val gpuCacheSizes = listOf(32, 64, 128, 256)
    val diskCacheSizes = listOf(128, 256, 512, 1024)

    @Volatile var active = PerformanceOptions()
        private set

    fun renderers(
        os: String = System.getProperty("os.name"),
        arch: String = System.getProperty("os.arch"),
    ): List<PerformanceRenderer> = when {
        os.startsWith("Windows", true) -> buildList {
            add(PerformanceRenderer.AUTO)
            add(PerformanceRenderer.DIRECT3D)
            // Skiko rejects OpenGL before renderer fallback can run on Windows ARM64.
            if (!arch.equals("aarch64", true) && !arch.equals("arm64", true)) {
                add(PerformanceRenderer.OPENGL)
            }
            add(PerformanceRenderer.SOFTWARE_COMPAT)
        }
        os.startsWith("Mac", true) || os.startsWith("Darwin", true) ->
            listOf(PerformanceRenderer.AUTO, PerformanceRenderer.METAL, PerformanceRenderer.SOFTWARE_COMPAT)
        else -> listOf(PerformanceRenderer.AUTO, PerformanceRenderer.OPENGL, PerformanceRenderer.SOFTWARE_COMPAT)
    }

    fun gpuPreferenceSupported(renderer: PerformanceRenderer, os: String = System.getProperty("os.name")): Boolean =
        (os.startsWith("Windows", true) && renderer in listOf(PerformanceRenderer.AUTO, PerformanceRenderer.DIRECT3D)) ||
            ((os.startsWith("Mac", true) || os.startsWith("Darwin", true)) &&
                renderer in listOf(PerformanceRenderer.AUTO, PerformanceRenderer.METAL))

    fun read(
        os: String = System.getProperty("os.name"),
        arch: String = System.getProperty("os.arch"),
        value: (String) -> String? = { Prefs.getString(it, null) },
    ): PerformanceOptions {
        fun size(key: String, allowed: List<Int>, default: Int) = value(key)?.toIntOrNull()?.takeIf { it in allowed } ?: default
        return PerformanceOptions(
            renderer = renderers(os, arch).firstOrNull { it.name == value(RENDERER) } ?: PerformanceRenderer.AUTO,
            gpu = PerformanceGpu.entries.firstOrNull { it.name == value(GPU) } ?: PerformanceGpu.AUTO,
            vsync = value(VSYNC)?.toBooleanStrictOrNull() ?: true,
            imageCacheMiB = size(IMAGE_CACHE, imageCacheSizes, 32),
            gpuCacheMiB = size(GPU_CACHE, gpuCacheSizes, 64),
            diskCacheMiB = size(DISK_CACHE, diskCacheSizes, 256),
            gpuId = value(GPU_ID)?.trim()?.takeIf { it.isNotBlank() && it.length <= 512 },
            gpuName = value(GPU_NAME)?.trim()?.takeIf { it.isNotBlank() && it.length <= 256 },
        )
    }

    fun startupProperties(
        options: PerformanceOptions,
        os: String,
        arch: String = System.getProperty("os.arch"),
        resolveWindowsGpu: (String) -> PerformanceGpu? = { id ->
            runCatching { WindowsGraphicsDevices.read().preference(id) }.getOrNull()
        },
    ): Map<String, String> = buildMap {
        if (options.renderer != PerformanceRenderer.AUTO && options.renderer in renderers(os, arch)) {
            put("skiko.renderApi", options.renderer.name)
        }
        if (gpuPreferenceSupported(options.renderer, os)) {
            val id = options.gpuId?.takeIf { os.startsWith("Windows", true) }
            val priority = if (id == null) options.gpu else resolveWindowsGpu(id) ?: PerformanceGpu.AUTO
            // A named card can take the default DXGI order. Request it explicitly to override a JVM priority.
            if (id != null || priority != PerformanceGpu.AUTO) {
                put("skiko.gpu.priority", priority.name.lowercase())
            }
        }
        put("skiko.vsync.enabled", options.vsync.toString())
        put("skiko.gpu.resourceCacheLimit", (options.gpuCacheMiB.toLong() * 1024 * 1024).toString())
    }

    fun applyAtStartup() {
        val os = System.getProperty("os.name")
        val options = read(os)
        val configured = keys.filter(Prefs::contains).toSet()
        val properties = startupProperties(options, os)
        properties.forEach { (property, value) ->
            val configuredKey = when (property) {
                "skiko.renderApi" -> RENDERER
                "skiko.vsync.enabled" -> VSYNC
                "skiko.gpu.resourceCacheLimit" -> GPU_CACHE
                else -> GPU
            }
            if (configuredKey in configured || (configuredKey == GPU && GPU_ID in configured)) System.setProperty(property, value)
        }
        active = options
    }

    internal fun selectGpu(device: GraphicsDevice?, inventory: GraphicsDevices?) {
        val preference = if (device == null) PerformanceGpu.AUTO else
            requireNotNull(inventory?.preference(device.id)) { "This adapter requires OS graphics settings" }
        saveGpu(preference, device)
    }

    internal fun selectGpuPreference(preference: PerformanceGpu) = saveGpu(preference, null)

    private fun saveGpu(preference: PerformanceGpu, device: GraphicsDevice?) {
        Prefs.replaceEntries(listOf(GPU, GPU_ID, GPU_NAME), buildMap {
            put(GPU, JsonPrimitive(preference.name))
            if (device != null) {
                put(GPU_ID, JsonPrimitive(device.id))
                put(GPU_NAME, JsonPrimitive(device.name))
            }
        })
    }

    internal fun applyPreset(preset: PerformancePreset) {
        val options = preset.options
        val replacements = if (preset == PerformancePreset.BALANCED) {
            emptyMap()
        } else {
            mapOf(
                RENDERER to JsonPrimitive(options.renderer.name),
                GPU to JsonPrimitive(options.gpu.name),
                VSYNC to JsonPrimitive(options.vsync),
                IMAGE_CACHE to JsonPrimitive(options.imageCacheMiB),
                GPU_CACHE to JsonPrimitive(options.gpuCacheMiB),
                DISK_CACHE to JsonPrimitive(options.diskCacheMiB),
            )
        }
        Prefs.replaceEntries(keys, replacements)
    }

    fun reset() {
        applyPreset(PerformancePreset.BALANCED)
        Prefs.flush()
    }
}
