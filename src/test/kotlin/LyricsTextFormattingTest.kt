package com.alananasss.kittytune

import com.alananasss.kittytune.ui.player.lyrics.LyricWord
import com.alananasss.kittytune.ui.player.lyrics.LyricsUtils
import com.alananasss.kittytune.ui.player.lyrics.formatLyricWordContents
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsTextFormattingTest {

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
}
