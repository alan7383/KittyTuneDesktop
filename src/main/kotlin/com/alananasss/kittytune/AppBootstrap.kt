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
import kotlinx.coroutines.launch

/**
 * One-shot application bootstrap — the desktop equivalent of the init sequence that
 * MainActivity.onCreate + PlaybackService.onCreate + KittyTuneApp ran on Android.
 * Order matters: DB and Config first, then repositories, then the player.
 */
object AppBootstrap {

    @Volatile
    private var done = false

    fun init() {
        if (done) return
        done = true

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
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                com.alananasss.kittytune.data.sync.SyncApply.reconcile()
            }
        }

        // 4. Player + session keep-alive.
        MusicManager.init()
        SessionManager.start()

        // 4b. Anonymous client_id: validate/scrape in background (replaces the ghost
        // WebView interception on Android). Guest mode depends on this.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            com.alananasss.kittytune.data.ClientIdScraper.ensureClientId()
        }

        // 5. Connectivity watcher (replaces ConnectivityManager callback).
        NetworkMonitor.start()

        // 6. End4 dotfiles Material You watcher (live color updates)
        if (com.alananasss.kittytune.data.theme.End4ThemeManager.isInstalled()) {
            com.alananasss.kittytune.data.theme.End4ThemeManager.startWatching()
        }
    }
}
