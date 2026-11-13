import androidx.compose.ui.awt.ComposeWindow
import com.alananasss.kittytune.core.TransparentWindowRendering
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.GraphicsEnvironment
import javax.swing.SwingUtilities
import org.jetbrains.skiko.GraphicsApi
import org.jetbrains.skiko.SkiaLayer
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransparentWindowRenderingTest {
    private fun findLayer(component: Component): SkiaLayer? {
        if (component is SkiaLayer) return component
        return (component as? Container)?.components?.firstNotNullOfOrNull(::findLayer)
    }

    @Test
    fun `renderer fallback keeps transparent window background compatible`() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        assumeFalse(GraphicsEnvironment.isHeadless())
        SwingUtilities.invokeAndWait {
            TransparentWindowRendering.install()
            val window = ComposeWindow()
            try {
                window.isUndecorated = true
                window.isTransparent = true
                window.setSize(340, 68)
                window.addNotify()
                val layer = checkNotNull(findLayer(window))
                for (api in listOf(GraphicsApi.OPENGL, GraphicsApi.SOFTWARE_COMPAT, GraphicsApi.SOFTWARE_FAST)) {
                    layer.renderApi = api
                    assertEquals(api, window.renderApi)
                    assertTrue(window.background == null || window.background.alpha == 255,
                        "$api must not be inside an AWT per-pixel translucent window")
                }
                layer.renderApi = GraphicsApi.DIRECT3D
                assertEquals(0, window.background.alpha)
                assertTrue(window.isTransparent)
            } finally {
                window.dispose()
            }
        }
    }

    @Test
    fun `opaque main window background is left alone`() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        assumeFalse(GraphicsEnvironment.isHeadless())
        SwingUtilities.invokeAndWait {
            TransparentWindowRendering.install()
            val window = ComposeWindow()
            val background = Color(19, 19, 19)
            try {
                window.background = background
                window.setSize(340, 68)
                window.addNotify()
                checkNotNull(findLayer(window)).renderApi = GraphicsApi.OPENGL
                assertEquals(background, window.background)
            } finally {
                window.dispose()
            }
        }
    }
}
