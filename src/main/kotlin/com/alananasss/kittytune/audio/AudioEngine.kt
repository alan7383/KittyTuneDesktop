package com.alananasss.kittytune.audio

import com.alananasss.kittytune.ui.player.AudioEffectsState
import com.alananasss.kittytune.utils.Logger
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

    @Volatile
    var isPlaying: Boolean = false
        private set

    @Volatile
    var state: State = State.IDLE
        private set

    @Volatile
    var positionMs: Long = 0L
        private set

    @Volatile
    var durationMs: Long = 0L
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

    @Volatile
    private var volume: Float = 1f

    @Volatile
    private var seekRequestMs: Long = -1L

    @Volatile
    private var paused = true

    @Volatile
    private var stopFlag = false

    @Volatile
    private var activeWorkerId = 0L

    @Volatile
    var pendingDeviceSwap = false

    private var worker: Thread? = null

    @Volatile
    private var line: SourceDataLine? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private var currentUrl: String? = null
    private var currentHeaders: Map<String, String> = emptyMap()
    private var hlsAdapter: HlsStreamAdapter? = null

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
        Logger.e("AudioEngine", "setMediaItem called with url: $url")
        try {
            hlsAdapter = if (url.contains(".m3u8")) HlsStreamAdapter(url, headers) else null
            Logger.e("AudioEngine", "hlsAdapter initialized: ${hlsAdapter != null}")
        } catch (e: Exception) {
            Logger.e("AudioEngine", "Failed to init HlsStreamAdapter: ${e.message}")
            hlsAdapter = null
        }
        
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
        val coerced = ms.coerceAtLeast(0)
        seekRequestMs = coerced
        positionMs = coerced
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

    private fun createGrabber(targetUrl: String, headers: Map<String, String>, startPositionMs: Long = 0L): FFmpegFrameGrabber {
        val adapter = hlsAdapter
        val isHls = targetUrl.contains(".m3u8")
        
        return if (isHls && adapter != null) {
            FFmpegFrameGrabber(adapter.getInputStream(startPositionMs)).apply {
                format = "mp4" // Fragments are ISOBMFF (mp4)
                setOption("probesize", "32768") // 32KB is enough for MOOV atom + some audio
                setOption("analyzeduration", "0")
                sampleRate = outputSampleRate
                audioChannels = outputChannels
            }
        } else {
            FFmpegFrameGrabber(targetUrl).apply {
                if (headers.isNotEmpty()) {
                    val headerBlob = headers.entries.joinToString("\r\n") { "${it.key}: ${it.value}" }
                    setOption("headers", headerBlob + "\r\n")
                }
                if (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) {
                    setOption("probesize", "32768") // Bypass default 5MB probing
                    setOption("analyzeduration", "0") // Bypass duration analysis
                    setOption("http_seekable", "1")
                    setOption("reconnect", "1")
                    setOption("reconnect_streamed", "1")
                    setOption("reconnect_on_network_error", "1")
                    setOption("reconnect_on_http_error", "4xx,5xx")
                    setOption("reconnect_delay_max", "5")
                    setOption("rw_timeout", "1000000")
                    setOption("tcp_nodelay", "1")
                }
                sampleRate = outputSampleRate
                audioChannels = outputChannels
            }
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
        } catch (_: Exception) {
        }

        var currentUrlToTry = url
        val targetTs = resumePosMs * 1000L
        var attempt = 0

        while (!stopFlag) {
            attempt++
            val backoffMs = (attempt * 1000L).coerceAtMost(5000L)
            Logger.e("AudioEngine", "Network recovery attempt $attempt (backoff=${backoffMs}ms) at $resumePosMs ms...")

            try {
                val newG = createGrabber(currentUrlToTry, headers)
                newG.start()
                if (targetTs > 0) {
                    val isHls = currentUrlToTry.contains(".m3u8")
                    if (!isHls) {
                        try {
                            newG.timestamp = targetTs
                        } catch (_: Exception) {}
                    }
                    
                    var f: Frame? = try { newG.grabSamples() } catch (_: Exception) { null }
                    if (f == null) {
                        Logger.e("AudioEngine", "Grab failed at start/seek, falling back to manual catch-up")
                        try {
                            newG.stop()
                            newG.release()
                        } catch (_: Exception) {}
                        
                        val fallbackG = createGrabber(currentUrlToTry, headers, targetTs / 1000L)
                        fallbackG.start()
                        var droppedCount = 0
                        f = try { fallbackG.grabSamples() } catch (_: Exception) { null }
                        while (f != null && fallbackG.timestamp < targetTs - 100_000L && !stopFlag) {
                            f = try { fallbackG.grabSamples() } catch (_: Exception) { null }
                            droppedCount++
                            if (droppedCount > 20000) break
                        }
                        if (f == null) throw Exception("EOF during manual catch-up")
                        Logger.e("AudioEngine", "Recovery (fallback catchup) succeeded on attempt $attempt!")
                        return Pair(fallbackG, currentUrlToTry)
                    } else {
                        var droppedCount = 0
                        while (f != null && newG.timestamp < targetTs - 100_000L && !stopFlag) {
                            f = try { newG.grabSamples() } catch (_: Exception) { null }
                            droppedCount++
                            if (droppedCount > 10000) break
                        }
                        if (f == null) throw Exception("EOF during post-seek catch-up")
                        Logger.e("AudioEngine", "Recovery (native/fast catchup) succeeded on attempt $attempt!")
                        return Pair(newG, currentUrlToTry)
                    }
                }
                Logger.e("AudioEngine", "Reopen succeeded on attempt $attempt!")
                return Pair(newG, currentUrlToTry)
            } catch (e: Exception) {
                Logger.e("AudioEngine", "Reopen attempt $attempt failed (${e.message}).")
            }

            if (attempt % 2 == 0) {
                val reResolver = onReResolveUrl
                if (reResolver != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                    var freshUrl: String? = null
                    kotlinx.coroutines.runBlocking {
                        try {
                            freshUrl = reResolver.invoke()
                        } catch (e: Exception) {
                            Logger.e("AudioEngine", "Re-resolve on attempt $attempt failed: ${e.message}")
                        }
                    }
                    if (!freshUrl.isNullOrEmpty()) {
                        currentUrlToTry = freshUrl
                        try {
                            val newG = createGrabber(currentUrlToTry, headers, targetTs / 1000L)
                            newG.start()
                            if (targetTs > 0) {
                                val isHls = currentUrlToTry.contains(".m3u8")
                                if (!isHls) {
                                    try {
                                        newG.timestamp = targetTs
                                    } catch (_: Exception) {}
                                }
                                var f: Frame? = try { newG.grabSamples() } catch (_: Exception) { null }
                                if (f == null) throw Exception("EOF on fresh URL seek")
                                var droppedCount = 0
                                while (f != null && newG.timestamp < targetTs - 100_000L && !stopFlag) {
                                    f = try { newG.grabSamples() } catch (_: Exception) { null }
                                    droppedCount++
                                    if (droppedCount > 10000) break
                                }
                                if (f == null) throw Exception("EOF on fresh URL catchup")
                            }
                            Logger.e("AudioEngine", "Recovery via fresh URL succeeded on attempt $attempt!")
                            return Pair(newG, currentUrlToTry)
                        } catch (e: Exception) {
                            Logger.e("AudioEngine", "Fresh URL reopen failed: ${e.message}")
                        }
                    }
                }
            }

            try {
                Thread.sleep(backoffMs)
            } catch (_: InterruptedException) {
                break
            }
        }

        return Pair(null, null)
    }

    private fun runDecodeLoop(workerId: Long, url: String, headers: Map<String, String>) {
        var grabber: FFmpegFrameGrabber? = null
        var localLine: SourceDataLine? = null
        var activeUrl = url
        try {
            grabber = createGrabber(activeUrl, headers, positionMs)
            grabber.start()

            if (stopFlag || activeWorkerId != workerId) return

            val adapter = hlsAdapter
            durationMs = if (adapter != null) adapter.totalDurationMs else (grabber.lengthInTime / 1000L)

            localLine = openLine()
            line = localLine

            if (stopFlag || activeWorkerId != workerId) return
            setState(State.READY)

            val outBuf = ShortArray(8192)

            while (!stopFlag && activeWorkerId == workerId) {
                if (pendingDeviceSwap) {
                    pendingDeviceSwap = false
                    closeLineInstance(localLine)
                    localLine = openLine()
                    line = localLine
                }

                val seek = seekRequestMs
                if (seek >= 0) {
                    val targetTimestamp = seek * 1000L
                    seekRequestMs = -1L

                    Logger.e("AudioEngine", "Seeking to targetTimestamp=$targetTimestamp (ms=$seek)")

                    val isHls = activeUrl?.contains(".m3u8") == true
                    val currentTsForCheck = grabber?.timestamp ?: 0L

                    var seekOk = false
                    var seekFrame: Frame? = null
                    try {
                        if (isHls && hlsAdapter != null) {
                            grabber?.stop()
                            grabber?.release()
                            
                            grabber = createGrabber(activeUrl, headers, targetTimestamp / 1000L)
                            grabber.start()
                            seekFrame = try { grabber.grabSamples() } catch (_: Exception) { null }
                            seekOk = seekFrame != null
                        } else {
                            grabber?.timestamp = targetTimestamp
                            seekFrame = grabber?.grabSamples()
                            val currentTs = grabber?.timestamp ?: 0L
                            if (seekFrame != null && currentTs <= targetTimestamp + 3_000_000L && (currentTs >= targetTimestamp - 5_000_000L || targetTimestamp < 5_000_000L)) {
                                seekOk = true
                            } else {
                                Logger.e(
                                    "AudioEngine",
                                    "setTimestamp landed at $currentTs, expected near $targetTimestamp. Will reopen stream."
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Logger.e("AudioEngine", "FFmpeg seek error - ${e.message}")
                    }

                    if (!seekOk) {
                        val recovered = recoverStream(grabber, activeUrl, headers, seek)
                        if (recovered.first != null) {
                            grabber = recovered.first
                            if (recovered.second != null) activeUrl = recovered.second!!
                            seekFrame = try { grabber?.grabSamples() } catch (_: Exception) { null }
                            seekOk = seekFrame != null
                        }
                    }

                    if (seekOk && grabber != null) {
                        var f = seekFrame
                        var droppedCount = 0
                        while (f != null && !stopFlag && activeWorkerId == workerId) {
                            val ts = grabber.timestamp
                            if (ts >= targetTimestamp - 50_000L) {
                                val samples = f.samples
                                if (samples != null) {
                                    val (pcm, pcmLen) = interleave(samples, f.sampleRate, f.audioChannels)
                                    pushThroughDsp(pcm, pcmLen)
                                }
                                break
                            }
                            f = try {
                                grabber.grabSamples()
                            } catch (_: Exception) {
                                null
                            }
                            droppedCount++
                            if (droppedCount > 6000) break
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
                    Logger.e("AudioEngine", "Read error from grabber: ${e.message}")
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
                            Logger.e(
                                "AudioEngine",
                                "Premature EOF/error at $positionMs ms (duration=$durationMs ms). Recovering stream (will retry until success)..."
                            )
                            val recovered = recoverStream(grabber, activeUrl, headers, positionMs)
                            if (recovered.first != null) {
                                grabber = recovered.first
                                if (recovered.second != null) activeUrl = recovered.second!!
                                Logger.e("AudioEngine", "Stream recovery succeeded at $positionMs ms!")
                                continue
                            } else {
                                break
                            }
                        }
                    } else {
                        break
                    }
                }

                val samples = frame.samples ?: continue
                val (pcm, pcmLen) = interleave(samples, frame.sampleRate, frame.audioChannels)
                pushThroughDsp(pcm, pcmLen)
                drainStretcher(outBuf, localLine)

                if (seekRequestMs == -1L) {
                    positionMs = (grabber?.timestamp ?: 0L) / 1000L
                }
            }
        } catch (t: Throwable) {
            if (!stopFlag && activeWorkerId == workerId) {
                onError?.invoke(t)
            }
        } finally {
            try {
                grabber?.stop(); grabber?.release()
            } catch (_: Exception) {
            }
            closeLineInstance(localLine)
        }
    }

    private var interleaveBuffer = ShortArray(0)

    private fun interleave(buffers: Array<java.nio.Buffer>, frameRate: Int, frameChannels: Int): Pair<ShortArray, Int> {
        val first = buffers[0]
        if (first is ShortBuffer) {
            if (buffers.size == frameChannels && frameChannels > 1) {
                val len = first.remaining()
                val required = len * frameChannels
                if (interleaveBuffer.size < required) {
                    interleaveBuffer = ShortArray(required)
                }
                for (ch in 0 until frameChannels) {
                    val b = buffers[ch] as ShortBuffer
                    val bPos = b.position()
                    for (i in 0 until len) interleaveBuffer[i * frameChannels + ch] = b.get(bPos + i)
                }
                return Pair(interleaveBuffer, required)
            } else {
                val len = first.remaining()
                if (interleaveBuffer.size < len) {
                    interleaveBuffer = ShortArray(len)
                }
                val pos = first.position()
                for (i in 0 until len) {
                    interleaveBuffer[i] = first.get(pos + i)
                }
                return Pair(interleaveBuffer, len)
            }
        }
        if (first is java.nio.FloatBuffer) {
            if (buffers.size == frameChannels && frameChannels > 1) {
                val len = first.remaining()
                val required = len * frameChannels
                if (interleaveBuffer.size < required) {
                    interleaveBuffer = ShortArray(required)
                }
                for (ch in 0 until frameChannels) {
                    val b = buffers[ch] as java.nio.FloatBuffer
                    val bPos = b.position()
                    for (i in 0 until len) {
                        val f = b.get(bPos + i)
                        interleaveBuffer[i * frameChannels + ch] = (f * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                    }
                }
                return Pair(interleaveBuffer, required)
            } else {
                val len = first.remaining()
                if (interleaveBuffer.size < len) {
                    interleaveBuffer = ShortArray(len)
                }
                val pos = first.position()
                for (i in 0 until len) {
                    val f = first.get(pos + i)
                    interleaveBuffer[i] = (f * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                }
                return Pair(interleaveBuffer, len)
            }
        }
        val bb = (first as java.nio.ByteBuffer).order(ByteOrder.LITTLE_ENDIAN)
        val sb = bb.asShortBuffer()
        val len = sb.remaining()
        if (interleaveBuffer.size < len) {
            interleaveBuffer = ShortArray(len)
        }
        val pos = sb.position()
        for (i in 0 until len) {
            interleaveBuffer[i] = sb.get(pos + i)
        }
        return Pair(interleaveBuffer, len)
    }

    private var dspInputBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.LITTLE_ENDIAN)
    private var processedBuffer = ShortArray(0)

    private fun pushThroughDsp(pcm: ShortArray, length: Int = pcm.size) {
        val requiredBytes = length * 2
        var buf = if (dspInputBuffer.capacity() < requiredBytes) {
            dspInputBuffer = ByteBuffer.allocateDirect(requiredBytes).order(ByteOrder.LITTLE_ENDIAN)
            dspInputBuffer
        } else {
            dspInputBuffer.clear()
            dspInputBuffer
        }
        buf.asShortBuffer().put(pcm, 0, length)
        buf.position(0)
        buf.limit(requiredBytes)

        for (p in chain) {
            p.queueInput(buf)
            buf = p.getOutput()
        }

        val requiredShorts = buf.remaining() / 2
        if (processedBuffer.size < requiredShorts) {
            processedBuffer = ShortArray(requiredShorts)
        }
        val sb = buf.asShortBuffer()
        val pos = sb.position()
        for (i in 0 until requiredShorts) {
            processedBuffer[i] = sb.get(pos + i)
        }
        stretcher.queue(processedBuffer, requiredShorts)
    }

    private fun drainStretcher(outBuf: ShortArray, localLine: SourceDataLine?) {
        while (stretcher.available() >= outputChannels) {
            val n = stretcher.drain(outBuf, outBuf.size)
            if (n <= 0) break
            writeToLine(outBuf, n, localLine)
        }
    }

    private var lineWriteBuffer = ByteArray(0)

    private fun writeToLine(samples: ShortArray, count: Int, localLine: SourceDataLine?) {
        val l = localLine ?: line ?: return
        val requiredBytes = count * 2
        if (lineWriteBuffer.size < requiredBytes) {
            lineWriteBuffer = ByteArray(requiredBytes)
        }
        var bi = 0
        for (i in 0 until count) {
            val s = samples[i].toInt()
            lineWriteBuffer[bi++] = (s and 0xFF).toByte()
            lineWriteBuffer[bi++] = ((s shr 8) and 0xFF).toByte()
        }
        try {
            l.write(lineWriteBuffer, 0, requiredBytes)
        } catch (_: Exception) {
        }
    }


    private fun openLine(): SourceDataLine {
        val fmt = JavaAudioFormat(
            outputSampleRate.toFloat(), 16, outputChannels, true, false,
        )
        val info = DataLine.Info(SourceDataLine::class.java, fmt)

        val prefs = com.alananasss.kittytune.data.local.PlayerPreferences()
        val deviceName = prefs.getAudioDevice()
        var mixer: Mixer? = null

        if (deviceName.isNotEmpty()) {
            val isLinux = System.getProperty("os.name").lowercase().contains("linux")
            if (isLinux && deviceName.startsWith("alsa_output.")) {
                System.setProperty("javax.sound.sampled.SourceDataLine", "")
                try {
                    val pb = ProcessBuilder("sh", "-c", "pactl set-default-sink '$deviceName'")
                    pb.start().waitFor()
                } catch (_: Exception) {
                }
            } else {
                val mixerInfos = AudioSystem.getMixerInfo()
                val targetInfo = mixerInfos.firstOrNull { it.name.trim() == deviceName }
                if (targetInfo != null) {
                    try {
                        val m = AudioSystem.getMixer(targetInfo)
                        if (m.isLineSupported(info)) mixer = m
                    } catch (_: Exception) {
                    }
                }
            }
        }

        val l =
            if (mixer != null) mixer.getLine(info) as SourceDataLine else AudioSystem.getLine(info) as SourceDataLine
        l.open(fmt, outputSampleRate * outputChannels * 2 * 200 / 1000)
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
        } catch (_: Exception) {
        }
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
            try {
                currentWorker.join(300)
            } catch (_: InterruptedException) {
            }
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
