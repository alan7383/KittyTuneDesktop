package com.alananasss.kittytune.domain

import kotlin.math.ln

/**
 * Intelligent ranking algorithm for selecting the Top Match ("Meilleur résultat") artist.
 *
 * Rather than naively taking the first result returned by the search API, this calculates
 * a multi-criteria composite score evaluating:
 * 1. Text match relevance (exact name match, normalized match, word boundaries, prefix)
 * 2. Official verification badge (Certified / Pro status)
 * 3. Audience popularity & follower count (logarithmic scale)
 * 4. Catalog volume (track count)
 * 5. Search engine prior index bias (tie breaker)
 */
object TopResultRanker {

    /**
     * Calculates the composite relevance score for an artist given the search query.
     */
    fun scoreArtist(user: User, query: String, index: Int = 0): Double {
        val name = user.username?.trim() ?: ""
        val q = query.trim()

        if (name.isEmpty() || q.isEmpty()) return 0.0

        var score = 0.0

        // 1. Textual match precision
        val cleanName = name.replace(Regex("[^\\p{L}\\p{Nd}]"), "").lowercase()
        val cleanQuery = q.replace(Regex("[^\\p{L}\\p{Nd}]"), "").lowercase()

        when {
            name.equals(q, ignoreCase = true) -> score += 1200.0 // Exact string match
            cleanName.isNotEmpty() && cleanName == cleanQuery -> score += 1100.0 // Exact normalized match (without punctuation/symbols)
            name.startsWith(q, ignoreCase = true) -> score += 750.0 // Starts with query
            name.split(" ", "-", "_").any { it.equals(q, ignoreCase = true) } -> score += 650.0 // Whole word match
            name.contains(q, ignoreCase = true) -> score += 350.0 // Substring match
            else -> score += 100.0
        }

        // 2. Official verified certification badge (Strong authenticity signal)
        if (user.verified) {
            score += 900.0
        }

        // 3. Audience popularity (Logarithmic follower count curve)
        val followers = user.followersCount
        if (followers > 0) {
            // Examples: 1k -> ~550 pts, 10k -> ~740 pts, 100k -> ~920 pts, 1M -> ~1100 pts, 10M -> ~1280 pts
            val followerScore = ln(followers.toDouble() + 1.0) * 80.0
            score += followerScore.coerceAtMost(1400.0)
        }

        // 4. Catalog volume
        val tracks = user.trackCount
        if (tracks > 0) {
            score += (tracks * 3.0).coerceAtMost(250.0)
        }

        // 5. Original search engine index rank bias (slight priority to top search positions)
        score += (200.0 - (index * 40.0)).coerceAtLeast(0.0)

        return score
    }

    /**
     * Finds the most relevant and popular top match artist from the search results.
     */
    fun findTopArtist(artists: List<User>, query: String): User? {
        if (artists.isEmpty()) return null
        return artists.mapIndexed { index, user -> user to scoreArtist(user, query, index) }
            .maxByOrNull { it.second }
            ?.first
    }
}
