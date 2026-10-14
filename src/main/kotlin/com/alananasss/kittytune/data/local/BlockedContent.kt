package com.alananasss.kittytune.data.local

/**
 * Desktop port of the Android `BlockedContent` Room entity (issue #41).
 *
 * Exactly one of [trackId] / [artistId] is non-null per row.
 * [reason] is "MANUAL" (user chose) or "AI_GENERATED" (ArtifactNet auto-block).
 */
data class BlockedContent(
    val id: Long = 0,
    val trackId: Long? = null,
    val trackTitle: String? = null,
    val trackArtworkUrl: String? = null,
    val artistId: Long? = null,
    val artistName: String? = null,
    val artistAvatarUrl: String? = null,
    /** Source platform: "soundcloud", "youtube", "spotify", … */
    val source: String = "soundcloud",
    /** "MANUAL" or "AI_GENERATED" */
    val reason: String = "MANUAL",
    val blockedAt: Long = System.currentTimeMillis(),
)
