package com.alananasss.kittytune.media

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.nio.ShortBuffer
import java.util.concurrent.atomic.AtomicLong
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.SourceDataLine

/**
 * Plays one video file with its sound: a music video on an artist's page (tester's list, 5.5).
 *
 * One FFmpeg reader gives picture and sound in the order they are stored. Sound goes straight to the output line,
 * whose blocking writes set the pace; each picture waits until the sound has played up to its time, or is dropped
 * when it comes too late, so lips stay on the words. Without a sound track the wall clock stands in.
 */
class ClipPlayback(
    private val scope: CoroutineScope,
) {

    /** The clip's own volume, 0 to 1 on the same curve as the music's; it starts at the music's so a clip is as loud as a song. */
    var volume by mutableFloatStateOf(MusicManager.getVolume())

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

    private data class DecodedVideo(
        val timestampUs: Long,
        val bitmap: ImageBitmap,
    )

    private suspend fun CoroutineScope.decode(streamUrl: String) {
        runCatching { avutil.av_log_set_level(avutil.AV_LOG_ERROR) }
        var grabber: FFmpegFrameGrabber? = null
        var line: SourceDataLine? = null
        val videoQueue = Channel<DecodedVideo>(capacity = 8)
        try {
            grabber = FFmpegFrameGrabber(streamUrl).apply {
                setOption("user_agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                setOption("rw_timeout", "10000000")
                setOption("reconnect", "1")
                setOption("reconnect_streamed", "1")
                setOption("reconnect_delay_max", "5")
                setOption("reconnect_at_eof", "1")
                setOption("buffer_size", "4194304")
                sampleFormat = avutil.AV_SAMPLE_FMT_S16
                // Handed to Skia as it is: the Java2D route through a BufferedImage cost more than the picture's time.
                pixelFormat = avutil.AV_PIX_FMT_BGRA
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

            coroutineScope {
                val presenter = launch(Dispatchers.Default) {
                    for (video in videoQueue) {
                        if (!isActive) break
                        var aheadUs = video.timestampUs - clock.nowUs()
                        if (aheadUs < -LATE_FRAME_US) continue
                        if (aheadUs > MAX_AHEAD_US) {
                            clock.restartAt(video.timestampUs)
                            aheadUs = 0L
                        }
                        while (aheadUs > 0 && isActive && !isPaused && pendingSeekMs.get() == NO_SEEK) {
                            delay((aheadUs / 1000).coerceIn(1L, FRAME_WAIT_STEP_MS))
                            aheadUs = video.timestampUs - clock.nowUs()
                        }
                        while (isActive && isPaused && pendingSeekMs.get() == NO_SEEK) {
                            delay(PAUSED_POLL_MS)
                        }
                        if (isActive && !isPaused && pendingSeekMs.get() == NO_SEEK) {
                            frame = video.bitmap
                            positionMs = video.timestampUs / 1000
                        }
                    }
                }

                try {
                    while (isActive) {
                        val seekMs = pendingSeekMs.getAndSet(NO_SEEK)
                        if (seekMs != NO_SEEK) {
                            while (videoQueue.tryReceive().isSuccess) {}
                            grabber.setTimestamp(seekMs * 1000)
                            line?.flush()
                            clock.restartAt(seekMs * 1000)
                            hasEnded = false
                        }
                        if (isPaused) {
                            line?.stop()
                            while (isActive && isPaused && pendingSeekMs.get() == NO_SEEK) delay(PAUSED_POLL_MS)
                            line?.start()
                            clock.restartAt(positionMs * 1000)
                            continue
                        }
                        val next = grabber.grab()
                        if (next == null) {
                            line?.drain()
                            hasEnded = true
                            isPaused = true
                            continue
                        }
                        val samplesArray = next.samples
                        if (samplesArray != null && line != null && samplesArray.isNotEmpty()) {
                            val gain = VolumeCurve.sliderToAmplitude(volume)
                            if (samplesArray.size == 1) {
                                val sb = samplesArray[0] as? ShortBuffer
                                if (sb != null) {
                                    val count = sb.remaining()
                                    val totalBytes = count * 2
                                    if (bytes.size < totalBytes) bytes = ByteArray(totalBytes)
                                    val pos = sb.position()
                                    for (i in 0 until count) {
                                        val value = (sb.get(pos + i) * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                                        bytes[i * 2] = value.toByte()
                                        bytes[i * 2 + 1] = (value shr 8).toByte()
                                    }
                                    line.write(bytes, 0, totalBytes)
                                }
                            } else if (samplesArray.size >= 2) {
                                val left = samplesArray[0] as? ShortBuffer
                                val right = samplesArray[1] as? ShortBuffer
                                if (left != null && right != null) {
                                    val count = minOf(left.remaining(), right.remaining())
                                    val totalBytes = count * 4
                                    if (bytes.size < totalBytes) bytes = ByteArray(totalBytes)
                                    val leftPos = left.position()
                                    val rightPos = right.position()
                                    for (i in 0 until count) {
                                        val lVal = (left.get(leftPos + i) * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                                        val rVal = (right.get(rightPos + i) * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                                        val idx = i * 4
                                        bytes[idx] = lVal.toByte()
                                        bytes[idx + 1] = (lVal shr 8).toByte()
                                        bytes[idx + 2] = rVal.toByte()
                                        bytes[idx + 3] = (rVal shr 8).toByte()
                                    }
                                    line.write(bytes, 0, totalBytes)
                                }
                            }
                        }
                        if (next.image != null) {
                            val bmp = toBitmap(next)
                            if (bmp != null) {
                                val qf = DecodedVideo(next.timestamp, bmp)
                                while (isActive && videoQueue.trySend(qf).isFailure) {
                                    if (pendingSeekMs.get() != NO_SEEK || isPaused) break
                                    delay(5L)
                                }
                            }
                        }
                    }
                } finally {
                    presenter.cancel()
                }
            }
        } catch (e: Exception) {
            Logger.w("ClipPlayback", "Clip playback failed: ${e.message}")
            hasFailed = true
            isLoading = false
        } finally {
            videoQueue.close()
            runCatching { line?.flush(); line?.stop(); line?.close() }
            runCatching { grabber?.stop(); grabber?.release() }
        }
    }

    /** One decoded picture (BGRA, as the grabber was asked for) as an image Compose can draw. */
    private fun toBitmap(decoded: org.bytedeco.javacv.Frame): ImageBitmap? {
        val buffer = decoded.image?.firstOrNull() as? java.nio.ByteBuffer ?: return null
        val width = decoded.imageWidth
        val height = decoded.imageHeight
        if (width <= 0 || height <= 0) return null
        val pixels = ByteArray(decoded.imageStride * height)
        buffer.duplicate().get(pixels, 0, minOf(pixels.size, buffer.remaining()))
        val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
        return Image.makeRaster(info, pixels, decoded.imageStride).toComposeImageBitmap()
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

        fun nowUs(): Long {
            val wallUs = baseUs + (System.nanoTime() - baseNanos) / 1000
            if (line == null || sampleRate <= 0) return wallUs
            val audioUs = baseUs + (line.longFramePosition - baseFrames) * 1_000_000L / sampleRate
            return if (audioUs < wallUs - AUDIO_UNDERRUN_TOLERANCE_US) {
                wallUs - AUDIO_UNDERRUN_TOLERANCE_US
            } else {
                audioUs
            }
        }
    }

    private companion object {
        const val NO_SEEK = -1L
        /** Room for a whole run of sound stored ahead of its pictures, so writing it never holds up decoding the pictures. */
        const val LINE_BUFFER_MS = 1000
        const val PAUSED_POLL_MS = 40L

        /** A picture this far behind the sound is skipped rather than shown late. */
        const val LATE_FRAME_US = 120_000L

        /** Maximum time ahead before forcing clock resync (prevents indefinite freezing on stream discontinuity). */
        const val MAX_AHEAD_US = 500_000L

        /** Maximum audio clock drift behind wall time before fallback to prevent underrun deadlocks. */
        const val AUDIO_UNDERRUN_TOLERANCE_US = 150_000L

        /** How often a picture that is not due yet looks at the clock again. */
        const val FRAME_WAIT_STEP_MS = 8L
    }
}
