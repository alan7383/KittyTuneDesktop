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
    import kotlinx.coroutines.launch
    import kotlinx.coroutines.withContext
    import okhttp3.MediaType.Companion.toMediaTypeOrNull
    import okhttp3.MultipartBody
    import okhttp3.OkHttpClient
    import okhttp3.Request
    import okhttp3.RequestBody.Companion.asRequestBody
    import java.io.ByteArrayOutputStream
    import java.io.File
    import java.io.FileOutputStream
    
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
        private val api = RetrofitClient.create()
    
        var user by mutableStateOf<User?>(null)
        var isCurrentUser by mutableStateOf(false)
        var isLoading by mutableStateOf(true)
        var selectedTab by mutableStateOf(ProfileTab.POPULAR)
    
        // Content lists
        val popularTracks = mutableStateListOf<Track>()
        val allTracks = mutableStateListOf<Track>()
        val repostedTracks = mutableStateListOf<Track>()
        val albums = mutableStateListOf<Playlist>()
        val playlists = mutableStateListOf<Playlist>()
        val likedTracks = mutableStateListOf<Track>()
        val similarArtists = mutableStateListOf<User>()
        val userComments = mutableStateListOf<Comment>()
        private var commentsNextUrl: String? = null
        var isCommentsLoadingMore by mutableStateOf(false)
    
        var artistStationId: Long? = null

    init {
        viewModelScope.launch {
            com.alananasss.kittytune.data.RepostRepository.repostedTrackIds.collect { repostedIds ->
                if (isCurrentUser) {
                    repostedTracks.removeAll { track -> !repostedIds.contains(track.id) }
                }
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
    
        fun loadProfile(userId: Long) {
            viewModelScope.launch {
                isLoading = true
                isCurrentUser = false
                try {
                    // Check if current user
                    try {
                        val me = api.getMe()
                        if (me.id == userId) {
                            isCurrentUser = true
                        }
                    } catch (e: Exception) { /* ignore */ }
    
                    // Avoid flickering if reloading same user
                    if (user?.id != userId) {
                        user = fetchUser(userId)
                    } else {
                        val freshUser = fetchUser(userId)
                        user = freshUser
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
                                    .mapNotNull { it.track }
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
                                allLikes.addAll(firstPage.collection.mapNotNull { it.track })
                                nextUrl = firstPage.next_href
                                var safetyCount = 0
                                while (nextUrl != null && safetyCount < 10) {
                                    val page = api.getTrackLikesNextPage(nextUrl!!)
                                    allLikes.addAll(page.collection.mapNotNull { it.track })
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
                        albums.addAll(fetchedAlbums.filter { it.isAlbum })
    
                        // Playlists list: Exclude anything that is an album
                        playlists.clear()
                        playlists.addAll(fetchedPlaylists.filter { !it.isAlbum })
    
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
                    val base64String = base64(jpegBytes(bitmap, 0.90f))
                    val request = AvatarUpdateRequest(imageData = base64String)
                    val updatedUser = api.updateAvatar(request)
                    user = updatedUser
                    Toaster.show(str("profile_update_success"))
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toaster.show(str("profile_update_error", e.message ?: ""))
                } finally {
                    isLoading = false
                }
            }
        }

        fun updateBannerFromDataUri(bitmap: BufferedImage) {
            viewModelScope.launch {
                isLoading = true
                try {
                    val base64String = base64(jpegBytes(bitmap, 0.90f))
                    val dataUri = "data:image/jpeg;base64,$base64String"
                    val request = BannerUploadRequest(imageUrl = dataUri)
                    api.updateBanner(request)
                    user = api.getMe()
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

        fun pickAndUploadAvatar() = pickImage { updateAvatarFromBitmap(it) }

        fun pickAndUploadBanner() = pickImage { updateBannerFromDataUri(it) }

        fun deleteAvatar() {
            viewModelScope.launch {                isLoading = true
                try {
                    val updatedUser = api.deleteAvatar()
                    user = updatedUser
                    Toaster.show(str("profile_avatar_deleted"))
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
                        user = api.getMe()
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

        fun updateBannerFromBitmap(bitmap: BufferedImage) {
            viewModelScope.launch {
                isLoading = true
                try {
                    // 1. Prepare file locally (SoundCloud banners are heavy — high quality).
                    val file = File(com.alananasss.kittytune.core.AppDirs.cacheDir, "banner_temp.jpg")
                    file.writeBytes(jpegBytes(bitmap, 0.95f))

                    // 2. Initialize Upload (Get Signed URL & Policy from SoundCloud API)
                    val presign = api.getBannerPresign("image/jpeg")

                    // 3. Construct S3 Multipart Request
                    val client = OkHttpClient()
                    val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
                    presign.fields.forEach { (key, value) -> builder.addFormDataPart(key, value) }

                    // File must be the last part
                    val fileBody = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    builder.addFormDataPart("file", "blob", fileBody)

                    val requestS3 = Request.Builder().url(presign.url).post(builder.build()).build()

                    // 4. Execute upload to S3
                    withContext(Dispatchers.IO) {
                        val response = client.newCall(requestS3).execute()
                        if (!response.isSuccessful) {
                            val errorBody = response.body?.string()
                            throw Exception("S3 Upload failed: ${response.code} - $errorBody")
                        }
                    }

                    // 5. Confirm Upload to SoundCloud
                    val s3Key = presign.fields["key"] ?: throw Exception("Missing S3 key")
                    val finalS3Url = presign.url + s3Key
                    val confirmRequest = VisualsConfirmRequest(imageUrl = finalS3Url)
                    api.confirmBannerUpload(confirmRequest)

                    // 6. Refresh User Profile
                    user = api.getMe()
                    Toaster.show(str("profile_update_success"))
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toaster.show(str("profile_update_error", e.message ?: "Unknown"))
                } finally {
                    isLoading = false
                }
            }
        }

        private suspend fun fetchUser(userId: Long): User {
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
            return response.data?.user?.copy(id = userId) ?: throw Exception("User not found via GraphQL")
        }
    
        fun onTabSelected(tab: ProfileTab) { selectedTab = tab }
    }
