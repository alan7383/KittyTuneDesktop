import com.alananasss.kittytune.data.lyrics.GeniusVoices
import com.alananasss.kittytune.ui.player.lyrics.LyricLine
import com.alananasss.kittytune.ui.player.lyrics.LyricSinger
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Who sings which line, from a Genius page's section headers (issue #66). */
class GeniusVoicesTest {

    private fun lines(vararg texts: String) = texts.mapIndexed { i, t -> LyricLine(t, i * 1_000L, i * 1_000L + 900) }

    @Test
    fun `two named artists make a duet, credited one on the left`() {
        val page = """
            [Куплет 1: Kai Angel]
            Холодный город спит
            Огни в моих глазах
            [Припев: 9mice]
            Фонтенбло фонтенбло
            [Аутро: 9mice & Kai Angel]
            Мы уходим навсегда
        """.trimIndent()
        val voices = GeniusVoices.voicesFor(
            lines("Холодный город спит", "Огни в моих глазах", "Фонтенбло фонтенбло", "Мы уходим навсегда"),
            GeniusVoices.parseSections(page),
            creditedArtists = listOf("9mice", "Kai Angel"),
        )
        assertEquals(listOf(LyricSinger.SINGER_2, LyricSinger.SINGER_2, LyricSinger.SINGER_1, LyricSinger.BOTH), voices)
    }

    @Test
    fun `one named artist throughout is not a duet`() {
        val page = "[Куплет 1: 9mice]\nПервая строка тут\n[Припев: 9mice]\nВторая строка там"
        val voices = GeniusVoices.voicesFor(
            lines("Первая строка тут", "Вторая строка там"),
            GeniusVoices.parseSections(page),
            creditedArtists = listOf("9mice"),
        )
        assertEquals(listOf(LyricSinger.DEFAULT, LyricSinger.DEFAULT), voices)
    }

    @Test
    fun `headers without names say nothing`() {
        val page = "[Куплет 1]\nПервая строка тут\n[Припев]\nВторая строка там"
        assertNull(GeniusVoices.voicesFor(lines("Первая строка тут"), GeniusVoices.parseSections(page), emptyList()))
    }

    @Test
    fun `an unmatched line keeps the voice of the part it is in`() {
        val page = "[Verse: A]\nfirst line of the verse\nsecond line of the verse\n[Chorus: B]\nthe chorus goes like this"
        val voices = GeniusVoices.voicesFor(
            lines("first line of the verse", "something the page spells differently", "the chorus goes like this"),
            GeniusVoices.parseSections(page),
            creditedArtists = listOf("A", "B"),
        )
        assertEquals(listOf(LyricSinger.SINGER_1, LyricSinger.SINGER_1, LyricSinger.SINGER_2), voices)
    }
}
