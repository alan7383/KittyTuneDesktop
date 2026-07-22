package com.alananasss.kittytune.data

import com.alananasss.kittytune.core.AppDirs
import com.alananasss.kittytune.core.NamedPrefs
import com.alananasss.kittytune.data.network.GithubAsset
import com.alananasss.kittytune.data.network.GithubClient
import com.alananasss.kittytune.data.network.GithubRelease
import com.alananasss.kittytune.utils.AppUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.awt.Desktop
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

enum class UpdateStatus {
    IDLE, CHECKING, AVAILABLE, DOWNLOADING, READY_TO_INSTALL, ERROR, NO_UPDATE
}

/**
 * Desktop port of the Android UpdateManager.
 * GitHub check + version-compare logic kept verbatim. APK download/install replaced
 * by fetching the Windows installer asset (.msi/.exe) and launching it via Desktop.open.
 */
object UpdateManager {
    private val _status = MutableStateFlow(UpdateStatus.IDLE)
    val status = _status.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress = _downloadProgress.asStateFlow()

    private val _downloadSize = MutableStateFlow(0L)
    val downloadSize = _downloadSize.asStateFlow()

    var releaseInfo: GithubRelease? = null
    var downloadedInstallerFile: File? = null

    private const val KEY_LAST_CHECK = "last_check_time"
    private val prefs = NamedPrefs("update_cache")

    private val client = OkHttpClient()
    private const val AUTO_CHECK_COOLDOWN_MS = 15 * 60 * 1000L

    suspend fun checkForUpdate(isManual: Boolean = false) {
        if (!isManual) {
            val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0L)
            val now = System.currentTimeMillis()
            if (now - lastCheck < AUTO_CHECK_COOLDOWN_MS) {
                return
            }
            prefs.putLong(KEY_LAST_CHECK, now)
        }

        _status.value = UpdateStatus.CHECKING

        try {
            val currentVersion = AppUtils.getAppVersion().replace("v", "")
            val release = GithubClient.api.getLatestRelease()

            releaseInfo = release
            val remoteVersion = release.tagName.replace("v", "")

            if (isNewerVersion(currentVersion, remoteVersion)) {
                _status.value = UpdateStatus.AVAILABLE
            } else {
                _status.value = if (isManual) UpdateStatus.NO_UPDATE else UpdateStatus.IDLE
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _status.value = if (isManual) UpdateStatus.ERROR else UpdateStatus.IDLE
        }
    }

    fun findMatchingAsset(): GithubAsset? {
        val assets = releaseInfo?.assets ?: return null
        val osName = System.getProperty("os.name", "").lowercase()
        val osArch = System.getProperty("os.arch", "").lowercase()
        val isArm = osArch.contains("arm") || osArch.contains("aarch64")

        return when {
            osName.contains("win") -> {
                assets.find { it.name.contains("Setup", ignoreCase = true) && it.name.endsWith(".exe", ignoreCase = true) }
                    ?: assets.find { it.name.endsWith(".exe", ignoreCase = true) }
                    ?: assets.find { it.name.endsWith(".msi", ignoreCase = true) }
            }
            osName.contains("mac") -> {
                assets.find { it.name.endsWith(".dmg", ignoreCase = true) }
            }
            osName.contains("nux") || osName.contains("nix") -> {
                val debArch = if (isArm) "arm64" else "amd64"
                val rpmArch = if (isArm) "aarch64" else "x86_64"

                assets.find { it.name.endsWith(".deb", ignoreCase = true) && it.name.contains(debArch, ignoreCase = true) }
                    ?: assets.find { it.name.endsWith(".rpm", ignoreCase = true) && it.name.contains(rpmArch, ignoreCase = true) }
                    ?: assets.find { it.name.endsWith(".pkg.tar.zst", ignoreCase = true) && it.name.contains(rpmArch, ignoreCase = true) }
                    ?: assets.find { it.name.endsWith(".AppImage", ignoreCase = true) }
                    ?: assets.find { it.name.endsWith(".deb", ignoreCase = true) }
                    ?: assets.firstOrNull()
            }
            else -> assets.firstOrNull()
        }
    }

    suspend fun downloadUpdate() {
        val asset = findMatchingAsset()

        if (asset == null) {
            _status.value = UpdateStatus.ERROR
            return
        }

        _status.value = UpdateStatus.DOWNLOADING
        _downloadProgress.value = 0f
        _downloadSize.value = asset.size

        withContext(Dispatchers.IO) {
            try {
                val noRedirectClient = client.newBuilder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build()

                val requestGitHub = Request.Builder().url(asset.browserDownloadUrl).build()
                var response = noRedirectClient.newCall(requestGitHub).execute()

                if (response.code == 302) {
                    val downloadUrl = response.header("Location")
                    response.close()
                    if (downloadUrl != null) {
                        response = client.newCall(Request.Builder().url(downloadUrl).build()).execute()
                    } else {
                        throw Exception("Redirect without location")
                    }
                }

                if (!response.isSuccessful) throw Exception("HTTP Error ${response.code}")

                val body = response.body ?: throw Exception("Empty response body")
                val totalSize = body.contentLength()
                val ext = asset.name.substringAfterLast('.', "installer")
                val file = File(AppDirs.cacheDir, "update_$ext.${asset.name.substringAfterLast('.', "bin")}")
                if (file.exists()) file.delete()

                body.byteStream().use { input ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesCopied = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } >= 0) {
                            output.write(buffer, 0, read)
                            bytesCopied += read
                            if (totalSize > 0) {
                                _downloadProgress.value = bytesCopied.toFloat() / totalSize.toFloat()
                            }
                        }
                        output.flush()
                    }
                }

                downloadedInstallerFile = file
                _status.value = UpdateStatus.READY_TO_INSTALL
            } catch (e: Exception) {
                e.printStackTrace()
                _status.value = UpdateStatus.ERROR
            } finally {
                _downloadSize.value = 0L
            }
        }
    }

    /** Launch the downloaded installer according to the OS. */
    fun installUpdate() {
        val file = downloadedInstallerFile ?: return
        try {
            val osName = System.getProperty("os.name", "").lowercase()
            when {
                osName.contains("win") -> {
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(file)
                    } else {
                        ProcessBuilder("cmd", "/c", "start", "", file.absolutePath).start()
                    }
                }
                osName.contains("nux") || osName.contains("nix") -> {
                    if (file.name.endsWith(".AppImage", ignoreCase = true)) {
                        file.setExecutable(true)
                        ProcessBuilder(file.absolutePath).start()
                    } else {
                        ProcessBuilder("xdg-open", file.absolutePath).start()
                    }
                }
                osName.contains("mac") -> {
                    ProcessBuilder("open", file.absolutePath).start()
                }
                else -> {
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(file)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _status.value = UpdateStatus.ERROR
        }
    }

    fun dismiss() {
        _status.value = UpdateStatus.IDLE
        _downloadProgress.value = 0f
        _downloadSize.value = 0L
    }

    private fun isNewerVersion(current: String, remote: String): Boolean {
        return try {
            val v1 = current.split(".").map { it.toIntOrNull() ?: 0 }
            val v2 = remote.split(".").map { it.toIntOrNull() ?: 0 }
            for (i in 0 until max(v1.size, v2.size)) {
                val v1Part = v1.getOrElse(i) { 0 }
                val v2Part = v2.getOrElse(i) { 0 }
                if (v2Part > v1Part) return true
                if (v2Part < v1Part) return false
            }
            false
        } catch (e: Exception) {
            false
        }
    }
}
