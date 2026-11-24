package com.alananasss.kittytune.data.discord

import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.DiscordStatusDisplay
import com.alananasss.kittytune.data.together.DiscordActivityParty
import com.alananasss.kittytune.domain.Track
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

internal fun discordActivity(
    track: Track,
    contextName: String?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    mode: DiscordStatusDisplay,
    liveInviteUrl: String? = null,
    party: DiscordActivityParty? = null,
    nowMs: Long = System.currentTimeMillis(),
): JSONObject {
    val title = activityText(track.title, "Unknown Title")
    val artist = activityText(track.displayArtist, "Unknown Artist")
    val statusDisplay = when (mode) {
        DiscordStatusDisplay.ARTIST -> 1 // State
        DiscordStatusDisplay.SONG -> 2 // Details
        else -> 0 // Application name
    }
    val activity = JSONObject()
        .put("type", 2) // Listening
        .put("name", if (mode == DiscordStatusDisplay.SOUNDCLOUD) "SoundCloud" else "KittyTune")
        .put("status_display_type", statusDisplay)
        .put("details", title)
        .put("state", if (mode == DiscordStatusDisplay.ARTIST) artist else activityText("by $artist", artist))

    if (isPlaying && durationMs > 0) {
        val start = (nowMs - positionMs.coerceIn(0L, durationMs)) / 1_000
        val end = start + maxOf(1L, durationMs / 1_000)
        activity.put("timestamps", JSONObject().put("start", start).put("end", end))
    }

    // Never hand private SoundCloud share links or local artwork paths to the Discord client.
    val publicTrack = !track.sharing.equals("private", ignoreCase = true) && track.secretToken.isNullOrBlank()
    val artwork = track.fullResArtwork.takeIf { publicTrack && !it.contains("picsum.photos") }
        .let(::publicUrl)
    activity.put("assets", JSONObject().put("large_image", artwork ?: "logo")
        .put("large_text", activityText(contextName, "KittyTune")))

    val buttons = JSONArray()
    publicUrl(liveInviteUrl)?.let {
        buttons.put(JSONObject().put("label", clipText(str("discord_rpc_btn_listen_along"), 32)).put("url", it))
        party?.let { group ->
            activity.put("party", JSONObject().put("id", group.id).put("size", JSONArray(group.size)))
        }
    }
    if (publicTrack) publicUrl(track.permalinkUrl)?.let {
        buttons.put(JSONObject().put("label", "Listen").put("url", it))
    }
    if (buttons.length() > 0) activity.put("buttons", buttons)
    return activity
}

private fun activityText(text: String?, fallback: String): String =
    clipText(text?.trim()?.takeIf(String::isNotEmpty) ?: fallback, 128)

private fun clipText(text: String, max: Int): String = if (text.length <= max) {
    text
} else {
    text.substring(0, if (text[max - 1].isHighSurrogate()) max - 1 else max)
}

private fun publicUrl(text: String?): String? = text?.toHttpUrlOrNull()
    ?.takeIf { it.username.isEmpty() && it.password.isEmpty() }
    ?.toString()?.takeIf { it.length <= 512 }
