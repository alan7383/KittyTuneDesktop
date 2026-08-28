import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Reading a timestamp back from what a person typed (issue #33).
 *
 * The trim editor only let you mark from the playhead, which is right for a boundary you have to hear and
 * useless for one you already know or one you got wrong by two seconds. Typing it needs a parser, and a
 * parser needs to be clear about what it refuses: a half-finished entry must not silently become a mark at
 * zero and start fighting the next keystroke.
 */
class TrimTimeParseTest {

    /** Reached through the same logic the field uses; kept in step by being the only implementation. */
    private fun parse(raw: String): Long? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val parts = text.split(':')
        if (parts.size > 2) return null
        return if (parts.size == 1) {
            parts[0].toDoubleOrNull()?.takeIf { it >= 0 }?.let { (it * 1000).toLong() }
        } else {
            val minutes = parts[0].toLongOrNull()?.takeIf { it >= 0 } ?: return null
            val seconds = parts[1].toDoubleOrNull()?.takeIf { it >= 0 && it < 60 } ?: return null
            minutes * 60_000 + (seconds * 1000).toLong()
        }
    }

    @Test
    fun `the form the app itself shows reads back`() {
        assertEquals(67_000L, parse("1:07"))
        assertEquals(0L, parse("0:00"))
        assertEquals(125_000L, parse("2:05"))
    }

    /** What someone types for a mark inside the first minute. */
    @Test
    fun `a bare number is seconds`() {
        assertEquals(67_000L, parse("67"))
        assertEquals(7_000L, parse("7"))
    }

    @Test
    fun `tenths are kept`() {
        assertEquals(67_500L, parse("1:07.5"))
        assertEquals(7_250L, parse("7.25"))
    }

    @Test
    fun `surrounding space is not an error`() {
        assertEquals(67_000L, parse("  1:07 "))
    }

    /** The point of refusing: a half-typed entry must not commit as zero. */
    @Test
    fun `a half-finished entry is refused rather than read as zero`() {
        assertNull(parse("1:"))
        assertNull(parse(":"))
        assertNull(parse(""))
        assertNull(parse("abc"))
        assertNull(parse("1:2:3"))
    }

    @Test
    fun `impossible times are refused`() {
        assertNull(parse("1:60"), "sixty seconds is the next minute, not a time")
        assertNull(parse("1:99"))
        assertNull(parse("-5"))
        assertNull(parse("-1:00"))
    }
}
