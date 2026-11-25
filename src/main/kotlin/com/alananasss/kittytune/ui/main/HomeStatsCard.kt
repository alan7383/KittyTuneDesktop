package com.alananasss.kittytune.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.ListeningStatsRepository
import com.alananasss.kittytune.data.local.TopArtistResult
import com.alananasss.kittytune.data.stats.ListeningReports
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle

/**
 * The week's listening, on the home page (issue #33; redesigned in round 2 of the tester's list, item 10.1).
 *
 * Statistics were recorded and had a screen, but nothing led to it. The card is what listening this week adds up
 * to at a glance: the time as the big figure, a bar for each of the last seven days, how many songs and artists
 * it was, and the artist at the top, on a tonal surface from the theme. Absent until there
 * is something to show: a card reading zero minutes on a fresh install is noise.
 */
private val STATS_CARD_PLACEHOLDER_HEIGHT = 170.dp

@Composable
internal fun ListeningStatsCard(navController: NavController) {
    val repo = ListeningStatsRepository
    val revision by repo.revision.collectAsState()
    val summary by produceState<HomeStatsSummary?>(initialValue = null, key1 = revision) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val zone = ZoneId.systemDefault()
                val now = System.currentTimeMillis()
                val window = ListeningReports.recentWeek(now, zone)
                val snapshot = repo.getSnapshot(window.startMs, window.endMs)
                val events = repo.getRecentEventsBetween(window.startMs, window.endMs, Int.MAX_VALUE)
                HomeStatsSummary(
                    listenedMs = snapshot.totalListenMs,
                    uniqueTracks = snapshot.uniqueTracks,
                    uniqueArtists = snapshot.uniqueArtists,
                    topArtist = repo.getTopArtistsBetween(window.startMs, window.endMs).firstOrNull(),
                    days = HomeStatsDays.lastSeven(
                        events.map { it.timestamp to it.listenDurationMs },
                        today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate(),
                        zone = zone,
                    ),
                )
            }.getOrNull()
        }
    }

    // While the numbers are read, a card-sized blank holds the place: the card used to arrive late and push everything under
    // it down, which showed as the page jumping while it was being scrolled.
    val stats = summary ?: run {
        Box(Modifier.fillMaxWidth().height(STATS_CARD_PLACEHOLDER_HEIGHT).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f)))
        return
    }
    if (stats.listenedMs <= 0L) return
    StatsCardContent(stats, onClick = { navController.navigate("listening_stats") })
}

@Composable
internal fun StatsCardContent(stats: HomeStatsSummary, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    str("listening_stats_title"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(2.dp))
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        listenedLabel(stats.listenedMs),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                    )
                    Text(
                        str("listening_stats_period_week_current"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
                DayBars(stats.days, scheme.primary, Modifier.width(DAY_BARS_WIDTH).height(DAY_BARS_HEIGHT))
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FigurePill(stats.uniqueTracks.toString(), str("listening_stats_unique_tracks"))
                FigurePill(stats.uniqueArtists.toString(), str("listening_stats_unique_artists"))
                stats.topArtist?.let { artist ->
                    Spacer(Modifier.weight(1f))
                    AsyncImage(
                        model = artist.artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(26.dp).clip(CircleShape).background(scheme.surfaceVariant),
                    )
                    Text(
                        artist.artistName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 160.dp),
                    )
                }
            }
        }
    }
}

/** A number and what it counts, in one small pill. */
@Composable
private fun FigurePill(value: String, label: String) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = CircleShape, color = scheme.surfaceContainerHighest, contentColor = scheme.onSurface) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** A bar for each of the last seven days, today's last and brightest, the others a step below it. */
@Composable
private fun DayBars(days: List<HomeStatsDays.Day>, color: Color, modifier: Modifier = Modifier) {
    val longest = (days.maxOfOrNull { it.listenedMs } ?: 0L).coerceAtLeast(1L)
    Canvas(modifier) {
        if (days.isEmpty()) return@Canvas
        val gap = 6.dp.toPx()
        val barWidth = (size.width - gap * (days.size - 1)) / days.size
        val radius = CornerRadius(barWidth / 2f)
        days.forEachIndexed { i, day ->
            val share = day.listenedMs.toFloat() / longest
            // A day without listening is a stub, so the row still reads as seven days.
            val height = (size.height * share).coerceAtLeast(barWidth)
            val alpha = if (i == days.lastIndex) 0.95f else 0.38f
            drawRoundRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(i * (barWidth + gap), size.height - height),
                size = Size(barWidth, height),
                cornerRadius = radius,
            )
        }
    }
}

/** What the card needs, fetched in one pass so the card appears whole rather than in pieces. */
internal data class HomeStatsSummary(
    val listenedMs: Long,
    val uniqueTracks: Int,
    val uniqueArtists: Int,
    val topArtist: TopArtistResult?,
    val days: List<HomeStatsDays.Day>,
)

/** The listening of the last seven days, one entry per day, oldest first, today last. */
internal object HomeStatsDays {

    data class Day(val date: LocalDate, val listenedMs: Long)

    /**
     * @param events (when it happened in epoch millis, how long was listened) of the week's plays.
     */
    fun lastSeven(
        events: List<Pair<Long, Long>>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Day> {
        val byDay = events.groupBy { (at, _) -> Instant.ofEpochMilli(at).atZone(zone).toLocalDate() }
            .mapValues { (_, plays) -> plays.sumOf { it.second } }
        return (6 downTo 0L).map { back ->
            val date = today.minusDays(back)
            Day(date, byDay[date] ?: 0L)
        }
    }
}

private val DAY_BARS_WIDTH = 124.dp
private val DAY_BARS_HEIGHT = 56.dp
