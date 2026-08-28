import com.alananasss.kittytune.ui.main.HistoryNavigator
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Back and forward on the mouse's side buttons (issue #33).
 *
 * Back is the navigation library's own; forward is not, because Navigation Compose keeps a back stack
 * and nothing else. So the forward list is ours, and the interesting question is not when it grows but
 * when it has to be thrown away: you cannot go forward to a page you have just navigated away from
 * towards somewhere else. That rule is what these tests hold in place.
 */
class HistoryNavigatorTest {

    /** A stack of routes standing in for the navigation host. */
    private val stack = ArrayDeque(listOf("home"))
    private lateinit var nav: HistoryNavigator

    private fun navigator() = HistoryNavigator(
        currentRoute = { stack.lastOrNull() },
        hasPrevious = { stack.size > 1 },
        pop = { if (stack.size > 1) { stack.removeLast(); true } else false },
        go = { stack.addLast(it) },
    ).also { nav = it }

    /** What the composable does when the route changes, for whatever reason. */
    private fun visit(route: String) {
        stack.addLast(route)
        nav.onRouteChanged()
    }

    @Test
    fun `back walks the stack and forward retraces it`() {
        navigator()
        visit("feed")
        visit("explorer")

        nav.back()
        nav.onRouteChanged()
        assertEquals("feed", stack.last())
        assertTrue(nav.canGoForward)

        nav.back()
        nav.onRouteChanged()
        assertEquals("home", stack.last())

        nav.forward()
        nav.onRouteChanged()
        assertEquals("feed", stack.last())

        nav.forward()
        nav.onRouteChanged()
        assertEquals("explorer", stack.last())
        assertFalse(nav.canGoForward)
    }

    @Test
    fun `nothing to go back to is not an error`() {
        navigator()
        assertFalse(nav.canGoBack)
        nav.back()
        assertEquals(listOf("home"), stack.toList())
        assertFalse(nav.canGoForward)
    }

    @Test
    fun `nothing to go forward to is not an error`() {
        navigator()
        visit("feed")
        nav.forward()
        assertEquals("feed", stack.last())
    }

    /** The rule worth protecting: going somewhere new abandons the forward trail. */
    @Test
    fun `navigating somewhere else clears the forward trail`() {
        navigator()
        visit("feed")
        nav.back()
        nav.onRouteChanged()
        assertTrue(nav.canGoForward)

        visit("upload")
        assertFalse(nav.canGoForward, "upload was not on the forward trail")

        nav.forward()
        assertEquals("upload", stack.last())
    }

    @Test
    fun `our own moves do not clear the trail`() {
        navigator()
        visit("feed")
        visit("explorer")
        nav.back(); nav.onRouteChanged()
        nav.back(); nav.onRouteChanged()
        // Two entries were popped, and going back twice must not have discarded either of them.
        assertEquals(2, nav.forwardSize)
    }

    @Test
    fun `the forward trail is bounded`() {
        navigator()
        repeat(50) { visit("route$it") }
        repeat(50) { nav.back(); nav.onRouteChanged() }
        assertTrue(nav.forwardSize <= 32, "expected a bound, got ${nav.forwardSize}")
    }
}
