package com.alananasss.kittytune

import com.alananasss.kittytune.utils.SignedUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * Reading the deadline off a signed CDN URL. What these protect: SoundCloud's media links die
 * after ~3 minutes, so a cache or prefetch that goes by its own TTL hands FFmpeg a URL the CDN
 * answers 403 to — and playback only came back once its reconnect backoff gave up.
 */
class SignedUrlTest {

    /** CloudFront's substituted base64: '+' -> '-', '=' -> '_', '/' -> '~'. */
    private fun policyParam(epochSeconds: Long): String {
        val policy =
            """{"Statement":[{"Resource":"*://cf-media.sndcdn.com/x.128.mp3*","Condition":{"DateLessThan":{"AWS:EpochTime":$epochSeconds}}}]}"""
        return Base64.getEncoder().encodeToString(policy.toByteArray())
            .replace('+', '-').replace('=', '_').replace('/', '~')
    }

    private fun signedUrl(epochSeconds: Long) =
        "https://cf-media.sndcdn.com/x.128.mp3?Policy=${policyParam(epochSeconds)}&Signature=abc&Key-Pair-Id=APKAI6TU7MMXM5DG6EPQ"

    @Test
    fun `reads the deadline out of a real soundcloud policy`() {
        // The URL from the report, verbatim: its policy expired the second the user skipped.
        val url =
            "https://cf-media.sndcdn.com/ilmwwMgy3kdH.128.mp3?Policy=eyJTdGF0ZW1lbnQiOlt7IlJlc291cmNlIjoiKjovL2NmLW1lZGlhLnNuZGNkbi5jb20vaWxtd3dNZ3kza2RILjEyOC5tcDMqIiwiQ29uZGl0aW9uIjp7IkRhdGVMZXNzVGhhbiI6eyJBV1M6RXBvY2hUaW1lIjoxNzg3NjE5NzUyfX19XX0_&Signature=cgL~yavi&Key-Pair-Id=APKAI6TU7MMXM5DG6EPQ"
        assertEquals(1787619752_000L, SignedUrl.expiryEpochMs(url))
    }

    @Test
    fun `reads a canned policy and a youtube fallback`() {
        assertEquals(
            1787619752_000L,
            SignedUrl.expiryEpochMs("https://cf-media.sndcdn.com/x.mp3?Expires=1787619752&Signature=abc")
        )
        assertEquals(
            1787619752_000L,
            SignedUrl.expiryEpochMs("https://rr3---sn-x.googlevideo.com/videoplayback?expire=1787619752&itag=140")
        )
    }

    @Test
    fun `takes the earliest deadline when a url carries several`() {
        val url = "https://cf-media.sndcdn.com/x.mp3?Expires=1787619900&Policy=${policyParam(1787619752)}"
        assertEquals(1787619752_000L, SignedUrl.expiryEpochMs(url))
    }

    @Test
    fun `survives a percent-encoded policy`() {
        val encoded = policyParam(1787619752).replace("~", "%7E")
        assertEquals(
            1787619752_000L,
            SignedUrl.expiryEpochMs("https://cf-media.sndcdn.com/x.mp3?Policy=$encoded")
        )
    }

    @Test
    fun `urls without a readable deadline have none`() {
        assertNull(SignedUrl.expiryEpochMs("https://cf-media.sndcdn.com/x.128.mp3"))
        assertNull(SignedUrl.expiryEpochMs("https://api-v2.soundcloud.com/tracks?ids=1&client_id=abc"))
        assertNull(SignedUrl.expiryEpochMs("/home/user/Music/track.mp3"))
        assertNull(SignedUrl.expiryEpochMs("soundtune://track/2011037447"))
    }

    @Test
    fun `a lapsed signature is expired and a fresh one is not`() {
        val nowSeconds = System.currentTimeMillis() / 1000L
        assertTrue(SignedUrl.isExpired(signedUrl(nowSeconds - 1)))
        assertFalse(SignedUrl.isExpired(signedUrl(nowSeconds + 600)))
    }

    @Test
    fun `the margin retires a url before the cdn does`() {
        val nowSeconds = System.currentTimeMillis() / 1000L
        // Five seconds left is not enough to open, probe and start decoding.
        assertTrue(SignedUrl.isExpired(signedUrl(nowSeconds + 5)))
        assertFalse(SignedUrl.isExpired(signedUrl(nowSeconds + 5), marginMs = 0L))
    }

    @Test
    fun `nothing to expire means never expired`() {
        assertFalse(SignedUrl.isExpired("https://cf-media.sndcdn.com/x.128.mp3"))
        assertFalse(SignedUrl.isExpired("/home/user/Music/track.mp3"))
    }

    @Test
    fun `remaining time counts down to the margin`() {
        val nowSeconds = System.currentTimeMillis() / 1000L
        val remaining = SignedUrl.remainingMs(signedUrl(nowSeconds + 180))
        assertNotNull(remaining)
        assertTrue("expected ~160s of usable life, got $remaining", remaining!! in 150_000..170_000)
        assertNull(SignedUrl.remainingMs("https://cf-media.sndcdn.com/x.128.mp3"))
    }

    @Test
    fun `network urls are told apart from local paths`() {
        assertTrue(SignedUrl.isNetworkUrl("https://cf-media.sndcdn.com/x.mp3"))
        assertTrue(SignedUrl.isNetworkUrl("HTTP://example.com/x.mp3"))
        assertFalse(SignedUrl.isNetworkUrl("/home/user/Music/track.mp3"))
        assertFalse(SignedUrl.isNetworkUrl("soundtune://track/1"))
    }
}
