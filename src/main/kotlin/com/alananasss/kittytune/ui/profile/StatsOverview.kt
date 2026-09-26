package com.alananasss.kittytune.ui.profile

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.stats.ListeningReport
import com.alananasss.kittytune.data.stats.ReportArtist
import com.alananasss.kittytune.data.stats.ReportPeriod
import com.alananasss.kittytune.data.stats.ReportTrack
import com.alananasss.kittytune.ui.common.pressScale
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TOP_SHOWN = 5

/** The overview: the headline, two charts, the top lists and the habits. */
@Composable
internal fun OverviewStats(
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { SummaryCard(report, period, onOpen) }
            item {
                if (isWide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.height(IntrinsicSize.Max)) {
                        ActivityCard(report, period, Modifier.weight(1.6f).fillMaxHeight())
                        HoursCard(report, Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ActivityCard(report, period, Modifier.fillMaxWidth())
                        HoursCard(report, Modifier.fillMaxWidth())
                    }
                }
            }
            if (report.topTracks.isNotEmpty()) {
                item {
                    StatsCard(
                        title = str("listening_stats_top_tracks"),
                        action = if (report.topTracks.size > TOP_SHOWN) ({ onOpen(StatsList.TRACKS) }) else null,
                    ) {
                        val top = report.topTracks.first().listenMs
                        report.topTracks.take(TOP_SHOWN).forEachIndexed { index, track ->
                            RankedTrackRow(index + 1, track, share = track.listenMs.toFloat() / top) { onTrackClick(track.trackId) }
                        }
                    }
                }
            }
            if (report.topArtists.isNotEmpty()) {
                item {
                    StatsCard(
                        title = str("listening_stats_top_artists"),
                        action = if (report.topArtists.size > 6) ({ onOpen(StatsList.ARTISTS) }) else null,
                    ) {
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            report.topArtists.take(6).forEachIndexed { index, artist ->
                                ArtistTile(index + 1, artist, Modifier.weight(1f)) { onArtistClick(artist) }
                            }
                            repeat((6 - report.topArtists.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            item { HabitsGrid(report, isWide) }
        }
    }
}

// ─── Summary ─────────────────────────────────────────────────────

@Composable
private fun SummaryCard(report: ListeningReport, period: ReportPeriod, onOpen: (StatsList) -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        val isNarrow = maxWidth < 520.dp
        Column(Modifier.fillMaxWidth().padding(if (isNarrow) 18.dp else 24.dp)) {
            Text(
                str("listening_stats_time_listened"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            FlowRow(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatDuration(report.totalListenMs),
                    style = if (isNarrow) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    softWrap = false,
                )
                report.change?.let { ChangeChip(it, period) }
            }
            Spacer(Modifier.height(20.dp))
            val tiles: List<@Composable (Modifier) -> Unit> = listOf(
                { m -> SummaryTile(Icons.Rounded.PlayArrow, report.plays.toString(), str("listening_stats_plays"), m) { onOpen(StatsList.PLAYS) } },
                { m -> SummaryTile(Icons.Rounded.MusicNote, report.uniqueTracks.toString(), str("listening_stats_unique_tracks"), m) { onOpen(StatsList.TRACKS) } },
                { m -> SummaryTile(Icons.Rounded.People, report.uniqueArtists.toString(), str("listening_stats_unique_artists"), m) { onOpen(StatsList.ARTISTS) } },
            )
            if (isNarrow) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { tiles.forEach { it(Modifier.fillMaxWidth()) } }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { tiles.forEach { it(Modifier.weight(1f)) } }
            }
        }
        }
    }
}

@Composable
internal fun ChangeChip(change: Float, period: ReportPeriod) {
    val percent = (change * 100).roundToInt()
    val isUp = percent >= 0
    val text = (if (isUp) "+" else "−") + "${abs(percent)} %"
    val key = when (period) {
        ReportPeriod.WEEK -> "listening_stats_change_week"
        ReportPeriod.MONTH -> "listening_stats_change_month"
        else -> "listening_stats_change_year"
    }
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                if (isUp) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Text(str(key, text), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun SummaryTile(icon: ImageVector, value: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        modifier = modifier.pressScale(interaction),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

// ─── Charts ──────────────────────────────────────────────────────

/**
 * Listening time per day (per month for a year or all time), with a labelled scale, the average as a dashed
 * line and today marked — so a bar reads as "about forty minutes on Friday" rather than as a shape.
 */
@Composable
private fun ActivityCard(report: ListeningReport, period: ReportPeriod, modifier: Modifier) {
    val buckets = report.activity
    var hovered by remember(buckets) { mutableStateOf<Int?>(null) }
    val shown = hovered?.let { buckets.getOrNull(it) }
    val isMonthly = buckets.firstOrNull()?.isMonth == true
    val subtitle = when {
        shown != null -> "${bucketLabel(shown)} · ${formatDuration(shown.listenMs)}"
        isMonthly -> str("listening_stats_activity_avg_month", formatDurationShort(if (buckets.isEmpty()) 0 else buckets.sumOf { it.listenMs } / buckets.size))
        else -> buildString {
            append(str("listening_stats_activity_avg_day", formatDurationShort(report.averagePerDayMs)))
            report.busiestWeekday?.takeIf { period != ReportPeriod.WEEK || report.activeDays > 1 }?.let {
                append(" · ")
                append(str("listening_stats_busiest_day", weekdayName(it)))
            }
        }
    }
    StatsCard(
        title = str(if (isMonthly) "listening_stats_activity_months" else "listening_stats_activity_days"),
        subtitle = subtitle,
        modifier = modifier,
    ) {
        BarChart(
            values = buckets.map { it.listenMs },
            labels = buckets.mapIndexed { index, bucket -> axisLabel(bucket, index, buckets.size) },
            current = buckets.indexOfFirst { isCurrent(it) }.takeIf { it >= 0 },
            average = if (isMonthly || buckets.isEmpty()) null else report.averagePerDayMs,
            hovered = hovered,
            onHover = { hovered = it },
            modifier = Modifier.fillMaxWidth().height(180.dp),
        )
    }
}

/** Listening by hour of the day, and the same split into night, morning, afternoon and evening. */
@Composable
private fun HoursCard(report: ListeningReport, modifier: Modifier) {
    var hovered by remember(report) { mutableStateOf<Int?>(null) }
    val subtitle = hovered?.let { "${hourLabel(it)}–${hourLabel((it + 1) % 24)} · ${formatDuration(report.hours[it])}" }
        ?: report.peakHour?.let { str("listening_stats_peak_hour", hourLabel(it)) }
    StatsCard(title = str("listening_stats_hours"), subtitle = subtitle, modifier = modifier) {
        BarChart(
            values = report.hours,
            labels = List(24) { hour -> if (hour % 6 == 0) hourLabel(hour) else "" },
            current = null,
            average = null,
            hovered = hovered,
            onHover = { hovered = it },
            modifier = Modifier.fillMaxWidth().height(120.dp),
        )
        Spacer(Modifier.height(12.dp))
        PartsOfDay(report.partsOfDay)
    }
}

@Composable
private fun PartsOfDay(parts: List<Long>) {
    val total = parts.sum().coerceAtLeast(1L)
    val labels = listOf("listening_stats_night", "listening_stats_morning", "listening_stats_afternoon", "listening_stats_evening")
    val icons = listOf(Icons.Rounded.Bedtime, Icons.Rounded.WbTwilight, Icons.Rounded.WbSunny, Icons.Rounded.NightsStay)
    val top = parts.indexOf(parts.max())
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        parts.forEachIndexed { index, value ->
            val isTop = index == top && value > 0
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isTop) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f),
            ) {
                Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(icons[index], contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${(value * 100 / total)} %", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(str(labels[index]), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

/** A round step for the scale: 5, 10, 15, 30 minutes, then whole hours. */
private fun scaleStepMs(maxMs: Long): Long {
    val minute = 60_000L
    val steps = listOf(1, 2, 5, 10, 15, 30, 60, 120, 180, 240, 360, 600, 1200, 2400, 6000).map { it * minute }
    return steps.firstOrNull { maxMs / it <= 3 } ?: steps.last()
}

/**
 * Bars with rounded tops over a labelled scale. The hovered bar (or today's) is in the primary colour, the
 * rest softer; the dashed line is the average. The bars grow in when the data changes.
 */
@Composable
private fun BarChart(
    values: List<Long>,
    labels: List<String>,
    current: Int?,
    average: Long?,
    hovered: Int?,
    onHover: (Int?) -> Unit,
    modifier: Modifier,
) {
    val grow = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { grow.animateTo(1f, tween(500)) }
    val step = scaleStepMs(values.maxOrNull() ?: 0L)
    val lines = (((values.maxOrNull() ?: 0L) + step - 1) / step).coerceIn(1, 4).toInt()
    val top = step * lines
    val strong = MaterialTheme.colorScheme.primary
    val soft = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val averageColor = MaterialTheme.colorScheme.tertiary
    val labelStyle = MaterialTheme.typography.labelSmall
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val scaleLabels = (lines downTo 1).map { formatDurationShort(step * it) }

    Column(modifier) {
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Canvas(
                    Modifier.fillMaxSize().pointerInput(values.size) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val position = event.changes.firstOrNull()?.position
                                onHover(
                                    when {
                                        event.type == PointerEventType.Exit || position == null || values.isEmpty() -> null
                                        else -> (position.x / (size.width.toFloat() / values.size)).toInt().coerceIn(0, values.size - 1)
                                    }
                                )
                            }
                        }
                    },
                ) {
                    if (values.isEmpty()) return@Canvas
                    for (line in 0..lines) {
                        val y = size.height - size.height * line / lines
                        drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    }
                    val slot = size.width / values.size
                    val barWidth = (slot * 0.62f).coerceAtMost(28.dp.toPx())
                    val radius = CornerRadius(barWidth / 2, barWidth / 2)
                    val highlight = hovered ?: current
                    values.forEachIndexed { index, value ->
                        if (value <= 0) return@forEachIndexed
                        val left = slot * index + (slot - barWidth) / 2
                        val height = (size.height * value / top * grow.value).coerceAtLeast(barWidth.coerceAtMost(6.dp.toPx()))
                        drawRoundRect(if (index == highlight) strong else soft, Offset(left, size.height - height), Size(barWidth, height), radius)
                    }
                    if (average != null && average > 0) {
                        val y = size.height - size.height * average / top
                        drawLine(
                            averageColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                        )
                    }
                }
            }
            // The scale, at the top of each grid line.
            Column(Modifier.width(56.dp).fillMaxHeight().padding(start = 6.dp)) {
                scaleLabels.forEachIndexed { index, label ->
                    Text(label, style = labelStyle, color = labelColor, maxLines = 1, softWrap = false)
                    if (index < scaleLabels.lastIndex) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().padding(end = 56.dp)) {
            labels.forEachIndexed { index, label ->
                Text(
                    label,
                    style = labelStyle,
                    color = if (index == current) MaterialTheme.colorScheme.primary else labelColor,
                    fontWeight = if (index == current) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ─── Top lists ───────────────────────────────────────────────────

/** A ranked track with a bar under it for its share of the top track's listening time. */
@Composable
internal fun RankedTrackRow(rank: Int, track: ReportTrack, share: Float, onClick: () -> Unit) {
    val clickable = track.source == "soundcloud"
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .clickable(enabled = clickable, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            rank.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (rank == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(32.dp),
        )
        Spacer(Modifier.width(6.dp))
        StatsCover(track.artworkUrl, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artistName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { share.coerceIn(0.02f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
        Spacer(Modifier.width(16.dp))
        PlaysAndTime(track.plays, track.listenMs)
    }
}

/** "▶ 3" over the time, in a column wide enough for the longest time, so nothing is ever cut. */
@Composable
internal fun PlaysAndTime(plays: Int, listenMs: Long) {
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 76.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Text(plays.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Text(formatDuration(listenMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun ArtistTile(rank: Int, artist: ReportArtist, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier.clip(RoundedCornerShape(20.dp))
            .clickable(interactionSource = interaction, indication = ripple(), onClick = onClick)
            .pressScale(interaction)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            StatsCover(artist.imageUrl, Modifier.size(84.dp).clip(CircleShape), placeholder = Icons.Rounded.Person)
            Surface(
                shape = CircleShape,
                color = if (rank == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.align(Alignment.BottomStart).size(26.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        rank.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (rank == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(artist.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(formatDuration(artist.listenMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

// ─── Habits ──────────────────────────────────────────────────────

@Composable
internal fun HabitsGrid(report: ListeningReport, isWide: Boolean) {
    val tiles: List<@Composable (Modifier) -> Unit> = listOf(
        { m -> HabitTile(Icons.Rounded.CheckCircle, "${(report.completionRate * 100).roundToInt()} %", str("listening_stats_completion_rate"), str("listening_stats_completion_rate_desc"), m) },
        { m -> HabitTile(Icons.Rounded.SkipNext, "${(report.skipRate * 100).roundToInt()} %", str("listening_stats_skip_rate"), str("listening_stats_skips_of", report.skips), m) },
        { m -> HabitTile(Icons.Rounded.Timer, formatDuration(report.averageListenMs), str("listening_stats_avg_play"), str("listening_stats_avg_play_desc"), m) },
        { m -> HabitTile(Icons.Rounded.LocalFireDepartment, report.longestStreakDays.toString(), str("listening_stats_streak"), str("listening_stats_active_days", report.activeDays), m) },
    )
    val perRow = if (isWide) 4 else 2
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(IntrinsicSize.Max)) {
                row.forEach { tile -> tile(Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

@Composable
private fun HabitTile(icon: ImageVector, value: String, title: String, description: String, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Column(Modifier.padding(18.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
