package com.alananasss.kittytune

import com.alananasss.kittytune.data.drm.CencDecrypter
import com.alananasss.kittytune.data.drm.HlsEncryptionInfo
import com.alananasss.kittytune.data.drm.IsoBmff
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * Validates the CENC port against a fragment encrypted by FFmpeg itself.
 *
 * `cenc_small.mp4` is a 2 s AAC tone muxed with `-encryption_scheme cenc-aes-ctr` (key `0f0e…0100`,
 * KID `a1b2…8f90`), which is the same layout SoundCloud serves as `ctr-encrypted-hls`: `ftyp`+`moov`
 * init segment carrying `enca`/`sinf`/`tenc`, then `moof`+`mdat` fragments with `senc`.
 *
 * [decryptMatchesFfmpeg] asserts the decrypted sample bytes hash to the value FFmpeg's own
 * `-decryption_key` path produces for the same file. That makes the reference an implementation we
 * did not write, so a mistake in the IV layout, the counter block, the sample offsets or the
 * `trun` data-offset fixup shows up as a hash mismatch instead of as noise in a decoder.
 */
class CencDecrypterTest {

    private val contentKey = IsoBmff.unhex("0f0e0d0c0b0a09080706050403020100")!!
    private val expectedKeyId = "a1b2c3d4e5f60718293a4b5c6d7e8f90"

    /** SHA-256 of the clear sample bytes, produced by `ffmpeg -decryption_key` on the same fixture. */
    private val expectedPlaintextSha256 =
        "92bf2b81e6ae2a5f2f81460ab6817ea9edbfdacbd436c2ea42d7d43fb2e4831f"

    private val fixture: ByteArray by lazy {
        javaClass.classLoader!!.getResourceAsStream("drm/cenc_small.mp4")!!.use { it.readBytes() }
    }

    // --- fixture layout ---------------------------------------------------------------------------

    private fun initSegment(): ByteArray {
        var end = 0
        for (box in IsoBmff.boxes(fixture, 0, fixture.size)) {
            if (box.type == "moof") break
            end = box.end
        }
        return fixture.copyOfRange(0, end)
    }

    private fun mediaSegment(): ByteArray {
        var start = -1
        var end = 0
        for (box in IsoBmff.boxes(fixture, 0, fixture.size)) {
            if (box.type == "moof" && start < 0) start = box.start
            if (box.type == "mdat") end = box.end
        }
        return fixture.copyOfRange(start, end)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun mdatPayload(segment: ByteArray): ByteArray =
        IsoBmff.boxes(segment, 0, segment.size)
            .filter { it.type == "mdat" }
            .fold(ByteArray(0)) { acc, box -> acc + segment.copyOfRange(box.payload, box.end) }

    // --- init segment -----------------------------------------------------------------------------

    @Test
    fun parseInitReadsTheTrackEncryption() {
        val track = CencDecrypter.parseInit(initSegment())
        assertNotNull("init segment should be seen as protected", track)
        assertEquals("cenc", track!!.scheme)
        assertEquals(8, track.ivSize)
        assertEquals(expectedKeyId, track.keyIdHex)
    }

    @Test
    fun clearInitDropsSinfAndRestoresTheOriginalFormat() {
        val init = initSegment()
        val clear = CencDecrypter.clearInitSegment(init)

        assertTrue("fixture should start protected", init.containsAscii("enca"))
        assertTrue("fixture should carry a sinf box", init.containsAscii("sinf"))

        assertFalse("cleared init must not keep sinf", clear.containsAscii("sinf"))
        assertFalse("cleared init must not keep enca", clear.containsAscii("enca"))
        assertTrue("sample entry must be restored to mp4a", clear.containsAscii("mp4a"))

        // Dropping sinf shortens enca, stsd, stbl, minf, mdia, trak and moov alike.
        assertTrue("cleared init must be smaller", clear.size < init.size)
        assertBoxesAreConsistent(clear)

        // With the encryption boxes gone, the init no longer parses as protected.
        assertNull(CencDecrypter.parseInit(clear))
        // ...and clearing an already-clear init changes nothing.
        assertArrayEquals(clear, CencDecrypter.clearInitSegment(clear))
    }

    // --- media segment ----------------------------------------------------------------------------

    @Test
    fun decryptMatchesFfmpeg() {
        val init = initSegment()
        val track = CencDecrypter.parseInit(init)!!
        val media = mediaSegment()

        assertTrue("fixture should carry a senc box", media.containsAscii("senc"))

        val clear = CencDecrypter.decryptSegment(media, contentKey, track)

        assertFalse("decrypted segment must not keep senc", clear.containsAscii("senc"))
        assertFalse("decrypted segment must not keep saiz", clear.containsAscii("saiz"))
        assertFalse("decrypted segment must not keep saio", clear.containsAscii("saio"))
        assertBoxesAreConsistent(clear)

        assertEquals(
            "decrypted samples must be byte-identical to FFmpeg's own decryption",
            expectedPlaintextSha256,
            sha256(mdatPayload(clear)),
        )
    }

    @Test
    fun decryptingWithTheWrongKeyDoesNotProduceThePlaintext() {
        val track = CencDecrypter.parseInit(initSegment())!!
        val wrongKey = "000102030405060708090a0b0c0d0e0f".let { IsoBmff.unhex(it)!! }
        val clear = CencDecrypter.decryptSegment(mediaSegment(), wrongKey, track)

        // The container rewrite still happens; only the payload is wrong. That is exactly why a bad
        // license must fail before playback, not during it.
        assertFalse(clear.containsAscii("senc"))
        assertTrue(sha256(mdatPayload(clear)) != expectedPlaintextSha256)
    }

    @Test
    fun fairplaySchemeIsRefusedRatherThanPlayedAsNoise() {
        val track = CencDecrypter.parseInit(initSegment())!!.copy(scheme = "cbcs")
        try {
            CencDecrypter.decryptSegment(mediaSegment(), contentKey, track)
            throw AssertionError("expected UnsupportedSchemeException")
        } catch (e: CencDecrypter.UnsupportedSchemeException) {
            assertTrue(e.message!!.contains("cbcs"))
        }
    }

    // --- playlist key -----------------------------------------------------------------------------

    /**
     * The exact `#EXT-X-KEY` line SoundCloud serves for `bladee1000/it-girl`, alongside a PlayReady
     * line for the same fragments. The Widevine one has to be the one selected.
     */
    private val realPlaylist = """
        #EXTM3U
        #EXT-X-VERSION:7
        #EXT-X-KEY:METHOD=SAMPLE-AES,URI="data:text/plain;base64,AAAAa3Bzc2gAAAAA7e+LqXnWSs6jyCfc1R0h7QAAAEsSEBb7NIiL+lkEpnFuMsqjKa4aC2J1eWRybWtleW9zIiQwNGJjMjkxYy1jM2E0LTQzMTAtYWNjNy0wMDU3MGYyNTk5ODZI49yVmwY=",IV=0x16fb34888bfa5904a6716e32caa329ae,KEYID=0x16fb34888bfa5904a6716e32caa329ae,KEYFORMAT="urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed",KEYFORMATVERSIONS="1"
        #EXT-X-KEY:METHOD=SAMPLE-AES-CTR,URI="data:text/plain;charset=UTF-16;base64,AAQAAAEAAQD2AzwA",KEYID=0x16fb34888bfa5904a6716e32caa329ae,KEYFORMAT="com.microsoft.playready",KEYFORMATVERSIONS="1"
        #EXT-X-MAP:URI="https://playback.media-streaming.soundcloud.cloud/cenc/x/aac_160k/h/init.mp4"
        #EXTINF:10.008,
        https://playback.media-streaming.soundcloud.cloud/cenc/x/aac_160k/h/data000.m4s
    """.trimIndent()

    @Test
    fun playlistKeySelectsWidevineOverPlayReady() {
        val info = HlsEncryptionInfo.parse(realPlaylist)
        assertNotNull(info)
        assertTrue("the Widevine key line must win", info!!.isWidevine)
        assertEquals("SAMPLE-AES", info.method)
        assertEquals("16fb34888bfa5904a6716e32caa329ae", info.keyIdHex)
        assertTrue(info.isSampleAes)

        val pssh = info.pssh
        assertNotNull("PSSH must be decoded out of the data: URI", pssh)
        assertEquals(107, pssh!!.size)
        assertEquals("pssh", String(pssh, 4, 4, Charsets.US_ASCII))
        // Widevine's system id, big-endian: edef8ba9-79d6-4ace-a3c8-27dcd51d21ed
        assertEquals("edef8ba979d64acea3c827dcd51d21ed", IsoBmff.hex(pssh, 12, 28))
    }

    @Test
    fun aClearPlaylistDeclaresNoEncryption() {
        assertNull(HlsEncryptionInfo.parse("#EXTM3U\n#EXTINF:10,\nhttps://cdn/x.m4s\n"))
    }

    // --- helpers ----------------------------------------------------------------------------------

    private fun ByteArray.containsAscii(token: String): Boolean {
        val needle = token.toByteArray(Charsets.US_ASCII)
        if (needle.isEmpty() || size < needle.size) return false
        outer@ for (i in 0..size - needle.size) {
            for (j in needle.indices) if (this[i + j] != needle[j]) continue@outer
            return true
        }
        return false
    }

    /** Every box must end exactly where its parent says it does; a broken size shows up as garbage. */
    private fun assertBoxesAreConsistent(bytes: ByteArray) {
        fun walk(from: Int, to: Int) {
            var off = from
            while (off + 8 <= to) {
                val box = IsoBmff.boxAt(bytes, off, to)
                assertNotNull("box at $off is malformed inside [$from,$to)", box)
                val childStart = IsoBmff.childStart(box!!.type, box.header)
                if (childStart != null && box.start + childStart <= box.end) {
                    walk(box.start + childStart, box.end)
                }
                assertEquals("box at $off must end inside its parent", box.end, off + box.size)
                off = box.end
            }
            assertEquals("children must fill their parent exactly", to, off)
        }
        walk(0, bytes.size)
    }
}
