import com.alananasss.kittytune.data.catalog.CatalogSource
import com.alananasss.kittytune.data.yandex.YandexTrack
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reading one track out of a Yandex Music search response (issue #33).
 *
 * Their shape differs from Apple's in three ways, and each one is a way to get a row that looks fine and cannot
 * be matched against anything: the artist is a list, the album is a list, and the cover is a template with `%%`
 * where a size belongs. Fixtures are trimmed from what their documented model says a track carries — this is the
 * one client here I could not verify live, because the API answers HTTP 451 outside the countries it serves and
 * that includes every machine I can reach.
 */
class YandexTrackTest {

    private fun track(json: String) = YandexTrack.from(Json.parseToJsonElement(json).jsonObject)

    private val full = """
        {"id":"10994777","realId":"10994777","title":"Кино","available":true,
         "durationMs":214000,
         "coverUri":"avatars.yandex.net/get-music-content/28589/abc/%%",
         "artists":[{"id":41052,"name":"Молчат Дома"}],
         "albums":[{"id":1193829,"title":"Этажи","year":2018}]}
    """.trimIndent()

    @Test
    fun `a track carries what is needed to find it elsewhere`() {
        val song = track(full)!!
        assertEquals(CatalogSource.YANDEX_MUSIC, song.source)
        assertEquals("10994777", song.id)
        assertEquals("Кино", song.title)
        assertEquals("Молчат Дома", song.artist)
        assertEquals("Этажи", song.album)
        assertEquals(214_000L, song.durationMs)
        assertEquals("2018", song.releaseDate)
    }

    /** Their cover is a template, and a row 48 dp wide has no use for a 1000 px original. */
    @Test
    fun `the cover template is resolved and given a scheme`() {
        val song = track(full)!!
        assertEquals("https://avatars.yandex.net/get-music-content/28589/abc/400x400", song.artworkUrl)
    }

    /**
     * Every credited artist, joined. A feature named only in the second entry is exactly the sort of thing that
     * makes a title unfindable, and the matcher does better with the whole credit than with a third of it.
     */
    @Test
    fun `every credited artist is kept`() {
        val song = track(
            """
            {"id":"1","title":"T","durationMs":1000,
             "artists":[{"name":"A"},{"name":"B"},{"name":"C"}],"albums":[]}
            """.trimIndent()
        )!!
        assertEquals("A, B, C", song.artist)
    }

    /**
     * Unavailable on Yandex is not unavailable everywhere — rights lapse, tracks get taken down — and the whole
     * reason for reading this catalogue is that the song exists somewhere. So it is kept.
     */
    @Test
    fun `a track Yandex will not serve is still a result`() {
        val song = track(
            """{"id":"1","title":"T","available":false,"artists":[{"name":"A"}],"albums":[]}"""
        )
        assertTrue(song != null, "an unavailable track is still findable elsewhere")
    }

    /** Missing the title or the credit, and there is nothing to search for. */
    @Test
    fun `an entry that could never be matched is dropped`() {
        assertNull(track("""{"id":"1","artists":[{"name":"A"}],"albums":[]}"""))
        assertNull(track("""{"id":"1","title":"T","artists":[],"albums":[]}"""))
        assertNull(track("""{"title":"T","artists":[{"name":"A"}],"albums":[]}"""))
    }

    /** Their ids come back as numbers as often as strings, and one shape must not lose to the other. */
    @Test
    fun `a numeric id is read as text`() {
        val song = track("""{"id":10994777,"title":"T","artists":[{"name":"A"}],"albums":[]}""")!!
        assertEquals("10994777", song.id)
    }

    /** No album, no cover, no duration: still a name and a credit, so still worth showing. */
    @Test
    fun `the optional fields stay absent rather than becoming empty`() {
        val song = track("""{"id":"1","title":"T","artists":[{"name":"A"}]}""")!!
        assertNull(song.album)
        assertNull(song.artworkUrl)
        assertNull(song.releaseDate)
        assertEquals(0L, song.durationMs)
    }

    /** The list key has to survive both catalogues being open at once. */
    @Test
    fun `the key names the source as well as the id`() {
        assertEquals("YANDEX_MUSIC:10994777", track(full)!!.key)
    }
}
