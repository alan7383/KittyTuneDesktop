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
 * On Windows the licence server refuses every CDM that is not hosted by a browser it recognises when
 * that CDM is a Windows build (`docs/SOUNDCLOUD_DRM_WINDOWS.md`, section 3). Chrome's **Linux**
 * module is accepted, so KittyTune runs that one, natively, in its own process ([NativeCdmSetup]).
 * The app decodes the audio itself: FFmpeg decode, DSP chain, WASAPI output, because it receives
 * plaintext samples.
 *
 * ## Where it runs
 *
 * Nothing is registered unless the daemon answers a real round trip. Without a daemon that can load
 * the module, protected tracks are simply not offered — there is no OS renderer left to fall back to.
 *
 * ## Configuration
 *
 * Two sources, first match wins — nothing is ever guessed:
 *
 *  1. `KITTYTUNE_CDM_SIDECAR` — a quoted command line, e.g.
 *     `C:\...\ktcdm.exe C:\...\libwidevinecdm.so`
 *  2. `%LOCALAPPDATA%\KittyTune\cdm-sidecar.json` — `{"command": ["ktcdm.exe", "libwidevinecdm.so"]}`,
 *     written by the in-app setup ([NativeCdmSetup]) and deleted by its uninstall.
 *
 * A configuration that goes through `wsl.exe` is ignored: WSL is no longer a supported backend, and
 * using it would silently play through a path the app no longer maintains. [WslCleanup] removes what
 * an older version left behind.
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

    private val lock = Any()
    private var installed: SidecarWidevineCdm? = null
    private var installedCommand: List<String>? = null
    private var secondary: SidecarWidevineCdm? = null
    private var attempted = false
    private var lastAttemptAt = 0L
    private var failureReason: String? = null

    init {
        Runtime.getRuntime().addShutdownHook(Thread({
            runCatching { installed?.release() }
            runCatching { secondary?.release() }
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

    /** What is registered, for diagnostics: `sidecar over WSL (kittytune-cdm)`. */
    val describe: String get() = installed?.name ?: (failureReason ?: "not configured")

    /**
     * Registers the sidecar CDM when one is configured, **without starting anything**.
     *
     * This runs whenever a SoundCloud track is resolved, protected or not. Probing here used to start
     * `wsl.exe`, and with it the whole WSL VM, at the first track of every session, and keep it for the
     * run. Now nothing starts until a protected track opens a session ([SidecarWidevineCdm]); a daemon
     * that fails then reports through [markFailed], and the next tracks do not try it again.
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
            val command = explicitCommand()
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
     * for the retry window, so the next tracks do not fail the same way.
     */
    internal fun markFailed(cdm: SidecarWidevineCdm, reason: String) {
        synchronized(lock) {
            // The download's daemon failing says nothing about playback's: only the second instance
            // and the download session go. Dropping the registered one here would stop the music for
            // a failed download.
            if (cdm === secondary) {
                failureReason = "${cdm.name}: $reason"
                Logger.w(TAG, "secondary sidecar failed — $failureReason")
                runCatching { secondary?.release() }
                secondary = null
                SoundCloudDrm.releaseDownloadSession()
                return
            }
            if (cdm !== installed) return
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

    /**
     * Proves that a command line really answers, without registering it.
     *
     * The setup calls this before it tells the user the method works: a daemon that starts but cannot
     * load the module must fail there, where the reason can be shown, not on the first track.
     */
    internal fun probeCommand(command: List<String>): String? {
        synchronized(lock) { return probe(command) }
    }

    /**
     * The environment variable or the configuration file the in-app setup writes — and nothing else.
     *
     * There used to be a third source, auto-detection: the first WSL distribution, with a daemon in its
     * home. It served the old setup script, and it picked whichever distribution came first — on a
     * machine where KittyTune's own `kittytune-cdm` had just been removed, it silently fell back to a
     * leftover daemon in the user's Ubuntu while the page said "connected". Only what the setup or the
     * user configured counts now.
     *
     * A command that goes through `wsl.exe` is refused: the Linux distribution was a backend of its
     * own, removed in favour of the native one, and a configuration an older version wrote must not
     * quietly keep playing through it. [WslCleanup] deletes what was left behind.
     */
    private fun explicitCommand(): List<String>? {
        val fromEnvironment = System.getenv("KITTYTUNE_CDM_SIDECAR")?.trim().orEmpty()
        if (fromEnvironment.isNotEmpty()) {
            return splitCommand(fromEnvironment).takeIf { it.isNotEmpty() && !goesThroughWsl(it) }
        }
        val local = System.getenv("LOCALAPPDATA") ?: return null
        val file = File(local, "KittyTune/$CONFIG_NAME")
        if (file.isFile) {
            return runCatching {
                val array = JSONObject(file.readText()).getJSONArray("command")
                (0 until array.length()).map { array.getString(it) }
            }.getOrNull()?.takeIf { it.isNotEmpty() && !goesThroughWsl(it) }
        }
        return null
    }

    /** True when [command] drives the daemon through wsl.exe, the backend this version dropped. */
    private fun goesThroughWsl(command: List<String>): Boolean {
        val head = command.firstOrNull()?.substringAfterLast('\\')?.substringAfterLast('/').orEmpty()
        return head.equals("wsl.exe", true) || head.equals("wsl", true)
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
            head.equals("ssh", true) -> "sidecar over SSH"
            else -> "sidecar ($head)"
        }
    }
}

/**
 * A [WidevineCdm] whose daemon runs only while it is needed.
 *
 * A running daemon holds the module's ~80 MiB image, so it should not outlive the tracks that need
 * it. So:
 *  - the process starts when a protected track opens a session, never before;
 *  - it stops [GRACE_MS] after the last session is closed. A track closes its session as soon as all
 *    its fragments are decrypted, or when the player drops it;
 *  - with a session still open but unused (a long pause), it stops after [IDLE_MS];
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
        // The bridge holds one session at a time on one CDM instance, exactly like the browser does.
        // On a daemon already running this is best effort; on a fresh one it is the proof that it
        // answers at all, and [startDaemon] retries before giving up.
        val link = existing ?: startDaemon()
        if (existing != null) {
            runCatching { existing.call(SidecarTransport.RESET_SESSION, ByteArray(0), 15_000) }
        }
        openSessions++
        scheduleCheck(IDLE_MS)
        SidecarWidevineSession(link, lock, pssh, onUse = ::touch, onClose = ::sessionClosed, restart = ::startDaemon)
    }

    /**
     * Starts the daemon and proves it answers, retrying once.
     *
     * The retry is not superstition: the executable is written by the installer moments before its
     * first run, and Windows' antivirus can hold a freshly written binary long enough for the process
     * to die before it reads a frame (observed once, as "the pipe closed" between the setup's probe
     * and the first licence). A second start, 250 ms later, costs nothing and does not depend on why
     * the first one was refused.
     */
    private fun startDaemon(): SidecarTransport {
        var lastError: String? = null
        repeat(START_ATTEMPTS) { attempt ->
            val link = try {
                SidecarTransport(command)
            } catch (e: Exception) {
                CdmSidecar.markFailed(this, "could not start: ${e.message}")
                throw DrmUnavailableException("could not start the CDM sidecar ($name): ${e.message}", e)
            }
            try {
                link.call(SidecarTransport.RESET_SESSION, ByteArray(0), 30_000)
                transport = link
                return link
            } catch (e: DrmLicenseException) {
                lastError = e.message
                runCatching { link.close() }
                if (attempt + 1 < START_ATTEMPTS) {
                    Logger.w("CdmSidecar", "$name did not answer, starting it again (${e.message})")
                    runCatching { Thread.sleep(250) }
                }
            }
        }
        CdmSidecar.markFailed(this, lastError ?: "no answer")
        throw DrmUnavailableException("the CDM sidecar ($name) does not answer: $lastError")
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
        /** One retry after a start that did not answer; see [startDaemon]. */
        private const val START_ATTEMPTS = 2

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
    link: SidecarTransport,
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
    /**
     * Starts a replacement daemon when the one behind this session died before a key existed. Null
     * when the CDM cannot be restarted that way (the in-process one), in which case failures
     * propagate as before.
     */
    private val restart: (() -> SidecarTransport)? = null,
) : WidevineLicenseSession, KeyIdScopedSession, ExpiringSession {

    private var link: SidecarTransport = link

    private val closed = java.util.concurrent.atomic.AtomicBoolean(false)

    /** True once closed, or once the daemon behind it stopped: its keys are gone with it. */
    override val expired: Boolean get() = closed.get() || !link.alive

    @Volatile
    private var keyId: ByteArray = ByteArray(0)

    /**
     * Asks the CDM for a licence request, on a fresh daemon if this one died first.
     *
     * Before a key exists the exchange holds no state worth keeping: a process that stopped between
     * the session being opened and this call costs one restart, and nothing else. A failure from a
     * *live* CDM (a PSSH it refuses) is a real answer and is thrown as before — retrying it would
     * only hide it.
     */
    override fun licenseRequest(): ByteArray = synchronized(lock) {
        onUse()
        val first = runCatching { link.call(SidecarTransport.LICENSE_REQUEST, pssh, 60_000) }
        first.getOrNull()?.let { return@synchronized it }
        val failure = first.exceptionOrNull()
        val dead = !link.alive
        if (dead) runCatching { link.close() } // release the handle of a process that is already gone
        val replacement = if (dead) restart?.invoke() else null
        if (replacement == null) throw failure ?: DrmLicenseException("the CDM sidecar did not answer")
        Logger.w("CdmSidecar", "the daemon stopped before the licence request; restarted it")
        link = replacement
        replacement.call(SidecarTransport.LICENSE_REQUEST, pssh, 60_000)
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

internal class SidecarTransport(command: List<String>) : AutoCloseable {

    private val released = java.util.concurrent.atomic.AtomicBoolean(false)

    private val process: Process = try {
        ProcessBuilder(command)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
    } catch (e: Exception) {
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
            // An answer queued here can still fail: close() interrupts this thread, and a blocked
            // put then throws. That is the shutdown path, not a fault worth a stack trace.
            putQuietly(closedMessage())
            dumpStderr()
        } catch (e: IOException) {
            putQuietly("the CDM sidecar pipe failed: ${e.message}")
            dumpStderr()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /**
     * Keeps what the daemon said when it died, in full, next to its configuration.
     *
     * The message in the exception carries three lines, which is right for a toast and useless for a
     * crash: the native host prints its faulting address, registers and backtrace on stderr before it
     * dies. This is the copy a bug report can attach. An orderly exit writes nothing: a daemon that
     * sees its stdin close because the app is quitting ends with code 0, and that is not a fault.
     */
    private fun dumpStderr() {
        if (closed) return // an orderly shutdown is not a fault
        val exit = runCatching { process.exitValue() }.getOrNull()
        if (exit == 0 || exit == null) return
        val text = stderr()
        if (text.isEmpty()) return
        runCatching {
            val base = System.getenv("LOCALAPPDATA") ?: System.getProperty("java.io.tmpdir")
            val file = java.io.File(base, "KittyTune/cdm-last-failure.log")
            file.parentFile?.mkdirs()
            file.writeText("exit $exit\n" + text + "\n")
            Logger.w("CdmSidecar", "the daemon stopped; its log is in ${file.absolutePath}")
        }
    }

    private fun putQuietly(message: String) {
        runCatching { answers.put(message) }
    }

    /**
     * Why the daemon stopped answering: its exit code and the tail of its stderr.
     *
     * The native host explains its failures there — a CPU without FSGSBASE, a module it cannot load —
     * and an ending that only says "the pipe closed" hides exactly the line a bug report needs.
     */
    private fun closedMessage(): String {
        val exit = runCatching { process.exitValue() }.getOrNull()
        val tail = synchronized(errors) { errors.toString() }.trim()
        val last = tail.lineSequence().filter { it.isNotBlank() }.toList().takeLast(3)
        return "the CDM sidecar closed the pipe" +
            (exit?.let { " (exit $it)" } ?: "") +
            (if (last.isEmpty()) "" else ": " + last.joinToString(" | "))
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
        if (TRACE) {
            val who = Throwable().stackTrace.drop(2).take(3).joinToString(" < ")
            Logger.d("CdmSidecar", "closing transport pid=${process.pid()} alive=$alive from $who")
        }
        closed = true
        runCatching { output.write(byteArrayOf(QUIT.toByte()) + length(0)); output.flush() }
        runCatching { process.waitFor(2, TimeUnit.SECONDS) }
        if (process.isAlive) process.destroyForcibly()
        reader.interrupt()
        errorReader.interrupt()
    }

    companion object {
        /** Set KITTYTUNE_CDM_TRACE=1 to log every transport start and close, with who closed it. */
        val TRACE: Boolean = !System.getenv("KITTYTUNE_CDM_TRACE").isNullOrBlank()

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
