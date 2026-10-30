package com.alananasss.kittytune.media

import androidx.compose.animation.core.FastOutSlowInEasing
import com.alananasss.kittytune.audio.AudioEngine
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.StreamResolver
import com.alananasss.kittytune.data.SessionManager
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.player.AudioEffectsState
import com.alananasss.kittytune.ui.player.EqualizerState
import com.alananasss.kittytune.utils.SignedUrl
import com.alananasss.kittytune.audio.automix.AutomixManager
import com.alananasss.kittytune.audio.automix.AutomixPlan
import com.alananasss.kittytune.data.local.PlayerPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Where the two gain ramps live, as fractions of the fade.
 *
 * Equal power needs **both** ramps over the *same* span: `out = cos(θ)` and `in = sin(θ)` with one
 * `θ`, and their squares only sum to a constant if both are the same function of progress. These two
 * used to span `[0, 0.6]` and `[0.4, 1]` — overlapping, but not identically — so the sum fell to
 * about 0.13 halfway through and the mix sagbed roughly 9 dB at exactly the moment it existed to be
 * seamless. The overlap was real; the arithmetic around it was not.
 *
 * A 40% span keeps the shape those numbers were reaching for: the outgoing track holds full for the
 * first third, the two genuinely blend across the middle, and the incoming is alone for the last
 * third. The point of a crossfade is that the sum does not move, and only a shared span achieves it.
 */
/** The ramp back from a mix's tempo and key: 60 steps of 40 ms, 2.4 s in all. */
private const val STRETCH_RAMP_STEPS = 60
private const val STRETCH_RAMP_STEP_MS = 40L

internal const val CROSSFADE_SPAN_START = 0.3f
internal const val CROSSFADE_SPAN_END = 0.7f

/**
 * The crossfade's two gain ramps, at file level so the mix glow can be lit by the same numbers the
 * ears hear.
 *
 * A second copy of this in the UI would be a second opinion about when a track is audible, and the
 * two would slowly stop agreeing — which is the one thing a glow like that must not do. The span
 * constants above are shared for the same reason: when the engine's overlap was corrected, the glow
 * had to be corrected with it, in the same commit, or it would have been lighting a fade that no
 * longer existed.
 */
internal fun equalPowerIn(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return sin(t * (Math.PI / 2.0).toFloat())
}

internal fun equalPowerOut(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return cos(t * (Math.PI / 2.0).toFloat())
}

/** Metadata attached to a media item (title/artist/artwork for the notification & UI). */
class MediaMetadata private constructor(
    val title: String?,
    val artist: String?,
    val artworkUri: String?,
    val artworkData: ByteArray?,
) {
    class Builder {
        private var title: String? = null
        private var artist: String? = null
        private var artworkUri: String? = null
        private var artworkData: ByteArray? = null
        fun setTitle(v: CharSequence?) = apply { title = v?.toString() }
        fun setArtist(v: CharSequence?) = apply { artist = v?.toString() }
        fun setArtworkUri(v: Any?) = apply { artworkUri = v?.toString() }
        fun setArtworkData(data: ByteArray?, pictureType: Int) = apply { artworkData = data }
        fun build() = MediaMetadata(title, artist, artworkUri, artworkData)
    }

    companion object {
        const val PICTURE_TYPE_FRONT_COVER = 3
    }
}

class RequestMetadata(val mediaUri: String?)

class MediaItem private constructor(
    val mediaId: String,
    val uri: String?,
    val mediaMetadata: MediaMetadata,
    val requestMetadata: RequestMetadata,
    val track: Track?,
    val mimeType: String? = null,
) {
    class Builder {
        private var mediaId: String = ""
        private var uri: String? = null
        private var metadata: MediaMetadata = MediaMetadata.Builder().build()
        private var track: Track? = null
        private var mimeType: String? = null
        fun setUri(v: Any?) = apply { uri = v?.toString() }
        fun setMediaId(v: String) = apply { mediaId = v }
        fun setMediaMetadata(v: MediaMetadata) = apply { metadata = v }
        fun setTrack(v: Track?) = apply { track = v }
        fun setMimeType(v: String?) = apply { mimeType = v }
        fun build() = MediaItem(mediaId, uri, metadata, RequestMetadata(uri), track, mimeType)
    }
}

class Player {

    interface Listener {
        fun onIsPlayingChanged(isPlaying: Boolean) {}
        fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {}
        fun onPlaybackStateChanged(playbackState: Int) {}
        fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {}
        fun onPositionDiscontinuity(oldPosition: PositionInfo, newPosition: PositionInfo, reason: Int) {}
        fun onPlayerError(error: Throwable) {}
    }

    class PositionInfo(val positionMs: Long)

    companion object {
        const val STATE_IDLE = 1
        const val STATE_BUFFERING = 2
        const val STATE_READY = 3
        const val STATE_ENDED = 4

        const val REPEAT_MODE_OFF = 0
        const val REPEAT_MODE_ONE = 1
        const val REPEAT_MODE_ALL = 2

        const val MEDIA_ITEM_TRANSITION_REASON_REPEAT = 0
        const val MEDIA_ITEM_TRANSITION_REASON_AUTO = 1
        const val MEDIA_ITEM_TRANSITION_REASON_SEEK = 2
        const val MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED = 3

        const val DISCONTINUITY_REASON_SEEK = 1
        const val DISCONTINUITY_REASON_SEEK_ADJUSTMENT = 2

        const val TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED = 0

        /** How often the prebuffer TTL is checked. */
        private const val PREBUFFER_TTL_CHECK_MS = 10_000L
        /** Prebuffered engines older than this are released if not consumed by a crossfade. */
        private const val PREBUFFER_TTL_MS = 60_000L
    }

    private val listeners = mutableListOf<Listener>()
    private val items = mutableListOf<MediaItem>()
    private var currentIndex = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var resolveJob: Job? = null
    
    var onCompletion: (() -> Unit)? = null
    var onError: ((Throwable) -> Unit)? = null

    @Volatile
    var activeEngine = AudioEngine()

    @Volatile
    private var fadingEngine: AudioEngine? = null

    /**
     * Identifies the transition that currently owns [activeEngine] and [fadingEngine].
     *
     * A crossfade prepares its incoming engine in a coroutine, and a listener can skip again
     * while that is still running. Cancelling the old job is not enough: cancellation is only
     * observed at a suspension point, so the superseded job could still reach its `prepare()`
     * or `play()` afterwards and leave a third engine decoding alongside the new track. Every
     * transition bumps this id, and a job may only touch the engines while its id is current.
     */
    @Volatile
    private var transitionId = 0L

    @Volatile var isCrossfadingOut = false
    var lastEffectsState: AudioEffectsState? = null
        private set
    var lastEqualizerState: EqualizerState? = null
        private set

    fun reapplyEffects() {
        lastEffectsState?.let { applyEffects(it) }
    }

    fun reapplyEqualizer() {
        lastEqualizerState?.let { applyEqualizer(it) }
    }

    data class PrebufferedTransition(
        val engine: AudioEngine,
        val item: MediaItem,
        val trackId: Long,
        val plan: AutomixPlan?,
        val url: String,
        val headers: Map<String, String>,
        /** Wall-clock ms when the prebuffer was stored, for TTL eviction. */
        val createdAtMs: Long = System.currentTimeMillis(),
    )

    @Volatile
    private var prebufferedTransition: PrebufferedTransition? = null
    /** True while prebufferTransition's coroutine is preparing an engine but hasn't stored it yet. */
    @Volatile
    private var prebufferInFlight: Boolean = false
    private var crossfadeJob: Job? = null

    /**
     * Prebuffered engines that aren't consumed by a crossfade within this TTL are released to avoid
     * holding ~1.5-2 MB of native DSP + FFmpeg state for a track the user never skipped to. The
     * prebuffer is re-created on demand if the user does skip later, so the cost of early release
     * is one re-resolve, not a lost feature (audit Phase 2 RAM gain).
     */
    private val prebufferTtlJob: Job = scope.launch {
        while (isActive) {
            delay(PREBUFFER_TTL_CHECK_MS)
            val pb = prebufferedTransition ?: continue
            if (System.currentTimeMillis() - pb.createdAtMs > PREBUFFER_TTL_MS) {
                releasePrebuffered()
            }
        }
    }

    fun isPrebuffered(trackId: Long): Boolean {
        val pb = prebufferedTransition ?: return false
        // TTL check: a prebuffer older than PREBUFFER_TTL_MS is stale (the user didn't skip to it)
        // — release it and report not-prebuffered so the caller re-resolves if needed.
        if (System.currentTimeMillis() - pb.createdAtMs > PREBUFFER_TTL_MS) {
            releasePrebuffered()
            return false
        }
        return pb.trackId == trackId && pb.engine.state != AudioEngine.State.IDLE
    }

    fun releasePrebuffered() {
        // Taking the reference and clearing the slot must be one step: a crossfade adopting the
        // prebuffer at the same instant would otherwise have its engine stopped from under it.
        val pb = synchronized(this) {
            prebufferedTransition.also { prebufferedTransition = null }
        } ?: return
        stopAndRelease(pb.engine)
    }

    fun prebufferTransition(item: MediaItem, nextTrack: Track, automixPlan: AutomixPlan? = null) {
        if (isCrossfadingOut) return
        // Hold the lock across the check-then-launch so two near-simultaneous calls for different
        // tracks can't both pass the isPrebuffered check and both create engines. The loser would
        // otherwise overwrite prebufferedTransition, orphaning the first engine (and its native
        // DSP handle, which GC can't free — see issue #33 RAM drift).
        synchronized(this) {
            if (isCrossfadingOut) return
            if (isPrebuffered(nextTrack.id)) return
            // Mark the slot as in-flight so a concurrent prebufferTransition for a different track
            // doesn't try to start a second engine alongside this one.
            if (prebufferInFlight) return
            prebufferInFlight = true
        }

        scope.launch {
            try {
                // If a previous prebuffer exists for a different track, release it before we even
                // resolve the new URL. Previously the old engine was just dereferenced and never
                // stop()+release()'d, leaking ~1.5-2 MB per orphan (native ebur128 + limiter + the
                // AiDetectionAudioProcessor's 800 KB preallocated buffer + FFmpeg contexts).
                releasePrebuffered()

                val rawUrl = item.uri?.takeIf {
                    if (it.startsWith("metrofuse-deezer://")) true
                    else if (SignedUrl.isNetworkUrl(it)) !SignedUrl.isExpired(it)
                    else if (it.startsWith("file:")) runCatching { java.io.File(java.net.URI(it)).exists() }.getOrDefault(false)
                    else java.io.File(it).exists()
                } ?: withContext(Dispatchers.IO) { StreamResolver.resolveStream(nextTrack) }

                if (rawUrl == null) return@launch

                val url = if (rawUrl.startsWith("metrofuse-deezer://")) {
                    com.alananasss.kittytune.audio.providers.deezer.DeezerAudioProxy.getPlayableUrlFromDeezerUri(rawUrl)
                } else {
                    rawUrl
                }
                val headers = buildHeaders(nextTrack)

                val engine = AudioEngine()
                lastEffectsState?.let { engine.applyEffects(it) }
                lastEqualizerState?.let { engine.applyEqualizer(it) }

                if (automixPlan != null && (automixPlan.tempoRatio != 1f || automixPlan.pitchRatio != 1f)) {
                    engine.setStretcherRatio(automixPlan.tempoRatio, automixPlan.pitchRatio)
                }

                val startPos = automixPlan?.incomingStartMs ?: 0L
                engine.setVolume(0f)
                engine.setMediaItem(url, headers, startPos, nextTrack.id)
                engine.prepare()

                // Re-check under the lock: if crossfadeToMediaItem adopted or released a prebuffer
                // while we were preparing, our engine is now stale — release it and bail.
                synchronized(this) {
                    prebufferInFlight = false
                    if (isCrossfadingOut || prebufferedTransition != null) {
                        runCatching { engine.stop(); engine.release() }
                        return@launch
                    }
                    prebufferedTransition = PrebufferedTransition(engine, item, nextTrack.id, automixPlan, url, headers)
                }
            } catch (_: Exception) {
                synchronized(this) { prebufferInFlight = false }
                // Ignore prebuffering error, fallback to normal load on transition
            }
        }
    }

    var playWhenReady: Boolean = false
        set(value) {
            val changed = field != value
            field = value
            // The outgoing track of a crossfade too: left alone it went on fading out after a pause, which
            // sounded like the pause had not worked, and the next press — meant as a second pause — resumed.
            if (value) {
                activeEngine.play()
                fadingEngine?.play()
            } else {
                activeEngine.pause()
                fadingEngine?.pause()
            }
            if (changed) { listeners.forEach { it.onPlayWhenReadyChanged(value, 1) } }
        }

    var repeatMode: Int = REPEAT_MODE_OFF

    val isPlaying: Boolean get() = activeEngine.isPlaying
    val currentPosition: Long get() = activeEngine.positionMs
    val duration: Long get() = activeEngine.durationMs
    val isLoading: Boolean get() = activeEngine.state == AudioEngine.State.BUFFERING

    /** The slider's position, 0..1. Engines are given [volumeAmplitude], never this directly. */
    var volume: Float = 1f
        set(value) {
            field = value
            activeEngine.setVolume(volumeAmplitude)
        }

    private val volumeAmplitude: Float
        get() = com.alananasss.kittytune.audio.VolumeCurve.sliderToAmplitude(volume)

    /**
     * The current track's own trim, in dB. Re-applied whenever playback moves to an engine, since
     * crossfade swaps between two of them and only one carries the trim at a time (issue #33).
     */
    var trackGainDb: Float = 0f
        set(value) {
            field = value
            activeEngine.setTrackGainDb(value)
        }

    val mediaItemCount: Int get() = items.size
    val currentMediaItem: MediaItem? get() = items.getOrNull(currentIndex)
    val currentMediaItemIndex: Int get() = currentIndex

    init {
        bindEngine(activeEngine)
    }
    
    private fun bindEngine(eng: AudioEngine) {
        eng.onPlayingChanged = { playing -> listeners.forEach { it.onIsPlayingChanged(playing) } }
        eng.onStateChanged = { st ->
            val mapped = when (st) {
                AudioEngine.State.BUFFERING -> STATE_BUFFERING
                AudioEngine.State.READY -> STATE_READY
                AudioEngine.State.ENDED -> STATE_ENDED
                AudioEngine.State.IDLE -> STATE_IDLE
            }
            listeners.forEach { it.onPlaybackStateChanged(mapped) }
        }
        eng.onError = { err -> 
            onError?.invoke(err)
            listeners.forEach { it.onPlayerError(err) } 
        }
        eng.onCompletion = {
            val trackId = currentMediaItem?.track?.id ?: com.alananasss.kittytune.data.MusicManager.currentTrack?.id
            trackId?.let { id ->
                val lufs = eng.getIntegratedLoudness()
                val tp = eng.getMaxTruePeakDb()
                if (lufs > -60f && lufs < 0f) {
                    com.alananasss.kittytune.data.TrackLoudnessRepository.saveLoudness(id, lufs, tp)
                }
            }
            onCompletion?.invoke()
        }
        eng.onReResolveUrl = { failedUrl ->
            val track = currentMediaItem?.track ?: com.alananasss.kittytune.data.MusicManager.currentTrack
            track?.let { t ->
                // Only throw the cache away when it is still holding the URL that just failed.
                // Otherwise the queue's prefetch has already refreshed it and can answer without
                // a round-trip — which is the difference between a seek resuming now and a seek
                // waiting on two SoundCloud requests.
                if (StreamResolver.cachedStreamUrl(t.id) == failedUrl) {
                    StreamResolver.evictStream(t.id)
                }
                withContext(Dispatchers.IO) {
                    StreamResolver.resolveStream(t)
                }
            }
        }
    }

    private fun unbindEngine(eng: AudioEngine) {
        eng.onPlayingChanged = null
        eng.onStateChanged = null
        eng.onError = null
        eng.onCompletion = null
        eng.onReResolveUrl = null
    }

    /** Starts a transition and returns its id. See [transitionId]. */
    private fun beginTransition(): Long = synchronized(this) { ++transitionId }

    /** Invalidates the transition in flight without starting another one. */
    private fun invalidateTransition(): Long = synchronized(this) { ++transitionId }

    private fun isCurrentTransition(id: Long): Boolean = transitionId == id

    /** Releases an engine that no longer belongs to the active/fading pair. */
    private fun releaseIfOrphaned(engine: AudioEngine?) {
        if (engine == null) return
        val orphan = synchronized(this) {
            engine !== activeEngine && engine !== fadingEngine
        }
        if (orphan) stopAndRelease(engine)
    }

    private fun stopAndRelease(engine: AudioEngine?) {
        if (engine == null) return
        try {
            engine.stop()
            engine.release()
        } catch (_: Exception) {
        }
    }

    fun addListener(l: Listener) { if (!listeners.contains(l)) listeners.add(l) }
    fun removeListener(l: Listener) { listeners.remove(l) }

    fun setMediaItemUrl(url: String, headers: Map<String, String>, startPositionMs: Long = 0L) {
        activeEngine.setMediaItem(url, headers, startPositionMs, currentMediaItem?.track?.id)
    }

    fun setMediaItem(item: MediaItem, startPositionMs: Long = 0L) {
        // A direct load supersedes any crossfade. Invalidating first waits for a transition that
        // is currently inside its engine-setup section, so it cannot start playing after us.
        invalidateTransition()
        crossfadeJob?.cancel()
        releasePrebuffered()
        val previousFade = synchronized(this) {
            isCrossfadingOut = false
            AutomixManager.setIsAutomixing(false)
            AutomixManager.clearPlan()
            AutomixManager.setMixProgress(0f)
            fadingEngine.also { fadingEngine = null }
        }
        stopAndRelease(previousFade)

        synchronized(this) {
            items.clear()
            items.add(item)
            currentIndex = 0
        }

        val uriStr = item.uri
        val isDirectUrl = uriStr != null && (
            uriStr.startsWith("http://") ||
            uriStr.startsWith("https://") ||
            uriStr.startsWith("file:") ||
            uriStr.startsWith("metrofuse-deezer://") ||
            java.io.File(uriStr).exists()
        ) && !SignedUrl.isExpired(uriStr)

        if (isDirectUrl && uriStr != null) {
            val url = if (uriStr.startsWith("metrofuse-deezer://")) {
                com.alananasss.kittytune.audio.providers.deezer.DeezerAudioProxy.getPlayableUrlFromDeezerUri(uriStr)
            } else {
                uriStr
            }
            val headers = buildHeaders(item.track)
            val track = item.track
            if (track != null) {
                val cached = kotlinx.coroutines.runBlocking { com.alananasss.kittytune.data.TrackLoudnessRepository.getLoudness(track.id) }
                if (cached != null) {
                    activeEngine.setTrackLoudness(cached.integratedLufs, cached.truePeakDb)
                } else {
                    activeEngine.clearTrackLoudness()
                }
            } else {
                activeEngine.clearTrackLoudness()
            }
            activeEngine.setVolume(volumeAmplitude)
            activeEngine.setTrackGainDb(trackGainDb)
            activeEngine.setMediaItem(url, headers, startPositionMs, track?.id)
        } else {
            activeEngine.stop()
        }

        loadCurrent(startPositionMs, false, 0L)
        listeners.forEach { it.onMediaItemTransition(currentMediaItem, MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) }
    }

    fun crossfadeToMediaItem(
        item: MediaItem,
        startPositionMs: Long,
        crossfadeDurationMs: Long,
        automixPlan: AutomixPlan? = null
    ) {
        val transition: Long
        lateinit var oldEngine: AudioEngine
        lateinit var newEngine: AudioEngine
        var effectivePlan: AutomixPlan? = null
        var isAdopted = false
        var pre: PrebufferedTransition? = null
        val enginesToRelease = mutableListOf<AudioEngine>()

        // Swap the state under the same monitor the transition setup uses. A superseded job can
        // then either finish its setup before us (and we replace it) or see that its id is stale
        // and never touch an engine at all; it can no longer start playback behind our back.
        synchronized(this) {
            transition = ++transitionId
            crossfadeJob?.cancel()
            resolveJob?.cancel()

            items.clear()
            items.add(item)
            currentIndex = 0

            // The previous fade is over the moment another transition begins.
            fadingEngine?.let { enginesToRelease.add(it) }
            fadingEngine = null

            oldEngine = activeEngine
            unbindEngine(oldEngine)

            val targetTrackId = item.track?.id
            pre = prebufferedTransition?.takeIf { it.trackId == targetTrackId }
            isAdopted = pre != null
            if (!isAdopted) {
                // Skip during crossfade: the prebuffered engine was warmed up for a
                // different track. Dropping the reference alone keeps its decoder, line
                // and DSP state alive until GC (which never frees the native DSP handle) —
                // stop+release it explicitly instead (issue #33 RAM drift).
                prebufferedTransition?.let { enginesToRelease.add(it.engine) }
            }
            prebufferedTransition = null

            newEngine = pre?.engine ?: AudioEngine()
            effectivePlan = pre?.plan ?: automixPlan

            bindEngine(newEngine)
            activeEngine = newEngine
            fadingEngine = oldEngine
            isCrossfadingOut = true

            // A superseded fade leaves these flags behind if only the old job is allowed to clear
            // them. The transition that replaces it owns the state now, so reset the mix here.
            AutomixManager.setMixProgress(0f)
            AutomixManager.setMixBeatsLeft(null)
            AutomixManager.setIsAutomixing(effectivePlan != null)
            if (effectivePlan == null) AutomixManager.clearPlan()

            if (!isAdopted) {
                lastEffectsState?.let { newEngine.applyEffects(it) }
                lastEqualizerState?.let { newEngine.applyEqualizer(it) }
                if (effectivePlan != null && (effectivePlan.tempoRatio != 1f || effectivePlan.pitchRatio != 1f)) {
                    newEngine.setStretcherRatio(effectivePlan.tempoRatio, effectivePlan.pitchRatio)
                }
            }
        }

        enginesToRelease.forEach(::stopAndRelease)

        listeners.forEach { it.onMediaItemTransition(currentMediaItem, MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) }

        val actualStartPos = effectivePlan?.incomingStartMs ?: startPositionMs

        startCrossfade(
            transitionId = transition,
            newEngine = newEngine,
            oldEngine = oldEngine,
            item = item,
            startPositionMs = actualStartPos,
            crossfadeDurationMs = crossfadeDurationMs,
            effectivePlan = effectivePlan,
            isAdopted = isAdopted,
            preUrl = pre?.url,
            preHeaders = pre?.headers
        )
    }

    private fun startCrossfade(
        transitionId: Long,
        newEngine: AudioEngine,
        oldEngine: AudioEngine,
        item: MediaItem,
        startPositionMs: Long,
        crossfadeDurationMs: Long,
        effectivePlan: AutomixPlan?,
        isAdopted: Boolean,
        preUrl: String?,
        preHeaders: Map<String, String>?
    ) {
        synchronized(this) {
            // Do not let a superseded transition replace the job handle of the one that
            // actually owns the engines: cancelling it later must cancel the live fade.
            if (!isCurrentTransition(transitionId)) return
            crossfadeJob = scope.launch {
                try {
                    if (!isAdopted) {
                        val rawUrl = item.uri?.takeIf {
                            if (it.startsWith("metrofuse-deezer://")) true
                            else if (SignedUrl.isNetworkUrl(it)) !SignedUrl.isExpired(it)
                            else if (it.startsWith("file:")) runCatching { java.io.File(java.net.URI(it)).exists() }.getOrDefault(false)
                            else java.io.File(it).exists()
                        } ?: item.track?.let { withContext(Dispatchers.IO) { StreamResolver.resolveStream(it) } }

                        // The listener may have skipped while we were resolving. Do not stop or
                        // start anything for a transition the player has already replaced.
                        if (!isActive || !isCurrentTransition(transitionId)) return@launch

                        if (rawUrl == null) {
                            synchronized(this@Player) {
                                if (!isCurrentTransition(transitionId)) return@launch
                                if (fadingEngine === oldEngine) fadingEngine = null
                                oldEngine.stop()
                                oldEngine.release()
                                isCrossfadingOut = false
                            }
                            listeners.forEach { it.onPlaybackStateChanged(STATE_ENDED) }
                            return@launch
                        }
                        val url = if (rawUrl.startsWith("metrofuse-deezer://")) {
                            com.alananasss.kittytune.audio.providers.deezer.DeezerAudioProxy.getPlayableUrlFromDeezerUri(rawUrl)
                        } else {
                            rawUrl
                        }
                        val headers = buildHeaders(item.track)
                        val track = item.track

                        // Resolve the loudness before taking the transition lock: this is a database
                        // read, and holding the lock across it would stall the next skip.
                        val cachedLoudness = track?.let {
                            com.alananasss.kittytune.data.TrackLoudnessRepository.getLoudness(it.id)
                        }

                        synchronized(this@Player) {
                            if (!isActive || !isCurrentTransition(transitionId)) return@launch
                            // Defensive: only the transition that still owns this pair may use it.
                            if (activeEngine !== newEngine || fadingEngine !== oldEngine) return@launch

                            if (track != null) {
                                if (cachedLoudness != null) {
                                    newEngine.setTrackLoudness(cachedLoudness.integratedLufs, cachedLoudness.truePeakDb)
                                } else {
                                    newEngine.clearTrackLoudness()
                                    com.alananasss.kittytune.data.TrackLoudnessRepository.scanTrackAsync(track, url, headers) { scanned ->
                                        if (isCurrentTransition(transitionId) && currentMediaItem?.track?.id == track.id) {
                                            newEngine.setTrackLoudness(scanned.integratedLufs, scanned.truePeakDb)
                                        }
                                    }
                                }
                            } else {
                                newEngine.clearTrackLoudness()
                            }

                            newEngine.setMediaItem(url, headers, startPositionMs, track?.id)
                            newEngine.prepare()
                        }
                    }

                    val targetVolume = volumeAmplitude
                    synchronized(this@Player) {
                        if (!isActive || !isCurrentTransition(transitionId)) return@launch
                        if (activeEngine !== newEngine || fadingEngine !== oldEngine) return@launch
                        newEngine.setVolume(0f)
                        newEngine.setTrackGainDb(trackGainDb)

                        if (playWhenReady) newEngine.play()
                    }

                    if (!isAdopted) {
                        var waitCount = 0
                        while (newEngine.state == AudioEngine.State.BUFFERING &&
                            waitCount < 50 && isActive && isCurrentTransition(transitionId)
                        ) {
                            delay(100)
                            waitCount++
                        }
                    }

                    if (!isActive || !isCurrentTransition(transitionId) || fadingEngine !== oldEngine) return@launch

                    val transitionDuration = effectivePlan?.overlapMs ?: crossfadeDurationMs
                    var remainingMs = oldEngine.durationMs - oldEngine.positionMs
                    if (remainingMs < 0) remainingMs = 0

                    val actualCrossfadeMs = if (effectivePlan != null) {
                        if (oldEngine.isPlaying) {
                            if (remainingMs > 0 && remainingMs < transitionDuration) remainingMs.coerceAtLeast(1000L)
                            else transitionDuration
                        } else 0L
                    } else if (oldEngine.isPlaying && remainingMs > 0 && remainingMs < transitionDuration) {
                        remainingMs
                    } else if (!oldEngine.isPlaying || remainingMs == 0L) {
                        0L
                    } else {
                        transitionDuration
                    }

                    val bassDuckingEnabled = PlayerPreferences().getAutomixBassDuckingEnabled()

                    if (actualCrossfadeMs <= 0L) {
                        synchronized(this@Player) {
                            if (!isCurrentTransition(transitionId)) return@launch
                            newEngine.setVolume(targetVolume)
                            if (fadingEngine === oldEngine) fadingEngine = null
                            oldEngine.stop()
                            oldEngine.release()
                        }
                        AutomixManager.setMixProgress(0f)
                    } else {
                        val steps = (actualCrossfadeMs / 15L).toInt().coerceIn(50, 800)
                        val delayMs = (actualCrossfadeMs / steps).coerceAtLeast(5L)

                        for (i in 0..steps) {
                            if (!isActive || !isCurrentTransition(transitionId)) break
                            if (fadingEngine !== oldEngine) break

                            var bufferWait = 0
                            while (!newEngine.isPlaying && isActive && isCurrentTransition(transitionId) &&
                                newEngine.state == AudioEngine.State.BUFFERING && bufferWait < 50
                            ) {
                                delay(100)
                                bufferWait++
                            }
                            // Paused mid-fade: hold the fade where it is, so it resumes rather than having finished
                            // in silence.
                            var pauseWait = 0
                            while (!playWhenReady && isActive && isCurrentTransition(transitionId) && pauseWait < 600) {
                                delay(50)
                                pauseWait++
                            }

                            if (!isActive || !isCurrentTransition(transitionId) || fadingEngine !== oldEngine) break

                            if (oldEngine.state == AudioEngine.State.ENDED || oldEngine.state == AudioEngine.State.IDLE) {
                                newEngine.setVolume(targetVolume)
                                break
                            }

                            val progress = i.toFloat() / steps
                            val fadeOut = equalPowerOut(CROSSFADE_SPAN_START, CROSSFADE_SPAN_END, progress)
                            val fadeIn = equalPowerIn(CROSSFADE_SPAN_START, CROSSFADE_SPAN_END, progress)

                            // The artwork is lit by this, so the glow and the mix are the same event
                            // rather than two things that usually happen together.
                            AutomixManager.setMixProgress(progress)

                            // Re-check ownership right before touching the engines. A second
                            // crossfadeToMediaItem call may have run between the top-of-loop check
                            // and here (the delay(100) / delay(50) above suspend). If so, the second
                            // call has already released oldEngine (or claimed newEngine as its fading
                            // engine) — calling setVolume on a released engine is a no-op (defensive
                            // in AudioEngine), but racing setVolume on a newEngine that a second
                            // crossfade has already claimed as its fadingEngine is audible as a brief
                            // level jump (issue #76 audio glitch).
                            if (!isActive || !isCurrentTransition(transitionId) || fadingEngine !== oldEngine) break

                            newEngine.setVolume(targetVolume * fadeIn)
                            oldEngine.setVolume(targetVolume * fadeOut)

                            if (effectivePlan != null && bassDuckingEnabled) {
                                oldEngine.setDuckMix(equalPowerIn(0.45f, 1f, progress))
                                newEngine.setDuckMix(1f - equalPowerIn(0f, 0.55f, progress))
                            }

                            delay(delayMs)
                        }
                    }
                } finally {
                    val ownedNow = synchronized(this@Player) {
                        if (!isCurrentTransition(transitionId)) {
                            false
                        } else {
                            // The incoming engine is still ours: give it the full gain and retire the
                            // outgoing one. Doing this under the transition lock keeps a skip that
                            // lands at this exact moment from seeing half-cleared state.
                            if (fadingEngine === oldEngine) {
                                newEngine.setVolume(volumeAmplitude)
                                fadingEngine = null
                            }
                            oldEngine.resetDuckMix()
                            newEngine.resetDuckMix()
                            try {
                                oldEngine.setVolume(0f)
                                oldEngine.stop()
                                oldEngine.release()
                            } catch (_: Exception) {
                            }

                            if (effectivePlan != null) {
                                AutomixManager.setIsAutomixing(false)
                                AutomixManager.clearPlan()

                                if (effectivePlan.tempoRatio != 1f || effectivePlan.pitchRatio != 1f) {
                                    // Back to the track's own tempo and key, in steps too small to hear. Ten jumps of a
                                    // fifth of a second each made the start of a mixed-in track warble, as if it were
                                    // playing at a lower quality for its first seconds (issue #66).
                                    scope.launch {
                                        val startTempo = effectivePlan.tempoRatio
                                        val startPitch = effectivePlan.pitchRatio
                                        for (step in 1..STRETCH_RAMP_STEPS) {
                                            delay(STRETCH_RAMP_STEP_MS)
                                            if (!isActive || !isCurrentTransition(transitionId)) break
                                            val frac = FastOutSlowInEasing.transform(step.toFloat() / STRETCH_RAMP_STEPS)
                                            val curTempo = startTempo + frac * (1f - startTempo)
                                            val curPitch = startPitch + frac * (1f - startPitch)
                                            newEngine.setStretcherRatio(curTempo, curPitch)
                                        }
                                        if (isCurrentTransition(transitionId)) newEngine.setStretcherRatio(1f, 1f)
                                    }
                                }
                            }
                            // Every exit from the loop above lands here, including a fade cut short or a track
                            // that ended underneath it. Leaving the artwork lit for a mix that is over would be
                            // worse than never lighting it.
                            isCrossfadingOut = false
                            AutomixManager.setMixProgress(0f)
                            true
                        }
                    }

                    if (!ownedNow) {
                        // A newer transition owns the active/fading pair now, or this transition was cancelled.
                        // Since this transition's coroutine is exiting, oldEngine has no coroutine fading it out
                        // anymore. If it is still fadingEngine or not active, stop and release it so it cannot
                        // decode in the background at full volume (issue #95).
                        val toRelease = mutableListOf<AudioEngine>()
                        synchronized(this@Player) {
                            if (fadingEngine === oldEngine) {
                                fadingEngine = null
                                isCrossfadingOut = false
                            }
                            if (activeEngine !== oldEngine) toRelease.add(oldEngine)
                            if (activeEngine !== newEngine) toRelease.add(newEngine)
                        }
                        toRelease.forEach(::stopAndRelease)
                    }
                }
            }
        }
    }

    fun addMediaItem(item: MediaItem) { items.add(item) }
    fun addMediaItems(newItems: List<MediaItem>) { items.addAll(newItems) }
    fun removeMediaItem(index: Int) { if (index in items.indices) items.removeAt(index) }

    fun prepare() { activeEngine.prepare() }

        fun play() {
        playWhenReady = true
        activeEngine.play()
        fadingEngine?.play()
    }
    fun pause() {
        playWhenReady = false
        activeEngine.pause()
        fadingEngine?.pause()
    }

    /**
     * Aborts any crossfade in flight, stopping and releasing the outgoing deck immediately,
     * resetting mix progress, and restoring full volume to [activeEngine].
     */
    fun cancelCrossfade() {
        val enginesToRelease = mutableListOf<AudioEngine>()
        synchronized(this) {
            invalidateTransition()
            crossfadeJob?.cancel()
            crossfadeJob = null
            resolveJob?.cancel()
            resolveJob = null
            releasePrebuffered()
            isCrossfadingOut = false
            AutomixManager.setMixProgress(0f)
            AutomixManager.setIsAutomixing(false)
            AutomixManager.clearPlan()
            activeEngine.setVolume(volumeAmplitude)
            fadingEngine?.let { enginesToRelease.add(it) }
            fadingEngine = null
        }
        enginesToRelease.forEach(::stopAndRelease)
    }

    fun stop() {
        // Invalidate first: a transition inside its setup section is allowed to finish before
        // this returns, and its stale finally cannot mistake itself for the current owner.
        invalidateTransition()
        crossfadeJob?.cancel()
        resolveJob?.cancel()
        releasePrebuffered()
        val previousFade = synchronized(this) {
            isCrossfadingOut = false
            fadingEngine.also { fadingEngine = null }
        }
        stopAndRelease(previousFade)
        activeEngine.stop()
    }

    fun release() {
        invalidateTransition()
        crossfadeJob?.cancel()
        resolveJob?.cancel()
        releasePrebuffered()
        val previousFade = synchronized(this) {
            isCrossfadingOut = false
            fadingEngine.also { fadingEngine = null }
        }
        stopAndRelease(previousFade)
        activeEngine.release()
    }

    fun seekTo(positionMs: Long) {
        activeEngine.seekTo(positionMs)
        val pi = PositionInfo(positionMs)
        listeners.forEach { it.onPositionDiscontinuity(pi, pi, DISCONTINUITY_REASON_SEEK) }
    }

    fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (mediaItemIndex in items.indices) {
            currentIndex = mediaItemIndex
            loadCurrent(positionMs, false, 0L)
            listeners.forEach { it.onMediaItemTransition(currentMediaItem, MEDIA_ITEM_TRANSITION_REASON_AUTO) }
        }
    }

    private fun loadCurrent(startPositionMs: Long, isCrossfade: Boolean, crossfadeDurationMs: Long) {
        val item = currentMediaItem ?: return
        val transition = beginTransition()
        crossfadeJob?.cancel()
        resolveJob?.cancel()

        // A direct load to an index is not a mix: retire any outgoing fade immediately rather
        // than leaving both tracks audible until the new URL has resolved.
        val previousFade = synchronized(this) {
            isCrossfadingOut = false
            fadingEngine.also { fadingEngine = null }
        }
        stopAndRelease(previousFade)

        resolveJob = scope.launch {
            val rawUrl = item.uri?.takeIf {
                if (it.startsWith("metrofuse-deezer://")) true
                else if (SignedUrl.isNetworkUrl(it)) !SignedUrl.isExpired(it)
                else if (it.startsWith("file:")) runCatching { java.io.File(java.net.URI(it)).exists() }.getOrDefault(false)
                else java.io.File(it).exists()
            }
                ?: item.track?.let { withContext(Dispatchers.IO) { StreamResolver.resolveStream(it) } }
            if (!isActive || !isCurrentTransition(transition)) return@launch
            if (rawUrl == null) {
                synchronized(this@Player) {
                    if (!isCurrentTransition(transition)) return@launch
                    activeEngine.stop()
                    fadingEngine?.stop()
                    fadingEngine?.release()
                    fadingEngine = null
                }
                listeners.forEach { it.onPlaybackStateChanged(STATE_ENDED) }
                return@launch
            }
            val url = if (rawUrl.startsWith("metrofuse-deezer://")) {
                com.alananasss.kittytune.audio.providers.deezer.DeezerAudioProxy.getPlayableUrlFromDeezerUri(rawUrl)
            } else {
                rawUrl
            }
            val headers = buildHeaders(item.track)

            val track = item.track
            // Leave the database read outside the transition lock so a quick skip never waits
            // behind it; the ownership check happens again below, before anything is touched.
            val cached = track?.let {
                com.alananasss.kittytune.data.TrackLoudnessRepository.getLoudness(it.id)
            }
            if (!isActive || !isCurrentTransition(transition)) return@launch

            synchronized(this@Player) {
                if (!isCurrentTransition(transition)) return@launch
                val engine = activeEngine

                if (track != null) {
                    if (cached != null) {
                        engine.setTrackLoudness(cached.integratedLufs, cached.truePeakDb)
                    } else {
                        engine.clearTrackLoudness()
                        com.alananasss.kittytune.data.TrackLoudnessRepository.scanTrackAsync(track, url, headers) { scanned ->
                            if (isCurrentTransition(transition) && currentMediaItem?.track?.id == track.id) {
                                engine.setTrackLoudness(scanned.integratedLufs, scanned.truePeakDb)
                            }
                        }
                    }
                } else {
                    engine.clearTrackLoudness()
                }

                fadingEngine?.stop()
                fadingEngine?.release()
                fadingEngine = null

                engine.setVolume(volumeAmplitude)
                engine.setTrackGainDb(trackGainDb)
                if (engine.currentUrl != url) {
                    engine.setMediaItem(url, headers, startPositionMs, currentMediaItem?.track?.id)
                    engine.prepare()
                    if (playWhenReady) engine.play()
                } else {
                    if (engine.state == AudioEngine.State.IDLE) {
                        engine.prepare()
                        if (playWhenReady) engine.play()
                    }
                }
            }
        }
    }

    /** The request headers a stream for [track] is opened with. */
    fun buildHeaders(track: Track?): Map<String, String> {
        val headers = mutableMapOf("User-Agent" to "SoundCloud/2025.12.10-release (Android 10; Android)")
        if (track?.source != "youtube") {
            headers["Origin"] = "https://soundcloud.com"
            headers["Referer"] = "https://soundcloud.com/"
        }
        return headers
    }
    
    fun applyEffects(state: AudioEffectsState) {
        lastEffectsState = state
        activeEngine.applyEffects(state)
        fadingEngine?.applyEffects(state)
    }

    fun applyEqualizer(state: EqualizerState) {
        lastEqualizerState = state
        activeEngine.applyEqualizer(state)
        fadingEngine?.applyEqualizer(state)
    }

    fun onKaraokeStemReady(trackId: Long) {
        activeEngine.onKaraokeStemReady(trackId)
        fadingEngine?.onKaraokeStemReady(trackId)
    }
}

typealias PlayerCompat = Player

