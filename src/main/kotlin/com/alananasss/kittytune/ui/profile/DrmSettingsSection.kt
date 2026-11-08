package com.alananasss.kittytune.ui.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.EscapableAlertDialog
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.core.trackTextInput
import com.alananasss.kittytune.data.StreamResolver
import com.alananasss.kittytune.data.drm.CdmSidecar
import com.alananasss.kittytune.data.drm.DrmSelfTest
import com.alananasss.kittytune.data.drm.NativeWidevineCdm
import com.alananasss.kittytune.data.drm.ProtectedBackend
import com.alananasss.kittytune.data.drm.WslSidecarSetup
import com.alananasss.kittytune.audio.WindowsPlayReadyRuntime
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsGroupTitle
import com.alananasss.kittytune.ui.common.SettingsItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.JFileChooser

/**
 * The page's name: Windows has no CDM to manage, so "Widevine CDM" would name something it never uses.
 * A getter, because the settings enum and the search index both read it.
 */
internal val drmSettingsTitleKey: String
    get() = if (WindowsPlayReadyRuntime.windows) "pref_drm_title_windows" else "pref_drm_title"

/**
 * Settings → protected SoundCloud tracks, whose page depends on who does the decrypting.
 *
 * Windows offers two methods ([WindowsDrmSection]): the OS PlayReady renderer, or Chrome's Linux
 * Widevine CDM in WSL, which the app sets up itself. Linux and macOS load the browser's Widevine CDM
 * ([WidevineDrmSection]).
 *
 * The first visit opens [DrmSetupWizard], which explains all of this and asks for the method.
 */
@Composable
fun DrmSettingsSection() {
    val prefs = remember { PlayerPreferences() }
    var wizard by remember {
        mutableStateOf(if (prefs.getDrmOnboardingDone()) null else DrmWizardStart.INTRO)
    }
    // Coming back after the reboot the WSL feature needed: reopen the assistant straight at the setup
    // step, which resumes by itself. Only when the feature is actually on now (the restart happened).
    LaunchedEffect(Unit) {
        if (WslSidecarSetup.pendingVersion() != null && WslSidecarSetup.pendingReady()) {
            wizard = DrmWizardStart.WSL
        }
    }
    // Bumped when the assistant closes: it may have changed the method or turned protected tracks
    // off, and the page's rows keep their own state.
    var generation by remember { mutableIntStateOf(0) }
    var uninstalling by remember { mutableStateOf(false) }

    key(generation) {
        if (WindowsPlayReadyRuntime.windows) {
            WindowsDrmSection(onOpenWizard = { wizard = it }, onOpenUninstall = { uninstalling = true })
        } else {
            WidevineDrmSection(onOpenWizard = { wizard = it })
        }
    }

    wizard?.let { start ->
        DrmSetupWizard(start = start, onClose = {
            wizard = null
            generation++
        })
    }
    if (uninstalling) {
        WslUninstallWizard(onClose = {
            uninstalling = false
            generation++
        })
    }
}

/**
 * Windows: pick the method, see where it stands, test it.
 *
 * WSL that is chosen but not (or no longer) answering is not an error the user caused: playback
 * quietly falls back to PlayReady meanwhile, and the page says so next to the way to fix it.
 */
@Composable
internal fun WindowsDrmSection(onOpenWizard: (DrmWizardStart) -> Unit, onOpenUninstall: () -> Unit = {}) {
    var backend by remember { mutableStateOf(ProtectedBackend.current) }
    // Null until checked: the first check extracts the renderer, which is file work.
    var playReady by remember { mutableStateOf<Boolean?>(null) }
    var detection by remember { mutableStateOf<WslSidecarSetup.Detection?>(null) }
    var wslConnected by remember { mutableStateOf(CdmSidecar.isInstalled) }
    var testing by remember { mutableStateOf(false) }
    var passed by remember { mutableStateOf<Boolean?>(null) }
    var report by remember { mutableStateOf<String?>(null) }
    var showLog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val ready = withContext(Dispatchers.IO) { WindowsPlayReadyRuntime.isAvailable }
        playReady = ready
        if (!ready && report == null) report = WindowsPlayReadyRuntime.failureReason
    }
    LaunchedEffect(backend) {
        if (backend != ProtectedBackend.WSL) return@LaunchedEffect
        detection = null
        val found = WslSidecarSetup.detect()
        detection = found
        // NOT_SET_UP too: a configuration written by scripts/setup-cdm-sidecar.ps1 points at a daemon
        // in the user's home rather than at ours, and works just as well.
        if (found.state == WslSidecarSetup.State.SET_UP || found.state == WslSidecarSetup.State.NOT_SET_UP) {
            wslConnected = withContext(Dispatchers.IO) { CdmSidecar.verify() }
            if (!wslConnected) report = CdmSidecar.lastFailure
        } else {
            wslConnected = CdmSidecar.isInstalled
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        DrmBanner(
            lines = listOf(str("pref_drm_windows_intro"), str("pref_drm_windows_beta")),
            beta = true,
        )

        SettingsGroup(
            title = str("pref_drm_group_playback"),
            items = listOf({ shape -> ProtectedTracksSwitch(shape) }),
        )

        // The method: a choice, so a button group; under it, what the chosen one does and where it
        // stands. Not rows: nothing in there is pressed except the group itself.
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            SettingsGroupTitle(str("pref_drm_group_method"))
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ExpressiveConnectedButtonGroup(
                        options = ProtectedBackend.entries,
                        selectedOption = backend,
                        onOptionSelected = { method ->
                            if (method != backend) {
                                ProtectedBackend.select(method)
                                backend = method
                                wslConnected = CdmSidecar.isInstalled
                                passed = null
                            }
                        },
                        fillMaxWidth = true,
                        uncheckedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        iconSpacing = 8.dp,
                        labelProvider = { Text(methodName(it), fontWeight = FontWeight.Bold) },
                        iconProvider = { Icon(methodIcon(it), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                    AnimatedContent(
                        targetState = backend,
                        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
                        label = "drm-method",
                    ) { method ->
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                MethodBadge(method, size = 40)
                                Text(
                                    methodTagline(method),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )
                                MethodInfoButton(method)
                            }
                            if (method == ProtectedBackend.PLAYREADY) {
                                PlayReadyStatus(playReady)
                            } else {
                                WslStatus(detection, wslConnected)
                            }
                            MethodPoints(method)
                        }
                    }
                }
            }
        }

        val testRow: @Composable (Shape) -> Unit = { shape ->
            val result = passed
            SettingsItem(
                shape = shape,
                title = str("pref_drm_test"),
                subtitle = when {
                    testing -> str("pref_drm_testing")
                    result == true -> str("pref_drm_windows_test_ok")
                    result == false -> str("pref_drm_windows_test_failed")
                    else -> str("pref_drm_test_desc")
                },
                icon = Icons.Rounded.PlayCircle,
                titleColor = if (result == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                enabled = !testing && if (backend == ProtectedBackend.WSL) wslConnected else playReady == true,
                trailingContent = if (testing) ({ DrmSpinner() }) else null,
                onClick = {
                    val widevine = backend == ProtectedBackend.WSL
                    val title = if (widevine) "WSL (${CdmSidecar.describe})" else "Windows PlayReady"
                    report = title
                    showLog = true
                    scope.launch {
                        testing = true
                        passed = null
                        val log = StringBuilder(title)
                        try {
                            passed = DrmSelfTest.run(widevine = widevine) { line ->
                                log.append('\n').append(line)
                                report = log.toString()
                            }
                        } finally {
                            testing = false
                        }
                    }
                },
            )
        }
        val wslSetupRow: @Composable (Shape) -> Unit = { shape ->
            SettingsItem(
                shape = shape,
                title = str(if (wslConnected) "pref_drm_wsl_redo_title" else "pref_drm_wsl_setup_title"),
                subtitle = str(if (wslConnected) "pref_drm_wsl_redo_desc" else "pref_drm_wsl_setup_desc"),
                icon = Icons.Rounded.Build,
                onClick = { onOpenWizard(DrmWizardStart.WSL) },
            )
        }
        // Offered whenever something of the WSL method may still be on the PC, including after switching
        // back to PlayReady: that is exactly when someone wants it gone.
        val hasWslLeftovers = remember(backend) { WslSidecarSetup.hasLeftovers() }
        val wslUninstallRow: @Composable (Shape) -> Unit = { shape ->
            SettingsItem(
                shape = shape,
                title = str("pref_drm_wsl_uninstall_title"),
                subtitle = str("pref_drm_wsl_uninstall_desc"),
                icon = Icons.Rounded.DeleteSweep,
                titleColor = MaterialTheme.colorScheme.error,
                onClick = onOpenUninstall,
            )
        }
        SettingsGroup(
            items = when {
                backend == ProtectedBackend.PLAYREADY -> listOf(testRow)
                wslConnected -> listOf(testRow, wslSetupRow)
                else -> listOf(wslSetupRow)
            } + (if (hasWslLeftovers) listOf(wslUninstallRow) else emptyList()) + drmLogRow(report) { showLog = true },
        )

        DrmHelpGroup(onOpenWizard, experimental = backend == ProtectedBackend.WSL)
    }

    val log = report
    if (showLog && log != null) {
        val result = passed
        DrmLogDialog(
            log = log,
            status = when {
                testing -> str("pref_drm_testing")
                result == true -> str("pref_drm_windows_test_ok")
                result == false -> str("pref_drm_windows_test_failed")
                else -> null
            },
            running = testing,
            failed = result != true,
            onDismiss = { showLog = false },
        )
    }
}

@Composable
private fun PlayReadyStatus(available: Boolean?) {
    when (available) {
        null -> DrmStatusLine(str("pref_drm_searching"), Icons.Rounded.Search, MaterialTheme.colorScheme.onSurfaceVariant)
        true -> DrmStatusLine(str("pref_drm_windows_ready"), Icons.Rounded.CheckCircle, MaterialTheme.colorScheme.primary)
        false -> DrmStatusLine(str("pref_drm_windows_unavailable"), Icons.Rounded.ErrorOutline, MaterialTheme.colorScheme.error)
    }
}

/** Where WSL stands, and, when it is not answering, that PlayReady covers for it meanwhile. */
@Composable
internal fun WslStatus(detection: WslSidecarSetup.Detection?, connected: Boolean) {
    val error = MaterialTheme.colorScheme.error
    when {
        connected -> DrmStatusLine(
            str("pref_drm_wsl_connected", WslSidecarSetup.DISTRO),
            Icons.Rounded.CheckCircle,
            MaterialTheme.colorScheme.primary,
        )
        detection == null -> DrmStatusLine(str("drm_wiz_wsl_checking"), Icons.Rounded.Search, MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val key = when (detection.state) {
                WslSidecarSetup.State.NO_WSL -> "drm_wiz_wsl_missing"
                WslSidecarSetup.State.FEATURE_OFF -> "drm_wiz_wsl_needs_enable"
                WslSidecarSetup.State.NOT_SET_UP -> "pref_drm_wsl_not_set_up"
                WslSidecarSetup.State.SET_UP -> "pref_drm_wsl_not_answering"
            }
            DrmStatusLine(str(key), Icons.Rounded.ErrorOutline, error)
            Text(
                str("pref_drm_wsl_fallback"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 28.dp),
            )
        }
    }
}

/**
 * The way back into the whole assistant, for whoever clicked through it too fast or chose the wrong
 * method, then the experimental notice with the link to the code (on Windows, only with WSL).
 */
@Composable
private fun DrmHelpGroup(onOpenWizard: (DrmWizardStart) -> Unit, experimental: Boolean) {
    SettingsGroup(
        title = str("pref_drm_group_help"),
        items = listOf({ shape ->
            SettingsItem(
                shape = shape,
                title = str("pref_drm_tour_title"),
                subtitle = str("pref_drm_tour_desc"),
                icon = Icons.Rounded.Replay,
                onClick = { onOpenWizard(DrmWizardStart.INTRO) },
            )
        }),
    )
    AnimatedVisibility(experimental, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        DrmExperimentalCard(
            windows = WindowsPlayReadyRuntime.windows,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/**
 * Linux and macOS: the Widevine CDM, so protected SoundCloud tracks can be unlocked.
 *
 * Two ways to find one, in the order the app itself uses: it looks in the places browsers leave a CDM
 * behind (Chrome, Chromium, Brave, Edge, Firefox, Opera, including Flatpak and Snap sandboxes), and the
 * user can point at a file when the CDM lives somewhere else (the Nix store, a second user's profile, a
 * custom install). The path is remembered, and "test" re-installs without restarting the app.
 *
 * The search runs when the page opens, not on the first protected track: nothing else loads a CDM
 * before one is played, so the page used to claim there was none on a machine that had one.
 */
@Composable
private fun WidevineDrmSection(onOpenWizard: (DrmWizardStart) -> Unit) {
    val prefs = remember { PlayerPreferences() }
    var path by remember { mutableStateOf(prefs.getWidevineCdmPath()) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusOk by remember { mutableStateOf(false) }
    var detected by remember { mutableStateOf(NativeWidevineCdm.currentCdmPath) }
    var searching by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val known = detected
        if (known != null) {
            // A CDM is already loaded (a protected track played): just show where it came from.
            if (path.isBlank()) path = known
            return@LaunchedEffect
        }
        searching = true
        try {
            var found = withContext(Dispatchers.IO) { searchOnce(deep = false) }
            if (found == null && !NativeWidevineCdm.fullScanDone) {
                // The quick pass only knows the browsers we listed; the machine may hold a CDM
                // somewhere else (Opera GX, Vivaldi, another user, a container).
                found = withContext(Dispatchers.IO) { searchOnce(deep = true) }
            }
            if (path.isBlank() && found != null) path = found
            detected = NativeWidevineCdm.currentCdmPath
        } finally {
            searching = false
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        DrmBanner(
            lines = listOf(str("pref_drm_no_browser"), str("pref_drm_hint")),
            status = {
                val known = detected
                DrmStatusLine(
                    text = known ?: if (searching) str("pref_drm_searching") else str("pref_drm_failed"),
                    icon = when {
                        known != null -> Icons.Rounded.CheckCircle
                        searching -> Icons.Rounded.Search
                        else -> Icons.Rounded.ErrorOutline
                    },
                    color = when {
                        known != null -> MaterialTheme.colorScheme.primary
                        searching -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.error
                    },
                )
            },
        )

        SettingsGroup(
            title = str("pref_drm_group_playback"),
            items = listOf({ shape -> ProtectedTracksSwitch(shape) }),
        )

        SettingsGroup(
            title = str("pref_drm_group_cdm"),
            items = listOf<@Composable (Shape) -> Unit>(
                { shape ->
                    Surface(
                        shape = shape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = path,
                            onValueChange = { path = it },
                            label = { Text(str("pref_drm_cdm_path")) },
                            placeholder = { Text("libwidevinecdm.so") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().padding(16.dp).trackTextInput(),
                        )
                    }
                },
                { shape ->
                    // Starts from a clean slate: a remembered path would win over the search and hand
                    // back the same file, which is the opposite of "look again".
                    SettingsItem(
                        shape = shape,
                        title = str("pref_drm_autodetect"),
                        subtitle = str("pref_drm_autodetect_desc"),
                        icon = Icons.Rounded.Search,
                        enabled = !searching,
                        onClick = {
                            // "Detect automatically" means the whole machine: the quick pass is only a
                            // head start, and whatever it does not find is looked for everywhere.
                            runCatching { prefs.setWidevineCdmPath("") }
                            path = ""
                            status = null
                            scope.launch {
                                searching = true
                                try {
                                    val found = withContext(Dispatchers.IO) { searchOnce(deep = false) }
                                        ?: withContext(Dispatchers.IO) { searchOnce(deep = true) }
                                    if (found != null) path = found
                                    val loaded = NativeWidevineCdm.isReady
                                    statusOk = loaded
                                    status = if (loaded) str("pref_drm_ok") else str("pref_drm_failed")
                                    detected = NativeWidevineCdm.currentCdmPath
                                    report = NativeWidevineCdm.diagnostics()
                                } finally {
                                    searching = false
                                }
                            }
                        },
                    )
                },
                { shape ->
                    SettingsItem(
                        shape = shape,
                        title = str("pref_drm_browse"),
                        subtitle = str("pref_drm_browse_desc"),
                        icon = Icons.Rounded.FolderOpen,
                        enabled = !searching,
                        onClick = {
                            runCatching {
                                val chooser = JFileChooser()
                                chooser.dialogTitle = str("pref_drm_cdm_path")
                                chooser.fileSelectionMode = JFileChooser.FILES_ONLY
                                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                    path = chooser.selectedFile?.absolutePath ?: path
                                }
                            }
                        },
                    )
                },
                { shape ->
                    val failed = status != null && !statusOk
                    SettingsItem(
                        shape = shape,
                        title = str("pref_drm_test"),
                        subtitle = status ?: str("pref_drm_test_desc"),
                        icon = Icons.Rounded.PlayCircle,
                        titleColor = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        enabled = !searching,
                        onClick = {
                            // Loading a CDM is file work and can wait on the library: never on the UI thread.
                            val requested = path
                            testing = true
                            report = report ?: ""
                            showLog = true
                            scope.launch {
                                searching = true
                                try {
                                    var found: String? = null
                                    val ok = withContext(Dispatchers.IO) {
                                        if (requested.isBlank()) {
                                            // An empty field means "find it": drop a remembered path first,
                                            // otherwise the search would just hand back the same file.
                                            runCatching { prefs.setWidevineCdmPath("") }
                                            found = NativeWidevineCdm.locateCdm()
                                            NativeWidevineCdm.retryDiscovery()
                                        } else {
                                            NativeWidevineCdm.applyCustomPath(requested)
                                        }
                                    }
                                    if (requested.isBlank()) path = found.orEmpty()
                                    statusOk = ok
                                    status = if (ok) str("pref_drm_ok") else str("pref_drm_failed")
                                    detected = NativeWidevineCdm.currentCdmPath

                                    // A loaded library is not proof that audio plays. Take a real protected
                                    // track all the way to decoded frames, and put every step in the log.
                                    val log = StringBuilder(NativeWidevineCdm.diagnostics())
                                    report = log.toString()
                                    if (ok) {
                                        statusOk = DrmSelfTest.run { line ->
                                            log.append('\n').append(line)
                                            report = log.toString()
                                        }
                                        // The CDM loading is not the verdict any more once audio was tried.
                                        status = str(if (statusOk) "pref_drm_windows_test_ok" else "pref_drm_windows_test_failed")
                                    } else {
                                        log.append('\n').append("audio test: skipped, no CDM is loaded")
                                        report = log.toString()
                                    }
                                } finally {
                                    searching = false
                                    testing = false
                                }
                            }
                        },
                    )
                },
            ) + drmLogRow(report) { showLog = true },
        )

        DrmHelpGroup(onOpenWizard, experimental = true)
    }

    val log = report
    if (showLog && log != null) {
        DrmLogDialog(
            log = log,
            status = if (testing) str("pref_drm_testing") else status,
            running = testing,
            failed = !statusOk,
            onDismiss = { showLog = false },
        )
    }
}

/** Whether protected streams are tried at all, or a track goes straight to its other sources. */
@Composable
private fun ProtectedTracksSwitch(shape: Shape) {
    val prefs = remember { PlayerPreferences() }
    var enabled by remember { mutableStateOf(prefs.getProtectedTracksEnabled()) }
    SettingsItem(
        shape = shape,
        title = str("pref_drm_attempt_title"),
        subtitle = str("pref_drm_attempt_desc"),
        icon = Icons.Rounded.Lock,
        hasSwitch = true,
        switchState = enabled,
        onSwitchChange = { on ->
            enabled = on
            prefs.setProtectedTracksEnabled(on)
            // A protected stream already resolved would otherwise keep playing until it expires.
            StreamResolver.forgetStreams()
        },
    )
}

/** A status is something to read, not to press, so it stays out of the settings rows. */
@Composable
private fun DrmStatusLine(text: String, icon: ImageVector, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun DrmSpinner() {
    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
}

/**
 * The rounded banner the proxy page uses, rather than a few lines of help text nobody reads: this is
 * where the user learns what the page is for.
 */
@Composable
private fun DrmBanner(lines: List<String>, beta: Boolean = false, status: (@Composable () -> Unit)? = null) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = str("pref_drm_info_title"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (beta) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        ) {
                            Text(
                                text = str("pref_drm_beta_badge"),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                lines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                status?.invoke()
            }
        }
    }
}

/** The way back to the log once its dialog is closed; there is no row until there is a log. */
private fun drmLogRow(report: String?, onClick: () -> Unit): List<@Composable (Shape) -> Unit> {
    if (report == null) return emptyList()
    return listOf { shape ->
        SettingsItem(
            shape = shape,
            title = str("pref_drm_log_title"),
            subtitle = str("pref_drm_log_desc"),
            icon = Icons.Rounded.Description,
            onClick = onClick,
        )
    }
}

/**
 * The log of the last test. It exists so that "it stopped working" can be answered with a paste
 * instead of a guessing session: where we looked, what was found, what the CDM said.
 *
 * A dialog, because a card at the end of the page appeared out of sight of the button that produced
 * it. It fills while the test runs and follows the latest line.
 */
@Composable
private fun DrmLogDialog(
    log: String,
    status: String?,
    running: Boolean,
    failed: Boolean,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val scroll = rememberScrollState()
    LaunchedEffect(log) { scroll.animateScrollTo(scroll.maxValue) }

    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str("pref_drm_log_title")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (running && status != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DrmSpinner()
                        Text(status, style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (status != null) {
                    DrmStatusLine(
                        text = status,
                        icon = if (failed) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
                        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
                SelectionContainer {
                    Text(
                        text = log,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(scroll),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) {
                Text(str("btn_close"))
            }
        },
        dismissButton = {
            TextButton(
                shapes = ButtonDefaults.shapes(),
                onClick = { clipboard.setText(AnnotatedString(log)) },
            ) {
                Text(str("pref_drm_log_copy"))
            }
        },
    )
}

/**
 * One search: the known browser profiles on [deep] = false, the whole machine on true.
 *
 * Kept outside the composable so both the page opening and the button run exactly the same code.
 * Returns the file that was found even when it then refuses to load, so the field can show it.
 */
private fun searchOnce(deep: Boolean): String? {
    if (!deep) {
        NativeWidevineCdm.retryDiscovery()
        return NativeWidevineCdm.lastDiscoveredPath
    }
    val found = NativeWidevineCdm.deepScan() ?: return NativeWidevineCdm.lastDiscoveredPath
    // A CDM found anywhere on the disk is worth remembering: sweeping it again would be wasteful.
    NativeWidevineCdm.applyCustomPath(found)
    return found
}
