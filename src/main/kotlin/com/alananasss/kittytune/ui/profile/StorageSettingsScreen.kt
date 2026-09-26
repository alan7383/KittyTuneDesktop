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
 * Storage: the audio cache (on/off, its limit, its size), the cover cache, downloaded music with a list of
 * what takes the space, where downloads go, and backups.
 */
@Composable
fun StorageSettingsScreen() {
    val prefs = remember { PlayerPreferences() }
    val scope = rememberCoroutineScope()

    var cacheEnabled by remember { mutableStateOf(AudioCache.isEnabled) }
    var cacheLimitMb by remember { mutableIntStateOf(AudioCache.maxMegabytes) }
    var audioCacheBytes by remember { mutableLongStateOf(0L) }
    var imageCacheBytes by remember { mutableLongStateOf(0L) }
    var downloadsBytes by remember { mutableLongStateOf(0L) }
    val downloads by AppDatabase.downloadDao.getAllTracks().collectAsState(initial = emptyList())
    val downloaded = downloads.filter { it.localAudioPath.isNotEmpty() }
    var downloadLocation by remember { mutableStateOf(prefs.getDownloadLocation() ?: AppDirs.defaultDownloadDir.absolutePath) }

    var showLimitDialog by remember { mutableStateOf(false) }
    var showDownloadsDialog by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }

    fun refreshSizes() {
        scope.launch {
            withContext(Dispatchers.IO) {
                audioCacheBytes = AudioCache.sizeBytes()
                imageCacheBytes = AppDirs.sizeOf(AppDirs.imageCacheDir)
                downloadsBytes = AppDirs.sizeOf(File(downloadLocation))
            }
        }
    }
    LaunchedEffect(downloadLocation, downloads.size) { refreshSizes() }

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

/** Every downloaded track, largest first, each removable on its own. */
@Composable
private fun DownloadsDialog(tracks: List<LocalTrack>, onDismiss: () -> Unit) {
    val sized by produceState(initialValue = emptyList<Pair<LocalTrack, Long>>(), tracks) {
        value = withContext(Dispatchers.IO) {
            tracks.map { it to File(it.localAudioPath).length() }.sortedByDescending { it.second }
        }
    }
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${str("storage_downloaded_music")} · ${tracks.size}", fontWeight = FontWeight.SemiBold) },
        text = {
            if (tracks.isEmpty()) {
                Text(str("storage_no_downloads"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(Modifier.widthIn(min = 380.dp, max = 560.dp).heightIn(max = 480.dp)) {
                    items(sized, key = { it.first.id }) { (track, bytes) ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = track.localArtworkPath.takeIf { File(it).exists() }?.let(::File) ?: track.artworkUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(formatBytes(bytes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            IconButton(onClick = { DownloadManager.deleteTrack(track.id) }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = str("btn_delete"))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_close")) } },
    )
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "${bytes / (1024 * 1024)} MB"
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

private fun pickDirectory(title: String, current: File): File? {
    val chooser = javax.swing.JFileChooser(current).apply {
        dialogTitle = title
        fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
    }
    return if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

private fun pickSaveFile(title: String, suggestedName: String): File? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.SAVE).apply { file = suggestedName }
    dialog.isVisible = true
    return dialog.files.firstOrNull()
}

private fun pickOpenFile(title: String): File? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.LOAD).apply {
        setFilenameFilter { _, name -> name.endsWith(".backup", true) || name.endsWith(".json", true) }
    }
    dialog.isVisible = true
    return dialog.files.firstOrNull()
}
