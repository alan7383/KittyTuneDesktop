package com.alananasss.kittytune.data.drm

import com.alananasss.kittytune.utils.Logger
import org.json.JSONObject
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * A [WidevineCdm] that lives in another process, reached over stdin/stdout.
 *
 * ## Why
 *
 * On Windows the licence server refuses every CDM that is not hosted by a browser it recognises, so
 * the native bridge gets a `403` (`docs/SOUNDCLOUD_DRM_WINDOWS.md`, section 3). PlayReady does play
 * protected tracks — but inside the protected media path, which never hands a sample back, so the
 * equalizer, the filters, the pitch shifter and the visualizer stay dead on exactly those tracks.
 *
 * The same bridge, running on Linux, is licensed and decrypts in-process. This transport is the
 * plug: the CDM runs wherever it is allowed to, and the JVM side keeps its whole pipeline —
 * FFmpeg decode, DSP chain, WASAPI output — because it receives plaintext samples.
 *
 * ## It is an option, never the default
 *
 * On Windows it only runs when the user picked WSL ([ProtectedBackend.WSL], usually through the
 * setup in Settings, see [WslSidecarSetup]) or set `KITTYTUNE_CDM_SIDECAR`. Even then, nothing is
 * registered unless it answers a real round trip. With no WSL, no
 * daemon, a daemon that cannot load the CDM, or a mangled pipe, [installIfConfigured] returns false
 * and the app keeps playing through PlayReady exactly as before.
 *
 * ## Configuration
 *
 * Three sources, first match wins:
 *
 *  1. `KITTYTUNE_CDM_SIDECAR` — a quoted command line, e.g.
 *     `wsl.exe -d Ubuntu -- /home/me/.local/share/kittytune/ktcdm /home/me/.../libwidevinecdm.so`
 *  2. `%LOCALAPPDATA%\KittyTune\cdm-sidecar.json` — `{"command": ["wsl.exe", "-d", ...]}`,
 *     written by `scripts/setup-cdm-sidecar.ps1`.
 *  3. WSL auto-detection — the first installed distribution, with the daemon at the path the setup
 *     script uses. This is what makes the feature one toggle for a user who ran the script.
 *
 * ## Protocol
 *
 * `src/main/cpp/cdmsidecar/CdmSidecar.cpp` is the other half. One request at a time, both
 * directions little-endian: `u8 opcode, u32 length, payload` in, `u8 status, u32 length, payload`
 * back. The content key never crosses the pipe: [SidecarWidevineSession.decryptSample] sends
 * ciphertext and gets plaintext, exactly as the in-process bridge does.
 */
internal object CdmSidecar {

    private const val TAG = "CdmSidecar"
    private const val CONFIG_NAME = "cdm-sidecar.json"

    /** How long a failed probe is trusted before the next track may retry it. */
    private const val RETRY_AFTER_MS = 5 * 60_000L

    /** The daemon and CDM paths `scripts/setup-cdm-sidecar.sh` installs under `$HOME`. */
    private const val DAEMON_SUFFIX = "/.local/share/kittytune/ktcdm"
    private const val CDM_SUFFIX = "/.local/share/kittytune/libwidevinecdm.so"

    private val lock = Any()
    private var installed: SidecarWidevineCdm? = null
    private var installedCommand: List<String>? = null
    private var secondary: SidecarWidevineCdm? = null
    private var attempted = false
    private var lastAttemptAt = 0L
    private var failureReason: String? = null

    init {
        // wsl.exe is a child process the JVM does not reap: without this, quitting the app would leave
        // the daemon, and with it the whole WSL VM, running.
        Runtime.getRuntime().addShutdownHook(Thread({
            runCatching { installed?.release() }
            runCatching { secondary?.release() }
            // The usual delayed shutdown would never run in a JVM that is exiting.
            runCatching { WslVmLifecycle.settleNow() }
        }, "KittyTune-cdm-sidecar-shutdown"))
    }

    /**
     * True when the sidecar is the registered CDM, so protected tracks decode through it.
     *
     * Registered is not running: the daemon starts on the first protected track and stops when idle.
     */
    val isInstalled: Boolean get() = installed != null

    /** Why the configured backend is not in use, or null when nothing is configured. */
    val lastFailure: String? get() = failureReason

    /** What is registered, for diagnostics: `Widevine (sidecar over WSL (Ubuntu))`. */
    val describe: String get() = installed?.name ?: (failureReason ?: "not configured")

    /**
     * Registers the sidecar CDM when one is configured, **without starting anything**.
     *
     * This runs whenever a SoundCloud track is resolved, protected or not. Probing here used to start
     * `wsl.exe`, and with it the whole WSL VM, at the first track of every session, and keep it for the
     * run. Now nothing starts until a protected track opens a session ([SidecarWidevineCdm]); a daemon
     * that fails then reports through [markFailed], and the next tracks fall back to PlayReady.
     * [verify] is the explicit, real round trip, for the settings page and the setup.
     *
     * Only the environment variable and the configuration file count here: guessing a WSL command
     * would itself start WSL.
     *
     * @return true when a sidecar CDM is registered.
     */
    fun installIfConfigured(): Boolean {
        synchronized(lock) {
            installed?.let { return true }
            if (!chosenByUser()) return false
            val now = System.currentTimeMillis()
            if (attempted && now - lastAttemptAt < RETRY_AFTER_MS) return false
            val command = explicitCommand() ?: return false
            register(command)
            return true
        }
    }

    /**
     * Starts the daemon, performs one real round trip, and stops it again.
     *
     * Verification is a real request, not an exit code: `wsl.exe` is the transport, and a probe that
     * only asked "did the process start" would happily register a daemon whose bytes never arrive.
     * Slow (it may boot WSL), so never on the UI thread.
     */
    fun verify(): Boolean {
        synchronized(lock) {
            if (!chosenByUser()) return false
            val command = explicitCommand() ?: configuredCommand()
            if (command == null) {
                failureReason = null
                return false
            }
            val label = commandLabel(command)
            val error = probe(command)
            attempted = true
            lastAttemptAt = System.currentTimeMillis()
            if (error != null) {
                failureReason = "$label: $error"
                Logger.w(TAG, "sidecar unavailable — $failureReason")
                unregister()
                return false
            }
            installed ?: register(command)
            return true
        }
    }

    /**
     * Called when the daemon could not start or died while a track needed it: the sidecar is dropped
     * for the retry window, so the next tracks go to PlayReady instead of failing the same way.
     */
    internal fun markFailed(cdm: SidecarWidevineCdm, reason: String) {
        synchronized(lock) {
            if (cdm !== installed && cdm !== secondary) return
            failureReason = "${cdm.name}: $reason"
            Logger.w(TAG, "sidecar failed — $failureReason")
            attempted = true
            lastAttemptAt = System.currentTimeMillis()
            unregister()
        }
    }

    private fun register(command: List<String>): SidecarWidevineCdm {
        val cdm = SidecarWidevineCdm(command, commandLabel(command))
        installed = cdm
        installedCommand = command
        failureReason = null
        WidevineCdmRegistry.install(cdm)
        // A refusal earned by the in-process CDM (always refused on Windows) says nothing about
        // this one, which SoundCloud licenses.
        SoundCloudWidevineLicense.resetLicenceRefusal()
        SoundCloudDrm.forgetSessions()
        Logger.d(TAG, "sidecar CDM registered: ${cdm.name}")
        return cdm
    }

    private fun unregister() {
        val previous = installed
        runCatching { previous?.release() }
        runCatching { secondary?.release() }
        installed = null
        installedCommand = null
        secondary = null
        // Only our own registration: on Linux the in-process CDM may be the registered one.
        if (previous != null && WidevineCdmRegistry.current() === previous) WidevineCdmRegistry.install(null)
        SoundCloudDrm.forgetSessions()
    }

    /**
     * A second daemon for downloads and the settings test.
     *
     * Opening a session resets the CDM instance it is opened on, so sharing playback's daemon would
     * drop the key of the track playing. A second process is a second instance.
     */
    fun secondaryCdm(): WidevineCdm? {
        synchronized(lock) {
            secondary?.let { return it }
            val command = installedCommand ?: return null
            val name = installed?.name ?: return null
            return SidecarWidevineCdm(command, "$name (download)").also { secondary = it }
        }
    }

    /** Drops the registration and forgets the retry window: the method or the setup changed. */
    fun reset() {
        synchronized(lock) {
            unregister()
            attempted = false
            lastAttemptAt = 0L
            failureReason = null
        }
    }

    /** Windows runs the sidecar only when WSL was chosen; elsewhere a configuration is enough. */
    private fun chosenByUser(): Boolean {
        if (!System.getenv("KITTYTUNE_CDM_SIDECAR").isNullOrBlank()) return true
        if (!System.getProperty("os.name").startsWith("Windows", true)) return true
        return ProtectedBackend.current == ProtectedBackend.WSL
    }

    /**
     * Starts the daemon and performs one round trip.
     *
     * Returns null when it answered, or the reason it did not. The request is `RESET_SESSION`
     * because it is the cheapest call the daemon serves and it still proves both directions of the
     * frame protocol survived the transport — which is the part `wsl.exe` could break.
     */
    private fun probe(command: List<String>): String? {
        val transport = runCatching { SidecarTransport(command) }
            .getOrElse { return it.message ?: it.javaClass.simpleName }
        return try {
            transport.call(SidecarTransport.RESET_SESSION, ByteArray(0), 20_000)
            null
        } catch (e: Throwable) {
            e.message ?: e.javaClass.simpleName
        } finally {
            runCatching { transport.close() }
        }
    }

    /** The command that starts the daemon, from the environment, the setup script, or WSL itself. */
    private fun configuredCommand(): List<String>? = explicitCommand() ?: wslCommand()

    /** The environment variable or the configuration file: what can be known without starting WSL. */
    private fun explicitCommand(): List<String>? {
        val fromEnvironment = System.getenv("KITTYTUNE_CDM_SIDECAR")?.trim().orEmpty()
        if (fromEnvironment.isNotEmpty()) {
            return splitCommand(fromEnvironment).takeIf { it.isNotEmpty() }
        }
        val local = System.getenv("LOCALAPPDATA") ?: return null
        val file = File(local, "KittyTune/$CONFIG_NAME")
        if (file.isFile) {
            return runCatching {
                val array = JSONObject(file.readText()).getJSONArray("command")
                (0 until array.length()).map { array.getString(it) }
            }.getOrNull()?.takeIf { it.isNotEmpty() }
        }
        return null
    }

    /**
     * A command that runs the daemon in the first WSL distribution.
     *
     * Windows only, and cheap to reject: no `wsl.exe`, no distribution, no `$HOME` and this returns
     * null and the app never notices.
     */
    private fun wslCommand(): List<String>? {
        if (!System.getProperty("os.name").startsWith("Windows", true)) return null
        val wsl = File(System.getenv("SystemRoot") ?: "C:\\Windows", "System32/wsl.exe")
        if (!wsl.isFile) return null
        val distro = runCatching {
            ProcessBuilder(wsl.absolutePath, "-l", "-q")
                .redirectErrorStream(true)
                .start()
                .inputStream.bufferedReader(Charsets.UTF_16LE).use { it.readText() }
                .replace("\u0000", "")
                .lineSequence()
                .map { it.trim().trim('"') }
                .firstOrNull { it.isNotEmpty() }
        }.getOrNull() ?: return null
        val home = runCatching {
            ProcessBuilder(wsl.absolutePath, "-d", distro, "--", "sh", "-lc", "printf %s \"\$HOME\"")
                .redirectErrorStream(true)
                .start()
                .inputStream.bufferedReader().use { it.readText() }
                .trim()
        }.getOrNull()?.takeIf { it.startsWith("/") } ?: return null
        return listOf(wsl.absolutePath, "-d", distro, "--", home + DAEMON_SUFFIX, home + CDM_SUFFIX)
    }

    /**
     * Splits a command line on spaces, honouring double quotes, so a Windows path with a space in it
     * survives. Not a shell: no variables, no operators, no expansion.
     */
    internal fun splitCommand(line: String): List<String> {
        val parts = ArrayList<String>()
        val current = StringBuilder()
        var quoted = false
        var touched = false
        for (character in line) {
            when {
                character == '"' -> { quoted = !quoted; touched = true }
                character == ' ' && !quoted -> {
                    if (touched || current.isNotEmpty()) { parts += current.toString(); current.clear(); touched = false }
                }
                else -> { current.append(character); touched = true }
            }
        }
        if (touched || current.isNotEmpty()) parts += current.toString()
        return parts
    }

    /** A short human name for the transport, for logs and the DRM settings page. */
    private fun commandLabel(command: List<String>): String {
        val head = command.firstOrNull()?.substringAfterLast('\\')?.substringAfterLast('/').orEmpty()
        return when {
            head.equals("wsl.exe", true) || head.equals("wsl", true) -> {
                val distro = command.indexOf("-d").takeIf { it >= 0 }?.let { command.getOrNull(it + 1) }
                "sidecar over WSL" + (distro?.let { " ($it)" } ?: "")
            }
            head.equals("ssh", true) -> "sidecar over SSH"
            else -> "sidecar ($head)"
        }
    }
}

/**
 * A [WidevineCdm] whose daemon runs only while it is needed.
 *
 * Over WSL, a running daemon keeps the whole Linux VM up, which costs well over a gigabyte. So:
 *  - the process starts when a protected track opens a session, never before;
 *  - it stops [GRACE_MS] after the last session is closed. A track closes its session as soon as all
 *    its fragments are decrypted, or when the player drops it;
 *  - with a session still open but unused (a long pause), it stops after [IDLE_MS];
 *  - once no daemon of ours runs, [WslVmLifecycle] shuts down what KittyTune itself started.
 *
 * A session whose daemon stopped reports itself [ExpiringSession.expired], and the player opens a
 * fresh one (one more licence) if it still needs one.
 */
internal class SidecarWidevineCdm(
    private val command: List<String>,
    override val name: String,
) : WidevineCdm {

    private val lock = Any()
    private var transport: SidecarTransport? = null
    private var openSessions = 0

    @Volatile
    private var lastUse = 0L

    override fun openSession(pssh: ByteArray): WidevineLicenseSession = synchronized(lock) {
        touch()
        val existing = transport?.takeIf { it.alive }
        if (existing == null) {
            // A daemon that died on its own still counts as running for the VM until closed.
            runCatching { transport?.close() }
            transport = null
        }
        val link = existing ?: runCatching { SidecarTransport(command) }
            .getOrElse {
                CdmSidecar.markFailed(this, "could not start: ${it.message}")
                throw DrmUnavailableException("could not start the CDM sidecar ($name): ${it.message}", it)
            }
            .also { transport = it }
        // The bridge holds one session at a time on one CDM instance, exactly like the browser does.
        // On a daemon just started this is also the proof that it answers at all.
        try {
            link.call(SidecarTransport.RESET_SESSION, ByteArray(0), if (existing == null) 30_000 else 15_000)
        } catch (e: DrmLicenseException) {
            if (existing == null) {
                runCatching { link.close() }
                transport = null
                CdmSidecar.markFailed(this, e.message ?: "no answer")
                throw DrmUnavailableException("the CDM sidecar ($name) does not answer: ${e.message}", e)
            }
        }
        openSessions++
        scheduleCheck(IDLE_MS)
        SidecarWidevineSession(link, lock, pssh, onUse = ::touch, onClose = ::sessionClosed)
    }

    private fun touch() {
        lastUse = System.currentTimeMillis()
    }

    private fun sessionClosed() {
        synchronized(lock) {
            openSessions = (openSessions - 1).coerceAtLeast(0)
            if (openSessions == 0) scheduleCheck(GRACE_MS)
        }
    }

    /** Stops the daemon once it is no longer needed: [GRACE_MS] with no session, [IDLE_MS] unused. */
    private fun scheduleCheck(delayMs: Long) {
        TIMER.schedule({
            synchronized(lock) {
                val link = transport ?: return@synchronized
                val limit = if (openSessions == 0) GRACE_MS else IDLE_MS
                val quiet = System.currentTimeMillis() - lastUse
                if (quiet >= limit) {
                    Logger.d("CdmSidecar", "$name not needed any more, stopping the daemon")
                    runCatching { link.close() }
                    transport = null
                } else {
                    scheduleCheck(limit - quiet)
                }
            }
        }, delayMs.coerceAtLeast(250), java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    /** Stops the daemon. Safe to call more than once. */
    fun release() {
        synchronized(lock) {
            runCatching { transport?.close() }
            transport = null
        }
    }

    companion object {
        /** A minute without a call while a session is open: a playing track decrypts far more often. */
        const val IDLE_MS = 60_000L

        /** After the last session closed: room for the next track to start without a cold start. */
        const val GRACE_MS = 4_000L

        internal val TIMER = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "KittyTune-cdm-sidecar-idle").apply { isDaemon = true }
        }
    }
}

/** One licence exchange on the sidecar CDM. */
internal class SidecarWidevineSession(
    private val link: SidecarTransport,
    private val lock: Any,
    /**
     * The PSSH this session was opened for.
     *
     * The bridge takes it at `CreateSessionAndGenerateRequest` time, so it travels with the session
     * rather than with the call.
     */
    private val pssh: ByteArray,
    private val onUse: () -> Unit = {},
    private val onClose: () -> Unit = {},
) : WidevineLicenseSession, KeyIdScopedSession, ExpiringSession {

    private val closed = java.util.concurrent.atomic.AtomicBoolean(false)

    /** True once closed, or once the daemon behind it stopped: its keys are gone with it. */
    override val expired: Boolean get() = closed.get() || !link.alive

    @Volatile
    private var keyId: ByteArray = ByteArray(0)

    override fun licenseRequest(): ByteArray = synchronized(lock) {
        onUse()
        link.call(SidecarTransport.LICENSE_REQUEST, pssh, 60_000)
    }

    override fun provideLicense(license: ByteArray): List<ContentKey> = synchronized(lock) {
        onUse()
        link.call(SidecarTransport.PROVIDE_LICENSE, license, 60_000)
        // The CDM does not expose key bytes, by design; SoundCloudDrm only needs the KID.
        emptyList()
    }

    override fun decryptSample(
        data: ByteArray,
        iv: ByteArray,
        subsamples: List<Pair<Int, Int>>,
    ): ByteArray = synchronized(lock) {
        onUse()
        link.call(SidecarTransport.DECRYPT, SidecarTransport.frame(data, iv, subsamples, keyId), 15_000)
    }

    /** Called by [SoundCloudDrm] once the init segment revealed the KID. */
    override fun useKeyId(kid: ByteArray) {
        keyId = kid
    }

    /** Tells the CDM this session is done, so the daemon can stop when it was the last one. */
    override fun close() {
        if (closed.compareAndSet(false, true)) onClose()
    }
}

/**
 * Shuts WSL down again when KittyTune was the one that started it.
 *
 * Stopping our daemon is not enough: WSL keeps the distribution, and the VM behind it (`VmmemWSL`,
 * often more than a gigabyte), running for a while afterwards. So the first daemon notes what was
 * running before it, and when the last one stops:
 *  - the distribution is terminated if it was not running before and nobody has a terminal open in
 *    it;
 *  - the whole VM is shut down if WSL ran nothing at all before and runs nothing now.
 *
 * Anything the user was already running in WSL is never touched.
 */
internal object WslVmLifecycle {

    private const val TAG = "WslVmLifecycle"
    private const val SETTLE_MS = 1_500L

    private val lock = Any()
    private var active = 0
    private var generation = 0L
    private var distro: String? = null
    private var distroWasRunning = true
    private var wslWasRunning = true

    private val wslExe: File
        get() = File(System.getenv("SystemRoot") ?: "C:\\Windows", "System32\\wsl.exe")

    /** The distribution a sidecar command runs in, or null when it does not go through WSL. */
    fun distroOf(command: List<String>): String? {
        val head = command.firstOrNull()?.substringAfterLast('\\')?.substringAfterLast('/') ?: return null
        if (!head.equals("wsl.exe", true) && !head.equals("wsl", true)) return null
        val at = command.indexOf("-d").takeIf { it >= 0 } ?: command.indexOf("--distribution").takeIf { it >= 0 }
        return at?.let { command.getOrNull(it + 1) }
    }

    /** Called before something of ours starts in [distro]. */
    fun acquire(distro: String) {
        synchronized(lock) {
            if (active == 0) {
                val running = runningDistros()
                // Unknown means "assume it was running": never shut down what might be someone else's.
                distroWasRunning = running?.contains(distro) ?: true
                wslWasRunning = running?.isNotEmpty() ?: true
                this.distro = distro
                Logger.d(TAG, "starting in $distro (it was ${if (distroWasRunning) "" else "not "}running)")
            }
            active++
            generation++
        }
    }

    /** Called when that thing stopped. The last one shuts down what we started, shortly after. */
    fun release() {
        val token = synchronized(lock) {
            active = (active - 1).coerceAtLeast(0)
            if (active > 0) return
            ++generation
        }
        SidecarWidevineCdm.TIMER.schedule({ settle(token) }, SETTLE_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    /** Settles at once, for the app exiting. */
    fun settleNow() {
        settle(synchronized(lock) { generation })
    }

    private fun settle(token: Long) {
        val (target, terminate, shutdown) = synchronized(lock) {
            // Something of ours started again meanwhile: it owns the decision now.
            if (active > 0 || generation != token) return
            val decision = Triple(distro, !distroWasRunning, !wslWasRunning)
            // Settled once: a second pass (the app exiting) must not act, nor even look, again.
            distroWasRunning = true
            wslWasRunning = true
            decision
        }
        if (target == null || (!terminate && !shutdown)) return
        if (someoneElseUsesWsl()) {
            Logger.d(TAG, "another program uses WSL, leaving it running")
            return
        }
        if (terminate && runningDistros()?.contains(target) == true) {
            Logger.d(TAG, "terminating $target, which KittyTune started")
            run(listOf("--terminate", target))
        }
        if (shutdown && runningDistros()?.isEmpty() == true) {
            Logger.d(TAG, "nothing runs in WSL any more, shutting its VM down")
            run(listOf("--shutdown"))
        }
    }

    /**
     * True when a `wsl.exe` that is not ours runs: a terminal tab, VS Code, a script. Looking from the
     * Windows side matters twice: it does not start the distribution to find out, and WSL itself keeps a
     * `login` shell on a pseudo-terminal inside every distribution, so a terminal there proves nothing.
     */
    private fun someoneElseUsesWsl(): Boolean {
        val ours = ProcessHandle.current().descendants().map { it.pid() }.toList().toSet()
        return ProcessHandle.allProcesses().anyMatch { handle ->
            handle.pid() !in ours && handle.isAlive &&
                handle.info().command().orElse("").substringAfterLast('\\').equals("wsl.exe", ignoreCase = true)
        }
    }

    private fun runningDistros(): List<String>? =
        run(listOf("--list", "--running", "--quiet"))?.let(WslSidecarSetup::parseDistros)

    /** Runs `wsl.exe` and returns its output, or null when it failed or WSL is not there. */
    private fun run(args: List<String>): String? = runCatching {
        if (!wslExe.isFile) return null
        val builder = ProcessBuilder(listOf(wslExe.absolutePath) + args).redirectErrorStream(true)
        builder.environment()["WSL_UTF8"] = "1"
        val process = builder.start()
        process.outputStream.close()
        val output = process.inputStream.bufferedReader(Charsets.UTF_8).readText()
        if (!process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return null
        }
        // `--list --running` exits non-zero when nothing runs; its output is still the answer.
        if (process.exitValue() != 0 && args.firstOrNull() != "--list") return null
        output
    }.getOrNull()
}

/**
 * The pipe to one `ktcdm` process.
 *
 * Answers arrive on a reader thread and are handed to the waiting caller through a queue, because a
 * `Process` pipe has no read timeout and a wedged daemon must not wedge the player thread forever.
 */
internal class SidecarTransport(command: List<String>) : AutoCloseable {

    /** The WSL distribution this daemon runs in, accounted for in [WslVmLifecycle]. */
    private val wslDistro: String? = WslVmLifecycle.distroOf(command)?.also { WslVmLifecycle.acquire(it) }
    private val released = java.util.concurrent.atomic.AtomicBoolean(false)

    private val process: Process = try {
        ProcessBuilder(command)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
    } catch (e: Exception) {
        if (wslDistro != null) WslVmLifecycle.release()
        throw e
    }
    private val output: OutputStream = process.outputStream
    private val answers = LinkedBlockingQueue<Any>()
    private val errors = StringBuilder()
    private val reader = Thread({ readLoop(process.inputStream) }, "KittyTune-cdm-sidecar").apply {
        isDaemon = true
        start()
    }
    private val errorReader = Thread({
        runCatching {
            process.errorStream.bufferedReader().forEachLine { line ->
                synchronized(errors) { if (errors.length < 4000) errors.append(line).append('\n') }
            }
        }
    }, "KittyTune-cdm-sidecar-err").apply { isDaemon = true; start() }

    @Volatile
    private var closed = false

    /** False once closed or once the process died. */
    val alive: Boolean get() = !closed && process.isAlive

    /** What the daemon said on stderr, so a dead process names its own reason. */
    private fun stderr(): String = synchronized(errors) { errors.toString().trim() }

    /**
     * Sends one request and waits for its answer.
     *
     * @throws DrmLicenseException when the daemon reports an error, dies, or times out.
     */
    fun call(opcode: Int, payload: ByteArray, timeoutMs: Long): ByteArray {
        synchronized(this) {
            if (!process.isAlive) {
                val detail = stderr()
                val exit = runCatching { process.exitValue() }.getOrNull()
                throw DrmLicenseException(
                    "the CDM sidecar process is not running" +
                        (exit?.let { " (exit $it)" } ?: "") +
                        (if (detail.isEmpty()) "" else ": $detail")
                )
            }
            try {
                // A timed-out call leaves its answer in the queue; dropping anything stale before the
                // next request keeps the exchange in step instead of handing back the previous reply.
                answers.clear()
                output.write(byteArrayOf(opcode.toByte()) + length(payload.size) + payload)
                output.flush()
            } catch (e: IOException) {
                throw DrmLicenseException("the CDM sidecar stopped listening: ${e.message}", e)
            }
            val answer = answers.poll(timeoutMs, TimeUnit.MILLISECONDS)
                ?: throw DrmLicenseException("the CDM sidecar did not answer within ${timeoutMs / 1000} s")
            return when (answer) {
                is ByteArray -> answer
                is String -> throw DrmLicenseException("CDM sidecar: $answer")
                else -> throw DrmLicenseException("CDM sidecar: $answer")
            }
        }
    }

    private fun readLoop(input: InputStream) {
        try {
            while (true) {
                val header = ByteArray(5)
                readFully(input, header)
                val status = header[0].toInt() and 0xFF
                val size = (header[1].toInt() and 0xFF) or
                    ((header[2].toInt() and 0xFF) shl 8) or
                    ((header[3].toInt() and 0xFF) shl 16) or
                    ((header[4].toInt() and 0xFF) shl 24)
                if (size < 0 || size > 32 * 1024 * 1024) {
                    answers.put("the daemon sent an oversized frame ($size bytes)")
                    return
                }
                val body = ByteArray(size)
                if (size > 0) readFully(input, body)
                answers.put(if (status == 0) body else String(body, Charsets.UTF_8))
            }
        } catch (_: EOFException) {
            answers.put("the CDM sidecar closed the pipe")
        } catch (e: IOException) {
            answers.put("the CDM sidecar pipe failed: ${e.message}")
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun readFully(input: InputStream, target: ByteArray) {
        var offset = 0
        while (offset < target.size) {
            val read = input.read(target, offset, target.size - offset)
            if (read < 0) throw EOFException()
            offset += read
        }
    }

    override fun close() {
        closed = true
        runCatching { output.write(byteArrayOf(QUIT.toByte()) + length(0)); output.flush() }
        runCatching { process.waitFor(2, TimeUnit.SECONDS) }
        if (process.isAlive) process.destroyForcibly()
        reader.interrupt()
        errorReader.interrupt()
        if (wslDistro != null && released.compareAndSet(false, true)) WslVmLifecycle.release()
    }

    companion object {
        const val QUIT = 0
        const val LICENSE_REQUEST = 1
        const val PROVIDE_LICENSE = 2
        const val UPDATE = 3
        const val DECRYPT = 4
        const val RESET_SESSION = 5
        const val SET_SERVER_CERTIFICATE = 6

        fun length(value: Int): ByteArray = byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte(),
        )

        /** The DECRYPT payload: data, IV, subsample pairs, KID — each length-prefixed. */
        fun frame(data: ByteArray, iv: ByteArray, subsamples: List<Pair<Int, Int>>, keyId: ByteArray): ByteArray {
            val out = java.io.ByteArrayOutputStream(data.size + iv.size + keyId.size + 24 + subsamples.size * 8)
            fun write(bytes: ByteArray) {
                out.write(length(bytes.size))
                out.write(bytes)
            }
            write(data)
            write(iv)
            out.write(length(subsamples.size))
            for ((clear, encrypted) in subsamples) {
                out.write(length(clear))
                out.write(length(encrypted))
            }
            write(keyId)
            return out.toByteArray()
        }
    }
}
