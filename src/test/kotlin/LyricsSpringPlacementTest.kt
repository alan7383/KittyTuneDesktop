import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.ui.player.lyrics.accompanist.springPlacement
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.assertTrue

class LyricsSpringPlacementTest {
    @Test
    fun retinaSpringKeepsFractionalPositionsAndSettles() {
        for (hz in listOf(60, 120)) {
            val target = mutableStateOf(0.dp)
            val positions = mutableListOf<Float>()
            var position = Offset.Zero
            val scene = ImageComposeScene(width = 200, height = 400, density = Density(2f)) {
                LookaheadScope {
                    Column {
                        Spacer(Modifier.height(target.value))
                        Box(Modifier.springPlacement(this@LookaheadScope, "line", false, 180f)
                            .onGloballyPositioned { position = it.positionInRoot() }
                            .size(30.dp).background(Color.White))
                    }
                }
            }
            try {
                var time = 0L
                repeat(3) { scene.render(time).close(); time += 1_000_000_000L / hz }
                target.value = 80.dp
                repeat(hz * 2) {
                    scene.render(time).close()
                    positions += position.y
                    time += 1_000_000_000L / hz
                }
                assertTrue(positions.any { abs(it - it.roundToInt()) > 0.05f }, "Spring must retain subpixel motion at $hz Hz: $positions")
                assertTrue(abs(positions.last() - 160f) < 1f, "Spring must settle at its Retina target at $hz Hz")
                assertTrue(positions.all { it >= -1f && it <= 162f }, "Spring must stay near its start/target")
            } finally { scene.close() }
        }
    }
}
