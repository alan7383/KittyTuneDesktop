package com.alananasss.kittytune.core

import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.Taskbar
import java.awt.Window
import java.awt.image.BufferedImage
import kotlin.math.roundToInt

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
    /**
     * Loads a variant icon specially prepared for the system tray (Linux/Windows/macOS).
     *
     * System tray icons on Linux (AWT XTrayIconPeer / XEmbed) do not support true alpha transparency
     * and fill any transparent corner pixels with the default AWT window background (light gray/white
     * #dfdedd), creating ugly white triangle artifacts around rounded squircle icons.
     * Furthermore, single-step downscaling from 256px to 22px causes severe aliasing artifacts.
     *
     * This method:
     * 1. Detects and fills transparent/semi-transparent squircle corners using edge ray-marching,
     *    ensuring the tray icon has solid, matching corner colors without white edges.
     * 2. Pre-scales the icon to a clean 48x48 tray resolution using high-quality progressive
     *    bicubic downsampling (IconEncoders.scaled), preserving fine logo details.
     */
    fun loadTrayPainter(variantKey: String): androidx.compose.ui.graphics.painter.Painter? = runCatching {
        val source = load(variantKey) ?: return@runCatching null
        val filled = fillTransparentCorners(source)
        val scaled = IconEncoders.scaled(filled, 48)
        androidx.compose.ui.graphics.painter.BitmapPainter(
            scaled.toComposeImageBitmap()
        )
    }.getOrNull()

    /**
     * Fills any transparent pixels (e.g. rounded squircle corners from Android adaptive icons)
     * by ray-marching towards the center to find the nearest opaque pixel.
     */
    fun fillTransparentCorners(source: BufferedImage): BufferedImage {
        val w = source.width
        val h = source.height
        // Quick check: if the 4 extreme corners are already fully opaque, nothing to fill
        if ((source.getRGB(0, 0) ushr 24) == 255 &&
            (source.getRGB(w - 1, 0) ushr 24) == 255 &&
            (source.getRGB(0, h - 1) ushr 24) == 255 &&
            (source.getRGB(w - 1, h - 1) ushr 24) == 255
        ) {
            return source
        }

        val result = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val pixels = IntArray(w * h)
        source.getRGB(0, 0, w, h, pixels, 0, w)

        val cx = w / 2.0
        val cy = h / 2.0

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val argb = pixels[idx]
                val alpha = (argb ushr 24) and 0xff
                if (alpha < 255) {
                    val dx = cx - x
                    val dy = cy - y
                    val dist = kotlin.math.hypot(dx, dy)
                    if (dist > 0.0) {
                        val ux = dx / dist
                        val uy = dy / dist
                        var nearestRgb = argb and 0x00ffffff
                        var step = 1
                        val maxSteps = dist.toInt()
                        while (step <= maxSteps) {
                            val nx = (x + ux * step).roundToInt().coerceIn(0, w - 1)
                            val ny = (y + uy * step).roundToInt().coerceIn(0, h - 1)
                            val nArgb = pixels[ny * w + nx]
                            val nAlpha = (nArgb ushr 24) and 0xff
                            if (nAlpha >= 250) {
                                nearestRgb = nArgb and 0x00ffffff
                                break
                            }
                            step++
                        }
                        pixels[idx] = (0xff shl 24) or nearestRgb
                    } else {
                        pixels[idx] = (0xff shl 24) or (argb and 0x00ffffff)
                    }
                }
            }
        }

        result.setRGB(0, 0, w, h, pixels, 0, w)
        return result
    }
}
