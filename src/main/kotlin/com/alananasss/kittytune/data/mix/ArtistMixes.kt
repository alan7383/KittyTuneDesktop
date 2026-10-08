package com.alananasss.kittytune.data.mix

import com.alananasss.kittytune.data.lyrics.GeniusVoices
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The mixes an artist's page offers (issue #66): the best of each artist the profile is (a duo's page gets one per
 * member), one in their style, and their rare tracks. Each is built when it is pressed.
 */
object ArtistMixes {

    private val api by lazy { RetrofitClient.create() }

    /** The people a profile name stands for: "Kai Angel & 9mice" is two, "9mice" is one. */
    fun membersOf(profileName: String?): List<String> =
        GeniusVoices.splitNames(profileName.orEmpty()).filter { it.isNotBlank() }.distinct()

    /**
     * [member]'s most played songs, theirs by credit, wherever they were uploaded: the profile of a duo holds the
     * duo's songs, and one member's best are mostly elsewhere.
     */
    suspend fun bestOf(member: String, limit: Int = 40): List<Track> = withContext(Dispatchers.IO) {
        val key = member.trim().lowercase()
        runCatching { api.searchTracksPop(member, limit = 100).collection }.getOrDefault(emptyList())
            .filter { track ->
                val credit = track.displayArtist.ifBlank { track.user?.username.orEmpty() }.lowercase()
                credit.contains(key) || track.user?.username?.lowercase() == key
            }
            .filter { (it.durationMs ?: 0L) >= 60_000L }
            .distinctBy { (it.title.orEmpty().lowercase()) }
            .sortedByDescending { it.playbackCount }
            .take(limit)
    }

    /** Songs in [artistName]'s style: their neighbourhood, from the mix engine. */
    suspend fun inTheStyleOf(artistId: Long?, artistName: String): List<Track> =
        when (val result = MixEngine.mix(MixEngine.Recipe.LikeArtist(artistId, artistName), size = 60)) {
            is MixEngine.Result.Mixed -> result.tracks
            else -> emptyList()
        }

    /** The artist's least played songs among the ones they released, the ones a fan has not heard yet. */
    fun rareTracks(all: List<Track>): List<Track> =
        all.filter { (it.durationMs ?: 0L) >= 60_000L }
            .sortedBy { it.playbackCount }
            .take(40)
            .shuffled()
}
