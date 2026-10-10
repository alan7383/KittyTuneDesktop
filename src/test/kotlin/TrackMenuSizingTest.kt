import org.junit.Test
import java.io.File
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Ensures track and playlist options menu tiles maintain uniform sizing and layout.
 *
 * Issue #5: When clicking on a track, options menu tiles previously differed in height
 * because 1-line text tiles were shorter (~74dp) than 2-line text tiles (~90dp), and
 * the download tile icon was 30dp instead of 26dp.
 *
 * All tiles must have a uniform fixed height (88.dp), matching 26.dp icon slots,
 * centered text containers with maxLines=2, and uniform 8.dp horizontal and vertical spacing.
 */
class TrackMenuSizingTest {

    private fun loadTrackOptionsOverlaysSource(): String {
        val file = File("src/main/kotlin/com/alananasss/kittytune/ui/main/TrackOptionsOverlays.kt")
        assertTrue(file.exists(), "TrackOptionsOverlays.kt must exist")
        return file.readText()
    }

    @Test
    fun testMenuTilesHaveUniformFixedDimensions() {
        val source = loadTrackOptionsOverlaysSource()

        // Tile Box container must have fixed uniform height (88.dp) and fillMaxWidth()
        assertTrue(
            source.contains("Box(modifier = Modifier.fillMaxWidth().height(88.dp))"),
            "MenuTile container must have Modifier.fillMaxWidth().height(88.dp) for uniform sizing"
        )

        // Inner Column must fillMaxSize() to match the outer Box constraints
        assertTrue(
            source.contains("modifier = Modifier\n                .fillMaxSize()") ||
            source.contains(".fillMaxSize()"),
            "MenuTile Column must fillMaxSize() to occupy the entire tile bounds"
        )
    }

    @Test
    fun testMenuIconsHaveUniformSize() {
        val source = loadTrackOptionsOverlaysSource()

        // Icon slot must be enclosed in a 26.dp Box
        assertTrue(
            source.contains("Box(modifier = Modifier.size(26.dp), contentAlignment = Alignment.Center)"),
            "MenuTile icon slot must be 26.dp by 26.dp and centered"
        )

        // Download tile iconContent must use 26.dp (not 30.dp) to match all other tiles
        assertFalse(
            source.contains("modifier = Modifier.size(30.dp)"),
            "Download icon should not use 30.dp, but 26.dp to match all other menu icons"
        )
    }

    @Test
    fun testGridHasUniformSpacingInBothAxes() {
        val source = loadTrackOptionsOverlaysSource()

        // LazyVerticalGrid must have 8.dp spacing both vertically and horizontally
        assertTrue(
            source.contains("verticalArrangement = Arrangement.spacedBy(8.dp)"),
            "LazyVerticalGrid should have 8.dp vertical arrangement to match horizontal spacing"
        )
        assertTrue(
            source.contains("horizontalArrangement = Arrangement.spacedBy(8.dp)"),
            "LazyVerticalGrid should have 8.dp horizontal arrangement"
        )
    }

    @Test
    fun testMenuTileTextSlotIsCentered() {
        val source = loadTrackOptionsOverlaysSource()

        // Text slot must use weight(1f) and center alignment
        assertTrue(
            source.contains("modifier = Modifier.fillMaxWidth().weight(1f)"),
            "MenuTile label should occupy the remaining vertical slot using weight(1f)"
        )
        assertTrue(
            source.contains("contentAlignment = Alignment.Center"),
            "MenuTile label should be centered inside its allocated slot"
        )
    }
}
