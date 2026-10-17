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
    @Volatile
    private var sharedEnv: OrtEnvironment? = null

    init {
        ensureNativeLibrariesLoaded()
    }

    private interface LibC : com.sun.jna.Library {
        fun setenv(name: String, value: String, overwrite: Int): Int
    }

    private interface Kernel32 : com.sun.jna.win32.StdCallLibrary {
        fun SetEnvironmentVariableW(lpName: com.sun.jna.WString, lpValue: com.sun.jna.WString): Boolean
    }

    /**
     * Disables ONNX Runtime's internal client telemetry.
     * On Linux, ONNX Runtime's telemetry attempts to parse /proc/self/cmdline using a C++ std::regex
     * ((.*)[ ]*.*[\n]*). In libstdc++, recursive backtracking on command lines exceeding 3,928 characters
     * (such as the JVM desktop classpath with 100+ JARs) recurses unbounded until thread stack overflow
     * (SIGSEGV 139).
     * Setting ORT_DISABLE_TELEMETRY=1 in the native C environment completely disables this routine.
     */
    fun disableNativeTelemetry() {
        runCatching {
            System.setProperty("ORT_DISABLE_TELEMETRY", "1")
            val os = System.getProperty("os.name", "").lowercase(Locale.ROOT)
            if (os.contains("win")) {
                val kernel32 = com.sun.jna.Native.load("kernel32", Kernel32::class.java)
                kernel32.SetEnvironmentVariableW(com.sun.jna.WString("ORT_DISABLE_TELEMETRY"), com.sun.jna.WString("1"))
            } else {
                val libc = com.sun.jna.Native.load("c", LibC::class.java)
                libc.setenv("ORT_DISABLE_TELEMETRY", "1", 1)
            }
        }
    }

    /**
     * Thread-safe global OrtEnvironment singleton. Guarantees single-instance initialization
     * and prevents concurrent CreateEnv race conditions in native ONNX Runtime.
     */
    fun getOrInitEnvironment(): OrtEnvironment {
        sharedEnv?.let { return it }
        return synchronized(lock) {
            sharedEnv ?: run {
                disableNativeTelemetry()
                ensureNativeLibrariesLoaded()
                OrtEnvironment.getEnvironment().also {
                    runCatching { it.setTelemetry(false) }
                    sharedEnv = it
                }
            }
        }
    }

    /**
     * Preloads system-installed native libonnxruntime.so on Linux if present,
     * ensuring binary compatibility with system C++ runtime and preventing crashes.
     */
    fun ensureNativeLibrariesLoaded() {
        if (nativeLoaded) return
        synchronized(lock) {
            if (nativeLoaded) return
            disableNativeTelemetry()
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
            // Pre-warm the environment safely on the caller thread under the lock
            runCatching {
                if (sharedEnv == null) {
                    sharedEnv = OrtEnvironment.getEnvironment().also {
                        runCatching { it.setTelemetry(false) }
                    }
                }
            }
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
