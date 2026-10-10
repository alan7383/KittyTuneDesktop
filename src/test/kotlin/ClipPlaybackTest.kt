import com.alananasss.kittytune.audio.VolumeCurve
import org.junit.Test
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import javax.swing.JDialog
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClipPlaybackTest {

    @Test
    fun `planar stereo samples interleave correctly into 16-bit PCM little endian`() {
        val leftSamples = shortArrayOf(1000, 2000, -3000)
        val rightSamples = shortArrayOf(-1000, -2000, 3000)

        val leftBuffer = ShortBuffer.wrap(leftSamples)
        val rightBuffer = ShortBuffer.wrap(rightSamples)

        val count = minOf(leftBuffer.remaining(), rightBuffer.remaining())
        val totalBytes = count * 4
        val bytes = ByteArray(totalBytes)

        val gain = VolumeCurve.sliderToAmplitude(1.0f) // full volume
        val leftPos = leftBuffer.position()
        val rightPos = rightBuffer.position()

        for (i in 0 until count) {
            val lVal = (leftBuffer.get(leftPos + i) * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            val rVal = (rightBuffer.get(rightPos + i) * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            val idx = i * 4
            bytes[idx] = lVal.toByte()
            bytes[idx + 1] = (lVal shr 8).toByte()
            bytes[idx + 2] = rVal.toByte()
            bytes[idx + 3] = (rVal shr 8).toByte()
        }

        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1000.toShort(), bb.getShort(0))
        assertEquals((-1000).toShort(), bb.getShort(2))
        assertEquals(2000.toShort(), bb.getShort(4))
        assertEquals((-2000).toShort(), bb.getShort(6))
        assertEquals((-3000).toShort(), bb.getShort(8))
        assertEquals(3000.toShort(), bb.getShort(10))
    }

    @Test
    fun `clock does not freeze on audio underrun and falls back to wall clock`() {
        class MockClock(private val sampleRate: Int) {
            var simulatedFramePos = 0L
            var baseUs = 0L
            var baseFrames = 0L
            var baseNanos = System.nanoTime()

            fun nowUs(): Long {
                val wallUs = baseUs + (System.nanoTime() - baseNanos) / 1000
                val audioUs = baseUs + (simulatedFramePos - baseFrames) * 1_000_000L / sampleRate
                return if (audioUs < wallUs - 150_000L) {
                    wallUs - 150_000L
                } else {
                    audioUs
                }
            }
        }

        val clock = MockClock(44100)
        // Normal playback: 1 second played
        clock.simulatedFramePos = 44100L

        // Simulate underrun: audio position freezes while time passes
        clock.baseNanos = System.nanoTime() - 2_000_000_000L // 2 seconds of wall time passed

        val now = clock.nowUs()
        // Audio position was 1.0s, but 2.0s passed; audioUs is 1.0s, wallUs is 2.0s.
        // Clock must NOT be stuck at 1.0s (1_000_000us); it must advance to at least 1.85s (1_850_000us)
        assertTrue(now >= 1_850_000L, "Clock must not stall when audio stops: nowUs was $now")
    }

    @Test
    fun `graphics device fullScreenWindow expands dialog and restores bounds on exit`() {
        if (GraphicsEnvironment.isHeadless()) return

        var parent: Frame? = null
        var dialog: JDialog? = null

        try {
            SwingUtilities.invokeAndWait {
                parent = Frame("ParentFrame").apply {
                    setBounds(50, 50, 400, 300)
                    isVisible = true
                }
                dialog = JDialog(parent, "TestClipDialog").apply {
                    setBounds(120, 100, 640, 480)
                    isVisible = true
                }
            }

            val currentDialog = dialog!!
            val initialBounds = currentDialog.bounds
            assertEquals(120, initialBounds.x)
            assertEquals(100, initialBounds.y)
            assertEquals(640, initialBounds.width)
            assertEquals(480, initialBounds.height)

            val device = currentDialog.graphicsConfiguration?.device
            if (device != null && device.isFullScreenSupported) {
                SwingUtilities.invokeAndWait {
                    device.fullScreenWindow = currentDialog
                }
                assertEquals(currentDialog, device.fullScreenWindow)

                val fsBounds = currentDialog.bounds
                val screenBounds = currentDialog.graphicsConfiguration.bounds
                assertEquals(screenBounds.width, fsBounds.width)
                assertEquals(screenBounds.height, fsBounds.height)

                SwingUtilities.invokeAndWait {
                    device.fullScreenWindow = null
                    currentDialog.bounds = initialBounds
                }
                assertNull(device.fullScreenWindow)
                assertEquals(initialBounds, currentDialog.bounds)
            }
        } finally {
            SwingUtilities.invokeAndWait {
                dialog?.dispose()
                parent?.dispose()
            }
        }
    }
}
