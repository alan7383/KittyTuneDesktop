package com.alananasss.kittytune.audio.automix

import com.alananasss.kittytune.utils.Logger
import com.alananasss.kittytune.audio.releaseQuietly
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.FrameGrabber
import java.io.File
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Offline BPM + beat-grid analyzer. Classical DSP, no ML:
 * decode -> mono PCM -> STFT -> spectral flux onset envelope ->
 * autocorrelation tempo estimate -> comb-filter phase for beat offset.
 *
 * Beat times of the track are: firstBeatOffsetMs + k * (60000 / bpm).
 */
object BeatAnalyzer {

    data class Result(
        val bpm: Float,
        val firstBeatOffsetMs: Long,
        val confidence: Float,
        val mixInPointMs: Long? = null,
        val mixOutPointMs: Long? = null,
        /** 0=C, 1=C#, ... 11=B. Null when the chroma signal was too weak to call a key. */
        val keyPitchClass: Int? = null,
        val keyIsMinor: Boolean? = null,
    )

    private const val TAG = "BeatAnalyzer"

    private const val FFT_SIZE = 1024
    private const val HOP_SIZE = 512
    private const val MIN_BPM = 60f
    private const val MAX_BPM = 180f

    /** Analysis window: 18s taken from the middle of the track. */
    private const val WINDOW_US = 18_000_000L

    /** Energy-scan windows for dynamic mix points. */
    private const val HEAD_WINDOW_US = 16_000_000L
    private const val TAIL_WINDOW_US = 24_000_000L

    /** Canonical BPM range; octave-fold estimates into it (61.9 -> 123.8, 160 -> 80...). */
    private const val MIN_CANONICAL_BPM = 70f
    private const val MAX_CANONICAL_BPM = 140f
    private const val ENERGY_BLOCK_MS = 500
    private const val MAX_INTRO_SKIP_MS = 20_000L
    private const val MAX_OUTRO_CUT_MS = 45_000L

    class CachedAnalysis(val result: Result?, val complete: Boolean)

    class MonoPcm(val samples: FloatArray, val sampleRate: Int, val actualStartUs: Long = 0L)

    fun analyzeFile(
        filePath: String,
        shouldCancel: () -> Boolean = { false },
    ): Result? = analyzeSource(filePath, emptyMap(), 0L, shouldCancel)

    fun analyzeStream(
        url: String,
        headers: Map<String, String>? = null,
        totalDurationMs: Long = 0L,
        shouldCancel: () -> Boolean = { false },
    ): CachedAnalysis? {
        val res = analyzeSource(url, headers.orEmpty(), totalDurationMs, shouldCancel)
        return CachedAnalysis(res, complete = true)
    }

    fun analyzeSource(
        source: String,
        headers: Map<String, String> = emptyMap(),
        knownDurationMs: Long = 0L,
        shouldCancel: () -> Boolean = { false },
    ): Result? {
        if (source.contains(".m3u8")) {
            val adapter = try {
                com.alananasss.kittytune.audio.HlsStreamAdapter(source, headers)
            } catch (e: Exception) {
                Logger.w(TAG, "Failed creating HlsStreamAdapter for beat analysis: ${e.message}")
                return null
            }
            val totalDurationMs = if (knownDurationMs > 0) knownDurationMs else adapter.totalDurationMs

            val windowStartMs = if (totalDurationMs > 36_000L) max(0L, totalDurationMs / 2 - 9_000L) else 0L
            if (shouldCancel()) return null
            val midPcm = try {
                decodeHlsWindow(adapter, windowStartMs, WINDOW_US, shouldCancel)
            } catch (e: Exception) {
                Logger.w(TAG, "Failed decoding HLS middle window: ${e.message}")
                null
            }

            if (midPcm == null || midPcm.samples.size < FFT_SIZE * 8) {
                Logger.d(TAG, "Insufficient PCM frames from HLS for beat analysis (${midPcm?.samples?.size ?: 0})")
                return null
            }

            if (shouldCancel()) return null
            val key = estimateKey(midPcm.samples, midPcm.sampleRate)

            if (shouldCancel()) return null
            val flux = spectralFlux(midPcm.samples)
            val frameRate = midPcm.sampleRate.toFloat() / HOP_SIZE

            val (periodFrames, confidence) = estimateTempoPeriod(flux, frameRate) ?: return null
            val phaseFrames = estimateBeatPhase(flux, periodFrames)

            var periodMs = periodFrames / frameRate * 1000f
            var bpm = 60_000f / periodMs
            while (bpm < MIN_CANONICAL_BPM) {
                bpm *= 2f
                periodMs /= 2f
            }
            while (bpm >= MAX_CANONICAL_BPM) {
                bpm /= 2f
                periodMs *= 2f
            }

            val windowStartAnchorMs = midPcm.actualStartUs / 1000L
            val anchorMs = windowStartAnchorMs + (phaseFrames / frameRate * 1000f).roundToLong()
            val firstBeatOffsetMs = (anchorMs % periodMs.roundToLong() + periodMs.roundToLong()) % periodMs.roundToLong()

            var mixInPointMs: Long? = null
            if (totalDurationMs > 16_000L && !shouldCancel()) {
                try {
                    val headPcm = decodeHlsWindow(adapter, 0L, HEAD_WINDOW_US, shouldCancel)
                    if (headPcm != null) {
                        mixInPointMs = detectMixIn(
                            energyEnvelope(headPcm.samples, headPcm.sampleRate),
                            firstBeatOffsetMs,
                            periodMs
                        )
                    }
                } catch (_: Exception) {}
            }

            var mixOutPointMs: Long? = null
            if (totalDurationMs > 45_000L && !shouldCancel()) {
                val tailStartMs = max(0L, totalDurationMs - 24_000L)
                try {
                    val tailPcm = decodeHlsWindow(adapter, tailStartMs, TAIL_WINDOW_US, shouldCancel)
                    if (tailPcm != null) {
                        mixOutPointMs = detectMixOut(
                            energyEnvelope(tailPcm.samples, tailPcm.sampleRate),
                            tailStartMs,
                            totalDurationMs
                        )
                    }
                } catch (_: Exception) {}
            }

            return Result(bpm, firstBeatOffsetMs, confidence, mixInPointMs, mixOutPointMs, key?.first, key?.second)
        }

        var grabber: FFmpegFrameGrabber? = null
        try {
            if (shouldCancel()) return null
            grabber = FFmpegFrameGrabber(source).apply {
                if (headers.isNotEmpty()) {
                    val headerBlob = headers.entries.joinToString("\r\n") { "${it.key}: ${it.value}" }
                    setOption("headers", headerBlob + "\r\n")
                }
                if (source.startsWith("http://") || source.startsWith("https://")) {
                    setOption("probesize", "32768")
                    setOption("analyzeduration", "0")
                    setOption("http_seekable", "1")
                    setOption("reconnect", "1")
                    setOption("reconnect_streamed", "1")
                    setOption("reconnect_on_network_error", "1")
                }
                setOption("timeout", "8000000") // 8s network timeout
                sampleRate = 44100
                audioChannels = 1
                sampleMode = FrameGrabber.SampleMode.FLOAT
            }
            grabber.start()

            val streamDurationUs = grabber.lengthInTime
            val durationUs = if (streamDurationUs > 0) streamDurationUs else knownDurationMs * 1000L
            val totalDurationMs = durationUs / 1000L

            var pcm: MonoPcm? = null

            if (durationUs > WINDOW_US * 2) {
                val windowStartUs = max(0L, durationUs / 2 - WINDOW_US / 2)
                try {
                    grabber.timestamp = windowStartUs
                } catch (_: Exception) {}
                if (shouldCancel()) return null
                pcm = decodeMono(grabber, WINDOW_US, shouldCancel)
            }

            if (pcm == null || pcm.samples.size < FFT_SIZE * 8) {
                try {
                    grabber.timestamp = 0L
                } catch (_: Exception) {}
                if (shouldCancel()) return null
                pcm = decodeMono(grabber, WINDOW_US, shouldCancel)
            }

            if (pcm == null || pcm.samples.size < FFT_SIZE * 8) {
                Logger.d(TAG, "Insufficient PCM frames for beat analysis (${pcm?.samples?.size ?: 0})")
                return null
            }

            if (shouldCancel()) return null
            val key = estimateKey(pcm.samples, pcm.sampleRate)

            if (shouldCancel()) return null
            val flux = spectralFlux(pcm.samples)
            val frameRate = pcm.sampleRate.toFloat() / HOP_SIZE

            val (periodFrames, confidence) = estimateTempoPeriod(flux, frameRate) ?: return null
            val phaseFrames = estimateBeatPhase(flux, periodFrames)

            var periodMs = periodFrames / frameRate * 1000f
            var bpm = 60_000f / periodMs
            while (bpm < MIN_CANONICAL_BPM) {
                bpm *= 2f
                periodMs /= 2f
            }
            while (bpm >= MAX_CANONICAL_BPM) {
                bpm /= 2f
                periodMs *= 2f
            }

            val windowStartMs = pcm.actualStartUs / 1000L
            val anchorMs = windowStartMs + (phaseFrames / frameRate * 1000f).roundToLong()
            val firstBeatOffsetMs = (anchorMs % periodMs.roundToLong() + periodMs.roundToLong()) % periodMs.roundToLong()

            var mixInPointMs: Long? = null
            if (durationUs > HEAD_WINDOW_US) {
                try {
                    grabber.timestamp = 0L
                    decodeMono(grabber, HEAD_WINDOW_US, shouldCancel)?.let { head ->
                        mixInPointMs = detectMixIn(
                            energyEnvelope(head.samples, head.sampleRate),
                            firstBeatOffsetMs,
                            periodMs,
                        )
                    }
                } catch (_: Exception) {}
            }

            var mixOutPointMs: Long? = null
            if (totalDurationMs > 45_000L && durationUs > TAIL_WINDOW_US && !shouldCancel()) {
                val tailStartUs = max(0L, durationUs - TAIL_WINDOW_US)
                try {
                    grabber.timestamp = tailStartUs
                    val tailActualStartMs = max(0L, grabber.timestamp) / 1000L
                    decodeMono(grabber, TAIL_WINDOW_US, shouldCancel)?.let { tail ->
                        mixOutPointMs = detectMixOut(
                            energyEnvelope(tail.samples, tail.sampleRate),
                            tailActualStartMs,
                            totalDurationMs,
                        )
                    }
                } catch (_: Exception) {}
            }

            return Result(bpm, firstBeatOffsetMs, confidence, mixInPointMs, mixOutPointMs, key?.first, key?.second)
        } catch (e: Exception) {
            Logger.w(TAG, "Beat analysis failed: ${e.message}")
            return null
        } finally {
            grabber?.releaseQuietly()
        }
    }

    /**
     * Decodes one window of an HLS stream. The grabber is released however decoding ends: its
     * demuxer and codec contexts live in native memory that no garbage collection will ever free,
     * so one skipped release on a network error is a permanent leak, and this runs for every track.
     */
    private fun decodeHlsWindow(
        adapter: com.alananasss.kittytune.audio.HlsStreamAdapter,
        startMs: Long,
        windowUs: Long,
        shouldCancel: () -> Boolean,
    ): MonoPcm? {
        val grabber = FFmpegFrameGrabber(adapter.getInputStream(startMs)).apply {
            format = "mp4"
            setOption("probesize", "32768")
            setOption("analyzeduration", "0")
            sampleRate = 44100
            audioChannels = 1
            sampleMode = FrameGrabber.SampleMode.FLOAT
        }
        return try {
            grabber.start()
            decodeMono(grabber, windowUs, shouldCancel, actualStartUs = startMs * 1000L)
        } finally {
            grabber.releaseQuietly()
        }
    }

    private fun decodeMono(
        grabber: FFmpegFrameGrabber,
        maxDurationUs: Long,
        shouldCancel: () -> Boolean = { false },
        actualStartUs: Long? = null,
    ): MonoPcm? {
        val startUs = actualStartUs ?: max(0L, grabber.timestamp)
        val channels = max(1, grabber.audioChannels)
        val sampleRate = if (grabber.sampleRate > 0) grabber.sampleRate else 44100
        val maxSamples = ((maxDurationUs.toDouble() / 1_000_000.0) * sampleRate).toInt()

        val chunks = ArrayList<FloatArray>()
        var collected = 0

        while (collected < maxSamples) {
            if (shouldCancel()) return null
            val frame: Frame = grabber.grabSamples() ?: break
            val buffers = frame.samples ?: continue
            if (buffers.isEmpty()) continue

            val first = buffers[0]
            val planar = buffers.size == channels && channels > 1

            if (!planar && first is FloatBuffer) {
                val len = first.remaining()
                if (len <= 0) continue
                val frames = len / channels
                val mono = FloatArray(frames)
                val pos = first.position()
                if (channels == 1) {
                    first.get(mono, 0, frames)
                } else {
                    for (f in 0 until frames) {
                        var sum = 0f
                        for (c in 0 until channels) {
                            sum += first.get(pos + f * channels + c)
                        }
                        mono[f] = sum / channels
                    }
                }
                chunks.add(mono)
                collected += frames
            } else if (planar) {
                val len = first.remaining()
                if (len <= 0) continue
                val mono = FloatArray(len)
                for (ch in 0 until channels) {
                    val b = buffers[ch] as? FloatBuffer ?: continue
                    val bPos = b.position()
                    for (i in 0 until len) {
                        mono[i] += b.get(bPos + i) / channels
                    }
                }
                chunks.add(mono)
                collected += len
            } else if (first is ShortBuffer) {
                val len = first.remaining()
                if (len <= 0) continue
                val frames = len / channels
                val mono = FloatArray(frames)
                val pos = first.position()
                for (f in 0 until frames) {
                    var sum = 0f
                    for (c in 0 until channels) {
                        sum += first.get(pos + f * channels + c).toFloat() / 32768f
                    }
                    mono[f] = sum / channels
                }
                chunks.add(mono)
                collected += frames
            }
        }

        if (chunks.isEmpty() || collected == 0) return null
        val total = chunks.sumOf { it.size }
        val out = FloatArray(total)
        var writePos = 0
        for (c in chunks) {
            c.copyInto(out, writePos)
            writePos += c.size
        }
        return MonoPcm(out, sampleRate, startUs)
    }

    /** RMS energy per ENERGY_BLOCK_MS block. */
    fun energyEnvelope(samples: FloatArray, sampleRate: Int): FloatArray {
        val blockSize = (sampleRate * ENERGY_BLOCK_MS / 1000).coerceAtLeast(1)
        val numBlocks = samples.size / blockSize
        val env = FloatArray(numBlocks)
        for (b in 0 until numBlocks) {
            var sum = 0f
            val offset = b * blockSize
            for (i in 0 until blockSize) {
                val s = samples[offset + i]
                sum += s * s
            }
            env[b] = sqrt(sum / blockSize)
        }
        return env
    }

    fun percentile(values: FloatArray, p: Float): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        return sorted[((sorted.size - 1) * p).roundToInt().coerceIn(0, sorted.size - 1)]
    }

    /**
     * First block where energy reaches and sustains near body level, snapped forward
     * onto the beat grid. Null when the track starts hot (no intro worth skipping).
     */
    fun detectMixIn(env: FloatArray, firstBeatOffsetMs: Long, periodMs: Float): Long? {
        if (env.size < 8) return null
        val ref = percentile(env, 0.75f)
        if (ref <= 0f) return null

        var candidateBlock = -1
        for (i in 0 until env.size - 4) {
            if (env[i] >= 0.55f * ref &&
                env[i + 1] >= 0.4f * ref &&
                env[i + 2] >= 0.4f * ref &&
                env[i + 3] >= 0.4f * ref
            ) {
                candidateBlock = i
                break
            }
        }
        if (candidateBlock <= 0) return null

        val candidateMs = candidateBlock.toLong() * ENERGY_BLOCK_MS
        if (candidateMs > MAX_INTRO_SKIP_MS) return null

        val k = kotlin.math.ceil((candidateMs - firstBeatOffsetMs) / periodMs.toDouble()).toLong()
        return (firstBeatOffsetMs + max(0L, k) * periodMs.toDouble()).roundToLong()
    }

    /**
     * Last moment the tail window is still at body loudness; everything after is outro.
     * Null when the track stays loud to the end (no early mix-out warranted).
     */
    fun detectMixOut(env: FloatArray, windowStartMs: Long, durationMs: Long): Long? {
        if (env.size < 8 || durationMs <= 0) return null
        val ref = percentile(env, 0.75f)
        if (ref <= 0f) return null

        var lastLoudBlock = -1
        for (i in env.indices.reversed()) {
            if (env[i] >= 0.5f * ref) {
                lastLoudBlock = i
                break
            }
        }
        if (lastLoudBlock < 0) return null

        val mixOutMs = windowStartMs + (lastLoudBlock + 1).toLong() * ENERGY_BLOCK_MS
        if (durationMs - mixOutMs < 3_000) return null
        return max(mixOutMs, durationMs - MAX_OUTRO_CUT_MS)
    }

    /** Half-wave-rectified spectral flux per hop, log-compressed magnitudes. */
    fun spectralFlux(samples: FloatArray): FloatArray {
        val window = FloatArray(FFT_SIZE) { 0.5f - 0.5f * cos(2.0 * Math.PI * it / FFT_SIZE).toFloat() }
        val numFrames = (samples.size - FFT_SIZE) / HOP_SIZE
        if (numFrames <= 0) return FloatArray(0)
        val bins = FFT_SIZE / 2
        val flux = FloatArray(numFrames)
        val prevMag = FloatArray(bins)
        val re = FloatArray(FFT_SIZE)
        val im = FloatArray(FFT_SIZE)

        for (frame in 0 until numFrames) {
            val offset = frame * HOP_SIZE
            for (i in 0 until FFT_SIZE) {
                re[i] = samples[offset + i] * window[i]
                im[i] = 0f
            }
            fft(re, im)
            var sum = 0f
            for (b in 0 until bins) {
                val mag = ln(1f + 10f * sqrt(re[b] * re[b] + im[b] * im[b]))
                val diff = mag - prevMag[b]
                if (diff > 0) sum += diff
                prevMag[b] = mag
            }
            flux[frame] = sum
        }

        val meanWindow = (0.5f * FFT_SIZE / HOP_SIZE * 8).roundToInt().coerceAtLeast(8)
        val detrended = FloatArray(numFrames)
        for (i in 0 until numFrames) {
            val lo = max(0, i - meanWindow)
            val hi = min(numFrames - 1, i + meanWindow)
            var mean = 0f
            for (j in lo..hi) mean += flux[j]
            mean /= hi - lo + 1
            detrended[i] = max(0f, flux[i] - mean)
        }
        return detrended
    }

    /**
     * Autocorrelation over the beat-period lag range. Returns (periodInFrames, confidence).
     */
    fun estimateTempoPeriod(flux: FloatArray, frameRate: Float): Pair<Float, Float>? {
        val minLag = (frameRate * 60f / MAX_BPM).roundToInt()
        val maxLag = (frameRate * 60f / MIN_BPM).roundToInt()
        if (flux.size < maxLag * 2) return null

        val ac = FloatArray(maxLag + 1)
        for (lag in minLag..maxLag) {
            var sum = 0f
            for (i in 0 until flux.size - lag) sum += flux[i] * flux[i + lag]
            ac[lag] = sum / (flux.size - lag)
        }

        var mean = 0f
        for (lag in minLag..maxLag) mean += ac[lag]
        mean /= maxLag - minLag + 1
        if (mean <= 0f) return null

        var bestLag = -1
        var bestScore = 0f
        for (lag in minLag..maxLag) {
            if (lag > minLag && lag < maxLag && (ac[lag] < ac[lag - 1] || ac[lag] < ac[lag + 1])) continue
            var score = ac[lag]
            val doubleLag = lag * 2
            if (doubleLag <= maxLag) score += 0.5f * ac[doubleLag]
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        if (bestLag < 0) return null

        val refined = if (bestLag in minLag + 1 until maxLag) {
            val y0 = ac[bestLag - 1]
            val y1 = ac[bestLag]
            val y2 = ac[bestLag + 1]
            val denom = y0 - 2 * y1 + y2
            if (denom != 0f) bestLag + 0.5f * (y0 - y2) / denom else bestLag.toFloat()
        } else bestLag.toFloat()

        val sortedAc = ac.copyOfRange(minLag, maxLag + 1).sorted()
        val median = sortedAc[sortedAc.size / 2]
        val confidence = if (ac[bestLag] > 0f) ((ac[bestLag] - median) / ac[bestLag]).coerceIn(0f, 1f) else 0f
        return refined to confidence
    }

    private val MAJOR_PROFILE = floatArrayOf(6.35f, 2.23f, 3.48f, 2.33f, 4.38f, 4.09f, 2.52f, 5.19f, 2.39f, 3.66f, 2.29f, 2.88f)
    private val MINOR_PROFILE = floatArrayOf(6.33f, 2.68f, 3.52f, 5.38f, 2.60f, 3.53f, 2.54f, 4.75f, 3.98f, 2.69f, 3.34f, 3.17f)

    /**
     * Chroma vector (12-bin pitch-class energy) correlated against the Krumhansl-Schmuckler
     * profiles across all 12 rotations.
     */
    fun estimateKey(samples: FloatArray, sampleRate: Int): Pair<Int, Boolean>? {
        if (samples.size < FFT_SIZE * 4) return null
        val window = FloatArray(FFT_SIZE) { 0.5f - 0.5f * cos(2.0 * Math.PI * it / FFT_SIZE).toFloat() }
        val hop = FFT_SIZE / 2
        val numFrames = (samples.size - FFT_SIZE) / hop
        if (numFrames < 4) return null

        val chroma = FloatArray(12)
        val re = FloatArray(FFT_SIZE)
        val im = FloatArray(FFT_SIZE)
        val binHz = sampleRate.toFloat() / FFT_SIZE
        val minBin = (65f / binHz).toInt().coerceAtLeast(1)
        val maxBin = (2000f / binHz).toInt().coerceAtMost(FFT_SIZE / 2 - 1)

        for (frame in 0 until numFrames) {
            val offset = frame * hop
            for (i in 0 until FFT_SIZE) {
                re[i] = samples[offset + i] * window[i]
                im[i] = 0f
            }
            fft(re, im)
            for (b in minBin..maxBin) {
                val mag = sqrt(re[b] * re[b] + im[b] * im[b])
                if (mag <= 0f) continue
                val freq = b * binHz
                val midi = 69.0 + 12.0 * (ln(freq / 440.0) / ln(2.0))
                val pitchClass = ((midi.roundToInt() % 12) + 12) % 12
                chroma[pitchClass] += mag
            }
        }

        val sum = chroma.sum()
        if (sum <= 0f) return null
        for (i in chroma.indices) chroma[i] /= sum

        var bestScore = Float.NEGATIVE_INFINITY
        var bestPitchClass = 0
        var bestIsMinor = false
        for (tonic in 0 until 12) {
            val majorScore = correlateChroma(chroma, MAJOR_PROFILE, tonic)
            if (majorScore > bestScore) {
                bestScore = majorScore; bestPitchClass = tonic; bestIsMinor = false
            }
            val minorScore = correlateChroma(chroma, MINOR_PROFILE, tonic)
            if (minorScore > bestScore) {
                bestScore = minorScore; bestPitchClass = tonic; bestIsMinor = true
            }
        }
        return bestPitchClass to bestIsMinor
    }

    private fun correlateChroma(chroma: FloatArray, profile: FloatArray, tonic: Int): Float {
        var sum = 0f
        for (degree in 0 until 12) sum += chroma[(degree + tonic) % 12] * profile[degree]
        return sum
    }

    /** Comb filter: phase (in frames) maximizing summed flux at phase + k*period. */
    fun estimateBeatPhase(flux: FloatArray, periodFrames: Float): Float {
        val period = periodFrames.roundToInt().coerceAtLeast(1)
        var bestPhase = 0
        var bestSum = -1f
        for (phase in 0 until period) {
            var sum = 0f
            var i = phase
            while (i < flux.size) {
                sum += flux[i]
                i += period
            }
            if (sum > bestSum) {
                bestSum = sum
                bestPhase = phase
            }
        }
        return bestPhase.toFloat()
    }

    /** In-place iterative radix-2 FFT. Arrays must be a power-of-two length. */
    fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * Math.PI / len
            val wRe = cos(ang).toFloat()
            val wIm = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var curRe = 1f
                var curIm = 0f
                for (k in 0 until len / 2) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + len / 2] * curRe - im[i + k + len / 2] * curIm
                    val vIm = re[i + k + len / 2] * curIm + im[i + k + len / 2] * curRe
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + len / 2] = uRe - vRe
                    im[i + k + len / 2] = uIm - vIm
                    val nRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nRe
                }
                i += len
            }
            len = len shl 1
        }
    }
}
