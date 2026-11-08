package com.alananasss.kittytune.audio

import com.alananasss.kittytune.core.BoundedCache
import com.alananasss.kittytune.utils.Logger
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Extracts our unpackaged desktop renderer. No MSIX, certificate import or Developer Mode. */
internal object WindowsPlayReadyRuntime {
    private val streams = BoundedCache<String, String>(200)
    val windows get() = System.getProperty("os.name").startsWith("Windows", true)
    private val processes = java.util.concurrent.ConcurrentHashMap<String, Process>()
    @Volatile var failureReason: String? = null
        private set
    private var executable: File? = null
    val bridgeDirectory: File? by lazy {
        if (!windows || System.getProperty("java.awt.headless") == "true") null else runCatching {
            val name = "KittyTuneProtectedPlayer.exe"
            val bytes = javaClass.getResourceAsStream("/native/playready/$name")?.use { it.readBytes() }
                ?: File("build/generated/native-resources/native/playready", name).takeIf { it.isFile }?.readBytes()
                ?: error("Native PlayReady renderer is missing")
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }.take(16)
            val local = File(System.getenv("LOCALAPPDATA") ?: error("LOCALAPPDATA is unavailable"))
            val target = File(local, "KittyTune/playready/desktop-$hash").apply { mkdirs() }
            executable = File(target, name).also { if (!it.isFile || !it.readBytes().contentEquals(bytes)) it.writeBytes(bytes) }
            File(target, "bridge").apply { mkdirs() }
        }.onFailure { failureReason = it.message ?: "Renderer unavailable"; Logger.w("PlayReady", failureReason!!) }.getOrNull()
    }
    val isAvailable: Boolean get() = bridgeDirectory != null
    fun rememberStream(url: String, token: String?) { if (windows && !token.isNullOrBlank()) streams[url] = token }
    fun tokenFor(url: String): String? = if (windows) streams[url] else null
    @Synchronized fun launch(directory: File) {
        if (!File(directory, "command.json").isFile) return
        val key = directory.absolutePath
        if (processes[key]?.isAlive == true) return
        val process = ProcessBuilder(requireNotNull(executable).absolutePath, key)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        processes[key] = process
        Thread({
            process.waitFor()
            processes.remove(key, process)
            // Only encrypted media was cached. Wait for native file handles to close before deleting.
            directory.listFiles()?.filter { it.extension == "mp4" || it.name.endsWith(".part") }?.forEach { it.delete() }
        }, "KittyTune-PlayReady-cleanup").apply { isDaemon = true; start() }
    }
}

/** One independently controlled native MediaPlayer, including prebuffer and crossfade engines. */
internal class WindowsProtectedPlayback(
    root: File,
    url: String,
    token: String,
    startPositionMs: Long,
    private val onStatus: (Status) -> Unit,
    private val onError: (Throwable) -> Unit,
    private val launch: (File) -> Unit = WindowsPlayReadyRuntime::launch,
    private val prepare: (File, String) -> JSONObject = WindowsProtectedMedia::prepare,

) : Closeable {
    data class Status(val positionMs: Long, val durationMs: Long, val state: Int, val ended: Boolean,
        val playbackRate: Double = 1.0, val balance: Double = 0.0, val outputVolume: Double = 1.0)
    private val directory = File(root, UUID.randomUUID().toString()).apply { mkdirs() }
    private val command = JSONObject().put("url", url).put("licenseAuthToken", token)
        .put("playing", false).put("volume", 1.0).put("rate", 1.0)
        .put("positionMs", startPositionMs.coerceAtLeast(0)).put("seekRevision", 0).put("closed", false)
    private val worker = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "KittyTune-PlayReady").apply { isDaemon = true }
    }
    private val startedAt = System.currentTimeMillis()
    private var closed = false
    private var failureDelivered = false
    private var endedDelivered = false
    @Volatile private var launchRequested = true
    private var lastLaunch = 0L
    private var preparedUrl: String? = null
    @Volatile private var lastStatusAt = 0L
    @Volatile private var wakeGraceUntil = 0L

    init { worker.scheduleWithFixedDelay({ poll() }, 0, 100, TimeUnit.MILLISECONDS) }

    @Synchronized private fun publish() {
        command.put("updatedAt", System.currentTimeMillis())
        val temp = File(directory, "command.json.tmp").toPath()
        Files.writeString(temp, command.toString())
        try { Files.move(temp, File(directory, "command.json").toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        catch (_: java.nio.file.AccessDeniedException) { return }
        catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temp, File(directory, "command.json").toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
    private fun poll() {
        val status = runCatching {
            val url = synchronized(this) { if (closed) return; command.getString("url") }
            if (url != preparedUrl) {
                val media = prepare(directory, url)
                synchronized(this) {
                    if (closed || command.getString("url") != url) return
                    media.keySet().forEach { command.put(it, media.get(it)) }
                    preparedUrl = url
                }
            }
            synchronized(this) { if (closed) return; publish() }
            val shouldPlay = synchronized(this) { command.optBoolean("playing") }
            if (launchRequested || (shouldPlay && System.currentTimeMillis() - lastLaunch > 5_000)) {
                launch(directory); launchRequested = false; lastLaunch = System.currentTimeMillis()
            }
            val file = File(directory, "status.json")
            if (!file.isFile) {
                check(System.currentTimeMillis() - startedAt < 30_000) { "Windows protected player did not start" }
                return
            }
            val json = try { JSONObject(Files.readString(file.toPath())) }
            catch (_: java.io.IOException) {
                check(System.currentTimeMillis() - maxOf(lastStatusAt, startedAt) < 30_000) { "Windows protected player status unavailable" }
                return
            }
            lastStatusAt = json.getLong("updatedAt")
            // UWP may suspend a paused/background app. play() reactivates it; a paused transport
            // must not be reported as a crashed decoder just because its last status is old.
            if (shouldPlay && System.currentTimeMillis() - lastStatusAt >= 15_000) {
                if (System.currentTimeMillis() < wakeGraceUntil) return
                error("Windows protected player stopped responding")
            }
            val error = json.optString("error")
            check(error.isEmpty()) { error }
            Status(json.optLong("positionMs"), json.optLong("durationMs"), json.optInt("state"), json.optBoolean("ended"),
                json.optDouble("playbackRate", 1.0), json.optDouble("balance", 0.0), json.optDouble("outputVolume", 1.0))
        }.getOrElse { fail(it); return }
        synchronized(this) {
            if (closed || failureDelivered || (status.ended && endedDelivered)) return
            endedDelivered = status.ended
        }
        onStatus(status)
    }
    private fun fail(error: Throwable) {
        synchronized(this) { if (closed || failureDelivered) return; failureDelivered = true }
        onError(error)
        close()
    }
    @Synchronized fun play() {
        if (closed) return
        if (lastStatusAt > 0 && System.currentTimeMillis() - lastStatusAt >= 15_000) wakeGraceUntil = System.currentTimeMillis() + 10_000
        command.put("playing", true)
        launchRequested = true
    }
    @Synchronized fun pause() { if (!closed) command.put("playing", false) }
    @Synchronized fun volume(value: Double) { if (!closed) command.put("volume", value.coerceIn(0.0, 1.0)) }
    @Synchronized fun rate(value: Float) { if (!closed) command.put("rate", value.toDouble().coerceIn(0.5, 2.0)) }
    @Synchronized fun effects(state: com.alananasss.kittytune.ui.player.AudioEffectsState) {
        if (closed) return
        command.put("eightD", state.is8DEnabled).put("eightDSpeed", state.eightDSpeed.coerceIn(0f, 1f))
            .put("tranceGate", state.isTranceGateEnabled).put("gateSpeed", state.tranceGateSpeed.coerceIn(0f, 1f))
            .put("gatePattern", state.tranceGatePattern.coerceIn(0f, 1f)).put("gateMix", state.tranceGateMix.coerceIn(0f, 1f))
    }
    @Synchronized fun seek(ms: Long) {
        if (closed) return
        endedDelivered = false
        command.put("positionMs", ms.coerceAtLeast(0)).put("seekRevision", command.getLong("seekRevision") + 1)
    }
    @Synchronized fun replaceSource(url: String, token: String, positionMs: Long) {
        if (closed) return
        command.put("url", url).put("licenseAuthToken", token)
        seek(positionMs)
    }
    @Synchronized override fun close() {
        if (closed) return
        closed = true
        // Revoke this session and erase the short-lived license token even after an engine is replaced.
        command.put("closed", true).put("playing", false).put("licenseAuthToken", "").put("url", "")
        runCatching { publish() }
        worker.shutdownNow()
    }
}
