package com.alananasss.kittytune.core

import java.awt.Toolkit

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

        // 1. Early system properties for AWT / Skiko / toolkit hints
        System.setProperty("sun.java2d.wm.className", appId)
        System.setProperty("awt.app.class.name", appId)

        // 2. Reflectively set sun.awt.X11.XToolkit.awtAppClassName
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
