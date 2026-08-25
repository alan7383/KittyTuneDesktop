package com.alananasss.kittytune.ui.player

import androidx.compose.ui.graphics.Color
import okhttp3.OkHttpClient
import okhttp3.Request
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min

object ArtworkPalette {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    fun load(url: String): BufferedImage? = try {
        if (url.startsWith("http")) {
            val req = Request.Builder().url(url).build()
            httpClient.newCall(req).execute().use { response ->
                response.body?.byteStream()?.let { ImageIO.read(it) }
            }
        } else {
            ImageIO.read(File(url))
        }
    } catch (_: Exception) {
        null
    }

    /**
     * The artwork's dominant colour, pushed light or dark so text stays legible on top of it.
     * For a backdrop, where contrast is the whole point.
     */
    fun dominantColor(image: BufferedImage, preferLight: Boolean): Color =
        dominant(image, clampValueTo = if (preferLight) 0.65f else null, clampDown = !preferLight)

    /**
     * The artwork's dominant colour exactly as it appears, with no brightness clamp.
     *
     * This is what the dynamic theme seeds from. The palette style the user picked — Vibrant,
     * Expressive, Fidelity and the rest — decides tone and chroma from the seed, so handing it a
     * pre-lightened colour meant the style was working from something that was no longer the
     * cover's colour, and the styles that stay faithful to the seed suffered most (issue #33).
     */
    fun dominantSeed(image: BufferedImage): Color = dominant(image, clampValueTo = null, clampDown = false)

    private fun dominant(image: BufferedImage, clampValueTo: Float?, clampDown: Boolean): Color {
        val w = min(image.width, 64)
        val h = min(image.height, 64)
        val scaled = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = scaled.createGraphics()
        g.drawImage(image, 0, 0, w, h, null)
        g.dispose()

        val buckets = HashMap<Int, IntArray>()
        for (y in 0 until h) {
            for (x in 0 until w) {
                val rgb = scaled.getRGB(x, y)
                val r = (rgb shr 16) and 0xFF
                val gg = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF
                val hsv = FloatArray(3)
                java.awt.Color.RGBtoHSB(r, gg, b, hsv)
                if (hsv[2] < 0.15f || (hsv[1] < 0.15f && hsv[2] > 0.9f)) continue
                val key = ((hsv[0] * 12).toInt() shl 8) or ((hsv[1] * 4).toInt() shl 4) or (hsv[2] * 4).toInt()
                val arr = buckets.getOrPut(key) { IntArray(4) }
                arr[0]++; arr[1] += r; arr[2] += gg; arr[3] += b
            }
        }

        if (buckets.isEmpty()) return Color(0xFF1E1E1E)

        val best = buckets.values.maxByOrNull { arr ->
            val cr = arr[1] / arr[0];
            val cg = arr[2] / arr[0];
            val cb = arr[3] / arr[0]
            val hsv = FloatArray(3)
            java.awt.Color.RGBtoHSB(cr, cg, cb, hsv)
            arr[0] * (0.5f + hsv[1])
        }!!

        var r = best[1] / best[0]
        var gg = best[2] / best[0]
        var b = best[3] / best[0]

        val hsv = FloatArray(3)
        java.awt.Color.RGBtoHSB(r, gg, b, hsv)
        when {
            clampValueTo != null -> hsv[2] = max(hsv[2], clampValueTo)
            clampDown -> hsv[2] = min(hsv[2], 0.45f)
        }
        val adjusted = java.awt.Color.HSBtoRGB(hsv[0], hsv[1], hsv[2])
        r = (adjusted shr 16) and 0xFF
        gg = (adjusted shr 8) and 0xFF
        b = adjusted and 0xFF

        return Color(red = r / 255f, green = gg / 255f, blue = b / 255f)
    }
}
