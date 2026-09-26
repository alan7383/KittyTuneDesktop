@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.zapret.Reachability
import com.alananasss.kittytune.data.zapret.ServiceCheck
import com.alananasss.kittytune.data.zapret.ZapretInstall
import com.alananasss.kittytune.data.zapret.ZapretManager
import com.alananasss.kittytune.data.zapret.ZapretService
import com.alananasss.kittytune.ui.common.SettingsGroupTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Zapret: where it is, which of the app's services get through without it, and adding the domains of the
 * ones that do not to its user list. What KittyTune adds sits in a marked block and can be taken out again.
 */
@Composable
fun ZapretSection() {
    val scope = rememberCoroutineScope()
    var folder by remember { mutableStateOf(ZapretManager.folder) }
    val install = remember(folder) { folder?.let(::ZapretInstall)?.takeIf { it.isValid } }
    var isRunning by remember { mutableStateOf(false) }
    var checks by remember { mutableStateOf<List<ServiceCheck>?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var own by remember(install) { mutableStateOf(install?.kittyTuneDomains().orEmpty()) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { isRunning = withContext(Dispatchers.IO) { ZapretManager.isRunning() } }

    fun useFolder(chosen: File?) {
        if (chosen == null) return
        if (!ZapretInstall(chosen).isValid) {
            message = str("zapret_invalid")
            return
        }
        ZapretManager.folder = chosen
        folder = chosen
        message = null
    }

    fun runCheck() {
        isChecking = true
        scope.launch {
            checks = ZapretManager.check()
            isChecking = false
        }
    }

    fun add(services: List<ZapretService>) {
        scope.launch {
            val added = ZapretManager.addDomains(services)
            own = install?.kittyTuneDomains().orEmpty()
            message = if (added.isEmpty()) str("zapret_nothing_to_add") else str("zapret_added", added.size)
            checks = ZapretManager.check()
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        SettingsGroupTitle("Zapret")
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(str("zapret_sub"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                // The folder, and whether zapret is running.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(str("zapret_folder"), style = MaterialTheme.typography.titleSmall)
                        Text(
                            folder?.absolutePath ?: str("zapret_folder_none"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis,
                        )
                    }
                    TextButton(onClick = {
                        val found = ZapretManager.detect()
                        if (found == null) message = str("zapret_not_found") else useFolder(found)
                    }) {
                        Text(str("zapret_detect"))
                    }
                    FilledTonalButton(onClick = { useFolder(pickFolder(str("zapret_folder"), folder)) }) {
                        Text(str("zapret_choose"))
                    }
                }
                StatusLine(
                    icon = if (isRunning) Icons.Rounded.CheckCircle else Icons.Rounded.PauseCircle,
                    text = str(if (isRunning) "zapret_running" else "zapret_not_running"),
                    color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // The services.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = ::runCheck, enabled = !isChecking) {
                        Icon(Icons.Rounded.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(str(if (isChecking) "zapret_checking" else "zapret_check"))
                    }
                    val blocked = checks.orEmpty().filter { it.reachability == Reachability.BLOCKED && !it.isCovered }
                    if (install != null && blocked.isNotEmpty()) {
                        FilledTonalButton(onClick = { add(blocked.map { it.service }) }) {
                            Text(str("zapret_add_blocked", blocked.size))
                        }
                    }
                    if (isChecking) LoadingIndicator(Modifier.size(32.dp))
                }
                checks?.let { results ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        results.forEach { check -> ServiceRow(check, canAdd = install != null) { add(listOf(check.service)) } }
                    }
                }

                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }

                // What KittyTune added.
                if (own.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(str("zapret_own_title"), style = MaterialTheme.typography.titleSmall)
                            Text(own.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = {
                            scope.launch {
                                ZapretManager.removeOwnDomains()
                                own = emptyList()
                                checks?.let { checks = ZapretManager.check() }
                            }
                        }) { Text(str("zapret_remove_own")) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun ServiceRow(check: ServiceCheck, canAdd: Boolean, onAdd: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (label, color) = when {
        check.reachability == Reachability.REACHABLE -> str("zapret_reachable") to scheme.primary
        check.isCovered -> str("zapret_covered") to scheme.secondary
        else -> str("zapret_blocked") to scheme.error
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = CircleShape, color = color, modifier = Modifier.size(10.dp)) {}
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(check.service.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(check.service.domains.joinToString(", "), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
        if (canAdd && !check.isCovered && check.reachability == Reachability.BLOCKED) {
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onAdd) { Text(str("zapret_add")) }
        }
    }
}

private fun pickFolder(title: String, current: File?): File? {
    val chooser = javax.swing.JFileChooser(current ?: File(System.getProperty("user.home"), "Desktop")).apply {
        dialogTitle = title
        fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
    }
    return if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}
