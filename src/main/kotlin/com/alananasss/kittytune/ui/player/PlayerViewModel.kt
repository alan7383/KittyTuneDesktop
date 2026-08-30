package com.alananasss.kittytune.ui.player

import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.core.Application
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import com.alananasss.kittytune.data.local.LyricsDisplayStyle
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.net.URLEncoder
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import com.alananasss.kittytune.data.network.MusixmatchClient
import com.alananasss.kittytune.data.network.SoundCloudTelemetryTracker
import com.alananasss.kittytune.utils.Logger
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
    val provider: String
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val gson = Gson()
    private val lyricsOverridesPrefs =
        com.alananasss.kittytune.core.NamedPrefs("lyrics_overrides")

    /**
     * Manual lyrics persistence (issue #27): when the user picks a lyric result by
     * hand, remember it per track so future plays reuse that exact choice.
     */
    private fun saveLyricsOverride(trackId: Long, result: UnifiedLyricResult) {
        if (trackId <= 0L) return
        try {
            lyricsOverridesPrefs.putString("override_$trackId", gson.toJson(result))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getLyricsOverride(trackId: Long): UnifiedLyricResult? {
        if (trackId <= 0L) return null
        return try {
            val raw = lyricsOverridesPrefs.getString("override_$trackId", null)
            raw?.takeIf { it.isNotBlank() }?.let { gson.fromJson(it, UnifiedLyricResult::class.java) }
        } catch (e: Exception) {
            null
        }
    }
    private val api = RetrofitClient.create()
    private val playerPrefs = PlayerPreferences()
    private val tokenManager = TokenManager

    private val _uiEvent = MutableSharedFlow<String>()
    val uiEvent = _uiEvent.asSharedFlow()

    var currentUserId by mutableLongStateOf(0L)
    var currentUser by mutableStateOf<User?>(null)
    private val initialTrack = if (playerPrefs.getPersistentQueueEnabled()) playerPrefs.getLastTrack() else null
    var currentTrack by mutableStateOf<Track?>(initialTrack)
    var isPlaying by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    var duration by mutableLongStateOf(initialTrack?.durationMs ?: 0L)
    var currentPosition by mutableLongStateOf(
        if (playerPrefs.getPersistentQueueEnabled() && playerPrefs.getSavePositionEnabled()) playerPrefs.getLastPosition() else 0L
    )
    var isScrubbing by mutableStateOf(false)
    var isPlayerExpanded by mutableStateOf(false)
    var isLiked by mutableStateOf(false)

    /**
     * Whether the player is filling the window.
     *
     * Separate from [showLyricsSheet] on purpose. The sheet is the lyrics inside the centre panel, which is
     * one of three columns; this is an overlay over the whole window, raised from that sheet by a button on
     * it. Two flags because they are two places, and because closing the big one should put you back on the
     * lyrics rather than back on the library (issue #33).
     */
    var isLyricsFullScreen by mutableStateOf(false)
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
                // Push whatever level the UI is already showing into the freshly built engine.
                // Reading the pref again here would undo a change the user made before the
                // first track started playing.
                MusicManager.setVolume(volume)
                MusicManager.player.addListener(playerListener)
                MusicManager.applyEffects(effectsState)
            }
            return MusicManager.player
        }

    var effectsState by mutableStateOf(playerPrefs.getLastEffects())
    var isPreciseSpeedEnabled by mutableStateOf(playerPrefs.getPreciseSpeedEnabled())

    /**
     * Seeded straight from preferences rather than from the engine: [player] is a lazy getter,
     * and until something touches it the engine still reports its default of 1.0 — which is why
     * the slider used to come up at maximum after every restart (issue #27).
     */
    var volume by mutableFloatStateOf(playerPrefs.getSavedVolume())
        private set

    private var volumeBeforeMute: Float = 1.0f
    private var volumePersistJob: Job? = null

    fun updateVolume(v: Float) {
        val newVol = v.coerceIn(0f, 1f)
        volume = newVol
        MusicManager.setVolume(newVol)
    }

    /** Called when the user finishes a volume interaction; persists across restarts. */
    fun persistVolume() {
        volumePersistJob?.cancel()
        volumePersistJob = null
        playerPrefs.saveVolume(volume)
    }

    /**
     * For continuous input like the scroll wheel. Every pref write re-serialises the whole
     * file, so writing once per wheel notch would stutter the UI.
     */
    fun persistVolumeSoon() {
        volumePersistJob?.cancel()
        volumePersistJob = viewModelScope.launch {
            delay(400)
            playerPrefs.saveVolume(volume)
        }
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
        persistVolume()
    }

    fun volumeDown(delta: Float = 0.1f) {
        updateVolume(volume - delta)
        persistVolume()
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
            } catch (_: Exception) {
            }
        }
    }

    private val _originalQueue = mutableListOf<Track>()
    private val _queue = mutableListOf<Track>()
    val queue: List<Track> get() = _queue
    /**
     * Which tracks in this queue have actually started playing (issue #33).
     *
     * Position in the queue is not the same thing as having been listened to, and the queue view had
     * been treating them as one: jump forward to the sixth track and the five you skipped were drawn
     * as "already played", jump back and the ones you had really heard were drawn as still to come.
     * Reported as the queue counting tracks it had no business counting.
     *
     * Filled where a listen session begins, so it says exactly what the statistics say, and cleared
     * with the queue it describes.
     */
    var playedTrackIds by mutableStateOf<Set<Long>>(emptySet())
        private set

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

    /** Auto-scroll for lyrics with no timings, and its rate. See [PlainLyricsView]. */
    var isPlainAutoScrollEnabled by mutableStateOf(playerPrefs.getLyricsPlainAutoScroll())
        private set
    var plainAutoScrollSpeed by mutableFloatStateOf(playerPrefs.getLyricsPlainAutoScrollSpeed())
        private set

    fun togglePlainAutoScroll(enabled: Boolean) {
        isPlainAutoScrollEnabled = enabled
        playerPrefs.setLyricsPlainAutoScroll(enabled)
    }

    fun updatePlainAutoScrollSpeed(speed: Float) {
        plainAutoScrollSpeed = speed.coerceIn(0.25f, 4f)
        playerPrefs.setLyricsPlainAutoScrollSpeed(plainAutoScrollSpeed)
    }

    /**
     * This track's own auto-scroll speed, or null when it follows the global one (issue #33).
     *
     * Loaded on every track change from [com.alananasss.kittytune.data.LyricsScrollSpeedRepository],
     * which caches misses as well as hits — almost no track has one.
     */
    var trackAutoScrollSpeed by mutableStateOf<Float?>(null)
        private set

    /** What the untimed views actually scroll at: the track's own speed if it has one. */
    val effectivePlainAutoScrollSpeed: Float
        get() = trackAutoScrollSpeed ?: plainAutoScrollSpeed

    fun setTrackAutoScrollSpeed(speed: Float) {
        val track = currentTrack ?: return
        val clamped = speed.coerceIn(
            com.alananasss.kittytune.data.LyricsScrollSpeedRepository.MIN_SPEED,
            com.alananasss.kittytune.data.LyricsScrollSpeedRepository.MAX_SPEED,
        )
        trackAutoScrollSpeed = clamped
        viewModelScope.launch(Dispatchers.IO) {
            com.alananasss.kittytune.data.LyricsScrollSpeedRepository.put(track.id, clamped)
        }
    }

    /** Hands this track back to the global speed. */
    fun clearTrackAutoScrollSpeed() {
        val track = currentTrack ?: return
        trackAutoScrollSpeed = null
        viewModelScope.launch(Dispatchers.IO) {
            com.alananasss.kittytune.data.LyricsScrollSpeedRepository.remove(track.id)
        }
    }

    /** How far one wheel notch moves the lyrics, in lines. */
    var lyricsWheelLines by mutableFloatStateOf(playerPrefs.getLyricsWheelLines())
        private set

    fun updateLyricsWheelLines(lines: Float) {
        lyricsWheelLines = lines.coerceIn(
            PlayerPreferences.LYRICS_WHEEL_LINES_MIN,
            PlayerPreferences.LYRICS_WHEEL_LINES_MAX,
        )
        playerPrefs.setLyricsWheelLines(lyricsWheelLines)
    }

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
                    val translationsRes = com.alananasss.kittytune.data.network.MusixmatchClient.api.getTranslations(
                        trackId = trackId,
                        lang = targetLang,
                        token = token
                    )
                    translationsRes.message.body?.translationsList?.forEach { wrapper ->
                        wrapper.translation?.let { t ->
                            translationMap[t.matchedLine.trim()] = t.description.trim()
                        }
                    }
                } catch (_: Exception) {
                }
            }

            val missingLines = originalLines.filter { !translationMap.containsKey(it.trim()) }
            if (missingLines.isNotEmpty()) {
                val machineTranslations =
                    com.alananasss.kittytune.data.network.FreeTranslator.translateMissing(missingLines, targetLang)
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
    var lyricsFullScreenFontSize by mutableFloatStateOf(playerPrefs.getLyricsFullScreenFontSize())
    var lyricsAlignment by mutableStateOf(playerPrefs.getLyricsAlignment())
    var lyricsFullScreenAlignment by mutableStateOf(playerPrefs.getLyricsFullScreenAlignment())

    /** How the line being sung is set apart. See [LyricsDisplayStyle]. */
    var lyricsDisplayStyle by mutableStateOf(playerPrefs.getLyricsDisplayStyle())
    var lyricsFullScreenDisplayStyle by mutableStateOf(playerPrefs.getLyricsFullScreenDisplayStyle())

    var lyricsMode by mutableStateOf(LyricsMode.SYNCED)
    var rawPlainLyrics by mutableStateOf<String?>(null)
    var showInlineLyrics by mutableStateOf(false)
    var lyricsOffset by mutableLongStateOf(0L)
    var showLyricsOffsetControls by mutableStateOf(false)

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

    /**
     * The listen in progress, and the track it belongs to (issue #33).
     *
     * Replaces a bare counter that was incremented by 250 ms per progress tick and then written only
     * when the track ended in one of three specific ways. Anything else — closing the app, stopping,
     * loading something else — discarded it, which is why whole listening sessions went unrecorded.
     * Every ending now goes through [flushListenSession].
     */
    private var listenSession: com.alananasss.kittytune.data.stats.ListenSessionAccumulator? = null
    private var listenSessionTrack: Track? = null

    /** Media milliseconds heard in the current listen. Exposed for the player's own displays. */
    val currentSessionListenMs: Long get() = listenSession?.listenedMs ?: 0L

    /**
     * Makes sure the track that is playing has a session, whichever code path started it.
     *
     * Auto-advance inside the player does not always go through [playTrackAtIndex], so relying on the
     * explicit calls alone left some transitions unrecorded. Called from the progress loop, where
     * "something is playing" is known to be true.
     */
    private fun ensureListenSession() {
        val track = currentTrack ?: return
        if (listenSession != null && listenSessionTrack?.id == track.id) return
        beginListenSession(track, currentPosition)
        listenSession?.onPlaying(currentPosition)
    }

    /**
     * Starts accounting for [track] from [startPositionMs], writing out whatever was in progress first.
     */
    private fun beginListenSession(track: Track?, startPositionMs: Long = 0L) {
        flushListenSession("TRACK_CHANGE")
        listenSessionTrack = track
        listenSession = track?.let {
            com.alananasss.kittytune.data.stats.ListenSessionAccumulator(startPositionMs)
        }
    }

    /**
     * Writes the listen in progress, if enough of it was heard, and clears it.
     *
     * Safe to call repeatedly and from anywhere: the session is cleared first, so two callers racing to
     * end the same listen cannot record it twice.
     *
     * @param reason how the listen ended, kept for detail only — no aggregate depends on it any more.
     */
    fun flushListenSession(reason: String, blocking: Boolean = false) {
        val session = listenSession ?: return
        val track = listenSessionTrack
        listenSession = null
        listenSessionTrack = null
        if (track == null || !playerPrefs.getListeningStatsEnabled()) return

        // Nothing heard at all is not a listen and not a skip — it is a track that was loaded. Recording
        // it would put a row in the table that every aggregate then has to exclude, and would make the
        // skip rate a measure of how often the next button was pressed while something loaded.
        if (session.listenedMs <= 0L) return

        ListeningStatsRepository.recordEvent(
            track = track,
            eventType = reason,
            listenDurationMs = session.listenedMs,
            furthestPositionMs = session.furthestPositionMs,
            blocking = blocking,
        )
    }

    // --- trim / smart skip -----------------------------------------------------------------------

    /**
     * The current track's trim, if it has one (issue #33).
     *
     * SoundCloud is full of re-uploads that exist only because someone wanted a song without its guest verse
     * or without a long intro. This is that, done in the player: a few remembered timestamps, skipped over on
     * the fly. The audio file is never touched, so clearing the trim gives the original back.
     */
    var currentTrim by mutableStateOf(com.alananasss.kittytune.audio.TrackTrim.none())
        private set

    /** Whether the trim editor is open. */
    var showTrimDialog by mutableStateOf(false)

    private var trimJob: Job? = null
    private var trimWatchJob: Job? = null

    /** Set while a trim jump is fading, so the watcher does not fire again mid-jump. */
    @Volatile
    private var trimJumpInProgress = false

    /** Loads the trim for [trackId], or clears it. Cheap enough to call on every track change. */
    private fun loadTrimFor(trackId: Long?) {
        trimJob?.cancel()
        if (trackId == null) {
            currentTrim = com.alananasss.kittytune.audio.TrackTrim.none()
            return
        }
        trimJob = viewModelScope.launch {
            currentTrim = com.alananasss.kittytune.data.TrackTrimRepository.get(trackId)
        }
    }

    /** Replaces the trim for the track playing now, and applies it immediately. */
    fun saveCurrentTrim(trim: com.alananasss.kittytune.audio.TrackTrim) {
        val trackId = currentTrack?.id ?: return
        currentTrim = trim
        viewModelScope.launch {
            com.alananasss.kittytune.data.TrackTrimRepository.put(trackId, trim)
            // Applied at once rather than at the next track: editing a trim while listening to the part you
            // are removing and having it keep playing is a confusing way to find out it worked.
            applyTrimNow()
        }
    }

    fun clearCurrentTrim() {
        val trackId = currentTrack?.id ?: return
        currentTrim = com.alananasss.kittytune.audio.TrackTrim.none()
        viewModelScope.launch { com.alananasss.kittytune.data.TrackTrimRepository.remove(trackId) }
    }

    /**
     * Watches the clock for the moment a trim applies.
     *
     * Its own loop rather than a hook in the progress updater, which reports four times a second: a quarter of
     * a second of the verse you asked to remove is exactly the thing that would make this feel unfinished. The
     * tick reads one volatile long, so running it twenty times faster costs nothing measurable.
     */
    private fun startTrimWatcher() {
        if (trimWatchJob != null) return
        trimWatchJob = viewModelScope.launch {
            while (isActive) {
                delay(TRIM_TICK_MS)
                if (!isPlaying || trimJumpInProgress || currentTrim.isEmpty) continue
                applyTrimNow()
            }
        }
    }

    private suspend fun applyTrimNow() {
        val trim = currentTrim
        if (trim.isEmpty) return
        when (val action = trim.actionFor(player.currentPosition, player.duration)) {
            is com.alananasss.kittytune.audio.TrimAction.Continue -> Unit

            is com.alananasss.kittytune.audio.TrimAction.JumpTo -> jumpWithFade(action.positionMs)

            is com.alananasss.kittytune.audio.TrimAction.Finished -> {
                // Treated as the track running out, so repeat, the queue and the listening statistics all see
                // what they would have seen if the file itself had ended here.
                trimJumpInProgress = true
                try {
                    fadeVolumeTo(0f)
                    flushListenSession("PLAY_COMPLETE")
                    playNext(manual = false)
                } finally {
                    player.volume = volume
                    trimJumpInProgress = false
                }
            }
        }
    }

    /**
     * Seeks to [positionMs] with a short fade either side.
     *
     * The fade is the whole reason this sounds like an edit rather than a fault: a bare seek cuts the waveform
     * mid-cycle and clicks.
     *
     * Measured on a real jump, the transition is about 316 ms end to end — 136 ms of true silence while the
     * seek drains and re-primes the decoder, then the ramp back. Most of that is the seek's cost, not the
     * fade's, and none of it is a hard edge. The earlier claim in this comment was ninety milliseconds, which
     * was the ramp's own duration and not what anyone hears (issue #33).
     */
    private suspend fun jumpWithFade(positionMs: Long) {
        trimJumpInProgress = true
        try {
            // A sleep-timer fade is already driving the volume; taking it over would leave the timer's own
            // restore fighting ours. The jump still happens, just without the ramp.
            val canFade = preFadeVolume == null

            // Fading *out* of something nobody has heard yet is a stutter, not a transition. A trim that
            // moves the start of the track fires within twenty milliseconds of playback beginning, and
            // ramping down from there would make every play of the track open with a wobble. Fading back
            // in still happens, which is what keeps the seek itself silent.
            val fromTheTop = player.currentPosition < TRIM_SILENT_SEEK_MS
            if (canFade && !fromTheTop) fadeVolumeTo(0f) else if (canFade) player.volume = 0f
            player.seekTo(positionMs)
            currentPosition = positionMs
            listenSession?.onSeek(positionMs)

            // Waited for, not assumed. Measuring this on a real jump is what caught the bug: the seek
            // drains and re-primes the decoder, so no audio comes out for roughly 120 ms — and a ramp on
            // the wall clock finished during that silence. The volume was already back at full when the
            // first sample returned, so the fade-in existed in the code and nowhere in the sound: the
            // audio resumed at full level in a single frame, which is precisely the edge the fade was
            // added to remove.
            if (canFade) {
                awaitPlaybackResumed(positionMs)
                fadeVolumeTo(volume)
            }
        } finally {
            if (preFadeVolume == null) player.volume = volume
            trimJumpInProgress = false
        }
    }

    /**
     * Waits until the engine is actually producing audio again after a seek to [seekedToMs].
     *
     * "Producing audio" is read as the reported position having moved past where we seeked to. Bounded,
     * because a seek that never completes — a dead stream URL, a stall — must not leave the track muted:
     * past the deadline the fade runs anyway and the worst case is the hard edge we had before.
     */
    private suspend fun awaitPlaybackResumed(seekedToMs: Long) {
        val deadline = System.currentTimeMillis() + TRIM_RESUME_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (!isPlaying) return
            if (player.currentPosition > seekedToMs) return
            delay(TRIM_TICK_MS)
        }
    }

    private suspend fun fadeVolumeTo(target: Float) {
        val from = player.volume
        val steps = TRIM_FADE_STEPS
        for (step in 1..steps) {
            val fraction = step.toFloat() / steps
            player.volume = (from + (target - from) * fraction).coerceIn(0f, 1f)
            delay(TRIM_FADE_MS / steps)
        }
        player.volume = target.coerceIn(0f, 1f)
    }

    private var hasPushedRecentlyPlayed = false

    var sleepTimerRemainingMs by mutableLongStateOf(0L)
    var sleepTimerEndOfTrack by mutableStateOf(false)
    var showSleepTimerDialog by mutableStateOf(false)
    val isSleepTimerActive: Boolean get() = sleepTimerRemainingMs > 0L || sleepTimerEndOfTrack
    private var sleepTimerJob: Job? = null
    /** Level to put back when a sleep-timer fade ends. Null means no fade is running. */
    private var preFadeVolume: Float? = null

    private var pendingSeekPosition: Long? = null
    private var seekTargetPosition: Long = -1L
    private var lastSeekTimestamp: Long = 0L
    private var saveQueueJob: Job? = null
    private var progressUpdateJob: Job? = null
    private var lyricsJob: Job? = null

    /**
     * Kept apart from [lyricsJob] so cancelling the current lookup — every track change does —
     * does not throw away a prefetch that has already paid for its requests.
     */
    private var lyricsPrefetchJob: Job? = null
    private var queueChunkingJob: Job? = null
    private var trackInitJob: Job? = null
    private var playJob: Job? = null
    private var prefetchWarmJob: Job? = null
    private var discordJob: Job? = null
    private var discordRpc: com.alananasss.kittytune.data.DiscordRPC? = null
    private var mprisService: com.alananasss.kittytune.data.MprisService? = null
    private var kdeMpris2Service: com.alananasss.kittytune.data.KdeMpris2Service? = null
    private var windowsSmtcService: com.alananasss.kittytune.data.WindowsSmtcService? = null

    companion object {
        const val TRACK_PREFIX = "track:"
        const val CONTEXT_SEPARATOR = ":context:"

        /**
         * Weight given to the lyrics provider listed first in the settings, when two results are
         * otherwise equally good. Far below the gap between sync tiers, and below any meaningful
         * difference in match quality: a preference should settle a tie, not override a better
         * match or a genuinely synced result from the other provider (issue #33).
         */
        private const val PROVIDER_PREFERENCE_BONUS = 0.05f

        /** What [Track.fullResArtwork] falls back to when a track has no cover at all. */
        private const val PLACEHOLDER_ARTWORK_PREFIX = "https://picsum.photos"

        /**
         * How often the queue's prefetched stream URLs are topped up. Well under the few
         * minutes a SoundCloud CDN signature lasts, and a cache hit whenever one still holds.
         */
        private const val PREFETCH_REWARM_INTERVAL_MS = 45_000L

        /**
         * How often the trim watcher looks at the clock (issue #33).
         *
         * Twenty milliseconds, not the progress loop's 250: a quarter of a second of the verse you asked to
         * remove is precisely what would make this feel half-finished. The tick reads one volatile long, so
         * running it twelve times more often costs nothing worth measuring.
         */
        private const val TRIM_TICK_MS = 20L

        /**
         * How long each ramp takes.
         *
         * Ninety milliseconds of ramp reaches full level in about 56 ms of audible signal once the device
         * buffer is accounted for — measured, not assumed. Long enough to leave no transient, short enough
         * that the ramp itself is not the thing you notice about the transition.
         */
        private const val TRIM_FADE_MS = 90L

        /** Steps in that fade. Enough to be smooth at 90 ms, few enough to cost nothing. */
        private const val TRIM_FADE_STEPS = 9

        /**
         * Below this, a jump is treated as "playback had not really started" and the fade-out is skipped.
         *
         * Covers the case that would otherwise be the most noticeable of all: a kept range that begins at
         * 00:30 fires on the first tick, and a ramp-down from an intro nobody heard is just a wobble at the
         * top of every play.
         */
        private const val TRIM_SILENT_SEEK_MS = 600L

        /**
         * How long to wait for audio to come back after a trim seek before fading in regardless.
         *
         * Measured at about 120 ms on a healthy stream; this leaves generous room for a slow one without
         * letting a stalled seek mute the track indefinitely.
         */
        private const val TRIM_RESUME_TIMEOUT_MS = 1_500L
    }

    private val syncReceiver = Any()

    private fun getString(resId: String): String = str(resId)
    private fun getString(resId: String, vararg args: Any): String = str(resId, *args)

    private fun parseIdFromMediaId(mediaId: String): Long {
        var cleanId = mediaId
        if (cleanId.startsWith(TRACK_PREFIX)) {
            cleanId = cleanId.removePrefix(TRACK_PREFIX)
        }
        if (cleanId.contains(CONTEXT_SEPARATOR)) {
            cleanId = cleanId.substringBefore(CONTEXT_SEPARATOR)
        }
        return cleanId.toLongOrNull() ?: mediaId.hashCode().toLong()
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlayingState: Boolean) {
            isPlaying = isPlayingState
            saveStateAsync(saveQueue = false)
            // The gap while paused is not listening, and the position after a resume is the new
            // baseline rather than a jump (issue #33).
            if (isPlayingState) listenSession?.onPlaying(currentPosition) else listenSession?.onPaused()
            if (isPlayingState) {
                startProgressUpdate()
                SoundCloudTelemetryTracker.onTrackResumed(currentPosition)
            } else {
                SoundCloudTelemetryTracker.onTrackPaused(currentPosition)
            }
            updateDiscordPresence()
        }


        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                isLoading = false
                if (MusicManager.player.duration > 0) duration = MusicManager.player.duration
                pendingSeekPosition?.let { MusicManager.player.seekTo(it); pendingSeekPosition = null }
            }
            if (state == Player.STATE_BUFFERING) isLoading = true

            if (state == Player.STATE_ENDED) {
                SoundCloudTelemetryTracker.onTrackCompleted()
                if (com.alananasss.kittytune.core.AppInstance.isShuttingDown) return

                flushListenSession(
                    if (repeatMode == RepeatMode.ONE) "REPEAT_ONE_LOOP" else "PLAY_COMPLETE"
                )

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
                        try {
                            MusicManager.player.removeMediaItem(0)
                        } catch (_: Exception) {
                        }
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
                // Was a silent reset: whatever had been listened to went missing here.
flushListenSession("TRACK_CHANGE")
                loadTrimFor(MusicManager.currentTrack?.id)
                hasPushedRecentlyPlayed = false
            }

            if (MusicManager.currentTrack?.id == trackId) {
                currentTrack = MusicManager.currentTrack
            } else if (currentTrack?.id != trackId) {
                val meta = mediaItem.mediaMetadata
                val source = if (mediaItem.mediaId.startsWith("yt_") || mediaItem.requestMetadata.mediaUri?.toString()
                        ?.contains("youtube") == true
                ) "youtube" else "soundcloud"

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
        observeArtworkColors()
        observeTrackGain()
        startTrimWatcher()

        // The listen in progress when the app exits used to be lost outright — the single most common
        // way for a track to end, and the one nobody was recording (issue #33). The hook runs on a
        // normal exit and on a termination signal; it writes synchronously because the process is on
        // its way out. A hard kill still loses the current track and nothing else.
        runCatching {
            Runtime.getRuntime().addShutdownHook(
                Thread { runCatching { flushListenSession("APP_EXIT", blocking = true) } }
            )
        }


        MusicManager.onNextClick = {
            val crossfadeEnabled = playerPrefs.getCrossfadeEnabled()
            playNext(manual = true, isCrossfade = crossfadeEnabled)
        }
        MusicManager.onPreviousClick = {
            val crossfadeEnabled = playerPrefs.getCrossfadeEnabled()
            smartPrevious(isCrossfade = crossfadeEnabled)
        }

        MusicManager.onTrackChange = trackChangeHandler@{ newTrack ->
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

            val foundInQueue = _queue.find { it.id == finalTrack.id }
            if (foundInQueue != null) {
                finalTrack = foundInQueue
            }

            currentTrack = finalTrack
            MusicManager.currentTrack = finalTrack

            // Colours are not extracted from here any more: this callback only fires for direct
            // URI launches, so hanging the dynamic theme off it meant normal playback never
            // updated it at all (issue #33). [observeArtworkColors] watches currentTrack instead.
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
                val sorted =
                    playlists.sortedWith(compareByDescending<LocalPlaylist> { it.isUserCreated || it.id < 0 }.thenByDescending { it.addedAt })
                userPlaylists.addAll(sorted)
            }
        }

        viewModelScope.launch {
            MusicManager.trackDeletedFlow.collect { deletedTrackId: Long ->
                val wasCurrentTrack = currentTrack?.id == deletedTrackId
                val idx = _queue.indexOfFirst { it.id == deletedTrackId }
                if (idx != -1) {
                    removeTrackFromQueue(idx)
                } else {
                    _originalQueue.removeAll { it.id == deletedTrackId }
                }

                if (_queue.isEmpty()) {
                    MusicManager.player.stop()
                    currentTrack = null
                    isPlaying = false
                } else if (wasCurrentTrack) {
                    val targetIdx = currentQueueIndex.coerceIn(0, _queue.size - 1)
                    playTrackAtIndex(targetIdx, addToHistory = false)
                }
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
        } catch (_: Exception) {
        }

        restoreSession()
        syncWithCurrentPlayback()

        initKdeMpris2Service()
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
        } catch (_: Exception) {
        }
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

    private fun initKdeMpris2Service() {
        if (kdeMpris2Service == null) {
            try {
                kdeMpris2Service = com.alananasss.kittytune.data.KdeMpris2Service(
                    onPlay = { player.play() },
                    onPause = { player.pause() },
                    onPlayPause = { togglePlayPause() },
                    onNext = { playNext() },
                    onPrevious = { smartPrevious() },
                    onSeek = { seekTo(it) },
                    onVolume = { v -> updateVolume(v.toFloat()) },
                    onShuffle = { s ->
                        if (s != shuffleEnabled) toggleShuffle()
                    },
                    onLoopStatus = { ls ->
                        val target = when (ls) {
                            com.alananasss.kittytune.data.KdeMpris2Service.LoopStatus.None -> RepeatMode.NONE
                            com.alananasss.kittytune.data.KdeMpris2Service.LoopStatus.Track -> RepeatMode.ONE
                            com.alananasss.kittytune.data.KdeMpris2Service.LoopStatus.Playlist -> RepeatMode.ALL
                        }
                        if (repeatMode != target) {
                            repeatMode = target
                            applyRepeatMode()
                            saveStateAsync(saveQueue = false)
                            kdeMpris2Service?.updateLoopStatus(ls)
                        }
                    }
                )
            } catch (e: Exception) {
                println("KDE MPRIS2 service init exception: ${e.message}")
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

        initKdeMpris2Service()
        val kdeService = kdeMpris2Service
        if (kdeService != null) {
            kdeService.updateMedia(currentTrack, isPlaying, currentPosition)
            kdeService.updateVolume(volume.toDouble())
            kdeService.updateShuffle(shuffleEnabled)
            kdeService.updateLoopStatus(
                when (repeatMode) {
                    RepeatMode.NONE -> com.alananasss.kittytune.data.KdeMpris2Service.LoopStatus.None
                    RepeatMode.ALL -> com.alananasss.kittytune.data.KdeMpris2Service.LoopStatus.Playlist
                    RepeatMode.ONE -> com.alananasss.kittytune.data.KdeMpris2Service.LoopStatus.Track
                }
            )
        }

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
        } catch (_: Exception) {
        }
        mprisService = null

        try {
            kdeMpris2Service?.close()
        } catch (_: Exception) {
        }
        kdeMpris2Service = null

        try {
            windowsSmtcService?.close()
        } catch (_: Exception) {
        }
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
            if (shuffleEnabled && _queue.size > 1) {
                applyShuffle(currentQueueIndex)
                updateQueueState()
            }

            try {
                isPlaying = player.isPlaying
                duration = player.duration.coerceAtLeast(0L)
                currentPosition = player.currentPosition
                if (isPlaying) startProgressUpdate()
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

    fun updateLyricsFullScreenFontSize(size: Float) {
        lyricsFullScreenFontSize = size
        playerPrefs.setLyricsFullScreenFontSize(size)
    }

    fun updateLyricsAlignment(alignment: LyricsAlignment) {
        lyricsAlignment = alignment
        playerPrefs.setLyricsAlignment(alignment)
    }

    fun updateLyricsFullScreenAlignment(alignment: LyricsAlignment) {
        lyricsFullScreenAlignment = alignment
        playerPrefs.setLyricsFullScreenAlignment(alignment)
    }

    fun updateLyricsDisplayStyle(style: LyricsDisplayStyle) {
        lyricsDisplayStyle = style
        playerPrefs.setLyricsDisplayStyle(style)
    }

    fun updateLyricsFullScreenDisplayStyle(style: LyricsDisplayStyle) {
        lyricsFullScreenDisplayStyle = style
        playerPrefs.setLyricsFullScreenDisplayStyle(style)
    }


    fun togglePreciseLyricsSearch(enabled: Boolean) {
        isPreciseLyricsSearchEnabled = enabled
        playerPrefs.setPreciseLyricsSearchEnabled(enabled)
        currentTrack?.let { loadLyrics(it) }
    }

    fun openLyrics(targetTrack: Track? = null, forceSheet: Boolean = false) {
        val target = targetTrack ?: currentTrack ?: return
        val isDifferentTrack = target.id != currentTrack?.id
        if (isDifferentTrack) playPlaylist(listOf(target), 0)

        if (!forceSheet && playerPrefs.getInlineLyricsEnabled()) {
            toggleInlineLyrics()
        } else {
            lyricsMode = if (lyricsLines.isNotEmpty()) {
                LyricsMode.SYNCED
            } else {
                LyricsMode.PLAIN
            }
            showMenuSheet = false
            showLyricsSheet = if (isDifferentTrack) true else !showLyricsSheet
        }
    }

    private fun generateSearchQueries(title: String, uploader: String): List<String> {
        val queries = mutableSetOf<String>()

        val cleanArtist = uploader.replace(Regex("[^\\p{L}\\p{Nd}\\s\\-&'$]"), "").trim()

        val cleanTitle = title.replace(Regex("(?i)\\[.*?\\]|\\(.*?\\)"), "").trim()

        var parsedArtist = cleanArtist
        var parsedTitle = cleanTitle
        if (cleanTitle.contains("-")) {
            val parts = cleanTitle.split("-", limit = 2)
            parsedArtist = parts[0].replace(Regex("[^\\p{L}\\p{Nd}\\s\\-&'$]"), "").trim()
            parsedTitle = parts[1].trim()
        } else if (title.contains("-")) {
            val parts = title.split("-", limit = 2)
            parsedArtist = parts[0].replace(Regex("[^\\p{L}\\p{Nd}\\s\\-&'$]"), "").trim()
            parsedTitle = parts[1].replace(Regex("(?i)\\[.*?\\]|\\(.*?\\)"), "").trim()
        }
        val ultraCleanTitle = parsedTitle.replace(Regex("(?i)\\s+(w/|feat\\.?|ft\\.?|prod\\.?|x(?=\\s)).*"), "").trim()
        if (ultraCleanTitle.isNotBlank() && parsedArtist.isNotBlank()) queries.add("$ultraCleanTitle $parsedArtist")
        if (ultraCleanTitle.isNotBlank() && cleanArtist.isNotBlank() && cleanArtist != parsedArtist) queries.add("$ultraCleanTitle $cleanArtist")
        if (parsedTitle.isNotBlank() && parsedArtist.isNotBlank()) queries.add("$parsedTitle $parsedArtist")
        if (ultraCleanTitle.isNotBlank()) queries.add(ultraCleanTitle)
        if (parsedTitle.isNotBlank()) queries.add(parsedTitle)
        queries.add(cleanTitle)

        return queries.filter { it.length > 2 }.toList()
    }

    /**
     * The settings a resolved lookup depends on. Two of them change the words themselves rather
     * than how they are shown, so a cache entry is only reusable while all three still hold.
     */
    private data class LyricsVariant(
        val providerPreference: String,
        val translationLang: String?,
        val romanized: Boolean,
    )

    private fun currentLyricsVariant() = LyricsVariant(
        providerPreference = lyricsProvider.name,
        translationLang =
            if (playerPrefs.getLyricsTranslationEnabled()) playerPrefs.getLyricsTranslationLang() else null,
        romanized = isRomanizationEnabled,
    )

    /** A resolved lookup, ready either to be shown or to be parked in the cache. */
    private data class LyricsPayload(
        val lines: List<LyricLine>,
        val plain: String?,
        val provider: String?,
    ) {
        val isEmpty: Boolean get() = lines.isEmpty() && plain.isNullOrBlank()
    }

    /**
     * One provider result in the running, with its lyrics and its plain text kept together.
     *
     * They travel as a pair on purpose. The previous pass reused a single `finalPlain` across
     * every generated query, so a query that only turned up plain text left it behind for a later
     * query that found synced lines but no plain text — and the lyrics screen then showed the
     * words of one song beside the timings of another (issue #33).
     */
    private data class LyricsCandidate(
        val lines: List<LyricLine>,
        val plain: String?,
        val provider: String,
        val matchScore: Float,
        /**
         * Small nudge for the provider named first in the settings, deliberately smaller than any
         * meaningful difference in [matchScore]: it decides a genuine tie, it does not let the
         * preferred provider win with lyrics that fit the track less well.
         */
        val providerBonus: Float = 0f,
    ) {
        /**
         * Identity first, then sync, then match quality — see [LyricsMatcher.rank] for why that order
         * had to change.
         */
        val rank: Float get() = LyricsMatcher.rank(syncTier, matchScore, providerBonus)

        /** See [LyricsMatcher.syncTier]: real timings first, provider second. */
        val syncTier: Int get() = LyricsMatcher.syncTier(lines, plain)

        val isUsable: Boolean get() = syncTier > LyricsMatcher.SYNC_TIER_NONE
    }

    private fun loadLyrics(track: Track) {
        lyricsJob?.cancel()
        lyricsLines.clear()
        lyricsOffset = 0L
        // Cleared before it is read, so a track with no speed of its own cannot inherit the last
        // track's for the moment it takes to answer.
        trackAutoScrollSpeed = null
        viewModelScope.launch(Dispatchers.IO) {
            val speed = com.alananasss.kittytune.data.LyricsScrollSpeedRepository.get(track.id)
            withContext(Dispatchers.Main) {
                // Only if we are still on the track that asked.
                if (currentTrack?.id == track.id) trackAutoScrollSpeed = speed
            }
        }
        showLyricsOffsetControls = false
        isLyricsLoading = true
        isSearchingLyrics = false
        rawPlainLyrics = null

        val queries = generateSearchQueries(track.title ?: "", track.user?.username ?: "")
        manualSearchQuery = queries.firstOrNull() ?: ""

        lyricsJob = viewModelScope.launch(Dispatchers.IO) {
            // A previously chosen manual search result wins over automatic matching.
            val storedOverride = getLyricsOverride(track.id)
            if (storedOverride != null) {
                withContext(Dispatchers.Main) { selectUnifiedLyricResult(storedOverride) }
                return@launch
            }

            val variant = currentLyricsVariant()

            // Cache first: a hit puts the lyrics on screen in this frame instead of after a
            // dozen HTTP round trips, which is what made them arrive ten seconds into the song
            // and flash "unavailable" until then (issue #33).
            val cached = LyricsCache.get(
                track.id,
                variant.providerPreference,
                variant.translationLang,
                variant.romanized,
            )
            if (cached != null) {
                withContext(Dispatchers.Main) {
                    applyLyricsPayload(LyricsPayload(cached.lines, cached.plain, cached.provider))
                }
                prefetchQueueLyrics()
                return@launch
            }

            val local = loadEmbeddedLyrics(track)
            if (local != null) {
                withContext(Dispatchers.Main) { applyLyricsPayload(local) }
                prefetchQueueLyrics()
                return@launch
            }

            val payload = resolveLyrics(track, queries, variant)
            if (!isActive) return@launch

            LyricsCache.put(
                track.id,
                LyricsCache.Entry(
                    found = payload != null && !payload.isEmpty,
                    lines = payload?.lines.orEmpty(),
                    plain = payload?.plain,
                    provider = payload?.provider,
                    providerPreference = variant.providerPreference,
                    translationLang = variant.translationLang,
                    romanized = variant.romanized,
                ),
            )

            withContext(Dispatchers.Main) {
                applyLyricsPayload(payload ?: LyricsPayload(emptyList(), null, null))
            }
            prefetchQueueLyrics()
        }
    }

    /** Publishes a resolved lookup to the screen. Main thread only. */
    private fun applyLyricsPayload(payload: LyricsPayload) {
        lyricsLines.clear()
        lyricsLines.addAll(payload.lines)
        rawPlainLyrics = payload.plain
        lyricsMode = if (payload.lines.isNotEmpty()) LyricsMode.SYNCED else LyricsMode.PLAIN
        isLyricsLoading = false
        if (payload.lines.isNotEmpty() || !payload.plain.isNullOrBlank()) isSearchingLyrics = false
    }

    /** Lyrics tagged into the downloaded file, when the user asked for those to come first. */
    private suspend fun loadEmbeddedLyrics(track: Track): LyricsPayload? {
        if (!playerPrefs.getLyricsPreferLocal()) return null
        val localTrack = DownloadManager.getLocalTrack(track.id) ?: return null
        if (localTrack.localAudioPath.isEmpty()) return null
        val raw = LyricsUtils.extractLocalLyrics(localTrack.localAudioPath)
        if (raw.isNullOrBlank()) return null
        val trackDurationMs = track.durationMs ?: 0L
        val parsed = LyricsUtils.parseLyricsContent(raw, trackDurationMs)
        return LyricsPayload(
            lines = parsed.ifEmpty { listOf(LyricLine(raw, 0, trackDurationMs)) },
            plain = raw.takeIf { parsed.isEmpty() },
            provider = "LOCAL",
        )
    }

    /**
     * Warms the cache for the track queued after this one, so moving to it shows its lyrics
     * immediately rather than starting the whole search over. Runs detached from [lyricsJob]:
     * cancelling the current lookup should not throw away work already paid for.
     */
    private fun prefetchQueueLyrics() {
        val next = _queue.getOrNull(currentQueueIndex + 1) ?: return
        if (next.id == currentTrack?.id) return
        val variant = currentLyricsVariant()
        if (LyricsCache.get(next.id, variant.providerPreference, variant.translationLang, variant.romanized) != null) {
            return
        }
        if (getLyricsOverride(next.id) != null) return

        lyricsPrefetchJob?.cancel()
        lyricsPrefetchJob = viewModelScope.launch(Dispatchers.IO) {
            val queries = generateSearchQueries(next.title ?: "", next.user?.username ?: "")
            val payload = runCatching { resolveLyrics(next, queries, variant) }.getOrNull()
            if (!isActive) return@launch
            LyricsCache.put(
                next.id,
                LyricsCache.Entry(
                    found = payload != null && !payload.isEmpty,
                    lines = payload?.lines.orEmpty(),
                    plain = payload?.plain,
                    provider = payload?.provider,
                    providerPreference = variant.providerPreference,
                    translationLang = variant.translationLang,
                    romanized = variant.romanized,
                ),
            )
        }
    }

    /**
     * Runs the whole provider search for [track] without touching any UI state, so the same code
     * serves the track being played and the prefetch of the one after it.
     *
     * @return the best result found, or null when no provider had anything usable.
     */
    private suspend fun resolveLyrics(
        track: Track,
        queries: List<String>,
        variant: LyricsVariant,
    ): LyricsPayload? = coroutineScope {
        val target = LyricsMatcher.Target(
            title = track.title ?: "",
            artist = track.user?.username ?: "",
            durationMs = track.durationMs ?: 0L,
        )
        val trackDurationMs = track.durationMs ?: 0L
        val preferLrcLib = lyricsProvider == LyricsProvider.OPEN_SOURCE
        val preferredProvider = if (preferLrcLib) "LRCLIB" else "MUSIXMATCH"

        var best: LyricsCandidate? = null

        for (query in queries) {
            if (!isActive) return@coroutineScope null

            val found = if (preferLrcLib) {
                searchLrcLibCandidates(query, target, trackDurationMs)
            } else {
                val mxm = async { searchMusixmatchCandidates(query, target, trackDurationMs, variant) }
                val lrc = async { searchLrcLibCandidates(query, target, trackDurationMs) }
                mxm.await() + lrc.await()
            }
            val candidates = found.map { candidate ->
                if (candidate.provider == preferredProvider) {
                    candidate.copy(providerBonus = PROVIDER_PREFERENCE_BONUS)
                } else {
                    candidate
                }
            }

            val bestOfQuery = candidates.filter { it.isUsable }.maxByOrNull { it.rank }
            if (bestOfQuery != null && bestOfQuery.rank > (best?.rank ?: Float.NEGATIVE_INFINITY)) {
                best = bestOfQuery
            }

            // Word-level sync from a confident match is as good as this gets, so stop spending
            // requests on the remaining, progressively looser, generated queries.
            val current = best
            if (current != null &&
                current.syncTier >= LyricsMatcher.SYNC_TIER_WORD &&
                current.matchScore >= 0.6f
            ) break
        }

        // Genius only once everything else has come up empty: it never carries timings, so it is
        // about having the words at all rather than about having them in sync (issue #33).
        if (best == null) {
            best = searchGeniusCandidate(queries, target)
        }

        val candidate = best ?: return@coroutineScope null
        // Only real sync is published as sync. A single timed line is what a provider returns when
        // it has the words but not the timings, and passing that through would put the lyrics view
        // in synced mode with one line in it instead of showing the plain text it also sent.
        val syncedLines =
            if (candidate.syncTier >= LyricsMatcher.SYNC_TIER_LINE) candidate.lines else emptyList()
        LyricsPayload(
            lines = decorateLyrics(syncedLines, variant),
            // Lines that turned out not to be synced are still the words: keep them as the plain
            // text rather than dropping them along with their useless timings.
            plain = candidate.plain
                ?: candidate.lines.takeIf { it.isNotEmpty() }?.joinToString("\n") { it.text },
            provider = candidate.provider,
        )
    }

    /**
     * LrcLib hits for one query. Every hit keeps its own plain text alongside its own timings, so
     * the two can never be mixed between songs.
     */
    private suspend fun searchLrcLibCandidates(
        query: String,
        target: LyricsMatcher.Target,
        trackDurationMs: Long,
    ): List<LyricsCandidate> {
        val results = try {
            LrcLibClient.api.searchLyrics(query)
        } catch (e: Exception) {
            emptyList()
        }
        return results
            .filter { LyricsMatcher.isAcceptable(it.name, it.artistName, target) }
            .map { result ->
                val synced = result.lyricsfile ?: result.syncedLyrics
                val lines = synced
                    ?.takeIf { it.isNotBlank() }
                    ?.let { LyricsUtils.parseLyricsContent(it, trackDurationMs) }
                    ?: emptyList()
                LyricsCandidate(
                    lines = lines,
                    plain = result.plainLyrics,
                    provider = "LRCLIB",
                    matchScore = LyricsMatcher.score(result.name, result.artistName, result.duration, target),
                )
            }
    }

    /**
     * Musixmatch hits for one query.
     *
     * Search returns metadata only, so the actual lyrics cost a second round trip per track —
     * which is why exactly one entry gets fetched: the one that both matches best and advertises
     * the richest sync.
     */
    private suspend fun searchMusixmatchCandidates(
        query: String,
        target: LyricsMatcher.Target,
        trackDurationMs: Long,
        variant: LyricsVariant,
    ): List<LyricsCandidate> {
        val results = try {
            MusixmatchClient.search(query)
        } catch (e: Exception) {
            emptyList()
        }
        // Exactly one hit is fetched, so this choice is final: whatever loses here never gets a second
        // chance from this provider. It used to be ranked tier-first, which meant that when the response
        // held both the right song and a better-synchronised wrong one, the wrong one was the only entry
        // the app ever downloaded — and no amount of ranking further down could recover from that
        // (issue #33). Same order as everywhere else now.
        val pick = results
            .filter { LyricsMatcher.isAcceptable(it.trackName, it.artistName, target) }
            .maxByOrNull { hit ->
                LyricsMatcher.rank(
                    syncTier = hit.hasRichSync * 2 + hit.hasSubtitles,
                    matchScore = LyricsMatcher.score(
                        hit.trackName, hit.artistName, hit.trackLength.toDouble(), target
                    ),
                )
            } ?: return emptyList()

        val data = try {
            MusixmatchClient.getLyricsData(
                pick.trackId,
                trackDurationMs,
                variant.translationLang,
                variant.romanized,
            )
        } catch (e: Exception) {
            return emptyList()
        }
        return listOf(
            LyricsCandidate(
                lines = data.first,
                plain = data.second,
                provider = "MUSIXMATCH",
                matchScore = LyricsMatcher.score(
                    pick.trackName,
                    pick.artistName,
                    pick.trackLength.toDouble(),
                    target,
                ),
            )
        )
    }

    /** The best Genius page for any of the generated queries, as plain text. */
    private suspend fun searchGeniusCandidate(
        queries: List<String>,
        target: LyricsMatcher.Target,
    ): LyricsCandidate? {
        // The first few queries are the tightest; the looser tail is not worth another round trip
        // against a provider that cannot give timings anyway.
        for (query in queries.take(3)) {
            if (!currentCoroutineContext().isActive) return null
            val pick = com.alananasss.kittytune.data.network.GeniusClient.search(query)
                .filter { LyricsMatcher.isAcceptable(it.title, it.artist, target) }
                .maxByOrNull { LyricsMatcher.score(it.title, it.artist, 0.0, target) }
                ?: continue
            val plain = com.alananasss.kittytune.data.network.GeniusClient.lyrics(pick.id) ?: continue
            return LyricsCandidate(
                lines = emptyList(),
                plain = plain,
                provider = "GENIUS",
                matchScore = LyricsMatcher.score(pick.title, pick.artist, 0.0, target),
            )
        }
        return null
    }

    /** Adds the translation and romanisation the user asked for, when the provider did not. */
    private suspend fun decorateLyrics(
        lines: List<LyricLine>,
        variant: LyricsVariant,
    ): List<LyricLine> {
        if (lines.isEmpty()) return lines
        val wantsTranslation = variant.translationLang != null && lines.none { it.translation != null }
        val wantsRomanization = variant.romanized && lines.none { it.romanization != null }
        if (!wantsTranslation && !wantsRomanization) return lines

        val texts = lines.map { it.text }.filter { it.isNotBlank() }.distinct()
        val translations = if (wantsTranslation) {
            com.alananasss.kittytune.data.network.FreeTranslator
                .translateMissing(texts, variant.translationLang!!)
        } else {
            emptyMap()
        }
        val romanizations = if (wantsRomanization) {
            com.alananasss.kittytune.data.network.FreeTranslator.getRomanization(texts)
        } else {
            emptyMap()
        }
        return lines.map { line ->
            line.copy(
                translation = line.translation ?: translations[line.text.trim()],
                romanization = line.romanization ?: romanizations[line.text.trim()],
            )
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

    /**
     * Searches a provider for lyrics to pick by hand.
     *
     * Only one search runs at a time, and the results replace rather than accumulate. Both halves of that
     * were needed: pressing the button twice used to start two searches that each cleared the list and then
     * appended to it, so the second one's results landed on top of the first one's — and since both searched
     * the same thing, every row appeared twice. `LazyColumn` keys rows by id, two rows with one id is a
     * duplicate key, and a duplicate key takes the window down: `Key "205071906MUSIXMATCH" was already used`
     * (issue #33).
     *
     * The de-duplication is a second, independent guard. A provider is entitled to return the same track
     * twice, and when it does, the list must not be able to crash the app.
     */
    private var manualLyricSearchJob: Job? = null

    fun searchLyricsManual(query: String, provider: String = manualSearchProvider) {
        if (query.isBlank()) return
        // Cancelled, not merely ignored: the older search would otherwise finish later and append its rows
        // to the newer search's list, which is both wrong and a crash.
        manualLyricSearchJob?.cancel()
        isLyricsLoading = true
        unifiedLyricSearchResults.clear()
        manualSearchProvider = provider

        manualLyricSearchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val mapped = when (provider) {
                    "LRCLIB" -> LrcLibClient.api.searchLyrics(query).map {
                        UnifiedLyricResult(
                            it.id.toString(),
                            it.name,
                            it.artistName,
                            it.albumName,
                            it.duration,
                            !it.syncedLyrics.isNullOrEmpty() || !it.lyricsfile.isNullOrEmpty(),
                            false,
                            "LRCLIB"
                        )
                    }

                    "GENIUS" -> com.alananasss.kittytune.data.network.GeniusClient.search(query).map {
                        UnifiedLyricResult(
                            it.id.toString(),
                            it.title ?: "",
                            it.artist,
                            it.releaseDate,
                            // Genius does not report a track length, and it never has timings.
                            0.0,
                            false,
                            false,
                            "GENIUS"
                        )
                    }

                    else -> MusixmatchClient.search(query).map {
                        UnifiedLyricResult(
                            it.trackId.toString(),
                            it.trackName,
                            it.artistName,
                            it.albumName,
                            it.trackLength.toDouble(),
                            it.hasSubtitles == 1,
                            it.hasRichSync == 1,
                            "MUSIXMATCH"
                        )
                    }
                }
                // Replaced in one go, and distinct by the identity the list is keyed on.
                val distinct = mapped.distinctBy { it.id + it.provider }
                withContext(Dispatchers.Main) {
                    unifiedLyricSearchResults.clear()
                    unifiedLyricSearchResults.addAll(distinct)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) { isLyricsLoading = false }
            }
        }
    }

    fun selectUnifiedLyricResult(result: UnifiedLyricResult) {
        // The automatic search has to be cut dead here. It was left running, so a slow resolution —
        // and it is slow once Genius is in the chain — finished after the manual pick and overwrote
        // it with whatever it had found, which is the "after some time it adds synchronised text
        // that does not match this song" report in issue #33.
        lyricsJob?.cancel()
        lyricsPrefetchJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            isLyricsLoading = true
            var finalLines = emptyList<LyricLine>()
            var finalPlain: String? = null

            when (result.provider) {
                "LRCLIB" -> {
                    try {
                        val lrcData = LrcLibClient.api.getLyricsById(result.id.toLong())
                        val lyricsText = lrcData.lyricsfile ?: lrcData.syncedLyrics
                        if (!lyricsText.isNullOrEmpty()) {
                            finalLines = LyricsUtils.parseLyricsContent(lyricsText, duration)
                        }
                        finalPlain = lrcData.plainLyrics
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                "GENIUS" -> {
                    finalPlain = com.alananasss.kittytune.data.network.GeniusClient
                        .lyrics(result.id.toLongOrNull() ?: 0L)
                }

                else -> {
                    val targetLang =
                        if (playerPrefs.getLyricsTranslationEnabled()) playerPrefs.getLyricsTranslationLang() else null
                    lastFetchedMxmTrackId = result.id.toLongOrNull()
                    val data =
                        MusixmatchClient.getLyricsData(result.id.toLong(), duration, targetLang, isRomanizationEnabled)
                    finalLines = data.first
                    finalPlain = data.second
                }
            }

            withContext(Dispatchers.Main) {
                if (finalLines.isNotEmpty()) {
                    lyricsLines.clear()
                    lyricsLines.addAll(finalLines)
                    rawPlainLyrics = finalPlain
                    lyricsMode = LyricsMode.SYNCED
                } else if (!finalPlain.isNullOrBlank()) {
                    // A result with no timings must not cost the user the synced lyrics they
                    // already had: synced outranks plain, so the plain text is added beside them
                    // and the view stays where it is (issue #33). Only with nothing synced on
                    // screen does picking plain text switch the view to it.
                    rawPlainLyrics = finalPlain
                    if (lyricsLines.isEmpty()) lyricsMode = LyricsMode.PLAIN
                } else {
                    lyricsLines.clear()
                    rawPlainLyrics = null
                    lyricsMode = LyricsMode.PLAIN
                }
                isLyricsLoading = false
                isSearchingLyrics = false
            }

            // Remember the manual pick for the current track (issue #27), and drop the cached
            // automatic match so it cannot come back on the next play.
            if (finalLines.isNotEmpty() || !finalPlain.isNullOrBlank()) {
                currentTrack?.id?.let {
                    saveLyricsOverride(it, result)
                    LyricsCache.invalidate(it)
                }
            }
        }
    }

    fun selectLyricResult(result: LrcLibResponse) {
        viewModelScope.launch(Dispatchers.IO) { processLyricsResponse(result, duration) }
    }

    private fun cleanTitleNoise(title: String): String = title.replace(Regex("\\(.*?\\)|\\[.*?]"), "")
        .replace(Regex("(?i)(official video|lyrics|ft\\.|feat\\.|prod\\.)"), "").trim()

    fun navigateToTrackDetails(trackId: Long, initialTab: Int = 0) {
        showMenuSheet = false; showDetailsSheet = false; navigateToPlaylistId = "track_detail:$trackId?tab=$initialTab"
    }

    fun shareTrack(track: Track) {
        val urlToShare = track.permalinkUrl ?: "https://soundcloud.com/tracks/${track.id}"
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

    fun navigateToTag(tagName: String) {
        showDetailsSheet = false; isPlayerExpanded = false; navigateToPlaylistId = "tag:$tagName"
    }

    fun navigateToArtist(userId: Long) {
        if (userId <= 0) return
        showDetailsSheet = false
        showMenuSheet = false
        showCommentsSheet = false
        isPlayerExpanded = false
        navigateToPlaylistId = "profile:$userId"
    }

    /** True when the track comes from the Spotify catalog (metadata-only source). */
    fun isSpotifyTrack(track: Track?): Boolean {
        if (track == null) return false
        return track.source == "spotify"
            || track.user?.urn?.startsWith("spotify") == true
            || track.permalinkUrl?.contains("open.spotify.com") == true
    }

    /**
     * Best-effort extraction of the Spotify base62 id from any track form:
     * permalink, share URL or artist URN. Only meaningful for catalog tracks.
     */
    fun getSpotifyTrackId(track: Track?): String? {
        if (!isSpotifyTrack(track)) return null
        val t = track ?: return null

        // Catalog tracks carry the raw base62 id as permalink.
        t.permalink?.takeIf { it.isNotBlank() && !it.contains('/') && !it.contains(':') && !it.contains('.') }
            ?.let { return it }

        t.permalinkUrl?.takeIf { it.contains("/track/") || it.startsWith("spotify") }
            ?.let { return com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(it).ifBlank { null } }

        // Last resort: the synthetic user URN (spotify:artist:<id>).
        t.user?.urn?.takeIf { it.startsWith("spotify") }
            ?.let { return com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(it).ifBlank { null } }

        return null
    }

    /** Resolves the primary Spotify artist id of a catalog track, if any. */
    fun getSpotifyTrackArtistId(track: Track?): String? {
        if (!isSpotifyTrack(track)) return null
        track?.artists?.firstOrNull()?.id?.takeIf { it.isNotBlank() }?.let { return it }
        return track?.user?.urn
            ?.takeIf { it.startsWith("spotify:artist:") }
            ?.removePrefix("spotify:artist:")
            ?.ifBlank { null }
            ?: track?.user?.permalink?.takeIf { it.isNotBlank() }
    }

    fun navigateToSpotifyArtist(artistId: String) {
        if (artistId.isBlank()) return
        showDetailsSheet = false
        showMenuSheet = false
        showCommentsSheet = false
        isPlayerExpanded = false
        navigateToPlaylistId = "spotify_artist:$artistId"
    }

    fun navigateToAlbum(albumId: String) {
        val cleanId = com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(albumId)
        if (cleanId.isBlank()) return
        showDetailsSheet = false
        showMenuSheet = false
        showCommentsSheet = false
        isPlayerExpanded = false
        navigateToPlaylistId = "spotify:album:$cleanId"
    }

    // ---- Multi-artist selection (tracks credited to several Spotify artists) ----

    var showSelectArtistDialog by mutableStateOf(false)
        private set
    var selectedArtistDialogTrack by mutableStateOf<Track?>(null)
        private set

    /**
     * Artists the selection dialog offers. Held separately from the track so a
     * caller without one — an album or radio header — can open the same dialog.
     */
    var selectArtistOptions by mutableStateOf<List<com.alananasss.kittytune.data.spotify.SpotifyArtistRef>>(emptyList())
        private set

    /**
     * Entry point for any "open artist" action on a track: with a single
     * catalog artist it navigates directly, with several ones it opens the
     * selection dialog (same behavior as the Android player).
     */
    fun navigateToTrackArtist(track: Track?) {
        if (track == null) return
        val artists = navigableArtists(track.artists)
        when {
            artists.size > 1 -> openSelectArtistDialog(artists, track)
            artists.size == 1 -> navigateToSpotifyArtist(artists.first().id)
            isSpotifyTrack(track) -> {
                val fallbackId = getSpotifyTrackArtistId(track)
                if (!fallbackId.isNullOrBlank()) navigateToSpotifyArtist(fallbackId)
                else track.user?.id?.takeIf { it > 0 }?.let { navigateToArtist(it) }
            }
            else -> track.user?.id?.takeIf { it > 0 }?.let { navigateToArtist(it) }
        }
    }

    /**
     * Same routing for a bare artist list (Spotify album / radio headers, where
     * the credited artists don't hang off a single track): one artist opens the
     * profile, several open the picker, none falls back to the SoundCloud user.
     */
    fun navigateToArtistChoice(
        artists: List<com.alananasss.kittytune.data.spotify.SpotifyArtistRef>?,
        fallbackUserId: Long? = null
    ) {
        val navigable = navigableArtists(artists)
        when {
            navigable.size > 1 -> openSelectArtistDialog(navigable, null)
            navigable.size == 1 -> navigateToSpotifyArtist(navigable.first().id)
            else -> fallbackUserId?.takeIf { it > 0 }?.let { navigateToArtist(it) }
        }
    }

    private fun openSelectArtistDialog(
        artists: List<com.alananasss.kittytune.data.spotify.SpotifyArtistRef>,
        track: Track?
    ) {
        // The sheet the click came from is stale once the picker is up.
        showMenuSheet = false
        showDetailsSheet = false
        showCommentsSheet = false
        selectArtistOptions = artists
        selectedArtistDialogTrack = track
        showSelectArtistDialog = true
    }

    /** Artists we can actually open a profile for, without duplicates. */
    private fun navigableArtists(
        artists: List<com.alananasss.kittytune.data.spotify.SpotifyArtistRef>?
    ): List<com.alananasss.kittytune.data.spotify.SpotifyArtistRef> =
        artists.orEmpty().filter { it.id.isNotBlank() }.distinctBy { it.id }

    fun dismissSelectArtistDialog() {
        showSelectArtistDialog = false
        selectedArtistDialogTrack = null
        selectArtistOptions = emptyList()
    }

    fun navigateToEditTrack(trackId: Long) {
        if (trackId <= 0) return
        showDetailsSheet = false
        showMenuSheet = false
        showCommentsSheet = false
        isPlayerExpanded = false
        navigateToPlaylistId = "edit_track:$trackId"
    }

    fun navigateToUpload() {
        showDetailsSheet = false
        showMenuSheet = false
        showCommentsSheet = false
        isPlayerExpanded = false
        navigateToPlaylistId = "upload"
    }

    fun navigateToContext() {
        currentContext?.let { context ->
            var destination = context.navigationId
            if (destination.startsWith("playlist_detail:")) {
                destination = destination.removePrefix("playlist_detail:")
            } else if (destination.startsWith("playlist_")) {
                destination = destination.removePrefix("playlist_")
            }
            navigateToPlaylistId = destination
        }
    }

    fun onNavigationHandled() {
        navigateToPlaylistId = null
    }

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
                    .mapNotNull { u ->
                        val userId = u.urn?.substringAfterLast(':')?.toLongOrNull() ?: 0L
                        if (userId <= 0) return@mapNotNull null
                        User(
                            id = userId,
                            username = u.username,
                            avatarUrl = u.avatarUrl,
                            verified = u.verified ?: false,
                            urn = u.urn
                        )
                    }
                socialLikerUser = users.firstOrNull()
                // Hand the answer to the shared cache so the track's rows show the same faces
                // without asking for them again (issue #33).
                SocialProofRepository.putLikersForTrack(trackId, users)
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
        if (refresh) {
            commentsList.clear(); commentNextHref = null
        }
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
            val query =
                "query UserInteractions(" + '$' + "parentUrn: String!, " + '$' + "interactionTypeUrn: String!, " + '$' + "targetUrns: [String!]!) { user: userInteractions(parentUrn: " + '$' + "parentUrn, interactionTypeUrn: " + '$' + "interactionTypeUrn, targetUrns: " + '$' + "targetUrns) { targetUrn, userInteraction, interactionCounts { count, interactionTypeValueUrn } } }"
            val variables = GraphQlVariablesUserCheck(parentUrn = parentUrn, targetUrns = batchUrns)
            val request = GraphQlRequest("UserInteractions", query, variables)
            try {
                val responseJson = api.postGraphQl("https://graph.soundcloud.com/graphql", request)
                val data = gson.fromJson(responseJson, GraphQlResponseUserInteractions::class.java)
                data.data?.user?.forEach { interaction ->
                    val idStr = interaction.targetUrn.substringAfterLast(":")
                    val commentId = idStr.toLongOrNull() ?: 0L
                    val isLikedByMe = interaction.userInteraction != null
                    val totalLikes =
                        interaction.interactionCounts?.find { it.type == "sc:interactiontypevalue:like" }?.count
                    updateCommentInList(commentId, isLikedByMe, totalLikes)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
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

    fun startReplying(comment: Comment) {
        replyingToComment = comment
    }

    fun cancelReplying() {
        replyingToComment = null
    }

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
            } finally {
                isPostingComment = false
            }
        }
    }

    fun onCaptchaSolved() {
        captchaUrl = null; SessionManager.requestSessionRefresh(force = true)
        if (pendingCommentBody != null) {
            emitUiEvent(str("msg_retrying")); postComment(pendingCommentBody!!, pendingCommentTimestamp)
        }
    }

    fun toggleCommentLike(comment: Comment) {
        val foundIndex = commentsList.indexOfFirst { it.id == comment.id }
        var parentIndex = -1
        if (foundIndex == -1) {
            for (i in commentsList.indices) {
                if (commentsList[i].replies?.any { it.id == comment.id } == true) {
                    parentIndex = i; break
                }
            }
        }
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
                    val query =
                        "mutation UpsertInteraction(" + '$' + "input: InteractionInput!) { upsertInteraction(input: " + '$' + "input) { interactionTypeUrn } }"
                    val request = GraphQlRequest("UpsertInteraction", query, GraphQlVariablesInteraction(input))
                    api.postGraphQl("https://graph.soundcloud.com/graphql", request)
                } else {
                    val query =
                        "mutation RemoveInteraction(" + '$' + "input: InteractionInput!) { removeInteraction(input: " + '$' + "input) }"
                    val request = GraphQlRequest("RemoveInteraction", query, GraphQlVariablesInteraction(input))
                    api.postGraphQl("https://graph.soundcloud.com/graphql", request)
                }
            } catch (e: Exception) {
                e.printStackTrace(); emitUiEvent(str("error_generic"))
            }
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
            } catch (e: Exception) {
                e.printStackTrace(); emitUiEvent(str("error_generic"))
            }
        }
    }

    fun fetchUserProfile() {
        if (tokenManager.isGuestMode() || tokenManager.getAccessToken().isNullOrEmpty()) return

        viewModelScope.launch {
            try {
                val me = api.getMe()
                currentUserId = me.id
                currentUser = me
                SoundCloudTelemetryTracker.updateCurrentUserId(me.id)
                com.alananasss.kittytune.data.RepostRepository.refreshReposts()
            } catch (_: Exception) {
            }
        }
    }

    fun startRadioFromTrack(track: Track) {
        showMenuSheet = false
        if (isSpotifyTrack(track)) {
            // Spotify seeds have no SoundCloud station; use the catalog radio.
            val spotifyId = getSpotifyTrackId(track)
            if (!spotifyId.isNullOrBlank()) {
                navigateToPlaylistId = "spotify_radio:$spotifyId"
                return
            }
        }
        navigateToPlaylistId = "station:${track.id}"
    }

    fun startYoutubeRadio(track: Track) {
        showMenuSheet = false
        track.permalinkUrl?.let {
            navigateToPlaylistId = "yt_radio:${URLEncoder.encode(it, "UTF-8")}"
        }
    }

    fun playPlaylist(
        tracks: List<Track>,
        startIndex: Int = 0,
        context: PlaybackContext? = null,
        maintainPlayerState: Boolean = false
    ) {
        if (tracks.isEmpty()) return
        if (!maintainPlayerState) {
            isPlayerExpanded = false
        }
        SoundCloudTelemetryTracker.onQueueReset()
        playedTrackIds = emptySet()
        _originalQueue.clear(); _originalQueue.addAll(tracks)
        _queue.clear()
        this.currentContext = context
        MusicManager.updateContext(context)

        val effectiveStartIndex = if (startIndex in tracks.indices) startIndex else 0
        val isHistoryContext = context?.navigationId == "history" || context?.navigationId?.startsWith("history") == true

        if (shuffleEnabled) {
            val clickedTrack = tracks[effectiveStartIndex]
            val rest =
                tracks.filterIndexed { index, _ -> index != effectiveStartIndex }.shuffled()
            _queue.add(clickedTrack)
            _queue.addAll(rest)
            playTrackAtIndex(0, addToHistory = (context == null || isHistoryContext))
        } else {
            _queue.addAll(tracks)
            playTrackAtIndex(effectiveStartIndex, addToHistory = (context == null || isHistoryContext))
        }

        updateQueueState(); saveStateAsync(saveQueue = true)

        if (context != null && !isHistoryContext) {
            val isStation =
                context.navigationId.contains("station") || context.navigationId.contains("yt_radio")
            val isProfile = context.navigationId.contains("profile")
            val idLong = when (context.navigationId) {
                "likes" -> -1L
                "downloads" -> -2L
                else -> context.navigationId.substringAfter(":").toLongOrNull() ?: 0L
            }
            val cleanTitle = when {
                context.displayText.contains("•") -> context.displayText.substringAfter("•").trim()
                context.displayText.contains("â€¢") -> context.displayText.substringAfter("â€¢").trim()
                else -> context.displayText.trim()
            }

            val playlistCreator = if (context.artistName != null) User(
                0,
                context.artistName,
                null,
                verified = context.isVerified
            ) else null
            val safePermalink =
                if (context.navigationId.startsWith("yt_radio:")) context.navigationId else null
            val safeArtworkUrl = if (idLong == -1L || context.navigationId == "likes" || idLong == -2L || context.navigationId == "downloads") null else context.imageUrl
            val historyPlaylist = Playlist(
                id = idLong,
                title = cleanTitle,
                artworkUrl = safeArtworkUrl,
                calculatedArtworkUrl = null,
                trackCount = tracks.size,
                user = playlistCreator,
                tracks = null,
                permalinkUrl = safePermalink
            )

            HistoryRepository.addToHistory(historyPlaylist, isStation, isProfile)
        }
    }

    fun playTrackAtPosition(track: Track, position: Long) {
        pendingSeekPosition = position; playPlaylist(listOf(track), 0); showCommentsSheet = false; isPlayerExpanded =
            true
    }

    fun skipToQueueItem(index: Int) {
        playTrackAtIndex(index, addToHistory = false)
    }

    private fun playTrackAtIndex(index: Int, addToHistory: Boolean = true, isCrossfade: Boolean = false) {
        if (index < 0 || index >= _queue.size) {
            currentContext = null; return
        }
        currentQueueIndex = index
        val trackToPlay = _queue[index]

        if (!isCrossfade) {
            MusicManager.stop()
            isPlaying = false
        }

        isLoading = true; duration = trackToPlay.durationMs ?: 0L; currentPosition = 0L
        beginListenSession(trackToPlay)
        // Alongside the session rather than anywhere else, so "played" means the same to the queue as
        // it does to the statistics.
        playedTrackIds = playedTrackIds + trackToPlay.id
        // Loaded before the stream is handed to the engine, so a trim that moves the starting point can be
        // applied as a start position rather than as a jump the listener hears (issue #33).
        loadTrimFor(trackToPlay.id)
        hasPushedRecentlyPlayed = false
        currentTrack = trackToPlay; MusicManager.currentTrack = trackToPlay

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
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else if ((finalTrack.source == "spotify" || finalTrack.user?.urn?.startsWith("spotify") == true) && finalTrack.user != null) {
                // Spotify catalog tracks: backfill the synthetic user with the
                // primary artist's identity so the profile is clickable and
                // correctly badged from the player.
                val firstArtist = finalTrack.artists?.firstOrNull()
                if (firstArtist != null && firstArtist.id.isNotBlank()) {
                    val updatedUser = finalTrack.user!!.copy(
                        verified = firstArtist.verified || finalTrack.user!!.verified,
                        avatarUrl = firstArtist.avatarUrl ?: finalTrack.user!!.avatarUrl,
                        urn = "spotify:artist:${firstArtist.id}",
                        permalink = firstArtist.id
                    )
                    finalTrack = finalTrack.copy(user = updatedUser)
                    val qIndex = _queue.indexOfFirst { it.id == trackToPlay.id }
                    if (qIndex != -1) _queue[qIndex] = finalTrack
                }
            }
            currentTrack = finalTrack
            MusicManager.currentTrack = finalTrack
            isLiked = LikeRepository.isTrackLiked(finalTrack.id)
            loadLyrics(finalTrack)
            loadSocialProof(finalTrack)
            saveStateAsync(saveQueue = false)

            SoundCloudTelemetryTracker.onTrackStarted(
                track = finalTrack,
                context = currentContext,
                isManual = true,
                startPositionMs = if (isCrossfade) currentPosition else 0L
            )

            if (addToHistory && currentContext?.navigationId?.startsWith("station:") != true && currentContext?.navigationId?.startsWith(
                    "yt_radio:"
                ) != true
            ) {
                HistoryRepository.addToHistory(finalTrack)
            }
        }
    }

    fun playNext(manual: Boolean = true, isCrossfade: Boolean = false, ignoreRepeatOne: Boolean = false) {
        if (isAutoplayRadioLoading) return

        if (manual) {
            currentTrack?.let { track ->
                flushListenSession("SKIP_NEXT")
            }
        }

        if (!manual && !ignoreRepeatOne && repeatMode == RepeatMode.ONE) {
            flushListenSession("REPEAT_ONE_LOOP")
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
                val isSpotify = isSpotifyTrack(currentTrack)
                val isYoutube = currentTrack?.source == "youtube"

                if (autoPlayEnabled || isYoutube) {
                    viewModelScope.launch {
                        val youtubeFallback = playerPrefs.getYouTubeFallbackEnabled()

                        when {
                            isSpotify -> fetchAndQueueSpotifyRadio()
                            isYoutube || (currentTrack?.source == "soundcloud" && youtubeFallback) -> fetchAndPlayYoutubeRadio()
                            else -> fetchAndQueueRadio()
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
                val encodedUrl = if (lastTrack.permalinkUrl != null) URLEncoder.encode(lastTrack.permalinkUrl, "UTF-8") else ""
                val ctx = PlaybackContext(
                    displayText = "YouTube Mix • ${lastTrack.title}",
                    navigationId = "yt_radio:$encodedUrl",
                    imageUrl = lastTrack.fullResArtwork,
                    artistName = lastTrack.user?.username,
                    isVerified = false
                )
                currentContext = ctx
                MusicManager.updateContext(ctx)
                saveStateAsync(saveQueue = false)

                val historyPlaylist = Playlist(
                    id = kotlin.math.abs((lastTrack.permalinkUrl ?: lastTrack.title ?: "").hashCode().toLong()),
                    title = "YouTube Mix • ${lastTrack.title}",
                    artworkUrl = lastTrack.fullResArtwork,
                    calculatedArtworkUrl = null,
                    trackCount = 0,
                    user = lastTrack.user,
                    tracks = null,
                    permalinkUrl = "yt_radio:$encodedUrl"
                )
                HistoryRepository.addToHistory(historyPlaylist, isStation = true)
            }

            val youtubeService = ServiceList.YouTube
            val extractor = youtubeService.getPlaylistExtractor(radioUrl)

            withContext(Dispatchers.IO) {
                extractor.fetchPage()
            }

            val streamItems = extractor.initialPage.items.filterIsInstance<StreamInfoItem>()
            val radioTracks = streamItems.map {
                Track(
                    id = kotlin.math.abs(it.url.hashCode().toLong()),
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
                    val stationTitle = str("context_station", lastTrack.title ?: "")
                    val ctx = PlaybackContext(
                        stationTitle,
                        "station:${lastTrack.id}",
                        lastTrack.fullResArtwork
                    )
                    currentContext = ctx
                    MusicManager.updateContext(ctx)

                    val historyPlaylist = Playlist(
                        id = lastTrack.id,
                        title = stationTitle,
                        artworkUrl = lastTrack.fullResArtwork,
                        calculatedArtworkUrl = null,
                        trackCount = 0,
                        user = lastTrack.user,
                        tracks = null,
                        permalinkUrl = "station:${lastTrack.id}"
                    )
                    HistoryRepository.addToHistory(historyPlaylist, isStation = true)
                }
            }
        } catch (_: Exception) {
        } finally {
            isAutoplayRadioLoading = false
        }
    }

    /**
     * End-of-queue autoplay for Spotify catalog tracks: resolve the Spotify
     * radio playlist for the seed, append its tracks (skipping the seed) and
     * register the station context so it lands in history like other radios.
     */
    private suspend fun fetchAndQueueSpotifyRadio() {
        val lastTrack = currentTrack ?: return
        isAutoplayRadioLoading = true
        try {
            val spotifyId = getSpotifyTrackId(lastTrack) ?: return

            val radioPlaylist =
                com.alananasss.kittytune.data.spotify.SpotifyRepository.getRadio(spotifyId, isArtist = false)
            val rawTracks = radioPlaylist?.tracks
                ?: com.alananasss.kittytune.data.spotify.SpotifyRepository.getRadioTracks(spotifyId)

            val tracksToAdd = rawTracks.drop(1).map { it.toTrack() }
                .filter { track -> _queue.none { it.id == track.id } }

            if (tracksToAdd.isNotEmpty()) {
                _queue.addAll(tracksToAdd)
                _originalQueue.addAll(tracksToAdd)
                updateQueueState()
            }
            if (currentContext == null) {
                val ctx = PlaybackContext(
                    displayText = str("context_station", lastTrack.title ?: ""),
                    navigationId = "spotify_radio:$spotifyId",
                    imageUrl = lastTrack.fullResArtwork,
                    artistName = lastTrack.user?.username,
                    isVerified = lastTrack.user?.verified == true
                )
                currentContext = ctx
                MusicManager.updateContext(ctx)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isAutoplayRadioLoading = false
        }
    }

    fun smartPrevious(isCrossfade: Boolean = false) {
        if (player.currentPosition > 3000) {
            flushListenSession("MANUAL_REPLAY")
            // The same track from the top is a new listen, not a continuation of the old one.
            beginListenSession(currentTrack)
            currentPosition = 0L
            player.seekTo(0)
        } else {
            currentTrack?.let { track ->
                flushListenSession("SKIP_PREVIOUS")
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

    fun toggleShuffle() {
        shuffleEnabled = !shuffleEnabled;
        if (shuffleEnabled) applyShuffle() else revertShuffle();
        updateQueueState();
        saveStateAsync(saveQueue = true)

        kdeMpris2Service?.updateShuffle(shuffleEnabled)
    }

    private fun applyShuffle(startIndex: Int = currentQueueIndex, sourceList: List<Track> = _originalQueue) {
        if (sourceList.isEmpty() || startIndex !in sourceList.indices) return

        val played = sourceList.subList(0, startIndex + 1)
        val upcoming =
            if (startIndex + 1 < sourceList.size) sourceList.subList(startIndex + 1, sourceList.size) else emptyList()

        val shuffledUpcoming = upcoming.shuffled()

        _queue.clear()
        _queue.addAll(played)
        _queue.addAll(shuffledUpcoming)
    }

    private fun revertShuffle() {
        val currentTrackId =
            currentTrack?.id ?: return; _queue.clear(); _queue.addAll(_originalQueue); currentQueueIndex =
            _queue.indexOfFirst { it.id == currentTrackId }.coerceAtLeast(0)
    }

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

        kdeMpris2Service?.updateLoopStatus(
            when (repeatMode) {
                RepeatMode.NONE -> com.alananasss.kittytune.data.KdeMpris2Service.LoopStatus.None
                RepeatMode.ALL -> com.alananasss.kittytune.data.KdeMpris2Service.LoopStatus.Playlist
                RepeatMode.ONE -> com.alananasss.kittytune.data.KdeMpris2Service.LoopStatus.Track
            }
        )
    }

    fun updateQueueState() {
        queueState = _queue.toList()
    }

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
            try {
                MusicManager.player.removeMediaItem(1)
            } catch (_: Exception) {
            }
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
            try {
                MusicManager.player.removeMediaItem(1)
            } catch (_: Exception) {
            }
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
            try {
                MusicManager.player.removeMediaItem(1)
            } catch (_: Exception) {
            }
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

    /**
     * Moves the playhead to [position], clamped to the track when its length is known.
     *
     * Only when it is known. [duration] starts at the track's metadata length, which the API leaves
     * out often enough to matter, and is not corrected until the stream opens and the engine reports
     * one. `coerceIn(0, duration)` against a zero duration is `coerceIn(0, 0)`: every seek arrived at
     * the start of the track instead of where it was aimed, which is the whole of "playback starts
     * from the very beginning" for anyone whose track came in without a duration (issue #33).
     */
    fun seekTo(position: Long) {
        val known = duration.takeIf { it > 0L }
        val target =
            if (known != null) position.coerceIn(0L, known) else position.coerceAtLeast(0L)
        isScrubbing = false
        seekTargetPosition = target
        lastSeekTimestamp = System.currentTimeMillis()
        currentPosition = target

        // If seeking to the very end of the track (within 500ms of duration or at duration),
        // cleanly advance to the next track in the queue or loop instead of freezing (issue #33).
        if (known != null && target >= known - 500L) {
            listenSession?.onSeek(target)
            SoundCloudTelemetryTracker.onTrackSeeked(target)
            saveStateAsync(savePositionOnly = true)
            if (repeatMode == RepeatMode.ONE) {
                currentPosition = 0L
                player.seekTo(0)
                player.play()
            } else {
                playNext(manual = false, isCrossfade = playerPrefs.getCrossfadeEnabled())
            }
            return
        }

        // Crossing the track deliberately is not listening to what was crossed (issue #33).
        listenSession?.onSeek(target)
        player.seekTo(target)
        SoundCloudTelemetryTracker.onTrackSeeked(target)
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

    fun togglePreciseSpeedEnabled(enabled: Boolean) {
        isPreciseSpeedEnabled = enabled; playerPrefs.setPreciseSpeedEnabled(enabled)
    }

    fun toggleRain() {
        val n = !effectsState.isRainEnabled; effectsState = effectsState.copy(isRainEnabled = n); applyEffectsAndSave()
    }

    fun setRainVolume(volume: Float) {
        effectsState =
            effectsState.copy(rainVolume = volume); MusicManager.applyEffects(effectsState); viewModelScope.launch(
            Dispatchers.IO
        ) { playerPrefs.saveEffects(effectsState) }
    }

    fun setCustomSpeed(speed: Float) {
        val factor = if (isPreciseSpeedEnabled) 20f else 10f;
        val r = (speed * factor).roundToInt() / factor; effectsState =
            effectsState.copy(speed = r); applyEffectsAndSave()
    }

    fun togglePitchEnabled(e: Boolean) {
        effectsState = effectsState.copy(isPitchEnabled = e); applyEffectsAndSave()
    }

    fun toggle8D() {
        effectsState = effectsState.copy(is8DEnabled = !effectsState.is8DEnabled); applyEffectsAndSave()
    }

    fun setEightDSpeed(v: Float) {
        effectsState = effectsState.copy(eightDSpeed = v); applyEffectsAndSave()
    }

    fun toggleMuffled() {
        val n = !effectsState.isMuffledEnabled; effectsState =
            effectsState.copy(isMuffledEnabled = n); applyEffectsAndSave()
    }

    fun setMuffledIntensity(v: Float) {
        effectsState = effectsState.copy(muffledIntensity = v); applyEffectsAndSave()
    }

    fun toggleBassBoost() {
        val n = !effectsState.isBassBoostEnabled; effectsState =
            effectsState.copy(isBassBoostEnabled = n); applyEffectsAndSave()
    }

    fun setBassBoostIntensity(v: Float) {
        effectsState = effectsState.copy(bassBoostIntensity = v); applyEffectsAndSave()
    }

    fun toggleReverb() {
        effectsState = effectsState.copy(isReverbEnabled = !effectsState.isReverbEnabled); applyEffectsAndSave()
    }

    fun setReverbIntensity(v: Float) {
        effectsState = effectsState.copy(reverbIntensity = v); applyEffectsAndSave()
    }

    fun toggleEarrape() {
        val n = !effectsState.isEarrapeEnabled; effectsState =
            effectsState.copy(isEarrapeEnabled = n); applyEffectsAndSave()
    }

    fun toggleMono() {
        val n = !effectsState.isMonoEnabled; effectsState = effectsState.copy(isMonoEnabled = n); applyEffectsAndSave()
    }

    fun toggleNormalization(enabled: Boolean = !effectsState.isNormalizationEnabled) {
        effectsState = effectsState.copy(isNormalizationEnabled = enabled); applyEffectsAndSave()
    }

    fun setNormalizationLevel(level: NormalizationLevel) {
        effectsState = effectsState.copy(normalizationLevel = level); applyEffectsAndSave()
    }

    fun setAmbientType(type: String) {
        effectsState = effectsState.copy(ambientType = type); applyEffectsAndSave()
    }

    fun setEarrapeIntensity(v: Float) {
        effectsState = effectsState.copy(earrapeIntensity = v); applyEffectsAndSave()
    }

    fun toggleVintageMp3() { val n = !effectsState.isVintageMp3Enabled; effectsState = effectsState.copy(isVintageMp3Enabled = n); applyEffectsAndSave() }
    fun setVintageMp3Compression(v: Float) { effectsState = effectsState.copy(vintageMp3Compression = v.coerceIn(0f, 1f)); applyEffectsAndSave() }

    fun toggleVocalRemover() { val n = !effectsState.isVocalRemoverEnabled; effectsState = effectsState.copy(isVocalRemoverEnabled = n); applyEffectsAndSave() }
    fun setVocalRemoverLevel(v: Float) { effectsState = effectsState.copy(vocalRemoverLevel = v.coerceIn(0f, 1f)); applyEffectsAndSave() }

    fun toggleVocalBoost() { val n = !effectsState.isVocalBoostEnabled; effectsState = effectsState.copy(isVocalBoostEnabled = n); applyEffectsAndSave() }
    fun setVocalBoostIntensity(v: Float) { effectsState = effectsState.copy(vocalBoostIntensity = v.coerceIn(0f, 1f)); applyEffectsAndSave() }

    fun toggleFlanger() { val n = !effectsState.isFlangerEnabled; effectsState = effectsState.copy(isFlangerEnabled = n); applyEffectsAndSave() }
    fun setFlangerIntensity(v: Float) { effectsState = effectsState.copy(flangerIntensity = v.coerceIn(0f, 1f)); applyEffectsAndSave() }
    fun setFlangerSpeed(v: Float) { effectsState = effectsState.copy(flangerSpeed = v.coerceIn(0f, 1f)); applyEffectsAndSave() }

    fun togglePartyNextDoor() { val n = !effectsState.isPartyNextDoorEnabled; effectsState = effectsState.copy(isPartyNextDoorEnabled = n); applyEffectsAndSave() }
    fun setPartyNextDoorIsolation(v: Float) { effectsState = effectsState.copy(partyNextDoorIsolation = v.coerceIn(0f, 1f)); applyEffectsAndSave() }
    fun setPartyNextDoorReverb(v: Float) { effectsState = effectsState.copy(partyNextDoorReverb = v.coerceIn(0f, 1f)); applyEffectsAndSave() }
    fun setPartyNextDoorBassRumble(v: Float) { effectsState = effectsState.copy(partyNextDoorBassRumble = v.coerceIn(0f, 1f)); applyEffectsAndSave() }

    fun toggleSuperWide() { val n = !effectsState.isSuperWideEnabled; effectsState = effectsState.copy(isSuperWideEnabled = n); applyEffectsAndSave() }
    fun setSuperWideWidth(v: Float) { effectsState = effectsState.copy(superWideWidth = v.coerceIn(0f, 1f)); applyEffectsAndSave() }
    fun setSuperWideDepth(v: Float) { effectsState = effectsState.copy(superWideDepth = v.coerceIn(0f, 1f)); applyEffectsAndSave() }

    fun toggleVinylLoFi() { val n = !effectsState.isVinylLoFiEnabled; effectsState = effectsState.copy(isVinylLoFiEnabled = n); applyEffectsAndSave() }
    fun setVinylCrackles(v: Float) { effectsState = effectsState.copy(vinylCrackles = v.coerceIn(0f, 1f)); applyEffectsAndSave() }
    fun setVinylFlutter(v: Float) { effectsState = effectsState.copy(vinylFlutter = v.coerceIn(0f, 1f)); applyEffectsAndSave() }

    fun togglePhaser() { val n = !effectsState.isPhaserEnabled; effectsState = effectsState.copy(isPhaserEnabled = n); applyEffectsAndSave() }
    fun setPhaserSpeed(v: Float) { effectsState = effectsState.copy(phaserSpeed = v.coerceIn(0f, 1f)); applyEffectsAndSave() }
    fun setPhaserFeedback(v: Float) { effectsState = effectsState.copy(phaserFeedback = v.coerceIn(0f, 1f)); applyEffectsAndSave() }

    fun toggleMegaphone() { val n = !effectsState.isMegaphoneEnabled; effectsState = effectsState.copy(isMegaphoneEnabled = n); applyEffectsAndSave() }
    fun setMegaphoneTone(v: Float) { effectsState = effectsState.copy(megaphoneTone = v.coerceIn(0f, 1f)); applyEffectsAndSave() }
    fun setMegaphoneDrive(v: Float) { effectsState = effectsState.copy(megaphoneDrive = v.coerceIn(0f, 1f)); applyEffectsAndSave() }

    fun toggleRobotVocoder() { val n = !effectsState.isRobotVocoderEnabled; effectsState = effectsState.copy(isRobotVocoderEnabled = n); applyEffectsAndSave() }
    fun setRobotFrequency(v: Float) { effectsState = effectsState.copy(robotFrequency = v.coerceIn(0f, 1f)); applyEffectsAndSave() }
    fun setRobotMix(v: Float) { effectsState = effectsState.copy(robotMix = v.coerceIn(0f, 1f)); applyEffectsAndSave() }

    fun toggleChorus() { val n = !effectsState.isChorusEnabled; effectsState = effectsState.copy(isChorusEnabled = n); applyEffectsAndSave() }
    fun setChorusRate(v: Float) { effectsState = effectsState.copy(chorusRate = v.coerceIn(0f, 1f)); applyEffectsAndSave() }
    fun setChorusDepth(v: Float) { effectsState = effectsState.copy(chorusDepth = v.coerceIn(0f, 1f)); applyEffectsAndSave() }

    fun toggleUnderwater() { val n = !effectsState.isUnderwaterEnabled; effectsState = effectsState.copy(isUnderwaterEnabled = n); applyEffectsAndSave() }
    fun setUnderwaterDepth(v: Float) { effectsState = effectsState.copy(underwaterDepth = v.coerceIn(0f, 1f)); applyEffectsAndSave() }
    fun setUnderwaterBubbles(v: Float) { effectsState = effectsState.copy(underwaterBubbles = v.coerceIn(0f, 1f)); applyEffectsAndSave() }

    fun toggleTranceGate() { val n = !effectsState.isTranceGateEnabled; effectsState = effectsState.copy(isTranceGateEnabled = n); applyEffectsAndSave() }
    fun setTranceGateSpeed(v: Float) { effectsState = effectsState.copy(tranceGateSpeed = v.coerceIn(0f, 1f)); applyEffectsAndSave() }
    fun setTranceGatePattern(v: Float) { effectsState = effectsState.copy(tranceGatePattern = v.coerceIn(0f, 1f)); applyEffectsAndSave() }
    fun setTranceGateMix(v: Float) { effectsState = effectsState.copy(tranceGateMix = v.coerceIn(0f, 1f)); applyEffectsAndSave() }

    fun togglePingPongDelay() { val n = !effectsState.isPingPongDelayEnabled; effectsState = effectsState.copy(isPingPongDelayEnabled = n); applyEffectsAndSave() }
    fun setPingPongDelayTime(v: Float) { effectsState = effectsState.copy(pingPongDelayTime = v.coerceIn(0f, 1f)); applyEffectsAndSave() }
    fun setPingPongFeedback(v: Float) { effectsState = effectsState.copy(pingPongFeedback = v.coerceIn(0f, 1f)); applyEffectsAndSave() }

    fun toggleChiptune() { val n = !effectsState.isChiptuneEnabled; effectsState = effectsState.copy(isChiptuneEnabled = n); applyEffectsAndSave() }
    fun setChiptuneBits(v: Float) { effectsState = effectsState.copy(chiptuneBits = v.coerceIn(0f, 1f)); applyEffectsAndSave() }
    fun setChiptuneSampleRate(v: Float) { effectsState = effectsState.copy(chiptuneSampleRate = v.coerceIn(0f, 1f)); applyEffectsAndSave() }

    fun toggleShimmerReverb() { val n = !effectsState.isShimmerReverbEnabled; effectsState = effectsState.copy(isShimmerReverbEnabled = n); applyEffectsAndSave() }
    fun setShimmerSize(v: Float) { effectsState = effectsState.copy(shimmerSize = v.coerceIn(0f, 1f)); applyEffectsAndSave() }
    fun setShimmerMix(v: Float) { effectsState = effectsState.copy(shimmerMix = v.coerceIn(0f, 1f)); applyEffectsAndSave() }

    fun toggleRotarySpeaker() { val n = !effectsState.isRotarySpeakerEnabled; effectsState = effectsState.copy(isRotarySpeakerEnabled = n); applyEffectsAndSave() }
    fun setRotarySpeed(v: Float) { effectsState = effectsState.copy(rotarySpeed = v.coerceIn(0f, 1f)); applyEffectsAndSave() }
    fun setRotaryDepth(v: Float) { effectsState = effectsState.copy(rotaryDepth = v.coerceIn(0f, 1f)); applyEffectsAndSave() }

    fun toggleTapeSaturation() { val n = !effectsState.isTapeSaturationEnabled; effectsState = effectsState.copy(isTapeSaturationEnabled = n); applyEffectsAndSave() }
    fun setTapeWarmth(v: Float) { effectsState = effectsState.copy(tapeWarmth = v.coerceIn(0f, 1f)); applyEffectsAndSave() }
    fun setTapeExciter(v: Float) { effectsState = effectsState.copy(tapeExciter = v.coerceIn(0f, 1f)); applyEffectsAndSave() }

    fun toggleSubOctaver() { val n = !effectsState.isSubOctaverEnabled; effectsState = effectsState.copy(isSubOctaverEnabled = n); applyEffectsAndSave() }
    fun setSubOctaverLevel(v: Float) { effectsState = effectsState.copy(subOctaverLevel = v.coerceIn(0f, 1f)); applyEffectsAndSave() }
    fun setSubOctaverCutoff(v: Float) { effectsState = effectsState.copy(subOctaverCutoff = v.coerceIn(0f, 1f)); applyEffectsAndSave() }

    fun toggleEmptyMall() { val n = !effectsState.isEmptyMallEnabled; effectsState = effectsState.copy(isEmptyMallEnabled = n); applyEffectsAndSave() }
    fun setEmptyMallDistance(v: Float) { effectsState = effectsState.copy(emptyMallDistance = v.coerceIn(0f, 1f)); applyEffectsAndSave() }
    fun setEmptyMallReverb(v: Float) { effectsState = effectsState.copy(emptyMallReverb = v.coerceIn(0f, 1f)); applyEffectsAndSave() }

    fun toggleGramophone() { val n = !effectsState.isGramophoneEnabled; effectsState = effectsState.copy(isGramophoneEnabled = n); applyEffectsAndSave() }
    fun setGramophoneAge(v: Float) { effectsState = effectsState.copy(gramophoneAge = v.coerceIn(0f, 1f)); applyEffectsAndSave() }
    fun setGramophoneHorn(v: Float) { effectsState = effectsState.copy(gramophoneHorn = v.coerceIn(0f, 1f)); applyEffectsAndSave() }

    fun toggleReverseEcho() { val n = !effectsState.isReverseEchoEnabled; effectsState = effectsState.copy(isReverseEchoEnabled = n); applyEffectsAndSave() }
    fun setReverseEchoTime(v: Float) { effectsState = effectsState.copy(reverseEchoTime = v.coerceIn(0f, 1f)); applyEffectsAndSave() }
    fun setReverseEchoFeedback(v: Float) { effectsState = effectsState.copy(reverseEchoFeedback = v.coerceIn(0f, 1f)); applyEffectsAndSave() }

    fun toggleStadium() { val n = !effectsState.isStadiumEnabled; effectsState = effectsState.copy(isStadiumEnabled = n); applyEffectsAndSave() }
    fun setStadiumSize(v: Float) { effectsState = effectsState.copy(stadiumSize = v.coerceIn(0f, 1f)); applyEffectsAndSave() }
    fun setStadiumAtmosphere(v: Float) { effectsState = effectsState.copy(stadiumAtmosphere = v.coerceIn(0f, 1f)); applyEffectsAndSave() }

    fun toggleWalkman() { val n = !effectsState.isWalkmanEnabled; effectsState = effectsState.copy(isWalkmanEnabled = n); applyEffectsAndSave() }
    fun setWalkmanDrive(v: Float) { effectsState = effectsState.copy(walkmanDrive = v.coerceIn(0f, 1f)); applyEffectsAndSave() }
    fun setWalkmanHiss(v: Float) { effectsState = effectsState.copy(walkmanHiss = v.coerceIn(0f, 1f)); applyEffectsAndSave() }

    fun toggleAsmrVocal() { val n = !effectsState.isAsmrVocalEnabled; effectsState = effectsState.copy(isAsmrVocalEnabled = n); applyEffectsAndSave() }
    fun setAsmrProximity(v: Float) { effectsState = effectsState.copy(asmrProximity = v.coerceIn(0f, 1f)); applyEffectsAndSave() }
    fun setAsmrAir(v: Float) { effectsState = effectsState.copy(asmrAir = v.coerceIn(0f, 1f)); applyEffectsAndSave() }

    fun toggleNightDrive() { val n = !effectsState.isNightDriveEnabled; effectsState = effectsState.copy(isNightDriveEnabled = n); applyEffectsAndSave() }
    fun setNightDriveCabin(v: Float) { effectsState = effectsState.copy(nightDriveCabin = v.coerceIn(0f, 1f)); applyEffectsAndSave() }
    fun setNightDriveRoad(v: Float) { effectsState = effectsState.copy(nightDriveRoad = v.coerceIn(0f, 1f)); applyEffectsAndSave() }

    var pinnedAudioFx by mutableStateOf(playerPrefs.getPinnedAudioFx())
        private set

    fun togglePinAudioFx(fxId: String): Boolean {
        val current = pinnedAudioFx.toMutableList()
        val isPinned = current.contains(fxId)
        if (isPinned) {
            current.remove(fxId)
        } else {
            current.add(fxId)
        }
        pinnedAudioFx = current
        playerPrefs.setPinnedAudioFx(current)
        return true
    }

    fun updatePinnedAudioFx(fxIds: List<String>) {
        pinnedAudioFx = fxIds
        playerPrefs.setPinnedAudioFx(fxIds)
    }

    fun resetPinnedAudioFx() {
        pinnedAudioFx = PlayerPreferences.DEFAULT_PINNED_AUDIO_FX
        playerPrefs.setPinnedAudioFx(PlayerPreferences.DEFAULT_PINNED_AUDIO_FX)
    }

    fun isAudioFxPinned(fxId: String): Boolean = pinnedAudioFx.contains(fxId)

    fun hasSeenEarrapeWarning(): Boolean = playerPrefs.hasSeenEarrapeWarning()
    fun setHasSeenEarrapeWarning(seen: Boolean) {
        playerPrefs.setHasSeenEarrapeWarning(seen)
    }

    private fun applyEffectsAndSave() {
        MusicManager.applyEffects(effectsState); viewModelScope.launch(Dispatchers.IO) {
            playerPrefs.saveEffects(
                effectsState
            )
        }
    }

    fun showTrackOptions(track: Track, playlistContextId: Long? = null, fromPlayer: Boolean = false) {
        trackForMenu = track; menuContextPlaylistId = playlistContextId; isMenuContextFromPlayer =
            fromPlayer; showMenuSheet = true
    }

    /** Desktop right-click on a playlist card = the Android playlist 3-dot sheet. */
    fun showPlaylistOptions(playlist: Playlist) {
        playlistForMenu = playlist; showPlaylistMenuSheet = true
    }

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

    fun prepareBulkAdd(tracks: List<Track>) {
        tracksToAddInBulk = tracks; trackForMenu = null; showAddToPlaylistSheet = true
    }

    fun addToPlaylist(playlistId: Long, track: Track) {
        DownloadManager.addTrackToPlaylist(playlistId, track); showAddToPlaylistSheet =
            false; emitUiEvent(str("success_generic"))
    }

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

    fun toggleTrackLike(track: Track) {
        if (track.id == currentTrack?.id) {
            toggleLike()
        } else {
            val isCurrentlyLiked = LikeRepository.isTrackLiked(track.id)
            if (isCurrentlyLiked) {
                LikeRepository.removeLike(track.id)
            } else {
                LikeRepository.addLike(track)
            }
        }
    }

    fun removeFromContextPlaylist(playlistId: Long, track: Track) {
        if (playlistId == -2L) {
            DownloadManager.deleteTrack(track.id)
        } else {
            val syncToCloud = playlistId > 0 && currentContext?.navigationId?.startsWith("downloaded_section:") != true && currentContext?.navigationId != "downloads"
            DownloadManager.removeTrackFromPlaylist(playlistId, track.id, syncToCloud = syncToCloud)
        }
        showMenuSheet = false
        emitUiEvent(str("success_generic"))
    }

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

    fun downloadTrack(track: Track) {
        if (DownloadManager.isTrackDownloading(track.id)) return; DownloadManager.downloadTrack(track)
    }

    /**
     * Says something to the user, through the same channel the sleep timer uses.
     *
     * Public because a press can now fail for a reason worth saying out loud: an Apple Music result is a
     * catalogue entry, and finding it on a source that streams is a search that can come back empty. A
     * press that silently does nothing is the complaint the whole feature came from (issue #33).
     */
    fun notify(message: String) = emitUiEvent(message)

    private fun emitUiEvent(msg: String) {
        viewModelScope.launch { _uiEvent.emit(msg) }
    }

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
                withContext(Dispatchers.IO) {
                    val qSnapshot = _originalQueue.toList()
                    playerPrefs.savePlaybackState(freshT, freshP, qSnapshot, freshC, freshS, freshR)
                }
            }
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                playerPrefs.savePlaybackState(t, p, emptyList(), c, s, r, saveQueue = false)
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
                        ensureListenSession()
                        listenSession?.onPosition(currentPosition)

                        // Dispatch progress to SoundCloud Telemetry Tracker (handles 5s threshold and 30s checkpoints)
                        SoundCloudTelemetryTracker.onProgressUpdate(currentPosition)

                        val crossfadeEnabled = playerPrefs.getCrossfadeEnabled()
                        val crossfadeMs = playerPrefs.getCrossfadeDuration() * 1000L
                        val dur = MusicManager.player.duration
                        // Taken here as well as at STATE_READY. A track whose metadata carried no
                        // duration is READY before the decoder has one, so that single assignment
                        // could miss it for the whole track — leaving the progress bar and every
                        // clamp built on it working against a length of zero (issue #33).
                        if (dur > 0L && dur != duration) duration = dur
                        val continuousPlaybackEnabled = playerPrefs.getContinuousPlaybackEnabled()
                        val shouldCrossfade =
                            crossfadeEnabled && (continuousPlaybackEnabled || repeatMode == RepeatMode.ONE)
                        if (shouldCrossfade && dur > 0 && currentPosition >= (dur - crossfadeMs) && !MusicManager.player.isCrossfadingOut) {
                            MusicManager.player.isCrossfadingOut = true
                            playNext(manual = false, isCrossfade = true)
                        }
                    }
                } catch (_: Exception) {
                }
                delay(250.milliseconds)
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
                    player.volume = preFadeVolume ?: volume
                    preFadeVolume = null
                    sleepTimerRemainingMs = 0L
                    showSleepTimerIslandNotification(isStarted = false)
                    break
                }

                // Progressive fade-out zone
                if (fadeDurationMs > 0L && remaining <= fadeDurationMs) {
                    val fraction = (remaining.toFloat() / fadeDurationMs).coerceIn(0f, 1f)
                    // Quadratic curve: perceived volume decreases naturally
                    val volumeFraction = fraction * fraction
                    player.volume = ((preFadeVolume ?: volume) * volumeFraction).coerceIn(0f, 1f)
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
        // Only if a fade actually lowered the volume. This used to compare against a field that
        // defaulted to 1.0, so cancelling — or merely *arming* a timer, which calls this first —
        // slammed the engine to full volume while the slider stayed where the user left it.
        preFadeVolume?.let { player.volume = it }
        preFadeVolume = null
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
                    if (lastQueue.isNotEmpty()) {
                        _queue.clear(); _queue.addAll(lastQueue); _originalQueue.clear(); _originalQueue.addAll(
                            lastQueue
                        ); updateQueueState()
                    }
                    if (lastTrack != null) {
                        shuffleEnabled = lastShuffle; repeatMode = lastRepeat; currentContext = lastContext
                        MusicManager.updateContext(lastContext)

                        currentTrack = lastTrack
                        MusicManager.currentTrack = lastTrack; isLiked =
                            LikeRepository.isTrackLiked(lastTrack.id); loadLyrics(lastTrack)
                        currentQueueIndex = _queue.indexOfFirst { it.id == lastTrack.id }
                        if (currentQueueIndex == -1) {
                            _queue.add(0, lastTrack); _originalQueue.add(
                                0,
                                lastTrack
                            ); updateQueueState(); currentQueueIndex = 0
                        }
                        if (shuffleEnabled && _queue.size > 1) {
                            applyShuffle(currentQueueIndex)
                            updateQueueState()
                        }
                        val currentPlayerMediaId = MusicManager.player.currentMediaItem?.mediaId
                        Logger.e(
                            "PlayerViewModel",
                            "restoring session. lastPosition = $lastPosition, currentPlayerMediaId = $currentPlayerMediaId, lastTrackId = ${lastTrack.id}"
                        )
                        if (currentPlayerMediaId == lastTrack.id.toString() && MusicManager.player.isPlaying) {
                            isPlaying = true
                            duration = MusicManager.player.duration.coerceAtLeast(lastTrack.durationMs ?: 0L)
                            currentPosition = MusicManager.player.currentPosition
                            MusicManager.applyEffects(effectsState)
                            Logger.e(
                                "PlayerViewModel",
                                "player already has track and is playing. currentPosition set to $currentPosition"
                            )
                        } else {
                            currentPosition = lastPosition
                            duration = lastTrack.durationMs ?: 0L
                            Logger.e("PlayerViewModel", "calling playRobustly with startPosition = $lastPosition")
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
            val playing = try {
                MusicManager.player.isPlaying
            } catch (_: Exception) {
                false
            }
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
                        if (shuffleEnabled && _queue.size > 1) {
                            applyShuffle(currentQueueIndex)
                            updateQueueState()
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

    /**
     * Keeps the dynamic theme on the cover of whatever is playing.
     *
     * Watches [currentTrack] rather than a playback callback. The colours used to be extracted
     * from `MusicManager.onTrackChange`, which only fires from `MusicManager.playTrack` — the
     * direct-URI path — while normal playback goes through `playTrackAtIndex`/`playRobustly`.
     * The dynamic theme therefore never updated during ordinary listening and the palette sat on
     * its default seed forever (issue #33). One watcher on the state every one of those paths
     * assigns covers them all, session restore and the metadata backfill included.
     *
     * Keyed on the artwork rather than the track, so the whole of an album costs one extraction
     * and the backfill that swaps in fuller metadata does not pay for a second.
     */
    /**
     * The current track's own volume trim (issue #33).
     *
     * Stored per track and applied the moment the track becomes current, which is the whole point:
     * normalisation was rejected because it measures as it plays and only reaches the right level a
     * few seconds in. Kept in its own file rather than the main preferences, which is rewritten
     * whole on every change and would grow a key per track.
     */
    private val trackGainPrefs = com.alananasss.kittytune.core.NamedPrefs("track_gain")

    var trackGainDb by mutableIntStateOf(com.alananasss.kittytune.audio.TrackGain.NONE)
        private set

    private fun trackGainKey(trackId: Long) = "gain_$trackId"

    private fun observeTrackGain() {
        viewModelScope.launch {
            snapshotFlow { currentTrack?.id }
                .distinctUntilChanged()
                .collect { id ->
                    val stored = if (id == null || id == 0L) {
                        com.alananasss.kittytune.audio.TrackGain.NONE
                    } else {
                        trackGainPrefs.getInt(trackGainKey(id), com.alananasss.kittytune.audio.TrackGain.NONE)
                    }
                    trackGainDb = com.alananasss.kittytune.audio.TrackGain.clamp(stored)
                    MusicManager.setTrackGainDb(trackGainDb.toFloat())
                }
        }
    }

    /** Moves the current track's trim by [delta] steps and remembers it. */
    fun adjustTrackGain(delta: Int) {
        val track = currentTrack ?: return
        val next = com.alananasss.kittytune.audio.TrackGain.adjust(trackGainDb, delta)
        if (next == trackGainDb) return
        trackGainDb = next
        MusicManager.setTrackGainDb(next.toFloat())
        // Nothing stored means no trim, so the common case leaves no key behind.
        if (next == com.alananasss.kittytune.audio.TrackGain.NONE) {
            trackGainPrefs.remove(trackGainKey(track.id))
        } else {
            trackGainPrefs.putInt(trackGainKey(track.id), next)
        }
    }

    /** Back to no trim for the current track. */
    fun resetTrackGain() = adjustTrackGain(-trackGainDb)

    private fun observeArtworkColors() {
        viewModelScope.launch {
            snapshotFlow { currentTrack }
                .distinctUntilChangedBy { it?.fullResArtwork }
                .collect { track ->
                    if (track == null) {
                        com.alananasss.kittytune.ui.theme.ThemeState.coverSeedColor = null
                        com.alananasss.kittytune.ui.theme.ThemeState.coverMeshColors = emptyList()
                    } else {
                        updatePlayerColors(track)
                    }
                }
        }
    }

    private fun updatePlayerColors(track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            val isDarkMode = playerPrefs.getThemeMode() != com.alananasss.kittytune.data.local.AppThemeMode.LIGHT

            // No real cover: `fullResArtwork` invents a random picsum image for those, and seeding
            // the whole palette from a stranger's photo is worse than falling back to the key
            // colour the user picked.
            if (track.fullResArtwork.startsWith(PLACEHOLDER_ARTWORK_PREFIX)) {
                com.alananasss.kittytune.ui.theme.ThemeState.coverSeedColor = null
                com.alananasss.kittytune.ui.theme.ThemeState.coverMeshColors = emptyList()
                return@launch
            }

            // Spotify catalog: use Spotify's own cover color extraction (one
            // tiny request) instead of downloading and analyzing the artwork.
            if (isSpotifyTrack(track)) {
                val extracted = com.alananasss.kittytune.data.spotify.SpotifyRepository
                    .getExtractedColors(track.artworkUrl)
                if (extracted != null && !extracted.isFallback) {
                    val chosen = if (isDarkMode) extracted.light else extracted.dark
                    val color = parseHexColor(chosen)
                    if (color != null) {
                        backgroundColor = color
                        com.alananasss.kittytune.ui.theme.ThemeState.coverSeedColor = backgroundColor.toArgb()
                        return@launch
                    }
                }
            }

            val bitmap = loadBitmap(track.fullResArtwork)
            if (bitmap != null) {
                try {
                    val artFile = File(com.alananasss.kittytune.core.AppDirs.imageCacheDir, "art_${track.id}.jpg")
                    if (!artFile.exists()) {
                        javax.imageio.ImageIO.write(bitmap, "jpg", artFile)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Desktop app is dark-first; pick a light vibrant color for contrast on dark bg.
                backgroundColor = ArtworkPalette.dominantColor(bitmap, preferLight = isDarkMode)
                // The theme seeds from the cover's own colour, not from the lightened backdrop:
                // the palette style the user chose is what should decide tone from there.
                com.alananasss.kittytune.ui.theme.ThemeState.coverSeedColor =
                    ArtworkPalette.dominantSeed(bitmap).toArgb()
                // And the several colours the full player's background moves through — read from the same
                // bitmap we already have decoded, so this costs a histogram and no extra request (issue #33).
                com.alananasss.kittytune.ui.theme.ThemeState.coverMeshColors =
                    ArtworkPalette.meshPalette(bitmap).map { it.toArgb() }
            } else {
                backgroundColor = Color(0xFF1E1E1E)
            }
        }
    }

    private fun parseHexColor(hex: String): Color? {
        val clean = hex.removePrefix("#")
        if (clean.length != 6) return null
        return runCatching { Color(0xFF000000.toInt() or clean.toInt(16)) }.getOrNull()
    }

    private fun playRobustly(
        index: Int,
        autoPlay: Boolean = true,
        startPosition: Long = 0L,
        allowSkipOnFailure: Boolean = true,
        isCrossfade: Boolean = false
    ) {
        if (index !in _queue.indices) return

        val trackToPlay = _queue[index]

        playJob?.cancel()
        playJob = viewModelScope.launch(Dispatchers.IO) {
            Logger.e(
                "PlayerViewModel",
                "playRobustly started for trackId=${trackToPlay.id} with startPosition=$startPosition, autoPlay=$autoPlay"
            )
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
            } catch (e: Exception) {
                e.printStackTrace()
            }

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
                    if (!com.alananasss.kittytune.utils.NetworkUtils.isInternetAvailable()) {
                        isLoading = true
                        isPlaying = false
                        viewModelScope.launch(Dispatchers.IO) {
                            while (!com.alananasss.kittytune.utils.NetworkUtils.isInternetAvailable()) {
                                delay(2000)
                            }
                            withContext(Dispatchers.Main) {
                                if (currentQueueIndex == index) {
                                    playRobustly(index, autoPlay, startPosition, allowSkipOnFailure, isCrossfade)
                                }
                            }
                        }
                    } else {
                        isLoading = false
                        isPlaying = false
                        if (allowSkipOnFailure) playNext(manual = false, ignoreRepeatOne = true)
                    }
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
        prefetchWarmJob?.cancel()

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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Second stage: warm the StreamResolver cache for the track AFTER next, so
        // skipping forward never waits on stream resolution (issue #27). No media
        // item is appended — only the resolved URL is cached.
        val warmUpIndex = when {
            nextIndex + 1 < _queue.size -> nextIndex + 1
            repeatMode == RepeatMode.ALL && _queue.isNotEmpty() -> 0
            else -> null
        }?.takeIf { it in _queue.indices }
        val warmUpTrack = warmUpIndex?.let { _queue[it] }

        // Third stage: keep those URLs alive. They are CDN links signed for about three
        // minutes, so staying on one track longer than that used to leave the whole prefetch
        // holding dead links — and the skip that followed spent FFmpeg's reconnect backoff on
        // a certain 403 before anything re-resolved. Topping them up costs a cache lookup
        // until one actually lapses.
        prefetchWarmJob = viewModelScope.launch(Dispatchers.IO) {
            warmUpTrack?.let { warmStreamCache(it) }
            while (isActive) {
                delay(PREFETCH_REWARM_INTERVAL_MS)
                if (!MusicManager.player.isPlaying) continue
                // The playing track is on the list too: a seek is a fresh range request, so the
                // engine needs a live URL for it as much as the queue needs one for the skip.
                listOfNotNull(currentTrack, nextTrack, warmUpTrack)
                    .distinctBy { it.id }
                    .forEach { warmStreamCache(it) }
            }
        }
    }

    /** Resolves [track] into the StreamResolver cache, unless it is already a local file. */
    private suspend fun warmStreamCache(track: Track) {
        try {
            val local = com.alananasss.kittytune.data.local.AppDatabase.downloadDao.getTrack(track.id)
            if (local != null && local.localAudioPath.isNotEmpty() &&
                !local.localAudioPath.startsWith("exo_cache://")
            ) return // local file — nothing to resolve

            StreamResolver.resolveStreamWithDrm(track)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isColorDark(color: Int): Boolean {
        val r = (color shr 16) and 0xFF;
        val g = (color shr 8) and 0xFF;
        val b = color and 0xFF
        val darkness = 1 - (0.299 * r + 0.587 * g + 0.114 * b) / 255
        return darkness >= 0.5
    }

    private fun buildMediaItem(
        track: Track,
        bitmap: java.awt.image.BufferedImage?,
        urlOverride: String? = null,
        offlineKeySetId: ByteArray? = null
    ): MediaItem {
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
            } catch (_: Exception) {
            }
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

