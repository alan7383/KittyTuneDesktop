package com.alananasss.kittytune.data

import com.alananasss.kittytune.core.AppDirs
import com.alananasss.kittytune.core.NamedPrefs
import com.alananasss.kittytune.data.network.GithubAsset
import com.alananasss.kittytune.data.network.GithubClient
import com.alananasss.kittytune.data.network.GithubRelease
import com.alananasss.kittytune.utils.AppUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.awt.Desktop
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

enum class UpdateStatus {
    IDLE, CHECKING, AVAILABLE, DOWNLOADING, PAUSED, WAITING_FOR_AUTH, INSTALLING, MULTIPLE_INSTANCES, READY_TO_INSTALL, ERROR, NO_UPDATE, AUTH_FAILED
}

/**
 * Desktop port of the Android UpdateManager.
 * In-app update flow:
 * 1. Download asset with KittyTune progress UI (DOWNLOADING)
 * 2. Silent background installation with KittyTune UI progress (INSTALLING)
 * 3. App restart button (READY_TO_INSTALL -> restartApp)
 */
object UpdateManager {
    private val _status = MutableStateFlow(UpdateStatus.IDLE)
    val status = _status.asStateFlow()

    private val _isDialogVisible = MutableStateFlow(false)
    val isDialogVisible = _isDialogVisible.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress = _downloadProgress.asStateFlow()

    private val _downloadSize = MutableStateFlow(0L)
    val downloadSize = _downloadSize.asStateFlow()

    var releaseInfo: GithubRelease? = null
    var downloadedInstallerFile: File? = null
    private var currentInstallProcess: Process? = null
    private var authFailedFile: File? = null

    private const val KEY_LAST_CHECK = "last_check_time"
    private val prefs = NamedPrefs("update_cache")

    fun isAutoUpdateEnabled(): Boolean = com.alananasss.kittytune.data.local.PlayerPreferences().getAutoUpdateEnabled()

    fun setAutoUpdateEnabled(enabled: Boolean) {
        com.alananasss.kittytune.data.local.PlayerPreferences().setAutoUpdateEnabled(enabled)
    }

    fun showDialog() {
        _isDialogVisible.value = true
    }

    fun hideDialog() {
        _isDialogVisible.value = false
    }

    fun pauseDownload() {
        if (_status.value == UpdateStatus.DOWNLOADING) {
            _status.value = UpdateStatus.PAUSED
        }
    }

    fun getOtherKittyTuneProcesses(): List<ProcessHandle> {
        return try {
            val currentPid = ProcessHandle.current().pid()
            ProcessHandle.allProcesses().filter { handle ->
                if (handle.pid() == currentPid) return@filter false

                val info = handle.info()
                val cmd = info.command().orElse("").lowercase()
                val cmdLine = info.commandLine().orElse("").lowercase()

                // Ignore Gradle Daemons, Gradle Workers, and IDE processes
                if (cmdLine.contains("gradledaemon") || cmdLine.contains("gradleworkermain") || cmdLine.contains("gradlewrappermain")) {
                    return@filter false
                }

                // Check native binary executable name
                val exeName = cmd.substringAfterLast('/', "").substringAfterLast('\\', "")
                val isNativeKittyTune = (exeName == "kittytune.exe" || exeName == "kittytune" || exeName == "kitty-tune")

                // Check java process running another KittyTune MainKt instance
                val isJavaKittyTune = (exeName == "java.exe" || exeName == "javaw.exe" || exeName == "java") &&
                        cmdLine.contains("com.alananasss.kittytune.mainkt")

                isNativeKittyTune || isJavaKittyTune
            }.toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun killOtherKittyTuneProcesses() {
        try {
            val currentPid = ProcessHandle.current().pid()
            getOtherKittyTuneProcesses().forEach { handle ->
                runCatching { handle.destroyForcibly() }
            }
            val osName = System.getProperty("os.name", "").lowercase()
            when {
                osName.contains("win") -> {
                    val currentExeName = "KittyTune.exe"
                    runCatching { ProcessBuilder("taskkill", "/F", "/FI", "PID ne $currentPid", "/IM", currentExeName).start().waitFor() }
                }
                osName.contains("nux") || osName.contains("nix") -> {
                    runCatching { ProcessBuilder("pkill", "-9", "-f", "kittytune").start().waitFor() }
                    runCatching { ProcessBuilder("pkill", "-9", "-f", "kitty-tune").start().waitFor() }
                    runCatching { ProcessBuilder("killall", "-9", "kittytune").start().waitFor() }
                }
                osName.contains("mac") -> {
                    runCatching { ProcessBuilder("pkill", "-9", "-f", "KittyTune").start().waitFor() }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun killInstancesAndContinue() {
        killOtherKittyTuneProcesses()
        downloadedInstallerFile?.let { file ->
            _status.value = UpdateStatus.INSTALLING
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                performBackgroundInstall(file)
            }
        }
    }

    fun retryInstall() {
        val file = authFailedFile ?: downloadedInstallerFile ?: return
        authFailedFile = null
        _status.value = UpdateStatus.WAITING_FOR_AUTH
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            performBackgroundInstall(file)
        }
    }

    suspend fun checkOnStartup() {
        if (isAutoUpdateEnabled()) {
            checkForUpdate(isManual = false)
        }
    }

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
            val remoteVersion = release.versionName.replace("v", "")

            if (isNewerVersion(currentVersion, remoteVersion)) {
                _status.value = UpdateStatus.AVAILABLE
                _isDialogVisible.value = true
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
                val archName = if (isArm) "aarch64" else "x86_64"
                val debArch = if (isArm) "arm64" else "amd64"

                val isArch = File("/etc/arch-release").exists() || File("/etc/pacman.conf").exists()
                val isDebian = File("/etc/debian_version").exists()
                val isRpm = File("/etc/redhat-release").exists() || File("/etc/fedora-release").exists()

                if (isArch) {
                    assets.find { it.name.endsWith(".pkg.tar.zst", ignoreCase = true) && (it.name.contains(archName, ignoreCase = true) || it.name.contains(osArch, ignoreCase = true)) }
                        ?: assets.find { it.name.endsWith(".pkg.tar.zst", ignoreCase = true) }
                } else if (isDebian) {
                    assets.find { it.name.endsWith(".deb", ignoreCase = true) && (it.name.contains(debArch, ignoreCase = true) || it.name.contains(archName, ignoreCase = true)) }
                        ?: assets.find { it.name.endsWith(".deb", ignoreCase = true) }
                } else if (isRpm) {
                    assets.find { it.name.endsWith(".rpm", ignoreCase = true) && (it.name.contains(archName, ignoreCase = true) || it.name.contains(osArch, ignoreCase = true)) }
                        ?: assets.find { it.name.endsWith(".rpm", ignoreCase = true) }
                } else {
                    assets.find { it.name.endsWith(".AppImage", ignoreCase = true) }
                        ?: assets.find { it.name.endsWith(".deb", ignoreCase = true) }
                        ?: assets.find { it.name.endsWith(".rpm", ignoreCase = true) }
                        ?: assets.find { it.name.endsWith(".pkg.tar.zst", ignoreCase = true) && it.name.contains(archName, ignoreCase = true) }
                        ?: assets.firstOrNull()
                }
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

        val isResuming = _status.value == UpdateStatus.PAUSED
        _status.value = UpdateStatus.DOWNLOADING

        withContext(Dispatchers.IO) {
            try {
                var file = File(AppDirs.cacheDir, "update_${asset.name}")

                var existingLength = 0L
                if (isResuming && file.exists()) {
                    existingLength = file.length()
                } else {
                    cleanupOldUpdateCache()
                    if (file.exists()) {
                        val deleted = runCatching { file.delete() }.getOrDefault(false)
                        if (!deleted) {
                            file = File(AppDirs.cacheDir, "update_${System.currentTimeMillis()}_${asset.name}")
                        }
                    }
                }

                val downloadClient = client.newBuilder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()

                val requestBuilder = Request.Builder()
                    .url(asset.browserDownloadUrl)
                    .header("User-Agent", "KittyTuneDesktop")

                if (existingLength > 0) {
                    requestBuilder.header("Range", "bytes=$existingLength-")
                }

                val response = downloadClient.newCall(requestBuilder.build()).execute()

                if (!response.isSuccessful && response.code != 206) {
                    throw Exception("HTTP Error ${response.code}")
                }

                val body = response.body
                val contentLength = body.contentLength()
                val totalSize = if (response.code == 206 || existingLength > 0) {
                    existingLength + (if (contentLength > 0) contentLength else 0L)
                } else {
                    if (contentLength > 0) contentLength else asset.size
                }

                _downloadSize.value = totalSize

                try {
                    body.byteStream().use { input ->
                        FileOutputStream(file, existingLength > 0).use { output ->
                            val buffer = ByteArray(8 * 1024)
                            var bytesCopied = existingLength
                            var read: Int
                            while (input.read(buffer).also { read = it } >= 0) {
                                if (_status.value == UpdateStatus.PAUSED) {
                                    return@withContext
                                }
                                if (_status.value != UpdateStatus.DOWNLOADING) {
                                    output.close()
                                    runCatching { file.delete() }
                                    return@withContext
                                }
                                output.write(buffer, 0, read)
                                bytesCopied += read
                                if (totalSize > 0) {
                                    _downloadProgress.value = bytesCopied.toFloat() / totalSize.toFloat()
                                }
                            }
                            output.flush()
                        }
                    }
                } catch (e: java.io.FileNotFoundException) {
                    val fallbackFile = File(AppDirs.cacheDir, "update_${System.currentTimeMillis()}_${asset.name}")
                    body.byteStream().use { input ->
                        FileOutputStream(fallbackFile, false).use { output ->
                            val buffer = ByteArray(8 * 1024)
                            var bytesCopied = 0L
                            var read: Int
                            while (input.read(buffer).also { read = it } >= 0) {
                                if (_status.value == UpdateStatus.PAUSED || _status.value != UpdateStatus.DOWNLOADING) {
                                    output.close()
                                    runCatching { fallbackFile.delete() }
                                    return@withContext
                                }
                                output.write(buffer, 0, read)
                                bytesCopied += read
                                if (totalSize > 0) {
                                    _downloadProgress.value = bytesCopied.toFloat() / totalSize.toFloat()
                                }
                            }
                            output.flush()
                        }
                    }
                    file = fallbackFile
                }

                if (_status.value == UpdateStatus.PAUSED) {
                    return@withContext
                }

                if (_status.value != UpdateStatus.DOWNLOADING) {
                    file.delete()
                    return@withContext
                }

                downloadedInstallerFile = file

                // Step 2: Perform background installation within KittyTune UI
                _status.value = UpdateStatus.INSTALLING
                performBackgroundInstall(file)
            } catch (e: Exception) {
                e.printStackTrace()
                _status.value = UpdateStatus.ERROR
            } finally {
                _downloadSize.value = 0L
            }
        }
    }

    private fun performBackgroundInstall(file: File) {
        try {
            if (getOtherKittyTuneProcesses().isNotEmpty()) {
                _status.value = UpdateStatus.MULTIPLE_INSTANCES
                return
            }

            val osName = System.getProperty("os.name", "").lowercase()
            val args = when {
                osName.contains("win") -> {
                    if (file.name.endsWith(".msi", ignoreCase = true)) {
                        arrayOf("msiexec", "/i", file.absolutePath, "/quiet", "/norestart")
                    } else {
                        arrayOf(file.absolutePath, "/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART", "/SP-", "/passive", "/quiet")
                    }
                }
                osName.contains("nux") || osName.contains("nix") -> {
                    when {
                        file.name.endsWith(".pkg.tar.zst", ignoreCase = true) -> {
                            arrayOf("pkexec", "pacman", "-U", "--noconfirm", file.absolutePath)
                        }
                        file.name.endsWith(".deb", ignoreCase = true) -> {
                            arrayOf("pkexec", "dpkg", "-i", file.absolutePath)
                        }
                        file.name.endsWith(".rpm", ignoreCase = true) -> {
                            val rpmManager = when {
                                File("/usr/bin/dnf").exists() -> "dnf"
                                File("/usr/bin/zypper").exists() -> "zypper"
                                File("/usr/bin/yum").exists() -> "yum"
                                else -> "dnf"
                            }
                            arrayOf("pkexec", rpmManager, "install", "-y", file.absolutePath)
                        }
                        file.name.endsWith(".AppImage", ignoreCase = true) -> {
                            file.setExecutable(true, false)
                            ProcessBuilder(file.absolutePath).start()
                            _status.value = UpdateStatus.READY_TO_INSTALL
                            kotlin.system.exitProcess(0)
                        }
                        else -> emptyArray()
                    }
                }
                osName.contains("mac") -> {
                    arrayOf("hdiutil", "attach", file.absolutePath)
                }
                else -> emptyArray()
            }

            if (args.isNotEmpty()) {
                val needsAuth = args.firstOrNull() == "pkexec"
                if (needsAuth) _status.value = UpdateStatus.WAITING_FOR_AUTH
                val process = ProcessBuilder(*args)
                    .redirectErrorStream(true)
                    .start()
                currentInstallProcess = process

                if (needsAuth) {
                    var authDetected = false
                    val reader = Thread {
                        try {
                            process.inputStream.bufferedReader().useLines { lines ->
                                for (line in lines) {
                                    if (!authDetected) {
                                        authDetected = true
                                        if (_status.value == UpdateStatus.WAITING_FOR_AUTH) {
                                            _status.value = UpdateStatus.INSTALLING
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    reader.isDaemon = true
                    reader.start()

                    val exitCode = process.waitFor()
                    reader.join(2000)

                    if (exitCode != 0 && !authDetected) {
                        authFailedFile = file
                        _status.value = UpdateStatus.AUTH_FAILED
                        return
                    }
                } else {
                    _status.value = UpdateStatus.INSTALLING
                    process.waitFor()
                }
                currentInstallProcess = null
            }
            _status.value = UpdateStatus.READY_TO_INSTALL
        } catch (e: Exception) {
            e.printStackTrace()
            _status.value = UpdateStatus.ERROR
        }
    }

    /** Restart the application after in-app installation. */
    fun restartApp() {
        var launched = false
        try {
            val osName = System.getProperty("os.name", "").lowercase()
            when {
                osName.contains("win") -> {
                    val nullDevice = File("NUL")
                    val jpackagePath = System.getProperty("jpackage.app.path")
                    val possibleExePaths = mutableListOf<File>()
                    jpackagePath?.let { p ->
                        val f = File(p)
                        if (f.isFile) possibleExePaths.add(f)
                        else if (f.isDirectory) File(f, "KittyTune.exe").let { if (it.exists()) possibleExePaths.add(it) }
                    }
                    possibleExePaths.addAll(listOfNotNull(
                        File(System.getProperty("user.dir"), "KittyTune.exe"),
                        File(System.getenv("LOCALAPPDATA") ?: "", "Programs/KittyTune/KittyTune.exe"),
                        File(System.getenv("LOCALAPPDATA") ?: "", "KittyTune/KittyTune.exe"),
                        File(System.getenv("ProgramFiles") ?: "", "KittyTune/KittyTune.exe")
                    ))

                    val existingExe = possibleExePaths.firstOrNull { it.exists() && it.isFile }
                    if (existingExe != null) {
                        ProcessBuilder(existingExe.absolutePath)
                            .redirectOutput(nullDevice)
                            .redirectError(nullDevice)
                            .start()
                        launched = true
                    }
                }
                osName.contains("nux") || osName.contains("nix") -> {
                    val jpackagePath = System.getProperty("jpackage.app.path")
                    val possibleBinPaths = mutableListOf<File>()
                    jpackagePath?.let { p ->
                        val f = File(p)
                        if (f.isFile) possibleBinPaths.add(f)
                        else if (f.isDirectory) {
                            listOf("bin/KittyTune", "bin/kitty-tune").forEach { sub ->
                                File(f, sub).let { if (it.exists()) possibleBinPaths.add(it) }
                            }
                        }
                    }
                    possibleBinPaths.addAll(listOfNotNull(
                        File("/opt/kitty-tune/bin/KittyTune"),
                        File("/opt/kitty-tune/bin/kitty-tune"),
                        File("/opt/KittyTune/bin/KittyTune"),
                        File("/opt/KittyTune/bin/kittytune"),
                        File("/usr/bin/KittyTune"),
                        File("/usr/bin/kittytune"),
                        File("/usr/bin/kitty-tune"),
                        File("/usr/local/bin/KittyTune"),
                        File("/usr/local/bin/kittytune"),
                        File("/usr/local/bin/kitty-tune")
                    ))
                    val existingBin = possibleBinPaths.firstOrNull { it.exists() && it.isFile }
                    if (existingBin != null) {
                        val devNull = File("/dev/null")
                        val args = mutableListOf<String>()
                        when {
                            File("/usr/bin/systemd-run").exists() -> args.addAll(listOf(
                                "systemd-run", 
                                "--user", 
                                "--setenv=DISPLAY=${System.getenv("DISPLAY") ?: ":0"}",
                                "--setenv=WAYLAND_DISPLAY=${System.getenv("WAYLAND_DISPLAY") ?: ""}",
                                "--setenv=XDG_RUNTIME_DIR=${System.getenv("XDG_RUNTIME_DIR") ?: ""}",
                                existingBin.absolutePath
                            ))
                            File("/usr/bin/gtk-launch").exists() -> args.addAll(listOf("gtk-launch", "kitty-tune"))
                            File("/usr/bin/dex").exists() -> args.addAll(listOf("dex", "/usr/share/applications/kitty-tune.desktop"))
                            else -> args.add(existingBin.absolutePath)
                        }

                        try {
                            ProcessBuilder(args)
                                .directory(File("/"))
                                .redirectOutput(devNull)
                                .redirectError(devNull)
                                .start()
                        } catch (e: Exception) {
                            val cmdStr = args.joinToString(" ") { if (it.contains(" ")) "\"$it\"" else it }
                            NativeCLibrary.INSTANCE?.system("$cmdStr >/dev/null 2>&1 &")
                        }
                        
                        try {
                            ProcessBuilder("kill", "-9", ProcessHandle.current().pid().toString()).start()
                        } catch (e: Exception) {
                            NativeCLibrary.INSTANCE?.system("kill -9 ${ProcessHandle.current().pid()}")
                        }
                        Thread.sleep(1000)
                    }
                }
                osName.contains("mac") -> {
                    val devNull = File("/dev/null")
                    val jpackagePath = System.getProperty("jpackage.app.path")
                    if (!jpackagePath.isNullOrBlank() && File(jpackagePath).exists()) {
                        ProcessBuilder("open", "-a", jpackagePath)
                            .redirectOutput(devNull)
                            .redirectError(devNull)
                            .start()
                        launched = true
                    } else {
                        ProcessBuilder("open", "-a", "KittyTune")
                            .redirectOutput(devNull)
                            .redirectError(devNull)
                            .start()
                        launched = true
                    }
                }
            }
            if (!launched) {
                relaunchJavaDevMode()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            relaunchJavaDevMode()
        }
        if (launched) {
            Thread.sleep(500)
        }
        if (System.getProperty("os.name", "").lowercase().let { it.contains("nux") || it.contains("nix") }) {
            try {
                ProcessBuilder("kill", "-9", ProcessHandle.current().pid().toString()).start()
            } catch (e: Exception) {
                NativeCLibrary.INSTANCE?.system("kill -9 ${ProcessHandle.current().pid()}")
            }
            Thread.sleep(1000)
        } else {
            kotlin.system.exitProcess(0)
        }
    }

    private fun relaunchJavaDevMode() {
        try {
            val osName = System.getProperty("os.name", "").lowercase()
            val javaExeName = if (osName.contains("win")) "bin/java.exe" else "bin/java"
            val javaBin = File(System.getProperty("java.home"), javaExeName)
            val classPath = System.getProperty("java.class.path")
            if (javaBin.exists() && !classPath.isNullOrBlank()) {
                val devNull = if (osName.contains("win")) File("NUL") else File("/dev/null")
                ProcessBuilder(javaBin.absolutePath, "-cp", classPath, "com.alananasss.kittytune.MainKt")
                    .redirectOutput(devNull)
                    .redirectError(devNull)
                    .start()
            } else {
                com.alananasss.kittytune.core.Toaster.show(com.alananasss.kittytune.core.str("update_restarting_toast"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun cleanupOldUpdateCache() {
        try {
            AppDirs.cacheDir.listFiles()?.forEach { f ->
                if (f.name.startsWith("update_") && f.isFile) {
                    runCatching { f.delete() }
                }
            }
        } catch (_: Exception) {}
    }

    fun cancelDownload() {
        _downloadProgress.value = 0f
        _downloadSize.value = 0L
        if (releaseInfo != null) {
            _status.value = UpdateStatus.AVAILABLE
        } else {
            _status.value = UpdateStatus.IDLE
        }
        cleanupOldUpdateCache()
        _isDialogVisible.value = false
    }

    fun dismiss() {
        if (_status.value == UpdateStatus.DOWNLOADING || _status.value == UpdateStatus.PAUSED) {
            cancelDownload()
        } else {
            _isDialogVisible.value = false
        }
    }

    private fun isNewerVersion(current: String, remote: String): Boolean {
        return try {
            val cleanCurrent = current.replace(Regex("[^0-9.]"), "")
            val cleanRemote = remote.replace(Regex("[^0-9.]"), "")
            val v1 = cleanCurrent.split(".").map { it.toIntOrNull() ?: 0 }
            val v2 = cleanRemote.split(".").map { it.toIntOrNull() ?: 0 }
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

private interface NativeCLibrary : com.sun.jna.Library {
    fun system(cmd: String): Int

    companion object {
        val INSTANCE: NativeCLibrary? by lazy {
            runCatching {
                com.sun.jna.Native.load("c", NativeCLibrary::class.java) as NativeCLibrary
            }.getOrNull()
        }
    }
}
