package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.LocalContentColor
import com.alananasss.kittytune.ui.common.PlayingBars
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxHeight
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
/**
 * The stand-in banner for an artist with none: the best songs' covers side by side in one row, blurred together.
 *
 * The blur is applied to the whole row, not to each cover, so the joins between covers dissolve into each other. The
 * earlier version laid separately blurred patches over a base cover, and where a patch ended it left a hard stripe across
 * the banner.
 */
@Composable
private fun ArtistCoverCollage(covers: List<String>, modifier: Modifier = Modifier) {
    Box(modifier.clipToBounds()) {
        Row(Modifier.fillMaxSize().blur(64.dp).graphicsLayer { scaleX = 1.35f; scaleY = 1.35f }) {
            covers.take(4).forEach { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
    }
}

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
    val streamingBanner = com.alananasss.kittytune.ui.common.rememberArtistBanner(user.username)
    val banner = user.bannerUrl ?: streamingBanner ?: profileViewModel.spotifyArtist?.headerImageUrl
    val portrait = user.avatarUrl.takeIf { !it.isDefaultAvatar() }?.getHighResAvatarUrl() ?: profile?.avatarUrl
    val heroCovers = remember(profileViewModel.popularTracks.size) {
        profileViewModel.popularTracks.mapNotNull { it.fullResArtwork.takeIf { url -> url.isNotBlank() } }.distinct().take(4)
    }
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
            // No banner: their best songs' covers, blurred into one wash, rather than the portrait smeared.
            banner == null && heroCovers.size >= 3 -> ArtistCoverCollage(heroCovers, Modifier.fillMaxSize())
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
    // Read from the library's own state, so the pin shows what is true once the library has loaded and follows
    // a change made anywhere, instead of being a copy taken at the first frame.
    val itemMetas by libraryViewModel.allItemMetas.collectAsState()
    val isPinned = itemMetas[pinKey]?.isPinned == true
    var menuOpen by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val topTracks = profileViewModel.popularTracks.ifEmpty { profileViewModel.allTracks }
    val context = artistContext ?: PlaybackContext(user.username.orEmpty(), "profile:${user.id}")

    // What is playing says what these buttons are: listening to this artist, or their trailer. They show it, and a
    // press pauses and resumes it instead of starting it over.
    val playingContext = playerViewModel.currentContext?.navigationId
    val isListening = playingContext == context.navigationId
    val isTrailer = playingContext == PlayerViewModel.TRAILER_NAV_PREFIX + context.navigationId
    val isPlaying = playerViewModel.isPlaying

    if (showShare) ArtistShareDialog(rememberArtistShareCard(user, profileViewModel), onDismiss = { showShare = false })
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = {
                if (isListening) playerViewModel.togglePlayPause()
                else if (topTracks.isNotEmpty()) playerViewModel.playPlaylist(topTracks.toList(), 0, context, respectShuffle = false)
            },
            enabled = topTracks.isNotEmpty() || isListening,
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.ContentPadding,
            modifier = Modifier.height(52.dp),
        ) {
            if (isListening) {
                PlayingBars(isPlaying = isPlaying, color = LocalContentColor.current, modifier = Modifier.size(22.dp))
            } else {
                Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                str(if (!isListening) "artist_listen" else if (isPlaying) "artist_listening" else "artist_resume"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Tip(str("artist_trailer_tip")) {
            FilledTonalButton(
                onClick = {
                    if (isTrailer) playerViewModel.togglePlayPause()
                    else playerViewModel.playTrailer(topTracks.toList(), context)
                },
                enabled = topTracks.isNotEmpty() || isTrailer,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.height(52.dp),
            ) {
                if (isTrailer) {
                    PlayingBars(isPlaying = isPlaying, color = LocalContentColor.current, modifier = Modifier.size(20.dp))
                } else {
                    Icon(Icons.Rounded.SlowMotionVideo, null, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    str(if (!isTrailer) "artist_trailer" else if (isPlaying) "artist_trailer_playing" else "artist_resume"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
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
                    showShare = true
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

/** One of the buttons at the foot of an artist's page: what it is called, how many there are, and where it goes. */
internal data class ArtistMoreEntry(val icon: ImageVector, val label: String, val count: Int, val section: String)

/** The small things beside an artist's music, as buttons that open them (round 2 of the tester's list, item 8.2). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun ArtistMoreRow(entries: List<ArtistMoreEntry>, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        ArtistSectionTitle(str("artist_more_title"), onOpen = null)
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            entries.forEach { entry ->
                androidx.compose.material3.OutlinedButton(
                    onClick = { onOpen(entry.section) },
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Icon(entry.icon, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(entry.label, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        NumberFormat.getIntegerInstance(com.alananasss.kittytune.core.Strings.locale()).format(entry.count),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
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
 * A release for the right of the artist's popular songs: its cover, whether it is a song or a record, who made it,
 * and when. The newest one is shown in full; the one before it is [previous], held back in quieter colours so the
 * two never read as equals.
 */
@Composable
internal fun NewReleaseCard(
    release: ArtistRelease,
    playerViewModel: PlayerViewModel,
    context: PlaybackContext?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    previous: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        when {
            previous -> if (hovered) scheme.surfaceContainer else scheme.surfaceContainerLow
            hovered -> scheme.surfaceContainerHigh
            else -> scheme.surfaceContainer
        },
        label = "newReleaseContainer",
    )
    val cover = if (previous) 64.dp else 132.dp
    val shape = RoundedCornerShape(if (previous) 16.dp else 24.dp)
    Surface(
        shape = shape,
        color = container,
        modifier = modifier
            .pressScale(interaction, pressedScale = 0.98f)
            .hoverable(interaction)
            .clip(shape)
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple()) {
                when (release) {
                    is ArtistRelease.Song -> playerViewModel.playPlaylist(listOf(release.track), 0, context)
                    is ArtistRelease.Record -> onNavigate(
                        if (release.playlist.urn?.contains("spotify") == true) release.playlist.urn else release.playlist.id.toString()
                    )
                }
            },
    ) {
        Row(Modifier.padding(if (previous) 10.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = release.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(cover).clip(RoundedCornerShape(if (previous) 10.dp else 16.dp)).background(scheme.surfaceVariant),
            )
            Spacer(Modifier.width(if (previous) 10.dp else 16.dp))
            Column(Modifier.weight(1f)) {
                Surface(shape = CircleShape, color = if (previous) scheme.surfaceVariant else scheme.primaryContainer) {
                    Text(
                        release.kindLabel().uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (previous) scheme.onSurfaceVariant else scheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(if (previous) 4.dp else 10.dp))
                Text(
                    release.title,
                    style = if (previous) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (previous) scheme.onSurface.copy(alpha = 0.85f) else scheme.onSurface,
                    maxLines = if (previous) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                release.byline().takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // A date, not "3 years ago": for an artist who has been quiet that says nothing about when.
                val released = ReleaseDate.text(release.date)
                val facts = listOfNotNull(released.takeIf { it.isNotBlank() }?.let { str("artist_released_when", it) }, release.sizeLabel())
                if (facts.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        facts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant.copy(alpha = if (previous) 0.7f else 1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** What an artist put out: a single song, or a record of several. */
internal sealed interface ArtistRelease {
    val title: String
    val artworkUrl: String?
    val date: String?

    /** "Track", "Album", "Single", "EP" or "Compilation". */
    @Composable fun kindLabel(): String

    /** Who it is by. */
    fun byline(): String

    /** How long a song is, or how many songs a record holds. */
    @Composable fun sizeLabel(): String?

    data class Song(val track: Track) : ArtistRelease {
        override val title get() = track.title.orEmpty()
        override val artworkUrl get() = track.fullResArtwork
        override val date get() = track.releaseDate ?: track.createdAt
        @Composable override fun kindLabel() = str("release_kind_track")
        override fun byline() = track.displayArtist.ifBlank { track.user?.username.orEmpty() }
        @Composable override fun sizeLabel() = track.durationMs?.takeIf { it > 0 }?.let {
            val seconds = it / 1000
            String.format("%d:%02d", seconds / 60, seconds % 60)
        }
    }

    data class Record(val playlist: Playlist) : ArtistRelease {
        override val title get() = playlist.title.orEmpty()
        override val artworkUrl get() = playlist.fullResArtwork
        override val date get() = playlist.releaseDate ?: playlist.createdAt
        @Composable override fun kindLabel() = when ((playlist.setType ?: playlist.playlistType)?.lowercase()) {
            "single" -> str("release_kind_single")
            "ep" -> str("release_kind_ep")
            "compilation" -> str("release_kind_compilation")
            else -> str("release_kind_album")
        }
        override fun byline() = playlist.user?.username.orEmpty()
        @Composable override fun sizeLabel() = playlist.trackCount?.takeIf { it > 0 }?.let { str("playlist_num_tracks", it) }
    }

    companion object {
        /**
         * The newest of the artist's songs and records, newest first, by release date where one is given and upload
         * date if not. A song that shares its name with a record is the record's own, so it is listed once.
         */
        fun latestOf(tracks: List<Track>, records: List<Playlist>, count: Int = 2): List<ArtistRelease> {
            val recordTitles = records.mapNotNull { it.title?.trim()?.lowercase() }.toSet()
            val candidates = records.map { Record(it) } +
                tracks.filter { it.title?.trim()?.lowercase() !in recordTitles }.map { Song(it) }
            val dated = candidates.filter { !it.date.isNullOrBlank() }.sortedByDescending { it.date!!.take(10) }
            return (dated + candidates.filter { it.date.isNullOrBlank() }).take(count)
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
 * A mix's own cover, drawn rather than borrowed: an album's cover made the mixes look like more albums (issue #66).
 *
 * The artist's portrait fills the card under a dark wash in one of the app's own accent colours, so a row of mixes
 * belongs to the page and to the chosen theme instead of being a row of unrelated gradients. What kind of mix it
 * is stands large at the bottom; a play button comes up under the pointer. Without a portrait (a member of a duo
 * has none of their own) the wash is the whole cover, with the mix's icon large and faint in a corner.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ArtistMixCard(mix: ArtistMix, accentIndex: Int = 0, onPlay: (List<Track>) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // One of three accents, by place in the row, so neighbours differ; held down to a muted tone below.
    val accent = remember(accentIndex, scheme.primary, scheme.tertiary, scheme.secondary) {
        listOf(scheme.primary, scheme.tertiary, scheme.secondary)[Math.floorMod(accentIndex, 3)]
    }
    val deep = remember(accent) { lerp(Color(0xFF101014), accent, 0.38f) }
    val mid = remember(accent) { lerp(Color(0xFF101014), accent, 0.62f) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    Column(Modifier.width(MIX_CARD_SIZE)) {
        Box(
            modifier = Modifier
                .size(MIX_CARD_SIZE)
                .pressScale(interaction)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(mid, deep)))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                .hoverable(interaction)
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
            if (mix.imageUrl != null) {
                AsyncImage(
                    model = mix.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // A soft light from the corner the icon sits in, so the plain wash is not flat.
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                            center = Offset(MIX_CARD_GLOW_X, 0f),
                            radius = MIX_CARD_GLOW_RADIUS,
                        )
                    )
                )
                Icon(
                    mix.icon, null,
                    tint = Color.White.copy(alpha = 0.16f),
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 18.dp, y = (-14).dp).size(112.dp),
                )
            }
            // The wash: the accent over the portrait, then dark toward the text.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to accent.copy(alpha = if (mix.imageUrl != null) 0.34f else 0f),
                        0.45f to deep.copy(alpha = if (mix.imageUrl != null) 0.38f else 0f),
                        1f to Color(0xFF08080B).copy(alpha = 0.88f),
                    )
                )
            )
            Box(
                Modifier
                    .padding(12.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(mix.icon, null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
            Text(
                mix.label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 14.dp, end = 58.dp),
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = hovered || isLoading,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.7f),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.7f),
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoading) {
                        ContainedLoadingIndicator(modifier = Modifier.size(34.dp))
                    } else {
                        Icon(Icons.Rounded.PlayArrow, null, tint = Color(0xFF101014), modifier = Modifier.size(26.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(mix.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private val HERO_HEIGHT = 380.dp
private val AVATAR_SIZE = 132.dp
private val MIX_CARD_SIZE = 168.dp
private const val MIX_CARD_GLOW_X = 460f
private const val MIX_CARD_GLOW_RADIUS = 520f
