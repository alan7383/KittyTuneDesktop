package com.alananasss.kittytune.data.discord

import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.DiscordStatusDisplay
import com.alananasss.kittytune.data.together.DiscordActivityParty
import com.alananasss.kittytune.domain.Track
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

internal enum class DiscordBrowsingKind {
    HOME,
    SEARCHING,
    CHARTS,
    FEED,
    GENRES,
    NEW_RELEASES,
    UPLOAD,
    RECOGNITION,
    STATS,
    PASSPORT,
    RECAPS,
    EQUALIZER,
    TOGETHER,
    PROFILE,
    PLAYLIST,
    HISTORY,
    SETTINGS,
    IMPORT,
    LYRICS,
}

internal data class DiscordBrowsingContext(
    val kind: DiscordBrowsingKind,
    val detail: String? = null,
)

internal fun discordActivity(
    track: Track,
    contextName: String?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    mode: DiscordStatusDisplay,
    liveInviteUrl: String? = null,
    party: DiscordActivityParty? = null,
    showCover: Boolean = true,
    showTimestamps: Boolean = true,
    showLinks: Boolean = true,
    titleParsing: Boolean = false,
    effectsSummary: String? = null,
    nowMs: Long = System.currentTimeMillis(),
): JSONObject {
    val parsed = if (titleParsing) {
        parseDiscordTrackTitle(track.title, track.displayArtist)
    } else {
        ParsedDiscordTrack(track.title ?: "", track.displayArtist)
    }
    val title = activityText(parsed.title, "Unknown Title")
    val artist = activityText(parsed.artist ?: track.displayArtist, "Unknown Artist")
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

    if (showTimestamps && isPlaying && durationMs > 0) {
        val start = (nowMs - positionMs.coerceIn(0L, durationMs)) / 1_000
        val end = start + maxOf(1L, durationMs / 1_000)
        activity.put("timestamps", JSONObject().put("start", start).put("end", end))
    }

    // Never hand private SoundCloud share links or local artwork paths to the Discord client.
    val publicTrack = !track.sharing.equals("private", ignoreCase = true) && track.secretToken.isNullOrBlank()
    val artwork = if (showCover && publicTrack) {
        track.fullResArtwork.takeIf { !it.contains("picsum.photos") }.let(::publicUrl)
    } else {
        null
    }
    val largeText = buildList {
        contextName?.trim()?.takeIf(String::isNotEmpty)?.let(::add)
        effectsSummary?.trim()?.takeIf(String::isNotEmpty)?.let(::add)
    }.joinToString(" • ").ifEmpty { "KittyTune" }

    val assets = JSONObject()
        .put("large_image", artwork ?: "logo")
        .put("large_text", activityText(largeText, "KittyTune"))

    if (!isPlaying) {
        val pausedLabel = str("discord_rpc_state_paused")
        val smallText = if (!effectsSummary.isNullOrBlank()) "$pausedLabel • $effectsSummary" else pausedLabel
        assets.put("small_image", "pause")
            .put("small_text", activityText(smallText, pausedLabel))
    } else if (!effectsSummary.isNullOrBlank()) {
        assets.put("small_image", "fx")
            .put("small_text", activityText(effectsSummary, "Audio FX"))
    }
    activity.put("assets", assets)

    val buttons = JSONArray()
    publicUrl(liveInviteUrl)?.let {
        buttons.put(JSONObject().put("label", clipText(str("discord_rpc_btn_listen_along"), 32)).put("url", it))
        party?.let { group ->
            activity.put("party", JSONObject().put("id", group.id).put("size", JSONArray(group.size)))
        }
    }
    if (showLinks && publicTrack) {
        publicUrl(track.permalinkUrl)?.let {
            buttons.put(JSONObject().put("label", "Listen").put("url", it))
        }
    }
    if (buttons.length() > 0) activity.put("buttons", buttons)
    return activity
}

internal fun discordBrowsingActivity(
    context: DiscordBrowsingContext,
    mode: DiscordStatusDisplay,
    showSearchQuery: Boolean = true,
    showTimestamps: Boolean = true,
    sessionStartSec: Long = System.currentTimeMillis() / 1_000,
): JSONObject {
    val (details, state, smallImage, smallText) = when (context.kind) {
        DiscordBrowsingKind.SEARCHING -> {
            val query = context.detail?.trim()?.takeIf { showSearchQuery && it.isNotEmpty() }
            Quad(
                str("discord_rpc_browsing_searching"),
                query ?: str("discord_rpc_browsing_searching_state"),
                "search",
                str("discord_rpc_browsing_searching"),
            )
        }
        DiscordBrowsingKind.UPLOAD -> Quad(
            str("discord_rpc_browsing_uploading"),
            str("discord_rpc_browsing_uploading_state"),
            "upload",
            str("discord_rpc_browsing_uploading"),
        )
        DiscordBrowsingKind.RECOGNITION -> Quad(
            str("discord_rpc_browsing_recognition"),
            str("discord_rpc_browsing_recognition_state"),
            "search",
            str("discord_rpc_browsing_recognition"),
        )
        DiscordBrowsingKind.CHARTS -> Quad(
            str("discord_rpc_browsing_charts"),
            context.detail?.takeIf { it.isNotBlank() } ?: str("discord_rpc_browsing_charts_state"),
            "reading",
            str("discord_rpc_browsing_label"),
        )
        DiscordBrowsingKind.FEED -> Quad(
            str("discord_rpc_browsing_feed"),
            str("discord_rpc_browsing_feed_state"),
            "reading",
            str("discord_rpc_browsing_label"),
        )
        DiscordBrowsingKind.GENRES -> Quad(
            str("discord_rpc_browsing_genres"),
            context.detail?.takeIf { it.isNotBlank() } ?: str("discord_rpc_browsing_genres_state"),
            "reading",
            str("discord_rpc_browsing_label"),
        )
        DiscordBrowsingKind.NEW_RELEASES -> Quad(
            str("discord_rpc_browsing_new_releases"),
            str("discord_rpc_browsing_new_releases_state"),
            "reading",
            str("discord_rpc_browsing_label"),
        )
        DiscordBrowsingKind.STATS -> Quad(
            str("discord_rpc_browsing_stats"),
            str("discord_rpc_browsing_stats_state"),
            "reading",
            str("discord_rpc_browsing_label"),
        )
        DiscordBrowsingKind.PASSPORT -> Quad(
            str("discord_rpc_browsing_passport"),
            str("discord_rpc_browsing_passport_state"),
            "reading",
            str("discord_rpc_browsing_label"),
        )
        DiscordBrowsingKind.RECAPS -> Quad(
            str("discord_rpc_browsing_recaps"),
            str("discord_rpc_browsing_recaps_state"),
            "reading",
            str("discord_rpc_browsing_label"),
        )
        DiscordBrowsingKind.EQUALIZER -> Quad(
            str("discord_rpc_browsing_equalizer"),
            str("discord_rpc_browsing_equalizer_state"),
            "fx",
            str("discord_rpc_browsing_equalizer"),
        )
        DiscordBrowsingKind.TOGETHER -> Quad(
            str("discord_rpc_browsing_together"),
            str("discord_rpc_browsing_together_state"),
            "reading",
            str("discord_rpc_browsing_label"),
        )
        DiscordBrowsingKind.PROFILE -> Quad(
            str("discord_rpc_browsing_profile"),
            context.detail?.takeIf { it.isNotBlank() } ?: str("discord_rpc_browsing_profile_state"),
            "reading",
            str("discord_rpc_browsing_label"),
        )
        DiscordBrowsingKind.PLAYLIST -> Quad(
            str("discord_rpc_browsing_playlist"),
            context.detail?.takeIf { it.isNotBlank() } ?: str("discord_rpc_browsing_playlist_state"),
            "reading",
            str("discord_rpc_browsing_label"),
        )
        DiscordBrowsingKind.HISTORY -> Quad(
            str("discord_rpc_browsing_history"),
            str("discord_rpc_browsing_history_state"),
            "reading",
            str("discord_rpc_browsing_label"),
        )
        DiscordBrowsingKind.SETTINGS -> Quad(
            str("discord_rpc_browsing_settings"),
            str("discord_rpc_browsing_settings_state"),
            "reading",
            str("discord_rpc_browsing_label"),
        )
        DiscordBrowsingKind.IMPORT -> Quad(
            str("discord_rpc_browsing_import"),
            str("discord_rpc_browsing_import_state"),
            "reading",
            str("discord_rpc_browsing_label"),
        )
        DiscordBrowsingKind.LYRICS -> Quad(
            str("discord_rpc_browsing_lyrics"),
            context.detail?.takeIf { it.isNotBlank() } ?: str("discord_rpc_browsing_lyrics_state"),
            "reading",
            str("discord_rpc_browsing_lyrics"),
        )
        DiscordBrowsingKind.HOME -> Quad(
            str("discord_rpc_browsing_home"),
            str("discord_rpc_browsing_home_state"),
            "reading",
            str("discord_rpc_browsing_label"),
        )
    }

    val activity = JSONObject()
        .put("type", 2) // Listening
        .put("name", if (mode == DiscordStatusDisplay.SOUNDCLOUD) "SoundCloud" else "KittyTune")
        .put("status_display_type", 0)
        .put("details", activityText(details, "Browsing"))
        .put("state", activityText(state, "KittyTune"))
        .put(
            "assets",
            JSONObject()
                .put("large_image", "logo")
                .put("large_text", "KittyTune")
                .put("small_image", smallImage)
                .put("small_text", activityText(smallText, "Browsing")),
        )

    if (showTimestamps && sessionStartSec > 0) {
        activity.put("timestamps", JSONObject().put("start", sessionStartSec))
    }
    return activity
}

private data class Quad(
    val details: String,
    val state: String,
    val smallImage: String,
    val smallText: String,
)

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
