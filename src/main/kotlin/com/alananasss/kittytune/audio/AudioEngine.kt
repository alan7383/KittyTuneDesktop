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
    private val vintageMp3 = VintageMp3AudioProcessor()
    private val vocalRemover = VocalRemoverAudioProcessor()
    private val vocalBoost = VocalBoostAudioProcessor()
    private val flanger = FlangerAudioProcessor()
    private val partyNextDoor = PartyNextDoorAudioProcessor()
    private val superWide = SuperWideAudioProcessor()
    private val vinylLoFi = VinylLoFiAudioProcessor()
    private val phaser = PhaserAudioProcessor()
    private val megaphone = MegaphoneRadioAudioProcessor()
    private val robotVocoder = RobotVocoderAudioProcessor()
    private val chorus = ChorusAudioProcessor()
    private val underwater = UnderwaterAudioProcessor()
    private val tranceGate = TranceGateAudioProcessor()
    private val pingPongDelay = PingPongDelayAudioProcessor()
    private val chiptune = ChiptuneAudioProcessor()
    private val shimmerReverb = ShimmerReverbAudioProcessor()
    private val rotarySpeaker = RotarySpeakerAudioProcessor()
    private val tapeSaturation = TapeSaturationAudioProcessor()
    private val subOctaver = SubOctaverAudioProcessor()
    private val emptyMall = EmptyMallAudioProcessor()
    private val gramophone = GramophoneAudioProcessor()
    private val reverseEcho = ReverseEchoAudioProcessor()
    private val stadium = StadiumAudioProcessor()
    private val walkman = CassetteWalkmanAudioProcessor()
    private val asmrVocal = AsmrVocalAudioProcessor()
    private val nightDrive = NightDriveAudioProcessor()

    private val chain: List<AudioProcessor> = listOf(
        fx, reverb, eightD, earrape, normalization, mono,
        vintageMp3, vocalRemover, vocalBoost, flanger, partyNextDoor,
        superWide, vinylLoFi, phaser, megaphone, robotVocoder, chorus,
        underwater, tranceGate, pingPongDelay, chiptune, shimmerReverb,
        rotarySpeaker, tapeSaturation, subOctaver, emptyMall, gramophone,
        reverseEcho, stadium, walkman, asmrVocal, nightDrive
    )
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

    @Volatile
    private var pausedTimestamp = 0L

    fun play() {
        val wasLongPause = paused && pausedTimestamp > 0L && (System.currentTimeMillis() - pausedTimestamp > 2 * 60 * 1000L)
        paused = false
        pausedTimestamp = 0L
        if (state == State.ENDED) {
            setState(State.BUFFERING)
            prepare()
        } else if (wasLongPause && currentUrl?.startsWith("http") == true) {
            // After a long pause (> 2 min), the CDN TCP connection or signed token may have expired.
            // Seek to current position to cleanly reconnect without hanging.
            seekTo(positionMs)
        }
        setPlaying(true)
    }

    fun pause() {
        paused = true
        pausedTimestamp = System.currentTimeMillis()
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
        earrape.setIntensity(state.earrapeIntensity)
        normalization.setParameters(state.isNormalizationEnabled, state.normalizationLevel)
        mono.setEnabled(state.isMonoEnabled)

        vintageMp3.setEnabled(state.isVintageMp3Enabled)
        vintageMp3.setCompression(state.vintageMp3Compression)

        vocalRemover.setEnabled(state.isVocalRemoverEnabled)
        vocalRemover.setSuppressionLevel(state.vocalRemoverLevel)

        vocalBoost.setEnabled(state.isVocalBoostEnabled)
        vocalBoost.setIntensity(state.vocalBoostIntensity)

        flanger.setEnabled(state.isFlangerEnabled)
        flanger.setIntensity(state.flangerIntensity)
        flanger.setSpeed(state.flangerSpeed)

        partyNextDoor.setEnabled(state.isPartyNextDoorEnabled)
        partyNextDoor.setIsolation(state.partyNextDoorIsolation)
        partyNextDoor.setReverb(state.partyNextDoorReverb)
        partyNextDoor.setBassRumble(state.partyNextDoorBassRumble)

        superWide.setEnabled(state.isSuperWideEnabled)
        superWide.setWidth(state.superWideWidth)
        superWide.setDepth(state.superWideDepth)

        vinylLoFi.setEnabled(state.isVinylLoFiEnabled)
        vinylLoFi.setCrackles(state.vinylCrackles)
        vinylLoFi.setFlutter(state.vinylFlutter)

        phaser.setEnabled(state.isPhaserEnabled)
        phaser.setSpeed(state.phaserSpeed)
        phaser.setFeedback(state.phaserFeedback)

        megaphone.setEnabled(state.isMegaphoneEnabled)
        megaphone.setTone(state.megaphoneTone)
        megaphone.setDrive(state.megaphoneDrive)

        robotVocoder.setEnabled(state.isRobotVocoderEnabled)
        robotVocoder.setFrequency(state.robotFrequency)
        robotVocoder.setMix(state.robotMix)

        chorus.setEnabled(state.isChorusEnabled)
        chorus.setRate(state.chorusRate)
        chorus.setDepth(state.chorusDepth)

        underwater.setEnabled(state.isUnderwaterEnabled)
        underwater.setDepth(state.underwaterDepth)
        underwater.setBubbles(state.underwaterBubbles)

        tranceGate.setEnabled(state.isTranceGateEnabled)
        tranceGate.setSpeed(state.tranceGateSpeed)
        tranceGate.setPattern(state.tranceGatePattern)
        tranceGate.setMix(state.tranceGateMix)

        pingPongDelay.setEnabled(state.isPingPongDelayEnabled)
        pingPongDelay.setDelayTime(state.pingPongDelayTime)
        pingPongDelay.setFeedback(state.pingPongFeedback)

        chiptune.setEnabled(state.isChiptuneEnabled)
        chiptune.setBits(state.chiptuneBits)
        chiptune.setSampleRateDown(state.chiptuneSampleRate)

        shimmerReverb.setEnabled(state.isShimmerReverbEnabled)
        shimmerReverb.setSize(state.shimmerSize)
        shimmerReverb.setShimmerMix(state.shimmerMix)

        rotarySpeaker.setEnabled(state.isRotarySpeakerEnabled)
        rotarySpeaker.setSpeed(state.rotarySpeed)
        rotarySpeaker.setDepth(state.rotaryDepth)

        tapeSaturation.setEnabled(state.isTapeSaturationEnabled)
        tapeSaturation.setWarmth(state.tapeWarmth)
        tapeSaturation.setExciter(state.tapeExciter)

        subOctaver.setEnabled(state.isSubOctaverEnabled)
        subOctaver.setSubLevel(state.subOctaverLevel)
        subOctaver.setSubCutoff(state.subOctaverCutoff)

        emptyMall.setEnabled(state.isEmptyMallEnabled)
        emptyMall.setDistance(state.emptyMallDistance)
        emptyMall.setGlassReverb(state.emptyMallReverb)

        gramophone.setEnabled(state.isGramophoneEnabled)
        gramophone.setAge(state.gramophoneAge)
        gramophone.setHorn(state.gramophoneHorn)

        reverseEcho.setEnabled(state.isReverseEchoEnabled)
        reverseEcho.setTime(state.reverseEchoTime)
        reverseEcho.setFeedback(state.reverseEchoFeedback)

        stadium.setEnabled(state.isStadiumEnabled)
        stadium.setStadiumSize(state.stadiumSize)
        stadium.setAtmosphere(state.stadiumAtmosphere)

        walkman.setEnabled(state.isWalkmanEnabled)
        walkman.setDrive(state.walkmanDrive)
        walkman.setTapeHiss(state.walkmanHiss)

        asmrVocal.setEnabled(state.isAsmrVocalEnabled)
        asmrVocal.setProximity(state.asmrProximity)
        asmrVocal.setAirSheen(state.asmrAir)

        nightDrive.setEnabled(state.isNightDriveEnabled)
        nightDrive.setCabinWidth(state.nightDriveCabin)
        nightDrive.setRoadRumble(state.nightDriveRoad)

        val pitch = if (state.isPitchEnabled) state.speed else 1f
        stretcher.setParameters(state.speed, pitch)
    }


    var onReResolveUrl: (suspend () -> String?)? = null

    private fun createGrabber(targetUrl: String, headers: Map<String, String>, startPositionMs: Long = 0L): FFmpegFrameGrabber {
        val isHls = targetUrl.contains(".m3u8")
        if (isHls) {
            var adapter = hlsAdapter
            if (adapter == null || adapter.playlistUrl != targetUrl) {
                adapter = HlsStreamAdapter(targetUrl, headers)
                hlsAdapter = adapter
            }
            return FFmpegFrameGrabber(adapter.getInputStream(startPositionMs)).apply {
                format = "mp4" // Fragments are ISOBMFF (mp4)
                setOption("probesize", "32768") // 32KB is enough for MOOV atom + some audio
                setOption("analyzeduration", "0")
                sampleRate = outputSampleRate
                audioChannels = outputChannels
            }
        } else {
            return FFmpegFrameGrabber(targetUrl).apply {
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
                    setOption("reconnect_delay_max", "3")
                    setOption("rw_timeout", "3000000") // 3s timeout
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
        var attempt = 0

        while (!stopFlag) {
            attempt++
            Logger.e("AudioEngine", "Network recovery attempt $attempt at $resumePosMs ms...")

            // Proactively re-resolve URL on network streams
            val reResolver = onReResolveUrl
            if (reResolver != null && (currentUrlToTry.startsWith("http://") || currentUrlToTry.startsWith("https://"))) {
                var freshUrl: String? = null
                kotlinx.coroutines.runBlocking {
                    try {
                        freshUrl = reResolver.invoke()
                    } catch (e: Exception) {
                        Logger.e("AudioEngine", "Re-resolve attempt $attempt failed: ${e.message}")
                    }
                }
                if (!freshUrl.isNullOrEmpty()) {
                    currentUrlToTry = freshUrl
                }
            }

            try {
                val newG = createGrabber(currentUrlToTry, headers, resumePosMs)
                newG.start()
                val isHls = currentUrlToTry.contains(".m3u8")
                if (!isHls && resumePosMs > 0) {
                    try {
                        newG.timestamp = resumePosMs * 1000L
                    } catch (_: Exception) {}
                }
                val f = try { newG.grabSamples() } catch (_: Exception) { null }
                if (f != null) {
                    val samples = f.samples
                    if (samples != null) {
                        val (pcm, pcmLen) = interleave(samples, f.sampleRate, f.audioChannels)
                        pushThroughDsp(pcm, pcmLen)
                    }
                    Logger.e("AudioEngine", "Recovery succeeded on attempt $attempt!")
                    return Pair(newG, currentUrlToTry)
                }
            } catch (e: Exception) {
                Logger.e("AudioEngine", "Reopen attempt $attempt failed (${e.message}).")
            }

            val backoffMs = (attempt * 400L).coerceAtMost(2000L)
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

                    val isHls = activeUrl.contains(".m3u8")

                    var seekOk = false
                    var seekFrame: Frame? = null
                    try {
                        if (isHls) {
                            grabber?.stop()
                            grabber?.release()
                            
                            grabber = createGrabber(activeUrl, headers, seek)
                            grabber.start()
                            seekFrame = try { grabber.grabSamples() } catch (_: Exception) { null }
                            seekOk = seekFrame != null
                        } else {
                            grabber?.timestamp = targetTimestamp
                            seekFrame = grabber?.grabSamples()
                            seekOk = seekFrame != null
                        }
                    } catch (e: Exception) {
                        Logger.e("AudioEngine", "Seek error - ${e.message}")
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
                        if (isHls) {
                            // On HLS, createGrabber(seek) already positioned at the requested segment.
                            val samples = seekFrame?.samples
                            if (samples != null) {
                                val (pcm, pcmLen) = interleave(samples, seekFrame!!.sampleRate, seekFrame!!.audioChannels)
                                pushThroughDsp(pcm, pcmLen)
                            }
                        } else {
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
                                if (droppedCount > 150) break
                            }
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
                                "Premature EOF/error at $positionMs ms (duration=$durationMs ms). Recovering stream..."
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

    private fun closeLineInstance(localLine: SourceDataLine?, drain: Boolean = true) {
        try {
            if (drain) localLine?.drain() else localLine?.flush()
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
                currentWorker.join(50)
            } catch (_: InterruptedException) {
            }
        }
        worker = null
        closeLineInstance(line, drain = false)
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
