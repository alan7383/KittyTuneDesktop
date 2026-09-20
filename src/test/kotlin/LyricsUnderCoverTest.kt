package com.alananasss.kittytune

import com.alananasss.kittytune.data.local.LyricsDisplayState
import com.alananasss.kittytune.data.local.LyricsUnderCoverPlacement
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.player.lyrics.LyricWord
import com.alananasss.kittytune.ui.player.lyrics.LyricsUtils
import com.alananasss.kittytune.ui.player.lyrics.formatLyricWordContents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LyricsUnderCoverTest {

    @Test
    fun testPreferencesDefaultsAndMutations() {
        val prefs = PlayerPreferences()

        val origUnderCover = prefs.getLyricsUnderCoverEnabled()
        val origMultiState = prefs.getLyricsMultiStateToggle()
        val origPlacement = prefs.getLyricsUnderCoverPlacement()
        val origAlways = prefs.getLyricsUnderCoverAlwaysVisible()

        try {
            prefs.setLyricsUnderCoverEnabled(true)
            assertTrue(prefs.getLyricsUnderCoverEnabled())
            prefs.setLyricsUnderCoverEnabled(false)
            assertFalse(prefs.getLyricsUnderCoverEnabled())

            prefs.setLyricsMultiStateToggle(true)
            assertTrue(prefs.getLyricsMultiStateToggle())
            prefs.setLyricsMultiStateToggle(false)
            assertFalse(prefs.getLyricsMultiStateToggle())

            prefs.setLyricsUnderCoverPlacement(LyricsUnderCoverPlacement.ABOVE_TITLE_ARTIST)
            assertEquals(LyricsUnderCoverPlacement.ABOVE_TITLE_ARTIST, prefs.getLyricsUnderCoverPlacement())
            prefs.setLyricsUnderCoverPlacement(LyricsUnderCoverPlacement.REPLACE_TITLE_ARTIST)
            assertEquals(LyricsUnderCoverPlacement.REPLACE_TITLE_ARTIST, prefs.getLyricsUnderCoverPlacement())

            prefs.setLyricsUnderCoverAlwaysVisible(true)
            assertTrue(prefs.getLyricsUnderCoverAlwaysVisible())
            prefs.setLyricsUnderCoverAlwaysVisible(false)
            assertFalse(prefs.getLyricsUnderCoverAlwaysVisible())
        } finally {
            prefs.setLyricsUnderCoverEnabled(origUnderCover)
            prefs.setLyricsMultiStateToggle(origMultiState)
            prefs.setLyricsUnderCoverPlacement(origPlacement)
            prefs.setLyricsUnderCoverAlwaysVisible(origAlways)
        }
    }

    @Test
    fun testLyricsDisplayStateEnum() {
        assertEquals(3, LyricsDisplayState.values().size)
        assertTrue(LyricsDisplayState.values().contains(LyricsDisplayState.OFF))
        assertTrue(LyricsDisplayState.values().contains(LyricsDisplayState.UNDER_COVER))
        assertTrue(LyricsDisplayState.values().contains(LyricsDisplayState.COVER_REPLACED))
    }

    @Test
    fun testHtmlEntityDecoding() {
        assertEquals("It's a test", LyricsUtils.decodeHtmlEntities("It&apos;s a test"))
        assertEquals("It's a test", LyricsUtils.decodeHtmlEntities("It&#39;s a test"))
        assertEquals("\"Quotes\"", LyricsUtils.decodeHtmlEntities("&quot;Quotes&quot;"))
        assertEquals("<tag>", LyricsUtils.decodeHtmlEntities("&lt;tag&gt;"))
        assertEquals("Rock & Roll", LyricsUtils.decodeHtmlEntities("Rock &amp; Roll"))
        assertEquals("Plain text", LyricsUtils.decodeHtmlEntities("Plain text"))
    }

    @Test
    fun testFormatLyricWordContents() {
        val words = listOf(
            LyricWord("Hello", 0L, 500L),
            LyricWord("world", 500L, 1000L)
        )
        val formatted = formatLyricWordContents("Hello world", words)
        assertEquals(2, formatted.size)
        assertEquals("Hello ", formatted[0])
        assertEquals("world", formatted[1])
    }

    @Test
    fun testI18nStringsPresentInAllLanguages() {
        val requiredKeys = listOf(
            "pref_lyrics_under_cover",
            "pref_lyrics_under_cover_sub",
            "pref_lyrics_multi_state",
            "pref_lyrics_multi_state_sub",
            "pref_lyrics_under_cover_placement",
            "pref_lyrics_under_cover_replace",
            "pref_lyrics_under_cover_above",
            "pref_lyrics_under_cover_always",
            "pref_lyrics_under_cover_always_sub"
        )

        val languages = listOf("en", "fr", "de", "ru", "hu", "vi")
        for (lang in languages) {
            val file = File("src/main/resources/i18n/strings-$lang.xml")
            assertTrue("strings-$lang.xml should exist", file.exists())
            val content = file.readText()
            for (key in requiredKeys) {
                assertTrue(
                    "strings-$lang.xml missing string key $key",
                    content.contains("name=\"$key\"")
                )
            }
        }
    }
}
