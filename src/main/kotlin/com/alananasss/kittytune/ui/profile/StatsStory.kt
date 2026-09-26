package com.alananasss.kittytune.ui.profile

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.stats.ListeningReport
import com.alananasss.kittytune.data.stats.ReportArtist
import com.alananasss.kittytune.data.stats.ReportPeriod
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn

/**
 * The span as a recap: a handful of big cards — the time, the artist, the track, when, and the habits — in
 * large type on the palette's colours. For looking back rather than looking up.
 */
@Composable
internal fun StoryStats(
    report: ListeningReport,
    period: ReportPeriod,
    onOpen: (StatsList) -> Unit,
    onTrackClick: (Long) -> Unit,
    onArtistClick: (ReportArtist) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            StoryCard(Brush.linearGradient(listOf(scheme.primaryContainer, scheme.tertiaryContainer)), scheme.onPrimaryContainer, index = 0) {
                Text(str("stats_story_you_listened"), style = MaterialTheme.typography.titleMedium)
                Text(
                    formatDurationShort(report.totalListenMs),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp, lineHeight = 70.sp),
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    str("stats_story_numbers", report.plays, report.uniqueTracks, report.uniqueArtists),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.clickable { onOpen(StatsList.PLAYS) },
                )
                report.change?.let {
                    Spacer(Modifier.height(12.dp))
                    ChangeChip(it, period)
                }
            }
        }
        report.topArtists.firstOrNull()?.let { artist ->
            item {
                StoryCard(Brush.linearGradient(listOf(scheme.secondaryContainer, scheme.primaryContainer)), scheme.onSecondaryContainer, index = 1) {
                    Text(str("stats_story_top_artist"), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatsCover(
                            artist.imageUrl,
                            Modifier.size(140.dp).clip(CircleShape).clickable { onArtistClick(artist) },
                            placeholder = Icons.Rounded.Person,
                        )
                        Spacer(Modifier.width(24.dp))
                        Column(Modifier.weight(1f)) {
                            Text(artist.name, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(formatDuration(artist.listenMs), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(12.dp))
                            report.topArtists.drop(1).take(4).forEachIndexed { index, other ->
                                Text(
                                    "${index + 2}. ${other.name}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { onArtistClick(other) }.padding(vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        report.topTracks.firstOrNull()?.let { track ->
            item {
                StoryCard(Brush.linearGradient(listOf(scheme.tertiaryContainer, scheme.secondaryContainer)), scheme.onTertiaryContainer, index = 2) {
                    Text(str("stats_story_top_track"), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatsCover(
                            track.artworkUrl,
                            Modifier.size(140.dp).clip(RoundedCornerShape(20.dp)).clickable(enabled = track.source == "soundcloud") { onTrackClick(track.trackId) },
                        )
                        Spacer(Modifier.width(24.dp))
                        Column(Modifier.weight(1f)) {
                            Text(track.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(track.artistName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(8.dp))
                            Text(str("stats_story_track_times", track.plays, formatDuration(track.listenMs)), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
        report.peakHour?.let { hour ->
            item {
                StoryCard(Brush.linearGradient(listOf(scheme.surfaceContainerHighest, scheme.primaryContainer)), scheme.onSurface, index = 3) {
                    Text(str("stats_story_when"), style = MaterialTheme.typography.titleMedium)
                    Text(hourLabel(hour), style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp, lineHeight = 70.sp), fontWeight = FontWeight.Black)
                    report.busiestWeekday?.let {
                        Text(str("listening_stats_busiest_day", weekdayName(it)), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        item {
            StoryCard(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary)), scheme.onPrimary, index = 4) {
                Text(str("stats_story_habits"), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    BigFigure(report.longestStreakDays.toString(), str("listening_stats_streak"))
                    BigFigure("${(report.completionRate * 100).toInt()} %", str("listening_stats_completion_rate"))
                    BigFigure(report.activeDays.toString(), str("stats_story_active_days"))
                }
            }
        }
    }
}

@Composable
private fun BigFigure(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** One recap card: a gradient, big type, and a short rise-in as it first appears. */
@Composable
private fun StoryCard(background: Brush, contentColor: Color, index: Int, content: @Composable ColumnScope.() -> Unit) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(420, delayMillis = 70 * index)) }
    Box(
        Modifier
            .widthIn(max = 880.dp)
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * 24.dp.toPx()
            }
            .clip(RoundedCornerShape(32.dp))
            .background(background)
            .padding(28.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Column(content = content)
        }
    }
}
