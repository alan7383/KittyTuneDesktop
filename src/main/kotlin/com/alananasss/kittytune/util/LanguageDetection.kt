package com.alananasss.kittytune.util

import com.github.pemistahl.lingua.api.LanguageDetectorBuilder
import com.github.pemistahl.lingua.api.Language
import com.github.pemistahl.lingua.api.LanguageDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object LanguageDetection {
    private var detector: LanguageDetector? = null

    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                detector = LanguageDetectorBuilder.fromAllLanguages().build()
                println("LanguageDetection: Lingua initialized successfully")
            } catch (e: Exception) {
                println("LanguageDetection: Failed to initialize Lingua: ${e.message}")
            }
        }
    }

    fun identifyLanguage(text: String): String {
        val currentDetector = detector ?: return "und"
        
        return try {
            val language = currentDetector.detectLanguageOf(text)
            if (language == Language.UNKNOWN) {
                "und"
            } else {
                language.isoCode639_1.toString().lowercase()
            }
        } catch (e: Exception) {
            "und"
        }
    }
}
