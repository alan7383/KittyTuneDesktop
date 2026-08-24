    package com.alananasss.kittytune.ui.profile
    
    import com.alananasss.kittytune.core.Application
    import com.alananasss.kittytune.core.str
    import java.awt.image.BufferedImage
    import java.util.Base64
    import javax.imageio.ImageIO
    import com.alananasss.kittytune.core.Toaster
        import androidx.compose.runtime.getValue
    import androidx.compose.runtime.mutableStateListOf
    import androidx.compose.runtime.mutableStateOf
    import androidx.compose.runtime.setValue
    import com.alananasss.kittytune.core.AndroidViewModel
    import androidx.lifecycle.viewModelScope
        import com.alananasss.kittytune.data.network.RetrofitClient
    import com.alananasss.kittytune.domain.*
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.async
    import kotlinx.coroutines.coroutineScope
    import kotlinx.coroutines.flow.MutableSharedFlow
    import kotlinx.coroutines.flow.SharedFlow
    import kotlinx.coroutines.flow.asSharedFlow
    import kotlinx.coroutines.launch
    import kotlinx.coroutines.withContext
    import java.io.ByteArrayOutputStream
    
    // Tab enum
    enum class ProfileTab {
        POPULAR,
        TRACKS,
        ALBUMS,
        PLAYLISTS,
        LIKES,
        REPOSTS
    }
    
    class ProfileViewModel(application: Application) : AndroidViewModel(application) {
        companion object {
            private val _refreshTrigger = MutableSharedFlow<Long>(extraBufferCapacity = 1)
            val refreshTrigger: SharedFlow<Long> = _refreshTrigger.asSharedFlow()

            fun triggerRefresh(userId: Long = 0L) {
                _refreshTrigger.tryEmit(userId)
            }
        }

        private val api = RetrofitClient.create()
    
        var user by mutableStateOf<User?>(null)
        var isCurrentUser by mutableStateOf(false)
        var isSpotifyProfile by mutableStateOf(false)
        var spotifyArtist by mutableStateOf<com.alananasss.kittytune.data.spotify.SpotifyArtist?>(null)
        var isLoading by mutableStateOf(true)
        var selectedTab by mutableStateOf(ProfileTab.POPULAR)
    
        // Content lists
        val popularTracks = mutableStateListOf<Track>()
        val allTracks = mutableStateListOf<Track>()
        val repostedTracks = mutableStateListOf<Track>()
        val popularReleases = mutableStateListOf<Playlist>()
        val albums = mutableStateListOf<Playlist>()
        val singles = mutableStateListOf<Playlist>()
        val compilations = mutableStateListOf<Playlist>()
        val appearsOn = mutableStateListOf<Playlist>()
        val discoveredOn = mutableStateListOf<Playlist>()
        val playlists = mutableStateListOf<Playlist>()
        val likedTracks = mutableStateListOf<Track>()
        val similarArtists = mutableStateListOf<User>()
        val userComments = mutableStateListOf<Comment>()
        private var commentsNextUrl: String? = null
        var isCommentsLoadingMore by mutableStateOf(false)
    
        var artistStationId: Long? = null

        // Full-discography pagination (Spotify catalog artists only).
        private var discographyOffset = 0
        private var discographyTotalCount = 0
        private var discographyExhausted = false
        var isDiscographyLoadingMore by mutableStateOf(false)
            private set

        /** True when more releases can still be paged in via queryArtistDiscographyAll. */
        fun hasMoreDiscography(): Boolean =
            isSpotifyProfile && !discographyExhausted &&
                (discographyTotalCount == 0 || discographyOffset < discographyTotalCount)

        /**
         * Appends the next page of the artist's complete discography, routing
         * each release to its section (albums / singles / compilations) by type
         * and skipping entries already surfaced by the overview.
         */
        fun loadMoreDiscography() {
            val artistId = spotifyArtist?.id ?: return
            if (isDiscographyLoadingMore || !hasMoreDiscography()) return

            viewModelScope.launch {
                isDiscographyLoadingMore = true
                try {
                    val page = com.alananasss.kittytune.data.spotify.SpotifyRepository
                        .getArtistDiscographyPage(artistId, offset = discographyOffset, limit = 50)
                    if (page == null) {
                        discographyExhausted = true
                        return@launch
                    }
                    val (releases, total) = page
                    if (total > 0) discographyTotalCount = total

                    if (releases.isEmpty()) {
                        discographyExhausted = true
                        return@launch
                    }

                    for (release in releases) {
                        val playlist = release.toPlaylist()
                        when (release.releaseType?.uppercase()) {
                            "ALBUM" -> if (albums.none { it.permalink == playlist.permalink }) albums.add(playlist)
                            "SINGLE", "EP" -> if (singles.none { it.permalink == playlist.permalink }) singles.add(playlist)
                            "COMPILATION" -> if (compilations.none { it.permalink == playlist.permalink }) compilations.add(playlist)
                            else -> {
                                // Unknown type: route by track count heuristic.
                                when {
                                    release.totalTracks <= 3 -> if (singles.none { it.permalink == playlist.permalink }) singles.add(playlist)
                                    else -> if (albums.none { it.permalink == playlist.permalink }) albums.add(playlist)
                                }
                            }
                        }
                    }

                    discographyOffset += releases.size
                    if (discographyTotalCount in 1..discographyOffset) discographyExhausted = true
                } catch (e: Exception) {
                    println("[ProfileViewModel] Failed to load discography page: ${e.message}")
                } finally {
                    isDiscographyLoadingMore = false
                }
            }
        }

    init {
        viewModelScope.launch {
            _refreshTrigger.collect { targetUserId ->
                val currentId = user?.id
                if (currentId != null && (targetUserId == 0L || targetUserId == currentId)) {
                    loadProfile(currentId, forceReload = true)
                }
            }
        }

        viewModelScope.launch {
            com.alananasss.kittytune.data.RepostRepository.repostedTrackIds.collect { repostedIds ->
                if (isCurrentUser) {
                    repostedTracks.removeAll { track -> !repostedIds.contains(track.id) }
                }
            }
        }

        viewModelScope.launch {
            com.alananasss.kittytune.data.MusicManager.trackUpdatedFlow.collect { updatedTrack ->
                val popIdx = popularTracks.indexOfFirst { it.id == updatedTrack.id }
                if (popIdx != -1) popularTracks[popIdx] = updatedTrack

                val allIdx = allTracks.indexOfFirst { it.id == updatedTrack.id }
                if (allIdx != -1) allTracks[allIdx] = updatedTrack

                val repIdx = repostedTracks.indexOfFirst { it.id == updatedTrack.id }
                if (repIdx != -1) repostedTracks[repIdx] = updatedTrack

                val likeIdx = likedTracks.indexOfFirst { it.id == updatedTrack.id }
                if (likeIdx != -1) likedTracks[likeIdx] = updatedTrack
            }
        }

        viewModelScope.launch {
            com.alananasss.kittytune.data.MusicManager.trackDeletedFlow.collect { deletedTrackId ->
                popularTracks.removeAll { it.id == deletedTrackId }
                allTracks.removeAll { it.id == deletedTrackId }
                repostedTracks.removeAll { it.id == deletedTrackId }
                likedTracks.removeAll { it.id == deletedTrackId }
            }
        }
    }
    
        // Helper to get strings from resources
        private fun getString(resId: String): String = str(resId)
        private fun getString(resId: String, vararg formatArgs: Any): String = str(resId, *formatArgs)
    
    
        // Helper to paginate through all user tracks
        private suspend fun fetchAllUserTracks(userId: Long): List<Track> {
            val allUserTracks = mutableListOf<Track>()
            try {
                val firstPage = api.getUserTracks(userId, limit = 200)
                allUserTracks.addAll(firstPage.collection.filterNotNull())
                var nextUrl = firstPage.next_href
                var pageCount = 0
                // Safety limit to avoid infinite loops
                while (nextUrl != null && pageCount < 20) {
                    val nextPage = api.getUserTracksNextPage(nextUrl)
                    allUserTracks.addAll(nextPage.collection.filterNotNull())
                    nextUrl = nextPage.next_href
                    pageCount++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return allUserTracks
        }

        fun loadProfile(userIdStr: String, forceRefresh: Boolean = false) {
            val cleanId = com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(userIdStr)
            if (userIdStr.startsWith("spotify:") || userIdStr.startsWith("spotify_artist:") || userIdStr.startsWith("spotify")) {
                loadSpotifyArtist(cleanId, forceRefresh)
                return
            }
            if (cleanId.length == 22 && cleanId.all { it.isLetterOrDigit() }) {
                loadSpotifyArtist(cleanId, forceRefresh)
                return
            }
            val id = userIdStr.toLongOrNull()
            if (id != null && id > 0L) {
                val prefs = com.alananasss.kittytune.data.local.PlayerPreferences()
                val mappedSpotifyId = prefs.getSpotifyArtistIdForStableId(id)
                if (!mappedSpotifyId.isNullOrBlank()) {
                    loadSpotifyArtist(mappedSpotifyId, forceRefresh)
                    return
                }
                if (id > 1000000000000L) {
                    viewModelScope.launch {
                        isLoading = true
                        val dao = com.alananasss.kittytune.data.local.AppDatabase.downloadDao
                        val histItem = dao.getHistoryItemById(id, "profile:$id")
                        val artistName = histItem?.title
                        if (!artistName.isNullOrBlank()) {
                            val search = com.alananasss.kittytune.data.spotify.SpotifyRepository.search(artistName)
                            val match = search.artists.firstOrNull { it.name.equals(artistName, ignoreCase = true) }
                                ?: search.artists.firstOrNull { it.name.contains(artistName, ignoreCase = true) || artistName.contains(it.name, ignoreCase = true) }
                            if (match != null) {
                                prefs.saveSpotifyArtistMapping(id, match.id)
                                loadSpotifyArtist(match.id, forceRefresh)
                                return@launch
                            }
                        }
                        loadProfile(id, forceRefresh)
                    }
                    return
                }
                loadProfile(id, forceRefresh)
            } else if (userIdStr.isNotBlank() && userIdStr != "0") {
                resolveAndLoadProfile(userIdStr, forceRefresh)
            }
        }

        private fun resolveAndLoadProfile(query: String, forceRefresh: Boolean = false) {
            viewModelScope.launch {
                isLoading = true
                user = null
                try {
                    val spotifyResults = com.alananasss.kittytune.data.spotify.SpotifyRepository.search(query)
                    val spotifyArtist = spotifyResults.artists.firstOrNull {
                        it.name.equals(query, ignoreCase = true)
                    } ?: spotifyResults.artists.firstOrNull {
                        it.name.contains(query, ignoreCase = true) || query.contains(it.name, ignoreCase = true)
                    }

                    if (spotifyArtist != null) {
                        loadSpotifyArtist(spotifyArtist.id, forceRefresh)
                        return@launch
                    }

                    val resolved = try {
                        val soundCloudUrl = if (query.startsWith("http")) query else "https://soundcloud.com/$query"
                        api.resolveUrl(soundCloudUrl)
                    } catch (_: Exception) { null }
                    if (resolved != null && resolved.isJsonObject) {
                        val id = resolved.get("id")?.asLong
                        if (id != null && id > 0L) {
                            loadProfile(id, forceRefresh)
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    println("[ProfileViewModel] Failed to resolve profile: $query ${e.message}")
                } finally {
                    isLoading = false
                }
            }
        }

        fun loadSpotifyArtist(artistId: String, forceRefresh: Boolean = false) {
            val cleanId = com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(artistId)
            if (cleanId.isBlank()) return
            if (spotifyArtist?.id == cleanId && user != null && !forceRefresh) return

            viewModelScope.launch {
                isSpotifyProfile = true
                isCurrentUser = false
                isLoading = true
                user = null
                popularTracks.clear()
                allTracks.clear()
                repostedTracks.clear()
                popularReleases.clear()
                albums.clear()
                singles.clear()
                compilations.clear()
                appearsOn.clear()
                discoveredOn.clear()
                playlists.clear()
                likedTracks.clear()
                similarArtists.clear()
                userComments.clear()
                commentsNextUrl = null

                try {
                    var artist = if (cleanId.length == 22 && cleanId.all { it.isLetterOrDigit() }) {
                        com.alananasss.kittytune.data.spotify.SpotifyRepository.getArtist(cleanId)
                    } else null

                    if (artist == null) {
                        val searchRes = com.alananasss.kittytune.data.spotify.SpotifyRepository.search(cleanId)
                        val match = searchRes.artists.firstOrNull { it.name.trim().equals(cleanId.trim(), ignoreCase = true) }
                            ?: searchRes.artists.firstOrNull { it.name.trim().startsWith(cleanId.trim(), ignoreCase = true) || cleanId.trim().startsWith(it.name.trim(), ignoreCase = true) }
                            ?: searchRes.artists.firstOrNull { it.name.contains(cleanId, ignoreCase = true) || cleanId.contains(it.name, ignoreCase = true) }
                        if (match != null) {
                            artist = com.alananasss.kittytune.data.spotify.SpotifyRepository.getArtist(match.id)
                        }
                    }
                    if (artist != null) {
                        spotifyArtist = artist
                        val u = artist.toUser()
                        user = u.copy(
                            description = artist.biography
                        )
                        popularTracks.addAll(artist.topTracks.map { it.toTrack() })
                        val popRels = artist.popularReleases.map { it.toPlaylist() }
                        val albRels = artist.albums.map { it.toPlaylist() }
                        val sngRels = artist.singles.map { it.toPlaylist() }
                        val compRels = artist.compilations.map { it.toPlaylist() }

                        albums.addAll(albRels)
                        singles.addAll(sngRels)
                        compilations.addAll(compRels)

                        if (popRels.isNotEmpty()) {
                            popularReleases.addAll(popRels)
                        } else {
                            popularReleases.addAll((albRels.take(5) + sngRels.take(5)))
                        }

                        appearsOn.addAll(artist.appearsOn.map { it.toPlaylist() })
                        discoveredOn.addAll(artist.discoveredOn.map { it.toPlaylist() })
                        similarArtists.addAll(
                            artist.relatedArtists.map { rel ->
                                User(
                                    id = kotlin.math.abs(rel.id.hashCode().toLong()),
                                    username = rel.name,
                                    avatarUrl = rel.avatarUrl,
                                    verified = rel.verified,
                                    urn = "spotify:artist:${rel.id}",
                                    permalink = rel.id
                                )
                            }
                        )

                        // Reset full-discography pagination state (queryArtistDiscographyAll).
                        discographyOffset = 0
                        discographyTotalCount = 0
                        discographyExhausted = false
                    }
                } catch (e: Exception) {
                    println("[ProfileViewModel] Failed to load Spotify artist: ${e.message}")
                } finally {
                    isLoading = false
                }
            }
        }

        fun loadProfile(userId: Long, forceReload: Boolean = false) {
            if (!forceReload && !isSpotifyProfile && user?.id == userId && (allTracks.isNotEmpty() || likedTracks.isNotEmpty() || popularTracks.isNotEmpty() || userComments.isNotEmpty())) {
                isLoading = false
                return
            }
            viewModelScope.launch {
                isSpotifyProfile = false
                spotifyArtist = null
                if (user?.id != userId) {
                    isLoading = true
                }
                isCurrentUser = false
                try {
                    // Check if current user
                    try {
                        val me = api.getMe()
                        if (me.id == userId) {
                            isCurrentUser = true
                        }
                    } catch (e: Exception) { /* ignore */ }

                    // Fetch the profile; on failure fall back to Spotify artist mapping /
                    // history-based search before giving up.
                    val freshUser = fetchUser(userId)
                    if (freshUser != null) {
                        user = freshUser
                    } else {
                        val prefs = com.alananasss.kittytune.data.local.PlayerPreferences()
                        val mappedSpotifyId = prefs.getSpotifyArtistIdForStableId(userId)
                        if (!mappedSpotifyId.isNullOrBlank()) {
                            loadSpotifyArtist(mappedSpotifyId, forceReload)
                            return@launch
                        }

                        val dao = com.alananasss.kittytune.data.local.AppDatabase.downloadDao
                        val histItem = dao.getHistoryItemById(userId, "profile:$userId")
                        val artistName = histItem?.title
                        if (!artistName.isNullOrBlank()) {
                            val search = com.alananasss.kittytune.data.spotify.SpotifyRepository.search(artistName)
                            val match = search.artists.firstOrNull { it.name.equals(artistName, ignoreCase = true) }
                                ?: search.artists.firstOrNull { it.name.contains(artistName, ignoreCase = true) || artistName.contains(it.name, ignoreCase = true) }
                            if (match != null) {
                                prefs.saveSpotifyArtistMapping(userId, match.id)
                                loadSpotifyArtist(match.id, forceReload)
                                return@launch
                            }
                        }
                        user = null
                        return@launch
                    }
    
                    // We rely on DownloadManager.refreshFollowings() in the background
                    // No need to fetch checkFollowState manually on each profile load.
    
                    coroutineScope {
                        // Parallel fetching
                        val popDef = async { try { api.getUserTopTracks(userId).collection.filterNotNull() } catch (_: Exception) { emptyList() } }
                        val tracksDef = async { fetchAllUserTracks(userId) }
                        val repostsDef = async {
                            try {
                                api.getUserReposts(userId, limit = 50).collection
                                    .filter { it.type == "track-repost" && it.track != null }
                                    .mapNotNull { item ->
                                        val millis = item.createdAt?.let { raw ->
                                            runCatching { java.time.Instant.parse(raw).toEpochMilli() }.getOrNull()
                                                ?: runCatching { java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss Z", java.util.Locale.US).parse(raw)?.time }.getOrNull()
                                        }
                                        item.track?.copy(likedAt = millis ?: item.track.likedAt, createdAt = item.createdAt ?: item.track.createdAt)
                                    }
                            } catch (_: Exception) { emptyList() }
                        }
    
                        val commentsResponseDef = async {
                            try {
                                api.getUserComments(userId, limit = 20)
                            } catch (_: Exception) {
                                null
                            }
                        }
    
                        // Retrieve collections for separation
                        val albumsDef = async { try { api.getUserAlbums(userId).collection.filterNotNull() } catch (_: Exception) { emptyList() } }
                        val playDef = async { try { api.getUserCreatedPlaylists(userId).collection.filterNotNull() } catch (_: Exception) { emptyList() } }
    
                        val likesDef = async {
                            val allLikes = mutableListOf<Track>()
                            try {
                                var nextUrl: String? = null
                                val firstPage = api.getUserTrackLikes(userId, limit = 50)
                                allLikes.addAll(firstPage.collection.map { item ->
                                    val millis = item.createdAt?.let { raw ->
                                        runCatching { java.time.Instant.parse(raw).toEpochMilli() }.getOrNull()
                                            ?: runCatching { java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss Z", java.util.Locale.US).parse(raw)?.time }.getOrNull()
                                    }
                                    item.track.copy(likedAt = millis ?: item.track.likedAt, createdAt = item.createdAt ?: item.track.createdAt)
                                })
                                nextUrl = firstPage.next_href
                                var safetyCount = 0
                                while (nextUrl != null && safetyCount < 10) {
                                    val page = api.getTrackLikesNextPage(nextUrl!!)
                                    allLikes.addAll(page.collection.map { item ->
                                        val millis = item.createdAt?.let { raw ->
                                            runCatching { java.time.Instant.parse(raw).toEpochMilli() }.getOrNull()
                                                ?: runCatching { java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss Z", java.util.Locale.US).parse(raw)?.time }.getOrNull()
                                        }
                                        item.track.copy(likedAt = millis ?: item.track.likedAt, createdAt = item.createdAt ?: item.track.createdAt)
                                    })
                                    nextUrl = page.next_href
                                    safetyCount++
                                }
                            } catch (_: Exception) { }
                            allLikes
                        }
                        val simDef = async {
                            var artists = emptyList<User>()
                            try {
                                val station = try { api.getArtistStation(userId) } catch (e: Exception) { null }
                                if (station != null) artistStationId = station.id
                                // Find related artists via tracks
                                val related = api.getRelatedTracks(station?.tracks?.firstOrNull()?.id ?: 0, limit = 20)
                                artists = related.collection.mapNotNull { it.user }.filter { it.id != userId }.distinctBy { it.id }.shuffled().take(10)
                            } catch (_: Exception) { }
                            artists
                        }
    
                        popularTracks.clear(); popularTracks.addAll(popDef.await())
                        allTracks.clear(); allTracks.addAll(tracksDef.await())
                        val fetchedReposts = repostsDef.await()
                        repostedTracks.clear(); repostedTracks.addAll(fetchedReposts)
                        fetchedReposts.forEach { track ->
                            com.alananasss.kittytune.data.RepostRepository.syncLocalState(track.id, true)
                        }
    
                        // STRICT SEPARATION LOGIC
                        val fetchedAlbums = albumsDef.await()
                        val fetchedPlaylists = playDef.await()

                        // Albums list: Only items where isAlbum is true
                        albums.clear()
                        albums.addAll(fetchedAlbums.filter { it.isRealAlbum })

                        // Playlists list: Exclude anything that is an album
                        playlists.clear()
                        playlists.addAll(fetchedPlaylists.filter { !it.isRealAlbum })

                        // Drop any Spotify catalog leftovers from a previously viewed artist
                        popularReleases.clear()
                        singles.clear()
                        compilations.clear()
                        appearsOn.clear()
                        discoveredOn.clear()
    
                        likedTracks.clear(); likedTracks.addAll(likesDef.await())
                        similarArtists.clear(); similarArtists.addAll(simDef.await())
                        userComments.clear()
                        val commentsRes = commentsResponseDef.await()
                        if (commentsRes != null) {
                            val validComments = commentsRes.collection.filter { it.track != null }
                            userComments.addAll(validComments)
                            commentsNextUrl = commentsRes.next_href
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isLoading = false
                }
            }
        }
    
        fun loadMoreUserComments() {
            if (isCommentsLoadingMore || commentsNextUrl == null) return
    
            viewModelScope.launch {
                isCommentsLoadingMore = true
                try {
                    val response = api.getUserCommentsNextPage(commentsNextUrl!!)
                    val validComments = response.collection.filter { it.track != null }
    
                    userComments.addAll(validComments)
    
                    commentsNextUrl = response.next_href
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isCommentsLoadingMore = false
                }
            }
        }
    
        var onUserUpdated: ((User) -> Unit)? = null

        fun updateProfile(
            username: String,
            bio: String,
            city: String,
            country: String
        ) {
            val oldUser = user ?: return
    
            viewModelScope.launch {
                // Optimistic update
                user = oldUser.copy(username = username, description = bio, city = city)
    
                try {
                    val request = UpdateProfileRequest(
                        username = username,
                        description = bio,
                        city = city,
                        countryCode = null
                    )
                    val updatedUser = api.updateMe(request)
    
                    if (!updatedUser.username.isNullOrBlank()) {
                        user = updatedUser
                        onUserUpdated?.invoke(updatedUser)
                    }
    
                    Toaster.show(str("profile_update_success"))
    
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Rollback on error
                    user = oldUser
                    Toaster.show(str("profile_update_error", e.message ?: ""))
                }
            }
        }
    
        private fun jpegBytes(image: BufferedImage, quality: Float): ByteArray {
            // Encode a BufferedImage to JPEG (desktop replacement for Bitmap.compress).
            val rgb = if (image.type == BufferedImage.TYPE_INT_RGB) image else {
                BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB).also { out ->
                    val g = out.createGraphics()
                    g.drawImage(image, 0, 0, java.awt.Color.BLACK, null)
                    g.dispose()
                }
            }
            val baos = ByteArrayOutputStream()
            val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
            val param = writer.defaultWriteParam.apply {
                compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
                compressionQuality = quality
            }
            ImageIO.createImageOutputStream(baos).use { ios ->
                writer.output = ios
                writer.write(null, javax.imageio.IIOImage(rgb, null, null), param)
            }
            writer.dispose()
            return baos.toByteArray()
        }

        private fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

        fun updateAvatarFromBitmap(bitmap: BufferedImage) {
            viewModelScope.launch {
                isLoading = true
                try {
                    val base64String = base64(jpegBytes(bitmap, 0.80f))
                    val request = AvatarUpdateRequest(imageData = base64String)
                    val response = api.updateAvatar(request)
                    if (!response.isSuccessful) {
                        throw Exception("Upload failed: ${response.code()}")
                    }
                    val freshMe = api.getMe()
                    user = freshMe
                    onUserUpdated?.invoke(freshMe)
                    Toaster.show(str("profile_update_success"))
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toaster.show(str("profile_update_error", e.message ?: ""))
                } finally {
                    isLoading = false
                }
            }
        }

        fun updateBannerFromBitmap(bitmap: BufferedImage) {
            viewModelScope.launch {
                isLoading = true
                try {
                    val base64String = base64(jpegBytes(bitmap, 0.80f))
                    val request = BannerUploadRequest(imageData = base64String)
                    val response = api.updateBanner(request)
                    if (!response.isSuccessful) {
                        throw Exception("Upload failed: ${response.code()}")
                    }
                    val freshMe = api.getMe()
                    user = freshMe
                    onUserUpdated?.invoke(freshMe)
                    Toaster.show(str("profile_update_success"))
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toaster.show(str("profile_update_error", e.message ?: ""))
                } finally {
                    isLoading = false
                }
            }
        }

        /**
         * Desktop replacement for the Android photo-picker + crop dialog:
         * opens a native file chooser (Swing, off the compose thread), loads the
         * image and feeds it into the existing upload path.
         */
        private fun pickImage(onPicked: (BufferedImage) -> Unit) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val chooser = javax.swing.JFileChooser().apply {
                        dialogTitle = str("profile_edit")
                        fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
                            "Images (*.jpg, *.png, *.webp)", "jpg", "jpeg", "png", "webp", "bmp"
                        )
                    }
                    val result = chooser.showOpenDialog(null)
                    if (result != javax.swing.JFileChooser.APPROVE_OPTION) return@launch
                    val image = ImageIO.read(chooser.selectedFile) ?: run {
                        Toaster.show(str("profile_update_error", chooser.selectedFile.name))
                        return@launch
                    }
                    withContext(Dispatchers.Main) { onPicked(image) }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        var avatarToCrop by mutableStateOf<BufferedImage?>(null)
        var bannerToCrop by mutableStateOf<BufferedImage?>(null)

        fun pickAndUploadAvatar() = pickImage { avatarToCrop = it }

        fun pickAndUploadBanner() = pickImage { bannerToCrop = it }

        fun deleteAvatar() {
            viewModelScope.launch {
                isLoading = true
                try {
                    val response = api.deleteAvatar()
                    if (response.isSuccessful) {
                        val freshMe = api.getMe()
                        user = freshMe
                        onUserUpdated?.invoke(freshMe)
                        Toaster.show(str("profile_avatar_deleted"))
                    } else {
                        Toaster.show(str("error_generic"))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toaster.show(str("profile_update_error", e.message ?: ""))
                } finally {
                    isLoading = false
                }
            }
        }

        fun deleteBanner() {
            viewModelScope.launch {
                isLoading = true
                try {
                    val response = api.deleteBanner()
                    if (response.isSuccessful) {
                        val freshMe = api.getMe()
                        user = freshMe
                        onUserUpdated?.invoke(freshMe)
                        Toaster.show(str("profile_banner_deleted"))
                    } else {
                        Toaster.show(str("error_generic"))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toaster.show(str("error_generic"))
                } finally {
                    isLoading = false
                }
            }
        }

        private suspend fun fetchUser(userId: Long): User? {
            return try {
                val req = GraphQlRequest(
                    operationName = "UserProfile",
                    query = """
                        query UserProfile(${'$'}urn: ID!) {
                          user(urn: ${'$'}urn) {
                            urn
                            username
                            avatarUrl
                            city
                            countryCode
                            followersCount
                            followingsCount
                            tracksCount
                            description
                            permalinkUrl
                            permalink
                            verified
                          }
                        }
                    """.trimIndent(),
                    variables = mapOf("urn" to "soundcloud:users:$userId")
                )
                val response = api.getUserProfileGraphQL(req)
                response.data?.user?.copy(id = userId) ?: try { api.getUser(userId) } catch (_: Exception) { null }
            } catch (e: Exception) {
                try { api.getUser(userId) } catch (_: Exception) { null }
            }
        }
    
        fun onTabSelected(tab: ProfileTab) { selectedTab = tab }
    }
