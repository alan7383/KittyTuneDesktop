import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The settings row's mark.
 *
 * [SettingsItem] is the most-used composable in the app, so its shape matters more than most. It
 * took three ways to give a row a mark — a Material vector, a painter, and a drawable resource path
 * — and whichever matched first won while the rest were dropped without a word. A row handed a
 * resource path that did not exist drew nothing, and looked deliberate doing it.
 *
 * Two of those had no callers. Nothing here can catch that at runtime, so what is pinned is the
 * signature and the single draw path.
 */
class SettingsItemMarkTest {

    private fun components(): String {
        val file = File("src/main/kotlin/com/alananasss/kittytune/ui/common/SettingsComponents.kt")
        assertTrue(file.exists(), "SettingsComponents.kt should exist")
        return file.readText()
    }

    private fun settingsItem(): String {
        val text = components()
        val start = text.indexOf("fun SettingsItem(")
        assertTrue(start >= 0, "SettingsItem should exist")
        return text.substring(start, text.indexOf("\n}\n", start))
    }

    private fun signature(): String {
        val text = components()
        val start = text.indexOf("fun SettingsItem(")
        return text.substring(start, text.indexOf(") {", start))
    }

    @Test
    fun testThereAreTwoWaysInAndNotThree() {
        val sig = signature()

        assertTrue(sig.contains("icon: ImageVector?"), "A Material vector, which is what most rows use")
        assertTrue(
            sig.contains("mark:") && sig.contains("Painter"),
            "A painter, for a brand's own logo — Material has no Discord",
        )
        assertFalse(
            sig.contains("iconRes") || sig.contains("iconPainter"),
            "The third spelling had no callers, and a second name for the same thing is how the " +
                "ambiguity started",
        )
    }

    @Test
    fun testTheMarkIsResolvedOnceAndDrawnOnce() {
        val body = settingsItem()

        assertTrue(
            body.contains("val rowMark = icon?.let"),
            "The mark is decided once, above the card, rather than re-decided while drawing",
        )
        assertTrue(body.contains("?: mark"), "A vector becomes a painter, so both kinds share a path")

        assertEquals(
            1,
            Regex("painter = rowMark").findAll(body).count(),
            "One place the mark is drawn",
        )
        assertFalse(
            Regex("if \\((icon|mark) !=").containsMatchIn(body),
            "Nothing branches on which kind of mark it is",
        )
        // The row also draws a fixed drill-in arrow, and that one is meant to stay a vector. It is
        // the only vector left in here, which is the point: the mark is not one.
        assertEquals(
            1,
            Regex("imageVector =").findAll(body).count(),
            "Only the drill-in arrow comes from a vector",
        )
        assertTrue(body.contains("ArrowForwardIos"), "And it is the arrow")
    }

    @Test
    fun testTheResourcePathIsGoneFromEveryCallSite() {
        val offenders = mutableListOf<String>()
        File("src/main/kotlin").walkTopDown()
            .filter { it.extension == "kt" }
            .forEach { file ->
                file.readText().lines().forEachIndexed { index, line ->
                    // Only a call site counts; SettingsScreen has its own component with its own
                    // resource parameter, and a doc comment mentioning the old name is not one.
                    if (line.matches(Regex("""\s*(iconRes|iconPainter)\s*[:=].*""")) &&
                        !file.path.endsWith("SettingsScreen.kt") &&
                        !file.path.endsWith("ProviderOrderScreen.kt")
                    ) {
                        offenders.add("${file.name}:${index + 1}")
                    }
                }
            }
        assertTrue(
            offenders.isEmpty(),
            "Call sites still using a removed parameter: ${offenders.joinToString(", ")}",
        )
    }

    @Test
    fun testAMarklessRowIsStillAValidRow() {
        // Most rows carry no mark at all — a switch row, a slider row, a plain link — so both
        // parameters have to stay optional. This is the one thing the old signature got right and
        // the reason this is two optional parameters and not one required one.
        val sig = signature()
        assertTrue(sig.contains("icon: ImageVector? = null"))
        assertTrue(Regex("mark:[^,\\n]*Painter\\? = null").containsMatchIn(sig))
    }
}
