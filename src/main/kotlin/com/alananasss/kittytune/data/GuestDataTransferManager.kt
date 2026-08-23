package com.alananasss.kittytune.data

import com.alananasss.kittytune.core.AppDirs
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.LocalPlaylist
import com.alananasss.kittytune.data.local.PlaylistTrackCrossRef
import com.alananasss.kittytune.data.network.PlaylistLikeItem
import com.alananasss.kittytune.data.network.PlaylistLikeRequest
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.data.network.TrackLikeItem
import com.alananasss.kittytune.data.network.TrackLikeRequest
import com.alananasss.kittytune.domain.ArtworkUploadRequest
import com.alananasss.kittytune.domain.PlaylistCreatePayload
import com.alananasss.kittytune.domain.PlaylistCreateRequest
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import javax.imageio.ImageIO

data class GuestDataSummary(
    val likes: List<Track>,
    val userPlaylists: List<LocalPlaylist>,
    val likedPlaylists: List<LocalPlaylist>
) {
    val playlists: List<LocalPlaylist> get() = userPlaylists
    val hasData: Boolean get() = likes.isNotEmpty() || userPlaylists.isNotEmpty() || likedPlaylists.isNotEmpty()
}

object GuestDataTransferManager {
    private const val TAG = "GuestTransfer"

    suspend fun getGuestDataSummary(): GuestDataSummary = withContext(Dispatchers.IO) {
        val dao = AppDatabase.downloadDao
        val guestLikes = LikeRepository.likedTracks.value.filter {
            (it.source == "soundcloud" || it.source.isNullOrBlank()) && it.id > 0L
        }
        val allLocalPlaylists = try {
            dao.getAllPlaylists().first()
        } catch (e: Exception) {
            emptyList()
        }
        val userPlaylists = allLocalPlaylists.filter { it.isUserCreated && it.id < 0 }

        val likedPlaylistIds = LikeRepository.likedPlaylists.value
        val allPositivePlaylists = allLocalPlaylists.filter { it.id > 0 }
        val positiveIds = allPositivePlaylists.map { it.id }.toSet()
        val missingLikedPlaylists = likedPlaylistIds.filter { it > 0 && !positiveIds.contains(it) }.map { id ->
            LocalPlaylist(
                id = id,
                title = "Playlist",
                artist = "",
                artworkUrl = "",
                trackCount = 0,
                permalinkUrl = "https://soundcloud.com/playlists/$id",
                isUserCreated = false,
                addedAt = System.currentTimeMillis()
            )
        }
        val likedPlaylists = allPositivePlaylists + missingLikedPlaylists

        GuestDataSummary(guestLikes, userPlaylists, likedPlaylists)
    }

    suspend fun transferData(
        transferLikes: Boolean,
        transferUserPlaylists: Boolean,
        transferLikedPlaylists: Boolean = true,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val api = RetrofitClient.create()
        val dao = AppDatabase.downloadDao
        val summary = getGuestDataSummary()

        val totalLikesCount = if (transferLikes) summary.likes.size else 0
        val totalUserPlaylistsCount = if (transferUserPlaylists) summary.userPlaylists.size else 0
        val totalLikedPlaylistsCount = if (transferLikedPlaylists) summary.likedPlaylists.size else 0

        val likesBatchesCount = if (totalLikesCount > 0) ((totalLikesCount + 24) / 25) else 0
        val likedPlaylistsBatchesCount = if (totalLikedPlaylistsCount > 0) ((totalLikedPlaylistsCount + 24) / 25) else 0
        val totalOperations = likesBatchesCount + totalUserPlaylistsCount + likedPlaylistsBatchesCount

        if (totalOperations == 0) return@withContext true

        var completedOperations = 0
        var hasErrors = false

        if (transferLikes && summary.likes.isNotEmpty()) {
            val batches = summary.likes.chunked(25)
            for (batch in batches) {
                try {
                    val likeItems = batch.map { TrackLikeItem(targetUrn = "soundcloud:tracks:${it.id}") }
                    val response = api.likeTrack(TrackLikeRequest(likes = likeItems))
                    if (!response.isSuccessful) {
                        println("[$TAG] Batch like failed: ${response.code()}")
                        hasErrors = true
                    }
                } catch (e: Exception) {
                    println("[$TAG] Exception liking batch: ${e.message}")
                    hasErrors = true
                }
                completedOperations++
                onProgress(completedOperations.toFloat() / totalOperations.toFloat())
                delay(350)
            }
        }

        if (transferLikedPlaylists && summary.likedPlaylists.isNotEmpty()) {
            val scPlaylists =
                summary.likedPlaylists.filter { !(it.permalinkUrl?.contains("spotify") == true || it.id > 1000000000000000L) }
            val batches = scPlaylists.chunked(25)
            for (batch in batches) {
                try {
                    val playlistLikeItems = batch.map { localPlaylist ->
                        val permalink = localPlaylist.permalinkUrl ?: ""
                        val targetUrn = when {
                            permalink.contains("artist-stations") -> "soundcloud:system-playlists:artist-stations:${localPlaylist.id}"
                            permalink.contains("track-stations") -> "soundcloud:system-playlists:track-stations:${localPlaylist.id}"
                            else -> "soundcloud:playlists:${localPlaylist.id}"
                        }
                        PlaylistLikeItem(targetUrn = targetUrn)
                    }
                    val response = api.likePlaylist(PlaylistLikeRequest(likes = playlistLikeItems))
                    if (!response.isSuccessful) {
                        println("[$TAG] Batch playlist like failed: ${response.code()}")
                        hasErrors = true
                    }
                } catch (e: Exception) {
                    println("[$TAG] Exception liking playlist batch: ${e.message}")
                    hasErrors = true
                }
                completedOperations++
                onProgress(completedOperations.toFloat() / totalOperations.toFloat())
                delay(350)
            }
        }

        if (transferUserPlaylists && summary.userPlaylists.isNotEmpty()) {
            for (localPlaylist in summary.userPlaylists) {
                try {
                    val tracks = dao.getTracksForPlaylist(localPlaylist.id).first()
                    val validTrackUrns = tracks.filter { it.id > 0 }.map { "soundcloud:tracks:${it.id}" }

                    val req = PlaylistCreateRequest(
                        playlist = PlaylistCreatePayload(
                            title = localPlaylist.title,
                            isPublic = true
                        ),
                        trackUrns = validTrackUrns
                    )
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
                            var newLocalCoverPath: String? = null

                            if (!localPlaylist.localCoverPath.isNullOrBlank()) {
                                try {
                                    val originalCoverFile = File(localPlaylist.localCoverPath)
                                    if (originalCoverFile.exists()) {
                                        val newCoverFile = File(AppDirs.dataDir, "playlist_cover_${extractedId}.jpg")
                                        originalCoverFile.copyTo(newCoverFile, overwrite = true)
                                        newLocalCoverPath = newCoverFile.absolutePath

                                        val image = ImageIO.read(originalCoverFile)
                                        if (image != null) {
                                            val rgbImage = if (image.type == BufferedImage.TYPE_INT_RGB) image else {
                                                val converted = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
                                                val g = converted.createGraphics()
                                                g.drawImage(image, 0, 0, Color.BLACK, null)
                                                g.dispose()
                                                converted
                                            }
                                            val baos = ByteArrayOutputStream()
                                            ImageIO.write(rgbImage, "jpg", baos)
                                            val base64 = Base64.getEncoder().encodeToString(baos.toByteArray())
                                            val artworkReq = ArtworkUploadRequest(imageData = base64)
                                            val artResponse = api.uploadPlaylistArtwork(
                                                "soundcloud:playlists:$extractedId",
                                                artworkReq
                                            )
                                            if (!artResponse.isSuccessful) {
                                                println("[$TAG] Failed to upload playlist artwork to SoundCloud: ${artResponse.code()}")
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    println("[$TAG] Exception uploading playlist artwork: ${e.message}")
                                }
                            }

                            val updatedPlaylist = localPlaylist.copy(
                                id = extractedId,
                                isUserCreated = true,
                                localCoverPath = newLocalCoverPath ?: localPlaylist.localCoverPath
                            )
                            dao.insertPlaylist(updatedPlaylist)
                            for (t in tracks) {
                                dao.insertPlaylistTrackRef(
                                    PlaylistTrackCrossRef(
                                        playlistId = extractedId,
                                        trackId = t.id,
                                        addedAt = System.currentTimeMillis()
                                    )
                                )
                            }
                            dao.deletePlaylist(localPlaylist.id)
                            dao.deletePlaylistRefs(localPlaylist.id)
                        }
                    } else {
                        println("[$TAG] Failed to create playlist on SoundCloud: ${response.code()}")
                        hasErrors = true
                    }
                } catch (e: Exception) {
                    println("[$TAG] Exception creating playlist on SoundCloud: ${e.message}")
                    hasErrors = true
                }
                completedOperations++
                onProgress(completedOperations.toFloat() / totalOperations.toFloat())
                delay(350)
            }
        }

        !hasErrors
    }

    suspend fun clearGuestData() = withContext(Dispatchers.IO) {
        val dao = AppDatabase.downloadDao
        try {
            val userPlaylists = dao.getUserPlaylists().first().filter { it.isUserCreated && it.id < 0 }
            userPlaylists.forEach { playlist ->
                dao.deletePlaylist(playlist.id)
                dao.deletePlaylistRefs(playlist.id)
            }
            dao.deleteNonDownloadedOnlinePlaylists()
            dao.cleanUnreferencedEmptyTracks()
            LikeRepository.clear()
            DownloadManager.notifyLibraryUpdated()
        } catch (e: Exception) {
            println("[$TAG] Exception clearing guest data: ${e.message}")
        }
    }
}
