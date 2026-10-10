import com.alananasss.kittytune.data.lyrics.GeniusVoices
import com.alananasss.kittytune.ui.player.lyrics.LyricLine
import com.alananasss.kittytune.ui.player.lyrics.LyricSinger
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Who sings which line, from a Genius page's section headers and formatting (issue #66). */
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
            GeniusVoices.parsePlain(page),
            creditedArtists = listOf("9mice", "Kai Angel"),
        )
        assertEquals(listOf(LyricSinger.SINGER_2, LyricSinger.SINGER_2, LyricSinger.SINGER_1, LyricSinger.BOTH), voices)
    }

    @Test
    fun `one named artist throughout is not a duet`() {
        val page = "[Куплет 1: 9mice]\nПервая строка тут\n[Припев: 9mice]\nВторая строка там"
        val voices = GeniusVoices.voicesFor(
            lines("Первая строка тут", "Вторая строка там"),
            GeniusVoices.parsePlain(page),
            creditedArtists = listOf("9mice"),
        )
        assertEquals(listOf(LyricSinger.DEFAULT, LyricSinger.DEFAULT), voices)
    }

    @Test
    fun `headers without names say nothing`() {
        val page = "[Куплет 1]\nПервая строка тут\n[Припев]\nВторая строка там"
        assertNull(GeniusVoices.voicesFor(lines("Первая строка тут"), GeniusVoices.parsePlain(page), emptyList()))
    }

    @Test
    fun `an unmatched line keeps the voice of the part it is in`() {
        val page = "[Verse: A]\nfirst line of the verse\nsecond line of the verse\n[Chorus: B]\nthe chorus goes like this"
        val voices = GeniusVoices.voicesFor(
            lines("first line of the verse", "something the page spells differently", "the chorus goes like this"),
            GeniusVoices.parsePlain(page),
            creditedArtists = listOf("A", "B"),
        )
        assertEquals(listOf(LyricSinger.SINGER_1, LyricSinger.SINGER_1, LyricSinger.SINGER_2), voices)
    }

    @Test
    fun `a shared verse is split by the formatting its header gives each name`() {
        // The shape of "So Good": plain lines are the first name's, italic the second's, bold a guest's.
        val html = """
            <p>[Интро: Guest]<br>
            <a href="/1">That is so good</a><br>
            <br>
            [Куплет 1: Kai Angel, <i>9mice</i> &amp; <b>Guest</b>]<br>
            Plain line sung by the first one<br>
            Another plain line from him<br>
            <i>Italic line from the second one<br>
            <a href="/2">and a second italic one</a></i><br>
            Back to plain for the first<br>
            <a href="/3">(<b>That is so good</b>)</a><br>
            <dfp-unit id="ad"><dfp-kv key="k" value="v"></dfp-kv></dfp-unit>
            [Куплет 2: 9mice]<br>
            A line only the second sings</p>
        """.trimIndent()
        val voices = GeniusVoices.voicesFor(
            lines(
                "That is so good",
                "Plain line sung by the first one",
                "Another plain line from him",
                "Italic line from the second one",
                "and a second italic one",
                "Back to plain for the first",
                "That is so good",
                "A line only the second sings",
            ),
            GeniusVoices.parseHtml(html),
            creditedArtists = listOf("Kai Angel", "9mice"),
        )
        assertEquals(
            listOf(
                LyricSinger.BOTH,
                LyricSinger.SINGER_1, LyricSinger.SINGER_1,
                LyricSinger.SINGER_2, LyricSinger.SINGER_2,
                LyricSinger.SINGER_1,
                LyricSinger.BOTH,
                LyricSinger.SINGER_2,
            ),
            voices,
        )
    }

    @Test
    fun `ad-libs in parentheses do not decide whose line it is`() {
        val html = "[Припев: Kai Angel &amp; <i>9mice</i>]<br>Мои деньги никогда не умрут (<i>Long live</i>)<br>" +
            "<i>Наши деньги феникс навсегда</i><br>[Бридж: 9mice]<br>Только его строка здесь"
        val voices = GeniusVoices.voicesFor(
            lines("Мои деньги никогда не умрут (Long live)", "Наши деньги феникс навсегда", "Только его строка здесь"),
            GeniusVoices.parseHtml(html),
            creditedArtists = listOf("Kai Angel", "9mice"),
        )
        assertEquals(listOf(LyricSinger.SINGER_1, LyricSinger.SINGER_2, LyricSinger.SINGER_2), voices)
    }

    @Test
    fun `a repeated line goes to whoever sings it at that point`() {
        // The same pre-chorus, first by one and later by the other.
        val page = "[Pre: A]\nyou give me so much hype\n[Chorus: B]\nbig city life all night\n" +
            "[Verse: B]\nhis own verse goes here\n[Pre: B]\nyou give me so much hype"
        val voices = GeniusVoices.voicesFor(
            lines("you give me so much hype", "big city life all night", "his own verse goes here", "you give me so much hype"),
            GeniusVoices.parsePlain(page),
            creditedArtists = listOf("A", "B"),
        )
        assertEquals(listOf(LyricSinger.SINGER_1, LyricSinger.SINGER_2, LyricSinger.SINGER_2, LyricSinger.SINGER_2), voices)
    }
}
