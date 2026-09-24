package com.alananasss.kittytune.ui.player.automix

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.audio.automix.AutomixManager
import com.alananasss.kittytune.utils.makeTimeString

@Composable
fun AutomixDebugOverlay(
    currentPositionMs: Long,
    modifier: Modifier = Modifier,
) {
    val isVisible by AutomixManager.isDebugOverlayVisible.collectAsState()
    val debugInfo by AutomixManager.automixDebugInfo.collectAsState()
    val isAutomixing by AutomixManager.isAutomixing.collectAsState()
    val beatsLeft by AutomixManager.mixBeatsLeft.collectAsState()

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier
    ) {
        val mono = MaterialTheme.typography.labelSmall.copy(
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.3.sp,
        )

        val dbg = debugInfo
        val isMixing = isAutomixing || (beatsLeft != null && beatsLeft!! > 0)
        val isReady = dbg?.triggerTimeMs != null && !isMixing
        val isAnalyzing = dbg?.status?.startsWith("Analyzing", ignoreCase = true) == true

        val statusColor = when {
            isMixing -> Color(0xFF00E676) // Bright Green
            isReady -> Color(0xFF00E5FF) // Cyan
            isAnalyzing -> Color(0xFFFFD600) // Amber
            dbg?.status?.startsWith("fallback", ignoreCase = true) == true -> Color(0xFFFF5252) // Red/Orange
            else -> Color(0xFFB0BEC5) // Gray
        }

        Box(
            modifier = Modifier
                .widthIn(min = 280.dp, max = 360.dp)
                .shadow(16.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xF0131418))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "AUTOMIX HUD",
                            style = mono.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val badgeText = when {
                            isMixing -> "MIXING"
                            isReady -> "READY"
                            isAnalyzing -> "ANALYZING"
                            dbg != null -> dbg.status.take(12).uppercase()
                            else -> "STANDBY"
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(statusColor.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badgeText,
                                style = mono.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold),
                                color = statusColor
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close HUD",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier
                                .size(16.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { AutomixManager.setDebugOverlayEnabled(false) }
                                )
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Outgoing track line
                val outTitle = dbg?.outTitle ?: "Current track"
                val outBpmStr = dbg?.outBpm?.let { "%.1f BPM".format(it) } ?: "— BPM"
                val outConfStr = dbg?.outConfidence?.let { " (%.0f%%)".format(it * 100f) } ?: ""
                val outKeyStr = dbg?.outCamelot?.let { " [$it ${dbg.outKey ?: ""}]" } ?: (dbg?.outKey?.let { " [$it]" } ?: "")
                val outMixOutStr = dbg?.outMixOutMs?.takeIf { it > 0 }?.let { " • mixOut ${makeTimeString(it)}" } ?: ""

                Text(
                    text = "OUT: $outTitle",
                    style = mono.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "     $outBpmStr$outConfStr$outKeyStr$outMixOutStr",
                    style = mono.copy(fontSize = 10.sp),
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(6.dp))

                // Incoming track line
                val inTitle = dbg?.inTitle ?: "Next track"
                val inBpmStr = dbg?.inBpm?.let { "%.1f BPM".format(it) } ?: "— BPM"
                val inConfStr = dbg?.inConfidence?.let { " (%.0f%%)".format(it * 100f) } ?: ""
                val inKeyStr = dbg?.inCamelot?.let { " [$it ${dbg.inKey ?: ""}]" } ?: (dbg?.inKey?.let { " [$it]" } ?: "")
                val inMixInStr = dbg?.inMixInMs?.takeIf { it > 0 }?.let { " • mixIn ${makeTimeString(it)}" } ?: ""

                Text(
                    text = "IN : $inTitle",
                    style = mono.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "     $inBpmStr$inConfStr$inKeyStr$inMixInStr",
                    style = mono.copy(fontSize = 10.sp),
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(8.dp))

                // Bottom Transition status
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Column {
                        if (isMixing) {
                            Text(
                                text = "🎛️ ACTIVE BLEND: Bass duck -10dB @ 150Hz" +
                                    (beatsLeft?.let { " ($it beats left)" } ?: ""),
                                style = mono.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF00E676)
                            )
                        } else if (dbg?.triggerTimeMs != null) {
                            val remainingS = ((dbg.triggerTimeMs - currentPositionMs) / 1000).coerceAtLeast(0)
                            Text(
                                text = "⚡ Transition in ${remainingS}s (${makeTimeString(dbg.triggerTimeMs)})" +
                                    (beatsLeft?.let { " • $it beats" } ?: ""),
                                style = mono.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF00E5FF)
                            )
                            val overlapS = dbg.overlapMs?.let { "%.1fs".format(it / 1000f) } ?: "auto"
                            val startStr = dbg.incomingStartMs?.let { "start ${makeTimeString(it)}" }
                            val tempoStr = dbg.tempoRatio?.let { if (it != 1f) "tempo ×%.3f".format(it) else null }
                            val pitchStr = dbg.pitchRatio?.let { if (it != 1f) "pitch ×%.3f".format(it) else null }
                            val details = listOfNotNull("overlap $overlapS", startStr, tempoStr, pitchStr).joinToString(" • ")
                            if (details.isNotBlank()) {
                                Text(
                                    text = details,
                                    style = mono.copy(fontSize = 9.sp),
                                    color = Color.White.copy(alpha = 0.75f)
                                )
                            }
                        } else {
                            Text(
                                text = dbg?.status?.ifBlank { "Standby" } ?: "Standby",
                                style = mono,
                                color = Color.White.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
