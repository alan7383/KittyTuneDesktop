import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.ui.player.lyrics.lyricsWheel
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * One notch of the wheel over a lyrics view moves exactly the configured number of lines (issue #33).
 *
 * Rendered and driven with a real pointer event rather than tested as arithmetic, because the part
 * that can break is not the arithmetic: it is that consuming the event on the Initial pass keeps the
 * list's own built-in wheel step from being added on top. That is an assumption about Compose's
 * internals, so it is worth a test that would notice if a version changed it.
 */
class LyricsWheelStepUiTest {

    private val lineHeight = 20.dp

    private fun scrollOnce(lines: Float): Int {
        var state: LazyListState? = null
        val scene = ImageComposeScene(width = 300, height = 200, density = Density(1f)) {
            MaterialTheme(colorScheme = lightColorScheme()) {
                val listState = rememberLazyListState().also { state = it }
                val scope = rememberCoroutineScope()
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .lyricsWheel(listState, scope, { lines }, {}),
                    ) {
                        items((1..400).toList()) {
                            Box(Modifier.fillMaxWidth().height(lineHeight))
                        }
                    }
                }
            }
        }
        return try {
            scene.render()
            scene.sendPointerEvent(PointerEventType.Scroll, Offset(150f, 100f), Offset(0f, 1f))
            repeat(4) { scene.render() }
            val s = state!!
            s.firstVisibleItemIndex * 20 + s.firstVisibleItemScrollOffset
        } finally {
            scene.close()
        }
    }

    @Test
    fun `one notch moves the configured number of lines`() {
        assertEquals(60, scrollOnce(3f), "3 lines at 20 px each")
        assertEquals(120, scrollOnce(6f), "6 lines at 20 px each")
        assertEquals(20, scrollOnce(1f), "1 line at 20 px")
    }

    @Test
    fun `the list does not also apply its own step`() {
        // If the event were not consumed, the built-in wheel handling would add its own distance on
        // top and the total would not be a whole number of lines.
        val moved = scrollOnce(3f)
        assertTrue(moved % 20 == 0, "expected a whole number of 20 px lines, got $moved")
    }
}
