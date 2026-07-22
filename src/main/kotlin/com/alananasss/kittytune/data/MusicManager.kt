package com.alananasss.kittytune.data

import com.alananasss.kittytune.audio.AudioEngine
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.player.AudioEffectsState
import com.alananasss.kittytune.ui.player.PlaybackContext
import com.alananasss.kittytune.utils.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Desktop port of the Android MusicManager.
 *
 * Android wrapped a single ExoPlayer whose ResolvingDataSource lazily turned a
 * `soundtune://track/<id>` URI into a real stream URL at load time. On desktop the
 * AudioEngine takes a concrete URL, so this manager resolves the stream *before*
 * handing it to the engine (via StreamResolver), then plays it.
 *
 * The app-side queue / shuffle / repeat / radio still lives in PlayerViewModel â€” this
 * manager owns exactly one "current item" plus playback state, matching the Android split.
 *
 * Widevine DRM is stubbed (see docs/port/TODO-widevine-drm.md); the token cache API is
 * kept so the rest of the code compiles unchanged.
 */
object MusicManager {

    val engine = AudioEngine()

    /** Media3-shaped player over the engine, for PlayerViewModel (ported from ExoPlayer). */
    val player: com.alananasss.kittytune.media.Player by lazy {
        com.alananasss.kittytune.media.Player(engine)
    }

    var currentTrack: Track? = null

    // --- DRM token cache (kept for API compatibility; playback via CEF is a later TODO) ---
    private val drmTokenCache = ConcurrentHashMap<Long, String>()
    fun putDrmToken(trackId: Long, token: String) { drmTokenCache[trackId] = token }
    fun getDrmToken(trackId: Long): String? = drmTokenCache[trackId]

    // --- shared playback context (playlist/station name shown in the player) ---
    private val _contextFlow = MutableStateFlow<PlaybackContext?>(null)
    val contextFlow = _contextFlow.asStateFlow()
    fun updateContext(context: PlaybackContext?) { _contextFlow.value = context }

    var onTrackChange: ((Track) -> Unit)? = null
    var onNextClick: (() -> Unit)? = null
    var onPreviousClick: (() -> Unit)? = null
    /** Invoked when the current track finishes (queue advance handled by the ViewModel). */
    var onCompletion: (() -> Unit)? = null

    private var rainPlayer: RainPlayer? = null
    private val tokenManager = TokenManager
    private val scope = CoroutineScope(Dispatchers.Default)

    @Volatile private var initialized = false

    // --- observable playback state (the ViewModel mirrors these into Compose state) ------
    val isPlaying: Boolean get() = engine.isPlaying
    val positionMs: Long get() = engine.positionMs
    val durationMs: Long get() = engine.durationMs
    val isLoading: Boolean get() = engine.state == AudioEngine.State.BUFFERING

    fun init() {
        if (initialized) return
        initialized = true

        rainPlayer = RainPlayer()

        val prefs = com.alananasss.kittytune.data.local.PlayerPreferences()
        _contextFlow.value = prefs.getLastContext()

        engine.onCompletion = { onCompletion?.invoke() }
        engine.onError = { it.printStackTrace() }
    }

    /**
     * Resolve [track] to a playable URL and start it in the engine.
     * @param autoPlay start immediately (false = prepared but paused, used for session restore)
     * @param startPositionMs resume position
     */
    fun playTrack(track: Track, autoPlay: Boolean = true, startPositionMs: Long = 0L) {
        currentTrack = track
        scope.launch {
            val resolved = withContext(Dispatchers.IO) {
                StreamResolver.resolveStreamWithDrm(track)
            }
            val url = resolved?.url
            if (resolved?.isDrmProtected == true && resolved.licenseAuthToken != null) {
                putDrmToken(track.id, resolved.licenseAuthToken)
            }
            if (url == null) {
                // Nothing playable (e.g. DRM-only with no fallback) â€” let the ViewModel skip.
                onCompletion?.invoke()
                return@launch
            }

            val headers = buildStreamHeaders(track)
            engine.setMediaItem(url, headers, startPositionMs)
            engine.prepare()
            if (autoPlay) engine.play()

            currentTrack?.let { t -> onTrackChange?.invoke(t) }
        }
    }

    private fun buildStreamHeaders(track: Track): Map<String, String> {
        val headers = mutableMapOf(
            "User-Agent" to "SoundCloud/2025.12.10-release (Android 10; Android)",
        )
        if (track.source != "youtube") {
            headers["Origin"] = "https://soundcloud.com"
            headers["Referer"] = "https://soundcloud.com/"
        }
        return headers
    }

    fun play() = engine.play()
    fun pause() = engine.pause()
    fun seekTo(ms: Long) = engine.seekTo(ms)
    fun setVolume(v: Float) = engine.setVolume(v)
    fun getVolume(): Float = engine.getVolume()
    fun stop() = engine.stop()

    fun applyEffects(state: AudioEffectsState) {
        engine.applyEffects(state)
        rainPlayer?.setEnabled(state.isRainEnabled)
        rainPlayer?.setVolume(state.rainVolume)
    }

    fun releasePlayer() {
        engine.release()
        rainPlayer?.release()
        rainPlayer = null
        drmTokenCache.clear()
        initialized = false
    }
}

