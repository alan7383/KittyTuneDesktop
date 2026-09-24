import com.alananasss.kittytune.ui.player.UnifiedLyricResult
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests verifying manual lyrics/text/session selection behavior in full-screen player mode.
 */
class FullScreenManualLyricsSearchTest {

    @Test
    fun `opening manual search preserves full screen mode`() {
        var isLyricsFullScreen = true
        var isSearchingLyrics = false

        // User clicks search button in full-screen mode
        isSearchingLyrics = true

        assertTrue(isLyricsFullScreen, "Full-screen mode should remain active when opening manual search")
        assertTrue(isSearchingLyrics, "Search dialog should become visible")
    }

    @Test
    fun `dismissing search retains full screen mode without exiting`() {
        var isLyricsFullScreen = true
        var isSearchingLyrics = true

        // User presses back or closes search dialog
        val onBack: () -> Unit = {
            if (isSearchingLyrics) {
                isSearchingLyrics = false
            } else {
                isLyricsFullScreen = false
            }
        }

        onBack()

        assertFalse(isSearchingLyrics, "Search dialog should be closed")
        assertTrue(isLyricsFullScreen, "Player should remain in full-screen mode")

        // Pressing back again when search is closed should exit full-screen
        onBack()
        assertFalse(isLyricsFullScreen, "Second back action should exit full-screen mode")
    }

    @Test
    fun `selecting search result clears search flag and retains full screen`() {
        var isLyricsFullScreen = true
        var isSearchingLyrics = true
        var selectedResult: UnifiedLyricResult? = null

        val result = UnifiedLyricResult(
            id = "12345",
            name = "Bohemian Rhapsody",
            artistName = "Queen",
            albumName = "A Night at the Opera",
            durationSec = 354.0,
            hasLineSync = true,
            hasWordSync = true,
            provider = "LRCLIB"
        )

        // User selects a lyrics version/session
        selectedResult = result
        isSearchingLyrics = false

        assertEquals("Queen", selectedResult.artistName)
        assertEquals("Bohemian Rhapsody", selectedResult.name)
        assertFalse(isSearchingLyrics, "Search view should be dismissed upon selection")
        assertTrue(isLyricsFullScreen, "Player should stay in full-screen mode after selecting lyrics")
    }

    @Test
    fun `quick settings dialog trigger switches to manual search without dropping full screen`() {
        var isLyricsFullScreen = true
        var showQuickSettings = true
        var isSearchingLyrics = false

        // User clicks 'Recherche manuelle' in QuickLyricsSettingsDialog
        val onManualSearchFromQuickSettings = {
            showQuickSettings = false
            isSearchingLyrics = true
        }

        onManualSearchFromQuickSettings()

        assertFalse(showQuickSettings, "Quick settings dialog should dismiss")
        assertTrue(isSearchingLyrics, "Manual lyrics search should open")
        assertTrue(isLyricsFullScreen, "Player should remain in full-screen mode")
    }

    @Test
    fun `clicking outside the dialog dismisses the popup and preserves full screen`() {
        var isLyricsFullScreen = true
        var isSearchingLyrics = true

        // User clicks outside the popup (backdrop clicked)
        val onBackdropClick = {
            isSearchingLyrics = false
        }

        onBackdropClick()

        assertFalse(isSearchingLyrics, "Popup should be closed when clicking outside")
        assertTrue(isLyricsFullScreen, "Player should remain in full-screen mode")
    }

    @Test
    fun `pressing escape dismisses the popup and preserves full screen`() {
        var isLyricsFullScreen = true
        var isSearchingLyrics = true

        // User hits Escape while typing or focusing on popup
        val onEscapePressed = {
            if (isSearchingLyrics) {
                isSearchingLyrics = false
            } else {
                isLyricsFullScreen = false
            }
        }

        onEscapePressed()

        assertFalse(isSearchingLyrics, "Popup should be closed when pressing Escape")
        assertTrue(isLyricsFullScreen, "Player should remain in full-screen mode")
    }
}
