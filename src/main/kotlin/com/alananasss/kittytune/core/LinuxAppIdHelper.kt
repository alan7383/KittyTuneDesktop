package com.alananasss.kittytune.core

import java.awt.Toolkit
import java.io.File
import java.nio.file.Files

/**
 * Ensures the Linux window manager / compositor associates KittyTune windows
 * with the correct application ID ("kitty-tune"), icon, and .desktop launcher.
 *
 * In OpenJDK's XToolkit, the X11 `WM_CLASS` property is initialized by deriving
 * the class name from the bottom of the stack trace (e.g. `com-alananasss-kittytune-MainKt`),
 * and `sun.java2d.wm.className` is ignored by OpenJDK. Compositors like Hyprland/Wayland
 * (via XWayland) and desktop shells like Quickshell / end-4 dotfiles query `window.class` to
 * resolve app icons and window rules. Setting `XToolkit.awtAppClassName` to "kitty-tune"
 * causes all AWT/Compose windows to declare `WM_CLASS = "kitty-tune", "kitty-tune"`.
 *
 * Also ensures that DISPLAY and XAUTHORITY are valid before AWT/Skiko connects to X11/Xwayland.
 * Under modern Linux desktop environments (e.g. KDE Plasma / KWin, GNOME/Mutter, Hyprland),
 * Xwayland requires MIT-MAGIC-COOKIE authentication located in `/run/user/<uid>/xauth_*`.
 * If KittyTune is launched without XAUTHORITY in its inherited process environment (e.g. via
 * Gradle daemon with configuration cache, IDEs, desktop shortcuts, or subshells), AWT's
 * `X11GraphicsEnvironment.initDisplay` fails with "Can't connect to X11 window server using ':0'".
 * This helper discovers active Xauthority files and sets them at both libc and filesystem level
 * before Toolkit initialization.
 */
object LinuxAppIdHelper {

    const val APP_ID = "kitty-tune"

    @Volatile
    private var applied = false

    fun apply(appId: String = APP_ID) {
        if (applied) return
        val os = System.getProperty("os.name")?.lowercase().orEmpty()
        if (!os.contains("linux") && !os.contains("nix")) return
        applied = true

        // 1. Ensure valid DISPLAY and XAUTHORITY before initializing any X11/AWT subsystem
        ensureX11Environment()

        // 2. Early system properties for AWT / Skiko / toolkit hints
        System.setProperty("sun.java2d.wm.className", appId)
        System.setProperty("awt.app.class.name", appId)

        // 3. Reflectively set sun.awt.X11.XToolkit.awtAppClassName
        try {
            Toolkit.getDefaultToolkit() // Initialize toolkit so XToolkit class is loaded
            val xToolkitClass = runCatching { Class.forName("sun.awt.X11.XToolkit") }.getOrNull()
            if (xToolkitClass != null) {
                val field = runCatching { xToolkitClass.getDeclaredField("awtAppClassName") }.getOrNull()
                if (field != null) {
                    try {
                        field.isAccessible = true
                        field.set(null, appId)
                        return
                    } catch (_: Throwable) {
                        // Reflection blocked by module system (--add-opens absent): use Unsafe fallback
                        setViaUnsafe(xToolkitClass, field, appId)
                    }
                }
            }
        } catch (t: Throwable) {
            println("[LinuxAppIdHelper] Could not set XToolkit.awtAppClassName: ${t.message}")
        }
    }

    private fun ensureX11Environment() {
        try {
            fixDisplay()
            fixXauthority()
        } catch (t: Throwable) {
            println("[LinuxAppIdHelper] Failed to prepare X11 environment: ${t.message}")
        }
    }

    private fun fixDisplay() {
        var display = System.getenv("DISPLAY")
        val x11SocketsDir = File("/tmp/.X11-unix")
        val availableDisplays = if (x11SocketsDir.isDirectory) {
            x11SocketsDir.listFiles()?.mapNotNull { file ->
                file.name.removePrefix("X").toIntOrNull()
            }?.sorted() ?: emptyList()
        } else {
            emptyList()
        }

        if (display.isNullOrBlank()) {
            if (availableDisplays.isNotEmpty()) {
                val targetDisplay = ":${availableDisplays.first()}"
                setNativeEnv("DISPLAY", targetDisplay)
                System.setProperty("display", targetDisplay)
            }
        } else {
            val currentNum = display.removePrefix(":").substringBefore(".").substringBefore(":").toIntOrNull()
            if (currentNum != null && availableDisplays.isNotEmpty() && !availableDisplays.contains(currentNum)) {
                val targetDisplay = ":${availableDisplays.first()}"
                setNativeEnv("DISPLAY", targetDisplay)
                System.setProperty("display", targetDisplay)
            }
        }
    }

    private fun fixXauthority() {
        val currentXauth = System.getenv("XAUTHORITY")
        if (!currentXauth.isNullOrBlank()) {
            val f = File(currentXauth)
            if (f.isFile && f.canRead() && f.length() > 0) {
                syncHomeXauthority(f)
                return
            }
        }

        val candidates = mutableListOf<File>()

        // 1. Check $XDG_RUNTIME_DIR or /run/user/<uid>
        val runtimeDirStr = System.getenv("XDG_RUNTIME_DIR")
        val runtimeDir = if (!runtimeDirStr.isNullOrBlank()) {
            File(runtimeDirStr)
        } else {
            File("/run/user").listFiles()?.firstOrNull { it.isDirectory && it.canRead() }
        }

        if (runtimeDir != null && runtimeDir.isDirectory) {
            runtimeDir.listFiles()?.forEach { file ->
                if (file.isFile && (file.name.startsWith("xauth") ||
                            file.name.contains("Xwaylandauth") ||
                            file.name.contains("xauthority", ignoreCase = true))) {
                    candidates.add(file)
                }
            }
        }

        // 2. Check /tmp
        File("/tmp").listFiles()?.forEach { file ->
            if (file.isFile && file.name.startsWith("xauth")) {
                candidates.add(file)
            }
        }

        // 3. Check ~/.Xauthority
        val homeXauth = File(System.getProperty("user.home"), ".Xauthority")
        if (homeXauth.isFile && !Files.isSymbolicLink(homeXauth.toPath())) {
            candidates.add(homeXauth)
        }

        val validCandidate = candidates
            .filter { it.canRead() && it.length() > 0 }
            .maxWithOrNull(compareBy { it.lastModified() })

        if (validCandidate != null) {
            setNativeEnv("XAUTHORITY", validCandidate.absolutePath)
            System.setProperty("xauthority", validCandidate.absolutePath)
            syncHomeXauthority(validCandidate)
        }
    }

    private fun syncHomeXauthority(targetFile: File) {
        try {
            val homeXauth = File(System.getProperty("user.home"), ".Xauthority")
            val linkPath = homeXauth.toPath()
            val targetPath = targetFile.toPath()

            if (Files.isSymbolicLink(linkPath)) {
                val currentTarget = runCatching { Files.readSymbolicLink(linkPath) }.getOrNull()
                if (currentTarget == targetPath) {
                    return
                }
                Files.deleteIfExists(linkPath)
            } else if (homeXauth.exists()) {
                if (homeXauth.canonicalPath == targetFile.canonicalPath) {
                    return
                }
                if (homeXauth.length() > 0) return
                Files.deleteIfExists(linkPath)
            }
            Files.createSymbolicLink(linkPath, targetPath)
        } catch (_: Throwable) {
            // Non-fatal if user home is read-only or symlink cannot be created
        }
    }

    private fun setNativeEnv(name: String, value: String) {
        try {
            com.sun.jna.NativeLibrary.getInstance("c").getFunction("setenv")
                .invokeInt(arrayOf<Any>(name, value, 1))
        } catch (_: Throwable) {
            // JNA might fail or libc might have different name on esoteric platforms
        }
    }

    private fun setViaUnsafe(clazz: Class<*>, field: java.lang.reflect.Field, value: String) {
        try {
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe")
            theUnsafeField.isAccessible = true
            val unsafe = theUnsafeField.get(null)

            val staticFieldBase = unsafeClass.getMethod("staticFieldBase", java.lang.reflect.Field::class.java)
            val staticFieldOffset = unsafeClass.getMethod("staticFieldOffset", java.lang.reflect.Field::class.java)
            val putObject = unsafeClass.getMethod("putObject", Any::class.java, Long::class.javaPrimitiveType, Any::class.java)

            val base = staticFieldBase.invoke(unsafe, field)
            val offset = staticFieldOffset.invoke(unsafe, field) as Long
            putObject.invoke(unsafe, base, offset, value)
        } catch (t: Throwable) {
            println("[LinuxAppIdHelper] Unsafe fallback failed: ${t.message}")
        }
    }
}
