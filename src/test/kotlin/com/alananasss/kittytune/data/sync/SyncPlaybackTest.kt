package com.alananasss.kittytune.data.sync

import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.google.gson.Gson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SyncPlaybackTest {
    private val track = Track(
        id = 42L, title = "Test", artworkUrl = "", durationMs = 180_000L,
        user = User(id = 1L, username = "Artist", avatarUrl = ""),
    )

    @Test fun legacyExchangeWithoutPlaybackStillDecodes() {
        val json = """{"deviceId":"phone","deviceName":"Phone","marks":{},"events":[]}"""
        assertNull(Gson().fromJson(json, SyncExchange::class.java).playback)
    }

    @Test fun snapshotRoundTripsAndProjectsPosition() {
        val snapshot = PlaybackSnapshot("phone", 1_000L, listOf(track), 0, 25_000L,
            isPlaying = true, shuffleEnabled = false, repeatMode = "NONE")
        val exchange = SyncExchange("phone", "Phone", emptyMap(), emptyList(), playback = snapshot)
        val decoded = Gson().fromJson(Gson().toJson(exchange), SyncExchange::class.java).playback!!
        assertEquals(42L, decoded.queue.single().id)
        assertEquals(30_000L, decoded.projectedPosition(6_000L))
        assertEquals(180_000L, decoded.projectedPosition(999_000L))
        assertEquals(25_000L, decoded.copy(isPlaying = false).projectedPosition(6_000L))
    }
}
