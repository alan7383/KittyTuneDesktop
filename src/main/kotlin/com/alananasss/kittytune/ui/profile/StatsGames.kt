@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.alananasss.kittytune.ui.profile

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.stats.ReportWindow
import com.alananasss.kittytune.ui.game.ArcadeLauncher
import com.alananasss.kittytune.ui.game.GameId
import com.alananasss.kittytune.ui.game.GamePlaytime

/**
 * Time spent in the arcade, kept apart from the listening figures: the same period as the page, game by game, with
 * the all-time total underneath.
 */
@Composable
internal fun GamesPlaytimeCard(window: ReportWindow, modifier: Modifier = Modifier, columns: Int = 1) {
    val scheme = MaterialTheme.colorScheme
    val version = GamePlaytime.version
    val inPeriod = remember(window.startMs, window.endMs, version) { GamePlaytime.totals(window.startMs, window.endMs) }
    val allTime = remember(version) { GamePlaytime.totals().values.sum() }
    val total = inPeriod.values.sum()

    Surface(shape = RoundedCornerShape(28.dp), color = scheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            StatsPanelHeader(
                title = str("stats_games_title"),
                subtitle = if (allTime > 0) {
                    str("stats_games_all_time", formatDurationShort(allTime))
                } else {
                    str("stats_games_empty")
                },
                icon = Icons.Rounded.SportsEsports,
                iconShape = MaterialShapes.Cookie9Sided.toShape(),
                iconContainer = scheme.tertiaryContainer,
                iconContent = scheme.onTertiaryContainer,
                actionLabel = str("stats_games_play"),
                actionIcon = Icons.Rounded.PlayArrow,
                onAction = { ArcadeLauncher.open() },
            )
            if (total > 0) {
                Spacer(Modifier.height(16.dp))
                Text(
                    formatDuration(total),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(12.dp))
                val top = inPeriod.values.maxOrNull() ?: 1L
                val sorted = inPeriod.entries.sortedByDescending { it.value }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    sorted.chunked(columns.coerceAtLeast(1)).forEach { rowGames ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            rowGames.forEach { (game, ms) ->
                                Box(Modifier.weight(1f)) { GameRow(game, ms, ms.toFloat() / top) }
                            }
                            repeat((columns - rowGames.size).coerceAtLeast(0)) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameRow(game: GameId, ms: Long, share: Float) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(scheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(game.icon, null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(str(game.titleKey), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatDurationShort(ms), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { share.coerceIn(0.02f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp), drawStopIndicator = {})
        }
    }
}
