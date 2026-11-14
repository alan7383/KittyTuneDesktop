package com.alananasss.kittytune.data

import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.Closeable
import java.io.File
import java.security.MessageDigest

/**
 * KittyTune in the Windows media flyout, on the lock screen and behind the keyboard's media keys.
 *
 * System Media Transport Controls are WinRT, out of the JVM's reach, so a small bundled process
 * (`native/WindowsSmtcBridge.cs`) owns the session and the two talk over its stdin/stdout. The bridge
 * exits when its stdin closes, so it goes away with the app even if the app is killed.
 */
class WindowsSmtcService(
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onPlayPause: () -> Unit,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
    private val onSeek: (Long) -> Unit,
) : Closeable {

    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val bridge = if (System.getProperty("os.name").lowercase().contains("win") &&
        !java.lang.Boolean.getBoolean("kittytune.trainStartupCache")
    ) {
        SmtcBridgeWorker(::start, ::handleBridgeLine)
    } else null
    private var lastTimelineUpdateNanos: Long? = null

    private fun start(): Process? {
        val exe = extractBridge() ?: return null
        // Our own launcher path, so the bridge can give Windows's flyout a shortcut to resolve the
        // session's app identity from (see WindowsSmtcBridge.cs). Dev runs from gradle have no
        // launcher exe; the bridge then only patches whatever shortcut already exists.
        val launcher = System.getProperty("jpackage.app-path")?.takeIf {
            it.substringAfterLast('/').substringAfterLast('\\').equals("KittyTune.exe", ignoreCase = true)
        } ?: ProcessHandle.current().info().command().orElse(null)
            ?.takeIf { it.substringAfterLast('/').substringAfterLast('\\').equals("KittyTune.exe", ignoreCase = true) }
        return ProcessBuilder(
            mutableListOf(exe.absolutePath).also { if (launcher != null) it.add(launcher) },
        ).redirectErrorStream(true).start()
    }

    private fun handleBridgeLine(line: String) {
        val action: (() -> Unit)? = when {
            line == "CMD:PLAY" -> onPlay
            line == "CMD:PAUSE" -> onPause
            line == "CMD:TOGGLE" -> onPlayPause
            line == "CMD:NEXT" -> onNext
            line == "CMD:PREV" -> onPrevious
            line.startsWith("SEEK:") -> line.removePrefix("SEEK:").toLongOrNull()?.let { ms -> { onSeek(ms) } }
            line.startsWith("ERROR:") -> { println("Windows SMTC bridge: $line"); null }
            else -> null
        }
        if (action != null) mainScope.launch { action() }
    }

    /**
     * Copies the bridge out of the jar, into a folder named after its content hash. A bridge left
     * running by an earlier instance keeps its file locked, and an update must not end up talking to
     * that stale copy. Named `KittyTune.exe` because Windows labels the session with the file name.
     */
    private fun extractBridge(): File? {
        val bytes = javaClass.getResourceAsStream("/native/$BRIDGE_RESOURCE")?.use { it.readBytes() }
            ?: File("src/main/resources/native/$BRIDGE_RESOURCE").takeIf { it.exists() }?.readBytes()
            ?: return null
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }.take(12)
        val target = File(File(System.getProperty("java.io.tmpdir"), "kittytune_smtc"), hash).resolve("KittyTune.exe")
        if (target.isFile && target.length() == bytes.size.toLong()) return target
        // Set executable on the temp copy too, not only on dev-mode resources, so a fresh hash-dir
        // never starts non-executable.
        target.parentFile.mkdirs()
        target.setExecutable(true)
        target.writeBytes(bytes)
        return target
    }

    /** Playback events publish immediately. Continuous playback refreshes the Windows timeline once a second. */
    fun updateMedia(
        track: Track?,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        playbackRate: Float,
        periodic: Boolean = false,
    ) {
        if (bridge == null) return
        val now = System.nanoTime()
        val lastUpdate = lastTimelineUpdateNanos
        if (periodic && (track == null || (lastUpdate != null && now - lastUpdate < 1_000_000_000L))) return
        lastTimelineUpdateNanos = now
        val fields = listOf(
            "UPDATE",
            track?.title.orEmpty(),
            track?.displayArtist.orEmpty(),
            (track?.publisherMetadata?.albumTitle ?: track?.publisherMetadata?.releaseTitle).orEmpty(),
            track?.fullResArtwork.orEmpty(),
            if (isPlaying) "1" else "0",
            positionMs.coerceAtLeast(0L).toString(),
            durationMs.coerceAtLeast(0L).toString(),
            playbackRate.toString(),
        )
        val message = fields.joinToString("\t") { it.replace(CONTROL_CHARS, " ") }
        bridge.update(message)
    }

    /** Queued with media writes; the new helper receives the latest known update automatically. */
    fun restart() {
        bridge?.restart()
    }

    override fun close() {
        bridge?.close()
        mainScope.cancel()
    }

    companion object {
        const val APP_USER_MODEL_ID = "KittyTune.KittyTune"
        private const val BRIDGE_RESOURCE = "WindowsSmtcBridge.exe"
        val CONTROL_CHARS = Regex("[\\t\\r\\n]")
    }
}
