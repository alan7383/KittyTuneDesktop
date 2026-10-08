package com.alananasss.kittytune.ui.profile

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.Toaster
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.DownloadManager
import com.alananasss.kittytune.data.mix.ArtistMixes
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.domain.getHighResAvatarUrl
import com.alananasss.kittytune.domain.isDefaultAvatar
import com.alananasss.kittytune.ui.common.Tip
import com.alananasss.kittytune.ui.common.monthlyListenersLabel
import com.alananasss.kittytune.ui.common.pressScale
import com.alananasss.kittytune.ui.common.rememberArtistProfile
import com.alananasss.kittytune.ui.common.viewableCover
import com.alananasss.kittytune.ui.library.LibraryViewModel
import com.alananasss.kittytune.ui.player.PlaybackContext
import com.alananasss.kittytune.ui.player.PlayerViewModel
import kotlinx.coroutines.launch
import java.text.NumberFormat

/**
 * The top of an artist's page (issue #66).
 *
 * The banner is shown as it is, full size: it used to be fetched at 128 px and blurred, which is the "pixelated
 * header" from the report. When SoundCloud has none, the artist's header from their streaming profile is used,
 * and only when neither exists does the blurred portrait stand in. The name, the monthly listeners and a few
 * lines about the artist sit over its lower edge, with the actions under them: listen, a trailer of the top
 * songs, follow, pin to the library, and a menu with the rest.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ArtistHero(
    user: User,
    profileViewModel: ProfileViewModel,
    playerViewModel: PlayerViewModel,
    artistContext: PlaybackContext?,
    onNavigate: (String) -> Unit,
    onAbout: () -> Unit,
) {
    val profile = rememberArtistProfile(user.username)
    val banner = user.bannerUrl ?: profile?.headerImageUrl ?: profileViewModel.spotifyArtist?.headerImageUrl
    val portrait = user.avatarUrl.takeIf { !it.isDefaultAvatar() }?.getHighResAvatarUrl() ?: profile?.avatarUrl
    val scheme = MaterialTheme.colorScheme
    val about = user.description?.takeIf { it.isNotBlank() } ?: profile?.biography

    Box(Modifier.fillMaxWidth().height(HERO_HEIGHT).clipToBounds()) {
        when {
            banner != null -> AsyncImage(
                model = banner,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            portrait != null -> AsyncImage(
                model = portrait,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(48.dp),
            )
            else -> Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(scheme.primaryContainer, scheme.tertiaryContainer))))
        }
        // Darkened towards the bottom, where the words are, and into the page's own colour at the very edge.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.15f),
                    0.45f to Color.Black.copy(alpha = 0.25f),
                    0.85f to scheme.background.copy(alpha = 0.92f),
                    1f to scheme.background,
                )
            )
        )

        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 28.dp, vertical = 20.dp).widthIn(max = 920.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Surface(shape = CircleShape, shadowElevation = 12.dp, color = scheme.surfaceVariant, modifier = Modifier.size(AVATAR_SIZE)) {
                    ArtistAvatar(avatarUrl = portrait ?: user.avatarUrl, modifier = Modifier.fillMaxSize().viewableCover(portrait))
                }
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = user.username ?: str("unknown_artist"),
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
                            color = scheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (user.verified || profile?.isVerified == true) {
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Rounded.Verified, null, tint = scheme.primary, modifier = Modifier.size(28.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    val facts = listOfNotNull(
                        profile?.monthlyListeners?.let(::monthlyListenersLabel),
                        user.followersCount.takeIf { it > 0 && !profileViewModel.isSpotifyProfile }?.let {
                            "${NumberFormat.getIntegerInstance(com.alananasss.kittytune.core.Strings.locale()).format(it)} ${str("profile_followers")}"
                        },
                        listOfNotNull(user.city, user.countryCode).filter { it.isNotBlank() }.joinToString(", ").takeIf { it.isNotBlank() },
                    )
                    if (facts.isNotEmpty()) {
                        Text(
                            facts.joinToString("  ·  "),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (about != null) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                about.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            TextButton(onClick = onAbout, shapes = ButtonDefaults.shapes()) {
                                Text(str("artist_read_more"), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            ArtistActions(user, profileViewModel, playerViewModel, artistContext, onNavigate, onAbout)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ArtistActions(
    user: User,
    profileViewModel: ProfileViewModel,
    playerViewModel: PlayerViewModel,
    artistContext: PlaybackContext?,
    onNavigate: (String) -> Unit,
    onAbout: () -> Unit,
) {
    val libraryViewModel: LibraryViewModel = androidx.lifecycle.viewmodel.compose.viewModel {
        LibraryViewModel(com.alananasss.kittytune.core.AppInstance.application)
    }
    val isSaved by DownloadManager.isArtistSavedFlow(user.id).collectAsState(initial = null)
    val pinKey = "artist_${user.id}"
    var isPinned by remember(user.id) { mutableStateOf(libraryViewModel.isItemPinned(pinKey)) }
    var menuOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val topTracks = profileViewModel.popularTracks.ifEmpty { profileViewModel.allTracks }
    val context = artistContext ?: PlaybackContext(user.username.orEmpty(), "profile:${user.id}")

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = { if (topTracks.isNotEmpty()) playerViewModel.playPlaylist(topTracks.toList(), 0, context, respectShuffle = false) },
            enabled = topTracks.isNotEmpty(),
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.ContentPadding,
            modifier = Modifier.height(52.dp),
        ) {
            Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(str("artist_listen"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Tip(str("artist_trailer_tip")) {
            FilledTonalButton(
                onClick = { playerViewModel.playTrailer(topTracks.toList(), context) },
                enabled = topTracks.isNotEmpty(),
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.height(52.dp),
            ) {
                Icon(Icons.Rounded.SlowMotionVideo, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(str("artist_trailer"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
        }
        if (!profileViewModel.isSpotifyProfile) {
            Tip(str("btn_follow")) {
                FilledTonalIconButton(
                    onClick = { DownloadManager.toggleSaveArtist(user) },
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        if (isSaved != null) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = str("btn_follow"),
                        tint = if (isSaved != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            // Pinned in the library's list at the top of the left panel; following comes with it, since only a
            // followed artist is in the library at all.
            Tip(if (isPinned) str("artist_unpin") else str("artist_pin")) {
                FilledTonalIconButton(
                    onClick = {
                        if (isSaved == null) DownloadManager.toggleSaveArtist(user)
                        libraryViewModel.togglePinItem(pinKey)
                        isPinned = !isPinned
                    },
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        if (isPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                        contentDescription = null,
                        tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        Box {
            Tip(str("artist_more")) {
                FilledTonalIconButton(onClick = { menuOpen = true }, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = str("artist_more"))
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, shape = RoundedCornerShape(16.dp)) {
                ArtistMenuItem(Icons.Rounded.AutoAwesome, str("artist_mix_style", user.username.orEmpty())) {
                    menuOpen = false
                    scope.launch {
                        val tracks = ArtistMixes.inTheStyleOf(user.id.takeIf { !profileViewModel.isSpotifyProfile }, user.username.orEmpty())
                        if (tracks.isNotEmpty()) playerViewModel.playPlaylist(tracks, 0, context, respectShuffle = false)
                    }
                }
                ArtistMenuItem(Icons.Rounded.Radio, str("artist_mix_radio", user.username.orEmpty())) {
                    menuOpen = false
                    onNavigate(artistRadioDestination(user, profileViewModel))
                }
                ArtistMenuItem(Icons.Rounded.Share, str("btn_share")) {
                    menuOpen = false
                    val shareUrl = user.permalinkUrl ?: "https://soundcloud.com/${user.permalink ?: user.username?.replace(" ", "")?.lowercase()}"
                    val selection = java.awt.datatransfer.StringSelection(shareUrl)
                    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                    Toaster.show(str("artist_link_copied"))
                }
                ArtistMenuItem(Icons.Rounded.Info, str("artist_about")) {
                    menuOpen = false
                    onAbout()
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                ArtistMenuItem(Icons.Rounded.Block, str("artist_dislike"), danger = true) {
                    menuOpen = false
                    com.alananasss.kittytune.data.wave.WaveFeedback.dislikeArtist(user.username.orEmpty())
                    Toaster.show(str("artist_disliked"))
                }
            }
        }
    }
}

@Composable
private fun ArtistMenuItem(icon: ImageVector, text: String, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = { Text(text, color = color) },
        leadingIcon = { Icon(icon, null, tint = if (danger) color else MaterialTheme.colorScheme.onSurfaceVariant) },
        onClick = onClick,
    )
}

/** Where an artist's radio opens: the catalogue's for a streaming profile, SoundCloud's station otherwise. */
internal fun artistRadioDestination(user: User, profileViewModel: ProfileViewModel): String =
    if (profileViewModel.isSpotifyProfile) {
        "spotify_radio:" + com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(user.permalink ?: user.urn ?: "")
    } else {
        "station_artist:${user.id}"
    }

/**
 * A section's title that opens all of it, with an arrow right after the words, where the eye is: the "see all"
 * at the far right of the row was a long way to go (issue #66).
 */
@Composable
internal fun ArtistSectionTitle(title: String, onOpen: (() -> Unit)?, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val color by animateColorAsState(
        if (hovered && onOpen != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        label = "artistSectionTitle",
    )
    val shift by animateDpAsState(if (hovered) 4.dp else 0.dp, label = "artistSectionArrow")
    Row(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (onOpen != null) Modifier
                    .hoverable(interaction)
                    .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClick = onOpen)
                    .pointerHoverIcon(PointerIcon.Hand)
                else Modifier
            )
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        if (onOpen != null) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = str("btn_see_all"),
                tint = color,
                modifier = Modifier.padding(start = 2.dp).offset(x = shift).size(26.dp),
            )
        }
    }
}

/**
 * The artist's latest release, for the right of their popular songs: the song or record, its cover, and how long
 * it has been out.
 */
@Composable
internal fun NewReleaseCard(
    release: ArtistRelease,
    playerViewModel: PlayerViewModel,
    context: PlaybackContext?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val container by animateColorAsState(
        if (hovered) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
        label = "newReleaseContainer",
    )
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = container,
        modifier = modifier
            .pressScale(interaction, pressedScale = 0.98f)
            .hoverable(interaction)
            .clip(RoundedCornerShape(24.dp))
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple()) {
                when (release) {
                    is ArtistRelease.Song -> playerViewModel.playPlaylist(listOf(release.track), 0, context)
                    is ArtistRelease.Record -> onNavigate(
                        if (release.playlist.urn?.contains("spotify") == true) release.playlist.urn else release.playlist.id.toString()
                    )
                }
            },
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = release.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(132.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        str("artist_new_release").uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(release.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val ago = getRelativeTime(release.date)
                if (ago.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(str("artist_released_when", ago), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** What an artist put out last: a single song, or a record of several. */
internal sealed interface ArtistRelease {
    val title: String
    val artworkUrl: String?
    val date: String?

    data class Song(val track: Track) : ArtistRelease {
        override val title get() = track.title.orEmpty()
        override val artworkUrl get() = track.fullResArtwork
        override val date get() = track.releaseDate ?: track.createdAt
    }

    data class Record(val playlist: Playlist) : ArtistRelease {
        override val title get() = playlist.title.orEmpty()
        override val artworkUrl get() = playlist.fullResArtwork
        override val date get() = playlist.releaseDate ?: playlist.createdAt
    }

    companion object {
        /** The newest of the artist's songs and records, by release date where one is given, upload date if not. */
        fun latestOf(tracks: List<Track>, records: List<Playlist>): ArtistRelease? {
            val candidates = tracks.map { Song(it) } + records.map { Record(it) }
            return candidates.filter { !it.date.isNullOrBlank() }.maxByOrNull { it.date!!.take(10) }
                ?: candidates.firstOrNull()
        }
    }
}

/** One of the artist's mixes, as the row of mixes shows it. */
internal data class ArtistMix(
    val label: String,
    val title: String,
    val imageUrl: String?,
    val icon: ImageVector,
    val load: suspend () -> List<Track>,
)

/**
 * The mixes for an artist's page: the best of the artist (of each member, for a duo), one in their style, their
 * rare tracks and their radio.
 */
@Composable
internal fun rememberArtistMixes(user: User, profileViewModel: ProfileViewModel): List<ArtistMix> {
    val name = user.username.orEmpty()
    val members = remember(name) { ArtistMixes.membersOf(name) }
    val avatar = user.avatarUrl?.takeIf { !it.isDefaultAvatar() }
    return remember(user.id, members, profileViewModel.popularTracks.size, profileViewModel.allTracks.size) {
        buildList {
            if (members.size >= 2) {
                members.forEach { member ->
                    add(ArtistMix(str("artist_mix_label_best"), str("artist_mix_best", member), null, Icons.Rounded.LocalFireDepartment) {
                        ArtistMixes.bestOf(member)
                    })
                }
            } else {
                add(ArtistMix(str("artist_mix_label_best"), str("artist_mix_best", name), avatar, Icons.Rounded.LocalFireDepartment) {
                    (profileViewModel.popularTracks + profileViewModel.allTracks).distinctBy { it.id }.sortedByDescending { it.playbackCount }
                })
            }
            add(ArtistMix(str("artist_mix_label_style"), str("artist_mix_style", name), avatar, Icons.Rounded.AutoAwesome) {
                ArtistMixes.inTheStyleOf(user.id.takeIf { !profileViewModel.isSpotifyProfile }, name)
            })
            if (profileViewModel.allTracks.size > 8) {
                add(ArtistMix(str("artist_mix_label_rare"), str("artist_mix_rare"), avatar, Icons.Rounded.Diamond) {
                    ArtistMixes.rareTracks(profileViewModel.allTracks.toList())
                })
            }
        }
    }
}

/**
 * A mix's own cover, drawn rather than borrowed: an album's cover made the mixes look like more albums (issue
 * #66). Colours follow the mix's title, so each mix keeps its own; the artist's portrait sits in a ring when
 * there is one, the mix's kind is written across the top.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ArtistMixCard(mix: ArtistMix, onPlay: (List<Track>) -> Unit) {
    val (from, to) = remember(mix.title) { mixColours(mix.title) }
    val scheme = MaterialTheme.colorScheme
    val start = lerp(from, scheme.primary, 0.25f)
    val end = lerp(to, scheme.tertiary, 0.25f)
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }

    Column(Modifier.width(MIX_CARD_SIZE)) {
        Box(
            modifier = Modifier
                .size(MIX_CARD_SIZE)
                .pressScale(interaction)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(start, end)))
                .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple()) {
                    if (isLoading) return@clickable
                    scope.launch {
                        isLoading = true
                        try {
                            val tracks = mix.load()
                            if (tracks.isNotEmpty()) onPlay(tracks)
                        } finally {
                            isLoading = false
                        }
                    }
                },
        ) {
            // Soft rings behind the portrait.
            Box(Modifier.align(Alignment.BottomEnd).offset(x = 30.dp, y = 30.dp).size(150.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f)))
            Box(Modifier.align(Alignment.BottomEnd).offset(x = 14.dp, y = 14.dp).size(110.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)))
            if (mix.imageUrl != null) {
                AsyncImage(
                    model = mix.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp).size(76.dp).clip(CircleShape),
                )
            } else {
                Icon(mix.icon, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.align(Alignment.BottomEnd).padding(22.dp).size(56.dp))
            }
            Column(Modifier.padding(14.dp)) {
                Text(mix.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.85f), letterSpacing = 1.2.sp)
            }
            if (isLoading) {
                ContainedLoadingIndicator(modifier = Modifier.align(Alignment.Center).size(48.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(mix.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Two colours for a mix, chosen from a few pairs that sit well together, by its title. */
private fun mixColours(title: String): Pair<Color, Color> {
    val pairs = listOf(
        Color(0xFF7C3AED) to Color(0xFFDB2777),
        Color(0xFF0EA5E9) to Color(0xFF6366F1),
        Color(0xFFF97316) to Color(0xFFE11D48),
        Color(0xFF10B981) to Color(0xFF0E7490),
        Color(0xFFEAB308) to Color(0xFFEA580C),
        Color(0xFF8B5CF6) to Color(0xFF0EA5E9),
    )
    return pairs[Math.floorMod(title.hashCode(), pairs.size)]
}

private val HERO_HEIGHT = 380.dp
private val AVATAR_SIZE = 132.dp
private val MIX_CARD_SIZE = 168.dp
