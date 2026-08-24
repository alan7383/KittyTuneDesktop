package com.alananasss.kittytune.core

import java.awt.Taskbar
import java.awt.Window
import java.awt.image.BufferedImage

/**
 * The part of the icon switch that applies to the process while it runs, as opposed to what
 * [AppIconInstaller] writes to disk for launchers.
 *
 * Compose's `Window(icon = …)` handles the window itself, but two things it cannot do:
 *
 *  - **macOS Dock.** The Dock does not read the window icon; it reads the application image,
 *    which is only reachable through [Taskbar]. Without this the Dock keeps showing the
 *    bundle icon until the app is relaunched.
 *  - **Multiple sizes.** Compose passes one bitmap, so Windows scales that single image for
 *    the 16 px taskbar slot and the 32 px Alt-Tab list alike. AWT accepts a list and lets the
 *    platform choose, which is the same reason the .ico carries every size.
 */
object AppIconRuntime {

    private val WINDOW_ICON_SIZES = intArrayOf(16, 20, 24, 32, 48, 64, 128, 256)

    /**
     * @param window the frame to hand the size ladder to. Passed in rather than discovered,
     *   because this runs from composition and [Window.getWindows] is still empty on the very
     *   first pass — which is exactly the launch where the icon matters most.
     */
    fun apply(variantKey: String, window: Window? = null) {
        val source = load(variantKey) ?: return
        setDockIcon(source)
        setWindowIcons(source, window)
    }

    private fun load(variantKey: String): BufferedImage? = runCatching {
        Thread.currentThread().contextClassLoader
            ?.getResourceAsStream(AppIconVariants.resourcePath(variantKey))
            ?.use { IconEncoders.decode(it.readBytes()) }
    }.getOrNull()

    /** macOS Dock, and the few Linux docks that implement the same protocol. */
    private fun setDockIcon(source: BufferedImage) {
        runCatching {
            if (!Taskbar.isTaskbarSupported()) return
            val taskbar = Taskbar.getTaskbar()
            if (!taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) return
            // Hand over the source at its own size; macOS scales it down for whatever the
            // Dock is currently showing, and upscaling here would only invent detail.
            taskbar.iconImage =
                if (maxOf(source.width, source.height) > 512) IconEncoders.scaled(source, 512) else source
        }
    }

    /**
     * Hands the window the full ladder of sizes. Runs after Compose has applied its own
     * single-bitmap icon, so this replaces it rather than being replaced by it.
     */
    private fun setWindowIcons(source: BufferedImage, window: Window?) {
        runCatching {
            val images = WINDOW_ICON_SIZES.map { IconEncoders.scaled(source, it) }
            val targets = if (window != null) listOf(window) else Window.getWindows().toList()
            targets.forEach { target -> runCatching { target.iconImages = images } }
        }
    }
}
