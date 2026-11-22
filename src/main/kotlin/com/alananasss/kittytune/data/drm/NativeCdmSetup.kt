package com.alananasss.kittytune.data.drm

import com.alananasss.kittytune.core.AtomicFiles
import com.alananasss.kittytune.data.network.ProxyManager
import com.alananasss.kittytune.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * The native route: Chrome's Linux Widevine module, run **inside a Windows process** by KittyTune's
 * own loader (`src/main/cpp/widevinelinux`).
 *
 * There is no virtual machine, no Linux distribution, no Windows feature to enable and therefore no
 * administrator prompt and no restart. The daemon is a single 200 KB executable the app already
 * ships; the only thing downloaded is Google's own `libwidevinecdm.so`, taken from the Chrome package
 * at setup and kept under `%LOCALAPPDATA%\KittyTuneCdm`.
 *
 * The module is **not bundled** with KittyTune: it is Google's proprietary binary, it is not
 * redistributable, and it changes with every Chrome release — a copy frozen inside an installer would
 * be both a licence problem and a version-drift problem. Downloading it at setup from Google's own
 * server is what the WSL route already did.
 */
internal object NativeCdmSetup {

    private const val TAG = "NativeCdmSetup"

    /** Chrome's own package, the canonical source the WSL route uses too. */
    /** Below this a downloaded package is a fragment, not Chrome: the real one is ~140 MB. */
    private const val MINIMUM_PACKAGE_BYTES = 64L * 1024 * 1024

    private const val CHROME_DEB =
        "https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb"

    private const val MODULE_NAME = "libwidevinecdm.so"
    private const val HOST_NAME = "ktcdm.exe"
    private const val CONFIG_NAME = "cdm-sidecar.json"

    val windows: Boolean = System.getProperty("os.name").startsWith("Windows", true)

    private val localAppData: File
        get() = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("java.io.tmpdir"))

    /**
     * Where the module, the daemon, the configuration and the scratch files live. User-writable: no elevation.
     *
     * Deliberately **outside** `%LOCALAPPDATA%\KittyTune`: that folder is the MSI's install directory, and
     * jpackage's installer removes it recursively on uninstall, which every update performs on the
     * previous version. Kept there, the module vanished with each update (msiexec log: WixRemoveFoldersEx
     * on `KittyTune\cdm\`).
     */
    val installDirectory: File get() = File(localAppData, "KittyTuneCdm")

    val moduleFile: File get() = File(installDirectory, MODULE_NAME)

    /** The daemon under its default name; the real one is content-addressed, see [extractDaemon]. */
    val hostFile: File get() = File(installDirectory, HOST_NAME)

    /** The configuration file [CdmSidecar] reads. Written here, never hand-edited. */
    val configFile: File get() = File(installDirectory, CONFIG_NAME)

    /** Where versions up to 1.7.0 kept everything, inside the install directory. */
    private val legacyConfigFile: File get() = File(localAppData, "KittyTune/$CONFIG_NAME")

    /**
     * Moves an installation an older version left inside the install directory to [installDirectory],
     * before the next update deletes it. Only a native one: a configuration that drives `wsl.exe` is
     * the dropped backend, and [WslCleanup] owns it.
     */
    @Synchronized
    fun migrateLegacyInstall(configFile: File = this.configFile, legacyConfigFile: File = this.legacyConfigFile) {
        if (!windows || configFile.isFile || !legacyConfigFile.isFile) return
        runCatching {
            val array = JSONObject(legacyConfigFile.readText()).getJSONArray("command")
            val command = (0 until array.length()).map { array.getString(it) }
            if (command.size < 2) return
            val host = File(command[0])
            val module = File(command[1])
            if (!host.name.startsWith("ktcdm", true) || !host.isFile || !module.isFile) return
            val installDirectory = configFile.parentFile
            val legacyDirectory = File(legacyConfigFile.parentFile, "cdm")
            installDirectory.mkdirs()
            val newHost = File(installDirectory, host.name)
            val newModule = File(installDirectory, MODULE_NAME)
            host.copyTo(newHost, overwrite = true)
            module.copyTo(newModule, overwrite = true)
            if (newHost.length() != host.length() || !isUsableModule(newModule)) return
            newHost.setExecutable(true)
            writeConfiguration(newHost, newModule, configFile)
            Logger.d(TAG, "moved the CDM out of the install directory to ${installDirectory.absolutePath}")
            runCatching { legacyConfigFile.delete() }
            if (host.parentFile?.canonicalFile == legacyDirectory.canonicalFile) runCatching { host.delete() }
            if (module.parentFile?.canonicalFile == legacyDirectory.canonicalFile) runCatching { module.delete() }
            runCatching { if (legacyDirectory.listFiles()?.isEmpty() == true) legacyDirectory.delete() }
        }.onFailure { Logger.w(TAG, "could not move the legacy CDM: ${it.message}") }
    }

    enum class Step { CHECK, HOST, DOWNLOAD, EXTRACT, CONFIG, PROBE, CONNECT }

    enum class State {
        /** Nothing installed, or the pieces do not add up. */
        MISSING,

        /** Module, daemon and configuration are in place; only a probe has not run yet. */
        INSTALLED,
    }

    /**
     * The command the configuration file names, or null when there is none.
     *
     * This is the truth about what is installed: the daemon's file name carries its hash (see
     * [extractDaemon]), so it cannot be guessed — it has to be read back.
     */
    fun configuredCommand(): List<String>? = runCatching {
        migrateLegacyInstall()
        if (!configFile.isFile) return@runCatching null
        val array = JSONObject(configFile.readText()).getJSONArray("command")
        (0 until array.length()).map { array.getString(it) }
    }.getOrNull()?.takeIf { it.size >= 2 }

    fun state(): State {
        val command = configuredCommand() ?: return State.MISSING
        val host = File(command[0])
        val module = File(command[1])
        return if (host.isFile && module.isFile) State.INSTALLED else State.MISSING
    }

    fun isInstalled(): Boolean = state() == State.INSTALLED

    /**
     * Installs or repairs the native route: extracts the daemon the app ships, downloads Chrome's
     * package, takes the module out of it, writes the sidecar configuration and proves the daemon
     * answers.
     *
     * Idempotent: an already-downloaded module is reused, and a working installation only needs the
     * probe. Reports each phase through [onStep] and a human line through [onLine].
     */
    suspend fun provision(
        onStep: (Step) -> Unit = {},
        onLine: (String) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        require(windows) { "the native CDM route is a Windows feature" }
        val report = { line: String ->
            Logger.d(TAG, line)
            onLine(line)
        }
        try {
            onStep(Step.CHECK)
            installDirectory.mkdirs()
            report("  install directory: ${installDirectory.absolutePath}")

            onStep(Step.HOST)
            val host = extractDaemon() ?: run {
                report("  result: FAILED (the CDM daemon is missing from this build)")
                return@withContext false
            }
            report("  daemon: ${host.name} (${host.length() / 1024} KiB)")

            onStep(Step.DOWNLOAD)
            val module = moduleFile
            if (isUsableModule(module)) {
                report("  module: already present (${module.length() / (1024 * 1024)} MiB), nothing to extract")
                // Reported even though it does nothing: the assistant draws every step, and a step
                // that is skipped without saying so leaves its tick empty and the bar short.
                onStep(Step.EXTRACT)
            } else {
                val packageFile = downloadChrome(report) ?: run {
                    report("  result: FAILED (could not download the Widevine module)")
                    return@withContext false
                }
                onStep(Step.EXTRACT)
                if (!extractModule(packageFile, module, report)) {
                    runCatching { packageFile.delete() }
                    report("  result: FAILED (could not extract libwidevinecdm.so)")
                    return@withContext false
                }
                packageFile.delete()
            }
            if (!isUsableModule(module)) {
                report("  result: FAILED (the extracted module is not a Linux x86-64 ELF)")
                return@withContext false
            }

            onStep(Step.CONFIG)
            writeConfiguration(host, module)
            report("  configuration: ${configFile.absolutePath}")

            // Registering is a real request, not "the file exists": a daemon that cannot load the
            // module must fail here, where the user can see why.
            onStep(Step.PROBE)
            val command = listOf(host.absolutePath, module.absolutePath)
            val failure = CdmSidecar.probeCommand(command)
            if (failure != null) {
                report("  probe: $failure")
                report("  result: FAILED (the daemon did not answer)")
                return@withContext false
            }
            report("  probe: the daemon answers")
            com.alananasss.kittytune.data.local.PlayerPreferences().setNativeCdmInstalled(true)

            onStep(Step.CONNECT)
            // The method is the native one by construction now: drop anything resolved or licensed
            // under the previous configuration (an old WSL sidecar, a different module).
            CdmSidecar.reset()
            com.alananasss.kittytune.data.StreamResolver.forgetStreams()
            report("  result: OK")
            true
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            report("  result: FAILED (${e.message ?: e::class.simpleName})")
            false
        }
    }

    /** Removes the module, every daemon copy and the configuration. Keeps nothing else. */
    fun uninstall() {
        com.alananasss.kittytune.data.local.PlayerPreferences().setNativeCdmInstalled(false)
        // Stop ours first: Windows will not delete a running executable, and a leftover copy would
        // make the next install look broken.
        runCatching { CdmSidecar.reset() }
        Thread.sleep(150)
        runCatching { moduleFile.delete() }
        runCatching { hostFile.delete() }
        runCatching { configFile.delete() }
        runCatching { legacyConfigFile.delete() }
        runCatching {
            installDirectory.listFiles()
                ?.filter { it.name.startsWith("ktcdm") || it.name.startsWith("data.tar") || it.name.endsWith(".deb") }
                ?.forEach { it.delete() }
        }
        com.alananasss.kittytune.data.StreamResolver.forgetStreams()
    }

    /**
     * Puts the module back, without asking, for someone who had installed it and lost it.
     *
     * Keeping the module out of the install directory protects it from now on, but the update *to* this
     * version was run by the previous one's uninstaller, which had already deleted it. The choice is
     * stored (the marker, or protected tracks on after the assistant), so redo the setup in the background
     * rather than leave every protected track falling back. At most one try a day.
     */
    fun repairIfLost() {
        if (!windows) return
        Thread({
            runCatching {
                val prefs = com.alananasss.kittytune.data.local.PlayerPreferences()
                if (isInstalled()) {
                    if (!prefs.getNativeCdmInstalled()) prefs.setNativeCdmInstalled(true)
                    return@runCatching
                }
                val hadIt = prefs.getNativeCdmInstalled() ||
                    (prefs.getProtectedTracksEnabled() && prefs.getDrmOnboardingDone())
                if (!hadIt) return@runCatching
                val now = System.currentTimeMillis()
                if (now - prefs.getNativeCdmRepairAttemptAt() < 24L * 60 * 60 * 1000) return@runCatching
                prefs.setNativeCdmRepairAttemptAt(now)
                Logger.d(TAG, "the CDM is gone (removed by an update); reinstalling it")
                val ok = kotlinx.coroutines.runBlocking { provision() }
                Logger.d(TAG, "automatic reinstall: ${if (ok) "OK" else "failed"}")
            }
        }, "KittyTune-cdm-repair").apply { isDaemon = true; start() }
    }

    /**
     * Copies the daemon out of the packaged resources, under a name that carries its hash.
     *
     * The name matters: Windows refuses to overwrite a running executable, so an app update that
     * rebuilt `ktcdm.exe` while a protected track was playing would fail to install it (measured: the
     * file is locked by the daemon still inside its 4 s grace window, and the setup reported the
     * daemon as "missing from this build"). A content-addressed name can always be written beside the
     * running one, and the configuration is then repointed at it.
     */
    private fun extractDaemon(): File? = runCatching {
        val bytes = javaClass.getResourceAsStream("/native/widevinelinux/$HOST_NAME")?.use { it.readBytes() }
            ?: listOf(
                File("build/generated/native-resources/native/widevinelinux/$HOST_NAME"),
                File("build/generated/native-resources/native/$HOST_NAME"),
                File("build/widevinelinux/$HOST_NAME"),
            ).firstOrNull { it.isFile }?.readBytes()
            ?: return@runCatching null
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }.take(16)
        installDirectory.mkdirs()
        val target = File(installDirectory, "ktcdm-$hash.exe")
        if (!target.isFile || target.length() != bytes.size.toLong()) {
            target.outputStream().use { output -> output.write(bytes) }
        }
        target.setExecutable(true)
        // Older builds left copies behind (the default name too): never reused, and each one a
        // megabyte. A running one cannot be deleted; best effort is enough.
        installDirectory.listFiles()
            ?.filter { it.name.startsWith("ktcdm") && it.name != target.name }
            ?.forEach { stale -> runCatching { stale.delete() } }
        target
    }.getOrNull()

    /**
     * Downloads Chrome's package, streaming so a 140 MB file never sits in the heap.
     *
     * Written to a `.part` first and only renamed once it is whole: closing the app mid-download used
     * to leave a truncated `chrome.deb` behind (measured: 2 MB of 140), and the next attempt then
     * failed at extraction with a package that looked present.
     */
    private fun downloadChrome(report: (String) -> Unit): File? = runCatching {
        installDirectory.mkdirs()
        val target = File(installDirectory, "chrome.deb")
        val partial = File(installDirectory, "chrome.deb.part")
        // Whatever an earlier attempt left is not trustworthy: a package is all or nothing.
        runCatching { target.delete() }
        runCatching { partial.delete() }

        val request = Request.Builder().url(CHROME_DEB).build()
        var expected = -1L
        var copied = 0L
        ProxyManager.getOkHttpClient().newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                report("  download: HTTP ${response.code}")
                return@runCatching null
            }
            expected = response.body?.contentLength() ?: -1L
            var lastReport = 0L
            response.body?.byteStream()?.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (expected > 0 && copied - lastReport > expected / 20) {
                            lastReport = copied
                            report("  download: ${copied / (1024 * 1024)} of ${expected / (1024 * 1024)} MiB")
                        }
                    }
                }
            }
        }

        if (expected > 0 && copied != expected) {
            report("  download: interrupted after $copied of $expected bytes")
            runCatching { partial.delete() }
            return@runCatching null
        }
        if (copied < MINIMUM_PACKAGE_BYTES) {
            report("  download: only $copied bytes, that is not Chrome's package")
            runCatching { partial.delete() }
            return@runCatching null
        }
        runCatching { com.alananasss.kittytune.core.AtomicFiles.replace(partial, target) }
        if (!target.isFile) {
            report("  download: could not put the package in place")
            runCatching { partial.delete() }
            return@runCatching null
        }
        report("  download: ${target.length() / (1024 * 1024)} MiB")
        target
    }.getOrElse {
        report("  download: ${it.message ?: it::class.simpleName}")
        null
    }

    private fun extractModule(packageFile: File, destination: File, report: (String) -> Unit): Boolean = try {
        ChromeCdmArchive.extract(packageFile, destination)
        report("  extract: ${destination.length() / (1024 * 1024)} MiB")
        true
    } catch (e: Exception) {
        report("  extract: ${e.message ?: e.javaClass.simpleName}")
        false
    }

    private fun isUsableModule(file: File): Boolean = ChromeCdmArchive.isUsableModule(file)

    private fun writeConfiguration(host: File, module: File, configFile: File = this.configFile) {
        val command = JSONArray().put(host.absolutePath).put(module.absolutePath)
        val json = JSONObject().put("command", command)
        val directory = configFile.parentFile
        directory.mkdirs()
        val temporary = File.createTempFile("cdm-sidecar-", ".tmp", directory)
        try {
            temporary.writeText(json.toString(2))
            AtomicFiles.replace(temporary, configFile)
        } finally {
            temporary.delete()
        }
    }
}
