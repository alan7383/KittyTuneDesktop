@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.alananasss.kittytune.ui.profile.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.stats.ListeningRecap
import com.alananasss.kittytune.data.stats.ReportArtist
import com.alananasss.kittytune.data.stats.ReportTrack
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** The slides a recap can have, in the order they play. */
internal enum class RecapSlide {
    INTRO, TIME, FIRST, TOP_ARTIST, TOP_ARTISTS, TOP_TRACK, TOP_TRACKS, REPLAY, GENRES, DAY_NIGHT, WEEK,
    DISCOVERIES, HABITS, PERSONA, SUMMARY,
}

internal fun slidesFor(recap: ListeningRecap): List<RecapSlide> {
    val report = recap.report
    val topTrack = report.topTracks.firstOrNull()
    return buildList {
        add(RecapSlide.INTRO); add(RecapSlide.TIME)
        if (recap.firstTrackTitle != null) add(RecapSlide.FIRST)
        if (report.topArtists.isNotEmpty()) add(RecapSlide.TOP_ARTIST)
        if (report.topArtists.size >= 3) add(RecapSlide.TOP_ARTISTS)
        if (topTrack != null) add(RecapSlide.TOP_TRACK)
        if (report.topTracks.size >= 3) add(RecapSlide.TOP_TRACKS)
        if (recap.mostReplayed != null && recap.mostReplayed.trackId != topTrack?.trackId) add(RecapSlide.REPLAY)
        if (recap.genres.isNotEmpty()) add(RecapSlide.GENRES)
        add(RecapSlide.DAY_NIGHT)
        if (recap.report.activeDays >= 2) add(RecapSlide.WEEK)
        if (recap.newArtists > 0 || recap.newTracks > 0) add(RecapSlide.DISCOVERIES)
        add(RecapSlide.HABITS); add(RecapSlide.PERSONA); add(RecapSlide.SUMMARY)
    }
}

/** A different colour role per slide, so moving through the story is moving through the palette. */
internal fun RecapSlide.palette(scheme: ColorScheme, recap: ListeningRecap): RecapPalette = when (this) {
    RecapSlide.INTRO, RecapSlide.SUMMARY -> recap.period.palette(scheme)
    RecapSlide.TIME -> RecapPalette(scheme.primaryContainer, scheme.onPrimaryContainer, scheme.primary)
    RecapSlide.FIRST, RecapSlide.WEEK -> RecapPalette(scheme.surfaceContainerHighest, scheme.onSurface, scheme.primary)
    RecapSlide.TOP_ARTIST, RecapSlide.GENRES, RecapSlide.HABITS ->
        RecapPalette(scheme.secondaryContainer, scheme.onSecondaryContainer, scheme.secondary)
    RecapSlide.TOP_ARTISTS, RecapSlide.TOP_TRACKS -> RecapPalette(scheme.surfaceContainerHigh, scheme.onSurface, scheme.tertiary)
    RecapSlide.TOP_TRACK -> RecapPalette(scheme.tertiaryContainer, scheme.onTertiaryContainer, scheme.tertiary)
    RecapSlide.REPLAY, RecapSlide.PERSONA -> RecapPalette(scheme.primary, scheme.onPrimary, scheme.primaryContainer)
    RecapSlide.DISCOVERIES -> RecapPalette(scheme.tertiary, scheme.onTertiary, scheme.tertiaryContainer)
    RecapSlide.DAY_NIGHT -> if (recap.nightShare > 0.5f) RecapPalette(scheme.inverseSurface, scheme.inverseOnSurface, scheme.inversePrimary)
    else RecapPalette(scheme.tertiaryContainer, scheme.onTertiaryContainer, scheme.tertiary)
}

internal fun RecapSlide.polygon(recap: ListeningRecap): RoundedPolygon = when (this) {
    RecapSlide.INTRO -> recap.period.polygon()
    RecapSlide.TIME -> MaterialShapes.Cookie9Sided
    RecapSlide.FIRST -> MaterialShapes.Arch
    RecapSlide.TOP_ARTIST -> MaterialShapes.Cookie12Sided
    RecapSlide.TOP_ARTISTS -> MaterialShapes.Clover4Leaf
    RecapSlide.TOP_TRACK -> MaterialShapes.Cookie7Sided
    RecapSlide.TOP_TRACKS -> MaterialShapes.Flower
    RecapSlide.REPLAY -> MaterialShapes.SoftBurst
    RecapSlide.GENRES -> MaterialShapes.Puffy
    RecapSlide.DAY_NIGHT -> if (recap.nightShare > 0.5f) MaterialShapes.Ghostish else MaterialShapes.Sunny
    RecapSlide.WEEK -> MaterialShapes.Slanted
    RecapSlide.DISCOVERIES -> MaterialShapes.Gem
    RecapSlide.HABITS -> MaterialShapes.ClamShell
    RecapSlide.PERSONA -> recap.persona.polygon()
    RecapSlide.SUMMARY -> MaterialShapes.Cookie12Sided
}

@Composable
internal fun SlideContent(
    slide: RecapSlide,
    recap: ListeningRecap,
    accent: Color,
    onTrackClick: (ReportTrack) -> Unit,
    onArtistClick: (ReportArtist) -> Unit,
) {
    when (slide) {
        RecapSlide.INTRO -> IntroSlide(recap, accent)
        RecapSlide.TIME -> TimeSlide(recap)
        RecapSlide.FIRST -> FirstSlide(recap)
        RecapSlide.TOP_ARTIST -> TopArtistSlide(recap, accent, onArtistClick)
        RecapSlide.TOP_ARTISTS -> TopArtistsSlide(recap, accent, onArtistClick)
        RecapSlide.TOP_TRACK -> TopTrackSlide(recap, accent, onTrackClick)
        RecapSlide.TOP_TRACKS -> TopTracksSlide(recap, accent, onTrackClick)
        RecapSlide.REPLAY -> ReplaySlide(recap, onTrackClick)
        RecapSlide.GENRES -> GenresSlide(recap, accent)
        RecapSlide.DAY_NIGHT -> DayNightSlide(recap, accent)
        RecapSlide.WEEK -> WeekSlide(recap, accent)
        RecapSlide.DISCOVERIES -> DiscoveriesSlide(recap, onArtistClick)
        RecapSlide.HABITS -> HabitsSlide(recap)
        RecapSlide.PERSONA -> PersonaSlide(recap, accent)
        RecapSlide.SUMMARY -> SummarySlide(recap)
    }
}

// ─── Blocks ───────────────────────────────────────────────────────────────────────────────────

@Composable
private fun Rise(order: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(120L + order * 110L)
        appear.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
    }
    Box(
        modifier.graphicsLayer {
            alpha = appear.value.coerceIn(0f, 1f)
            translationY = (1f - appear.value) * 36.dp.toPx()
            val scale = 0.9f + 0.1f * appear.value
            scaleX = scale; scaleY = scale
        }
    ) { content() }
}

@Composable
private fun countUp(target: Long, durationMs: Int = 1_400): Long {
    val value = remember(target) { Animatable(0f) }
    LaunchedEffect(target) {
        kotlinx.coroutines.delay(250)
        value.animateTo(1f, tween(durationMs))
    }
    return (target * value.value).toLong()
}

@Composable private fun Kicker(text: String) =
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = LocalContentColor.current.copy(alpha = 0.85f))

@Composable private fun Headline(text: String, maxLines: Int = 2) =
    Text(text, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, maxLines = maxLines, overflow = TextOverflow.Ellipsis)

@Composable private fun Giant(text: String) =
    Text(text, style = MaterialTheme.typography.displayLarge.copy(fontSize = 96.sp, lineHeight = 100.sp), fontWeight = FontWeight.Black, maxLines = 1, softWrap = false)

@Composable private fun Body(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, color = LocalContentColor.current.copy(alpha = 0.85f))

@Composable private fun SlideColumn(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)

@Composable
private fun ShapedPicture(
    url: String?,
    polygon: RoundedPolygon,
    accent: Color,
    size: Dp,
    placeholder: ImageVector = Icons.Rounded.Person,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(size * 1.25f), contentAlignment = Alignment.Center) {
        SpinningShape(polygon, accent.copy(alpha = 0.45f), Modifier.size(size * 1.25f), periodMs = 18_000)
        Picture(url, Modifier.size(size).clip(polygon.toShape()), placeholder)
    }
}

@Composable
private fun Picture(url: String?, modifier: Modifier = Modifier, placeholder: ImageVector = Icons.Rounded.PlayArrow) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        if (url.isNullOrBlank()) {
            Icon(placeholder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        } else {
            AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun SmallAction(text: String, icon: ImageVector, onClick: () -> Unit) {
    val content = LocalContentColor.current
    FilledTonalButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = content.copy(alpha = 0.14f), contentColor = content),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(text)
    }
}

@Composable
private fun RankRow(
    rank: Int,
    imageUrl: String?,
    title: String,
    subtitle: String,
    pictureShape: Shape,
    accent: Color,
    placeholder: ImageVector,
    onClick: () -> Unit,
) {
    val big = rank == 1
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(if (big) 40.dp else 32.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(if (big) accent else LocalContentColor.current.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(rank.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = if (big) MaterialTheme.colorScheme.surface else LocalContentColor.current)
        }
        Spacer(Modifier.width(12.dp))
        Picture(imageUrl, Modifier.size(if (big) 64.dp else 52.dp).clip(pictureShape), placeholder)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = LocalContentColor.current.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ShareBar(label: String, value: String, fraction: Float, color: Color, emphasized: Boolean = false) {
    val grown by animateFloatAsState(fraction.coerceIn(0.04f, 1f), spring(Spring.DampingRatioLowBouncy, Spring.StiffnessVeryLow), label = "shareBar")
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, style = if (emphasized) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium, fontWeight = if (emphasized) FontWeight.Black else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(if (emphasized) 18.dp else 12.dp).clip(CircleShape).background(LocalContentColor.current.copy(alpha = 0.12f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(grown).clip(CircleShape).background(color))
        }
    }
}

@Composable private fun percent(f: Float): String = str("recap_percent", (f * 100).roundToInt())

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
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

private fun weekdayName(day: DayOfWeek) = day.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())
    .replaceFirstChar { it.titlecase(Locale.getDefault()) }

// ─── Slides ───────────────────────────────────────────────────────────────────────────────────

@Composable
private fun IntroSlide(recap: ListeningRecap, accent: Color) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Rise(0) {
            Box(contentAlignment = Alignment.Center) {
                SpinningShape(recap.period.polygon(), accent, Modifier.size(240.dp), periodMs = 14_000)
                Icon(recap.period.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(96.dp))
            }
        }
        Spacer(Modifier.height(28.dp))
        Rise(1) { Kicker(recap.period.kindLabel()) }
        Rise(2) { Text(recap.period.title(), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black, textAlign = TextAlign.Center) }
        Spacer(Modifier.height(12.dp))
        Rise(3) { Text(str("recap_intro_body", recap.report.plays, recap.report.uniqueArtists), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center) }
        Spacer(Modifier.height(20.dp))
        Rise(4) { Text(str("recap_tap_hint"), style = MaterialTheme.typography.labelLarge, color = LocalContentColor.current.copy(alpha = 0.6f)) }
    }
}

@Composable
private fun TimeSlide(recap: ListeningRecap) {
    val minutes = recap.report.totalListenMs / 60_000
    SlideColumn {
        Rise(0) { Kicker(str("recap_time_kicker")) }
        Rise(1) { Giant("%,d".format(countUp(minutes))) }
        Rise(2) { Headline(str("recap_minutes_label")) }
        Spacer(Modifier.height(16.dp))
        Rise(3) { Body(str("recap_time_per_day", formatDuration(recap.report.averagePerDayMs))) }
        recap.report.change?.let { change ->
            val pct = (change * 100).roundToInt()
            Rise(4) {
                Surface(shape = CircleShape, color = LocalContentColor.current.copy(alpha = 0.12f), contentColor = LocalContentColor.current) {
                    Text(str(if (pct >= 0) "recap_change_up" else "recap_change_down", kotlin.math.abs(pct)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
        }
        val films = minutes / 110
        if (films >= 2) Rise(5) { Body(str("recap_time_films", films)) }
    }
}

@Composable
private fun FirstSlide(recap: ListeningRecap) {
    SlideColumn {
        Rise(0) { Kicker(str("recap_first_kicker")) }
        Spacer(Modifier.height(8.dp))
        Rise(1) { Picture(recap.firstTrackArtwork, Modifier.size(240.dp).clip(RoundedCornerShape(36.dp))) }
        Spacer(Modifier.height(8.dp))
        Rise(2) { Headline(recap.firstTrackTitle.orEmpty()) }
        Rise(3) { Body(recap.firstTrackArtist.orEmpty()) }
        Rise(4) { Body(str("recap_first_body")) }
    }
}

@Composable
private fun TopArtistSlide(recap: ListeningRecap, accent: Color, onArtistClick: (ReportArtist) -> Unit) {
    val artist = recap.report.topArtists.first()
    SlideColumn {
        Rise(0) { Kicker(str("recap_top_artist_kicker")) }
        Rise(1) { ShapedPicture(artist.imageUrl, MaterialShapes.Cookie12Sided, accent, 220.dp) }
        Rise(2) { Headline(artist.name) }
        Rise(3) { Body(str("recap_listen_detail", formatDuration(artist.listenMs), artist.plays)) }
        Rise(4) { Body(str("recap_top_artist_share", percent(recap.topArtistShare))) }
        Rise(5) { SmallAction(str("recap_open_artist"), Icons.AutoMirrored.Rounded.ArrowForward) { onArtistClick(artist) } }
    }
}

@Composable
private fun TopArtistsSlide(recap: ListeningRecap, accent: Color, onArtistClick: (ReportArtist) -> Unit) {
    SlideColumn {
        Rise(0) { Kicker(str("recap_top_artists_kicker")) }
        recap.report.topArtists.take(5).forEachIndexed { i, artist ->
            Rise(i + 1) { RankRow(i + 1, artist.imageUrl, artist.name, formatDuration(artist.listenMs), CircleShape, accent, Icons.Rounded.Person) { onArtistClick(artist) } }
        }
    }
}

@Composable
private fun TopTrackSlide(recap: ListeningRecap, accent: Color, onTrackClick: (ReportTrack) -> Unit) {
    val track = recap.report.topTracks.first()
    SlideColumn {
        Rise(0) { Kicker(str("recap_top_track_kicker")) }
        Rise(1) { ShapedPicture(track.artworkUrl, MaterialShapes.Cookie7Sided, accent, 220.dp, Icons.Rounded.PlayArrow) }
        Rise(2) { Headline(track.title) }
        Rise(3) { Body(track.artistName) }
        Rise(4) { Body(str("stats_story_track_times", track.plays, formatDuration(track.listenMs))) }
        if (track.source == "soundcloud") Rise(5) { SmallAction(str("recap_play"), Icons.Rounded.PlayArrow) { onTrackClick(track) } }
    }
}

@Composable
private fun TopTracksSlide(recap: ListeningRecap, accent: Color, onTrackClick: (ReportTrack) -> Unit) {
    SlideColumn {
        Rise(0) { Kicker(str("recap_top_tracks_kicker")) }
        recap.report.topTracks.take(5).forEachIndexed { i, track ->
            Rise(i + 1) { RankRow(i + 1, track.artworkUrl, track.title, track.artistName, RoundedCornerShape(14.dp), accent, Icons.Rounded.PlayArrow) { onTrackClick(track) } }
        }
    }
}

@Composable
private fun ReplaySlide(recap: ListeningRecap, onTrackClick: (ReportTrack) -> Unit) {
    val track = recap.mostReplayed ?: return
    SlideColumn {
        Rise(0) { Kicker(str("recap_replay_kicker")) }
        Rise(1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Picture(track.artworkUrl, Modifier.size(140.dp).clip(RoundedCornerShape(28.dp)))
                Spacer(Modifier.width(20.dp))
                Giant("×${countUp(track.plays.toLong(), 1_000)}")
            }
        }
        Rise(2) { Headline(track.title) }
        Rise(3) { Body(track.artistName) }
        Rise(4) { Body(str("recap_replay_body", track.plays)) }
        if (track.source == "soundcloud") Rise(5) { SmallAction(str("recap_play"), Icons.Rounded.PlayArrow) { onTrackClick(track) } }
    }
}

@Composable
private fun GenresSlide(recap: ListeningRecap, accent: Color) {
    val top = recap.genres.first().share.coerceAtLeast(0.01f)
    SlideColumn {
        Rise(0) { Kicker(str("recap_genres_kicker")) }
        Rise(1) { Headline(recap.genres.first().name) }
        Spacer(Modifier.height(8.dp))
        recap.genres.forEachIndexed { i, g ->
            Rise(i + 2) { ShareBar(g.name, percent(g.share), g.share / top, if (i == 0) accent else LocalContentColor.current.copy(alpha = 0.55f), emphasized = i == 0) }
        }
    }
}

@Composable
private fun DayNightSlide(recap: ListeningRecap, accent: Color) {
    val night = recap.nightShare
    val isNight = night > 0.5f
    SlideColumn {
        Rise(0) { Kicker(str("recap_daynight_kicker")) }
        Rise(1) { Headline(str(if (isNight) "recap_daynight_night" else "recap_daynight_day")) }
        Spacer(Modifier.height(8.dp))
        Rise(2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DayNightBadge(Icons.Rounded.WbSunny, MaterialShapes.Sunny, str("recap_day"), percent(1f - night), if (!isNight) accent else LocalContentColor.current.copy(alpha = 0.2f), Modifier.weight(1f))
                DayNightBadge(Icons.Rounded.Bedtime, MaterialShapes.Cookie6Sided, str("recap_night"), percent(night), if (isNight) accent else LocalContentColor.current.copy(alpha = 0.2f), Modifier.weight(1f))
            }
        }
        recap.report.peakHour?.let { h -> Rise(3) { Body(str("recap_peak_hour", hourLabel(h))) } }
        val parts = recap.report.partsOfDay
        val max = parts.max().coerceAtLeast(1L)
        val total = parts.sum().coerceAtLeast(1L)
        val labels = listOf("listening_stats_night", "listening_stats_morning", "listening_stats_afternoon", "listening_stats_evening")
        parts.forEachIndexed { i, ms ->
            Rise(4 + i) { ShareBar(str(labels[i]), percent(ms.toFloat() / total), ms.toFloat() / max, if (ms == max) accent else LocalContentColor.current.copy(alpha = 0.5f)) }
        }
    }
}

@Composable
private fun DayNightBadge(icon: ImageVector, polygon: RoundedPolygon, label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(104.dp).clip(polygon.toShape()).background(color), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun WeekSlide(recap: ListeningRecap, accent: Color) {
    val locale = Locale.getDefault()
    val max = recap.weekdays.max().coerceAtLeast(1L)
    SlideColumn {
        Rise(0) { Kicker(str("recap_week_kicker")) }
        recap.busiestWeekday?.let { d -> Rise(1) { Headline(str("recap_week_busiest", weekdayName(d))) } }
        Spacer(Modifier.height(12.dp))
        Rise(2) {
            Row(Modifier.fillMaxWidth().height(200.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                recap.weekdays.forEachIndexed { i, ms ->
                    val fr = ms.toFloat() / max
                    val grown by animateFloatAsState(fr.coerceAtLeast(0.04f), spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessVeryLow), label = "weekBar")
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Box(Modifier.fillMaxWidth().weight(1f, fill = false).fillMaxHeight(grown).clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 6.dp, bottomEnd = 6.dp)).background(if (ms == max) accent else LocalContentColor.current.copy(alpha = 0.25f)))
                        Spacer(Modifier.height(6.dp))
                        Text(DayOfWeek.of(i + 1).getDisplayName(TextStyle.NARROW_STANDALONE, locale), style = MaterialTheme.typography.labelLarge, fontWeight = if (ms == max) FontWeight.Black else FontWeight.Medium)
                    }
                }
            }
        }
        recap.busiestDay?.let { d ->
            Spacer(Modifier.height(8.dp))
            Rise(3) { Body(str("recap_busiest_day", d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)), formatDuration(recap.busiestDayMs))) }
        }
    }
}

@Composable
private fun DiscoveriesSlide(recap: ListeningRecap, onArtistClick: (ReportArtist) -> Unit) {
    SlideColumn {
        Rise(0) { Kicker(str("recap_discover_kicker")) }
        Rise(1) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BigFigure(countUp(recap.newArtists.toLong()).toString(), str("recap_new_artists"), Modifier.weight(1f))
                BigFigure(countUp(recap.newTracks.toLong()).toString(), str("recap_new_tracks"), Modifier.weight(1f))
            }
        }
        recap.topNewArtist?.let { a ->
            Spacer(Modifier.height(16.dp))
            Rise(2) { Body(str("recap_best_find")) }
            Rise(3) {
                Row(Modifier.clip(RoundedCornerShape(24.dp)).clickable { onArtistClick(a) }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Picture(a.imageUrl, Modifier.size(80.dp).clip(MaterialShapes.Gem.toShape()), Icons.Rounded.Person)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(a.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatDuration(a.listenMs), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun BigFigure(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(28.dp), color = LocalContentColor.current.copy(alpha = 0.12f), contentColor = LocalContentColor.current, modifier = modifier) {
        Column(Modifier.padding(20.dp)) {
            Text(value, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 2)
        }
    }
}

@Composable
private fun HabitsSlide(recap: ListeningRecap) {
    val report = recap.report
    val tiles = listOf(
        report.longestStreakDays.toString() to str("listening_stats_streak"),
        report.activeDays.toString() to str("stats_story_active_days"),
        formatDuration(recap.longestSessionMs) to str("recap_longest_session"),
        percent(report.completionRate) to str("listening_stats_completion_rate"),
        report.uniqueTracks.toString() to str("listening_stats_unique_tracks"),
        percent(report.skipRate) to str("recap_skip_rate"),
    )
    SlideColumn {
        Rise(0) { Kicker(str("recap_habits_kicker")) }
        tiles.chunked(3).forEachIndexed { row, three ->
            Rise(row + 1) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    three.forEach { (v, l) -> BigFigure(v, l, Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun PersonaSlide(recap: ListeningRecap, accent: Color) {
    val persona = recap.persona
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Rise(0) { Kicker(str("recap_persona_kicker")) }
        Spacer(Modifier.height(20.dp))
        Rise(1) {
            Box(contentAlignment = Alignment.Center) {
                SpinningShape(persona.polygon(), accent, Modifier.size(240.dp), periodMs = 16_000)
                Icon(persona.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(96.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        Rise(2) { Text(str(persona.titleKey()), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, textAlign = TextAlign.Center) }
        Spacer(Modifier.height(8.dp))
        Rise(3) { Text(str(persona.descriptionKey()), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center) }
    }
}

@Composable
private fun SummarySlide(recap: ListeningRecap) {
    val report = recap.report
    val palette = recap.period.palette(MaterialTheme.colorScheme)
    Surface(
        shape = RoundedCornerShape(36.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(recap.period.polygon().toShape()).background(palette.container), contentAlignment = Alignment.Center) {
                    Icon(recap.period.icon(), contentDescription = null, tint = palette.onContainer, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(recap.period.title(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 1)
                    Text(str("recap_summary_brand"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SummaryList(str("recap_top_artists_kicker"), report.topArtists.take(5).map { it.name }, Modifier.weight(1f))
                SummaryList(str("recap_top_tracks_kicker"), report.topTracks.take(5).map { it.title }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryTile("%,d".format(report.totalListenMs / 60_000), str("recap_minutes_label"), palette.container, palette.onContainer, Modifier.weight(1f))
                SummaryTile(
                    recap.genres.firstOrNull()?.name ?: report.plays.toString(),
                    str(if (recap.genres.isNotEmpty()) "recap_top_genre" else "listening_stats_plays"),
                    MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, Modifier.weight(1f),
                )
            }
            SummaryTile(str(recap.persona.titleKey()), str("recap_persona_kicker"), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SummaryList(title: String, items: List<String>, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        items.forEachIndexed { i, name ->
            Text("${i + 1}  $name", style = MaterialTheme.typography.bodyMedium, fontWeight = if (i == 0) FontWeight.Black else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SummaryTile(value: String, label: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(22.dp), color = container, contentColor = content, modifier = modifier) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
