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

    /** Where the WSL package (the engine WSL2 runs on, Store or MSI) installs itself. */
    private val wslPackageFile: File
        get() = File(System.getenv("ProgramFiles") ?: "C:\\Program Files", "WSL\\wsl.exe")

    /**
     * Whether WSL is really installed — its package, or WSL1's built-in component — found **without running
     * `wsl.exe`**. `System32\wsl.exe` exists on every Windows, WSL or not: without WSL it is only Windows'
     * installer stub, and running it for anything (even `--list`) opens a terminal offering to install WSL,
     * where a single key press reinstalls it. So nothing of ours ever runs it unless this is true.
     */
    val wslInstalled: Boolean
        get() = windows && wslExe.isFile && (wslPackageFile.isFile || serviceRegistered("LxssManager"))

    /** A Windows service is registered (read from the registry; starts nothing). */
    private fun serviceRegistered(name: String): Boolean = runCatching {
        val process = ProcessBuilder("reg.exe", "query", "HKLM\\SYSTEM\\CurrentControlSet\\Services\\$name")
            .redirectErrorStream(true).start()
        process.inputStream.readAllBytes()
        process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0
    }.getOrDefault(false)

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
        /**
         * The WSL engine is usable (see [wslInstalled]). Both versions need it besides their component: on current
         * Windows, with only the component on, `wsl.exe` is the installer stub and imports nothing — WSL1 included.
         */
        val wslPackage: Boolean = true,
    ) {
        /**
         * Whether everything [version] needs is in place. Readiness is always judged for the version the user
         * picked: WSL2's component being on does not make a WSL1 import possible, and vice versa.
         */
        fun featureOn(version: Int): Boolean = (if (version == 1) wsl1Available else wsl2Available) && wslPackage
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
        // The engine is usable: the package, or (older Windows) the inbox WSL behind the component.
        val pkg = wslInstalled
        // Without WSL installed, every question to wsl.exe would wake the installer stub: answer from what
        // Windows itself says (the components) and stop here.
        if ((!wsl1 && !wsl2) || !wslInstalled) {
            return@withContext Detection(State.FEATURE_OFF, wsl1Available = wsl1, wsl2Available = wsl2, wslPackage = pkg)
        }
        val imported = parseAllDistros(
            listOutput()
        ).any { it.equals(DISTRO, ignoreCase = true) }
        if (!imported) {
            return@withContext Detection(State.NOT_SET_UP, wsl1, wsl2, wslPackage = pkg)
        }
        val ready = runCatching {
            run(
                listOf("-d", DISTRO, "-u", "root", "--exec", "test", "-x", DAEMON, "-a", "-f", CDM),
                timeoutMs = 60_000,
            )
        }.getOrNull()?.exit == 0
        val version = runCatching { importedVersion() }.getOrNull()
        Detection(if (ready) State.SET_UP else State.NOT_SET_UP, wsl1, wsl2, version, wslPackage = pkg)
    }

    /**
     * The WSL package (the engine WSL2 runs on) is installed. Looked up on disk: asking `wsl --version` would
     * run the installer stub on a PC without WSL (see [wslInstalled]).
     */
    private fun wslPackagePresent(): Boolean = wslPackageFile.isFile

    /**
     * `wsl --list` output — empty unless wsl.exe really answered. A failed or refused call (WSL not installed,
     * see [wslInstalled]) must never be parsed into distribution names.
     */
    private suspend fun listOutput(mode: String = "--quiet"): String =
        runCatching { run(listOf("--list", mode), timeoutMs = 20_000) }.getOrNull()
            ?.takeIf { it.exit == 0 }?.output.orEmpty()

    /** The WSL version our distribution is registered as, read from `wsl -l -v`. */
    private suspend fun importedVersion(): Int? = registeredVersions()[DISTRO.lowercase()]

    /** Every registered distribution (lower-cased name) with its WSL version, from `wsl -l -v`. */
    private suspend fun registeredVersions(): Map<String, Int> {
        val out = listOutput("--verbose").takeIf { it.isNotEmpty() }
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

    /** What uninstalling may do on the Windows side, as found when the assistant opens. */
    data class WindowsSide(
        /** WSL is on this PC at all (its engine or one of its components), so it can be removed. */
        val wslPresent: Boolean,
        /**
         * Every distribution but ours (Docker's included). Removing WSL takes them with it, so the page lists
         * them and they are deleted only when the user ticks them.
         */
        val otherDistros: List<String>,
        /** The virtual machine platform is on. */
        val platformOn: Boolean,
        /** KittyTune turned that platform on itself: removing WSL turns it off without asking. */
        val platformByApp: Boolean,
        /** The single component that may be turned off on its own (see [removableFeature]), or null. */
        val featureVersion: Int?,
    ) {
        /** WSL can be removed without deleting any distribution of the user's. */
        val wslRemovable: Boolean get() = wslPresent && otherDistros.isEmpty()
    }

    suspend fun planWindowsSide(): WindowsSide = withContext(Dispatchers.IO) {
        if (!windows || !wslExe.isFile) return@withContext WindowsSide(false, emptyList(), false, false, null)
        val registered = parseAllDistros(
            listOutput()
        )
        planWindowsSide(
            registered = registered,
            versions = registeredVersions(),
            wsl1On = featureEnabled(FEATURE_WSL1),
            wsl2On = featureEnabled(FEATURE_WSL2),
            storeWsl = wslInstalled,
            enabledByApp = PlayerPreferences().getWslFeaturesEnabledByApp(),
        )
    }

    internal fun planWindowsSide(
        registered: List<String>,
        versions: Map<String, Int>,
        wsl1On: Boolean,
        wsl2On: Boolean,
        storeWsl: Boolean,
        enabledByApp: Set<String>,
    ): WindowsSide {
        val others = registered.filterNot { it.equals(DISTRO, ignoreCase = true) }
        return WindowsSide(
            wslPresent = storeWsl || wsl1On || wsl2On,
            otherDistros = others,
            platformOn = wsl2On,
            platformByApp = FEATURE_WSL2 in enabledByApp,
            featureVersion = removableFeature(versions[DISTRO.lowercase()], wsl1On, wsl2On, versions),
        )
    }

    /**
     * Removes WSL itself: the WSL package, its own component, and the virtual machine platform when
     * [disablePlatform] — KittyTune turned it on, or the user chose to turn it off too (it may serve something
     * else, so it is never assumed).
     */
    internal fun removeWslScript(disablePlatform: Boolean): String = buildString {
        appendLine("& \"\$env:SystemRoot\\System32\\wsl.exe\" --uninstall")
        // The engine (the WSL package, ≈ 370 MB): `wsl --uninstall` takes it; when it was installed from its MSI
        // and is still there, its own uninstaller goes too.
        appendLine("if (Test-Path (Join-Path \$env:ProgramFiles 'WSL\\wsl.exe')) {")
        appendLine("  Get-ItemProperty 'HKLM:\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\*' -ErrorAction SilentlyContinue |")
        appendLine("    Where-Object { \$_.DisplayName -like 'Windows Subsystem for Linux*' -and \$_.PSChildName -like '{*}' } |")
        appendLine("    ForEach-Object { Start-Process -FilePath msiexec.exe -ArgumentList @('/x', \$_.PSChildName, '/quiet', '/norestart') -Wait }")
        appendLine("}")
        appendLine("Disable-WindowsOptionalFeature -Online -FeatureName $FEATURE_WSL1 -NoRestart | Out-String")
        if (disablePlatform) {
            appendLine("Disable-WindowsOptionalFeature -Online -FeatureName $FEATURE_WSL2 -NoRestart | Out-String")
        }
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
    fun enable(version: Int, onProgress: (Int) -> Unit = {}): EnableResult {
        val feature = if (version == 1) FEATURE_WSL1 else FEATURE_WSL2
        val wasOn = featureEnabled(feature)
        // On current Windows, wsl.exe runs nothing — WSL1 included — without the WSL package (the engine): the
        // component alone leaves the installer stub answering "not installed". Downloaded here, with progress,
        // when it is missing; installed by the elevated step below after checking Microsoft's signature.
        val msi = if (wslPackagePresent()) null else runCatching { downloadWslPackage(onProgress) }.getOrElse {
            return EnableResult(ok = false, refused = false, log = "the WSL engine could not be downloaded: ${it.message}")
        }
        var result = try {
            runElevated(enableScript(version, msi?.absolutePath))
        } finally {
            msi?.delete()
        }
        // Usable only once the engine is there: a component switched on alone is not a success.
        if (result.ok && !wslInstalled) {
            val reason = result.log.lineSequence().firstOrNull { it.startsWith("KT_ERROR:") }?.removePrefix("KT_ERROR:")?.trim()
            result = result.copy(ok = false, log = reason ?: result.log.ifBlank { "the WSL engine could not be installed" })
        }
        if (result.ok) {
            PlayerPreferences().apply {
                setWslSetupPending(true)
                setWslSetupVersion(version)
                // Recorded only when KittyTune is the one turning it on, so removing WSL later turns off
                // exactly that and never a component the user already had.
                if (!wasOn) setWslFeaturesEnabledByApp(getWslFeaturesEnabledByApp() + feature)
            }
        }
        return result
    }

    /**
     * The elevated step of turning [version] on: install the WSL package from [msiPath] when given — only if it
     * carries Microsoft's valid signature — then turn on that version's component and nothing else (WSL1 never
     * gets the virtual machine platform). No distribution of any kind comes with it. Failures are printed as
     * `KT_ERROR:` lines rather than thrown, so the transcript reaches the app.
     */
    internal fun enableScript(version: Int, msiPath: String?): String = buildString {
        appendLine("\$ok = \$true")
        if (msiPath != null) {
            appendLine("\$msi = '${msiPath.replace("'", "''")}'")
            appendLine("\$sig = Get-AuthenticodeSignature -FilePath \$msi")
            appendLine("if (\$sig.Status -ne 'Valid' -or \$sig.SignerCertificate.Subject -notmatch 'O=Microsoft Corporation') {")
            appendLine("  'KT_ERROR: the WSL package is not signed by Microsoft'; \$ok = \$false")
            appendLine("}")
            appendLine("if (\$ok) {")
            appendLine("  \$p = Start-Process -FilePath msiexec.exe -ArgumentList @('/i', ('\"' + \$msi + '\"'), '/quiet', '/norestart') -Wait -PassThru")
            appendLine("  if (\$p.ExitCode -ne 0 -and \$p.ExitCode -ne 3010) { 'KT_ERROR: the WSL package did not install (msiexec ' + \$p.ExitCode + ')'; \$ok = \$false }")
            appendLine("}")
        }
        val feature = if (version == 1) FEATURE_WSL1 else FEATURE_WSL2
        appendLine("if (\$ok) { Enable-WindowsOptionalFeature -Online -FeatureName $feature -All -NoRestart | Out-String }")
    }

    /** The WSL package of a GitHub release, for this PC's architecture. */
    internal data class WslPackage(val name: String, val url: String, val sha256: String?)

    /** Picks the `wsl.<version>.<arch>.msi` asset out of a GitHub release (JSON), with its digest when given. */
    internal fun pickWslPackage(releaseJson: String, arch: String): WslPackage? {
        val assets = JSONObject(releaseJson).optJSONArray("assets") ?: return null
        val pattern = Regex("""wsl\.[0-9.]+\.${Regex.escape(arch)}\.msi""", RegexOption.IGNORE_CASE)
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            if (!pattern.matches(name)) continue
            val digest = asset.optString("digest").removePrefix("sha256:").takeIf { Regex("[0-9a-fA-F]{64}").matches(it) }
            return WslPackage(name, asset.optString("browser_download_url"), digest)
        }
        return null
    }

    private val windowsArch: String
        get() = if (listOf(System.getenv("PROCESSOR_ARCHITEW6432"), System.getenv("PROCESSOR_ARCHITECTURE"))
                .any { it.equals("ARM64", ignoreCase = true) }
        ) "arm64" else "x64"

    /** Downloads Microsoft's latest WSL package (≈ 370 MB), reporting whole percents, and checks its digest. */
    private fun downloadWslPackage(onProgress: (Int) -> Unit): File {
        val client = ProxyManager.getOkHttpClient()
        val release = client.newCall(
            Request.Builder().url("https://api.github.com/repos/microsoft/WSL/releases/latest")
                .header("User-Agent", "KittyTune").header("Accept", "application/vnd.github+json").build()
        ).execute().use { response ->
            if (!response.isSuccessful) error("GitHub answered HTTP ${response.code}")
            response.body?.string() ?: error("empty answer from GitHub")
        }
        val pkg = pickWslPackage(release, windowsArch) ?: error("no WSL package for $windowsArch in the latest release")
        val target = File(System.getProperty("java.io.tmpdir"), pkg.name)
        try {
            client.newCall(Request.Builder().url(pkg.url).header("User-Agent", "KittyTune").build()).execute().use { response ->
                if (!response.isSuccessful) error("download answered HTTP ${response.code}")
                val body = response.body ?: error("empty download")
                val total = body.contentLength()
                target.outputStream().use { out ->
                    val input = body.byteStream()
                    val buffer = ByteArray(1 shl 16)
                    var done = 0L
                    var last = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        done += read
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != last) { last = percent; onProgress(percent) }
                        }
                    }
                }
            }
            if (pkg.sha256 != null && !sha256(target).equals(pkg.sha256, ignoreCase = true)) {
                error("the WSL package did not match its checksum")
            }
            return target
        } catch (e: Throwable) {
            target.delete()
            throw e
        }
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
        featureEnabled(if (version == 1) FEATURE_WSL1 else FEATURE_WSL2) && wslInstalled
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
        if (result.exit != 0) {
            // A failed import must not leave its empty folder behind: it would read as something to uninstall.
            runCatching { if (dir.list()?.isEmpty() == true) dir.delete() }
            runCatching { dir.parentFile?.let { if (it.list()?.isEmpty() == true) it.delete() } }
            throw SetupException(Step.IMPORT, lastLine(result))
        }
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

    /**
     * Whether KittyTune left anything of the WSL method on disk, without starting WSL. Cheap enough to ask on
     * every visit. Only what is really there counts: the method merely being picked (the tab clicked, after a
     * removal) is not something to remove, and offering the removal again then read as "WSL is never gone".
     * The distribution itself is asked separately ([imported]), since that lists WSL's registrations.
     */
    fun hasLeftovers(): Boolean {
        if (!windows) return false
        // An empty folder (a failed import) holds nothing to remove.
        return configFile?.isFile == true || distroDir?.list()?.isNotEmpty() == true
    }

    /**
     * Ends every WSL process still running once WSL is removed: none of them can work any more, and one left open
     * when Windows restarts is relaunched by Windows at the next sign-in (it restores the terminal windows that
     * were open), where `wsl.exe` is now only the installer stub — the "WSL is not installed, press a key to
     * install it" terminal at every boot. Called only right after removing WSL, before the restart: WSL itself
     * is gone by then, though [wslInstalled] still says otherwise until the restart retires its service.
     */
    fun closeWslProcesses() {
        if (!windows) return
        val names = setOf("wsl.exe", "wslhost.exe", "wslrelay.exe")
        ProcessHandle.allProcesses()
            .filter { handle -> handle.info().command().orElse("").substringAfterLast('\\').lowercase() in names }
            .forEach { runCatching { it.destroyForcibly() } }
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
        listOutput()
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

    enum class UninstallStep { STOP, DISTRO, LEFTOVERS, DISTROS, SETTINGS, FEATURE, WSL }

    class UninstallException(val step: UninstallStep, message: String, val refused: Boolean = false) : Exception(message)

    /** Removing WSL entirely covers its components: then no single component is turned off on its own. */
    fun uninstallSteps(
        removeFeature: Boolean,
        cleanLeftovers: Boolean = false,
        removeWsl: Boolean = false,
        deleteDistros: Boolean = false,
    ): List<UninstallStep> = buildList {
        add(UninstallStep.STOP)
        add(UninstallStep.DISTRO)
        if (cleanLeftovers) add(UninstallStep.LEFTOVERS)
        if (deleteDistros) add(UninstallStep.DISTROS)
        add(UninstallStep.SETTINGS)
        when {
            removeWsl -> add(UninstallStep.WSL)
            removeFeature -> add(UninstallStep.FEATURE)
        }
    }

    /**
     * The Store app a distribution came with, removed along with it so it does not linger in the Start menu
     * (launching it would even recreate the distribution). Only the well-known publishers; others keep theirs.
     */
    internal fun storePackageOf(distro: String): String? = when {
        distro.startsWith("Ubuntu", ignoreCase = true) -> "CanonicalGroupLimited.Ubuntu*"
        distro.startsWith("Debian", ignoreCase = true) -> "TheDebianProject.DebianGNULinux*"
        distro.startsWith("kali", ignoreCase = true) -> "KaliLinux.*"
        else -> null
    }

    /**
     * Removes the distribution and KittyTune's link to it; protected tracks go back to PlayReady. Also
     * deletes the [leftovers] chosen (KittyTune files in other distributions), the other distributions in
     * [deleteDistros] — the user's own, only ever when ticked — and then either removes WSL entirely
     * ([removeWsl], once no other distribution is left) or, when [featureVersion] is set, turns that version's
     * Windows component back off. Both are elevated and need a restart.
     *
     * @param disablePlatform also turn the virtual machine platform off when removing WSL; it is turned off
     *   anyway when KittyTune is the one that turned it on.
     * @return true when Windows must restart to finish.
     */
    suspend fun uninstall(
        featureVersion: Int?,
        leftovers: List<Leftover> = emptyList(),
        removeWsl: Boolean = false,
        deleteDistros: List<String> = emptyList(),
        disablePlatform: Boolean = false,
        onStep: (UninstallStep) -> Unit,
        onLine: (String) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        var restart = false
        // Cleaning KittyTune's files out of a distribution about to be deleted would be pointless.
        val cleaned = leftovers.filterNot { l -> deleteDistros.any { it.equals(l.distro, ignoreCase = true) } }
        for (step in uninstallSteps(featureVersion != null, cleaned.isNotEmpty(), removeWsl, deleteDistros.isNotEmpty())) {
            onStep(step)
            onLine("== ${step.name.lowercase()}")
            when (step) {
                UninstallStep.STOP -> {
                    ProtectedBackend.select(ProtectedBackend.PLAYREADY)
                    runCatching { run(listOf("--terminate", DISTRO), timeoutMs = 30_000) }
                    onLine("module stopped, protected tracks play through PlayReady")
                }
                UninstallStep.DISTRO -> {
                    if (DISTRO.lowercase() !in registeredVersions()) {
                        // Already gone (an earlier removal): nothing to unregister, and no WSL error to show.
                        runCatching { distroDir?.deleteRecursively() }
                        onLine("the KittyTune Linux image was already removed")
                    } else {
                        // WSL1 keeps the files in the folder, WSL2 an ext4.vhdx disk there: --unregister removes both.
                        val result = runCatching { run(listOf("--unregister", DISTRO), timeoutMs = 300_000, onLine = onLine) }
                            .getOrNull()
                        // Judged by what is registered afterwards, not by wsl.exe's message, which is translated.
                        if (DISTRO.lowercase() in registeredVersions()) {
                            throw UninstallException(step, result?.let { lastLine(it) } ?: "wsl --unregister did not answer")
                        }
                        runCatching { distroDir?.deleteRecursively() }
                        onLine("the KittyTune Linux image is removed")
                    }
                }
                UninstallStep.DISTROS -> {
                    for (distro in deleteDistros) {
                        onLine("deleting $distro")
                        runCatching { run(listOf("--terminate", distro), timeoutMs = 30_000) }
                        val result = runCatching { run(listOf("--unregister", distro), timeoutMs = 300_000) }.getOrNull()
                        if (distro.lowercase() in registeredVersions()) {
                            throw UninstallException(step, "$distro: ${result?.let { lastLine(it) } ?: "wsl --unregister did not answer"}")
                        }
                        // Its Store app too, or the Start menu keeps a launcher that would recreate it.
                        storePackageOf(distro)?.let { pattern ->
                            runCatching {
                                val process = ProcessBuilder(
                                    "powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                                    "Get-AppxPackage -Name '$pattern' | Remove-AppxPackage",
                                ).redirectErrorStream(true).start()
                                process.inputStream.bufferedReader().readText()
                                process.waitFor(120, TimeUnit.SECONDS)
                            }
                        }
                        onLine("$distro is deleted")
                    }
                }
                UninstallStep.LEFTOVERS -> withDistributionsRestored(cleaned.map { it.distro }) {
                    for (leftover in cleaned) {
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
                    // The folder our distribution lived in, once empty: nothing of the method stays on disk.
                    distroDir?.parentFile?.let { dir -> if (dir.list()?.isEmpty() == true) dir.delete() }
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
                    PlayerPreferences().apply { setWslFeaturesEnabledByApp(getWslFeaturesEnabledByApp() - feature) }
                    restart = true
                    onLine("$feature turned off; Windows finishes after a restart")
                }
                UninstallStep.WSL -> {
                    // Re-checked here, not trusted from when the page opened: never remove WSL under a
                    // distribution registered meanwhile.
                    val others = parseAllDistros(
                        listOutput()
                    ).filterNot { it.equals(DISTRO, ignoreCase = true) }
                    if (others.isNotEmpty()) {
                        throw UninstallException(step, "WSL is kept: ${others.joinToString(", ")} still use it")
                    }
                    runCatching { run(listOf("--shutdown"), timeoutMs = 60_000) }
                    val prefs = PlayerPreferences()
                    val platform = disablePlatform || FEATURE_WSL2 in prefs.getWslFeaturesEnabledByApp()
                    val result = runElevated(removeWslScript(disablePlatform = platform))
                    if (result.refused) throw UninstallException(step, "permission refused", refused = true)
                    if (!result.ok) throw UninstallException(step, "WSL could not be removed")
                    result.log.lineSequence().map { it.trimEnd() }.filter { it.isNotBlank() }.forEach(onLine)
                    prefs.setWslFeaturesEnabledByApp(emptySet())
                    closeWslProcesses()
                    restart = true
                    onLine("WSL removed; Windows finishes after a restart")
                }
            }
        }
        restart
    }

    // ------------------------------------------------------------------------------------------------
    // Windows feature state + elevation (one reusable elevated-PowerShell runner)
    // ------------------------------------------------------------------------------------------------

    /**
     * Whether an optional component is on (and its restart done), readable **without elevation**.
     *
     * `Get-WindowsOptionalFeature` needs an administrator: from a normal session it only answers "the requested
     * operation requires elevation", which read as "off" for every user not running KittyTune as admin — WSL then
     * never looked enabled, even right after the setup's restart. It is still asked first (it alone tells the
     * pending states apart); without elevation, `Win32_OptionalFeature` answers, and a component servicing restart
     * still pending means the change is not in effect yet.
     */
    private fun featureEnabled(feature: String): Boolean = runCatching {
        val process = ProcessBuilder(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", featureStateScript(feature),
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor(30, TimeUnit.SECONDS)
        parseFeatureState(output)
    }.getOrDefault(false)

    internal fun featureStateScript(feature: String): String =
        "try { 'STATE:' + (Get-WindowsOptionalFeature -Online -FeatureName $feature -ErrorAction Stop).State } catch { " +
            "'CIM:' + (Get-CimInstance Win32_OptionalFeature -Filter \"Name='$feature'\").InstallState; " +
            "if (Test-Path 'HKLM:\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Component Based Servicing\\RebootPending') { 'REBOOT_PENDING' } }"

    /** `STATE:Enabled` (DISM, elevated) or `CIM:1` with no servicing restart pending (not elevated). */
    internal fun parseFeatureState(output: String): Boolean {
        val lines = output.lineSequence().map { it.trim() }.toList()
        lines.firstOrNull { it.startsWith("STATE:") }?.let { return it.removePrefix("STATE:").trim().equals("Enabled", true) }
        val cim = lines.firstOrNull { it.startsWith("CIM:") }?.removePrefix("CIM:")?.trim()
        // Win32_OptionalFeature.InstallState: 1 enabled, 2 disabled, 3 absent.
        return cim == "1" && lines.none { it == "REBOOT_PENDING" }
    }

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
        // The one gate every wsl.exe call of ours goes through: never wake the installer stub.
        if (!wslInstalled) return@runInterruptible Result(-1, "KT_ERROR: WSL is not installed\n")
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
