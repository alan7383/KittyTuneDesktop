package com.alananasss.kittytune.ui.pet

/** How a song feels to the cat, which decides how it moves to it. */
enum class MusicMood { DANCE, GROOVE, CALM, SAD }

/**
 * Reads the mood of a song from what is known about it: its genre and tags, a few words of its title, the tempo
 * the beat tracker hears and the key the automix analysis found. Words win over numbers (a sad piano ballad at 130
 * BPM is still sad); without words, a fast tempo dances and a slow minor one is sad.
 */
object PetMood {
    private val SAD = listOf(
        "sad", "triste", "ballad", "melanchol", "heartbreak", "broken", "cry", "tears", "lonely", "grief",
        "emo", "slowed", "piano", "requiem", "sorrow", "depress", "rain",
    )
    private val CALM = listOf(
        "ambient", "chill", "lofi", "lo-fi", "acoustic", "classical", "jazz", "sleep", "relax", "study",
        "meditation", "folk", "soul", "r&b", "rnb", "bossa", "downtempo", "instrumental",
    )
    private val DANCE = listOf(
        "dance", "house", "techno", "edm", "electro", "disco", "dnb", "drum & bass", "drum and bass", "dubstep",
        "trance", "hardstyle", "phonk", "funk", "club", "rave", "garage", "jersey", "breakbeat", "hyperpop",
        "reggaeton", "afro", "dancehall", "k-pop", "kpop", "remix", "bass", "eurobeat", "j-core", "footwork",
    )

    private val ENDINGS = setOf("s", "es", "y", "ing", "ness", "er", "ers", "ed", "beat", "beats")

    fun classify(genre: String?, tags: String?, title: String?, bpm: Float, minor: Boolean?): MusicMood {
        val words = listOfNotNull(genre, tags).joinToString(" ").lowercase()
        val name = title.orEmpty().lowercase()
        // Whole words, or the start of one ("melanchol" finds "melancholy"); phrases as they are written.
        fun hits(list: List<String>, text: String): Int {
            val tokens = text.split(Regex("[^\\p{L}\\p{N}&]+")).filter { it.isNotEmpty() }
            return list.count { key ->
                when {
                    key.any { !it.isLetterOrDigit() } -> key in text
                    // Long stems may start a longer word ("melanchol" in "melancholy"); short ones must be the word
                    // itself, give or take an ending ("cry" in "crying", never in "crystal").
                    key.length >= 5 -> tokens.any { it.startsWith(key) }
                    else -> tokens.any { t -> t == key || (t.startsWith(key) && t.removePrefix(key) in ENDINGS) }
                }
            }
        }
        val sad = hits(SAD, words) * 2 + hits(SAD, name)
        val calm = hits(CALM, words) * 2 + hits(CALM, name)
        val dance = hits(DANCE, words) * 2 + hits(DANCE, name)
        val top = maxOf(sad, calm, dance)
        if (top > 0) {
            return when (top) {
                sad -> MusicMood.SAD
                dance -> if (bpm in 1f..95f) MusicMood.GROOVE else MusicMood.DANCE
                else -> MusicMood.CALM
            }
        }
        return when {
            bpm <= 0f -> MusicMood.GROOVE
            bpm >= 118f -> MusicMood.DANCE
            bpm < 88f && minor == true -> MusicMood.SAD
            bpm < 88f -> MusicMood.CALM
            else -> MusicMood.GROOVE
        }
    }

    /** The moves for a mood; a groove keeps the per-track style so each song still has its own dance. */
    fun danceFor(mood: MusicMood, trackId: Long?, quiet: Boolean): PetActivity = when {
        mood == MusicMood.SAD -> PetActivity.SAD_PHONES
        mood == MusicMood.CALM || quiet -> PetActivity.DANCE_SWAY
        mood == MusicMood.DANCE -> listOf(PetActivity.DANCE_BOUNCE, PetActivity.DANCE_HEADBANG, PetActivity.DANCE_DJ, PetActivity.DANCE_PAWS)[
            (((trackId ?: 0L) % 4L).toInt() + 4) % 4]
        else -> PetBrain.danceFor(trackId)
    }
}
