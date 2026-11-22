package com.alananasss.kittytune.audio

import com.alananasss.kittytune.data.LocalMediaRepository
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.ui.player.AudioEffectsState
import com.alananasss.kittytune.ui.player.EqualizerState
import kotlinx.coroutines.runBlocking
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.junit.Test
import java.io.File
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioFxExporterTest {
    private fun source(dir: File): File {
        val file = File(dir, "source.flac")
        FFmpegFrameRecorder(file, 2).apply {
            format = "flac"; audioCodec = avcodec.AV_CODEC_ID_FLAC; sampleRate = 44_100
            setMetadata(mapOf("title" to "Sine", "artist" to "Kitty"))
            start()
            repeat(44_100 * 5 / 1024) { block ->
                val buf = FloatBuffer.allocate(2048)
                repeat(1024) { i -> val t = (block * 1024 + i) / 44_100.0; val v = (0.15 * sin(2 * PI * 80 * t) + 0.1 * sin(2 * PI * 1000 * t)).toFloat(); buf.put(v); buf.put(v) }
                buf.flip(); recordSamples(44_100, 2, buf)
            }
            stop(); release()
        }
        return file
    }

    private fun decode(file: File): Pair<Long, Double> {
        val g = FFmpegFrameGrabber(file).apply { sampleMode = org.bytedeco.javacv.FrameGrabber.SampleMode.SHORT; audioChannels = 2; sampleRate = 44_100 }
        g.start()
        var n = 0L; var sum = 0.0
        while (true) {
            val f = g.grabSamples() ?: break
            val b = f.samples[0] as ShortBuffer
            while (b.hasRemaining()) { val s = b.get() / 32768.0; sum += s * s; n++ }
        }
        g.release()
        return (n / 2 * 1000 / 44_100) to sqrt(sum / n.coerceAtLeast(1))
    }

    private fun exportAndWait(effects: AudioEffectsState, format: AudioFxExporter.Format, dir: File, name: String): File {
        AppDatabase.init()
        val track = runBlocking { LocalMediaRepository.importFile(File(dir, "source.flac")) }!!
        AudioFxExporter.export(track, effects, EqualizerState(), format, File(dir, "out"), name)
        val end = System.currentTimeMillis() + 60_000
        while (System.currentTimeMillis() < end) {
            when (val s = AudioFxExporter.state.value) {
                is AudioFxExporter.State.Done -> { AudioFxExporter.dismiss(); return s.file }
                is AudioFxExporter.State.Failed -> error("export failed: $s")
                else -> Thread.sleep(50)
            }
        }
        error("export timed out")
    }

    @Test fun theExportSoundsLikeThePlaybackWithEveryEffect() {
        val dir = File(System.getProperty("java.io.tmpdir"), "fx-export").apply { deleteRecursively(); mkdirs() }
        source(dir)
        val (dryMs, dryRms) = decode(File(dir, "source.flac"))
        val fx = AudioEffectsState(speed = 0.8f, isPitchEnabled = true, isBassBoostEnabled = true, bassBoostIntensity = 0.2f, isEarrapeEnabled = true, earrapeIntensity = 0.1f)
        for (format in AudioFxExporter.Format.entries) {
            val out = exportAndWait(fx, format, dir, "Kitty - Sine (FX)")
            assertEquals(format.extension, out.extension)
            val (ms, rms) = decode(out)
            println("EXPORT ${out.name}: ${out.length()} bytes, $ms ms (dry $dryMs ms), rms $rms (dry $dryRms)")
            assertTrue(ms in 6_000L..6_600L, "0.8x of 5 s should last ~6.25 s, got $ms")
            assertTrue(rms > dryRms * 1.2, "bass boost + earrape should be louder: $rms vs $dryRms")
            if (format != AudioFxExporter.Format.WAV) {
                assertEquals("Kitty", AudioFileIO.read(out).tag.getFirst(FieldKey.ARTIST))
            }
        }
        // Nothing overwritten: a second export with the same name gets its own file.
        val again = exportAndWait(fx, AudioFxExporter.Format.MP3, dir, "Kitty - Sine (FX)")
        assertEquals("Kitty - Sine (FX) (2).mp3", again.name)
        assertTrue(File(dir, "out").listFiles()!!.none { it.name.endsWith(".part") })
    }
}
