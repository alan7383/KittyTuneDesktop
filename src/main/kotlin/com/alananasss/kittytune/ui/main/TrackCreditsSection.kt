package com.alananasss.kittytune.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.spotify.SpotifyCreditArtist
import com.alananasss.kittytune.data.spotify.SpotifyCredits
import com.alananasss.kittytune.data.spotify.SpotifyRepository
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.utils.makeTimeString

/**
 * The credits of a Spotify catalog track, parity with the Android credits sheet: performers, writers,
 * producers, sources, then the release details.
 *
 * Each section is one card rather than one list item per name, so a section reads as a group instead of
 * a column of rows spaced like separate blocks.
 */
internal fun LazyListScope.spotifyCreditsSection(
    vm: PlayerViewModel,
    track: Track,
    credits: SpotifyCredits?,
) {
    val performers = performerArtists(track, credits)
    if (performers.isNotEmpty()) item(key = "credits_performers") {
        CreditsCard(str("spotify_credits_performers")) {
            performers.forEach { CreditArtistRow(it, vm) }
        }
    }

    val writers = credits?.roles?.firstOrNull { it.roleTitle.isWritersRole() }?.artists.orEmpty()
    val composer = track.publisherMetadata?.composer?.takeIf { it.isNotBlank() }
    if (writers.isNotEmpty()) item(key = "credits_writers") {
        CreditsCard(str("spotify_credits_writers")) {
            writers.forEach { CreditNameRow(it.name, it.subroles.joinToString(", ") { r -> creditSubroleLabel(r) }) }
        }
    } else if (composer != null) item(key = "credits_composer") {
        CreditsCard(str("spotify_credits_composer")) { CreditNameRow(composer, subtitle = "") }
    }

    val producers = credits?.roles
        ?.filter { it.roleTitle.contains("Producer", true) || it.roleTitle.contains("Production", true) }
        ?.flatMap { it.artists }
        ?.distinctBy { it.name }
        .orEmpty()
    if (producers.isNotEmpty()) item(key = "credits_producers") {
        CreditsCard(str("spotify_credits_producers")) {
            producers.forEach { CreditNameRow(it.name, subtitle = "") }
        }
    }

    val sources = credits?.sourceNames?.takeIf { it.isNotEmpty() }
        ?: listOfNotNull(track.publisherMetadata?.publisher?.takeIf { it.isNotBlank() })
    if (sources.isNotEmpty()) item(key = "credits_sources") {
        CreditsCard(str("spotify_credits_sources")) {
            Text(
                text = sources.joinToString("\n"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }

    item(key = "credits_release") { ReleaseDetailsCard(vm, track) }
}

/** The performers role of the credits, or the track's own artist list when the credits have none. */
private fun performerArtists(track: Track, credits: SpotifyCredits?): List<SpotifyCreditArtist> {
    val role = credits?.roles?.firstOrNull { role ->
        role.roleTitle.contains("Performer", true) || role.roleTitle.contains("Artist", true)
    }
    role?.artists?.takeIf { it.isNotEmpty() }?.let { return it }

    val refs = track.artists.orEmpty()
    return refs.map { ref ->
        SpotifyCreditArtist(
            id = ref.id,
            name = ref.name,
            uri = ref.uri,
            imageUri = ref.avatarUrl,
            subroles = listOf(if (refs.size > 1 && ref !== refs.first()) "Featured Artist" else "Main Artist")
        )
    }
}

private fun String.isWritersRole(): Boolean =
    contains("Writer", true) || contains("Lyric", true) || contains("Composition", true) || contains("Composer", true)

@Composable
private fun ReleaseDetailsCard(vm: PlayerViewModel, track: Track) {
    val meta = track.publisherMetadata
    // Playlist/search/artist payloads carry no release date, so fetch this one track's (one request,
    // memoized). No date, no row — better than "Unknown".
    val releaseDate by produceState(track.releaseDate, track.id) {
        if (value.isNullOrBlank()) {
            val spotifyId = vm.getSpotifyTrackId(track)
            if (!spotifyId.isNullOrBlank()) {
                value = runCatching { SpotifyRepository.getTrackReleaseDate(spotifyId) }.getOrNull()
            }
        }
    }
    CreditsCard(title = null) {
        releaseDate?.takeIf { it.isNotBlank() }?.let { DetailInfoRow(str("detail_release_date"), formatReleaseDate(it)) }
        meta?.albumTitle?.let { DetailInfoRow(str("profile_tab_albums"), it) }
        DetailInfoRow(str("detail_duration"), makeTimeString(track.durationMs ?: 0L))
        meta?.publisher?.let { DetailInfoRow(str("spotify_credits_sources"), it) }
    }
}

@Composable
private fun CreditsCard(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = SpotifyGreen,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            content()
        }
    }
}

@Composable
private fun CreditNameRow(name: String, subtitle: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(text = name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun creditSubroleLabel(subrole: String): String = when (subrole.trim().lowercase()) {
    "main artist", "main performer" -> str("spotify_credits_main_artist")
    "featured artist" -> str("spotify_credits_featured_artist")
    "composer" -> str("spotify_credits_composer")
    "lyricist" -> str("spotify_credits_lyricist")
    "producer" -> str("spotify_credits_producer")
    else -> subrole
}

/** Label / value row used for release date, album, duration. */
@Composable
private fun DetailInfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Clickable credit artist row: avatar, name, localized subroles, chevron. */
@Composable
private fun CreditArtistRow(artist: SpotifyCreditArtist, vm: PlayerViewModel) {
    // The credits payload only sometimes carries an image; fetch the missing ones so the section shows
    // faces instead of a column of silhouettes.
    val avatar by produceState(artist.imageUri, artist.id) {
        if (value.isNullOrBlank() && artist.id.isNotBlank()) {
            value = runCatching { SpotifyRepository.getArtistAvatar(artist.id) }.getOrNull()
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = artist.id.isNotBlank()) { vm.navigateToSpotifyArtist(artist.id) }
            .padding(vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!avatar.isNullOrBlank()) {
                AsyncImage(
                    model = avatar,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Rounded.Person, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = artist.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            val subroles = artist.subroles.joinToString(", ") { creditSubroleLabel(it) }
            if (subroles.isNotBlank()) {
                Text(
                    text = subroles,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Rounded.ArrowForwardIos, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
    }
}
