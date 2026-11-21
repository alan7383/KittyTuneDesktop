package com.alananasss.kittytune.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.isSpecified
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.spotify.SpotifyArtistRef
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.player.lyrics.lyricUnderline

typealias TrackArtistClick = (Track, SpotifyArtistRef?) -> Unit

private fun SpotifyArtistRef.hasProfile() = id.isNotBlank() || !uri.isNullOrBlank()

/**
 * Whether the artist line of a track row should behave as a link. Catalog tracks
 * carry their credited artists, Spotify-only tracks fall
 * back to the synthetic artist urn/permalink, and SoundCloud ones to the uploader
 * id. Local files and YouTube mixes have no profile to open, so they stay inert.
 */
fun Track.canOpenArtist(): Boolean {
    if (artists?.any { it.hasProfile() } == true) return true
    if (source == "spotify") {
        // A synthetic urn built from a blank artist id ("spotify:artist:") leads nowhere.
        val urnId = user?.urn?.takeIf { it.startsWith("spotify:artist:") }?.removePrefix("spotify:artist:")
        return !urnId.isNullOrBlank() || !user?.permalink.isNullOrBlank()
    }
    if (source == "youtube") return false
    return (user?.id ?: 0L) > 0L
}

@Composable
fun ArtistLinkText(
    track: Track,
    onArtistClick: TrackArtistClick?,
    modifier: Modifier = Modifier,
    text: String = track.displayArtist.ifBlank { track.user?.username.orEmpty() }.ifBlank { str("unknown_artist") },
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

    val artists = track.artists.orEmpty()
        .filter { it.name.isNotBlank() }
        .distinctBy { it.id.ifBlank { it.uri.orEmpty().ifBlank { it.name } } }
    if (artists.size > 1) {
        val linkStyles = TextLinkStyles(
            hoveredStyle = SpanStyle(color = hoverColor, textDecoration = TextDecoration.Underline),
            focusedStyle = SpanStyle(color = hoverColor, textDecoration = TextDecoration.Underline)
        )
        val linkedArtists = buildAnnotatedString {
            artists.forEachIndexed { index, artist ->
                if (index > 0) append(", ")
                if (artist.hasProfile()) {
                    withLink(LinkAnnotation.Clickable("artist_$index", linkStyles) {
                        onArtistClick(track, artist)
                    }) {
                        append(artist.name)
                    }
                } else {
                    append(artist.name)
                }
            }
        }
        Text(
            text = linkedArtists,
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
    // Same fix as the lyrics (issue #33): TextDecoration.Underline is drawn per
    // font run, so an artist name in Cyrillic / Arabic / CJK falling back to a
    // system face comes out as a mismatched dashed rule. One hand-drawn rect
    // per laid-out line via lyricUnderline instead.
    var linkLayout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    val linkColor = if (hovered) hoverColor else color
    val fontSizeSp = if (style.fontSize.isSpecified) style.fontSize.value else 14f
    Text(
        text = text,
        style = style,
        color = linkColor,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = { linkLayout = it },
        modifier = modifier
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null) { onArtistClick(track, null) }
            .lyricUnderline(
                layout = { linkLayout },
                visible = hovered,
                fontSizeSp = fontSizeSp,
                color = linkColor,
            )
    )
}
