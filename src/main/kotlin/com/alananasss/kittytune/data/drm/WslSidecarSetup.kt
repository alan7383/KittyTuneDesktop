package com.alananasss.kittytune.data.drm

import com.alananasss.kittytune.data.StreamResolver
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.network.ProxyManager
import com.alananasss.kittytune.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * How protected tracks are played on Windows, chosen by the user.
 *
 * [PLAYREADY] is the OS renderer: nothing to install, but it never hands a sample back, so effects,
 * the visualizer and downloads stay off. [WSL] runs Chrome's Linux Widevine CDM inside a tiny Linux
 * distribution through [CdmSidecar]: the app decodes the audio itself, so everything works. When WSL
 * is chosen but does not answer, playback still falls back to PlayReady rather than skipping the track.
 *
 * Other platforms ignore this: they load the browser's CDM in-process.
 */
enum class ProtectedBackend(val key: String) {
    PLAYREADY("playready"),
    WSL("wsl");

    companion object {
        val current: ProtectedBackend
            get() = if (PlayerPreferences().getProtectedBackend() == WSL.key) WSL else PLAYREADY

        /** Switches the method and drops everything resolved or licensed under the previous one. */
        fun select(backend: ProtectedBackend) {
            PlayerPreferences().setProtectedBackend(backend.key)
            CdmSidecar.reset()
            StreamResolver.forgetStreams()
        }
    }
}

/**
 * Sets up the CDM sidecar in a tiny, dedicated WSL distribution from inside the app — downloaded, with
 * no command typed and the UAC prompt handled for the user.
 *
 * The old method built a full Ubuntu (apt, a C++ toolchain, a VM that held ~1.5 GB). This one imports
 * a ~14 MB rootfs that is just enough Linux to run the daemon. Imported **`--version 1` (WSL1)** it is
 * not a VM at all — the `lxcore.sys` kernel driver runs the daemon as a near-native process, so
 * `vmmemWSL` stays at zero; `--version 2` (WSL2) is the fallback when WSL1 cannot be enabled. The
 * Widevine module itself is not bundled (it cannot be redistributed): the rootfs carries `curl`,
 * `busybox` and `xz`, and fetches it from Chrome's `.deb` at setup, as the old flow did.
 *
 * Enabling the Windows feature WSL1 needs ([FEATURE_WSL1]) takes an administrator and a restart; the
 * assistant triggers Windows' own prompt and, after the reboot, resumes itself ([pendingVersion]).
 */
internal object WslSidecarSetup {

    private const val TAG = "WslSidecarSetup"

    /** The private distribution KittyTune imports. Fixed, so it is never confused with the user's own. */
    const val DISTRO = "kittytune-cdm"

    const val INSTALL_DIR = "/opt/kittytune"
    const val DAEMON = "$INSTALL_DIR/ktcdm"
    const val CDM = "$INSTALL_DIR/libwidevinecdm.so"

    /** The Windows optional components. WSL1 runs on the first; WSL2 on the second. */
    const val FEATURE_WSL1 = "Microsoft-Windows-Subsystem-Linux"
    const val FEATURE_WSL2 = "VirtualMachinePlatform"

    /**
     * The minimal rootfs, built by `scripts/build-wsl1-rootfs.sh` and published by the "Build CDM rootfs"
     * workflow under an **immutable, versioned** release (`cdm-rootfs-v<N>`): a published version is never
     * overwritten, so [ROOTFS_SHA256] stays valid. Changing the rootfs means publishing `v<N+1>` and bumping
     * both constants here together.
     */
    const val ROOTFS_VERSION = 1
    const val ROOTFS_URL =
        "https://github.com/alan7383/KittyTuneDesktop/releases/download/cdm-rootfs-v$ROOTFS_VERSION/ktroot.tgz"
    const val ROOTFS_SHA256 = "fc7d52fd63502291cf759c51c34cfe3b538400d13f3c377f4985b918493dbdd9"

    private const val CHROME_DEB =
        "https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb"

    private const val CONFIG_NAME = "cdm-sidecar.json"

    val windows: Boolean = System.getProperty("os.name").startsWith("Windows", true)

    private val wslExe: File
        get() = File(System.getenv("SystemRoot") ?: "C:\\Windows", "System32\\wsl.exe")

    private val localAppData: File?
        get() = System.getenv("LOCALAPPDATA")?.let { File(it) }

    private val configFile: File?
        get() = localAppData?.let { File(it, "KittyTune/$CONFIG_NAME") }

    private val distroDir: File?
        get() = localAppData?.let { File(it, "KittyTune/wsl/$DISTRO") }

    enum class State {
        /** No WSL on this machine at all: `wsl.exe` is missing. */
        NO_WSL,

        /** WSL is present but the component WSL1 (or, for the fallback, WSL2) needs is off: enable + reboot. */
        FEATURE_OFF,

        /** The feature is on and WSL can run; KittyTune's distribution is not imported yet. */
        NOT_SET_UP,

        /** The distribution is imported and the daemon + CDM are in it. */
        SET_UP,
    }

    /**
     * Where WSL stands on this machine, found without starting anything heavy.
     *
     * @param wsl1Available the WSL1 component is enabled (so `--version 1` imports work).
     * @param wsl2Available the WSL2 component is enabled (the fallback).
     */
    data class Detection(
        val state: State,
        val wsl1Available: Boolean,
        val wsl2Available: Boolean,
        /** The version our distribution is imported as, when [State.SET_UP] (1 or 2), else null. */
        val installedVersion: Int? = null,
    ) {
        /**
         * Whether the Windows component [version] needs is on. Readiness is always judged for the version
         * the user picked: WSL2's component being on does not make a WSL1 import possible, and vice versa.
         */
        fun featureOn(version: Int): Boolean = if (version == 1) wsl1Available else wsl2Available
    }

    enum class Step { DOWNLOAD, IMPORT, CDM, PROBE, CONNECT }

    class SetupException(val step: Step, message: String) : Exception(message)

    /** The outcome of asking Windows (elevated) to turn a feature on. */
    data class EnableResult(val ok: Boolean, val refused: Boolean, val log: String)

    // ------------------------------------------------------------------------------------------------
    // Detection
    // ------------------------------------------------------------------------------------------------

    /** Slow (it may start the distribution to check the daemon), so callers run it off the UI thread. */
    suspend fun detect(): Detection = withContext(Dispatchers.IO) {
        if (!windows || !wslExe.isFile) {
            return@withContext Detection(State.NO_WSL, wsl1Available = false, wsl2Available = false)
        }
        val wsl1 = featureEnabled(FEATURE_WSL1)
        val wsl2 = featureEnabled(FEATURE_WSL2)
        if (!wsl1 && !wsl2) {
            return@withContext Detection(State.FEATURE_OFF, wsl1Available = false, wsl2Available = false)
        }
        val imported = parseAllDistros(
            runCatching { run(listOf("--list", "--quiet"), timeoutMs = 20_000) }.getOrNull()?.output.orEmpty()
        ).any { it.equals(DISTRO, ignoreCase = true) }
        if (!imported) {
            return@withContext Detection(State.NOT_SET_UP, wsl1, wsl2)
        }
        val ready = runCatching {
            run(
                listOf("-d", DISTRO, "-u", "root", "--exec", "test", "-x", DAEMON, "-a", "-f", CDM),
                timeoutMs = 60_000,
            )
        }.getOrNull()?.exit == 0
        val version = runCatching { importedVersion() }.getOrNull()
        Detection(if (ready) State.SET_UP else State.NOT_SET_UP, wsl1, wsl2, version)
    }

    /** The WSL version our distribution is registered as, read from `wsl -l -v`. */
    private suspend fun importedVersion(): Int? = registeredVersions()[DISTRO.lowercase()]

    /** Every registered distribution (lower-cased name) with its WSL version, from `wsl -l -v`. */
    private suspend fun registeredVersions(): Map<String, Int> {
        val out = runCatching { run(listOf("--list", "--verbose"), timeoutMs = 20_000) }.getOrNull()?.output
            ?: return emptyMap()
        return parseVerbose(out)
    }

    /**
     * `wsl -l -v` rows → name to version. The header and the default-distribution `*` are skipped; the
     * version is the last column, whatever language the STATE column is printed in.
     */
    internal fun parseVerbose(output: String): Map<String, Int> = output
        .replace("\u0000", "").replace("﻿", "").replace("�", "")
        .lineSequence()
        .map { it.trim().removePrefix("*").trim() }
        .mapNotNull { line ->
            val parts = line.split(Regex("\\s+"))
            val version = parts.lastOrNull()?.toIntOrNull()?.takeIf { it == 1 || it == 2 } ?: return@mapNotNull null
            if (parts.size < 3) return@mapNotNull null
            parts.first().lowercase() to version
        }
        .toMap()

    /**
     * The WSL version (1 or 2) whose Windows component uninstalling may offer to turn back off, or null:
     * the component our distribution runs on, when it is on and no other distribution needs it (turning
     * it off would break that one).
     */
    suspend fun removableFeature(): Int? = withContext(Dispatchers.IO) {
        if (!windows || !wslExe.isFile) return@withContext null
        val versions = registeredVersions()
        removableFeature(
            ours = versions[DISTRO.lowercase()],
            wsl1On = featureEnabled(FEATURE_WSL1),
            wsl2On = featureEnabled(FEATURE_WSL2),
            versions = versions,
        )
    }

    /**
     * The pure decision behind [removableFeature]: the component of the version our distribution runs on
     * (WSL1 when it is already gone, the default), provided it is on and no other distribution of that
     * version needs it. For WSL2 that component, `VirtualMachinePlatform`, is what every WSL2 distribution
     * runs on (the user's own, Docker's…), so it is only ever offered when ours was the last one.
     */
    internal fun removableFeature(ours: Int?, wsl1On: Boolean, wsl2On: Boolean, versions: Map<String, Int>): Int? {
        val version = ours ?: 1
        val on = if (version == 1) wsl1On else wsl2On
        val othersNeedIt = versions.any { (name, v) -> v == version && name != DISTRO.lowercase() }
        return version.takeIf { on && !othersNeedIt }
    }

    // ------------------------------------------------------------------------------------------------
    // Enabling the Windows feature (UAC), then resuming after the reboot it needs
    // ------------------------------------------------------------------------------------------------

    /**
     * Turns the component on through Windows' own elevation prompt — no command typed — and records
     * that the setup must resume for [version] after the restart.
     *
     * @param version 1 for WSL1 (the light default), 2 for the WSL2 fallback.
     */
    fun enable(version: Int): EnableResult {
        val feature = if (version == 1) FEATURE_WSL1 else FEATURE_WSL2
        val result = runElevated(
            "Enable-WindowsOptionalFeature -Online -FeatureName $feature -All -NoRestart | Out-String"
        )
        if (result.ok) {
            PlayerPreferences().apply {
                setWslSetupPending(true)
                setWslSetupVersion(version)
            }
        }
        return result
    }

    /** The version to resume setting up after a reboot, or null when nothing is pending. */
    fun pendingVersion(): Int? {
        val prefs = PlayerPreferences()
        if (!prefs.getWslSetupPending()) return null
        return prefs.getWslSetupVersion().takeIf { it == 1 || it == 2 }
    }

    /** True once the feature the pending setup needs is actually enabled (the reboot has happened). */
    suspend fun pendingReady(): Boolean = withContext(Dispatchers.IO) {
        val version = pendingVersion() ?: return@withContext false
        featureEnabled(if (version == 1) FEATURE_WSL1 else FEATURE_WSL2)
    }

    fun clearPending() {
        PlayerPreferences().apply {
            setWslSetupPending(false)
            setWslSetupVersion(0)
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Provisioning: download the rootfs, import it, fetch the CDM, probe, connect
    // ------------------------------------------------------------------------------------------------

    /**
     * Imports and wires up the distribution. The feature must already be enabled (see [enable]).
     *
     * @param version 1 (WSL1, light) or 2 (WSL2, fallback).
     * @throws SetupException naming the step that failed and why.
     */
    suspend fun provision(version: Int, onStep: (Step) -> Unit, onLine: (String) -> Unit) {
        withContext(Dispatchers.IO) {
            onStep(Step.DOWNLOAD)
            onLine("== download")
            val tgz = downloadRootfs(onLine)
            try {
                onStep(Step.IMPORT)
                onLine("== import (WSL$version)")
                importDistro(tgz, version, onLine)

                onStep(Step.CDM)
                onLine("== cdm")
                fetchCdm(onLine)

                onStep(Step.PROBE)
                onLine("== probe")
                probeDaemon(onLine)

                onStep(Step.CONNECT)
                onLine("== connect")
                writeConfig()
                ProtectedBackend.select(ProtectedBackend.WSL)
                if (!CdmSidecar.verify()) {
                    throw SetupException(Step.CONNECT, CdmSidecar.lastFailure ?: "the sidecar did not answer")
                }
                clearPending()
                onLine("connected: ${CdmSidecar.describe}")
            } finally {
                runCatching { tgz.delete() }
                // Setup leaves nothing running; the daemon starts on demand later.
                runCatching { run(listOf("--terminate", DISTRO), timeoutMs = 30_000) }
            }
        }
    }

    private fun downloadRootfs(onLine: (String) -> Unit): File {
        val target = File.createTempFile("kittytune-rootfs-", ".tgz")
        onLine("downloading the Linux image (v$ROOTFS_VERSION)")
        try {
            val request = Request.Builder().url(ROOTFS_URL).header("User-Agent", "KittyTune").build()
            ProxyManager.getOkHttpClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw SetupException(Step.DOWNLOAD, "the Linux image could not be downloaded (HTTP ${response.code})")
                }
                val body = response.body ?: throw SetupException(Step.DOWNLOAD, "the Linux image came back empty")
                val total = body.contentLength()
                target.outputStream().use { out ->
                    val input = body.byteStream()
                    val buffer = ByteArray(1 shl 16)
                    var done = 0L
                    var lastStep = -1L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        done += read
                        if (total > 0) {
                            val step = done * 4 / total // a line every 25 %
                            if (step != lastStep) { lastStep = step; onLine("  ${done * 100 / total} %") }
                        }
                    }
                }
            }
            val actual = sha256(target)
            if (!actual.equals(ROOTFS_SHA256, ignoreCase = true)) {
                onLine("expected sha256 $ROOTFS_SHA256")
                onLine("received sha256 $actual")
                throw SetupException(
                    Step.DOWNLOAD,
                    "the downloaded Linux image is not the one this version of KittyTune expects",
                )
            }
            onLine("downloaded and verified (${target.length() / 1024} KB)")
            return target
        } catch (e: Throwable) {
            target.delete()
            if (e is SetupException) throw e
            throw SetupException(Step.DOWNLOAD, "the Linux image could not be downloaded: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private suspend fun importDistro(tgz: File, version: Int, onLine: (String) -> Unit) {
        // A leftover half-import must go first, or --import refuses the name.
        runCatching { run(listOf("--unregister", DISTRO), timeoutMs = 120_000) }
        val dir = distroDir ?: throw SetupException(Step.IMPORT, "LOCALAPPDATA is not set")
        dir.mkdirs()
        val result = run(
            listOf("--import", DISTRO, dir.absolutePath, tgz.absolutePath, "--version", version.toString()),
            timeoutMs = 300_000,
            onLine = onLine,
        )
        if (result.exit != 0) throw SetupException(Step.IMPORT, lastLine(result))
    }

    private suspend fun fetchCdm(onLine: (String) -> Unit) {
        val result = run(
            listOf("-d", DISTRO, "-u", "root", "--exec", "/bin/sh", "-s"),
            CDM_SCRIPT, timeoutMs = 300_000, onLine = onLine,
        )
        if (result.exit != 0) throw SetupException(Step.CDM, lastLine(result))
    }

    private suspend fun probeDaemon(onLine: (String) -> Unit) {
        val result = run(
            listOf("-d", DISTRO, "-u", "root", "--exec", DAEMON, CDM, "--probe"),
            timeoutMs = 120_000, onLine = onLine,
        )
        if (result.exit != 0) throw SetupException(Step.PROBE, "the daemon could not load the Widevine module")
    }

    private fun writeConfig() {
        val directory = configFile?.parentFile?.apply { mkdirs() }
            ?: throw SetupException(Step.CONNECT, "LOCALAPPDATA is not set")
        val command = JSONArray(listOf(wslExe.absolutePath, "-d", DISTRO, "-u", "root", "--exec", DAEMON, CDM))
        val json = JSONObject()
            .put("command", command)
            .put("label", "WSL $DISTRO")
            .put("written", java.time.LocalDateTime.now().withNano(0).toString())
        val temporary = File(directory, "$CONFIG_NAME.tmp")
        temporary.writeText(json.toString(2))
        val target = File(directory, CONFIG_NAME)
        if (!temporary.renameTo(target)) {
            target.delete()
            temporary.renameTo(target)
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Uninstalling
    // ------------------------------------------------------------------------------------------------

    /** Whether there is anything to remove, without starting WSL. Cheap enough to ask on every visit. */
    fun hasLeftovers(): Boolean {
        if (!windows) return false
        return ProtectedBackend.current == ProtectedBackend.WSL ||
            configFile?.isFile == true ||
            distroDir?.exists() == true
    }

    /**
     * Whether another WSL distribution is registered, where KittyTune files may have been left (by an older
     * version, a script or a manual install). Only lists them: no distribution is started.
     */
    suspend fun otherDistributionsRegistered(): Boolean = withContext(Dispatchers.IO) {
        windows && wslExe.isFile && otherDistributions().isNotEmpty()
    }

    /** Whether KittyTune's own distribution is registered. Only lists: nothing is started. */
    suspend fun imported(): Boolean = withContext(Dispatchers.IO) {
        windows && wslExe.isFile && DISTRO.lowercase() in registeredVersions()
    }

    private suspend fun otherDistributions(): List<String> = parseDistros(
        runCatching { run(listOf("--list", "--quiet"), timeoutMs = 20_000) }.getOrNull()?.output.orEmpty()
    ).filterNot { it.equals(DISTRO, ignoreCase = true) }

    /** KittyTune files found in a distribution other than ours, outside the official setup. */
    data class Leftover(val distro: String, val paths: List<String>)

    /**
     * The only places KittyTune ever put files in a distribution: the old in-app setup (`/opt/kittytune`) and
     * the old setup script or the daemon's own storage (`~/.local/share/kittytune`). Anything read back from a
     * distribution must match exactly before it goes near `rm`: no other path is ever removed.
     */
    internal val LEFTOVER_PATH = Regex("""/opt/kittytune|/root/\.local/share/kittytune|/home/[A-Za-z0-9._-]+/\.local/share/kittytune""")

    /** Prints `KT_PATH:<path>` for each of those places that exists. Read-only. */
    internal val SCAN_SCRIPT = """
        for p in /opt/kittytune /root/.local/share/kittytune /home/*/.local/share/kittytune; do
          if [ -e "${'$'}p" ]; then echo "KT_PATH:${'$'}p"; fi
        done
        exit 0
    """.trimIndent() + "\n"

    internal fun parseLeftoverScan(output: String): List<String> = output
        .replace("\u0000", "").replace("﻿", "")
        .lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("KT_PATH:") }
        .map { it.removePrefix("KT_PATH:").trim() }
        .filter { LEFTOVER_PATH.matches(it) }
        .distinct()
        .toList()

    /** Removes exactly [paths] (each re-validated), then lists what is still there. */
    internal fun removeScript(paths: List<String>): String {
        val safe = paths.filter { LEFTOVER_PATH.matches(it) }
        return buildString {
            if (safe.isNotEmpty()) appendLine("rm -rf -- ${safe.joinToString(" ") { "'$it'" }}")
            append(SCAN_SCRIPT)
        }
    }

    /**
     * Looks in every other distribution for KittyTune files. Starting a distribution to look costs a moment
     * (and, for WSL2, its VM), so this only runs when the removal assistant opens; every distribution it
     * had to start is stopped again afterwards.
     */
    suspend fun findLeftovers(onLine: (String) -> Unit = {}): List<Leftover> = withContext(Dispatchers.IO) {
        if (!windows || !wslExe.isFile) return@withContext emptyList()
        val distros = otherDistributions()
        withDistributionsRestored(distros) {
            distros.mapNotNull { distro ->
                onLine("looking in $distro")
                val result = runCatching {
                    run(listOf("-d", distro, "-u", "root", "--exec", "/bin/sh", "-s"), SCAN_SCRIPT, timeoutMs = 60_000)
                }.getOrNull()
                parseLeftoverScan(result?.output.orEmpty()).takeIf { it.isNotEmpty() }?.let { Leftover(distro, it) }
            }
        }
    }

    /**
     * Runs [block] in the given distributions, then stops those of them that were not running before, and
     * the WSL VM when nothing ran at all — unless another program started using WSL meanwhile.
     */
    private suspend fun <T> withDistributionsRestored(distros: List<String>, block: suspend () -> T): T {
        val runningBefore = runningDistributions()
        try {
            return block()
        } finally {
            if (runningBefore != null) {
                for (distro in distros) {
                    if (distro !in runningBefore) runCatching { run(listOf("--terminate", distro), timeoutMs = 30_000) }
                }
                if (runningBefore.isEmpty() && runningDistributions()?.isEmpty() == true) {
                    runCatching { run(listOf("--shutdown"), timeoutMs = 60_000) }
                }
            }
        }
    }

    /** The running distributions, or null when that could not be told (then nothing is stopped). */
    private suspend fun runningDistributions(): List<String>? =
        runCatching { run(listOf("--list", "--running", "--quiet"), timeoutMs = 20_000) }.getOrNull()
            ?.takeIf { it.exit == 0 || it.output.isBlank() }?.output?.let(::parseAllDistros)

    enum class UninstallStep { STOP, DISTRO, LEFTOVERS, SETTINGS, FEATURE }

    class UninstallException(val step: UninstallStep, message: String, val refused: Boolean = false) : Exception(message)

    fun uninstallSteps(removeFeature: Boolean, cleanLeftovers: Boolean = false): List<UninstallStep> = buildList {
        add(UninstallStep.STOP)
        add(UninstallStep.DISTRO)
        if (cleanLeftovers) add(UninstallStep.LEFTOVERS)
        add(UninstallStep.SETTINGS)
        if (removeFeature) add(UninstallStep.FEATURE)
    }

    /**
     * Removes the distribution and KittyTune's link to it; protected tracks go back to PlayReady. Also
     * deletes the [leftovers] chosen — KittyTune files found in other distributions — and, when
     * [featureVersion] is set, turns that version's Windows component back off (elevated, needs a restart).
     *
     * @return true when Windows must restart to finish (the component was removed).
     */
    suspend fun uninstall(
        featureVersion: Int?,
        leftovers: List<Leftover> = emptyList(),
        onStep: (UninstallStep) -> Unit,
        onLine: (String) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        var restart = false
        for (step in uninstallSteps(featureVersion != null, leftovers.isNotEmpty())) {
            onStep(step)
            onLine("== ${step.name.lowercase()}")
            when (step) {
                UninstallStep.STOP -> {
                    ProtectedBackend.select(ProtectedBackend.PLAYREADY)
                    runCatching { run(listOf("--terminate", DISTRO), timeoutMs = 30_000) }
                    onLine("module stopped, protected tracks play through PlayReady")
                }
                UninstallStep.DISTRO -> {
                    // WSL1 keeps the files in the folder, WSL2 an ext4.vhdx disk there: --unregister removes both.
                    val result = runCatching { run(listOf("--unregister", DISTRO), timeoutMs = 300_000, onLine = onLine) }
                        .getOrNull()
                    // Judged by what is registered afterwards, not by wsl.exe's message, which is translated:
                    // a distribution that was already gone is not an error, one still registered is.
                    if (DISTRO.lowercase() in registeredVersions()) {
                        throw UninstallException(step, result?.let { lastLine(it) } ?: "wsl --unregister did not answer")
                    }
                    runCatching { distroDir?.deleteRecursively() }
                    onLine("the KittyTune Linux image is removed")
                }
                UninstallStep.LEFTOVERS -> withDistributionsRestored(leftovers.map { it.distro }) {
                    for (leftover in leftovers) {
                        onLine("cleaning ${leftover.distro}: ${leftover.paths.joinToString(", ")}")
                        val result = runCatching {
                            run(
                                listOf("-d", leftover.distro, "-u", "root", "--exec", "/bin/sh", "-s"),
                                removeScript(leftover.paths), timeoutMs = 120_000,
                            )
                        }.getOrNull()
                        // What is still there afterwards decides, not the exit code.
                        val still = parseLeftoverScan(result?.output.orEmpty()).filter { it in leftover.paths }
                        if (result == null || still.isNotEmpty()) {
                            throw UninstallException(step, "${leftover.distro}: could not remove ${still.ifEmpty { leftover.paths }.joinToString(", ")}")
                        }
                    }
                    onLine("KittyTune's files are removed from the other distributions")
                }
                UninstallStep.SETTINGS -> {
                    configFile?.let { file ->
                        if (file.exists() && !file.delete()) throw UninstallException(step, "could not delete ${file.path}")
                    }
                    clearPending()
                    StreamResolver.forgetStreams()
                    onLine("KittyTune no longer points at WSL")
                }
                UninstallStep.FEATURE -> {
                    val feature = if (featureVersion == 1) FEATURE_WSL1 else FEATURE_WSL2
                    val result = runElevated(
                        "Disable-WindowsOptionalFeature -Online -FeatureName $feature -NoRestart | Out-String"
                    )
                    if (result.refused) throw UninstallException(step, "permission refused", refused = true)
                    if (!result.ok) throw UninstallException(step, "the Windows feature could not be removed")
                    restart = true
                    onLine("$feature turned off; Windows finishes after a restart")
                }
            }
        }
        restart
    }

    // ------------------------------------------------------------------------------------------------
    // Windows feature state + elevation (one reusable elevated-PowerShell runner)
    // ------------------------------------------------------------------------------------------------

    /** Reads an optional component's state without elevation. */
    private fun featureEnabled(feature: String): Boolean = runCatching {
        val process = ProcessBuilder(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
            "(Get-WindowsOptionalFeature -Online -FeatureName $feature).State",
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor(30, TimeUnit.SECONDS)
        output.contains("Enabled", ignoreCase = true) && !output.contains("DisablePending", ignoreCase = true)
    }.getOrDefault(false)

    /**
     * Runs [psBody] as administrator through Windows' own UAC prompt, hidden, and waits.
     *
     * The script writes its transcript to a log we read back, so the daemon's own output survives the
     * elevated process. A refusal of the prompt surfaces as [EnableResult.refused] (exit 1223), not an
     * error, so the UI can say "you declined" rather than "it failed".
     */
    private fun runElevated(psBody: String): EnableResult {
        val work = File(System.getProperty("java.io.tmpdir"), "kittytune-wsl").apply { mkdirs() }
        val script = File(work, "elevate.ps1")
        val log = File(work, "elevate.log").apply { delete() }
        fun quoted(file: File) = "'" + file.absolutePath.replace("'", "''") + "'"
        script.writeText(
            buildString {
                appendLine("\$ErrorActionPreference = 'Continue'")
                appendLine("& {")
                appendLine("  $psBody")
                appendLine("} *>&1 | Out-File -FilePath ${quoted(log)} -Encoding utf8")
                appendLine("exit 0")
            }
        )
        val launch = "try { \$p = Start-Process -FilePath powershell.exe -Verb RunAs -Wait -PassThru -WindowStyle Hidden " +
            "-ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File','\"${script.absolutePath.replace("'", "''")}\"'); " +
            "exit \$p.ExitCode } catch { exit 1223 }"
        return runCatching {
            val process = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", launch)
                .redirectErrorStream(true).start()
            process.inputStream.bufferedReader().readText()
            if (!process.waitFor(15, TimeUnit.MINUTES)) {
                process.destroyForcibly()
                return@runCatching EnableResult(ok = false, refused = false, log = "Windows did not finish in time")
            }
            val transcript = runCatching { log.readText(Charsets.UTF_8) }.getOrNull()
                ?.replace("\uFEFF", "")?.trim().orEmpty()
            runCatching { script.delete(); log.delete() }
            when (process.exitValue()) {
                0 -> EnableResult(ok = true, refused = false, log = transcript)
                1223 -> EnableResult(ok = false, refused = true, log = transcript)
                else -> EnableResult(ok = false, refused = false, log = transcript.ifBlank { "exit ${process.exitValue()}" })
            }
        }.getOrElse { EnableResult(ok = false, refused = false, log = it.message ?: it.javaClass.simpleName) }
    }

    // ------------------------------------------------------------------------------------------------
    // Low-level wsl.exe runner + helpers (kept from the old engine)
    // ------------------------------------------------------------------------------------------------

    private class Result(val exit: Int, val output: String)

    /**
     * Runs `wsl.exe` with [args], feeding [input] on stdin.
     *
     * Scripts travel over stdin, not as arguments, because `wsl.exe` re-parses its own command line.
     * Interruptible: cancelling the caller kills the process instead of leaving it running.
     */
    private suspend fun run(
        args: List<String>,
        input: String? = null,
        timeoutMs: Long,
        onLine: ((String) -> Unit)? = null,
    ): Result = runInterruptible(Dispatchers.IO) {
        val builder = ProcessBuilder(listOf(wslExe.absolutePath) + args).redirectErrorStream(true)
        builder.environment()["WSL_UTF8"] = "1"
        val process = builder.start()
        val output = StringBuilder()
        val reader = Thread({
            runCatching {
                process.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { raw ->
                    val line = raw.replace("\u0000", "").replace("\uFEFF", "").replace("\uFFFD", "").trimEnd('\r')
                    synchronized(output) { output.append(line).append('\n') }
                    // WSL1 prints "Failed to translate <windows PATH entry>" noise we never want to show.
                    if (line.isNotBlank() && !line.startsWith("wsl: Failed to translate") &&
                        !line.startsWith("wsl: Processing /etc/fstab")
                    ) onLine?.invoke(line)
                }
            }
        }, "KittyTune-wsl-setup").apply { isDaemon = true; start() }
        try {
            runCatching {
                process.outputStream.use { stream -> input?.let { stream.write(it.toByteArray(Charsets.UTF_8)) } }
            }
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                reader.join(1000)
                return@runInterruptible Result(-1, synchronized(output) { output.toString() } +
                    "KT_ERROR: no answer within ${timeoutMs / 1000} s\n")
            }
            reader.join(2000)
            Result(process.exitValue(), synchronized(output) { output.toString() })
        } catch (e: InterruptedException) {
            process.destroyForcibly()
            throw e
        }
    }

    private fun lastLine(result: Result): String =
        result.output.lineSequence().firstOrNull { it.startsWith("KT_ERROR:") }?.removePrefix("KT_ERROR:")?.trim()
            ?: result.output.lineSequence().lastOrNull { it.isNotBlank() && !it.startsWith("wsl: ") }
            ?: "exit ${result.exit}"

    /** Every registered distribution, as `wsl -l -q` prints it (older `wsl.exe` answers in UTF-16LE). */
    internal fun parseAllDistros(output: String): List<String> = output
        .replace("\u0000", "")
        .replace("\uFEFF", "")
        .replace("\uFFFD", "")
        .lineSequence()
        .map { it.trim().trim('"') }
        .filter { it.isNotEmpty() }
        .toList()

    internal fun parseDistros(output: String): List<String> = parseAllDistros(output)
        .filterNot { it.startsWith("docker-desktop", true) || it.startsWith("rancher-desktop", true) }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Fetches the Widevine module from Chrome's `.deb` into the distribution, using only tools the
     * rootfs carries. GNU `tar` and `dpkg-deb` fail under WSL1 (newer syscalls return ENOSYS), so the
     * extraction is `busybox ar` + `xz` + `busybox tar`, which use syscalls WSL1 implements.
     */
    internal val CDM_SCRIPT = """
        set -e
        export PATH=/usr/bin:/bin
        if [ -s $CDM ]; then echo "Widevine module already present"; exit 0; fi
        work=${'$'}(mktemp -d)
        cd "${'$'}work"
        echo "downloading Chrome for Linux"
        curl -fsSL --retry 2 -o chrome.deb "$CHROME_DEB" \
          || { echo "KT_ERROR: downloading Chrome for Linux failed, check the internet connection"; exit 69; }
        echo "extracting the Widevine module"
        ar x chrome.deb
        mkdir -p root
        if [ -f data.tar.xz ]; then xz -dc data.tar.xz | tar -C root -xf -
        elif [ -f data.tar.gz ]; then tar -C root -xzf data.tar.gz
        else echo "KT_ERROR: unexpected Chrome package layout"; exit 69; fi
        found=${'$'}(find root -path '*WidevineCdm*linux_x64/libwidevinecdm.so' -type f | head -n 1)
        [ -n "${'$'}found" ] || { echo "KT_ERROR: the Chrome package carried no Widevine module"; exit 69; }
        mkdir -p $INSTALL_DIR
        # cp + chmod + mv rather than `install`: Ubuntu's busybox-static has no `install` applet.
        cp "${'$'}found" $CDM.tmp
        chmod 0755 $CDM.tmp
        mv -f $CDM.tmp $CDM
        cd /; rm -rf "${'$'}work"
        echo "Widevine module ready"
    """.trimIndent() + "\n"
}
