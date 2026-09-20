package com.alananasss.kittytune

import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GermanLocalizationTest {

    @Test
    fun testGermanLanguageResolution() {
        val previousLang = Strings.appLanguage
        try {
            Strings.appLanguage = "de"
            assertEquals("de", Strings.resolvedLanguage)
            assertEquals("de-DE,de;q=0.9,en;q=0.8", Strings.getAcceptLanguage())
            assertEquals("Deutsch", str("lang_german"))
            assertEquals("Mehr von dem, was dir gefällt", str("home_more_of_what_you_like"))
            assertEquals("Für dich gemacht", str("home_made_for_you"))
        } finally {
            Strings.appLanguage = previousLang
        }
    }

    @Test
    fun testGermanEnumEntry() {
        assertEquals("de", AppLanguage.GERMAN.code)
        assertTrue(AppLanguage.entries.contains(AppLanguage.GERMAN))
    }
}
