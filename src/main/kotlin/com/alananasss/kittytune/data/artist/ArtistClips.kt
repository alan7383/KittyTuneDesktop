package com.alananasss.kittytune.data.artist

import com.alananasss.kittytune.data.StreamResolver
import com.alananasss.kittytune.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** A music video on YouTube. */
data class Clip(
    val url: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String?,
    val durationSec: Long,
    val viewCount: Long,
)

/**
 * An artist's music videos from YouTube, for watching on their page without leaving the app (tester's list, 5.5).
 *
 * A search for the artist's videos returns everyone who sings with them, covers them or reacts to them, so only
 * videos the artist uploaded, or whose title starts with their name, are kept. Lyric videos, audio uploads and
 * teasers are left out: they are not clips.
 */
object ArtistClips {

    private const val MAX_CLIPS = 10
    private const val ENOUGH_CLIPS = 3
    private const val MIN_CLIP_SEC = 90L
    private const val MAX_CLIP_SEC = 15 * 60L
    private const val CACHE_SIZE = 32

    /** Highest muxed resolution asked for; YouTube rarely offers more than 360p with sound in one file. */
    private const val MAX_HEIGHT = 720

    private val cache = object : LinkedHashMap<String, List<Clip>>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Clip>>) = size > CACHE_SIZE
    }

    suspend fun clipsFor(artistName: String): List<Clip> {
        val key = normalize(artistName)
        if (key.isEmpty()) return emptyList()
        synchronized(cache) { cache[key] }?.let { return it }
        val clips = withContext(Dispatchers.IO) {
            runCatching {
                StreamResolver.init()
                val found = search("$artistName official music video")
                val clips = pickClips(artistName, found)
                // Artists who sing in Russian rarely title anything "official music video"; their clips are "клип".
                if (clips.size >= ENOUGH_CLIPS) clips else pickClips(artistName, found + search("$artistName клип"))
            }.onFailure { Logger.w("ArtistClips", "Clip search failed for $artistName: ${it.message}") }.getOrNull()
        } ?: return emptyList()
        synchronized(cache) { cache[key] = clips }
        return clips
    }

    private fun search(query: String): List<Clip> {
        val youtube = ServiceList.YouTube
        val handler = youtube.searchQHFactory.fromQuery(query, listOf("videos"), "")
        return SearchInfo.getInfo(youtube, handler).relatedItems.filterIsInstance<StreamInfoItem>().map { item ->
            Clip(
                url = item.url,
                title = item.name,
                uploader = item.uploaderName.orEmpty(),
                thumbnailUrl = item.thumbnails.maxByOrNull { it.height }?.url,
                durationSec = item.duration,
                viewCount = item.viewCount,
            )
        }
    }

    /** A playable address for [clip]: one file with picture and sound together. Expires after a few hours. */
    suspend fun streamUrl(clip: Clip): String? = withContext(Dispatchers.IO) {
        runCatching {
            StreamResolver.init()
            val extractor = ServiceList.YouTube.getStreamExtractor(clip.url)
            extractor.fetchPage()
            extractor.videoStreams
                .filter { it.url != null && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                .map { it to (it.resolution.substringBefore('p').toIntOrNull() ?: 0) }
                .filter { (_, height) -> height <= MAX_HEIGHT }
                .maxByOrNull { (_, height) -> height }
                ?.first?.url
        }.onFailure { Logger.w("ArtistClips", "No stream for ${clip.url}: ${it.message}") }.getOrNull()
    }

    internal fun pickClips(artistName: String, found: List<Clip>): List<Clip> {
        val artist = normalize(artistName)
        val seenSongs = mutableSetOf<String>()
        val seenUrls = mutableSetOf<String>()
        return found.filter { clip ->
            val title = clip.title.lowercase()
            // "Artist - Topic" is YouTube's own channel of audio uploads over a still cover, never a clip.
            val isArtists = !TOPIC_CHANNEL.containsMatchIn(clip.uploader) &&
                (isArtistsChannel(artist, clip.uploader) || normalize(clip.title.substringBefore(" - ")) == artist)
            isArtists &&
                clip.durationSec in MIN_CLIP_SEC..MAX_CLIP_SEC &&
                NOT_CLIPS.none { it in title } &&
                seenUrls.add(clip.url) &&
                seenSongs.add(songOf(clip.title))
        }.take(MAX_CLIPS)
    }

    private fun isArtistsChannel(artist: String, uploader: String): Boolean {
        val channel = normalize(uploader)
        return channel == artist || channel == artist + "vevo" || channel == artist + "official"
    }

    /** The song a video is of, so two versions of one video count once. */
    private fun songOf(title: String): String =
        normalize(title.substringAfter(" - ").replace(BRACKETS, "").split(FEATURING).first())

    private fun normalize(text: String) = text.lowercase().filter { it.isLetterOrDigit() }

    private val NOT_CLIPS = listOf("lyric", "official audio", "(audio)", "[audio]", "visualizer", "teaser", "#shorts", "текст")
    private val FEATURING = Regex("""(?i)\s(ft\.?|feat\.?|featuring)\s""")
    private val BRACKETS = Regex("""\(.*?\)|\[.*?]""")
    private val TOPIC_CHANNEL = Regex("""(?i)\s-\stopic$""")
}
