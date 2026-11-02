package com.alananasss.kittytune.data.stats

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

data class MusicPassport(
    val holderName: String,
    val passportNo: String,
    val issuedAtMs: Long,
    val avatarUrl: String?,
    val citizenships: List<Citizenship>,
    val visas: List<Visa>,
    val stamps: List<Stamp>,
    val totalMinutes: Long,
    val tracks: Int,
    val artists: Int,
    val longestStreakDays: Int,
    val travellerClass: String,
    val accentArgb: Int = 0xFF8E6BFF.toInt(),
) {
    data class Citizenship(val genre: String, val sharePercent: Int)
    data class Visa(val platform: String, val discoveries: Int, val since: String)
    data class Stamp(val label: String, val date: String)

    companion object {
        fun passportNumber(userId: Long, firstEventMs: Long, plays: Int): String {
            val year = if (firstEventMs > 0L) {
                Instant.ofEpochMilli(firstEventMs).atZone(ZoneId.systemDefault()).year
            } else {
                Instant.now().atZone(ZoneId.systemDefault()).year
            }
            val seed = ((userId * 31 + firstEventMs / 86_400_000L + plays) % 1_000_000L + 1_000_000L) % 1_000_000L
            return String.format(Locale.US, "KT\u00B7%d\u00B7%06d", year, seed)
        }
    }
}
