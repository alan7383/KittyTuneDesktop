package com.alananasss.kittytune.data

import com.alananasss.kittytune.ui.player.lyrics.LyricLine
import java.text.Normalizer

/**
 * Decides how well a lyrics-provider result matches the track being played.
 *
 * The problem this exists for is SoundCloud (issue #33). A track there is whatever the uploader
 * typed: the field the app treats as the artist is really the account that posted the file, so a
 * re-upload of a well known song carries a completely unrelated "artist", and the title is padded
 * with `(Official Video)`, `[FREE DL]`, `prod. by …` and similar. Matching on title *and* artist
 * together, then discarding anything whose duration is not within fifteen seconds, threw away the
 * correct lyrics for exactly those tracks — the user had to delete part of the artist name by hand
 * before anything was found.
 *
 * So the title and the artist are scored separately and the duration only nudges the ranking:
 * a confident title match is enough on its own, and an artist match reinforces it rather than
 * gating it.
 */
object LyricsMatcher {

    /** What we are looking for: the track as the app knows it. */
    data class Target(val title: String, val artist: String, val durationMs: Long)

    /** Word-level timings. */
    const val SYNC_TIER_WORD = 3

    /** Line-level timings. */
    const val SYNC_TIER_LINE = 2

    /** The words, with no usable timings. */
    const val SYNC_TIER_PLAIN = 1

    /** Nothing worth showing. */
    const val SYNC_TIER_NONE = 0

    /**
     * How much timing a provider result actually carries — the first thing results are ranked on,
     * ahead of which provider they came from.
     *
     * Lines only count as synced when their timings advance. A provider that has the words but no
     * timings can still answer with a whole list of lines — a Musixmatch subtitle whose entries
     * carry no time, an LRC where every stamp is `[00:00.00]` — and judging by line count alone let
     * that outrank a genuinely synced result from the other provider. That is the "switches to the
     * version without synchronisation even though a synchronised one exists" report in issue #33.
     */
    fun syncTier(lines: List<LyricLine>, plain: String?): Int {
        val timingsAdvance = lines.size > 1 && lines.distinctBy { it.startTime }.size > 1
        return when {
            !timingsAdvance ->
                if (!plain.isNullOrBlank() || lines.isNotEmpty()) SYNC_TIER_PLAIN else SYNC_TIER_NONE
            lines.any { it.words.isNotEmpty() } -> SYNC_TIER_WORD
            else -> SYNC_TIER_LINE
        }
    }

    /**
     * Above this, a candidate's title and artist agree with the track well enough that it is believed
     * over a rival that merely carries better timings. Below it, the candidate is plausible and no more.
     *
     * The same threshold [isAcceptable] uses for a title that passes on its own, and for the same
     * reason: it is the point at which the words are about this song rather than about a song with some
     * words in common.
     */
    const val CONFIDENT_MATCH = 0.60f

    /**
     * How provider results are ordered against each other.
     *
     * ## Why the sync tier is no longer the first thing asked
     *
     * It used to be `syncTier * 10 + matchScore`, and since [score] never exceeds 1, that made the tier
     * decide every comparison outright: a *wrong* song with word-level timings scored 30.4 against the
     * right song with line-level timings at 21.0, and won. That is the report that has survived every
     * round of this — "I still find other lyrics, and when I do a manual search, it gives me the correct
     * one, without any changes." The correct sheet was in the same response all along; it was simply
     * outranked by a better-synchronised stranger, which is also why picking by hand fixed it.
     *
     * Identity comes first now. A confident match wins over any number of tiers, so the right song with
     * no timings at all is preferred to the wrong song in perfect word-by-word sync — which is the only
     * defensible order: unsynchronised words the reader can follow are worth something, and synchronised
     * words from another song are worth less than nothing. Within one confidence bracket the tier decides,
     * as it did, and [score] settles the ties inside that.
     */
    fun rank(syncTier: Int, matchScore: Float, providerBonus: Float = 0f): Float =
        (if (matchScore >= CONFIDENT_MATCH) CONFIDENCE_WEIGHT else 0f) +
            syncTier * TIER_WEIGHT + matchScore + providerBonus

    /** Larger than every tier put together, because identity is not a tie-break. */
    private const val CONFIDENCE_WEIGHT = 100f

    /** Larger than any [score] difference, so the tier still decides within a bracket. */
    private const val TIER_WEIGHT = 10f

    /**
     * How close a candidate is, in `0f..1f`. Only comparable between candidates for the same
     * [Target]; the absolute value means nothing on its own beyond [isAcceptable].
     */
    fun score(
        candidateTitle: String?,
        candidateArtist: String?,
        candidateDurationSec: Double,
        target: Target,
    ): Float {
        val titleSim = similarity(candidateTitle ?: "", target.title)
        val artistSim = similarity(candidateArtist ?: "", target.artist)
        return titleSim * 0.60f + artistSim * 0.25f + durationCloseness(candidateDurationSec, target.durationMs) * 0.15f
    }

    /**
     * Whether a candidate is worth showing at all.
     *
     * A strong title match passes by itself — that is the whole point for re-uploads, where the
     * artist we hold is the uploader's account name and cannot match. A weaker title needs the
     * artist to back it up.
     */
    fun isAcceptable(
        candidateTitle: String?,
        candidateArtist: String?,
        target: Target,
    ): Boolean {
        val titleSim = similarity(candidateTitle ?: "", target.title)
        if (titleSim >= CONFIDENT_MATCH) return true
        val artistSim = similarity(candidateArtist ?: "", target.artist)
        return titleSim >= 0.35f && artistSim >= 0.45f
    }

    /**
     * 1f when the durations agree, tapering to 0f at half a minute apart. A candidate that does
     * not report a duration scores neutrally rather than being punished: LrcLib and Musixmatch
     * both return 0 for plain-text-only entries, which say nothing about whether the words fit.
     */
    private fun durationCloseness(candidateSec: Double, targetMs: Long): Float {
        if (candidateSec <= 0.0 || targetMs <= 0L) return 0.5f
        val deltaSec = kotlin.math.abs(candidateSec - targetMs / 1000.0)
        return (1.0 - (deltaSec / 30.0)).coerceIn(0.0, 1.0).toFloat()
    }

    /**
     * Token-overlap similarity, with a containment shortcut.
     *
     * Containment first, because the common shape here is one string being the other plus noise
     * ("Song Name" vs "Song Name (Official Video) [FREE]"), and overlap alone under-rates that.
     * Otherwise it is the share of the shorter side's words that appear on the longer side, which
     * ignores word order — titles and artist credits get reordered constantly.
     */
    fun similarity(a: String, b: String): Float {
        val normA = normalize(a)
        val normB = normalize(b)
        if (normA.isEmpty() || normB.isEmpty()) return 0f
        if (normA == normB) return 1f

        val tokensA = tokens(normA)
        val tokensB = tokens(normB)
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0f

        // The same words in a different order are the same credit, not a near miss: artist credits
        // and title fragments get reordered constantly.
        if (tokensA == tokensB) return 1f

        val shared = tokensA.count { it in tokensB }
        val fewer = minOf(tokensA.size, tokensB.size)
        val more = maxOf(tokensA.size, tokensB.size)

        // Containment is still the common shape: one title is the other plus packaging, and the
        // packaging is mostly gone by now. But it has to be containment of *words*, and of enough of
        // them to identify a song.
        //
        // It used to be a plain substring test on the whole string, which is how a wrong song got on
        // screen (issue #33). A short title is a substring of almost anything: "go" sits inside
        // "mango", so a track called "fortuna 812 go with me" would accept a lyric sheet titled "Go"
        // at 0.9 and publish it as a confident match.
        if (shared == fewer) {
            if (fewer >= 2) return 0.9f
            // A single word can still identify a song when it is a word rather than a syllable, but
            // never with the confidence of two.
            val onlyToken = tokensA.intersect(tokensB).first()
            if (onlyToken.length >= DISTINCTIVE_TOKEN_LENGTH) return 0.75f
        }

        // Divided by the longer side, not the shorter one. Dividing by the shorter side scored a
        // one-word candidate that appears anywhere in a long title as a perfect match, which is the
        // same bug from the other direction: one word out of eight is a coincidence, however short
        // the sheet's own title happens to be.
        return shared.toFloat() / more
    }

    /**
     * How long a lone shared word has to be before it identifies a song on its own.
     *
     * Six characters is past the length of the short English words that turn up inside other words
     * and inside every second track title.
     */
    private const val DISTINCTIVE_TOKEN_LENGTH = 6

    /** Words worth comparing: everything else is packaging, not identity. */
    private val NOISE = setOf(
        "official", "video", "audio", "lyric", "lyrics", "visualizer", "visualiser",
        "hd", "hq", "4k", "remaster", "remastered", "explicit", "clean", "version",
        "free", "dl", "download", "prod", "by", "feat", "ft", "featuring", "with",
        "the", "a", "an", "el", "la", "le", "les", "und", "and", "vs",
        "music", "mv", "full", "album", "single", "ep", "cover", "reupload", "upload",
    )

    private fun tokens(normalized: String): Set<String> =
        normalized.split(' ')
            .filter { it.length > 1 && it !in NOISE }
            .toSet()
            // A title made only of noise words ("The Video") would otherwise compare as empty,
            // so fall back to the raw words rather than throwing the candidate away.
            .ifEmpty { normalized.split(' ').filter { it.isNotBlank() }.toSet() }

    /**
     * Folds a title or artist credit down to comparable words: accents removed, bracketed asides
     * and everything after a "feat."-style marker dropped, punctuation flattened to spaces.
     */
    fun normalize(raw: String): String {
        var text = raw.lowercase()
        // Every bracket escaped, including the closing ones. The JVM treats a bare `]` or `}` outside a
        // character class as a literal; Android's ICU engine rejects it outright —
        // `PatternSyntaxException: Syntax error in regexp pattern near index 21`, which crashed the app on
        // the first lyrics lookup. Escaping is valid on both, so the file stays identical between them
        // (issue #33).
        text = text.replace(Regex("\\[.*?\\]|\\(.*?\\)|\\{.*?\\}"), " ")
        text = text.replace(Regex("(?i)\\b(feat|ft|featuring|prod|w)\\.?\\s.*$"), " ")
        text = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        text = text.replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
        return text.trim().replace(Regex("\\s+"), " ")
    }
}
