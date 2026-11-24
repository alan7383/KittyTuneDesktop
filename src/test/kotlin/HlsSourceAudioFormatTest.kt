package com.alananasss.kittytune.audio

import com.sun.net.httpserver.HttpServer
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ShortBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HlsSourceAudioFormatTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `AAC bitrate uses the media fragment duration when FFmpeg only probes the opening`() {
        verifyFragmentBitrate(globalIndex = true, startFragment = 0)
    }

    @Test
    fun `AAC bitrate uses the media fragment duration when HLS opens at a seek position`() {
        verifyFragmentBitrate(globalIndex = false, startFragment = 9)
    }

    private fun verifyFragmentBitrate(globalIndex: Boolean, startFragment: Int) {
        val file = temp.newFile("fragmented.m4a")
        recordFragmentedAac(file, globalIndex)
        val bytes = file.readBytes()
        val boxes = ByteBuffer.wrap(bytes)
        // A global index is part of the init, while DASH writes an index before each fragment.
        val fragmentBox = if (globalIndex) 0x6D6F6F66 else 0x73696478 // moof or sidx
        val fragmentStarts = buildList {
            while (boxes.remaining() >= 8) {
                val start = boxes.position()
                val size = boxes.int
                if (boxes.int == fragmentBox) add(start)
                boxes.position(start + size)
            }
            add(bytes.size)
        }
        val mediaRequests = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/init.mp4") { exchange ->
            exchange.sendResponseHeaders(200, fragmentStarts.first().toLong())
            exchange.responseBody.use { it.write(bytes, 0, fragmentStarts.first()) }
        }
        val playlist = buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-VERSION:7")
            appendLine("#EXT-X-TARGETDURATION:2")
            appendLine("#EXT-X-MAP:URI=\"init.mp4\"")
            fragmentStarts.zipWithNext().forEachIndexed { index, (start, end) ->
                val path = "fragment-$index.m4s"
                val duration = if (index == fragmentStarts.size - 2) "0.998" else "1.091"
                appendLine("#EXTINF:$duration,")
                appendLine(path)
                server.createContext("/$path") { exchange ->
                    mediaRequests.incrementAndGet()
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
            FFmpegFrameGrabber(adapter.getInputStream(startFragment * 1091L), 0).use { grabber ->
                grabber.format = "mp4"
                grabber.setOption("probesize", "32768")
                grabber.setOption("analyzeduration", "0")
                grabber.start()

                val openedFragments = mediaRequests.get()
                assertTrue(openedFragments in 1 until (fragmentStarts.lastIndex - startFragment))
                // The index advertises an end timestamp longer than the probed audio span.
                val uncorrected = assertNotNull(SourceAudioFormat.of(grabber))
                assertTrue(uncorrected.bitrateBps in 8_000L..25_000L, uncorrected.badgeText())

                val format = assertNotNull(SourceAudioFormat.of(grabber, adapter.fragmentBitrateBps))
                assertEquals("AAC", format.codec)
                assertEquals(44_100, format.sampleRateHz)
                assertTrue(format.bitrateBps in 145_000L..175_000L, format.badgeText())

                val unknownBitrate = assertNotNull(SourceAudioFormat.of(grabber, 0L))
                assertEquals("AAC • 44.1 kHz", unknownBitrate.badgeText())
                // At most the next fragment, which the adapter prefetches while the current one plays.
                assertTrue(mediaRequests.get() <= openedFragments + 1, "reading the format fetched fragments")
            }
        } finally {
            server.stop(0)
        }
    }

    private fun recordFragmentedAac(file: File, globalIndex: Boolean) {
        val sampleRate = 44_100
        FFmpegFrameRecorder(file, 2).use { recorder ->
            recorder.format = "mp4"
            recorder.sampleRate = sampleRate
            recorder.audioCodec = avcodec.AV_CODEC_ID_AAC
            recorder.audioBitrate = 160_000
            val indexFlag = if (globalIndex) "global_sidx" else "dash"
            recorder.setOption("movflags", "empty_moov+default_base_moof+frag_keyframe+$indexFlag")
            recorder.setOption("frag_duration", "1080000")
            recorder.start()
            val pcm = ShortArray(1024 * 2)
            var sample = 0
            repeat(560) {
                for (i in 0 until 1024) {
                    pcm[i * 2] = (sin(sample * 0.09) * 18_000 + sin(sample * 0.37) * 9_000).toInt().toShort()
                    pcm[i * 2 + 1] = (sin(sample * 0.11) * 18_000 + sin(sample * 0.43) * 9_000).toInt().toShort()
                    sample++
                }
                recorder.recordSamples(sampleRate, 2, ShortBuffer.wrap(pcm))
            }
        }
    }
}
