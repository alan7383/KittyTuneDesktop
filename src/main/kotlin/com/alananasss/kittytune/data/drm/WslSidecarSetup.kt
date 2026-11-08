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
     */
    fun openInstaller(): Boolean = runCatching {
        ProcessBuilder("cmd.exe", "/c", "start", "KittyTune - WSL", wslExe.absolutePath, "--install", "-d", "Ubuntu")
            .start()
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
    internal fun parseDistros(output: String): List<String> = output
        .replace("\u0000", "")
        .replace("\uFEFF", "")
        .replace("\uFFFD", "")
        .lineSequence()
        .map { it.trim().trim('"') }
        .filter { it.isNotEmpty() }
        .filterNot { it.startsWith("docker-desktop", true) || it.startsWith("rancher-desktop", true) }
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
