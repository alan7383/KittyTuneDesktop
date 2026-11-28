package com.alananasss.kittytune.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class StartupRevealTest {
    @Test fun `first draw modifier removal preserves the fade state`() = SwingUtilities.invokeAndWait {
        val ready = mutableStateOf(false)
        val scene = ImageComposeScene(width = 48, height = 48, coroutineContext = Dispatchers.Main.immediate) {
            val firstDraw = if (ready.value) Modifier else Modifier.drawWithContent { drawContent() }
            Box(firstDraw.startupReveal(ready.value, true).fillMaxSize().background(Color.White))
        }
        fun pixel(time: Long): Int = scene.render(time).use { image ->
            image.encodeToData()!!.use { bytes ->
                ImageIO.read(ByteArrayInputStream(bytes.bytes)).getRGB(24, 24) and 0xffffff
            }
        }
        try {
            assertEquals(0, pixel(0))
            ready.value = true
            assertEquals("ready must not reset the cover to fully visible", 0, pixel(16_000_000))
            pixel(32_000_000)
            val middle = pixel(96_000_000)
            assertTrue("fade must draw an intermediate frame", middle in 1..0xfffffe)
            pixel(240_000_000)
            assertEquals(0xffffff, pixel(256_000_000))
        } finally {
            scene.close()
        }
    }

    @Test fun `disabled fade and an already drawn window reveal immediately`() = SwingUtilities.invokeAndWait {
        for ((drawn, fade) in listOf(false to false, true to true)) {
            val scene = ImageComposeScene(width = 48, height = 48, coroutineContext = Dispatchers.Main.immediate) {
                Box(Modifier.startupReveal(drawn, fade).fillMaxSize().background(Color.White))
            }
            try {
                scene.render(0).use { image ->
                    image.encodeToData()!!.use { bytes ->
                        assertEquals(0xffffff, ImageIO.read(ByteArrayInputStream(bytes.bytes)).getRGB(24, 24) and 0xffffff)
                    }
                }
            } finally { scene.close() }
        }
    }
}
