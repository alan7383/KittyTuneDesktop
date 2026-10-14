import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.ui.theme.withUiScale
import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopDensityTest {
    @Test
    fun textAndControlsZoomTogetherAtEveryDesktopDpi() {
        for (monitorScale in listOf(1f, 1.25f, 1.5f, 2f)) {
            for (zoom in listOf(0.7f, 1f, 1.1f, 1.3f)) {
                val density = Density(monitorScale).withUiScale(zoom)
                with(density) {
                    assertEquals(16.dp.toPx(), 16.sp.toPx(), 0.001f)
                    assertEquals(16f * monitorScale * zoom, 16.sp.toPx(), 0.001f)
                }
            }
        }
    }

    @Test
    fun accessibilityFontScaleIsPreservedWhileMovingBetweenMonitors() {
        for (monitorScale in listOf(1f, 1.25f, 1.5f, 2f)) {
            val density = Density(monitorScale, fontScale = 1.2f).withUiScale(1.3f)
            with(density) { assertEquals(16.dp.toPx() * 1.2f, 16.sp.toPx(), 0.001f) }
        }
    }
}
