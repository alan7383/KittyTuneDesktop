import androidx.compose.ui.graphics.Color
import com.alananasss.kittytune.ui.player.ArtworkPalette
import java.awt.image.BufferedImage
import org.junit.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * The colours the full player's moving background is built from (issue #33).
 *
 * "Mais surtout dans la vidéo c'est que le fond est animé sur différentes nuances de la cover."
 *
 * Three attempts got the same correction restated, so these are the three failures, as tests: a scheme
 * invented from one sample, a rainbow invented from a single-coloured sleeve, and a black screen for a black
 * sleeve. Each one looked fine in the abstract and wrong on somebody's actual record.
 */
class ArtworkMeshPaletteTest {

    /** A square filled from a list of colours, one horizontal band each. */
    private fun cover(vararg bands: Int): BufferedImage {
        val size = 64
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until size) {
            val band = bands[(y * bands.size) / size]
            for (x in 0 until size) image.setRGB(x, y, band)
        }
        return image
    }

    private fun hueOf(color: Color): Float {
        val hsb = java.awt.Color.RGBtoHSB(
            (color.red * 255f).toInt(), (color.green * 255f).toInt(), (color.blue * 255f).toInt(), null,
        )
        return hsb[0]
    }

    private fun valueOf(color: Color): Float {
        val hsb = java.awt.Color.RGBtoHSB(
            (color.red * 255f).toInt(), (color.green * 255f).toInt(), (color.blue * 255f).toInt(), null,
        )
        return hsb[2]
    }

    /**
     * The correction he had to give twice. A sleeve of one colour at several brightnesses must come back as
     * that colour at several brightnesses — not as a hue wheel's idea of what goes with it.
     */
    @Test
    fun `a sleeve of one colour returns shades of that colour`() {
        val reds = cover(0xFF3B0A0A.toInt(), 0xFF7A1414.toInt(), 0xFFB02020.toInt(), 0xFFE04A4A.toInt())
        val palette = ArtworkPalette.meshPalette(reds)

        assertTrue(palette.size >= 4, "expected several shades, got ${palette.size}")
        // Every one of them still red: hue near 0, i.e. within a twentieth of the wheel of either end.
        palette.forEach { colour ->
            val hue = hueOf(colour)
            assertTrue(hue < 0.06f || hue > 0.94f, "hue $hue is not a red")
        }
    }

    /** And they have to actually differ, or there is no mesh to look at. */
    @Test
    fun `those shades span a visible range of brightness`() {
        val reds = cover(0xFF3B0A0A.toInt(), 0xFF7A1414.toInt(), 0xFFB02020.toInt(), 0xFFE04A4A.toInt())
        val values = ArtworkPalette.meshPalette(reds).map { valueOf(it) }
        assertTrue(values.max() - values.min() >= 0.25f, "spread was only ${values.max() - values.min()}")
    }

    /**
     * The first failure of the three. A uniformly dark sleeve offers nothing bright, and a background that
     * reflected that honestly was a flat near-black rectangle with no mesh visible in it at all.
     */
    @Test
    fun `a nearly black sleeve still gives something to see`() {
        val dark = cover(0xFF0E0E12.toInt(), 0xFF141419.toInt(), 0xFF1A1A20.toInt())
        val palette = ArtworkPalette.meshPalette(dark)
        if (palette.isEmpty()) return // every pixel below the histogram's floor: the caller falls back
        val values = palette.map { valueOf(it) }
        assertTrue(values.max() - values.min() >= 0.25f, "a dark cover must still be stretched into a spread")
    }

    /** White text sits on all of these, so none of them may be bright enough to swallow it. */
    @Test
    fun `nothing comes back bright enough to lose white text on`() {
        val pale = cover(0xFFFFF3E0.toInt(), 0xFFFFE0B2.toInt(), 0xFFFFCC80.toInt())
        ArtworkPalette.meshPalette(pale).forEach {
            assertTrue(valueOf(it) <= 0.73f, "value ${valueOf(it)} is too light for white text")
        }
    }

    /** And none dark enough to be indistinguishable from the ground. */
    @Test
    fun `nothing comes back darker than the floor`() {
        val dark = cover(0xFF201018.toInt(), 0xFF100810.toInt())
        ArtworkPalette.meshPalette(dark).forEach {
            assertTrue(valueOf(it) >= 0.13f, "value ${valueOf(it)} is below the floor")
        }
    }

    /** The caller draws them in order and takes the last as the ground, so the order is part of the contract. */
    @Test
    fun `the list is ordered brightest first`() {
        val mixed = cover(0xFFE04A4A.toInt(), 0xFF2E7D32.toInt(), 0xFF1565C0.toInt(), 0xFF3B0A0A.toInt())
        val values = ArtworkPalette.meshPalette(mixed).map { valueOf(it) }
        assertEquals(values.sortedDescending(), values, "expected brightest first")
    }

    /** A genuinely multi-coloured sleeve keeps being multi-coloured: this must not collapse to one hue either. */
    @Test
    fun `a multi-coloured sleeve keeps more than one hue`() {
        val mixed = cover(0xFFE04A4A.toInt(), 0xFF2E7D32.toInt(), 0xFF1565C0.toInt())
        val hues = ArtworkPalette.meshPalette(mixed).map { hueOf(it) }
        assertTrue(
            hues.distinctBy { (it * 8).toInt() }.size >= 2,
            "expected several hues from a red/green/blue cover, got $hues",
        )
    }

    /** Nothing to read, nothing to invent — the caller has its own stand-in for that. */
    @Test
    fun `an image with no usable colour returns nothing`() {
        val black = BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB)
        assertTrue(ArtworkPalette.meshPalette(black).isEmpty())
    }
}
