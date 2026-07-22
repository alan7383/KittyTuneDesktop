    package com.alananasss.kittytune.ui.library
import com.alananasss.kittytune.core.str
    
    import com.alananasss.kittytune.core.Application
        import androidx.compose.runtime.getValue
    import androidx.compose.runtime.mutableStateListOf
    import androidx.compose.runtime.mutableStateOf
    import androidx.compose.runtime.setValue
    import androidx.compose.runtime.snapshotFlow
    import com.alananasss.kittytune.core.AndroidViewModel
    import androidx.lifecycle.viewModelScope
        import com.alananasss.kittytune.data.DownloadManager
    import com.alananasss.kittytune.data.LikeRepository
    import com.alananasss.kittytune.data.TokenManager
    import com.alananasss.kittytune.data.local.AppDatabase
    import com.alananasss.kittytune.data.local.LocalArtist
    import com.alananasss.kittytune.data.local.PlayerPreferences
    import com.alananasss.kittytune.data.network.RetrofitClient
    import com.alananasss.kittytune.domain.Playlist
    import com.alananasss.kittytune.domain.Track
    import com.alananasss.kittytune.domain.User
    import com.alananasss.kittytune.utils.NetworkUtils
    import com.alananasss.kittytune.data.SessionManager
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.async
    import kotlinx.coroutines.coroutineScope
    import kotlinx.coroutines.flow.first
    import kotlinx.coroutines.launch
    import java.text.SimpleDateFormat
    import java.util.Locale
    
    sealed class LibraryItem(open val timestamp: Long) {
        data class PlaylistItem(val playlist: Playlist, override val timestamp: Long) : LibraryItem(timestamp)
        data class ArtistItem(val artist: LocalArtist, override val timestamp: Long) : LibraryItem(timestamp)
    }
    
    class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    
        private val app = application
        private val prefs = com.alananasss.kittytune.core.NamedPrefs("library_prefs")
        private val tokenManager = TokenManager
    
        var userProfile by mutableStateOf<User?>(null)
        val likedTracks = mutableStateListOf<Track>()
    
        private var onlineItemsCache = listOf<LibraryItem>()
        private var localItemsCache = listOf<LibraryItem>()
        private var savedArtistsCache = listOf<LibraryItem>()
    
        private val _allItems = mutableStateListOf<LibraryItem>()
    
        val displayedItems: List<LibraryItem>
            get() {
                val playlistsLabel = str("lib_playlists")
                val albumsLabel = str("lib_albums")
                val artistsLabel = str("lib_artists")
                val stationsLabel = str("lib_stations")
    
                val items = _allItems.filter { item ->
                    when (item) {
                        is LibraryItem.PlaylistItem -> {
                            val matchesSearch = if (searchQuery.isBlank()) true else {
                                item.playlist.title?.contains(searchQuery, ignoreCase = true) == true ||
                                        item.playlist.user?.username?.contains(searchQuery, ignoreCase = true) == true
                            }
                            val isAlbum = item.playlist.isAlbum
                            val isStation = item.playlist.permalinkUrl?.let {
                                it.contains("artist-stations") || it.contains("track-stations")
                            } == true
                            val matchesType = when (selectedFilter) {
                                playlistsLabel -> !isAlbum && !isStation
                                albumsLabel -> isAlbum && !isStation
                                stationsLabel -> isStation
                                null -> !isStation
                                else -> false // if artist/station filter is selected, standard playlists don't match
                            }
                            val matchesOwnership = if (matchesType && !isStation) {
                                when (ownershipFilter) {
                                    OwnershipFilter.ALL -> true
                                    OwnershipFilter.CREATED -> item.playlist.user?.id == userProfile?.id || item.playlist.user?.id == 0L || item.playlist.id < 0L
                                    OwnershipFilter.LIKED -> LikeRepository.isPlaylistLiked(item.playlist.id)
                                }
                            } else true
                            matchesSearch && matchesType && matchesOwnership
                        }
                        is LibraryItem.ArtistItem -> {
                            val matchesSearch = if (searchQuery.isBlank()) true else {
                                item.artist.username.contains(searchQuery, ignoreCase = true)
                            }
                            val matchesType = selectedFilter == artistsLabel || (selectedFilter == null && searchQuery.isNotBlank())
                            matchesSearch && matchesType
                        }
                    }
                }
                return if (isSortDescending) items.sortedByDescending { it.timestamp } else items.sortedBy { it.timestamp }
            }
    
        // --- state variables ---
        var isLoading by mutableStateOf(true)
        var isOfflineMode by mutableStateOf(false)
        var searchQuery by mutableStateOf("")
        var isGuestUser by mutableStateOf(false)
        var showLocalMedia by mutableStateOf(false)
        val isSyncing = LikeRepository.isSyncing
        var selectedFilter by mutableStateOf<String?>(null)
        var ownershipFilter by mutableStateOf(OwnershipFilter.ALL)
        var isGridLayout by mutableStateOf(prefs.getBoolean("is_grid_layout", true))
        var isSortDescending by mutableStateOf(true)

        // --- sidebar layout state (desktop) ---
        var viewMode by mutableStateOf(
            runCatching { LibraryViewMode.valueOf(prefs.getString("lib_view_mode", null) ?: "") }
                .getOrDefault(LibraryViewMode.LIST)
        )
        var sidebarWidth by mutableStateOf(
            prefs.getInt("sidebar_width", SIDEBAR_DEFAULT_WIDTH.toInt()).toFloat()
                .coerceIn(SIDEBAR_MIN_WIDTH, SIDEBAR_MAX_WIDTH)
        )
        var isSidebarCollapsed by mutableStateOf(prefs.getBoolean("sidebar_collapsed", false))
        var isLibraryFullScreen by mutableStateOf(false)
        var isCreatingPlaylist by mutableStateOf(false)

        private var sidebarDragRaw = 0f

        fun sidebarDragStart() {
            sidebarDragRaw = if (isSidebarCollapsed) SIDEBAR_COLLAPSED_WIDTH else sidebarWidth
        }

        fun sidebarDragBy(deltaDp: Float) {
            sidebarDragRaw = (sidebarDragRaw + deltaDp).coerceIn(0f, SIDEBAR_MAX_WIDTH)
            if (sidebarDragRaw < SIDEBAR_SNAP_THRESHOLD) {
                isSidebarCollapsed = true
            } else {
                isSidebarCollapsed = false
                sidebarWidth = sidebarDragRaw.coerceIn(SIDEBAR_MIN_WIDTH, SIDEBAR_MAX_WIDTH)
            }
        }

        fun sidebarDragEnd() {
            prefs.putInt("sidebar_width", sidebarWidth.toInt())
            prefs.putBoolean("sidebar_collapsed", isSidebarCollapsed)
        }

        fun toggleSidebarCollapsed() {
            isSidebarCollapsed = !isSidebarCollapsed
            prefs.putBoolean("sidebar_collapsed", isSidebarCollapsed)
        }

        fun createPlaylist(title: String, isPublic: Boolean, onCreated: (Long) -> Unit = {}) {
            val name = title.trim()
            if (name.isEmpty() || isCreatingPlaylist) return
            viewModelScope.launch {
                isCreatingPlaylist = true
                try {
                    val id = DownloadManager.createUserPlaylist(name, isPublic)
                    com.alananasss.kittytune.core.Toaster.show(str("lib_playlist_created"))
                    onCreated(id)
                } catch (e: Exception) {
                    e.printStackTrace()
                    com.alananasss.kittytune.core.Toaster.show(str("lib_playlist_create_failed"))
                } finally {
                    isCreatingPlaylist = false
                }
            }
        }
    
        private var isHydratingLikes = false
        private val isoParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    
        private val api = RetrofitClient.create()
        private val db = AppDatabase.downloadDao
    
        init {
            viewModelScope.launch {
                snapshotFlow { isGridLayout }.collect { isGrid ->
                    prefs.putBoolean("is_grid_layout", isGrid)
                }
            }

            viewModelScope.launch {
                snapshotFlow { viewMode }.collect { mode ->
                    prefs.putString("lib_view_mode", mode.name)
                }
            }
    
            viewModelScope.launch {
                LikeRepository.likedTracks.collect { tracksFromRepo ->
                    likedTracks.clear()
                    likedTracks.addAll(tracksFromRepo)
                }
            }

            viewModelScope.launch {
                LikeRepository.likedPlaylists.collect {
                    rebuildAllItems()
                }
            }
    
            viewModelScope.launch {
                SessionManager.isClientIdValid.collect { isReady ->
                    if (isReady) {
                        loadData()
                    }
                }
            }

            viewModelScope.launch {
                DownloadManager.libraryUpdated.collect {
                    if (SessionManager.isClientIdValid.value) {
                        loadData(forceRefresh = true)
                    }
                }
            }

            viewModelScope.launch {
                DownloadManager.deletedPlaylistIds.collect {
                    rebuildAllItems()
                }
            }
    
            viewModelScope.launch {
                db.getAllPlaylists().collect { localPlaylists ->
                    localItemsCache = localPlaylists.map { local ->
                        val finalArtwork = if (!local.localCoverPath.isNullOrEmpty()) local.localCoverPath else local.artworkUrl
                        val p = Playlist(
                            id = local.id,
                            title = local.title,
                            artworkUrl = finalArtwork,
                            calculatedArtworkUrl = null,
                            trackCount = local.trackCount,
                            user = User(0, local.artist, null),
                            tracks = null,
                            isAlbum = local.isAlbum,
                            permalinkUrl = local.permalinkUrl
                        )
    
                        LibraryItem.PlaylistItem(p, local.addedAt)
                    }
                    rebuildAllItems()
                }
            }
    
            viewModelScope.launch {
                DownloadManager.getSavedArtists().collect { artists ->
                    savedArtistsCache = artists.map { LibraryItem.ArtistItem(it, it.savedAt) }
                    rebuildAllItems()
                }
            }
        }
    
        private fun rebuildAllItems() {
            val deletedIds = DownloadManager.deletedPlaylistIds.value
            val localIds = localItemsCache
                .filterIsInstance<LibraryItem.PlaylistItem>()
                .map { it.playlist.id }
                .toSet()
    
            val filteredOnlineItems = onlineItemsCache.filter { item ->
                if (item is LibraryItem.PlaylistItem) !localIds.contains(item.playlist.id) && !deletedIds.contains(item.playlist.id) else true
            }
            
            val filteredLocalItems = localItemsCache.filter { item ->
                if (item is LibraryItem.PlaylistItem) !deletedIds.contains(item.playlist.id) else true
            }
    
            _allItems.clear()
            _allItems.addAll(filteredOnlineItems)
            _allItems.addAll(filteredLocalItems)
            _allItems.addAll(savedArtistsCache)
        }
    
        fun loadData(forceRefresh: Boolean = false) {
            if (isLoading && !forceRefresh && userProfile != null) return
    
            val playerPrefs = PlayerPreferences()
            showLocalMedia = playerPrefs.getLocalMediaEnabled()
    
            isGuestUser = tokenManager.isGuestMode()
            val token = tokenManager.getAccessToken()
            if (!isGuestUser && token.isNullOrEmpty()) {
                isLoading = false
                return
            }
    
            if (isGuestUser) {
                userProfile = User(0, str("guest_user"), null)
                isOfflineMode = false
                isLoading = false
                return
            }
    
            viewModelScope.launch {
                isLoading = true
                if (NetworkUtils.isInternetAvailable()) {
                    try {
                        isOfflineMode = false
                        val user = userProfile ?: api.getMe()
                        userProfile = user
                        loadOnlineData(user)
                    } catch (e: Exception) {
                        println("online error or not connected: ${e.message}")
                        isLoading = false
                    }
                } else {
                    isOfflineMode = true
                    isLoading = false
                }
            }
        }
    
        private fun loadOnlineData(user: User) {
        viewModelScope.launch {
            DownloadManager.refreshFollowings()
            try {
                coroutineScope {
                    val likedPlaylistsDeferred = async { api.getUserPlaylistLikes(user.id) }
                    val createdPlaylistsDeferred = async { api.getUserCreatedPlaylists(user.id) }
                    val repostedPlaylistsDeferred = async { api.getMyPlaylistPosts() }
                    val libraryAllDeferred = async { try { api.getMyLibraryAll(limit = 200) } catch (e: Exception) { null } }
    
                        val likedResponse = likedPlaylistsDeferred.await()
                        val createdResponse = createdPlaylistsDeferred.await()
                        val repostedResponse = repostedPlaylistsDeferred.await()
                        val libraryAllResponse = libraryAllDeferred.await()
    
                        val newOnlineItems = mutableListOf<LibraryItem>()
                        val addedPlaylistIds = mutableSetOf<Long>()
                        val trulyLikedIds = mutableSetOf<Long>()
    
                                                likedResponse.collection.forEach { item ->
                            val pl = item.playlist
                            val sp = item.systemPlaylist
                            
                            if (pl != null && addedPlaylistIds.add(pl.id)) {
                                trulyLikedIds.add(pl.id)
                                val date = try { item.likedAt?.let { isoParser.parse(it)?.time } ?: 0L } catch (e: Exception) { 0L }
                                newOnlineItems.add(LibraryItem.PlaylistItem(pl, date))
                            } else if (sp != null) {
                                val numId = sp.urn?.hashCode()?.toLong() ?: sp.numericId
                                if (numId != 0L && addedPlaylistIds.add(numId)) {
                                    trulyLikedIds.add(numId)
                                    val stationPermalink = sp.permalinkUrl ?: if (sp.isArtistStation) "https://soundcloud.com/discover/sets/artist-stations:${sp.numericId}" else "https://soundcloud.com/discover/sets/track-stations:${sp.numericId}"
                                    val fakePlaylist = Playlist(
                                        id = numId,
                                        title = sp.title,
                                        artworkUrl = sp.artworkUrl,
                                        calculatedArtworkUrl = sp.calculatedArtworkUrl,
                                        trackCount = sp.tracks?.size,
                                        user = sp.user,
                                        tracks = null,
                                        permalinkUrl = stationPermalink,
                                        urn = sp.urn
                                    )
                                    val date = try { item.likedAt?.let { isoParser.parse(it)?.time } ?: 0L } catch (e: Exception) { 0L }
                                    newOnlineItems.add(LibraryItem.PlaylistItem(fakePlaylist, date))
                                }
                            }
                        }
    
                        createdResponse.collection.forEach { playlist ->
                            if (addedPlaylistIds.add(playlist.id)) {
                                val date = try {
                                    val dateStr = playlist.lastModified ?: playlist.createdAt
                                    dateStr?.let { isoParser.parse(it)?.time } ?: 0L
                                } catch (e: Exception) { 0L }
                                newOnlineItems.add(LibraryItem.PlaylistItem(playlist, date))
                            }
                        }
    
                        repostedResponse.collection.forEach { item ->
                            val playlist = item.playlist ?: return@forEach
                            if (addedPlaylistIds.add(playlist.id)) {
                                val date = try {
                                    val dateStr = playlist.lastModified ?: playlist.createdAt
                                    dateStr?.let { isoParser.parse(it)?.time } ?: 0L
                                } catch (e: Exception) { 0L }
                                newOnlineItems.add(LibraryItem.PlaylistItem(playlist, date))
                            }
                        }

                        // Add liked system playlists and stations from library/all
                        libraryAllResponse?.collection?.forEach { item ->
                            val sp = item.systemPlaylist ?: return@forEach
                            val numId = sp.urn?.hashCode()?.toLong() ?: sp.numericId
                            if (numId != 0L && addedPlaylistIds.add(numId)) {
                                trulyLikedIds.add(numId)
                                val stationPermalink = sp.permalinkUrl ?: if (sp.isArtistStation) "https://soundcloud.com/discover/sets/artist-stations:${sp.numericId}" else "https://soundcloud.com/discover/sets/track-stations:${sp.numericId}"
                                val fakePlaylist = Playlist(
                                    id = numId,
                                    title = sp.title,
                                    artworkUrl = sp.artworkUrl,
                                    calculatedArtworkUrl = sp.calculatedArtworkUrl,
                                    trackCount = sp.tracks?.size,
                                    user = sp.user,
                                    tracks = null,
                                    permalinkUrl = stationPermalink,
                                    urn = sp.urn
                                )
                                val date = try { item.createdAt?.let { isoParser.parse(it)?.time } ?: 0L } catch (e: Exception) { 0L }
                                newOnlineItems.add(LibraryItem.PlaylistItem(fakePlaylist, date))
                            }
                        }
    
                        LikeRepository.setLikedPlaylists(trulyLikedIds)
                        
                        onlineItemsCache = newOnlineItems
                        rebuildAllItems()
    
                        if (!isHydratingLikes) {
                            isHydratingLikes = true
                            LikeRepository.setSyncing(true)
    
                            launch(Dispatchers.IO) {
                                try {
                                    val allCollectedLikes = mutableListOf<Track>()
                                    var nextUrl: String? = null
    
                                    val firstPage = api.getUserTrackLikes(user.id, limit = 200)
    
                                    allCollectedLikes.addAll(firstPage.collection.map { item ->
                                        val time = try { item.createdAt?.let { isoParser.parse(it)?.time } } catch (e: Exception) { 0L }
                                        item.track.copy(likedAt = time)
                                    })
    
                                    nextUrl = firstPage.next_href
    
                                    while (nextUrl != null) {
                                        val page = api.getTrackLikesNextPage(nextUrl!!)
    
                                        allCollectedLikes.addAll(page.collection.map { item ->
                                            val time = try { item.createdAt?.let { isoParser.parse(it)?.time } } catch (e: Exception) { 0L }
                                            item.track.copy(likedAt = time)
                                        })
    
                                        nextUrl = page.next_href
                                    }
    
                                    LikeRepository.replaceAllLikes(allCollectedLikes)
    
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                } finally {
                                    isHydratingLikes = false
                                    isLoading = false
                                    LikeRepository.setSyncing(false)
                                }
                            }
                        } else {
                            isLoading = false
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    isLoading = false
                }
            }
        }
    }




enum class OwnershipFilter { ALL, CREATED, LIKED }

/** The four sidebar display styles (compact list / list / cover grid / grid with titles). */
enum class LibraryViewMode { COMPACT_LIST, LIST, COMPACT_GRID, GRID }

// Sidebar sizing (dp) â€” shared between LibraryViewModel and MainScreen/Sidebar.
const val SIDEBAR_MIN_WIDTH = 264f
const val SIDEBAR_MAX_WIDTH = 480f
const val SIDEBAR_DEFAULT_WIDTH = 300f
const val SIDEBAR_COLLAPSED_WIDTH = 80f
const val SIDEBAR_SNAP_THRESHOLD = 176f


