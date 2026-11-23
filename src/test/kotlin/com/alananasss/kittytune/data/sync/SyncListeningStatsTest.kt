package com.alananasss.kittytune.data.sync

import com.alananasss.kittytune.core.AppDirs
import com.alananasss.kittytune.data.ListeningStatsRepository
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.ListeningStatsEvent
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.stats.ListeningReports
import com.google.gson.Gson
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SyncListeningStatsTest {
    private val dao get() = AppDatabase.downloadDao
    private val preferences = PlayerPreferences()
    private val gson = Gson()
    private var isolated = false
    private var wasSharing = true

    @Before
    fun prepare() = runBlocking {
        assumeTrue("Requires Gradle's isolated profile", System.getenv("APPDATA")?.contains("test-profile") == true)
        AppDatabase.init()
        isolated = true
        wasSharing = preferences.getSyncListensEnabled()
        preferences.setSyncListensEnabled(true)
        SyncLog.clear()
        dao.clearStats()
        ListeningStatsRepository.onStatsChanged()
    }

    @After
    fun reset() = runBlocking {
        if (!isolated) return@runBlocking
        AppDatabase.exec("DROP TRIGGER IF EXISTS reject_sync_test_listen")
        SyncLog.clear()
        dao.clearStats()
        preferences.setSyncListensEnabled(wasSharing)
        ListeningStatsRepository.onStatsChanged()
    }

    @Test
    fun `failed projection is retried on the next exchange without losing or doubling listens`() = runBlocking {
        val sunday = Instant.parse("2026-11-22T12:00:00Z").toEpochMilli()
        val monday = Instant.parse("2026-11-23T12:00:00Z").toEpochMilli()
        val lastMonth = Instant.parse("2026-10-17T12:00:00Z").toEpochMilli()
        val twelveHours = 12 * 60 * 60_000L
        val twentyNineMinutes = 29 * 60_000L
        val repo = ListeningStatsRepository
        dao.insertStatsEvent(ListeningStatsEvent(
            trackId = 1, trackTitle = "Local", artistName = "Artist", artworkUrl = "",
            eventType = "PLAY_COMPLETE", listenDurationMs = twelveHours,
            trackDurationMs = twelveHours, timestamp = sunday,
        ))
        repo.onStatsChanged()
        assertEquals(sunday, repo.getOldestEventAt())

        val events = listOf(listen(1, monday, twentyNineMinutes), listen(2, lastMonth, 60_000L))
        val week = ListeningReports.recentWeek(monday, ZoneOffset.UTC)
        AppDatabase.exec("""
            CREATE TEMP TRIGGER reject_sync_test_listen BEFORE INSERT ON listening_stats
            WHEN NEW.syncEventId = 'fixture-phone#2' BEGIN SELECT RAISE(ABORT, 'write failed'); END
        """.trimIndent())
        assertFailsWith<java.sql.SQLException> { SyncApply.mergeAndApply(events) }
        assertEquals(2L, SyncLog.marks()["fixture-phone"])
        assertEquals(twelveHours, repo.getTotalListenTime(week.startMs, week.endMs))

        AppDatabase.exec("DROP TRIGGER reject_sync_test_listen")
        // The peer has already seen our mark. It can now send an empty batch.
        SyncApply.mergeAndApply(emptyList())
        assertEquals(twelveHours + twentyNineMinutes, repo.getTotalListenTime(week.startMs, week.endMs))
        assertEquals(lastMonth, repo.getOldestEventAt())
        assertEquals(60_000L, repo.getTotalListenTime(lastMonth, lastMonth + 1))

        assertTrue(SyncApply.mergeAndApply(events).isEmpty())
        AppDatabase.exec("DROP INDEX idx_stats_sync_event")
        try {
            // Older databases may have pre-existing duplicates which prevented creating the index.
            assertEquals(0, SyncApply.reconcile())
        } finally {
            AppDatabase.exec("CREATE UNIQUE INDEX idx_stats_sync_event ON listening_stats(syncEventId)")
        }
        assertEquals(3, repo.getTotalEvents(0L))
    }

    @Test
    fun `journal write failure leaves the peer free to resend the listen`() = runBlocking {
        val path = File(AppDirs.dataDir, "sync_log.jsonl")
        val event = listen(1, 1_000L, 60_000L)
        assertTrue(path.mkdir())
        try {
            assertFailsWith<IOException> { SyncApply.mergeAndApply(listOf(event)) }
            assertTrue(SyncLog.marks().isEmpty())
            assertTrue(SyncLog.all().isEmpty())
        } finally {
            assertTrue(path.delete())
        }
        assertEquals(listOf(event), SyncApply.mergeAndApply(listOf(event)))
        assertEquals(60_000L, ListeningStatsRepository.getTotalListenTime(0L))
    }

    private fun listen(seq: Long, timestamp: Long, duration: Long): SyncEvent {
        val payload = ListenPayload(
            trackId = seq + 1, trackTitle = "Remote $seq", artistName = "Artist",
            artistId = null, artistPermalink = null, artistAvatarUrl = null, artworkUrl = null,
            source = "soundcloud", eventType = "PLAY_COMPLETE", listenDurationMs = duration,
            trackDurationMs = duration,
        )
        return SyncEvent("fixture-phone", seq, timestamp, SyncKinds.LISTEN, gson.toJson(payload))
    }
}
