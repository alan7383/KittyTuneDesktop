package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.AppDirs
import com.alananasss.kittytune.core.EscapableAlertDialog
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.BackupManager
import com.alananasss.kittytune.data.DownloadManager
import com.alananasss.kittytune.data.cache.AudioCache
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.LocalTrack
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val cacheLimitsMb = listOf(256, 512, 1024, 2048, 5120, 10240)

/**
 * Storage: the audio cache, cover cache, downloaded music, and backups.
 */
@Composable
fun StorageSettingsScreen() {
    Column(modifier = Modifier.fillMaxWidth()) {
        StorageCachePage()
        Spacer(Modifier.height(16.dp))
        StorageDownloadsPage()
        Spacer(Modifier.height(16.dp))
        StorageBackupPage()
        Spacer(Modifier.height(16.dp))
        LocalMediaSettingsScreen(onBackClick = null)
    }
}

/**
 * Storage → Cache: audio cache toggle, limit, size and image cache cleanup.
 */
@Composable
fun StorageCachePage() {
    val scope = rememberCoroutineScope()
    var cacheEnabled by remember { mutableStateOf(AudioCache.isEnabled) }
    var cacheLimitMb by remember { mutableIntStateOf(AudioCache.maxMegabytes) }
    var audioCacheBytes by remember { mutableLongStateOf(0L) }
    var imageCacheBytes by remember { mutableLongStateOf(0L) }
    var showLimitDialog by remember { mutableStateOf(false) }

    fun refreshSizes() {
        scope.launch {
            withContext(Dispatchers.IO) {
                audioCacheBytes = AudioCache.sizeBytes()
                imageCacheBytes = AppDirs.sizeOf(AppDirs.imageCacheDir)
            }
        }
    }
    LaunchedEffect(Unit) { refreshSizes() }

    if (showLimitDialog) {
        ChoiceDialog(
            title = str("storage_cache_limit"),
            options = cacheLimitsMb.map { it to formatBytes(it.toLong() * 1024 * 1024) },
            selected = cacheLimitMb,
            onSelect = {
                cacheLimitMb = it
                AudioCache.maxMegabytes = it
                refreshSizes()
            },
            onDismiss = { showLimitDialog = false },
        )
    }

    SettingsGroup(
        title = str("storage_group_cache"),
        items = buildList {
            add { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("storage_cache_audio"),
                    subtitle = str("storage_cache_audio_sub"),
                    icon = Icons.Rounded.OfflineBolt,
                    hasSwitch = true,
                    switchState = cacheEnabled,
                    onSwitchChange = {
                        cacheEnabled = it
                        AudioCache.isEnabled = it
                    },
                )
            }
            if (cacheEnabled) add { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("storage_cache_limit"),
                    subtitle = str("storage_cache_used", formatBytes(audioCacheBytes), formatBytes(cacheLimitMb.toLong() * 1024 * 1024)),
                    icon = Icons.Rounded.DataUsage,
                    onClick = { showLimitDialog = true },
                )
            }
            add { shape ->
                CleanableRow(shape, str("storage_cache_audio_size"), formatBytes(audioCacheBytes), Icons.Rounded.GraphicEq) {
                    scope.launch {
                        withContext(Dispatchers.IO) { AudioCache.clear() }
                        refreshSizes()
                    }
                }
            }
            add { shape ->
                CleanableRow(shape, str("storage_cache_images"), formatBytes(imageCacheBytes), Icons.Rounded.Image) {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            AppDirs.imageCacheDir.listFiles()?.forEach { it.deleteRecursively() }
                            AppDirs.imageCacheDir.mkdirs()
                        }
                        refreshSizes()
                    }
                }
            }
        },
    )
}

/**
 * Storage → Downloads: downloaded music list, download directory picker, delete all downloads.
 */
@Composable
fun StorageDownloadsPage() {
    val prefs = remember { PlayerPreferences() }
    val scope = rememberCoroutineScope()
    var downloadsBytes by remember { mutableLongStateOf(0L) }
    val downloads by AppDatabase.downloadDao.getAllTracks().collectAsState(initial = emptyList())
    val downloaded = downloads.filter { it.localAudioPath.isNotEmpty() }
    var downloadLocation by remember { mutableStateOf(prefs.getDownloadLocation() ?: AppDirs.defaultDownloadDir.absolutePath) }

    var showDownloadsDialog by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    fun refreshSizes() {
        scope.launch {
            withContext(Dispatchers.IO) {
                downloadsBytes = AppDirs.sizeOf(File(downloadLocation))
            }
        }
    }
    LaunchedEffect(downloadLocation, downloads.size) { refreshSizes() }

    if (showDownloadsDialog) DownloadsDialog(downloaded) { showDownloadsDialog = false }
    if (confirmDeleteAll) {
        EscapableAlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(str("pref_storage_downloads_clear")) },
            text = { Text(str("storage_delete_all_confirm", downloaded.size)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAll = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            AppDatabase.downloadDao.deleteAll()
                            File(downloadLocation).listFiles()?.forEach { it.deleteRecursively() }
                        }
                        refreshSizes()
                    }
                }) { Text(str("btn_delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text(str("btn_cancel")) } },
        )
    }

    SettingsGroup(
        title = str("pref_storage_downloads"),
        items = listOf(
            { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("storage_downloaded_music"),
                    subtitle = str("storage_downloaded_summary", downloaded.size, formatBytes(downloadsBytes)),
                    icon = Icons.Rounded.DownloadDone,
                    onClick = { showDownloadsDialog = true },
                )
            },
            { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_storage_location"),
                    subtitle = downloadLocation,
                    icon = Icons.Rounded.FolderOpen,
                    onClick = {
                        pickDirectory(str("pref_storage_location_change"), File(downloadLocation))?.let { chosen ->
                            prefs.saveDownloadLocation(chosen.absolutePath)
                            downloadLocation = chosen.absolutePath
                        }
                    },
                )
            },
            { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_storage_downloads_clear"),
                    icon = Icons.Rounded.DeleteSweep,
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = if (downloaded.isNotEmpty()) ({ confirmDeleteAll = true }) else null,
                )
            },
        ),
    )
}

/**
 * Storage → Backup & Restore: export or import app data and settings.
 */
@Composable
fun StorageBackupPage() {
    val scope = rememberCoroutineScope()
    var backupMessage by remember { mutableStateOf<String?>(null) }

    SettingsGroup(
        title = str("storage_group_backup"),
        items = listOf(
            { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("storage_backup_create"),
                    subtitle = str("storage_backup_create_sub"),
                    icon = Icons.Rounded.Backup,
                    onClick = {
                        val target = pickSaveFile(str("storage_backup_create"), BackupManager.getBackupFileName()) ?: return@SettingsItem
                        scope.launch {
                            backupMessage = runCatching { BackupManager.createBackup(target) }
                                .fold({ str("storage_backup_done", target.name) }, { str("storage_backup_failed", it.message ?: "") })
                        }
                    },
                )
            },
            { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("storage_backup_restore"),
                    subtitle = str("storage_backup_restore_sub"),
                    icon = Icons.Rounded.Restore,
                    onClick = {
                        val source = pickOpenFile(str("storage_backup_restore")) ?: return@SettingsItem
                        scope.launch {
                            backupMessage = runCatching { BackupManager.restoreBackup(source) }
                                .fold({ str("storage_backup_restored") }, { str("storage_backup_failed", it.message ?: "") })
                        }
                    },
                )
            },
        ),
    )
    backupMessage?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(horizontal = 32.dp))
    }
}

@Composable
private fun CleanableRow(shape: androidx.compose.ui.graphics.Shape, title: String, size: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClear: () -> Unit) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(size, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onClear) { Text(str("storage_clear")) }
        }
    }
}

@Composable
private fun DownloadsDialog(tracks: List<LocalTrack>, onDismiss: () -> Unit) {
    com.alananasss.kittytune.core.BackHandler(onBack = onDismiss)
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(str("storage_downloaded_music"), style = MaterialTheme.typography.headlineSmall)
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = null) }
                }
                Spacer(Modifier.height(12.dp))
                if (tracks.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(str("storage_downloaded_empty"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(tracks, key = { it.id }) { track ->
                            val file = File(track.localAudioPath)
                            val size = if (file.exists()) formatBytes(file.length()) else "—"
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = track.artworkUrl.ifBlank { track.localArtworkPath },
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(size, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                IconButton(onClick = { DownloadManager.deleteTrack(track.id) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = str("btn_delete"), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024f)
    bytes < 1024 * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024f * 1024f))
    else -> String.format(java.util.Locale.US, "%.2f GB", bytes / (1024f * 1024f * 1024f))
}

private fun pickDirectory(title: String, initial: File?): File? {
    val chooser = javax.swing.JFileChooser().apply {
        dialogTitle = title
        fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
        initial?.let { currentDirectory = if (it.isDirectory) it else it.parentFile }
    }
    return if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

private fun pickSaveFile(title: String, defaultName: String): File? {
    val chooser = javax.swing.JFileChooser().apply {
        dialogTitle = title
        selectedFile = File(defaultName)
    }
    return if (chooser.showSaveDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

private fun pickOpenFile(title: String): File? {
    val chooser = javax.swing.JFileChooser().apply {
        dialogTitle = title
        fileSelectionMode = javax.swing.JFileChooser.FILES_ONLY
    }
    return if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}
