import com.alananasss.kittytune.data.LyricsOffsetRepository
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.assertEquals

class LyricsOffsetTest {

    private fun open(): Connection {
        Class.forName("org.sqlite.JDBC")
        val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        conn.createStatement().use {
            it.execute(
                """
                CREATE TABLE IF NOT EXISTS lyrics_offset (
                    trackId INTEGER PRIMARY KEY NOT NULL,
                    offsetMs INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
        return conn
    }

    @Test
    fun `sqlite table handles insert, update, and delete`() {
        val conn = open()
        val trackId = 12345L

        // Insert
        conn.prepareStatement("INSERT OR REPLACE INTO lyrics_offset(trackId,offsetMs,updatedAt) VALUES(?,?,?)").use {
            it.setLong(1, trackId)
            it.setLong(2, 1000L)
            it.setLong(3, 100L)
            it.executeUpdate()
        }

        // Query
        conn.prepareStatement("SELECT offsetMs FROM lyrics_offset WHERE trackId = ?").use {
            it.setLong(1, trackId)
            val rs = it.executeQuery()
            assertEquals(true, rs.next())
            assertEquals(1000L, rs.getLong("offsetMs"))
        }

        // Update (change to -500ms)
        conn.prepareStatement("INSERT OR REPLACE INTO lyrics_offset(trackId,offsetMs,updatedAt) VALUES(?,?,?)").use {
            it.setLong(1, trackId)
            it.setLong(2, -500L)
            it.setLong(3, 200L)
            it.executeUpdate()
        }

        conn.prepareStatement("SELECT offsetMs FROM lyrics_offset WHERE trackId = ?").use {
            it.setLong(1, trackId)
            val rs = it.executeQuery()
            assertEquals(true, rs.next())
            assertEquals(-500L, rs.getLong("offsetMs"))
        }

        // Delete
        conn.prepareStatement("DELETE FROM lyrics_offset WHERE trackId = ?").use {
            it.setLong(1, trackId)
            it.executeUpdate()
        }

        conn.prepareStatement("SELECT offsetMs FROM lyrics_offset WHERE trackId = ?").use {
            it.setLong(1, trackId)
            val rs = it.executeQuery()
            assertEquals(false, rs.next())
        }
    }

    @Test
    fun `clamping prevents extreme offsets`() {
        val clampedMin = (-200_000L).coerceIn(LyricsOffsetRepository.MIN_OFFSET_MS, LyricsOffsetRepository.MAX_OFFSET_MS)
        val clampedMax = 200_000L.coerceIn(LyricsOffsetRepository.MIN_OFFSET_MS, LyricsOffsetRepository.MAX_OFFSET_MS)
        val normal = 1_000L.coerceIn(LyricsOffsetRepository.MIN_OFFSET_MS, LyricsOffsetRepository.MAX_OFFSET_MS)

        assertEquals(LyricsOffsetRepository.MIN_OFFSET_MS, clampedMin)
        assertEquals(LyricsOffsetRepository.MAX_OFFSET_MS, clampedMax)
        assertEquals(1_000L, normal)
    }
}
