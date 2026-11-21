package com.alananasss.kittytune.ui.player.mini

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.data.local.PlayerPreferences
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO

class MiniPlayerAppearanceTest {
    @Test
    fun `artwork and progress options preserve the existing cover and progress preferences`() {
        val saved = Prefs.flow.value
        try {
            val prefs = PlayerPreferences()
            prefs.setMiniPlayerShowCover(false)
            prefs.setMiniPlayerShowProgress(false)
            prefs.setMiniPlayerCoverAsBackground(true)
            prefs.setMiniPlayerProgressFill(true)

            assertFalse(prefs.getMiniPlayerShowCover())
            assertFalse(prefs.getMiniPlayerShowProgress())
            assertTrue(prefs.getMiniPlayerCoverAsBackground())
            assertTrue(prefs.getMiniPlayerProgressFill())

            prefs.setMiniPlayerCoverAsBackground(false)
            assertTrue(prefs.getMiniPlayerProgressFill())
        } finally {
            Prefs.restore(saved)
        }
    }

    @Test
    fun `fill follows playback and can be combined with the artwork background`() {
        val fill = mutableStateOf(true)
        val backdrop = mutableStateOf(false)
        val progress = mutableFloatStateOf(0.5f)
        val artwork = File.createTempFile("kittytune-mini-player", ".png")
        val cover = BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB)
        val graphics = cover.createGraphics()
        try {
            graphics.color = java.awt.Color(95, 140, 212)
            graphics.fillRect(0, 0, cover.width, cover.height)
            ImageIO.write(cover, "png", artwork)
        } finally {
            graphics.dispose()
        }

        val scene = ImageComposeScene(width = 320, height = 72) {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(Modifier.fillMaxSize().background(Color(0xFF181820))) {
                    MiniPlayerAppearance(
                        artwork = artwork.toURI().toString(),
                        coverAsBackground = backdrop.value,
                        progressFill = fill.value,
                        progress = { progress.floatValue },
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Mini player", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
        var timestamp = 0L
        fun frame(): Pair<BufferedImage, ByteArray> {
            repeat(4) {
                timestamp += 16_000_000L
                scene.render(timestamp).close()
            }
            val bytes = scene.render(timestamp).use { image ->
                image.encodeToData(EncodedImageFormat.PNG)!!.use { it.bytes }
            }
            return ImageIO.read(ByteArrayInputStream(bytes)) to bytes
        }

        try {
            val half = frame().first
            assertNotEquals(half.getRGB(60, 12), half.getRGB(220, 12))

            progress.floatValue = 0f
            val zero = frame().first
            assertEquals(zero.getRGB(60, 12), zero.getRGB(220, 12))

            backdrop.value = true
            progress.floatValue = 0.5f
            fun hasBlueCover(image: BufferedImage): Boolean {
                val pixel = java.awt.Color(image.getRGB(220, 12))
                return pixel.blue - pixel.red > 20
            }
            var combinedFrame = frame()
            val deadline = System.nanoTime() + 2_000_000_000L
            while (!hasBlueCover(combinedFrame.first) && System.nanoTime() < deadline) {
                Thread.sleep(10)
                combinedFrame = frame()
            }
            val (combined, preview) = combinedFrame
            assertTrue("The supplied cover must be visible through the scrim", hasBlueCover(combined))
            assertNotEquals(combined.getRGB(60, 12), combined.getRGB(220, 12))

            fill.value = false
            val backdropOnly = frame().first
            assertNotEquals(combined.getRGB(60, 12), backdropOnly.getRGB(60, 12))
            assertEquals(combined.getRGB(220, 12), backdropOnly.getRGB(220, 12))

            File("build/previews/desktop-mini-player-appearance.png").apply {
                parentFile.mkdirs()
                writeBytes(preview)
            }
        } finally {
            scene.close()
            artwork.delete()
        }
    }
}
