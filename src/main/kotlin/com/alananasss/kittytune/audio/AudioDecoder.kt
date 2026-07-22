package com.alananasss.kittytune.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import java.nio.ShortBuffer

/**
 * Decodes any audio source (local file path or HTTP/HLS URL) into interleaved
 * stereo Float PCM at [SAMPLE_RATE] using FFmpeg — the desktop stand-in for
 * ExoPlayer's extractor/decoder chain.
 *
 * The Android app supports MP3, FLAC, WAV, M4A, AAC, OGG, WMA, OPUS, AMR, MP4
 * audio plus SoundCloud progressive/HLS streams; FFmpeg covers all of these.
 */
class AudioDecoder(
    private val source: String,
    private val headers: Map<String, String> = emptyMap(),
) : AutoCloseable {

    companion object {
        const val SAMPLE_RATE = 48_000
        const val CHANNELS = 2
    }

    private val grabber = FFmpegFrameGrabber(source).apply {
        sampleRate = SAMPLE_RATE
        audioChannels = CHANNELS
        // FFmpeg option: pass request headers (SoundCloud HLS needs Authorization).
        if (headers.isNotEmpty()) {
            val headerBlob = headers.entries.joinToString("\r\n") { "${it.key}: ${it.value}" }
            setOption("headers", headerBlob + "\r\n")
        }
        // Sensible network behavior for streams.
        if (source.startsWith("http")) {
            setOption("reconnect", "1")
            setOption("reconnect_streamed", "1")
            setOption("reconnect_delay_max", "5")
            setOption("rw_timeout", "15000000") // 15s in µs
        }
    }

    @Volatile
    private var started = false

    val durationMs: Long
        get() {
            ensureStarted()
            return grabber.lengthInTime / 1000
        }

    private fun ensureStarted() {
        if (!started) {
            grabber.start()
            started = true
        }
    }

    /**
     * Reads the next chunk of decoded audio.
     * @return interleaved stereo float samples in [-1, 1], or null at end of stream.
     */
    fun readChunk(): FloatArray? {
        ensureStarted()
        while (true) {
            val frame: Frame = grabber.grabSamples() ?: return null
            val samples = frame.samples ?: continue
            val buf = samples[0] as? ShortBuffer ?: continue
            val n = buf.remaining()
            if (n == 0) continue
            val out = FloatArray(n)
            for (i in 0 until n) {
                out[i] = buf.get() / 32768f
            }
            return out
        }
    }

    /** Current decode position in milliseconds. */
    val positionMs: Long
        get() = if (started) grabber.timestamp / 1000 else 0

    suspend fun seekTo(positionMs: Long) = withContext(Dispatchers.IO) {
        ensureStarted()
        grabber.timestamp = positionMs * 1000
    }

    override fun close() {
        try {
            grabber.stop()
        } catch (_: Exception) {
        }
        try {
            grabber.release()
        } catch (_: Exception) {
        }
    }
}
