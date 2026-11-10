package com.alananasss.kittytune.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.EncodedImageFormat

/**
 * The liked songs' aura is drawn from cached gradients that are moved, not rebuilt, and its fade is a layer
 * alpha. Both are easy to get wrong in a way that still renders a rectangle, so this pins the picture: colour
 * where a blob is, nothing where the fade should have taken it, and fully gone at alpha zero.
 */
class AuraRenderTest {

    private val width = 600
    private val height = 600
    // AURA_HEIGHT is 460 dp and the scene renders at density 1, so the aura fills the first 460 px.
    private val auraHeight = 460

    private fun render(intensity: Float, drift: Float, name: String): java.awt.image.BufferedImage {
        val palette = AuraPalette(
            colors = listOf(Color(0xFFFF0000), Color(0xFF00FF00), Color(0xFF0000FF)),
            mix = Color(0xFF808080),
        )
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            val intensityState = remember { mutableFloatStateOf(intensity) }
            val driftState = remember { mutableFloatStateOf(drift) }
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AuraCanvas(palette, intensityState, driftState)
            }
        }
        val output = File("build/previews/$name.png").apply { parentFile.mkdirs() }
        try {
            scene.render().use { image ->
                image.encodeToData(EncodedImageFormat.PNG)!!.use { data -> output.writeBytes(data.bytes) }
            }
        } finally {
            scene.close()
        }
        return ImageIO.read(output)
    }

    private fun redness(pixel: Int): Int = (pixel shr 16) and 0xFF

    @Test
    fun `a blob lights the top of the page and the bottom fades to nothing`() {
        val image = render(1f, 0f, "aura-lit")
        // Blob 0 sits at 30% across and 20% down at phase zero.
        val blob = image.getRGB((width * 0.30).toInt(), (auraHeight * 0.20).toInt())
        assertTrue(redness(blob) > 20, "the red blob should tint the top of the page, was ${redness(blob)}")
        val bottom = image.getRGB(width / 2, auraHeight - 2)
        assertTrue(redness(bottom) < 8, "the mask should have faded the aura out by the bottom")
    }

    @Test
    fun `the drift moves a blob rather than rebuilding it in place`() {
        val atZero = render(1f, 0f, "aura-phase-zero")
        val atHalfTurn = render(1f, PI.toFloat(), "aura-phase-half")
        val left = (width * 0.02).toInt()
        val middle = (auraHeight * 0.20).toInt()
        val leftAtZero = redness(atZero.getRGB(left, middle))
        val leftAtHalf = redness(atHalfTurn.getRGB(left, middle))
        assertTrue(
            leftAtHalf > leftAtZero + 8,
            "blob 0 should have drifted to the left edge by half a turn ($leftAtZero -> $leftAtHalf)",
        )
    }

    @Test
    fun `a faded-out aura draws nothing at all`() {
        val image = render(0f, 0f, "aura-faded")
        for (y in listOf(10, auraHeight / 2, auraHeight - 10)) {
            for (x in listOf(10, width / 2, width - 10)) {
                val pixel = image.getRGB(x, y)
                assertTrue(
                    (pixel and 0xFFFFFF) == 0,
                    "a zero layer alpha must leave the page untouched, found ${Integer.toHexString(pixel)} at $x,$y",
                )
            }
        }
    }
}
