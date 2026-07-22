package com.alananasss.kittytune.data

import com.alananasss.kittytune.domain.Track
import com.my.kizzy.rpc.KizzyRPC
import com.my.kizzy.rpc.RpcImage
import kotlinx.coroutines.CancellationException
import com.alananasss.kittytune.data.local.DiscordStatusDisplay
import com.alananasss.kittytune.data.local.PlayerPreferences

class DiscordRPC(
    token: String
) : KizzyRPC(token) {

    private val applicationId = "1473071817693331540"
    private val logoAssetId = "1473370878195794073"

    suspend fun updatePresence(
        track: Track,
        contextName: String?,
        isPlaying: Boolean,
        position: Long
    ) {
        val duration = track.durationMs ?: 0L

        val startTime: Long?
        val endTime: Long?

        if (isPlaying && duration > 0) {
            startTime = System.currentTimeMillis() - position
            endTime = startTime + duration
        } else {
            startTime = null
            endTime = null
        }

        val trackArtwork = track.fullResArtwork

        println("Presence update - playing=$isPlaying pos=$position dur=$duration start=$startTime end=$endTime")

        try {
            sendPresence(
                track = track,
                contextName = contextName,
                largeImage = if (trackArtwork.isNotEmpty() && !trackArtwork.contains("picsum")) RpcImage.ExternalImage(trackArtwork) else RpcImage.DiscordImage(logoAssetId),
                startTime = startTime,
                endTime = endTime
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            println("RPC Error: ${e.message}")
        }
    }

    private suspend fun sendPresence(
        track: Track,
        contextName: String?,
        largeImage: RpcImage?,
        startTime: Long?,
        endTime: Long?
    ) {
        val artistName = track.user?.username ?: "Unknown Artist"
        val trackTitle = track.title ?: "Unknown Title"
        val playlistInfo = contextName ?: "KittyTune"

        val rpcButtons = mutableListOf<Pair<String, String>>()
        if (!track.permalinkUrl.isNullOrEmpty() && track.permalinkUrl.startsWith("http")) {
            rpcButtons.add("Listen" to track.permalinkUrl)
        }

        val prefs = PlayerPreferences()
        val displayMode = prefs.getDiscordStatusDisplay()
        val activityName: String
        val detailsLine: String
        val stateLine: String

        when (displayMode) {
            DiscordStatusDisplay.ARTIST -> {
                activityName = artistName
                detailsLine = trackTitle
                stateLine = "on KittyTune"
            }

            DiscordStatusDisplay.SONG -> {
                activityName = trackTitle
                detailsLine = "by $artistName"
                stateLine = "on KittyTune"
            }

            DiscordStatusDisplay.ACTIVITY -> {
                activityName = "KittyTune"
                detailsLine = trackTitle
                stateLine = "by $artistName"
            }

            DiscordStatusDisplay.SOUNDCLOUD -> {
                activityName = "SoundCloud"
                detailsLine = trackTitle
                stateLine = "by $artistName"
            }
        }

        setActivity(
            applicationId = applicationId,
            name = activityName,
            details = detailsLine,
            state = stateLine,
            largeImage = largeImage,
            largeText = playlistInfo,
            smallImage = null,
            smallText = null,
            buttons = rpcButtons.takeIf { it.isNotEmpty() },
            type = Type.LISTENING,
            startTime = startTime,
            endTime = endTime
        )
    }
}
