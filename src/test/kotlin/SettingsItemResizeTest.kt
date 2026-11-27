import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.ui.common.SettingsItem
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsItemResizeTest {
    @Test
    fun rewrappingTheDescriptionMovesTheNextSectionThroughIntermediatePositions() {
        val width = mutableStateOf(720.dp)
        var nextSectionTop = 0f
        val scene = ImageComposeScene(width = 720, height = 300) {
            MaterialTheme {
                Column(Modifier.width(width.value)) {
                    SettingsItem(
                        shape = RoundedCornerShape(24.dp),
                        title = "Expand the sidebar on hover",
                        subtitle = "Automatically open the sidebar when you move the pointer over it while it is collapsed.",
                        hasSwitch = true,
                    )
                    Spacer(Modifier.height(24.dp).onGloballyPositioned { nextSectionTop = it.positionInRoot().y })
                }
            }
        }
        try {
            var time = 0L
            fun renderFrame(): Float {
                scene.render(time).close()
                time += 16_666_667L
                return nextSectionTop
            }

            val wideTop = renderFrame()
            width.value = 400.dp
            val wrapping = List(45) { renderFrame() }
            val narrowTop = wrapping.last()
            assertTrue(narrowTop > wideTop)
            assertTrue(wrapping.any { it > wideTop && it < narrowTop }, "Wrapping positions: $wrapping")

            width.value = 720.dp
            val unwrapping = List(45) { renderFrame() }
            assertEquals(wideTop, unwrapping.last())
            assertTrue(unwrapping.any { it > wideTop && it < narrowTop }, "Unwrapping positions: $unwrapping")
        } finally {
            scene.close()
        }
    }
}
