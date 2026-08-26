import com.alananasss.kittytune.audio.TRACK_GAIN_MAX_DB
import com.alananasss.kittytune.audio.TRACK_GAIN_MIN_DB
import com.alananasss.kittytune.audio.TrackGain
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The per-track volume trim (issue #33).
 *
 * The arithmetic is small but it is the part that can silently go wrong: a trim that escapes the
 * range the output line accepts is clamped by the mixer instead, so the stored value stops matching
 * what is heard and the buttons appear to do nothing near the ends.
 */
class TrackGainTest {

    @Test
    fun `no trim is the default and reads as zero`() {
        assertEquals(0, TrackGain.NONE)
        assertEquals("0 dB", TrackGain.label(TrackGain.NONE))
    }

    @Test
    fun `the range matches what the engine will accept`() {
        assertEquals(TRACK_GAIN_MIN_DB.toInt(), TrackGain.MIN_DB)
        assertEquals(TRACK_GAIN_MAX_DB.toInt(), TrackGain.MAX_DB)
        assertTrue(TrackGain.MIN_DB < 0 && TrackGain.MAX_DB > 0)
    }

    @Test
    fun `cut goes further than boost`() {
        // Bringing a track down is always safe; boosting is capped by the line's own gain control.
        assertTrue(-TrackGain.MIN_DB > TrackGain.MAX_DB)
    }

    @Test
    fun `adjust steps by one decibel in each direction`() {
        assertEquals(1, TrackGain.adjust(0, 1))
        assertEquals(-1, TrackGain.adjust(0, -1))
        assertEquals(3, TrackGain.adjust(2, 1))
    }

    @Test
    fun `adjust stops at the ends instead of running past them`() {
        assertEquals(TrackGain.MAX_DB, TrackGain.adjust(TrackGain.MAX_DB, 1))
        assertEquals(TrackGain.MIN_DB, TrackGain.adjust(TrackGain.MIN_DB, -1))
    }

    @Test
    fun `clamp brings a stored value from an older range back inside`() {
        assertEquals(TrackGain.MAX_DB, TrackGain.clamp(99))
        assertEquals(TrackGain.MIN_DB, TrackGain.clamp(-99))
        assertEquals(4, TrackGain.clamp(4))
    }

    @Test
    fun `a boost is never mistakable for a cut`() {
        assertEquals("+3 dB", TrackGain.label(3))
        assertEquals("−6 dB", TrackGain.label(-6))
        // A real minus sign, not a hyphen: the two are different characters and only one lines up
        // with the rest of the numbers in the player.
        assertTrue(TrackGain.label(-6).startsWith("−"))
    }

    @Test
    fun `resetting from any point lands on no trim`() {
        for (db in TrackGain.MIN_DB..TrackGain.MAX_DB) {
            assertEquals(TrackGain.NONE, TrackGain.adjust(db, -db), "from $db")
        }
    }
}
