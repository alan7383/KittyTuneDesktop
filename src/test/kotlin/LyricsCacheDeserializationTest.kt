import com.alananasss.kittytune.data.LyricsCache
import com.alananasss.kittytune.ui.player.lyrics.LyricLine
import com.alananasss.kittytune.ui.player.lyrics.LyricSinger
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LyricsCacheDeserializationTest {

    private val gson = Gson()
    private val entryType = object : TypeToken<LyricsCache.Entry>() {}.type

    @Test
    fun `legacy cache json without singer field does not crash when evaluating singer`() {
        val legacyJson = """
            {
                "found": true,
                "lines": [
                    {
                        "text": "Hello world",
                        "startTime": 1000,
                        "endTime": 3000,
                        "words": []
                    }
                ],
                "plain": "Hello world"
            }
        """.trimIndent()

        val entry = gson.fromJson<LyricsCache.Entry>(legacyJson, entryType)
        assertNotNull(entry)
        val line = entry.lines.first()

        // Before our fix, line.singer was typed non-null LyricSinger, but Gson reflection left it null,
        // causing `when (line.singer)` to invoke .ordinal() on null and crash with NPE.
        val resolved = when (line.singer) {
            LyricSinger.SINGER_1 -> "singer1"
            LyricSinger.SINGER_2 -> "singer2"
            LyricSinger.BOTH -> "both"
            else -> "default"
        }
        assertEquals("default", resolved)

        val safeSinger = line.singer ?: LyricSinger.DEFAULT
        assertEquals(LyricSinger.DEFAULT, safeSinger)
    }

    @Test
    fun `entry sanitization populates default singer and words`() {
        val legacyJson = """
            {
                "found": true,
                "lines": [
                    {
                        "text": "Legacy line without words or singer",
                        "startTime": 0,
                        "endTime": 2000
                    }
                ]
            }
        """.trimIndent()

        val entry = gson.fromJson<LyricsCache.Entry>(legacyJson, entryType)
        assertNotNull(entry)
        val sanitized = entry.sanitized()

        val line = sanitized.lines.first()
        assertEquals(LyricSinger.DEFAULT, line.singer)
        assertEquals(emptyList(), line.words)
    }
}
