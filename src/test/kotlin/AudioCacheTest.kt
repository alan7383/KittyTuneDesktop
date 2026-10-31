package com.alananasss.kittytune

import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.data.DownloadManager
import com.alananasss.kittytune.data.StreamResolver
import com.alananasss.kittytune.data.cache.AudioCache
import com.alananasss.kittytune.data.cache.isCompleteCacheFile
import com.alananasss.kittytune.domain.Format
import com.alananasss.kittytune.domain.Transcoding
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.ShortBuffer
import kotlin.math.abs
import kotlin.math.sin

/**
 * Guards the cache predicate behind AudioCache.lookup: an in-progress `.part` (or any
 * empty file) must never be served as a playable track. Serving the empty `.part` made
 * the decoder fail its open, and the player error path retried that same file every
 * couple of seconds without end.
 */
class AudioCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `in-progress part files are not servable`() {
        assertFalse(isCompleteCacheFile(tmp.newFile("1471550845.part")))
    }

    @Test
    fun `empty finals are not servable`() {
        assertFalse(isCompleteCacheFile(tmp.newFile("1471550845.mp3")))
    }

    @Test
    fun `finished downloads are servable`() {
        val done = tmp.newFile("1471550845.mp3")
        done.writeBytes(byteArrayOf(1, 2, 3))
        assertTrue(isCompleteCacheFile(done))
    }

    @Test
    fun `directories are not servable`() {
        assertFalse(isCompleteCacheFile(tmp.newFolder("1471550845.mp3")))
    }

    @Test
    fun `remuxHls preserves 160kbps AAC stream without re-encoding to 64kbps`() {
        val srcFile = tmp.newFile("source_160k.m4a")
        val outFile = tmp.newFile("remuxed.part")
        val sampleRate = 44100
        val channels = 2

        FFmpegFrameRecorder(srcFile.absolutePath, channels).use { rec ->
            rec.format = "mp4"
            rec.sampleRate = sampleRate
            rec.audioChannels = channels
            rec.audioCodec = avcodec.AV_CODEC_ID_AAC
            rec.audioBitrate = 160_000
            rec.start()
            val framesPerChunk = 1024
            val pcm = ShortArray(framesPerChunk * channels)
            var sampleIdx = 0
            repeat(90) {
                for (i in 0 until framesPerChunk) {
                    val vL = (sin(sampleIdx * 0.09) * 18000 + sin(sampleIdx * 0.37) * 9000).toInt().toShort()
                    val vR = (sin(sampleIdx * 0.11) * 18000 + sin(sampleIdx * 0.43) * 9000).toInt().toShort()
                    pcm[i * 2] = vL
                    pcm[i * 2 + 1] = vR
                    sampleIdx++
                }
                rec.recordSamples(sampleRate, channels, ShortBuffer.wrap(pcm))
            }
        }

        val inputBitrate = FFmpegFrameGrabber(srcFile.absolutePath).use { g ->
            g.start()
            g.audioBitrate
        }
        assertTrue("Expected synthetic input >= 140kbps, got $inputBitrate", inputBitrate >= 140_000)

        DownloadManager.remuxHls(srcFile.absolutePath, outFile) {}

        FFmpegFrameGrabber(outFile.absolutePath).use { g ->
            g.start()
            assertTrue("Expected remuxed bitrate >= 140kbps (never 64kbps default), got ${g.audioBitrate}", g.audioBitrate >= 140_000)
            assertTrue("Expected stream-copied bitrate to match source ($inputBitrate vs ${g.audioBitrate})", abs(inputBitrate - g.audioBitrate) < 2_000)
            assertTrue("Expected file size to match source within MP4 header bytes", abs(srcFile.length() - outFile.length()) < 256)
        }
    }

    @Test
    fun `ensureMigrated evicts legacy 64kbps m4a files while keeping mp3 files`() {
        val dir = tmp.newFolder("tracks")
        val legacyM4a = dir.resolve("2425161693.m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val validMp3 = dir.resolve("999999.mp3").apply { writeBytes(byteArrayOf(4, 5, 6)) }

        val prevVersion = Prefs.getInt(AudioCache.KEY_REMUX_VERSION, 0)
        try {
            Prefs.putInt(AudioCache.KEY_REMUX_VERSION, 1)
            AudioCache.ensureMigrated(dir)
            assertFalse("Legacy .m4a file should be evicted on migration", legacyM4a.exists())
            assertTrue("Progressive .mp3 file should be preserved", validMp3.exists())
        } finally {
            Prefs.putInt(AudioCache.KEY_REMUX_VERSION, prevVersion)
        }
    }

    @Test
    fun `buildTranscodingCandidates prioritizes aac_160k HQ Free over 128k progressive and 96k HLS`() {
        val progressiveMp3 = Transcoding(
            url = "https://api-v2.soundcloud.com/stream/prog",
            preset = "mp3_0_0",
            format = Format(protocol = "progressive", mimeType = "audio/mpeg"),
            snipped = false
        )
        val aac96 = Transcoding(
            url = "https://api-v2.soundcloud.com/stream/aac96",
            preset = "aac_96k",
            format = Format(protocol = "hls", mimeType = "audio/mp4; codecs=\"mp4a.40.2\""),
            snipped = false
        )
        val aac160 = Transcoding(
            url = "https://api-v2.soundcloud.com/stream/aac160",
            preset = "aac_160k",
            format = Format(protocol = "hls", mimeType = "audio/mp4; codecs=\"mp4a.40.2\""),
            snipped = false
        )

        val candidates = StreamResolver.buildTranscodingCandidates(
            transcodings = listOf(aac96, progressiveMp3, aac160),
            qualityPref = "HIGH",
            forDownload = false,
            allowDrm = false
        )

        assertEquals(listOf(aac160, progressiveMp3, aac96), candidates)
    }
}
