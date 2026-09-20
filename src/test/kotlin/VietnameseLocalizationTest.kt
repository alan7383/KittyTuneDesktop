package com.alananasss.kittytune

import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VietnameseLocalizationTest {

    @Test
    fun testVietnameseLanguageResolution() {
        val previousLang = Strings.appLanguage
        try {
            Strings.appLanguage = "vi"
            assertEquals("vi", Strings.resolvedLanguage)
            assertEquals("vi-VN,vi;q=0.9,en;q=0.8", Strings.getAcceptLanguage())
            assertEquals("Tiếng Việt", str("lang_vietnamese"))
            assertEquals("Thêm nội dung bạn thích", str("home_more_of_what_you_like"))
            assertEquals("Dành riêng cho bạn", str("home_made_for_you"))
        } finally {
            Strings.appLanguage = previousLang
        }
    }

    @Test
    fun testVietnameseEnumEntry() {
        assertEquals("vi", AppLanguage.VIETNAMESE.code)
        assertTrue(AppLanguage.entries.contains(AppLanguage.VIETNAMESE))
    }
}
