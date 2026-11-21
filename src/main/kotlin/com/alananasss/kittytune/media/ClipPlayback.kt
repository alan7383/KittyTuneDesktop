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
import com.alananasss.kittytune.data.YoutubeAudioResolver
import com.alananasss.kittytune.data.artist.ArtistClips.ClipStream
import com.alananasss.kittytune.utils.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * Plays either a muxed music video or paired video and audio streams. The output line's played
 * sample count clocks the pictures. Each separate audio read stays close to the video timestamp.
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

    private var pendingSeekMs = AtomicLong(NO_SEEK)
    private var job: Job? = null
    private val decoderLock = Mutex()

    internal fun play(streams: List<ClipStream>, startMs: Long = 0, paused: Boolean = false) {
        job?.cancel()
        hasEnded = false
        hasFailed = false
        isLoading = true
        isPaused = paused
        positionMs = startMs
        frame = null
        val seek = AtomicLong(startMs.takeIf { it > 0 } ?: NO_SEEK)
        pendingSeekMs = seek
        job = scope.launch(Dispatchers.IO) {
            decoderLock.withLock {
                for (stream in streams) {
                    try {
                        decode(stream, seek)
                        return@launch
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        ensureActive()
                        Logger.w("ClipPlayback", "Could not play ${stream.height}p: ${e.javaClass.simpleName}")
                        isLoading = true
                        frame = null
                        seek.compareAndSet(NO_SEEK, positionMs)
                    }
                }
                hasFailed = true
                isLoading = false
            }
        }
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
    }

    private data class DecodedVideo(
        val timestampUs: Long,
        val bitmap: ImageBitmap,
        val generation: Long,
    )

    private suspend fun CoroutineScope.decode(stream: ClipStream, pendingSeekMs: AtomicLong) {
        runCatching { avutil.av_log_set_level(avutil.AV_LOG_ERROR) }
        val video = openGrabber(stream.videoUrl)
        var audio: FFmpegFrameGrabber? = null
        var line: SourceDataLine? = null
        val videoQueue = Channel<DecodedVideo>(capacity = 3)
        val generation = AtomicLong()
        try {
            video.start()
            if (stream.audioUrl != null) {
                audio = openGrabber(stream.audioUrl)
                audio.start()
            }
            check(video.imageWidth > 0 && video.imageHeight > 0)
            durationMs = video.lengthInTime / 1000
            val audioReader = audio ?: video
            val sampleRate = audioReader.sampleRate
            val channels = audioReader.audioChannels
            check(sampleRate > 0 && channels > 0)
            line = openPlaybackLine(
                AudioFormat(sampleRate.toFloat(), 16, channels, true, false),
                sampleRate * channels * 2 * LINE_BUFFER_MS / 1000,
            )
            line.start()
            currentCoroutineContext().ensureActive()
            val clock = Clock(line, sampleRate)
            var bytes = ByteArray(0)
            isLoading = false

            coroutineScope {
                val presenter = launch(Dispatchers.Default) {
                    for (video in videoQueue) {
                        if (!isActive) break
                        if (video.generation != generation.get()) continue
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
                        if (isActive && !isPaused && video.generation == generation.get() && pendingSeekMs.get() == NO_SEEK) {
                            frame = video.bitmap
                            positionMs = video.timestampUs / 1000
                        }
                    }
                }

                var audioTimestampUs = -1L
                var audioEnded = false
                var previousVideoTimestamp = 0L
                var sawFrame = false
                var wroteAudio = false
                fun writeAudio(next: org.bytedeco.javacv.Frame) {
                    val samples = next.samples ?: return
                    val buffers = samples.map { it as? ShortBuffer ?: return }
                    if (buffers.isEmpty()) return
                    val count = buffers.minOf { it.remaining() }
                    val totalBytes = count * buffers.size * 2
                    if (bytes.size < totalBytes) bytes = ByteArray(totalBytes)
                    val gain = VolumeCurve.sliderToAmplitude(volume)
                    var index = 0
                    for (sample in 0 until count) {
                        for (buffer in buffers) {
                            val value = (buffer.get(buffer.position() + sample) * gain).toInt()
                                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                            bytes[index++] = value.toByte()
                            bytes[index++] = (value shr 8).toByte()
                        }
                    }
                    if (totalBytes > 0) {
                        line.write(bytes, 0, totalBytes)
                        wroteAudio = true
                    }
                }
                try {
                    while (isActive) {
                        val seekMs = pendingSeekMs.getAndSet(NO_SEEK)
                        if (seekMs != NO_SEEK) {
                            generation.incrementAndGet()
                            while (videoQueue.tryReceive().isSuccess) {}
                            line.flush()
                            val targetUs = seekMs.coerceAtMost(durationMs.takeIf { it > 0 } ?: seekMs) * 1000
                            video.setTimestamp(targetUs)
                            audio?.setTimestamp(targetUs)
                            audioTimestampUs = targetUs - 1
                            previousVideoTimestamp = targetUs
                            audioEnded = false
                            clock.restartAt(targetUs)
                            hasEnded = false
                        }
                        if (isPaused) {
                            line.stop()
                            while (isActive && isPaused && pendingSeekMs.get() == NO_SEEK) delay(PAUSED_POLL_MS)
                            line.start()
                            clock.restartAt(positionMs * 1000)
                            continue
                        }
                        val next = if (audio != null) video.grabImage() else video.grab()
                        ensureActive()
                        if (next == null) {
                            check(sawFrame && wroteAudio) { "Clip ended without both video and audio" }
                            line.drain()
                            if (pendingSeekMs.get() != NO_SEEK) continue
                            hasEnded = true
                            isPaused = true
                            continue
                        }
                        val timestampUs = next.timestamp
                        if (audio == null) {
                            writeAudio(next)
                        } else {
                            while (!audioEnded && audioTimestampUs < timestampUs + AUDIO_LEAD_US && isActive) {
                                val decodedAudio = audio.grabSamples()
                                if (decodedAudio == null) {
                                    audioEnded = true
                                } else {
                                    audioTimestampUs = decodedAudio.timestamp
                                    if (audioTimestampUs >= previousVideoTimestamp - AUDIO_LEAD_US) writeAudio(decodedAudio)
                                }
                            }
                            previousVideoTimestamp = timestampUs
                        }
                        if (next.image != null) {
                            val bitmap = toBitmap(next) ?: continue
                            sawFrame = true
                            val decoded = DecodedVideo(timestampUs, bitmap, generation.get())
                            while (isActive && videoQueue.trySend(decoded).isFailure) {
                                if (pendingSeekMs.get() != NO_SEEK || isPaused) break
                                delay(5L)
                            }
                        }
                    }
                } finally {
                    presenter.cancel()
                }
            }
        } finally {
            videoQueue.close()
            runCatching { line?.close() }
            runCatching { audio?.release() }
            runCatching { video.release() }
        }
    }

    private fun openGrabber(url: String): FFmpegFrameGrabber = FFmpegFrameGrabber(url).apply {
        val headers = YoutubeAudioResolver.requestHeaders(url)
        headers["User-Agent"]?.let { setOption("user_agent", it) }
        val otherHeaders = headers.filterKeys { it != "User-Agent" }
        if (otherHeaders.isNotEmpty()) {
            setOption("headers", otherHeaders.entries.joinToString("") { (key, value) -> "$key: $value\r\n" })
        }
        setOption("rw_timeout", "8000000")
        setOption("reconnect", "1")
        setOption("reconnect_streamed", "1")
        setOption("reconnect_delay_max", "2")
        sampleFormat = avutil.AV_SAMPLE_FMT_S16
        pixelFormat = avutil.AV_PIX_FMT_BGRA
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

        @Synchronized
        fun restartAt(us: Long) {
            baseUs = us
            baseFrames = line?.longFramePosition ?: 0L
            baseNanos = System.nanoTime()
        }

        @Synchronized
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
        const val LINE_BUFFER_MS = 250
        const val AUDIO_LEAD_US = 120_000L
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
