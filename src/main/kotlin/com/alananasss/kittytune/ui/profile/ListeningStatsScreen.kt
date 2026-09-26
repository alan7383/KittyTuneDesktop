@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alananasss.kittytune.ui.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.EscapableAlertDialog
import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.stats.ActivityBucket
import com.alananasss.kittytune.data.stats.ListeningReport
import com.alananasss.kittytune.data.stats.ReportArtist
import com.alananasss.kittytune.data.stats.ReportPeriod
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** The full lists the summary opens. */
internal enum class StatsList { NONE, PLAYS, TRACKS, ARTISTS }

/**
 * How the statistics are laid out: an overview with charts, a dense list for a quick look, or big "wrapped"
 * cards that read like a recap.
 */
internal enum class StatsStyle(val labelKey: String, val icon: ImageVector) {
    OVERVIEW("stats_style_overview", Icons.Rounded.Dashboard),
    COMPACT("stats_style_compact", Icons.Rounded.ViewAgenda),
    STORY("stats_style_story", Icons.Rounded.AutoAwesome),
}

private const val KEY_STATS_STYLE = "listening_stats_style"

/**
 * Listening statistics: how much, when, and what — for this week, month, year or all time — in the style the
 * listener picked.
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
    var style by remember {
        mutableStateOf(runCatching { StatsStyle.valueOf(Prefs.getString(KEY_STATS_STYLE, null) ?: "") }.getOrDefault(StatsStyle.OVERVIEW))
    }

    if (showPrivacy) PrivacyDialog { showPrivacy = false }
    report?.let {
        when (openList) {
            StatsList.PLAYS -> PlaysListDialog(viewModel.events, onTrackClick) { openList = StatsList.NONE }
            StatsList.TRACKS -> TracksListDialog(it.topTracks, onTrackClick) { openList = StatsList.NONE }
            StatsList.ARTISTS -> ArtistsListDialog(it.topArtists, onArtistClick) { openList = StatsList.NONE }
            StatsList.NONE -> Unit
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        StatsHeader(
            report = report,
            period = viewModel.period,
            style = style,
            onSelect = viewModel::selectPeriod,
            onStyle = {
                style = it
                Prefs.putString(KEY_STATS_STYLE, it.name)
            },
            onPrivacy = { showPrivacy = true },
        )

        AnimatedContent(
            targetState = report?.takeIf { !viewModel.isLoading }?.let { it to style },
            transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(90)) },
            contentKey = { it?.let { (r, s) -> r.window to s } },
            modifier = Modifier.fillMaxSize(),
            label = "statsBody",
        ) { shown ->
            val (shownReport, shownStyle) = shown ?: (null to style)
            when {
                shownReport == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ContainedLoadingIndicator()
                }
                !shownReport.hasData -> EmptyStats()
                shownStyle == StatsStyle.OVERVIEW -> OverviewStats(shownReport, viewModel.period, { openList = it }, onTrackClick, onArtistClick)
                shownStyle == StatsStyle.COMPACT -> CompactStats(shownReport, viewModel.period, { openList = it }, onTrackClick, onArtistClick)
                else -> StoryStats(shownReport, viewModel.period, { openList = it }, onTrackClick, onArtistClick)
            }
        }
    }
}

@Composable
private fun StatsHeader(
    report: ListeningReport?,
    period: ReportPeriod,
    style: StatsStyle,
    onSelect: (ReportPeriod) -> Unit,
    onStyle: (StatsStyle) -> Unit,
    onPrivacy: () -> Unit,
) {
    var styleMenu by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val isNarrow = maxWidth < 560.dp
    Column(Modifier.fillMaxWidth().padding(start = if (isNarrow) 16.dp else 24.dp, end = if (isNarrow) 8.dp else 16.dp, top = 12.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    str("listening_stats_title"),
                    style = if (isNarrow) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
                Text(
                    report?.let { spanLabel(period, it) } ?: " ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                if (isNarrow) {
                    FilledTonalIconButton(onClick = { styleMenu = true }) {
                        Icon(style.icon, contentDescription = str(style.labelKey), modifier = Modifier.size(20.dp))
                    }
                } else {
                    FilledTonalButton(onClick = { styleMenu = true }, contentPadding = PaddingValues(horizontal = 14.dp)) {
                        Icon(style.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(str(style.labelKey))
                    }
                }
                DropdownMenu(
                    expanded = styleMenu,
                    onDismissRequest = { styleMenu = false },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    StatsStyle.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(str(option.labelKey)) },
                            leadingIcon = { Icon(option.icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            trailingIcon = {
                                if (option == style) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            onClick = {
                                styleMenu = false
                                onStyle(option)
                            },
                        )
                    }
                }
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
            contentPadding = if (isNarrow) PaddingValues(horizontal = 6.dp) else null,
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
                    style = if (isNarrow) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    softWrap = false,
                )
            },
        )
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

// ─── Shared pieces ───────────────────────────────────────────────

/** A cover or avatar, with an icon on the container colour while it loads or when there is none. */
@Composable
internal fun StatsCover(url: String?, modifier: Modifier, placeholder: ImageVector = Icons.Rounded.MusicNote) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        Icon(placeholder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        if (!url.isNullOrBlank()) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}

/** A titled card on the surface-container colour, with an optional "show all" action. */
@Composable
internal fun StatsCard(
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

internal fun statsLocale(): Locale = com.alananasss.kittytune.core.Strings.locale()

/** "1 ч 5 мин", "3 мин 12 с", "40 с". */
@Composable
internal fun formatDuration(ms: Long): String {
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

/** Coarser, for axes and headlines: "1 ч 5 мин", "12 мин", "40 с". */
@Composable
internal fun formatDurationShort(ms: Long): String {
    val totalMinutes = ms / 60_000
    return when {
        totalMinutes >= 60 -> str("listening_stats_duration_hr_min", totalMinutes / 60, totalMinutes % 60)
        totalMinutes > 0 -> str("listening_stats_duration_min", totalMinutes)
        else -> str("listening_stats_duration_sec", ms / 1000)
    }
}

internal fun hourLabel(hour: Int): String = "%02d:00".format(hour)

internal fun weekdayName(day: DayOfWeek): String =
    day.getDisplayName(TextStyle.FULL_STANDALONE, statsLocale()).replaceFirstChar { it.titlecase(statsLocale()) }

/** "22–28 Sept", "September 2026", "2026", or "March 2024 – today". */
@Composable
internal fun spanLabel(period: ReportPeriod, report: ListeningReport): String {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(report.window.startMs).atZone(zone).toLocalDate()
    val end = Instant.ofEpochMilli(report.window.endMs).atZone(zone).toLocalDate().minusDays(1)
    val loc = statsLocale()
    return when (period) {
        ReportPeriod.WEEK -> "${start.dayOfMonth} ${start.month.getDisplayName(TextStyle.SHORT, loc)} – ${end.dayOfMonth} ${end.month.getDisplayName(TextStyle.SHORT, loc)}"
        ReportPeriod.MONTH -> "${start.month.getDisplayName(TextStyle.FULL_STANDALONE, loc).replaceFirstChar { it.titlecase(loc) }} ${start.year}"
        ReportPeriod.YEAR -> start.year.toString()
        ReportPeriod.ALL_TIME -> str("listening_stats_since", "${start.month.getDisplayName(TextStyle.FULL_STANDALONE, loc)} ${start.year}")
    }
}

internal fun bucketLabel(bucket: ActivityBucket): String {
    val date = Instant.ofEpochMilli(bucket.startMs).atZone(ZoneId.systemDefault()).toLocalDate()
    val loc = statsLocale()
    return if (bucket.isMonth) {
        "${date.month.getDisplayName(TextStyle.FULL_STANDALONE, loc).replaceFirstChar { it.titlecase(loc) }} ${date.year}"
    } else {
        "${date.dayOfWeek.getDisplayName(TextStyle.SHORT, loc)}, ${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, loc)}"
    }
}

/** Every bar's label on short charts; on long ones only every few, so they never overlap. */
internal fun axisLabel(bucket: ActivityBucket, index: Int, count: Int): String {
    val date = Instant.ofEpochMilli(bucket.startMs).atZone(ZoneId.systemDefault()).toLocalDate()
    val loc = statsLocale()
    return when {
        bucket.isMonth -> if (count <= 12 || index % 3 == 0) date.month.getDisplayName(TextStyle.NARROW_STANDALONE, loc) else ""
        count <= 7 -> date.dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, loc)
        else -> if (date.dayOfMonth == 1 || date.dayOfMonth % 5 == 0) date.dayOfMonth.toString() else ""
    }
}

/** Whether [bucket] is today (or, for month bars, this month). */
internal fun isCurrent(bucket: ActivityBucket): Boolean {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(bucket.startMs).atZone(zone).toLocalDate()
    val today = java.time.LocalDate.now(zone)
    return if (bucket.isMonth) date.year == today.year && date.month == today.month else date == today
}

internal fun Modifier.roundedClip(radius: Int): Modifier = clip(RoundedCornerShape(radius.dp))
