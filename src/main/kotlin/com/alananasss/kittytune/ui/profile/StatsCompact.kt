package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.stats.ListeningReport
import com.alananasss.kittytune.data.stats.ReportArtist
import com.alananasss.kittytune.data.stats.ReportPeriod
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
import kotlin.math.roundToInt

private const val COMPACT_ROWS = 10

/**
 * Everything at a glance: the numbers in one strip, a small activity strip, and the top ten tracks and artists
 * as tight tables side by side. For looking something up rather than for browsing.
 */
@Composable
internal fun CompactStats(
    report: ListeningReport,
    period: ReportPeriod,
    onOpen: (StatsList) -> Unit,
    onTrackClick: (Long) -> Unit,
    onArtistClick: (ReportArtist) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 760.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { NumbersStrip(report, period, onOpen) }
            item { ActivityStrip(report) }
            item {
                val tracks: @Composable (Modifier) -> Unit = { m ->
                    StatsCard(str("listening_stats_top_tracks"), m, action = { onOpen(StatsList.TRACKS) }) {
                        report.topTracks.take(COMPACT_ROWS).forEachIndexed { index, track ->
                            CompactRow(
                                rank = index + 1,
                                title = track.title,
                                subtitle = track.artistName,
                                trailing = formatDurationShort(track.listenMs),
                                onClick = if (track.source == "soundcloud") ({ onTrackClick(track.trackId) }) else null,
                            )
                        }
                    }
                }
                val artists: @Composable (Modifier) -> Unit = { m ->
                    StatsCard(str("listening_stats_top_artists"), m, action = { onOpen(StatsList.ARTISTS) }) {
                        report.topArtists.take(COMPACT_ROWS).forEachIndexed { index, artist ->
                            CompactRow(
                                rank = index + 1,
                                title = artist.name,
                                subtitle = null,
                                trailing = formatDurationShort(artist.listenMs),
                                imageUrl = artist.imageUrl,
                                onClick = { onArtistClick(artist) },
                            )
                        }
                    }
                }
                if (isWide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(IntrinsicSize.Max)) {
                        tracks(Modifier.weight(1f).fillMaxHeight())
                        artists(Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        tracks(Modifier.fillMaxWidth())
                        artists(Modifier.fillMaxWidth())
                    }
                }
            }
            item { HabitsLine(report) }
        }
    }
}

@Composable
private fun NumbersStrip(report: ListeningReport, period: ReportPeriod, onOpen: (StatsList) -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 520.dp) {
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    NumberCell(formatDurationShort(report.totalListenMs), str("listening_stats_time_listened"), Modifier.weight(1f), null)
                    NumberCell(report.plays.toString(), str("listening_stats_plays"), Modifier.weight(1f)) { onOpen(StatsList.PLAYS) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    NumberCell(report.uniqueTracks.toString(), str("listening_stats_unique_tracks"), Modifier.weight(1f)) { onOpen(StatsList.TRACKS) }
                    NumberCell(report.uniqueArtists.toString(), str("listening_stats_unique_artists"), Modifier.weight(1f)) { onOpen(StatsList.ARTISTS) }
                }
            }
            return@BoxWithConstraints
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            NumberCell(formatDurationShort(report.totalListenMs), str("listening_stats_time_listened"), Modifier.weight(1.4f), null)
            VerticalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
            NumberCell(report.plays.toString(), str("listening_stats_plays"), Modifier.weight(1f)) { onOpen(StatsList.PLAYS) }
            VerticalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
            NumberCell(report.uniqueTracks.toString(), str("listening_stats_unique_tracks"), Modifier.weight(1f)) { onOpen(StatsList.TRACKS) }
            VerticalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
            NumberCell(report.uniqueArtists.toString(), str("listening_stats_unique_artists"), Modifier.weight(1f)) { onOpen(StatsList.ARTISTS) }
        }
        }
    }
    report.change?.let {
        Box(Modifier.padding(top = 8.dp, start = 4.dp)) { ChangeChip(it, period) }
    }
}

@Composable
private fun NumberCell(value: String, label: String, modifier: Modifier, onClick: (() -> Unit)?) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A thin row of bars, one per day, today in the primary colour. */
@Composable
private fun ActivityStrip(report: ListeningReport) {
    val values = report.activity.map { it.listenMs }
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val current = report.activity.indexOfFirst { isCurrent(it) }
    val strong = MaterialTheme.colorScheme.primary
    val soft = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val empty = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                str("listening_stats_activity_avg_day", formatDurationShort(report.averagePerDayMs)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Canvas(Modifier.fillMaxWidth().height(40.dp)) {
                if (values.isEmpty()) return@Canvas
                val slot = size.width / values.size
                val barWidth = (slot * 0.7f).coerceAtMost(20.dp.toPx())
                values.forEachIndexed { index, value ->
                    val left = slot * index + (slot - barWidth) / 2
                    val height = if (value > 0) (size.height * value / max).coerceAtLeast(3.dp.toPx()) else 3.dp.toPx()
                    val color = when {
                        value <= 0 -> empty
                        index == current -> strong
                        else -> soft
                    }
                    drawRoundRect(color, Offset(left, size.height - height), Size(barWidth, height), CornerRadius(barWidth / 3))
                }
            }
        }
    }
}

@Composable
private fun CompactRow(
    rank: Int,
    title: String,
    subtitle: String?,
    trailing: String,
    imageUrl: String? = null,
    onClick: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(RoundedCornerShape(12.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            rank.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(26.dp),
        )
        if (imageUrl != null || subtitle == null) {
            StatsCover(imageUrl, Modifier.size(28.dp).clip(CircleShape), placeholder = Icons.Rounded.Person)
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
    }
}

/** The habits as one line of chips. */
@Composable
private fun HabitsLine(report: ListeningReport) {
    val chips = buildList {
        add(str("listening_stats_completion_rate") + ": ${(report.completionRate * 100).roundToInt()} %")
        add(str("listening_stats_skip_rate") + ": ${(report.skipRate * 100).roundToInt()} %")
        add(str("listening_stats_streak") + ": ${report.longestStreakDays}")
        report.peakHour?.let { add(str("listening_stats_peak_hour", hourLabel(it))) }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEach { text ->
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
    }
}
