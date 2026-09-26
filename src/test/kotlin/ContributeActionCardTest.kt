import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The "how to help" cards, and the shape of the one row they all share.
 *
 * Three of the four are Material icons and one — Discord — is a drawable, so the row grew a second
 * way to be given a mark. It took two optional parameters for it, which is a shape the compiler
 * cannot complain about: a caller who passed neither got an empty 44 dp disc and a build that was
 * perfectly happy. There is no runtime symptom to test for here, so what is pinned is the signature.
 */
class ContributeActionCardTest {

    private fun credits(): String {
        val file = File("src/main/kotlin/com/alananasss/kittytune/ui/profile/CreditsScreen.kt")
        assertTrue(file.exists(), "CreditsScreen.kt should exist")
        return file.readText()
    }

    private fun cardSignature(): String {
        val text = credits()
        val start = text.indexOf("private fun ContributeActionCard(")
        assertTrue(start >= 0, "ContributeActionCard should exist")
        return text.substring(start, text.indexOf(") {", start))
    }

    /** The body of the card, up to the first line that is only a closing brace at column zero. */
    private fun cardBody(): String {
        val text = credits()
        val start = text.indexOf("private fun ContributeActionCard(")
        val end = text.indexOf("\n}\n", start)
        return text.substring(start, end)
    }

    @Test
    fun testTheCardTakesExactlyOneMarkAndItIsRequired() {
        val signature = cardSignature()

        assertTrue(
            signature.contains("mark:") && signature.contains("Painter"),
            "The mark must be a single required painter, which covers both a vector and a drawable",
        )
        assertFalse(
            Regex("mark:[^,\\n]*=\\s*null").containsMatchIn(signature),
            "A mark with a default is a mark that can be left out, and then nothing is drawn",
        )
        assertFalse(
            signature.contains("icon:") || signature.contains("iconPainter:"),
            "The two optional icon parameters are what let a card be built with no mark at all",
        )
    }

    @Test
    fun testEveryCardSuppliesItsMark() {
        val text = credits()
        val needle = "ContributeActionCard("
        val sites = mutableListOf<String>()
        var at = text.indexOf(needle)
        while (at >= 0) {
            // The declaration is the one occurrence that is part of a `private fun` line.
            val lineStart = text.lastIndexOf("\n", at) + 1
            val isDeclaration = text.substring(lineStart, at).contains("private fun")
            if (!isDeclaration) sites.add(text.substring(at, (at + 300).coerceAtMost(text.length)))
            at = text.indexOf(needle, at + needle.length)
        }

        assertEquals(4, sites.size, "Four ways to help: Crowdin, Discord, GitHub, Ko-fi")
        sites.forEachIndexed { index, call ->
            assertTrue(
                call.contains("mark = "),
                "Card ${index + 1} supplies no mark: ${call.take(100).replace("\n", " ")}",
            )
        }
    }

    @Test
    fun testTheMarkIsDrawnOnOneUnbranchedPath() {
        val body = cardBody()

        assertEquals(
            1,
            Regex("painter = mark").findAll(body).count(),
            "The mark is drawn once, from the parameter it was given",
        )
        assertFalse(
            Regex("if \\((mark|icon) ").containsMatchIn(body),
            "Nothing should branch on the mark: the two-optional-parameters version did, and a " +
                "caller who passed neither fell through both branches and drew nothing",
        )
        assertFalse(
            body.contains("iconPainter"),
            "There is one way to give a card a mark now, not two",
        )
        // The card also draws a fixed "opens externally" arrow; that one is meant to be a vector,
        // and is the only imageVector left in here.
        assertEquals(
            1,
            Regex("imageVector =").findAll(body).count(),
            "Only the external-link arrow comes from a vector",
        )
        assertTrue(body.contains("OpenInNew"), "And it is the arrow")
    }
}
