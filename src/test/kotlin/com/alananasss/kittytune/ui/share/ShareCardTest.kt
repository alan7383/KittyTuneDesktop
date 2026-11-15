package com.alananasss.kittytune.ui.share

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.toAwtImage
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class ShareCardTest {

    private val url = "https://soundcloud.com/tove-lo/habits-stay-high"

    /** A cover with some texture, so the modules carry real artwork. */
    private fun cover(): BufferedImage = BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB).also { img ->
        for (y in 0 until 300) for (x in 0 until 300) img.setRGB(x, y, ((x * 255 / 300) shl 16) or ((y * 255 / 300) shl 8) or 0x60)
    }

    private fun decode(image: BufferedImage): String {
        val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(image.width, image.height, pixels)))
        return QRCodeReader().decode(bitmap).text
    }

    @Test
    fun `the cover code still scans in both styles`() {
        for (style in QrCoverRenderer.CoverCodeStyle.entries) {
            val qr = QrCoverRenderer.render(url, cover(), 1024, style)
            assertNotNull(qr)
            assertEquals("style $style", url, decode(qr!!.toAwtImage()))
        }
        assertEquals(url, decode(QrCoverRenderer.render(url, null, 1024)!!.toAwtImage()))
    }

    @Test
    fun `text share carries the lines, the credit and the link`() {
        assertEquals(
            "a\nb\n\nHabits — Tove Lo\n$url",
            shareText(listOf("a", "b"), "Habits", "Tove Lo", url),
        )
    }

    @Test
    fun `the share dialog opens in a short window`() {
        assumeTrue("run through headlessTest (isolated profile)", System.getenv("APPDATA")?.contains("test-profile") == true)
        val scene = ImageComposeScene(width = 800, height = 450) {
            MaterialTheme {
                ShareCardSheet(
                    artwork = cover(),
                    title = "Habits (Stay High)",
                    artist = "Tove Lo",
                    trackUrl = null,
                    lyrics = listOf("First line"),
                    allLyrics = listOf("First line", "Second line"),
                    onDismiss = {},
                )
            }
        }
        try {
            scene.render(0L).close()
            scene.render(16_000_000L).close()
        } finally {
            scene.close()
        }
    }

    @Test
    fun `exported card is a 4 by 5 picture`() {
        assumeTrue("run through headlessTest (isolated profile)", System.getenv("APPDATA")?.contains("test-profile") == true)
        val lyrics = renderShareCard(null, "Habits (Stay High)", "Tove Lo", ShareCardStyle.Neutral, null,
            listOf("I eat my dinner in my bathtub", "Then I go to sex clubs"))
        assertEquals(1080, lyrics.width)
        assertEquals(1350, lyrics.height)
        File("build/previews").mkdirs()
        ImageIO.write(lyrics, "png", File("build/previews/share-card-lyrics.png"))
        val qr = QrCoverRenderer.render(url, cover(), 1024)
        val song = renderShareCard(null, "Habits (Stay High)", "Tove Lo", ShareCardStyle.Neutral, qr, null)
        ImageIO.write(song, "png", File("build/previews/share-card-qr.png"))
    }
}
