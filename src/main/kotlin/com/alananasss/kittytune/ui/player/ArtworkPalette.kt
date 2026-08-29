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
     * ## Shades of the cover, not a scheme built from it
     *
     * "Mais surtout dans la vidéo c'est que le fond est animé sur différentes nuances de la cover."
     *
     * Three attempts at this, and the correction each time was the same one restated. The mesh started as four
     * tints of the single dominant colour, rotated a few degrees apart — a scheme invented from one sample. It
     * then became the cover's colours, but chosen to be as *different* from each other as possible and flattened
     * onto a fixed ramp of brightnesses, which is a scheme again by a longer route: a sleeve of a dozen reds
     * came back as red, green, blue, because nothing else was far enough away in hue to qualify.
     *
     * What the reference actually moves through are the sleeve's own shades. A red cover gives deep red, mid
     * red, bright pink and cream — neighbours, not opposites — and they keep the brightness they had, which is
     * why one corner of that screen can be nearly white while another is nearly black.
     *
     * So neighbours are allowed now: colours are kept apart by distance through all three of hue, saturation
     * and value rather than by hue alone, and the threshold is small enough that two shades of one colour are
     * two entries. Each keeps its own hue and its own saturation, and its own brightness too — clamped at the
     * top so white text survives, and at the bottom so a light is still a light.
     *
     * ## What is still forced, and why
     *
     * One thing. If every shade a cover offers falls inside a narrow band of brightness — which is what a
     * uniformly dark sleeve gives you — the band is stretched around its own middle until there is something to
     * see. Honesty about a black cover is a black screen, and that was the first bug of the three.
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
                val hsv = FloatArray(3)
                java.awt.Color.RGBtoHSB(arr[1] / arr[0], arr[2] / arr[0], arr[3] / arr[0], hsv)
                hsv to arr[0] * (0.5f + hsv[1])
            }
            .sortedByDescending { it.second }
            .map { it.first }

        val chosen = mutableListOf<FloatArray>()
        for (hsv in ranked) {
            if (chosen.size == count) break
            // Distance through all three axes. Hue alone was the mistake: it threw away every shade of the
            // dominant colour, which is precisely what the background is supposed to move through.
            if (chosen.none { distance(it, hsv) < MIN_SEPARATION }) chosen.add(hsv)
        }

        // A sleeve with fewer than `count` distinguishable shades — one flat colour, near enough. Fill up by
        // stepping the brightness of what we have, which is still that colour's own shades rather than a hue
        // wheel's guesses about what goes with it.
        var step = 1
        while (chosen.isNotEmpty() && chosen.size < count) {
            val base = chosen.first()
            chosen.add(floatArrayOf(base[0], base[1], (base[2] * (1f - step * 0.18f)).coerceAtLeast(0.14f)))
            step++
        }

        return spreadBrightness(chosen).map { hsv ->
            Color(java.awt.Color.HSBtoRGB(hsv[0], max(hsv[1], MESH_SATURATION_FLOOR), hsv[2]))
        }
    }

    /**
     * Holds each shade's own brightness, brightest first, and only intervenes when there is nothing to see.
     *
     * The clamp comes first: nothing above [MESH_VALUE_MAX], because white text has to sit on all of these, and
     * nothing below [MESH_VALUE_MIN], because a light darker than the wall is not a light. Then, if what is
     * left spans less than [MESH_MIN_SPREAD], it is stretched around its own mean — the case that matters being
     * a uniformly dark cover, where every shade is 0.2 and the screen would otherwise be black on black.
     */
    private fun spreadBrightness(shades: List<FloatArray>): List<FloatArray> {
        if (shades.isEmpty()) return shades
        val clamped = shades
            .map { floatArrayOf(it[0], it[1], it[2].coerceIn(MESH_VALUE_MIN, MESH_VALUE_MAX)) }
            .sortedByDescending { it[2] }

        val high = clamped.first()[2]
        val low = clamped.last()[2]
        if (high - low >= MESH_MIN_SPREAD || clamped.size == 1) return clamped

        val mean = clamped.map { it[2] }.average().toFloat()
        val factor = MESH_MIN_SPREAD / (high - low).coerceAtLeast(0.01f)
        return clamped.map {
            floatArrayOf(it[0], it[1], (mean + (it[2] - mean) * factor).coerceIn(MESH_VALUE_MIN, MESH_VALUE_MAX))
        }
    }

    /** Bright enough to read as a light, dark enough that white text still sits on it. */
    private const val MESH_VALUE_MAX = 0.72f
    private const val MESH_VALUE_MIN = 0.14f

    /** Below this difference between the lightest and darkest shade there is no mesh to see. */
    private const val MESH_MIN_SPREAD = 0.30f

    /** Below this a colour reads as grey, and a grey mesh is the flat rectangle this exists to avoid. */
    private const val MESH_SATURATION_FLOOR = 0.30f

    /**
     * How far apart two shades have to be to count as two.
     *
     * Small, and that is the point of this version: at a twelfth of the hue wheel — the previous threshold —
     * a cover made of one colour could only ever yield one entry, so the list was filled with hues the sleeve
     * did not contain.
     */
    private const val MIN_SEPARATION = 0.14f

    /**
     * Distance between two shades, with hue counted on a wheel and weighted below the other two.
     *
     * Weighted down deliberately: two reds at different brightnesses are two shades worth having, and two
     * near-blacks at different hues are the same near-black twice.
     */
    private fun distance(a: FloatArray, b: FloatArray): Float {
        val dh = kotlin.math.abs(a[0] - b[0]).let { min(it, 1f - it) }
        val ds = kotlin.math.abs(a[1] - b[1])
        val dv = kotlin.math.abs(a[2] - b[2])
        return kotlin.math.sqrt(dh * dh * 0.6f + ds * ds + dv * dv)
    }

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
