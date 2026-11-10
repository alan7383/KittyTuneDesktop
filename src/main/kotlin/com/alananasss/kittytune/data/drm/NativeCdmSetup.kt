package com.alananasss.kittytune.data.drm

import com.alananasss.kittytune.data.network.ProxyManager
import com.alananasss.kittytune.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * The native route: Chrome's Linux Widevine module, run **inside a Windows process** by KittyTune's
 * own loader (`src/main/cpp/widevinelinux`).
 *
 * There is no virtual machine, no Linux distribution, no Windows feature to enable and therefore no
 * administrator prompt and no restart. The daemon is a single 200 KB executable the app already
 * ships; the only thing downloaded is Google's own `libwidevinecdm.so`, taken from the Chrome package
 * at setup and kept under `%LOCALAPPDATA%\KittyTune\cdm`.
 *
 * The module is **not bundled** with KittyTune: it is Google's proprietary binary, it is not
 * redistributable, and it changes with every Chrome release — a copy frozen inside an installer would
 * be both a licence problem and a version-drift problem. Downloading it at setup from Google's own
 * server is what the WSL route already did.
 */
internal object NativeCdmSetup {

    private const val TAG = "NativeCdmSetup"

    /** Chrome's own package, the canonical source the WSL route uses too. */
    private const val CHROME_DEB =
        "https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb"

    /** The member inside the package. Chrome has shipped this layout for years. */
    private const val CDM_MEMBER =
        "./opt/google/chrome/WidevineCdm/_platform_specific/linux_x64/libwidevinecdm.so"

    private const val MODULE_NAME = "libwidevinecdm.so"
    private const val HOST_NAME = "ktcdm.exe"
    private const val CONFIG_NAME = "cdm-sidecar.json"

    /** A real module is ~21 MB; anything much smaller is a failed extraction. */
    private const val MINIMUM_MODULE_BYTES = 8L * 1024 * 1024

    val windows: Boolean = System.getProperty("os.name").startsWith("Windows", true)

    /** Where the module, the daemon and the scratch files live. User-writable: no elevation. */
    val installDirectory: File
        get() {
            val base = System.getenv("LOCALAPPDATA") ?: System.getProperty("java.io.tmpdir")
            return File(base, "KittyTune/cdm")
        }

    val moduleFile: File get() = File(installDirectory, MODULE_NAME)

    /** The daemon under its default name; the real one is content-addressed, see [extractDaemon]. */
    val hostFile: File get() = File(installDirectory, HOST_NAME)

    /** The configuration file [CdmSidecar] already reads. Written here, never hand-edited. */
    val configFile: File
        get() {
            val base = System.getenv("LOCALAPPDATA") ?: System.getProperty("java.io.tmpdir")
            return File(base, "KittyTune/$CONFIG_NAME")
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
        // Stop ours first: Windows will not delete a running executable, and a leftover copy would
        // make the next install look broken.
        runCatching { CdmSidecar.reset() }
        Thread.sleep(150)
        runCatching { moduleFile.delete() }
        runCatching { hostFile.delete() }
        runCatching { configFile.delete() }
        runCatching {
            installDirectory.listFiles()
                ?.filter { it.name.startsWith("ktcdm") || it.name.startsWith("data.tar") || it.name.endsWith(".deb") }
                ?.forEach { it.delete() }
        }
        com.alananasss.kittytune.data.StreamResolver.forgetStreams()
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

    /** Downloads Chrome's package, streaming so a 140 MB file never sits in the heap. */
    private fun downloadChrome(report: (String) -> Unit): File? = runCatching {
        val target = File(installDirectory, "chrome.deb")
        val request = Request.Builder().url(CHROME_DEB).build()
        ProxyManager.getOkHttpClient().newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                report("  download: HTTP ${response.code}")
                return@runCatching null
            }
            val total = response.body?.contentLength() ?: -1L
            var copied = 0L
            var lastReport = 0L
            response.body?.byteStream()?.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0 && copied - lastReport > total / 20) {
                            lastReport = copied
                            report("  download: ${copied / (1024 * 1024)} of ${total / (1024 * 1024)} MiB")
                        }
                    }
                }
            }
            report("  download: ${target.length() / (1024 * 1024)} MiB")
        }
        target
    }.getOrNull()

    /**
     * Pulls the module out of the package with Windows' own `tar.exe` (bsdtar). A `.deb` is an `ar`
     * archive containing `data.tar.xz`, which bsdtar reads, so no external tool has to be shipped or
     * installed — and the module is written straight to its destination, without a shell.
     */
    private fun extractModule(packageFile: File, destination: File, report: (String) -> Unit): Boolean {
        val tar = File(System.getenv("SystemRoot") ?: "C:\\Windows", "System32/tar.exe")
        if (!tar.isFile) {
            report("  extract: tar.exe not found")
            return false
        }
        val payload = File(installDirectory, "data.tar.xz")
        val first = ProcessBuilder(tar.absolutePath, "-xf", packageFile.absolutePath, "data.tar.xz")
            .directory(installDirectory)
            .redirectErrorStream(true)
            .start()
        val firstOutput = first.inputStream.readBytes().toString(Charsets.UTF_8)
        if (!first.waitFor(120, TimeUnit.SECONDS) || first.exitValue() != 0) {
            report("  extract: tar failed (${firstOutput.take(160).trim()})")
            payload.delete()
            return false
        }
        destination.delete()
        val second = ProcessBuilder(tar.absolutePath, "-xOf", payload.absolutePath, CDM_MEMBER)
            .redirectErrorStream(false)
            .redirectOutput(destination)
            .start()
        val secondError = second.errorStream.readBytes().toString(Charsets.UTF_8)
        if (!second.waitFor(120, TimeUnit.SECONDS) || second.exitValue() != 0) {
            report("  extract: ${secondError.take(160).trim().ifEmpty { "tar exited ${second.exitValue()}" }}")
            destination.delete()
            payload.delete()
            return false
        }
        payload.delete()
        report("  extract: ${destination.length() / (1024 * 1024)} MiB")
        return true
    }

    /** A Linux x86-64 ELF, and a plausible size. Catches a truncated download before it is used. */
    private fun isUsableModule(file: File): Boolean = runCatching {
        if (!file.isFile || file.length() < MINIMUM_MODULE_BYTES) return false
        file.inputStream().use { input ->
            val header = ByteArray(20)
            if (input.read(header) != header.size) return false
            header[0] == 0x7f.toByte() && header[1] == 'E'.code.toByte() &&
                header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte() &&
                header[4] == 2.toByte() && header[5] == 1.toByte() &&
                // e_machine at offset 18, little-endian: 0x3e is x86-64.
                header[18] == 0x3e.toByte() && header[19] == 0.toByte()
        }
    }.getOrDefault(false)

    private fun writeConfiguration(host: File, module: File) {
        val command = JSONArray().put(host.absolutePath).put(module.absolutePath)
        val json = JSONObject().put("command", command)
        configFile.parentFile?.mkdirs()
        configFile.writeText(json.toString(2))
    }
}
