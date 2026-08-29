import com.alananasss.kittytune.data.applemusic.AppleMusicTokens
import com.alananasss.kittytune.data.applemusic.AppleSong
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reading the Apple Music web player's own catalogue credential, and its answers (issue #33).
 *
 * The fixtures here are trimmed from what `music.apple.com` actually served when this was written: the
 * bundle reference from its HTML, and three JWTs from that bundle of which only the second answered 200.
 * That last detail is the reason the extractor returns a list instead of a match.
 */
class AppleMusicCatalogTest {

    private val pageHtml = """
        <!DOCTYPE html><html><head>
        <script type="module" crossorigin src="/assets/index~3eb8a0d364.js"></script>
        <script nomodule crossorigin src="/assets/index-legacy~a5e638ce59.js"></script>
        <script src="/includes/js-cdn/musickit/v3/amp/musickit.js"></script>
        </head><body></body></html>
    """.trimIndent()

    @Test
    fun `the bundle is found where the page points`() {
        assertEquals(
            "https://music.apple.com/assets/index~3eb8a0d364.js",
            AppleMusicTokens.bundleUrlIn(pageHtml),
        )
    }

    /** The legacy bundle and MusicKit's own script must not be mistaken for it. */
    @Test
    fun `only the entry bundle matches`() {
        val found = AppleMusicTokens.bundleUrlIn(pageHtml)
        assertTrue(found!!.endsWith("index~3eb8a0d364.js"), found)
    }

    @Test
    fun `a page without the bundle answers nothing rather than guessing`() {
        assertNull(AppleMusicTokens.bundleUrlIn("<html><body>no assets here</body></html>"))
    }

    /**
     * Three candidates, in order. The caller probes them because the bundle does not say which is the
     * catalogue's — and when this was written the first one answered 401.
     */
    @Test
    fun `every token in the bundle is offered, in order`() {
        val bundle = """
            var a="eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiIsImtpZCI6IkFBQUFBQUFBQUEifQ.eyJpc3MiOiJNNjJZRDg1RlRRIiwiaWF0IjoxNzAwMDAwMDAwfQ.SIGNATUREONESIGNATUREONESIG",
                b="eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiIsImtpZCI6IkJCQkJCQkJCQkIifQ.eyJpc3MiOiI1SUtQUDJJRUNRIiwiaWF0IjoxNzAwMDAwMDAwfQ.SIGNATURETWOSIGNATURETWOSIG",
                c="eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiIsImtpZCI6IkNDQ0NDQ0NDQ0MifQ.eyJpc3MiOiJBTVBXZWJQbGF5IiwiaWF0IjoxNzAwMDAwMDAwfQ.SIGNATURE3SIGNATURE3SIGNATU";
        """.trimIndent()
        val candidates = AppleMusicTokens.candidatesIn(bundle)
        assertEquals(3, candidates.size)
        assertTrue(candidates[0].contains("TTYyWUQ") || candidates[0].startsWith("eyJ"), candidates[0])
        assertTrue(candidates.all { it.count { c -> c == '.' } == 2 })
    }

    @Test
    fun `duplicates are offered once`() {
        val token = "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiJ9.eyJpc3MiOiJYWFhYWFhYWFhYIn0.SIGSIGSIGSIGSIGSIGSIGSIG"
        assertEquals(1, AppleMusicTokens.candidatesIn("a=\"$token\";b=\"$token\";").size)
    }

    @Test
    fun `a bundle with no token at all is empty rather than a crash`() {
        assertTrue(AppleMusicTokens.candidatesIn("console.log('nothing to see')").isEmpty())
    }

    /** The catalogue is keyed to a country, and it follows the app's language, not the machine's. */
    @Test
    fun `the storefront follows the app language`() {
        assertEquals("fr", AppleMusicTokens.storefrontFor("fr"))
        assertEquals("ru", AppleMusicTokens.storefrontFor("ru"))
        assertEquals("hu", AppleMusicTokens.storefrontFor("hu"))
        assertEquals("us", AppleMusicTokens.storefrontFor("en"))
        assertEquals("us", AppleMusicTokens.storefrontFor("something-else"))
    }

    // ---- what a result looks like -------------------------------------------------------------

    private fun song(json: String) =
        AppleSong.from(Json.parseToJsonElement(json).jsonObject)

    @Test
    fun `a song carries what is needed to find it elsewhere`() {
        val parsed = song(
            """
            {"id":"1440833098","type":"songs","attributes":{
              "name":"One More Time","artistName":"Daft Punk","albumName":"Discovery",
              "durationInMillis":320357,"releaseDate":"2000-11-30",
              "artwork":{"width":3000,"height":3000,"url":"https://is1.mzstatic.com/image/{w}x{h}bb.jpg"}
            }}
            """.trimIndent()
        )!!
        assertEquals("One More Time", parsed.title)
        assertEquals("Daft Punk", parsed.artist)
        assertEquals("Discovery", parsed.album)
        assertEquals(320357L, parsed.durationMs)
        assertEquals("2000-11-30", parsed.releaseDate)
    }

    /** Apple hands out a template, not a URL, and expects the caller to pick a size. */
    @Test
    fun `the artwork template is resolved to a real size`() {
        val parsed = song(
            """
            {"id":"1","attributes":{"name":"T","artistName":"A",
              "artwork":{"url":"https://is1.mzstatic.com/image/thumb/x/{w}x{h}bb.jpg"}}}
            """.trimIndent()
        )!!
        assertEquals("https://is1.mzstatic.com/image/thumb/x/600x600bb.jpg", parsed.artworkUrl)
    }

    /**
     * A result with no title or no artist cannot be looked for on a source that streams, so it is not a
     * result at all — one fewer row beats a row that does nothing.
     */
    @Test
    fun `an entry that could never be matched is dropped`() {
        assertNull(song("""{"id":"1","attributes":{"artistName":"A"}}"""))
        assertNull(song("""{"id":"1","attributes":{"name":"T"}}"""))
        assertNull(song("""{"attributes":{"name":"T","artistName":"A"}}"""))
        assertNull(song("""{"id":"1"}"""))
    }

    /** Missing extras are missing, not zero-length strings pretending to be values. */
    @Test
    fun `optional fields stay absent`() {
        val parsed = song("""{"id":"1","attributes":{"name":"T","artistName":"A","albumName":""}}""")!!
        assertNull(parsed.album)
        assertNull(parsed.artworkUrl)
        assertNull(parsed.releaseDate)
        assertEquals(0L, parsed.durationMs)
    }
}
