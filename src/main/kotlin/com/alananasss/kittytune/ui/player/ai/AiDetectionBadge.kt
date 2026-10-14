package com.alananasss.kittytune.ui.player.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.zIndex
import com.alananasss.kittytune.audio.ai.AiDetectionManager
import com.alananasss.kittytune.core.BackHandler
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.BlockManager
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.player.PlayerViewModel
import kotlin.math.roundToInt

/**
 * Desktop port of the Android AiDetectionBadge (issue #41).
 *
 * Compact pill-shaped badge displayed in the player next to the track metadata.
 *
 * Shows:
 * - "AI XX%" when ArtifactNet detects AI-generated audio (score ≥ threshold)
 * - "AI..." with a pulsing indicator while analyzing the audio stream
 * - "Human XX%" if the user enabled [PlayerPreferences.aiShowHumanBadge]
 *
 * Tapping the badge opens [AiDetectionDialog] with forensic details and block actions.
 * (No haptics on desktop; hover pointer instead.)
 */
@Composable
fun AiDetectionBadge(
    result: AiDetectionManager.Result?,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs = remember { PlayerPreferences() }
    val showBadge = prefs.aiShowBadge
    if (!showBadge) {
        return
    }

    val threshold = prefs.aiScoreThreshold
    val score = if (result == null || result.score.isNaN()) 0f else result.score.coerceIn(0f, 1f)
    val pct = (score * 100).roundToInt().coerceIn(0, 100)
    val isAi = result?.isAi ?: (score >= threshold)
    val isAnalyzing = result == null || result.status == AiDetectionManager.Status.ANALYZING || result.status == AiDetectionManager.Status.IDLE

    if (!isAi && !isAnalyzing && !prefs.aiShowHumanBadge) {
        return
    }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val (containerColor, contentColor) = when {
        isAnalyzing -> {
            MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
        }
        isAi -> {
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        }
        else -> {
            if (isDark) {
                Color(0xFF1E3A24) to Color(0xFF81C784)
            } else {
                Color(0xFFE8F5E9) to Color(0xFF1B5E20)
            }
        }
    }

    val interactionSource = remember { MutableInteractionSource() }

    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = modifier
                .clip(CircleShape)
                .background(containerColor)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = onClick,
                )
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            if (isAnalyzing) {
                val infiniteTransition = rememberInfiniteTransition(label = "AiBadgePulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(750, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "AiBadgePulseAlpha",
                )
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = str("ai_badge_scanning"),
                    tint = contentColor.copy(alpha = pulseAlpha),
                    modifier = Modifier.size(11.dp),
                )
                Text(
                    text = str("ai_badge_scanning"),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.2.sp,
                    ),
                    color = contentColor.copy(alpha = pulseAlpha),
                    maxLines = 1,
                )
            } else if (isAi) {
                Icon(
                    imageVector = Icons.Rounded.SmartToy,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = str("ai_badge_ai_tag", pct),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp,
                    ),
                    maxLines = 1,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.VerifiedUser,
                    contentDescription = null,
                    modifier = Modifier.size(11.dp),
                )
                Text(
                    text = str("ai_badge_human_tag", 100 - pct),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.2.sp,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Detailed dialog opened when clicking the AI detection badge.
 * Desktop adaptation of the Android AiDetectionBottomSheet: a centered dialog
 * card instead of a bottom sheet, with the same ArtifactNet probability meter,
 * forensic context, and 1-tap block actions.
 */
@Composable
fun AiDetectionDialog(
    viewModel: PlayerViewModel,
    onDismissRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val track = viewModel.currentTrack ?: return
    val prefs = remember { PlayerPreferences() }
    val aiResult by AiDetectionManager.result.collectAsState()

    val rawScore = aiResult?.score ?: 0f
    val score = if (rawScore.isNaN()) 0f else rawScore.coerceIn(0f, 1f)
    val pct = (score * 100).roundToInt().coerceIn(0, 100)
    val threshold = prefs.aiScoreThreshold
    val isAi = score >= threshold
    val isAnalyzing = aiResult?.status == AiDetectionManager.Status.ANALYZING

    val blockedTracks by BlockManager.blockedTrackIdsFlow.collectAsState()
    val blockedArtists by BlockManager.blockedArtistIdsFlow.collectAsState()
    val isTrackBlocked = track.id in blockedTracks
    val artistId = track.user?.id
    val isArtistBlocked = artistId != null && artistId in blockedArtists

    BackHandler(onBack = onDismissRequest)

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .width(480.dp)
                    .heightIn(max = 640.dp)
                    .focusRequester(focusRequester)
                    .focusable()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}, // Absorb click inside card
                    )
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.Escape && (event.type == KeyEventType.KeyDown || event.type == KeyEventType.KeyUp)) {
                            onDismissRequest()
                            true
                        } else {
                            false
                        }
                    },
            ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // ─── Header ─────────────────────────────────────────────────────
                    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                    val (dialogBadgeContainer, dialogBadgeContent) = when {
                        isAnalyzing -> {
                            MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        isAi -> {
                            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                        }
                        else -> {
                            if (isDark) {
                                Color(0xFF1E3A24) to Color(0xFF81C784)
                            } else {
                                Color(0xFFE8F5E9) to Color(0xFF1B5E20)
                            }
                        }
                    }

                    val scoreAccentColor by animateColorAsState(
                        targetValue = when {
                            isAnalyzing -> MaterialTheme.colorScheme.primary
                            isAi -> MaterialTheme.colorScheme.error
                            else -> if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
                        },
                        label = "AiSheetScoreAccent",
                    )

                Box(
                    modifier = Modifier.size(68.dp),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    Surface(
                        modifier = Modifier.size(68.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        val artwork = track.fullResArtwork.ifBlank { track.artworkUrl.orEmpty() }
                        if (!artwork.isNullOrBlank()) {
                            AsyncImage(
                                model = artwork,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.SmartToy,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier.size(24.dp),
                        shape = CircleShape,
                        color = dialogBadgeContainer,
                        contentColor = dialogBadgeContent,
                        shadowElevation = 2.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when {
                                    isAnalyzing -> Icons.Rounded.AutoAwesome
                                    isAi -> Icons.Rounded.SmartToy
                                    else -> Icons.Rounded.VerifiedUser
                                },
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = when {
                            isAnalyzing -> str("ai_sheet_title_analyzing")
                            isAi -> str("ai_sheet_title_ai")
                            else -> str("ai_sheet_title_human")
                        },
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${track.title ?: str("untitled_track")} · ${track.displayArtist.ifBlank { str("unknown_artist") }}",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // ─── Score Meter Card ───────────────────────────────────────────
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = str("ai_sheet_prob_label"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = if (isAnalyzing) "…" else "$pct%",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 32.sp,
                                    ),
                                    color = scoreAccentColor,
                                )
                            }

                            // Status Chip (Pure Material 3 tonal capsule - no harsh border)
                            Surface(
                                shape = CircleShape,
                                color = dialogBadgeContainer,
                                contentColor = dialogBadgeContent,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Icon(
                                        imageVector = when {
                                            isAnalyzing -> Icons.Rounded.AutoAwesome
                                            isAi -> Icons.Rounded.SmartToy
                                            else -> Icons.Rounded.VerifiedUser
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = when {
                                            isAnalyzing -> str("ai_badge_scanning")
                                            score >= 0.85f -> str("ai_chip_high_confidence")
                                            score >= threshold -> str("ai_chip_ai_detected")
                                            else -> str("ai_chip_human_audio")
                                        },
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.2.sp,
                                        ),
                                    )
                                }
                            }
                        }

                        // Confidence Meter Bar
                        val animatedScore by animateFloatAsState(
                            targetValue = if (isAnalyzing) 0.5f else score,
                            animationSpec = tween(600),
                            label = "ScoreProgress",
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(12.dp)
                                    .clip(CircleShape),
                            ) {
                                val trackBrush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF4CAF50), // Green (Human)
                                        Color(0xFFFFC107), // Yellow (Mid)
                                        Color(0xFFFF7043), // Orange
                                        Color(0xFFE53935), // Red (AI)
                                    ),
                                )

                                // Background gradient track
                                drawRoundRect(
                                    brush = trackBrush,
                                    size = size,
                                    cornerRadius = CornerRadius(size.height / 2),
                                )

                                // Threshold marker line
                                val thresholdX = size.width * threshold
                                drawLine(
                                    color = Color.White.copy(alpha = 0.85f),
                                    start = Offset(thresholdX, 0f),
                                    end = Offset(thresholdX, size.height),
                                    strokeWidth = 2.dp.toPx(),
                                )

                                // Thumb indicator
                                val indicatorX = (size.width * animatedScore).coerceIn(size.height / 2, size.width - size.height / 2)
                                drawCircle(
                                    color = Color.White,
                                    radius = size.height / 2 + 1.dp.toPx(),
                                    center = Offset(indicatorX, size.height / 2),
                                )
                                drawCircle(
                                    color = Color.Black.copy(alpha = 0.7f),
                                    radius = size.height / 4,
                                    center = Offset(indicatorX, size.height / 2),
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = str("ai_sheet_meter_human"),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = str("ai_sheet_meter_threshold", (threshold * 100).toInt()),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = str("ai_sheet_meter_ai"),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // Verdict explanation text
                        Text(
                            text = when {
                                isAnalyzing -> str("ai_sheet_confidence_analyzing")
                                score >= 0.85f -> str("ai_sheet_confidence_high_ai")
                                score >= threshold -> str("ai_sheet_confidence_likely_ai")
                                score >= 0.35f -> str("ai_sheet_confidence_borderline")
                                else -> str("ai_sheet_confidence_human")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // ─── Privacy Note ───────────────────────────────────────────────
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = str(AiDetectionManager.currentModel.noteKey),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp, lineHeight = 15.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // ─── Actions ────────────────────────────────────────────────────
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 1. Block / Unblock Track
                    FilledTonalButton(
                        onClick = {
                            if (isTrackBlocked) {
                                BlockManager.unblockTrack(track.id)
                            } else {
                                BlockManager.blockTrack(
                                    track = track,
                                    reason = if (isAi) BlockManager.REASON_AI_GENERATED else BlockManager.REASON_MANUAL,
                                    skipIfCurrent = true,
                                    currentlyPlayingId = viewModel.currentTrack?.id,
                                )
                            }
                            onDismissRequest()
                        },
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isTrackBlocked) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                            contentColor = if (isTrackBlocked) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                        ),
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                    ) {
                        Icon(
                            imageVector = if (isTrackBlocked) Icons.Rounded.VisibilityOff else Icons.Rounded.Block,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isTrackBlocked) {
                                str("ai_sheet_unblock_track")
                            } else {
                                str("ai_sheet_block_track")
                            },
                        )
                    }

                    // 2. Block / Unblock Artist
                    val artistName = track.user?.username?.ifBlank { null }
                        ?: track.displayArtist.ifBlank { null }
                        ?: ""
                    if (artistId != null && artistName.isNotBlank()) {
                        FilledTonalButton(
                            onClick = {
                                if (isArtistBlocked) {
                                    BlockManager.unblockArtist(artistId)
                                } else {
                                    BlockManager.blockArtist(
                                        artistId = artistId,
                                        artistName = artistName,
                                        avatarUrl = track.user.avatarUrl,
                                        source = track.source ?: "soundcloud",
                                        reason = if (isAi) BlockManager.REASON_AI_GENERATED else BlockManager.REASON_MANUAL,
                                        currentlyPlayingTrack = viewModel.currentTrack,
                                    )
                                }
                                onDismissRequest()
                            },
                            shapes = ButtonDefaults.shapes(),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                        ) {
                            Icon(
                                imageVector = if (isArtistBlocked) Icons.Rounded.PersonAdd else Icons.Rounded.PersonOff,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isArtistBlocked) {
                                    str("menu_unblock_artist")
                                } else {
                                    str("ai_sheet_block_artist", artistName)
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    // 3. Settings shortcut
                    TextButton(
                        onClick = onOpenSettings,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(str("ai_sheet_open_settings"))
                    }
                }
            }

            IconButton(
                onClick = onDismissRequest,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .size(36.dp)
                    .zIndex(100f)
                    .pointerHoverIcon(PointerIcon.Hand),
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = str("btn_cancel"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
}
}
