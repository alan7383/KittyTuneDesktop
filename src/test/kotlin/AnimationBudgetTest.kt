import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * Guards the choices that keep decorative motion cheap, so a later edit cannot quietly bring the cost back.
 *
 * The animated walkthrough is the one thing a screenshot cannot show, and a regression here is invisible until
 * someone watches the task manager: an `rememberInfiniteTransition` added to one row is enough to have the whole
 * window re-recorded and redrawn at the display's rate, read or not, minimised or not. These assertions are about
 * which mechanism a decoration is built on, which is exactly what changed.
 */
class AnimationBudgetTest {

    private fun mainSources(): List<File> =
        File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun source(path: String): String = File("src/main/kotlin/$path").readText()

    @Test
    fun `no decoration holds the window's own frame clock any more`() {
        val offenders = mainSources()
            .filter { it.readText().contains("rememberInfiniteTransition(") }
            .map { it.path.replace('\\', '/') }
        assertTrue(
            offenders.isEmpty(),
            "Infinite transitions ask for a frame at the display's rate for as long as they are composed, " +
                "hidden or not. Use rememberAmbientLoop instead (see AmbientClock): $offenders",
        )
    }

    @Test
    fun `the ambient ticker parks itself while the window cannot be seen`() {
        val clock = source("com/alananasss/kittytune/ui/common/AmbientClock.kt")
        assertTrue(clock.contains("fun setWindowSeen"), "the clock needs a visibility switch")
        assertTrue(
            clock.contains("while (!windowSeen"),
            "the ticker must refuse to request frames while the window is hidden",
        )
        assertTrue(
            clock.contains("if (!windowSeen) continue"),
            "a window hidden during the sleep must not be sent the frame it was waiting for",
        )
        assertTrue(
            clock.contains("setWindowSeen(seen)"),
            "ProvideAmbientClock must feed the window's own seen state to the ticker",
        )
    }

    @Test
    fun `the ticker hands over its timestamp without waiting for a frame of its own`() {
        val clock = source("com/alananasss/kittytune/ui/common/AmbientClock.kt")
        val drive = clock.substringAfter("internal suspend fun drive()").substringBefore("internal companion object")
        assertTrue(
            !drive.contains("withFrameNanos"),
            "Awaiting a frame before delivering the state change made Compose draw a second frame per tick " +
                "(PresentMon: 2 ms then 28 ms), so the window was still presented at the old rate.",
        )
        assertTrue(
            drive.contains("System.nanoTime()") && drive.contains("broadcast.sendFrame(now)"),
            "The tick must deliver its own timestamp: one tick is one presented frame.",
        )
    }

    @Test
    fun `the liked songs' aura rides the slow pulse and builds its shaders once`() {
        val aura = source("com/alananasss/kittytune/ui/library/LikesVibe.kt")
        assertTrue(aura.contains("rememberAmbientLoop"), "the aura's drift must come from the ambient pulse")
        assertTrue(aura.contains("slow = true"), "a forty second drift gains nothing from the other half of the frames")
        assertTrue(
            aura.contains("drawWithCache"),
            "the blobs' gradients must be cached rather than rebuilt on every frame",
        )
        assertTrue(
            aura.contains("alpha = intensity.value"),
            "the fade-in must be a layer alpha, so it does not rebuild the gradients",
        )
    }

    @Test
    fun `the My Wave card no longer draws its decorative waves`() {
        val card = source("com/alananasss/kittytune/ui/main/MyWaveCard.kt")
        assertTrue(
            !card.contains("WaveBackground") && !card.contains("waveShift"),
            "the card is a plain themed surface now; the drifting waves were removed",
        )
    }

    @Test
    fun `the playhead stops asking for frames while the window is hidden`() {
        val playhead = source("com/alananasss/kittytune/ui/player/slider/SmoothPlayhead.kt")
        assertTrue(
            playhead.contains("LocalWindowSeen"),
            "a hidden window draws no playhead, so the clock must not keep ticking for it",
        )
    }

    @Test
    fun `every window provides its own seen state`() {
        // A window without one would fall back to the default `true` and animate while minimised or covered.
        for (window in listOf(
            "com/alananasss/kittytune/Main.kt",
            "com/alananasss/kittytune/ui/player/mini/MiniLyricsPlayer.kt",
            "com/alananasss/kittytune/ui/pet/PetWindow.kt",
            "com/alananasss/kittytune/ui/common/FloatingMenuWindow.kt",
        )) {
            assertTrue(
                source(window).contains("LocalWindowSeen provides"),
                "$window must provide LocalWindowSeen for its own window",
            )
        }
    }

    @Test
    fun `the looping Lottie background is parked while the window is hidden`() {
        val blob = source("com/alananasss/kittytune/ui/recognition/BlobBackgroundView.kt")
        assertTrue(
            blob.contains("isPlaying = com.alananasss.kittytune.core.LocalWindowSeen.current"),
            "an IterateForever Lottie redraws a vector every frame and must not do it for nobody",
        )
    }
}
