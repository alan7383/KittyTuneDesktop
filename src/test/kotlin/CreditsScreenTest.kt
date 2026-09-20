import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.alananasss.kittytune.ui.profile.CreditsScreen
import org.junit.Test

class CreditsScreenTest {
    @Test
    fun testCreditsScreenRendersWithoutCrash() {
        val scene = ImageComposeScene(width = 1000, height = 800, density = Density(1f)) {
            MaterialTheme {
                CreditsScreen(onBackClick = {})
            }
        }
        scene.render()
    }
}
