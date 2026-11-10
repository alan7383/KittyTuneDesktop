import com.alananasss.kittytune.ui.pet.PetInAppGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The cat kept inside the app's window: its place is a fraction of the room left to move in, and
 * dragging it must stop at the window's edges instead of letting it escape the way the floating
 * desktop window (or the mini player) can sit over another app.
 */
class PetInAppPlacementTest {

    @Test
    fun draggingStopsAtTheWindowEdges() {
        val free = 600f
        // A drag past the right edge holds at the edge rather than going outside.
        assertEquals(1f, PetInAppGeometry.dragFraction(0.9f, 500f, free), 0.0001f)
        // And past the left one.
        assertEquals(0f, PetInAppGeometry.dragFraction(0.1f, -500f, free), 0.0001f)
        // A drag that stays inside moves by exactly its share of the free room.
        assertEquals(0.5f, PetInAppGeometry.dragFraction(0.25f, 150f, free), 0.0001f)
    }

    @Test
    fun offsetsFollowTheFraction() {
        assertEquals(0, PetInAppGeometry.offsetPx(0f, 400f))
        assertEquals(300, PetInAppGeometry.offsetPx(0.75f, 400f))
        assertEquals(400, PetInAppGeometry.offsetPx(1f, 400f))
        // A stale fraction left over from a bigger window never places the cat outside.
        assertEquals(400, PetInAppGeometry.offsetPx(3f, 400f))
        assertEquals(0, PetInAppGeometry.offsetPx(-2f, 400f))
    }

    @Test
    fun aWindowSmallerThanTheCatDoesNotDivideByZero() {
        assertEquals(0, PetInAppGeometry.offsetPx(1f, 0f))
        assertEquals(0.5f, PetInAppGeometry.dragFraction(0.5f, 120f, 0f), 0.0001f)
        // An impossible fraction is still normalized, never used as-is.
        assertEquals(1f, PetInAppGeometry.dragFraction(4f, 0f, 0f), 0.0001f)
    }
}
