@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.alananasss.kittytune.ui.together

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alananasss.kittytune.core.Toaster
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.core.trackTextInput
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.together.Together
import com.alananasss.kittytune.data.together.TogetherLinks
import com.alananasss.kittytune.data.together.TogetherWire
import com.alananasss.kittytune.ui.player.PlayerViewModel

@Composable
fun LiveListenDialog(
    playerViewModel: PlayerViewModel,
    onOpenRoom: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val prefs = remember { PlayerPreferences() }
    val activeCode by Together.active.collectAsState()
    val rooms by Together.rooms.collectAsState()
    val isHostLocked by Together.isHostLocked.collectAsState()
    var discordListenAlong by remember { mutableStateOf(prefs.getDiscordListenAlongEnabled()) }
    var joinInput by remember { mutableStateOf("") }

    val activeRoom = activeCode?.let { rooms[it] ?: Together.Room(it, "") }
    val isHost = activeCode?.let { Together.isHostOf(it) } == true
    val currentTrack = playerViewModel.uiCurrentTrack

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 380.dp, max = 520.dp)
                .heightIn(max = 680.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(32.dp),
            color = cs.surfaceContainer,
            tonalElevation = 6.dp,
            shadowElevation = 20.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ExpressiveCookieBadge(
                        isActive = activeCode != null,
                        size = 56.dp,
                        activeColor = cs.primary,
                        inactiveColor = cs.secondaryContainer,
                    ) {
                        if (activeCode != null) {
                            ListeningBars(
                                isPlaying = true,
                                modifier = Modifier.size(22.dp),
                                color = cs.onPrimary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Podcasts,
                                contentDescription = null,
                                tint = cs.onSecondaryContainer,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = str("together_live_title"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = if (activeRoom != null) {
                                val listeners = activeRoom.listeners.map { it.name }
                                if (listeners.isEmpty()) {
                                    str("together_code", TogetherWire.displayCode(activeRoom.code))
                                } else {
                                    str("together_listening_now", listeners.joinToString(", "))
                                }
                            } else {
                                str("together_live_subtitle")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                    FilledTonalIconButton(
                        onClick = onDismissRequest,
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = cs.surfaceContainerHigh,
                            contentColor = cs.onSurfaceVariant,
                        ),
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = str("btn_cancel"))
                    }
                }

                if (activeCode == null) {
                    if (currentTrack != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(cs.surfaceContainerHigh)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            TogetherArtwork(
                                url = currentTrack.fullResArtwork,
                                isLive = false,
                                size = 52.dp,
                                cornerRadius = 14.dp,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentTrack.title ?: str("untitled_track"),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = currentTrack.displayArtist.ifBlank { str("unknown_artist") },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { playerViewModel.startLiveListenAlong() },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        Icon(Icons.Rounded.Podcasts, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(str("together_start_live"), fontWeight = FontWeight.Bold)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(cs.surfaceContainerHigh)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = str("together_join"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        val submitJoin = {
                            val joined = Together.joinAndListen(joinInput)
                            if (joined != null) {
                                joinInput = ""
                                onDismissRequest()
                                onOpenRoom(joined.code)
                            } else {
                                Toaster.show(str("together_bad_code"))
                            }
                        }
                        OutlinedTextField(
                            value = joinInput,
                            onValueChange = { joinInput = it },
                            placeholder = {
                                Text(
                                    str("together_link_or_code_hint"),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submitJoin() }),
                            modifier = Modifier.fillMaxWidth().trackTextInput(),
                        )
                        FilledTonalButton(
                            onClick = { submitJoin() },
                            enabled = joinInput.isNotBlank(),
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Groups, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(str("together_listen"))
                        }
                    }
                } else {
                    val code = activeCode!!
                    val inviteUrl = remember(code) { TogetherLinks.inviteUrl(code) }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(26.dp))
                            .background(cs.surfaceContainerHigh)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            onClick = { TogetherLinks.copyCode(code) },
                            shape = CircleShape,
                            color = cs.primaryContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Link,
                                    contentDescription = null,
                                    tint = cs.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = TogetherWire.displayCode(code),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = cs.onPrimaryContainer,
                                )
                            }
                        }
                        Text(
                            text = inviteUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = { TogetherLinks.copyShareMessage(code) },
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(str("together_share_link"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            FilledTonalButton(
                                onClick = { TogetherLinks.copyInviteLink(code) },
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(str("together_copy_link"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }

                    if (isHost) {
                        TogetherGroupSection {
                            TogetherGroupItem(
                                onClick = { Together.setHostLocked(!isHostLocked) },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    TogetherIconBadge(icon = Icons.Rounded.Lock)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = str("together_lock_controls"),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = str("together_lock_controls_desc"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = cs.onSurfaceVariant,
                                        )
                                    }
                                    Switch(
                                        checked = isHostLocked,
                                        onCheckedChange = { Together.setHostLocked(it) },
                                        thumbContent = if (isHostLocked) {
                                            { Icon(Icons.Rounded.Check, null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                                        } else null,
                                    )
                                }
                            }

                            TogetherGroupItem(
                                onClick = {
                                    val next = !discordListenAlong
                                    discordListenAlong = next
                                    prefs.setDiscordListenAlongEnabled(next)
                                    playerViewModel.updateDiscordPresence()
                                },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    TogetherIconBadge(icon = Icons.Rounded.Headphones)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = str("discord_listen_along_title"),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = str("discord_listen_along_desc"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = cs.onSurfaceVariant,
                                        )
                                    }
                                    Switch(
                                        checked = discordListenAlong,
                                        onCheckedChange = {
                                            discordListenAlong = it
                                            prefs.setDiscordListenAlongEnabled(it)
                                            playerViewModel.updateDiscordPresence()
                                        },
                                        thumbContent = if (discordListenAlong) {
                                            { Icon(Icons.Rounded.Check, null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                                        } else null,
                                    )
                                }
                            }

                            if (code == Together.personalLiveCode) {
                                TogetherGroupItem(
                                    onClick = {
                                        val fresh = Together.regeneratePersonalLiveCode()
                                        TogetherLinks.copyInviteLink(fresh)
                                        playerViewModel.updateDiscordPresence()
                                    },
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        TogetherIconBadge(icon = Icons.Rounded.Refresh)
                                        Text(
                                            text = str("together_reset_link"),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    } else if (activeRoom?.hostLocked == true) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = cs.secondaryContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = cs.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = str("together_listen_only_badge"),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = cs.onSecondaryContainer,
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilledTonalButton(
                            onClick = {
                                onDismissRequest()
                                onOpenRoom(code)
                            },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(str("together_open_room"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = { Together.stopListening() },
                            shapes = ButtonDefaults.shapes(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.error),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.StopCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                str(if (isHost) "together_stop_live" else "together_leave"),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TogetherInviteDialog(
    code: String,
    onJoinAndListen: (String) -> Unit,
    onOpenRoomOnly: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val normalized = TogetherWire.normalizeCode(code)
    val rooms by Together.rooms.collectAsState()
    val room = rooms[normalized] ?: Together.Room(normalized, "")
    val cs = MaterialTheme.colorScheme

    DisposableEffect(normalized) {
        Together.previewRoom(normalized)
        onDispose {
            Together.closePreview(normalized)
        }
    }

    val hostName = room.hostDisplayName
        ?: room.name.substringBefore("·").trim().takeIf { it.isNotBlank() }
    val isLive = room.hasLiveHost || room.listeners.isNotEmpty()

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 380.dp, max = 500.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(32.dp),
            color = cs.surfaceContainer,
            tonalElevation = 6.dp,
            shadowElevation = 20.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ExpressiveCookieBadge(
                    isActive = isLive,
                    size = 76.dp,
                    activeColor = cs.primary,
                    inactiveColor = cs.secondaryContainer,
                ) {
                    if (isLive && room.isPlaying) {
                        ListeningBars(
                            isPlaying = true,
                            modifier = Modifier.size(28.dp),
                            color = cs.onPrimary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Podcasts,
                            contentDescription = null,
                            tint = if (isLive) cs.onPrimary else cs.onSecondaryContainer,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = if (isLive) cs.primaryContainer else cs.surfaceContainerHighest,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = if (isLive) {
                                "${str("together_live_badge")} · ${TogetherWire.displayCode(normalized)}"
                            } else {
                                TogetherWire.displayCode(normalized)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isLive) cs.onPrimaryContainer else cs.onSurfaceVariant,
                        )
                        if (room.hostLocked) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = if (isLive) cs.onPrimaryContainer else cs.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }

                Text(
                    text = if (!hostName.isNullOrBlank()) {
                        str("together_invite_prompt_title", hostName)
                    } else {
                        str("together_invite_prompt_generic")
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    color = cs.onSurface,
                )

                Text(
                    text = str("together_invite_prompt_desc"),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = cs.onSurfaceVariant,
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(cs.surfaceContainerHigh)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val now = room.nowPlaying
                    if (now != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            TogetherArtwork(
                                url = now.artworkUrl,
                                isLive = room.isPlaying,
                                size = 64.dp,
                                cornerRadius = 16.dp,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = now.title ?: str("untitled_track"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
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
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            TogetherIconBadge(icon = Icons.Rounded.Headphones)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = room.name.ifBlank { str("together_title") },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = str("together_invite_connecting"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    if (room.listeners.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            room.listeners.forEach { member ->
                                TogetherMemberPill(
                                    name = member.name,
                                    isHost = member.id == room.hostId,
                                    isMe = member.id == Together.memberId,
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = { onJoinAndListen(normalized) },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                ) {
                    Icon(Icons.Rounded.Headphones, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = str("together_listen"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilledTonalButton(
                        onClick = { onOpenRoomOnly(normalized) },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(str("together_invite_view_only"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = onDismissRequest,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.height(46.dp),
                    ) {
                        Text(str("btn_cancel"))
                    }
                }
            }
        }
    }
}
