@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alananasss.kittytune.ui.main

import androidx.compose.foundation.layout.*

import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.player.PlayerViewModel
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
import androidx.compose.foundation.interaction.MutableInteractionSource

@Composable
fun TrackInfoTab(vm: PlayerViewModel) {
    val currentTrack = vm.currentTrack ?: return
    val trackId = currentTrack.id

    var fullTrack by remember(trackId) { mutableStateOf<Track?>(null) }
    var isLoading by remember(trackId) { mutableStateOf(true) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(trackId) {
        if (trackId == 0L) return@LaunchedEffect
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

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
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
                Text(
                    text = displayTrack.user?.username ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable {
                        displayTrack.user?.id?.let { vm.navigateToArtist(it) }
                    }
                )
                
                // Stats Row
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

        // Tags & Details
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!displayTrack.genre.isNullOrBlank()) {
                    Row(
                        modifier = Modifier.clickable { vm.navigateToTag(displayTrack.genre!!) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, 
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.MusicNote, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Genre: ${displayTrack.genre}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (!displayTrack.releaseDate.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.CalendarToday, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Released: ${displayTrack.releaseDate?.take(10)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

        // Description
        if (!displayTrack.description.isNullOrBlank()) {
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
                                    try {
                                        Desktop.getDesktop().browse(URI(url))
                                    } catch (e: Exception) { e.printStackTrace() }
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

        // Comments
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Comments (${displayTrack.commentCount})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (vm.isCommentsLoading) {
                    ContainedLoadingIndicator()
                }
            }
        }
        
        // Add a new comment
        item {
            var newCommentText by remember { mutableStateOf("") }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newCommentText,
                    onValueChange = { newCommentText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Write a comment...") },
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
            if (vm.replyingToComment != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Replying to ${vm.replyingToComment?.user?.username ?: "someone"}", style = MaterialTheme.typography.labelSmall)
                    Icon(Icons.Rounded.Close, null, modifier = Modifier.size(16.dp).clickable { vm.cancelReplying() })
                }
            }
        }



        items(organizedComments) { comment ->
            CommentItemUI(comment, vm)
        }

        if (!vm.isCommentsLoading && vm.commentsList.isEmpty()) {
            item {
                Text("No comments yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

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
                        text = comment.user?.username ?: "",
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
                Text(comment.body, style = MaterialTheme.typography.bodyMedium)
                
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
                        "Reply",
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

private fun parseTags(tagListStr: String): List<String> {
    val regex = """"([^"]*)"|(\S+)""".toRegex()
    return regex.findAll(tagListStr).mapNotNull { it.groupValues[1].takeIf { it.isNotEmpty() } ?: it.groupValues[2].takeIf { it.isNotEmpty() } }.toList()
}
