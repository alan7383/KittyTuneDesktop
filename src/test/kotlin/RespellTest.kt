import com.alananasss.kittytune.data.LyricsMatcher
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Run-together titles spelled the way another catalogue spells them (issue #66). */
class RespellTest {
    @Test
    fun `a run-together title takes the spelling Genius gives it`() {
        assertEquals("NEW YORK 9mice", LyricsMatcher.respellWith("NEWYORK 9mice", listOf("NEW-YORK", "Blum (Big City Life)")))
    }

    @Test
    fun `nothing changes when no known title spells the word out`() {
        assertNull(LyricsMatcher.respellWith("NEWYORK 9mice", listOf("Frank Ocean")))
    }
}
