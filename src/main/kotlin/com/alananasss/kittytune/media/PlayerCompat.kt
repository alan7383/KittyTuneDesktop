package com.alananasss.kittytune.media

import com.alananasss.kittytune.audio.AudioEngine
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.StreamResolver
import com.alananasss.kittytune.data.SessionManager
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.player.AudioEffectsState
import com.alananasss.kittytune.utils.SignedUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

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

    var playWhenReady: Boolean = false
        set(value) {
            field = value
            if (value) activeEngine.play() else activeEngine.pause()
        }

    var repeatMode: Int = REPEAT_MODE_OFF

    val isPlaying: Boolean get() = activeEngine.isPlaying
    val currentPosition: Long get() = activeEngine.positionMs
    val duration: Long get() = activeEngine.durationMs
    val isLoading: Boolean get() = activeEngine.state == AudioEngine.State.BUFFERING

    var volume: Float = 1f
        set(value) {
            field = value
            activeEngine.setVolume(value)
        }

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
        eng.onCompletion = { onCompletion?.invoke() }
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
        items.clear()
        items.add(item)
        currentIndex = 0
        loadCurrent(startPositionMs, false, 0L)
        listeners.forEach { it.onMediaItemTransition(currentMediaItem, MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) }
    }

    fun crossfadeToMediaItem(item: MediaItem, startPositionMs: Long, crossfadeDurationMs: Long) {
        items.clear()
        items.add(item)
        currentIndex = 0
        
        isCrossfadingOut = false
        
        val oldEngine = activeEngine
        val newEngine = AudioEngine()
        
        unbindEngine(oldEngine)
        bindEngine(newEngine)
        
        activeEngine = newEngine
        fadingEngine = oldEngine
        
        lastEffectsState?.let { newEngine.applyEffects(it) }
        
        listeners.forEach { it.onMediaItemTransition(currentMediaItem, MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) }
        
        loadCurrent(startPositionMs, true, crossfadeDurationMs)
    }

    fun addMediaItem(item: MediaItem) { items.add(item) }
    fun addMediaItems(newItems: List<MediaItem>) { items.addAll(newItems) }
    fun removeMediaItem(index: Int) { if (index in items.indices) items.removeAt(index) }

    fun prepare() { activeEngine.prepare() }

    fun play() { playWhenReady = true }
    fun pause() { playWhenReady = false }
    fun stop() {
        fadingEngine?.stop()
        fadingEngine?.release()
        fadingEngine = null
        activeEngine.stop()
    }
    fun release() {
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
            // A prefetched item carries the URL it was resolved with; if its CDN signature has
            // since lapsed, resolving again is far cheaper than handing FFmpeg a certain 403.
            val url = item.uri?.takeIf {
                if (SignedUrl.isNetworkUrl(it)) !SignedUrl.isExpired(it) else java.io.File(it).exists()
            }
                ?: item.track?.let { withContext(Dispatchers.IO) { StreamResolver.resolveStream(it) } }
            if (url == null) {
                listeners.forEach { it.onPlaybackStateChanged(STATE_ENDED) }
                return@launch
            }
            val headers = buildHeaders(item.track)
            
            if (isCrossfade) {
                val oldEngine = fadingEngine
                activeEngine.setMediaItem(url, headers, startPositionMs)
                activeEngine.prepare()
                
                val targetVolume = volume
                activeEngine.setVolume(0f)
                activeEngine.setTrackGainDb(trackGainDb)
                
                if (playWhenReady) activeEngine.play()
                
                if (oldEngine != null) {
                    var remainingMs = oldEngine.durationMs - oldEngine.positionMs
                    if (remainingMs < 0) remainingMs = 0
                    
                    val actualCrossfadeMs = if (oldEngine.isPlaying && remainingMs > 0 && remainingMs < crossfadeDurationMs) {
                        remainingMs
                    } else if (!oldEngine.isPlaying || remainingMs == 0L) {
                        0L
                    } else {
                        crossfadeDurationMs
                    }

                    if (actualCrossfadeMs <= 0L) {
                        activeEngine.setVolume(targetVolume)
                        oldEngine.stop()
                        oldEngine.release()
                        fadingEngine = null
                    } else {
                        val steps = 40
                        val delayMs = actualCrossfadeMs / steps
                        for (i in 1..steps) {
                            if (fadingEngine != oldEngine) break // superseded
                            val ratio = i.toFloat() / steps
                            activeEngine.setVolume(targetVolume * ratio)
                            oldEngine.setVolume(targetVolume * (1f - ratio))
                            delay(delayMs)
                        }
                        if (fadingEngine == oldEngine) {
                            activeEngine.setVolume(targetVolume)
                            oldEngine.stop()
                            oldEngine.release()
                            fadingEngine = null
                        }
                    }
                } else {
                    activeEngine.setVolume(targetVolume)
                }
            } else {
                fadingEngine?.stop()
                fadingEngine?.release()
                fadingEngine = null
                
                activeEngine.setVolume(volume)
                activeEngine.setTrackGainDb(trackGainDb)
                activeEngine.setMediaItem(url, headers, startPositionMs)
                activeEngine.prepare()
                if (playWhenReady) activeEngine.play()
            }
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
