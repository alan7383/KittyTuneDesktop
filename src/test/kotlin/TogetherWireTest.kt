import com.alananasss.kittytune.data.together.SharedTrack
import com.alananasss.kittytune.data.together.TogetherMessage
import com.alananasss.kittytune.data.together.TogetherWire
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Shared playlist messages: sealed by the code, readable only with it (issue #66). */
class TogetherWireTest {

    private val track = SharedTrack(42L, "NEW-YORK", "9mice", null, 180_000L, null, "soundcloud", 7L, "9mice")

    @Test
    fun `a message opens with its own code and comes back the same`() {
        val sent = TogetherMessage.PlayNext("member-a", 1_000L, track, "Matvey")
        val opened = TogetherWire.open("ABCD-EFGH", TogetherWire.seal("abcdefgh", sent))
        assertEquals(sent, opened)
    }

    @Test
    fun `another code reads nothing`() {
        val sealed = TogetherWire.seal("ABCDEFGH", TogetherMessage.Hello("member-a", 1L, "Matvey", true))
        assertNull(TogetherWire.open("ABCDEFGJ", sealed))
    }

    @Test
    fun `the topic does not show the code`() {
        val topic = TogetherWire.topicFor("ABCDEFGH")
        assertTrue(!topic.contains("ABCDEFGH", ignoreCase = true))
        assertNotEquals(topic, TogetherWire.topicFor("ABCDEFGJ"))
        assertEquals(topic, TogetherWire.topicFor("abcd efgh"))
    }

    @Test
    fun `new codes are eight easy characters`() {
        repeat(50) {
            val code = TogetherWire.newCode()
            assertEquals(8, code.length)
            assertTrue(code.none { it in "01OIL" })
        }
    }
}
