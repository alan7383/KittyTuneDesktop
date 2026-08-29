package com.alananasss.kittytune.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.EscapableAlertDialog
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.sync.KnownDevice
import com.alananasss.kittytune.data.sync.SyncClient
import com.alananasss.kittytune.data.sync.SyncDiscovery
import com.alananasss.kittytune.data.sync.SyncLog
import com.alananasss.kittytune.data.sync.SyncPeers
import com.alananasss.kittytune.data.sync.SyncScheduler
import com.alananasss.kittytune.data.sync.SyncService
import com.alananasss.kittytune.ui.common.QrCode
import com.alananasss.kittytune.ui.common.ScrollableColumn
import com.alananasss.kittytune.ui.common.SettingsGroupTitle
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.common.getSettingsShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pairing devices, and seeing that it is working (issue #33).
 *
 * The screen this replaces exposed the whole mechanism: a listener switch, a port, an address, a
 * regenerate button, a raw code, a paste field, and two separate lists of devices — fifteen controls for
 * a feature whose entire job is to need no attention. Reading it, you could not tell whether sync was
 * working, and the one thing you had to do (turn the listener on) looked like an advanced option.
 *
 * So the order is inverted. The state comes first and in a sentence — in step, or not, and with what.
 * There is one action, *pair a device*, which turns the listener on itself rather than asking. Everything
 * that is a mechanism rather than a decision is behind **Advanced**, where it can be found when something
 * has gone wrong and ignored the rest of the time.
 */
@Composable
fun SyncSettingsScreen(onBackClick: (() -> Unit)? = null) {
    val scope = rememberCoroutineScope()
    val scrollState = com.alananasss.kittytune.ui.common.rememberRestorableScrollState()

    var devices by remember { mutableStateOf(SyncPeers.all()) }
    var pairing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var showAdvanced by remember { mutableStateOf(false) }

    val isSyncing by SyncScheduler.isSyncing.collectAsState()
    val lastSyncAtMs by SyncScheduler.lastSyncAtMs.collectAsState()

    // Re-read after anything that could have changed the list, including an exchange a paired phone
    // started on its own while this screen was open.
    LaunchedEffect(lastSyncAtMs, pairing) { devices = SyncPeers.all() }

    if (pairing) {
        PairDeviceDialog(
            onDismiss = {
                pairing = false
                devices = SyncPeers.all()
            },
            onPaired = { name ->
                pairing = false
                devices = SyncPeers.all()
                status = str("sync_paired_with", name)
            },
        )
    }

    SettingsScaffold(
        title = str("sync_title"),
        onBackClick = onBackClick,
        scrollState = scrollState,
    ) { padding ->
        ScrollableColumn(
            state = scrollState,
            modifier = Modifier.fillMaxWidth().padding(padding),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // The one sentence worth keeping from the old screen: it is the answer to "where does my
                // listening history go", and no arrangement of controls says it.
                Text(
                    str("sync_intro"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )

                StatusCard(
                    devices = devices,
                    isSyncing = isSyncing,
                    onSyncNow = {
                        scope.launch {
                            SyncScheduler.syncAll("button")
                            devices = SyncPeers.all()
                            status = str("sync_all_done")
                        }
                    },
                )

                // Sized to its label rather than to the window. Pairing happens once per device, so a
                // full-width slab overstated it next to the card that actually carries the state.
                Button(
                    onClick = { pairing = true },
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(str("sync_pair_device"))
                }

                status?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                if (devices.isNotEmpty()) {
                    SettingsGroupTitle(str("sync_devices"))
                    devices.forEachIndexed { index, device ->
                        DeviceRow(
                            device = device,
                            shape = getSettingsShape(devices.size, index),
                            onForget = {
                                SyncPeers.forget(device.deviceId)
                                devices = SyncPeers.all()
                            },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { showAdvanced = !showAdvanced }) {
                    Icon(
                        if (showAdvanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(str("sync_advanced"))
                }
                AnimatedVisibility(visible = showAdvanced) {
                    AdvancedSection(
                        onForgetAll = {
                            SyncPeers.forgetAll()
                            devices = SyncPeers.all()
                        },
                        onStatus = { status = it },
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/**
 * The answer to "is this working?", in one card.
 *
 * Deliberately a sentence rather than a set of fields. What was wrong before was not that the address and
 * the port were hidden — it is that they were the first thing on the screen, and neither of them answers
 * the only question anyone opens this page with.
 */
@Composable
private fun StatusCard(
    devices: List<KnownDevice>,
    isSyncing: Boolean,
    onSyncNow: () -> Unit,
) {
    val paired = devices.isNotEmpty()
    val lastSynced = devices.mapNotNull { it.lastSyncedAtMs.takeIf { at -> at > 0 } }.maxOrNull()

    // A plain card with the accent on the icon, not a saturated slab.
    //
    // This was a `primaryContainer` plate, and under a theme whose primary and primaryContainer are both
    // the same bright colour — a yellow dynamic scheme, say — the filled button inside it disappeared: two
    // near-identical fills with the label floating between them. Nothing about the state needs a full block
    // of colour to be legible, and the card now uses the same container as every other card on the page, so
    // a normal primary button on top of it has maximum contrast in every theme rather than in most of them.
    val onContainer = MaterialTheme.colorScheme.onSurface
    val onContainerMuted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = if (paired) MaterialTheme.colorScheme.primary else onContainerMuted

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = onContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    // The one spot of accent: coloured when in step, grey when there is nothing to be in
                    // step with. It carries the state without shouting it.
                    Icon(Icons.Rounded.Sync, null, modifier = Modifier.size(20.dp), tint = accent)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    when {
                        isSyncing -> str("sync_state_syncing")
                        !paired -> str("sync_state_not_paired")
                        lastSynced == null -> str("sync_state_never")
                        else -> str("sync_state_in_step")
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = onContainer,
                )
            }
            Text(
                when {
                    !paired -> str("sync_state_not_paired_sub")
                    lastSynced == null -> str("sync_state_never_sub")
                    else -> str("sync_last_synced", agoLabel(lastSynced))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = onContainerMuted,
            )
            if (paired) {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val canDial = devices.any { it.canDial }
                    Button(
                        onClick = onSyncNow,
                        enabled = !isSyncing && canDial,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(str("sync_now"))
                    }
                    if (!canDial) {
                        Spacer(Modifier.width(10.dp))
                        // Honest rather than a button that does nothing: some devices can only call in.
                        Text(
                            str("sync_only_inbound"),
                            style = MaterialTheme.typography.bodySmall,
                            color = onContainerMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: KnownDevice,
    shape: androidx.compose.ui.graphics.Shape,
    onForget: () -> Unit,
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (device.platform == "android") Icons.Rounded.PhoneAndroid
                    else Icons.Rounded.Computer,
                    null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    device.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    if (device.lastSyncedAtMs > 0) str("sync_last_synced", agoLabel(device.lastSyncedAtMs))
                    else str("sync_state_never_sub"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onForget) { Text(str("sync_forget_device")) }
        }
    }
}


/**
 * Pairing, as one gesture (issue #33).
 *
 * Three things used to be spread across the screen and had to be done in the right order: turn the
 * listener on, reveal or copy the code, then watch a device list for something to appear. All three
 * happen here. Opening this dialog starts the listener and makes the machine answer discovery, because
 * that is what "I am pairing right now" means; closing it puts the discovery beacon back to silent.
 *
 * It also notices success by itself. The phone completes the pairing by calling in, so the desktop has no
 * return value to wait on — it watches for a device it did not know before, which is exactly the event the
 * old screen made the reader look for by hand.
 */
@Composable
private fun PairDeviceDialog(
    onDismiss: () -> Unit,
    onPaired: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    // Built eagerly rather than in the effect below, so the QR is drawn on the first frame instead of
    // appearing blank and then filling in.
    val code = remember { SyncService.pairingCode() }
    var revealed by remember { mutableStateOf(false) }
    var pasteMode by remember { mutableStateOf(false) }
    var peerCode by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // The listener has to be up before the code is shown, or the phone scans something that cannot be
    // answered and reports a connection failure that says nothing about a switch being off.
    DisposableEffect(Unit) {
        SyncService.isListenerEnabled = true
        SyncDiscovery.isAdvertising = true
        onDispose { SyncDiscovery.isAdvertising = false }
    }

    // Success arrives as an inbound call, not as a return value. Poll for a device we did not have when
    // the dialog opened; a second is well below the time it takes to point a phone at a screen.
    val knownAtOpen = remember { SyncPeers.all().map { it.deviceId }.toSet() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            val fresh = SyncPeers.all().firstOrNull { it.deviceId !in knownAtOpen }
            if (fresh != null) {
                onPaired(fresh.label)
                return@LaunchedEffect
            }
        }
    }

    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str("sync_pair_device"), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    str("sync_pair_instructions"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    // A light plate rather than the theme's surface: a scanner needs the contrast, and
                    // this is the one place where reliability beats blending in.
                    color = Color.White,
                    modifier = Modifier.align(Alignment.CenterHorizontally).size(230.dp),
                ) {
                    QrCode(
                        content = code,
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        foreground = Color(0xFF111318),
                        background = Color.White,
                    )
                }

                Text(
                    str("sync_waiting_for_device"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(code))
                    }) {
                        Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(str("sync_copy_code"))
                    }
                    TextButton(onClick = { revealed = !revealed }) {
                        Text(str(if (revealed) "sync_hide_code" else "sync_show_code"))
                    }
                }
                if (revealed) {
                    Text(
                        code,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // For pairing with another computer, where there is no camera in the loop. Folded away
                // because it is the rarer half.
                TextButton(onClick = { pasteMode = !pasteMode }) {
                    Text(str("sync_have_a_code"))
                }
                if (pasteMode) {
                    OutlinedTextField(
                        value = peerCode,
                        onValueChange = { peerCode = it; error = null },
                        label = { Text(str("sync_peer_code")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            val peer = SyncService.parsePairingCode(peerCode)
                            if (peer == null) {
                                error = str("sync_bad_code")
                                return@Button
                            }
                            busy = true
                            scope.launch {
                                val result = SyncClient.exchange(peer)
                                busy = false
                                when (result) {
                                    is SyncClient.Result.Success -> {
                                        SyncScheduler.start()
                                        onPaired(result.peerName)
                                    }

                                    SyncClient.Result.Unauthorized -> error = str("sync_unauthorized")
                                    is SyncClient.Result.Failed ->
                                        error = str("sync_failed", result.reason)
                                }
                            }
                        },
                        enabled = !busy && peerCode.isNotBlank(),
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(str("sync_pair"))
                    }
                    if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(str("btn_close")) }
        },
    )
}

/**
 * The mechanism, for when something has gone wrong.
 *
 * Nothing here is needed to use sync. It is here so that a firewall, a clashing port or a code that was
 * regenerated on the other device can be dealt with — and nowhere near the parts that are used daily.
 */
@Composable
private fun AdvancedSection(
    onForgetAll: () -> Unit,
    onStatus: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var deviceName by remember { mutableStateOf(SyncLog.deviceName) }
    var listenerOn by remember { mutableStateOf(SyncService.isListenerEnabled) }
    var generation by remember { mutableStateOf(0) }
    // Keyed on the generation, so replacing the secret rebuilds the code instead of showing the old one —
    // which is what made regenerating appear to break pairing rather than fix it (issue #33).
    val code = remember(generation) { SyncService.pairingCode() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = deviceName,
            onValueChange = {
                deviceName = it
                SyncLog.deviceName = it
            },
            label = { Text(str("sync_device_name")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // Their own column, tight. [getSettingsShape] gives a group large outer corners and small inner
        // ones so the rows read as one block — which only works if they touch. Inheriting the section's
        // 8 dp spacing pulled them apart, leaving three cards with mismatched corners that looked like a
        // layout fault rather than a group (issue #33).
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SettingsItem(
                shape = getSettingsShape(3, 0),
                title = str("sync_listener"),
                subtitle = str("sync_listener_sub"),
                hasSwitch = true,
                switchState = listenerOn,
                onSwitchChange = {
                    listenerOn = it
                    SyncService.isListenerEnabled = it
                },
            )
            SettingsItem(
                shape = getSettingsShape(3, 1),
                title = str("sync_address"),
                subtitle = "${SyncService.localAddress()}:${SyncService.port}",
            )
            SettingsItem(
                shape = getSettingsShape(3, 2),
                title = str("sync_events_held_title"),
                subtitle = str("sync_events_held", SyncLog.size()),
            )
        }

        Text(
            str("sync_firewall_hint", SyncService.port),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(code))
                onStatus(str("sync_code_copied"))
            }) {
                Text(str("sync_copy_code"))
            }
            TextButton(onClick = {
                SyncService.regeneratePairingSecret()
                // Every device paired with the old secret is locked out now, so their entries go too
                // rather than sitting in the list failing silently.
                onForgetAll()
                generation++
                onStatus(str("sync_code_regenerated"))
            }) {
                Text(str("sync_regenerate_code"))
            }
        }
    }
}

/**
 * "just now", "3 min", "2 h", "yesterday" — how long ago something happened.
 *
 * Deliberately coarse: the useful question is "is this still happening?", and a timestamp to the second
 * invites staring at it.
 */
private fun agoLabel(atMs: Long): String {
    val elapsed = (System.currentTimeMillis() - atMs).coerceAtLeast(0L)
    val minutes = elapsed / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> str("sync_just_now")
        minutes < 60 -> "$minutes min"
        hours < 24 -> "$hours h"
        days == 1L -> str("sync_yesterday")
        else -> str("sync_days_ago", days)
    }
}
