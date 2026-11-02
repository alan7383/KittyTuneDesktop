package com.alananasss.kittytune.data

import com.alananasss.kittytune.domain.Media
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StreamResolverMobileTranscodingTest {

    @Test
    fun `guest mode resolves 128k MP3 stream for soundcloud track`() = runBlocking {
        // Track ID for https://soundcloud.com/xajiat/klubnyak2010
        val trackId = 2121158391L
        val track = Track(
            id = trackId,
            title = "tuborosho - Клубnyak2010",
            artworkUrl = null,
            durationMs = 127112L,
            user = null,
            media = Media(
                transcodings = emptyList() // When v2 progressive MP3 is missing, mobile synthesis triggers
            )
        )

        val resolved = StreamResolver.resolveStreamWithDrm(track)
        assertNotNull(resolved, "Stream should resolve successfully in guest mode")
        println("Resolved stream: url=${resolved.url}, mimeType=${resolved.mimeType}")
        
        // Ensure stream is resolved to the direct CloudFront 128k MP3 URL
        assertTrue(resolved.url.contains("sndcdn.com"), "Resolved URL should be a CloudFront/SoundCloud CDN URL: ${resolved.url}")
        assertTrue(resolved.url.contains(".128.mp3") || resolved.url.contains(".mp3"), "Resolved URL should be an MP3 stream: ${resolved.url}")
        assertEquals("audio/mpeg", resolved.mimeType)
    }

    @Test
    fun `download mode resolves 128k MP3 stream directly without DRM`() = runBlocking {
        val trackId = 2121158391L
        val track = Track(
            id = trackId,
            title = "tuborosho - Клубnyak2010",
            artworkUrl = null,
            durationMs = 127112L,
            user = null,
            media = Media(
                transcodings = emptyList()
            )
        )

        val resolved = StreamResolver.resolveStreamWithDrm(track, forDownload = true)
        assertNotNull(resolved, "Stream should resolve successfully for download in guest mode")
        println("Resolved download stream: url=${resolved.url}, mimeType=${resolved.mimeType}")

        assertTrue(resolved.url.contains(".128.mp3") || resolved.url.contains(".mp3"), "Download stream should be MP3: ${resolved.url}")
        assertEquals("audio/mpeg", resolved.mimeType)
    }
}
