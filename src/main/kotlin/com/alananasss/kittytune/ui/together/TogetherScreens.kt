@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.alananasss.kittytune.ui.together

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.EscapableAlertDialog
import com.alananasss.kittytune.core.Toaster
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.core.trackTextInput
import com.alananasss.kittytune.data.BlockManager
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.data.together.SharedTrack
import com.alananasss.kittytune.data.together.Together
import com.alananasss.kittytune.data.together.TogetherLinks
import com.alananasss.kittytune.data.together.TogetherWire
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
import com.alananasss.kittytune.ui.player.PlayerViewModel
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class HomeActionMode { JOIN, CREATE }
private enum class RoomSectionTab { QUEUE, PLAYLIST, SUGGESTIONS, ADD }

// ─── Together Home Screen (Material 3 Expressive, Zero Gradients, Responsive Desktop) ───────

@Composable
fun TogetherHomeScreen(
    onOpen: (String) -> Unit,
    playerViewModel: PlayerViewModel? = null,
) {
    val cs = MaterialTheme.colorScheme
    val saved by Together.saved.collectAsState()
    val rooms by Together.rooms.collectAsState()
    val activeCode by Together.active.collectAsState()
    val myAvatarUrl by Together.myAvatarUrlFlow.collectAsState()

    var actionMode by rememberSaveable { mutableStateOf(HomeActionMode.JOIN) }
    var newName by rememberSaveable { mutableStateOf("") }
    var joinCode by rememberSaveable { mutableStateOf("") }
    var myName by remember { mutableStateOf(Together.myName) }
    var showNameDialog by remember { mutableStateOf(false) }

    val clipboardInviteCode = remember(saved, activeCode) {
        TogetherLinks.readClipboardInviteCode()?.takeIf { code ->
            code != activeCode && saved.none { it.code == code }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background),
    ) {
        val isWide = maxWidth >= 920.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Top Bar Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = str("together_title"),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                        color = cs.onSurface,
                    )

                    Surface(
                        onClick = { showNameDialog = true },
                        shape = CircleShape,
                        color = cs.secondaryContainer,
                    ) {
                        var selfAvatarLoaded by remember(myAvatarUrl) { mutableStateOf(false) }
                        Row(
                            modifier = Modifier.padding(
                                start = if (myAvatarUrl != null) 8.dp else 16.dp,
                                end = 16.dp,
                                top = if (myAvatarUrl != null) 7.dp else 10.dp,
                                bottom = if (myAvatarUrl != null) 7.dp else 10.dp,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (myAvatarUrl != null) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(cs.surfaceContainerHighest),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (!selfAvatarLoaded) {
                                        Icon(
                                            imageVector = Icons.Rounded.Person,
                                            contentDescription = null,
                                            tint = cs.onSecondaryContainer,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                    AsyncImage(
                                        model = myAvatarUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        onSuccess = { selfAvatarLoaded = true },
                                        onError = { selfAvatarLoaded = false },
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape),
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = null,
                                    tint = cs.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Text(
                                text = myName,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = cs.onSecondaryContainer,
                                maxLines = 1,
                            )
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = str("together_edit_name"),
                                tint = cs.onSecondaryContainer,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
            }

            if (clipboardInviteCode != null) {
                item {
                    ClipboardInviteBanner(
                        code = clipboardInviteCode,
                        onJoin = {
                            Together.joinAndListen(clipboardInviteCode)?.let { onOpen(it.code) }
                        },
                    )
                }
            }

            val liveRoom = activeCode?.let { rooms[it] ?: Together.Room(it, "") }

            if (isWide) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            modifier = Modifier.weight(1.05f),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            LiveBroadcastHeroCard(
                                activeCode = activeCode,
                                activeRoom = liveRoom,
                                onStartLive = {
                                    val code = playerViewModel?.startLiveListenAlong()
                                        ?: Together.startLiveBroadcast()
                                    onOpen(code)
                                },
                                onOpenRoom = onOpen,
                                onShareLink = { TogetherLinks.copyInviteLink(it) },
                                onStopLive = { Together.stopListening() },
                            )

                            JoinOrCreateCard(
                                actionMode = actionMode,
                                onActionModeChange = { actionMode = it },
                                joinCode = joinCode,
                                onJoinCodeChange = { joinCode = it },
                                newName = newName,
                                onNewNameChange = { newName = it },
                                onOpen = onOpen,
                            )
                        }

                        Box(modifier = Modifier.weight(0.95f)) {
                            SavedSessionsSection(
                                saved = saved,
                                rooms = rooms,
                                activeCode = activeCode,
                                onOpen = onOpen,
                            )
                        }
                    }
                }
            } else {
                item {
                    LiveBroadcastHeroCard(
                        activeCode = activeCode,
                        activeRoom = liveRoom,
                        onStartLive = {
                            val code = playerViewModel?.startLiveListenAlong()
                                ?: Together.startLiveBroadcast()
                            onOpen(code)
                        },
                        onOpenRoom = onOpen,
                        onShareLink = { TogetherLinks.copyInviteLink(it) },
                        onStopLive = { Together.stopListening() },
                    )
                }

                item {
                    JoinOrCreateCard(
                        actionMode = actionMode,
                        onActionModeChange = { actionMode = it },
                        joinCode = joinCode,
                        onJoinCodeChange = { joinCode = it },
                        newName = newName,
                        onNewNameChange = { newName = it },
                        onOpen = onOpen,
                    )
                }

                item {
                    SavedSessionsSection(
                        saved = saved,
                        rooms = rooms,
                        activeCode = activeCode,
                        onOpen = onOpen,
                    )
                }
            }
        }
    }

    if (showNameDialog) {
        var draft by remember(myName) { mutableStateOf(myName) }
        EscapableAlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text(str("together_your_name")) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().trackTextInput(),
                )
            },
            confirmButton = {
                Button(
                    shapes = ButtonDefaults.shapes(),
                    onClick = {
                        val cleaned = draft.trim()
                        if (cleaned.isNotBlank()) {
                            myName = cleaned
                            Together.myName = cleaned
                        }
                        showNameDialog = false
                    },
                ) {
                    Text(str("btn_save"))
                }
            },
            dismissButton = {
                TextButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = { showNameDialog = false },
                ) {
                    Text(str("btn_cancel"))
                }
            },
        )
    }
}

@Composable
private fun ClipboardInviteBanner(
    code: String,
    onJoin: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onJoin,
        shape = RoundedCornerShape(26.dp),
        color = cs.tertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ExpressiveCookieBadge(
                isActive = true,
                size = 46.dp,
                activeColor = cs.tertiary,
                inactiveColor = cs.tertiary,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Link,
                    contentDescription = null,
                    tint = cs.onTertiary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = str("together_clipboard_banner", TogetherWire.displayCode(code)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = cs.onTertiaryContainer,
                )
                Text(
                    text = str("together_listen"),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onTertiaryContainer.copy(alpha = 0.8f),
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = cs.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun JoinOrCreateCard(
    actionMode: HomeActionMode,
    onActionModeChange: (HomeActionMode) -> Unit,
    joinCode: String,
    onJoinCodeChange: (String) -> Unit,
    newName: String,
    onNewNameChange: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = cs.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TogetherSegmentedTabs(
                options = listOf(
                    TogetherTabOption(
                        value = HomeActionMode.JOIN,
                        label = str("together_join"),
                        icon = Icons.AutoMirrored.Rounded.Login,
                    ),
                    TogetherTabOption(
                        value = HomeActionMode.CREATE,
                        label = str("together_create"),
                        icon = Icons.Rounded.Add,
                    ),
                ),
                selected = actionMode,
                onSelect = onActionModeChange,
                containerColor = cs.surfaceContainerHigh,
            )

            AnimatedContent(
                targetState = actionMode,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                label = "homeActionModeContent",
            ) { mode ->
                when (mode) {
                    HomeActionMode.JOIN -> {
                        val submitJoin = {
                            val joined = Together.joinAndListen(joinCode)
                            if (joined != null) {
                                onJoinCodeChange("")
                                onOpen(joined.code)
                            } else {
                                Toaster.show(str("together_bad_code"))
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = joinCode,
                                onValueChange = onJoinCodeChange,
                                placeholder = {
                                    Text(
                                        str("together_link_or_code_hint"),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Link, contentDescription = null)
                                },
                                trailingIcon = {
                                    if (joinCode.isNotBlank()) {
                                        IconButton(onClick = { onJoinCodeChange("") }) {
                                            Icon(Icons.Rounded.Close, contentDescription = null)
                                        }
                                    } else {
                                        IconButton(
                                            onClick = {
                                                val clip = runCatching {
                                                    val cb = Toolkit.getDefaultToolkit().systemClipboard
                                                    if (cb.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                                                        cb.getData(DataFlavor.stringFlavor) as? String
                                                    } else null
                                                }.getOrNull()
                                                if (!clip.isNullOrBlank()) {
                                                    onJoinCodeChange(clip.trim())
                                                }
                                            },
                                        ) {
                                            Icon(
                                                Icons.Rounded.ContentPaste,
                                                contentDescription = str("together_paste"),
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(18.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { submitJoin() }),
                                modifier = Modifier.fillMaxWidth().trackTextInput(),
                            )
                            Button(
                                onClick = { submitJoin() },
                                enabled = joinCode.isNotBlank(),
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                            ) {
                                Icon(Icons.Rounded.Headphones, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(str("together_listen"), fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    HomeActionMode.CREATE -> {
                        val submitCreate = {
                            val created = Together.create(newName)
                            onNewNameChange("")
                            TogetherLinks.copyInviteLink(created.code)
                            onOpen(created.code)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = newName,
                                onValueChange = onNewNameChange,
                                placeholder = {
                                    Text(
                                        str("together_name_hint"),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null)
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(18.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { submitCreate() }),
                                modifier = Modifier.fillMaxWidth().trackTextInput(),
                            )
                            Button(
                                onClick = { submitCreate() },
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(str("together_create"), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedSessionsSection(
    saved: List<Together.Saved>,
    rooms: Map<String, Together.Room>,
    activeCode: String?,
    onOpen: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    TogetherGroupSection(
        title = str("together_saved_sessions"),
    ) {
        if (saved.isEmpty()) {
            TogetherGroupItem {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    TogetherIconBadge(icon = Icons.Rounded.Groups)
                    Text(
                        text = str("together_no_saved_sessions"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            saved.forEach { entry ->
                val room = rooms[entry.code]
                val isActiveHere = activeCode == entry.code
                val listeners = room?.listeners?.map { it.name }.orEmpty()
                val live = isActiveHere || listeners.isNotEmpty()
                val displayName = room?.name?.ifBlank { entry.name }?.ifBlank { "" } ?: entry.name

                TogetherGroupItem(
                    highlighted = isActiveHere,
                    onClick = { onOpen(entry.code) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        TogetherArtwork(
                            url = room?.nowPlaying?.artworkUrl ?: entry.tracks.firstOrNull()?.artworkUrl,
                            isLive = live,
                            size = 56.dp,
                            cornerRadius = 16.dp,
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = displayName.ifBlank { str("together_title") },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (live) {
                                    Surface(shape = CircleShape, color = cs.primary) {
                                        Text(
                                            text = str("together_live_badge"),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = cs.onPrimary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                            val statusText = when {
                                room?.nowPlaying != null -> listOfNotNull(
                                    room.nowPlaying.title,
                                    room.nowPlaying.artist,
                                ).joinToString(" · ")
                                listeners.isNotEmpty() -> str("together_listening_now", listeners.joinToString(", "))
                                isActiveHere -> str("together_listen")
                                else -> str("together_nobody")
                            }
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (live) cs.primary else cs.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = TogetherWire.displayCode(entry.code),
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.outline,
                            )
                        }
                        IconButton(
                            onClick = { TogetherLinks.copyInviteLink(entry.code) },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = str("together_share_link"),
                                tint = cs.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveBroadcastHeroCard(
    activeCode: String?,
    activeRoom: Together.Room?,
    onStartLive: () -> Unit,
    onOpenRoom: (String) -> Unit,
    onShareLink: (String) -> Unit,
    onStopLive: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val isLive = activeCode != null
    val containerColor by animateColorAsState(
        targetValue = if (isLive) cs.primaryContainer else cs.surfaceContainerHigh,
        animationSpec = tween(280),
        label = "liveHeroContainer",
    )
    val titleColor = if (isLive) cs.onPrimaryContainer else cs.onSurface
    val subColor = if (isLive) cs.onPrimaryContainer.copy(alpha = 0.82f) else cs.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(containerColor)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ExpressiveCookieBadge(
                isActive = isLive,
                size = 76.dp,
                activeColor = cs.primary,
                inactiveColor = cs.secondaryContainer,
            ) {
                if (isLive) {
                    ListeningBars(
                        isPlaying = true,
                        modifier = Modifier.size(28.dp),
                        color = cs.onPrimary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Podcasts,
                        contentDescription = null,
                        tint = cs.onSecondaryContainer,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = if (isLive) {
                        activeRoom?.name?.takeIf { it.isNotBlank() } ?: str("together_live_title")
                    } else {
                        str("together_live_title")
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (activeCode != null) {
                        val listeners = activeRoom?.listeners?.map { it.name }.orEmpty()
                        if (listeners.isNotEmpty()) {
                            str("together_listening_now", listeners.joinToString(", "))
                        } else {
                            str("together_code", TogetherWire.displayCode(activeCode))
                        }
                    } else {
                        str("together_hint")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = subColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (activeCode != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { onOpenRoom(activeCode) },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = cs.primary,
                        contentColor = cs.onPrimary,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(str("together_open_room"), fontWeight = FontWeight.Bold, maxLines = 1)
                }
                FilledTonalIconButton(
                    onClick = { onShareLink(activeCode) },
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = cs.surface,
                        contentColor = cs.onSurface,
                    ),
                ) {
                    Icon(Icons.Rounded.Share, str("together_share_link"))
                }
                FilledTonalIconButton(
                    onClick = onStopLive,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = cs.errorContainer,
                        contentColor = cs.onErrorContainer,
                    ),
                ) {
                    Icon(Icons.Rounded.StopCircle, str("together_stop_live"))
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onStartLive,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                ) {
                    Icon(Icons.Rounded.Podcasts, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(str("together_start_live"), fontWeight = FontWeight.Bold, maxLines = 1)
                }
                FilledTonalIconButton(
                    onClick = { onShareLink(Together.personalLiveCode) },
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(50.dp),
                ) {
                    Icon(Icons.Rounded.Share, str("together_share_link"))
                }
            }
        }
    }
}

// ─── Together Room / Live Session Screen (Material 3 Expressive, Zero Gradients!) ───────────

@Composable
fun TogetherScreen(
    code: String,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val normalizedCode = TogetherWire.normalizeCode(code)
    val rooms by Together.rooms.collectAsState()
    val active by Together.active.collectAsState()
    val connected by Together.isConnected.collectAsState()
    val hostLockedPref by Together.isHostLocked.collectAsState()

    val room = rooms[normalizedCode] ?: Together.Room(normalizedCode, "")
    val isListening = active == normalizedCode
    val isHost = room.hostId == Together.memberId
    val effectiveHostLocked = if (isHost) hostLockedPref else room.hostLocked

    val defaultTab = when {
        room.upcoming.isNotEmpty() || room.hasLiveHost || isListening -> RoomSectionTab.QUEUE
        room.playlist.isNotEmpty() -> RoomSectionTab.PLAYLIST
        else -> RoomSectionTab.ADD
    }
    var selectedTab by rememberSaveable(normalizedCode) { mutableStateOf(defaultTab) }
    var showAllPlaylistTracks by rememberSaveable(normalizedCode) { mutableStateOf(false) }

    var query by rememberSaveable(normalizedCode) { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    val results = remember { mutableStateListOf<Track>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(query, selectedTab) {
        if (selectedTab != RoomSectionTab.ADD) return@LaunchedEffect
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            if (trimmed.isEmpty()) results.clear()
            isSearching = false
            return@LaunchedEffect
        }
        delay(360L)
        isSearching = true
        val found = runCatching {
            RetrofitClient.create().searchTracks(trimmed, limit = 20).collection
        }.getOrDefault(emptyList())
        results.clear()
        results.addAll(BlockManager.filterBlocked(found))
        isSearching = false
    }

    val tabs = remember(room.upcoming.size, room.playlist.size, room.suggestions.size, isHost) {
        buildList {
            add(
                TogetherTabOption(
                    value = RoomSectionTab.QUEUE,
                    label = str("together_tab_queue"),
                    badgeCount = room.upcoming.size,
                )
            )
            add(
                TogetherTabOption(
                    value = RoomSectionTab.PLAYLIST,
                    label = str("together_tab_playlist"),
                    badgeCount = room.playlist.size,
                )
            )
            if (isHost && room.suggestions.isNotEmpty()) {
                add(
                    TogetherTabOption(
                        value = RoomSectionTab.SUGGESTIONS,
                        label = str("together_suggestions"),
                        badgeCount = room.suggestions.size,
                    )
                )
            }
            add(
                TogetherTabOption(
                    value = RoomSectionTab.ADD,
                    label = str("together_tab_search"),
                    icon = Icons.Rounded.Add,
                )
            )
        }
    }
    val activeTab = if (tabs.any { it.value == selectedTab }) selectedTab else RoomSectionTab.QUEUE

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background),
    ) {
        val isWide = maxWidth >= 980.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    if (!com.alananasss.kittytune.ui.common.LocalMainNavigationVisible.current) {
                        FilledTonalIconButton(
                            onClick = onBack,
                            shapes = IconButtonDefaults.shapes(),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = cs.surfaceContainerHigh,
                                contentColor = cs.onSurface,
                            ),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, str("btn_back"))
                        }
                    }
                    Text(
                        text = room.name.ifBlank { str("together_title") },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (!connected) {
                        Surface(shape = CircleShape, color = cs.errorContainer) {
                            Text(
                                text = str("together_offline"),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = cs.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalIconButton(
                        onClick = { TogetherLinks.copyInviteLink(normalizedCode) },
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = cs.secondaryContainer,
                            contentColor = cs.onSecondaryContainer,
                        ),
                    ) {
                        Icon(Icons.Rounded.Share, str("together_share_link"))
                    }
                    IconButton(
                        onClick = {
                            Together.forget(normalizedCode)
                            onBack()
                        },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = str("together_forget"),
                            tint = cs.onSurfaceVariant,
                        )
                    }
                }
            }

            if (isWide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(0.95f)
                            .padding(bottom = 24.dp),
                    ) {
                        RoomExpressiveHeroCard(
                            room = room,
                            isListening = isListening,
                            isHost = isHost,
                            isHostLocked = effectiveHostLocked,
                            onCopyCode = { TogetherLinks.copyCode(normalizedCode) },
                            onShareLink = { TogetherLinks.copyInviteLink(normalizedCode) },
                            onToggleListen = {
                                if (isListening) Together.stopListening() else Together.startListening(normalizedCode)
                            },
                            onToggleLock = { Together.setHostLocked(!hostLockedPref) },
                            onClaimHost = { Together.claimHost(normalizedCode) },
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1.25f)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 48.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        roomTabItems(
                            normalizedCode = normalizedCode,
                            room = room,
                            isListening = isListening,
                            isHost = isHost,
                            effectiveHostLocked = effectiveHostLocked,
                            tabs = tabs,
                            activeTab = activeTab,
                            onSelectTab = { selectedTab = it },
                            showAllPlaylistTracks = showAllPlaylistTracks,
                            onShowAllPlaylistTracks = { showAllPlaylistTracks = true },
                            query = query,
                            onQueryChange = { query = it },
                            isSearching = isSearching,
                            results = results,
                            onTriggerSearch = {
                                scope.launch {
                                    val trimmed = query.trim()
                                    if (trimmed.isBlank()) return@launch
                                    isSearching = true
                                    val found = runCatching {
                                        RetrofitClient.create().searchTracks(trimmed, limit = 20).collection
                                    }.getOrDefault(emptyList())
                                    results.clear()
                                    results.addAll(BlockManager.filterBlocked(found))
                                    isSearching = false
                                }
                            },
                            onClearSearch = { query = ""; results.clear() },
                            playerViewModel = playerViewModel,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        RoomExpressiveHeroCard(
                            room = room,
                            isListening = isListening,
                            isHost = isHost,
                            isHostLocked = effectiveHostLocked,
                            onCopyCode = { TogetherLinks.copyCode(normalizedCode) },
                            onShareLink = { TogetherLinks.copyInviteLink(normalizedCode) },
                            onToggleListen = {
                                if (isListening) Together.stopListening() else Together.startListening(normalizedCode)
                            },
                            onToggleLock = { Together.setHostLocked(!hostLockedPref) },
                            onClaimHost = { Together.claimHost(normalizedCode) },
                        )
                    }

                    roomTabItems(
                        normalizedCode = normalizedCode,
                        room = room,
                        isListening = isListening,
                        isHost = isHost,
                        effectiveHostLocked = effectiveHostLocked,
                        tabs = tabs,
                        activeTab = activeTab,
                        onSelectTab = { selectedTab = it },
                        showAllPlaylistTracks = showAllPlaylistTracks,
                        onShowAllPlaylistTracks = { showAllPlaylistTracks = true },
                        query = query,
                        onQueryChange = { query = it },
                        isSearching = isSearching,
                        results = results,
                        onTriggerSearch = {
                            scope.launch {
                                val trimmed = query.trim()
                                if (trimmed.isBlank()) return@launch
                                isSearching = true
                                val found = runCatching {
                                    RetrofitClient.create().searchTracks(trimmed, limit = 20).collection
                                }.getOrDefault(emptyList())
                                results.clear()
                                results.addAll(BlockManager.filterBlocked(found))
                                isSearching = false
                            }
                        },
                        onClearSearch = { query = ""; results.clear() },
                        playerViewModel = playerViewModel,
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.roomTabItems(
    normalizedCode: String,
    room: Together.Room,
    isListening: Boolean,
    isHost: Boolean,
    effectiveHostLocked: Boolean,
    tabs: List<TogetherTabOption<RoomSectionTab>>,
    activeTab: RoomSectionTab,
    onSelectTab: (RoomSectionTab) -> Unit,
    showAllPlaylistTracks: Boolean,
    onShowAllPlaylistTracks: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    results: List<Track>,
    onTriggerSearch: () -> Unit,
    onClearSearch: () -> Unit,
    playerViewModel: PlayerViewModel,
) {
    if (isHost && room.suggestions.isNotEmpty() && activeTab != RoomSectionTab.SUGGESTIONS) {
        item {
            val cs = MaterialTheme.colorScheme
            TogetherGroupSection(
                title = "${str("together_suggestions")} (${room.suggestions.size})",
            ) {
                room.suggestions.forEach { suggestion ->
                    TogetherGroupItem {
                        ExpressiveTrackRow(
                            track = suggestion.track,
                            subtitle = str("together_suggested_by", suggestion.byName),
                        ) {
                            IconButton(
                                onClick = { Together.listenToSuggestion(normalizedCode, suggestion) },
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                Icon(Icons.Rounded.PlayArrow, str("together_preview"))
                            }
                            FilledTonalIconButton(
                                onClick = { Together.acceptSuggestion(normalizedCode, suggestion) },
                                shapes = IconButtonDefaults.shapes(),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = cs.primaryContainer,
                                    contentColor = cs.onPrimaryContainer,
                                ),
                            ) {
                                Icon(Icons.Rounded.Check, str("together_accept"))
                            }
                            IconButton(
                                onClick = { Together.declineSuggestion(normalizedCode, suggestion) },
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                Icon(Icons.Rounded.Close, str("together_decline"))
                            }
                        }
                    }
                }
            }
        }
    }

    item {
        TogetherSegmentedTabs(
            options = tabs,
            selected = activeTab,
            onSelect = onSelectTab,
        )
    }

    when (activeTab) {
        RoomSectionTab.QUEUE -> {
            item {
                val cs = MaterialTheme.colorScheme
                if (room.upcoming.isEmpty()) {
                    TogetherGroupSection(title = str("together_up_next")) {
                        TogetherGroupItem {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                TogetherIconBadge(icon = Icons.AutoMirrored.Rounded.QueueMusic)
                                Text(
                                    text = str("together_empty"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = cs.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                FilledTonalButton(
                                    onClick = { onSelectTab(RoomSectionTab.ADD) },
                                    shapes = ButtonDefaults.shapes(),
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(str("together_tab_search"))
                                }
                            }
                        }
                    }
                } else {
                    TogetherGroupSection(title = str("together_up_next")) {
                        room.upcoming.take(20).forEachIndexed { index, track ->
                            TogetherGroupItem {
                                ExpressiveTrackRow(
                                    track = track,
                                    index = index,
                                )
                            }
                        }
                    }
                }
            }
        }

        RoomSectionTab.PLAYLIST -> {
            item {
                val cs = MaterialTheme.colorScheme
                if (room.playlist.isEmpty()) {
                    TogetherGroupSection(title = str("together_tracks")) {
                        TogetherGroupItem {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                TogetherIconBadge(icon = Icons.Rounded.MusicNote)
                                Text(
                                    text = str("together_empty"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = cs.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                FilledTonalButton(
                                    onClick = { onSelectTab(RoomSectionTab.ADD) },
                                    shapes = ButtonDefaults.shapes(),
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(str("together_tab_search"))
                                }
                            }
                        }
                    }
                } else {
                    val visibleTracks = if (showAllPlaylistTracks) room.playlist else room.playlist.take(25)
                    TogetherGroupSection(title = str("together_tracks")) {
                        visibleTracks.forEachIndexed { index, track ->
                            val isCurrent = room.nowPlaying?.id == track.id
                            TogetherGroupItem(
                                highlighted = isCurrent,
                                onClick = {
                                    if (isListening && isHost) {
                                        playerViewModel.hostPlay(room.playlist, index)
                                    } else if (isListening && !effectiveHostLocked) {
                                        Together.playNext(track)
                                    } else if (!isListening) {
                                        Together.startListening(normalizedCode, index)
                                    }
                                },
                            ) {
                                ExpressiveTrackRow(
                                    track = track,
                                    index = index,
                                ) {
                                    if (isListening && (isHost || !effectiveHostLocked)) {
                                        IconButton(
                                            onClick = { Together.playNext(track) },
                                            shapes = IconButtonDefaults.shapes(),
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Rounded.PlaylistPlay,
                                                str("together_play_next"),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (!showAllPlaylistTracks && room.playlist.size > 25) {
                            TogetherGroupItem(onClick = onShowAllPlaylistTracks) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 14.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = str("together_show_all_tracks", room.playlist.size),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = cs.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        RoomSectionTab.SUGGESTIONS -> {
            item {
                val cs = MaterialTheme.colorScheme
                TogetherGroupSection(title = str("together_suggestions")) {
                    room.suggestions.forEach { suggestion ->
                        TogetherGroupItem {
                            ExpressiveTrackRow(
                                track = suggestion.track,
                                subtitle = str("together_suggested_by", suggestion.byName),
                            ) {
                                IconButton(
                                    onClick = { Together.listenToSuggestion(normalizedCode, suggestion) },
                                    shapes = IconButtonDefaults.shapes(),
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, str("together_preview"))
                                }
                                FilledTonalIconButton(
                                    onClick = { Together.acceptSuggestion(normalizedCode, suggestion) },
                                    shapes = IconButtonDefaults.shapes(),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = cs.primaryContainer,
                                        contentColor = cs.onPrimaryContainer,
                                    ),
                                ) {
                                    Icon(Icons.Rounded.Check, str("together_accept"))
                                }
                                IconButton(
                                    onClick = { Together.declineSuggestion(normalizedCode, suggestion) },
                                    shapes = IconButtonDefaults.shapes(),
                                ) {
                                    Icon(Icons.Rounded.Close, str("together_decline"))
                                }
                            }
                        }
                    }
                }
            }
        }

        RoomSectionTab.ADD -> {
            item {
                val cs = MaterialTheme.colorScheme
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (isSearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else if (query.isNotBlank()) {
                                IconButton(onClick = onClearSearch) {
                                    Icon(Icons.Rounded.Close, contentDescription = null)
                                }
                            }
                        },
                        placeholder = {
                            Text(
                                str("search_hint"),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(22.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onTriggerSearch() }),
                        modifier = Modifier.fillMaxWidth().trackTextInput(),
                    )

                    if (results.isNotEmpty()) {
                        TogetherGroupSection(title = str("together_add_tracks")) {
                            results.forEach { track ->
                                val shared = SharedTrack.of(track)
                                TogetherGroupItem {
                                    ExpressiveTrackRow(track = shared) {
                                        if (isListening && (isHost || !effectiveHostLocked)) {
                                            IconButton(
                                                onClick = { Together.playNext(shared) },
                                                shapes = IconButtonDefaults.shapes(),
                                            ) {
                                                Icon(
                                                    Icons.AutoMirrored.Rounded.PlaylistPlay,
                                                    str("together_play_next"),
                                                )
                                            }
                                        }
                                        FilledTonalIconButton(
                                            onClick = { Together.suggest(normalizedCode, shared) },
                                            shapes = IconButtonDefaults.shapes(),
                                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                containerColor = cs.primaryContainer,
                                                contentColor = cs.onPrimaryContainer,
                                            ),
                                        ) {
                                            Icon(Icons.Rounded.Add, str("together_suggest"))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomExpressiveHeroCard(
    room: Together.Room,
    isListening: Boolean,
    isHost: Boolean,
    isHostLocked: Boolean,
    onCopyCode: () -> Unit,
    onShareLink: () -> Unit,
    onToggleListen: () -> Unit,
    onToggleLock: () -> Unit,
    onClaimHost: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val isLive = isListening || room.hasLiveHost || room.listeners.isNotEmpty()
    val now = room.nowPlaying

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = cs.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ExpressiveCookieBadge(
                        isActive = isLive,
                        size = 42.dp,
                        activeColor = cs.primary,
                        inactiveColor = cs.secondaryContainer,
                    ) {
                        if (isLive && room.isPlaying) {
                            ListeningBars(
                                isPlaying = true,
                                modifier = Modifier.size(16.dp),
                                color = cs.onPrimary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Podcasts,
                                contentDescription = null,
                                tint = if (isLive) cs.onPrimary else cs.onSecondaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Column {
                        Text(
                            text = if (isLive) str("together_live_badge") else str("together_title").uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isLive) cs.primary else cs.onSurfaceVariant,
                        )
                        val listenerTotal = room.listeners.size +
                            if (isListening && room.listeners.none { it.id == Together.memberId }) 1 else 0
                        if (listenerTotal > 0) {
                            Text(
                                text = "$listenerTotal",
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant,
                            )
                        }
                    }
                }

                Surface(
                    onClick = onCopyCode,
                    shape = CircleShape,
                    color = cs.secondaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = TogetherWire.displayCode(room.code),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = cs.onSecondaryContainer,
                        )
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = str("together_copy_code"),
                            tint = cs.onSecondaryContainer,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
                TogetherInviteQrButton(room.code)
            }

            if (isListening) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TogetherReactionButton(roomCode = room.code)
                    TogetherSyncBadge(roomCode = room.code)
                }
            }

            if (now != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(cs.surfaceContainerHigh)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        TogetherArtwork(
                            url = now.artworkUrl,
                            isLive = room.isPlaying,
                            size = 76.dp,
                            cornerRadius = 18.dp,
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = now.title ?: str("untitled_track"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = cs.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = now.artist.orEmpty().ifBlank { str("unknown_artist") },
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    TogetherLiveProgressBar(room = room)
                }
            }

            val myAvatarUrl by Together.myAvatarUrlFlow.collectAsState()
            val allMembers = remember(room.listeners, isHost, isListening, room.hostId, myAvatarUrl) {
                buildList {
                    if (isHost || isListening) {
                        add(Together.Member(Together.memberId, Together.myName, true, System.currentTimeMillis(), myAvatarUrl))
                    }
                    room.listeners.filterNot { it.id == Together.memberId }.forEach { add(it) }
                }
            }
            if (allMembers.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    allMembers.forEach { member ->
                        TogetherMemberPill(
                            name = member.name,
                            isHost = member.id == room.hostId || (isHost && member.id == Together.memberId),
                            isMe = member.id == Together.memberId,
                            avatarUrl = if (member.id == Together.memberId) (myAvatarUrl ?: member.avatarUrl) else member.avatarUrl,
                        )
                    }
                }
            } else {
                Text(
                    text = str("together_nobody"),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }

            if (isHost) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(cs.surfaceContainerHigh)
                        .clickable(onClick = onToggleLock)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TogetherIconBadge(
                        icon = if (isHostLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                        containerColor = if (isHostLocked) cs.primaryContainer else cs.secondaryContainer,
                        contentColor = if (isHostLocked) cs.onPrimaryContainer else cs.onSecondaryContainer,
                        size = 36.dp,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = str("together_lock_controls"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = str("together_lock_controls_desc"),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Switch(
                        checked = isHostLocked,
                        onCheckedChange = { onToggleLock() },
                        thumbContent = if (isHostLocked) {
                            { Icon(Icons.Rounded.Check, null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                        } else null,
                    )
                }
            } else if (isListening) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(cs.secondaryContainer)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = if (isHostLocked) Icons.Rounded.Lock else Icons.Rounded.Headphones,
                            contentDescription = null,
                            tint = cs.onSecondaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = str(if (isHostLocked) "together_listen_only_badge" else "together_automix_host_only"),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSecondaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        if (!isHostLocked) {
                            Surface(
                                onClick = onClaimHost,
                                shape = CircleShape,
                                color = cs.surface,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(Icons.Rounded.Star, null, tint = cs.primary, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = str("together_become_host"),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isListening) {
                    FilledTonalButton(
                        onClick = onToggleListen,
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = cs.errorContainer,
                            contentColor = cs.onErrorContainer,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(str("together_leave"), fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onToggleListen,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                    ) {
                        Icon(Icons.Rounded.Headphones, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(str("together_listen"), fontWeight = FontWeight.Bold)
                    }
                }

                FilledTonalButton(
                    onClick = onShareLink,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.height(50.dp),
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(str("together_share_link"), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ExpressiveTrackRow(
    track: SharedTrack,
    index: Int? = null,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            Text(
                text = (index + 1).toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = cs.onSurfaceVariant,
                modifier = Modifier.width(28.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        TogetherArtwork(
            url = track.artworkUrl,
            isLive = false,
            size = 48.dp,
            cornerRadius = 12.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle ?: track.artist.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        track.durationMs?.takeIf { it > 0 }?.let { ms ->
            Text(
                text = formatTrackClock(ms),
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            actions()
        }
    }
}
