import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.use
import com.alananasss.kittytune.core.DialogExit
import com.alananasss.kittytune.core.DialogExitHost
import com.alananasss.kittytune.core.EscapableAlertDialog
import org.jetbrains.skia.Image
import org.junit.Test
import kotlin.test.assertTrue

/** Closed dialogs fade out instead of vanishing (tester's list, 1.7). */
class DialogExitTest {

    @Test
    fun `a closed dialog is still on screen for a moment, then gone`() {
        val isOpen = mutableStateOf(true)
        ImageComposeScene(width = 400, height = 400) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                if (isOpen.value) {
                    EscapableAlertDialog(
                        onDismissRequest = { isOpen.value = false },
                        title = { Text("Title") },
                        confirmButton = {},
                        containerColor = Color.Red,
                    )
                }
                DialogExitHost()
            }
        }.use { scene ->
            var now = 0L
            fun frame(): Image = scene.render(now).also { now += FRAME_NS }

            repeat(30) { frame() }
            assertTrue(redPixels(frame()) > 1_000, "the dialog draws")

            isOpen.value = false
            assertTrue(redPixels(frame()) > 1_000, "just after closing it is still visible")

            repeat(30) { frame() }
            assertTrue(redPixels(frame()) < 50, "after the animation nothing is left")
            assertTrue(DialogExit.ghosts.isEmpty(), "and nothing is kept")
        }
    }

    private fun redPixels(image: Image): Int {
        val bitmap = org.jetbrains.skia.Bitmap.makeFromImage(image)
        var count = 0
        for (y in 0 until bitmap.height step 2) {
            for (x in 0 until bitmap.width step 2) {
                val c = bitmap.getColor(x, y)
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                if (r > 150 && g < 140 && b < 140) count++
            }
        }
        return count
    }

    private companion object {
        const val FRAME_NS = 16_000_000L
    }
}
