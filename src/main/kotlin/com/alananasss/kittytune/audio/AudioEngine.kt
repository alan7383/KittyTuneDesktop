package com.alananasss.kittytune.audio

import com.alananasss.kittytune.ui.player.AudioEffectsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.thread
import javax.sound.sampled.AudioFormat as JavaAudioFormat

/**
 * The desktop audio engine: FFmpeg (via JavaCV) decodes any source (progressive MP3,
 * HLS/m3u8, local files) to 16-bit PCM, which is pushed through the KittyTune DSP chain
 * (Fx -> Reverb -> 8D -> Earrape) and the pitch/tempo stage, then out to a JavaSound
 * SourceDataLine. This replaces ExoPlayer, which has no desktop equivalent.
 *
 * A single decode+playback thread per track keeps the design close to the Android
 * "one player, app-side queue" model in MusicManager.
 */
class AudioEngine {

    enum class State { IDLE, BUFFERING, READY, ENDED }

    // --- observable callbacks (set by MusicManager) --------------------------------------
    var onStateChanged: ((State) -> Unit)? = null
    var onPlayingChanged: ((Boolean) -> Unit)? = null
    var onCompletion: (() -> Unit)? = null
    var onError: ((Throwable) -> Unit)? = null

    @Volatile var isPlaying: Boolean = false
        private set

    @Volatile var state: State = State.IDLE
        private set

    /** Current playback position in ms. */
    @Volatile var positionMs: Long = 0L
        private set

    /** Track duration in ms (0 if unknown). */
    @Volatile var durationMs: Long = 0L
        private set

    private val outputSampleRate = 44100
    private val outputChannels = 2

    // --- DSP chain (order matches Android sink: Fx -> Reverb -> 8D -> Earrape) -----------
    private val fx = FxAudioProcessor()
    private val reverb = ReverbAudioProcessor()
    private val eightD = EightDAudioProcessor()
    private val earrape = EarrapeAudioProcessor()
    private val chain: List<AudioProcessor> = listOf(fx, reverb, eightD, earrape)
    private val stretcher = TimeStretcher(outputSampleRate, outputChannels)

    private var effects = AudioEffectsState()

    @Volatile private var volume: Float = 1f
    @Volatile private var seekRequestMs: Long = -1L
    @Volatile private var paused = true
    @Volatile private var stopFlag = false

    @Volatile private var activeWorkerId = 0L

    private var worker: Thread? = null
    @Volatile private var line: SourceDataLine? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private var currentUrl: String? = null
    private var currentHeaders: Map<String, String> = emptyMap()

    init {
        val fmt = AudioFormat(outputSampleRate, outputChannels)
        chain.forEach { it.configure(fmt) }
    }

    // --- public control API (mirrors the subset of ExoPlayer MusicManager used) ----------

    @Synchronized
    fun setMediaItem(url: String, headers: Map<String, String> = emptyMap(), startPositionMs: Long = 0L) {
        stopInternal()
        currentUrl = url
        currentHeaders = headers
        positionMs = startPositionMs
        durationMs = 0L
        seekRequestMs = if (startPositionMs > 0) startPositionMs else -1L
        setState(State.BUFFERING)
    }

    @Synchronized
    fun prepare() {
        val url = currentUrl ?: return
        stopFlag = false
        val newWorkerId = ++activeWorkerId
        worker = thread(name = "kittytune-audio-$newWorkerId", isDaemon = true) {
            runDecodeLoop(newWorkerId, url, currentHeaders)
        }
    }

    fun play() {
        paused = false
        if (state == State.ENDED) {
            setState(State.BUFFERING)
            prepare()
        }
        setPlaying(true)
    }

    fun pause() {
        paused = true
        setPlaying(false)
    }

    fun seekTo(ms: Long) {
        seekRequestMs = ms.coerceAtLeast(0)
    }

    fun setVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
        applyLineVolume()
    }

    fun getVolume(): Float = volume

    @Synchronized
    fun stop() {
        stopInternal()
        setState(State.IDLE)
    }

    fun release() {
        stopInternal()
        scope.coroutineContext[Job]?.cancel()
    }

    /** Apply the full effect state (called by MusicManager.applyEffects). */
    fun applyEffects(state: AudioEffectsState) {
        effects = state
        fx.setEffects(state.isMuffledEnabled, state.isBassBoostEnabled)
        fx.setBassBoostGain(state.bassBoostIntensity)
        fx.setMuffledCutoff(state.muffledIntensity)
        reverb.setEnabled(state.isReverbEnabled)
        reverb.setDecay(state.reverbIntensity)
        eightD.setEnabled(state.is8DEnabled)
        eightD.setSpeed(state.eightDSpeed)
        earrape.setEnabled(state.isEarrapeEnabled)

        val pitch = if (state.isPitchEnabled) state.speed else 1f
        stretcher.setParameters(state.speed, pitch)
    }

    // --- decode + playback loop ----------------------------------------------------------

    private fun runDecodeLoop(workerId: Long, url: String, headers: Map<String, String>) {
        var grabber: FFmpegFrameGrabber? = null
        var localLine: SourceDataLine? = null
        try {
            grabber = FFmpegFrameGrabber(url).apply {
                // HLS/HTTP options + spoofed SoundCloud headers (progressive & m3u8).
                if (headers.isNotEmpty()) {
                    val headerBlob = headers.entries.joinToString("\r\n") { "${it.key}: ${it.value}" }
                    setOption("headers", headerBlob + "\r\n")
                }
                setOption("reconnect", "1")
                setOption("reconnect_streamed", "1")
                setOption("reconnect_delay_max", "5")
                sampleRate = outputSampleRate
                audioChannels = outputChannels
                start()
            }

            if (stopFlag || activeWorkerId != workerId) return

            durationMs = grabber.lengthInTime / 1000L

            if (seekRequestMs > 0) {
                grabber.timestamp = seekRequestMs * 1000L
                seekRequestMs = -1L
            }

            localLine = openLine()
            line = localLine

            if (stopFlag || activeWorkerId != workerId) return
            setState(State.READY)

            val outBuf = ShortArray(8192)

            while (!stopFlag && activeWorkerId == workerId) {
                // handle seek requested during playback
                val seek = seekRequestMs
                if (seek >= 0) {
                    grabber.timestamp = seek * 1000L
                    seekRequestMs = -1L
                    positionMs = seek
                    stretcher.flush()
                    chain.forEach { it.flush() }
                    localLine.flush()
                }

                if (paused) {
                    Thread.sleep(20)
                    continue
                }

                val frame: Frame? = grabber.grabSamples()
                if (frame == null) {
                    if (!stopFlag && activeWorkerId == workerId) {
                        drainStretcher(outBuf, localLine)
                        setStateAsync(State.ENDED)
                    }
                    break
                }

                val samples = frame.samples ?: continue
                val pcm = interleave(samples, frame.sampleRate, frame.audioChannels)
                pushThroughDsp(pcm)
                drainStretcher(outBuf, localLine)

                positionMs = grabber.timestamp / 1000L
            }
        } catch (t: Throwable) {
            if (!stopFlag && activeWorkerId == workerId) {
                onError?.invoke(t)
            }
        } finally {
            try { grabber?.stop(); grabber?.release() } catch (_: Exception) {}
            closeLineInstance(localLine)
        }
    }

    /** Convert an FFmpeg audio Frame's sample buffers to interleaved 16-bit PCM shorts. */
    private fun interleave(buffers: Array<java.nio.Buffer>, frameRate: Int, frameChannels: Int): ShortArray {
        // JavaCV gives one ShortBuffer per plane (or interleaved in buffer[0]).
        val first = buffers[0]
        if (first is ShortBuffer) {
            // Already 16-bit; may be planar (one buffer per channel) or packed (buffer[0]).
            return if (buffers.size == frameChannels && frameChannels > 1) {
                val len = first.limit()
                val out = ShortArray(len * frameChannels)
                for (ch in 0 until frameChannels) {
                    val b = buffers[ch] as ShortBuffer
                    for (i in 0 until len) out[i * frameChannels + ch] = b.get(i)
                }
                out
            } else {
                val out = ShortArray(first.limit())
                first.rewind()
                first.get(out)
                out
            }
        }
        // Fallback: treat as bytes.
        val bb = (first as java.nio.ByteBuffer).order(ByteOrder.LITTLE_ENDIAN)
        val sb = bb.asShortBuffer()
        val out = ShortArray(sb.limit())
        sb.get(out)
        return out
    }

    private fun pushThroughDsp(pcm: ShortArray) {
        // Run the interleaved PCM through the DSP chain (byte domain), then feed the
        // pitch/tempo stretcher.
        var bytes = shortsToBytes(pcm)
        for (p in chain) {
            val inBuf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            p.queueInput(inBuf)
            val out = p.getOutput()
            bytes = ByteArray(out.remaining())
            out.get(bytes)
        }
        val processed = bytesToShorts(bytes)
        stretcher.queue(processed, processed.size)
    }

    private fun drainStretcher(outBuf: ShortArray, localLine: SourceDataLine?) {
        while (stretcher.available() >= outputChannels) {
            val n = stretcher.drain(outBuf, outBuf.size)
            if (n <= 0) break
            writeToLine(outBuf, n, localLine)
        }
    }

    private fun writeToLine(samples: ShortArray, count: Int, localLine: SourceDataLine?) {
        val l = localLine ?: line ?: return
        val bytes = ByteArray(count * 2)
        var bi = 0
        for (i in 0 until count) {
            val s = samples[i].toInt()
            bytes[bi++] = (s and 0xFF).toByte()
            bytes[bi++] = ((s shr 8) and 0xFF).toByte()
        }
        try {
            l.write(bytes, 0, bytes.size)
        } catch (_: Exception) {}
    }

    // --- JavaSound line management -------------------------------------------------------

    private fun openLine(): SourceDataLine {
        val fmt = JavaAudioFormat(
            outputSampleRate.toFloat(), 16, outputChannels, true, false, // signed, little-endian
        )
        val info = DataLine.Info(SourceDataLine::class.java, fmt)
        val l = AudioSystem.getLine(info) as SourceDataLine
        // Use a ~40ms buffer for ultra-low latency (near instant reaction for effects)
        l.open(fmt, outputSampleRate * outputChannels * 2 * 40 / 1000) 
        l.start()
        applyLineVolume(l)
        return l
    }

    private fun applyLineVolume(targetLine: SourceDataLine? = line) {
        val l = targetLine ?: return
        try {
            if (l.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                val ctrl = l.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
                // Convert linear 0..1 to dB; clamp to control range.
                val db = if (volume <= 0.0001f) ctrl.minimum
                else (20.0 * Math.log10(volume.toDouble())).toFloat().coerceIn(ctrl.minimum, ctrl.maximum)
                ctrl.value = db
            }
        } catch (_: Exception) {
        }
    }

    private fun closeLineInstance(localLine: SourceDataLine?) {
        try {
            localLine?.drain()
            localLine?.stop()
            localLine?.close()
        } catch (_: Exception) {}
        if (line == localLine) {
            line = null
        }
    }

    @Synchronized
    private fun stopInternal() {
        stopFlag = true
        activeWorkerId++
        paused = true
        val currentWorker = worker
        if (currentWorker != null && Thread.currentThread() != currentWorker) {
            try { currentWorker.join(300) } catch (_: InterruptedException) {}
        }
        worker = null
        closeLineInstance(line)
        stretcher.flush()
        chain.forEach { it.flush() }
        setPlaying(false)
    }

    private fun setState(s: State) {
        state = s
        onStateChanged?.invoke(s)
    }

    private fun setStateAsync(s: State) {
        state = s
        scope.launch(Dispatchers.Main) {
            onStateChanged?.invoke(s)
            if (s == State.ENDED) {
                onCompletion?.invoke()
            }
        }
    }

    private fun setPlaying(p: Boolean) {
        if (isPlaying != p) {
            isPlaying = p
            onPlayingChanged?.invoke(p)
        }
    }

    private fun shortsToBytes(shorts: ShortArray): ByteArray {
        val out = ByteArray(shorts.size * 2)
        var bi = 0
        for (s in shorts) {
            val v = s.toInt()
            out[bi++] = (v and 0xFF).toByte()
            out[bi++] = ((v shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun bytesToShorts(bytes: ByteArray): ShortArray {
        val out = ShortArray(bytes.size / 2)
        var bi = 0
        for (i in out.indices) {
            val lo = bytes[bi++].toInt() and 0xFF
            val hi = bytes[bi++].toInt()
            out[i] = ((hi shl 8) or lo).toShort()
        }
        return out
    }
}
