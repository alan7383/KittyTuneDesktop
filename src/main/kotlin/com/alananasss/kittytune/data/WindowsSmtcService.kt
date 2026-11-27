package com.alananasss.kittytune.data

import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * KittyTune in the Windows media flyout, on the lock screen and behind the keyboard's media keys.
 *
 * Runs inside the app's own process ([WinRtSmtc]). Earlier versions started a separate helper
 * executable for this, which antivirus engines flagged (issue #145); there is no helper any more.
 * Every WinRT call happens on one worker thread, so a slow call never holds up the UI.
 */
class WindowsSmtcService(
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onPlayPause: () -> Unit,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
    private val onSeek: (Long) -> Unit,
) : Closeable {

    private val enabled = System.getProperty("os.name").lowercase().contains("win") &&
        !java.lang.Boolean.getBoolean("kittytune.trainStartupCache")
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "WindowsSmtc").apply { isDaemon = true }
    }
    @Volatile private var closed = false
    // Only the worker touches these.
    private var controls: WinRtSmtc? = null
    private var latest: Update? = null
    private var lastTimelineUpdateNanos: Long? = null

    private data class Update(
        val title: String,
        val artist: String,
        val album: String,
        val artwork: String,
        val playing: Boolean,
        val positionMs: Long,
        val durationMs: Long,
        val rate: Double,
    )

    init {
        if (enabled) submit { open() }
    }

    private fun open() {
        // The launcher, for the Start Menu shortcut a portable install has to be given (see
        // WindowsShortcutIdentity). Dev runs have none and only patch shortcuts that already exist.
        val launcher = sequenceOf(System.getProperty("jpackage.app-path"), ProcessHandle.current().info().command().orElse(null))
            .filterNotNull().map { java.io.File(it) }.firstOrNull { it.name.equals("KittyTune.exe", ignoreCase = true) && it.isFile }
        controls = WinRtSmtc(
            appUserModelId = APP_USER_MODEL_ID,
            launcher = launcher,
            onButton = { button ->
                val action: (() -> Unit)? = when (button) {
                    WinRtSmtc.Button.PLAY -> onPlay
                    WinRtSmtc.Button.PAUSE, WinRtSmtc.Button.STOP -> onPause
                    WinRtSmtc.Button.NEXT -> onNext
                    WinRtSmtc.Button.PREVIOUS -> onPrevious
                    WinRtSmtc.Button.OTHER -> null
                }
                if (action != null && !closed) mainScope.launch { action() }
            },
            onSeek = { ms -> if (!closed) mainScope.launch { onSeek(ms) } },
        )
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
        if (!enabled) return
        val update = Update(
            title = track?.displayTitle.orEmpty(),
            artist = track?.displayArtist.orEmpty(),
            album = track?.displayAlbumTitle.orEmpty(),
            artwork = track?.fullResArtwork.orEmpty(),
            playing = isPlaying,
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
            rate = playbackRate.toDouble(),
        )
        submit {
            val now = System.nanoTime()
            val last = lastTimelineUpdateNanos
            if (periodic && (track == null || (last != null && now - last < 1_000_000_000L))) return@submit
            if (update == latest) return@submit
            lastTimelineUpdateNanos = now
            latest = update
            publish(update)
        }
    }

    private fun publish(update: Update) {
        controls?.update(
            update.title, update.artist, update.album, update.artwork,
            update.playing, update.positionMs, update.durationMs, update.rate,
        )
    }

    /** Recreates the session (after the audio device or Explorer restarted) and republishes the last state. */
    fun restart() {
        if (!enabled) return
        submit {
            controls?.close()
            controls = null
            open()
            latest?.let(::publish)
        }
    }

    private fun submit(action: () -> Unit) {
        if (closed) return
        runCatching {
            worker.execute {
                if (!closed) runCatching(action).onFailure { println("Windows SMTC unavailable: ${it.message}") }
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { worker.execute { runCatching { controls?.close() }; controls = null } }
        worker.shutdown()
        runCatching { worker.awaitTermination(2, TimeUnit.SECONDS) }
        mainScope.cancel()
    }

    companion object {
        const val APP_USER_MODEL_ID = "KittyTune.KittyTune"

        /** Where versions up to 1.7.0 installed the helper executable; the updater still stops one left running. */
        internal const val LEGACY_BRIDGE_PATH = "app/smtc/KittyTune.exe"
    }
}
