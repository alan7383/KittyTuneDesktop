package com.alananasss.kittytune.ui.player

import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.core.Application
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.alananasss.kittytune.core.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.media.MediaItem
import com.alananasss.kittytune.media.MediaMetadata
import com.alananasss.kittytune.media.Player
import com.alananasss.kittytune.media.Player as ExoPlayer
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.*
import com.alananasss.kittytune.data.local.LocalPlaylist
import com.alananasss.kittytune.data.local.LyricsAlignment
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.network.LrcLibClient
import com.alananasss.kittytune.data.ListeningStatsRepository
import com.alananasss.kittytune.data.network.LrcLibResponse
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.*
import com.alananasss.kittytune.ui.player.lyrics.LyricLine
import com.alananasss.kittytune.ui.player.lyrics.LyricsUtils
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.net.URLEncoder
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import com.alananasss.kittytune.data.network.MusixmatchClient
import kotlin.time.Duration.Companion.milliseconds

enum class CommentSort(val value: String, val labelResId: String) {
    NEWEST("newest", "sort_newest"),
    TIMESTAMP("track-timestamp", "sort_timestamp"),
    OLDEST("oldest", "sort_oldest"),
}

enum class LyricsMode { SYNCED, PLAIN }

data class UnifiedLyricResult(
    val id: String,
    val name: String,
    val artistName: String,
    val albumName: String?,
    val durationSec: Double,
    val hasLineSync: Boolean,
    val hasWordSync: Boolean,
    val provider: String // "LRCLIB" ou "MUSIXMATCH"
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val gson = Gson()
    private val api = RetrofitClient.create()
    private val playerPrefs = PlayerPreferences()
    private val tokenManager = TokenManager

    private val _uiEvent = MutableSharedFlow<String>()
    val uiEvent = _uiEvent.asSharedFlow()

    var currentUserId by mutableLongStateOf(0L)
    var currentUser by mutableStateOf<User?>(null)
    var currentTrack by mutableStateOf<Track?>(null)
    var isPlaying by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    var duration by mutableLongStateOf(0L)
    var currentPosition by mutableLongStateOf(0L)
    var isScrubbing by mutableStateOf(false)
    var isPlayerExpanded by mutableStateOf(false)
    var isLiked by mutableStateOf(false)
    var backgroundColor by mutableStateOf(Color(0xFF1E1E1E))
    val hasLyrics by derivedStateOf { lyricsLines.isNotEmpty() || !rawPlainLyrics.isNullOrBlank() }
    var commentSort by mutableStateOf(CommentSort.NEWEST)

    var currentContext by mutableStateOf<PlaybackContext?>(null)
    private var isRestoringSession = true

    private var playerInitialized = false
    val player: ExoPlayer
        get() {
            if (!playerInitialized) {
                playerInitialized = true
                MusicManager.init()
                MusicManager.player.addListener(playerListener)
                MusicManager.applyEffects(effectsState)
            }
            return MusicManager.player
        }

    var effectsState by mutableStateOf(playerPrefs.getLastEffects())
    var isPreciseSpeedEnabled by mutableStateOf(playerPrefs.getPreciseSpeedEnabled())

    var volume by mutableFloatStateOf(MusicManager.getVolume())
        private set

    private var volumeBeforeMute: Float = 1.0f

    fun updateVolume(v: Float) {
        val newVol = v.coerceIn(0f, 1f)
        volume = newVol
        MusicManager.setVolume(newVol)
    }

    fun toggleMute() {
        if (volume > 0f) {
            volumeBeforeMute = volume
            updateVolume(0f)
        } else {
            updateVolume(if (volumeBeforeMute > 0f) volumeBeforeMute else 1.0f)
        }
    }

    fun volumeUp(delta: Float = 0.1f) {
        updateVolume(volume + delta)
    }

    fun volumeDown(delta: Float = 0.1f) {
        updateVolume(volume - delta)
    }

    fun changeOutputDevice(deviceName: String) {
        playerPrefs.setAudioDevice(deviceName)
        MusicManager.hotSwapDevice()
    }

    var repeatMode by mutableStateOf(playerPrefs.getLastRepeatMode())
    var shuffleEnabled by mutableStateOf(playerPrefs.getLastShuffleEnabled())
    private var isAutoplayRadioLoading by mutableStateOf(false)

    var repostedTrackIds by mutableStateOf<Set<Long>>(emptySet())
        private set

    var showMenuSheet by mutableStateOf(false)
    var navigateToPlaylistId by mutableStateOf<String?>(null)
    var trackForMenu by mutableStateOf<Track?>(null)
    var menuContextPlaylistId by mutableStateOf<Long?>(null)
    var isMenuContextFromPlayer by mutableStateOf(false)

    // Playlist context menu (right-click on a playlist card anywhere)
    var showPlaylistMenuSheet by mutableStateOf(false)
    var playlistForMenu by mutableStateOf<Playlist?>(null)

    var selectedTrackForSheet by mutableStateOf<Track?>(null)
    var isLocalDetailsMode by mutableStateOf(false)
    var localFilePathForDetails by mutableStateOf<String?>(null)

    var showDetailsSheet by mutableStateOf(false)

    var showCommentsSheet by mutableStateOf(false)
    val commentsList = mutableStateListOf<Comment>()
    var isCommentsLoading by mutableStateOf(false)
    var commentNextHref: String? = null
    var isPostingComment by mutableStateOf(false)
    var captchaUrl by mutableStateOf<String?>(null)

    var socialLikerUser by mutableStateOf<User?>(null)
    var isSocialLikerLoading by mutableStateOf(false)
    private var socialProofTrackId: Long? = null

    var replyingToComment by mutableStateOf<Comment?>(null)
    private var pendingCommentBody: String? = null
    private var pendingCommentTimestamp: Long? = null

    private var _showAddToPlaylistSheet by mutableStateOf(false)
    var showAddToPlaylistSheet: Boolean
        get() = _showAddToPlaylistSheet
        set(value) {
            _showAddToPlaylistSheet = value
            if (value) fetchOnlinePlaylistsForAdd()
        }
    var tracksToAddInBulk by mutableStateOf<List<Track>?>(null)
    val userPlaylists = mutableStateListOf<LocalPlaylist>()

    private fun fetchOnlinePlaylistsForAdd() {
        if (TokenManager.isGuestMode() || !com.alananasss.kittytune.utils.NetworkUtils.isInternetAvailable()) return
        viewModelScope.launch {
            try {
                val api = RetrofitClient.create()
                val me = api.getMe()
                val online = api.getUserCreatedPlaylists(me.id).collection
                val currentLocalIds = userPlaylists.map { it.id }.toSet()
                val newPlaylists = online.filter { !currentLocalIds.contains(it.id) }.map {
                    LocalPlaylist(
                        id = it.id,
                        title = it.title ?: "",
                        artist = it.user?.username ?: "",
                        artworkUrl = it.fullResArtwork,
                        trackCount = it.trackCount ?: 0,
                        isUserCreated = true
                    )
                }
                userPlaylists.addAll(newPlaylists)
            } catch (_: Exception) {}
        }
    }

    private val _originalQueue = mutableListOf<Track>()
    private val _queue = mutableListOf<Track>()
    val queue: List<Track> get() = _queue
    var queueState by mutableStateOf<List<Track>>(emptyList())
        private set
    var currentQueueIndex by mutableIntStateOf(-1)

    var isPreciseLyricsSearchEnabled by mutableStateOf(playerPrefs.getPreciseLyricsSearchEnabled())
    var isAppleMusicEffectEnabled by mutableStateOf(playerPrefs.getLyricsAppleEffectEnabled())
    var isWordSyncEnabled by mutableStateOf(playerPrefs.getLyricsWordSyncEnabled())

    fun toggleAppleMusicEffect(enabled: Boolean) {
        isAppleMusicEffectEnabled = enabled
        playerPrefs.setLyricsAppleEffectEnabled(enabled)
    }

    fun toggleWordSync(enabled: Boolean) {
        isWordSyncEnabled = enabled
        playerPrefs.setLyricsWordSyncEnabled(enabled)
    }

    var isRomanizationEnabled by mutableStateOf(playerPrefs.getLyricsRomanizationEnabled())

    fun toggleRomanization(enabled: Boolean) {
        isRomanizationEnabled = enabled
        playerPrefs.setLyricsRomanizationEnabled(enabled)
        if (enabled && lyricsLines.isNotEmpty()) {
            fetchRomanizationForCurrentLines()
        }
    }

    fun fetchRomanizationForCurrentLines() {
        if (lyricsLines.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val originalLines = lyricsLines.map { it.text }.filter { it.isNotBlank() }.distinct()
            val romMap = com.alananasss.kittytune.data.network.FreeTranslator.getRomanization(originalLines)
            withContext(Dispatchers.Main) {
                for (i in lyricsLines.indices) {
                    val oldLine = lyricsLines[i]
                    val rom = romMap[oldLine.text.trim()]
                    lyricsLines[i] = oldLine.copy(romanization = rom)
                }
            }
        }
    }

    var lyricsProvider by mutableStateOf(playerPrefs.getLyricsProvider())
        private set

    fun updateLyricsProvider(provider: LyricsProvider) {
        lyricsProvider = provider
        playerPrefs.setLyricsProvider(provider)
        reloadLyrics()
    }

    var isLyricsTranslationEnabled by mutableStateOf(playerPrefs.getLyricsTranslationEnabled())
    var lyricsTranslationLang by mutableStateOf(playerPrefs.getLyricsTranslationLang())
    var isTranslatingLyrics by mutableStateOf(false)
    var lastFetchedMxmTrackId: Long? = null
    private var translationJob: Job? = null

    fun toggleLyricsTranslation(enabled: Boolean) {
        isLyricsTranslationEnabled = enabled
        playerPrefs.setLyricsTranslationEnabled(enabled)
        if (enabled && lyricsLines.isNotEmpty()) {
            fetchTranslationsForCurrentLines(lyricsTranslationLang)
        }
    }

    fun setLyricsTranslationLanguage(lang: String) {
        lyricsTranslationLang = lang
        playerPrefs.setLyricsTranslationLang(lang)
        if (isLyricsTranslationEnabled && lyricsLines.isNotEmpty()) {
            fetchTranslationsForCurrentLines(lang)
        }
    }

    fun fetchTranslationsForCurrentLines(targetLang: String = lyricsTranslationLang) {
        translationJob?.cancel()
        if (lyricsLines.isEmpty()) return
        isTranslatingLyrics = true
        val trackId = lastFetchedMxmTrackId
        val originalLines = lyricsLines.map { it.text }.filter { it.isNotBlank() }.distinct()

        translationJob = viewModelScope.launch(Dispatchers.IO) {
            val translationMap = mutableMapOf<String, String>()

            if (trackId != null) {
                try {
                    val token = com.alananasss.kittytune.data.network.MusixmatchClient.getValidToken()
                    val translationsRes = com.alananasss.kittytune.data.network.MusixmatchClient.api.getTranslations(trackId = trackId, lang = targetLang, token = token)
                    translationsRes.message.body?.translationsList?.forEach { wrapper ->
                        wrapper.translation?.let { t ->
                            translationMap[t.matchedLine.trim()] = t.description.trim()
                        }
                    }
                } catch (_: Exception) {}
            }

            val missingLines = originalLines.filter { !translationMap.containsKey(it.trim()) }
            if (missingLines.isNotEmpty()) {
                val machineTranslations = com.alananasss.kittytune.data.network.FreeTranslator.translateMissing(missingLines, targetLang)
                translationMap.putAll(machineTranslations)
            }

            withContext(Dispatchers.Main) {
                for (i in lyricsLines.indices) {
                    val oldLine = lyricsLines[i]
                    val newTranslation = translationMap[oldLine.text.trim()]
                    lyricsLines[i] = oldLine.copy(translation = newTranslation)
                }
                isTranslatingLyrics = false
            }
        }
    }

    var showLyricsSheet by mutableStateOf(false)
    var lyricsLines = mutableStateListOf<LyricLine>()
    var isLyricsLoading by mutableStateOf(false)
    var isSearchingLyrics by mutableStateOf(false)
    var manualSearchQuery by mutableStateOf("")
    val lyricSearchResults = mutableStateListOf<LrcLibResponse>()
    var manualSearchProvider by mutableStateOf("MUSIXMATCH") // Par défaut sur Musixmatch !
    val unifiedLyricSearchResults = mutableStateListOf<UnifiedLyricResult>()

    var lyricsFontSize by mutableFloatStateOf(playerPrefs.getLyricsFontSize())
    var lyricsAlignment by mutableStateOf(playerPrefs.getLyricsAlignment())

    var lyricsMode by mutableStateOf(LyricsMode.SYNCED)
    var rawPlainLyrics by mutableStateOf<String?>(null)
    var showInlineLyrics by mutableStateOf(false)
    var lyricsOffset by mutableLongStateOf(0L)
    var showLyricsOffsetControls by mutableStateOf(false)

    // Right Panel resize state
    var rightPanelWidth by mutableFloatStateOf(playerPrefs.getRightPanelWidth())
    private var rightPanelDragRaw = 0f

    fun rightPanelDragStart() {
        rightPanelDragRaw = rightPanelWidth
    }

    fun rightPanelDragBy(deltaDp: Float) {
        rightPanelDragRaw = (rightPanelDragRaw - deltaDp).coerceIn(
            com.alananasss.kittytune.data.local.RIGHT_PANEL_MIN_WIDTH,
            com.alananasss.kittytune.data.local.RIGHT_PANEL_MAX_WIDTH
        )
        rightPanelWidth = rightPanelDragRaw
    }

    fun rightPanelDragEnd() {
        playerPrefs.setRightPanelWidth(rightPanelWidth)
    }

    var currentSessionListenMs = 0L
    private var hasPushedRecentlyPlayed = false

    // Sleep Timer
    var sleepTimerRemainingMs by mutableLongStateOf(0L)
    var sleepTimerEndOfTrack by mutableStateOf(false)
    var showSleepTimerDialog by mutableStateOf(false)
    val isSleepTimerActive: Boolean get() = sleepTimerRemainingMs > 0L || sleepTimerEndOfTrack
    private var sleepTimerJob: Job? = null
    private var preFadeVolume: Float = 1f

    private var pendingSeekPosition: Long? = null
    private var seekTargetPosition: Long = -1L
    private var lastSeekTimestamp: Long = 0L
    private var saveQueueJob: Job? = null
    private var progressUpdateJob: Job? = null
    private var lyricsJob: Job? = null
    private var queueChunkingJob: Job? = null
    private var trackInitJob: Job? = null
    private var playJob: Job? = null
    private var discordJob: Job? = null
    private var discordRpc: com.alananasss.kittytune.data.DiscordRPC? = null
    private var mprisService: com.alananasss.kittytune.data.MprisService? = null
    private var windowsSmtcService: com.alananasss.kittytune.data.WindowsSmtcService? = null

    // Android used a BroadcastReceiver to sync player state across the app<->service
    // process boundary. Desktop is single-process, so this is a no-op.
    private val syncReceiver = Any()

    private fun getString(resId: String): String = str(resId)
    private fun getString(resId: String, vararg args: Any): String = str(resId, *args)

    private fun parseIdFromMediaId(mediaId: String): Long {
        var cleanId = mediaId
        if (cleanId.startsWith(KittyTuneMediaLibrarySessionCallback.TRACK_PREFIX)) {
            cleanId = cleanId.removePrefix(KittyTuneMediaLibrarySessionCallback.TRACK_PREFIX)
        }
        if (cleanId.contains(KittyTuneMediaLibrarySessionCallback.CONTEXT_SEPARATOR)) {
            cleanId = cleanId.substringBefore(KittyTuneMediaLibrarySessionCallback.CONTEXT_SEPARATOR)
        }
        return cleanId.toLongOrNull() ?: mediaId.hashCode().toLong()
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlayingState: Boolean) {
            isPlaying = isPlayingState
            saveStateAsync(saveQueue = false)
            if (isPlayingState) startProgressUpdate()
            updateDiscordPresence()
        }

        // onTimelineChanged: Android used it to react to playlist changes, but syncQueueFromPlayer
        // is a no-op (KittyTune keeps its own queue). Dropped on desktop.

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                isLoading = false
                if (MusicManager.player.duration > 0) duration = MusicManager.player.duration
                pendingSeekPosition?.let { MusicManager.player.seekTo(it); pendingSeekPosition = null }
            }
            if (state == Player.STATE_BUFFERING) isLoading = true

            if (state == Player.STATE_ENDED) {
                if (com.alananasss.kittytune.core.AppInstance.isShuttingDown) return

                // Record listening stats
                currentTrack?.let { track ->
                    if (playerPrefs.getListeningStatsEnabled() && currentSessionListenMs > 0) {
                        if (repeatMode == RepeatMode.ONE) {
                            ListeningStatsRepository.recordEvent(track, "REPEAT_ONE_LOOP", currentSessionListenMs)
                        } else {
                            ListeningStatsRepository.recordEvent(track, "PLAY_COMPLETE", currentSessionListenMs)
                        }
                    }
                }

                // Reset session listen tracking after a complete play or loop
                currentSessionListenMs = 0L

                // Sleep timer: end of track mode
                if (sleepTimerEndOfTrack) {
                    cancelSleepTimer()
                    MusicManager.player.pause()
                    showSleepTimerIslandNotification(isStarted = false)
                    emitUiEvent(str("sleep_timer_cancelled"))
                    return
                }

                if (repeatMode == RepeatMode.ONE) {
                    currentPosition = 0L
                    MusicManager.player.seekTo(0)
                    MusicManager.player.play()
                } else {
                    if (!MusicManager.player.isCrossfadingOut) {
                        playNext(manual = false, isCrossfade = playerPrefs.getCrossfadeEnabled())
                    }
                }
            }
        }

        override fun onPlayerError(error: Throwable) {
            println("Player error: ${error.message}")

            currentTrack?.let { StreamResolver.evictStream(it.id) }

            // On desktop the engine surfaces a generic Throwable; treat auth/network/format
            // failures uniformly by retrying the current track once at the same position.
            val msg = (error.message ?: "").lowercase()
            val isRetryable = msg.contains("403") || msg.contains("401") ||
                msg.contains("network") || msg.contains("timeout") ||
                msg.contains("connection") || msg.contains("format")

            if (isRetryable) {
                if (currentQueueIndex >= 0 && currentQueueIndex < _queue.size) {
                    viewModelScope.launch {
                        player.playWhenReady = false
                        isLoading = true

                        if (isActive) {
                            playRobustly(
                                index = currentQueueIndex,
                                autoPlay = true,
                                startPosition = currentPosition,
                                allowSkipOnFailure = false
                            )
                        } else {
                            isLoading = false
                        }
                    }
                    return
                }
            }

            isLoading = false
            isPlaying = false
            if (playJob?.isActive != true) {
                if (!com.alananasss.kittytune.core.AppInstance.isShuttingDown) {
                    playNext(manual = false, ignoreRepeatOne = true)
                }
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            super.onPositionDiscontinuity(oldPosition, newPosition, reason)

            if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
                currentPosition = newPosition.positionMs

                saveStateAsync(saveQueue = false)
                updateDiscordPresence()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
            if (mediaItem == null) return
            
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                if (repeatMode == RepeatMode.ALL && MusicManager.player.mediaItemCount == 1) {
                    MusicManager.player.pause()
                    playNext(manual = false)
                    return
                }
            }
            
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                val shiftCount = MusicManager.player.currentMediaItemIndex
                if (shiftCount > 0) {
                    currentQueueIndex += shiftCount
                    if (currentQueueIndex >= _queue.size && repeatMode == RepeatMode.ALL && _queue.isNotEmpty()) {
                        currentQueueIndex %= _queue.size
                    }
                    repeat(shiftCount) {
                        try { MusicManager.player.removeMediaItem(0) } catch (_: Exception) {}
                    }
                    preloadNextTrack(currentQueueIndex + 1)
                }
            }

            val trackId = parseIdFromMediaId(mediaItem.mediaId)

            val expectedTrackId = _queue.getOrNull(currentQueueIndex)?.id
            if (expectedTrackId != null && expectedTrackId != trackId) {
                return
            }

            if (currentTrack?.id != trackId) {
                currentSessionListenMs = 0L // reset listen time on track change
                hasPushedRecentlyPlayed = false
            }

            if (MusicManager.currentTrack?.id == trackId) {
                currentTrack = MusicManager.currentTrack
            } else if (currentTrack?.id != trackId) {
                val meta = mediaItem.mediaMetadata
                val source = if (mediaItem.mediaId.startsWith("yt_") || mediaItem.requestMetadata.mediaUri?.toString()?.contains("youtube") == true) "youtube" else "soundcloud"

                currentTrack = Track(
                    id = trackId,
                    title = meta.title?.toString() ?: "Unknown",
                    durationMs = 0L,
                    artworkUrl = meta.artworkUri?.toString(),
                    user = User(0, meta.artist?.toString() ?: "Unknown", null),
                    permalinkUrl = "",
                    playbackCount = 0,
                    likesCount = 0,
                    repostsCount = 0,
                    commentCount = 0,
                    source = source
                )
            }
        }
    }

    init {
        MusicManager.init()
        playerInitialized = true
        MusicManager.player.addListener(playerListener)
        MusicManager.applyEffects(effectsState)
        applyRepeatMode()
        fetchUserProfile()


        MusicManager.onNextClick = { 
            val crossfadeEnabled = playerPrefs.getCrossfadeEnabled()
            playNext(manual = true, isCrossfade = crossfadeEnabled) 
        }
        MusicManager.onPreviousClick = { 
            val crossfadeEnabled = playerPrefs.getCrossfadeEnabled()
            smartPrevious(isCrossfade = crossfadeEnabled) 
        }

        MusicManager.onTrackChange = trackChangeHandler@{ newTrack ->
            // Sleep timer: end of track mode â€” stop here
            if (sleepTimerEndOfTrack) {
                cancelSleepTimer()
                viewModelScope.launch(Dispatchers.Main) {
                    MusicManager.player.pause()
                    isPlaying = false
                    showSleepTimerIslandNotification(isStarted = false)
                }
                emitUiEvent(str("sleep_timer_cancelled"))
                return@trackChangeHandler
            }

            showInlineLyrics = false
            lyricsLines.clear()
            rawPlainLyrics = null

            val expectedTrackId = _queue.getOrNull(currentQueueIndex)?.id
            if (expectedTrackId != null && expectedTrackId != newTrack.id) {
                // Ignore stale track change events from fast skipping
                return@trackChangeHandler
            }

            var finalTrack = newTrack

            val currentMediaItem = MusicManager.player.currentMediaItem
            if (currentMediaItem != null) {
                val realId = parseIdFromMediaId(currentMediaItem.mediaId)
                if (realId != newTrack.id) {
                    finalTrack = newTrack.copy(id = realId)
                }
            }

            // KittyTune custom QueueManager: ExoPlayer only holds 1 item, so its internal index is ignored.
            // currentQueueIndex is strictly managed by playTrackAtIndex and playNext/smartPrevious.
            // However, on auto-advance, currentQueueIndex might not be updated yet, so we search the queue.
            val foundInQueue = _queue.find { it.id == finalTrack.id }
            if (foundInQueue != null) {
                finalTrack = foundInQueue
            }
            
            currentTrack = finalTrack
            MusicManager.currentTrack = finalTrack

            updatePlayerColors(finalTrack)
            updateDiscordPresence()

            viewModelScope.launch {
                isLiked = LikeRepository.isTrackLiked(finalTrack.id)
                loadLyrics(finalTrack)

                if (finalTrack.source == "soundcloud" && finalTrack.id > 0 && (finalTrack.permalinkUrl.isNullOrEmpty() || finalTrack.user?.avatarUrl.isNullOrEmpty() || finalTrack.playbackCount == 0)) {
                    try {
                        val fullTracks = api.getTracksByIds(finalTrack.id.toString())
                        val fullTrack = fullTracks.firstOrNull()

                        if (fullTrack != null) {
                            currentTrack = fullTrack
                            MusicManager.currentTrack = fullTrack
                        }
                    } catch (_: Exception) {
                    }
                }
            }

            try {
                if (MusicManager.player.isPlaying) {
                    isPlaying = true
                    duration = MusicManager.player.duration.coerceAtLeast(0L)
                    currentPosition = MusicManager.player.currentPosition
                    startProgressUpdate()
                    saveStateAsync(saveQueue = false)
                }
            } catch (_: Exception) {
            }
        }

        viewModelScope.launch {
            MusicManager.contextFlow.collect { ctx ->
                currentContext = ctx
                saveStateAsync(saveQueue = false)
            }
        }

        viewModelScope.launch {
            LikeRepository.likedTracks.collect { likedList ->
                currentTrack?.let { track ->
                    isLiked = likedList.any { it.id == track.id }
                }
            }
        }

        viewModelScope.launch {
            RepostRepository.repostedTrackIds.collect { ids ->
                repostedTrackIds = ids
            }
        }

        viewModelScope.launch {
            DownloadManager.getAllPlaylistsFlow().collect { playlists ->
                userPlaylists.clear()
                val sorted = playlists.sortedWith(compareByDescending<LocalPlaylist> { it.isUserCreated || it.id < 0 }.thenByDescending { it.addedAt })
                userPlaylists.addAll(sorted)
            }
        }
        try {
            Runtime.getRuntime().addShutdownHook(Thread {
                com.alananasss.kittytune.core.AppInstance.isShuttingDown = true
                val t = currentTrack
                val p = currentPosition
                val c = currentContext
                val s = shuffleEnabled
                val r = repeatMode
                val q = _originalQueue.toList()
                if (t != null && p > 0) {
                    playerPrefs.savePlaybackState(t, p, q, c, s, r)
                    com.alananasss.kittytune.core.Prefs.flush(force = true)
                }
            })
        } catch (_: Exception) {}

        restoreSession()
        syncWithCurrentPlayback()
    }

    fun toggleInlineLyrics() {
        showInlineLyrics = !showInlineLyrics
    }


    private fun initDiscordRpc() {
        val token = playerPrefs.getDiscordToken()
        val enabled = playerPrefs.getDiscordRpcEnabled()
        if (enabled && !token.isNullOrEmpty()) {
            if (discordRpc == null) {
                try {
                    discordRpc = com.alananasss.kittytune.data.DiscordRPC(token)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            closeDiscordRpc()
        }
    }

    fun closeDiscordRpc() {
        try {
            discordRpc?.closeRPC()
        } catch (_: Exception) {}
        discordRpc = null
    }

    private fun initMprisService() {
        if (mprisService == null) {
            try {
                mprisService = com.alananasss.kittytune.data.MprisService(
                    onPlay = { player.play() },
                    onPause = { player.pause() },
                    onPlayPause = { togglePlayPause() },
                    onNext = { playNext() },
                    onPrevious = { smartPrevious() },
                    onSeek = { seekTo(it) }
                )
            } catch (e: Exception) {
                println("MPRIS service init exception: ${e.message}")
            }
        }
    }

    private fun initWindowsSmtcService() {
        if (windowsSmtcService == null) {
            try {
                windowsSmtcService = com.alananasss.kittytune.data.WindowsSmtcService(
                    onPlay = { togglePlayPause() },
                    onPause = { togglePlayPause() },
                    onPlayPause = { togglePlayPause() },
                    onNext = { playNext() },
                    onPrevious = { smartPrevious() }
                )
            } catch (e: Exception) {
                println("Windows SMTC service init exception: ${e.message}")
            }
        }
    }

    fun updateMprisMedia() {
        initMprisService()
        mprisService?.updateMedia(currentTrack, isPlaying, currentPosition)

        initWindowsSmtcService()
        windowsSmtcService?.updateMedia(currentTrack, isPlaying)
    }

    fun updateDiscordPresence() {
        updateMprisMedia()
        val track = currentTrack
        val token = playerPrefs.getDiscordToken()
        val enabled = playerPrefs.getDiscordRpcEnabled()

        if (enabled && !token.isNullOrEmpty() && track != null) {
            initDiscordRpc()
            val contextText = currentContext?.displayText
            val playing = isPlaying
            val pos = currentPosition
            discordJob?.cancel()
            discordJob = viewModelScope.launch(Dispatchers.IO) {
                delay(300.milliseconds)
                try {
                    discordRpc?.updatePresence(track, contextText, playing, pos)
                } catch (e: Exception) {
                    if (e !is kotlinx.coroutines.CancellationException) {
                        e.printStackTrace()
                    }
                }
            }
        } else {
            closeDiscordRpc()
        }
    }

    override fun onCleared() {
        super.onCleared()
        closeDiscordRpc()
        try {
            mprisService?.close()
        } catch (_: Exception) {}
        mprisService = null

        try {
            windowsSmtcService?.close()
        } catch (_: Exception) {}
        windowsSmtcService = null

        try {
            MusicManager.player.removeListener(playerListener)
        } catch (_: IllegalStateException) {
        }
    }

    private fun syncStateFromPreferences() {
        viewModelScope.launch {
            val lastTrack = playerPrefs.getLastTrack()
            val lastQueue = playerPrefs.getLastQueue()
            val lastContext = playerPrefs.getLastContext()
            val lastShuffle = playerPrefs.getLastShuffleEnabled()
            val lastRepeat = playerPrefs.getLastRepeatMode()

            _queue.clear()
            _queue.addAll(lastQueue)
            _originalQueue.clear()
            _originalQueue.addAll(lastQueue)
            updateQueueState()

            currentTrack = lastTrack
            currentContext = lastContext
            shuffleEnabled = lastShuffle
            repeatMode = lastRepeat
            applyRepeatMode()

            if (lastTrack != null) {
                isLiked = LikeRepository.isTrackLiked(lastTrack.id)
                currentQueueIndex = _queue.indexOfFirst { it.id == lastTrack.id }.coerceAtLeast(0)
            }

            try {
                isPlaying = player.isPlaying
                duration = player.duration.coerceAtLeast(0L)
                currentPosition = player.currentPosition
                if(isPlaying) startProgressUpdate()
            } catch (_: Exception) {
            }
        }
    }

    fun isTrackReposted(trackId: Long): Boolean {
        return repostedTrackIds.contains(trackId)
    }

    fun repostTrack(track: Track, caption: String?) {
        RepostRepository.syncLocalState(track.id, true)
        emitUiEvent(str("repost_success"))

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = api.repostTrack(track.id)
                if (response.isSuccessful && !caption.isNullOrBlank()) {
                    delay(100.milliseconds)
                    api.addRepostCaption(track.id, RepostCaptionRequest(caption))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                RepostRepository.syncLocalState(track.id, false)
            }
        }
    }

    fun deleteRepost(trackId: Long) {
        RepostRepository.removeRepost(trackId)
        emitUiEvent(str("success_generic"))
    }

    fun updateLyricsFontSize(size: Float) {
        lyricsFontSize = size
        playerPrefs.setLyricsFontSize(size)
    }

    fun updateLyricsAlignment(alignment: LyricsAlignment) {
        lyricsAlignment = alignment
        playerPrefs.setLyricsAlignment(alignment)
    }



    fun togglePreciseLyricsSearch(enabled: Boolean) {
        isPreciseLyricsSearchEnabled = enabled
        playerPrefs.setPreciseLyricsSearchEnabled(enabled)
        currentTrack?.let { loadLyrics(it) }
    }

    fun openLyrics(targetTrack: Track? = null, forceSheet: Boolean = false) {
        val target = targetTrack ?: currentTrack ?: return
        if (target.id != currentTrack?.id) playPlaylist(listOf(target), 0)

        if (!forceSheet && playerPrefs.getInlineLyricsEnabled()) {
            toggleInlineLyrics()
        } else {
            lyricsMode = if (lyricsLines.isNotEmpty()) {
                LyricsMode.SYNCED
            } else {
                LyricsMode.PLAIN
            }
            showMenuSheet = false
            showLyricsSheet = true
        }
    }

    // Générateur intelligent de requêtes pour déjouer les titres SoundCloud
    private fun generateSearchQueries(title: String, uploader: String): List<String> {
        val queries = mutableSetOf<String>()

        // 1. Nettoyage EXTRÊME de l'artiste (Détruit les emojis, étoiles, symboles bizarres)
        // Ne garde que les lettres, chiffres, espaces, tirets, &, ', et $ (pour $uicideboy$ ou A$AP)
        val cleanArtist = uploader.replace(Regex("[^\\p{L}\\p{Nd}\\s\\-&'$]"), "").trim()

        // 2. Nettoyage de base du titre (Enlève tout ce qui est entre parenthèses ou crochets)
        val cleanTitle = title.replace(Regex("(?i)\\[.*?\\]|\\(.*?\\)"), "").trim()

        var parsedArtist = cleanArtist
        var parsedTitle = cleanTitle

        // 3. Détection du format "Artiste - Titre" (très courant sur SoundCloud)
        if (cleanTitle.contains("-")) {
            val parts = cleanTitle.split("-", limit = 2)
            parsedArtist = parts[0].replace(Regex("[^\\p{L}\\p{Nd}\\s\\-&'$]"), "").trim()
            parsedTitle = parts[1].trim()
        } else if (title.contains("-")) {
            val parts = title.split("-", limit = 2)
            parsedArtist = parts[0].replace(Regex("[^\\p{L}\\p{Nd}\\s\\-&'$]"), "").trim()
            parsedTitle = parts[1].replace(Regex("(?i)\\[.*?\\]|\\(.*?\\)"), "").trim()
        }

        // 4. Nettoyage "Ultra" du titre (Coupe tout ce qui suit w/, feat, ft, prod, x)
        // Ça transforme "giving girls cocaine w/ lil tracy" en "giving girls cocaine"
        val ultraCleanTitle = parsedTitle.replace(Regex("(?i)\\s+(w/|feat\\.?|ft\\.?|prod\\.?|x(?=\\s)).*"), "").trim()

        // 5. Génération des requêtes (de la plus stricte/parfaite à la plus large)
        
        // A. La perfection (Ex: "giving girls cocaine LiL PEEP")
        if (ultraCleanTitle.isNotBlank() && parsedArtist.isNotBlank()) queries.add("$ultraCleanTitle $parsedArtist")
        if (ultraCleanTitle.isNotBlank() && cleanArtist.isNotBlank() && cleanArtist != parsedArtist) queries.add("$ultraCleanTitle $cleanArtist")
        
        // B. Le titre nettoyé mais avec les featurings + artiste
        if (parsedTitle.isNotBlank() && parsedArtist.isNotBlank()) queries.add("$parsedTitle $parsedArtist")
        
        // C. Juste le titre pur (Sans artiste, si la chanson est très connue)
        if (ultraCleanTitle.isNotBlank()) queries.add(ultraCleanTitle)
        
        // D. Juste le titre avec featurings
        if (parsedTitle.isNotBlank()) queries.add(parsedTitle)

        // E. En tout dernier recours, la chaîne brute
        queries.add(cleanTitle)

        return queries.filter { it.length > 2 }.toList()
    }

    private fun loadLyrics(track: Track) {
        lyricsJob?.cancel()
        lyricsLines.clear()
        lyricsOffset = 0L
        showLyricsOffsetControls = false
        isLyricsLoading = true
        isSearchingLyrics = false
        rawPlainLyrics = null

        val trackDurationMs = track.durationMs ?: 0L
        val trackDurationSec = trackDurationMs / 1000.0

        val queries = generateSearchQueries(track.title ?: "", track.user?.username ?: "")
        manualSearchQuery = queries.firstOrNull() ?: ""

        lyricsJob = viewModelScope.launch(Dispatchers.IO) {
            val preferLocal = playerPrefs.getLyricsPreferLocal()
            if (preferLocal) {
                val localTrack = DownloadManager.getLocalTrack(track.id)
                if (localTrack != null && localTrack.localAudioPath.isNotEmpty()) {
                    val rawLyrics = LyricsUtils.extractLocalLyrics(localTrack.localAudioPath)
                    if (!rawLyrics.isNullOrBlank()) {
                        val parsed = LyricsUtils.parseLyricsContent(rawLyrics, trackDurationMs)
                        withContext(Dispatchers.Main) {
                            lyricsLines.addAll(parsed.ifEmpty { listOf(LyricLine(rawLyrics, 0, trackDurationMs)) })
                            lyricsMode = LyricsMode.SYNCED
                            isLyricsLoading = false
                        }
                        return@launch
                    }
                }
            }

            var bestMxmLineSync: Pair<List<LyricLine>, String?>? = null
            var bestLrcLineSync: LrcLibResponse? = null

            var finalLines = emptyList<LyricLine>()
            var finalPlain: String? = null

            val preferLrcLib = lyricsProvider == LyricsProvider.OPEN_SOURCE

            for (query in queries) {
                if (!isActive) return@launch

                if (preferLrcLib) {
                    val lrcAll = try { LrcLibClient.api.searchLyrics(query) } catch (e: Exception) { emptyList() }
                    val lrcFiltered = lrcAll.filter { kotlin.math.abs(it.duration - trackDurationSec) < 15.0 }
                    val topLrc = if (lrcFiltered.isNotEmpty()) lrcFiltered.take(3) else lrcAll.take(1)
                    val lrcMatch = topLrc.maxByOrNull { if (!it.syncedLyrics.isNullOrEmpty()) 1 else 0 }

                    val lrcLines = lrcMatch?.syncedLyrics?.let { LyricsUtils.parseLyricsContent(it, trackDurationMs) } ?: emptyList()
                    val lrcPlain = lrcMatch?.plainLyrics

                    if (lrcLines.isNotEmpty() || !lrcPlain.isNullOrBlank()) {
                        finalLines = lrcLines
                        finalPlain = lrcPlain
                        break
                    }
                } else {
                    val mxmAll = try { MusixmatchClient.search(query) } catch (e: Exception) { emptyList() }
                    val mxmFiltered = mxmAll.filter { kotlin.math.abs(it.trackLength - trackDurationSec) < 15.0 }
                    val topMxm = if (mxmFiltered.isNotEmpty()) mxmFiltered.take(3) else mxmAll.take(1)
                    val mxmMatch = topMxm.maxByOrNull { it.hasRichSync * 2 + it.hasSubtitles }

                    val targetLang = if (playerPrefs.getLyricsTranslationEnabled()) playerPrefs.getLyricsTranslationLang() else null
                    val mxmData = mxmMatch?.let { MusixmatchClient.getLyricsData(it.trackId, trackDurationMs, targetLang, isRomanizationEnabled) }
                    
                    if (mxmData != null && mxmData.first.any { it.words.isNotEmpty() }) {
                        finalLines = mxmData.first; finalPlain = mxmData.second
                        break
                    }
                    if (mxmData != null && mxmData.first.size > 1 && bestMxmLineSync == null) bestMxmLineSync = mxmData

                    val lrcAll = try { LrcLibClient.api.searchLyrics(query) } catch (e: Exception) { emptyList() }
                    val lrcFiltered = lrcAll.filter { kotlin.math.abs(it.duration - trackDurationSec) < 15.0 }
                    val topLrc = if (lrcFiltered.isNotEmpty()) lrcFiltered.take(3) else lrcAll.take(1)
                    val lrcMatch = topLrc.maxByOrNull { if (!it.syncedLyrics.isNullOrEmpty()) 1 else 0 }

                    val lrcLines = lrcMatch?.syncedLyrics?.let { LyricsUtils.parseLyricsContent(it, trackDurationMs) } ?: emptyList()
                    val lrcPlain = lrcMatch?.plainLyrics

                    if (lrcLines.any { it.words.isNotEmpty() }) {
                        finalLines = lrcLines; finalPlain = lrcPlain
                        break
                    }
                    if (lrcLines.size > 1 && bestLrcLineSync == null) bestLrcLineSync = lrcMatch

                    val mxmWordSync = mxmData != null && mxmData.first.any { it.words.isNotEmpty() }
                    val lrcWordSync = lrcLines.any { it.words.isNotEmpty() }
                    val mxmLineSync = mxmData != null && mxmData.first.size > 1
                    val lrcLineSync = lrcLines.size > 1
                    val hasMxmPlain = !mxmData?.second.isNullOrBlank()
                    val hasLrcPlain = !lrcPlain.isNullOrBlank()

                    if (mxmWordSync || lrcWordSync || mxmLineSync || lrcLineSync || hasMxmPlain || hasLrcPlain) {
                        when {
                            mxmWordSync -> { finalLines = mxmData!!.first; finalPlain = mxmData.second }
                            lrcWordSync -> { finalLines = lrcLines; finalPlain = lrcPlain }
                            mxmLineSync -> { finalLines = mxmData!!.first; finalPlain = mxmData.second }
                            lrcLineSync -> { finalLines = lrcLines; finalPlain = lrcPlain }
                            hasMxmPlain -> { finalPlain = mxmData!!.second }
                            hasLrcPlain -> { finalPlain = lrcPlain }
                        }
                        break
                    }
                }
            }

            if (finalLines.isEmpty() && finalPlain.isNullOrBlank()) {
                for (query in queries) {
                    if (!isActive) break
                    val lrcPlain = try { LrcLibClient.api.searchLyrics(query).firstOrNull { !it.plainLyrics.isNullOrBlank() }?.plainLyrics } catch(e:Exception){null}
                    if (lrcPlain != null) { finalPlain = lrcPlain; break }
                }
            }

            if (finalLines.isNotEmpty() && (preferLrcLib || (finalLines.firstOrNull()?.translation == null && finalLines.firstOrNull()?.romanization == null))) {
                val targetLang = if (playerPrefs.getLyricsTranslationEnabled()) playerPrefs.getLyricsTranslationLang() else null
                val wantsRomanization = isRomanizationEnabled

                if (targetLang != null || wantsRomanization) {
                    val originalTexts = finalLines.map { it.text }.filter { it.isNotBlank() }.distinct()
                    val translations = if (targetLang != null) com.alananasss.kittytune.data.network.FreeTranslator.translateMissing(originalTexts, targetLang) else emptyMap()
                    val romanizations = if (wantsRomanization) com.alananasss.kittytune.data.network.FreeTranslator.getRomanization(originalTexts) else emptyMap()

                    finalLines = finalLines.map { line ->
                        line.copy(
                            translation = line.translation ?: translations[line.text.trim()],
                            romanization = line.romanization ?: romanizations[line.text.trim()]
                        )
                    }
                }
            }

            withContext(Dispatchers.Main) {
                lyricsLines.addAll(finalLines)
                rawPlainLyrics = finalPlain
                lyricsMode = if (finalLines.isNotEmpty()) LyricsMode.SYNCED else LyricsMode.PLAIN
                isLyricsLoading = false
                if (finalLines.isNotEmpty() || !rawPlainLyrics.isNullOrBlank()) isSearchingLyrics = false
            }
        }
    }

    fun reloadLyrics() {
        currentTrack?.let { loadLyrics(it) }
    }

    fun adjustLyricsOffset(amount: Long) {
        lyricsOffset += amount
    }

    fun loadCustomLyrics(content: String) {
        viewModelScope.launch {
            val trackDuration = currentTrack?.durationMs ?: 0L
            val resultLines = LyricsUtils.parseLyricsContent(content, trackDuration)
            withContext(Dispatchers.Main) {
                lyricsLines.clear()
                lyricsLines.addAll(resultLines)
                rawPlainLyrics = if (resultLines.isNotEmpty()) {
                    resultLines.joinToString("\n") { it.text }
                } else {
                    content
                }
                lyricsMode = if (resultLines.isNotEmpty()) LyricsMode.SYNCED else LyricsMode.PLAIN
                isSearchingLyrics = false
                isLyricsLoading = false
            }
        }
    }

    private suspend fun processLyricsResponse(response: LrcLibResponse?, trackDuration: Long) {
        val lyricsText = response?.lyricsfile ?: response?.syncedLyrics
        val resultLines = when {
            response == null -> emptyList()
            !lyricsText.isNullOrEmpty() -> LyricsUtils.parseLyricsContent(lyricsText, trackDuration)

            else -> emptyList()
        }

        withContext(Dispatchers.Main) {
            lyricsLines.clear()
            lyricsLines.addAll(resultLines)

            rawPlainLyrics = response?.plainLyrics ?: if (resultLines.isNotEmpty()) {
                resultLines.joinToString("\n") { it.text }
            } else {
                response?.syncedLyrics
            }

            lyricsMode = if (resultLines.isNotEmpty()) {
                LyricsMode.SYNCED
            } else {
                LyricsMode.PLAIN
            }

            isLyricsLoading = false
            if (resultLines.isNotEmpty() || !rawPlainLyrics.isNullOrBlank()) isSearchingLyrics = false
        }
    }

    fun searchLyricsManual(query: String, provider: String = manualSearchProvider) {
        if (query.isBlank()) return
        isLyricsLoading = true
        unifiedLyricSearchResults.clear()
        manualSearchProvider = provider
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (provider == "LRCLIB") {
                    val results = LrcLibClient.api.searchLyrics(query)
                    val mapped = results.map {
                        UnifiedLyricResult(it.id.toString(), it.name, it.artistName, it.albumName, it.duration, !it.syncedLyrics.isNullOrEmpty(), false, "LRCLIB")
                    }
                    withContext(Dispatchers.Main) { unifiedLyricSearchResults.addAll(mapped) }
                } else {
                    val results = MusixmatchClient.search(query)
                    val mapped = results.map {
                        UnifiedLyricResult(it.trackId.toString(), it.trackName, it.artistName, it.albumName, it.trackLength.toDouble(), it.hasSubtitles == 1, it.hasRichSync == 1, "MUSIXMATCH")
                    }
                    withContext(Dispatchers.Main) { unifiedLyricSearchResults.addAll(mapped) }
                }
            } catch (e: Exception) { e.printStackTrace() }
            finally { withContext(Dispatchers.Main) { isLyricsLoading = false } }
        }
    }

    fun selectUnifiedLyricResult(result: UnifiedLyricResult) {
        viewModelScope.launch(Dispatchers.IO) {
            isLyricsLoading = true
            var finalLines = emptyList<LyricLine>()
            var finalPlain: String? = null

            if (result.provider == "LRCLIB") {
                try {
                    val lrcData = LrcLibClient.api.searchLyrics(result.name + " " + result.artistName).find { it.id.toString() == result.id }
                    val lyricsText = lrcData?.lyricsfile ?: lrcData?.syncedLyrics
                    if (!lyricsText.isNullOrEmpty()) finalLines = LyricsUtils.parseLyricsContent(lyricsText, duration)
                    finalPlain = lrcData?.plainLyrics
                } catch (e: Exception) {}
            } else {
                val targetLang = if (playerPrefs.getLyricsTranslationEnabled()) playerPrefs.getLyricsTranslationLang() else null
                lastFetchedMxmTrackId = result.id.toLongOrNull()
                val data = MusixmatchClient.getLyricsData(result.id.toLong(), duration, targetLang, isRomanizationEnabled)
                finalLines = data.first
                finalPlain = data.second
            }

            withContext(Dispatchers.Main) {
                lyricsLines.clear()
                lyricsLines.addAll(finalLines)
                rawPlainLyrics = finalPlain
                lyricsMode = if (finalLines.isNotEmpty()) LyricsMode.SYNCED else LyricsMode.PLAIN
                isLyricsLoading = false
                isSearchingLyrics = false
            }
        }
    }

    fun selectLyricResult(result: LrcLibResponse) { viewModelScope.launch(Dispatchers.IO) { processLyricsResponse(result, duration) } }
    private fun cleanTitleNoise(title: String): String = title.replace(Regex("\\(.*?\\)|\\[.*?]"), "").replace(Regex("(?i)(official video|lyrics|ft\\.|feat\\.|prod\\.)"), "").trim()

    fun navigateToTrackDetails(trackId: Long, initialTab: Int = 0) { showMenuSheet = false; showDetailsSheet = false; navigateToPlaylistId = "track_detail:$trackId?tab=$initialTab" }

    fun shareTrack(track: Track) {
        val urlToShare = track.permalinkUrl ?: "https://soundcloud.com/tracks/${track.id}"
        // Desktop "share" = copy the link to the clipboard + toast (no Android share sheet).
        try {
            val selection = java.awt.datatransfer.StringSelection(urlToShare)
            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
            com.alananasss.kittytune.core.Toaster.show(str("copied_to_clipboard"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        showMenuSheet = false
    }

    fun openTrackDetails(targetTrack: Track? = null) {
        val target = targetTrack ?: selectedTrackForSheet ?: trackForMenu ?: currentTrack ?: return
        selectedTrackForSheet = target

        if (target.id < 0) {
            activateLocalDetailsMode(target)
            return
        }

        viewModelScope.launch {
            val localTrack = DownloadManager.getLocalTrack(target.id)
            val isDownloaded = localTrack != null && localTrack.localAudioPath.isNotEmpty()

            if (isDownloaded) {
                activateLocalDetailsMode(target)
            } else {
                var isContextLocal = false
                if (menuContextPlaylistId != null) {
                    if (menuContextPlaylistId == -2L || menuContextPlaylistId!! < 0) {
                        isContextLocal = true
                    }
                } else if (target.id == currentTrack?.id) {
                    val navId = currentContext?.navigationId
                    if (navId == "downloads" || navId?.startsWith("local_playlist:") == true) {
                        isContextLocal = true
                    }
                }

                if (isContextLocal) {
                    activateLocalDetailsMode(target)
                } else {
                    isLocalDetailsMode = false
                    localFilePathForDetails = null
                    showMenuSheet = false
                    showDetailsSheet = true

                    if (target.source == "soundcloud" && target.id > 0 && (target.user?.id == 0L || target.playbackCount == 0)) {
                        try {
                            val fullTracks = api.getTracksByIds(target.id.toString())
                            val fullTrack = fullTracks.firstOrNull()
                            if (fullTrack != null) {
                                selectedTrackForSheet = fullTrack
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    private fun activateLocalDetailsMode(target: Track) {
        isLocalDetailsMode = true
        viewModelScope.launch {
            val localTrack = DownloadManager.getLocalTrack(target.id)
            val prefix = str("prefix_local_file_marker")
            localFilePathForDetails = localTrack?.localAudioPath ?: target.description?.removePrefix(prefix)
            showMenuSheet = false
            showDetailsSheet = true
        }
    }

    fun openComments(targetTrack: Track? = null) {
        val target = targetTrack ?: selectedTrackForSheet ?: trackForMenu ?: currentTrack ?: return
        selectedTrackForSheet = target
        showMenuSheet = false
        showDetailsSheet = false
        showCommentsSheet = true
        if (currentUserId == 0L || currentUser == null) fetchUserProfile()
        loadComments(true, target)
    }

    fun onCommentSortChanged(sort: CommentSort) {
        if (commentSort == sort) return
        commentSort = sort
        loadComments(refresh = true)
    }

    fun navigateToExpandedQueue() {
        showMenuSheet = false
        showDetailsSheet = false
        showCommentsSheet = false
        isPlayerExpanded = false
        navigateToPlaylistId = "expanded_queue"
    }

    fun resolveAndNavigateToArtist(username: String, artistId: Long? = null) {
        showDetailsSheet = false
        showMenuSheet = false
        showCommentsSheet = false
        isPlayerExpanded = false
        
        if (artistId != null && artistId > 0) {
            navigateToPlaylistId = "profile:$artistId"
            return
        }

        val cleanName = username.replace("@", "")
            .replace(Regex("[\\p{C}\\p{Zl}\\p{Zp}]"), "")
            .trim()
            
        if (cleanName.isBlank()) return
        
        viewModelScope.launch {
            try {
                val resolvedObject = api.resolveUrl("https://soundcloud.com/$cleanName")
                val user = gson.fromJson(resolvedObject, User::class.java)
                if (user.id > 0) {
                    navigateToPlaylistId = "profile:${user.id}"
                }
            } catch (_: Exception) {
                emitUiEvent(str("error_generic"))
            }
        }
    }

    fun navigateToTag(tagName: String) { showDetailsSheet = false; isPlayerExpanded = false; navigateToPlaylistId = "tag:$tagName" }

    fun navigateToArtist(userId: Long) {
        if (userId <= 0) return
        showDetailsSheet = false
        showMenuSheet = false
        showCommentsSheet = false
        isPlayerExpanded = false
        navigateToPlaylistId = "profile:$userId"
    }

    fun navigateToContext() {
        currentContext?.let { context ->
            var destination = context.navigationId
            if (destination.startsWith("playlist_detail:")) {
                destination = destination.removePrefix("playlist_detail:")
            }
            else if (destination.startsWith("playlist_")) {
                destination = destination.removePrefix("playlist_")
            }
            navigateToPlaylistId = destination
        }
    }

    fun onNavigationHandled() { navigateToPlaylistId = null }

    fun loadSocialProof(specificTrack: Track? = null) {
        val t = specificTrack ?: selectedTrackForSheet ?: trackForMenu ?: currentTrack ?: return
        val trackId = t.id
        if (trackId <= 0) {
            socialLikerUser = null
            return
        }
        if (socialProofTrackId == trackId) return
        socialProofTrackId = trackId
        viewModelScope.launch {
            try {
                isSocialLikerLoading = true
                socialLikerUser = null
                val trackUrn = "soundcloud:tracks:$trackId"
                val request = com.alananasss.kittytune.data.network.RelatedLikersGraphQl.request(
                    trackUris = listOf(trackUrn)
                )
                val response = com.alananasss.kittytune.data.network.RetrofitClient.create()
                    .getRelatedLikersGraphQL(request)
                val me = com.alananasss.kittytune.data.network.RetrofitClient.create().getMe()
                val myUrn = "soundcloud:users:${me.id}"
                val users = response.data?.allTracks
                    ?.flatMap { it.relatedLikers?.users.orEmpty() }
                    ?.filter { it.urn != myUrn }
                    .orEmpty()
                if (users.isNotEmpty()) {
                    val u = users.first()
                    val userId = u.urn?.substringAfterLast(':')?.toLongOrNull() ?: 0L
                    if (userId > 0) {
                        socialLikerUser = User(
                            id = userId,
                            username = u.username,
                            avatarUrl = u.avatarUrl,
                            verified = u.verified ?: false,
                            urn = u.urn
                        )
                    }
                } else {
                    socialLikerUser = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                socialLikerUser = null
            } finally {
                isSocialLikerLoading = false
            }
        }
    }

    fun loadComments(refresh: Boolean = false, specificTrack: Track? = null) {
        val t = specificTrack ?: selectedTrackForSheet ?: trackForMenu ?: currentTrack ?: return
        if (refresh) { commentsList.clear(); commentNextHref = null }
        if (!refresh && commentNextHref == null && commentsList.isNotEmpty()) return

        viewModelScope.launch {
            if (refresh) isCommentsLoading = true
            try {
                val response = if (refresh) {
                    api.getTrackComments(trackId = t.id, threaded = 1, filterReplies = 1, sort = commentSort.value)
                } else {
                    api.getCommentsNextPage(commentNextHref!!)
                }
                commentNextHref = response.next_href
                val newComments = response.collection.filter { c -> commentsList.none { it.id == c.id } }
                commentsList.addAll(newComments)
                checkCommentLikesStatus(t.id, newComments)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isCommentsLoading = false
            }
        }
    }

    private suspend fun checkCommentLikesStatus(trackId: Long, comments: List<Comment>) {
        if (comments.isEmpty()) return
        val targetUrns = mutableListOf<String>()
        comments.forEach { c ->
            targetUrns.add("soundcloud:comments:${c.id}")
            c.replies?.forEach { reply -> targetUrns.add("soundcloud:comments:${reply.id}") }
        }

        targetUrns.chunked(100).forEach { batchUrns ->
            val parentUrn = "soundcloud:tracks:$trackId"
            val query = "query UserInteractions(" + '$' + "parentUrn: String!, " + '$' + "interactionTypeUrn: String!, " + '$' + "targetUrns: [String!]!) { user: userInteractions(parentUrn: " + '$' + "parentUrn, interactionTypeUrn: " + '$' + "interactionTypeUrn, targetUrns: " + '$' + "targetUrns) { targetUrn, userInteraction, interactionCounts { count, interactionTypeValueUrn } } }"
            val variables = GraphQlVariablesUserCheck(parentUrn = parentUrn, targetUrns = batchUrns)
            val request = GraphQlRequest("UserInteractions", query, variables)
            try {
                val responseJson = api.postGraphQl("https://graph.soundcloud.com/graphql", request)
                val data = gson.fromJson(responseJson, GraphQlResponseUserInteractions::class.java)
                data.data?.user?.forEach { interaction ->
                    val idStr = interaction.targetUrn.substringAfterLast(":")
                    val commentId = idStr.toLongOrNull() ?: 0L
                    val isLikedByMe = interaction.userInteraction != null
                    val totalLikes = interaction.interactionCounts?.find { it.type == "sc:interactiontypevalue:like" }?.count
                    updateCommentInList(commentId, isLikedByMe, totalLikes)
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun updateCommentInList(commentId: Long, isLiked: Boolean, count: Int?) {
        val index = commentsList.indexOfFirst { it.id == commentId }
        if (index != -1) {
            val c = commentsList[index]
            commentsList[index] = c.copy(isLiked = isLiked, likesCount = count ?: c.likesCount)
            return
        }
        for (i in commentsList.indices) {
            val parent = commentsList[i]
            val replyIndex = parent.replies?.indexOfFirst { it.id == commentId } ?: -1
            if (replyIndex != -1) {
                val replies = parent.replies!!.toMutableList()
                val r = replies[replyIndex]
                replies[replyIndex] = r.copy(isLiked = isLiked, likesCount = count ?: r.likesCount)
                commentsList[i] = parent.copy(replies = replies)
                return
            }
        }
    }

    fun startReplying(comment: Comment) { replyingToComment = comment }
    fun cancelReplying() { replyingToComment = null }

    fun postComment(body: String, timestamp: Long?) {
        val t = selectedTrackForSheet ?: trackForMenu ?: currentTrack ?: return
        if (body.isBlank()) return
        pendingCommentBody = body
        pendingCommentTimestamp = timestamp
        viewModelScope.launch {
            isPostingComment = true
            try {
                val finalTimestamp = replyingToComment?.trackTimestamp ?: timestamp ?: currentPosition
                val parentId = replyingToComment?.id
                var newComment = api.postComment(t.id, body, finalTimestamp, parentId)
                if (newComment.user == null || newComment.user.username.isNullOrEmpty()) {
                    if (currentUser != null) newComment = newComment.copy(user = currentUser)
                }
                if (parentId != null) {
                    val parentIndex = commentsList.indexOfFirst { it.id == parentId }
                    if (parentIndex != -1) {
                        val parent = commentsList[parentIndex]
                        val updatedReplies = (parent.replies ?: emptyList()) + newComment
                        commentsList[parentIndex] = parent.copy(replies = updatedReplies)
                    } else commentsList.add(0, newComment)
                } else commentsList.add(0, newComment)
                emitUiEvent(str("success_generic"))
                pendingCommentBody = null; pendingCommentTimestamp = null; replyingToComment = null
            } catch (e: Exception) {
                e.printStackTrace()
                if (e.toString().contains("403") || e.toString().contains("401")) {
                    captchaUrl = t.permalinkUrl ?: "https://soundcloud.com/tracks/${t.id}"
                    emitUiEvent(str("error_security_check"))
                } else emitUiEvent(str("error_generic"))
            } finally { isPostingComment = false }
        }
    }

    fun onCaptchaSolved() {
        captchaUrl = null; SessionManager.requestSessionRefresh(force = true)
        if (pendingCommentBody != null) { emitUiEvent(str("msg_retrying")); postComment(pendingCommentBody!!, pendingCommentTimestamp) }
    }

    fun toggleCommentLike(comment: Comment) {
        val foundIndex = commentsList.indexOfFirst { it.id == comment.id }
        var parentIndex = -1
        if (foundIndex == -1) { for (i in commentsList.indices) { if (commentsList[i].replies?.any { it.id == comment.id } == true) { parentIndex = i; break } } }
        if (foundIndex == -1 && parentIndex == -1) return
        val isCurrentlyLiked = comment.isLiked
        val newLikedState = !isCurrentlyLiked
        val newCount = if (newLikedState) comment.likesCount + 1 else (comment.likesCount - 1).coerceAtLeast(0)
        if (foundIndex != -1) commentsList[foundIndex] = comment.copy(isLiked = newLikedState, likesCount = newCount)
        else {
            val parent = commentsList[parentIndex]
            val replies = parent.replies!!.toMutableList()
            val rIndex = replies.indexOfFirst { it.id == comment.id }
            replies[rIndex] = replies[rIndex].copy(isLiked = newLikedState, likesCount = newCount)
            commentsList[parentIndex] = parent.copy(replies = replies)
        }
        viewModelScope.launch {
            try {
                val parentUrn = "soundcloud:tracks:${selectedTrackForSheet?.id ?: currentTrack?.id}"
                val targetUrn = "soundcloud:comments:${comment.id}"
                val input = InteractionInput(parentUrn, targetUrn)
                if (newLikedState) {
                    val query = "mutation UpsertInteraction(" + '$' + "input: InteractionInput!) { upsertInteraction(input: " + '$' + "input) { interactionTypeUrn } }"
                    val request = GraphQlRequest("UpsertInteraction", query, GraphQlVariablesInteraction(input))
                    api.postGraphQl("https://graph.soundcloud.com/graphql", request)
                } else {
                    val query = "mutation RemoveInteraction(" + '$' + "input: InteractionInput!) { removeInteraction(input: " + '$' + "input) }"
                    val request = GraphQlRequest("RemoveInteraction", query, GraphQlVariablesInteraction(input))
                    api.postGraphQl("https://graph.soundcloud.com/graphql", request)
                }
            } catch (e: Exception) { e.printStackTrace(); emitUiEvent(str("error_generic")) }
        }
    }

    fun deleteComment(comment: Comment) {
        val index = commentsList.indexOfFirst { it.id == comment.id }
        if (index != -1) commentsList.removeAt(index)
        else {
            for (i in commentsList.indices) {
                if (commentsList[i].replies?.any { it.id == comment.id } == true) {
                    val parent = commentsList[i]
                    val newReplies = parent.replies!!.filter { it.id != comment.id }
                    commentsList[i] = parent.copy(replies = newReplies)
                    break
                }
            }
        }
        viewModelScope.launch {
            try {
                val response = api.deleteComment(comment.id)
                if (response.isSuccessful) emitUiEvent(str("success_generic")) else emitUiEvent(str("error_generic"))
            } catch (e: Exception) { e.printStackTrace(); emitUiEvent(str("error_generic")) }
        }
    }

    fun fetchUserProfile() {
        if (tokenManager.isGuestMode() || tokenManager.getAccessToken().isNullOrEmpty()) return

        viewModelScope.launch {
            try {
                val me = api.getMe()
                currentUserId = me.id
                currentUser = me
                com.alananasss.kittytune.data.RepostRepository.refreshReposts()
            } catch (_: Exception) {}
        }
    }

    fun startRadioFromTrack(track: Track) {
        showMenuSheet = false
        navigateToPlaylistId = "station:${track.id}"
    }

    fun startYoutubeRadio(track: Track) {
        showMenuSheet = false
        track.permalinkUrl?.let {
            navigateToPlaylistId = "yt_radio:${URLEncoder.encode(it, "UTF-8")}"
        }
    }

    fun playPlaylist(tracks: List<Track>, startIndex: Int = 0, context: PlaybackContext? = null, maintainPlayerState: Boolean = false) {
        if (tracks.isEmpty()) return
        if (!maintainPlayerState) {
            isPlayerExpanded = false
        }
        _originalQueue.clear(); _originalQueue.addAll(tracks)
        _queue.clear()
        this.currentContext = context
        MusicManager.updateContext(context)

        val effectiveStartIndex = if (startIndex in tracks.indices) startIndex else 0

        if (shuffleEnabled) {
            val clickedTrack = tracks[effectiveStartIndex]
            val rest =
                tracks.filterIndexed { index, _ -> index != effectiveStartIndex }.shuffled()
            _queue.add(clickedTrack)
            _queue.addAll(rest)
            playTrackAtIndex(0, addToHistory = (context == null))
        } else {
            _queue.addAll(tracks)
            playTrackAtIndex(effectiveStartIndex, addToHistory = (context == null))
        }

        updateQueueState(); saveStateAsync(saveQueue = true)

        if (context != null) {
            val isStation =
                context.navigationId.contains("station") || context.navigationId.contains("yt_radio")
            val isProfile = context.navigationId.contains("profile")
            val idLong = when (context.navigationId) {
                "likes" -> -1L
                "downloads" -> -2L
                else -> context.navigationId.substringAfter(":").toLongOrNull() ?: 0L
            }
            val cleanTitle = context.displayText.substringAfter("â€¢").trim()

            val playlistCreator = if (context.artistName != null) User(
                0,
                context.artistName,
                null,
                verified = context.isVerified
            ) else null
            val safePermalink =
                if (context.navigationId.startsWith("yt_radio:")) context.navigationId else null
            val historyPlaylist = Playlist(
                id = idLong,
                title = cleanTitle,
                artworkUrl = context.imageUrl,
                calculatedArtworkUrl = null,
                trackCount = tracks.size,
                user = playlistCreator,
                tracks = null,
                permalinkUrl = safePermalink
            )

            HistoryRepository.addToHistory(historyPlaylist, isStation, isProfile)
        }
    }

    fun playTrackAtPosition(track: Track, position: Long) { pendingSeekPosition = position; playPlaylist(listOf(track), 0); showCommentsSheet = false; isPlayerExpanded = true }
    fun skipToQueueItem(index: Int) { playTrackAtIndex(index, addToHistory = false) }

    private fun playTrackAtIndex(index: Int, addToHistory: Boolean = true, isCrossfade: Boolean = false) {
        if (index < 0 || index >= _queue.size) { currentContext = null; return }
        currentQueueIndex = index
        val trackToPlay = _queue[index]
        
        // Stop current music audio & update UI playback states IMMEDIATELY on skip
        if (!isCrossfade) {
            MusicManager.stop()
            isPlaying = false
        }
        
        isLoading = true; duration = trackToPlay.durationMs ?: 0L; currentPosition = 0L
        currentSessionListenMs = 0L
        hasPushedRecentlyPlayed = false
        currentTrack = trackToPlay; MusicManager.currentTrack = trackToPlay
        // (Android media-notification refresh — no service on desktop)

        // Start playback loading immediately
        playRobustly(index, autoPlay = true, isCrossfade = isCrossfade)

        trackInitJob?.cancel()
        trackInitJob = viewModelScope.launch {
            var finalTrack = trackToPlay
            if (finalTrack.source == "soundcloud" && trackToPlay.id > 0 && (trackToPlay.user?.id == 0L || trackToPlay.media == null || trackToPlay.playbackCount == 0)) {
                try {
                    val fullTrackList = api.getTracksByIds(trackToPlay.id.toString())
                    if (fullTrackList.isNotEmpty()) { 
                        finalTrack = fullTrackList[0]
                        val qIndex = _queue.indexOfFirst { it.id == trackToPlay.id }
                        if (qIndex != -1) _queue[qIndex] = finalTrack 
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
            currentTrack = finalTrack
            MusicManager.currentTrack = finalTrack
            isLiked = LikeRepository.isTrackLiked(finalTrack.id)
            loadLyrics(finalTrack)
            loadSocialProof(finalTrack)
            saveStateAsync(saveQueue = false)

            if (addToHistory && currentContext?.navigationId?.startsWith("station:") != true && currentContext?.navigationId?.startsWith("yt_radio:") != true) {
                HistoryRepository.addToHistory(finalTrack)
            }
        }
    }

    fun playNext(manual: Boolean = true, isCrossfade: Boolean = false, ignoreRepeatOne: Boolean = false) {
        if (isAutoplayRadioLoading) return

        // Record skip stats
        if (manual) {
            currentTrack?.let { track ->
                if (playerPrefs.getListeningStatsEnabled() && currentSessionListenMs > 0) {
                    ListeningStatsRepository.recordEvent(track, "SKIP_NEXT", currentSessionListenMs)
                }
            }
        }

        if (!manual && !ignoreRepeatOne && repeatMode == RepeatMode.ONE) {
            currentTrack?.let { track ->
                if (playerPrefs.getListeningStatsEnabled() && currentSessionListenMs > 0) {
                    ListeningStatsRepository.recordEvent(track, "REPEAT_ONE_LOOP", currentSessionListenMs)
                }
            }
            currentSessionListenMs = 0L
            playTrackAtIndex(currentQueueIndex, addToHistory = false, isCrossfade = isCrossfade)
            return
        }

        if (!manual && !playerPrefs.getContinuousPlaybackEnabled()) {
            MusicManager.player.pause()
            MusicManager.player.seekTo(0)
            saveStateAsync()
            return
        }


        val nextIndex = currentQueueIndex + 1

        if (nextIndex < _queue.size) {
            playTrackAtIndex(nextIndex, addToHistory = false, isCrossfade = isCrossfade)
        } else {
            if (repeatMode == RepeatMode.ALL) {
                playTrackAtIndex(0, addToHistory = false, isCrossfade = isCrossfade)
            } else {
                val autoPlayEnabled = playerPrefs.getAutoplayEnabled()
                val isYoutube = currentTrack?.source == "youtube"

                if (autoPlayEnabled || isYoutube) {
                    viewModelScope.launch {
                        val youtubeFallback = playerPrefs.getYouTubeFallbackEnabled()

                        if (isYoutube || (currentTrack?.source == "soundcloud" && youtubeFallback)) {
                            fetchAndPlayYoutubeRadio()
                        } else {
                            fetchAndQueueRadio()
                        }

                        val newNextIndex = currentQueueIndex + 1
                        if (newNextIndex < _queue.size) {
                            playTrackAtIndex(newNextIndex, addToHistory = false, isCrossfade = isCrossfade)
                        } else {
                            MusicManager.player.pause()
                            MusicManager.player.seekTo(0)
                            saveStateAsync()
                        }
                    }
                } else {
                    MusicManager.player.pause()
                    MusicManager.player.seekTo(0)
                }
            }
        }
    }

    private suspend fun fetchAndPlayYoutubeRadio() {
        val lastTrack = currentTrack ?: return
        isAutoplayRadioLoading = true
        try {
            val videoId = lastTrack.permalinkUrl?.substringAfter("v=")?.substringBefore("&") ?: return
            val radioUrl = "https://www.youtube.com/watch?v=$videoId&list=RD$videoId"

            withContext(Dispatchers.Main) {
                val ctx = PlaybackContext(
                    displayText = "YouTube Mix â€¢ ${lastTrack.title}",
                    navigationId = "yt_radio:${URLEncoder.encode(lastTrack.permalinkUrl, "UTF-8")}",
                    imageUrl = lastTrack.fullResArtwork,
                    artistName = lastTrack.user?.username,
                    isVerified = false
                )
                currentContext = ctx
                MusicManager.updateContext(ctx)
                saveStateAsync(saveQueue = false)
            }

            val youtubeService = ServiceList.YouTube
            val extractor = youtubeService.getPlaylistExtractor(radioUrl)

            withContext(Dispatchers.IO) {
                extractor.fetchPage()
            }

            val streamItems = extractor.initialPage.items.filterIsInstance<StreamInfoItem>()
            val radioTracks = streamItems.map {
                Track(
                    id = abs(it.url.hashCode().toLong()),
                    title = it.name,
                    user = User(
                        id = it.uploaderUrl?.hashCode()?.toLong() ?: 0L,
                        username = it.uploaderName,
                        avatarUrl = it.uploaderAvatars.firstOrNull()?.url
                    ),
                    artworkUrl = it.thumbnails.firstOrNull()?.url,
                    durationMs = it.duration * 1000,
                    permalinkUrl = it.url,
                    source = "youtube"
                )
            }

            if (radioTracks.isNotEmpty()) {
                val newTracks = radioTracks.filter { track -> _queue.none { it.id == track.id } }

                _queue.addAll(newTracks)
                _originalQueue.addAll(newTracks)
                updateQueueState()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isAutoplayRadioLoading = false
        }
    }
    private suspend fun fetchAndQueueRadio() {
        val lastTrack = currentTrack ?: return
        isAutoplayRadioLoading = true
        try {
            val station = api.getTrackStation(lastTrack.id)
            val partialTracks = station.tracks
            if (!partialTracks.isNullOrEmpty()) {
                val newTrackIds = partialTracks.map { it.id }.filter { trackId -> _queue.none { it.id == trackId } }
                if (newTrackIds.isNotEmpty()) {
                    val unorderedFullTracks = api.getTracksByIds(newTrackIds.joinToString(","))
                    val trackMap = unorderedFullTracks.associateBy { it.id }
                    val orderedFullTracks = newTrackIds.mapNotNull { id -> trackMap[id] }
                    _queue.addAll(orderedFullTracks); _originalQueue.addAll(orderedFullTracks); updateQueueState()
                }
                if (currentContext == null) {
                    val ctx = PlaybackContext(str("context_station", lastTrack.title ?: ""), "station:${lastTrack.id}", lastTrack.fullResArtwork)
                    currentContext = ctx
                    MusicManager.updateContext(ctx)
                }
            }
        } catch (_: Exception) { } finally { isAutoplayRadioLoading = false }
    }

    fun smartPrevious(isCrossfade: Boolean = false) {
        if (player.currentPosition > 3000) {
            // User is restarting the same track â€” this is a manual replay
            currentTrack?.let { track ->
                if (playerPrefs.getListeningStatsEnabled() && currentSessionListenMs > 0) {
                    ListeningStatsRepository.recordEvent(track, "MANUAL_REPLAY", currentSessionListenMs)
                }
            }
            currentSessionListenMs = 0L
            currentPosition = 0L
            player.seekTo(0)
        } else {
            // User is going to previous track
            currentTrack?.let { track ->
                if (playerPrefs.getListeningStatsEnabled() && currentSessionListenMs > 0) {
                    ListeningStatsRepository.recordEvent(track, "SKIP_PREVIOUS", currentSessionListenMs)
                }
            }
            val prev = currentQueueIndex - 1
            if (prev >= 0) {
                playTrackAtIndex(prev, addToHistory = false, isCrossfade = isCrossfade)
            } else {
                currentPosition = 0L
                player.seekTo(0)
            }
        }
    }
    fun toggleShuffle() { shuffleEnabled = !shuffleEnabled; if (shuffleEnabled) applyShuffle() else revertShuffle(); updateQueueState(); saveStateAsync(saveQueue = true) }

    private fun applyShuffle(startIndex: Int = currentQueueIndex, sourceList: List<Track> = _originalQueue) {
        if (sourceList.isEmpty() || startIndex !in sourceList.indices) return

        val played = sourceList.subList(0, startIndex + 1)
        val upcoming = if (startIndex + 1 < sourceList.size) sourceList.subList(startIndex + 1, sourceList.size) else emptyList()

        val shuffledUpcoming = upcoming.shuffled()

        _queue.clear()
        _queue.addAll(played)
        _queue.addAll(shuffledUpcoming)
    }

    private fun revertShuffle() { val currentTrackId = currentTrack?.id ?: return; _queue.clear(); _queue.addAll(_originalQueue); currentQueueIndex = _queue.indexOfFirst { it.id == currentTrackId }.coerceAtLeast(0) }

    private fun applyRepeatMode() {
        val exoMode = when (repeatMode) {
            RepeatMode.NONE -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
        MusicManager.player.repeatMode = exoMode
    }

    fun toggleRepeatMode() {
        repeatMode = when (repeatMode) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.NONE
        }
        applyRepeatMode()
        saveStateAsync(saveQueue = false)
    }

    fun syncQueueFromPlayer() {
        // Disabled: KittyTune uses a custom QueueManager approach (like SoundCloud).
        // ExoPlayer only holds the current track, so its timeline does not represent the full queue.
    }

    fun updateQueueState() { queueState = _queue.toList() }

    fun moveQueueItem(from: Int, to: Int) {
        if (from == to) return

        if (from < queueState.size && to < queueState.size) {
            val mut = queueState.toMutableList()
            val item = mut.removeAt(from)
            mut.add(to, item)
            queueState = mut
        }

        if (from < _queue.size && to < _queue.size) {
            val item = _queue.removeAt(from)
            _queue.add(to, item)
        }

        if (!shuffleEnabled && from < _originalQueue.size && to < _originalQueue.size + 1) {
            val originalItem = _originalQueue.removeAt(from)
            _originalQueue.add(to, originalItem)
        }

        if (currentQueueIndex == from) {
            currentQueueIndex = to
        } else if (from < currentQueueIndex && to >= currentQueueIndex) {
            currentQueueIndex--
        } else if (from > currentQueueIndex && to <= currentQueueIndex) {
            currentQueueIndex++
        }

        if (MusicManager.player.mediaItemCount > 1) {
            try { MusicManager.player.removeMediaItem(1) } catch (_: Exception) {}
        }
        preloadNextTrack(currentQueueIndex + 1)

        saveStateAsync(saveQueue = true)
    }

    fun removeTrackFromQueue(index: Int) {
        if (index !in _queue.indices) return

        val trackToRemove = _queue[index]

        // 1. Remove from _queue
        _queue.removeAt(index)

        // 2. Remove from queueState
        if (index < queueState.size) {
            val mut = queueState.toMutableList()
            mut.removeAt(index)
            queueState = mut
        }

        // 3. Remove from _originalQueue if present (matching by reference)
        val origIdx = _originalQueue.indexOfFirst { it === trackToRemove }
        if (origIdx != -1) _originalQueue.removeAt(origIdx)

        // 4. Update index if current track was moved
        if (index < currentQueueIndex) {
            currentQueueIndex--
        } else if (index == currentQueueIndex) {
            currentQueueIndex = currentQueueIndex.coerceAtMost((_queue.size - 1).coerceAtLeast(0))
        }

        // 5. Reset preloaded track in player
        if (MusicManager.player.mediaItemCount > 1) {
            try { MusicManager.player.removeMediaItem(1) } catch (_: Exception) {}
        }
        preloadNextTrack(currentQueueIndex + 1)

        saveStateAsync(saveQueue = true)
    }

    fun insertNext(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val insertIndex = currentQueueIndex + 1

        val uniqueTracks = tracks.map { it.copy() }

        _queue.addAll(insertIndex, uniqueTracks)
        _originalQueue.addAll(insertIndex, uniqueTracks)
        updateQueueState()
        
        if (MusicManager.player.mediaItemCount > 1) {
            try { MusicManager.player.removeMediaItem(1) } catch (_: Exception) {}
        }
        preloadNextTrack(currentQueueIndex + 1)

        saveStateAsync(saveQueue = true)
        emitUiEvent(str("menu_play_next"))
    }
    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
            saveStateAsync(savePositionOnly = true)
        } else {
            if (player.currentMediaItem == null && currentTrack != null) {
                pendingSeekPosition = currentPosition
                playPlaylist(
                    tracks = queueState.toList(),
                    startIndex = currentQueueIndex,
                    context = currentContext,
                    maintainPlayerState = true
                )
            } else {
                player.play()
            }
        }
    }
    fun seekTo(position: Long) {
        val target = position.coerceIn(0L, duration.coerceAtLeast(0L))
        isScrubbing = false
        seekTargetPosition = target
        lastSeekTimestamp = System.currentTimeMillis()
        currentPosition = target
        player.seekTo(target)
        saveStateAsync(savePositionOnly = true)
        updateDiscordPresence()
    }
    fun toggleLike() {
        val t = currentTrack ?: return
        isLiked = !isLiked

        if (isLiked) {
            LikeRepository.addLike(t)
        } else {
            LikeRepository.removeLike(t.id)
        }
    }

    fun togglePreciseSpeedEnabled(enabled: Boolean) { isPreciseSpeedEnabled = enabled; playerPrefs.setPreciseSpeedEnabled(enabled) }
    fun toggleRain() { val n = !effectsState.isRainEnabled; effectsState = effectsState.copy(isRainEnabled = n); applyEffectsAndSave() }
    fun setRainVolume(volume: Float) { effectsState = effectsState.copy(rainVolume = volume); MusicManager.applyEffects(effectsState); viewModelScope.launch(Dispatchers.IO) { playerPrefs.saveEffects(effectsState) } }
    fun setCustomSpeed(speed: Float) { val factor = if (isPreciseSpeedEnabled) 20f else 10f; val r = (speed * factor).roundToInt() / factor; effectsState = effectsState.copy(speed = r); applyEffectsAndSave() }
    fun togglePitchEnabled(e: Boolean) { effectsState = effectsState.copy(isPitchEnabled = e); applyEffectsAndSave() }
    fun toggle8D() { effectsState = effectsState.copy(is8DEnabled = !effectsState.is8DEnabled); applyEffectsAndSave() }
    fun setEightDSpeed(v: Float) { effectsState = effectsState.copy(eightDSpeed = v); applyEffectsAndSave() }
    fun toggleMuffled() { val n = !effectsState.isMuffledEnabled; effectsState = effectsState.copy(isMuffledEnabled = n); applyEffectsAndSave() }
    fun setMuffledIntensity(v: Float) { effectsState = effectsState.copy(muffledIntensity = v); applyEffectsAndSave() }
    fun toggleBassBoost() { val n = !effectsState.isBassBoostEnabled; effectsState = effectsState.copy(isBassBoostEnabled = n); applyEffectsAndSave() }
    fun setBassBoostIntensity(v: Float) { effectsState = effectsState.copy(bassBoostIntensity = v); applyEffectsAndSave() }
    fun toggleReverb() { effectsState = effectsState.copy(isReverbEnabled = !effectsState.isReverbEnabled); applyEffectsAndSave() }
    fun setReverbIntensity(v: Float) { effectsState = effectsState.copy(reverbIntensity = v); applyEffectsAndSave() }
    fun toggleEarrape() { val n = !effectsState.isEarrapeEnabled; effectsState = effectsState.copy(isEarrapeEnabled = n); applyEffectsAndSave() }
    fun toggleMono() { val n = !effectsState.isMonoEnabled; effectsState = effectsState.copy(isMonoEnabled = n); applyEffectsAndSave() }
    fun toggleNormalization(enabled: Boolean) { effectsState = effectsState.copy(isNormalizationEnabled = enabled); applyEffectsAndSave() }
    fun setNormalizationLevel(level: NormalizationLevel) { effectsState = effectsState.copy(normalizationLevel = level); applyEffectsAndSave() }


    fun hasSeenEarrapeWarning(): Boolean = playerPrefs.hasSeenEarrapeWarning()
    fun setHasSeenEarrapeWarning(seen: Boolean) { playerPrefs.setHasSeenEarrapeWarning(seen) }

    private fun applyEffectsAndSave() { MusicManager.applyEffects(effectsState); viewModelScope.launch(Dispatchers.IO) { playerPrefs.saveEffects(effectsState) } }

    fun showTrackOptions(track: Track, playlistContextId: Long? = null, fromPlayer: Boolean = false) { trackForMenu = track; menuContextPlaylistId = playlistContextId; isMenuContextFromPlayer = fromPlayer; showMenuSheet = true }

    /** Desktop right-click on a playlist card = the Android playlist 3-dot sheet. */
    fun showPlaylistOptions(playlist: Playlist) { playlistForMenu = playlist; showPlaylistMenuSheet = true }

    fun sharePlaylist(playlist: Playlist) {
        val urlToShare = playlist.permalinkUrl ?: "https://soundcloud.com/playlists/${playlist.id}"
        // Desktop "share" = copy the link to the clipboard + toast (same as shareTrack).
        try {
            val selection = java.awt.datatransfer.StringSelection(urlToShare)
            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
            com.alananasss.kittytune.core.Toaster.show(str("copied_to_clipboard"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        showPlaylistMenuSheet = false
    }
    fun prepareBulkAdd(tracks: List<Track>) { tracksToAddInBulk = tracks; trackForMenu = null; showAddToPlaylistSheet = true }
    fun addToPlaylist(playlistId: Long, track: Track) { DownloadManager.addTrackToPlaylist(playlistId, track); showAddToPlaylistSheet = false; emitUiEvent(str("success_generic")) }
    fun addTracksToPlaylist(playlistId: Long, tracks: List<Track>) {
        DownloadManager.addTracksToPlaylistBulk(playlistId, tracks)
        viewModelScope.launch {
            showAddToPlaylistSheet = false
            emitUiEvent(str("success_generic"))
        }
    }
    fun createAndAddToPlaylist(name: String, track: Track) { 
        viewModelScope.launch(Dispatchers.IO) { 
            val id = DownloadManager.createUserPlaylist(name)
            DownloadManager.addTrackToPlaylist(id, track)
            withContext(Dispatchers.Main) { 
                showAddToPlaylistSheet = false
                emitUiEvent(str("success_generic"))
            }
        } 
    }
    fun createAndAddTracksToPlaylist(name: String, tracks: List<Track>) { 
        viewModelScope.launch(Dispatchers.IO) { 
            val id = DownloadManager.createUserPlaylist(name)
            DownloadManager.addTracksToPlaylistBulk(id, tracks)
            withContext(Dispatchers.Main) { 
                showAddToPlaylistSheet = false
                emitUiEvent(str("success_generic"))
            } 
        } 
    }
    fun removeFromContextPlaylist(playlistId: Long, track: Track) { DownloadManager.removeTrackFromPlaylist(playlistId, track.id); showMenuSheet = false; emitUiEvent(str("success_generic")) }
    fun addToQueue(tracks: List<Track>) {
        if (tracks.isEmpty()) return

        val uniqueTracks = tracks.map { it.copy() }

        val mediaItems = uniqueTracks.map { track ->
            buildMediaItem(track, null, null)
        }
        player.addMediaItems(mediaItems)

        _queue.addAll(uniqueTracks)
        _originalQueue.addAll(uniqueTracks)
        updateQueueState()
        saveStateAsync(saveQueue = true)
        emitUiEvent(str("menu_add_queue"))
    }
    fun downloadTrack(track: Track) { if (DownloadManager.isTrackDownloading(track.id)) return; DownloadManager.downloadTrack(track) }

    private fun emitUiEvent(msg: String) { viewModelScope.launch { _uiEvent.emit(msg) } }
    private fun saveStateAsync(saveQueue: Boolean = false, savePositionOnly: Boolean = false) {
        if (isRestoringSession) return
        val t = currentTrack
        val p = currentPosition
        val c = currentContext
        val s = shuffleEnabled
        val r = repeatMode
        if (savePositionOnly) {
            playerPrefs.savePosition(p)
            return
        }
        if (saveQueue) {
            saveQueueJob?.cancel()
            saveQueueJob = viewModelScope.launch(Dispatchers.Main) {
                delay(500.milliseconds)
                val freshT = currentTrack
                val freshP = currentPosition
                val freshC = currentContext
                val freshS = shuffleEnabled
                val freshR = repeatMode
                val qSnapshot = _originalQueue.toList()
                withContext(Dispatchers.IO) {
                    playerPrefs.savePlaybackState(freshT, freshP, qSnapshot, freshC, freshS, freshR)
                }
            }
        } else {
            val q = _originalQueue.toList()
            viewModelScope.launch(Dispatchers.IO) {
                playerPrefs.savePlaybackState(t, p, q, c, s, r)
            }
        }
    }

    private fun startProgressUpdate() {
        progressUpdateJob?.cancel()
        progressUpdateJob = viewModelScope.launch {
            val tokenManager = TokenManager
            val isGuest = tokenManager.isGuestMode()
            var lastSaveTime = System.currentTimeMillis()
            while (isActive && isPlaying) {
                try {
                    if (!isScrubbing && !isLoading) {
                        val now = System.currentTimeMillis()
                        if (now - lastSaveTime > 5000L) {
                            lastSaveTime = now
                            saveStateAsync(savePositionOnly = true)
                        }

                        val enginePos = MusicManager.player.currentPosition.coerceAtLeast(0L)
                        val timeSinceSeek = System.currentTimeMillis() - lastSeekTimestamp
                        if (timeSinceSeek < 800L) {
                            if (kotlin.math.abs(enginePos - seekTargetPosition) < 1500L) {
                                currentPosition = enginePos
                            }
                        } else {
                            currentPosition = enginePos
                        }
                        currentSessionListenMs += 16L
                        
                        if (currentSessionListenMs >= 30_000L && !hasPushedRecentlyPlayed) {
                            println("Threshold reached, pushing history for track ${currentTrack?.id}")
                            hasPushedRecentlyPlayed = true
                            pushRecentlyPlayedToSoundCloud(currentTrack)
                        }
                        
                        val crossfadeEnabled = playerPrefs.getCrossfadeEnabled()
                        val crossfadeMs = playerPrefs.getCrossfadeDuration() * 1000L
                        val dur = MusicManager.player.duration
                        val continuousPlaybackEnabled = playerPrefs.getContinuousPlaybackEnabled()
                        val shouldCrossfade = crossfadeEnabled && (continuousPlaybackEnabled || repeatMode == RepeatMode.ONE)
                        if (shouldCrossfade && dur > 0 && currentPosition >= (dur - crossfadeMs) && !MusicManager.player.isCrossfadingOut) {
                            MusicManager.player.isCrossfadingOut = true
                            playNext(manual = false, isCrossfade = true)
                        }
                    }
                } catch (_: Exception) {
                }
                delay(16.milliseconds)
            }
        }
    }

    private fun pushRecentlyPlayedToSoundCloud(track: Track?) {
        println("pushRecentlyPlayedToSoundCloud called for track ${track?.id}, source: ${track?.source}")
        track ?: return
        if (track.source != null && track.source != "soundcloud") {
            println("Ignoring track because source is not soundcloud: ${track.source}")
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tokenManager = TokenManager
                if (tokenManager.isGuestMode()) {
                    println("Skipping history sync for track ${track.id} (Guest Mode)")
                    return@launch
                }

                val now = System.currentTimeMillis()

                // Sync individual track history
                val trackUrn = "soundcloud:tracks:${track.id}"
                
                // 1) api-mobile endpoint (used by the Android app)
                val trackEntry = com.alananasss.kittytune.data.network.ApiRecentlyPlayed(
                    playedAt = now,
                    urn = trackUrn
                )
                val trackCollection = com.alananasss.kittytune.data.network.ApiCollection(
                    collection = listOf(trackEntry)
                )
                val trackResponse = api.pushPlayHistory(trackCollection)
                if (trackResponse.isSuccessful) {
                    println("Synced track ${track.id} to mobile history")
                } else {
                    println("Failed to sync mobile history: ${trackResponse.code()}")
                }

                // 2) api-v2 endpoint (used by the Web app)
                val bodyV2 = com.google.gson.JsonObject().apply { addProperty("track_urn", trackUrn) }
                val respV2 = api.pushPlayHistoryV2Me(bodyV2)
                if (respV2.isSuccessful) {
                    println("Synced track ${track.id} to web history")
                } else {
                    println("Failed to sync web history: ${respV2.code()}")
                }

                // 3) Sync context history (updates recently played carousel on SoundCloud home)
                val contextUrn = currentContext?.let { ctx ->
                    val navId = ctx.navigationId
                    when {
                        navId == "likes" -> {
                            if (currentUserId == 0L) {
                                try {
                                    val me = api.getMe()
                                    currentUserId = me.id
                                    currentUser = me
                                } catch (_: Exception) {
                                    println("Failed to fetch user ID for recently-played context")
                                }
                            }
                            if (currentUserId != 0L) {
                                "soundcloud:liked-tracks:$currentUserId"
                            } else {
                                null
                            }
                        }
                        navId.startsWith("station:") -> {
                            val id = navId.removePrefix("station:")
                            if (id.toLongOrNull() != null) {
                                "soundcloud:system-playlists:track-stations:$id"
                            } else {
                                null
                            }
                        }
                        navId.startsWith("station_artist:") -> {
                            val id = navId.removePrefix("station_artist:")
                            if (id.toLongOrNull() != null) {
                                "soundcloud:system-playlists:artist-stations:$id"
                            } else {
                                null
                            }
                        }
                        navId.startsWith("profile:") -> {
                            val id = navId.removePrefix("profile:")
                            if (id.toLongOrNull() != null) {
                                "soundcloud:users:$id"
                            } else {
                                null
                            }
                        }
                        navId == "downloads" || navId.startsWith("local_playlist:") || navId.startsWith("yt_radio:") -> {
                            null
                        }
                        else -> {
                            // Assume it's a playlist or album if it parses to a valid positive long
                            val playlistId = navId.toLongOrNull()
                            if (playlistId != null && playlistId > 0L) {
                                "soundcloud:playlists:$playlistId"
                            } else {
                                null
                            }
                        }
                    }
                }

                if (contextUrn != null) {
                    val contextEntry = com.alananasss.kittytune.data.network.ApiRecentlyPlayed(
                        playedAt = now,
                        urn = contextUrn
                    )
                    val contextCollection = com.alananasss.kittytune.data.network.ApiCollection(
                        collection = listOf(contextEntry)
                    )
                    val contextResponse = api.pushRecentlyPlayed(contextCollection)
                    if (contextResponse.isSuccessful) {
                        println("Synced context $contextUrn to recently played")
                    } else {
                        println("Failed to sync context $contextUrn: ${contextResponse.code()}")
                    }
                } else {
                    println("No valid SoundCloud context URN to sync")
                }

            } catch (e: Exception) {
                println("Error syncing history")
            }
        }
    }

    fun updateScrubPosition(position: Long) {
        isScrubbing = true
        currentPosition = position
        player.seekTo(position)
    }

    // â”€â”€â”€ Sleep Timer â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun startSleepTimer(durationMs: Long) {
        cancelSleepTimer()

        val isFadeEnabled = playerPrefs.getSleepTimerFadeEnabled()
        val fadeDurationSec = if (isFadeEnabled) playerPrefs.getSleepTimerFadeDuration() else 0
        val fadeDurationMs = fadeDurationSec * 1000L

        // Save current volume before any fade
        preFadeVolume = player.volume

        val startTime = System.currentTimeMillis()
        val endTime = startTime + durationMs
        sleepTimerEndOfTrack = false

        showSleepTimerDialog = false
        showSleepTimerIslandNotification(isStarted = true, durationText = formatRemaining(durationMs))
        emitUiEvent(str("sleep_timer_started"))

        sleepTimerJob = viewModelScope.launch(Dispatchers.Main) {
            while (isActive) {
                val now = System.currentTimeMillis()
                val remaining = endTime - now

                if (remaining <= 0) {
                    // Timer elapsed: smooth cut to 0 then pause
                    player.volume = 0f
                    player.pause()
                    // Restore original volume (playback is paused = no sound)
                    player.volume = preFadeVolume
                    sleepTimerRemainingMs = 0L
                    showSleepTimerIslandNotification(isStarted = false)
                    break
                }

                // Progressive fade-out zone
                if (fadeDurationMs > 0L && remaining <= fadeDurationMs) {
                    val fraction = (remaining.toFloat() / fadeDurationMs).coerceIn(0f, 1f)
                    // Quadratic curve: perceived volume decreases naturally
                    val volumeFraction = fraction * fraction
                    player.volume = (preFadeVolume * volumeFraction).coerceIn(0f, 1f)
                }

                sleepTimerRemainingMs = remaining
                delay(PlayerPreferences.SLEEP_TIMER_FADE_UPDATE_INTERVAL_MS.milliseconds)
            }
        }
    }

    private fun formatRemaining(durationMs: Long): String {
        val totalSeconds = (durationMs + 999) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return when {
            hours > 0 -> str("sleep_timer_hours_minutes_format", hours.toInt(), minutes.toInt())
            minutes > 0 -> str("sleep_timer_minutes_seconds_format", minutes.toInt(), seconds.toInt())
            else -> str("sleep_timer_seconds_format", seconds.toInt())
        }
    }

    fun startSleepTimerEndOfTrack() {
        cancelSleepTimer()
        sleepTimerRemainingMs = 0L
        sleepTimerEndOfTrack = true
        showSleepTimerDialog = false
        showSleepTimerIslandNotification(isStarted = true, durationText = str("sleep_timer_end_of_track"))
        emitUiEvent(str("sleep_timer_started"))
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        // Restore original volume if a fade was in progress
        if (player.volume != preFadeVolume) {
            player.volume = preFadeVolume
        }
        sleepTimerRemainingMs = 0L
        sleepTimerEndOfTrack = false
    }

    fun formatSleepTimerRemaining(): String {
        if (sleepTimerEndOfTrack) return str("sleep_timer_end_of_track")
        return formatRemaining(sleepTimerRemainingMs)
    }

    private fun showSleepTimerIslandNotification(isStarted: Boolean, durationText: String? = null) {
        viewModelScope.launch {
            val subtitle = if (isStarted) {
                str("sleep_timer_island_started_subtitle", durationText ?: "")
            } else {
                str("sleep_timer_island_finished_subtitle")
            }
            emitUiEvent(subtitle)
        }
    }


    private fun restoreSession() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val lastQueue = playerPrefs.getLastQueue()
                val lastTrack = playerPrefs.getLastTrack()
                val lastPosition = playerPrefs.getLastPosition()
                val lastContext = playerPrefs.getLastContext()
                val lastShuffle = playerPrefs.getLastShuffleEnabled()
                val lastRepeat = playerPrefs.getLastRepeatMode()
                withContext(Dispatchers.Main) {
                    if (lastQueue.isNotEmpty()) { _queue.clear(); _queue.addAll(lastQueue); _originalQueue.clear(); _originalQueue.addAll(lastQueue); updateQueueState() }
                    if (lastTrack != null) {
                        shuffleEnabled = lastShuffle; repeatMode = lastRepeat; currentContext = lastContext
                        MusicManager.updateContext(lastContext)

                        currentTrack = lastTrack
                        MusicManager.currentTrack = lastTrack; isLiked = LikeRepository.isTrackLiked(lastTrack.id); loadLyrics(lastTrack)
                        currentQueueIndex = _queue.indexOfFirst { it.id == lastTrack.id }
                        if (currentQueueIndex == -1) { _queue.add(0, lastTrack); _originalQueue.add(0, lastTrack); updateQueueState(); currentQueueIndex = 0 }
                        val currentPlayerMediaId = MusicManager.player.currentMediaItem?.mediaId
                        System.err.println("PlayerViewModel: restoring session. lastPosition = $lastPosition, currentPlayerMediaId = $currentPlayerMediaId, lastTrackId = ${lastTrack.id}")
                        if (currentPlayerMediaId == lastTrack.id.toString() && MusicManager.player.isPlaying) {
                            isPlaying = true
                            duration = MusicManager.player.duration.coerceAtLeast(lastTrack.durationMs ?: 0L)
                            currentPosition = MusicManager.player.currentPosition
                            MusicManager.applyEffects(effectsState)
                            System.err.println("PlayerViewModel: player already has track and is playing. currentPosition set to $currentPosition")
                        } else {
                            currentPosition = lastPosition
                            duration = lastTrack.durationMs ?: 0L
                            System.err.println("PlayerViewModel: calling playRobustly with startPosition = $lastPosition")
                            if (currentQueueIndex >= 0) {
                                playRobustly(currentQueueIndex, autoPlay = false, startPosition = lastPosition)
                            }
                        }
                        delay(200.milliseconds)
                    }
                    isRestoringSession = false
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { isRestoringSession = false }
            }
        }
    }

    fun syncWithCurrentPlayback() {
        viewModelScope.launch(Dispatchers.Main) {
            val playing = try { MusicManager.player.isPlaying } catch (_: Exception) { false }
            if (MusicManager.currentTrack != null && playing) {
                currentTrack = MusicManager.currentTrack
                isPlaying = true
                duration = MusicManager.player.duration.coerceAtLeast(0L)
                currentPosition = MusicManager.player.currentPosition
            }

            withContext(Dispatchers.IO) {
                val savedQueue = playerPrefs.getLastQueue()
                val savedContext = playerPrefs.getLastContext()

                withContext(Dispatchers.Main) {
                    if (savedQueue.isNotEmpty()) {
                        _queue.clear()
                        _queue.addAll(savedQueue)
                        _originalQueue.clear()
                        _originalQueue.addAll(savedQueue)
                        updateQueueState()

                        if (currentTrack != null) {
                            currentQueueIndex = _queue.indexOfFirst { it.id == currentTrack!!.id }.coerceAtLeast(0)
                        }
                    }
                    if (savedContext != null) {
                        currentContext = savedContext
                        MusicManager.updateContext(savedContext)
                    }
                }
            }
        }
    }

    private fun loadBitmap(url: String): java.awt.image.BufferedImage? = ArtworkPalette.load(url)

    private fun updatePlayerColors(track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            val bitmap = loadBitmap(track.fullResArtwork)
            if (bitmap != null) {
                try {
                    val artFile = File(com.alananasss.kittytune.core.AppDirs.imageCacheDir, "art_${track.id}.jpg")
                    if (!artFile.exists()) {
                        javax.imageio.ImageIO.write(bitmap, "jpg", artFile)
                    }
                } catch (e: Exception) { e.printStackTrace() }

                // Desktop app is dark-first; pick a light vibrant color for contrast on dark bg.
                val isDarkMode = playerPrefs.getThemeMode() != com.alananasss.kittytune.data.local.AppThemeMode.LIGHT
                backgroundColor = ArtworkPalette.dominantColor(bitmap, preferLight = isDarkMode)
            } else {
                backgroundColor = Color(0xFF1E1E1E)
            }
        }
    }

    private fun playRobustly(index: Int, autoPlay: Boolean = true, startPosition: Long = 0L, allowSkipOnFailure: Boolean = true, isCrossfade: Boolean = false) {
        if (index !in _queue.indices) return

        val trackToPlay = _queue[index]

        playJob?.cancel()
        playJob = viewModelScope.launch(Dispatchers.IO) {
            System.err.println("PlayerViewModel: playRobustly started for trackId=${trackToPlay.id} with startPosition=$startPosition, autoPlay=$autoPlay")
            var resolvedUrl: String? = null
            var offlineKeySetId: ByteArray? = null

            try {
                val db = com.alananasss.kittytune.data.local.AppDatabase.downloadDao
                val localTrack = db.getTrack(trackToPlay.id)
                if (localTrack != null && localTrack.localAudioPath.isNotEmpty()) {
                    if (localTrack.localAudioPath.startsWith("exo_cache://")) {
                        val parts = localTrack.localAudioPath.removePrefix("exo_cache://").split("::", limit = 3)
                        val cachedStreamUrl = parts.getOrNull(1)
                        val tokenStr = parts.getOrNull(2)

                        if (!cachedStreamUrl.isNullOrEmpty()) {
                            resolvedUrl = cachedStreamUrl
                            if (!tokenStr.isNullOrEmpty()) {
                                offlineKeySetId = java.util.Base64.getDecoder().decode(tokenStr)
                            }
                        }
                    } else {
                        val isContentUri = localTrack.localAudioPath.startsWith("content://")
                        val fileExists = if (isContentUri) true else File(localTrack.localAudioPath).exists()
                        if (fileExists) {
                            resolvedUrl = localTrack.localAudioPath
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }

            if (resolvedUrl == null) {
                // Not downloaded, resolve from network / streamCache
                val resolved = StreamResolver.resolveStreamWithDrm(trackToPlay)
                resolvedUrl = resolved?.url

                // Cache DRM token if present (streaming JWT)
                if (resolved?.isDrmProtected == true && resolved.licenseAuthToken != null) {
                    MusicManager.putDrmToken(trackToPlay.id, resolved.licenseAuthToken)
                    println("DRM token pre-cached for track ${trackToPlay.id}")
                }
            }

            if (resolvedUrl == null) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    isPlaying = false
                    if (allowSkipOnFailure) playNext(manual = false, ignoreRepeatOne = true)
                }
                return@launch
            }

            val newMediaItem = buildMediaItem(trackToPlay, null, resolvedUrl, offlineKeySetId)

            withContext(Dispatchers.Main) {
                try {
                    queueChunkingJob?.cancel()
                    
                    val crossfadeDurationMs = playerPrefs.getCrossfadeDuration() * 1000L
                    if (isCrossfade && crossfadeDurationMs > 0 && MusicManager.player.isPlaying && startPosition == 0L) {
                        MusicManager.player.crossfadeToMediaItem(newMediaItem, startPosition, crossfadeDurationMs)
                    } else {
                        MusicManager.player.setMediaItem(newMediaItem, startPosition)
                        MusicManager.player.prepare()
                        if (autoPlay) {
                            MusicManager.player.playWhenReady = true
                        }
                    }

                    if (autoPlay) {
                        MusicManager.player.play()
                    }

                    MusicManager.applyEffects(effectsState)
                    preloadNextTrack(index + 1)
                } catch (e: Exception) {
                    e.printStackTrace()
                    isLoading = false
                    isPlaying = false
                }
            }
        }
    }

    private fun preloadNextTrack(nextIndex: Int) {
        val targetIndex = if (nextIndex >= _queue.size) {
            if (repeatMode == RepeatMode.ALL && _queue.isNotEmpty()) 0 else return
        } else nextIndex

        val nextTrack = _queue[targetIndex]

        viewModelScope.launch(Dispatchers.IO) {
            try {
                var resolvedUrl: String? = null
                var offlineKeySetId: ByteArray? = null

                val db = com.alananasss.kittytune.data.local.AppDatabase.downloadDao
                val localTrack = db.getTrack(nextTrack.id)
                if (localTrack != null && localTrack.localAudioPath.isNotEmpty()) {
                    if (localTrack.localAudioPath.startsWith("exo_cache://")) {
                        val parts = localTrack.localAudioPath.removePrefix("exo_cache://").split("::", limit = 3)
                        resolvedUrl = parts.getOrNull(1)
                        val tokenStr = parts.getOrNull(2)
                        if (!tokenStr.isNullOrEmpty()) offlineKeySetId = java.util.Base64.getDecoder().decode(tokenStr)
                    } else {
                        resolvedUrl = localTrack.localAudioPath
                    }
                }

            if (resolvedUrl == null) {
                    val resolved = StreamResolver.resolveStreamWithDrm(nextTrack)
                    resolvedUrl = resolved?.url
                    if (resolved?.isDrmProtected == true && resolved.licenseAuthToken != null) {
                        MusicManager.putDrmToken(nextTrack.id, resolved.licenseAuthToken)
                    }
                }

                if (resolvedUrl != null) {
                    val nextMediaItem = buildMediaItem(nextTrack, null, resolvedUrl, offlineKeySetId)
                    withContext(Dispatchers.Main) {
                        if (MusicManager.player.mediaItemCount == 1) {
                            MusicManager.player.addMediaItem(nextMediaItem)
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun isColorDark(color: Int): Boolean {
        val r = (color shr 16) and 0xFF; val g = (color shr 8) and 0xFF; val b = color and 0xFF
        val darkness = 1 - (0.299 * r + 0.587 * g + 0.114 * b) / 255
        return darkness >= 0.5
    }

    private fun buildMediaItem(track: Track, bitmap: java.awt.image.BufferedImage?, urlOverride: String? = null, offlineKeySetId: ByteArray? = null): MediaItem {
        // The desktop engine resolves the real stream URL itself; pass an override if we have one,
        // else attach the Track so the Player shim resolves via StreamResolver at load time.
        val uri = urlOverride ?: "soundtune://track/${track.id}"

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(track.title ?: str("untitled_track"))
            .setArtist(track.user?.username ?: str("unknown_artist"))
            .setArtworkUri(track.fullResArtwork)

        if (bitmap != null) {
            try {
                val stream = java.io.ByteArrayOutputStream()
                javax.imageio.ImageIO.write(bitmap, "jpg", stream)
                metadataBuilder.setArtworkData(stream.toByteArray(), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            } catch (_: Exception) {}
        }

        // DRM (Widevine) is not decryptable on desktop â€” see docs/port/TODO-widevine-drm.md.
        // Such tracks fall back to progressive/YouTube in StreamResolver; no DRM config here.
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(track.id.toString())
            .setMediaMetadata(metadataBuilder.build())
            .setTrack(track)
            .build()
    }
}

