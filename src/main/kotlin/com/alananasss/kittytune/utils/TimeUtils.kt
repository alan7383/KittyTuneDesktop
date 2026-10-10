package com.alananasss.kittytune.utils

import com.alananasss.kittytune.core.str
import java.util.Locale

fun makeTimeString(duration: Long): String {
    val totalSeconds = duration / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        if (minutes > 0) {
            str("time_hours_minutes", hours, minutes)
        } else {
            str("time_hours", hours)
        }
    } else {
        // standard song format (03:45)
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

/**
 * Localized duration string formatted for remaining queue time:
 * - hours and minutes: e.g. "2h 14min", "2 ч. 14 мин.", "2 Std. 14 Min."
 * - minutes only: e.g. "45 min", "45 мин.", "45 Min."
 * - seconds under a minute: e.g. "30s", "30 с", "30 Sek."
 */
fun formatQueueDuration(durationMs: Long): String {
    if (durationMs <= 0L) return str("time_minutes", 0)
    val totalSeconds = (durationMs + 500) / 1000
    val totalMinutes = totalSeconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val seconds = totalSeconds % 60

    return when {
        hours > 0 -> {
            if (minutes > 0) {
                str("time_hours_minutes", hours, minutes)
            } else {
                str("time_hours", hours)
            }
        }
        totalMinutes > 0 -> {
            str("time_minutes", totalMinutes)
        }
        else -> {
            str("time_seconds", seconds)
        }
    }
}
