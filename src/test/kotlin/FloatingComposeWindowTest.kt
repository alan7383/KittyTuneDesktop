import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.awaitApplication
import androidx.compose.ui.window.rememberWindowState
import com.alananasss.kittytune.core.LinuxWindowHelper
import com.alananasss.kittytune.ui.common.FloatingComposeWindow
import java.awt.Graphics
import java.awt.GraphicsEnvironment
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.RepaintManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.skiko.GraphicsApi
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.awt.Color as AwtColor

class FloatingComposeWindowTest {
    @Test
    fun `Wayland overlay composes before showing and renders alpha through offscreen OpenGL`() {
        assumeTrue(LinuxWindowHelper.isWayland)
        assumeFalse(GraphicsEnvironment.isHeadless())
        assumeFalse(System.getenv("KITTYTUNE_FLOATING_RENDERER") == "direct")

        runBlocking {
            withTimeout(15_000) {
                val created = CompletableDeferred<JFrame>()
                val closed = CompletableDeferred<Unit>()
                val finish = CompletableDeferred<Unit>()
                val app = launch {
                    awaitApplication {
                        LaunchedEffect(Unit) {
                            finish.await()
                            exitApplication()
                        }
                        val state = rememberWindowState(
                            position = WindowPosition(40.dp, 40.dp),
                            size = DpSize(120.dp, 80.dp),
                        )
                        var ready by remember { mutableStateOf(false) }
                        FloatingComposeWindow(
                            onCloseRequest = { closed.complete(Unit) },
                            state = state,
                            title = "KittyTune floating rendering test",
                            transparent = true,
                            resizable = false,
                            visible = ready,
                        ) {
                            DisposableEffect(window) {
                                check(window.isDisplayable && !window.isShowing)
                                created.complete(window as JFrame)
                                ready = true
                                onDispose {}
                            }
                            Box(Modifier.size(40.dp).background(Color.Red.copy(alpha = 0.5f)))
                        }
                    }
                }
                try {
                    val frame = created.await()
                    while (!withContext(Dispatchers.Swing) { frame.isShowing }) delay(16)
                    withContext(Dispatchers.Swing) {
                        assertEquals(0, frame.background.alpha)
                        val panel = frame.contentPane as ComposePanel
                        val image = BufferedImage(frame.width, frame.height, BufferedImage.TYPE_INT_ARGB)
                        var pixelDuringPaint: Int? = null
                        val paintProbe = object : JComponent() {
                            private var paintingTestImage = false

                            override fun paintImmediately(x: Int, y: Int, width: Int, height: Int) {
                                val graphics = image.createGraphics()
                                try {
                                    graphics.color = AwtColor.BLUE
                                    graphics.fillRect(0, 0, image.width, image.height)
                                    paintingTestImage = true
                                    frame.paint(graphics)
                                } finally {
                                    paintingTestImage = false
                                    graphics.dispose()
                                }
                            }

                            override fun paintComponent(graphics: Graphics) {
                                if (paintingTestImage) pixelDuringPaint = image.getRGB(20, 20)
                            }
                        }
                        frame.glassPane = paintProbe
                        paintProbe.isVisible = true
                        frame.validate()

                        // Test while Swing is processing a repaint and can choose its own buffer.
                        val repaintManager = RepaintManager.currentManager(frame)
                        repaintManager.paintDirtyRegions()
                        paintProbe.repaint()
                        repaintManager.paintDirtyRegions()

                        assertEquals(GraphicsApi.OPENGL, panel.renderApi)
                        assertEquals(AwtColor.BLUE.rgb, pixelDuringPaint, "Keep the previous frame visible while painting")
                        val painted = image.getRGB(20, 20)
                        assertTrue((painted ushr 24) in 126..129, "Expected a half-transparent pixel")
                        assertTrue(((painted ushr 16) and 0xff) >= 250, "Expected a red pixel")
                        assertEquals(0, image.getRGB(100, 60) ushr 24)
                        frame.dispatchEvent(WindowEvent(frame, WindowEvent.WINDOW_CLOSING))
                    }
                    closed.await()
                    finish.complete(Unit)
                    app.join()
                    withContext(Dispatchers.Swing) {
                        assertFalse(frame.isDisplayable, "Closing the composition must dispose the render target")
                    }
                } finally {
                    finish.complete(Unit)
                }
            }
        }
    }
}
