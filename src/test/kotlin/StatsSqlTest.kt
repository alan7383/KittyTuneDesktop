import com.alananasss.kittytune.data.stats.ListenRules
import com.alananasss.kittytune.data.stats.StatsSql
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * That the rule in SQL is the rule in Kotlin (issue #33).
 *
 * This is the test the bug needed. The rule for "did this count as a listen" exists twice — once where a
 * listen in progress is judged, once where a million finished ones are — and the two had drifted so far
 * apart that the desktop and the phone reported different totals from identical rows. Nothing catches that
 * by reading the code; the only way is to run both against the same data and compare.
 *
 * Uses a real SQLite in memory rather than a fake, because what is being checked is exactly SQLite's
 * arithmetic — integer division, a `REAL` multiplication, and how it treats a zero duration.
 */
class StatsSqlTest {

    private fun open(): Connection {
        Class.forName("org.sqlite.JDBC")
        val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        conn.createStatement().use {
            it.execute(
                """
                CREATE TABLE listening_stats (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  listenDurationMs INTEGER NOT NULL,
                  trackDurationMs INTEGER NOT NULL,
                  furthestPositionMs INTEGER NOT NULL DEFAULT 0,
                  eventType TEXT NOT NULL DEFAULT 'TRACK_CHANGE',
                  trackId INTEGER NOT NULL DEFAULT 1,
                  syncEventId TEXT,
                  artistName TEXT NOT NULL DEFAULT 'a',
                  timestamp INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
        }
        return conn
    }

    private fun Connection.insert(
        listened: Long,
        duration: Long,
        furthest: Long = 0,
        eventType: String = "TRACK_CHANGE",
    ) {
        prepareStatement(
            "INSERT INTO listening_stats(listenDurationMs,trackDurationMs,furthestPositionMs,eventType) " +
                "VALUES(?,?,?,?)"
        ).use {
            it.setLong(1, listened)
            it.setLong(2, duration)
            it.setLong(3, furthest)
            it.setString(4, eventType)
            it.executeUpdate()
        }
    }

    private fun Connection.matches(predicate: String): Int =
        createStatement().use { st ->
            st.executeQuery("SELECT COUNT(*) FROM listening_stats WHERE $predicate").use {
                it.next(); it.getInt(1)
            }
        }

    /**
     * Every interesting shape of row, judged both ways.
     *
     * The pairs are chosen around the two thresholds and on both sides of them, plus the cases that broke
     * before: a track of unknown length, and one short enough that the fraction decides rather than the
     * absolute floor.
     */
    @Test
    fun `the SQL play rule agrees with the Kotlin one`() {
        val cases = listOf(
            0L to 200_000L,
            1L to 200_000L,
            29_999L to 200_000L,
            30_000L to 200_000L,
            30_001L to 200_000L,
            // Too short to ever reach thirty seconds: half of it has to do.
            9_999L to 20_000L,
            10_000L to 20_000L,
            10_001L to 20_000L,
            // Unknown length: the absolute floor is the only thing that can apply.
            10_000L to 0L,
            30_000L to 0L,
            // Heard more than the track lasts, which a loop produces.
            400_000L to 200_000L,
        )

        open().use { conn ->
            for ((listened, duration) in cases) {
                conn.createStatement().use { it.execute("DELETE FROM listening_stats") }
                conn.insert(listened, duration)
                val fromSql = conn.matches(StatsSql.COUNTS_AS_PLAY) == 1
                val fromKotlin = ListenRules.countsAsPlay(listened, duration)
                assertEquals(
                    fromKotlin,
                    fromSql,
                    "listened=$listened duration=$duration: Kotlin said $fromKotlin, SQL said $fromSql",
                )
            }
        }
    }

    @Test
    fun `the SQL completion rule agrees with the Kotlin one`() {
        val cases = listOf(
            0L to 200_000L,
            189_999L to 200_000L,
            190_000L to 200_000L,
            200_000L to 200_000L,
            // No duration to compare against: nothing can be called complete.
            200_000L to 0L,
        )

        open().use { conn ->
            for ((furthest, duration) in cases) {
                conn.createStatement().use { it.execute("DELETE FROM listening_stats") }
                // A non-zero furthest position is what stops the legacy fallback applying, so these rows
                // carry a neutral ending label.
                conn.insert(listened = 60_000, duration = duration, furthest = furthest.coerceAtLeast(1))
                val fromSql = conn.matches(StatsSql.IS_COMPLETE) == 1
                val fromKotlin = ListenRules.isComplete(furthest.coerceAtLeast(1), duration)
                assertEquals(
                    fromKotlin,
                    fromSql,
                    "furthest=$furthest duration=$duration",
                )
            }
        }
    }

    /**
     * Rows from before the position was recorded are judged on their ending label instead.
     *
     * Without this, switching the aggregates over to the new rule would have declared every completed
     * track in the existing history unfinished — the numbers would have moved for reasons that have
     * nothing to do with what was listened to.
     */
    @Test
    fun `rows with no recorded position fall back to how they ended`() {
        open().use { conn ->
            conn.insert(listened = 190_000, duration = 200_000, furthest = 0, eventType = "PLAY_COMPLETE")
            conn.insert(listened = 190_000, duration = 200_000, furthest = 0, eventType = "REPEAT_ONE_LOOP")
            conn.insert(listened = 190_000, duration = 200_000, furthest = 0, eventType = "SKIP_NEXT")
            assertEquals(2, conn.matches(StatsSql.IS_COMPLETE))
        }
    }

    /**
     * A skip is not simply "did not finish".
     *
     * Pausing halfway through and coming back tomorrow is neither a completion nor a skip, and counting
     * it as one is what made the skip rate read as a measure of how often the app was closed.
     */
    @Test
    fun `a long listen that never reached the end is not a skip`() {
        open().use { conn ->
            conn.insert(listened = 120_000, duration = 400_000, furthest = 120_000)
            assertEquals(0, conn.matches(StatsSql.IS_SKIP))
            assertEquals(1, conn.matches(StatsSql.COUNTS_AS_PLAY))
        }
    }

    @Test
    fun `a track dropped in the first seconds is a skip`() {
        open().use { conn ->
            conn.insert(listened = 4_000, duration = 400_000, furthest = 4_000)
            assertEquals(1, conn.matches(StatsSql.IS_SKIP))
        }
    }

    /** Plays and skips must partition the rows that are one or the other, never both. */
    @Test
    fun `nothing is both a play and a skip`() {
        open().use { conn ->
            conn.insert(listened = 4_000, duration = 400_000, furthest = 4_000)
            conn.insert(listened = 120_000, duration = 400_000, furthest = 120_000)
            conn.insert(listened = 400_000, duration = 400_000, furthest = 400_000)
            conn.insert(listened = 0, duration = 0, furthest = 0)
            assertEquals(
                0,
                conn.matches("${StatsSql.COUNTS_AS_PLAY} AND ${StatsSql.IS_SKIP}"),
            )
            assertTrue(conn.matches(StatsSql.COUNTS_AS_PLAY) > 0)
            assertTrue(conn.matches(StatsSql.IS_SKIP) > 0)
        }
    }

    /**
     * The claim that makes syncing safe: the same listen cannot become two rows.
     *
     * The merge rule already drops events it has seen, but that bookkeeping lives in preferences, and
     * preferences get cleared, restored from a backup, or lost. This is the second, independent guarantee —
     * the database itself refuses the duplicate — and it is the one that holds when the first fails.
     */
    @Test
    fun `applying the same event twice produces one row`() {
        open().use { conn ->
            conn.createStatement().use {
                it.execute(
                    "CREATE UNIQUE INDEX idx_sync_event ON listening_stats(syncEventId)"
                )
            }
            repeat(3) { conn.insertSynced("phone#7") }
            assertEquals(1, conn.count())
        }
    }

    /**
     * Rows recorded before sync existed carry no event id, and there can be any number of them.
     *
     * SQLite counts NULLs as distinct in a unique index, which is exactly the behaviour needed here — the
     * alternative would have been a unique index that rejected every listen in the existing history after
     * the first.
     */
    @Test
    fun `rows with no event id do not collide`() {
        open().use { conn ->
            conn.createStatement().use {
                it.execute(
                    "CREATE UNIQUE INDEX idx_sync_event ON listening_stats(syncEventId)"
                )
            }
            repeat(4) { conn.insertSynced(null) }
            assertEquals(4, conn.count())
        }
    }

    /** Two devices' events differ by device id even at the same sequence number. */
    @Test
    fun `the same sequence number from two devices is two rows`() {
        open().use { conn ->
            conn.createStatement().use {
                it.execute(
                    "CREATE UNIQUE INDEX idx_sync_event ON listening_stats(syncEventId)"
                )
            }
            conn.insertSynced("phone#7")
            conn.insertSynced("desktop#7")
            assertEquals(2, conn.count())
        }
    }

    private fun Connection.insertSynced(syncEventId: String?) {
        prepareStatement(
            "INSERT OR IGNORE INTO listening_stats(listenDurationMs,trackDurationMs,syncEventId) " +
                "VALUES(?,?,?)"
        ).use {
            it.setLong(1, 60_000)
            it.setLong(2, 200_000)
            if (syncEventId == null) it.setNull(3, java.sql.Types.VARCHAR)
            else it.setString(3, syncEventId)
            it.executeUpdate()
        }
    }

    private fun Connection.count(): Int = createStatement().use { st ->
        st.executeQuery("SELECT COUNT(*) FROM listening_stats").use { it.next(); it.getInt(1) }
    }
}
