package com.alananasss.kittytune

import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.ui.library.formatPlaylistTotalDuration
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistTotalDurationTest {

    private val hourMs = 60 * 60 * 1000L
    private val dayMs = 24 * hourMs

    @Test
    fun testDurationFormattingEnglish() {
        val prev = Strings.appLanguage
        try {
            Strings.appLanguage = "en"

            assertEquals("", formatPlaylistTotalDuration(0L))
            assertEquals("", formatPlaylistTotalDuration(-1000L))
            assertEquals("45s", formatPlaylistTotalDuration(45_000L))
            assertEquals("4 min", formatPlaylistTotalDuration(4 * 60 * 1000L))
            assertEquals("1h", formatPlaylistTotalDuration(hourMs))
            assertEquals("7h 23m", formatPlaylistTotalDuration(7 * hourMs + 23 * 60 * 1000L))

            // Days + Hours
            assertEquals("1d", formatPlaylistTotalDuration(1 * dayMs))
            assertEquals("1d 4h", formatPlaylistTotalDuration(28 * hourMs + 5 * 60 * 1000L))
            assertEquals("11d 16h", formatPlaylistTotalDuration(280 * hourMs + 17 * 60 * 1000L))

            // Months + Days
            assertEquals("1mo", formatPlaylistTotalDuration(30 * dayMs))
            assertEquals("1mo 15d", formatPlaylistTotalDuration(45 * dayMs))
            assertEquals("2mo", formatPlaylistTotalDuration(60 * dayMs))

            // Years + Months / Days
            assertEquals("1y", formatPlaylistTotalDuration(365 * dayMs))
            assertEquals("1y 15d", formatPlaylistTotalDuration(380 * dayMs))
            assertEquals("1y 2mo", formatPlaylistTotalDuration((365 + 60) * dayMs))
            assertEquals("2y 3mo", formatPlaylistTotalDuration((2 * 365 + 90) * dayMs))
            assertEquals("2y", formatPlaylistTotalDuration(2 * 365 * dayMs))
        } finally {
            Strings.appLanguage = prev
        }
    }

    @Test
    fun testDurationFormattingFrench() {
        val prev = Strings.appLanguage
        try {
            Strings.appLanguage = "fr"

            assertEquals("", formatPlaylistTotalDuration(0L))
            assertEquals("45s", formatPlaylistTotalDuration(45_000L))
            assertEquals("4 min", formatPlaylistTotalDuration(4 * 60 * 1000L))
            assertEquals("1h", formatPlaylistTotalDuration(hourMs))
            assertEquals("7h 23m", formatPlaylistTotalDuration(7 * hourMs + 23 * 60 * 1000L))

            // Days + Hours (User's screenshot: 4332 titres = 280h 17m -> 11j 16h)
            assertEquals("1j", formatPlaylistTotalDuration(1 * dayMs))
            assertEquals("1j 4h", formatPlaylistTotalDuration(28 * hourMs + 5 * 60 * 1000L))
            assertEquals("11j 16h", formatPlaylistTotalDuration(280 * hourMs + 17 * 60 * 1000L))

            // Months + Days
            assertEquals("1 mois", formatPlaylistTotalDuration(30 * dayMs))
            assertEquals("1 mois 15j", formatPlaylistTotalDuration(45 * dayMs))
            assertEquals("2 mois", formatPlaylistTotalDuration(60 * dayMs))

            // Years + Months / Days
            assertEquals("1 an", formatPlaylistTotalDuration(365 * dayMs))
            assertEquals("1 an 15j", formatPlaylistTotalDuration(380 * dayMs))
            assertEquals("1 an 2 mois", formatPlaylistTotalDuration((365 + 60) * dayMs))
            assertEquals("2 ans 3 mois", formatPlaylistTotalDuration((2 * 365 + 90) * dayMs))
            assertEquals("2 ans", formatPlaylistTotalDuration(2 * 365 * dayMs))
        } finally {
            Strings.appLanguage = prev
        }
    }

    @Test
    fun testDurationFormattingGerman() {
        val prev = Strings.appLanguage
        try {
            Strings.appLanguage = "de"

            assertEquals("1 Std.", formatPlaylistTotalDuration(hourMs))
            assertEquals("7 Std. 23 Min.", formatPlaylistTotalDuration(7 * hourMs + 23 * 60 * 1000L))
            assertEquals("11 T. 16 Std.", formatPlaylistTotalDuration(280 * hourMs + 17 * 60 * 1000L))
            assertEquals("1 Mon. 15 T.", formatPlaylistTotalDuration(45 * dayMs))
            assertEquals("1 J.", formatPlaylistTotalDuration(365 * dayMs))
            assertEquals("2 J. 3 Mon.", formatPlaylistTotalDuration((2 * 365 + 90) * dayMs))
        } finally {
            Strings.appLanguage = prev
        }
    }

    @Test
    fun testDurationFormattingRussian() {
        val prev = Strings.appLanguage
        try {
            Strings.appLanguage = "ru"

            assertEquals("1 ч.", formatPlaylistTotalDuration(hourMs))
            assertEquals("7 ч. 23 мин.", formatPlaylistTotalDuration(7 * hourMs + 23 * 60 * 1000L))
            assertEquals("11 дн. 16 ч.", formatPlaylistTotalDuration(280 * hourMs + 17 * 60 * 1000L))
            assertEquals("1 мес. 15 дн.", formatPlaylistTotalDuration(45 * dayMs))
            assertEquals("1 г.", formatPlaylistTotalDuration(365 * dayMs))
            assertEquals("2 г. 3 мес.", formatPlaylistTotalDuration((2 * 365 + 90) * dayMs))
        } finally {
            Strings.appLanguage = prev
        }
    }
}
