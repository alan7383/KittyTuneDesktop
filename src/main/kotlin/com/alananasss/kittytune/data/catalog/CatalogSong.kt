package com.alananasss.kittytune.data.catalog

/**
 * A song some catalogue knows about but nothing here can play (issue #33).
 *
 * ## Why the two metadata sources share one type
 *
 * Apple Music and Yandex Music arrived a day apart and for the same reason — "many artists don't upload their
 * music to soundcloud, youtube, spotify" — and they are the same shape of thing: a catalogue we can read and
 * cannot stream from. Apple's audio is DRM-locked; Yandex's needs the listener's own account and a
 * subscription, and reaching it means reproducing a signing scheme out of their client, which is not
 * something to do.
 *
 * So neither of them produces a [com.alananasss.kittytune.domain.Track]. A Track is something the player can
 * be handed, and keeping the types apart is what stops a result that cannot play reaching the queue. What both
 * produce is this, and pressing one asks [CatalogFallback] whether the same song exists somewhere that streams.
 *
 * One type rather than two also means one results list and one resolve path in the UI. The alternative was
 * writing the whole of the Apple search a second time with the word Yandex in it, which is the mistake this
 * issue has already cost three rounds of in the sidebar.
 */
data class CatalogSong(
    val source: CatalogSource,
    /** Unique within [source]; the two never share an id space, so the list keys on both. */
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val artworkUrl: String?,
    val releaseDate: String?,
) {
    /** Stable across sources, for a list key. */
    val key: String get() = "${source.name}:$id"
}

/** Where a [CatalogSong] was read from. Not a playback source — none of these can play anything. */
enum class CatalogSource {
    APPLE_MUSIC,
    YANDEX_MUSIC,
}
