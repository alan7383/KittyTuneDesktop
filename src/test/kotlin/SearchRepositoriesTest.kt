package com.alananasss.kittytune

import com.alananasss.kittytune.audio.providers.qobuz.QobuzAudioProvider
import com.alananasss.kittytune.data.qobuz.QobuzSearchRepository
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.InetSocketAddress

class SearchRepositoriesTest {

    @Test
    fun testDeezerTrackParsing() {
        val trackJsonStr = """
        {
            "id": 1109731,
            "title": "Harder, Better, Faster, Stronger",
            "duration": 224,
            "artist": {
                "id": 27,
                "name": "Daft Punk",
                "picture_medium": "https://e-cdns-images.dzcdn.net/images/artist/f2bc007e9133c946ac3c3f5ed515d902/250x250-000000-80-0-0.jpg"
            },
            "album": {
                "id": 302127,
                "title": "Discovery",
                "cover_big": "https://e-cdns-images.dzcdn.net/images/cover/2e018122cb56986277102d2041a592c8/500x500-000000-80-0-0.jpg"
            }
        }
        """.trimIndent()

        val json = JSONObject(trackJsonStr)
        val id = json.getLong("id")
        val title = json.getString("title")
        val artist = json.getJSONObject("artist").getString("name")
        val album = json.getJSONObject("album").getString("title")

        assertEquals(1109731L, id)
        assertEquals("Harder, Better, Faster, Stronger", title)
        assertEquals("Daft Punk", artist)
        assertEquals("Discovery", album)
    }

    @Test
    fun testTidalSearchParsing() {
        val tidalJsonStr = """
        {
            "tracks": {
                "items": [
                    {
                        "id": 123456,
                        "title": "Starboy",
                        "duration": 230,
                        "artist": {
                            "id": 4567,
                            "name": "The Weeknd"
                        },
                        "album": {
                            "id": 7890,
                            "title": "Starboy",
                            "cover": "1234-5678-abcd"
                        }
                    }
                ]
            },
            "artists": {
                "items": [
                    {
                        "id": 4567,
                        "name": "The Weeknd",
                        "picture": "abcd-ef01-2345"
                    }
                ]
            }
        }
        """.trimIndent()

        val json = JSONObject(tidalJsonStr)
        val tracks = json.getJSONObject("tracks").getJSONArray("items")
        assertEquals(1, tracks.length())
        val firstTrack = tracks.getJSONObject(0)
        assertEquals(123456L, firstTrack.getLong("id"))
        assertEquals("Starboy", firstTrack.getString("title"))

        val artists = json.getJSONObject("artists").getJSONArray("items")
        assertEquals(1, artists.length())
        val firstArtist = artists.getJSONObject(0)
        assertEquals(4567L, firstArtist.getLong("id"))
        assertEquals("The Weeknd", firstArtist.getString("name"))
    }

    @Test
    fun testQobuzCatalogueSearch() = withQobuzServer { server, base ->
        server.createContext("/catalog/search") { it.respond(200, catalogue) }
        val result = runBlocking { QobuzSearchRepository.search("Test Artist", 5, base, emptyList()) }
        assertEquals("qobuz:track:999888", result.tracks.single().permalink)
        assertEquals("Test Song", result.tracks.single().title)
        assertEquals(238_000L, result.tracks.single().durationMs)
        assertEquals("Test Album", result.albums.single().title)
        assertEquals("Test Artist", result.artists.single().username)
        assertTrue(result.playlists.isEmpty())
    }

    @Test
    fun testQobuzSearchFallsBackWhenCatalogueIsUnavailable() = withQobuzServer { server, base ->
        server.createContext("/catalog/search") { it.respond(503, "{}") }
        server.createContext("/api/get-music") { it.respond(200, """{"success":true,"data":$catalogue}""") }
        val result = runBlocking { QobuzSearchRepository.search("Test Artist", 5, base, listOf(base)) }
        assertEquals("Test Song", result.tracks.single().title)
        assertEquals("Test Album", result.albums.single().title)
        assertEquals("Test Artist", result.artists.single().username)
    }

    @Test
    fun testQobuzResolutionMatchesByMetadata() = withQobuzServer { server, base ->
        server.createContext("/api/get-music") { it.respond(200, """{"success":true,"data":$catalogue}""") }
        server.createContext("/api/download-music") {
            if (it.requestURI.query.contains("track_id=999888")) {
                it.respond(200, streamResponse(base))
            } else {
                it.respond(404, "{}")
            }
        }
        val resolved = QobuzAudioProvider.resolve(testQuery("metadata", base))
        assertEquals("999888", resolved.trackId)
        assertEquals("$base/track.flac", resolved.mediaUri)
        assertEquals("audio/flac", resolved.mimeType)
        assertEquals(96_000, resolved.sampleRate)
    }

    @Test
    fun testQobuzDirectTrackResolutionFallsBackInQuality() = withQobuzServer { server, base ->
        // There is no search endpoint. A direct track link can be resolved using its ID alone.
        val requestedQualities = java.util.concurrent.ConcurrentLinkedQueue<String>()
        server.createContext("/api/download-music") {
            val parameters = it.requestURI.query.split('&').map { part -> part.split('=', limit = 2) }
                .associate { (key, value) -> key to value }
            val quality = parameters.getValue("quality")
            requestedQualities.add(quality)
            if (parameters["track_id"] == "999888" && quality == "7") {
                it.respond(200, streamResponse(base))
            } else {
                it.respond(200, """{"success":false,"error":"Format unavailable"}""")
            }
        }
        val resolved = QobuzAudioProvider.resolve(testQuery("qobuz:track:999888", base))
        assertEquals("999888", resolved.trackId)
        assertEquals("$base/track.flac", resolved.mediaUri)
        assertEquals(listOf("27", "7"), requestedQualities.toList())
    }

    @Test
    fun testQobuzLiveCatalogue() = runBlocking {
        requireLiveServices()
        val searchResult = QobuzSearchRepository.search("Daft Punk")
        assertTrue(searchResult.tracks.isNotEmpty())
        assertTrue(searchResult.albums.isNotEmpty())
        assertTrue(searchResult.artists.isNotEmpty())

        val album = QobuzSearchRepository.getAlbum("0886443927087")
        assertNotNull(album)
        assertEquals("Random Access Memories", album?.title)
        assertTrue(album?.tracks?.isNotEmpty() == true)

        for ((id, name) in listOf("36819" to "Daft Punk", "11170794" to "snorunt")) {
            val artist = QobuzSearchRepository.getArtist(id)
            assertEquals(name, artist?.title)
            assertTrue(artist?.artworkUrl?.startsWith("https://static.qobuz.com") == true)
            assertTrue(artist?.tracks?.isNotEmpty() == true)
        }
    }

    @Test
    fun testQobuzLiveResolver() {
        requireLiveServices()
        val query = QobuzAudioProvider.Query(
            mediaId = "qobuz:track:33933680",
            title = "Creep",
            artists = listOf("Radiohead"),
            album = "Pablo Honey",
            isrc = null,
            durationMs = 238_000L,
        )
        assertTrue(QobuzAudioProvider.resolve(query).mediaUri.startsWith("http"))
        assertTrue(QobuzAudioProvider.resolve(query.copy(mediaId = "live-qobuz-metadata")).mediaUri.startsWith("http"))
    }

    private fun requireLiveServices() = assumeTrue(
        "Set KITTYTUNE_TEST_LIVE_SERVICES=1 to check live endpoints",
        System.getenv("KITTYTUNE_TEST_LIVE_SERVICES") == "1",
    )

    private fun testQuery(mediaId: String, base: String) = QobuzAudioProvider.Query(
        mediaId = mediaId,
        title = "Test Song",
        artists = listOf("Test Artist"),
        album = "Test Album",
        isrc = null,
        durationMs = 238_000L,
        customInstances = base,
    )

    private fun withQobuzServer(test: (HttpServer, String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        try {
            test(server, "http://127.0.0.1:${server.address.port}")
        } finally {
            server.stop(0)
        }
    }

    private fun HttpExchange.respond(status: Int, body: String) {
        val bytes = body.toByteArray()
        responseHeaders.set("Content-Type", "application/json")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    private fun streamResponse(base: String) = """
        {"success": true, "data": {
            "url": "$base/track.flac", "format_id": 7,
            "bit_depth": 24, "sampling_rate": 96, "mime_type": "audio/flac"
        }}
    """.trimIndent()

    private val catalogue = """
        {
            "tracks": {"items": [{
                "id": 999888, "title": "Test Song", "duration": 238, "streamable": true,
                "performer": {"id": 27, "name": "Test Artist"},
                "album": {"id": "111222", "title": "Test Album"}
            }]},
            "albums": {"items": [{"id": "111222", "title": "Test Album", "artist": {"id": 27, "name": "Test Artist"}}]},
            "artists": {"items": [{"id": 27, "name": "Test Artist"}]}
        }
    """.trimIndent()
}
