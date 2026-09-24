import com.alananasss.kittytune.ui.home.HomeViewModel
import java.io.File
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Validates search focusing, dismissing, and clearFocusOnEmptyClick wiring (issue #33 / search focus dismiss).
 */
class SearchFocusDismissTest {

    @Test
    fun `MainTopBar search field has onFocusChanged escapeDismisses and trackTextInput`() {
        val file = File("src/main/kotlin/com/alananasss/kittytune/ui/main/MainTopBar.kt")
        assertTrue(file.exists(), "MainTopBar.kt must exist")
        val content = file.readText()

        assertTrue(content.contains("onFocusChanged"), "MainTopBar must handle onFocusChanged")
        assertTrue(content.contains("escapeDismisses"), "MainTopBar must handle escapeDismisses")
        assertTrue(content.contains("trackTextInput"), "MainTopBar must track text input")
        assertTrue(content.contains("vm.isSearching = true"), "MainTopBar must activate search on focus")
        assertTrue(content.contains("vm.isSearching = false"), "MainTopBar must deactivate search when blank on focus loss")
    }

    @Test
    fun `Sidebar search field has focusLossDismisses and escapeDismisses`() {
        val file = File("src/main/kotlin/com/alananasss/kittytune/ui/main/Sidebar.kt")
        assertTrue(file.exists(), "Sidebar.kt must exist")
        val content = file.readText()

        assertTrue(content.contains("focusLossDismisses(dismiss)"), "Sidebar must dismiss on focus loss")
        assertTrue(content.contains("escapeDismisses(dismiss)"), "Sidebar must dismiss on escape")
    }

    @Test
    fun `MainScreen root Column has clearFocusOnEmptyClick modifier`() {
        val file = File("src/main/kotlin/com/alananasss/kittytune/ui/main/MainScreen.kt")
        assertTrue(file.exists(), "MainScreen.kt must exist")
        val content = file.readText()

        assertTrue(content.contains("clearFocusOnEmptyClick(focusManager)"), "MainScreen root Column must have clearFocusOnEmptyClick")
    }

    @Test
    fun `HomeViewModel clearSearch resets isSearching and searchQuery`() {
        val vm = HomeViewModel(com.alananasss.kittytune.core.Application())
        vm.searchQuery = "test query"
        vm.isSearching = true

        vm.clearSearch()

        assertEquals("", vm.searchQuery)
        assertFalse(vm.isSearching)
    }
}
