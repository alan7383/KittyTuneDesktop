package com.alananasss.kittytune.ui.main

import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals

/** The bars of the home statistics card: one per day, today last (round 2 of the tester's list, item 10.1). */
class HomeStatsDaysTest {

    private val utc = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 8)

    private fun at(date: LocalDate, hour: Int) = date.atTime(hour, 0).atZone(utc).toInstant().toEpochMilli()

    @Test
    fun `seven days, oldest first, today last, silent days at zero`() {
        val days = HomeStatsDays.lastSeven(emptyList(), today, utc)
        assertEquals(7, days.size)
        assertEquals(today.minusDays(6), days.first().date)
        assertEquals(today, days.last().date)
        assertEquals(0L, days.sumOf { it.listenedMs })
    }

    @Test
    fun `plays of one day add up, and each goes to its own day`() {
        val events = listOf(
            at(today, 9) to 120_000L, at(today, 21) to 60_000L,
            at(today.minusDays(2), 12) to 300_000L,
            at(today.minusDays(9), 12) to 999_000L, // a play from before the week is not in it
        )
        val days = HomeStatsDays.lastSeven(events, today, utc)
        assertEquals(180_000L, days.last().listenedMs)
        assertEquals(300_000L, days[4].listenedMs)
        assertEquals(480_000L, days.sumOf { it.listenedMs })
    }
}
