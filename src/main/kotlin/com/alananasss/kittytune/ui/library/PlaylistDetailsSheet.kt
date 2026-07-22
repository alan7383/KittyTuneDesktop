@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alananasss.kittytune.ui.library

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.AppInstance
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.common.viewableCover
import com.alananasss.kittytune.ui.profile.getRelativeTime
import java.util.regex.Pattern

/**
 * Desktop playlist-info dialog content (Android's PlaylistDetailsSheet redesigned
 * for a wide dialog): artwork + meta header, tag chips, expandable description,
 * and likers/reposters side by side instead of stacked.
 */

@Composable
fun PlaylistDetailsSheet(
    playlistId: String,
    onDismiss: () -> Unit,
    onViewAll: (Int) -> Unit, // 0 = likes, 1 = reposts
    onNavigate: (String) -> Unit,
    onMentionClick: (String) -> Unit,
    viewModel: PlaylistInfoViewModel = viewModel(key = "playlist_info_$playlistId") {
        PlaylistInfoViewModel(AppInstance.application)
    }
) {
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(playlistId) {
        viewModel.loadPlaylistDetails(playlistId)
    }

    val playlist = viewModel.playlistDetails

    Column(modifier = Modifier.fillMaxWidth()) {
        // -------- header: artwork + title + meta + close
        Row(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = playlist?.fullResArtwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .viewableCover(playlist?.fullResArtwork)
            )
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist?.title ?: str("menu_playlist_details"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (playlist?.user != null) {
                    Text(
                        text = str("playlist_by_user", playlist.user.username ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable {
                                onDismiss()
                                onNavigate("profile:${playlist.user.id}")
                            }
                            .padding(vertical = 2.dp)
                    )
                }
                val metaParts = remember(playlist) {
                    if (playlist == null) emptyList() else buildList {
                        if (playlist.isAlbum) add(str("table_header_album"))
                        playlist.trackCount?.takeIf { it > 0 }?.let { add(str("playlist_num_tracks", it)) }
                        val totalMs = playlist.tracks.orEmpty().mapNotNull { it.durationMs }.sum()
                        if (totalMs > 0) add(formatTotalDuration(totalMs))
                        (playlist.lastModified ?: playlist.createdAt)?.let {
                            val relative = getRelativeTime(it)
                            if (relative.isNotEmpty()) add(str("detail_updated", relative))
                        }
                    }
                }
                if (metaParts.isNotEmpty()) {
                    Text(
                        text = metaParts.joinToString(" • "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp),

                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(Icons.Rounded.Close, str("btn_close"), modifier = Modifier.size(18.dp))
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        )

        if (viewModel.isLoading) {
            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 24.dp)
            ) {
                // -------- genre + tags
                if (playlist != null) {
                    val tags = remember(playlist.tagList, playlist.genre) {
                        val list = parseSoundCloudTags(playlist.tagList).toMutableList()
                        if (!playlist.genre.isNullOrBlank() && !list.contains(playlist.genre)) {
                            list.add(0, playlist.genre)
                        }
                        list
                    }
                    if (tags.isNotEmpty()) {
                        FlowRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                            tags.take(10).forEach { tag ->
                                Button(
                                    onClick = {
                                        onDismiss()
                                        onNavigate("tag:$tag")
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.padding(end = 8.dp, bottom = 8.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Tag,
                                        null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = tag.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }

                // -------- description
                if (!playlist?.description.isNullOrBlank()) {
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        Text(
                            text = str("detail_description"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        ExpandableDescription(
                            text = playlist.description!!,
                            onUrlClick = { url -> uriHandler.openUri(url) },
                            onMentionClick = { username ->
                                onDismiss()
                                onMentionClick(username)
                            }
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )
                    Spacer(Modifier.height(24.dp))
                }

                // -------- likers + reposters, side by side on desktop
                if (viewModel.likers.isEmpty() && viewModel.reposters.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.Info, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.surfaceVariant)
                            Spacer(Modifier.height(16.dp))
                            Text(
                                str("no_details_available"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        if (viewModel.likers.isNotEmpty()) {
                            UserSection(
                                title = str("detail_stats_likes"),
                                icon = Icons.Rounded.Favorite,
                                users = viewModel.likers,
                                count = playlist?.likesCount,
                                onViewAll = { onViewAll(0) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (viewModel.reposters.isNotEmpty()) {
                            UserSection(
                                title = str("detail_stats_reposts"),
                                icon = Icons.Rounded.Repeat,
                                users = viewModel.reposters,
                                count = null,
                                onViewAll = { onViewAll(1) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserSection(
    title: String,
    icon: ImageVector,
    users: List<User>,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onViewAll() }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (count != null && count > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = String.format("%,d", count),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Text(
                text = str("btn_see_all"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy((-12).dp)) {
            users.take(6).forEach { user ->
                AvatarItem(user.avatarUrl)
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable { onViewAll() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "+",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AvatarItem(url: String?) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(2.dp)
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
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
    val secondaryColor = MaterialTheme.colorScheme.secondary

    val annotatedString = remember(text, isExpanded, primaryColor, secondaryColor) {
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
                        styles = TextLinkStyles(style = SpanStyle(color = secondaryColor, fontWeight = FontWeight.SemiBold)),
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
                maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis
            )

            if (text.length > 150 || text.lines().size > 3) {
                Text(
                    text = if (isExpanded) str("detail_show_less") else str("detail_show_more"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable { isExpanded = !isExpanded }
                )
            }
        }
    }
}

fun parseSoundCloudTags(tagList: String?): List<String> {
    if (tagList.isNullOrBlank()) return emptyList()
    val tags = mutableListOf<String>()
    val pattern = Pattern.compile("\"([^\"]*)\"|(\\S+)")
    val matcher = pattern.matcher(tagList)
    while (matcher.find()) {
        if (matcher.group(1) != null) {
            tags.add(matcher.group(1)!!)
        } else {
            tags.add(matcher.group(2)!!)
        }
    }
    return tags
}

/** "1 h 23 min" / "23 min" from a summed track duration. */
private fun formatTotalDuration(ms: Long): String {
    val totalMin = ms / 60000
    val h = totalMin / 60
    val min = totalMin % 60
    return if (h > 0) "$h h $min min" else "$min min"
}
