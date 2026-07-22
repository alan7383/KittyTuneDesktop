@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alananasss.kittytune.ui.library

import androidx.compose.material3.IconButtonDefaults

import androidx.compose.material3.ButtonDefaults

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.onClick
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.AppInstance
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.AlbumResolver
import com.alananasss.kittytune.data.DownloadManager
import com.alananasss.kittytune.data.LikeRepository
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.common.viewableCover
import com.alananasss.kittytune.ui.common.TrackListItemShimmer
import com.alananasss.kittytune.ui.player.PlaybackContext
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.utils.NetworkUtils
import kotlinx.coroutines.flow.first
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URLDecoder

enum class TrackSortBy {
    FIRST_ADDED, RECENTLY_ADDED, TITLE_AZ, ARTIST_AZ
}

/** Width of the gap between likes-table columns; the header's drag handles live in it. */
private val COLUMN_GAP = 16.dp

/** Formats an API date ("2022-10-04T21:58:09Z" or "2022/10/04 21:58:09 +0000") as "dd MMM yyyy". */
private fun formatApiDate(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val millis = runCatching { java.time.Instant.parse(raw).toEpochMilli() }.getOrNull()
        ?: runCatching {
            java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss Z", java.util.Locale.US).parse(raw)?.time
        }.getOrNull()
        ?: runCatching {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(raw)?.time
        }.getOrNull()
        ?: return ""
    return java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(millis))
}

/**
 * User-resizable column weights for the playlist track table (Spotify-style drag
 * handles in the header). Shared by TrackTableHeaderRow and TrackTableItem so header
 * and rows stay aligned; persisted so the layout survives restarts.
 */
/** Spotify-style display mode for playlist/likes/album track lists. */
enum class TrackViewMode { COMPACT, LIST }

/** Single shared instance: TrackTableColumns and TrackViewModePref write the same file. */
private val uiPrefs = com.alananasss.kittytune.core.NamedPrefs("ui_prefs")

/** Persisted display mode, shared by every playlist/likes/album page. */
private object TrackViewModePref {
    var mode by mutableStateOf(
        runCatching { TrackViewMode.valueOf(uiPrefs.getString("track_view_mode", "") ?: "") }
            .getOrDefault(TrackViewMode.LIST)
    )
        private set

    fun set(value: TrackViewMode) {
        mode = value
        uiPrefs.putString("track_view_mode", value.name)
    }
}

private object TrackTableColumns {
    private val prefs get() = uiPrefs
    private const val MIN_WEIGHT = 1f

    var title by mutableStateOf(prefs.getInt("likes_col_title", 500) / 100f)
        private set
    var album by mutableStateOf(prefs.getInt("likes_col_album", 300) / 100f)
        private set
    var date by mutableStateOf(prefs.getInt("likes_col_date", 200) / 100f)
        private set

    fun total(albumVisible: Boolean, dateVisible: Boolean) =
        title + (if (albumVisible) album else 0f) + (if (dateVisible) date else 0f)

    /**
     * With all columns visible: divider 0 = title|album, divider 1 = album|date.
     * With one middle/right column hidden, the single divider 0 resizes the two
     * remaining columns (title|date on album pages, title|album on remote playlists).
     */
    fun drag(divider: Int, deltaWeight: Float, albumVisible: Boolean, dateVisible: Boolean) {
        when {
            !albumVisible && !dateVisible -> return
            !albumVisible -> {
                val pair = title + date
                val newTitle = (title + deltaWeight).coerceIn(MIN_WEIGHT, pair - MIN_WEIGHT)
                title = newTitle
                date = pair - newTitle
            }
            divider == 0 -> {
                val pair = title + album
                val newTitle = (title + deltaWeight).coerceIn(MIN_WEIGHT, pair - MIN_WEIGHT)
                title = newTitle
                album = pair - newTitle
            }
            dateVisible -> {
                val pair = album + date
                val newAlbum = (album + deltaWeight).coerceIn(MIN_WEIGHT, pair - MIN_WEIGHT)
                album = newAlbum
                date = pair - newAlbum
            }
        }
    }

    fun save() {
        prefs.putInt("likes_col_title", (title * 100).toInt())
        prefs.putInt("likes_col_album", (album * 100).toInt())
        prefs.putInt("likes_col_date", (date * 100).toInt())
    }
}

/**
 * User-resizable column weights for the compact view (no artwork, artist in its
 * own column, denser rows). Same drag-handle mechanism as [TrackTableColumns],
 * persisted separately so each mode keeps its own layout.
 */
private object TrackCompactColumns {
    private val prefs get() = uiPrefs
    private const val MIN_WEIGHT = 1f

    var title by mutableStateOf(prefs.getInt("compact_col_title", 350) / 100f)
        private set
    var artist by mutableStateOf(prefs.getInt("compact_col_artist", 220) / 100f)
        private set
    var album by mutableStateOf(prefs.getInt("compact_col_album", 280) / 100f)
        private set
    var date by mutableStateOf(prefs.getInt("compact_col_date", 200) / 100f)
        private set

    fun total(albumVisible: Boolean, dateVisible: Boolean) =
        title + artist + (if (albumVisible) album else 0f) + (if (dateVisible) date else 0f)

    /**
     * Divider 0 = title|artist, divider 1 = artist|album (or artist|date when the
     * album column is hidden), divider 2 = album|date.
     */
    fun drag(divider: Int, deltaWeight: Float, albumVisible: Boolean, dateVisible: Boolean) {
        when (divider) {
            0 -> {
                val pair = title + artist
                val newTitle = (title + deltaWeight).coerceIn(MIN_WEIGHT, pair - MIN_WEIGHT)
                title = newTitle
                artist = pair - newTitle
            }
            1 -> when {
                albumVisible -> {
                    val pair = artist + album
                    val newArtist = (artist + deltaWeight).coerceIn(MIN_WEIGHT, pair - MIN_WEIGHT)
                    artist = newArtist
                    album = pair - newArtist
                }
                dateVisible -> {
                    val pair = artist + date
                    val newArtist = (artist + deltaWeight).coerceIn(MIN_WEIGHT, pair - MIN_WEIGHT)
                    artist = newArtist
                    date = pair - newArtist
                }
            }
            2 -> if (albumVisible && dateVisible) {
                val pair = album + date
                val newAlbum = (album + deltaWeight).coerceIn(MIN_WEIGHT, pair - MIN_WEIGHT)
                album = newAlbum
                date = pair - newAlbum
            }
        }
    }

    fun save() {
        prefs.putInt("compact_col_title", (title * 100).toInt())
        prefs.putInt("compact_col_artist", (artist * 100).toInt())
        prefs.putInt("compact_col_album", (album * 100).toInt())
        prefs.putInt("compact_col_date", (date * 100).toInt())
    }
}

/**
 * Desktop port of the Android PlaylistDetailScreen.
 * Renders inside the center panel; the Android bottom sheets become dropdown
 * menus, drag-reorder/swipe-delete become menu actions, the photo picker is an
 * AWT FileDialog and "share" copies the URL to the clipboard.
 *
 * Handles the same playlistId protocol: "likes", "downloads", "local_files",
 * numeric ids, "station:", "station_artist:", "liked_by:", "local_playlist:",
 * "system_playlist:", "downloaded_section:", "yt_radio:<encoded url>".
 */

@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    onBackClick: () -> Unit,
    onNavigate: (String) -> Unit,
    playerViewModel: PlayerViewModel,
    youtubeRadioViewModel: YoutubeRadioViewModel = viewModel(key = "yt_radio_$playlistId") {
        YoutubeRadioViewModel(AppInstance.application)
    }
) {
    val api = remember { RetrofitClient.create() }
    val storageTrigger by DownloadManager.storageTrigger.collectAsState()
    val scope = rememberCoroutineScope()
    val isYoutubeRadio = playlistId.startsWith("yt_radio:")

    val tracks = remember { mutableStateListOf<Track>() }
    val downloadedPlaylists = remember { mutableStateListOf<Playlist>() }

    val likedTracksRepo by LikeRepository.likedTracks.collectAsState()
    val likedPlaylistsRepo by LikeRepository.likedPlaylists.collectAsState()
    var isAlbum by remember { mutableStateOf(false) }

    var playlistTitle by remember { mutableStateOf("") }
    var playlistSharing by remember { mutableStateOf<String?>(null) }
    var playlistCover by remember { mutableStateOf<String?>(null) }
    var playlistDescription by remember { mutableStateOf<String?>(null) }
    var playlistUrn by remember { mutableStateOf<String?>(null) }
    var playlistPermalinkUrl by remember { mutableStateOf<String?>(null) }

    var playlistGenre by remember { mutableStateOf<String?>(null) }
    var playlistTagList by remember { mutableStateOf<String?>(null) }
    var playlistSetType by remember { mutableStateOf<String?>(null) }
    var playlistReleaseDate by remember { mutableStateOf<String?>(null) }
    var playlistPermalink by remember { mutableStateOf<String?>(null) }

    var playlistUser by remember { mutableStateOf<User?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var defaultIcon by remember { mutableStateOf<ImageVector?>(null) }

    val downloadProgress by DownloadManager.downloadProgress.collectAsState()
    val playlistDownloadProgress by DownloadManager.playlistDownloadProgress.collectAsState()
    val downloadedIds by DownloadManager.downloadedIds.collectAsState()

    val isDownloadedView = playlistId.startsWith("downloaded_section:")

    val cleanIdStr = playlistId.replace("station_artist:", "")
        .replace("station:", "")
        .replace("liked_by:", "")
        .replace("local_playlist:", "")
        .replace("yt_radio:", "")
        .replace("downloaded_section:", "")
        .replace("system_playlist:", "")

    val currentIdLong = cleanIdStr.toLongOrNull() ?: 0L

    val stableId = remember(playlistId, cleanIdStr, currentIdLong) {
        if (currentIdLong != 0L) currentIdLong else cleanIdStr.hashCode().toLong()
    }

    val playlistInDb by DownloadManager.isPlaylistInLibraryFlow(stableId).collectAsState(initial = null)

    val effectiveBatchId = if (playlistId == "likes") DownloadManager.LIKES_BATCH_ID else stableId
    val isPlaylistDownloading = DownloadManager.isPlaylistDownloading(effectiveBatchId)
    val currentPlaylistProgress = playlistDownloadProgress[effectiveBatchId]

    var isUserCreated by remember { mutableStateOf(false) }
    var isLocalPlaylist by remember { mutableStateOf(false) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRemoveDownloadDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showViewModeMenu by remember { mutableStateOf(false) }
    var showDetailsSheet by remember { mutableStateOf(false) }

    var playlistSearchQuery by remember { mutableStateOf("") }
    var playlistSortBy by remember { mutableStateOf(TrackSortBy.FIRST_ADDED) }

    val listState = rememberLazyListState()

    // Drag-to-reorder — only active for user-owned playlists with no active search/sort
    val canReorder = isUserCreated && playlistSearchQuery.isEmpty() && playlistSortBy == TrackSortBy.FIRST_ADDED
    val reorderableState = rememberReorderableLazyListState(lazyListState = listState) { from, to ->
        if (canReorder) {
            val fromIndex = tracks.indexOfFirst { it.id == from.key as? Long }
            val toIndex = tracks.indexOfFirst { it.id == to.key as? Long }
            if (fromIndex != -1 && toIndex != -1 && fromIndex != toIndex) {
                tracks.add(toIndex, tracks.removeAt(fromIndex))
            }
        }
    }
    // Sync to local DB + SoundCloud when the drag ends. The wasDragging latch keeps
    // the effect from firing on first composition with the untouched order. Local
    // persistence also covers negative (local-only) playlist ids; the online sync
    // guards on id > 0 internally.
    val isDragging = reorderableState.isAnyItemDragging
    var wasDragging by remember { mutableStateOf(false) }
    LaunchedEffect(isDragging) {
        if (wasDragging && !isDragging && canReorder && currentIdLong != 0L) {
            val newOrder = tracks.map { it.id }
            DownloadManager.reorderPlaylistTracks(currentIdLong, newOrder)
            DownloadManager.syncPlaylistOrderOnline(currentIdLong, newOrder)
        }
        wasDragging = isDragging
    }

    val rawTracks: List<Track> = if (playlistId == "likes") likedTracksRepo else tracks

    // Warm the album cache from SQLite so already-resolved rows render instantly.
    LaunchedEffect(rawTracks.size) {
        if (rawTracks.isNotEmpty()) AlbumResolver.prefetchFromDb(rawTracks)
    }

    val tracksToDisplay = if (playlistSearchQuery.isEmpty() && playlistSortBy == TrackSortBy.FIRST_ADDED) {
        rawTracks
    } else {
        val filtered = rawTracks.filter {
            it.title?.contains(playlistSearchQuery, ignoreCase = true) == true ||
                it.user?.username?.contains(playlistSearchQuery, ignoreCase = true) == true
        }
        when (playlistSortBy) {
            TrackSortBy.FIRST_ADDED -> filtered
            TrackSortBy.RECENTLY_ADDED -> filtered.reversed()
            TrackSortBy.TITLE_AZ -> filtered.sortedBy { it.title?.lowercase() ?: "" }
            TrackSortBy.ARTIST_AZ -> filtered.sortedBy { it.user?.username?.lowercase() ?: "" }
        }
    }

    // Date column always shown; uses added-at where available (likes, local
    // playlists, downloads), falls back to release/upload date for remote playlists.
    // Computed directly (no remember): tracksToDisplay can be a SnapshotStateList
    // whose identity never changes, and `any` short-circuits on the first element.
    val showDateColumn = true
    val useReleaseDate = tracksToDisplay.any { it.likedAt == null }

    val downloadedCount = remember(tracksToDisplay, downloadedIds) {
        if (tracksToDisplay.isEmpty()) 0
        else tracksToDisplay.count { track -> track.id < 0 || downloadedIds.contains(track.id) }
    }

    val isFullyDownloaded = remember(tracksToDisplay.size, downloadedCount, isPlaylistDownloading) {
        if (tracksToDisplay.isEmpty()) false
        else {
            val ratio = downloadedCount.toFloat() / tracksToDisplay.size.toFloat()
            downloadedCount == tracksToDisplay.size || (ratio > 0.9f && !isPlaylistDownloading)
        }
    }
    val refreshTrigger = if (playlistId == "downloads" || playlistId == "local_files") storageTrigger else 0

    val shareUrl = remember(playlistId, currentIdLong, playlistPermalinkUrl, playlistUser) {
        when {
            playlistId.startsWith("station_artist:") -> "https://soundcloud.com/discover/sets/artist-stations:$currentIdLong"
            playlistId.startsWith("station:") -> "https://soundcloud.com/discover/sets/track-stations:$currentIdLong"
            playlistId.startsWith("yt_radio:") -> {
                val decodedUrl = URLDecoder.decode(cleanIdStr, "UTF-8")
                val videoId = decodedUrl.substringAfter("v=").substringBefore("&")
                "https://www.youtube.com/watch?v=$videoId&list=RD$videoId"
            }
            playlistId.startsWith("liked_by:") -> {
                val profileUrl = playlistPermalinkUrl ?: playlistUser?.permalinkUrl
                if (profileUrl != null) "$profileUrl/likes"
                else "https://soundcloud.com/discover/sets/liked-by::$currentIdLong"
            }
            playlistId == "likes" -> {
                val user = playlistUser
                if (user != null && user.id > 0 && !user.permalinkUrl.isNullOrEmpty()) "${user.permalinkUrl}/likes" else ""
            }
            playlistId == "downloads" -> ""
            currentIdLong > 0 -> playlistPermalinkUrl ?: "https://soundcloud.com/playlists/$currentIdLong"
            else -> ""
        }
    }

    LaunchedEffect(playlistInDb) {
        if (playlistInDb != null) {
            isLocalPlaylist = true
            isUserCreated = playlistInDb!!.isUserCreated || currentIdLong < 0
            playlistTitle = playlistInDb!!.title
            playlistCover = playlistInDb!!.localCoverPath ?: playlistInDb!!.artworkUrl
        } else {
            isLocalPlaylist = currentIdLong < 0
            isUserCreated = currentIdLong < 0
        }
    }

    LaunchedEffect(playlistId, refreshTrigger) {
        if (playlistId.startsWith("yt_radio:")) {
            val encodedUrl = playlistId.removePrefix("yt_radio:")
            val url = URLDecoder.decode(encodedUrl, "UTF-8")
            youtubeRadioViewModel.loadInitial(url)
            return@LaunchedEffect
        }

        if (tracks.isEmpty() && downloadedPlaylists.isEmpty()) {
            isLoading = true
        }

        val newTracks = mutableListOf<Track>()
        val newDownloadedPlaylists = mutableListOf<Playlist>()

        try {
            if (playerViewModel.currentUserId == 0L) {
                playerViewModel.fetchUserProfile()
            }

            val db = AppDatabase.downloadDao

            when {
                playlistId == "likes" -> {
                    playlistTitle = str("lib_liked_tracks")
                    defaultIcon = Icons.Rounded.Favorite
                    playlistUser = try {
                        api.getMe()
                    } catch (e: Exception) {
                        User(0, str("me_artist"), null)
                    }
                }

                playlistId == "downloads" -> {
                    playlistTitle = str("lib_downloads")
                    defaultIcon = Icons.Rounded.Folder
                    isLocalPlaylist = false
                    val localPlaylists = db.getDownloadedPlaylists().first()
                    newDownloadedPlaylists.addAll(localPlaylists.map { local ->
                        val tracksInPlaylist = db.getTracksForPlaylistSync(local.id)
                        val realDownloadedCount = tracksInPlaylist.count { it.localAudioPath.isNotEmpty() }
                        Playlist(
                            id = local.id,
                            title = local.title,
                            artworkUrl = local.artworkUrl,
                            calculatedArtworkUrl = local.localCoverPath,
                            trackCount = realDownloadedCount,
                            user = User(0, local.artist, null),
                            tracks = null
                        )
                    })
                    val allDownloadedTracks = db.getAllTracksList()
                    newTracks.addAll(allDownloadedTracks.map { local ->
                        Track(
                            id = local.id,
                            title = local.title,
                            artworkUrl = local.localArtworkPath.ifEmpty { local.artworkUrl },
                            durationMs = local.duration,
                            user = User(0, local.artist, null),
                            isLiked = true,
                            likedAt = local.downloadedAt.takeIf { it > 0 }
                        )
                    })
                }

                playlistId == "local_files" -> {
                    playlistTitle = str("lib_local_media")
                    defaultIcon = Icons.Default.SdStorage
                    isLocalPlaylist = false
                    val allTracks = db.getAllTracksList()
                    val localFileTracks = allTracks.filter { it.id < 0 }
                    newTracks.addAll(localFileTracks.map { local ->
                        Track(
                            id = local.id,
                            title = local.title,
                            artworkUrl = local.localArtworkPath.ifEmpty { local.artworkUrl },
                            durationMs = local.duration,
                            user = User(0, local.artist, null),
                            description = str("description_local_file", local.localAudioPath),
                            likedAt = local.downloadedAt.takeIf { it > 0 }
                        )
                    })
                }

                playlistId.startsWith("liked_by:") -> {
                    val targetUserId = currentIdLong
                    defaultIcon = Icons.Rounded.Favorite
                    val user = api.getUser(targetUserId)
                    playlistTitle = "Liked by ${user.username}"
                    playlistCover = user.avatarUrl?.replace("large", "t500x500")
                    playlistUser = user
                    playlistPermalinkUrl = user.permalinkUrl

                    val allCollectedTracks = mutableListOf<Track>()
                    val likesResponse = api.getUserTrackLikes(targetUserId, limit = 200)
                    allCollectedTracks.addAll(likesResponse.collection.mapNotNull { it.track })

                    var nextUrl = likesResponse.next_href
                    var safetyPageCount = 0
                    while (nextUrl != null && safetyPageCount < 50) {
                        try {
                            val nextResponse = api.getTrackLikesNextPage(nextUrl!!)
                            allCollectedTracks.addAll(nextResponse.collection.mapNotNull { it.track })
                            nextUrl = nextResponse.next_href
                            safetyPageCount++
                        } catch (e: Exception) {
                            e.printStackTrace()
                            break
                        }
                    }
                    newTracks.addAll(allCollectedTracks.distinctBy { it.id })
                }

                else -> {
                    val isOffline = !NetworkUtils.isInternetAvailable()
                    val forceLocal = isOffline || isDownloadedView || currentIdLong < 0
                    val localPlaylist = if (currentIdLong != 0L && forceLocal) db.getPlaylist(currentIdLong) else null
                    if (localPlaylist != null) {
                        playlistTitle = localPlaylist.title
                        playlistCover = localPlaylist.localCoverPath ?: localPlaylist.artworkUrl
                        playlistUser = User(0, localPlaylist.artist, null)
                        isUserCreated = localPlaylist.isUserCreated || currentIdLong < 0
                        isLocalPlaylist = true
                        playlistPermalinkUrl = localPlaylist.permalinkUrl

                        val playlistTracks = db.getTracksForPlaylistSync(currentIdLong)
                        val filteredTracks = if (isDownloadedView) {
                            playlistTracks.filter { it.localAudioPath.isNotEmpty() }
                        } else playlistTracks

                        val addedAtMap = db.getAddedAtForPlaylist(currentIdLong)
                        newTracks.addAll(filteredTracks.map { local ->
                            Track(
                                id = local.id,
                                title = local.title,
                                artworkUrl = local.localArtworkPath.ifEmpty { local.artworkUrl },
                                durationMs = local.duration,
                                user = User(0, local.artist, null),
                                likedAt = addedAtMap[local.id]?.takeIf { it > 0 }
                            )
                        })
                    } else {
                        val isSystemPlaylistRoute = playlistId.startsWith("system_playlist:")
                        if (currentIdLong > 0L || isSystemPlaylistRoute) {
                            val isArtistStation = playlistId.startsWith("station_artist:")
                            val isTrackStation = playlistId.startsWith("station:")

                            val localFallback = if (currentIdLong != 0L) db.getPlaylist(currentIdLong) else null
                            if (localFallback != null) {
                                playlistTitle = localFallback.title
                                playlistCover = localFallback.localCoverPath ?: localFallback.artworkUrl
                                playlistUser = User(0, localFallback.artist, null)
                                isUserCreated = localFallback.isUserCreated
                            }

                            val playlistObj = when {
                                isSystemPlaylistRoute -> api.getSystemPlaylist(cleanIdStr)
                                isArtistStation -> api.getArtistStation(currentIdLong)
                                isTrackStation -> api.getTrackStation(currentIdLong)
                                else -> api.getPlaylist(currentIdLong)
                            }
                            isAlbum = playlistObj.isAlbum

                            playlistTitle = playlistObj.title.takeIf { !it.isNullOrBlank() } ?: playlistTitle
                            playlistCover = playlistObj.fullResArtwork ?: playlistCover
                            playlistUser = playlistObj.user ?: playlistUser
                            isUserCreated = (playlistUser?.id != 0L && playlistUser?.id == playerViewModel.currentUserId) ||
                                (playerViewModel.currentUser != null && playlistUser?.username == playerViewModel.currentUser?.username)
                            playlistSharing = playlistObj.sharing
                            playlistDescription = playlistObj.description
                            playlistUrn = playlistObj.urn
                            playlistGenre = playlistObj.genre
                            playlistTagList = playlistObj.tagList
                            playlistSetType = playlistObj.setType
                            playlistReleaseDate = playlistObj.releaseDate
                            playlistPermalink = playlistObj.permalink
                            playlistPermalinkUrl = playlistObj.permalinkUrl.takeIf { !it.isNullOrBlank() }
                                ?: if (isArtistStation) "https://soundcloud.com/discover/sets/artist-stations:$currentIdLong"
                                else if (isTrackStation) "https://soundcloud.com/discover/sets/track-stations:$currentIdLong"
                                else null

                            val rawPlaylistTracks = playlistObj.tracks ?: emptyList()
                            val incompleteIds = rawPlaylistTracks.filter { it.title.isNullOrBlank() || it.user == null }.map { it.id }

                            if (incompleteIds.isNotEmpty()) {
                                val fetchedTracksMap = mutableMapOf<Long, Track>()
                                incompleteIds.chunked(50).forEach { batchIds ->
                                    try {
                                        val fetched = api.getTracksByIds(batchIds.joinToString(","))
                                        fetched.forEach { fetchedTracksMap[it.id] = it }
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                                newTracks.addAll(rawPlaylistTracks.map { track ->
                                    if (track.title.isNullOrBlank() || track.user == null) fetchedTracksMap[track.id] ?: track else track
                                })
                            } else {
                                newTracks.addAll(rawPlaylistTracks)
                            }
                        }
                    }
                }
            }

            if (playlistId != "likes") {
                tracks.clear()
                tracks.addAll(newTracks)
            }

            if (playlistId == "downloads") {
                downloadedPlaylists.clear()
                downloadedPlaylists.addAll(newDownloadedPlaylists)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    if (playlistId.startsWith("yt_radio:")) {
        playlistTitle = youtubeRadioViewModel.playlistTitle
        playlistCover = youtubeRadioViewModel.playlistCover
        playlistUser = youtubeRadioViewModel.playlistUser
        tracks.clear()
        tracks.addAll(youtubeRadioViewModel.tracks)
        isLoading = youtubeRadioViewModel.isLoading
    }

    // ---------------------------------------------------------------- dialogs

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(str(if (isUserCreated) "dialog_delete_playlist_title" else "dialog_delete_playlist_from_lib_title")) },
            text = { Text(str("dialog_delete_playlist_msg")) },
            confirmButton = {
                TextButton(onClick = {
                    if (stableId != 0L) {
                        DownloadManager.deletePlaylist(
                            playlistId = stableId,
                            forceUserCreated = isUserCreated,
                            forcePermalink = playlistPermalinkUrl
                        )
                    }
                    showDeleteDialog = false
                    onBackClick()
                }) { Text(str("btn_confirm"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(str("btn_cancel")) } }
        )
    }

    if (showRemoveDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDownloadDialog = false },
            title = { Text(str("dialog_remove_download_title")) },
            text = { Text(str("dialog_remove_download_msg")) },
            confirmButton = {
                TextButton(onClick = {
                    if (playlistId == "likes") {
                        // Full list, not tracksToDisplay — an active search must not
                        // limit the removal to the visible subset.
                        DownloadManager.removeDownloads(rawTracks.toList())
                    } else if (currentIdLong != 0L) {
                        DownloadManager.removePlaylistDownloads(currentIdLong)
                    }
                    showRemoveDownloadDialog = false
                    if (isDownloadedView) onBackClick()
                }) { Text(str("btn_delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showRemoveDownloadDialog = false }) { Text(str("btn_cancel")) } }
        )
    }

    if (showRenameDialog) {
        var newTitle by remember { mutableStateOf(playlistTitle) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(str("profile_edit")) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (currentIdLong != 0L && newTitle.isNotBlank()) {
                        DownloadManager.editPlaylistMetadata(currentIdLong, newTitle)
                        playlistTitle = newTitle
                    }
                    showRenameDialog = false
                }) { Text(str("btn_confirm")) }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text(str("btn_cancel")) } }
        )
    }

    if (showDetailsSheet) {
        Dialog(onDismissRequest = { showDetailsSheet = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.width(620.dp).heightIn(max = 680.dp)
            ) {
                PlaylistDetailsSheet(
                    // system_playlist: ids are urns, not numeric — pass them verbatim
                    playlistId = if (playlistId.startsWith("system_playlist:")) playlistId else currentIdLong.toString(),
                    onDismiss = { showDetailsSheet = false },
                    onViewAll = { tabIndex ->
                        showDetailsSheet = false
                        onNavigate("playlist_fans/$currentIdLong?tab=$tabIndex")
                    },
                    onNavigate = { dest -> showDetailsSheet = false; onNavigate(dest) },
                    onMentionClick = { username ->
                        showDetailsSheet = false
                        playerViewModel.resolveAndNavigateToArtist(username)
                    }
                )
            }
        }
    }

    if (showEditDialog) {
        EditPlaylistScreen(
            initialTitle = playlistTitle ?: "",
            initialDescription = playlistDescription,
            initialSharing = playlistSharing,
            initialTagList = playlistTagList,
            initialGenre = playlistGenre,
            initialSetType = playlistSetType,
            initialReleaseDate = playlistReleaseDate,
            initialPermalink = playlistPermalink,
            playlistUser = playlistUser,
            onDismissRequest = { showEditDialog = false },
            onSave = { title, description, sharing, tagList, genre, setType, releaseDate, permalink ->
                showEditDialog = false
                // Routes through DownloadManager so the local DB stays in sync, the
                // online track list is preserved (trackUrns re-fetched) and DataDome
                // 403 captchas are handled — same path as Android.
                DownloadManager.editPlaylistMetadata(
                    playlistId = currentIdLong,
                    newTitle = title,
                    newDescription = description,
                    newSharing = sharing,
                    newTagList = tagList,
                    newPermalink = permalink,
                    newGenre = genre,
                    newSetType = setType,
                    newReleaseDate = releaseDate
                )
                playlistTitle = title
                playlistDescription = description
                playlistSharing = sharing
                playlistTagList = tagList
                playlistGenre = genre
                playlistSetType = setType
                playlistReleaseDate = releaseDate
                playlistPermalink = permalink
            }
        )
    }

    val playbackContext = remember(playlistId, playlistTitle, playlistCover, playlistUser, isAlbum) {
        val creatorName = playlistUser?.username
        val isVerified = playlistUser?.verified == true
        when {
            playlistId == "likes" -> PlaybackContext(str("context_playlist", str("lib_liked_tracks")), "likes", playlistCover, artistName = null)
            playlistId == "downloads" -> PlaybackContext(str("context_playlist", str("lib_downloads")), "downloads", playlistCover, artistName = null)
            playlistId.startsWith("station") || playlistId.startsWith("yt_radio:") ->
                PlaybackContext(str("context_station", playlistTitle), playlistId, playlistCover, artistName = null, isVerified = isVerified)
            isAlbum -> PlaybackContext(str("context_album", playlistTitle), playlistId, playlistCover, artistName = creatorName, isVerified = isVerified)
            else -> PlaybackContext(str("context_playlist", playlistTitle), playlistId, playlistCover, artistName = creatorName, isVerified = isVerified)
        }
    }

    // ---------------------------------------------------------------- layout

    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow

    Box(modifier = Modifier.fillMaxSize()) {
        // Blurred cover backdrop fading into the panel background.
        if (!playlistCover.isNullOrEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(340.dp)) {
                AsyncImage(
                    model = playlistCover,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().blur(100.dp).alpha(0.5f),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                backgroundColor.copy(alpha = 0.3f),
                                backgroundColor.copy(alpha = 0.8f),
                                backgroundColor
                            )
                        )
                    )
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // -------- header: cover + meta + actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp).padding(top = 32.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Card(shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(12.dp), modifier = Modifier.size(180.dp)) {
                        if (!playlistCover.isNullOrEmpty()) {
                            AsyncImage(model = playlistCover, contentDescription = null, modifier = Modifier.fillMaxSize().viewableCover(playlistCover), contentScale = ContentScale.Crop)
                        } else if (defaultIcon != null) {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                                Icon(defaultIcon!!, null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(Modifier.width(24.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = playlistTitle,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (playlistSharing == "private" || playlistSharing == "secret") {
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Rounded.Lock, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (playlistUser != null && playlistUser!!.id > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { playerViewModel.navigateToArtist(playlistUser!!.id) }
                            ) {
                                Text(str("playlist_by_user", playlistUser!!.username ?: ""), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                if (playlistUser?.verified == true) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                }
                            }
                        } else if (playlistUser?.username != null) {
                            Text(
                                str("playlist_by_user", playlistUser?.username ?: str("me_artist")),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        Spacer(Modifier.height(4.dp))

                        val trackCountText = when {
                            isYoutubeRadio -> str("radio") + " â€¢ YouTube"
                            isLoading && playlistId != "likes" -> "..."
                            else -> {
                                val count = tracksToDisplay.size + downloadedPlaylists.sumOf { it.trackCount ?: 0 }
                                if (count == 0 && playlistSearchQuery.isNotEmpty()) str("no_tracks_found_filter")
                                else str("playlist_num_tracks", count)
                            }
                        }
                        if (trackCountText.isNotEmpty()) {
                            Text(trackCountText, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(12.dp))

                        // Action row
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (tracksToDisplay.isNotEmpty()) {
                                Button(shapes = ButtonDefaults.shapes(), onClick = { playerViewModel.playPlaylist(tracksToDisplay.toList(), 0, playbackContext) },
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(str("btn_play"), fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(8.dp))
                                FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = { playerViewModel.playPlaylist(tracksToDisplay.toList().shuffled(), context = playbackContext) },
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Icon(Icons.Default.Shuffle, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(str("btn_shuffle"), fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(8.dp))
                            }

                            if ((isLocalPlaylist || isUserCreated) && !isYoutubeRadio) {
                                IconButton(shapes = IconButtonDefaults.shapes(), onClick = { 
                                    if (isUserCreated) showEditDialog = true else showRenameDialog = true 
                                }) {
                                    Icon(Icons.Outlined.Edit, str("profile_edit"))
                                }
                                IconButton(onClick = {
                                    val dialog = FileDialog(null as Frame?, str("storage_change_btn"), FileDialog.LOAD)
                                    dialog.setFilenameFilter { _, name ->
                                        name.endsWith(".png", true) || name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) || name.endsWith(".webp", true)
                                    }
                                    dialog.isVisible = true
                                    val file = dialog.files.firstOrNull()
                                    if (file != null && currentIdLong != 0L) DownloadManager.updatePlaylistCover(currentIdLong, file)
                                }) {
                                    Icon(Icons.Outlined.Image, str("storage_change_btn"))
                                }
                            }

                            if (playlistId != "downloads" && playlistId != "likes" && playlistId != "local_files") {
                                if (isUserCreated) {
                                    IconButton(onClick = { showDeleteDialog = true }) {
                                        Icon(Icons.Default.Delete, str("btn_delete"), tint = MaterialTheme.colorScheme.error)
                                    }
                                } else {
                                    val isPlaylistLiked = likedPlaylistsRepo.contains(stableId)
                                    IconButton(onClick = {
                                        LikeRepository.togglePlaylistLike(stableId, !isPlaylistLiked, playlistPermalinkUrl, playlistUrn)
                                    }) {
                                        if (isPlaylistLiked) Icon(Icons.Rounded.Favorite, str("lib_liked_tracks"), tint = MaterialTheme.colorScheme.primary)
                                        else Icon(Icons.Outlined.FavoriteBorder, str("menu_add_playlist"))
                                    }
                                }
                            }

                            if (!isYoutubeRadio && playlistId != "downloads" && tracksToDisplay.isNotEmpty()) {
                                IconButton(onClick = {
                                    val targetBatchId = if (playlistId == "likes") DownloadManager.LIKES_BATCH_ID else stableId
                                    if (isFullyDownloaded || isDownloadedView) {
                                        showRemoveDownloadDialog = true
                                    } else if (isPlaylistDownloading) {
                                        DownloadManager.cancelBatch(targetBatchId)
                                    } else {
                                        if (playlistId == "likes") {
                                            DownloadManager.downloadBatch(tracksToDisplay.toList(), DownloadManager.LIKES_BATCH_ID)
                                        } else if (stableId != 0L) {
                                            val fakePlaylist = Playlist(stableId, playlistTitle, playlistCover, null, tracks.size, playlistUser, null)
                                            DownloadManager.downloadPlaylist(fakePlaylist, tracks.toList())
                                        }
                                    }
                                }) {
                                    when {
                                        isFullyDownloaded || isDownloadedView -> Icon(Icons.Rounded.Delete, str("btn_delete"), tint = MaterialTheme.colorScheme.error)
                                        isPlaylistDownloading -> Icon(Icons.Rounded.Close, str("btn_cancel"))
                                        else -> Icon(Icons.Rounded.Download, str("btn_download"))
                                    }
                                }
                            }

                            // Overflow menu: queue actions + share
                            Box {
                                IconButton(onClick = { showOptionsMenu = true }) {
                                    Icon(Icons.Default.MoreVert, str("btn_options"))
                                }
                                DropdownMenu(expanded = showOptionsMenu, onDismissRequest = { showOptionsMenu = false }) {
                                    if (!isYoutubeRadio) {
                                        DropdownMenuItem(
                                            text = { Text(str("menu_play_next")) },
                                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null) },
                                            onClick = { playerViewModel.insertNext(tracksToDisplay.toList()); showOptionsMenu = false }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(str("menu_add_queue")) },
                                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) },
                                            onClick = { playerViewModel.addToQueue(tracksToDisplay.toList()); showOptionsMenu = false }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(str("menu_add_playlist")) },
                                            leadingIcon = { Icon(Icons.Default.Add, null) },
                                            onClick = { playerViewModel.prepareBulkAdd(tracksToDisplay.toList()); showOptionsMenu = false }
                                        )
                                    }
                                    val isSystemPlaylist = playlistId.startsWith("system_playlist:")
                                    if (!isLocalPlaylist && (currentIdLong > 0 || isSystemPlaylist) && !isYoutubeRadio &&
                                        !playlistId.startsWith("station") && playlistId != "likes" &&
                                        playlistId != "downloads" && playlistId != "local_files"
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(str("menu_playlist_details")) },
                                            leadingIcon = { Icon(Icons.Rounded.Info, null) },
                                            onClick = { showOptionsMenu = false; showDetailsSheet = true }
                                        )
                                    }
                                    playlistUser?.id?.takeIf { it > 0 }?.let { ownerId ->
                                        DropdownMenuItem(
                                            text = { Text(str("menu_go_artist")) },
                                            leadingIcon = { Icon(Icons.Default.Person, null) },
                                            onClick = { showOptionsMenu = false; playerViewModel.navigateToArtist(ownerId) }
                                        )
                                    }
                                    if (shareUrl.isNotEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text(str("btn_share")) },
                                            leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                                            onClick = {
                                                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(shareUrl), null)
                                                showOptionsMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // -------- playlist download progress
            item {
                AnimatedVisibility(
                    visible = isPlaylistDownloading && currentPlaylistProgress != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val percentage = ((currentPlaylistProgress ?: 0f) * 100).toInt()
                        Text(
                            str("playlist_downloading_progress", percentage),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearWavyProgressIndicator(
                            progress = { currentPlaylistProgress ?: 0f },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }

            // -------- "downloads" root: playlist grid section
            if (playlistId == "downloads" && downloadedPlaylists.isNotEmpty()) {
                item {
                    Text(
                        str("lib_playlists"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
                val chunkedPlaylists = downloadedPlaylists.chunked(4)
                items(chunkedPlaylists.size) { index ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        for (playlist in chunkedPlaylists[index]) {
                            Box(modifier = Modifier.weight(1f)) {
                                PlaylistSquareCard(playlist = playlist) {
                                    val id = if (playlist.id < 0) "local_playlist:${playlist.id}" else playlist.id.toString()
                                    onNavigate("downloaded_section:$id")
                                }
                            }
                        }
                        repeat(4 - chunkedPlaylists[index].size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
                if (tracksToDisplay.isNotEmpty()) {
                    item {
                        Text(
                            str("profile_tracks"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                        )
                    }
                }
            }

            // -------- track list
            if (isLoading && playlistId != "likes") {
                items(10) { TrackListItemShimmer() }
            } else {
                val isReallyEmpty = rawTracks.isEmpty() &&
                    (playlistId != "downloads" || downloadedPlaylists.isEmpty()) &&
                    !isYoutubeRadio

                if (isReallyEmpty) {
                    item { EmptyPlaylistView(playlistId = playlistId, isUserCreated = isUserCreated) }
                } else {
                    if (isYoutubeRadio && rawTracks.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                CircularWavyProgressIndicator()
                            }
                        }
                    }
                    if (rawTracks.isNotEmpty()) {
                        item(key = "search_and_sort") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = playlistSearchQuery,
                                    onValueChange = { playlistSearchQuery = it },
                                    placeholder = { Text(str("search_playlist_hint")) },
                                    leadingIcon = { Icon(Icons.Default.Search, null) },
                                    trailingIcon = {
                                        if (playlistSearchQuery.isNotEmpty()) {
                                            IconButton(onClick = { playlistSearchQuery = "" }) { Icon(Icons.Rounded.Close, null) }
                                        }
                                    },
                                    singleLine = true,
                                    shape = CircleShape,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                Box {
                                    FilledTonalIconButton(onClick = { showSortMenu = true }) {
                                        Icon(Icons.Rounded.Sort, str("btn_options"))
                                    }
                                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                        val options = listOf(
                                            TrackSortBy.FIRST_ADDED to str("sort_first_added"),
                                            TrackSortBy.RECENTLY_ADDED to str("sort_recently_added"),
                                            TrackSortBy.TITLE_AZ to str("sort_title_az"),
                                            TrackSortBy.ARTIST_AZ to str("sort_artist_az")
                                        )
                                        options.forEach { (sortType, label) ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        label,
                                                        fontWeight = if (playlistSortBy == sortType) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (playlistSortBy == sortType) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                    )
                                                },
                                                trailingIcon = {
                                                    if (playlistSortBy == sortType) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                                                },
                                                onClick = { playlistSortBy = sortType; showSortMenu = false }
                                            )
                                        }
                                    }
                                }
                                // Spotify-style view mode picker (Compact / List)
                                Box {
                                    val viewMode = TrackViewModePref.mode
                                    TextButton(
                                        onClick = { showViewModeMenu = true },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                                    ) {
                                        Text(
                                            if (viewMode == TrackViewMode.COMPACT) str("view_mode_compact") else str("view_mode_list"),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Icon(
                                            if (viewMode == TrackViewMode.COMPACT) Icons.Rounded.Menu else Icons.AutoMirrored.Rounded.FormatListBulleted,
                                            str("lib_view_mode"),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    DropdownMenu(expanded = showViewModeMenu, onDismissRequest = { showViewModeMenu = false }) {
                                        Text(
                                            str("lib_view_mode"),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                        val modes = listOf(
                                            Triple(TrackViewMode.COMPACT, str("view_mode_compact"), Icons.Rounded.Menu),
                                            Triple(TrackViewMode.LIST, str("view_mode_list"), Icons.AutoMirrored.Rounded.FormatListBulleted)
                                        )
                                        modes.forEach { (mode, label, icon) ->
                                            val selected = viewMode == mode
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        label,
                                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                    )
                                                },
                                                leadingIcon = {
                                                    Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                                },
                                                trailingIcon = {
                                                    if (selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                                                },
                                                onClick = { TrackViewModePref.set(mode); showViewModeMenu = false }
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        if (tracksToDisplay.isEmpty() && playlistSearchQuery.isNotEmpty()) {
                            item { EmptyPlaylistView(playlistId = playlistId, isUserCreated = isUserCreated, isEmptySearch = true) }
                        }
                    }

                    if (tracksToDisplay.isNotEmpty()) {
                        item(key = "table_header") {
                            if (TrackViewModePref.mode == TrackViewMode.COMPACT) {
                                TrackCompactHeaderRow(showAlbum = !isAlbum, showDate = showDateColumn, releaseDateMode = useReleaseDate)
                            } else {
                                TrackTableHeaderRow(showAlbum = !isAlbum, showDate = showDateColumn, releaseDateMode = useReleaseDate)
                            }
                        }
                    }

                    itemsIndexed(items = tracksToDisplay, key = { _, t -> t.id }) { index, track ->
                        if (index >= tracksToDisplay.size - 5 && isYoutubeRadio) {
                            LaunchedEffect(Unit) { youtubeRadioViewModel.loadMore() }
                        }

                        val progress = downloadProgress[track.id]
                        val isDownloading = progress != null
                        val isDownloaded = remember(track.id, downloadedIds) {
                            (track.id < 0 && track.source != "youtube") || downloadedIds.contains(track.id)
                        }

                        ReorderableItem(reorderableState, key = track.id) { isDraggingThis ->
                            val dragHandleModifier = if (canReorder)
                                Modifier.draggableHandle()
                            else Modifier

                            if (TrackViewModePref.mode == TrackViewMode.COMPACT) {
                                TrackCompactItem(
                                    track = track,
                                    currentlyPlayingTrack = playerViewModel.currentTrack,
                                    index = index,
                                    isDownloading = isDownloading,
                                    isDownloaded = isDownloaded,
                                    downloadProgress = progress ?: 0,
                                    onClick = { playerViewModel.playPlaylist(tracksToDisplay.toList(), index, playbackContext) },
                                    onOptionClick = {
                                        val contextId = if (playlistId == "downloads") -2L else if (isUserCreated) currentIdLong else null
                                        playerViewModel.showTrackOptions(track, contextId)
                                    },
                                    onAlbumClick = { onNavigate(it) },
                                    onArtistClick = { playerViewModel.navigateToArtist(it) },
                                    showAlbum = !isAlbum,
                                    showDate = showDateColumn,
                                    dragHandleModifier = dragHandleModifier,
                                    isDragging = isDraggingThis
                                )
                            } else {
                                TrackTableItem(
                                    track = track,
                                    currentlyPlayingTrack = playerViewModel.currentTrack,
                                    index = index,
                                    isDownloading = isDownloading,
                                    isDownloaded = isDownloaded,
                                    downloadProgress = progress ?: 0,
                                    showVerifiedBadge = false,
                                    onClick = { playerViewModel.playPlaylist(tracksToDisplay.toList(), index, playbackContext) },
                                    onOptionClick = {
                                        val contextId = if (playlistId == "downloads") -2L else if (isUserCreated) currentIdLong else null
                                        playerViewModel.showTrackOptions(track, contextId)
                                    },
                                    onAlbumClick = { onNavigate(it) },
                                    showAlbum = !isAlbum,
                                    showDate = showDateColumn,
                                    useReleaseDate = useReleaseDate,
                                    dragHandleModifier = dragHandleModifier,
                                    isDragging = isDraggingThis
                                )
                            }
                        }
                    }
                    if (isYoutubeRadio && youtubeRadioViewModel.isLoadingMore) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularWavyProgressIndicator()
                            }
                        }
                    }
                }
            }
        }

    }
}

@Composable
fun PlaylistSquareCard(playlist: Playlist, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        AsyncImage(
            model = playlist.fullResArtwork,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = playlist.title ?: str("generic_title"),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = str("playlist_num_tracks", playlist.trackCount ?: 0),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TrackListItem(
    track: Track,
    currentlyPlayingTrack: Track? = null,
    index: Int,
    isDownloading: Boolean,
    isDownloaded: Boolean,
    downloadProgress: Int,
    modifier: Modifier = Modifier,
    showVerifiedBadge: Boolean = true,
    onClick: () -> Unit,
    onOptionClick: () -> Unit
) {
    val isCurrent = currentlyPlayingTrack?.id == track.id
    val titleColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val titleWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold

    androidx.compose.material3.TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .padding(horizontal = 16.dp)
            .onClick(
                matcher = PointerMatcher.mouse(PointerButton.Secondary),
                onClick = onOptionClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth().height(64.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = track.fullResArtwork,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .alpha(if (isDownloading) 0.3f else 1f),
                    contentScale = ContentScale.Crop
                )
                if (isDownloading) {
                    CircularWavyProgressIndicator(
                        progress = { downloadProgress / 100f },
                        modifier = Modifier.size(28.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                }
                if (isCurrent && !isDownloading) {
                    Box(
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.GraphicEq, str("player_playing_now"), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title ?: str("untitled_track"),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = titleWeight,
                    color = titleColor
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isDownloaded && !isDownloading) {
                        Icon(Icons.Rounded.DownloadDone, str("btn_downloaded"), modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = track.user?.username ?: str("unknown_artist"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (showVerifiedBadge && track.user?.verified == true) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                    }
                }
            }
            IconButton(onClick = onOptionClick, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.MoreVert, str("btn_options"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun TrackTableHeaderRow(
    modifier: Modifier = Modifier,
    showAlbum: Boolean = true,
    showDate: Boolean = true,
    releaseDateMode: Boolean = false
) {
    var weightedWidthPx by remember { mutableStateOf(0f) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = str("table_header_index"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(48.dp),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Spacer(Modifier.width(16.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .onGloballyPositioned { weightedWidthPx = it.size.width.toFloat() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = str("table_header_title"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(TrackTableColumns.title),
                    maxLines = 1
                )
                if (showAlbum || showDate) {
                    ColumnDragHandle(divider = 0, weightedWidthPx = { weightedWidthPx }, albumVisible = showAlbum, dateVisible = showDate)
                }
                if (showAlbum) {
                    Text(
                        text = str("table_header_album"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(TrackTableColumns.album),
                        maxLines = 1
                    )
                    if (showDate) {
                        ColumnDragHandle(divider = 1, weightedWidthPx = { weightedWidthPx }, albumVisible = true, dateVisible = true)
                    }
                }
                if (showDate) {
                    Text(
                        text = str(if (releaseDateMode) "table_header_release_date" else "table_header_date_added"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(TrackTableColumns.date),
                        maxLines = 1
                    )
                }
            }
            Box(modifier = Modifier.width(60.dp).padding(end = 16.dp), contentAlignment = Alignment.CenterEnd) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(40.dp))
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

/** Spotify-style draggable column divider in the track table header. */
@Composable
private fun ColumnDragHandle(divider: Int, weightedWidthPx: () -> Float, albumVisible: Boolean, dateVisible: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val dragState = rememberDraggableState { deltaPx ->
        val w = weightedWidthPx()
        if (w > 0f) TrackTableColumns.drag(divider, deltaPx / w * TrackTableColumns.total(albumVisible, dateVisible), albumVisible, dateVisible)
    }
    Box(
        modifier = Modifier
            .width(COLUMN_GAP)
            .height(20.dp)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon(java.awt.Cursor(java.awt.Cursor.E_RESIZE_CURSOR)))
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                onDragStopped = { TrackTableColumns.save() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .width(if (hovered) 2.dp else 1.dp)
                .fillMaxHeight()
                .background(
                    if (hovered) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
                )
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TrackTableItem(
    track: Track,
    currentlyPlayingTrack: Track? = null,
    index: Int,
    isDownloading: Boolean,
    isDownloaded: Boolean,
    downloadProgress: Int,
    modifier: Modifier = Modifier,
    showVerifiedBadge: Boolean = true,
    onClick: () -> Unit,
    onOptionClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    showAlbum: Boolean = true,
    showDate: Boolean = true,
    useReleaseDate: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    isDragging: Boolean = false
) {
    val isCurrent = currentlyPlayingTrack?.id == track.id
    val titleColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val titleWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold

    val albumState by AlbumResolver.stateFor(track).collectAsState()
    LaunchedEffect(track.id, showAlbum) { if (showAlbum) AlbumResolver.requestResolve(track) }

    androidx.compose.material3.TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .padding(horizontal = 16.dp)
            .background(
                if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .onClick(
                matcher = PointerMatcher.mouse(PointerButton.Secondary),
                onClick = onOptionClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                if (dragHandleModifier != Modifier) {
                    Icon(
                        Icons.Rounded.DragHandle,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp).then(dragHandleModifier)
                    )
                } else if (isCurrent && !isDownloading) {
                    Icon(Icons.Rounded.GraphicEq, str("player_playing_now"), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                } else {
                    Text(
                        text = (index + 1).toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Visible
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(TrackTableColumns.title), verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = track.fullResArtwork,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .alpha(if (isDownloading) 0.3f else 1f),
                        contentScale = ContentScale.Crop
                    )
                    if (isDownloading) {
                        CircularWavyProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = track.title ?: str("untitled_track"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = titleWeight,
                        color = titleColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isDownloaded && !isDownloading) {
                            Icon(Icons.Rounded.DownloadDone, str("btn_downloaded"), modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = track.user?.username ?: str("unknown_artist"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (showVerifiedBadge && track.user?.verified == true) {
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
            if (showAlbum) {
                Spacer(Modifier.width(COLUMN_GAP))
                Box(modifier = Modifier.weight(TrackTableColumns.album).padding(end = 8.dp), contentAlignment = Alignment.CenterStart) {
                val resolved = albumState as? AlbumResolver.AlbumUiState.Resolved
                if (resolved != null) {
                    val albumId = resolved.info.playlistId
                    if (albumId != null) {
                        val interaction = remember { MutableInteractionSource() }
                        val hovered by interaction.collectIsHoveredAsState()
                        Text(
                            text = resolved.info.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (hovered) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = if (hovered) TextDecoration.Underline else null,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .hoverable(interaction)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable(interactionSource = interaction, indication = null) {
                                    onAlbumClick(albumId.toString())
                                }
                        )
                    } else {
                        Text(
                            text = resolved.info.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                }
            }
            if (showDate) {
                // Added date (like stamp, playlist cross-ref, or download time) where we
                // have it; release/upload date on remote playlists where SoundCloud
                // doesn't expose per-track added-at (header label switches accordingly).
                val dateStr = remember(track.likedAt, track.releaseDate, track.createdAt, useReleaseDate) {
                    track.likedAt?.let {
                        java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(it))
                    } ?: formatApiDate(track.releaseDate ?: track.createdAt)
                }
                Spacer(Modifier.width(COLUMN_GAP))
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(TrackTableColumns.date),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            }
            val durationStr = remember(track.durationMs) {
                track.durationMs?.let {
                    val totalSecs = it / 1000
                    val m = totalSecs / 60
                    val s = totalSecs % 60
                    String.format("%d:%02d", m, s)
                } ?: ""
            }
            Text(
                text = durationStr,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(60.dp),
                textAlign = TextAlign.End
            )
            IconButton(onClick = onOptionClick, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.MoreVert, str("btn_options"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun TrackCompactHeaderRow(
    modifier: Modifier = Modifier,
    showAlbum: Boolean = true,
    showDate: Boolean = true,
    releaseDateMode: Boolean = false
) {
    var weightedWidthPx by remember { mutableStateOf(0f) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = str("table_header_index"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(48.dp),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Spacer(Modifier.width(16.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .onGloballyPositioned { weightedWidthPx = it.size.width.toFloat() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = str("table_header_title"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(TrackCompactColumns.title),
                    maxLines = 1
                )
                CompactColumnDragHandle(divider = 0, weightedWidthPx = { weightedWidthPx }, albumVisible = showAlbum, dateVisible = showDate)
                Text(
                    text = str("table_header_artist"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(TrackCompactColumns.artist),
                    maxLines = 1
                )
                if (showAlbum || showDate) {
                    CompactColumnDragHandle(divider = 1, weightedWidthPx = { weightedWidthPx }, albumVisible = showAlbum, dateVisible = showDate)
                }
                if (showAlbum) {
                    Text(
                        text = str("table_header_album"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(TrackCompactColumns.album),
                        maxLines = 1
                    )
                    if (showDate) {
                        CompactColumnDragHandle(divider = 2, weightedWidthPx = { weightedWidthPx }, albumVisible = true, dateVisible = true)
                    }
                }
                if (showDate) {
                    Text(
                        text = str(if (releaseDateMode) "table_header_release_date" else "table_header_date_added"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(TrackCompactColumns.date),
                        maxLines = 1
                    )
                }
            }
            Box(modifier = Modifier.width(60.dp).padding(end = 16.dp), contentAlignment = Alignment.CenterEnd) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(40.dp))
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

/** Draggable column divider in the compact table header; see [ColumnDragHandle]. */
@Composable
private fun CompactColumnDragHandle(divider: Int, weightedWidthPx: () -> Float, albumVisible: Boolean, dateVisible: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val dragState = rememberDraggableState { deltaPx ->
        val w = weightedWidthPx()
        if (w > 0f) TrackCompactColumns.drag(divider, deltaPx / w * TrackCompactColumns.total(albumVisible, dateVisible), albumVisible, dateVisible)
    }
    Box(
        modifier = Modifier
            .width(COLUMN_GAP)
            .height(20.dp)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon(java.awt.Cursor(java.awt.Cursor.E_RESIZE_CURSOR)))
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                onDragStopped = { TrackCompactColumns.save() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .width(if (hovered) 2.dp else 1.dp)
                .fillMaxHeight()
                .background(
                    if (hovered) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
                )
        )
    }
}

/**
 * Spotify-style compact row: no artwork, artist in its own column, half the
 * height of [TrackTableItem]. Shares the [TrackCompactColumns] weights with
 * [TrackCompactHeaderRow] so header and rows stay aligned.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TrackCompactItem(
    track: Track,
    currentlyPlayingTrack: Track? = null,
    index: Int,
    isDownloading: Boolean,
    isDownloaded: Boolean,
    downloadProgress: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onOptionClick: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: ((Long) -> Unit)? = null,
    showAlbum: Boolean = true,
    showDate: Boolean = true,
    dragHandleModifier: Modifier = Modifier,
    isDragging: Boolean = false
) {
    val isCurrent = currentlyPlayingTrack?.id == track.id
    val titleColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val titleWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium

    val albumState by AlbumResolver.stateFor(track).collectAsState()
    LaunchedEffect(track.id, showAlbum) { if (showAlbum) AlbumResolver.requestResolve(track) }

    androidx.compose.material3.TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .padding(horizontal = 16.dp)
            .background(
                if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .onClick(
                matcher = PointerMatcher.mouse(PointerButton.Secondary),
                onClick = onOptionClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp).fillMaxWidth().height(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                when {
                    dragHandleModifier != Modifier -> Icon(
                        Icons.Rounded.DragHandle,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp).then(dragHandleModifier)
                    )
                    isDownloading -> CircularWavyProgressIndicator(
                        progress = { downloadProgress / 100f },
                        modifier = Modifier.size(18.dp)
                    )
                    isCurrent -> Icon(Icons.Rounded.GraphicEq, str("player_playing_now"), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    else -> Text(
                        text = (index + 1).toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Visible
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(TrackCompactColumns.title), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.title ?: str("untitled_track"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = titleWeight,
                        color = titleColor,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isDownloaded && !isDownloading) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.DownloadDone, str("btn_downloaded"), modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(COLUMN_GAP))
                Box(modifier = Modifier.weight(TrackCompactColumns.artist), contentAlignment = Alignment.CenterStart) {
                    val artistId = track.user?.id
                    if (onArtistClick != null && artistId != null && artistId > 0) {
                        val interaction = remember { MutableInteractionSource() }
                        val hovered by interaction.collectIsHoveredAsState()
                        Text(
                            text = track.user?.username ?: str("unknown_artist"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (hovered) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = if (hovered) TextDecoration.Underline else null,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .hoverable(interaction)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable(interactionSource = interaction, indication = null) {
                                    onArtistClick(artistId)
                                }
                        )
                    } else {
                        Text(
                            text = track.user?.username ?: str("unknown_artist"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (showAlbum) {
                    Spacer(Modifier.width(COLUMN_GAP))
                    Box(modifier = Modifier.weight(TrackCompactColumns.album).padding(end = 8.dp), contentAlignment = Alignment.CenterStart) {
                        val resolved = albumState as? AlbumResolver.AlbumUiState.Resolved
                        if (resolved != null) {
                            val albumId = resolved.info.playlistId
                            if (albumId != null) {
                                val interaction = remember { MutableInteractionSource() }
                                val hovered by interaction.collectIsHoveredAsState()
                                Text(
                                    text = resolved.info.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (hovered) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textDecoration = if (hovered) TextDecoration.Underline else null,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .hoverable(interaction)
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .clickable(interactionSource = interaction, indication = null) {
                                            onAlbumClick(albumId.toString())
                                        }
                                )
                            } else {
                                Text(
                                    text = resolved.info.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                if (showDate) {
                    val dateStr = remember(track.likedAt, track.releaseDate, track.createdAt) {
                        track.likedAt?.let {
                            java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(it))
                        } ?: formatApiDate(track.releaseDate ?: track.createdAt)
                    }
                    Spacer(Modifier.width(COLUMN_GAP))
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(TrackCompactColumns.date),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            val durationStr = remember(track.durationMs) {
                track.durationMs?.let {
                    val totalSecs = it / 1000
                    val m = totalSecs / 60
                    val s = totalSecs % 60
                    String.format("%d:%02d", m, s)
                } ?: ""
            }
            Text(
                text = durationStr,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(60.dp),
                textAlign = TextAlign.End
            )
            IconButton(onClick = onOptionClick, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.MoreVert, str("btn_options"), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun EmptyPlaylistView(
    playlistId: String,
    isUserCreated: Boolean,
    isEmptySearch: Boolean = false
) {
    val (kaomoji, title, subtitle) = when {
        isEmptySearch -> Triple(str("empty_playlist_search_kaomoji"), str("empty_playlist_search_title"), str("empty_playlist_search_subtitle"))
        playlistId == "downloads" -> Triple(str("empty_downloads_kaomoji"), str("empty_downloads_title"), str("empty_downloads_subtitle"))
        isUserCreated -> Triple(str("empty_user_playlist_kaomoji"), str("empty_user_playlist_title"), str("empty_user_playlist_subtitle"))
        else -> Triple(str("empty_playlist_generic_kaomoji"), str("empty_playlist_generic"), "")
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            text = kaomoji,
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        if (subtitle.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(48.dp))
    }
}

