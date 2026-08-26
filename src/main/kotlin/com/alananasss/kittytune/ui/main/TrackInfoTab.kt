@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alananasss.kittytune.ui.main

import androidx.compose.foundation.layout.*

import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.focus.focusRequester
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.graphics.Color
import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.ui.player.lyrics.LyricsUtils
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.vector.ImageVector
import coil3.compose.AsyncImage
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.Comment
import com.alananasss.kittytune.core.trackTextInput
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.player.CommentSort
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.ui.common.viewableCover
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import java.awt.Desktop
import java.net.URI
import com.alananasss.kittytune.ui.profile.ExpandableDescription
import com.alananasss.kittytune.ui.profile.getRelativeTime
import com.alananasss.kittytune.ui.modifiers.squish
import com.alananasss.kittytune.utils.makeTimeString
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles

@Composable
fun TrackInfoTab(vm: PlayerViewModel) {
    val currentTrack = vm.currentTrack ?: return
    val trackId = currentTrack.id

    val isSpotifyTrack = remember(trackId) { vm.isSpotifyTrack(currentTrack) }
    var fullTrack by remember(trackId) { mutableStateOf<Track?>(null) }
    var isLoading by remember(trackId) { mutableStateOf(!isSpotifyTrack) }
    var spotifyCredits by remember(trackId) {
        mutableStateOf<com.alananasss.kittytune.data.spotify.SpotifyCredits?>(null)
    }

    val scope = rememberCoroutineScope()

    LaunchedEffect(trackId) {
        if (trackId == 0L) return@LaunchedEffect
        if (isSpotifyTrack) {
            // Catalog tracks have no SoundCloud entity: fetch credits instead.
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val spotifyId = vm.getSpotifyTrackId(currentTrack)
                spotifyCredits = spotifyId?.let {
                    com.alananasss.kittytune.data.spotify.SpotifyRepository.getCredits(it)
                }
            }
            return@LaunchedEffect
        }
        isLoading = true
        scope.launch {
            try {
                val api = RetrofitClient.create()
                val tracks = api.getTracksByIds(trackId.toString())
                if (tracks.isNotEmpty()) {
                    fullTrack = tracks.first()
                }
            } catch (e: Exception) { e.printStackTrace() }
            finally {
                isLoading = false
            }
        }
        vm.loadComments(refresh = true, specificTrack = currentTrack)
        vm.loadSocialProof(currentTrack)
    }

    val displayTrack = fullTrack ?: currentTrack

    val organizedComments = remember(vm.commentsList.toList()) {
        val list = mutableListOf<Comment>()
        for (comment in vm.commentsList) {
            if (comment.body.trim().startsWith("@") && list.isNotEmpty()) {
                val parentIndex = list.indexOfLast { it.trackTimestamp == comment.trackTimestamp }
                if (parentIndex != -1) {
                    val parent = list[parentIndex]
                    list[parentIndex] = parent.copy(replies = (parent.replies ?: emptyList()) + comment)
                    continue
                }
            }
            list.add(comment)
        }
        list
    }

    /**
     * Which half the panel shows below the track details (issue #33). Deliberately not keyed on the
     * track: someone who opened the panel for the lyrics wants the lyrics on the next track too.
     * Saveable, so it also survives closing and reopening the panel.
     *
     * Spotify catalog tracks have no comments at all, so there is nothing to toggle between: the
     * lyrics are the only half they have.
     */
    var showLyricsHalf by rememberSaveable { mutableStateOf(false) }
    val lyricsHalf = isSpotifyTrack || showLyricsHalf

    LazyColumn(
        // The horizontal inset belongs to the content, not to the container: applied to the
        // container it also pushed the scrollbar 16.dp inwards, which parked it against the text
        // instead of at the panel edge (issue #33).
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AsyncImage(
                    model = displayTrack.fullResArtwork,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .viewableCover(displayTrack.fullResArtwork)
                )
                Text(
                    text = displayTrack.title ?: "",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // navigateToTrackArtist already routes both sources, and opens the
                    // picker when the track credits several artists.
                    com.alananasss.kittytune.ui.common.ArtistLinkText(
                        track = displayTrack,
                        onArtistClick = { vm.navigateToTrackArtist(it) },
                        text = displayTrack.user?.username ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        hoverColor = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (displayTrack.user?.verified == true) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Rounded.Verified, null,
                            tint = if (isSpotifyTrack) SpotifyGreen else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Spotify catalog chip
                if (isSpotifyTrack) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = SpotifyGreen.copy(alpha = 0.15f),
                        modifier = Modifier.widthIn(max = 160.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = str("music_provider_spotify"),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = SpotifyGreen
                            )
                        }
                    }
                }

                // Stats Row
                if (isSpotifyTrack) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        (displayTrack.playCount ?: 0L).takeIf { it > 0 }?.let { streams ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    NumberFormat.getCompactNumberInstance(Locale.US, NumberFormat.Style.SHORT).format(streams) + " " + str("spotify_streams_formatted"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (displayTrack.publisherMetadata?.explicit == true) {
                            Text(
                                text = "E",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatItem(Icons.Rounded.PlayArrow, displayTrack.playbackCount, onClick = null)
                        StatItem(Icons.Rounded.Favorite, displayTrack.likesCount, onClick = { vm.navigateToTrackDetails(displayTrack.id, 0) })
                        StatItem(Icons.Rounded.Repeat, displayTrack.repostsCount, onClick = { vm.navigateToTrackDetails(displayTrack.id, 1) })
                        StatItem(Icons.Rounded.Comment, displayTrack.commentCount, onClick = null)
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(

                            onClick = { vm.navigateToTrackDetails(displayTrack.id, 0) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Info,
                                contentDescription = "Track Details",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // ---- Spotify catalog: credits sheet, same structure as Android ----
        if (isSpotifyTrack) {
            // Streams counter card
            val streamCount = displayTrack.playCount
                ?: displayTrack.playbackCount.takeIf { it > 0 }?.toLong()
            if (streamCount != null && streamCount > 0) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                Icons.Rounded.PlayArrow, null,
                                tint = SpotifyGreen,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = NumberFormat.getNumberInstance(Locale.getDefault()).format(streamCount),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = str("spotify_streams_formatted"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Performers (credits role or fallback on the track's artist list)
            val performersRole = spotifyCredits?.roles?.firstOrNull { role ->
                role.roleTitle.equals("Performers", true) || role.roleTitle.equals("Artists", true) ||
                    role.roleTitle.equals("Artist", true) || role.roleTitle.contains("Performer", true) ||
                    role.roleTitle.contains("Artist", true)
            }
            val trackArtistRefs = displayTrack.artists.orEmpty()
            val performerArtists = performersRole?.artists?.takeIf { it.isNotEmpty() }
                ?: trackArtistRefs.map { ref ->
                    com.alananasss.kittytune.data.spotify.SpotifyCreditArtist(
                        id = ref.id,
                        name = ref.name,
                        uri = ref.uri,
                        imageUri = ref.avatarUrl,
                        subroles = if (trackArtistRefs.size > 1 && ref !== trackArtistRefs.first()) {
                            listOf("Featured Artist")
                        } else listOf("Main Artist")
                    )
                }

            if (performerArtists.isNotEmpty()) {
                item {
                    Text(
                        text = str("spotify_credits_performers"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SpotifyGreen
                    )
                    Spacer(Modifier.height(8.dp))
                }
                itemsIndexed(performerArtists, key = { _, a -> "perf_${a.id}_${a.name}" }) { _, artist ->
                    CreditArtistRow(artist, vm)
                }
                item { Spacer(Modifier.height(12.dp)) }
            }

            // Writers / composition
            val writersRole = spotifyCredits?.roles?.firstOrNull { role ->
                role.roleTitle.equals("Writers", true) || role.roleTitle.contains("Writer", true) ||
                    role.roleTitle.contains("Lyric", true) || role.roleTitle.contains("Composition", true) ||
                    role.roleTitle.contains("Composer", true)
            }
            val writerArtists = writersRole?.artists.orEmpty()
            if (writerArtists.isNotEmpty()) {
                item {
                    Text(
                        text = str("spotify_credits_writers"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SpotifyGreen
                    )
                    Spacer(Modifier.height(8.dp))
                }
                items(writerArtists, key = { "writer_${it.id}_${it.name}" }) { writer ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(text = writer.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        val subrolesText = writer.subroles.joinToString(", ") { creditSubroleLabel(it) }
                        if (subrolesText.isNotBlank()) {
                            Text(
                                text = subrolesText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            } else if (!displayTrack.publisherMetadata?.composer.isNullOrBlank()) {
                item {
                    Text(
                        text = str("spotify_credits_composer"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SpotifyGreen
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = displayTrack.publisherMetadata!!.composer!!,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            // Producers
            val producerArtists = spotifyCredits?.roles
                ?.filter { it.roleTitle.contains("Producer", true) || it.roleTitle.contains("Production", true) }
                ?.flatMap { it.artists }
                .orEmpty()
            if (producerArtists.isNotEmpty()) {
                item {
                    Text(
                        text = str("spotify_credits_producers"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SpotifyGreen
                    )
                    Spacer(Modifier.height(8.dp))
                }
                items(producerArtists.distinctBy { it.name }, key = { "prod_${it.id}_${it.name}" }) { producer ->
                    Text(
                        text = producer.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
            }

            // Sources
            val sources = spotifyCredits?.sourceNames?.takeIf { it.isNotEmpty() }
                ?: listOfNotNull(displayTrack.publisherMetadata?.publisher?.takeIf { it.isNotBlank() })
            if (sources.isNotEmpty()) {
                item {
                    Text(
                        text = str("spotify_credits_sources"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SpotifyGreen
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = sources.joinToString("\n"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }

            // Album / release details
            item {
                val meta = displayTrack.publisherMetadata
                // Playlist/search/artist payloads carry no release date, so fetch this one
                // track's (one request, memoized). No date, no row — better than "Unknown".
                val resolvedReleaseDate by produceState(displayTrack.releaseDate, displayTrack.id) {
                    if (value.isNullOrBlank()) {
                        val spotifyId = vm.getSpotifyTrackId(displayTrack)
                        if (!spotifyId.isNullOrBlank()) {
                            value = runCatching {
                                com.alananasss.kittytune.data.spotify.SpotifyRepository
                                    .getTrackReleaseDate(spotifyId)
                            }.getOrNull()
                        }
                    }
                }
                if (!resolvedReleaseDate.isNullOrBlank()) {
                    DetailInfoRow(str("detail_release_date"), formatReleaseDate(resolvedReleaseDate))
                }
                if (!meta?.albumTitle.isNullOrBlank()) {
                    DetailInfoRow(str("profile_tab_albums"), meta!!.albumTitle!!)
                }
                DetailInfoRow(str("detail_duration"), makeTimeString(displayTrack.durationMs ?: 0L))
                if (!meta?.publisher.isNullOrBlank()) {
                    DetailInfoRow(str("spotify_credits_sources"), meta!!.publisher!!)
                }
            }
        }

        // Tags & details (SoundCloud only). Ahead of the social proof and the description so
        // that the release date sits with the rest of the track's own facts, right under the
        // play/like/repost counts, instead of below a banner about other listeners (issue #33).
        if (!isSpotifyTrack) item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val dateRaw = displayTrack.releaseDate ?: displayTrack.createdAt
                val releaseDateStr = remember(dateRaw) { formatReleaseDate(dateRaw) }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${str("detail_release_date")}: $releaseDateStr",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!displayTrack.genre.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .clickable { vm.navigateToTag(displayTrack.genre) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically, 
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${str("detail_genre")}: ${displayTrack.genre}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                if (!displayTrack.tagList.isNullOrBlank()) {
                    val tags = parseTags(displayTrack.tagList)
                    if (tags.isNotEmpty()) {
                        val scrollState = rememberScrollState()
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(Unit) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                if (event.type == PointerEventType.Scroll) {
                                                    val delta = event.changes.first().scrollDelta.y
                                                    scope.launch {
                                                        scrollState.scrollBy(delta * 50f)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    .horizontalScroll(scrollState),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                tags.forEach { tag ->
                                    SuggestionChip(
                                        onClick = { vm.navigateToTag(tag) },
                                        label = { Text(tag) }
                                    )
                                }
                            }

                            // Left shadow & arrow
                            androidx.compose.animation.AnimatedVisibility(
                                visible = scrollState.value > 0,
                                enter = fadeIn(),
                                exit = fadeOut(),
                                modifier = Modifier.align(Alignment.CenterStart)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(40.dp)
                                        .height(32.dp)
                                        .background(
                                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.surface,
                                                    androidx.compose.ui.graphics.Color.Transparent
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(start = 4.dp)
                                            .size(22.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.CircleShape)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .clickable { scope.launch { scrollState.animateScrollBy(-200f) } },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.ChevronLeft, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }

                            // Right shadow & arrow
                            androidx.compose.animation.AnimatedVisibility(
                                visible = scrollState.value < scrollState.maxValue,
                                enter = fadeIn(),
                                exit = fadeOut(),
                                modifier = Modifier.align(Alignment.CenterEnd)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(40.dp)
                                        .height(32.dp)
                                        .background(
                                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                listOf(
                                                    androidx.compose.ui.graphics.Color.Transparent,
                                                    MaterialTheme.colorScheme.surface
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(end = 4.dp)
                                            .size(22.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.CircleShape)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .clickable { scope.launch { scrollState.animateScrollBy(200f) } },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Social Liked Proof Banner (e.g. "Mandra and 1,400 others liked this track")
        val socialLiker = vm.socialLikerUser
        if (!isSpotifyTrack && socialLiker != null) {
            item {
                Surface(
                    onClick = { vm.navigateToArtist(socialLiker.id) },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AsyncImage(
                            model = socialLiker.avatarUrl?.replace("large", "t500x500"),
                            contentDescription = null,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop
                        )
                        val totalLikes = displayTrack.likesCount ?: 1
                        val otherCount = (totalLikes - 1).coerceAtLeast(0)
                        val text = if (otherCount > 0) {
                            val formattedOthers = NumberFormat.getNumberInstance(Locale.getDefault()).format(otherCount)
                            str("social_proof_liked_multiple", socialLiker.username ?: str("comment_anonymous"), formattedOthers)
                        } else {
                            str("social_proof_liked_single", socialLiker.username ?: str("comment_anonymous"))
                        }
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Description (SoundCloud only)
        if (!isSpotifyTrack && !displayTrack.description.isNullOrBlank()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            ExpandableDescription(
                                text = displayTrack.description!!,
                                onUrlClick = { url ->
                                    com.alananasss.kittytune.core.openUrl(url)
                                },
                                onMentionClick = { username ->
                                    vm.resolveAndNavigateToArtist(username.removePrefix("@"))
                                }
                            )
                        }
                    }
                }
            }
        }

        // The switch between the two halves. Only for SoundCloud tracks: a catalog track has no
        // comments, so there would be only one side to switch to (issue #33).
        if (!isSpotifyTrack) item {
            InfoHalfToggle(
                lyricsSelected = lyricsHalf,
                commentCount = displayTrack.commentCount ?: organizedComments.size,
                onSelect = { showLyricsHalf = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }

        // Comments sort selector (SoundCloud only). No title above it: the selected half of the
        // toggle already names the section and carries the count.
        if (!isSpotifyTrack && !lyricsHalf) item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                var isSortMenuExpanded by remember { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box {
                        OutlinedButton(
                            onClick = { isSortMenuExpanded = true },
                            shapes = ButtonDefaults.shapes(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = str("sorted_by", str(vm.commentSort.labelResId)),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                        }

                        DropdownMenu(
                            expanded = isSortMenuExpanded,
                            onDismissRequest = { isSortMenuExpanded = false }
                        ) {
                            CommentSort.values().forEach { sortOption ->
                                DropdownMenuItem(
                                    text = { Text(str(sortOption.labelResId)) },
                                    onClick = {
                                        vm.onCommentSortChanged(sortOption)
                                        isSortMenuExpanded = false
                                    },
                                    trailingIcon = {
                                        if (sortOption == vm.commentSort) {
                                            Icon(Icons.Rounded.Check, contentDescription = str("desc_selected"), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                )
                            }
                        }
                    }
                    if (vm.isCommentsLoading) {
                        CircularWavyProgressIndicator(modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // Add a new comment
        if (!isSpotifyTrack && !lyricsHalf) item {
            var newCommentText by remember { mutableStateOf("") }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newCommentText,
                    onValueChange = { newCommentText = it },
                    modifier = Modifier.weight(1f).trackTextInput(),
                    // The panel is narrow and the send button takes its share, so the hint has to
                    // survive being given less room than it wants: one line, ellipsized, and a step
                    // down from bodyLarge so it usually fits whole (issue #33).
                    placeholder = {
                        Text(str("add_comment_hint"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp)
                )
                IconButton(
                    onClick = {
                        if (newCommentText.isNotBlank()) {
                            vm.postComment(newCommentText, null)
                            newCommentText = ""
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(Icons.Rounded.Send, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }

        if (!isSpotifyTrack && !lyricsHalf && vm.isCommentsLoading && vm.commentsList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ContainedLoadingIndicator()
                }
            }
        }

        if (!isSpotifyTrack && !lyricsHalf) itemsIndexed(organizedComments, key = { _, comment -> comment.id }) { index, comment ->
            if (index >= organizedComments.size - 3 && !vm.isCommentsLoading && vm.commentNextHref != null) {
                LaunchedEffect(index) {
                    vm.loadComments(refresh = false)
                }
            }
            CommentItemUI(comment, vm)
        }

        if (!isSpotifyTrack && !lyricsHalf && vm.isCommentsLoading && vm.commentsList.isNotEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }

        if (!isSpotifyTrack && !lyricsHalf && !vm.isCommentsLoading && vm.commentsList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(str("comment_no_comments"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (lyricsHalf) trackLyricsHalf(vm, showHeader = isSpotifyTrack)
    }
}

/**
 * The Comments / Lyrics switch in the middle of the info panel (issue #33).
 *
 * The same pill as the lyrics screen's own mode switch, so the two read as one control rather than
 * two conventions. Both halves take the same width and ellipsize: "Commentaires" is a third longer
 * than "Comments" and the panel can be dragged narrow.
 */
@Composable
private fun InfoHalfToggle(
    lyricsSelected: Boolean,
    commentCount: Int,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = remember(commentCount) {
        NumberFormat.getCompactNumberInstance(Strings.locale(), NumberFormat.Style.SHORT)
            .format(commentCount)
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.height(38.dp)
    ) {
        Row(Modifier.padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
            InfoHalfChip(
                text = "${str("menu_comments")} ($count)",
                isSelected = !lyricsSelected,
                onClick = { onSelect(false) },
                modifier = Modifier.weight(1f)
            )
            InfoHalfChip(
                text = str("player_lyrics"),
                isSelected = lyricsSelected,
                onClick = { onSelect(true) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun InfoHalfChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val background by animateColorAsState(
        targetValue = if (isSelected) scheme.primary else Color.Transparent,
        animationSpec = tween(300),
        label = "infoHalfBackground"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) scheme.onPrimary else scheme.onSurfaceVariant,
        animationSpec = tween(300),
        label = "infoHalfText"
    )
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

/**
 * The lyrics half of the info panel (issue #33).
 *
 * The same pane the panel's own Lyrics tab uses, so it follows the song and scrolls itself rather
 * than being a second, stiller copy of the lyrics. It gets a bounded height instead of filling the
 * panel: the cover and the track's details above it are the point of this tab, and a lyrics view
 * that pushed them off the top would have replaced the tab rather than shared it.
 *
 * @param showHeader whether to name the section. Only needed where no toggle names it, which is
 *   Spotify catalog tracks: they have no comments to switch between.
 */
private fun LazyListScope.trackLyricsHalf(vm: PlayerViewModel, showHeader: Boolean) {
    item {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showHeader) {
                Text(
                    text = str("player_lyrics"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Still searching, and nothing to show yet. A result already on screen stays there: the
            // search also runs while a better match is being looked for.
            if (vm.isLyricsLoading && !vm.hasLyrics) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ContainedLoadingIndicator()
                }
            } else {
                PanelLyrics(vm, Modifier.fillMaxWidth().height(LYRICS_HALF_HEIGHT))
            }

            if (vm.hasLyrics) {
                OutlinedButton(
                    onClick = { vm.openLyrics(forceSheet = true) },
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Rounded.Lyrics, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = str("info_open_lyrics_view"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Height of the lyrics pane inside the info tab. Tall enough for the current line to sit a third of
 * the way down with lines either side of it, short enough to leave the cover and the details above
 * it on screen.
 */
private val LYRICS_HALF_HEIGHT = 320.dp

@Composable
fun CommentItemUI(comment: Comment, vm: PlayerViewModel, isReply: Boolean = false) {
    var replyText by remember { mutableStateOf("") }
    var showReplyField by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(start = if (isReply) 48.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(
                model = comment.user?.avatarUrl?.replace("large", "t500x500"),
                contentDescription = null,
                modifier = Modifier.size(if (isReply) 28.dp else 36.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable { comment.user?.id?.let { vm.navigateToArtist(it) } }
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = comment.user?.username ?: str("comment_anonymous"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { comment.user?.id?.let { vm.navigateToArtist(it) } }
                    )
                    if (comment.user?.verified == true) {
                        Icon(
                            Icons.Rounded.Verified,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    if (comment.trackTimestamp != null && comment.trackTimestamp > 0) {
                        val minutes = comment.trackTimestamp / 60000
                        val seconds = (comment.trackTimestamp % 60000) / 1000
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.clickable { vm.seekTo(comment.trackTimestamp) }
                        ) {
                            Text(
                                String.format(Locale.getDefault(), "%d:%02d", minutes, seconds),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    val relTime = getRelativeTime(comment.createdAt)
                    if (relTime.isNotBlank()) {
                        Text(
                            text = relTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            var translatedText by remember { mutableStateOf<String?>(null) }
            var showTranslation by remember { mutableStateOf(false) }
            var isTranslating by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            if (showTranslation && !translatedText.isNullOrEmpty()) {
                CommentBodyText(body = translatedText!!, onMentionClick = { vm.resolveAndNavigateToArtist(it) })
            } else {
                CommentBodyText(body = comment.body, onMentionClick = { vm.resolveAndNavigateToArtist(it) })
            }

            val appLang = com.alananasss.kittytune.core.Strings.appLanguage
            val langCode = if (appLang == "system" || appLang.isBlank()) java.util.Locale.getDefault().language else appLang
            val langName = remember(langCode) {
                val loc = java.util.Locale(langCode)
                loc.getDisplayLanguage(loc).replaceFirstChar { if (it.isLowerCase()) it.titlecase(loc) else it.toString() }
            }

            var isTargetLanguage by remember(comment.body, langCode) { mutableStateOf(false) }
            LaunchedEffect(comment.body, langCode) {
                val cleanText = comment.body.replace(Regex("[^\\p{L}\\p{Nd}\\s]"), "").trim()
                if (cleanText.isBlank()) {
                    isTargetLanguage = true
                } else {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val language = com.alananasss.kittytune.util.LanguageDetection.identifyLanguage(cleanText)
                        if (language == langCode || language == "und") {
                            isTargetLanguage = true
                        }
                    }
                }
            }

            if (translatedText == null && !isTranslating && !isTargetLanguage) {
                Text(
                    text = str("comment_translate", langName),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            isTranslating = true
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val res = com.alananasss.kittytune.data.network.FreeTranslator.translateMissing(listOf(comment.body), langCode)
                                val t = res[comment.body.trim()]
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    if (t != null && t.lowercase() != comment.body.trim().lowercase()) {
                                        translatedText = t
                                        showTranslation = true
                                    } else {
                                        translatedText = "" 
                                    }
                                    isTranslating = false
                                }
                            }
                        }
                        .padding(vertical = 2.dp)
                )
            } else if (isTranslating) {
                Text(
                    text = str("comment_translating"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            } else if (!translatedText.isNullOrEmpty()) {
                Text(
                    text = if (showTranslation) str("comment_see_original") else str("comment_translate", langName),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { showTranslation = !showTranslation }
                        .padding(vertical = 2.dp)
                )
            }
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.clickable { vm.toggleCommentLike(comment) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            if (comment.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            null,
                            modifier = Modifier.size(16.dp),
                            tint = if (comment.isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if ((comment.likesCount ?: 0) > 0) {
                            Text(comment.likesCount.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    
                    Text(
                        str("comment_reply"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { vm.startReplying(comment) }
                    )
                }
            }
        }

        if (!comment.replies.isNullOrEmpty()) {
            Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                comment.replies.forEach { reply ->
                    CommentItemUI(reply, vm, isReply = true)
                }
            }
        }

        if (vm.replyingToComment == comment) {
            var replyText by remember { mutableStateOf("") }
            val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
            
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val targetUser = comment.user?.username ?: str("comment_anonymous")
                    Text(str("comment_replying_to", targetUser), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Icon(Icons.Rounded.Close, null, modifier = Modifier.size(16.dp).clickable { vm.cancelReplying() }, tint = MaterialTheme.colorScheme.primary)
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        modifier = Modifier.weight(1f).trackTextInput().focusRequester(focusRequester),
                        placeholder = { Text(str("comment_write_reply")) },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp)
                    )
                    IconButton(
                        onClick = {
                            if (replyText.isNotBlank()) {
                                vm.postComment(replyText, null)
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(Icons.Rounded.Send, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        }
    }
}

@Composable
private fun StatItem(icon: ImageVector, count: Int, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(NumberFormat.getCompactNumberInstance(Locale.US, NumberFormat.Style.SHORT).format(count), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CommentBodyText(body: String, onMentionClick: (String) -> Unit) {
    val mentionPattern = remember { """@[\w-]+""".toRegex() }
    val urlPattern = remember { """https?://[^\s]+""".toRegex() }
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val primaryColor = MaterialTheme.colorScheme.primary

    val annotatedString = remember(body, tertiaryColor, primaryColor) {
        buildAnnotatedString {
            append(body)
            for (match in mentionPattern.findAll(body)) {
                val username = match.value.removePrefix("@")
                addLink(
                    LinkAnnotation.Clickable(
                        tag = "MENTION",
                        styles = TextLinkStyles(style = SpanStyle(color = tertiaryColor, fontWeight = FontWeight.SemiBold)),
                        linkInteractionListener = { onMentionClick(username) }
                    ),
                    match.range.first, match.range.last + 1
                )
            }
            for (match in urlPattern.findAll(body)) {
                addLink(
                    LinkAnnotation.Url(
                        url = match.value,
                        styles = TextLinkStyles(style = SpanStyle(color = primaryColor, textDecoration = TextDecoration.Underline))
                    ),
                    match.range.first, match.range.last + 1
                )
            }
        }
    }

    SelectionContainer {
        Text(text = annotatedString, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun parseTags(tagListStr: String): List<String> {
    val regex = """"([^"]*)"|(\S+)""".toRegex()
    return regex.findAll(tagListStr).mapNotNull { it.groupValues[1].takeIf { it.isNotEmpty() } ?: it.groupValues[2].takeIf { it.isNotEmpty() } }.toList()
}

private fun formatReleaseDate(raw: String?): String {
    if (raw.isNullOrBlank()) return str("detail_unknown")
    // Spotify sometimes gives precision that stops at the year or the month; those used to
    // fall through every pattern below and come out as "Unknown".
    Regex("^(\\d{4})(?:-(\\d{2}))?$").find(raw.trim())?.let { m ->
        val year = m.groupValues[1]
        val month = m.groupValues[2]
        if (month.isBlank()) return year
        val monthDate = runCatching {
            java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).parse("$year-$month")
        }.getOrNull() ?: return year
        return java.text.SimpleDateFormat("MMMM yyyy", com.alananasss.kittytune.core.Strings.locale()).format(monthDate)
    }
    val date = runCatching { java.time.Instant.parse(raw).let { java.util.Date.from(it) } }.getOrNull()
        ?: runCatching { java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).parse(raw) }.getOrNull()
        ?: runCatching { java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).parse(raw) }.getOrNull()
        ?: runCatching { java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss Z", java.util.Locale.US).parse(raw) }.getOrNull()
        ?: runCatching { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(raw) }.getOrNull()
        ?: return str("detail_unknown")

    val displayFormat = java.text.SimpleDateFormat("d MMMM yyyy", com.alananasss.kittytune.core.Strings.locale())
    return displayFormat.format(date)
}

// ---- Spotify catalog credits (parity with the Android credits sheet) ----

private val SpotifyGreen = androidx.compose.ui.graphics.Color(0xFF1DB954)

@Composable
private fun DetailRow(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
private fun CreditArtistRow(
    artist: com.alananasss.kittytune.data.spotify.SpotifyCreditArtist,
    vm: PlayerViewModel
) {
    // The credits payload only sometimes carries an image; fetch the missing ones so the
    // section shows faces instead of a column of silhouettes.
    val avatar by produceState(artist.imageUri, artist.id) {
        if (value.isNullOrBlank() && artist.id.isNotBlank()) {
            value = runCatching {
                com.alananasss.kittytune.data.spotify.SpotifyRepository.getArtistAvatar(artist.id)
            }.getOrNull()
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = artist.id.isNotBlank()) { vm.navigateToSpotifyArtist(artist.id) }
            .padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
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
            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            val subrolesText = artist.subroles.joinToString(", ") { creditSubroleLabel(it) }
            if (subrolesText.isNotBlank()) {
                Text(
                    text = subrolesText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Rounded.ArrowForwardIos, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

