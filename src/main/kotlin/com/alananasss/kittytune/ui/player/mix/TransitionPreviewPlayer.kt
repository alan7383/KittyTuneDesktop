package com.alananasss.kittytune.ui.player.mix

import com.alananasss.kittytune.audio.AudioEngine
import com.alananasss.kittytune.audio.automix.EffectTransitionMode
import com.alananasss.kittytune.audio.automix.EqTransitionMode
import com.alananasss.kittytune.audio.automix.LoopTransitionMode
import com.alananasss.kittytune.audio.automix.MixTempoMatch
import com.alananasss.kittytune.audio.automix.MixTransitionPresets
import com.alananasss.kittytune.audio.automix.TempoMatchMode
import com.alananasss.kittytune.audio.automix.TransitionConfig
import com.alananasss.kittytune.audio.automix.TransitionSweepAudioProcessor
import com.alananasss.kittytune.audio.automix.bassLevelAt
import com.alananasss.kittytune.audio.automix.curveLevelAt
import com.alananasss.kittytune.audio.automix.filterInFractionAt
import com.alananasss.kittytune.audio.automix.filterLegIsHighPass
import com.alananasss.kittytune.audio.automix.filterOutFractionAt
import com.alananasss.kittytune.audio.automix.volumeInAt
import com.alananasss.kittytune.audio.automix.volumeOutAt
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.StreamResolver
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * The transition editor's two-deck rehearsal (desktop port of Android PR #83).
 *
 * Two isolated [AudioEngine]s play the pair. The editor's frame loop drives everything through [updateFrame]:
 * deck gains come from the same [volumeOutAt]/[volumeInAt] envelopes the canvas draws, the bass handover rides
 * each deck's duck shelf (ownership from [bassLevelAt]), the filter legs ride each deck's
 * [TransitionSweepAudioProcessor] fed by [filterOutFractionAt]/[filterInFractionAt], and the effect tail rides
 * the outgoing deck's transition-effects tail, so what is drawn is what is heard. Outside the zone
 * ([updateFrame] progress < 0 or > 1) the decks run dry and solo.
 *
 * The main player is paused while the rehearsal runs, and the decks never touch app-wide playback state.
 * The API matches the Android class, so the editor drives both the same way.
 */
class TransitionPreviewPlayer(
    private val trackA: Track,
    private val trackB: Track,
) {

    private class Deck(val engine: AudioEngine) {
        /** Set on a playback error; the deck then drops out of the rehearsal. */
        @Volatile var failed = false
    }

    enum class PrepareState { RESOLVING, READY, NO_AUDIO }

    private val _prepareState = MutableStateFlow(PrepareState.RESOLVING)
    val prepareState: StateFlow<PrepareState> = _prepareState

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var syncJob: Job? = null

    @Volatile private var deckA: Deck? = null
    @Volatile private var deckB: Deck? = null
    @Volatile private var prepared = false

    /** Deck A's tempo over deck B's: deck B is stretched to it so both grids line up in the overlap. */
    @Volatile private var beatLockRatio = 1f
    @Volatile private var startRequested = false
    @Volatile private var audioRunning = false

    private var config = TransitionConfig()
    private var overlapMs = 0L
    private var armedLeadInMs = 0L
    private var zoneEntered = false
    private var beatMs = 500f
    private var mainPlaybackPaused = false

    /** Resolves both streams in parallel and loads the two decks, paused at the run's start. */
    suspend fun prepare() {
        if (prepared) return
        _prepareState.value = PrepareState.RESOLVING
        val (a, b) = coroutineScope {
            val a = async(Dispatchers.IO) { runCatching { buildDeck(trackA) }.getOrNull() }
            val b = async(Dispatchers.IO) { runCatching { buildDeck(trackB) }.getOrNull() }
            a.await() to b.await()
        }
        if (prepared) {
            // A release() slipped in while the streams were resolving.
            listOfNotNull(a, b).forEach { runCatching { it.engine.release() } }
            return
        }
        deckA = a
        deckB = b
        prepared = true
        _prepareState.value = if (a != null || b != null) PrepareState.READY else PrepareState.NO_AUDIO
        Logger.d(TAG, "Preview decks ready (A=${a != null}, B=${b != null}); startRequested=$startRequested")
        trySyncStart()
    }

    /** Arms the shape the editor currently draws; the next [start] obeys it. */
    fun arm(newConfig: TransitionConfig, overlapDurationMs: Long) {
        config = newConfig
        overlapMs = overlapDurationMs.coerceAtLeast(1L)
        beatMs = if (config.duration.beats > 0) overlapMs.toFloat() / config.duration.beats else 500f
    }

    /** Live curve update: reaches the running decks on the next [updateFrame] without re-seeking. */
    fun updateCurves(newConfig: TransitionConfig) {
        config = newConfig
    }

    /**
     * Beat-lock with the editor's per-pair toggle: AUTO matches within ±10 BPM / 8 % (octaves folded), MATCH
     * forces the stretch inside ±15 %, ORIGINAL leaves both tempos alone.
     */
    fun setTempoPair(outBpm: Float, inBpm: Float, mode: TempoMatchMode = TempoMatchMode.AUTO) {
        val (canMatch, ratio) = calculateSpotifyBpmMatch(outBpm, inBpm)
        beatLockRatio = when (mode) {
            TempoMatchMode.ORIGINAL -> 1f
            TempoMatchMode.MATCH -> {
                val folded = listOf(inBpm, inBpm * 2f, inBpm * 0.5f).minByOrNull { kotlin.math.abs(outBpm - it) } ?: inBpm
                if (folded > 0f && outBpm > 0f) (outBpm / folded).coerceIn(0.85f, 1.15f) else 1f
            }
            TempoMatchMode.AUTO -> if (canMatch) ratio else 1f
        }
        deckB?.takeIf { !it.failed }?.engine?.setStretcherRatio(beatLockRatio, 1f)
    }

    /** Dragging deck B's wave: re-seeks the incoming deck to its new entry without touching the run. */
    fun updateIncomingOffset(incomingStartMs: Long) {
        config = config.copy(userNudgeOffsetMs = incomingStartMs)
        if (!audioRunning) return
        deckB?.takeIf { !it.failed }?.engine?.let { it.seekTo(incomingEntryMs(it.durationMs)) }
    }

    /** Begins (or restarts) the run: the lead-in is derived once, both decks re-seek, playback begins when ready. */
    fun start() {
        armedLeadInMs = computeArmedLeadInMs()
        startRequested = true
        if (!prepared) return
        audioRunning = false
        seekPreviewStart()
        updateFrame(-1f)
        trySyncStart()
    }

    /** The run-in this armed run uses; the editor's clock spans it before the zone. */
    fun effectiveLeadInMs(): Long = armedLeadInMs.coerceIn(0L, PREVIEW_LEAD_IN_MS)

    /** Stops and rewinds both decks to the run's start, so the next Play opens from the run-in again. */
    fun pauseAndReset() {
        startRequested = false
        audioRunning = false
        zoneEntered = false
        syncJob?.cancel()
        deckA?.takeIf { !it.failed }?.engine?.let { runCatching { it.pause(); it.seekTo(previewStartMs(it.durationMs)) } }
        deckB?.takeIf { !it.failed }?.engine?.let { runCatching { it.pause(); it.seekTo(incomingEntryMs(it.durationMs)) } }
    }

    /** The end of the run: stop and park the decks. */
    fun finishAndRelease() {
        pauseAndReset()
        mainPlaybackPaused = false
    }

    /**
     * One editor frame. [progress] is the zone's own coordinate: 0..1 inside the transition, negative during
     * the run-in (only the outgoing deck, dry) and above 1 during the run-out (only the incoming deck, dry).
     * The first frame at or past 0 releases the incoming deck into the zone.
     */
    fun updateFrame(progress: Float) {
        val a = deckA?.takeIf { !it.failed }?.engine
        val b = deckB?.takeIf { !it.failed }?.engine
        if (progress < 0f) {
            a?.setVolume(1f)
            b?.setVolume(0f)
            clearFrameProcessors()
            return
        }
        if (!zoneEntered) {
            zoneEntered = true
            releaseIncomingDeck()
        }
        if (progress > 1f) {
            // Run-out: the incoming track plays on alone. If it never made it, the outgoing deck keeps the floor.
            val incomingAlive = b != null
            a?.let { if (incomingAlive) it.pause(); it.setVolume(if (incomingAlive) 0f else 1f) }
            b?.setVolume(1f)
            clearFrameProcessors()
            return
        }

        val t = progress.coerceIn(0f, 1f)
        // Gains: the exact envelopes the canvas draws; hand-placed curves win over the named modes.
        a?.setVolume(curveLevelAt(config.customVolumeOut, t) ?: volumeOutAt(config.volumeCurve, t))
        b?.setVolume(curveLevelAt(config.customVolumeIn, t) ?: volumeInAt(config.volumeCurve, t))

        // Bass handover: each deck's shelf cuts deeper as it loses the lows.
        val bassActive = config.customEqOut.isNotEmpty() || config.customEqIn.isNotEmpty() ||
            config.eqMode != EqTransitionMode.NONE
        val bassLevelOut = curveLevelAt(config.customEqOut, t)
            ?: if (config.eqMode == EqTransitionMode.NONE) 1f else bassLevelAt(config.eqMode, t)
        val bassLevelIn = curveLevelAt(config.customEqIn, t)
            ?: if (config.eqMode == EqTransitionMode.NONE) 1f else 1f - bassLevelAt(config.eqMode, t)
        a?.setDuckMix(if (bassActive) 1f - bassLevelOut else 0f)
        b?.setDuckMix(if (bassActive) 1f - bassLevelIn else 0f)

        // The filter, leg by leg: each deck has its own tap and cutoff.
        val legA = curveLevelAt(config.customFilterOut, t) ?: filterOutFractionAt(config.filterMode, t)
        val legB = curveLevelAt(config.customFilterIn, t) ?: filterInFractionAt(config.filterMode, t)
        a?.transitionSweep?.let { sweep ->
            if (legA != null) sweep.setSweep(sweepMode(filterLegIsHighPass(config.filterMode, isOutgoingDeck = true)), legA)
            else sweep.clear()
        }
        b?.transitionSweep?.let { sweep ->
            if (legB != null) sweep.setSweep(sweepMode(filterLegIsHighPass(config.filterMode, isOutgoingDeck = false)), legB)
            else sweep.clear()
        }

        // The dramatic exit: the outgoing deck's tail, its wet level the white curve the canvas draws.
        val effectActive = config.customEffect.isNotEmpty() || config.effectMode != EffectTransitionMode.NONE
        if (effectActive) {
            val wetLevel = curveLevelAt(config.customEffect, t) ?: previewEffectWetAt(config.effectMode, t)
            val delayBeats = previewEffectDelayBeats(config.effectMode)
            val delayMs = if (delayBeats > 0f) delayBeats * beatMs else 95f
            a?.transitionEffects?.setTail(wetLevel, delayMs, previewEffectFeedback(config.effectMode))
        } else {
            a?.transitionEffects?.resetTail()
        }
        b?.transitionEffects?.resetTail()
    }

    /** The encore: keeps deck A inside its loop region while the zone runs. */
    fun applyLoop() {
        val beats = previewLoopBeats(config.loopMode)
        if (beats <= 0) return
        val engine = deckA?.takeIf { !it.failed }?.engine ?: return
        if (!engine.isPlaying) return
        val zoneStart = zoneStartMs(engine.durationMs, trackA)
        val zoneEnd = zoneStart + (beats * beatMs).toLong()
        if (zoneEnd > zoneStart && engine.positionMs >= zoneEnd) engine.seekTo(zoneStart)
    }

    /** Wraps the run back to its start: deck A to the run-in, deck B parked at its entry. */
    fun restartRun() {
        zoneEntered = false
        deckA?.takeIf { !it.failed }?.engine?.let { runCatching { it.seekTo(previewStartMs(it.durationMs)); if (startRequested) it.play() } }
        deckB?.takeIf { !it.failed }?.engine?.let { runCatching { it.pause(); it.seekTo(incomingEntryMs(it.durationMs)) } }
    }

    /** Parks both decks at the zone's own start (no run-in). */
    fun seekZoneStart() {
        zoneEntered = false
        deckA?.takeIf { !it.failed }?.engine?.let { it.seekTo(zoneStartMs(it.durationMs, trackA)) }
        deckB?.takeIf { !it.failed }?.engine?.let { it.seekTo(incomingEntryMs(it.durationMs)) }
    }

    /** Tears both decks down; the editor calls this when it leaves. */
    fun release() {
        startRequested = false
        audioRunning = false
        zoneEntered = false
        prepared = true // a prepare() still resolving releases what it built instead of installing it
        _prepareState.value = PrepareState.NO_AUDIO
        listOfNotNull(deckA, deckB).forEach { deck ->
            runCatching { deck.engine.release() }.onFailure { Logger.w(TAG, "Preview deck release failed: ${it.message}") }
        }
        deckA = null
        deckB = null
        scope.cancel()
    }

    private fun seekPreviewStart() {
        zoneEntered = false
        deckA?.takeIf { !it.failed }?.engine?.let { it.seekTo(previewStartMs(it.durationMs)) }
        deckB?.takeIf { !it.failed }?.engine?.let { it.pause(); it.seekTo(incomingEntryMs(it.durationMs)) }
    }

    /**
     * Starts the run once every live deck is ready: deck A plays, deck B waits at its entry until the zone
     * opens. Polls, since the decks load on their own threads.
     */
    private fun trySyncStart() {
        if (!startRequested || audioRunning || syncJob?.isActive == true) return
        syncJob = scope.launch {
            val startedAt = System.currentTimeMillis()
            while (isActive && startRequested && !audioRunning) {
                val decks = listOfNotNull(deckA, deckB).filter { !it.failed }
                if (decks.isEmpty()) return@launch
                val ready = decks.all { it.engine.state == AudioEngine.State.READY && it.engine.durationMs > 0L }
                if (ready || System.currentTimeMillis() - startedAt > READY_TIMEOUT_MS) {
                    decks.filter { it.engine.state != AudioEngine.State.READY }.forEach { it.failed = true }
                    if (!mainPlaybackPaused) {
                        // The rehearsal owns the speakers while it runs.
                        runCatching { if (MusicManager.player.isPlaying) MusicManager.player.pause() }
                        mainPlaybackPaused = true
                    }
                    seekPreviewStart()
                    deckB?.takeIf { !it.failed }?.engine?.setStretcherRatio(beatLockRatio, 1f)
                    deckA?.takeIf { !it.failed }?.engine?.play()
                    audioRunning = true
                    return@launch
                }
                delay(50)
            }
        }
    }

    /** Three seconds of the outgoing track before the zone, or less when the zone sits closer to its head. */
    private fun computeArmedLeadInMs(): Long {
        val engine = deckA?.takeIf { !it.failed }?.engine ?: return PREVIEW_LEAD_IN_MS
        val duration = engine.durationMs.takeIf { it > 0L } ?: trackA.durationMs?.takeIf { it > 0L } ?: return PREVIEW_LEAD_IN_MS
        val zoneStart = zoneStartMs(duration, trackA)
        return if (zoneStart <= 0L) 0L else PREVIEW_LEAD_IN_MS.coerceAtMost(zoneStart)
    }

    private fun previewStartMs(deckDurationMs: Long): Long =
        (zoneStartMs(deckDurationMs, trackA) - armedLeadInMs).coerceAtLeast(0L)

    /** Where the overlap begins on deck A: the hand-placed anchor when one was drawn, else the end minus the zone. */
    private fun zoneStartMs(deckDurationMs: Long, outgoing: Track): Long {
        val duration = deckDurationMs.takeIf { it > 0L } ?: outgoing.durationMs?.takeIf { it > 0L } ?: return 0L
        config.zoneStartMs.takeIf { it >= 0L }?.let { anchor ->
            return anchor.coerceIn(0L, (duration - overlapMs / 2).coerceAtLeast(0L))
        }
        return (duration - overlapMs + config.userNudgeOffsetMs.coerceAtMost(overlapMs / 2))
            .coerceIn(0L, (duration - 1).coerceAtLeast(0L))
    }

    /** Where deck B enters: the hand-drawn nudge, clamped so the deck still has the whole zone ahead. */
    private fun incomingEntryMs(deckDurationMs: Long): Long {
        val offset = config.userNudgeOffsetMs.coerceAtLeast(0L)
        if (offset <= 0L) return 0L
        val duration = deckDurationMs.takeIf { it > 0L } ?: return offset
        return offset.coerceAtMost((duration - overlapMs).coerceAtLeast(0L))
    }

    private fun releaseIncomingDeck() {
        deckB?.takeIf { !it.failed }?.engine?.let { engine ->
            engine.setStretcherRatio(beatLockRatio, 1f)
            engine.seekTo(incomingEntryMs(engine.durationMs))
            engine.play()
        }
    }

    private fun clearFrameProcessors() {
        listOfNotNull(deckA?.engine, deckB?.engine).forEach { engine ->
            engine.setDuckMix(0f)
            engine.transitionSweep.clear()
            engine.transitionEffects.resetTail()
        }
    }

    /** Loads [track] into a new isolated deck, paused at 0. Null when no stream could be resolved. */
    private suspend fun buildDeck(track: Track): Deck? {
        val local = runCatching { AppDatabase.downloadDao.getTrack(track.id) }.getOrNull()
            ?.localAudioPath?.takeIf { it.isNotBlank() && File(it).exists() }
        val url = local ?: StreamResolver.resolveStream(track)?.let { raw ->
            if (raw.startsWith("metrofuse-deezer://")) {
                com.alananasss.kittytune.audio.providers.deezer.DeezerAudioProxy.getPlayableUrlFromDeezerUri(raw)
            } else raw
        } ?: return null
        val engine = AudioEngine(isolated = true)
        val deck = Deck(engine)
        engine.onError = { e ->
            Logger.w(TAG, "Preview deck error, dropping it from the preview: ${e.message}")
            deck.failed = true
            runCatching { engine.pause() }
        }
        engine.setVolume(0f)
        engine.setMediaItem(url, if (local != null) emptyMap() else MusicManager.player.buildHeaders(track), 0L, track.id)
        engine.prepare()
        engine.pause()
        return deck
    }

    companion object {
        private const val TAG = "TransitionPreview"
        private const val READY_TIMEOUT_MS = 20_000L

        /** How much of the outgoing track plays alone before the zone opens. */
        const val PREVIEW_LEAD_IN_MS = 3_000L

        /** How much of the incoming track plays alone after the zone closes. */
        const val PREVIEW_TAIL_OUT_MS = 3_000L

        const val MAX_TEMPO_STRETCH = MixTempoMatch.MAX_TEMPO_STRETCH

        /** Same rule as the engine: within ±10 BPM (octaves folded) and an 8 % stretch. */
        fun calculateSpotifyBpmMatch(outBpm: Float, inBpm: Float): Pair<Boolean, Float> = MixTempoMatch.match(outBpm, inBpm)

        private fun sweepMode(highPass: Boolean) =
            if (highPass) TransitionSweepAudioProcessor.SweepMode.HIGH_PASS else TransitionSweepAudioProcessor.SweepMode.LOW_PASS
    }
}

/** The outgoing deck's effects wet level (0..1) at [x]: the white curve the canvas draws. */
private fun previewEffectWetAt(mode: EffectTransitionMode, x: Float): Float {
    val p = x.coerceIn(0f, 1f)
    return when (mode) {
        EffectTransitionMode.REVERB_OUT_CENTER -> MixTransitionPresets.smoothstep(0.05f, 0.35f, p)
        EffectTransitionMode.REVERB_CUT_END ->
            MixTransitionPresets.smoothstep(0.10f, 0.55f, p) * (1f - MixTransitionPresets.smoothstep(0.85f, 0.95f, p))
        EffectTransitionMode.REVERB_OUT_END -> MixTransitionPresets.smoothstep(0.35f, 0.90f, p)
        EffectTransitionMode.DELAY_HALF_CUT_END,
        EffectTransitionMode.DELAY_THREE_QUARTER_CUT_END ->
            MixTransitionPresets.smoothstep(0.15f, 0.50f, p) * (1f - MixTransitionPresets.smoothstep(0.80f, 0.92f, p))
        EffectTransitionMode.ECHO_HALF_CUT_END ->
            MixTransitionPresets.smoothstep(0.10f, 0.45f, p) * (1f - MixTransitionPresets.smoothstep(0.75f, 0.90f, p))
        EffectTransitionMode.ECHO_HALF_OUT_END -> MixTransitionPresets.smoothstep(0.30f, 0.95f, p)
        EffectTransitionMode.NONE -> 0f
    }
}

/** The repeats' spacing in beats; zero means the reverb's fixed short tail. */
private fun previewEffectDelayBeats(mode: EffectTransitionMode): Float = when (mode) {
    EffectTransitionMode.DELAY_THREE_QUARTER_CUT_END -> 0.75f
    EffectTransitionMode.DELAY_HALF_CUT_END,
    EffectTransitionMode.ECHO_HALF_CUT_END,
    EffectTransitionMode.ECHO_HALF_OUT_END -> 0.5f
    else -> 0f
}

/** The tail's regrowth: reverb trails stay modest, echo rings longer. */
private fun previewEffectFeedback(mode: EffectTransitionMode): Float = when (mode) {
    EffectTransitionMode.REVERB_OUT_CENTER,
    EffectTransitionMode.REVERB_CUT_END,
    EffectTransitionMode.REVERB_OUT_END -> 0.30f
    EffectTransitionMode.DELAY_HALF_CUT_END,
    EffectTransitionMode.DELAY_THREE_QUARTER_CUT_END -> 0.38f
    EffectTransitionMode.ECHO_HALF_CUT_END,
    EffectTransitionMode.ECHO_HALF_OUT_END -> 0.55f
    EffectTransitionMode.NONE -> 0f
}

/** The loop region's length in beats: the orange control's choice. */
private fun previewLoopBeats(mode: LoopTransitionMode): Int = when (mode) {
    LoopTransitionMode.NONE -> 0
    LoopTransitionMode.BEAT_1 -> 1
    LoopTransitionMode.BEAT_2 -> 2
    LoopTransitionMode.BEAT_4 -> 4
    LoopTransitionMode.BEAT_8 -> 8
    LoopTransitionMode.BEAT_16 -> 16
}
