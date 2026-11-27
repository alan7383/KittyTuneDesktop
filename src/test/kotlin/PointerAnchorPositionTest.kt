import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.alananasss.kittytune.ui.common.AtPointPositionProvider
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PointerAnchorPositionTest {

    @Test
    fun opensRightwardClampedWhenReasonableSpaceExistsOnRight() {
        var resolvedLeft: Boolean? = null
        val provider = AtPointPositionProvider(
            anchor = IntOffset(920, 420),
            onSideResolved = { resolvedLeft = it },
        )

        val pos = provider.calculatePosition(
            anchorBounds = IntRect(0, 0, 1280, 800),
            windowSize = IntSize(1280, 800),
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = IntSize(412, 320),
        )

        assertEquals(868, pos.x)
        assertEquals(420, pos.y)
        assertEquals(false, resolvedLeft)
    }

    @Test
    fun flipsLeftwardWhenClickedNearRightEdge() {
        var resolvedLeft: Boolean? = null
        val provider = AtPointPositionProvider(
            anchor = IntOffset(1240, 420),
            onSideResolved = { resolvedLeft = it },
        )

        val pos = provider.calculatePosition(
            anchorBounds = IntRect(0, 0, 1280, 800),
            windowSize = IntSize(1280, 800),
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = IntSize(412, 320),
        )

        assertEquals(828, pos.x)
        assertEquals(420, pos.y)
        assertEquals(true, resolvedLeft)
    }

    @Test
    fun opensAtExactAnchorWhenEnoughSpaceExistsOnRight() {
        var resolvedLeft = true
        val provider = AtPointPositionProvider(
            anchor = IntOffset(120, 100),
            onSideResolved = { resolvedLeft = it },
        )

        val pos = provider.calculatePosition(
            anchorBounds = IntRect(0, 0, 1280, 800),
            windowSize = IntSize(1280, 800),
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = IntSize(412, 320),
        )

        assertEquals(120, pos.x)
        assertEquals(100, pos.y)
        assertFalse(resolvedLeft)
    }
}
