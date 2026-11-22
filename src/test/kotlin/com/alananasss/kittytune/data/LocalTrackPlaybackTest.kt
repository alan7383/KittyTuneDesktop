package com.alananasss.kittytune.data

import com.alananasss.kittytune.audio.SourceAudioFormat
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.LocalTrack
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import kotlinx.coroutines.runBlocking
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.junit.Test
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LocalTrackPlaybackTest {
    private val row = LocalTrack(
        id = -485497272, title = "chanson", artist = "Artiste", artworkUrl = "", duration = 6000,
        localAudioPath = "C:/Music/chanson.mp3", source = "local", localArtworkPath = "",
    )

    /** The library rebuilds the track without a source, so it carries Track's default "soundcloud". */
    @Test fun aLocalFileRebuiltWithoutItsSourceStillFindsItsFile() {
        val rebuilt = Track(id = row.id, title = row.title, artworkUrl = null, durationMs = row.duration, user = User(0, row.artist, null))
        assertEquals("soundcloud", rebuilt.source)
        assertTrue(row.matchesPlayback(rebuilt))
        assertTrue(row.matchesPlayback(rebuilt.copy(source = "local")))
        assertEquals("local", row.playbackSource)
        assertEquals("local", row.copy(source = null).playbackSource)
    }

    @Test fun aCatalogTrackWithTheSameIdDoesNotTakeTheLocalFile() {
        val other = Track(id = row.id, title = "other", artworkUrl = null, durationMs = 6000, user = User(0, "x", null),
            source = "soundcloud", permalinkUrl = "https://soundcloud.com/a/b")
        assertFalse(row.matchesPlayback(other))
        assertFalse(row.matchesPlayback(other.copy(source = "spotify", permalinkUrl = null)))
    }

    @Test fun filesTheTagReaderRejectsAreStillImported() {
        AppDatabase.init()
        val dir = File(System.getProperty("java.io.tmpdir"), "local-import été").apply { deleteRecursively(); mkdirs() }
        for ((name, format, codec, rate) in listOf(
            Quad("opus é.opus", "ogg", avcodec.AV_CODEC_ID_OPUS, 48_000),
            Quad("raw.aac", "adts", avcodec.AV_CODEC_ID_AAC, 44_100),
        )) {
            val file = File(dir, name)
            FFmpegFrameRecorder(file, 2).apply {
                this.format = format; audioCodec = codec; sampleRate = rate; audioBitrate = 128_000
                start()
                repeat(rate * 3 / 960) { block ->
                    val buf = FloatBuffer.allocate(960 * 2)
                    repeat(960) { i -> val v = (0.2 * sin(2 * PI * 440 * (block * 960 + i) / rate)).toFloat(); buf.put(v); buf.put(v) }
                    buf.flip(); recordSamples(rate, 2, buf)
                }
                stop(); release()
            }
            val track = assertNotNull(runBlocking { LocalMediaRepository.importFile(file) }, name)
            assertEquals("local", track.source)
            assertTrue(track.durationMs!! in 2_500L..3_500L, "$name: ${track.durationMs}")
        }
    }

    @Test fun aFragmentedMp4sBogusBitrateGivesWayToWhatTheFileWeighs() {
        // YouTube's itag 140, as FFmpeg reported it: 6 kbps for a 3.4 MB, 213 s file.
        val measured = SourceAudioFormat.measuredBitrate(3_440_000, 213_000_000)!!
        assertEquals(measured, SourceAudioFormat.chooseBitrate(6_000, measured))
        assertTrue(measured in 125_000L..135_000L)
        // A container that agrees keeps its nominal figure.
        assertEquals(160_000, SourceAudioFormat.chooseBitrate(160_000, 161_400))
        // No claim (Ogg) stays no claim: the weight would count cover art in the tags.
        assertEquals(0, SourceAudioFormat.chooseBitrate(0, 300_000))
        assertEquals(null, SourceAudioFormat.measuredBitrate(0, 213_000_000))
    }

    private data class Quad(val a: String, val b: String, val c: Int, val d: Int)
}
