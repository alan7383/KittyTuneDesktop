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

    /**
     * Several of the artwork's colours rather than one, for the full player's moving background (issue #33).
     *
     * ## Why one colour was not enough
     *
     * The full player's mesh was built from the single dominant colour, rotated a few degrees to fake a
     * palette. On a colourful sleeve that passes. On a nearly black one it produced a flat dark grey
     * rectangle with no mesh visible in it at all — which is exactly the screenshot that prompted this, and
     * the reference video makes the reason obvious: what moves there are the *sleeve's own* colours, several
     * of them, and it goes from near-white to near-black inside one frame.
     *
     * So this returns the top buckets of the same histogram [dominant] picks its winner from, kept apart in
     * hue so five shades of one red do not come back as five entries, and then **normalised**: the returned
     * colours are pushed onto a spread of brightnesses with a saturation floor. That normalisation is the
     * point. A black sleeve has no bright colour to offer, and a background that honestly reflected that
     * would be a black screen; forcing the spread means a dark cover still gives a mesh you can see, in its
     * own hue.
     *
     * Ordered brightest first, because the caller draws them in that order and the last one is the ground.
     */
    fun meshPalette(image: BufferedImage, count: Int = 5): List<Color> {
        val buckets = histogram(image)
        if (buckets.isEmpty()) return emptyList()

        // Weighted the way `dominant` weighs them — how much of the sleeve is this colour, biased towards
        // saturated ones, since a large field of near-grey is not what anybody remembers a cover by.
        val ranked = buckets.values
            .map { arr ->
                val r = arr[1] / arr[0]
                val g = arr[2] / arr[0]
                val b = arr[3] / arr[0]
                val hsv = FloatArray(3)
                java.awt.Color.RGBtoHSB(r, g, b, hsv)
                Triple(hsv.copyOf(), arr[0] * (0.5f + hsv[1]), hsv[0])
            }
            .sortedByDescending { it.second }

        val chosen = mutableListOf<FloatArray>()
        for ((hsv, _, hue) in ranked) {
            if (chosen.size == count) break
            // Far enough from everything already taken. Without this a gradient sleeve fills the whole list
            // with neighbouring buckets of one colour, which is the single-seed problem again by another route.
            val tooClose = chosen.any { existing ->
                val d = kotlin.math.abs(existing[0] - hue)
                min(d, 1f - d) < HUE_SEPARATION
            }
            if (!tooClose) chosen.add(hsv)
        }

        // Not enough distinct colours on the sleeve — a monochrome cover. Fill up by walking round the wheel
        // from what we have, which keeps the result recognisably the record's rather than inventing a scheme.
        var step = 1
        while (chosen.size < count) {
            val base = chosen.first()
            chosen.add(floatArrayOf((base[0] + step * HUE_SEPARATION) % 1f, base[1], base[2]))
            step++
        }

        return chosen.mapIndexed { index, hsv ->
            val value = MESH_VALUES[index.coerceAtMost(MESH_VALUES.lastIndex)]
            val saturation = max(hsv[1], MESH_SATURATION_FLOOR)
            Color(java.awt.Color.HSBtoRGB(hsv[0], saturation, value))
        }
    }

    /**
     * The brightnesses the mesh colours are forced onto, brightest first.
     *
     * The spread is what makes the background readable *and* visible: the top one is the light in the corner,
     * the bottom one is the wall. Nothing above 0.62, because white text has to sit on all of them.
     */
    private val MESH_VALUES = floatArrayOf(0.62f, 0.46f, 0.34f, 0.26f, 0.19f)

    /** Below this a colour reads as grey, and a grey mesh is the flat rectangle this exists to avoid. */
    private const val MESH_SATURATION_FLOOR = 0.34f

    /** A twelfth of the wheel: enough that two entries are two colours. */
    private const val HUE_SEPARATION = 0.083f

    /** The colour histogram both [dominant] and [meshPalette] read, bucketed by hue, saturation and value. */
    private fun histogram(image: BufferedImage): Map<Int, IntArray> {
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
        return buckets
    }

    private fun dominant(image: BufferedImage, clampValueTo: Float?, clampDown: Boolean): Color {
        val buckets = histogram(image)
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
