package com.alananasss.kittytune.core

enum class PerformanceRenderer { AUTO, DIRECT3D, OPENGL, SOFTWARE_COMPAT, METAL }
enum class PerformanceGpu { AUTO, INTEGRATED, DISCRETE }

data class PerformanceOptions(
    val renderer: PerformanceRenderer = PerformanceRenderer.AUTO,
    val gpu: PerformanceGpu = PerformanceGpu.AUTO,
    val vsync: Boolean = true,
    val imageCacheMiB: Int = 32,
    val gpuCacheMiB: Int = 64,
    val diskCacheMiB: Int = 256,
)

object PerformanceSettings {
    const val RENDERER = "performance_renderer"
    const val GPU = "performance_gpu"
    const val VSYNC = "performance_vsync"
    const val IMAGE_CACHE = "performance_image_cache"
    const val GPU_CACHE = "performance_gpu_cache"
    const val DISK_CACHE = "performance_disk_cache"
    val keys = listOf(RENDERER, GPU, VSYNC, IMAGE_CACHE, GPU_CACHE, DISK_CACHE)
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
        )
    }

    fun startupProperties(
        options: PerformanceOptions,
        os: String,
        arch: String = System.getProperty("os.arch"),
    ): Map<String, String> = buildMap {
        if (options.renderer != PerformanceRenderer.AUTO && options.renderer in renderers(os, arch)) {
            put("skiko.renderApi", options.renderer.name)
        }
        if (gpuPreferenceSupported(options.renderer, os) && options.gpu != PerformanceGpu.AUTO) {
            put("skiko.gpu.priority", options.gpu.name.lowercase())
        }
        put("skiko.vsync.enabled", options.vsync.toString())
        put("skiko.gpu.resourceCacheLimit", (options.gpuCacheMiB.toLong() * 1024 * 1024).toString())
    }

    fun applyAtStartup() {
        val options = read()
        val configured = keys.filter(Prefs::contains).toSet()
        val properties = startupProperties(options, System.getProperty("os.name"))
        properties.forEach { (property, value) ->
            val configuredKey = when (property) {
                "skiko.renderApi" -> RENDERER
                "skiko.vsync.enabled" -> VSYNC
                "skiko.gpu.resourceCacheLimit" -> GPU_CACHE
                else -> GPU
            }
            if (configuredKey in configured) System.setProperty(property, value)
        }
        active = options
    }

    fun reset() {
        keys.forEach(Prefs::remove)
        Prefs.flush()
    }
}
