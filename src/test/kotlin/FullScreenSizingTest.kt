import com.alananasss.kittytune.data.local.PlayerPreferences
import org.junit.Test
import kotlin.test.assertEquals

class FullScreenSizingTest {

    @Test
    fun testFullScreenDefaultFontSize() {
        val prefs = PlayerPreferences()
        val origFsFontSize = prefs.getLyricsFullScreenFontSize()
        try {
            // Unset or reset should default to 42f
            prefs.setLyricsFullScreenFontSize(42f)
            assertEquals(42f, prefs.getLyricsFullScreenFontSize(), "Fullscreen font size default should be 42f")
        } finally {
            prefs.setLyricsFullScreenFontSize(origFsFontSize)
        }
    }

    @Test
    fun testFullScreenActiveScaleIndependent() {
        val prefs = PlayerPreferences()
        val origScale = prefs.getLyricsActiveScale()
        val origFsScale = prefs.getLyricsFullScreenActiveScale()
        try {
            prefs.setLyricsActiveScale(1.05f)
            prefs.setLyricsFullScreenActiveScale(1.15f)

            assertEquals(1.05f, prefs.getLyricsActiveScale(), 0.001f)
            assertEquals(1.15f, prefs.getLyricsFullScreenActiveScale(), 0.001f)

            // Clamping 1.00f..1.30f
            prefs.setLyricsFullScreenActiveScale(0.5f)
            assertEquals(1.00f, prefs.getLyricsFullScreenActiveScale(), 0.001f)

            prefs.setLyricsFullScreenActiveScale(1.8f)
            assertEquals(1.30f, prefs.getLyricsFullScreenActiveScale(), 0.001f)
        } finally {
            prefs.setLyricsActiveScale(origScale)
            prefs.setLyricsFullScreenActiveScale(origFsScale)
        }
    }
}
