import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.PlayerSliderStyle
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerSliderTest {

    @Test
    fun testPlayerSliderStyleEnum() {
        val styles = PlayerSliderStyle.entries
        assertEquals(4, styles.size)
        assertTrue(styles.contains(PlayerSliderStyle.BAR))
        assertTrue(styles.contains(PlayerSliderStyle.WAVY))
        assertTrue(styles.contains(PlayerSliderStyle.SLIM))
        assertTrue(styles.contains(PlayerSliderStyle.SQUIGGLY))
    }

    @Test
    fun testPlayerSliderStylePreferences() {
        val prefs = PlayerPreferences()
        val originalStyle = prefs.getPlayerSliderStyle()

        try {
            prefs.setPlayerSliderStyle(PlayerSliderStyle.WAVY)
            assertEquals(PlayerSliderStyle.WAVY, prefs.getPlayerSliderStyle())

            prefs.setPlayerSliderStyle(PlayerSliderStyle.SLIM)
            assertEquals(PlayerSliderStyle.SLIM, prefs.getPlayerSliderStyle())

            prefs.setPlayerSliderStyle(PlayerSliderStyle.SQUIGGLY)
            assertEquals(PlayerSliderStyle.SQUIGGLY, prefs.getPlayerSliderStyle())

            prefs.setPlayerSliderStyle(PlayerSliderStyle.BAR)
            assertEquals(PlayerSliderStyle.BAR, prefs.getPlayerSliderStyle())
        } finally {
            prefs.setPlayerSliderStyle(originalStyle)
        }
    }

    @Test
    fun testPlayerSliderStyleFallback() {
        val prefs = PlayerPreferences()
        val originalStyle = prefs.getPlayerSliderStyle()

        try {
            Prefs.putString(PlayerPreferences.KEY_PLAYER_SLIDER_STYLE, "UNKNOWN_INVALID_STYLE")
            assertEquals(PlayerSliderStyle.WAVY, prefs.getPlayerSliderStyle())
        } finally {
            prefs.setPlayerSliderStyle(originalStyle)
        }
    }
}
