package com.alananasss.kittytune.audio.automix

import com.alananasss.kittytune.utils.Logger
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.pow

/**
 * The whole track as a peak envelope, for the transition editor's waveforms (port of Android PR #83).
 *
 * The audio is decoded once with FFmpeg and folded on the fly into 20 ms peak buckets ([PEAKS_PER_SECOND]):
 * a 4-minute track becomes about 12 000 floats (48 KB) instead of 40 MB of PCM, and nothing is ever played.
 * Peaks are normalised to the loudest one and shaped by `pow(0.75)` with a 0.02 floor, as on Android.
 */
object FullTrackWaveformDecoder {

    private const val TAG = "FullWaveformDecoder"

    /** Waveform resolution: buckets per second of audio. */
    const val PEAKS_PER_SECOND = 50

    /** Never decode more than this much audio (podcast and mix uploads can run for hours). */
    const val MAX_DURATION_MS = 15 * 60_000L

    /** One decode at a time, fairly: several full decodes at once would compete with playback for the CPU. */
    private val decodeGate = Semaphore(1, true)
    private const val GATE_WAIT_MS = 20_000L

    /** Mono at a reduced rate: plenty for 20 ms peaks, and four times less to decode and fold. */
    private const val DECODE_RATE = 11_025

    class DecodedWaveform(
        val peaks: FloatArray,
        val durationMs: Long,
        val peaksPerSecond: Int,
        /**
         * True when the pass covered the audio: the stream ran to its end or the full requested duration was
         * read. A stalled network leaves it false, and such a wave is never cached as the track's waveform.
         */
        val complete: Boolean = true,
    )

    /** Decodes [source] (a URL or a file path) into peaks. Blocking: call from Dispatchers.IO. */
    fun decodePeaks(
        source: String,
        headers: Map<String, String> = emptyMap(),
        durationHintMs: Long = 0L,
        shouldCancel: () -> Boolean = { false },
        onProgress: ((Float) -> Unit)? = null,
    ): DecodedWaveform? {
        if (source.isBlank() || shouldCancel()) return null
        try {
            val deadline = System.currentTimeMillis() + GATE_WAIT_MS
            while (!decodeGate.tryAcquire(200, TimeUnit.MILLISECONDS)) {
                if (shouldCancel() || System.currentTimeMillis() >= deadline) {
                    Logger.w(TAG, "Waveform decode gate busy; giving up")
                    return null
                }
            }
        } catch (_: InterruptedException) {
            return null
        }
        try {
            return decodeInternal(source, headers, durationHintMs, shouldCancel, onProgress)
        } finally {
            decodeGate.release()
        }
    }

    private fun decodeInternal(
        source: String,
        headers: Map<String, String>,
        durationHintMs: Long,
        shouldCancel: () -> Boolean,
        onProgress: ((Float) -> Unit)?,
    ): DecodedWaveform? {
        // A protected HLS stream is ciphertext to FFmpeg: use the clear copy when one is on disk.
        val clear = if (source.contains(".m3u8")) com.alananasss.kittytune.audio.ClearStreamCache.cached(source) else null
        var hls: com.alananasss.kittytune.audio.HlsStreamAdapter? = null
        val grabber = when {
            clear != null -> FFmpegFrameGrabber(clear.absolutePath)
            source.contains(".m3u8") -> {
                hls = com.alananasss.kittytune.audio.HlsStreamAdapter(source, headers)
                FFmpegFrameGrabber(hls.getInputStream(0L)).apply { format = "mp4" }
            }
            else -> FFmpegFrameGrabber(source).apply {
                if (headers.isNotEmpty()) {
                    setOption("headers", headers.entries.joinToString("\r\n") { "${it.key}: ${it.value}" } + "\r\n")
                }
                if (source.startsWith("http://") || source.startsWith("https://")) {
                    setOption("reconnect", "1")
                    setOption("reconnect_streamed", "1")
                    setOption("reconnect_on_network_error", "1")
                }
                setOption("timeout", "15000000")
            }
        }
        grabber.sampleRate = DECODE_RATE
        grabber.audioChannels = 1
        grabber.sampleMode = FrameGrabber.SampleMode.FLOAT
        try {
            grabber.start()
            val streamDurationMs = grabber.lengthInTime.takeIf { it > 0L }?.div(1000L)
                ?: hls?.totalDurationMs?.takeIf { it > 0L }
                ?: durationHintMs
            val targetMs = (if (streamDurationMs > 0L) streamDurationMs else MAX_DURATION_MS).coerceAtMost(MAX_DURATION_MS)
            val rate = if (grabber.sampleRate > 0) grabber.sampleRate else DECODE_RATE
            val perBucket = (rate / PEAKS_PER_SECOND).coerceAtLeast(1)
            val folder = PeakFolder(perBucket, (targetMs * PEAKS_PER_SECOND / 1000L).toInt().coerceAtLeast(1))

            var ended = false
            var lastProgressBucket = -1
            while (true) {
                if (shouldCancel()) return null
                val frame = grabber.grabSamples()
                if (frame == null) { ended = true; break }
                val buffers = frame.samples ?: continue
                if (buffers.isEmpty()) continue
                when (val b = buffers[0]) {
                    is FloatBuffer -> { val p = b.position(); for (i in 0 until b.remaining()) folder.add(b.get(p + i)) }
                    is ShortBuffer -> { val p = b.position(); for (i in 0 until b.remaining()) folder.add(b.get(p + i) / 32768f) }
                    else -> continue
                }
                if (folder.bucketCount >= folder.capacity) break
                val pct = folder.bucketCount * 100 / folder.capacity
                if (pct != lastProgressBucket) {
                    lastProgressBucket = pct
                    onProgress?.invoke(pct / 100f)
                }
            }
            val raw = folder.finish()
            if (raw.isEmpty()) return null
            val decodedMs = raw.size * 1000L / PEAKS_PER_SECOND
            // Complete when the decoder reached the end, or covered the duration it was asked for (95 % margin
            // for codec padding and rounding).
            val complete = ended || (targetMs > 0L && decodedMs >= targetMs * 95 / 100)
            onProgress?.invoke(1f)
            return DecodedWaveform(shape(raw), decodedMs, PEAKS_PER_SECOND, complete)
        } catch (e: Exception) {
            Logger.w(TAG, "Waveform decode failed: ${e.message}")
            return null
        } finally {
            runCatching { grabber.stop() }
            runCatching { grabber.release() }
        }
    }

    /** Normalises to the loudest peak and shapes by pow(0.75) with a 0.02 floor, as the Android decoder does. */
    internal fun shape(raw: FloatArray): FloatArray {
        val maxPeak = raw.maxOrNull() ?: 0f
        val safeMax = if (maxPeak > 0.02f) maxPeak else 1f
        return FloatArray(raw.size) { i ->
            val shaped = (raw[i] / safeMax).coerceIn(0f, 1f).toDouble().pow(0.75).toFloat()
            if (shaped < 0.02f) 0.02f else shaped
        }
    }

    /** Folds samples into fixed-size peak buckets as they arrive. */
    internal class PeakFolder(private val perBucket: Int, val capacity: Int) {
        private val peaks = FloatArray(capacity)
        var bucketCount = 0
            private set
        private var inBucket = 0
        private var current = 0f

        fun add(sample: Float) {
            if (bucketCount >= capacity) return
            val a = abs(sample)
            if (a > current) current = a
            if (++inBucket >= perBucket) {
                peaks[bucketCount++] = current
                current = 0f
                inBucket = 0
            }
        }

        fun finish(): FloatArray {
            if (inBucket > 0 && bucketCount < capacity) {
                peaks[bucketCount++] = current
                inBucket = 0
                current = 0f
            }
            return peaks.copyOf(bucketCount)
        }
    }
}
