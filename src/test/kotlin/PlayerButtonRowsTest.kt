import org.junit.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The player-bar button rows, and the menu-tile rows beside them.
 *
 * These were a hand-rolled row: a clickable `Surface` with a `Switch` inside it, its own icon chip,
 * its own switch. Three things followed from that. The marks were nearly invisible, because the chip
 * was `primaryContainer` and the glyph `primary` — one hue at two lightnesses. The switches had no
 * check in the thumb while every other switch in the app did. And the rows felt inert, because the
 * surface and the switch each had their own interaction source, so a press lit one small part of a
 * row instead of the row.
 *
 * The fix was not to tune the copy of the row. It was to stop having one: these are `SettingsItem`
 * now, the same control as every other row in the settings, which is what "it should behave like
 * the others" has to mean if it is to stay true.
 */
class PlayerButtonRowsTest {

    private fun design(): String {
        val file = File("src/main/kotlin/com/alananasss/kittytune/ui/profile/PlayerDesignScreen.kt")
        assertTrue(file.exists(), "PlayerDesignScreen.kt should exist")
        return file.readText()
    }

    private fun settingsItem(): String {
        val text = File("src/main/kotlin/com/alananasss/kittytune/ui/common/SettingsComponents.kt").readText()
        val start = text.indexOf("fun SettingsItem(")
        assertTrue(start >= 0, "SettingsItem should exist")
        return text.substring(start, text.indexOf("\nfun ", start + 10).takeIf { it > 0 } ?: (start + 6000))
    }

    // ── The rows are the shared row ──

    @Test
    fun testBothListsUseTheSharedSettingsRow() {
        val design = design()
        assertFalse(
            design.contains("ButtonToggleRow"),
            "The hand-rolled row is gone; a second copy of a settings row is how these drifted",
        )
        // The player-bar buttons and the menu tiles, both.
        assertTrue(
            design.countOf("SettingsItem(") >= 2,
            "Both lists go through the shared row",
        )
        assertTrue(
            design.contains("hasSwitch = true") && design.contains("switchState = item.enabled"),
            "…as switch rows, with their state carried through",
        )
    }

    @Test
    fun testTheRowsAreWiredAsSwitchesNotLinks() {
        val design = design()
        assertTrue(
            design.contains("onSwitchChange = { onToggle(item.key, it) }"),
            "The player-bar list toggles its button through the switch callback",
        )
        assertTrue(
            design.contains("prefs.setHiddenMenuTiles(menu, hidden)"),
            "And the menu-tile list persists its own change",
        )
        // A switch row must not also be a link row: SettingsItem gives onClick precedence, and a
        // row with both would navigate instead of toggling.
        assertFalse(
            Regex("onClick\\s*=").containsMatchIn(design.substringAfter("SettingsItem(").take(600)),
            "No onClick alongside the switch, or the row would navigate rather than toggle",
        )
    }

    // ── Which is only true if the shared row really is one control ──

    @Test
    fun testTheSharedRowSharesOneInteractionSource() {
        val body = settingsItem()
        assertTrue(
            body.contains("val interactionSource = remember { MutableInteractionSource() }"),
            "One interaction source for the row",
        )
        assertTrue(
            body.countOf("interactionSource = interactionSource") >= 2,
            "…given to both the card and the switch, which is what merges their feedback into one " +
                "control. Two sources meant a press lit the switch but not the row it sat in.",
        )
    }

    @Test
    fun testTheSharedRowIsTheWholeClickTarget() {
        val body = settingsItem()
        assertTrue(
            body.contains("onClick = { onToggleOrClick() }") &&
                body.contains("enabled = onClick != null || hasSwitch"),
            "A switch row is enabled and the card is what you press, anywhere on it",
        )
        assertTrue(
            body.contains("if (hasSwitch && onSwitchChange != null)"),
            "…and the press toggles rather than navigating",
        )
    }

    @Test
    fun testTheSharedRowCarriesACheckInTheThumb() {
        val body = settingsItem()
        assertTrue(body.contains("SettingsSwitch("), "The shared switch, not a bare one")
        val shared = File("src/main/kotlin/com/alananasss/kittytune/ui/common/SettingsComponents.kt").readText()
        val start = shared.indexOf("fun SettingsSwitch(")
        val switch = shared.substring(start, shared.indexOf("\n}\n", start))
        assertTrue(switch.contains("thumbContent"))
        assertTrue(
            switch.contains("Icons.Rounded.Check") && switch.contains("Icons.Rounded.Close"),
            "A check when on and a cross when off, so the state does not rely on colour alone",
        )
    }

    @Test
    fun testTheSharedRowDrainsItsMarkInTheToneItSitsOn() {
        val body = settingsItem()
        assertTrue(
            body.contains("color = MaterialTheme.colorScheme.secondaryContainer") &&
                body.contains("tint = MaterialTheme.colorScheme.onSecondaryContainer"),
            "The chip and its mark are one tonal pair",
        )
        assertFalse(
            Regex("tint = MaterialTheme\\.colorScheme\\.primary\\b").containsMatchIn(body),
            "primary on secondaryContainer is the pairing that made the old chips unreadable",
        )
    }

    // ── The glyphs are the ones the bar actually shows ──

    @Test
    fun testTheGlyphsMatchThePlayerBar() {
        val design = design()
        val bar = File("src/main/kotlin/com/alananasss/kittytune/ui/main/PlayerBar.kt").readText()

        // The point of the list is to say which buttons are shown, so the mark has to be the mark
        // that button carries. The lyrics one is a file rather than a Material icon, which is why
        // these are painters and not vectors.
        assertTrue(
            design.contains("""mark = painterResource("icons/lyrics.svg")"""),
            "The bar draws its lyrics button from a file, so the list must as well",
        )
        assertTrue(File("src/main/resources/icons/lyrics.svg").exists())
        assertTrue(bar.contains("icons/lyrics.svg"), "The bar really does draw it from there")

        for (icon in listOf(
            "Icons.Filled.Favorite", "Icons.Outlined.Tune", "Icons.Outlined.QueueMusic",
            "Icons.Filled.Shuffle", "Icons.Filled.Repeat", "Icons.Rounded.PictureInPictureAlt",
        )) {
            assertTrue(design.contains("rememberVectorPainter($icon)"), "The list shows $icon")
            assertTrue(bar.contains(icon), "…and the bar is what draws $icon")
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

private fun String.countOf(needle: String): Int = split(needle).size - 1
