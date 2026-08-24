package com.alananasss.kittytune.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.domain.Track

/**
 * Whether the artist line of a track row should behave as a link. Catalog tracks
 * carry their credited artists (a feat opens the picker), Spotify-only tracks fall
 * back to the synthetic artist urn/permalink, and SoundCloud ones to the uploader
 * id. Local files and YouTube mixes have no profile to open, so they stay inert.
 */
fun Track.canOpenArtist(): Boolean {
    if (artists?.any { it.id.isNotBlank() } == true) return true
    if (source == "spotify") {
        // A synthetic urn built from a blank artist id ("spotify:artist:") leads nowhere.
        val urnId = user?.urn?.takeIf { it.startsWith("spotify:artist:") }?.removePrefix("spotify:artist:")
        return !urnId.isNullOrBlank() || !user?.permalink.isNullOrBlank()
    }
    if (source == "youtube") return false
    return (user?.id ?: 0L) > 0L
}

/**
 * Artist label that reads as a link: highlighted and underlined on hover, hand
 * cursor, and a click routed through [onArtistClick] — which callers wire to
 * PlayerViewModel.navigateToTrackArtist, so a single artist opens the profile
 * straight away while a track credited to several opens the picker.
 *
 * Renders the exact same text as a plain label when the track has no reachable
 * artist (local file, YouTube mix, download without an uploader), so a row never
 * offers a link that goes nowhere.
 */
@Composable
fun ArtistLinkText(
    track: Track,
    onArtistClick: ((Track) -> Unit)?,
    modifier: Modifier = Modifier,
    text: String = track.user?.username?.takeIf { it.isNotBlank() } ?: str("unknown_artist"),
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    hoverColor: Color = MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight? = null,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis
) {
    if (onArtistClick == null || !track.canOpenArtist()) {
        Text(
            text = text,
            style = style,
            color = color,
            fontWeight = fontWeight,
            maxLines = maxLines,
            overflow = overflow,
            modifier = modifier
        )
        return
    }

    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Text(
        text = text,
        style = style,
        color = if (hovered) hoverColor else color,
        fontWeight = fontWeight,
        textDecoration = if (hovered) TextDecoration.Underline else null,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null) { onArtistClick(track) }
    )
}
