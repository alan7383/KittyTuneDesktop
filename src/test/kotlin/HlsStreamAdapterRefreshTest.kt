package com.alananasss.kittytune

import com.alananasss.kittytune.audio.HlsStreamAdapter
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

/**
 * Re-signing an HLS playlist mid-track. What this protects: SoundCloud signs every fragment for a
 * few minutes, so a track longer than that window used to 403 partway through — the decoder saw
 * EOF and playback only came back once the engine had torn the whole stream down and rebuilt it,
 * audible as a gap.
 *
 * The fixture is a local CDN stand-in: it always serves the playlist, and refuses any fragment
 * whose `Expires` has passed, exactly as CloudFront does.
 */
class HlsStreamAdapterRefreshTest {

    private lateinit var server: HttpServer
    private var port = 0
    private val refreshCalls = AtomicInteger()
    private val refusedFragments = AtomicInteger()
    private val acceptedToken = "current"

    private val fragmentBytes = 16
    private val fragmentCount = 3

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        port = server.address.port

        server.createContext("/playlist.m3u8") { exchange ->
            val expires = expiresOf(exchange)
            val body = buildString {
                append("#EXTM3U\n#EXT-X-TARGETDURATION:10\n")
                repeat(fragmentCount) { index ->
                    append("#EXTINF:10.0,\n")
                    append("http://127.0.0.1:$port/fragment/$index?Expires=$expires\n")
                }
                append("#EXT-X-ENDLIST\n")
            }
            respond(exchange, 200, body.toByteArray())
        }

        server.createContext("/fragment/") { exchange ->
            val expires = expiresOf(exchange)
            if (expires * 1000L <= System.currentTimeMillis()) {
                respond(exchange, 403, ByteArray(0))
                return@createContext
            }
            val index = exchange.requestURI.path.substringAfterLast('/').toInt()
            respond(exchange, 200, ByteArray(fragmentBytes) { index.toByte() })
        }

        // Fragments whose signature the adapter cannot read ahead of time: the only signal that
        // they went stale is the CDN turning them down.
        server.createContext("/opaque.m3u8") { exchange ->
            val token = tokenOf(exchange)
            val body = buildString {
                append("#EXTM3U\n#EXT-X-TARGETDURATION:10\n")
                repeat(fragmentCount) { index ->
                    append("#EXTINF:10.0,\n")
                    append("http://127.0.0.1:$port/opaque/$index?Token=$token\n")
                }
                append("#EXT-X-ENDLIST\n")
            }
            respond(exchange, 200, body.toByteArray())
        }

        server.createContext("/opaque/") { exchange ->
            if (tokenOf(exchange) != acceptedToken) {
                refusedFragments.incrementAndGet()
                respond(exchange, 403, ByteArray(0))
                return@createContext
            }
            val index = exchange.requestURI.path.substringAfterLast('/').toInt()
            respond(exchange, 200, ByteArray(fragmentBytes) { index.toByte() })
        }

        server.start()
    }

    @After
    fun stopServer() {
        server.stop(0)
    }

    private fun expiresOf(exchange: HttpExchange): Long =
        exchange.requestURI.query.orEmpty()
            .split('&')
            .firstOrNull { it.startsWith("Expires=") }
            ?.removePrefix("Expires=")
            ?.toLongOrNull() ?: 0L

    private fun tokenOf(exchange: HttpExchange): String =
        exchange.requestURI.query.orEmpty()
            .split('&')
            .firstOrNull { it.startsWith("Token=") || it.startsWith("token=") }
            ?.substringAfter('=')
            .orEmpty()

    private fun respond(exchange: HttpExchange, code: Int, body: ByteArray) {
        exchange.sendResponseHeaders(code, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    private fun playlistUrl(expiresInSeconds: Long) =
        "http://127.0.0.1:$port/playlist.m3u8?Expires=${System.currentTimeMillis() / 1000L + expiresInSeconds}"

    /** Every fragment, in order, as the decoder would read them. */
    private fun readAll(adapter: HlsStreamAdapter): ByteArray =
        adapter.getInputStream(0L).use { it.readBytes() }

    private fun expectedAudio(): ByteArray =
        ByteArray(fragmentBytes * fragmentCount) { (it / fragmentBytes).toByte() }

    private fun refresherTo(expiresInSeconds: Long): (String) -> String? = { failedUrl ->
        assertTrue("the adapter should report the playlist it was using", failedUrl.contains("/playlist.m3u8"))
        refreshCalls.incrementAndGet()
        playlistUrl(expiresInSeconds)
    }

    @Test
    fun `a live playlist is read straight through without asking for a new one`() {
        val adapter = HlsStreamAdapter(playlistUrl(600), emptyMap(), refresherTo(600))

        assertEquals(30_000L, adapter.totalDurationMs)
        assertArrayEquals(expectedAudio(), readAll(adapter))
        assertEquals(0, refreshCalls.get())
    }

    @Test
    fun `lapsed fragments are re-signed and the audio still arrives complete`() {
        val expired = playlistUrl(-30)
        val adapter = HlsStreamAdapter(expired, emptyMap(), refresherTo(600))

        assertArrayEquals(expectedAudio(), readAll(adapter))
        // One refresh covers the rest of the track: every fragment is re-signed at once.
        assertEquals(1, refreshCalls.get())
        // The identity the engine matches on stays what it was built with.
        assertEquals(expired, adapter.playlistUrl)
        assertEquals(30_000L, adapter.totalDurationMs)
    }

    @Test
    fun `without a way to re-sign, the read fails so the engine can recover`() {
        val adapter = HlsStreamAdapter(playlistUrl(-30), emptyMap(), null)
        try {
            readAll(adapter)
            org.junit.Assert.fail("expected the expired fragment to fail the read")
        } catch (e: IOException) {
            assertTrue("expected the CDN's refusal, got ${e.message}", e.message.orEmpty().contains("403"))
        }
    }

    @Test
    fun `a playlist that segments the track differently is refused`() {
        // A re-resolve that came back with another transcoding: adopting it would leave the
        // decoder's fragment index pointing at the wrong moment of the track.
        server.createContext("/other.m3u8") { exchange ->
            val body = "#EXTM3U\n#EXTINF:30.0,\nhttp://127.0.0.1:$port/fragment/0?Expires=${System.currentTimeMillis() / 1000L + 600}\n#EXT-X-ENDLIST\n"
            respond(exchange, 200, body.toByteArray())
        }
        val adapter = HlsStreamAdapter(playlistUrl(-30), emptyMap()) {
            refreshCalls.incrementAndGet()
            "http://127.0.0.1:$port/other.m3u8?Expires=${System.currentTimeMillis() / 1000L + 600}"
        }

        try {
            readAll(adapter)
            org.junit.Assert.fail("expected the mismatched playlist to be refused")
        } catch (e: IOException) {
            assertTrue("expected the CDN's refusal, got ${e.message}", e.message.orEmpty().contains("403"))
        }
        assertEquals(1, refreshCalls.get())
    }

    @Test
    fun `a progressive re-resolve is not mistaken for a playlist`() {
        val adapter = HlsStreamAdapter(playlistUrl(-30), emptyMap()) {
            refreshCalls.incrementAndGet()
            "https://cf-media.sndcdn.com/x.128.mp3?Expires=${System.currentTimeMillis() / 1000L + 600}"
        }

        try {
            readAll(adapter)
            org.junit.Assert.fail("expected the read to fail rather than fetch an mp3 as a playlist")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains("403"))
        }
        assertEquals(1, refreshCalls.get())
    }

    @Test
    fun `a refusal is enough to trigger the re-sign when the deadline is unreadable`() {
        val adapter = HlsStreamAdapter("http://127.0.0.1:$port/opaque.m3u8?token=stale", emptyMap()) {
            refreshCalls.incrementAndGet()
            "http://127.0.0.1:$port/opaque.m3u8?token=$acceptedToken"
        }

        assertArrayEquals(expectedAudio(), readAll(adapter))
        assertEquals(1, refreshCalls.get())
        // Exactly one wasted request: the refusal that told us the signature had rotated.
        assertEquals(1, refusedFragments.get())
    }

    private fun assertArrayEquals(expected: ByteArray, actual: ByteArray) =
        org.junit.Assert.assertArrayEquals(expected, actual)
    // --- seeking (issue #33) ------------------------------------------------------------------
    //
    // "When you click on the text, playback does not start from the very beginning." Clicking a
    // lyric line seeks to its timestamp, and a sheet matched from a longer song carries timestamps
    // past this track's end. The start-fragment lookup used to answer "the first one" for anything
    // it could not place, so seeking past the end restarted the song.

    /** What the decoder reads from a seek to [positionMs]: one byte per fragment, in order. */
    private fun fragmentsFrom(adapter: HlsStreamAdapter, positionMs: Long): List<Int> =
        adapter.getInputStream(positionMs).use { it.readBytes() }
            .toList()
            .chunked(fragmentBytes)
            .map { it.first().toInt() }

    @Test
    fun `seeking into the middle starts at the fragment holding that moment`() {
        val adapter = HlsStreamAdapter(playlistUrl(600), emptyMap())
        // Fragments are ten seconds each, so 15 s is inside the second one.
        assertEquals(listOf(1, 2), fragmentsFrom(adapter, 15_000L))
        assertEquals(listOf(0, 1, 2), fragmentsFrom(adapter, 0L))
        assertEquals(listOf(2), fragmentsFrom(adapter, 25_000L))
    }

    @Test
    fun `seeking past the end ends the track instead of restarting it`() {
        val adapter = HlsStreamAdapter(playlistUrl(600), emptyMap())
        // Thirty seconds of audio in total. Past that there is nothing left to read, and reading
        // fragment zero again is what made the song start over.
        assertEquals(emptyList<Int>(), fragmentsFrom(adapter, 30_000L))
        assertEquals(emptyList<Int>(), fragmentsFrom(adapter, 600_000L))
    }

    @Test
    fun `a position on a fragment boundary takes the fragment that contains it`() {
        val adapter = HlsStreamAdapter(playlistUrl(600), emptyMap())
        assertEquals(listOf(1, 2), fragmentsFrom(adapter, 10_000L))
        assertEquals(listOf(2), fragmentsFrom(adapter, 20_000L))
    }

}
