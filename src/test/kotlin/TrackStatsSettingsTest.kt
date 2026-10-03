package com.alananasss.kittytune

import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.main.formatReleaseDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackStatsSettingsTest {

    @Test
    fun testDefaultHiddenStatsContainsInfo() {
        assertTrue(PlayerPreferences.DEFAULT_HIDDEN_PANEL_TRACK_STATS.contains(PlayerPreferences.PANEL_STAT_INFO))
        assertFalse(PlayerPreferences.DEFAULT_HIDDEN_PANEL_TRACK_STATS.contains(PlayerPreferences.PANEL_STAT_PLAYS))
        assertFalse(PlayerPreferences.DEFAULT_HIDDEN_PANEL_TRACK_STATS.contains(PlayerPreferences.PANEL_STAT_LIKES))
        assertFalse(PlayerPreferences.DEFAULT_HIDDEN_PANEL_TRACK_STATS.contains(PlayerPreferences.PANEL_STAT_REPOSTS))
        assertFalse(PlayerPreferences.DEFAULT_HIDDEN_PANEL_TRACK_STATS.contains(PlayerPreferences.PANEL_STAT_COMMENTS))
    }

    @Test
    fun testHiddenTrackStatsPersistence() {
        val prefs = PlayerPreferences()
        // Save initial state to restore later
        val initialHidden = prefs.getHiddenPanelTrackStats()
        try {
            prefs.setHiddenPanelTrackStats(emptySet())
            assertEquals(emptySet<String>(), prefs.getHiddenPanelTrackStats())

            val hiddenSet = setOf(
                PlayerPreferences.PANEL_STAT_REPOSTS,
                PlayerPreferences.PANEL_STAT_COMMENTS,
                PlayerPreferences.PANEL_STAT_INFO
            )
            prefs.setHiddenPanelTrackStats(hiddenSet)
            val result = prefs.getHiddenPanelTrackStats()

            assertTrue(result.contains(PlayerPreferences.PANEL_STAT_REPOSTS))
            assertTrue(result.contains(PlayerPreferences.PANEL_STAT_COMMENTS))
            assertTrue(result.contains(PlayerPreferences.PANEL_STAT_INFO))
            assertFalse(result.contains(PlayerPreferences.PANEL_STAT_PLAYS))
            assertFalse(result.contains(PlayerPreferences.PANEL_STAT_LIKES))
        } finally {
            prefs.setHiddenPanelTrackStats(initialHidden)
        }
    }

    @Test
    fun testFormatReleaseDatePatterns() {
        // ISO 8601 date-time with millis
        val formatted1 = formatReleaseDate("2024-03-27T14:48:35.331Z")
        assertTrue(formatted1.contains("2024"))

        // Standard date yyyy-MM-dd
        val formatted2 = formatReleaseDate("2023-11-15")
        assertTrue(formatted2.contains("2023"))

        // Year and month only
        val formatted3 = formatReleaseDate("2022-05")
        assertTrue(formatted3.contains("2022"))

        // Year only
        val formatted4 = formatReleaseDate("2021")
        assertEquals("2021", formatted4)

        // Null or blank
        val formattedNull = formatReleaseDate(null)
        val formattedBlank = formatReleaseDate("")
        assertEquals(formattedNull, formattedBlank)
    }
}
