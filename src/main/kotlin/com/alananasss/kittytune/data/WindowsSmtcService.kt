package com.alananasss.kittytune.data

import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.Closeable
import java.io.File

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
        // Our own launcher path, so the bridge can give Windows's flyout a shortcut to resolve the
        // session's app identity from (see WindowsSmtcBridge.cs). Dev runs from gradle have no
        // launcher exe; the bridge then only patches whatever shortcut already exists.
        val launcher = sequenceOf(
            System.getProperty("jpackage.app-path"),
            ProcessHandle.current().info().command().orElse(null),
        ).filterNotNull().map(::File).firstOrNull { it.name.equals("KittyTune.exe", ignoreCase = true) }
        val exe = if (launcher != null) {
            launcher.absoluteFile.parentFile.resolve(PACKAGED_BRIDGE_PATH)
        } else {
            System.getProperty("kittytune.smtc.bridge")?.let(::File)
        }?.takeIf { it.isAbsolute && it.isFile } ?: return null
        return ProcessBuilder(
            listOfNotNull(exe.absolutePath, launcher?.absolutePath),
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
        internal const val PACKAGED_BRIDGE_PATH = "app/smtc/KittyTune.exe"
        val CONTROL_CHARS = Regex("[\\t\\r\\n]")
    }
}
