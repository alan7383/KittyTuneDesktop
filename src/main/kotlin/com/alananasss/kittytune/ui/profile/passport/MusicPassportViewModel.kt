package com.alananasss.kittytune.ui.profile.passport

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.core.AppInstance
import com.alananasss.kittytune.core.NamedPrefs
import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.LikeRepository
import com.alananasss.kittytune.data.ListeningStatsRepository
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.data.local.ListeningStatsEvent
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.data.stats.ListenRules
import com.alananasss.kittytune.data.stats.ListeningRecaps
import com.alananasss.kittytune.data.stats.ListeningReport
import com.alananasss.kittytune.data.stats.ListeningReports
import com.alananasss.kittytune.data.stats.MusicPassport
import com.alananasss.kittytune.data.stats.ReportPeriod
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.domain.getHighResAvatarUrl
import com.alananasss.kittytune.domain.isDefaultAvatar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MusicPassportViewModel(private val initialUser: User? = null) : ViewModel() {

    var passport by mutableStateOf<MusicPassport?>(null)
        private set

    var bitmap by mutableStateOf<ImageBitmap?>(null)
        private set

    var awtImage by mutableStateOf<BufferedImage?>(null)
        private set

    var isLoading by mutableStateOf(true)
        private set

    private var loadJob: Job? = null
    private var renderJob: Job? = null
    private var renderedFor: Pair<MusicPassport, Int>? = null
    private var backfilled = false

    init {
        load()
        viewModelScope.launch {
            ListeningStatsRepository.revision.drop(1).collect { load() }
        }
    }

    fun renderIfNeeded(accentArgb: Int) {
        val current = passport ?: return
        val key = current to accentArgb
        if (renderedFor == key && bitmap != null && awtImage != null) return
        renderedFor = key
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            val colored = current.copy(accentArgb = accentArgb)
            val renderedAwt = MusicPassportRender.render(colored)
            awtImage = renderedAwt
            bitmap = withContext(Dispatchers.Default) { renderedAwt.toComposeImageBitmap() }
        }
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val zone = ZoneId.systemDefault()
                val now = System.currentTimeMillis()
                val first = ListeningStatsRepository.getOldestEventAt()
                val window = ListeningReports.windowFor(ReportPeriod.ALL_TIME, now, zone, first)
                val rows = ListeningStatsRepository.getRecentEventsBetween(window.startMs, window.endMs, EVENT_LIMIT)
                val report = ListeningReports.build(
                    period = ReportPeriod.ALL_TIME,
                    window = window,
                    events = rows,
                    previousListenMs = null,
                    zone = zone,
                    topLimit = 10,
                    nowMs = now,
                )

                val resolvedUser = resolveUser()
                passport = buildPassport(rows, report, first ?: now, now, zone, resolvedUser)
                isLoading = false

                if (!backfilled && backfillGenres(rows)) {
                    backfilled = true
                    val updated = ListeningStatsRepository.getRecentEventsBetween(window.startMs, window.endMs, EVENT_LIMIT)
                    val updatedReport = ListeningReports.build(
                        period = ReportPeriod.ALL_TIME,
                        window = window,
                        events = updated,
                        previousListenMs = null,
                        zone = zone,
                        topLimit = 10,
                        nowMs = now,
                    )
                    passport = buildPassport(updated, updatedReport, first ?: now, now, zone, resolvedUser)
                }
            }
        }
    }

    private suspend fun resolveUser(): User? {
        if (initialUser != null) return initialUser
        if (!TokenManager.isGuestMode() && !TokenManager.getAccessToken().isNullOrEmpty()) {
            return runCatching { RetrofitClient.create().getMe() }.getOrNull()
        }
        return null
    }

    private fun buildPassport(
        rows: List<ListeningStatsEvent>,
        report: ListeningReport,
        firstEventMs: Long,
        nowMs: Long,
        zone: ZoneId,
        user: User?,
    ): MusicPassport {
        val prefs = PlayerPreferences()
        val holder = user?.username?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(user?.firstName, user?.lastName).joinToString(" ").trim().takeIf { it.isNotBlank() }
            ?: Prefs.getString("cached_name", null)?.takeIf { it.isNotBlank() }
            ?: prefs.getCachedUsername()?.takeIf { it.isNotBlank() }
            ?: str("passport_default_holder")

        val rawAvatar = user?.avatarUrl?.takeIf { !it.isDefaultAvatar() }
            ?: Prefs.getString("cached_avatar_url", null)?.takeIf { !it.isDefaultAvatar() }
        val avatar = rawAvatar?.getHighResAvatarUrl()?.replace("-large", "-t500x500")
            ?: report.topArtists.firstOrNull()?.imageUrl
            ?: report.topTracks.firstOrNull()?.artworkUrl

        val userId = user?.id?.takeIf { it != 0L } ?: prefs.getCachedUserId()
        val plays = rows.filter { ListenRules.countsAsPlay(it.listenDurationMs, it.trackDurationMs) }
        val citizenships = ListeningRecaps.genres(plays, limit = 4).map { g ->
            MusicPassport.Citizenship(
                genre = g.name,
                sharePercent = (g.share * 100f).toInt().coerceIn(1, 100),
            )
        }
        val visas = visas(plays, zone)
        val stamps = stamps(rows, plays, report, zone)

        return MusicPassport(
            holderName = holder,
            passportNo = MusicPassport.passportNumber(userId, firstEventMs, report.plays),
            issuedAtMs = nowMs,
            avatarUrl = avatar,
            citizenships = citizenships,
            visas = visas,
            stamps = stamps,
            totalMinutes = report.totalListenMs / 60_000L,
            tracks = report.uniqueTracks,
            artists = report.uniqueArtists,
            longestStreakDays = report.longestStreakDays,
            travellerClass = travellerClass(report),
        )
    }

    private fun visas(plays: List<ListeningStatsEvent>, zone: ZoneId): List<MusicPassport.Visa> {
        val known = setOf("soundcloud", "youtube", "spotify", "tidal", "deezer", "qobuz", "local")
        return plays.groupBy { row ->
            val src = row.source.lowercase(Locale.ROOT)
            known.firstOrNull { src.contains(it) } ?: src.ifBlank { "soundcloud" }
        }.mapNotNull { (source, list) ->
            val tracks = list.mapTo(HashSet()) { it.trackId }.size
            if (tracks == 0) return@mapNotNull null
            val firstMs = list.minOf { it.timestamp }
            val year = Instant.ofEpochMilli(firstMs).atZone(zone).year.toString()
            MusicPassport.Visa(
                platform = platformDisplay(source),
                discoveries = tracks,
                since = year,
            )
        }.sortedByDescending { it.discoveries }
    }

    private fun platformDisplay(source: String): String = when (source) {
        "soundcloud" -> "SOUNDCLOUD"
        "youtube" -> "YOUTUBE"
        "spotify" -> "SPOTIFY"
        "tidal" -> "TIDAL"
        "deezer" -> "DEEZER"
        "qobuz" -> "QOBUZ"
        "local" -> "LOCAL"
        else -> source.uppercase(Locale.ROOT)
    }

    private fun stamps(
        rows: List<ListeningStatsEvent>,
        plays: List<ListeningStatsEvent>,
        report: ListeningReport,
        zone: ZoneId,
    ): List<MusicPassport.Stamp> {
        val achPrefs = NamedPrefs("achievements_prefs")
        val format = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
        val sortedRows = rows.sortedBy { it.timestamp }
        val sortedPlays = plays.sortedBy { it.timestamp }
        val derived = deriveAchievements(sortedRows, sortedPlays, report, zone)

        return STAMP_DEFINITIONS.mapNotNull { def ->
            val prefUnlocked = achPrefs.getBoolean("unlocked_${def.id}", false)
            val prefTime = achPrefs.getLong("time_${def.id}", 0L)
            val derivedTime = derived[def.id]
            if (!prefUnlocked && derivedTime == null) return@mapNotNull null

            val unlockedAt = when {
                prefTime > 0L && derivedTime != null -> minOf(prefTime, derivedTime)
                prefTime > 0L -> prefTime
                derivedTime != null -> derivedTime
                else -> 0L
            }
            val date = if (unlockedAt > 0L) {
                Instant.ofEpochMilli(unlockedAt).atZone(zone).format(format).uppercase(Locale.ENGLISH)
            } else {
                "KITTYTUNE"
            }
            Triple(def.xpReward, str(def.titleKey), date)
        }
            .sortedByDescending { it.first }
            .take(4)
            .map { (_, title, date) -> MusicPassport.Stamp(title, date) }
    }

    /**
     * Evaluates the achievement definitions against the listener's real desktop history so unlocked
     * milestones appear with the exact date they were earned even without Android's runtime counter prefs.
     */
    private fun deriveAchievements(
        sortedRows: List<ListeningStatsEvent>,
        sortedPlays: List<ListeningStatsEvent>,
        report: ListeningReport,
        zone: ZoneId,
    ): Map<String, Long> {
        val unlocked = HashMap<String, Long>()

        // 1. Cumulative listening time milestones (target in seconds)
        val timeTargets = listOf(
            "time_1h" to 3_600L,
            "time_24h" to 86_400L,
            "time_100h" to 360_000L,
            "time_500h" to 1_800_000L,
            "time_1000h" to 3_600_000L,
            "time_2500h" to 9_000_000L,
            "time_5000h" to 18_000_000L,
            "time_10000h" to 36_000_000L,
        )
        var cumMs = 0L
        var nextTimeIdx = 0
        for (row in sortedRows) {
            cumMs += row.listenDurationMs
            while (nextTimeIdx < timeTargets.size && cumMs >= timeTargets[nextTimeIdx].second * 1_000L) {
                unlocked[timeTargets[nextTimeIdx].first] = row.timestamp
                nextTimeIdx++
            }
            if (nextTimeIdx >= timeTargets.size) break
        }

        // 2. Play volume milestones
        val playTargets = listOf(
            "plays_1" to 1,
            "plays_100" to 100,
            "plays_1000" to 1_000,
            "plays_5000" to 5_000,
            "plays_10000" to 10_000,
            "plays_20000" to 20_000,
            "plays_50000" to 50_000,
            "plays_100000" to 100_000,
        )
        for ((id, count) in playTargets) {
            if (sortedPlays.size >= count) {
                unlocked[id] = sortedPlays[count - 1].timestamp
            }
        }

        // 3. Consecutive day streak milestones
        val dayFirstTs = LinkedHashMap<LocalDate, Long>()
        for (row in sortedRows) {
            if (row.listenDurationMs <= 0L) continue
            val day = Instant.ofEpochMilli(row.timestamp).atZone(zone).toLocalDate()
            dayFirstTs.putIfAbsent(day, row.timestamp)
        }
        val activeDays = dayFirstTs.keys.sorted()
        val streakTargets = listOf(
            "streak_7" to 7,
            "streak_30" to 30,
            "streak_100" to 100,
            "streak_200" to 200,
            "streak_365" to 365,
            "streak_500" to 500,
        )
        var runLen = 0
        var prevDay: LocalDate? = null
        for (day in activeDays) {
            runLen = if (prevDay != null && prevDay.plusDays(1) == day) runLen + 1 else 1
            prevDay = day
            for ((id, target) in streakTargets) {
                if (runLen >= target && id !in unlocked) {
                    unlocked[id] = dayFirstTs[day] ?: 0L
                }
            }
        }

        // 4. Time-of-day loyalty habits & secret track names
        var earlyCount = 0
        var nightCount = 0
        var lunchCount = 0
        var luckyCount = 0
        val trackPlayCounts = HashMap<Long, Int>()
        for (play in sortedPlays) {
            val hour = Instant.ofEpochMilli(play.timestamp).atZone(zone).hour
            if (hour in 5..8 && ++earlyCount == 10) unlocked["early_bird"] = play.timestamp
            if ((hour >= 23 || hour < 5) && ++nightCount == 10) unlocked["night_owl"] = play.timestamp
            if (hour in 12..13 && ++lunchCount == 10) unlocked["lunch_break"] = play.timestamp

            val count = (trackPlayCounts[play.trackId] ?: 0) + 1
            trackPlayCounts[play.trackId] = count
            if (count == 50 && "obsessed_50" !in unlocked) unlocked["obsessed_50"] = play.timestamp
            if (count == 200 && "obsessed_200" !in unlocked) unlocked["obsessed_200"] = play.timestamp

            val title = play.trackTitle
            if ((title.contains("777") || title.contains("Lucky", ignoreCase = true)) && ++luckyCount == 7 && "lucky7" !in unlocked) {
                unlocked["lucky7"] = play.timestamp
            }
            if ((title.contains("Error", ignoreCase = true) || title.contains("Glitch", ignoreCase = true)) && "glitch" !in unlocked) {
                unlocked["glitch"] = play.timestamp
            }
        }

        // 5. Likes & Skips & Marathon
        val likes = LikeRepository.likedTracks.value
        val sortedLikes = likes.sortedBy { it.likedAt ?: Long.MAX_VALUE }
        if (sortedLikes.size >= 50) unlocked["liker_50"] = sortedLikes[49].likedAt ?: 0L
        if (sortedLikes.size >= 1_000) unlocked["liker_1000"] = sortedLikes[999].likedAt ?: 0L
        if (sortedLikes.size >= 5_000) unlocked["liker_5000"] = sortedLikes[4999].likedAt ?: 0L

        if (report.skips >= 100) unlocked["skipper_100"] = sortedRows.lastOrNull()?.timestamp ?: 0L
        if (report.skips >= 1_000) unlocked["skipper_1000"] = sortedRows.lastOrNull()?.timestamp ?: 0L
        if (ListeningRecaps.longestSession(sortedRows) >= 28_800_000L) {
            unlocked["marathon"] = sortedRows.lastOrNull()?.timestamp ?: 0L
        }

        return unlocked
    }

    private fun travellerClass(report: ListeningReport): String {
        val total = report.totalListenMs.coerceAtLeast(1L)
        val night = (0..4).sumOf { report.hours[it] }.toFloat() / total
        val dawn = (5..8).sumOf { report.hours[it] }.toFloat() / total
        val replay = if (report.uniqueTracks == 0) 0f else report.plays.toFloat() / report.uniqueTracks
        return str(
            when {
                night >= 0.25f -> "passport_class_night"
                dawn >= 0.30f -> "passport_class_dawn"
                report.uniqueArtists >= 150 -> "passport_class_explorer"
                replay >= 2.2f -> "passport_class_devoted"
                else -> "passport_class_wanderer"
            },
        )
    }

    private suspend fun backfillGenres(rows: List<ListeningStatsEvent>): Boolean {
        val tagged = rows.filter { !it.genre.isNullOrBlank() }.mapTo(HashSet()) { it.trackId }
        val missing = rows.asSequence()
            .filter { it.source == "soundcloud" && it.trackId > 0 && it.trackId !in tagged }
            .groupBy { it.trackId }
            .entries.sortedByDescending { (_, list) -> list.sumOf { it.listenDurationMs } }
            .take(100)
            .map { it.key }
        if (missing.isEmpty()) return false
        val api = runCatching { RetrofitClient.create() }.getOrNull() ?: return false
        return ListeningStatsRepository.backfillGenres(missing) { ids ->
            ids.chunked(50).flatMap { chunk ->
                runCatching { api.getTracksByIds(chunk.joinToString(",")) }.getOrDefault(emptyList())
            }.mapNotNull { track -> track.genre?.takeIf { it.isNotBlank() }?.let { track.id to it } }.toMap()
        }
    }

    private data class StampDef(val id: String, val titleKey: String, val xpReward: Int)

    private companion object {
        const val EVENT_LIMIT = 100_000

        val STAMP_DEFINITIONS = listOf(
            StampDef("time_1h", "ach_time_1h_title", 10),
            StampDef("time_24h", "ach_time_24h_title", 100),
            StampDef("time_100h", "ach_time_100h_title", 500),
            StampDef("time_500h", "ach_time_500h_title", 2000),
            StampDef("time_1000h", "ach_time_1000h_title", 5000),
            StampDef("time_2500h", "ach_time_2500h_title", 15000),
            StampDef("time_5000h", "ach_time_5000h_title", 50000),
            StampDef("time_10000h", "ach_time_10000h_title", 100000),
            StampDef("plays_1", "ach_plays_1_title", 5),
            StampDef("plays_100", "ach_plays_100_title", 50),
            StampDef("plays_1000", "ach_plays_1000_title", 500),
            StampDef("plays_5000", "ach_plays_5000_title", 2000),
            StampDef("plays_10000", "ach_plays_10000_title", 5000),
            StampDef("plays_20000", "ach_plays_20000_title", 10000),
            StampDef("plays_50000", "ach_plays_50000_title", 25000),
            StampDef("plays_100000", "ach_plays_100000_title", 100000),
            StampDef("streak_7", "ach_streak_7_title", 100),
            StampDef("streak_30", "ach_streak_30_title", 500),
            StampDef("streak_100", "ach_streak_100_title", 2000),
            StampDef("streak_200", "ach_streak_200_title", 5000),
            StampDef("streak_365", "ach_streak_365_title", 20000),
            StampDef("streak_500", "ach_streak_500_title", 50000),
            StampDef("early_bird", "ach_early_bird_title", 100),
            StampDef("night_owl", "ach_night_owl_title", 100),
            StampDef("lunch_break", "ach_lunch_break_title", 100),
            StampDef("liker_50", "ach_liker_50_title", 100),
            StampDef("liker_1000", "ach_liker_1000_title", 2000),
            StampDef("liker_5000", "ach_liker_5000_title", 10000),
            StampDef("playlist_creator", "ach_playlist_creator_title", 100),
            StampDef("playlist_god", "ach_playlist_god_title", 2500),
            StampDef("download_100", "ach_download_100_title", 500),
            StampDef("download_1000", "ach_download_1000_title", 5000),
            StampDef("skipper_100", "ach_skipper_100_title", 100),
            StampDef("skipper_1000", "ach_skipper_1000_title", 1000),
            StampDef("bass_addict", "ach_bass_addict_title", 2000),
            StampDef("speed_demon", "ach_speed_demon_title", 500),
            StampDef("social_star", "ach_social_star_title", 1000),
            StampDef("obsessed_50", "ach_obsessed_50_title", 1000),
            StampDef("obsessed_200", "ach_obsessed_200_title", 10000),
            StampDef("night_shift_pro", "ach_night_shift_pro_title", 15000),
            StampDef("marathon", "ach_marathon_title", 5000),
            StampDef("no_skip_50", "ach_no_skip_50_title", 1000),
            StampDef("developer", "ach_developer_title", 1000),
            StampDef("weekend_warrior", "ach_weekend_warrior_title", 200),
            StampDef("ghost", "ach_ghost_title", 5000),
            StampDef("lucky7", "ach_lucky7_title", 7777),
            StampDef("glitch", "ach_glitch_title", 1337),
        )
    }
}
