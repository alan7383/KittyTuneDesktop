package com.alananasss.kittytune.data.drm


import com.alananasss.kittytune.data.local.PlayerPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one notice about protected tracks the player itself may raise, for the main screen to show.
 *
 * Raised from stream resolution, at the moment it matters: a track SoundCloud only serves protected was played
 * from another source, or could not be played at all.
 */
object ProtectedTracksNotice {

    enum class Kind {
        /** Protected tracks are off (Windows' default) and this one went to another source: offer them, once. */
        OFFER_ENABLE,

        /** Protected tracks are on but nothing can play them here (no CDM installed or answering). */
        UNAVAILABLE,
    }

    /**
     * The announcement this release owes, also a version.
     *
     * Bumping it shows the card and the floating offer again: the method behind protected tracks
     * changed completely in this release, and a flag that can only turn true would leave everyone who
     * dismissed the previous one without ever hearing about it.
     */
    const val CURRENT = 2

    /** [id] tells two notices of the same kind apart, so the host restarts its timer for a new one. */
    data class Notice(val kind: Kind, val id: Long = System.nanoTime())

    private val _current = MutableStateFlow<Notice?>(null)
    val current: StateFlow<Notice?> = _current.asStateFlow()

    fun dismiss() {
        _current.value = null
    }

    /**
     * A protected-only track was resolved without a CDM that can play it. Offered once, on Windows,
     * and only to someone who never set the switch — turning it off in the settings is a choice, and
     * a choice needs no notice.
     *
     * One exception, and it is the common case after this version: someone who *had* protected tracks
     * on now has none, because the module has to be installed once now instead of PlayReady playing
     * them. Nothing would tell them otherwise: their choice is already stored, and the track would
     * simply fall back for ever.
     */
    internal fun onProtectedTrackSkipped() {
        if (!NativeCdmSetup.windows) return
        val prefs = PlayerPreferences()
        if (prefs.getProtectedHintVersion() >= CURRENT) return
        // Someone who turned the switch off made a choice, and a choice needs no notice. Someone who
        // turned it on but has no module any more needs one: their choice is stored, the track would
        // simply fall back for ever, and nothing else would tell them.
        val stranded = prefs.getProtectedTracksEnabled() && !NativeCdmSetup.isInstalled()
        if (prefs.hasProtectedTracksChoice() && !stranded) return
        prefs.setProtectedHintVersion(CURRENT)
        _current.value = Notice(Kind.OFFER_ENABLE)
    }

    /** Protected tracks are on, and this one has nothing that can play it. Rate-limited by the caller. */
    internal fun onProtectedTrackUnavailable() {
        _current.value = Notice(Kind.UNAVAILABLE)
    }

    /** Whether every stream SoundCloud offers for a track is an encrypted one. */
    internal fun onlyProtected(transcodings: List<com.alananasss.kittytune.domain.Transcoding>): Boolean =
        transcodings.isNotEmpty() && transcodings.all {
            it.format?.protocol?.contains("encrypted", ignoreCase = true) == true
        }
}
