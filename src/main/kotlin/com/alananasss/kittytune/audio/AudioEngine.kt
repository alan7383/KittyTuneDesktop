package com.alananasss.kittytune.audio

import com.alananasss.kittytune.ui.player.AudioEffectsState
import com.alananasss.kittytune.utils.Logger
import com.alananasss.kittytune.utils.SignedUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.FrameGrabber
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

    /**
     * Catches the inter-sample overshoot lossy decoders leave above full scale, before
     * the 16-bit conversion turns it into flat-topped crackle. See [PeakLimiter].
     */
    private val limiter = PeakLimiter(outputSampleRate, outputChannels)

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
            hlsAdapter = if (url.contains(".m3u8")) HlsStreamAdapter(url, headers, ::reResolveUrl) else null
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

    /**
     * Set while the decode loop is servicing a reconnect nobody is waiting for, which changes two
     * things: recovery is bounded instead of retrying forever, and success resets the pause clock.
     */
    @Volatile
    private var warmUpRequested = false

    private var warmUpJob: kotlinx.coroutines.Job? = null

    private companion object {
        /** How long a pause has to last before resuming reconnects instead of trusting the socket. */
        const val RECONNECT_AFTER_PAUSE_MS = 30_000L

        /** Just past the point where resuming would reconnect, so we get there first. */
        const val WARM_UP_START_DELAY_MS = RECONNECT_AFTER_PAUSE_MS + 2_000L

        /** Under the ~3 minute life of a SoundCloud signature, so the stream never goes stale. */
        const val WARM_UP_INTERVAL_MS = 2 * 60 * 1000L

        /**
         * How many times a pause is worth refreshing. Roughly half an hour: past that, someone has
         * walked away rather than paused, and reconnecting on a timer all night to save them two
         * seconds is not a trade worth making.
         */
        const val WARM_UP_ROUNDS = 15

        /**
         * Recovery attempts allowed for a warm-up. Unbounded retries are right when a listener is
         * waiting for the sound to come back; here nobody is, and an offline machine would
         * otherwise reopen the stream every two seconds for as long as it stayed paused.
         */
        const val WARM_UP_RECOVERY_ATTEMPTS = 2

        /**
         * Re-resolve tries allowed while opening a track. Bounded so a genuinely unplayable
         * track still surfaces an error (and lets the queue move on) instead of spinning.
         */
        const val INITIAL_OPEN_ATTEMPTS = 3
    }

    fun play() {
        warmUpJob?.cancel()
        warmUpJob = null
        val pausedFor = if (paused && pausedTimestamp > 0L) System.currentTimeMillis() - pausedTimestamp else 0L
        paused = false
        pausedTimestamp = 0L

        // The decode loop can exit on its own — a recovery that ran out of attempts, an
        // unexpected throw — without ever reaching ENDED. The engine then still looks READY
        // while nothing is feeding the line, and pressing play did nothing at all: the
        // "stop a song, come back later, it never starts" report in issue #27. Restarting
        // whenever no worker is alive covers that regardless of how the thread went away.
        val workerAlive = worker?.isAlive == true

        if (state == State.ENDED) {
            setState(State.BUFFERING)
            prepare()
        } else if (!workerAlive && currentUrl != null) {
            Logger.e("AudioEngine", "play() with no live decoder; re-preparing at $positionMs ms")
            setState(State.BUFFERING)
            seekRequestMs = positionMs
            prepare()
        } else if (pausedFor > RECONNECT_AFTER_PAUSE_MS && currentUrl?.startsWith("http") == true) {
            // A CDN keep-alive rarely survives even half a minute, and SoundCloud's signed
            // URLs expire outright. Seeking forces a clean reconnect instead of a stall.
            seekTo(positionMs)
        }
        setPlaying(true)
    }

    fun pause() {
        paused = true
        pausedTimestamp = System.currentTimeMillis()
        setPlaying(false)
        scheduleWarmUp()
    }

    /**
     * Keeps a paused network stream connected, so pressing play is instant.
     *
     * [play] reconnects whenever the pause outlasted [RECONNECT_AFTER_PAUSE_MS] — a signed
     * SoundCloud URL dies in about three minutes and the CDN socket rarely survives even half of
     * that — and that reconnect is the couple of seconds between pressing play and hearing
     * anything (issue #33). It cannot be skipped, but it can be paid for while nobody is
     * listening, which is what this does.
     */
    private fun scheduleWarmUp() {
        warmUpJob?.cancel()
        if (currentUrl?.startsWith("http") != true) return
        warmUpJob = scope.launch {
            kotlinx.coroutines.delay(WARM_UP_START_DELAY_MS)
            var round = 0
            while (round < WARM_UP_ROUNDS && paused) {
                warmUpAfterPause()
                round++
                kotlinx.coroutines.delay(WARM_UP_INTERVAL_MS)
            }
        }
    }

    /**
     * Asks the decode loop to reconnect at the current position while staying paused.
     *
     * The loop services a seek request before it checks the pause flag, so this reuses the seek
     * path — which already re-signs an expired URL — rather than adding a second way to reopen a
     * stream. The pause clock is reset by that path on success, not here, so a warm-up that failed
     * still leaves [play] to reconnect the usual way.
     */
    @Synchronized
    private fun warmUpAfterPause() {
        if (!paused || pausedTimestamp <= 0L) return
        if (currentUrl?.startsWith("http") != true) return
        if (worker?.isAlive != true) return
        if (seekRequestMs >= 0L) return
        Logger.e("AudioEngine", "Warming up paused stream at $positionMs ms")
        warmUpRequested = true
        seekRequestMs = positionMs
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

        limiter.setCeiling(peakLimiterCeilingFor(state))
    }


    /**
     * Asked for a freshly signed URL for the loaded track; receives the URL that just failed (or
     * expired) so the owner can tell a stale cache entry from one already refreshed elsewhere.
     */
    var onReResolveUrl: (suspend (failedUrl: String) -> String?)? = null

    private fun createGrabber(targetUrl: String, headers: Map<String, String>, startPositionMs: Long = 0L): FFmpegFrameGrabber {
        val isHls = targetUrl.contains(".m3u8")
        if (isHls) {
            var adapter = hlsAdapter
            if (adapter == null || adapter.playlistUrl != targetUrl) {
                adapter = HlsStreamAdapter(targetUrl, headers, ::reResolveUrl)
                hlsAdapter = adapter
            }
            return FFmpegFrameGrabber(adapter.getInputStream(startPositionMs)).apply {
                format = "mp4" // Fragments are ISOBMFF (mp4)
                setOption("probesize", "32768") // 32KB is enough for MOOV atom + some audio
                setOption("analyzeduration", "0")
                sampleRate = outputSampleRate
                audioChannels = outputChannels
                // Hand us the decoder's own float output. javacv's default (SHORT) would
                // resample straight to s16 and saturate every overshoot on the way.
                sampleMode = FrameGrabber.SampleMode.FLOAT
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
                    // Only codes worth waiting on. A 4xx from the CDN means the signed URL is
                    // dead, not busy: retrying it burnt ~10s of backoff before the error even
                    // reached us, and only a fresh URL can fix it.
                    setOption("reconnect_on_http_error", "429,5xx")
                    setOption("reconnect_delay_max", "2")
                    setOption("rw_timeout", "3000000") // 3s timeout
                    setOption("tcp_nodelay", "1")
                }
                sampleRate = outputSampleRate
                audioChannels = outputChannels
                sampleMode = FrameGrabber.SampleMode.FLOAT
            }
        }
    }

    /**
     * Asks the owner for a freshly signed URL for whatever is loaded. Blocking on purpose: the
     * decode loop is a plain thread and has nothing to suspend into.
     */
    private fun reResolveUrl(failedUrl: String): String? {
        val reResolver = onReResolveUrl ?: return null
        var freshUrl: String? = null
        kotlinx.coroutines.runBlocking {
            try {
                freshUrl = reResolver.invoke(failedUrl)
            } catch (e: Exception) {
                Logger.e("AudioEngine", "Re-resolve failed: ${e.message}")
            }
        }
        return freshUrl?.takeIf { it.isNotEmpty() }
    }

    private fun recoverStream(
        oldGrabber: FFmpegFrameGrabber?,
        url: String,
        headers: Map<String, String>,
        resumePosMs: Long,
        maxAttempts: Int = Int.MAX_VALUE
    ): Pair<FFmpegFrameGrabber?, String?> {
        try {
            oldGrabber?.stop()
            oldGrabber?.release()
        } catch (_: Exception) {
        }

        var currentUrlToTry = url
        var attempt = 0

        while (!stopFlag && attempt < maxAttempts) {
            attempt++
            Logger.e("AudioEngine", "Network recovery attempt $attempt at $resumePosMs ms...")

            // Proactively re-resolve URL on network streams
            if (SignedUrl.isNetworkUrl(currentUrlToTry)) {
                reResolveUrl(currentUrlToTry)?.let { currentUrlToTry = it }
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
                        pushFrame(samples, f.audioChannels)
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
            // The URL may have been signed long before we got here — a queue prefetch that sat
            // through a whole track, a restored session — and SoundCloud's signature only lasts
            // minutes. Refresh it up front instead of paying for a 403 to find out.
            if (SignedUrl.isExpired(activeUrl)) {
                Logger.e("AudioEngine", "Stream URL expired before open; re-resolving")
                reResolveUrl(activeUrl)?.let { activeUrl = it }
            }

            var opening: FFmpegFrameGrabber? = null
            try {
                // Both halves can fail on a dead URL: an HLS playlist 403s while the grabber is
                // still being built, a progressive one only on start().
                opening = createGrabber(activeUrl, headers, positionMs)
                opening.start()
                grabber = opening
            } catch (e: Exception) {
                // A dead URL that slipped past the check above (unsigned, or the CDN disagrees
                // about the deadline). Re-resolve and retry a couple of times before handing the
                // failure up: the owner's own error path is a full teardown and reload.
                Logger.e("AudioEngine", "Initial open failed (${e.message}); recovering")
                val recovered = recoverStream(opening, activeUrl, headers, positionMs, INITIAL_OPEN_ATTEMPTS)
                grabber = recovered.first ?: throw e
                recovered.second?.let { activeUrl = it }
            }

            if (stopFlag || activeWorkerId != workerId) return

            // Only trust the adapter's duration while it actually describes what we opened: a
            // re-resolve can hand back a progressive URL where the playlist used to be.
            val adapter = hlsAdapter
            durationMs = if (adapter != null && adapter.playlistUrl == activeUrl) {
                adapter.totalDurationMs
            } else {
                grabber.lengthInTime / 1000L
            }

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

                    // Seeking is a fresh range request, so an expired signature turns it into a
                    // guaranteed 403. Skip straight to the re-resolve — waiting for that 403 (and
                    // FFmpeg's retries on it) is the stall behind "seek back after a long listen
                    // and nothing plays for ten seconds".
                    // On HLS the adapter does the fetching and can re-sign itself mid-track, so
                    // ask what it is actually using rather than the URL we were handed.
                    val effectiveUrl = hlsAdapter?.takeIf { it.playlistUrl == activeUrl }?.liveUrl ?: activeUrl
                    val urlExpired = SignedUrl.isExpired(effectiveUrl)
                    if (urlExpired) {
                        Logger.e("AudioEngine", "Stream URL expired; re-resolving before seek to $seek ms")
                    }

                    var seekOk = false
                    var seekFrame: Frame? = null
                    if (!urlExpired) try {
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
                        val recovered = recoverStream(
                            grabber,
                            activeUrl,
                            headers,
                            seek,
                            maxAttempts = if (warmUpRequested) WARM_UP_RECOVERY_ATTEMPTS else Int.MAX_VALUE,
                        )
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
                                pushFrame(samples, seekFrame!!.audioChannels)
                            }
                        } else {
                            var f = seekFrame
                            var droppedCount = 0
                            while (f != null && !stopFlag && activeWorkerId == workerId) {
                                val ts = grabber.timestamp
                                if (ts >= targetTimestamp - 50_000L) {
                                    val samples = f.samples
                                    if (samples != null) {
                                        pushFrame(samples, f.audioChannels)
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
                    limiter.flush()
                    chain.forEach { it.flush() }
                    localLine?.flush()

                    if (warmUpRequested) {
                        warmUpRequested = false
                        // The pause clock measures how long it has been since the stream was known
                        // good, which is what play() reads to decide whether to reconnect. A
                        // successful warm-up makes that "just now"; a failed one leaves it alone so
                        // resuming still reconnects.
                        if (seekOk) pausedTimestamp = System.currentTimeMillis()
                    }
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
                pushFrame(samples, frame.audioChannels)
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
    private var decodeBuffer = FloatArray(0)

    /**
     * Decodes one frame into the 16-bit effect chain: de-interleaves to float, limits the
     * peaks, then quantises. The limiter has to run while we are still in float — once the
     * samples are Shorts the overshoot has already been flat-topped.
     */
    private fun pushFrame(buffers: Array<java.nio.Buffer>, frameChannels: Int) {
        val n = decodeToFloat(buffers, frameChannels)
        if (n <= 0) return

        limiter.process(decodeBuffer, n)

        if (interleaveBuffer.size < n) interleaveBuffer = ShortArray(n)
        for (i in 0 until n) {
            interleaveBuffer[i] = (decodeBuffer[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }
        pushThroughDsp(interleaveBuffer, n)
    }

    /**
     * Normalises whatever sample format the grabber handed us into interleaved floats in
     * [-1, 1] in [decodeBuffer]. Returns the number of samples written.
     */
    private fun decodeToFloat(buffers: Array<java.nio.Buffer>, frameChannels: Int): Int {
        if (buffers.isEmpty()) return 0
        val first = buffers[0]
        val planar = buffers.size == frameChannels && frameChannels > 1
        val len = first.remaining()
        val required = if (planar) len * frameChannels else len
        if (required <= 0) return 0
        if (decodeBuffer.size < required) decodeBuffer = FloatArray(required)

        if (!planar && first is java.nio.FloatBuffer) {
            // The case that actually happens: SampleMode.FLOAT hands back packed floats,
            // already in [-1, 1], so take the block wholesale and leave the grabber's
            // buffer position where we found it.
            val pos = first.position()
            first.get(decodeBuffer, 0, len)
            first.position(pos)
            return required
        }

        if (planar) {
            for (ch in 0 until frameChannels) {
                val b = buffers[ch]
                val bPos = b.position()
                for (i in 0 until len) {
                    decodeBuffer[i * frameChannels + ch] = sampleAt(b, bPos + i)
                }
            }
        } else {
            val pos = first.position()
            for (i in 0 until len) {
                decodeBuffer[i] = sampleAt(first, pos + i)
            }
        }
        return required
    }

    /** Reads one sample from [buffer] at [index], scaled to [-1, 1]. */
    private fun sampleAt(buffer: java.nio.Buffer, index: Int): Float = when (buffer) {
        is java.nio.FloatBuffer -> buffer.get(index)
        is ShortBuffer -> buffer.get(index) / 32768f
        is java.nio.IntBuffer -> buffer.get(index) / 2147483648f
        is java.nio.DoubleBuffer -> buffer.get(index).toFloat()
        // javacv only hands back a plain ByteBuffer for unsigned 8-bit samples.
        is ByteBuffer -> ((buffer.get(index).toInt() and 0xFF) - 128) / 128f
        else -> 0f
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
        warmUpJob?.cancel()
        warmUpJob = null
        warmUpRequested = false
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
        limiter.flush()
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
