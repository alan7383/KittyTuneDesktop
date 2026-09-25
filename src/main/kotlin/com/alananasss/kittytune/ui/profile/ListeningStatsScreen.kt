@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alananasss.kittytune.ui.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.EscapableAlertDialog
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.ListeningStatsEvent
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.stats.ActivityBucket
import com.alananasss.kittytune.data.stats.ListeningReport
import com.alananasss.kittytune.data.stats.ReportArtist
import com.alananasss.kittytune.data.stats.ReportPeriod
import com.alananasss.kittytune.data.stats.ReportTrack
import com.alananasss.kittytune.ui.common.pressScale
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class StatsList { NONE, PLAYS, TRACKS, ARTISTS }

private const val TOP_SHOWN = 5

/**
 * Listening statistics: how much, when, and what — for this week, month, year or all time.
 *
 * Calendar spans compared with the one before ("+23 % on last week"), a bar per day (or month) of activity,
 * the hours of the day music is played, the top tracks and artists as a ranked list, and a few habits that
 * actually mean something (how much is finished, how much skipped, the longest run of days).
 */
@Composable
fun ListeningStatsScreen(
    onBackClick: () -> Unit,
    onTrackClick: (trackId: Long) -> Unit,
    onArtistClick: (ReportArtist) -> Unit,
) {
    // Built through an explicit initializer: the default factory route throws on desktop (issue #33).
    val viewModel: ListeningStatsViewModel = viewModel { ListeningStatsViewModel() }
    val report = viewModel.report
    var openList by remember { mutableStateOf(StatsList.NONE) }
    var showPrivacy by remember { mutableStateOf(false) }

    if (showPrivacy) PrivacyDialog { showPrivacy = false }
    report?.let {
        when (openList) {
            StatsList.PLAYS -> PlaysDialog(viewModel.events, onTrackClick) { openList = StatsList.NONE }
            StatsList.TRACKS -> TracksDialog(it.topTracks, onTrackClick) { openList = StatsList.NONE }
            StatsList.ARTISTS -> ArtistsDialog(it.topArtists, onArtistClick) { openList = StatsList.NONE }
            StatsList.NONE -> Unit
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        StatsHeader(report, viewModel.period, onSelect = viewModel::selectPeriod, onPrivacy = { showPrivacy = true })

        AnimatedContent(
            targetState = report?.takeIf { !viewModel.isLoading },
            transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(90)) },
            contentKey = { it?.window },
            modifier = Modifier.fillMaxSize(),
            label = "statsBody",
        ) { shown ->
            when {
                shown == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ContainedLoadingIndicator()
                }
                !shown.hasData -> EmptyStats()
                else -> StatsBody(
                    report = shown,
                    period = viewModel.period,
                    onOpen = { openList = it },
                    onTrackClick = onTrackClick,
                    onArtistClick = onArtistClick,
                )
            }
        }
    }
}

@Composable
private fun StatsHeader(
    report: ListeningReport?,
    period: ReportPeriod,
    onSelect: (ReportPeriod) -> Unit,
    onPrivacy: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(str("listening_stats_title"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    report?.let { spanLabel(period, it) } ?: " ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onPrivacy) {
                Icon(Icons.Rounded.Tune, contentDescription = str("pref_privacy_title"))
            }
        }
        Spacer(Modifier.height(12.dp))
        com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup(
            options = ReportPeriod.entries,
            selectedOption = period,
            onOptionSelected = onSelect,
            modifier = Modifier.widthIn(max = 560.dp),
            fillMaxWidth = true,
            labelProvider = { value ->
                Text(
                    str(
                        when (value) {
                            ReportPeriod.WEEK -> "listening_stats_period_week_short"
                            ReportPeriod.MONTH -> "listening_stats_period_month_short"
                            ReportPeriod.YEAR -> "listening_stats_period_year_short"
                            ReportPeriod.ALL_TIME -> "listening_stats_period_all"
                        }
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    softWrap = false,
                )
            },
        )
    }
}

@Composable
private fun StatsBody(
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
                        ActivityCard(report.activity, Modifier.weight(1.6f).fillMaxHeight())
                        HoursCard(report, Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ActivityCard(report.activity, Modifier.fillMaxWidth())
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
                        report.topTracks.take(TOP_SHOWN).forEachIndexed { index, track ->
                            TrackRow(index + 1, track) { onTrackClick(track.trackId) }
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
                        Row(
                            Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
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
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Text(
                str("listening_stats_time_listened"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    formatDuration(report.totalListenMs),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                report.change?.let { ChangeChip(it, period) }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryTile(Icons.Rounded.PlayArrow, report.plays.toString(), str("listening_stats_plays"), Modifier.weight(1f)) { onOpen(StatsList.PLAYS) }
                SummaryTile(Icons.Rounded.MusicNote, report.uniqueTracks.toString(), str("listening_stats_unique_tracks"), Modifier.weight(1f)) { onOpen(StatsList.TRACKS) }
                SummaryTile(Icons.Rounded.People, report.uniqueArtists.toString(), str("listening_stats_unique_artists"), Modifier.weight(1f)) { onOpen(StatsList.ARTISTS) }
            }
        }
    }
}

@Composable
private fun ChangeChip(change: Float, period: ReportPeriod) {
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
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

// ─── Charts ──────────────────────────────────────────────────────

@Composable
private fun StatsCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (action != null) {
                    TextButton(onClick = action) { Text(str("listening_stats_show_all")) }
                }
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ActivityCard(buckets: List<ActivityBucket>, modifier: Modifier) {
    var hovered by remember(buckets) { mutableStateOf<Int?>(null) }
    val shown = hovered?.let { buckets.getOrNull(it) }
    StatsCard(
        title = str("listening_stats_activity"),
        subtitle = shown?.let { "${bucketLabel(it, long = true)} · ${formatDuration(it.listenMs)}" } ?: str("listening_stats_activity_hint"),
        modifier = modifier,
    ) {
        BarChart(
            values = buckets.map { it.listenMs },
            labels = buckets.mapIndexed { index, bucket -> axisLabel(bucket, index, buckets.size) },
            hovered = hovered,
            onHover = { hovered = it },
            modifier = Modifier.fillMaxWidth().height(160.dp),
        )
    }
}

@Composable
private fun HoursCard(report: ListeningReport, modifier: Modifier) {
    var hovered by remember(report) { mutableStateOf<Int?>(null) }
    val subtitle = hovered?.let { "${hourLabel(it)}–${hourLabel((it + 1) % 24)} · ${formatDuration(report.hours[it])}" }
        ?: report.peakHour?.let { str("listening_stats_peak_hour", hourLabel(it)) }
    StatsCard(title = str("listening_stats_hours"), subtitle = subtitle, modifier = modifier) {
        BarChart(
            values = report.hours,
            labels = List(24) { hour -> if (hour % 6 == 0) hourLabel(hour) else "" },
            hovered = hovered,
            onHover = { hovered = it },
            modifier = Modifier.fillMaxWidth().height(160.dp),
        )
    }
}

/**
 * Bars with rounded tops, the tallest in the primary colour, the rest softer; hovering one lifts it and
 * reports its index so the card can say what it is. The bars grow in when the data changes.
 */
@Composable
private fun BarChart(
    values: List<Long>,
    labels: List<String>,
    hovered: Int?,
    onHover: (Int?) -> Unit,
    modifier: Modifier,
) {
    val grow = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { grow.animateTo(1f, tween(500)) }
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val peak = values.indexOf(values.maxOrNull() ?: -1L)
    val strong = MaterialTheme.colorScheme.primary
    val soft = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val labelStyle = MaterialTheme.typography.labelSmall
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier) {
        Canvas(
            Modifier.fillMaxWidth().weight(1f).pointerInput(values.size) {
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
            val slot = size.width / values.size
            val barWidth = (slot * 0.62f).coerceAtMost(28.dp.toPx())
            val radius = CornerRadius(barWidth / 2, barWidth / 2)
            values.forEachIndexed { index, value ->
                val left = slot * index + (slot - barWidth) / 2
                drawRoundRect(track, Offset(left, 0f), Size(barWidth, size.height), radius)
                if (value > 0) {
                    val height = (size.height * value / max * grow.value).coerceAtLeast(barWidth)
                    val color = if (index == hovered || (hovered == null && index == peak)) strong else soft
                    drawRoundRect(color, Offset(left, size.height - height), Size(barWidth, height), radius)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { label ->
                Text(
                    label,
                    style = labelStyle,
                    color = labelColor,
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

@Composable
private fun TrackRow(rank: Int, track: ReportTrack, onClick: () -> Unit) {
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
            modifier = Modifier.width(28.dp),
        )
        Spacer(Modifier.width(8.dp))
        Cover(track.artworkUrl, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artistName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        CountAndTime(track.plays, track.listenMs)
    }
}

/** "▶ 3" over the time: a count that needs no plural forms in any of the six languages. */
@Composable
private fun CountAndTime(plays: Int, listenMs: Long) {
    Column(horizontalAlignment = Alignment.End) {
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
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClick = onClick)
            .pressScale(interaction)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Cover(artist.imageUrl, Modifier.size(84.dp).clip(CircleShape), placeholder = Icons.Rounded.Person)
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

/** A cover or avatar, with an icon on the container colour while it loads or when there is none. */
@Composable
private fun Cover(url: String?, modifier: Modifier, placeholder: ImageVector = Icons.Rounded.MusicNote) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        Icon(placeholder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        if (!url.isNullOrBlank()) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}

// ─── Habits ──────────────────────────────────────────────────────

@Composable
private fun HabitsGrid(report: ListeningReport, isWide: Boolean) {
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

@Composable
private fun EmptyStats() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(88.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(44.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(str("listening_stats_empty_title"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(str("listening_stats_empty_desc"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

// ─── Full lists ──────────────────────────────────────────────────

@Composable
private fun ListDialog(title: String, count: Int, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$title · $count", fontWeight = FontWeight.SemiBold) },
        text = { Box(Modifier.widthIn(min = 360.dp, max = 560.dp).heightIn(max = 480.dp)) { content() } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_close")) } },
    )
}

@Composable
private fun TracksDialog(tracks: List<ReportTrack>, onTrackClick: (Long) -> Unit, onDismiss: () -> Unit) {
    ListDialog(str("listening_stats_all_tracks"), tracks.size, onDismiss) {
        LazyColumn {
            itemsIndexed(tracks) { index, track ->
                TrackRow(index + 1, track) {
                    onDismiss()
                    onTrackClick(track.trackId)
                }
            }
        }
    }
}

@Composable
private fun ArtistsDialog(artists: List<ReportArtist>, onArtistClick: (ReportArtist) -> Unit, onDismiss: () -> Unit) {
    ListDialog(str("listening_stats_all_artists"), artists.size, onDismiss) {
        LazyColumn {
            itemsIndexed(artists) { index, artist ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .clickable { onDismiss(); onArtistClick(artist) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text((index + 1).toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.width(28.dp))
                    Spacer(Modifier.width(8.dp))
                    Cover(artist.imageUrl, Modifier.size(44.dp).clip(CircleShape), placeholder = Icons.Rounded.Person)
                    Spacer(Modifier.width(14.dp))
                    Text(artist.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    CountAndTime(artist.plays, artist.listenMs)
                }
            }
        }
    }
}

@Composable
private fun PlaysDialog(events: List<ListeningStatsEvent>, onTrackClick: (Long) -> Unit, onDismiss: () -> Unit) {
    val format = remember { DateTimeFormatter.ofPattern("d MMM, HH:mm", com.alananasss.kittytune.core.Strings.locale()) }
    ListDialog(str("listening_stats_all_plays"), events.size, onDismiss) {
        LazyColumn {
            itemsIndexed(events) { _, event ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .clickable(enabled = event.source == "soundcloud") { onDismiss(); onTrackClick(event.trackId) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(event.artworkUrl, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(event.trackTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(event.artistName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(format.format(Instant.ofEpochMilli(event.timestamp).atZone(ZoneId.systemDefault())), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatDuration(event.listenDurationMs), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyDialog(onDismiss: () -> Unit) {
    val prefs = remember { PlayerPreferences() }
    var isEnabled by remember { mutableStateOf(prefs.getListeningStatsEnabled()) }
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str("pref_privacy_title")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(str("pref_privacy_subtitle"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SwitchRow(
                    title = str("pref_privacy_tracking_title"),
                    subtitle = str("pref_privacy_tracking_subtitle"),
                    checked = isEnabled,
                ) {
                    isEnabled = !isEnabled
                    prefs.setListeningStatsEnabled(isEnabled)
                }
                Text(str("listening_stats_disclaimer"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_close")) } },
    )
}

// ─── Formatting ──────────────────────────────────────────────────

private fun locale(): Locale = com.alananasss.kittytune.core.Strings.locale()

@Composable
private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0"
    val totalSeconds = ms / 1000
    val days = totalSeconds / 86400
    val hours = (totalSeconds % 86400) / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        days > 0 -> str("listening_stats_duration_days_hrs", days, hours)
        hours > 0 -> str("listening_stats_duration_hr_min", hours, minutes)
        minutes > 0 -> str("listening_stats_duration_min_sec", minutes, seconds)
        else -> str("listening_stats_duration_sec", seconds)
    }
}

private fun hourLabel(hour: Int): String = "%02d:00".format(hour)

/** "22–28 Sept", "September 2026", "2026", or "since March 2024". */
@Composable
private fun spanLabel(period: ReportPeriod, report: ListeningReport): String {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(report.window.startMs).atZone(zone).toLocalDate()
    val end = Instant.ofEpochMilli(report.window.endMs).atZone(zone).toLocalDate().minusDays(1)
    val loc = locale()
    return when (period) {
        ReportPeriod.WEEK -> "${start.dayOfMonth} ${start.month.getDisplayName(TextStyle.SHORT, loc)} – ${end.dayOfMonth} ${end.month.getDisplayName(TextStyle.SHORT, loc)}"
        ReportPeriod.MONTH -> "${start.month.getDisplayName(TextStyle.FULL_STANDALONE, loc).replaceFirstChar { it.titlecase(loc) }} ${start.year}"
        ReportPeriod.YEAR -> start.year.toString()
        ReportPeriod.ALL_TIME -> str("listening_stats_since", "${start.month.getDisplayName(TextStyle.FULL_STANDALONE, loc)} ${start.year}")
    }
}

private fun bucketLabel(bucket: ActivityBucket, long: Boolean): String {
    val date = Instant.ofEpochMilli(bucket.startMs).atZone(ZoneId.systemDefault()).toLocalDate()
    val loc = locale()
    return if (bucket.isMonth) {
        "${date.month.getDisplayName(if (long) TextStyle.FULL_STANDALONE else TextStyle.SHORT_STANDALONE, loc)} ${date.year}"
    } else {
        "${date.dayOfWeek.getDisplayName(TextStyle.SHORT, loc)}, ${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, loc)}"
    }
}

/** Every bar's label on short charts; on long ones only every few, so they never overlap. */
private fun axisLabel(bucket: ActivityBucket, index: Int, count: Int): String {
    val date = Instant.ofEpochMilli(bucket.startMs).atZone(ZoneId.systemDefault()).toLocalDate()
    val loc = locale()
    return when {
        bucket.isMonth -> if (count <= 12 || index % 3 == 0) date.month.getDisplayName(TextStyle.NARROW_STANDALONE, loc) else ""
        count <= 7 -> date.dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, loc)
        else -> if (date.dayOfMonth == 1 || date.dayOfMonth % 5 == 0) date.dayOfMonth.toString() else ""
    }
}
