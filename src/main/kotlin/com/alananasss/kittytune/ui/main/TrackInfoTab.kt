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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.dp
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
     * Which half the panel shows below the track details (issue #33).
     *
     * It was a `rememberSaveable`, which sounds like it survives the panel closing and does not: the
     * saved-state registry belongs to the composition the panel is part of, so choosing the lyrics
     * and closing the panel gave the comments back on the next open. Reported as "the comments
     * button and the text don't remember my choice".
     *
     * It reads a preference now, which also answers the request for a default: Comments, Lyrics, or
     * whichever was last picked. Never keyed on the track — someone who opened the panel for the
     * lyrics wants the lyrics on the next track too.
     *
     * Spotify catalog tracks have no comments at all, so there is nothing to toggle between: the
     * lyrics are the only half they have.
     */
    val playerPrefs = remember { com.alananasss.kittytune.data.local.PlayerPreferences() }
    var showLyricsHalf by remember { mutableStateOf(playerPrefs.infoPanelOpensOnLyrics()) }
    val lyricsHalf = isSpotifyTrack || showLyricsHalf

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panelHeight = maxHeight
        val isCompact = lyricsHalf && panelHeight < 780.dp
        val isUltraCompact = lyricsHalf && panelHeight < 580.dp

        val dynamicLyricsHeight = when {
            !lyricsHalf -> LYRICS_HALF_HEIGHT
            isUltraCompact -> (panelHeight - 120.dp).coerceAtLeast(180.dp)
            isCompact -> (panelHeight - 160.dp).coerceAtLeast(220.dp)
            else -> LYRICS_HALF_HEIGHT
        }

        LazyColumn(
            // The horizontal inset belongs to the content, not to the container: applied to the
            // container it also pushed the scrollbar 16.dp inwards, which parked it against the text
            // instead of at the panel edge (issue #33).
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(if (isCompact) 12.dp else 18.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)
        ) {
            item {
                TrackInfoHeader(vm, displayTrack, isSpotifyTrack, isCompact, isUltraCompact)
            }

        if (isSpotifyTrack) spotifyCreditsSection(vm, displayTrack, spotifyCredits)

        // Release date, genre, and tags — kept in a stable position above the toggle
        // so switching between Comments and Lyrics doesn't cause the buttons to jump (issue #33).
        if (!isSpotifyTrack) trackTagsAndDetails(vm, displayTrack, scope)

        // Social Liked Proof Banner (e.g. "Mandra and 1,400 others liked this track")
        val socialLiker = vm.socialLikerUser
        if (!isSpotifyTrack && socialLiker != null) {
            item {
                Surface(
                    onClick = { vm.navigateToArtist(socialLiker.id) },
                    shape = RoundedCornerShape(16.dp),
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
                        shape = RoundedCornerShape(16.dp),
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
                onSelect = {
                    showLyricsHalf = it
                    // Written whatever the setting says. Someone who switches the setting to
                    // "last choice" later should find the choice they had already been making.
                    playerPrefs.setInfoPanelLastLyrics(it)
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }

        // Sorting and writing, in one row: a round sort button, then one long field with the send button
        // inside it (SoundCloud only).
        if (!isSpotifyTrack && !lyricsHalf) item {
            var isSortMenuExpanded by remember { mutableStateOf(false) }
            var newCommentText by remember { mutableStateOf("") }
            val send = {
                if (newCommentText.isNotBlank()) {
                    vm.postComment(newCommentText, null)
                    newCommentText = ""
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    FilledTonalIconButton(onClick = { isSortMenuExpanded = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = str("sorted_by", str(vm.commentSort.labelResId)))
                    }
                    DropdownMenu(expanded = isSortMenuExpanded, onDismissRequest = { isSortMenuExpanded = false }) {
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
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = newCommentText,
                    onValueChange = { newCommentText = it },
                    modifier = Modifier.weight(1f).trackTextInput(),
                    placeholder = { Text(str("add_comment_hint"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { send() }),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                    trailingIcon = {
                        IconButton(
                            onClick = send,
                            enabled = newCommentText.isNotBlank(),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                            modifier = Modifier.padding(end = 4.dp).size(40.dp),
                        ) {
                            Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    },
                )
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

        if (lyricsHalf) trackLyricsHalf(vm, showHeader = isSpotifyTrack, lyricsHeight = dynamicLyricsHeight)
    }
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
                text = str("menu_comments"),
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
private fun LazyListScope.trackLyricsHalf(
    vm: PlayerViewModel,
    showHeader: Boolean,
    lyricsHeight: androidx.compose.ui.unit.Dp = LYRICS_HALF_HEIGHT,
) {
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
                PanelLyrics(vm, Modifier.fillMaxWidth().height(lyricsHeight))
            }

            if (vm.hasLyrics) {
                OutlinedButton(
                    onClick = { vm.openLyrics() },
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
/**
 * How tall the lyrics are inside the info tab.
 *
 * "Make the text higher." It was 320 dp, chosen when the release date, the genre and the tag chips sat
 * above it and took the difference; they are below it now, so the words get the height back (issue #33).
 */
private val LYRICS_HALF_HEIGHT = 440.dp

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

                    val relTime = getRelativeTime(comment.createdAt)
                    if (relTime.isNotBlank()) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = relTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
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

internal fun formatReleaseDate(raw: String?): String {
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

/**
 * The release date, the genre and the tags (issue #33).
 *
 * Kept in a stable position above the Comments / Lyrics toggle rather than swapping between
 * top (in comments) and bottom (in lyrics), which caused the toggle buttons to jump up and down.
 *
 * One line for the date and the genre, not two rows twelve dp apart, and no labels: a calendar before a
 * date and a note before a genre say the same thing in no horizontal space at all.
 */
private fun LazyListScope.trackTagsAndDetails(
    vm: PlayerViewModel,
    displayTrack: com.alananasss.kittytune.domain.Track,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val dateRaw = displayTrack.releaseDate ?: displayTrack.createdAt
                val releaseDateStr = remember(dateRaw) { formatReleaseDate(dateRaw) }
                val genre = displayTrack.genre

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        Icons.Rounded.CalendarToday,
                        contentDescription = str("detail_release_date"),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = releaseDateStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )

                    if (!genre.isNullOrBlank()) {
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Row(
                            modifier = Modifier
                                .clickable { vm.navigateToTag(genre) }
                                .weight(1f, fill = false),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                Icons.Rounded.MusicNote,
                                contentDescription = str("detail_genre"),
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            // Ellipsized rather than wrapping: a long genre string must not be what
                            // turns this one line back into two.
                            Text(
                                text = genre,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
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
}