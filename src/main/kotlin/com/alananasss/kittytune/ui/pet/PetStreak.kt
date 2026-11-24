package com.alananasss.kittytune.ui.pet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alananasss.kittytune.data.ListeningStatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The cat's feeding streak, the way a Duolingo or TikTok streak works: every day you listen to enough
 * music the cat is fed and the streak grows. Miss a day and the streak is not gone at once, it can be
 * restored (a few times a month); stay away longer and the cat leaves, until music brings it back.
 *
 * The days come from the listening history itself (not a counter of its own), so a streak follows the
 * listener across devices through sync and survives a reinstall with a restored backup.
 */
object PetStreak {

    /** Music a day needs for the cat to count as fed. */
    const val DAILY_GOAL_MS = 10 * 60_000L

    /** Missed days that can still be restored; one more and the streak is lost and the cat leaves. */
    const val RESTORABLE_DAYS = 2

    /** Restores allowed in any rolling month. */
    const val RESTORES_PER_MONTH = 3

    private const val HISTORY_DAYS = 800L
    private const val KEY_RESTORED = "streak_restored_days"
    private const val KEY_RESTORES = "streak_restores_at"

    enum class Mood {
        /** Fed today. */
        FED,
        /** Streak alive, today not fed yet. */
        HUNGRY,
        /** A day (or two) missed: the streak waits to be restored. */
        RESTORABLE,
        /** Too long without music: the cat has left. */
        GONE,
        /** Never fed yet (new cat). */
        NEW,
    }

    data class Status(
        val mood: Mood,
        /** Current streak; for [Mood.RESTORABLE] the streak a restore would bring back. */
        val days: Int,
        val todayMs: Long,
        val missedDays: Int = 0,
        val restoresLeft: Int = RESTORES_PER_MONTH,
    ) {
        val goalProgress: Float get() = (todayMs.toFloat() / DAILY_GOAL_MS).coerceIn(0f, 1f)
        val canRestore: Boolean get() = mood == Mood.RESTORABLE && restoresLeft > 0
    }

    /**
     * The streak for [today] given how much was listened each day and the days already restored.
     * Kept free of storage and clocks so it can be checked without the app.
     */
    fun compute(
        listened: Map<LocalDate, Long>,
        restored: Set<LocalDate>,
        today: LocalDate,
        restoresLeft: Int = RESTORES_PER_MONTH,
    ): Status {
        fun fed(day: LocalDate) = (listened[day] ?: 0L) >= DAILY_GOAL_MS || day in restored
        val todayMs = listened[today] ?: 0L

        fun runEndingAt(day: LocalDate): Int {
            var n = 0
            var d = day
            while (fed(d) && n <= HISTORY_DAYS) { n++; d = d.minusDays(1) }
            return n
        }

        if (fed(today)) return Status(Mood.FED, runEndingAt(today), todayMs, restoresLeft = restoresLeft)

        val yesterday = today.minusDays(1)
        if (fed(yesterday)) return Status(Mood.HUNGRY, runEndingAt(yesterday), todayMs, restoresLeft = restoresLeft)

        // The last day the cat was fed, if any in the window the history covers.
        var last = yesterday.minusDays(1)
        val oldest = today.minusDays(HISTORY_DAYS)
        while (!fed(last) && last.isAfter(oldest)) last = last.minusDays(1)
        if (!fed(last)) return Status(Mood.NEW, 0, todayMs, restoresLeft = restoresLeft)

        // Days strictly between the last fed day and today (today itself is not over yet).
        val missed = ChronoUnit.DAYS.between(last, today).toInt() - 1
        return if (missed <= RESTORABLE_DAYS) {
            Status(Mood.RESTORABLE, runEndingAt(last), todayMs, missedDays = missed, restoresLeft = restoresLeft)
        } else {
            Status(Mood.GONE, 0, todayMs, missedDays = missed, restoresLeft = restoresLeft)
        }
    }

    /** The days a restore fills in: every missed day between the last fed one and today. */
    fun daysToRestore(status: Status, today: LocalDate): List<LocalDate> =
        if (status.mood != Mood.RESTORABLE) emptyList()
        else (1..status.missedDays).map { today.minusDays(it.toLong()) }

    // ---- live state

    var status by mutableStateOf(Status(Mood.NEW, 0, 0L))
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watcher: Job? = null

    /** Follows the listening history (and midnight) for as long as the app runs. Idempotent. */
    fun start() {
        if (watcher?.isActive == true) return
        watcher = scope.launch {
            launch { ListeningStatsRepository.revision.collectLatest { refreshNow() } }
            // Midnight turns "fed" into "hungry" without any listen happening.
            while (isActive) {
                delay(60_000L)
                refreshNow()
            }
        }
    }

    fun refresh() {
        scope.launch { refreshNow() }
    }

    private suspend fun refreshNow() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val since = today.minusDays(HISTORY_DAYS).atStartOfDay(zone).toInstant().toEpochMilli()
        val listened = runCatching { ListeningStatsRepository.init(); ListeningStatsRepository.getEvents(since) }.getOrDefault(emptyList())
            .groupingBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
            .fold(0L) { acc, e -> acc + e.listenDurationMs.coerceAtLeast(0L) }
        status = compute(listened, restoredDays(), today, restoresLeft())
    }

    /** Brings a missed day or two back, if a restore is left this month. */
    fun restore(): Boolean {
        val current = status
        if (!current.canRestore) return false
        val today = LocalDate.now(ZoneId.systemDefault())
        val days = daysToRestore(current, today)
        if (days.isEmpty()) return false
        val p = PetConfig.prefs
        val kept = restoredDays().filter { it.isAfter(today.minusDays(HISTORY_DAYS)) }
        p.putString(KEY_RESTORED, (kept + days).distinct().joinToString(","))
        p.putString(KEY_RESTORES, (recentRestores() + System.currentTimeMillis()).joinToString(","))
        refresh()
        return true
    }

    private fun restoredDays(): Set<LocalDate> =
        PetConfig.prefs.getString(KEY_RESTORED, null).orEmpty().split(',')
            .mapNotNull { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }.toSet()

    private fun recentRestores(): List<Long> {
        val monthAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        return PetConfig.prefs.getString(KEY_RESTORES, null).orEmpty().split(',')
            .mapNotNull { it.trim().toLongOrNull() }.filter { it > monthAgo }
    }

    private fun restoresLeft(): Int = (RESTORES_PER_MONTH - recentRestores().size).coerceAtLeast(0)
}
