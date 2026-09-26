package com.alananasss.kittytune.media

import com.alananasss.kittytune.audio.AudioEngine
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.StreamResolver
import com.alananasss.kittytune.data.SessionManager
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.player.AudioEffectsState
import com.alananasss.kittytune.utils.SignedUrl
import com.alananasss.kittytune.audio.automix.AutomixManager
import com.alananasss.kittytune.audio.automix.AutomixPlan
import com.alananasss.kittytune.data.local.PlayerPreferences
import kotlinx.coroutines.CoroutineScope
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
    }

    private val listeners = mutableListOf<Listener>()
    private val items = mutableListOf<MediaItem>()
    private var currentIndex = 0
    private val scope = CoroutineScope(Dispatchers.Default)
    private var resolveJob: Job? = null
    
    var onCompletion: (() -> Unit)? = null
    var onError: ((Throwable) -> Unit)? = null

    var activeEngine = AudioEngine()
    private var fadingEngine: AudioEngine? = null
    
    @Volatile var isCrossfadingOut = false
    private var lastEffectsState: AudioEffectsState? = null

    data class PrebufferedTransition(
        val engine: AudioEngine,
        val item: MediaItem,
        val trackId: Long,
        val plan: AutomixPlan?,
        val url: String,
        val headers: Map<String, String>,
    )

    @Volatile
    private var prebufferedTransition: PrebufferedTransition? = null
    private var crossfadeJob: Job? = null

    fun isPrebuffered(trackId: Long): Boolean {
        val pb = prebufferedTransition ?: return false
        return pb.trackId == trackId && pb.engine.state != AudioEngine.State.IDLE
    }

    fun releasePrebuffered() {
        val pb = prebufferedTransition ?: return
        prebufferedTransition = null
        try {
            pb.engine.stop()
            pb.engine.release()
        } catch (_: Exception) {}
    }

    fun prebufferTransition(item: MediaItem, nextTrack: Track, automixPlan: AutomixPlan? = null) {
        if (isCrossfadingOut) return
        if (isPrebuffered(nextTrack.id)) return

        scope.launch {
            try {
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

                if (automixPlan != null && (automixPlan.tempoRatio != 1f || automixPlan.pitchRatio != 1f)) {
                    engine.setStretcherRatio(automixPlan.tempoRatio, automixPlan.pitchRatio)
                }

                val startPos = automixPlan?.incomingStartMs ?: 0L
                engine.setVolume(0f)
                engine.setMediaItem(url, headers, startPos)
                engine.prepare()

                prebufferedTransition = PrebufferedTransition(engine, item, nextTrack.id, automixPlan, url, headers)
            } catch (_: Exception) {
                // Ignore prebuffering error, fallback to normal load on transition
            }
        }
    }

    var playWhenReady: Boolean = false
        set(value) {
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

    fun addListener(l: Listener) { if (!listeners.contains(l)) listeners.add(l) }
    fun removeListener(l: Listener) { listeners.remove(l) }

    fun setMediaItemUrl(url: String, headers: Map<String, String>, startPositionMs: Long = 0L) {
        activeEngine.setMediaItem(url, headers, startPositionMs)
    }

    fun setMediaItem(item: MediaItem, startPositionMs: Long = 0L) {
        releasePrebuffered()
        crossfadeJob?.cancel()
        items.clear()
        items.add(item)
        currentIndex = 0
        loadCurrent(startPositionMs, false, 0L)
        listeners.forEach { it.onMediaItemTransition(currentMediaItem, MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) }
    }

    fun crossfadeToMediaItem(
        item: MediaItem,
        startPositionMs: Long,
        crossfadeDurationMs: Long,
        automixPlan: AutomixPlan? = null
    ) {
        items.clear()
        items.add(item)
        currentIndex = 0
        
        crossfadeJob?.cancel()
        resolveJob?.cancel()
        
        val oldEngine = activeEngine
        unbindEngine(oldEngine)

        val targetTrackId = item.track?.id
        val isAdopted = prebufferedTransition != null && prebufferedTransition?.trackId == targetTrackId
        val pre = if (isAdopted) prebufferedTransition else null
        prebufferedTransition = null

        val newEngine = pre?.engine ?: AudioEngine()
        val effectivePlan = pre?.plan ?: automixPlan

        bindEngine(newEngine)
        activeEngine = newEngine
        fadingEngine = oldEngine
        isCrossfadingOut = true

        if (effectivePlan != null) {
            AutomixManager.setIsAutomixing(true)
        }

        if (!isAdopted) {
            lastEffectsState?.let { newEngine.applyEffects(it) }
            if (effectivePlan != null && (effectivePlan.tempoRatio != 1f || effectivePlan.pitchRatio != 1f)) {
                newEngine.setStretcherRatio(effectivePlan.tempoRatio, effectivePlan.pitchRatio)
            }
        }

        listeners.forEach { it.onMediaItemTransition(currentMediaItem, MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) }

        val actualStartPos = if (isAdopted) (effectivePlan?.incomingStartMs ?: startPositionMs) else (effectivePlan?.incomingStartMs ?: startPositionMs)

        startCrossfade(
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
        crossfadeJob = scope.launch {
            try {
                if (!isAdopted) {
                    val rawUrl = item.uri?.takeIf {
                        if (it.startsWith("metrofuse-deezer://")) true
                        else if (SignedUrl.isNetworkUrl(it)) !SignedUrl.isExpired(it)
                        else if (it.startsWith("file:")) runCatching { java.io.File(java.net.URI(it)).exists() }.getOrDefault(false)
                        else java.io.File(it).exists()
                    } ?: item.track?.let { withContext(Dispatchers.IO) { StreamResolver.resolveStream(it) } }

                    if (rawUrl == null) {
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
                    if (track != null) {
                        val cached = com.alananasss.kittytune.data.TrackLoudnessRepository.getLoudness(track.id)
                        if (cached != null) {
                            newEngine.setTrackLoudness(cached.integratedLufs, cached.truePeakDb)
                        } else {
                            newEngine.clearTrackLoudness()
                            com.alananasss.kittytune.data.TrackLoudnessRepository.scanTrackAsync(track, url, headers) { scanned ->
                                if (currentMediaItem?.track?.id == track.id) {
                                    newEngine.setTrackLoudness(scanned.integratedLufs, scanned.truePeakDb)
                                }
                            }
                        }
                    } else {
                        newEngine.clearTrackLoudness()
                    }

                    newEngine.setMediaItem(url, headers, startPositionMs)
                    newEngine.prepare()
                }

                val targetVolume = volumeAmplitude
                newEngine.setVolume(0f)
                newEngine.setTrackGainDb(trackGainDb)

                if (playWhenReady) newEngine.play()

                if (!isAdopted) {
                    var waitCount = 0
                    while (newEngine.state == AudioEngine.State.BUFFERING && waitCount < 50 && isActive) {
                        delay(100)
                        waitCount++
                    }
                }

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
                    newEngine.setVolume(targetVolume)
                    oldEngine.stop()
                    oldEngine.release()
                    AutomixManager.setMixProgress(0f)
                } else {
                    val steps = (actualCrossfadeMs / 15L).toInt().coerceIn(50, 800)
                    val delayMs = (actualCrossfadeMs / steps).coerceAtLeast(5L)

                    for (i in 0..steps) {
                        if (fadingEngine != oldEngine) break
                        if (!isActive) break

                        while (!newEngine.isPlaying && isActive && newEngine.state == AudioEngine.State.BUFFERING) {
                            delay(100)
                        }
                        // Paused mid-fade: hold the fade where it is, so it resumes rather than having finished
                        // in silence.
                        while (!playWhenReady && isActive) {
                            delay(50)
                        }

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
                try {
                    if (fadingEngine == oldEngine) {
                        newEngine.setVolume(volumeAmplitude)
                        oldEngine.setVolume(0f)
                        oldEngine.stop()
                        oldEngine.release()
                        fadingEngine = null
                    }
                } catch (_: Exception) {}

                oldEngine.resetDuckMix()
                newEngine.resetDuckMix()

                if (effectivePlan != null) {
                    AutomixManager.setIsAutomixing(false)
                    AutomixManager.clearPlan()

                    if (effectivePlan.tempoRatio != 1f || effectivePlan.pitchRatio != 1f) {
                        scope.launch {
                            val rampSteps = 10
                            val startTempo = effectivePlan.tempoRatio
                            val startPitch = effectivePlan.pitchRatio
                            for (step in 1..rampSteps) {
                                delay(200)
                                if (!isActive) break
                                val frac = step.toFloat() / rampSteps
                                val curTempo = startTempo + frac * (1f - startTempo)
                                val curPitch = startPitch + frac * (1f - startPitch)
                                newEngine.setStretcherRatio(curTempo, curPitch)
                            }
                            newEngine.setStretcherRatio(1f, 1f)
                        }
                    }
                }
                isCrossfadingOut = false
                // Every exit from the loop above lands here, including a fade cut short or a track
                // that ended underneath it. Leaving the artwork lit for a mix that is over would be
                // worse than never lighting it.
                AutomixManager.setMixProgress(0f)
            }
        }
    }

    fun addMediaItem(item: MediaItem) { items.add(item) }
    fun addMediaItems(newItems: List<MediaItem>) { items.addAll(newItems) }
    fun removeMediaItem(index: Int) { if (index in items.indices) items.removeAt(index) }

    fun prepare() { activeEngine.prepare() }

    fun play() { playWhenReady = true }
    fun pause() { playWhenReady = false }
    fun stop() {
        crossfadeJob?.cancel()
        resolveJob?.cancel()
        releasePrebuffered()
        fadingEngine?.stop()
        fadingEngine?.release()
        fadingEngine = null
        activeEngine.stop()
    }
    fun release() {
        crossfadeJob?.cancel()
        resolveJob?.cancel()
        releasePrebuffered()
        fadingEngine?.release()
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
        resolveJob?.cancel()
        resolveJob = scope.launch {
            val rawUrl = item.uri?.takeIf {
                if (it.startsWith("metrofuse-deezer://")) true
                else if (SignedUrl.isNetworkUrl(it)) !SignedUrl.isExpired(it)
                else if (it.startsWith("file:")) runCatching { java.io.File(java.net.URI(it)).exists() }.getOrDefault(false)
                else java.io.File(it).exists()
            }
                ?: item.track?.let { withContext(Dispatchers.IO) { StreamResolver.resolveStream(it) } }
            if (rawUrl == null) {
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
            if (track != null) {
                val cached = com.alananasss.kittytune.data.TrackLoudnessRepository.getLoudness(track.id)
                if (cached != null) {
                    activeEngine.setTrackLoudness(cached.integratedLufs, cached.truePeakDb)
                } else {
                    activeEngine.clearTrackLoudness()
                    com.alananasss.kittytune.data.TrackLoudnessRepository.scanTrackAsync(track, url, headers) { scanned ->
                        if (currentMediaItem?.track?.id == track.id) {
                            activeEngine.setTrackLoudness(scanned.integratedLufs, scanned.truePeakDb)
                        }
                    }
                }
            } else {
                activeEngine.clearTrackLoudness()
            }

            fadingEngine?.stop()
            fadingEngine?.release()
            fadingEngine = null
            
            activeEngine.setVolume(volumeAmplitude)
            activeEngine.setTrackGainDb(trackGainDb)
            activeEngine.setMediaItem(url, headers, startPositionMs)
            activeEngine.prepare()
            if (playWhenReady) activeEngine.play()
        }
    }

    private fun buildHeaders(track: Track?): Map<String, String> {
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
}
