import org.junit.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The player-bar button rows in Interface -> Player design.
 *
 * Three things were wrong with them, all of them visible on screen rather than in a failure:
 * the marks were nearly invisible, the switches had no check in the thumb while every other switch
 * in the app did, and a press could toggle nothing at all.
 */
class PlayerButtonRowsTest {

    private fun design(): String {
        val file = File("src/main/kotlin/com/alananasss/kittytune/ui/profile/PlayerDesignScreen.kt")
        assertTrue(file.exists(), "PlayerDesignScreen.kt should exist")
        return file.readText()
    }

    private fun bar(): String {
        val file = File("src/main/kotlin/com/alananasss/kittytune/ui/main/PlayerBar.kt")
        assertTrue(file.exists(), "PlayerBar.kt should exist")
        return file.readText()
    }

    private fun row(): String {
        val text = design()
        val start = text.indexOf("private fun ButtonToggleRow(")
        assertTrue(start >= 0, "ButtonToggleRow should exist")
        return text.substring(start, text.indexOf("\n}\n", start))
    }

    // ── The marks were barely there ──

    @Test
    fun testTheChipAndItsMarkAreATonalPair() {
        val body = row()

        assertTrue(
            body.contains("color = if (item.enabled) MaterialTheme.colorScheme.primaryContainer"),
            "The chip keeps its tonal fill",
        )
        assertTrue(
            body.contains("MaterialTheme.colorScheme.onPrimaryContainer"),
            "…and the mark is drawn in the colour meant to sit on it. It was `primary` on " +
                "`primaryContainer` — the same hue at two lightnesses, which is why the glyph " +
                "barely registered against the circle behind it.",
        )
        assertFalse(
            Regex("""tint = if \(item\.enabled\) MaterialTheme\.colorScheme\.primary\b""").containsMatchIn(body),
            "Primary on primaryContainer is the pairing that caused this",
        )
    }

    @Test
    fun testTheMarkIsBigEnoughToRead() {
        // It was 20 dp in a 38 dp circle. At that size a Material glyph's thin strokes disappear
        // against a tinted fill before contrast is even considered.
        assertTrue(row().contains("modifier = Modifier.size(22.dp)"), "22 dp, in a 40 dp chip")
        assertTrue(row().contains("modifier = Modifier.size(40.dp)"))
    }

    // ── The switches were the wrong switch ──

    @Test
    fun testTheRowsUseTheSharedSwitchSoTheyGetItsCheck() {
        val body = row()

        assertTrue(
            body.contains("SettingsSwitch("),
            "The rows must use the shared switch, which puts a check in the thumb",
        )
        assertFalse(
            Regex("""(?<!\.)\bSwitch\(\s*\n\s*checked = item\.enabled""").containsMatchIn(body),
            "Not a bare Material Switch: that is the one without the check",
        )
    }

    @Test
    fun testTheSharedSwitchCarriesTheCheck() {
        val shared = File("src/main/kotlin/com/alananasss/kittytune/ui/common/SettingsComponents.kt")
            .readText()
        val start = shared.indexOf("fun SettingsSwitch(")
        val body = shared.substring(start, shared.indexOf("\n}\n", start))
        assertTrue(body.contains("thumbContent"))
        assertTrue(
            body.contains("Icons.Rounded.Check") && body.contains("Icons.Rounded.Close"),
            "A check when on and a cross when off, so the state does not rely on colour alone",
        )
    }

    // ── A press could do nothing ──

    @Test
    fun testTheRowIsTheOnlyClickTarget() {
        val body = row()

        assertTrue(
            body.contains("Surface(\n        onClick = { onToggle(!item.enabled) },"),
            "The whole row toggles, which is the Material pattern for a list item",
        )
        // The switch is shown and not handled. Both firing would write the same value twice and the
        // switch would look like it had ignored the press.
        assertTrue(
            body.contains("onCheckedChange = null"),
            "The switch must not carry its own callback inside a clickable row",
        )
        assertTrue(
            body.contains("enabled = true"),
            "…and must be told it is enabled, because Material derives that from the callback " +
                "being present and would otherwise grey it out",
        )
    }

    @Test
    fun testTheSharedSwitchCanBeShownWithoutHandlingClicks() {
        val shared = File("src/main/kotlin/com/alananasss/kittytune/ui/common/SettingsComponents.kt")
            .readText()
        val start = shared.indexOf("fun SettingsSwitch(")
        val body = shared.substring(start, shared.indexOf("\n}\n", start))
        assertTrue(
            body.contains("enabled: Boolean = true"),
            "The switch needs `enabled` separate from the callback, or this arrangement is impossible",
        )
        assertTrue(body.contains("enabled = enabled"))
    }

    // ── The glyphs are the ones the bar actually shows ──

    @Test
    fun testTheGlyphsMatchThePlayerBar() {
        val design = design()
        val bar = bar()

        // The point of the list is to say which buttons are shown, so the mark has to be the mark
        // that button carries. The lyrics one is a file, not a Material icon, which is why these are
        // painters and not vectors.
        assertTrue(
            design.contains("""mark = painterResource("icons/lyrics.svg")"""),
            "The bar draws its lyrics button from a file, so the list must as well",
        )
        assertTrue(
            File("src/main/resources/icons/lyrics.svg").exists(),
            "…and that file has to exist, or the row crashes instead of drawing nothing",
        )
        assertTrue(bar.contains("icons/lyrics.svg"), "The bar really does draw it from there")

        for (icon in listOf("Icons.Filled.Favorite", "Icons.Outlined.Tune", "Icons.Outlined.QueueMusic",
                            "Icons.Filled.Shuffle", "Icons.Filled.Repeat",
                            "Icons.Rounded.PictureInPictureAlt")) {
            assertTrue(design.contains("rememberVectorPainter($icon)"), "The list should show $icon")
            assertTrue(bar.contains(icon), "…and the bar should be the one drawing $icon")
        }
    }

    @Test
    fun testTheItemCarriesOneRequiredMark() {
        val design = design()
        val start = design.indexOf("private data class ButtonConfigItem(")
        val decl = design.substring(start, design.indexOf(")", start))
        assertTrue(decl.contains("mark:"))
        assertFalse(decl.contains("icon:"), "One mark, not a second spelling of it")
    }
}
