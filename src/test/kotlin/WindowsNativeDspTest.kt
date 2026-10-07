import com.alananasss.kittytune.audio.AudioFormat
import com.alananasss.kittytune.audio.NormalizationAudioProcessor
import com.alananasss.kittytune.ui.player.NormalizationLevel
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/** Exercises the bundled JNI library in memory. Never opens an audio device. */
class WindowsNativeDspTest {
    @Test
    fun bundledNativeLibraryMeasuresPcmRatherThanSilentlyFallingBack() {
        val processor = NormalizationAudioProcessor()
        try {
            processor.setParameters(true, NormalizationLevel.NORMAL)
            processor.configure(AudioFormat(44100, 2))
            processor.flush()
            val buffer = ByteBuffer.allocateDirect(4410 * 4).order(ByteOrder.LITTLE_ENDIAN)
            repeat(40) { chunk ->
                buffer.clear()
                repeat(4410) { frame ->
                    val sample = (sin(2 * PI * 440 * (chunk * 4410 + frame) / 44100) * 8000).toInt().toShort()
                    buffer.putShort(sample).putShort(sample)
                }
                buffer.flip()
                processor.queueInput(buffer)
                assertTrue("PCM input must be consumed", !buffer.hasRemaining())
            }
            val loudness = processor.getIntegratedLoudness()
            assertTrue("JNI must measure the tone; silent fallback returns -70 LUFS: $loudness", loudness in -35f..-5f)
        } finally {
            processor.release()
            assertEquals("Released native handles must not be reused", -70f, processor.getIntegratedLoudness(), 0f)
            processor.release() // Releasing twice must also be safe.
        }
    }
}
