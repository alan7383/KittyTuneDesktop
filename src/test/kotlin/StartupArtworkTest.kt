package com.alananasss.kittytune.core

import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import org.junit.Assert.*
import org.junit.Test

class StartupArtworkTest {
    @Test fun `every locale and completed stage has a baked text row`() {
        val stream = javaClass.getResourceAsStream("/startup/text.png")
        assertNotNull("Bundled startup text is missing", stream)
        val image = stream!!.use { ImageIO.read(it) }
        assertEquals(312, image.width)
        assertEquals(8 * StartupStage.entries.size * 26, image.height)
        for (row in 0 until 8 * StartupStage.entries.size) {
            assertTrue("Missing status or percentage in row $row", hasInk(image, row, 0, 220))
            assertTrue("Missing percentage in row $row", hasInk(image, row, 250, 312))
        }
        image.flush()
    }

    private fun hasInk(image: BufferedImage, row: Int, from: Int, end: Int): Boolean =
        (row * 26 until (row + 1) * 26).any { y ->
            (from until end).any { x -> image.getRGB(x, y).ushr(24) > 0 }
        }
}
