package com.alananasss.kittytune.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.alananasss.kittytune.core.LocalWindowSeen
import com.alananasss.kittytune.core.NamedPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Time spent in each game, one bucket per day and per game, so the statistics can show it for the same week,
 * month or year as the listening figures. Pure: the store is handed in, the tests use a plain map.
 */
internal class PlaytimeLedger(
    private val read: () -> Map<String, Long>,
    private val write: (key: String, value: Long) -> Unit,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    fun add(game: GameId, ms: Long, atMs: Long = System.currentTimeMillis()) {
        if (ms <= 0L) return
        val key = keyFor(game, Instant.ofEpochMilli(atMs).atZone(zone()).toLocalDate())
        write(key, (read()[key] ?: 0L) + ms)
    }

    /** Per game, for the days that overlap [startMs, endMs). */
    fun totals(startMs: Long = Long.MIN_VALUE, endMs: Long = Long.MAX_VALUE): Map<GameId, Long> {
        val first = if (startMs == Long.MIN_VALUE) LocalDate.MIN else Instant.ofEpochMilli(startMs).atZone(zone()).toLocalDate()
        val last = if (endMs == Long.MAX_VALUE) LocalDate.MAX else Instant.ofEpochMilli(endMs - 1).atZone(zone()).toLocalDate()
        val result = mutableMapOf<GameId, Long>()
        for ((key, value) in read()) {
            val (game, day) = parse(key) ?: continue
            if (day < first || day > last) continue
            result[game] = (result[game] ?: 0L) + value
        }
        return result
    }

    companion object {
        private const val PREFIX = "d:"
        fun keyFor(game: GameId, day: LocalDate) = "$PREFIX$day:${game.name}"
        fun parse(key: String): Pair<GameId, LocalDate>? {
            if (!key.startsWith(PREFIX)) return null
            val parts = key.removePrefix(PREFIX).split(':')
            if (parts.size != 2) return null
            val day = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return null
            val game = GameId.entries.firstOrNull { it.name == parts[1] } ?: return null
            return game to day
        }
    }
}

/** The app's own ledger, kept in its prefs file; [version] ticks on every write so the stats card refreshes. */
internal object GamePlaytime {
    private val prefs by lazy { NamedPrefs("game_playtime") }
    var version by mutableIntStateOf(0)
        private set

    private val ledger = PlaytimeLedger(
        read = { prefs.all().mapValues { (_, v) -> (v as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull() ?: 0L } },
        write = { key, value -> prefs.putLong(key, value) },
    )

    fun add(game: GameId, ms: Long) {
        ledger.add(game, ms)
        version++
    }

    fun totals(startMs: Long = Long.MIN_VALUE, endMs: Long = Long.MAX_VALUE): Map<GameId, Long> = ledger.totals(startMs, endMs)

    // ─── Activity ────────────────────────────────────────────────────────────────────────
    @Volatile private var lastInputMs = 0L

    /** Called on any click, drag or key inside a game. */
    fun touch() {
        lastInputMs = System.currentTimeMillis()
    }

    internal fun recentlyActive(now: Long = System.currentTimeMillis()): Boolean = now - lastInputMs < IDLE_AFTER_MS

    private const val IDLE_AFTER_MS = 45_000L
}

/**
 * Counts the time [game] is really played: while the window is seen and [active] holds — and, unless the game moves by
 * itself ([needsInput] false, like the snake), only while the player has touched it in the last 45 seconds, so a
 * board left open on the side does not count as an afternoon of play.
 */
@Composable
internal fun TrackGamePlaytime(game: GameId, active: Boolean, needsInput: Boolean = true) {
    val isActive by rememberUpdatedState(active)
    val seen by rememberUpdatedState(LocalWindowSeen.current)
    val needs by rememberUpdatedState(needsInput)
    LaunchedEffect(game) {
        var pending = 0L
        var last = System.nanoTime()
        try {
            while (true) {
                delay(1_000)
                val now = System.nanoTime()
                val elapsed = ((now - last) / 1_000_000L).coerceIn(0L, 2_000L)
                last = now
                if (isActive && seen && (!needs || GamePlaytime.recentlyActive())) pending += elapsed
                if (pending >= 15_000L) {
                    GamePlaytime.add(game, pending)
                    pending = 0L
                }
            }
        } finally {
            if (pending > 0L) withContext(NonCancellable) { GamePlaytime.add(game, pending) }
        }
    }
}
