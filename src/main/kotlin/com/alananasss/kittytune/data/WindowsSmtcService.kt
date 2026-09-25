package com.alananasss.kittytune.data

import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.Closeable
import java.io.File
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.concurrent.thread

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

    private var process: Process? = null
    private var writer: PrintWriter? = null
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastSentUpdate: String? = null

    init {
        if (System.getProperty("os.name").lowercase().contains("win")) {
            runCatching { start() }.onFailure { e ->
                println("Windows SMTC unavailable: ${e.message}")
                close()
            }
        }
    }

    private fun start() {
        val exe = extractBridge() ?: return
        val p = ProcessBuilder(exe.absolutePath).redirectErrorStream(true).start()
        process = p
        writer = PrintWriter(p.outputStream.bufferedWriter(StandardCharsets.UTF_8), true)
        thread(isDaemon = true, name = "WindowsSmtc-Reader") {
            runCatching {
                p.inputStream.bufferedReader(StandardCharsets.UTF_8).forEachLine { handleBridgeLine(it.trim()) }
            }
        }
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
        target.parentFile.mkdirs()
        target.writeBytes(bytes)
        return target
    }

    /** Pushes what is playing. Cheap to call often: identical updates are not re-sent. */
    fun updateMedia(track: Track?, isPlaying: Boolean, positionMs: Long, durationMs: Long) {
        val out = writer ?: return
        val fields = listOf(
            "UPDATE",
            track?.title.orEmpty(),
            track?.displayArtist.orEmpty(),
            (track?.publisherMetadata?.albumTitle ?: track?.publisherMetadata?.releaseTitle).orEmpty(),
            track?.fullResArtwork.orEmpty(),
            if (isPlaying) "1" else "0",
            positionMs.coerceAtLeast(0L).toString(),
            durationMs.coerceAtLeast(0L).toString(),
        )
        val message = fields.joinToString("\t") { it.replace(CONTROL_CHARS, " ") }
        if (message == lastSentUpdate) return
        lastSentUpdate = message
        out.println(message)
    }

    override fun close() {
        runCatching { writer?.println("QUIT") }
        runCatching { writer?.close() }
        process?.destroy()
        process = null
        writer = null
        mainScope.cancel()
    }

    private companion object {
        const val BRIDGE_RESOURCE = "WindowsSmtcBridge.exe"
        val CONTROL_CHARS = Regex("[\\t\\r\\n]")
    }
}
