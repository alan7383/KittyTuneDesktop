package com.alananasss.kittytune.util

import com.github.pemistahl.lingua.api.Language
import com.github.pemistahl.lingua.api.LanguageDetector
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder
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
    private const val UNDETERMINED = "und"
    private const val IDLE_UNLOAD_DELAY_MS = 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private var detector: LanguageDetector? = null
    private var unloadJob: Job? = null

    /** ISO 639-1 code of the text's language, or `und` when it cannot be determined. Blocking; call off the UI thread. */
    fun identifyLanguage(text: String): String {
        // Held for the detection too, so an idle unload can never empty the models mid-lookup.
        val language = synchronized(lock) {
            runCatching { acquireDetector().detectLanguageOf(text) }.getOrNull()
                .also { scheduleUnload() }
        }
        if (language == null || language == Language.UNKNOWN) return UNDETERMINED
        return language.isoCode639_1.toString().lowercase()
    }

    private fun acquireDetector(): LanguageDetector =
        detector ?: LanguageDetectorBuilder.fromAllLanguages()
            .withLowAccuracyMode()
            .build()
            .also { detector = it }

    private fun scheduleUnload() {
        unloadJob?.cancel()
        unloadJob = scope.launch {
            delay(IDLE_UNLOAD_DELAY_MS)
            synchronized(lock) {
                detector?.unloadLanguageModels()
                detector = null
            }
        }
    }
}
