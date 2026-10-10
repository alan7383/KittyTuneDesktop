package com.alananasss.kittytune.util

import com.github.pemistahl.lingua.api.IsoCode639_1
import com.github.pemistahl.lingua.api.Language
import com.github.pemistahl.lingua.api.LanguageDetector
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Answers one question for the comment "Translate" button: is this text in the reader's language?
 *
 * Lingua keeps its n-gram models in static caches that are only filled on first use and never
 * emptied on their own. Built with every language in high-accuracy mode, the first comment on screen
 * pulled every model into the heap — several hundred megabytes that stayed there for the rest of the
 * session. A yes/no check on a comment does not need that precision, so the detector runs in
 * low-accuracy mode (trigram models only) and the models are unloaded once comments stop asking.
 */
object LanguageDetection {
    private const val IDLE_UNLOAD_DELAY_MS = 60_000L

    /**
     * How far behind the detector's best guess the reader's language may be and still count as the
     * comment's language. Close relatives score near each other: French text puts English at 0.93.
     */
    private const val SAME_LANGUAGE_RATIO = 0.95
    private const val MIN_WORDS_TO_DETECT = 3
    private const val RUSSIAN_LETTERS = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя"
    private val WHITESPACE = Regex("\\s+")
    /** @mentions and links are names, not prose: "@dj_kitty nice track" is Russian text in Latin letters. */
    private val MENTION_OR_LINK = Regex("(@[\\w.\\-]+)|(https?://\\S+)|(\\bwww\\.\\S+)")
    private val NOT_A_WORD = Regex("[^\\p{L}\\p{Nd}\\s]")

    /**
     * The languages a comment is weighed against. With all 75, low-accuracy mode called a short
     * English comment Bosnian and a Russian one Kazakh; comments on a music site are almost all in
     * one of these. The reader's own language is always added.
     */
    private val COMMENT_LANGUAGES = setOf(
        Language.ENGLISH, Language.SPANISH, Language.PORTUGUESE, Language.FRENCH, Language.GERMAN,
        Language.ITALIAN, Language.DUTCH, Language.POLISH, Language.TURKISH, Language.INDONESIAN,
        Language.VIETNAMESE, Language.HUNGARIAN, Language.SWEDISH, Language.ROMANIAN, Language.CZECH,
        Language.TAGALOG, Language.RUSSIAN, Language.UKRAINIAN, Language.ARABIC, Language.PERSIAN,
        Language.HINDI, Language.JAPANESE, Language.CHINESE, Language.KOREAN, Language.THAI,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private var detector: LanguageDetector? = null
    private var detectorReader: Language? = null
    private var unloadJob: Job? = null

    /**
     * Whether a comment is worth offering to translate into [readerLanguage] (an ISO 639-1 code,
     * a region suffix is ignored). Blocking; call off the UI thread.
     *
     * Asking only "is the detector's best guess the reader's language?" offered to translate Russian
     * into Russian (issue #66). So the alphabet decides first: a comment in another script always
     * needs translating, and Cyrillic text is Russian when it has no letter outside the Russian
     * alphabet. A short comment in the reader's own script is left alone, and the rest goes to the
     * detector, where the reader's language has to be clearly behind the best guess.
     */
    fun needsTranslation(text: String, readerLanguage: String): Boolean {
        val words = text.replace(MENTION_OR_LINK, " ").replace(NOT_A_WORD, " ").trim()
        val textScript = dominantScript(words) ?: return false
        val readerCode = readerLanguage.substringBefore('-').substringBefore('_').lowercase()
        val readerLocale = Locale.forLanguageTag(readerCode)
        // A language's own name is written in its own script: "русский", "English", "日本語".
        val readerScript = dominantScript(readerLocale.getDisplayLanguage(readerLocale))
        if (readerScript != null && readerScript != textScript) return true
        if (readerCode == "ru" && textScript == Character.UnicodeScript.CYRILLIC) {
            return words.any { it.isLetter() && it.lowercaseChar() !in RUSSIAN_LETTERS }
        }
        // One or two words are too little to tell "banger" from Dutch, and rarely need translating.
        if (words.split(WHITESPACE).count { word -> word.any { it.isLetter() } } < MIN_WORDS_TO_DETECT) return false

        val reader = languageOf(readerCode)
        // Held for the detection too, so an idle unload can never empty the models mid-lookup.
        val confidences = synchronized(lock) {
            runCatching { acquireDetector(reader).computeLanguageConfidenceValues(words) }.getOrNull()
                .also { scheduleUnload() }
        }
        if (confidences.isNullOrEmpty()) return false
        val best = confidences.maxBy { it.value }
        if (best.value <= 0.0 || best.key == reader) return false
        val readerConfidence = reader?.let { confidences[it] } ?: 0.0
        return readerConfidence / best.value < SAME_LANGUAGE_RATIO
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

    private fun languageOf(isoCode: String): Language? =
        runCatching { Language.getByIsoCode639_1(IsoCode639_1.valueOf(isoCode.uppercase())) }.getOrNull()

    private fun acquireDetector(reader: Language?): LanguageDetector {
        detector?.takeIf { detectorReader == reader }?.let { return it }
        detector?.unloadLanguageModels()
        val languages = COMMENT_LANGUAGES + listOfNotNull(reader)
        return LanguageDetectorBuilder.fromLanguages(*languages.toTypedArray())
            .withLowAccuracyMode()
            .build()
            .also {
                detector = it
                detectorReader = reader
            }
    }

    private fun scheduleUnload() {
        unloadJob?.cancel()
        unloadJob = scope.launch {
            delay(IDLE_UNLOAD_DELAY_MS)
            synchronized(lock) {
                detector?.unloadLanguageModels()
                detector = null
                detectorReader = null
            }
        }
    }
}
