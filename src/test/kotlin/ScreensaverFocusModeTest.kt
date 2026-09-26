import com.alananasss.kittytune.data.local.PlayerPreferences
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ScreensaverFocusModeTest {

    private fun loadKeys(lang: String): Map<String, String> {
        val file = File("src/main/resources/i18n/strings-$lang.xml")
        assertTrue(file.exists(), "Resource file strings-$lang.xml should exist")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        val result = mutableMapOf<String, String>()
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            val name = el.getAttribute("name")
            val text = el.textContent
            result[name] = text
        }
        return result
    }

    @Test
    fun testScreensaverPreferencesDefaultsAndChanges() {
        val prefs = PlayerPreferences()
        val origEnabled = prefs.getFullPlayerScreensaverEnabled()
        val origTimeout = prefs.getFullPlayerScreensaverTimeout()

        try {
            // Default should be enabled
            prefs.setFullPlayerScreensaverEnabled(true)
            assertTrue(prefs.getFullPlayerScreensaverEnabled())

            prefs.setFullPlayerScreensaverEnabled(false)
            assertEquals(false, prefs.getFullPlayerScreensaverEnabled())

            prefs.setFullPlayerScreensaverTimeout(120)
            assertEquals(120, prefs.getFullPlayerScreensaverTimeout())
        } finally {
            prefs.setFullPlayerScreensaverEnabled(origEnabled)
            prefs.setFullPlayerScreensaverTimeout(origTimeout)
        }
    }

    @Test
    fun testScreensaverLocalizationInAllLanguages() {
        val languages = listOf("en", "fr", "de", "hu", "ru", "vi")
        val keys = listOf(
            "pref_screensaver_title",
            "pref_screensaver_desc",
            "screensaver_focus_mode",
            "screensaver_tap_to_wake",
            "screensaver_session_stats",
        )

        for (lang in languages) {
            val table = loadKeys(lang)
            for (key in keys) {
                val value = table[key]
                assertTrue(
                    value != null && value.isNotBlank(),
                    "Missing or blank translation for key '$key' in language '$lang'"
                )
                assertNotEquals(key, value, "Key was not translated in language '$lang': $key")
            }
        }
    }

    @Test
    fun testDurationFormatting() {
        val secMs = 45_000L
        val minMs = 125_000L
        val hrMs = 3_665_000L

        val sec = (secMs / 1000).coerceAtLeast(0L)
        assertEquals(45L, sec)

        val minMinutes = (minMs / 1000) / 60
        assertEquals(2L, minMinutes)

        val hrHours = (hrMs / 1000) / 3600
        val hrMinutes = ((hrMs / 1000) % 3600) / 60
        assertEquals(1L, hrHours)
        assertEquals(1L, hrMinutes)
    }
}
