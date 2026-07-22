@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alananasss.kittytune.ui.profile

import androidx.compose.material3.IconButtonDefaults

import androidx.compose.material3.ButtonDefaults

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.*
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.AppInstance
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.DownloadManager
import com.alananasss.kittytune.domain.Comment
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.common.ShimmerLine
import com.alananasss.kittytune.ui.common.viewableCover
import com.alananasss.kittytune.ui.common.TrackListItemShimmer
import com.alananasss.kittytune.ui.library.TrackListItem
import com.alananasss.kittytune.ui.player.PlaybackContext
import com.alananasss.kittytune.ui.player.PlayerViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

@Composable
fun ProfileScreen(
    userId: String,
    onBackClick: () -> Unit,
    playerViewModel: PlayerViewModel,
    onNavigate: (String) -> Unit = {},
) {
    val profileViewModel: ProfileViewModel = viewModel(key = "profile_$userId") { ProfileViewModel(AppInstance.application) }
    val downloadProgress by DownloadManager.downloadProgress.collectAsState()
    val listState = rememberLazyListState()
    var expandedSection by remember { mutableStateOf<String?>(null) }
    var showEditSheet by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(userId) {
        val id = userId.toLongOrNull()
        if (id != null) profileViewModel.loadProfile(id)
    }

    val user = profileViewModel.user

    val artistText = str("generic_artist")
    val artistPlaybackContext = remember(user, artistText) {
        user?.let {
            PlaybackContext(
                displayText = "$artistText • ${it.username}",
                navigationId = "profile:${it.id}",
                imageUrl = it.avatarUrl,
                artistName = it.username,
                isVerified = it.verified
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (profileViewModel.isLoading && user == null) {
            ProfileScreenShimmer(onBackClick)
        } else if (user != null) {
            // overlay for expanded sections
            AnimatedVisibility(
                visible = expandedSection != null,
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it },
                modifier = Modifier.zIndex(2f)
            ) {
                if (expandedSection == "comments") {
                    FullCommentListScreen(
                        comments = profileViewModel.userComments,
                        onBack = { expandedSection = null },
                        playerViewModel = playerViewModel,
                        profileViewModel = profileViewModel
                    )
                } else {
                    val (title, list) = when (expandedSection) {
                        "popular" -> str("profile_tab_popular") to profileViewModel.popularTracks.toList()
                        "tracks" -> str("profile_tab_tracks") to profileViewModel.allTracks.toList()
                        "reposts" -> str("profile_tab_reposts") to profileViewModel.repostedTracks.toList()
                        "likes" -> str("profile_tab_likes", user.username ?: "") to profileViewModel.likedTracks.toList()
                        else -> "" to emptyList<Track>()
                    }
                    val contextForList = if (expandedSection == "likes") null else artistPlaybackContext
                    FullListScreen(
                        title = title,
                        tracks = list,
                        onBack = { expandedSection = null },
                        playerViewModel = playerViewModel,
                        downloadProgress = downloadProgress,
                        context = contextForList
                    )
                }
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    ModernProfileHeader(
                        user = user,
                        isCurrentUser = profileViewModel.isCurrentUser,
                        onEditClick = { showEditSheet = true },
                        playerViewModel = playerViewModel,
                        onNavigate = onNavigate,
                        profileViewModel = profileViewModel,
                        artistContext = artistPlaybackContext
                    )
                }

                // bio section
                if (!user.description.isNullOrBlank()) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                            Text(
                                text = str("profile_about"),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(8.dp))
                            ExpandableDescription(
                                text = user.description,
                                onUrlClick = { url -> uriHandler.openUri(url) },
                                onMentionClick = { username ->
                                    playerViewModel.resolveAndNavigateToArtist(username.removePrefix("@"))
                                }
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }

                if (profileViewModel.popularTracks.isNotEmpty()) {
                    item { ProfileSectionTitle(title = str("profile_tab_popular"), showMore = profileViewModel.popularTracks.size > 5, onMoreClick = { expandedSection = "popular" }) }
                    itemsIndexed(profileViewModel.popularTracks.take(5)) { index, track ->
                        ProfileTrackItem(track, index, playerViewModel, downloadProgress, profileViewModel.popularTracks, artistPlaybackContext)
                    }
                }

                if (profileViewModel.allTracks.isNotEmpty()) {
                    item { ProfileSectionTitle(title = str("profile_latest_tracks"), showMore = true, onMoreClick = { expandedSection = "tracks" }) }
                    itemsIndexed(profileViewModel.allTracks.take(5)) { index, track ->
                        ProfileTrackItem(track, index, playerViewModel, downloadProgress, profileViewModel.allTracks, artistPlaybackContext)
                    }
                }

                if (profileViewModel.albums.isNotEmpty()) {
                    item { ProfileSectionTitle(str("profile_tab_albums")) }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(profileViewModel.albums) { playlist -> ProfileSquareCard(playlist) { onNavigate(playlist.id.toString()) } }
                        }
                    }
                }

                if (profileViewModel.playlists.isNotEmpty()) {
                    item {
                        val name = user.username ?: str("generic_artist")
                        ProfileSectionTitle(str("profile_playlists_by_user", name))
                    }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(profileViewModel.playlists) { playlist -> ProfileSquareCard(playlist) { onNavigate(playlist.id.toString()) } }
                        }
                    }
                }

                if (profileViewModel.likedTracks.isNotEmpty()) {
                    item {
                        val name = user.username ?: str("generic_artist")
                        ProfileSectionTitle(title = str("profile_likes_by_user", name), showMore = true, onMoreClick = { expandedSection = "likes" })
                    }
                    itemsIndexed(profileViewModel.likedTracks.take(3)) { index, track ->
                        ProfileTrackItem(track, index, playerViewModel, downloadProgress, profileViewModel.likedTracks, null)
                    }
                }

                if (profileViewModel.repostedTracks.isNotEmpty()) {
                    item {
                        ProfileSectionTitle(title = str("profile_tab_reposts"), showMore = true, onMoreClick = { expandedSection = "reposts" })
                    }
                    itemsIndexed(profileViewModel.repostedTracks.take(5)) { index, track ->
                        ProfileTrackItem(track, index, playerViewModel, downloadProgress, profileViewModel.repostedTracks, artistPlaybackContext)
                    }
                }

                if (profileViewModel.userComments.isNotEmpty()) {
                    item {
                        ProfileSectionTitle(
                            title = str("profile_tab_comments"),
                            showMore = profileViewModel.userComments.size > 3,
                            onMoreClick = { expandedSection = "comments" }
                        )
                    }
                    itemsIndexed(profileViewModel.userComments.take(3)) { _, comment ->
                        UserCommentItem(
                            comment = comment,
                            onTrackClick = {
                                comment.track?.let { track ->
                                    if (comment.trackTimestamp != null && comment.trackTimestamp > 0) {
                                        playerViewModel.playTrackAtPosition(track, comment.trackTimestamp)
                                    } else {
                                        playerViewModel.playPlaylist(listOf(track), 0)
                                    }
                                }
                            }
                        )
                    }
                }

                if (profileViewModel.similarArtists.isNotEmpty()) {
                    item { Spacer(Modifier.height(24.dp)) }
                    item { ProfileSectionTitle(str("profile_similar_artists")) }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(profileViewModel.similarArtists) { artist -> ArtistCircle(artist) { onNavigate("profile:${artist.id}") } }
                        }
                    }
                }
            }

            // dynamic app bar
            val showBarBackground by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 300 } }
            val barColor by animateColorAsState(if (showBarBackground) MaterialTheme.colorScheme.surface.copy(alpha = 0.98f) else Color.Transparent, label = "bar")
            val contentColor by animateColorAsState(if (showBarBackground) MaterialTheme.colorScheme.onSurface else Color.White, label = "content")

            val isArtistSaved by DownloadManager.isArtistSavedFlow(user.id).collectAsState(initial = null)

            CenterAlignedTopAppBar(
                title = {
                    AnimatedVisibility(visible = showBarBackground, enter = fadeIn(), exit = fadeOut()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(user.username ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            if (user.verified) {
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                },
                // Removed redundant navigationIcon
                actions = {
                    if (profileViewModel.isCurrentUser) {
                        AnimatedVisibility(visible = showBarBackground, enter = fadeIn(), exit = fadeOut()) {
                            IconButton(onClick = { showEditSheet = true }) {
                                Icon(Icons.Outlined.Edit, str("profile_edit"), tint = contentColor)
                            }
                        }
                    } else {
                        IconButton(onClick = { DownloadManager.toggleSaveArtist(user) },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = if (showBarBackground) Color.Transparent else Color.Black.copy(alpha = 0.3f), contentColor = if (isArtistSaved != null) Color(0xFFFF4081) else contentColor)
                        ) {
                            Icon(if (isArtistSaved != null) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, str("btn_follow"))
                        }
                    }

                    IconButton(onClick = {
                            val cleanUsername = user.username?.replace(" ", "")?.lowercase() ?: "user"
                            val shareUrl = user.permalinkUrl ?: "https://soundcloud.com/$cleanUsername"
                            // desktop share: copy to clipboard
                            val selection = java.awt.datatransfer.StringSelection(shareUrl)
                            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = if (showBarBackground) Color.Transparent else Color.Black.copy(alpha = 0.3f), contentColor = contentColor)
                    ) {
                        Icon(Icons.Outlined.Share, str("btn_share"))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = barColor, titleContentColor = MaterialTheme.colorScheme.onSurface, actionIconContentColor = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.align(Alignment.TopCenter).zIndex(1f)
            )

            if (showEditSheet) {
                EditProfileDialog(
                    user = user,
                    onDismiss = { showEditSheet = false },
                    profileViewModel = profileViewModel,
                    onSave = { name, bio, city ->
                        profileViewModel.updateProfile(name, bio, city, "")
                        showEditSheet = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreenShimmer(onBackClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(userScrollEnabled = false) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
                    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.5f), MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background), startY = 0f)))
                    Column(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp).padding(bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ArtistAvatar(avatarUrl = null, modifier = Modifier.size(140.dp).clip(CircleShape))
                        Spacer(Modifier.height(20.dp))
                        ShimmerLine(Modifier.width(200.dp).height(30.dp))
                        Spacer(Modifier.height(12.dp))
                        ShimmerLine(Modifier.width(150.dp))
                    }
                }
            }
            item { ProfileSectionTitle(title = "...") }
            items(5) { TrackListItemShimmer() }
        }
        CenterAlignedTopAppBar(
            title = {},
            // Removed redundant navigationIcon
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
            modifier = Modifier.align(Alignment.TopCenter).zIndex(1f)
        )
    }
}

@Composable
fun ArtistAvatar(modifier: Modifier = Modifier, avatarUrl: String?) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrEmpty()) {
            AsyncImage(
                model = avatarUrl.replace("large", "t500x500"),
                contentDescription = str("profile_avatar"),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Default.Person,
                contentDescription = str("profile_avatar"),
                modifier = Modifier.fillMaxSize(0.6f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ModernProfileHeader(
    user: User,
    isCurrentUser: Boolean,
    onEditClick: () -> Unit,
    playerViewModel: PlayerViewModel,
    onNavigate: (String) -> Unit,
    profileViewModel: ProfileViewModel,
    artistContext: PlaybackContext?
) {
    Box(modifier = Modifier.fillMaxWidth().height(420.dp)) {
        val bgModel = user.bannerUrl ?: user.avatarUrl
        AsyncImage(model = bgModel, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(60.dp).alpha(0.6f))
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.5f), MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background), startY = 0f)))

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp).padding(bottom = 24.dp).widthIn(max = 620.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box {
                Surface(shape = CircleShape, shadowElevation = 12.dp, color = Color.Transparent, modifier = Modifier.size(140.dp)) {
                    ArtistAvatar(avatarUrl = user.avatarUrl, modifier = Modifier.fillMaxSize().viewableCover(user.avatarUrl?.replace("large", "t500x500")))
                }
                if (isCurrentUser) {
                    Surface(
                        onClick = onEditClick,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = (-4).dp)
                    ) {
                        Icon(Icons.Outlined.Edit, str("profile_edit"), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(8.dp).size(20.dp))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text(text = user.username ?: str("unknown_artist"), style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground)
                if (user.verified) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
            }

            if (!user.city.isNullOrBlank() || !user.countryCode.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocationOn, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(text = listOfNotNull(user.city, user.countryCode).joinToString(", "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text(
                    text = "${NumberFormat.getNumberInstance(Locale.US).format(user.followersCount)} ${str("profile_followers")}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)
                )
                Text(text = " • ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                Text(
                    text = "${NumberFormat.getNumberInstance(Locale.US).format(user.followingsCount)} ${str("profile_followings")}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)
                )
                Text(text = " • ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                Text(text = "${NumberFormat.getNumberInstance(Locale.US).format(user.trackCount)} ${str("profile_tracks")}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(32.dp))

            if (isCurrentUser) {
                Button(shapes = ButtonDefaults.shapes(), onClick = onEditClick,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Text(str("profile_edit"), fontWeight = FontWeight.SemiBold)
                }
            } else {
                if (user.trackCount > 0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(shapes = ButtonDefaults.shapes(), onClick = {
                                playerViewModel.playPlaylist(
                                    tracks = profileViewModel.allTracks.toList().shuffled(),
                                    startIndex = 0,
                                    context = artistContext
                                )
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                        ) {
                            Icon(Icons.Default.Shuffle, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                            Text(str("btn_shuffle"), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = { onNavigate("station_artist:${user.id}") },
                            modifier = Modifier.weight(1f).height(56.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                        ) {
                            Icon(Icons.Default.Radio, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                            Text(str("radio"), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditProfileDialog(
    user: User,
    onDismiss: () -> Unit,
    profileViewModel: ProfileViewModel,
    onSave: (name: String, bio: String, city: String) -> Unit
) {
    var name by remember { mutableStateOf(user.username ?: "") }
    var bio by remember { mutableStateOf(user.description ?: "") }
    var city by remember { mutableStateOf(user.city ?: "") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDeleteBannerConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(str("dialog_delete_avatar_title")) },
            text = { Text(str("dialog_delete_avatar_msg")) },
            confirmButton = {
                TextButton(onClick = { profileViewModel.deleteAvatar(); showDeleteConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(str("btn_delete")) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(str("btn_cancel")) } }
        )
    }

    if (showDeleteBannerConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteBannerConfirm = false },
            title = { Text(str("dialog_delete_header_title")) },
            text = { Text(str("dialog_delete_header_msg")) },
            confirmButton = {
                TextButton(onClick = { profileViewModel.deleteBanner(); showDeleteBannerConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(str("btn_delete")) }
            },
            dismissButton = { TextButton(onClick = { showDeleteBannerConfirm = false }) { Text(str("btn_cancel")) } }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.width(520.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(str("profile_edit_title"), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, str("btn_close")) }
                }

                Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                    // banner area — click to pick a new banner image
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { profileViewModel.pickAndUploadBanner() }
                    ) {
                        val bannerModel = user.bannerUrl
                        AsyncImage(
                            model = bannerModel,
                            contentDescription = str("profile_banner"),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().alpha(0.7f)
                        )
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Edit, str("profile_edit"), tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(32.dp))
                        }
                        if (bannerModel != null) {
                            Surface(
                                onClick = { showDeleteBannerConfirm = true },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Outlined.Delete,
                                        contentDescription = str("btn_delete"),
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // avatar container — click to pick a new avatar
                    Box(modifier = Modifier.align(Alignment.BottomCenter).size(130.dp)) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .align(Alignment.Center)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .clickable { profileViewModel.pickAndUploadAvatar() }
                        ) {
                            ArtistAvatar(avatarUrl = user.avatarUrl, modifier = Modifier.fillMaxSize())
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Edit, str("profile_edit"), tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                        }

                        val hasCustomAvatar = user.avatarUrl != null && !user.avatarUrl.contains("default_avatar")
                        if (hasCustomAvatar) {
                            Surface(
                                onClick = { showDeleteConfirm = true },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.errorContainer,
                                border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
                                modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp).size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Outlined.Delete,
                                        contentDescription = str("btn_delete"),
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(str("profile_name")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text(str("profile_city")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it },
                        label = { Text(str("profile_bio")) },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { onSave(name, bio, city) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(str("btn_save_changes"))
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun FullListScreen(
    title: String,
    tracks: List<Track>,
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel,
    downloadProgress: Map<Long, Int>,
    context: PlaybackContext?
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = 32.dp),
            modifier = Modifier.padding(innerPadding).fillMaxSize()
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(shapes = ButtonDefaults.shapes(), onClick = { playerViewModel.playPlaylist(tracks, context = context) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(str("btn_play"))
                    }
                    FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = { playerViewModel.playPlaylist(tracks.shuffled(), context = context) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Shuffle, null); Spacer(Modifier.width(8.dp)); Text(str("btn_shuffle"))
                    }
                }
            }
            itemsIndexed(tracks) { index, track ->
                ProfileTrackItem(track, index, playerViewModel, downloadProgress, tracks, context)
            }
        }
    }
}

@Composable
fun ProfileTrackItem(
    track: Track,
    index: Int,
    playerViewModel: PlayerViewModel,
    downloadProgress: Map<Long, Int>,
    contextList: List<Track>,
    context: PlaybackContext?
) {
    val progress = downloadProgress[track.id]
    val isDownloading = progress != null
    val isDownloaded by produceState(false, track.id, downloadProgress) {
        value = DownloadManager.getLocalTrack(track.id)?.localAudioPath?.isNotEmpty() == true
    }

    TrackListItem(
        track = track,
        currentlyPlayingTrack = playerViewModel.currentTrack,
        index = index,
        isDownloading = isDownloading,
        isDownloaded = isDownloaded,
        downloadProgress = progress ?: 0,
        showVerifiedBadge = false,
        onClick = {
            playerViewModel.playPlaylist(contextList, startIndex = index, context = context)
        },
        onOptionClick = { playerViewModel.showTrackOptions(track) }
    )
}

@Composable
fun ProfileSectionTitle(title: String, showMore: Boolean = false, onMoreClick: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        if (showMore) {
            TextButton(shape = RoundedCornerShape(12.dp), onClick = onMoreClick) {
                Text(str("btn_see_all"), fontWeight = FontWeight.Bold)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun ProfileSquareCard(playlist: Playlist, onClick: () -> Unit) {
    Column(modifier = Modifier.width(140.dp).clickable { onClick() }) {
        AsyncImage(
            model = playlist.fullResArtwork,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(140.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = playlist.title ?: "",
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold
        )
        val tracksText = str("playlist_num_tracks", playlist.trackCount ?: 0)
        val likesText = if (playlist.likesCount != null && playlist.likesCount > 0) " • ${playlist.likesCount} likes" else ""
        Text(
            text = "$tracksText$likesText",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ArtistCircle(user: User, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(120.dp).clickable { onClick() }) {
        ArtistAvatar(avatarUrl = user.avatarUrl, modifier = Modifier.size(120.dp).clip(CircleShape))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = user.username ?: str("generic_artist"), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            if (user.verified) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
fun ExpandableDescription(
    text: String,
    onUrlClick: (String) -> Unit,
    onMentionClick: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    val urlPattern = remember { Pattern.compile("((https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|])") }
    val mentionPattern = remember { Pattern.compile("@[\\w-]+") }
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val annotatedString = remember(text, isExpanded, primaryColor, tertiaryColor) {
        buildAnnotatedString {
            append(text)

            val urlMatcher = urlPattern.matcher(text)
            while (urlMatcher.find()) {
                val url = urlMatcher.group()
                addLink(
                    LinkAnnotation.Clickable(
                        tag = "URL",
                        styles = TextLinkStyles(style = SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold)),
                        linkInteractionListener = { onUrlClick(url) }
                    ),
                    urlMatcher.start(), urlMatcher.end()
                )
            }

            val mentionMatcher = mentionPattern.matcher(text)
            while (mentionMatcher.find()) {
                val mention = mentionMatcher.group()
                addLink(
                    LinkAnnotation.Clickable(
                        tag = "MENTION",
                        styles = TextLinkStyles(style = SpanStyle(color = tertiaryColor, fontWeight = FontWeight.SemiBold)),
                        linkInteractionListener = { onMentionClick(mention) }
                    ),
                    mentionMatcher.start(), mentionMatcher.end()
                )
            }
        }
    }

    SelectionContainer {
        Column(modifier = Modifier.animateContentSize()) {
            Text(
                text = annotatedString,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                ),
                maxLines = if (isExpanded) Int.MAX_VALUE else 5,
                overflow = TextOverflow.Ellipsis
            )

            if (text.length > 200) {
                Text(
                    text = if (isExpanded) str("detail_show_less") else str("detail_show_more"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp).clickable { isExpanded = !isExpanded }
                )
            }
        }
    }
}

@Composable
fun UserCommentItem(
    comment: Comment,
    onTrackClick: () -> Unit
) {
    val track = comment.track ?: return

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Surface(
            onClick = onTrackClick,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = track.fullResArtwork,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = str("profile_comment_on_track", track.title ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (comment.trackTimestamp != null) {
                        Text(
                            text = " • " + com.alananasss.kittytune.utils.makeTimeString(comment.trackTimestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = comment.body,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = getRelativeTime(comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.End).padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun FullCommentListScreen(
    comments: List<Comment>,
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel,
    profileViewModel: ProfileViewModel
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(str("profile_tab_comments"), fontWeight = FontWeight.Bold) },
                // Removed redundant navigationIcon
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = 32.dp),
            modifier = Modifier.padding(innerPadding).fillMaxSize()
        ) {
            itemsIndexed(comments) { index, comment ->
                if (index >= comments.size - 5 && !profileViewModel.isCommentsLoadingMore) {
                    LaunchedEffect(Unit) {
                        profileViewModel.loadMoreUserComments()
                    }
                }
                UserCommentItem(
                    comment = comment,
                    onTrackClick = {
                        comment.track?.let { track ->
                            if (comment.trackTimestamp != null && comment.trackTimestamp > 0) {
                                playerViewModel.playTrackAtPosition(track, comment.trackTimestamp)
                            } else {
                                playerViewModel.playPlaylist(listOf(track), 0)
                            }
                        }
                    }
                )
            }

            if (profileViewModel.isCommentsLoadingMore) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularWavyProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

fun getRelativeTime(dateStr: String?): String {
    if (dateStr.isNullOrBlank()) return ""
    try {
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US),
            SimpleDateFormat("yyyy/MM/dd HH:mm:ss Z", Locale.US),
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        )
        val date = formats.firstNotNullOfOrNull { fmt ->
            try { fmt.parse(dateStr) } catch (_: Exception) { null }
        } ?: return ""

        val diff = System.currentTimeMillis() - date.time

        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        val weeks = days / 7
        val months = days / 30
        val years = days / 365

        return when {
            seconds < 60 -> str("time_now")
            minutes < 60 -> str("time_minutes_ago", minutes)
            hours < 24 -> str("time_hours_ago", hours)
            days < 7 -> str("time_days_ago", days)
            weeks < 5 -> str("time_weeks_ago", weeks)
            months < 12 -> str("time_months_ago", months)
            years == 1L -> str("time_one_year_ago")
            else -> str("time_years_ago", years)
        }
    } catch (_: Exception) {
        return ""
    }
}
