package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.core.trackTextInput
import com.alananasss.kittytune.data.drm.DrmSelfTest
import com.alananasss.kittytune.data.drm.NativeWidevineCdm
import com.alananasss.kittytune.data.local.PlayerPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.JFileChooser
import java.io.File

/**
 * Settings → the Widevine CDM, so protected SoundCloud tracks can be unlocked.
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
fun DrmSettingsSection(showTitle: Boolean = true) {
    val prefs = remember { PlayerPreferences() }
    var path by remember { mutableStateOf(prefs.getWidevineCdmPath()) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusOk by remember { mutableStateOf(false) }
    var detected by remember { mutableStateOf(NativeWidevineCdm.currentCdmPath) }
    var searching by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

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

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showTitle) {
            Text(str("pref_drm_title"), style = MaterialTheme.typography.titleMedium)
        }

        // The explanation sits in the same rounded banner the proxy page uses, rather than a few lines
        // of help text nobody reads: this is the one page where the user has to understand what the
        // box is for before being asked for a file path.
        Card(
            modifier = Modifier.fillMaxWidth(),
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
                    Text(
                        text = str("pref_drm_info_title"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = str("pref_drm_no_browser"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = str("pref_drm_hint"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        val known = detected
        val statusLine = known
            ?: if (searching) str("pref_drm_searching") else str("pref_drm_failed")
        val statusIcon: ImageVector = when {
            known != null -> Icons.Rounded.CheckCircle
            searching -> Icons.Rounded.Search
            else -> Icons.Rounded.ErrorOutline
        }
        val statusColor = when {
            known != null -> MaterialTheme.colorScheme.primary
            searching -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.error
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(20.dp),
            )
            Text(statusLine, style = MaterialTheme.typography.bodyMedium, color = statusColor)
        }

        OutlinedTextField(
            value = path,
            onValueChange = { path = it },
            label = { Text(str("pref_drm_cdm_path")) },
            placeholder = { Text("libwidevinecdm.so") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().trackTextInput(),
        )

        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Starts from a clean slate: a remembered path would win over the search and hand back
            // the same file, which is the opposite of "look again".
            FilledTonalButton(
                shapes = ButtonDefaults.shapes(),
                enabled = !searching,
                onClick = {
                    // "Detect automatically" means the whole machine: the quick pass is only a head
                    // start, and whatever it does not find is looked for everywhere.
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
            ) {
                Text(str("pref_drm_autodetect"))
            }

            OutlinedButton(
                shapes = ButtonDefaults.shapes(),
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
            ) {
                Text(str("pref_drm_browse"))
            }

            Button(
                shapes = ButtonDefaults.shapes(),
                enabled = !searching,
                onClick = {
                    // Loading a CDM is file work and can wait on the library: never on the UI thread.
                    val requested = path
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
                                DrmSelfTest.run { line ->
                                    log.append('\n').append(line)
                                    report = log.toString()
                                }
                            } else {
                                log.append('\n').append("audio test: skipped, no CDM is loaded")
                                report = log.toString()
                            }
                        } finally {
                            searching = false
                        }
                    }
                },
            ) {
                Text(str("pref_drm_test"))
            }
        }

        status?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = if (statusOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }

        // The log of the last test. It exists so that "it stopped working" can be answered with a
        // paste instead of a guessing session: where we looked, what was found, what the CDM said.
        report?.let { log ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = str("pref_drm_log_title"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        TextButton(
                            shapes = ButtonDefaults.shapes(),
                            onClick = { clipboard.setText(AnnotatedString(log)) },
                        ) {
                            Text(str("pref_drm_log_copy"))
                        }
                    }
                    SelectionContainer {
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
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
