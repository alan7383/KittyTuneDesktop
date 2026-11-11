import androidx.compose.ui.ImageComposeScene
import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.setup.SetupScreen
import com.alananasss.kittytune.ui.theme.KittyTuneTheme
import org.junit.Test
import kotlin.test.assertEquals

class ThemePersistenceTest {
    @Test fun `new installations use the Ocean seed and palette`() {
        val saved = Prefs.flow.value
        try {
            Prefs.remove("key_color")
            Prefs.remove("color_style")
            val prefs = PlayerPreferences()
            assertEquals(0xFF1565C0.toInt(), prefs.getKeyColor())
            assertEquals("TonalSpot", prefs.getColorStyle())
            renderSetup()
            assertEquals(0xFF1565C0.toInt(), prefs.getKeyColor())
            assertEquals("TonalSpot", prefs.getColorStyle())
        } finally { Prefs.restore(saved) }
    }

    @Test fun `returning to setup preserves a chosen color and palette`() {
        val saved = Prefs.flow.value
        try {
            val prefs = PlayerPreferences()
            prefs.setKeyColor(0xFF7E57C2.toInt())
            prefs.setColorStyle("Expressive")
            repeat(2) { renderSetup() }
            assertEquals(0xFF7E57C2.toInt(), prefs.getKeyColor())
            assertEquals("Expressive", prefs.getColorStyle())
        } finally { Prefs.restore(saved) }
    }

    private fun renderSetup() {
        val scene = ImageComposeScene(width = 1000, height = 800) {
            KittyTuneTheme { SetupScreen(onSetupComplete = {}) }
        }
        try {
            repeat(12) { scene.render(it * 16_000_000L).close() }
        } finally { scene.close() }
    }
}
