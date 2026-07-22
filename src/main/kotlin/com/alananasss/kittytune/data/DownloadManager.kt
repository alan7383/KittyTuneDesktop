package com.alananasss.kittytune.data

import com.alananasss.kittytune.core.AppDirs
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.*
import com.alananasss.kittytune.data.network.PlaylistLikeItem
import com.alananasss.kittytune.data.network.PlaylistLikeRequest
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.data.network.SoundCloudApi
import com.alananasss.kittytune.domain.*
import com.mpatric.mp3agic.ID3v24Tag
import com.mpatric.mp3agic.Mp3File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Desktop port of the Android DownloadManager.
 * Context/filesDir -> AppDirs; SAF DocumentFile output -> real java.io.File dirs;
 * getString -> str(). Widevine/HLS-DRM downloads are not portable to desktop, so DRM
 * streams are skipped by StreamResolver; plain HLS is remuxed to .m4a with FFmpeg.
 */
object DownloadManager {
    const val LIKES_BATCH_ID = -1L

    private const val CONCURRENT_DOWNLOAD_LIMIT = 4
    private val downloadSemaphore = Semaphore(CONCURRENT_DOWNLOAD_LIMIT)
    private val trackMutexes = ConcurrentHashMap<Long, Mutex>()

    private val dao get() = AppDatabase.downloadDao
    private val prefs = PlayerPreferences()
    private val tokenManager = TokenManager

    private val api: SoundCloudApi by lazy { RetrofitClient.create() }
    private val scope = CoroutineScope(Dispatchers.IO)
    private val client = OkHttpClient()

    private fun trackUrns(trackIds: List<Long>): List<String> =
        trackIds.filter { it > 0 }.map { "soundcloud:tracks:$it" }

    private fun appendMissingTrackIds(existingTrackIds: List<Long>, newTrackIds: List<Long>): List<Long> {
        val merged = existingTrackIds.filter { it > 0 }.toMutableList()
        newTrackIds.filter { it > 0 }.forEach { trackId ->
            if (!merged.contains(trackId)) merged.add(trackId)
        }
        return merged
    }

    private fun mergeReorderedTrackIds(remoteTrackIds: List<Long>, reorderedTrackIds: List<Long>): List<Long> {
        val orderedIds = reorderedTrackIds.filter { it > 0 }
        val orderedSet = orderedIds.toSet()
        return orderedIds + remoteTrackIds.filter { it > 0 && it !in orderedSet }
    }

    private fun playlistUpdateRequest(
        playlist: Playlist,
        trackIds: List<Long>,
        title: String = playlist.title.orEmpty(),
        description: String = playlist.description.orEmpty(),
        genre: String = playlist.genre.orEmpty(),
        tagList: String = playlist.tagList.orEmpty(),
        isPublic: Boolean = playlist.sharing == "public",
        setType: String? = playlist.setType,
        releaseDate: String? = playlist.releaseDate,
        permalink: String? = playlist.permalink
    ): PlaylistUpdateRequest = PlaylistUpdateRequest(
        trackUrns = trackUrns(trackIds),
        title = title, description = description, genre = genre, tagList = tagList, isPublic = isPublic,
        setType = setType, releaseDate = releaseDate, permalink = permalink
    )

    private suspend fun updateRemotePlaylist(playlistId: Long, request: PlaylistUpdateRequest) {
        var response = api.updatePlaylist(playlistId, request)
        if (!response.isSuccessful) {
            var errorBody = runCatching { response.errorBody()?.string() }.getOrNull()
            val captchaUrl = SessionManager.extractDataDomeCaptchaUrl(errorBody)
            if (response.code() == 403 && captchaUrl != null) {
                val solved = SessionManager.awaitDataDomeChallenge(captchaUrl)
                if (solved) {
                    response = api.updatePlaylist(playlistId, request)
                    if (response.isSuccessful) return
                    errorBody = runCatching { response.errorBody()?.string() }.getOrNull()
                }
            }
            throw Exception("SoundCloud playlist update failed (${response.code()}): ${errorBody ?: response.message()}")
        }
    }

    private val _downloadProgress = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val downloadProgress = _downloadProgress.asStateFlow()

    private val _playlistDownloadProgress = MutableStateFlow<Map<Long, Float>>(emptyMap())
    val playlistDownloadProgress = _playlistDownloadProgress.asStateFlow()

    private val _storageTrigger = MutableStateFlow(0)
    val storageTrigger = _storageTrigger.asStateFlow()

    private val _libraryUpdated = MutableSharedFlow<Unit>(replay = 1)
    val libraryUpdated = _libraryUpdated.asSharedFlow()

    fun notifyLibraryUpdated() {
        _libraryUpdated.tryEmit(Unit)
    }

    private val _deletedPlaylistIds = MutableStateFlow<Set<Long>>(emptySet())
    val deletedPlaylistIds = _deletedPlaylistIds.asStateFlow()

    lateinit var downloadedIds: StateFlow<Set<Long>>

    private val activeJobs = mutableMapOf<Long, Job>()
    private val activePlaylistJobs = mutableMapOf<Long, Job>()

    fun init() {
        downloadedIds = dao.getAllTracks()
            .map { list -> list.map { it.id }.toSet() }
            .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = emptySet())

        scope.launch {
            try {
                val allPlaylists = dao.getAllPlaylists().first()
                allPlaylists.forEach { localPlaylist ->
                    if (!localPlaylist.isUserCreated && localPlaylist.id > 0) {
                        val tracks = dao.getTracksForPlaylistSync(localPlaylist.id)
                        val hasDownloadedTracks = tracks.any { it.localAudioPath.isNotEmpty() }
                        if (!hasDownloadedTracks) {
                            dao.deletePlaylist(localPlaylist.id)
                            dao.deletePlaylistRefs(localPlaylist.id)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun sanitizeFilename(name: String): String {
        var clean = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        val reserved = listOf("CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9")
        if (reserved.any { clean.equals(it, ignoreCase = true) }) clean = "sanitized_$clean"
        if (clean.length > 200) clean = clean.substring(0, 200)
        return clean
    }

    /** Resolve the output directory (custom download location or default Music\KittyTune). */
    private fun outputDirFor(subDir: String?): File {
        val custom = prefs.getDownloadLocation()
        val root = if (custom != null) File(custom) else AppDirs.defaultDownloadDir
        val dir = if (subDir != null) File(root, sanitizeFilename(subDir)) else root
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getOutputStreamForFile(fileName: String, subDir: String?): Pair<OutputStream, String> {
        val dir = outputDirFor(subDir)
        val file = File(dir, fileName)
        if (file.exists()) file.delete()
        return Pair(FileOutputStream(file), file.absolutePath)
    }

    private fun deleteFileByPath(path: String) {
        if (path.isEmpty()) return
        try {
            if (path.startsWith("exo_cache://")) return // legacy Android DRM path, N/A on desktop
            val file = File(path)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun removeAllContent(includeAudio: Boolean, includeImages: Boolean) {
        withContext(Dispatchers.IO) {
            val allTracks = dao.getAllTracks().first()
            allTracks.forEach { track ->
                var updatedTrack = track
                var changed = false
                if (includeAudio && track.localAudioPath.isNotEmpty()) {
                    deleteFileByPath(track.localAudioPath)
                    updatedTrack = updatedTrack.copy(localAudioPath = "")
                    changed = true
                }
                if (includeImages && track.localArtworkPath.isNotEmpty()) {
                    deleteFileByPath(track.localArtworkPath)
                    updatedTrack = updatedTrack.copy(localArtworkPath = "")
                    changed = true
                }
                if (changed) dao.updateTrack(updatedTrack)
            }
            _storageTrigger.update { it + 1 }
        }
    }

    suspend fun createUserPlaylist(name: String, isPublic: Boolean = true): Long {
        var serverId: Long? = null
        if (!tokenManager.isGuestMode()) {
            try {
                val req = PlaylistCreateRequest(PlaylistCreatePayload(title = name, isPublic = isPublic))
                val response = api.createPlaylist(req)
                if (response.isSuccessful) {
                    val body = response.body()?.asJsonObject
                    var extractedId = 0L
                    if (body != null) {
                        if (body.has("id")) extractedId = body.get("id").asLong
                        else if (body.has("playlist")) {
                            val pObj = body.getAsJsonObject("playlist")
                            if (pObj.has("id")) extractedId = pObj.get("id").asLong
                            if (extractedId == 0L && pObj.has("urn")) {
                                extractedId = pObj.get("urn").asString.split(":").lastOrNull()?.toLongOrNull() ?: 0L
                            }
                        }
                        if (extractedId == 0L && body.has("urn")) {
                            extractedId = body.get("urn").asString.split(":").lastOrNull()?.toLongOrNull() ?: 0L
                        }
                    }
                    if (extractedId > 0L) {
                        serverId = extractedId
                        _libraryUpdated.emit(Unit)
                        return extractedId
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val finalId = serverId ?: -(System.currentTimeMillis())
        val playlist = LocalPlaylist(id = finalId, title = name, artist = str("me_artist"), artworkUrl = "", trackCount = 0, isUserCreated = true)
        dao.insertPlaylist(playlist)
        _libraryUpdated.emit(Unit)
        return finalId
    }

    fun addTrackToPlaylist(playlistId: Long, track: Track) {
        scope.launch {
            val existingTrack = dao.getTrack(track.id)
            if (existingTrack == null) {
                val localTrack = LocalTrack(
                    id = track.id,
                    title = track.title ?: str("untitled_track"),
                    artist = track.user?.username ?: str("unknown_artist"),
                    artworkUrl = track.fullResArtwork,
                    duration = track.durationMs ?: 0L,
                    localAudioPath = "",
                    localArtworkPath = ""
                )
                dao.insertTrack(localTrack)
            }
            val currentTracks = dao.getTracksForPlaylistSync(playlistId)
            val alreadyExists = currentTracks.any { it.id == track.id }
            if (!alreadyExists) {
                dao.insertPlaylistTrackRef(PlaylistTrackCrossRef(playlistId, track.id))
                val playlist = dao.getPlaylist(playlistId)
                if (playlist != null) dao.updatePlaylist(playlist.copy(trackCount = playlist.trackCount + 1))
            }

            if (playlistId > 0 && !tokenManager.isGuestMode()) {
                try {
                    val onlinePlaylist = api.getPlaylist(playlistId)
                    val trackIds = appendMissingTrackIds(
                        existingTrackIds = (onlinePlaylist.tracks ?: emptyList()).map { it.id },
                        newTrackIds = listOf(track.id)
                    )
                    updateRemotePlaylist(playlistId, playlistUpdateRequest(onlinePlaylist, trackIds))
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        scope.launch {
            dao.removeTrackFromPlaylist(playlistId, trackId)
            val playlist = dao.getPlaylist(playlistId)
            if (playlist != null) dao.updatePlaylist(playlist.copy(trackCount = (playlist.trackCount - 1).coerceAtLeast(0)))

            if (playlistId > 0 && !tokenManager.isGuestMode()) {
                try {
                    val onlinePlaylist = api.getPlaylist(playlistId)
                    val trackIds = (onlinePlaylist.tracks ?: emptyList()).map { it.id }.toMutableList()
                    trackIds.remove(trackId)
                    updateRemotePlaylist(playlistId, playlistUpdateRequest(onlinePlaylist, trackIds))
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    fun swapTrackOrder(playlistId: Long, trackId1: Long, trackId2: Long) {
        scope.launch {
            val ref1 = dao.getRef(playlistId, trackId1); val ref2 = dao.getRef(playlistId, trackId2)
            if (ref1 != null && ref2 != null) {
                dao.updatePlaylistTrackRef(ref1.copy(addedAt = ref2.addedAt))
                dao.updatePlaylistTrackRef(ref2.copy(addedAt = ref1.addedAt))
            }
        }
    }

    fun reorderPlaylistTracks(playlistId: Long, orderedTrackIds: List<Long>) {
        scope.launch {
            val baseTime = System.currentTimeMillis()
            orderedTrackIds.forEachIndexed { index, trackId ->
                val ref = dao.getRef(playlistId, trackId)
                if (ref != null) dao.updatePlaylistTrackRef(ref.copy(addedAt = baseTime + index))
            }
        }
    }

    fun syncPlaylistOrderOnline(playlistId: Long, newOrderIds: List<Long>) {
        scope.launch {
            if (playlistId > 0 && !tokenManager.isGuestMode()) {
                try {
                    val onlinePlaylist = api.getPlaylist(playlistId)
                    val onlineTrackIds = (onlinePlaylist.tracks ?: emptyList()).map { it.id }
                    val finalTrackIds = mergeReorderedTrackIds(onlineTrackIds, newOrderIds)
                    updateRemotePlaylist(playlistId, playlistUpdateRequest(onlinePlaylist, finalTrackIds))
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    fun updatePlaylistCover(playlistId: Long, sourceFile: File) {
        scope.launch {
            try {
                val file = File(AppDirs.imageCacheDir, "playlist_cover_$playlistId.jpg")
                sourceFile.copyTo(file, overwrite = true)
                val playlist = dao.getPlaylist(playlistId)
                if (playlist != null) dao.updatePlaylist(playlist.copy(localCoverPath = file.absolutePath))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun editPlaylistMetadata(
        playlistId: Long, newTitle: String, newDescription: String? = null, newSharing: String? = null,
        newTagList: String? = null, newPermalink: String? = null, newGenre: String? = null,
        newSetType: String? = null, newReleaseDate: String? = null
    ) {
        scope.launch {
            dao.updatePlaylistTitle(playlistId, newTitle)
            if (playlistId > 0 && !tokenManager.isGuestMode()) {
                try {
                    val onlinePlaylist = api.getPlaylist(playlistId)
                    val request = playlistUpdateRequest(
                        playlist = onlinePlaylist,
                        trackIds = (onlinePlaylist.tracks ?: emptyList()).map { it.id },
                        title = newTitle,
                        description = newDescription ?: onlinePlaylist.description.orEmpty(),
                        genre = newGenre ?: onlinePlaylist.genre.orEmpty(),
                        tagList = newTagList ?: onlinePlaylist.tagList.orEmpty(),
                        isPublic = newSharing?.let { it == "public" } ?: (onlinePlaylist.sharing == "public"),
                        setType = newSetType ?: onlinePlaylist.setType,
                        releaseDate = newReleaseDate ?: onlinePlaylist.releaseDate,
                        permalink = newPermalink ?: onlinePlaylist.permalink
                    )
                    updateRemotePlaylist(playlistId, request)
                } catch (e: Exception) { e.printStackTrace() }
            }
            _libraryUpdated.tryEmit(Unit)
        }
    }

    fun getAllPlaylistsFlow() = dao.getAllPlaylists()
    fun getUserPlaylistsFlow() = dao.getUserPlaylists()
    fun isPlaylistInLibraryFlow(playlistId: Long) = dao.getPlaylistFlow(playlistId)

    fun importPlaylistToLibrary(playlist: Playlist, tracks: List<Track>, syncToCloud: Boolean = true) {
        scope.launch {
            if (syncToCloud && playlist.id > 0 && !tokenManager.isGuestMode()) {
                val token = tokenManager.getAccessToken()
                if (!token.isNullOrEmpty()) {
                    try {
                        val permalink = playlist.permalinkUrl ?: ""
                        val targetUrn = playlist.urn ?: when {
                            permalink.contains("artist-stations") -> "soundcloud:system-playlists:artist-stations:${playlist.id}"
                            permalink.contains("track-stations") -> "soundcloud:system-playlists:track-stations:${playlist.id}"
                            else -> "soundcloud:playlists:${playlist.id}"
                        }
                        val payload = PlaylistLikeRequest(likes = listOf(PlaylistLikeItem(targetUrn)))
                        val response = api.likePlaylist(payload)
                        if (response.code() == 401) SessionManager.requestSessionRefresh(force = true)
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }

            val baseTime = System.currentTimeMillis()
            val localPlaylist = LocalPlaylist(
                id = playlist.id,
                title = playlist.title ?: str("untitled_track"),
                artist = playlist.user?.username ?: str("unknown_artist"),
                artworkUrl = playlist.fullResArtwork,
                trackCount = tracks.size,
                isUserCreated = false,
                permalinkUrl = playlist.permalinkUrl,
                isAlbum = playlist.isAlbum
            )
            dao.insertPlaylist(localPlaylist)

            tracks.forEachIndexed { index, track ->
                val existingTrack = dao.getTrack(track.id)
                if (existingTrack == null) {
                    val localTrack = LocalTrack(
                        id = track.id,
                        title = track.title ?: str("untitled_track"),
                        artist = track.user?.username ?: str("unknown_artist"),
                        artworkUrl = track.fullResArtwork,
                        duration = track.durationMs ?: 0L,
                        localAudioPath = "",
                        localArtworkPath = ""
                    )
                    dao.insertTrack(localTrack)
                }
                dao.insertPlaylistTrackRef(PlaylistTrackCrossRef(playlist.id, track.id, baseTime + index))
            }
            _libraryUpdated.tryEmit(Unit)
        }
    }

    fun deletePlaylist(playlistId: Long, syncToCloud: Boolean = true, forceUserCreated: Boolean? = null, forcePermalink: String? = null) {
        scope.launch {
            val playlistToDelete = dao.getPlaylist(playlistId)

            if (syncToCloud && playlistId > 0 && !tokenManager.isGuestMode()) {
                val token = tokenManager.getAccessToken()
                if (!token.isNullOrEmpty()) {
                    try {
                        val permalink = forcePermalink ?: playlistToDelete?.permalinkUrl ?: ""
                        val targetUrn = when {
                            permalink.contains("artist-stations") -> "soundcloud:system-playlists:artist-stations:$playlistId"
                            permalink.contains("track-stations") -> "soundcloud:system-playlists:track-stations:$playlistId"
                            else -> "soundcloud:playlists:$playlistId"
                        }
                        val isUserCreated = forceUserCreated ?: playlistToDelete?.isUserCreated ?: false
                        if (isUserCreated) {
                            val response = api.deletePlaylist(playlistId)
                            if (response.code() == 401) SessionManager.requestSessionRefresh(force = true)
                        } else {
                            val payload = PlaylistLikeRequest(likes = listOf(PlaylistLikeItem(targetUrn)))
                            val response = api.unlikePlaylist(payload)
                            if (response.code() == 401) SessionManager.requestSessionRefresh(force = true)
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }

            if (playlistToDelete != null) {
                val folderName = sanitizeFilename("${playlistToDelete.title}_${playlistToDelete.id}")
                try {
                    val playlistDir = File(outputDirFor(null), folderName)
                    if (playlistDir.exists() && playlistDir.isDirectory) playlistDir.deleteRecursively()
                } catch (e: Exception) { e.printStackTrace() }
            }

            dao.deletePlaylist(playlistId)
            dao.deletePlaylistRefs(playlistId)
            HistoryRepository.removeFromHistory(playlistId)

            val orphans = dao.getOrphanTracksList()
            orphans.forEach { track ->
                deleteFileByPath(track.localAudioPath)
                deleteFileByPath(track.localArtworkPath)
                dao.deleteTrack(track.id)
            }

            _storageTrigger.update { it + 1 }
            _deletedPlaylistIds.update { it + playlistId }
            _libraryUpdated.tryEmit(Unit)
        }
    }

    fun removePlaylistDownloads(playlistId: Long) {
        scope.launch {
            val localTracks = dao.getTracksForPlaylistSync(playlistId)
            val domainTracks = localTracks.map { local ->
                Track(id = local.id, title = local.title, artworkUrl = local.artworkUrl, durationMs = local.duration, user = User(0, local.artist, null))
            }
            removeDownloads(domainTracks)
        }
    }

    fun removeDownloads(tracks: List<Track>) {
        scope.launch {
            tracks.forEach { track ->
                val local = dao.getTrack(track.id)
                if (local != null) {
                    deleteFileByPath(local.localAudioPath)
                    deleteFileByPath(local.localArtworkPath)
                    dao.updateTrack(local.copy(localAudioPath = "", localArtworkPath = ""))
                }
            }
            _storageTrigger.update { it + 1 }
        }
    }

    fun toggleSaveArtist(user: User) {
        scope.launch {
            val userId = user.numericId
            val isSaved = dao.getArtist(userId) != null
            if (isSaved) dao.deleteArtist(userId)
            else dao.insertArtist(LocalArtist(userId, user.username ?: str("menu_go_artist"), user.avatarUrl ?: "", user.trackCount))

            if (!tokenManager.isGuestMode()) {
                try {
                    if (isSaved) api.unfollowUser(userId) else api.followUser(userId)
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    fun isArtistSavedFlow(artistId: Long) = dao.getArtistFlow(artistId)
    fun getSavedArtists() = dao.getAllSavedArtists()
    suspend fun isArtistSaved(artistId: Long): Boolean = dao.getArtist(artistId) != null
    suspend fun saveArtist(user: User) = dao.insertArtist(LocalArtist(user.numericId, user.username ?: "", user.avatarUrl ?: "", user.trackCount))
    suspend fun deleteArtist(artistId: Long) = dao.deleteArtist(artistId)

    fun refreshFollowings() {
        scope.launch {
            try {
                if (tokenManager.isGuestMode()) return@launch
                val me = api.getMe()
                val allFollowings = mutableListOf<User>()

                var nextCursor: String?
                val userSchema = "urn permalink username avatarUrl firstName lastName city country countryCode tracksCount playlistCount followersCount followingsCount verified isPro description userAvatarUrlTemplate visualUrlTemplate stationUrns createdAt badges"
                val followingsQuery = "query UserFollowingsQuery(\$input: UserFollowsInput!) { userFollowings(input: \$input) { pageInfo { endCursor } items { user { $userSchema } } } }"

                val req = GraphQlFollowsRequest(
                    operationName = "UserFollowingsQuery",
                    query = followingsQuery,
                    variables = GraphQlFollowsVariables(GraphQlFollowsInput(urn = "soundcloud:users:${me.id}", first = 200, after = null))
                )
                val firstPage = api.getUserFollowingsGraphQL(req)
                val result = firstPage.data?.userFollowings
                result?.items?.forEach { it.user?.let { u -> allFollowings.add(u) } }
                nextCursor = result?.pageInfo?.endCursor

                var safetyCount = 0
                while (nextCursor != null && safetyCount < 20) {
                    val nextReq = req.copy(variables = GraphQlFollowsVariables(GraphQlFollowsInput(urn = "soundcloud:users:${me.id}", first = 200, after = nextCursor)))
                    val page = api.getUserFollowingsGraphQL(nextReq)
                    val pageResult = page.data?.userFollowings
                    pageResult?.items?.forEach { it.user?.let { u -> allFollowings.add(u) } }
                    nextCursor = pageResult?.pageInfo?.endCursor
                    safetyCount++
                }

                allFollowings.forEach { user ->
                    dao.insertArtist(LocalArtist(user.numericId, user.username ?: "", user.avatarUrl ?: "", user.trackCount))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun downloadPlaylist(playlist: Playlist, tracks: List<Track>) {
        importPlaylistToLibrary(playlist, tracks)
        val title = playlist.title ?: "Playlist"
        val folderName = sanitizeFilename("${title}_${playlist.id}")
        downloadBatch(tracks, playlist.id, folderName)
    }

    fun downloadBatch(tracks: List<Track>, batchId: Long, subFolderName: String? = null) {
        val existingJob = activePlaylistJobs[batchId]
        if (existingJob != null && existingJob.isActive) return

        val batchJob = scope.launch {
            val trackIdsToDownload = tracks.map { it.id }.toSet()
            try {
                _playlistDownloadProgress.update { it + (batchId to 0f) }

                supervisorScope {
                    val individualJobs = tracks.map { track ->
                        launch {
                            downloadSemaphore.withPermit {
                                val existing = dao.getTrack(track.id)
                                if (existing == null || existing.localAudioPath.isEmpty()) {
                                    startDownloadJob(track, subFolderName).join()
                                }
                            }
                        }
                    }

                    val progressJob = launch {
                        combine(_downloadProgress, downloadedIds) { progressMap, downloadedSet ->
                            var totalPercent = 0L
                            trackIdsToDownload.forEach { id ->
                                val currentProgress = progressMap[id]
                                when {
                                    currentProgress != null -> totalPercent += currentProgress.toLong()
                                    downloadedSet.contains(id) -> totalPercent += 100L
                                    else -> totalPercent += 0L
                                }
                            }
                            val count = trackIdsToDownload.size.coerceAtLeast(1)
                            (totalPercent.toFloat() / (count * 100f)).coerceIn(0f, 1f)
                        }.collect { overallPercentage ->
                            _playlistDownloadProgress.update { it + (batchId to overallPercentage) }
                        }
                    }

                    individualJobs.joinAll()
                    progressJob.cancel()
                }

                _playlistDownloadProgress.update { it + (batchId to 1f) }
                delay(500)
                _storageTrigger.update { it + 1 }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _playlistDownloadProgress.update { it - batchId }
                activePlaylistJobs.remove(batchId)
                _storageTrigger.update { it + 1 }
            }
        }
        activePlaylistJobs[batchId] = batchJob
    }

    fun downloadTrack(track: Track) {
        if (activeJobs.containsKey(track.id)) return
        scope.launch {
            val existing = dao.getTrack(track.id)
            if (existing != null && existing.localAudioPath.isNotEmpty()) return@launch
            startDownloadJob(track, null)
        }
    }

    private fun startDownloadJob(track: Track, subFolderName: String? = null): Job {
        val job = scope.launch {
            trackMutexes.getOrPut(track.id) { Mutex() }.withLock {
                var tempAudioFile: File? = null
                var tempImageFile: File? = null
                var taggedAudioFile: File? = null

                try {
                    val doubleCheck = dao.getTrack(track.id)
                    if (doubleCheck != null && doubleCheck.localAudioPath.isNotEmpty()) return@withLock

                    _downloadProgress.update { it + (track.id to 0) }

                val resolvedStream = StreamResolver.resolveStreamWithDrm(track, forDownload = true)
                val streamUrl = resolvedStream?.url ?: throw Exception("Cannot resolve stream URL for download")

                val isHlsStream = streamUrl.contains(".m3u8") || streamUrl.contains("hls")

                val isYoutubeStream = streamUrl.contains("googlevideo.com") || track.source == "youtube"
                val ext = if (isYoutubeStream || isHlsStream) "m4a" else "mp3"
                val mime = if (isYoutubeStream || isHlsStream) "audio/mp4" else "audio/mpeg"

                tempAudioFile = File(AppDirs.audioCacheDir, "temp_${track.id}.$ext")
                tempImageFile = File(AppDirs.audioCacheDir, "temp_art_${track.id}.jpg")
                taggedAudioFile = File(AppDirs.audioCacheDir, "tagged_${track.id}.$ext")
                val internalArtFile = File(AppDirs.imageCacheDir, "art_${track.id}.jpg")

                if (isHlsStream) {
                    // Plain HLS: remux to .m4a with FFmpeg (no re-encode). DRM HLS was filtered out.
                    remuxHls(streamUrl, tempAudioFile) { p ->
                        if (isActive) _downloadProgress.update { c -> c + (track.id to p) }
                    }
                } else {
                    FileOutputStream(tempAudioFile).use { fos ->
                        downloadFileToStream(streamUrl, fos) { p ->
                            if (isActive) _downloadProgress.update { c -> c + (track.id to p) }
                        }
                    }
                }
                FileOutputStream(tempImageFile).use { fos ->
                    downloadFileToStream(track.fullResArtwork, fos) { _ -> }
                }

                if (tempImageFile.exists()) tempImageFile.copyTo(internalArtFile, overwrite = true)

                if (ext == "mp3") {
                    try {
                        val mp3file = Mp3File(tempAudioFile)
                        val id3v2Tag = if (mp3file.hasId3v2Tag()) mp3file.id3v2Tag else ID3v24Tag()
                        mp3file.id3v2Tag = id3v2Tag
                        id3v2Tag.title = track.title ?: str("untitled_track")
                        id3v2Tag.artist = track.user?.username ?: str("unknown_artist")
                        id3v2Tag.album = subFolderName ?: str("app_name")
                        id3v2Tag.comment = str("download_comment")
                        val imageBytes = tempImageFile.readBytes()
                        id3v2Tag.setAlbumImage(imageBytes, "image/jpeg")
                        mp3file.save(taggedAudioFile.absolutePath)
                    } catch (e: Exception) {
                        tempAudioFile.copyTo(taggedAudioFile, overwrite = true)
                    }
                } else {
                    tempAudioFile.copyTo(taggedAudioFile, overwrite = true)
                }

                val cleanArtist = sanitizeFilename(track.user?.username ?: str("generic_artist"))
                val cleanTitle = sanitizeFilename(track.title ?: str("generic_title"))
                val finalFileName = "$cleanArtist - $cleanTitle.$ext"

                val (audioStream, audioPath) = getOutputStreamForFile(finalFileName, subFolderName)
                FileInputStream(taggedAudioFile).use { input ->
                    audioStream.use { output -> input.copyTo(output) }
                }

                val existingTrack = dao.getTrack(track.id)
                val creationTimestamp = existingTrack?.downloadedAt ?: System.currentTimeMillis()

                val localTrack = LocalTrack(
                    id = track.id,
                    title = track.title ?: str("untitled_track"),
                    artist = track.user?.username ?: str("unknown_artist"),
                    artworkUrl = track.fullResArtwork,
                    duration = track.durationMs ?: 0L,
                    localAudioPath = audioPath,
                    localArtworkPath = internalArtFile.absolutePath,
                    downloadedAt = creationTimestamp
                )
                if (existingTrack == null) dao.insertTrack(localTrack) else dao.updateTrack(localTrack)

                _storageTrigger.update { it + 1 }
                AchievementManager.increment("download_100")
                AchievementManager.increment("download_1000")
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    tempAudioFile?.let { if (it.exists()) it.delete() }
                    tempImageFile?.let { if (it.exists()) it.delete() }
                    taggedAudioFile?.let { if (it.exists()) it.delete() }
                } catch (e: Exception) {}
                _downloadProgress.update { it - track.id }
                activeJobs.remove(track.id)
            }
        }
    }
        activeJobs[track.id] = job
        return job
    }

    /** Remux a plain HLS stream to a local m4a container with FFmpeg (stream copy, no re-encode). */
    private fun remuxHls(m3u8Url: String, outFile: File, onProgress: (Int) -> Unit) {
        val grabber = org.bytedeco.javacv.FFmpegFrameGrabber(m3u8Url)
        grabber.start()
        val recorder = org.bytedeco.javacv.FFmpegFrameRecorder(outFile.absolutePath, grabber.audioChannels).apply {
            format = "mp4"
            sampleRate = grabber.sampleRate
            audioChannels = grabber.audioChannels
            audioCodec = org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC
            start()
        }
        val total = grabber.lengthInTime.coerceAtLeast(1)
        var frame = grabber.grabSamples()
        while (frame != null) {
            recorder.recordSamples(frame.sampleRate, frame.audioChannels, *frame.samples)
            onProgress(((grabber.timestamp * 100) / total).toInt().coerceIn(0, 100))
            frame = grabber.grabSamples()
        }
        recorder.stop(); recorder.release()
        grabber.stop(); grabber.release()
        onProgress(100)
    }

    private suspend fun downloadFileToStream(url: String, outputStream: OutputStream, onProgress: (Int) -> Unit) {
        val headRequest = Request.Builder().url(url)
            .header("User-Agent", com.alananasss.kittytune.utils.Config.USER_AGENT)
            .header("Range", "bytes=0-0").build()

        var contentLength = -1L
        var acceptRanges = false

        try {
            client.newCall(headRequest).execute().use { response ->
                if (response.isSuccessful || response.code == 206) {
                    val contentRange = response.header("Content-Range")
                    if (contentRange != null && contentRange.contains("/")) {
                        contentLength = contentRange.substringAfter("/").toLongOrNull() ?: -1L
                    }
                    acceptRanges = response.code == 206 || response.header("Accept-Ranges") == "bytes"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (contentLength <= 0 || !acceptRanges) {
            val request = Request.Builder().url(url)
                .header("User-Agent", com.alananasss.kittytune.utils.Config.USER_AGENT).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("HTTP Error ${response.code}")
                val body = response.body ?: throw Exception(str("error_empty_body"))
                val total = body.contentLength()
                body.byteStream().use { input ->
                    val buffer = ByteArray(32 * 1024)
                    var copied = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } >= 0) {
                        outputStream.write(buffer, 0, read)
                        copied += read
                        if (total > 0) onProgress(((copied * 100) / total).toInt())
                    }
                    outputStream.flush()
                }
            }
            return
        }

        val numThreads = 3
        val chunkSize = contentLength / numThreads
        val tempFiles = Array(numThreads) { File(AppDirs.audioCacheDir, "chunk_${UUID.randomUUID()}_$it.tmp") }
        var totalCopied = 0L

        coroutineScope {
            val jobs = (0 until numThreads).map { i ->
                async(Dispatchers.IO) {
                    val startByte = i * chunkSize
                    val endByte = if (i == numThreads - 1) contentLength - 1 else (startByte + chunkSize - 1)
                    val chunkRequest = Request.Builder().url(url)
                        .header("User-Agent", com.alananasss.kittytune.utils.Config.USER_AGENT)
                        .header("Range", "bytes=$startByte-$endByte").build()

                    client.newCall(chunkRequest).execute().use { response ->
                        if (!response.isSuccessful) throw Exception("HTTP Error ${response.code}")
                        val body = response.body ?: throw Exception("Empty body in chunk $i")
                        tempFiles[i].outputStream().use { fileOut ->
                            val buffer = ByteArray(32 * 1024)
                            var read: Int
                            val input = body.byteStream()
                            while (input.read(buffer).also { read = it } >= 0) {
                                fileOut.write(buffer, 0, read)
                                synchronized(this@DownloadManager) {
                                    totalCopied += read
                                    onProgress(((totalCopied * 100) / contentLength).toInt())
                                }
                            }
                        }
                    }
                }
            }
            jobs.awaitAll()
        }

        for (tempFile in tempFiles) {
            if (tempFile.exists()) {
                tempFile.inputStream().use { input -> input.copyTo(outputStream, 64 * 1024) }
                tempFile.delete()
            }
        }
        outputStream.flush()
    }

    fun deleteTrack(trackId: Long) {
        scope.launch {
            val track = dao.getTrack(trackId)
            if (track != null) {
                deleteFileByPath(track.localAudioPath)
                deleteFileByPath(track.localArtworkPath)
                dao.updateTrack(track.copy(localAudioPath = "", localArtworkPath = ""))
            }
            _storageTrigger.update { it + 1 }
        }
    }

    fun cancelDownload(trackId: Long) {
        activeJobs[trackId]?.cancel()
        activeJobs.remove(trackId)
        _downloadProgress.update { it - trackId }
        try { File(AppDirs.audioCacheDir, "temp_$trackId.mp3").delete() } catch (e: Exception) {}
        _storageTrigger.update { it + 1 }
    }

    fun isPlaylistDownloading(playlistId: Long): Boolean = activePlaylistJobs.containsKey(playlistId)
    fun isTrackDownloading(trackId: Long): Boolean = activeJobs.containsKey(trackId)
    suspend fun getLocalTrack(id: Long): LocalTrack? = dao.getTrack(id)

    fun addTracksToPlaylistBulk(playlistId: Long, tracks: List<Track>) {
        scope.launch {
            tracks.forEach { track ->
                val existingTrack = dao.getTrack(track.id)
                if (existingTrack == null) {
                    val localTrack = LocalTrack(
                        id = track.id,
                        title = track.title ?: str("untitled_track"),
                        artist = track.user?.username ?: str("unknown_artist"),
                        artworkUrl = track.fullResArtwork,
                        duration = track.durationMs ?: 0L,
                        localAudioPath = "",
                        localArtworkPath = ""
                    )
                    dao.insertTrack(localTrack)
                }
                dao.insertPlaylistTrackRef(PlaylistTrackCrossRef(playlistId, track.id))
            }

            val playlist = dao.getPlaylist(playlistId)
            if (playlist != null) {
                val finalTrackCount = dao.getTracksForPlaylistSync(playlistId).size
                dao.updatePlaylist(playlist.copy(trackCount = finalTrackCount))
            }

            if (playlistId > 0 && !tokenManager.isGuestMode()) {
                try {
                    val onlinePlaylist = api.getPlaylist(playlistId)
                    val trackIds = appendMissingTrackIds(
                        existingTrackIds = (onlinePlaylist.tracks ?: emptyList()).map { it.id },
                        newTrackIds = tracks.map { it.id }
                    )
                    updateRemotePlaylist(playlistId, playlistUpdateRequest(onlinePlaylist, trackIds))
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    fun cancelBatch(batchId: Long) {
        val job = activePlaylistJobs[batchId]
        if (job != null) {
            job.cancel()
            activePlaylistJobs.remove(batchId)
            _playlistDownloadProgress.update { it - batchId }
            _storageTrigger.update { it + 1 }
        }
    }
}

