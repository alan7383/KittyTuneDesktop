import com.alananasss.kittytune.data.sections.SduiItem
import com.alananasss.kittytune.data.sections.SduiSection
import com.alananasss.kittytune.data.sections.SectionsParser
import com.google.gson.JsonParser
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reading SoundCloud's server-driven browse screens (issue #33).
 *
 * "Ça doit avoir le même endpoint que SoundCloud mobile […] tu mets tout exactement comme Android."
 *
 * It does, and the price of that is a layout language with sixteen section kinds that they extend whenever they
 * ship a new shelf. So the contract these tests hold is not "parse SoundCloud correctly" — nobody can promise
 * that against a versioned server-driven format — it is **degrade one shelf at a time**. An unrecognised kind, a
 * missing artwork, an item pointing at an entity that is not in the payload: each of those costs exactly itself
 * and never the screen.
 *
 * The fixtures are built from the field names in their decompiled `ApiSectionData` and `ApiSectionEntityItem`.
 * The endpoint answers 401 without a listener's token, so this is the half that can be tested without one.
 */
class SectionsParserTest {

    private fun parse(json: String) = SectionsParser.parse(JsonParser.parseString(json).asJsonObject)

    // ---- the browse grid ----------------------------------------------------------------------

    private val browse = """
    {
      "sections": [
        {"data": {"kind": "grid", "title": "Ambiances", "items": [
          {"data": {"title": "Hip hop et rap", "query": "soundcloud:genres:hiphoprap",
                    "image_medium_dark": "https://i1.sndcdn.com/hiphop.jpg"}},
          {"data": {"title": "Electro", "query": "soundcloud:genres:electronic",
                    "image_medium_dark": "https://i1.sndcdn.com/electro.jpg"}}
        ]}}
      ],
      "entities": {},
      "_links": {"next": {"href": "/search/query?offset=30"}}
    }
    """.trimIndent()

    @Test
    fun `the browse grid comes back as category tiles`() {
        val screen = parse(browse)
        val shelf = screen.sections.single() as SduiSection.Shelf
        assertEquals("Ambiances", shelf.title)
        assertEquals(SduiSection.Style.GRID, shelf.style)
        assertEquals(listOf("Hip hop et rap", "Electro"), shelf.items.map { it.title })
        assertTrue(shelf.items.all { it.kind == SduiItem.Kind.CATEGORY })
    }

    /**
     * The query is the whole point of a category tile: it is what goes back as `q` to open the page. Inventing a
     * slug from the title is exactly how this would stop matching Android.
     */
    @Test
    fun `a category tile keeps the query it was given`() {
        val shelf = parse(browse).sections.single() as SduiSection.Shelf
        assertEquals("soundcloud:genres:hiphoprap", shelf.items.first().query)
    }

    @Test
    fun `the next link is kept for paging`() {
        assertEquals("/search/query?offset=30", parse(browse).nextHref)
    }

    // ---- a category page ----------------------------------------------------------------------

    /** Their category page nests its shelves in a container; a desktop list has no use for the nesting. */
    @Test
    fun `nested containers are flattened into a group`() {
        val screen = parse(
            """
            {"sections": [{"data": {"kind": "container", "title": "Electro", "sections": [
                {"data": {"kind": "carousel", "title": "En tendance", "items": [
                    {"data": {"urn": "soundcloud:tracks:1"}}]}},
                {"data": {"kind": "grid", "title": "Playlists", "items": [
                    {"data": {"urn": "soundcloud:playlists:9"}}]}}
            ]}}],
             "entities": {
               "soundcloud:tracks:1": {"title": "I Will Find You", "user": {"username": "BUNT."}},
               "soundcloud:playlists:9": {"title": "EDM Next", "user": {"username": "The Peak"}}
             }}
            """.trimIndent()
        )
        val group = screen.sections.single() as SduiSection.Group
        assertEquals("Electro", group.title)
        assertEquals(listOf("En tendance", "Playlists"), group.sections.map { (it as SduiSection.Shelf).title })
    }

    /** Items point at the `entities` map by urn as often as they carry their content inline. */
    @Test
    fun `an item is resolved through the entities map`() {
        val screen = parse(
            """
            {"sections": [{"data": {"kind": "carousel", "items": [{"data": {"urn": "soundcloud:tracks:1"}}]}}],
             "entities": {"soundcloud:tracks:1": {
                "title": "Gods of Rave", "user": {"username": "FLKN"},
                "artwork_url": "https://i1.sndcdn.com/a.jpg"}}}
            """.trimIndent()
        )
        val item = (screen.sections.single() as SduiSection.Shelf).items.single()
        assertEquals("Gods of Rave", item.title)
        assertEquals("FLKN", item.subtitle)
        assertEquals("https://i1.sndcdn.com/a.jpg", item.artworkUrl)
        assertEquals(SduiItem.Kind.TRACK, item.kind)
    }

    @Test
    fun `the kind is read from the urn`() {
        fun kindOf(urn: String): SduiItem.Kind {
            val screen = parse(
                """{"sections":[{"data":{"items":[{"data":{"urn":"$urn","title":"x"}}]}}],"entities":{}}"""
            )
            return ((screen.sections.single() as SduiSection.Shelf).items.single()).kind
        }
        assertEquals(SduiItem.Kind.TRACK, kindOf("soundcloud:tracks:1"))
        assertEquals(SduiItem.Kind.PLAYLIST, kindOf("soundcloud:playlists:1"))
        assertEquals(SduiItem.Kind.PLAYLIST, kindOf("soundcloud:system-playlists:weekly"))
        assertEquals(SduiItem.Kind.USER, kindOf("soundcloud:users:1"))
    }

    // ---- degrading one shelf at a time --------------------------------------------------------

    /**
     * The reason this parser is deliberately loose. Their layout language is versioned and grows; a parser that
     * rejected an unknown kind would take the browse screen down the next time SoundCloud shipped a shelf.
     */
    @Test
    fun `an unknown section kind is dropped and the rest survive`() {
        val screen = parse(
            """
            {"sections": [
               {"data": {"kind": "some_shelf_from_next_year", "mystery": 1}},
               {"data": {"kind": "grid", "title": "Ambiances", "items": [{"data": {"title": "Pop"}}]}}
            ], "entities": {}}
            """.trimIndent()
        )
        assertEquals(1, screen.sections.size)
        assertEquals("Ambiances", (screen.sections.single() as SduiSection.Shelf).title)
    }

    @Test
    fun `a shelf with no usable items is dropped rather than shown empty`() {
        val screen = parse("""{"sections":[{"data":{"kind":"grid","title":"Empty","items":[]}}],"entities":{}}""")
        assertTrue(screen.isEmpty)
    }

    /** An item whose entity is not in the payload would be a blank tile, which is worse than one fewer tile. */
    @Test
    fun `an item pointing at a missing entity is skipped`() {
        val screen = parse(
            """{"sections":[{"data":{"items":[{"data":{"urn":"soundcloud:tracks:404"}}]}}],"entities":{}}"""
        )
        assertTrue(screen.isEmpty)
    }

    @Test
    fun `a response with nothing in it is empty rather than an error`() {
        assertTrue(parse("""{"sections":[],"entities":{}}""").isEmpty)
        assertTrue(SectionsParser.parse(null).isEmpty)
        assertNull(SectionsParser.parse(null).nextHref)
    }

    /** Their tiles carry six sizes and two themes of one picture, and any of them is a picture. */
    @Test
    fun `artwork is taken from whichever size the shelf supplied`() {
        val screen = parse(
            """
            {"sections":[{"data":{"items":[
               {"data":{"title":"A","image_large_light":"https://i1.sndcdn.com/large.jpg"}}]}}],
             "entities":{}}
            """.trimIndent()
        )
        val item = (screen.sections.single() as SduiSection.Shelf).items.single()
        assertEquals("https://i1.sndcdn.com/large.jpg", item.artworkUrl)
    }

    /** `results` and `pills` are the same thing as `items` under two other shelf kinds. */
    @Test
    fun `results and pills are read like items`() {
        val fromResults = parse("""{"sections":[{"data":{"results":[{"data":{"title":"R"}}]}}],"entities":{}}""")
        val fromPills = parse("""{"sections":[{"data":{"pills":[{"data":{"title":"P"}}]}}],"entities":{}}""")
        assertEquals("R", ((fromResults.sections.single() as SduiSection.Shelf).items.single()).title)
        assertEquals("P", ((fromPills.sections.single() as SduiSection.Shelf).items.single()).title)
    }
}
