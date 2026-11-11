package com.alananasss.kittytune

import com.alananasss.kittytune.audio.HlsStreamAdapter
import com.alananasss.kittytune.data.drm.ContentKey
import com.alananasss.kittytune.data.drm.IsoBmff
import com.alananasss.kittytune.data.drm.SoundCloudDrm
import com.alananasss.kittytune.data.drm.SoundCloudWidevineLicense
import com.alananasss.kittytune.data.drm.WidevineCdm
import com.alananasss.kittytune.data.drm.WidevineCdmRegistry
import com.alananasss.kittytune.data.drm.WidevineLicenseSession
import com.alananasss.kittytune.data.StreamResolver
import com.alananasss.kittytune.domain.Format
import com.alananasss.kittytune.domain.Transcoding
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

/**
 * End-to-end test of the protected-stream path, with a stand-in CDM.
 *
 * Everything except the license exchange is the real code: a real `#EXT-X-KEY` line in the
 * SoundCloud layout, the real playlist/init/fragment fetching in [HlsStreamAdapter], the real
 * [SoundCloudDrm] orchestration, the real CENC decryption and the real `moov`/`moof` rewrites. Only
 * [WidevineCdm] is a stub, because it is the one piece that cannot exist without a licensed CDM.
 *
 * The fragment served is the FFmpeg CENC fixture, so the bytes that come out of the adapter are
 * compared against the same SHA-256 that `ffmpeg -decryption_key` produces for it.
 */
class SoundCloudDrmPipelineTest {

    /** Same fixture and key as [CencDecrypterTest]. */
    private val contentKey = IsoBmff.unhex("0f0e0d0c0b0a09080706050403020100")!!
    private val keyIdHex = "a1b2c3d4e5f60718293a4b5c6d7e8f90"
    private val expectedPlaintextSha256 =
        "92bf2b81e6ae2a5f2f81460ab6817ea9edbfdacbd436c2ea42d7d43fb2e4831f"

    /** The real Widevine PSSH SoundCloud serves, used here verbatim as an opaque blob. */
    private val psshB64 =
        "AAAAa3Bzc2gAAAAA7e+LqXnWSs6jyCfc1R0h7QAAAEsSEBb7NIiL+lkEpnFuMsqjKa4aC2J1eWRybWtleW9z" +
            "IiQwNGJjMjkxYy1jM2E0LTQzMTAtYWNjNy0wMDU3MGYyNTk5ODZI49yVmwY="

    private lateinit var server: HttpServer
    private lateinit var base: String

    private val licensePosts = AtomicInteger(0)
    private val fragmentRequests = AtomicInteger(0)
    private val licenseRefusals = AtomicInteger(0)
    private val licenseRequests = mutableListOf<ByteArray>()
    private val originalPlaybackUrl = SoundCloudWidevineLicense.playbackUrl

    private val fixture: ByteArray by lazy {
        javaClass.classLoader!!.getResourceAsStream("drm/cenc_small.mp4")!!.use { it.readBytes() }
    }

    private val initBytes: ByteArray by lazy {
        var end = 0
        for (box in IsoBmff.boxes(fixture, 0, fixture.size)) {
            if (box.type == "moof") break
            end = box.end
        }
        fixture.copyOfRange(0, end)
    }

    private val segmentBytes: ByteArray by lazy {
        var start = -1
        var end = 0
        for (box in IsoBmff.boxes(fixture, 0, fixture.size)) {
            if (box.type == "moof" && start < 0) start = box.start
            if (box.type == "mdat") end = box.end
        }
        fixture.copyOfRange(start, end)
    }

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/playlist.m3u8") { exchange -> respond(exchange, playlist(), "application/vnd.apple.mpegurl") }
        server.createContext("/long.m3u8") { exchange ->
            val body = String(playlist()).replace("#EXT-X-ENDLIST", "#EXTINF:2.000,\ndata000.m4s\n#EXT-X-ENDLIST")
            respond(exchange, body.toByteArray(), "application/vnd.apple.mpegurl")
        }
        server.createContext("/analysis.m3u8") { exchange ->
            val body = String(playlist()).replace("#EXT-X-ENDLIST",
                "#EXTINF:2.000,\ndata000.m4s\n".repeat(1000) + "#EXT-X-ENDLIST")
            respond(exchange, body.toByteArray(), "application/vnd.apple.mpegurl")
        }
        server.createContext("/init.mp4") { exchange -> respond(exchange, initBytes, "video/mp4") }
        server.createContext("/data000.m4s") { exchange ->
            fragmentRequests.incrementAndGet()
            respond(exchange, segmentBytes, "video/iso.segment")
        }
        server.createContext("/playback/widevine") { exchange ->
            licensePosts.incrementAndGet()
            synchronized(licenseRequests) { licenseRequests.add(exchange.requestBody.readBytes()) }
            respond(exchange, "LICENSE".toByteArray(), "application/octet-stream")
        }
        // What the licence server does to a CDM driven outside a browser on Windows.
        server.createContext("/refuse/widevine") { exchange ->
            licenseRefusals.incrementAndGet()
            exchange.requestBody.readBytes()
            exchange.sendResponseHeaders(403, 0)
            exchange.responseBody.close()
        }
        server.executor = null
        server.start()
        base = "http://127.0.0.1:${server.address.port}"
        SoundCloudWidevineLicense.playbackUrl = "$base/playback/widevine?license_token="
    }

    @After
    fun stopServer() {
        SoundCloudWidevineLicense.playbackUrl = originalPlaybackUrl
        SoundCloudWidevineLicense.resetLicenceRefusal()
        WidevineCdmRegistry.install(null)
        SoundCloudDrm.clearCache()
        server.stop(0)
    }

    private fun playlist(): ByteArray = """
        #EXTM3U
        #EXT-X-VERSION:7
        #EXT-X-KEY:METHOD=SAMPLE-AES,URI="data:text/plain;base64,$psshB64",KEYID=0x$keyIdHex,KEYFORMAT="urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed",KEYFORMATVERSIONS="1"
        #EXT-X-KEY:METHOD=SAMPLE-AES-CTR,URI="data:text/plain;charset=UTF-16;base64,AAQAAAEA",KEYID=0x$keyIdHex,KEYFORMAT="com.microsoft.playready",KEYFORMATVERSIONS="1"
        #EXT-X-MAP:URI="init.mp4"
        #EXTINF:2.000,
        data000.m4s
        #EXT-X-ENDLIST
    """.trimIndent().toByteArray()

    private fun respond(exchange: HttpExchange, body: ByteArray, contentType: String) {
        exchange.responseHeaders.add("Content-Type", contentType)
        exchange.sendResponseHeaders(200, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    // --- the stand-in CDM -------------------------------------------------------------------------

    /** Returns a fixed key, and records what the app asked the license server for. */
    private inner class FakeCdm : WidevineCdm {
        override val name = "test CDM"
        val forks = mutableListOf<FakeCdm>()
        var disposed = false
        override fun fork(): WidevineCdm = FakeCdm().also { forks.add(it) }
        override fun close() { disposed = true }

        var psshSeen: ByteArray? = null
        var closed = false


        override fun openSession(pssh: ByteArray): WidevineLicenseSession {
            psshSeen = pssh
            return object : WidevineLicenseSession {
                override fun licenseRequest(): ByteArray = "REQUEST".toByteArray()

                override fun provideLicense(license: ByteArray): List<ContentKey> {
                    assertEquals("LICENSE", String(license, Charsets.UTF_8))
                    return listOf(ContentKey(IsoBmff.unhex(keyIdHex)!!, contentKey))
                }

                /** Stands in for a CDM: applies the AES-CTR it holds the key for. */
                override fun decryptSample(
                    data: ByteArray,
                    iv: ByteArray,
                    subsamples: List<Pair<Int, Int>>,
                ): ByteArray {
                    val counter = ByteArray(16)
                    System.arraycopy(iv, 0, counter, 0, minOf(iv.size, 16))
                    val cipher = javax.crypto.Cipher.getInstance("AES/CTR/NoPadding")
                    cipher.init(
                        javax.crypto.Cipher.DECRYPT_MODE,
                        javax.crypto.spec.SecretKeySpec(contentKey, "AES"),
                        javax.crypto.spec.IvParameterSpec(counter),
                    )
                    if (subsamples.isEmpty()) return cipher.doFinal(data)
                    val out = data.copyOf()
                    var cursor = 0
                    for ((clear, encrypted) in subsamples) {
                        cursor += clear
                        val plain = cipher.update(data, cursor, encrypted)
                        System.arraycopy(plain, 0, out, cursor, plain.size)
                        cursor += encrypted
                    }
                    cipher.doFinal()
                    return out
                }

                override fun close() {
                    closed = true
                }
            }
        }
    }

    // --- tests ------------------------------------------------------------------------------------

    @Test
    fun cancelledPartialBeatAnalysisReleasesThePrimarySession() {
        val cdm = FakeCdm()
        WidevineCdmRegistry.install(cdm)
        val url = "$base/analysis.m3u8"
        SoundCloudDrm.rememberLicence(url, "analysis-token")
        com.alananasss.kittytune.audio.automix.BeatAnalyzer.analyzeSource(url,
            shouldCancel = { cdm.psshSeen != null })
        assertTrue("analysis must read a protected fragment", cdm.psshSeen != null)
        assertTrue("analysis must stop before the complete stream", fragmentRequests.get() < 1000)
        assertTrue("partial analysis left its session open", cdm.closed)
        val next = HlsStreamAdapter("$base/playlist.m3u8", emptyMap()).apply { licenseToken = "next" }
        try {
            next.getInputStream(0).use { it.readBytes() }
            assertTrue("the primary instance should be reusable", cdm.forks.isEmpty())
        } finally { next.releaseDrm() }
    }

    @Test
    fun partialBeatAnalysisDisposesItsExtraInstanceWithoutClosingPlayback() {
        val cdm = FakeCdm()
        WidevineCdmRegistry.install(cdm)
        val playing = HlsStreamAdapter("$base/long.m3u8", emptyMap()).apply { licenseToken = "playing" }
        try {
            playing.getInputStream(0).use { input ->
                val buffer = ByteArray(64 * 1024)
                input.read(buffer)
                input.read(buffer)
                val url = "$base/analysis.m3u8"
                SoundCloudDrm.rememberLicence(url, "analysis-token")
                com.alananasss.kittytune.audio.automix.BeatAnalyzer.analyzeSource(url,
                    shouldCancel = { cdm.forks.any { it.psshSeen != null } })
                assertTrue("analysis must read with its own instance", cdm.forks.isNotEmpty())
                assertTrue("analysis must stop before the complete stream", fragmentRequests.get() < 1000)
                assertTrue("extra CDM was not disposed", cdm.forks.all { it.closed && it.disposed })
                assertFalse("analysis closed the playing session", cdm.closed)
            }
        } finally { playing.releaseDrm() }
    }

    @Test
    fun overlappingAdaptersDoNotCloseThePlayingSession() {
        val cdm = FakeCdm()
        WidevineCdmRegistry.install(cdm)
        val first = HlsStreamAdapter("$base/long.m3u8", emptyMap()).apply { licenseToken = "first" }
        val second = HlsStreamAdapter("$base/long.m3u8?next", emptyMap()).apply { licenseToken = "second" }
        try {
            first.getInputStream(0).use { a ->
                val buffer = ByteArray(64 * 1024)
                a.read(buffer)
                a.read(buffer)
                second.getInputStream(0).use { b ->
                    b.read(buffer)
                    b.read(buffer)
                    assertFalse("the incoming adapter closed the playing session", cdm.closed)
                    assertTrue(a.read(buffer) > 0)
                }
            }
        } finally { first.releaseDrm(); second.releaseDrm() }
    }

    @Test
    fun protectedHeadPrefetchDoesNotOpenALicense() {
        val cdm = FakeCdm()
        WidevineCdmRegistry.install(cdm)
        val url = "$base/long.m3u8?warm"
        SoundCloudDrm.rememberLicence(url, "warm-token")
        com.alananasss.kittytune.audio.HlsHeadCache.warm(url, emptyMap())
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3)
        var ready: HlsStreamAdapter? = null
        while (ready == null && System.nanoTime() < deadline) {
            ready = com.alananasss.kittytune.audio.HlsHeadCache.take(url) { null }
            if (ready == null) Thread.sleep(5)
        }
        try {
            assertTrue("head prefetch did not complete", ready?.isEncrypted == true)
            assertEquals(0, licensePosts.get())
        } finally { ready?.releaseDrm() }
    }

    @Test
    fun aProtectedPlaylistIsLicensedDecryptedAndFedAsClearAudio() {
        val cdm = FakeCdm()
        WidevineCdmRegistry.install(cdm)

        val adapter = HlsStreamAdapter("$base/playlist.m3u8", emptyMap())
        assertTrue("playlist should be recognised as protected", adapter.isEncrypted)
        adapter.licenseToken = "test-token"
        adapter.prefetchOpening()

        val streamed = adapter.getInputStream(0).use { it.readBytes() }

        // The license was asked for once, with the request the CDM produced...
        assertEquals(1, licensePosts.get())
        assertEquals("REQUEST", String(synchronized(licenseRequests) { licenseRequests.single() }, Charsets.UTF_8))
        // ...for the PSSH the playlist declared...
        assertEquals(107, cdm.psshSeen!!.size)
        assertEquals("pssh", String(cdm.psshSeen!!, 4, 4, Charsets.US_ASCII))

        // The stream the decoder receives is plain fMP4: no sinf, no enca, no senc.
        assertFalse("clear stream must not carry sinf", streamed.containsAscii("sinf"))
        assertFalse("clear stream must not carry enca", streamed.containsAscii("enca"))
        assertFalse("clear stream must not carry senc", streamed.containsAscii("senc"))
        assertTrue("sample entry must read as mp4a", streamed.containsAscii("mp4a"))

        // ...and its samples are the ones FFmpeg's own decryption produces.
        assertEquals(expectedPlaintextSha256, sha256(mdatPayloads(streamed)))
    }

    @Test
    fun withoutACdmTheProtectedStreamFailsLoudlyInsteadOfPlayingNoise() {
        WidevineCdmRegistry.install(null)
        val adapter = HlsStreamAdapter("$base/playlist.m3u8", emptyMap())
        adapter.licenseToken = "test-token"
        assertTrue(adapter.isEncrypted)

        val failure = runCatching { adapter.getInputStream(0).use { it.readBytes() } }.exceptionOrNull()
        assertTrue("expected an IOException, got $failure", failure is java.io.IOException)
        assertTrue(
            "the message should name the missing CDM, was: ${failure?.message}",
            failure!!.message!!.contains("CDM", ignoreCase = true)
        )
        assertEquals("no license should be requested without a CDM", 0, licensePosts.get())
    }

    @Test
    fun aProtectedStreamWithoutALicenseTokenFailsBeforeFetchingFragments() {
        WidevineCdmRegistry.install(FakeCdm())
        val adapter = HlsStreamAdapter("$base/playlist.m3u8", emptyMap())
        // licenceToken deliberately left null

        val failure = runCatching { adapter.getInputStream(0).use { it.readBytes() } }.exceptionOrNull()
        assertTrue(failure is java.io.IOException)
        assertTrue(
            "message should mention the token, was: ${failure?.message}",
            failure!!.message!!.contains("license token", ignoreCase = true)
        )
        assertEquals(0, licensePosts.get())
    }

    /**
     * A `403` is the server judging the client identity, and it will judge it the same way for every
     * protected track on that machine. Remembering it is what lets `StreamResolver` stop routing
     * these tracks into a licence exchange that cannot succeed, so its provider/preview fallback
     * runs instead — the difference between a track that dies at the first fragment and one that
     * plays from somewhere else.
     */
    @Test
    fun aRefusedLicenceIsRememberedForTheRestOfTheRun() {
        assertFalse("nothing has been refused yet", SoundCloudWidevineLicense.licenceRefused)
        WidevineCdmRegistry.install(FakeCdm())
        SoundCloudWidevineLicense.playbackUrl = "$base/refuse/widevine?license_token="

        val adapter = HlsStreamAdapter("$base/playlist.m3u8", emptyMap())
        adapter.licenseToken = "test-token"
        val failure = runCatching { adapter.getInputStream(0).use { it.readBytes() } }.exceptionOrNull()

        assertTrue("the adapter must fail rather than feed ciphertext, got $failure", failure is java.io.IOException)
        assertTrue(
            "a refused licence must be remembered, message was: ${failure?.cause?.message ?: failure?.message}",
            SoundCloudWidevineLicense.licenceRefused
        )
        assertEquals("only one exchange should be attempted", 1, licenseRefusals.get())
    }

    /**
     * With licensing off, a playlist that only offers encrypted presets must yield *no* candidate, so
     * the resolver falls through to its providers and its 30 s preview instead of picking a stream it
     * cannot decrypt. With licensing on, the same list must yield the Widevine preset.
     */
    @Test
    fun drmOnlyTranscodingsYieldNoCandidateWhenLicensingIsOff() {
        val drmOnly = listOf(
            Transcoding(
                url = "$base/ctr-encrypted/aac_160k",
                preset = "aac_160k",
                duration = 135_543,
                format = Format(protocol = "ctr-encrypted-hls", mimeType = "audio/mp4"),
            ),
            Transcoding(
                url = "$base/cbc-encrypted/aac_160k",
                preset = "aac_160k",
                duration = 135_543,
                format = Format(protocol = "cbc-encrypted-hls", mimeType = "audio/mp4"),
            ),
        )

        assertTrue(
            "no encrypted candidate (Widevine nor FairPlay) without a usable licence",
            StreamResolver.buildTranscodingCandidates(drmOnly, "HIGH", forDownload = false, allowDrm = false)
                .none { it.format?.protocol?.contains("encrypted", ignoreCase = true) == true }
        )
        assertTrue(
            "the Widevine preset must be offered when licensing is on",
            StreamResolver.buildTranscodingCandidates(drmOnly, "HIGH", forDownload = false, allowDrm = true)
                .any { it.format?.protocol == "ctr-encrypted-hls" }
        )
    }

    // --- helpers ----------------------------------------------------------------------------------

    private fun mdatPayloads(stream: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        for (box in IsoBmff.boxes(stream, 0, stream.size)) {
            if (box.type == "mdat") out.write(stream, box.payload, box.size - box.header)
        }
        return out.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun ByteArray.containsAscii(token: String): Boolean {
        val needle = token.toByteArray(Charsets.US_ASCII)
        if (needle.isEmpty() || size < needle.size) return false
        outer@ for (i in 0..size - needle.size) {
            for (j in needle.indices) if (this[i + j] != needle[j]) continue@outer
            return true
        }
        return false
    }
}
