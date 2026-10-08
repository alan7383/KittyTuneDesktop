package com.alananasss.kittytune.media

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.alananasss.kittytune.audio.VolumeCurve
import com.alananasss.kittytune.audio.openPlaybackLine
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import java.nio.ShortBuffer
import java.util.concurrent.atomic.AtomicLong
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.SourceDataLine
import kotlin.math.min

/**
 * Plays one video file with its sound: a music video on an artist's page (tester's list, 5.5).
 *
 * One FFmpeg reader gives picture and sound in the order they are stored. Sound goes straight to the output line,
 * whose blocking writes set the pace; each picture waits until the sound has played up to its time, or is dropped
 * when it comes too late, so lips stay on the words. Without a sound track the wall clock stands in.
 */
class ClipPlayback(
    private val scope: CoroutineScope,
    /** Loudness as an amplitude factor; the music's volume by default, so a clip is as loud as a song. */
    private val gain: () -> Float = { VolumeCurve.sliderToAmplitude(MusicManager.getVolume()) },
) {

    var frame by mutableStateOf<ImageBitmap?>(null)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var isPaused by mutableStateOf(false)
        private set
    var hasEnded by mutableStateOf(false)
        private set
    var hasFailed by mutableStateOf(false)
        private set

    private val pendingSeekMs = AtomicLong(NO_SEEK)
    private var job: Job? = null

    fun play(streamUrl: String) {
        job?.cancel()
        hasEnded = false
        hasFailed = false
        isLoading = true
        job = scope.launch(Dispatchers.IO) { decode(streamUrl) }
    }

    fun togglePause() {
        if (hasEnded) {
            hasEnded = false
            seekTo(0L)
        }
        isPaused = !isPaused
    }

    fun seekTo(ms: Long) {
        pendingSeekMs.set(ms.coerceAtLeast(0L))
        positionMs = ms
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun CoroutineScope.decode(streamUrl: String) {
        runCatching { avutil.av_log_set_level(avutil.AV_LOG_ERROR) }
        var grabber: FFmpegFrameGrabber? = null
        var line: SourceDataLine? = null
        val converter = Java2DFrameConverter()
        try {
            grabber = FFmpegFrameGrabber(streamUrl).apply {
                setOption("user_agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                setOption("rw_timeout", "10000000")
                setOption("reconnect", "1")
                setOption("reconnect_streamed", "1")
                sampleFormat = avutil.AV_SAMPLE_FMT_S16
                start()
            }
            durationMs = grabber.lengthInTime / 1000
            val sampleRate = grabber.sampleRate
            val channels = grabber.audioChannels
            if (sampleRate > 0 && channels > 0) {
                line = openPlaybackLine(
                    AudioFormat(sampleRate.toFloat(), 16, channels, true, false),
                    sampleRate * channels * 2 * LINE_BUFFER_MS / 1000,
                )
            }
            val clock = Clock(line, sampleRate)
            var bytes = ByteArray(0)
            isLoading = false

            while (isActive) {
                val seekMs = pendingSeekMs.getAndSet(NO_SEEK)
                if (seekMs != NO_SEEK) {
                    grabber.setTimestamp(seekMs * 1000)
                    line?.flush()
                    clock.restartAt(seekMs * 1000)
                }
                if (isPaused) {
                    line?.stop()
                    while (isActive && isPaused && pendingSeekMs.get() == NO_SEEK) delay(PAUSED_POLL_MS)
                    line?.start()
                    // A stopped line stops counting too, so only the wall clock has to be told about the pause.
                    if (line == null) clock.restartAt(positionMs * 1000)
                    continue
                }
                val next = grabber.grab()
                if (next == null) {
                    line?.drain()
                    hasEnded = true
                    isPaused = true
                    continue
                }
                val samples = next.samples?.firstOrNull() as? ShortBuffer
                if (samples != null && line != null) {
                    val count = samples.remaining()
                    if (bytes.size < count * 2) bytes = ByteArray(count * 2)
                    val gain = gain()
                    for (i in 0 until count) {
                        val value = (samples.get(samples.position() + i) * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        bytes[i * 2] = value.toByte()
                        bytes[i * 2 + 1] = (value shr 8).toByte()
                    }
                    line.write(bytes, 0, count * 2)
                }
                if (next.image != null) {
                    val aheadUs = next.timestamp - clock.nowUs()
                    if (aheadUs < -LATE_FRAME_US) continue
                    if (aheadUs > 0) delay(min(aheadUs / 1000, MAX_FRAME_WAIT_MS))
                    converter.convert(next)?.let { frame = it.toComposeImageBitmap() }
                    positionMs = next.timestamp / 1000
                }
            }
        } catch (e: Exception) {
            Logger.w("ClipPlayback", "Clip playback failed: ${e.message}")
            hasFailed = true
            isLoading = false
        } finally {
            runCatching { line?.flush(); line?.stop(); line?.close() }
            runCatching { converter.close() }
            runCatching { grabber?.stop(); grabber?.release() }
        }
    }

    /** Where playback is, in microseconds: the sound actually played, or the wall clock when there is none. */
    private class Clock(private val line: SourceDataLine?, private val sampleRate: Int) {
        private var baseUs = 0L
        private var baseFrames = line?.longFramePosition ?: 0L
        private var baseNanos = System.nanoTime()

        fun restartAt(us: Long) {
            baseUs = us
            baseFrames = line?.longFramePosition ?: 0L
            baseNanos = System.nanoTime()
        }

        fun nowUs(): Long = if (line != null) {
            baseUs + (line.longFramePosition - baseFrames) * 1_000_000L / sampleRate
        } else {
            baseUs + (System.nanoTime() - baseNanos) / 1000
        }
    }

    private companion object {
        const val NO_SEEK = -1L
        const val LINE_BUFFER_MS = 400
        const val PAUSED_POLL_MS = 40L

        /** A picture this far behind the sound is skipped rather than shown late. */
        const val LATE_FRAME_US = 120_000L

        /** Longest a picture waits for the sound; the line's buffer keeps playing meanwhile. */
        const val MAX_FRAME_WAIT_MS = 100L
    }
}
