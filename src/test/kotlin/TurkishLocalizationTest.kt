package com.alananasss.kittytune

import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TurkishLocalizationTest {

    @Test
    fun testTurkishLanguageResolution() {
        val previousLang = Strings.appLanguage
        try {
            Strings.appLanguage = "tr"
            assertEquals("tr", Strings.resolvedLanguage)
            assertEquals("tr-TR,tr;q=0.9,en;q=0.8", Strings.getAcceptLanguage())
            assertEquals("Türkçe", str("lang_turkish"))
            assertTrue(str("home_made_for_you").isNotBlank())
            assertTrue(str("recaps_title").isNotBlank())
        } finally {
            Strings.appLanguage = previousLang
        }
    }

    @Test
    fun testTurkishEnumEntry() {
        assertEquals("tr", AppLanguage.TURKISH.code)
        assertTrue(AppLanguage.entries.contains(AppLanguage.TURKISH))
    }
}
