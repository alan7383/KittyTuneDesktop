package com.alananasss.kittytune.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.TrackSourceBadgeStyle
import com.alananasss.kittytune.domain.Track

enum class AudioSourceType(
    val id: String,
    val displayName: String,
    val iconRes: String?,
    val inlineWidth: androidx.compose.ui.unit.Dp,
    val inlineHeight: androidx.compose.ui.unit.Dp,
) {
    SOUNDCLOUD("soundcloud", "SoundCloud", R.drawable.ic_logo_soundcloud, inlineWidth = 32.dp, inlineHeight = 14.dp),
    YOUTUBE("youtube", "YouTube", R.drawable.ic_logo_youtube, inlineWidth = 20.dp, inlineHeight = 14.dp),
    YOUTUBE_MUSIC("youtube_music", "YouTube Music", R.drawable.ic_logo_youtube_music, inlineWidth = 14.dp, inlineHeight = 14.dp),
    SPOTIFY("spotify", "Spotify", R.drawable.ic_logo_spotify, inlineWidth = 14.dp, inlineHeight = 14.dp),
    DEEZER("deezer", "Deezer", R.drawable.ic_logo_deezer, inlineWidth = 15.dp, inlineHeight = 14.dp),
    TIDAL("tidal", "TIDAL", R.drawable.ic_logo_tidal, inlineWidth = 14.dp, inlineHeight = 14.dp),
    QOBUZ("qobuz", "Qobuz", R.drawable.ic_logo_qobuz, inlineWidth = 14.dp, inlineHeight = 14.dp),
    APPLE_MUSIC("apple_music", "Apple Music", "drawable/ic_logo_apple_music.xml", inlineWidth = 14.dp, inlineHeight = 14.dp),
    LOCAL("local", "Local", null, inlineWidth = 16.dp, inlineHeight = 14.dp);

    companion object {
        fun fromTrack(track: Track?, resolvedSource: String? = null): AudioSourceType {
            if (track == null) return SOUNDCLOUD
            if (track.id < 0 && track.source != "youtube" && track.source != "youtube_music") {
                return LOCAL
            }
            val key = resolvedSource?.lowercase()
                ?: track.source?.lowercase()
                ?: when {
                    track.permalink?.startsWith("deezer:") == true -> "deezer"
                    track.permalink?.startsWith("tidal:") == true -> "tidal"
                    track.permalink?.startsWith("qobuz:") == true -> "qobuz"
                    track.permalink?.startsWith("spotify:") == true -> "spotify"
                    track.permalinkUrl?.contains("youtube.com") == true || track.permalinkUrl?.contains("youtu.be") == true -> "youtube"
                    else -> "soundcloud"
                }

            return when {
                key == "youtube_music" || key.contains("youtube_music") || key == "ytm" -> YOUTUBE_MUSIC
                key == "youtube" || key.contains("youtube") || key == "yt" -> YOUTUBE
                key == "soundcloud" || key.contains("soundcloud") || key == "sc" -> SOUNDCLOUD
                key.contains("spotify") -> SPOTIFY
                key.contains("deezer") -> DEEZER
                key.contains("tidal") -> TIDAL
                key.contains("qobuz") -> QOBUZ
                key.contains("apple") -> APPLE_MUSIC
                key == "local" || key.contains("local") -> LOCAL
                else -> SOUNDCLOUD
            }
        }
    }
}

/**
 * Modern, configurable badge displaying the source platform (YouTube, SoundCloud, Deezer, etc.)
 * of a given track or playing stream.
 */
@Composable
fun TrackSourceBadge(
    track: Track?,
    resolvedSource: String? = null,
    modifier: Modifier = Modifier,
    overrideStyle: TrackSourceBadgeStyle? = null,
) {
    if (track == null) return
    val prefs = remember { PlayerPreferences() }
    val enabled by prefs.fullPlayerSourceIndicatorEnabledFlow().collectAsState(initial = prefs.getFullPlayerSourceIndicatorEnabled())
    if (!enabled) return
    val badgeStyle by prefs.trackSourceBadgeStyleFlow().collectAsState(initial = prefs.getTrackSourceBadgeStyle())
    val effectiveStyle = overrideStyle ?: badgeStyle

    if (effectiveStyle == TrackSourceBadgeStyle.HIDDEN) return

    val sourceType = remember(track.id, track.source, resolvedSource) {
        AudioSourceType.fromTrack(track, resolvedSource)
    }

    val tooltipText = str("audio_source_tooltip", sourceType.displayName)

    Tip(text = tooltipText) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = modifier
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.5.dp)
            ) {
                if (effectiveStyle == TrackSourceBadgeStyle.ICON_AND_TEXT || effectiveStyle == TrackSourceBadgeStyle.ICON_ONLY) {
                    if (sourceType.iconRes != null) {
                        val resourceExists = remember(sourceType.iconRes) {
                            Thread.currentThread().contextClassLoader.getResource(sourceType.iconRes) != null
                        }
                        if (resourceExists) {
                            val effectiveTint = when (sourceType) {
                                AudioSourceType.SOUNDCLOUD -> Color(0xFFFF5500)
                                AudioSourceType.TIDAL, AudioSourceType.QOBUZ -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> Color.Unspecified
                            }
                            val pillWidth = when (sourceType) {
                                AudioSourceType.SOUNDCLOUD -> 18.dp
                                AudioSourceType.YOUTUBE -> 14.dp
                                else -> 11.dp
                            }
                            val pillHeight = when (sourceType) {
                                AudioSourceType.SOUNDCLOUD -> 7.8.dp
                                AudioSourceType.YOUTUBE -> 9.8.dp
                                else -> 11.dp
                            }
                            Icon(
                                painter = painterResource(sourceType.iconRes),
                                contentDescription = sourceType.displayName,
                                tint = effectiveTint,
                                modifier = Modifier.size(width = pillWidth, height = pillHeight)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Folder,
                            contentDescription = sourceType.displayName,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
                if (effectiveStyle == TrackSourceBadgeStyle.ICON_AND_TEXT || effectiveStyle == TrackSourceBadgeStyle.TEXT_ONLY) {
                    Text(
                        text = sourceType.displayName,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Clean, circular emblem badge overlaid directly on the album artwork (corner).
 * Features a high-contrast container, the official platform logo, and an interactive tooltip on hover
 * explaining the audio origin (e.g. "Audio venant de SoundCloud").
 */
@Composable
fun TrackSourceCoverBadge(
    track: Track?,
    resolvedSource: String? = null,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 20.dp,
    iconSize: androidx.compose.ui.unit.Dp = 12.dp,
) {
    if (track == null) return
    val prefs = remember { PlayerPreferences() }
    val enabled by prefs.fullPlayerSourceIndicatorEnabledFlow().collectAsState(initial = prefs.getFullPlayerSourceIndicatorEnabled())
    if (!enabled) return
    val badgeStyle by prefs.trackSourceBadgeStyleFlow().collectAsState(initial = prefs.getTrackSourceBadgeStyle())
    if (badgeStyle == TrackSourceBadgeStyle.HIDDEN) return

    val sourceType = remember(track.id, track.source, resolvedSource) {
        AudioSourceType.fromTrack(track, resolvedSource)
    }

    val tooltipText = str("audio_source_tooltip", sourceType.displayName)

    Tip(text = tooltipText) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shadowElevation = 3.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            modifier = modifier.size(size)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (sourceType.iconRes != null) {
                    val resourceExists = remember(sourceType.iconRes) {
                        Thread.currentThread().contextClassLoader.getResource(sourceType.iconRes) != null
                    }
                    if (resourceExists) {
                        val effectiveTint = when (sourceType) {
                            AudioSourceType.SOUNDCLOUD -> Color(0xFFFF5500)
                            AudioSourceType.TIDAL, AudioSourceType.QOBUZ -> MaterialTheme.colorScheme.onSurface
                            else -> Color.Unspecified
                        }
                        val badgeWidth = when (sourceType) {
                            AudioSourceType.SOUNDCLOUD -> 14.dp
                            AudioSourceType.YOUTUBE -> 13.dp
                            else -> iconSize
                        }
                        val badgeHeight = when (sourceType) {
                            AudioSourceType.SOUNDCLOUD -> 6.1.dp
                            AudioSourceType.YOUTUBE -> 9.1.dp
                            else -> iconSize
                        }
                        Icon(
                            painter = painterResource(sourceType.iconRes),
                            contentDescription = sourceType.displayName,
                            tint = effectiveTint,
                            modifier = Modifier.size(width = badgeWidth, height = badgeHeight)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = sourceType.displayName,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

/**
 * Inline source indicator designed for title rows in FullPlayer / TrackDetail:
 * Displays a middle dot (•) followed by the platform SVG logo with an interactive tooltip on hover.
 * The logo size is optically balanced across all platforms to match the cap-height of the title.
 */
@Composable
fun TrackSourceInlineDot(
    track: Track?,
    resolvedSource: String? = null,
    modifier: Modifier = Modifier,
    dotColor: Color = Color.White.copy(alpha = 0.6f),
    iconTint: Color = Color.Unspecified,
    iconSize: androidx.compose.ui.unit.Dp = 14.dp,
    customHeight: androidx.compose.ui.unit.Dp? = null,
) {
    if (track == null) return
    val prefs = remember { PlayerPreferences() }
    val fullPlayerEnabled by prefs.fullPlayerSourceIndicatorEnabledFlow().collectAsState(initial = prefs.getFullPlayerSourceIndicatorEnabled())
    if (!fullPlayerEnabled) return

    val sourceType = remember(track.id, track.source, resolvedSource) {
        AudioSourceType.fromTrack(track, resolvedSource)
    }
    val tooltipText = str("audio_source_tooltip", sourceType.displayName)
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val iconAlpha by animateFloatAsState(if (isHovered) 1f else 0.85f)

    val effectiveHeight = customHeight ?: iconSize
    val scale = effectiveHeight.value / sourceType.inlineHeight.value
    val effectiveWidth = (sourceType.inlineWidth.value * scale).dp

    Tip(text = tooltipText) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.hoverable(interactionSource)
        ) {
            Text(
                text = "•",
                style = MaterialTheme.typography.titleMedium,
                color = dotColor,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(6.dp))
            if (sourceType.iconRes != null) {
                val resourceExists = remember(sourceType.iconRes) {
                    Thread.currentThread().contextClassLoader.getResource(sourceType.iconRes) != null
                }
                if (resourceExists) {
                    val effectiveTint = if (iconTint != Color.Unspecified) {
                        iconTint.copy(alpha = iconAlpha)
                    } else {
                        when (sourceType) {
                            AudioSourceType.SOUNDCLOUD -> Color(0xFFFF5500).copy(alpha = iconAlpha)
                            AudioSourceType.TIDAL, AudioSourceType.QOBUZ -> Color.White.copy(alpha = iconAlpha)
                            else -> Color.Unspecified
                        }
                    }
                    Icon(
                        painter = painterResource(sourceType.iconRes),
                        contentDescription = sourceType.displayName,
                        tint = effectiveTint,
                        modifier = Modifier
                            .size(width = effectiveWidth, height = effectiveHeight)
                            .alpha(if (effectiveTint == Color.Unspecified) iconAlpha else 1f)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = sourceType.displayName,
                    tint = dotColor.copy(alpha = iconAlpha),
                    modifier = Modifier.size(width = effectiveWidth, height = effectiveHeight)
                )
            }
        }
    }
}
