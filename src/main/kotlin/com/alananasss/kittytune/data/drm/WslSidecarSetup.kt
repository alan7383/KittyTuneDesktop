package com.alananasss.kittytune.data.drm

import com.alananasss.kittytune.data.StreamResolver
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * How protected tracks are played on Windows, chosen by the user.
 *
 * [PLAYREADY] is the OS renderer: nothing to install, but it never hands a sample back, so effects,
 * the visualizer and downloads stay off. [WSL] runs Chrome's Linux Widevine CDM inside WSL through
 * [CdmSidecar]: the app decodes the audio itself, so everything works. When WSL is chosen but does
 * not answer, playback still falls back to PlayReady rather than skipping the track.
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
 * Sets up the CDM sidecar in WSL from inside the app, so nobody has to type a command.
 *
 * The work is the same as `scripts/setup-cdm-sidecar.ps1`, but self-contained: the C++ sources ship
 * in the app (`sidecar-src/`, Windows builds only) and are written into the distribution over stdin,
 * so a packaged install needs no checkout and no mounted drive. Everything lands in [INSTALL_DIR],
 * owned by root and readable by every user, so the daemon runs as whoever the default user is.
 *
 * Every step is idempotent: running the setup again repairs a half-finished one.
 */
internal object WslSidecarSetup {

    private const val TAG = "WslSidecarSetup"

    const val INSTALL_DIR = "/opt/kittytune"
    const val DAEMON = "$INSTALL_DIR/ktcdm"
    const val CDM = "$INSTALL_DIR/libwidevinecdm.so"

    /** What [openInstaller] runs, for people who would rather paste it themselves. */
    const val INSTALL_COMMAND = "wsl --install -d Ubuntu"

    /** The distribution [openInstaller] adds. */
    private const val INSTALLER_DISTRO = "Ubuntu"

    /** The packages [TOOLS_SCRIPT] had to install, one per line, so uninstalling removes those and no others. */
    const val PACKAGES_FILE = "$INSTALL_DIR/.installed-packages"

    private const val CONFIG_NAME = "cdm-sidecar.json"
    private const val HEREDOC = "KITTYTUNE_SOURCE_EOF"

    /** The sources the daemon is compiled from, as shipped in `sidecar-src/`. */
    private val SOURCES = listOf(
        "widevine/WidevineBridge.cpp",
        "widevine/content_decryption_module.h",
        "widevine/content_decryption_module_export.h",
        "cdmsidecar/CdmSidecar.cpp",
    )

    val windows: Boolean = System.getProperty("os.name").startsWith("Windows", true)

    private val wslExe: File
        get() = File(System.getenv("SystemRoot") ?: "C:\\Windows", "System32\\wsl.exe")

    enum class State {
        /** WSL itself is missing. */
        NO_WSL,

        /** WSL is there but holds no usable Linux distribution. */
        NO_DISTRO,

        /** A distribution is ready, KittyTune's daemon is not in it yet. */
        NOT_SET_UP,

        /** The daemon and the CDM are installed. Whether they answer is [CdmSidecar]'s question. */
        SET_UP,
    }

    data class Detection(val state: State, val distro: String? = null)

    enum class Step { TOOLS, SOURCES, CDM, BUILD, PROBE, CONNECT }

    class SetupException(val step: Step, message: String) : Exception(message)

    /**
     * Where WSL stands on this machine. Slow on a cold machine (it may start the WSL VM), so callers
     * run it off the UI thread, and it never throws: anything unexpected reads as "not there".
     */
    suspend fun detect(): Detection = withContext(Dispatchers.IO) {
        if (!windows || !wslExe.isFile) return@withContext Detection(State.NO_WSL)
        val list = runCatching { run(listOf("--list", "--quiet"), timeoutMs = 20_000) }.getOrNull()
        val distros = if (list?.exit == 0) parseDistros(list.output) else emptyList()
        if (distros.isEmpty()) {
            // The Store WSL answers --version even with no distribution; the inbox stub does not.
            val version = runCatching { run(listOf("--version"), timeoutMs = 15_000) }.getOrNull()
            val present = list?.exit == 0 || version?.exit == 0
            return@withContext Detection(if (present) State.NO_DISTRO else State.NO_WSL)
        }
        val distro = pickDistro(distros)
        // Checking boots the distribution; it is shut down again afterwards if we were the ones.
        WslVmLifecycle.acquire(distro)
        val installed = try {
            runCatching {
                run(
                    listOf("-d", distro, "-u", "root", "--exec", "test", "-x", DAEMON, "-a", "-f", CDM),
                    timeoutMs = 60_000,
                )
            }.getOrNull()
        } finally {
            WslVmLifecycle.release()
        }
        Detection(if (installed?.exit == 0) State.SET_UP else State.NOT_SET_UP, distro)
    }

    /**
     * Opens `wsl --install -d Ubuntu` in its own console window.
     *
     * Its own window because the user has to see it: Windows asks for elevation, may ask for a
     * restart, and Ubuntu's first start asks for a Linux user name and password. None of that can be
     * answered from here, and pretending otherwise would hang.
     *
     * Notes what it is about to install, from [state] — the machine as [detect] found it — so that
     * uninstalling can later offer to remove what KittyTune added and nothing the user already had.
     */
    fun openInstaller(state: State): Boolean = runCatching {
        ProcessBuilder("cmd.exe", "/c", "start", "KittyTune - WSL", wslExe.absolutePath, "--install", "-d", "Ubuntu")
            .start()
        val prefs = PlayerPreferences()
        if (state == State.NO_WSL) prefs.setWslInstalledByApp(true)
        if (state == State.NO_WSL || state == State.NO_DISTRO) prefs.setWslDistroInstalledByApp(INSTALLER_DISTRO)
        true
    }.getOrElse {
        Logger.w(TAG, "could not open the WSL installer: ${it.message}")
        false
    }

    /**
     * Installs the tools, the CDM and the daemon in [distro], then points KittyTune at it.
     *
     * @param onStep called when a step starts.
     * @param onLine every line the distribution prints, for the log.
     * @throws SetupException naming the step that failed and why.
     */
    suspend fun provision(distro: String, onStep: (Step) -> Unit, onLine: (String) -> Unit) {
        WslVmLifecycle.acquire(distro)
        try {
            provisionHeld(distro, onStep, onLine)
        } finally {
            WslVmLifecycle.release()
        }
    }

    private suspend fun provisionHeld(distro: String, onStep: (Step) -> Unit, onLine: (String) -> Unit) {
        installInDistro(distro, onStep, onLine)
        withContext(Dispatchers.IO) {
            onStep(Step.CONNECT)
            onLine("== connect")
            writeConfig(distro)
            ProtectedBackend.select(ProtectedBackend.WSL)
            if (!CdmSidecar.verify()) {
                throw SetupException(Step.CONNECT, CdmSidecar.lastFailure ?: "the sidecar did not answer")
            }
            onLine("connected: ${CdmSidecar.describe}")
        }
    }

    /**
     * The Linux half of [provision]: everything up to a daemon that loads the CDM, nothing on the
     * Windows side. Separate so it can be checked against a real distribution on its own.
     */
    internal suspend fun installInDistro(distro: String, onStep: (Step) -> Unit, onLine: (String) -> Unit) {
        withContext(Dispatchers.IO) {
            suspend fun step(step: Step, asRoot: Boolean = true, timeoutMs: Long = 600_000, script: String) {
                onStep(step)
                onLine("== ${step.name.lowercase()}")
                val user = if (asRoot) listOf("-u", "root") else emptyList()
                val result = run(listOf("-d", distro) + user + listOf("--exec", "bash", "-s"), script, timeoutMs, onLine)
                if (result.exit != 0) {
                    val reason = result.output.lineSequence()
                        .firstOrNull { it.startsWith("KT_ERROR:") }
                        ?.removePrefix("KT_ERROR:")?.trim()
                        ?: result.output.lineSequence().lastOrNull { it.isNotBlank() }
                        ?: "exit ${result.exit}"
                    throw SetupException(step, reason)
                }
            }

            step(Step.TOOLS, script = TOOLS_SCRIPT)
            step(Step.SOURCES, script = sourcesScript())
            step(Step.CDM, script = CDM_SCRIPT)
            step(Step.BUILD, script = BUILD_SCRIPT)
            // As the default user, because that is who runs the daemon afterwards.
            step(Step.PROBE, asRoot = false, timeoutMs = 120_000, script = "exec $DAEMON $CDM --probe </dev/null\n")
        }
    }

    private fun writeConfig(distro: String) {
        val local = System.getenv("LOCALAPPDATA") ?: throw SetupException(Step.CONNECT, "LOCALAPPDATA is not set")
        val directory = File(local, "KittyTune").apply { mkdirs() }
        val command = JSONArray(listOf(wslExe.absolutePath, "-d", distro, "--exec", DAEMON, CDM))
        val json = JSONObject()
            .put("command", command)
            .put("label", "WSL $distro")
            .put("written", java.time.LocalDateTime.now().withNano(0).toString())
        val temporary = File(directory, "$CONFIG_NAME.tmp")
        temporary.writeText(json.toString(2))
        val target = File(directory, CONFIG_NAME)
        if (!temporary.renameTo(target)) {
            target.delete()
            temporary.renameTo(target)
        }
    }

    /** The distribution named in an earlier setup, else an Ubuntu, a Debian, then anything apt-like. */
    private fun pickDistro(distros: List<String>): String {
        val configured = runCatching {
            val local = System.getenv("LOCALAPPDATA") ?: return@runCatching null
            val array = JSONObject(File(local, "KittyTune/$CONFIG_NAME").readText()).getJSONArray("command")
            val items = (0 until array.length()).map { array.getString(it) }
            items.getOrNull(items.indexOf("-d") + 1)
        }.getOrNull()
        return distros.firstOrNull { it == configured }
            ?: distros.firstOrNull { it.startsWith("Ubuntu", true) }
            ?: distros.firstOrNull { it.startsWith("Debian", true) }
            ?: distros.first()
    }

    /**
     * `wsl -l -q` lines, without the helper distributions Docker and Rancher register.
     *
     * An older `wsl.exe` ignores `WSL_UTF8` and answers in UTF-16LE: read as UTF-8, every character
     * carries a NUL and the byte order mark becomes replacement characters. Both are dropped.
     */
    internal fun parseDistros(output: String): List<String> = parseAllDistros(output)
        .filterNot { it.startsWith("docker-desktop", true) || it.startsWith("rancher-desktop", true) }

    /** Every registered distribution, Docker's and Rancher's included: they need WSL as much as any other. */
    internal fun parseAllDistros(output: String): List<String> = output
        .replace("\u0000", "")
        .replace("\uFEFF", "")
        .replace("\uFFFD", "")
        .lineSequence()
        .map { it.trim().trim('"') }
        .filter { it.isNotEmpty() }
        .toList()

    internal fun sourcesScript(): String = buildString {
        append("set -e\n")
        append("mkdir -p $INSTALL_DIR/src/widevine $INSTALL_DIR/src/cdmsidecar\n")
        for (path in SOURCES) {
            val text = WslSidecarSetup::class.java.getResourceAsStream("/sidecar-src/$path")
                ?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: throw SetupException(Step.SOURCES, "this build does not carry $path")
            append("cat > $INSTALL_DIR/src/$path <<'$HEREDOC'\n")
            append(text.replace("\r\n", "\n").trimEnd('\n'))
            append("\n$HEREDOC\n")
            append("echo \"copied $path\"\n")
        }
    }

    private class Result(val exit: Int, val output: String)

    /**
     * Runs `wsl.exe` with [args], feeding [input] on stdin.
     *
     * Scripts travel over stdin rather than as arguments because `wsl.exe` re-parses its own command
     * line, which mangles quotes and backslashes. Interruptible: cancelling the caller kills the
     * process instead of leaving it to finish in the background.
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
                    if (line.isNotBlank()) onLine?.invoke(line)
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

    // ------------------------------------------------------------------------------------------------
    // Uninstalling: everything the setup put on the machine, and, when the user wants it and nothing else
    // depends on it, the distribution and WSL that came with it.
    // ------------------------------------------------------------------------------------------------

    /**
     * What removing the WSL method involves on this machine, as [planUninstall] found it.
     *
     * The distribution and WSL itself are only ever offered as choices: a distribution the user had before
     * KittyTune can hold their own work, and WSL can carry Docker or another distribution. What KittyTune
     * added on its own ([distroInstalledByApp], [wslInstalledByApp]) is what the assistant ticks by default.
     */
    data class UninstallPlan(
        /** The distribution the method uses or used, null when there is none left. */
        val distro: String?,
        /** Whether KittyTune's files are in it: [INSTALL_DIR], or the setup script's copy in a home. */
        val filesPresent: Boolean,
        /** What the setup had to install with apt, as recorded in [PACKAGES_FILE]. */
        val packages: List<String>,
        /** True when [distro] is the one the installer KittyTune opened added. */
        val distroInstalledByApp: Boolean,
        /** Every other registered distribution, Docker's included: while any is left, WSL itself stays. */
        val otherDistros: List<String>,
        val wslPresent: Boolean,
        /** True when WSL was installed from nothing by the installer KittyTune opened. */
        val wslInstalledByApp: Boolean,
        /** Whether KittyTune's link to the daemon is still written on the Windows side. */
        val configPresent: Boolean,
    )

    enum class UninstallStep { STOP, FILES, PACKAGES, DISTRO, SETTINGS, WSL }

    /** [refused]: Windows asked for permission to remove WSL and was told no. */
    class UninstallException(val step: UninstallStep, message: String, val refused: Boolean = false) : Exception(message)

    private val configFile: File?
        get() = System.getenv("LOCALAPPDATA")?.let { File(it, "KittyTune/$CONFIG_NAME") }

    /**
     * Whether there may be anything to uninstall, without starting WSL: the method is chosen, its link is
     * written, or the setup installed something. Cheap enough for the settings page to ask on every visit.
     */
    fun hasLeftovers(): Boolean {
        if (!windows) return false
        val prefs = PlayerPreferences()
        return ProtectedBackend.current == ProtectedBackend.WSL ||
            configFile?.isFile == true ||
            prefs.getWslInstalledByApp() ||
            prefs.getWslDistroInstalledByApp() != null
    }

    /** Looks at what is installed. May start the distribution for a moment, so callers stay off the UI thread. */
    suspend fun planUninstall(): UninstallPlan = withContext(Dispatchers.IO) {
        val prefs = PlayerPreferences()
        val configPresent = configFile?.isFile == true
        val wslInstalledByApp = prefs.getWslInstalledByApp()
        val ownedDistro = prefs.getWslDistroInstalledByApp()
        if (!windows || !wslExe.isFile) {
            return@withContext UninstallPlan(
                distro = null, filesPresent = false, packages = emptyList(), distroInstalledByApp = false,
                otherDistros = emptyList(), wslPresent = false, wslInstalledByApp = wslInstalledByApp,
                configPresent = configPresent,
            )
        }
        val list = runCatching { run(listOf("--list", "--quiet"), timeoutMs = 20_000) }.getOrNull()
        val all = if (list?.exit == 0) parseAllDistros(list.output) else emptyList()
        val usable = parseDistros(all.joinToString("\n"))
        val wslPresent = list?.exit == 0 ||
            runCatching { run(listOf("--version"), timeoutMs = 15_000) }.getOrNull()?.exit == 0
        val distro = if (usable.isEmpty()) null else pickDistro(usable)

        var filesPresent = false
        var packages = emptyList<String>()
        if (distro != null) {
            WslVmLifecycle.acquire(distro)
            try {
                val probe = runCatching {
                    run(listOf("-d", distro, "-u", "root", "--exec", "bash", "-s"), INSPECT_SCRIPT, timeoutMs = 60_000)
                }.getOrNull()
                val lines = probe?.output?.lineSequence()?.map { it.trim() }?.toList().orEmpty()
                filesPresent = "KT_FILES" in lines
                packages = lines.filter { it.startsWith("KT_PKG:") }
                    .map { it.removePrefix("KT_PKG:").trim() }
                    .filter { PACKAGE_NAME.matches(it) }
                    .distinct()
            } finally {
                WslVmLifecycle.release()
            }
        }
        UninstallPlan(
            distro = distro,
            filesPresent = filesPresent,
            packages = packages,
            distroInstalledByApp = distro != null && ownedDistro != null && distro.equals(ownedDistro, ignoreCase = true),
            otherDistros = all.filterNot { it == distro },
            wslPresent = wslPresent,
            wslInstalledByApp = wslInstalledByApp,
            configPresent = configPresent,
        )
    }

    /** WSL itself can only go when nothing else would be left using it. */
    fun canRemoveWsl(plan: UninstallPlan, removeDistro: Boolean): Boolean =
        plan.wslPresent && plan.otherDistros.isEmpty() && (plan.distro == null || removeDistro)

    /** The steps [uninstall] runs for these choices, in order. */
    fun uninstallSteps(plan: UninstallPlan, removeDistro: Boolean, removeWsl: Boolean): List<UninstallStep> = buildList {
        add(UninstallStep.STOP)
        if (plan.distro != null) {
            if (removeDistro) {
                add(UninstallStep.DISTRO)
            } else {
                add(UninstallStep.FILES)
                if (plan.packages.isNotEmpty()) add(UninstallStep.PACKAGES)
            }
        }
        add(UninstallStep.SETTINGS)
        if (removeWsl && canRemoveWsl(plan, removeDistro)) add(UninstallStep.WSL)
    }

    /**
     * Removes what [plan] found, as chosen: the module is stopped and protected tracks go back to PlayReady,
     * KittyTune's files and the tools it installed leave the distribution (or the whole distribution goes),
     * its link is forgotten, and WSL itself is uninstalled when [removeWsl] and nothing else uses it.
     *
     * @return true when Windows has to restart to finish, which is when WSL itself was removed.
     * @throws UninstallException naming the step that failed.
     */
    suspend fun uninstall(
        plan: UninstallPlan,
        removeDistro: Boolean,
        removeWsl: Boolean,
        onStep: (UninstallStep) -> Unit,
        onLine: (String) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        val distro = plan.distro
        var restart = false
        for (step in uninstallSteps(plan, removeDistro, removeWsl)) {
            onStep(step)
            onLine("== ${step.name.lowercase()}")
            when (step) {
                UninstallStep.STOP -> {
                    // Also drops the running daemon and every stream licensed through it.
                    ProtectedBackend.select(ProtectedBackend.PLAYREADY)
                    if (distro != null) runCatching { run(listOf("--terminate", distro), timeoutMs = 30_000, onLine = onLine) }
                    onLine("module stopped, protected tracks play through PlayReady")
                }
                UninstallStep.FILES -> inDistro(distro!!, step, REMOVE_FILES_SCRIPT, 120_000, onLine)
                UninstallStep.PACKAGES -> inDistro(distro!!, step, removePackagesScript(plan.packages), 600_000, onLine)
                UninstallStep.DISTRO -> {
                    val result = run(listOf("--unregister", distro!!), timeoutMs = 300_000, onLine = onLine)
                    if (result.exit != 0) throw UninstallException(step, lastLine(result))
                    if (plan.distroInstalledByApp) removeDistroStorePackage(distro, onLine)
                    PlayerPreferences().setWslDistroInstalledByApp(null)
                }
                UninstallStep.SETTINGS -> {
                    configFile?.let { file ->
                        if (file.exists() && !file.delete()) throw UninstallException(step, "could not delete ${file.path}")
                    }
                    StreamResolver.forgetStreams()
                    onLine("KittyTune no longer points at WSL")
                }
                UninstallStep.WSL -> {
                    runCatching { run(listOf("--shutdown"), timeoutMs = 60_000) }
                    removeWslElevated(disablePlatform = plan.wslInstalledByApp, onLine = onLine)
                    PlayerPreferences().setWslInstalledByApp(false)
                    restart = true
                }
            }
        }
        restart
    }

    private suspend fun inDistro(distro: String, step: UninstallStep, script: String, timeoutMs: Long, onLine: (String) -> Unit) {
        WslVmLifecycle.acquire(distro)
        try {
            val result = run(listOf("-d", distro, "-u", "root", "--exec", "bash", "-s"), script, timeoutMs, onLine)
            if (result.exit != 0) throw UninstallException(step, lastLine(result))
        } finally {
            WslVmLifecycle.release()
        }
    }

    private fun lastLine(result: Result): String =
        result.output.lineSequence().firstOrNull { it.startsWith("KT_ERROR:") }?.removePrefix("KT_ERROR:")?.trim()
            ?: result.output.lineSequence().lastOrNull { it.isNotBlank() }
            ?: "exit ${result.exit}"

    /**
     * An Ubuntu that `wsl --install` added from the Store also left its app behind, in the Start menu. Only
     * called for the distribution KittyTune's installer added; never fatal, the distribution itself is gone.
     */
    private fun removeDistroStorePackage(distro: String, onLine: (String) -> Unit) {
        if (!distro.startsWith("Ubuntu", ignoreCase = true)) return
        runCatching {
            val process = ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                "Get-AppxPackage -Name 'CanonicalGroupLimited.Ubuntu*' | Remove-AppxPackage",
            ).redirectErrorStream(true).start()
            process.inputStream.bufferedReader().readText().lineSequence().filter { it.isNotBlank() }.forEach(onLine)
            if (process.waitFor(120, TimeUnit.SECONDS)) onLine("Ubuntu app removed")
        }.onFailure { onLine("Ubuntu app left in place: ${it.message}") }
    }

    /**
     * Uninstalls WSL and turns off the Windows features it runs on, which takes an administrator: Windows
     * shows its own permission prompt. Virtual Machine Platform is only turned off when KittyTune's installer
     * is what turned it on ([disablePlatform]); the WSL feature itself is WSL's alone.
     */
    private fun removeWslElevated(disablePlatform: Boolean, onLine: (String) -> Unit) {
        val work = File(System.getProperty("java.io.tmpdir"), "kittytune-wsl-uninstall").apply { mkdirs() }
        val script = File(work, "uninstall-wsl.ps1")
        val log = File(work, "uninstall-wsl.log").apply { delete() }
        fun quoted(file: File) = "'" + file.absolutePath.replace("'", "''") + "'"
        script.writeText(
            buildString {
                appendLine("\$ErrorActionPreference = 'Continue'")
                appendLine("& {")
                appendLine("  & \"\$env:SystemRoot\\System32\\wsl.exe\" --uninstall")
                appendLine("  Disable-WindowsOptionalFeature -Online -FeatureName Microsoft-Windows-Subsystem-Linux -NoRestart | Out-String")
                if (disablePlatform) {
                    appendLine("  Disable-WindowsOptionalFeature -Online -FeatureName VirtualMachinePlatform -NoRestart | Out-String")
                }
                appendLine("} *>&1 | Out-File -FilePath ${quoted(log)} -Encoding utf8")
                appendLine("exit 0")
            }
        )
        val launch = "try { \$p = Start-Process -FilePath powershell.exe -Verb RunAs -Wait -PassThru -WindowStyle Hidden " +
            "-ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File','\"${script.absolutePath.replace("'", "''")}\"'); " +
            "exit \$p.ExitCode } catch { exit 1223 }"
        val process = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", launch)
            .redirectErrorStream(true).start()
        process.inputStream.bufferedReader().readText()
        if (!process.waitFor(15, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            throw UninstallException(UninstallStep.WSL, "Windows did not finish removing WSL within 15 minutes")
        }
        runCatching { log.readText(Charsets.UTF_8) }.getOrNull()
            ?.replace("\uFEFF", "")?.lineSequence()?.map { it.trimEnd() }?.filter { it.isNotBlank() }?.forEach(onLine)
        when (process.exitValue()) {
            0 -> onLine("WSL removed; Windows finishes after a restart")
            1223 -> throw UninstallException(UninstallStep.WSL, "permission refused", refused = true)
            else -> throw UninstallException(UninstallStep.WSL, "exit ${process.exitValue()}")
        }
        runCatching { script.delete(); log.delete() }
    }

    /** apt package names only: what is read back from the distribution goes into a command. */
    private val PACKAGE_NAME = Regex("[a-z0-9][a-z0-9.+-]*")

    /** Finds KittyTune's files and the packages the setup recorded; prints KT_FILES and KT_PKG:name lines. */
    internal val INSPECT_SCRIPT = """
        if [ -d $INSTALL_DIR ] || ls -d /home/*/.local/share/kittytune /root/.local/share/kittytune >/dev/null 2>&1; then echo KT_FILES; fi
        if [ -f $PACKAGES_FILE ]; then sed 's/^/KT_PKG:/' $PACKAGES_FILE; fi
        exit 0
    """.trimIndent() + "\n"

    /** KittyTune's install, and the copy `scripts/setup-cdm-sidecar.sh` leaves in a home. */
    internal val REMOVE_FILES_SCRIPT = """
        rm -rf $INSTALL_DIR /root/.local/share/kittytune
        for d in /home/*/.local/share/kittytune; do
          if [ -e "${'$'}d" ]; then rm -rf "${'$'}d"; fi
        done
        echo "KittyTune's files removed"
        exit 0
    """.trimIndent() + "\n"

    /**
     * Gives back the tools the setup installed. Marked as automatically installed rather than purged, so
     * apt only removes the ones nothing else came to depend on since: purging curl outright would also
     * take away whatever the user installed afterwards that needs it.
     */
    internal fun removePackagesScript(packages: List<String>): String {
        val names = packages.filter { PACKAGE_NAME.matches(it) }.joinToString(" ")
        return """
            export DEBIAN_FRONTEND=noninteractive
            apt-mark auto $names </dev/null || { echo "KT_ERROR: apt-mark failed"; exit 69; }
            apt-get autoremove -y -q --purge </dev/null || { echo "KT_ERROR: apt-get autoremove failed"; exit 69; }
            echo "tools no other program needs were removed"
            exit 0
        """.trimIndent() + "\n"
    }

    internal val TOOLS_SCRIPT = """
        if [ "${'$'}(uname -m)" != "x86_64" ]; then
          echo "KT_ERROR: Chrome's Widevine module only exists for x86_64, this distribution is ${'$'}(uname -m)"; exit 65
        fi
        if ! command -v apt-get >/dev/null 2>&1; then
          echo "KT_ERROR: automatic setup needs Ubuntu or Debian (apt-get was not found)"; exit 65
        fi
        missing=""
        command -v g++ >/dev/null 2>&1 || missing="${'$'}missing build-essential"
        command -v curl >/dev/null 2>&1 || missing="${'$'}missing curl"
        command -v dpkg-deb >/dev/null 2>&1 || missing="${'$'}missing dpkg"
        [ -f /etc/ssl/certs/ca-certificates.crt ] || missing="${'$'}missing ca-certificates"
        if [ -z "${'$'}missing" ]; then echo "tools already installed"; exit 0; fi
        export DEBIAN_FRONTEND=noninteractive
        echo "installing:${'$'}missing"
        apt-get update -q </dev/null || { echo "KT_ERROR: apt-get update failed, check the internet connection"; exit 69; }
        apt-get install -y -q --no-install-recommends ${'$'}missing </dev/null || { echo "KT_ERROR: installing${'$'}missing failed"; exit 69; }
        mkdir -p $INSTALL_DIR
        for p in ${'$'}missing; do grep -qx "${'$'}p" $PACKAGES_FILE 2>/dev/null || echo "${'$'}p" >> $PACKAGES_FILE; done
        echo "tools installed"
    """.trimIndent() + "\n"

    internal val CDM_SCRIPT = """
        set -e
        mkdir -p $INSTALL_DIR
        if [ -s $CDM ]; then echo "Widevine module already present"; exit 0; fi
        # A Chrome already installed, or the module scripts/setup-cdm-sidecar.sh left in a home.
        for c in /opt/google/chrome/WidevineCdm/_platform_specific/linux_x64/libwidevinecdm.so \
                 /opt/google/chrome-beta/WidevineCdm/_platform_specific/linux_x64/libwidevinecdm.so \
                 /home/*/.local/share/kittytune/libwidevinecdm.so /root/.local/share/kittytune/libwidevinecdm.so; do
          if [ -f "${'$'}c" ]; then install -m 0755 "${'$'}c" $CDM; echo "Widevine module copied from ${'$'}c"; exit 0; fi
        done
        work="${'$'}(mktemp -d)"
        trap 'rm -rf "${'$'}work"' EXIT
        echo "downloading Chrome for Linux from dl.google.com"
        curl -fsSL --retry 2 -o "${'$'}work/chrome.deb" https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb </dev/null \
          || { echo "KT_ERROR: downloading Chrome for Linux failed, check the internet connection"; exit 69; }
        echo "extracting the Widevine module"
        dpkg-deb -x "${'$'}work/chrome.deb" "${'$'}work/root"
        found="${'$'}(find "${'$'}work/root" -name libwidevinecdm.so -type f -print -quit)"
        [ -n "${'$'}found" ] || { echo "KT_ERROR: the Chrome package carried no Widevine module"; exit 69; }
        install -m 0755 "${'$'}found" $CDM
        echo "Widevine module ready"
    """.trimIndent() + "\n"

    internal val BUILD_SCRIPT = """
        set -e
        cd $INSTALL_DIR/src
        echo "compiling ktcdm"
        g++ -std=c++17 -O2 -pthread -Iwidevine widevine/WidevineBridge.cpp cdmsidecar/CdmSidecar.cpp -ldl -o $INSTALL_DIR/ktcdm.tmp
        mv -f $INSTALL_DIR/ktcdm.tmp $DAEMON
        chmod 0755 $DAEMON
        echo "compiled"
    """.trimIndent() + "\n"
}
