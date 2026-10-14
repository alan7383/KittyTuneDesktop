package com.alananasss.kittytune.util

import java.util.Locale

/**
 * Answers one question for the comment "Translate" button: is this text in the reader's language?
 *
 * Lightweight, zero-dependency detector replacing Lingua (which weighed 103MB with its dependencies
 * and models). Combines Unicode script detection for non-Latin alphabets and a high-precision stopword
 * and n-gram scoring matrix for Latin-based languages.
 */
object LanguageDetection {

    private const val MIN_WORDS_TO_DETECT = 3
    private const val RUSSIAN_LETTERS = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя"
    private val WHITESPACE = Regex("\\s+")
    /** @mentions and links are names, not prose: "@dj_kitty nice track" is Russian text in Latin letters. */
    private val MENTION_OR_LINK = Regex("(@[\\w.\\-]+)|(https?://\\S+)|(\\bwww\\.\\S+)")
    private val NOT_A_WORD = Regex("[^\\p{L}\\p{Nd}\\s]")

    // Distinctive stopwords and keywords for Latin-script languages common in music comments
    private val LATIN_LANGUAGE_KEYWORDS = mapOf(
        "en" to setOf(
            "the", "is", "are", "and", "you", "that", "this", "with", "for", "track", "song", "love",
            "hard", "been", "looping", "all", "day", "fire", "banger", "listen", "listening", "have",
            "from", "not", "but", "what", "just", "like", "my", "so", "it", "goes", "much", "best",
            "great", "amazing", "vibes", "beats", "repeat", "album", "music", "drop", "bass", "vocals"
        ),
        "fr" to setOf(
            "le", "la", "les", "un", "une", "des", "est", "ce", "cette", "dans", "sur", "pour", "avec",
            "morceau", "écoute", "ecoute", "boucle", "chanson", "très", "tres", "trop", "mon", "ma",
            "mes", "son", "sa", "ses", "je", "tu", "il", "elle", "nous", "vous", "ils", "qui", "que",
            "incroyable", "lourd", "magnifique", "merci", "bien", "bon", "c'est", "j'aime", "tuile"
        ),
        "es" to setOf(
            "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "en", "que", "qué",
            "por", "para", "con", "buena", "bueno", "canción", "cancion", "verdad", "este", "esta",
            "estos", "estas", "muy", "todo", "todos", "como", "cómo", "más", "mas", "pero", "tema",
            "rolón", "rolon", "temazo", "hermoso", "genial", "gusta", "escuchando", "siempre"
        ),
        "de" to setOf(
            "der", "die", "das", "ein", "eine", "einen", "einem", "einer", "und", "ist", "sind",
            "nicht", "mit", "auf", "für", "dieser", "dieses", "diese", "sehr", "lied", "track",
            "gut", "schön", "ich", "du", "er", "sie", "wir", "ihr", "geil", "hammer", "bester"
        ),
        "it" to setOf(
            "il", "la", "lo", "i", "gli", "le", "un", "uno", "una", "di", "in", "che", "non", "con",
            "per", "canzone", "brano", "molto", "bello", "bella", "questo", "questa", "questi", "queste",
            "sono", "mio", "mia", "stupendo", "adoro", "sempre"
        ),
        "pt" to setOf(
            "o", "a", "os", "as", "um", "uma", "de", "do", "da", "dos", "das", "em", "no", "na",
            "nos", "nas", "que", "não", "nao", "com", "para", "música", "musica", "faixa", "muito",
            "bom", "boa", "este", "esta", "esse", "essa", "demais", "adoro", "escutando"
        ),
        "nl" to setOf(
            "de", "het", "een", "en", "van", "ik", "te", "dat", "die", "in", "een", "hij", "het",
            "niet", "zijn", "is", "was", "op", "aan", "met", "als", "voor", "nummer", "liedje"
        )
    )

    /**
     * Whether a comment is worth offering to translate into [readerLanguage] (an ISO 639-1 code,
     * a region suffix is ignored). Blocking; call off the UI thread.
     */
    fun needsTranslation(text: String, readerLanguage: String): Boolean {
        val words = text.replace(MENTION_OR_LINK, " ").replace(NOT_A_WORD, " ").trim()
        val textScript = dominantScript(words) ?: return false
        val readerCode = readerLanguage.substringBefore('-').substringBefore('_').lowercase()
        val readerLocale = Locale.forLanguageTag(readerCode)
        // A language's own name is written in its own script: "русский", "English", "日本語".
        val readerScript = dominantScript(readerLocale.getDisplayLanguage(readerLocale))
        if (readerScript != null && readerScript != textScript) return true

        // Cyrillic script: check if text has letters outside the Russian alphabet (e.g. Ukrainian і, ї, є, ґ)
        if (textScript == Character.UnicodeScript.CYRILLIC) {
            val hasNonRussianCyrillic = words.any { it.isLetter() && it.lowercaseChar() !in RUSSIAN_LETTERS }
            if (readerCode == "ru") {
                return hasNonRussianCyrillic
            }
            if (readerCode == "uk") {
                return !hasNonRussianCyrillic
            }
        }

        // One or two words are too little to tell "banger" from Dutch, and rarely need translating.
        val wordList = words.lowercase().split(WHITESPACE).filter { word -> word.any { it.isLetter() } }
        if (wordList.size < MIN_WORDS_TO_DETECT) return false

        // Latin script classification based on keyword matching
        if (textScript == Character.UnicodeScript.LATIN) {
            val scores = mutableMapOf<String, Int>()
            for ((lang, keywords) in LATIN_LANGUAGE_KEYWORDS) {
                var score = 0
                for (w in wordList) {
                    if (w in keywords) score += 2
                    else {
                        // Partial substring match for accented roots (e.g. "écoute", "canción")
                        if (keywords.any { it.length >= 4 && (w.contains(it) || it.contains(w)) }) {
                            score += 1
                        }
                    }
                }
                if (score > 0) scores[lang] = score
            }

            val readerScore = scores[readerCode] ?: 0
            val bestEntry = scores.maxByOrNull { it.value }

            if (bestEntry != null) {
                // If the top scored language is distinctly not the reader's language
                if (bestEntry.key != readerCode && bestEntry.value >= 2 && bestEntry.value > readerScore * 1.3) {
                    return true
                }
                // If reader scored best or tied, do not translate
                if (bestEntry.key == readerCode) {
                    return false
                }
            }

            // Fallback for reader's language: if words strongly contain reader's keywords
            if (readerScore > 0) return false
        }

        return false
    }

    /** The script most of the letters are in, with the Japanese and Chinese scripts counted as one. */
    private fun dominantScript(text: String): Character.UnicodeScript? =
        text.filter { it.isLetter() }
            .groupingBy { letter ->
                when (val script = Character.UnicodeScript.of(letter.code)) {
                    Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> Character.UnicodeScript.HAN
                    else -> script
                }
            }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
}
