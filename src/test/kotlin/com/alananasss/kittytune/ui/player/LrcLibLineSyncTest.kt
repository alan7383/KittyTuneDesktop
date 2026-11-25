package com.alananasss.kittytune.ui.player

import com.alananasss.kittytune.data.LyricsCache
import com.alananasss.kittytune.data.LyricsMatcher
import com.alananasss.kittytune.ui.player.lyrics.LyricLine
import com.alananasss.kittytune.ui.player.lyrics.LyricWord
import com.alananasss.kittytune.ui.player.lyrics.LyricsUtils
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LrcLibLineSyncTest {

    private val brokenWordYaml = """
        version: '1.0'
        lines:
          - text: О-о-о, о-о-очень рваные джинсы
            start_ms: 19930
            end_ms: 23888
            words:
              - text: О-
                start_ms: 19930
              - text: о-
                start_ms: 20400
              - text: 'о, '
                start_ms: 20950
              - text: о-о-очень рваные джинсы
                start_ms: 21400
        plain: |-
          О-о-о, о-о-очень рваные джинсы
    """.trimIndent()

    @Test
    fun `LrcLib prefers line-synced LRC and never emits word timings`() {
        val syncedLrc = """
            [00:19.93] О-о-о, о-о-очень рваные джинсы
            [00:24.00] Детка, ты любишь рваные джинсы
        """.trimIndent()

        val parsed = LyricsUtils.parseLrcLibPayload(
            syncedLyrics = syncedLrc,
            lyricsfile = brokenWordYaml,
            plainLyrics = null,
            totalDurationMs = 180_000L,
        )

        val sung = parsed.lines.filterNot { it.isInstrumental }
        assertEquals(2, sung.size)
        assertTrue(parsed.lines.all { it.words.isEmpty() }, "LrcLib must only keep line timings")
        assertEquals(syncedLrc, parsed.rawContent)
        assertEquals(LyricsMatcher.SYNC_TIER_LINE, LyricsMatcher.syncTier(parsed.lines, parsed.plain))
    }

    @Test
    fun `LrcLib fallback to YAML lyricsfile preserves line timings but strips words`() {
        val parsed = LyricsUtils.parseLrcLibPayload(
            syncedLyrics = null,
            lyricsfile = brokenWordYaml,
            plainLyrics = null,
            totalDurationMs = 180_000L,
        )

        val sung = parsed.lines.filterNot { it.isInstrumental }
        assertEquals(1, sung.size)
        assertEquals("О-о-о, о-о-очень рваные джинсы", sung[0].text)
        assertEquals(19_930L, sung[0].startTime)
        assertEquals(23_888L, sung[0].endTime)
        assertTrue(parsed.lines.all { it.words.isEmpty() }, "LrcLib YAML fallback must strip word timings")
        assertEquals(LyricsMatcher.SYNC_TIER_LINE, LyricsMatcher.syncTier(parsed.lines, parsed.plain))
    }

    @Test
    fun `LrcLib strips enhanced LRC word tags from syncedLyrics`() {
        val enhancedLrc = "[00:10.00]Hello <00:10.50>world\n[00:12.00]Next line"

        val parsed = LyricsUtils.parseLrcLibPayload(
            syncedLyrics = enhancedLrc,
            lyricsfile = null,
            plainLyrics = null,
            totalDurationMs = 60_000L,
        )

        val sung = parsed.lines.filterNot { it.isInstrumental }
        assertEquals(2, sung.size)
        assertTrue(parsed.lines.all { it.words.isEmpty() })
        assertEquals(LyricsMatcher.SYNC_TIER_LINE, LyricsMatcher.syncTier(parsed.lines, parsed.plain))
    }

    @Test
    fun `custom uploaded YAML preserves word timings and infers missing end_ms from next word`() {
        val parsed = LyricsUtils.parseLyricsContent(brokenWordYaml, 180_000L)
        val sung = parsed.filterNot { it.isInstrumental }

        assertEquals(1, sung.size)
        val words = sung[0].words
        assertEquals(4, words.size)
        assertEquals(19_930L to 20_400L, words[0].startTime to words[0].endTime)
        assertEquals(20_400L to 20_950L, words[1].startTime to words[1].endTime)
        assertEquals(20_950L to 21_400L, words[2].startTime to words[2].endTime)
        assertEquals(21_400L to 23_888L, words[3].startTime to words[3].endTime)
    }

    @Test
    fun `cache sanitization strips legacy LRCLIB word timings without touching Musixmatch`() {
        val word = LyricWord("Hello ", 1_000L, 1_500L)
        val line = LyricLine("Hello world", 1_000L, 3_000L, listOf(word))

        val lrcLibSanitized = LyricsCache.Entry(found = true, lines = listOf(line), provider = "LRCLIB").sanitized()
        assertTrue(lrcLibSanitized.lines.single().words.isEmpty())

        val mxmSanitized = LyricsCache.Entry(found = true, lines = listOf(line), provider = "MUSIXMATCH").sanitized()
        assertEquals(listOf(word), mxmSanitized.lines.single().words)

        val lrcResult = UnifiedLyricResult(
            id = "7668123",
            name = "Рваные джинсы",
            artistName = "Элджей",
            albumName = null,
            durationSec = 180.0,
            hasLineSync = lrcLibSanitized.lines.isNotEmpty(),
            hasWordSync = false,
            provider = "LRCLIB",
        )
        assertEquals(LyricsMatcher.SYNC_TIER_LINE, ManualLyricsSearch.syncTier(lrcResult))
        assertFalse(lrcResult.hasWordSync)
    }
}
