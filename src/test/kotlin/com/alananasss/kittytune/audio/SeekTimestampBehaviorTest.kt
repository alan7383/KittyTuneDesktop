package com.alananasss.kittytune.audio

import com.alananasss.kittytune.data.StreamResolver
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.runBlocking
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SeekTimestampBehaviorTest {

    @Test
    fun testHlsSeekPruningAccuracy() = runBlocking {
        val trackId = 2067917628L
        val api = RetrofitClient.create()
        val fetched: List<Track> = api.getTracksByIds(trackId.toString())
        val track = fetched.firstOrNull()
        assertNotNull(track)

        val transcodings = StreamResolver.buildTranscodingCandidates(
            track.media?.transcodings.orEmpty(),
            "HIGH",
            forDownload = false,
            allowDrm = false
        )
        val candidate = transcodings.firstOrNull()
        assertNotNull(candidate)

        val authClient = okhttp3.OkHttpClient()
        val clientId = "SSdQ80vM8nLPhbDBylHl2JFK6ElhBr9B"
        val candidateUrlWithAuth = if (!candidate.url.contains("client_id=")) {
            "${candidate.url}?client_id=$clientId"
        } else candidate.url

        val streamInfoRequest = okhttp3.Request.Builder().url(candidateUrlWithAuth).build()
        val streamInfoResponse = authClient.newCall(streamInfoRequest).execute()
        val bodyStr = streamInfoResponse.body.string()
        val mediaUrl = org.json.JSONObject(bodyStr).getString("url")

        val adapter = HlsStreamAdapter(mediaUrl, emptyMap()) { null }

        // Test seeking to 69,000 ms (middle of a 10s segment that starts at 60,000 ms)
        val seekMs = 69_000L
        val targetTimestamp = seekMs * 1000L
        val inputStream = adapter.getInputStream(seekMs)
        val grabber = FFmpegFrameGrabber(inputStream, 0).apply {
            if (adapter.hasInitSegment) format = "mp4"
            sampleRate = 44100
            audioChannels = 2
            sampleMode = FrameGrabber.SampleMode.FLOAT
        }
        grabber.start()

        val seekFrame = try { grabber.grabSamples() } catch (_: Exception) { null }
        assertNotNull(seekFrame)

        // Pruning loop as implemented in our AudioEngine fix:
        var f = seekFrame
        var droppedCount = 0
        var lastTs = Long.MIN_VALUE
        while (f != null) {
            val ts = grabber.timestamp
            if (ts >= targetTimestamp - 25_000L || (lastTs != Long.MIN_VALUE && ts <= lastTs)) {
                break
            }
            lastTs = ts
            f = try { grabber.grabSamples() } catch (_: Exception) { null }
            droppedCount++
            if (droppedCount > 1500) break
        }

        assertNotNull(f)
        val finalTsMs = grabber.timestamp / 1000L
        println("Seeked to $seekMs ms -> Pruned $droppedCount frames -> Landed at $finalTsMs ms (diff: ${abs(finalTsMs - seekMs)} ms)")

        // The landed position must be within 50 ms of the target 69,000 ms (instead of being 10,000 ms before!)
        assertTrue(abs(finalTsMs - seekMs) < 50L, "Seek position ($finalTsMs ms) should be within 50 ms of target ($seekMs ms)")

        // Next frame grabbed must continue advancing past seekMs
        val nextFrame = grabber.grabSamples()
        assertNotNull(nextFrame)
        val nextTsMs = grabber.timestamp / 1000L
        println("Next frame at $nextTsMs ms")
        assertTrue(nextTsMs >= finalTsMs, "Next frame timestamp should advance")

        grabber.stop()
        grabber.release()
    }
}
