import com.alananasss.kittytune.audio.AudioFormat
import com.alananasss.kittytune.audio.ReverbAudioProcessor
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReverbBugTest {

    @Test
    fun testReverbToggleEnabledAndQueueInput() {
        val reverb = ReverbAudioProcessor()
        reverb.configure(AudioFormat(44100, 2))

        // Reverb initially disabled
        reverb.setEnabled(false)

        // User enables reverb
        reverb.setEnabled(true)

        val input = ByteBuffer.allocateDirect(1024).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 512) input.putShort((i * 10).toShort())
        input.flip()

        reverb.queueInput(input)
        val out = reverb.getOutput()
        assertNotNull(out)
        assertEquals(1024, out.remaining())
    }

    @Test
    fun testReverbDisabledThenReEnabled() {
        val reverb = ReverbAudioProcessor()
        reverb.configure(AudioFormat(44100, 2))

        // User turns ON reverb
        reverb.setEnabled(true)

        val input = ByteBuffer.allocateDirect(1024).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 512) input.putShort(100.toShort())
        input.flip()
        reverb.queueInput(input)
        reverb.getOutput()

        // User turns OFF reverb
        reverb.setEnabled(false)

        // User turns ON reverb again
        reverb.setEnabled(true)

        val input2 = ByteBuffer.allocateDirect(1024).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 512) input2.putShort(200.toShort())
        input2.flip()

        reverb.queueInput(input2)
        val out2 = reverb.getOutput()
        assertNotNull(out2)
        assertEquals(1024, out2.remaining())
    }

    @Test
    fun testReverbTrackTransitionSimulation() {
        val reverb = ReverbAudioProcessor()
        reverb.configure(AudioFormat(44100, 2))
        reverb.setEnabled(true)

        // Track 1 plays
        for (frame in 0 until 10) {
            val input = ByteBuffer.allocateDirect(2048).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until 1024) input.putShort((i % 1000).toShort())
            input.flip()
            reverb.queueInput(input)
            val out = reverb.getOutput()
            assertEquals(2048, out.remaining())
        }

        // New music begins: track stops and chain is flushed
        reverb.flush()

        // Track 2 starts playing with reverb enabled
        for (frame in 0 until 10) {
            val input = ByteBuffer.allocateDirect(2048).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until 1024) input.putShort((i % 500).toShort())
            input.flip()
            reverb.queueInput(input)
            val out = reverb.getOutput()
            assertEquals(2048, out.remaining())
        }
    }

    @Test
    fun testReverbSafeWhenEnabledBeforeConfigure() {
        val reverb = ReverbAudioProcessor()
        // Enabled BEFORE configure is ever called
        reverb.setEnabled(true)

        val input = ByteBuffer.allocateDirect(1024).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 512) input.putShort(50.toShort())
        input.flip()

        // Should safely process or passthrough without throwing
        reverb.queueInput(input)
        val out = reverb.getOutput()
        assertNotNull(out)
        assertEquals(1024, out.remaining())
    }

    @Test
    fun testReverbResetAndReconfigure() {
        val reverb = ReverbAudioProcessor()
        reverb.configure(AudioFormat(44100, 2))
        reverb.setEnabled(true)

        reverb.reset()
        reverb.flush()

        val input = ByteBuffer.allocateDirect(1024).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 512) input.putShort(30.toShort())
        input.flip()

        reverb.queueInput(input)
        val out = reverb.getOutput()
        assertNotNull(out)
        assertEquals(1024, out.remaining())
    }

    @Test
    fun testReverbVaryingSampleRatesAndChannels() {
        val reverb = ReverbAudioProcessor()
        reverb.setEnabled(true)

        // 48kHz stereo
        reverb.configure(AudioFormat(48000, 2))
        val input48k = ByteBuffer.allocateDirect(1024).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 512) input48k.putShort(10.toShort())
        input48k.flip()
        reverb.queueInput(input48k)
        assertEquals(1024, reverb.getOutput().remaining())

        // 44.1kHz mono
        reverb.configure(AudioFormat(44100, 1))
        val inputMono = ByteBuffer.allocateDirect(512).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 256) inputMono.putShort(20.toShort())
        inputMono.flip()
        reverb.queueInput(inputMono)
        assertEquals(512, reverb.getOutput().remaining())
    }
}
