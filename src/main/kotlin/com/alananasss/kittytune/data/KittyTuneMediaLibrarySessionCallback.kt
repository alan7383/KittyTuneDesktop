package com.alananasss.kittytune.data

/**
 * Desktop stub of the Android media-session callback. On desktop there is no
 * MediaLibraryService/Android Auto browse tree; we only keep the mediaId prefix
 * constants that PlayerViewModel references when parsing/building media ids.
 */
object KittyTuneMediaLibrarySessionCallback {
    const val TRACK_PREFIX = "track:"
    const val CONTEXT_SEPARATOR = ":context:"
}
