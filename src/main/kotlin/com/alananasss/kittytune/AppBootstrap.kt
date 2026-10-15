package com.alananasss.kittytune

import com.alananasss.kittytune.core.NetworkMonitor
import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.data.AlbumResolver
import com.alananasss.kittytune.data.DownloadManager
import com.alananasss.kittytune.data.HistoryRepository
import com.alananasss.kittytune.data.LikeRepository
import com.alananasss.kittytune.data.ListeningStatsRepository
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.RecognitionHistoryRepository
import com.alananasss.kittytune.data.RepostRepository
import com.alananasss.kittytune.data.SessionManager
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.utils.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * One-shot application bootstrap — the desktop equivalent of the init sequence that
 * MainActivity.onCreate + PlaybackService.onCreate + KittyTuneApp ran on Android.
 * Order matters: DB and Config first, then repositories, then the player.
 */
object AppBootstrap {

    @Volatile
    private var done = false

    /**
     * A single supervised scope for the bootstrap's long-running coroutines (sync reconcile,
     * client_id scrape, native memory trim). The previous code created three separate
     * `CoroutineScope(Dispatchers.X).launch { ... }` with no stored reference — the scopes
     * were unparented and the launched coroutines had no parent job, making them impossible to
     * cancel and structurally unstructured. This scope is owned by the singleton, so the
     * coroutines are properly parented and the whole group can be cancelled if ever needed.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Set to true when the main window is visible, false when it's hidden/minimised. The native
     * memory trim loop reads this to skip work while the app is in the tray — paying 15-second
     * native trim cycles on an idle hidden app is wasted CPU (audit issue).
     */
    @Volatile
    var windowVisible: Boolean = true

    @OptIn(androidx.compose.ui.InternalComposeUiApi::class)
    fun init() {
        if (done) return
        done = true

        // Initialize native memory allocator (M_ARENA_MAX=2 on Linux to eliminate 96-arena multi-gigabyte bloat)
        com.alananasss.kittytune.utils.NativeMemoryManager.init()

        // Cap JavaCPP off-heap memory to 64MB so FFmpeg/native decoders release frame buffers promptly
        System.setProperty("org.bytedeco.javacpp.maxbytes", "67108864")
        // Prevent JavaCPP from falsely throwing OutOfMemoryError when host physical memory > 8GB
        System.setProperty("org.bytedeco.javacpp.maxphysicalbytes", "0")

        com.alananasss.kittytune.audio.ai.OnnxHelper.ensureNativeLibrariesLoaded()
        com.alananasss.kittytune.core.LinuxAppIdHelper.apply()

        runCatching {
            androidx.compose.ui.platform.registerSkikoComposeImplementation()
        }

        // Suppress benign Skiko / Compose Multiplatform desktop redrawer race conditions
        // such as "RootNodeOwner is already disposed" during fast scene disposal on AWT event thread.
        fun isBenign(t: Throwable?): Boolean {
            var curr: Throwable? = t
            while (curr != null) {
                val msg = curr.message.orEmpty()
                if (msg.contains("RootNodeOwner is already disposed", ignoreCase = true) ||
                    (msg.contains("ComposeScene", ignoreCase = true) && msg.contains("disposed", ignoreCase = true))
                ) {
                    return true
                }
                curr = curr.cause
            }
            return false
        }

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (isBenign(throwable)) return@setDefaultUncaughtExceptionHandler
            defaultHandler?.uncaughtException(thread, throwable) ?: throwable.printStackTrace()
        }

        runCatching {
            java.awt.Toolkit.getDefaultToolkit().systemEventQueue.push(object : java.awt.EventQueue() {
                override fun dispatchEvent(event: java.awt.AWTEvent) {
                    try {
                        super.dispatchEvent(event)
                    } catch (t: Throwable) {
                        if (!isBenign(t)) {
                            Thread.currentThread().uncaughtExceptionHandler?.uncaughtException(Thread.currentThread(), t)
                                ?: t.printStackTrace()
                        }
                    }
                }
            })
        }

        // 0. Memory diagnostics, before anything has had a chance to allocate. Does nothing at all
        //    unless the launcher asked for it, which only the diagnostic build does (issue #33).
        com.alananasss.kittytune.utils.MemoryDiagnostics.start()

        // 1. Core config + persistence.
        Config.init()
        AppDatabase.init()

        // 1b. Network Proxy (applies system properties and OkHttp hooks)
        com.alananasss.kittytune.data.network.ProxyManager.applyConfiguration()

        // 2. Language (mirrors KittyTuneApp locale setup).
        Strings.appLanguage = PlayerPreferences().getAppLanguage().code

        // 3. Repositories (all global singletons, like the Android objects).
        LikeRepository.init()
        HistoryRepository.init()
        ListeningStatsRepository.init()
        RepostRepository.init()
        RecognitionHistoryRepository.init()
        DownloadManager.init()
        AlbumResolver.init()

        // The sync listener. It opens a port on the local network, so it stays off until there is a
        // reason for it — which is a device having been paired. Pairing turns it on; an install that
        // has never paired never opens anything (issue #33).
        if (com.alananasss.kittytune.data.sync.SyncService.isListenerEnabled) {
            com.alananasss.kittytune.data.sync.SyncService.start()
        }

        // Paired once, in step from then on. Costs nothing until something is paired.
        if (!com.alananasss.kittytune.data.sync.SyncPeers.isEmpty()) {
            com.alananasss.kittytune.data.sync.SyncScheduler.start()
            // Anything the log holds that the statistics table is missing goes back in. The marks advance
            // when events are merged, not when their rows land, so a failed or cancelled insert used to lose
            // listens the peer would never send again (issue #33).
            scope.launch {
                com.alananasss.kittytune.data.sync.SyncApply.reconcile()
            }
        }

        // 4. Player + session keep-alive.
        MusicManager.init()
        SessionManager.start()

        // 4b. Anonymous client_id: validate/scrape in background (replaces the ghost
        // WebView interception on Android). Guest mode depends on this.
        scope.launch {
            com.alananasss.kittytune.data.ClientIdScraper.ensureClientId()
        }

        // 5. Connectivity watcher (replaces ConnectivityManager callback).
        NetworkMonitor.start()

        // 6. End4 dotfiles Material You watcher (live color updates)
        if (com.alananasss.kittytune.data.theme.End4ThemeManager.isInstalled()) {
            com.alananasss.kittytune.data.theme.End4ThemeManager.startWatching()
        }

        // 7. Periodic native memory cleanup (JavaCPP Pointer + Linux glibc arena trim).
        // Skipped while the main window is hidden: paying 15-second native trim cycles on an
        // idle hidden app is wasted CPU (audit issue). The loop checks [windowVisible] each tick
        // so visibility changes take effect without restarting the loop.
        scope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(15_000L)
                if (!windowVisible) continue
                com.alananasss.kittytune.utils.NativeMemoryManager.trim()
            }
        }
    }
}
