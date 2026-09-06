import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.ui.main.PanelLyricsStyle
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PanelLyricsResponsiveTest {

    @Test
    fun compact_panel_lyrics_style_centers_active_line_at_midpoint() {
        val compact = PanelLyricsStyle.Compact
        assertEquals(0.50f, compact.anchorFraction, "Compact mode must anchor the active line at the exact vertical middle")
        assertEquals(0.40f, compact.topInsetFraction)
        assertEquals(0.40f, compact.tailFraction)
        assertTrue(compact.lineSpacing <= 4.dp, "Compact mode must use tighter line spacing to fit 5 lines comfortably")
    }

    @Test
    fun standard_panel_lyrics_style_preserved_for_full_height() {
        val standard = PanelLyricsStyle.Panel
        assertEquals(0.42f, standard.anchorFraction)
        assertEquals(0.03f, standard.topInsetFraction)
    }
}
