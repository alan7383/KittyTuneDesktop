package com.alananasss.kittytune.audio

import com.sun.net.httpserver.HttpServer
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.FrameGrabber
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ShortBuffer
import java.util.concurrent.Executors
import kotlin.math.sin
import kotlin.test.assertEquals

/**
 * A slow CDN must not make playback stop at every fragment boundary.
 *
 * The decoder only runs about 200 ms ahead of the speakers (the output line's buffer). The adapter used
 * to request each fragment when the decoder reached it, on the decoder's thread, so any fragment slower
 * than that to arrive left the line empty: on SoundCloud's 10-second fragments, a freeze every ten seconds.
 */
class HlsFragmentStallTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `a slow fragment server does not starve a 200 ms output line`() {
        val file = temp.newFile("fragmented.m4a")
        recordFragmentedAac(file, seconds = 7)
        val bytes = file.readBytes()
        val boxes = ByteBuffer.wrap(bytes)
        val fragmentStarts = buildList {
            while (boxes.remaining() >= 8) {
                val start = boxes.position()
                val size = boxes.int
                if (boxes.int == 0x6D6F6F66) add(start) // moof
                boxes.position(start + size)
            }
            add(bytes.size)
        }
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/init.mp4") { exchange ->
            exchange.sendResponseHeaders(200, fragmentStarts.first().toLong())
            exchange.responseBody.use { it.write(bytes, 0, fragmentStarts.first()) }
        }
        val playlist = buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-VERSION:7")
            appendLine("#EXT-X-TARGETDURATION:1")
            appendLine("#EXT-X-MAP:URI=\"init.mp4\"")
            fragmentStarts.zipWithNext().forEachIndexed { index, (start, end) ->
                appendLine("#EXTINF:1.0,")
                appendLine("fragment-$index.m4s")
                server.createContext("/fragment-$index.m4s") { exchange ->
                    Thread.sleep(SERVER_DELAY_MS) // A distant or congested CDN edge.
                    exchange.sendResponseHeaders(200, (end - start).toLong())
                    exchange.responseBody.use { it.write(bytes, start, end - start) }
                }
            }
            appendLine("#EXT-X-ENDLIST")
        }.toByteArray()
        server.createContext("/track.m3u8") { exchange ->
            exchange.sendResponseHeaders(200, playlist.size.toLong())
            exchange.responseBody.use { it.write(playlist) }
        }
        server.start()
        try {
            val adapter = HlsStreamAdapter("http://127.0.0.1:${server.address.port}/track.m3u8", emptyMap())
            val grabber = FFmpegFrameGrabber(adapter.getInputStream(0), 0).apply {
                format = "mp4"
                setOption("probesize", "32768")
                setOption("analyzeduration", "0")
                sampleRate = 44_100
                audioChannels = 2
                sampleMode = FrameGrabber.SampleMode.FLOAT
            }
            try {
                grabber.start()
                val underruns = playAgainstLine(grabber, lineMs = 200.0)
                assertEquals(emptyList(), underruns, "the line ran dry at these positions (s)")
            } finally {
                grabber.release()
            }
        } finally {
            server.stop(0)
        }
    }

    /** Decodes in real time into a simulated output line and returns where it ran out of audio. */
    private fun playAgainstLine(grabber: FFmpegFrameGrabber, lineMs: Double): List<String> {
        val underruns = mutableListOf<String>()
        var decodedMs = 0.0
        var startNs = 0L
        var silentMs = 0.0
        fun playedMs() = (System.nanoTime() - startNs) / 1e6 - silentMs
        while (true) {
            val frame = grabber.grabSamples() ?: break
            val samples = frame.samples?.get(0)?.remaining() ?: continue
            if (startNs == 0L) startNs = System.nanoTime()
            val queued = decodedMs - playedMs()
            if (queued < -20) underruns += "%.1f (%.0f ms silent)".format(decodedMs / 1000, -queued)
            if (queued < 0) silentMs += -queued
            decodedMs += samples / 2.0 / 44.1
            // SourceDataLine.write blocks once the line holds lineMs of audio.
            val ahead = decodedMs - playedMs()
            if (ahead > lineMs) Thread.sleep((ahead - lineMs).toLong())
        }
        return underruns
    }

    private fun recordFragmentedAac(file: File, seconds: Int) {
        val sampleRate = 44_100
        FFmpegFrameRecorder(file, 2).use { recorder ->
            recorder.format = "mp4"
            recorder.sampleRate = sampleRate
            recorder.audioCodec = avcodec.AV_CODEC_ID_AAC
            recorder.audioBitrate = 160_000
            recorder.setOption("movflags", "empty_moov+default_base_moof+frag_keyframe")
            recorder.setOption("frag_duration", "1000000")
            recorder.start()
            val pcm = ShortArray(1024 * 2)
            var sample = 0
            repeat(seconds * sampleRate / 1024) {
                for (i in 0 until 1024) {
                    pcm[i * 2] = (sin(sample * 0.09) * 18_000).toInt().toShort()
                    pcm[i * 2 + 1] = (sin(sample * 0.11) * 18_000).toInt().toShort()
                    sample++
                }
                recorder.recordSamples(sampleRate, 2, ShortBuffer.wrap(pcm))
            }
        }
    }

    private companion object {
        const val SERVER_DELAY_MS = 450L
    }
}
