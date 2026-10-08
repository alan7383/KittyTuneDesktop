package com.alananasss.kittytune.ui.together

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.Toaster
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.core.trackTextInput
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.data.together.SharedTrack
import com.alananasss.kittytune.data.together.Together
import com.alananasss.kittytune.data.together.TogetherWire
import com.alananasss.kittytune.ui.player.PlayerViewModel
import kotlinx.coroutines.launch

/**
 * The shared playlists this listener has, and the ways to make or join one (issue #66). Each card says who is
 * listening right now, live, so a friend already playing is seen from here.
 */
@Composable
fun TogetherHomeScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val saved by Together.saved.collectAsState()
    val rooms by Together.rooms.collectAsState()
    var newName by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }
    var myName by remember { mutableStateOf(Together.myName) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, str("btn_back")) }
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.Groups, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Text(str("together_title"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                ActionCard(
                    title = str("together_create"),
                    icon = Icons.Rounded.Add,
                    value = newName,
                    onValueChange = { newName = it },
                    hint = str("together_name_hint"),
                    button = str("together_create"),
                    modifier = Modifier.weight(1f),
                ) {
                    val created = Together.create(newName)
                    newName = ""
                    copyCode(created.code)
                    onOpen(created.code)
                }
                ActionCard(
                    title = str("together_join"),
                    icon = Icons.Rounded.Login,
                    value = joinCode,
                    onValueChange = { joinCode = it.uppercase().take(12) },
                    hint = str("together_code_hint"),
                    button = str("together_join"),
                    modifier = Modifier.weight(1f),
                ) {
                    Together.join(joinCode)?.let {
                        joinCode = ""
                        onOpen(it.code)
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = myName,
                onValueChange = { myName = it; Together.myName = it },
                label = { Text(str("together_your_name")) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.widthIn(max = 360.dp).trackTextInput(),
            )
        }
        items(saved, key = { it.code }) { entry ->
            val room = rooms[entry.code]
            SavedRoomCard(
                name = room?.name?.ifBlank { entry.name }?.ifBlank { str("together_title") } ?: entry.name.ifBlank { str("together_title") },
                code = entry.code,
                listeners = room?.listeners?.map { it.name }.orEmpty(),
                cover = room?.nowPlaying?.artworkUrl ?: entry.tracks.firstOrNull()?.artworkUrl,
                onClick = { onOpen(entry.code) },
            )
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    button: String,
    modifier: Modifier = Modifier,
    onAction: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                }
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(hint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAction() }),
                modifier = Modifier.fillMaxWidth().trackTextInput(),
            )
            Button(onClick = onAction, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text(button) }
        }
    }
}

@Composable
private fun SavedRoomCard(name: String, code: String, listeners: List<String>, cover: String?, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            TogetherCover(cover, isLive = listeners.isNotEmpty(), size = 64)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (listeners.isEmpty()) str("together_nobody") else str("together_listening_now", listeners.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (listeners.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(str("together_code", TogetherWire.displayCode(code)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            if (listeners.isNotEmpty()) PlayingBars(Modifier.size(22.dp))
        }
    }
}

/** A shared playlist's cover: the song playing in it, on the two-person mark, with live bars over it. */
@Composable
private fun TogetherCover(url: String?, isLive: Boolean, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 4).dp))
            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(Icons.Rounded.Groups, null, tint = Color.White, modifier = Modifier.size((size / 2).dp))
        if (isLive && url != null) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
            PlayingBars(Modifier.size((size / 3).dp), color = Color.White)
        }
    }
}

/** Three bars moving out of step: someone is listening. */
@Composable
private fun PlayingBars(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val transition = rememberInfiniteTransition(label = "togetherBars")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        listOf(420, 560, 360).forEachIndexed { i, period ->
            val h by transition.animateFloat(0.25f, 1f, infiniteRepeatable(tween(period), RepeatMode.Reverse), label = "bar$i")
            Box(Modifier.weight(1f).fillMaxHeight(h).clip(RoundedCornerShape(1.dp)).background(color))
        }
    }
}

private fun copyCode(code: String) {
    val selection = java.awt.datatransfer.StringSelection(TogetherWire.displayCode(code))
    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
    Toaster.show(str("together_code_copied"))
}

/**
 * One shared playlist (issue #66): who is in it and who hosts, the song playing for everyone, the host's
 * suggestions to answer, what plays next for everyone, the playlist's own tracks, and a search to add more.
 */
@Composable
fun TogetherScreen(code: String, playerViewModel: PlayerViewModel, onBack: () -> Unit) {
    val rooms by Together.rooms.collectAsState()
    val active by Together.active.collectAsState()
    val connected by Together.isConnected.collectAsState()
    val room = rooms[code] ?: Together.Room(code, "")
    val isListening = active == code
    val isHost = room.hostId == Together.memberId
    var query by remember { mutableStateOf("") }
    val results = remember { mutableStateListOf<com.alananasss.kittytune.domain.Track>() }
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { RoomHeader(room, isListening, isHost, connected, playerViewModel, onBack) }

        if (isHost && room.suggestions.isNotEmpty()) {
            item { SectionTitle(str("together_suggestions")) }
            itemsIndexed(room.suggestions, key = { i, s -> "s$i-${s.track.id}" }) { _, s ->
                SharedTrackRow(s.track, subtitle = str("together_suggested_by", s.byName)) {
                    IconButton(onClick = { Together.listenToSuggestion(code, s) }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.PlayArrow, str("together_preview")) }
                    FilledTonalIconButton(onClick = { Together.acceptSuggestion(code, s) }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Check, str("together_accept")) }
                    IconButton(onClick = { Together.declineSuggestion(code, s) }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Close, str("together_decline")) }
                }
            }
        }

        if (room.upcoming.isNotEmpty() && room.hasLiveHost) {
            item { SectionTitle(str("together_up_next")) }
            itemsIndexed(room.upcoming.take(8), key = { i, t -> "u$i-${t.id}" }) { _, t -> SharedTrackRow(t) {} }
        }

        item { SectionTitle(str("together_tracks")) }
        if (room.playlist.isEmpty()) {
            item {
                Text(
                    str("together_empty"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
        itemsIndexed(room.playlist, key = { i, t -> "p$i-${t.id}" }) { index, t ->
            SharedTrackRow(t, onClick = {
                if (isListening && isHost) playerViewModel.hostPlay(room.playlist, index)
                else if (isListening) Together.playNext(t)
                else Together.startListening(code, index)
            }) {
                if (isListening) {
                    IconButton(onClick = { Together.playNext(t) }, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, str("together_play_next"))
                    }
                }
            }
        }

        item {
            SectionTitle(str("together_add_tracks"))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text(str("search_hint"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    scope.launch {
                        val found = runCatching { RetrofitClient.create().searchTracks(query, limit = 20).collection }.getOrDefault(emptyList())
                        results.clear(); results.addAll(found)
                    }
                }),
                modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 560.dp).fillMaxWidth().trackTextInput(),
            )
        }
        itemsIndexed(results, key = { i, t -> "r$i-${t.id}" }) { _, track ->
            val shared = SharedTrack.of(track)
            SharedTrackRow(shared) {
                if (isListening) {
                    IconButton(onClick = { Together.playNext(shared) }, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, str("together_play_next"))
                    }
                }
                FilledTonalIconButton(onClick = { Together.suggest(code, shared) }, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Add, str("together_suggest"))
                }
            }
        }
    }
}

@Composable
private fun RoomHeader(
    room: Together.Room,
    isListening: Boolean,
    isHost: Boolean,
    connected: Boolean,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(scheme.primaryContainer.copy(alpha = 0.7f), scheme.surfaceContainerLow)))
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, str("btn_back")) }
                Spacer(Modifier.weight(1f))
                if (!connected) Text(str("together_offline"), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                IconButton(onClick = { Together.forget(room.code); onBack() }, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Delete, str("together_forget"), tint = scheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TogetherCover(room.nowPlaying?.artworkUrl ?: room.playlist.firstOrNull()?.artworkUrl, isLive = room.listeners.isNotEmpty() || isListening, size = 120)
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(str("together_title").uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = scheme.primary)
                    Text(room.name.ifBlank { str("together_title") }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Surface(onClick = { copyCode(room.code) }, shape = CircleShape, color = scheme.surfaceContainerHighest, modifier = Modifier.padding(top = 6.dp)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(str("together_code", TogetherWire.displayCode(room.code)), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            MembersRow(room, isHost)
            AnimatedVisibility(visible = room.nowPlaying != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                room.nowPlaying?.let { now ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (room.isPlaying) PlayingBars(Modifier.size(18.dp)) else Icon(Icons.Rounded.Headphones, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            listOfNotNull(now.title, now.artist).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!isListening) {
                    Button(onClick = { Together.startListening(room.code) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.height(48.dp)) {
                        Icon(Icons.Rounded.Headphones, null)
                        Spacer(Modifier.width(8.dp))
                        Text(str("together_listen"), fontWeight = FontWeight.Bold)
                    }
                } else {
                    FilledTonalButton(onClick = { Together.stopListening() }, shapes = ButtonDefaults.shapes(), modifier = Modifier.height(48.dp)) {
                        Icon(Icons.Rounded.Logout, null)
                        Spacer(Modifier.width(8.dp))
                        Text(str("together_leave"))
                    }
                    if (!isHost) {
                        FilledTonalButton(onClick = { Together.claimHost(room.code) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.height(48.dp)) {
                            Icon(Icons.Rounded.Star, null)
                            Spacer(Modifier.width(8.dp))
                            Text(str("together_become_host"))
                        }
                    }
                }
                if (isListening && !isHost) {
                    Text(str("together_automix_host_only"), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MembersRow(room: Together.Room, isHost: Boolean) {
    val listeners = room.listeners
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (isHost) MemberChip(Together.myName, isHost = true, isMe = true)
        listeners.forEach { member -> MemberChip(member.name, isHost = member.id == room.hostId, isMe = false) }
        if (!isHost && listeners.isEmpty()) {
            Text(str("together_nobody"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MemberChip(name: String, isHost: Boolean, isMe: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = CircleShape, color = if (isHost) scheme.tertiaryContainer else scheme.surfaceContainerHighest) {
        Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(avatarColour(name)),
                contentAlignment = Alignment.Center,
            ) {
                Text(name.take(1).uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Text(name, style = MaterialTheme.typography.labelLarge, fontWeight = if (isMe) FontWeight.Bold else FontWeight.Medium)
            if (isHost) {
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.Star, str("together_host"), tint = scheme.tertiary, modifier = Modifier.size(16.dp).offset(y = (-1).dp))
            }
        }
    }
}

private fun avatarColour(name: String): Color {
    val palette = listOf(Color(0xFF7C3AED), Color(0xFF0EA5E9), Color(0xFFF97316), Color(0xFF10B981), Color(0xFFE11D48), Color(0xFFEAB308))
    return palette[Math.floorMod(name.hashCode(), palette.size)]
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
}

@Composable
private fun SharedTrackRow(
    track: SharedTrack,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    actions: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = track.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title.orEmpty(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle ?: track.artist.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}
