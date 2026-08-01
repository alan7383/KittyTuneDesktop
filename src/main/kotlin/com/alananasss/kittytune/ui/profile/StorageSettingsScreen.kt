@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.AppDirs
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

@Composable
fun StorageSettingsScreen() {
    val prefs = remember { PlayerPreferences() }
    val scope = rememberCoroutineScope()

    var downloadsSizeMB by remember { mutableStateOf(0L) }
    var cacheSizeMB by remember { mutableStateOf(0L) }
    var downloadLocation by remember { mutableStateOf(prefs.getDownloadLocation() ?: AppDirs.defaultDownloadDir.absolutePath) }

    fun refreshSizes() {
        scope.launch {
            val dSize = withContext(Dispatchers.IO) { AppDirs.sizeOf(File(downloadLocation)) / (1024 * 1024) }
            val cSize = withContext(Dispatchers.IO) { AppDirs.sizeOf(AppDirs.cacheDir) / (1024 * 1024) }
            downloadsSizeMB = dSize
            cacheSizeMB = cSize
        }
    }

    LaunchedEffect(downloadLocation) {
        refreshSizes()
    }

    fun openFolderPicker() {
        SwingUtilities.invokeLater {
            val chooser = JFileChooser(downloadLocation)
            chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            chooser.dialogTitle = str("pref_storage_location_change")
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                val selectedFile = chooser.selectedFile
                if (selectedFile != null) {
                    val path = selectedFile.absolutePath
                    prefs.saveDownloadLocation(path)
                    downloadLocation = path
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${str("pref_storage_downloads")} : $downloadsSizeMB MB",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = str("pref_storage_downloads_desc"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            AppDatabase.downloadDao.deleteAll()
                            val dir = File(downloadLocation)
                            dir.listFiles()?.forEach { it.deleteRecursively() }
                        }
                        refreshSizes()
                    }
                },
                shapes = ButtonDefaults.shapes()
            ) {
                Text(str("pref_storage_downloads_clear"))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${str("pref_storage_cache")} : $cacheSizeMB MB",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = str("pref_storage_cache_desc"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            AppDirs.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                            AppDirs.imageCacheDir.mkdirs()
                            AppDirs.audioCacheDir.mkdirs()
                        }
                        refreshSizes()
                    }
                },
                shapes = ButtonDefaults.shapes()
            ) {
                Text(str("pref_storage_cache_clear"))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = str("pref_storage_location"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = downloadLocation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            OutlinedButton(onClick = { openFolderPicker() }, shapes = ButtonDefaults.shapes()) {
                Text(str("pref_storage_location_change"))
            }
        }
    }
}
