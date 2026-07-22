package com.alananasss.kittytune.media

import com.alananasss.kittytune.audio.AudioEngine
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.StreamResolver
import com.alananasss.kittytune.data.SessionManager
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Minimal Media3-shaped compatibility layer over the desktop [AudioEngine], so
 * PlayerViewModel ports from the Android version with only its media3 imports rewritten.
 *
 * It reproduces exactly the ExoPlayer surface PlayerViewModel touches:
 *   isPlaying, playWhenReady, currentPosition, duration, volume, repeatMode,
 *   mediaItemCount, currentMediaItem, currentMediaItemIndex,
 *   play/pause/prepare/seekTo/setMediaItem/addMediaItem(s)/removeMediaItem,
 *   addListener/removeListener + Player.Listener callbacks and state constants.
 *
 * The app keeps its own queue in PlayerViewModel and drives ExoPlayer with 1â€“2 items;
 * this shim mirrors that: it holds a small media-item list and plays the current one
 * through the engine (resolving the stream URL first, since the engine needs a real URL).
 */

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

/** Request metadata (Android exposed the original request URI here). */
class RequestMetadata(val mediaUri: String?)

/** A single playable item: a track id (mediaId) + resolved/placeholder URI + metadata. */
class MediaItem private constructor(
    val mediaId: String,
    val uri: String?,
    val mediaMetadata: MediaMetadata,
    val requestMetadata: RequestMetadata,
    /** The domain Track this item was built from (desktop extra: lets the shim resolve streams). */
    val track: Track?,
) {
    class Builder {
        private var mediaId: String = ""
        private var uri: String? = null
        private var metadata: MediaMetadata = MediaMetadata.Builder().build()
        private var track: Track? = null
        fun setUri(v: Any?) = apply { uri = v?.toString() }
        fun setMediaId(v: String) = apply { mediaId = v }
        fun setMediaMetadata(v: MediaMetadata) = apply { metadata = v }
        fun setTrack(v: Track?) = apply { track = v }
        fun build() = MediaItem(mediaId, uri, metadata, RequestMetadata(uri), track)
    }
}

/**
 * The desktop "Player" â€” same members PlayerViewModel uses, backed by AudioEngine.
 * Constants live in the [Player] companion to mirror `Player.STATE_*` / `Player.REPEAT_MODE_*`.
 */
class Player(private val engine: AudioEngine) {

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
    private val tokenManager = TokenManager

    var playWhenReady: Boolean = false
        set(value) {
            field = value
            if (value) engine.play() else engine.pause()
        }

    var repeatMode: Int = REPEAT_MODE_OFF

    val isPlaying: Boolean get() = engine.isPlaying
    val currentPosition: Long get() = engine.positionMs
    val duration: Long get() = engine.durationMs
    var volume: Float
        get() = engine.getVolume()
        set(value) { engine.setVolume(value) }

    val mediaItemCount: Int get() = items.size
    val currentMediaItem: MediaItem? get() = items.getOrNull(currentIndex)
    val currentMediaItemIndex: Int get() = currentIndex

    init {
        engine.onPlayingChanged = { playing -> listeners.forEach { it.onIsPlayingChanged(playing) } }
        engine.onStateChanged = { st ->
            val mapped = when (st) {
                AudioEngine.State.BUFFERING -> STATE_BUFFERING
                AudioEngine.State.READY -> STATE_READY
                AudioEngine.State.ENDED -> STATE_ENDED
                AudioEngine.State.IDLE -> STATE_IDLE
            }
            listeners.forEach { it.onPlaybackStateChanged(mapped) }
        }
        engine.onError = { err -> listeners.forEach { it.onPlayerError(err) } }
        engine.onCompletion = {
            // ExoPlayer would emit STATE_ENDED; the state callback above already does that.
        }
    }

    fun addListener(l: Listener) { if (!listeners.contains(l)) listeners.add(l) }
    fun removeListener(l: Listener) { listeners.remove(l) }

    fun setMediaItem(item: MediaItem, startPositionMs: Long = 0L) {
        items.clear()
        items.add(item)
        currentIndex = 0
        loadCurrent(startPositionMs)
        listeners.forEach { it.onMediaItemTransition(currentMediaItem, MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) }
    }

    fun addMediaItem(item: MediaItem) { items.add(item) }
    fun addMediaItems(newItems: List<MediaItem>) { items.addAll(newItems) }
    fun removeMediaItem(index: Int) { if (index in items.indices) items.removeAt(index) }

    fun prepare() { /* engine.prepare() is triggered inside loadCurrent */ }

    fun play() { playWhenReady = true }
    fun pause() { playWhenReady = false }

    fun seekTo(positionMs: Long) {
        engine.seekTo(positionMs)
        val pi = PositionInfo(positionMs)
        listeners.forEach { it.onPositionDiscontinuity(pi, pi, DISCONTINUITY_REASON_SEEK) }
    }

    /** seekTo(mediaItemIndex, positionMs) overload used for queue jumps. */
    fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (mediaItemIndex in items.indices) {
            currentIndex = mediaItemIndex
            loadCurrent(positionMs)
            listeners.forEach { it.onMediaItemTransition(currentMediaItem, MEDIA_ITEM_TRANSITION_REASON_AUTO) }
        }
    }

    private fun loadCurrent(startPositionMs: Long) {
        val item = currentMediaItem ?: return
        resolveJob?.cancel()
        resolveJob = scope.launch {
            val url = item.uri?.takeIf { it.startsWith("http") || java.io.File(it).exists() }
                ?: item.track?.let { withContext(Dispatchers.IO) { StreamResolver.resolveStream(it) } }
            if (url == null) {
                listeners.forEach { it.onPlaybackStateChanged(STATE_ENDED) }
                return@launch
            }
            val headers = buildHeaders(item.track)
            engine.setMediaItem(url, headers, startPositionMs)
            engine.prepare()
            if (playWhenReady) engine.play()
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
}

