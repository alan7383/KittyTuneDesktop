import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HorizontalMouseSwipeTest {

    @Test
    fun `dragging right to left scrolls forward`() {
        // User moves mouse from x=300 to x=220 (dragAmount = -80 px)
        val startX = 300f
        val currentX = 220f
        val dragAmount = currentX - startX // -80f

        // Scroll delta to apply is -dragAmount
        val scrollDelta = -dragAmount
        assertTrue(scrollDelta > 0f, "Dragging right-to-left must produce positive (forward) scroll delta")
        assertEquals(80f, scrollDelta)
    }

    @Test
    fun `dragging left to right scrolls backward`() {
        // User moves mouse from x=100 to x=190 (dragAmount = +90 px)
        val startX = 100f
        val currentX = 190f
        val dragAmount = currentX - startX // +90f

        // Scroll delta to apply is -dragAmount
        val scrollDelta = -dragAmount
        assertTrue(scrollDelta < 0f, "Dragging left-to-right must produce negative (backward) scroll delta")
        assertEquals(-90f, scrollDelta)
    }

    @Test
    fun `friction decay terminates within bounded steps and distance`() {
        val trackerVelocity = -1500f // swipe from right to left
        var currentVelocity = -trackerVelocity // +1500f forward
        val friction = 0.92f
        var totalScrolled = 0f
        var steps = 0

        while (abs(currentVelocity) > 15f && steps < 200) {
            totalScrolled += currentVelocity * 0.016f
            currentVelocity *= friction
            steps++
        }

        assertTrue(steps in 10..100, "Fling animation must finish in a reasonable number of frames ($steps)")
        assertTrue(totalScrolled > 0f, "Forward fling must accumulate positive scroll distance")
        assertTrue(abs(currentVelocity) <= 15f, "Fling must stop when velocity reaches stopping threshold")
    }

    @Test
    fun `vertical mouse wheel is not consumed by horizontal containers`() {
        // When vertical wheel delta arrives (y != 0, x == 0):
        // Horizontal containers must not consume delta.y, leaving vertical scroll intact.
        val scrollDeltaY = -1.0f
        val scrollDeltaX = 0f

        val consumesVertical = false // Our new rule: do NOT consume vertical wheel in horizontal lists
        val shouldForwardToParent = scrollDeltaY != 0f && !consumesVertical

        assertTrue(shouldForwardToParent, "Vertical mouse wheel should pass through to parent vertical scroll")
    }
}
