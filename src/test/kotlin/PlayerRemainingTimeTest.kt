import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.utils.makeTimeString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlayerRemainingTimeTest {

    private val prefs = PlayerPreferences()

    @Before
    fun setUp() {
        // Reset to default
        Prefs.remove(PlayerPreferences.KEY_SHOW_REMAINING_TIME)
    }

    @Test
    fun testKeyConstant() {
        assertEquals("player_bar_show_remaining", PlayerPreferences.KEY_SHOW_REMAINING_TIME)
    }

    @Test
    fun testDefaultValueIsFalse() {
        assertFalse("Show remaining time should default to false", prefs.getShowRemainingTime())
    }

    @Test
    fun testTogglePreference() {
        prefs.setShowRemainingTime(true)
        assertTrue("Show remaining time should be true after setting true", prefs.getShowRemainingTime())

        prefs.setShowRemainingTime(false)
        assertFalse("Show remaining time should be false after setting false", prefs.getShowRemainingTime())
    }

    @Test
    fun testTimeFormattingDisplay() {
        val duration = 214_000L // 3:34
        val position = 200_000L // 3:20
        // Left: elapsed
        val elapsedStr = makeTimeString(position)
        assertEquals("03:20", elapsedStr)

        // Right when showRemaining is true: -00:14
        val remainingMs = (duration - position).coerceAtLeast(0L)
        val remainingStr = "-" + makeTimeString(remainingMs)
        assertEquals("-00:14", remainingStr)

        // Right when showRemaining is false: 03:34
        val durationStr = makeTimeString(duration)
        assertEquals("03:34", durationStr)
    }

    @Test
    fun testRemainingCoercedAtLeastZero() {
        val duration = 60_000L
        val position = 70_000L // Past duration
        val remainingMs = (duration - position).coerceAtLeast(0L)
        val remainingStr = "-" + makeTimeString(remainingMs)
        assertEquals("-00:00", remainingStr)
    }
}
