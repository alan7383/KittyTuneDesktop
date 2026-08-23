package com.alananasss.kittytune.data.network

import com.alananasss.kittytune.data.SessionManager
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.player.PlaybackContext
import com.alananasss.kittytune.utils.Config
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * SoundCloud Telemetry & Event Logger Tracker for Desktop.
 *
 * Reproduces the exact play tracking, server-side view counting, and listening history
 * synchronization architecture used by SoundCloud.
 *
 * In the official SoundCloud app:
 * - Single track view counts and user listening history (GET /recently-played/tracks) are
 *   recorded server-side via telemetry events sent to https://telemetry.soundcloud.com/v1/events (backend "boogaloo").
 * - Whenever a track plays, the app dispatches "audio" events (play_start, play_checkpoint, play_stop, play_resume)
 *   with Authorization: OAuth <token> and user: "soundcloud:users:<id>".
 * - Container contexts (playlists, stations, users) are synced via POST /recently-played/contexts/v2.
 */
object SoundCloudTelemetryTracker {
    private const val TAG = "SCTelemetryTracker"
    private const val TELEMETRY_URL = "https://telemetry.soundcloud.com/v1/events"
    private const val EVENTS_API_FALLBACK_URL = "https://events-api.soundcloud.com/v1/events"
    private const val BOOGALOO_VERSION = "v1.27.45"
    private const val APP_VERSION = "2025.12.10-release"
    private const val APP_ID = 459 // SoundCloud Android official integer app/client id

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()

    private val playerPrefs = com.alananasss.kittytune.data.local.PlayerPreferences()
    private val directHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    // Session-level IDs
    private val sessionId: String = UUID.randomUUID().toString()

    // Current Track Playback Session State
    @Volatile private var currentTrack: Track? = null
    @Volatile private var currentContext: PlaybackContext? = null
    @Volatile private var currentPlayId: String? = null
    @Volatile private var currentQueueId: String = UUID.randomUUID().toString()
    @Volatile private var isUserTriggered: Boolean = true
    @Volatile private var isHls: Boolean = true
    @Volatile private var currentPreset: String = "mp3_128k"
    @Volatile private var lastCheckpointPositionMs: Long = 0L
    @Volatile private var hasSentStartForCurrentSession: Boolean = false
    @Volatile private var hasSentFiveSecThreshold: Boolean = false
    @Volatile private var currentUserId: Long = 0L

    private fun isSyncEnabled(): Boolean {
        return playerPrefs.getSoundCloudHistorySyncEnabled()
    }

    fun updateCurrentUserId(userId: Long) {
        if (userId > 0L) {
            currentUserId = userId
        }
    }

    fun onQueueReset() {
        currentQueueId = UUID.randomUUID().toString()
    }

    /**
     * Called when a track begins loading / starts playback.
     */
    fun onTrackStarted(
        track: Track,
        context: PlaybackContext?,
        isManual: Boolean = true,
        startPositionMs: Long = 0L,
        isHlsStream: Boolean = true,
        preset: String? = null
    ) {
        if (!isSyncEnabled()) {
            return
        }

        if (track.source != null && track.source != "soundcloud") {
            return
        }

        // If a previous track was active and wasn't cleanly stopped, send play_stop with "skip"
        val prevTrack = currentTrack
        val prevPlayId = currentPlayId
        if (prevTrack != null && prevPlayId != null && prevTrack.id != track.id && hasSentStartForCurrentSession) {
            sendAudioEvent(
                track = prevTrack,
                action = "play_stop",
                positionMs = lastCheckpointPositionMs,
                playId = prevPlayId,
                stopReason = "skip"
            )
        }

        currentTrack = track
        currentContext = context
        isUserTriggered = isManual
        isHls = isHlsStream
        currentPreset = preset ?: "mp3_128k"
        currentPlayId = UUID.randomUUID().toString()
        lastCheckpointPositionMs = startPositionMs
        hasSentStartForCurrentSession = true
        hasSentFiveSecThreshold = false

        // 1. Send play_start audio telemetry event to telemetry.soundcloud.com
        sendAudioEvent(
            track = track,
            action = "play_start",
            positionMs = startPositionMs,
            playId = currentPlayId!!
        )

        // 2. If the track is being played within a container context (playlist, station, user), sync context
        syncContextRecentlyPlayed(context)
    }

    /**
     * Called during playback progress ticker (e.g. every second).
     */
    fun onProgressUpdate(currentPositionMs: Long) {
        if (!isSyncEnabled()) return
        val track = currentTrack ?: return
        val playId = currentPlayId ?: return

        // If user seeked backwards, adapt baseline
        if (currentPositionMs < lastCheckpointPositionMs) {
            lastCheckpointPositionMs = currentPositionMs
        }

        // 5-second listen threshold (where SoundCloud marks initial play confirmation)
        if (currentPositionMs >= 5000L && !hasSentFiveSecThreshold) {
            hasSentFiveSecThreshold = true
        }

        // 30-second checkpoint interval (standard SoundCloud playback checkpoint)
        if (currentPositionMs - lastCheckpointPositionMs >= 30_000L) {
            lastCheckpointPositionMs = currentPositionMs
            sendAudioEvent(
                track = track,
                action = "play_checkpoint",
                positionMs = currentPositionMs,
                playId = playId
            )
        }
    }

    /**
     * Called when user seeks within the current track.
     */
    fun onTrackSeeked(newPositionMs: Long) {
        lastCheckpointPositionMs = newPositionMs
    }

    /**
     * Called when user pauses playback.
     */
    fun onTrackPaused(currentPositionMs: Long) {
        val track = currentTrack ?: return
        val playId = currentPlayId ?: return
        if (!hasSentStartForCurrentSession) return

        lastCheckpointPositionMs = currentPositionMs
        sendAudioEvent(
            track = track,
            action = "play_stop",
            positionMs = currentPositionMs,
            playId = playId,
            stopReason = "pause"
        )
    }

    /**
     * Called when user resumes playback from pause.
     */
    fun onTrackResumed(currentPositionMs: Long) {
        val track = currentTrack ?: return
        val playId = currentPlayId ?: return

        lastCheckpointPositionMs = currentPositionMs
        sendAudioEvent(
            track = track,
            action = "play_resume",
            positionMs = currentPositionMs,
            playId = playId
        )
    }

    /**
     * Called when user manually skips or changes track.
     */
    fun onTrackStopped(currentPositionMs: Long, reason: String = "skip") {
        val track = currentTrack ?: return
        val playId = currentPlayId ?: return
        if (!hasSentStartForCurrentSession) return

        lastCheckpointPositionMs = currentPositionMs
        sendAudioEvent(
            track = track,
            action = "play_stop",
            positionMs = currentPositionMs,
            playId = playId,
            stopReason = reason
        )
        currentTrack = null
        currentPlayId = null
        hasSentStartForCurrentSession = false
    }

    /**
     * Called when track completes naturally (STATE_ENDED).
     */
    fun onTrackCompleted() {
        val track = currentTrack ?: return
        val playId = currentPlayId ?: return
        if (!hasSentStartForCurrentSession) return

        val duration = (track.durationMs ?: 0L).coerceAtLeast(1000L)
        sendAudioEvent(
            track = track,
            action = "play_stop",
            positionMs = duration,
            playId = playId,
            stopReason = "track_finished"
        )
        currentTrack = null
        currentPlayId = null
        hasSentStartForCurrentSession = false
    }

    /**
     * Constructs and sends an audio telemetry event to SoundCloud.
     */
    private fun sendAudioEvent(
        track: Track,
        action: String,
        positionMs: Long,
        playId: String,
        stopReason: String? = null
    ) {
        scope.launch {
            try {
                if (!isSyncEnabled()) return@launch
                val isGuest = TokenManager.isGuestMode()
                val token = if (!isGuest) {
                    SessionManager.harvestStoredSession() ?: TokenManager.getAccessToken()
                } else null

                val deviceId = Config.getOrCreateSoundCloudDeviceId()
                val now = System.currentTimeMillis()
                val trackDurationMs = (track.durationMs ?: 0L).coerceAtLeast(1000L)
                val pageName = resolvePageName(currentContext)
                val contextUrn = resolveContextUrn(currentContext, currentUserId)

                // Build payload dictionary adhering to DataBuilderV1 schema (na0/h.java)
                val payload = mutableMapOf<String, Any?>()
                payload["client_id"] = APP_ID
                payload["session_id"] = sessionId
                payload["anonymous_id"] = deviceId
                payload["ts"] = now
                payload["connection_type"] = "wifi"
                payload["app_version"] = APP_VERSION
                payload["action"] = action
                payload["page_name"] = pageName
                payload["playhead_position"] = positionMs.coerceAtLeast(0L)
                payload["track_length"] = trackDurationMs
                payload["track"] = "soundcloud:tracks:${track.id}"
                payload["track_owner"] = "soundcloud:users:${track.user?.id ?: 0L}"
                payload["client_event_id"] = UUID.randomUUID().toString()
                payload["is_local_storage_playback"] = false
                payload["consumer_subs_plan"] = "free"
                payload["trigger"] = if (isUserTriggered) "manual" else "auto"
                payload["player_type"] = if (isHls) "exo_hls" else "exo_progressive"
                payload["audio_port"] = "speaker"
                payload["play_id"] = playId
                payload["protocol"] = if (isHls) "hls" else "progressive"
                payload["app_state"] = "foreground"
                payload["preset"] = currentPreset
                payload["quality"] = "standard"
                payload["policy"] = track.policy ?: "ALLOW"
                payload["monetization_model"] = track.monetizationModel ?: "free"
                payload["queue_id"] = currentQueueId

                if (!contextUrn.isNullOrEmpty()) {
                    payload["queue_source_id"] = contextUrn
                    payload["page_urn"] = contextUrn
                }

                if (!isGuest && currentUserId > 0L) {
                    payload["user"] = "soundcloud:users:$currentUserId"
                }

                if (action == "play_stop" && !stopReason.isNullOrEmpty()) {
                    payload["reason"] = stopReason
                }

                val eventObject = mapOf(
                    "event" to "audio",
                    "version" to BOOGALOO_VERSION,
                    "event-id" to UUID.randomUUID().toString(),
                    "payload" to payload
                )

                val sentAtFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }

                val batchObject = mapOf(
                    "events" to listOf(eventObject),
                    "sent_at" to sentAtFormat.format(Date(now))
                )

                val jsonBody = gson.toJson(batchObject)
                println("[SoundCloudTelemetryTracker] Dispatching $action event for track ${track.id} (pos: ${positionMs}ms, playId: $playId)")
                postTelemetryBatch(jsonBody, token, action, track.id)

            } catch (e: Exception) {
                println("[SoundCloudTelemetryTracker] Error building/sending telemetry event: ${e.message}")
            }
        }
    }

    /**
     * HTTP POST to telemetry.soundcloud.com/v1/events with OAuth token and SoundCloud mobile User-Agent.
     */
    private fun postTelemetryBatch(jsonPayload: String, token: String?, action: String, trackId: Long) {
        val client = directHttpClient

        val buildVersion = "2025.12.10-release"
        val userAgent = "SoundCloud/$buildVersion (Android 14; Pixel 8 Pro)"

        val requestBuilder = Request.Builder()
            .url(TELEMETRY_URL)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json; charset=utf-8")
            .post(jsonPayload.toRequestBody(JSON_MEDIA_TYPE))

        if (!token.isNullOrEmpty() && token != "null") {
            requestBuilder.header("Authorization", "OAuth $token")
        }

        try {
            val response = client.newCall(requestBuilder.build()).execute()
            val code = response.code
            val body = response.body?.string()
            response.close()

            if (code in 200..299) {
                println("[SoundCloudTelemetryTracker] Telemetry event '$action' for track $trackId sent successfully (code $code)")
            } else if (code == 404 || code >= 500) {
                println("[SoundCloudTelemetryTracker] Primary telemetry failed ($code), trying fallback endpoint...")
                val fallbackRequest = requestBuilder.url(EVENTS_API_FALLBACK_URL).build()
                val fallbackResponse = client.newCall(fallbackRequest).execute()
                println("[SoundCloudTelemetryTracker] Fallback telemetry response: ${fallbackResponse.code}")
                fallbackResponse.close()
            } else {
                println("[SoundCloudTelemetryTracker] Telemetry event '$action' returned $code: $body")
            }
        } catch (e: Exception) {
            println("[SoundCloudTelemetryTracker] Failed to send telemetry request: ${e.message}")
        }
    }

    /**
     * Syncs container context (playlist, station, user) to SoundCloud's /recently-played/contexts/v2.
     */
    private fun syncContextRecentlyPlayed(context: PlaybackContext?) {
        if (!isSyncEnabled()) return
        if (TokenManager.isGuestMode()) return

        val contextUrn = resolveContextUrn(context, currentUserId) ?: return

        scope.launch {
            try {
                val api = RetrofitClient.create()
                val now = System.currentTimeMillis()
                val entry = ApiRecentlyPlayed(playedAt = now, urn = contextUrn)
                val collection = ApiCollection(collection = listOf(entry))

                val response = api.pushRecentlyPlayed(collection)
                if (response.isSuccessful) {
                    println("[SoundCloudTelemetryTracker] Synced recently played context: $contextUrn")
                } else {
                    println("[SoundCloudTelemetryTracker] Failed to sync context: ${response.code()}")
                }
            } catch (e: Exception) {
                println("[SoundCloudTelemetryTracker] Error syncing context $contextUrn: ${e.message}")
            }
        }
    }

    private fun resolvePageName(context: PlaybackContext?): String {
        val navId = context?.navigationId ?: return "player:player"
        return when {
            navId.startsWith("search") -> "search:search"
            navId == "likes" -> "collection:likes"
            navId == "history" -> "collection:history"
            navId.startsWith("station:") || navId.startsWith("station_artist:") -> "station:station"
            navId.startsWith("profile:") -> "users:tracks"
            navId == "stream" -> "stream"
            navId == "discovery" -> "discovery"
            else -> "playlist:playlist"
        }
    }

    private fun resolveContextUrn(context: PlaybackContext?, userId: Long): String? {
        val navId = context?.navigationId ?: return null
        return when {
            navId == "likes" -> {
                if (userId > 0L) "soundcloud:liked-tracks:$userId" else null
            }
            navId.startsWith("station:") -> {
                val id = navId.removePrefix("station:")
                if (id.toLongOrNull() != null) "soundcloud:system-playlists:track-stations:$id" else null
            }
            navId.startsWith("station_artist:") -> {
                val id = navId.removePrefix("station_artist:")
                if (id.toLongOrNull() != null) "soundcloud:system-playlists:artist-stations:$id" else null
            }
            navId.startsWith("profile:") -> {
                val id = navId.removePrefix("profile:")
                if (id.toLongOrNull() != null) "soundcloud:users:$id" else null
            }
            navId == "downloads" || navId.startsWith("local_playlist:") || navId.startsWith("yt_radio:") -> {
                null
            }
            else -> {
                val playlistId = navId.toLongOrNull()
                if (playlistId != null && playlistId > 0L) "soundcloud:playlists:$playlistId" else null
            }
        }
    }
}
