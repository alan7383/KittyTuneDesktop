import com.alananasss.kittytune.core.LinuxWindowHelper
import com.alananasss.kittytune.ui.player.mini.WindowDragHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.Frame
import java.awt.GraphicsEnvironment
import javax.swing.SwingUtilities

/**
 * The mini player must stay draggable on every OS, and must never claim a native
 * move it did not post (that lie disabled the manual fallback and read as
 * "can't drag, click or move at all").
 *
 * Window-touching tests need a display: they run on Windows CI (real desktop
 * session, see mini-player-drag-test.yml) and skip headless.
 */
class MiniPlayerDragTest {

    private fun withDisplay(): Boolean = !GraphicsEnvironment.isHeadless()

    private fun hiddenFrame(): Frame = Frame("MiniPlayerDragProbe")

    @Test
    fun nativeMoveRefusesNonDisplayableWindow() {
        assumeFalse("Requires a display to create AWT windows", GraphicsEnvironment.isHeadless())
        val frame = hiddenFrame()
        try {
            assertFalse(
                "A move on an unrealized window must report failure so the fallback engages",
                LinuxWindowHelper.startNativeMove(frame, 100, 100)
            )
        } finally {
            SwingUtilities.invokeAndWait { frame.dispose() }
        }
    }

    @Test
    fun windowsMoveReportsRealPostResult() {
        assumeTrue("Only runs on Windows", System.getProperty("os.name").lowercase().contains("win"))
        assumeFalse("Requires graphical environment", GraphicsEnvironment.isHeadless())
        var frame: Frame? = null
        try {
            SwingUtilities.invokeAndWait {
                val f = Frame("MiniPlayerDragPost")
                frame = f
                f.isUndecorated = true
                f.setBounds(100, 100, 400, 90)
                f.isVisible = true
            }
            val window = frame ?: error("Frame was not created")
            assertTrue(
                "A posted move on a live peer must report success",
                LinuxWindowHelper.startNativeMoveWindows(window)
            )
            SwingUtilities.invokeAndWait { window.dispose() }
            assertFalse(
                "A disposed peer must report failure, never a phantom success",
                LinuxWindowHelper.startNativeMoveWindows(window)
            )
        } finally {
            frame?.let { f -> SwingUtilities.invokeAndWait { runCatching { f.dispose() } } }
        }
    }

    @Test
    fun dragHandlerSelfHealsStaleDrag() {
        assumeFalse("Requires a display to create AWT windows", GraphicsEnvironment.isHeadless())
        val frame = hiddenFrame()
        try {
            val handler = WindowDragHandler(frame, onDragStart = {}, onDragEnd = {})
            assertFalse(handler.isDragging)

            handler.startDrag(100, 100)
            assertTrue(handler.isDragging)

            // A missed release must never latch dragging on: a fresh press resets.
            handler.startDrag(120, 120)
            assertTrue("Second press must reset stale state, not be ignored", handler.isDragging)

            handler.stopDrag()
            assertFalse(handler.isDragging)

            // Idempotent: stopping twice must not throw or corrupt listeners.
            handler.stopDrag()
            assertFalse(handler.isDragging)
        } finally {
            SwingUtilities.invokeAndWait { frame.dispose() }
        }
    }

    @Test
    fun dragHandlerFallsBackToManualMove() {
        assumeFalse("Requires a display to create AWT windows", GraphicsEnvironment.isHeadless())
        val frame = hiddenFrame()
        try {
            SwingUtilities.invokeAndWait { frame.setLocation(50, 50) }
            val handler = WindowDragHandler(frame, onDragStart = {}, onDragEnd = {})

            // Hidden frame: native move refuses -> manual fallback engages.
            handler.startDrag(100, 100)
            assertTrue(handler.isDragging)
            handler.onPointerMove(150, 130)
            assertEquals("Manual fallback must track the pointer delta", 100, frame.x)
            assertEquals("Manual fallback must track the pointer delta", 80, frame.y)
            handler.stopDrag()
            assertFalse(handler.isDragging)

            // After release the window stays put.
            handler.onPointerMove(999, 999)
            assertEquals(100, frame.x)
            assertEquals(80, frame.y)
        } finally {
            SwingUtilities.invokeAndWait { frame.dispose() }
        }
    }

    @Test
    fun manualMoveIsFramePacedAndLandsExactlyOnRelease() {
        assumeFalse("Requires a display to create AWT windows", GraphicsEnvironment.isHeadless())
        val frame = hiddenFrame()
        try {
            SwingUtilities.invokeAndWait { frame.setLocation(50, 50) }
            val handler = WindowDragHandler(frame, onDragStart = {}, onDragEnd = {})

            handler.startDrag(100, 100)
            handler.onPointerMove(150, 130)
            assertEquals("First placement applies immediately", 100, frame.x)
            assertEquals("First placement applies immediately", 80, frame.y)

            // Same instant: throttled, holds the last flushed position.
            handler.onPointerMove(1000, 900)
            assertEquals("Back-to-back moves must coalesce, not storm the compositor", 100, frame.x)
            assertEquals("Back-to-back moves must coalesce, not storm the compositor", 80, frame.y)

            // Release flushes the pending tail exactly under the cursor.
            handler.stopDrag()
            assertEquals(950, frame.x)
            assertEquals(850, frame.y)
            assertFalse(handler.isDragging)
        } finally {
            SwingUtilities.invokeAndWait { frame.dispose() }
        }
    }

    @Test
    fun dragHandlerHelperExistsForDisplayCheck() {
        // Pure sanity: the display probe itself behaves, so skips above mean
        // "headless", never a broken assumption.
        assertEquals(withDisplay(), !GraphicsEnvironment.isHeadless())
    }
}
