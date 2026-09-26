import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.alananasss.kittytune.ui.profile.AboutDialog
import com.alananasss.kittytune.ui.profile.CreditsScreen
import org.junit.Test
import kotlin.test.assertEquals

class CreditsNavigationTest {
    @Test
    fun testNavigationToCredits() {
        val testClassLoader = javaClass.classLoader
        javax.swing.SwingUtilities.invokeAndWait {
            Thread.currentThread().contextClassLoader = testClassLoader
            var currentRoute: String? = null
            var creditsNavLambda: (() -> Unit)? = null
            val scene = ImageComposeScene(width = 1000, height = 800, density = Density(1f)) {
                MaterialTheme {
                    val navController = rememberNavController()
                    val entry by navController.currentBackStackEntryAsState()
                    currentRoute = entry?.destination?.route

                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {}
                        composable("credits") {
                            CreditsScreen(onBackClick = { navController.popBackStack() })
                        }
                    }

                    var showAbout by remember { mutableStateOf(true) }
                    creditsNavLambda = {
                        showAbout = false
                        navController.navigate("credits")
                    }

                    if (showAbout) {
                        AboutDialog(
                            onDismiss = { showAbout = false },
                            onCreditsClick = { creditsNavLambda?.invoke() }
                        )
                    }
                }
            }
            scene.render()
            assertEquals("home", currentRoute)

            // Trigger the credits navigation
            creditsNavLambda?.invoke()
            scene.render()
            assertEquals("credits", currentRoute)
        }
    }
}
