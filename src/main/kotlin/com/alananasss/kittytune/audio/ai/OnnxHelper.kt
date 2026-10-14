package com.alananasss.kittytune.audio.ai

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.util.Locale

/**
 * Manages native ONNX Runtime library execution provider dispatch.
 * DirectML on Windows provides out-of-the-box hardware acceleration via DirectX 12.
 * On Linux, ONNX Runtime runs via the bundled high-performance SIMD multi-threaded CPU engine
 * (100% autonomous, zero external package installations required).
 */
object OnnxHelper {

    private val lock = Any()
    @Volatile
    private var nativeLoaded = false

    init {
        ensureNativeLibrariesLoaded()
    }

    /**
     * Preloads system-installed native libonnxruntime.so on Linux if present,
     * ensuring binary compatibility with system C++ runtime and preventing crashes.
     */
    fun ensureNativeLibrariesLoaded() {
        if (nativeLoaded) return
        synchronized(lock) {
            if (nativeLoaded) return
            val os = System.getProperty("os.name", "").lowercase(Locale.ROOT)
            if (os.contains("linux") || os.contains("unix")) {
                val candidatePaths = listOf(
                    "/usr/lib/libonnxruntime.so",
                    "/usr/lib64/libonnxruntime.so",
                    "/usr/local/lib/libonnxruntime.so"
                )
                for (path in candidatePaths) {
                    val file = File(path)
                    if (file.exists()) {
                        val result = runCatching {
                            System.load(file.absolutePath)
                        }
                        if (result.isSuccess) {
                            System.setProperty("onnxruntime.native.onnxruntime.skip", "true")
                            break
                        }
                    }
                }
            }
            val nativePath = System.getProperty("onnxruntime.native.path")
            if (!nativePath.isNullOrEmpty() && !File(nativePath, "libonnxruntime4j_jni.so").exists()) {
                System.clearProperty("onnxruntime.native.path")
            }
            nativeLoaded = true
        }
    }

    /**
     * Configures GPU acceleration on [sessionOpts] for the given [targetGpu].
     * DirectML is used on Windows (DirectX 12).
     *
     * @return The descriptive name of the acceleration provider attached, or null if CPU should be used.
     */
    fun applyGpuProvider(
        sessionOpts: OrtSession.SessionOptions,
        targetGpu: GpuDevice,
        gpuIndex: Int = 0,
        allowMigraphx: Boolean = false
    ): String? {
        val os = System.getProperty("os.name", "").lowercase(Locale.ROOT)

        if (os.contains("win")) {
            return try {
                sessionOpts.addDirectML(gpuIndex)
                "DirectML (GPU: ${targetGpu.name})"
            } catch (_: Throwable) {
                null
            }
        }

        return null
    }

    /**
     * MIGraphX is disabled on Linux to ensure 100% out-of-the-box compatibility
     * without multi-gigabyte ROCm dependencies or JIT kernel compilation freezes.
     */
    fun isMigraphxAvailable(): Boolean = false

    /**
     * DirectML is available natively on Windows via DirectX 12 out of the box.
     */
    fun isDirectMlAvailable(): Boolean {
        val os = System.getProperty("os.name", "").lowercase(Locale.ROOT)
        return os.contains("win")
    }
}
