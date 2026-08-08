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
import javax.sound.sampled.Mixer
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.thread
import javax.sound.sampled.AudioFormat as JavaAudioFormat

class AudioEngine {

    enum class State { IDLE, BUFFERING, READY, ENDED }

    var onStateChanged: ((State) -> Unit)? = null
    var onPlayingChanged: ((Boolean) -> Unit)? = null
    var onCompletion: (() -> Unit)? = null
    var onError: ((Throwable) -> Unit)? = null

    @Volatile var isPlaying: Boolean = false
        private set

    @Volatile var state: State = State.IDLE
        private set

    @Volatile var positionMs: Long = 0L
        private set

    @Volatile var durationMs: Long = 0L
        private set

    private val outputSampleRate = 44100
    private val outputChannels = 2

    private val fx = FxAudioProcessor()
    private val reverb = ReverbAudioProcessor()
    private val eightD = EightDAudioProcessor()
    private val earrape = EarrapeAudioProcessor()
    private val normalization = NormalizationAudioProcessor()
    private val mono = MonoAudioProcessor()
    private val chain: List<AudioProcessor> = listOf(fx, reverb, eightD, earrape, normalization, mono)
    private val stretcher = TimeStretcher(outputSampleRate, outputChannels)

    private var effects = AudioEffectsState()

    @Volatile private var volume: Float = 1f
    @Volatile private var seekRequestMs: Long = -1L
    @Volatile private var paused = true
    @Volatile private var stopFlag = false

    @Volatile private var activeWorkerId = 0L

    @Volatile var pendingDeviceSwap = false

    private var worker: Thread? = null
    @Volatile private var line: SourceDataLine? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private var currentUrl: String? = null
    private var currentHeaders: Map<String, String> = emptyMap()

    init {
        val fmt = AudioFormat(outputSampleRate, outputChannels)
        chain.forEach { it.configure(fmt) }
    }


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
        normalization.setParameters(state.isNormalizationEnabled, state.normalizationLevel)
        mono.setEnabled(state.isMonoEnabled)

        val pitch = if (state.isPitchEnabled) state.speed else 1f
        stretcher.setParameters(state.speed, pitch)
    }


    var onReResolveUrl: (suspend () -> String?)? = null

    private fun createGrabber(targetUrl: String, headers: Map<String, String>): FFmpegFrameGrabber {
        return FFmpegFrameGrabber(targetUrl).apply {
            if (headers.isNotEmpty()) {
                val headerBlob = headers.entries.joinToString("\r\n") { "${it.key}: ${it.value}" }
                setOption("headers", headerBlob + "\r\n")
            }
            if (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) {
                setOption("http_seekable", "1")
                setOption("reconnect", "1")
                setOption("reconnect_streamed", "1")
                setOption("reconnect_delay_max", "5")
                setOption("rw_timeout", "15000000") // 15s in us
            }
            sampleRate = outputSampleRate
            audioChannels = outputChannels
        }
    }

    private fun recoverStream(
        oldGrabber: FFmpegFrameGrabber?,
        url: String,
        headers: Map<String, String>,
        resumePosMs: Long
    ): Pair<FFmpegFrameGrabber?, String?> {
        try {
            oldGrabber?.stop()
            oldGrabber?.release()
        } catch (_: Exception) {}

        var currentUrlToTry = url
        val targetTs = resumePosMs * 1000L

        // Attempt 1: Reopen with current URL
        for (attempt in 1..2) {
            if (stopFlag) return Pair(null, null)
            try {
                val newG = createGrabber(currentUrlToTry, headers)
                newG.start()
                if (targetTs > 0) {
                    try { newG.timestamp = targetTs } catch (_: Exception) {}
                }
                return Pair(newG, currentUrlToTry)
            } catch (e: Exception) {
                System.err.println("AudioEngine: Reopen attempt $attempt failed: ${e.message}")
                try { Thread.sleep(300) } catch (_: InterruptedException) {}
            }
        }

        // Attempt 2: Re-resolve fresh stream URL (in case URL / token expired after long pause)
        val reResolver = onReResolveUrl
        if (reResolver != null && (url.startsWith("http://") || url.startsWith("https://"))) {
            System.err.println("AudioEngine: Attempting to re-resolve fresh stream URL...")
            var freshUrl: String? = null
            kotlinx.coroutines.runBlocking {
                try {
                    freshUrl = reResolver.invoke()
                } catch (e: Exception) {
                    System.err.println("AudioEngine: Re-resolve failed: ${e.message}")
                }
            }
            val nonNullUrl = freshUrl
            if (!nonNullUrl.isNullOrEmpty()) {
                currentUrlToTry = nonNullUrl
                try {
                    val newG = createGrabber(currentUrlToTry, headers)
                    newG.start()
                    if (targetTs > 0) {
                        try { newG.timestamp = targetTs } catch (_: Exception) {}
                    }
                    return Pair(newG, currentUrlToTry)
                } catch (e: Exception) {
                    System.err.println("AudioEngine: Fresh URL open failed: ${e.message}")
                }
            }
        }

        return Pair(null, null)
    }

    private fun runDecodeLoop(workerId: Long, url: String, headers: Map<String, String>) {
        var grabber: FFmpegFrameGrabber? = null
        var localLine: SourceDataLine? = null
        var activeUrl = url
        try {
            grabber = createGrabber(activeUrl, headers)
            grabber.start()

            if (stopFlag || activeWorkerId != workerId) return

            durationMs = grabber.lengthInTime / 1000L

            localLine = openLine()
            line = localLine

            if (stopFlag || activeWorkerId != workerId) return
            setState(State.READY)

            val outBuf = ShortArray(8192)

            while (!stopFlag && activeWorkerId == workerId) {
                // --- HOT-SWAP DEVICE ---
                if (pendingDeviceSwap) {
                    pendingDeviceSwap = false
                    closeLineInstance(localLine)
                    localLine = openLine()
                    line = localLine
                }
                // -----------------------

                val seek = seekRequestMs
                if (seek >= 0) {
                    val targetTimestamp = seek * 1000L
                    seekRequestMs = -1L

                    System.err.println("AudioEngine: Seeking to targetTimestamp=$targetTimestamp (ms=$seek)")

                    var seekOk = false
                    try {
                        grabber?.timestamp = targetTimestamp
                        val currentTs = grabber?.timestamp ?: 0L
                        if (currentTs <= targetTimestamp + 1_500_000L && (currentTs >= targetTimestamp - 5_000_000L || targetTimestamp < 5_000_000L)) {
                            seekOk = true
                        } else {
                            System.err.println("AudioEngine: setTimestamp landed at $currentTs, expected near $targetTimestamp. Will reopen stream.")
                        }
                    } catch (e: Exception) {
                        System.err.println("AudioEngine: FFmpeg seek error - ${e.message}")
                    }

                    if (!seekOk) {
                        val recovered = recoverStream(grabber, activeUrl, headers, seek)
                        if (recovered.first != null) {
                            grabber = recovered.first
                            if (recovered.second != null) activeUrl = recovered.second!!
                            seekOk = true
                        }
                    }

                    val activeG = grabber
                    if (seekOk && activeG != null) {
                        var droppedCount = 0
                        while (!stopFlag && activeWorkerId == workerId) {
                            val ts = activeG.timestamp
                            if (ts >= targetTimestamp) break
                            val f = try { activeG.grabSamples() } catch (_: Exception) { null }
                            if (f == null) break
                            droppedCount++
                            if (droppedCount > 20000) break
                        }
                    }

                    positionMs = seek
                    stretcher.flush()
                    chain.forEach { it.flush() }
                    localLine?.flush()
                }

                if (paused) {
                    Thread.sleep(20)
                    continue
                }

                var frame: Frame? = null
                try {
                    frame = grabber?.grabSamples()
                } catch (e: Exception) {
                    System.err.println("AudioEngine: Read error from grabber: ${e.message}")
                    frame = null
                }

                if (frame == null) {
                    if (!stopFlag && activeWorkerId == workerId) {
                        val isNearEnd = durationMs > 0 && positionMs >= durationMs - 3000L
                        if (isNearEnd) {
                            drainStretcher(outBuf, localLine)
                            setStateAsync(State.ENDED)
                            break
                        } else {
                            System.err.println("AudioEngine: Premature EOF/error at $positionMs ms (duration=$durationMs ms). Recovering stream...")
                            val recovered = recoverStream(grabber, activeUrl, headers, positionMs)
                            if (recovered.first != null) {
                                grabber = recovered.first
                                if (recovered.second != null) activeUrl = recovered.second!!
                                System.err.println("AudioEngine: Stream recovery succeeded at $positionMs ms!")
                                continue
                            } else {
                                System.err.println("AudioEngine: Stream recovery failed. Ending track.")
                                drainStretcher(outBuf, localLine)
                                setStateAsync(State.ENDED)
                                break
                            }
                        }
                    } else {
                        break
                    }
                }

                val samples = frame.samples ?: continue
                val pcm = interleave(samples, frame.sampleRate, frame.audioChannels)
                pushThroughDsp(pcm)
                drainStretcher(outBuf, localLine)

                positionMs = (grabber?.timestamp ?: 0L) / 1000L
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

    private fun interleave(buffers: Array<java.nio.Buffer>, frameRate: Int, frameChannels: Int): ShortArray {
        val first = buffers[0]
        if (first is ShortBuffer) {
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
        val bb = (first as java.nio.ByteBuffer).order(ByteOrder.LITTLE_ENDIAN)
        val sb = bb.asShortBuffer()
        val out = ShortArray(sb.limit())
        sb.get(out)
        return out
    }

    private var dspInputBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.LITTLE_ENDIAN)

    private fun pushThroughDsp(pcm: ShortArray) {
        val requiredBytes = pcm.size * 2
        var buf = if (dspInputBuffer.capacity() < requiredBytes) {
            dspInputBuffer = ByteBuffer.allocateDirect(requiredBytes).order(ByteOrder.LITTLE_ENDIAN)
            dspInputBuffer
        } else {
            dspInputBuffer.clear()
            dspInputBuffer
        }
        buf.asShortBuffer().put(pcm)
        
        for (p in chain) {
            p.queueInput(buf)
            buf = p.getOutput()
        }
        
        val processed = ShortArray(buf.remaining() / 2)
        buf.asShortBuffer().get(processed)
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


    private fun openLine(): SourceDataLine {
        val fmt = JavaAudioFormat(
            outputSampleRate.toFloat(), 16, outputChannels, true, false, // signed, little-endian
        )
        val info = DataLine.Info(SourceDataLine::class.java, fmt)

        val prefs = com.alananasss.kittytune.data.local.PlayerPreferences()
        val deviceName = prefs.getAudioDevice()
        var mixer: Mixer? = null

        if (deviceName.isNotEmpty()) {
            val isLinux = System.getProperty("os.name").lowercase().contains("linux")
            if (isLinux && deviceName.startsWith("alsa_output.")) {
                // pactl sink ID: set PULSE_SINK so Java Sound routes through PipeWire/PulseAudio
                // This is the standard way to select a specific PipeWire sink from a JVM app
                System.setProperty("javax.sound.sampled.SourceDataLine", "")
                try {
                    val pb = ProcessBuilder("sh", "-c", "pactl set-default-sink '$deviceName'")
                    pb.start().waitFor()
                } catch (_: Exception) {}
                // Java Sound will then use the default PipeWire sink which we just set
            } else {
                // Windows/macOS or Java Sound mixer name
                val mixerInfos = AudioSystem.getMixerInfo()
                val targetInfo = mixerInfos.firstOrNull { it.name.trim() == deviceName }
                if (targetInfo != null) {
                    try {
                        val m = AudioSystem.getMixer(targetInfo)
                        if (m.isLineSupported(info)) mixer = m
                    } catch (_: Exception) {}
                }
            }
        }

        val l = if (mixer != null) mixer.getLine(info) as SourceDataLine else AudioSystem.getLine(info) as SourceDataLine
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
