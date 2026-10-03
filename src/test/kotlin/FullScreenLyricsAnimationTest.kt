import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

class FullScreenLyricsAnimationTest {

    @Test
    fun `full player implements smooth animations for all lyrics variants`() {
        val source = File("src/main/kotlin/com/alananasss/kittytune/ui/player/FullPlayer.kt").readText()

        // 1. LYRICS_RIGHT and LYRICS_LEFT (CoverBesideLyrics)
        assertTrue(
            source.contains("label = \"lyricsShare\""),
            "lyricsShare must be animated via animateFloatAsState",
        )
        assertTrue(
            source.contains("FastOutSlowInEasing.transform(progress)"),
            "CoverBesideLyrics words must use smooth alpha easing on open",
        )

        // 2. LYRICS_CENTRED
        assertTrue(
            source.contains("label = \"centredLyrics\""),
            "CentredLyricsLayout must use AnimatedContent for transitions",
        )
        assertTrue(
            source.contains("slideInVertically"),
            "CentredLyricsLayout must include vertical slide animation",
        )
        assertTrue(
            source.contains("slideOutVertically"),
            "CentredLyricsLayout must include vertical slide out animation on close",
        )

        // 3. COVER_AND_LINE
        assertTrue(
            source.contains("label = \"lineRoomProgress\""),
            "CoverColumn must animate lineRoom dynamically to smoothly resize cover",
        )
        assertTrue(
            source.contains("expandVertically"),
            "CoverColumn must expandVertically when lyrics line appears",
        )
        assertTrue(
            source.contains("shrinkVertically"),
            "CoverColumn must shrinkVertically when lyrics line disappears",
        )

        // 4. Portrait mode
        assertTrue(
            source.contains("label = \"portraitCoverLyricsCrossfade\""),
            "Portrait mode must use AnimatedContent with smooth transitionSpec",
        )
    }
}
