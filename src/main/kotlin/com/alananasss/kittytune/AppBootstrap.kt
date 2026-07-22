package com.alananasss.kittytune

import com.alananasss.kittytune.core.NetworkMonitor
import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.data.AchievementManager
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

        // 1. Core config + persistence.
        Config.init()
        AppDatabase.init()

        // 2. Language (mirrors KittyTuneApp locale setup).
        Strings.appLanguage = PlayerPreferences().getAppLanguage().code

        // 3. Repositories (all global singletons, like the Android objects).
        LikeRepository.init()
        HistoryRepository.init()
        ListeningStatsRepository.init()
        RepostRepository.init()
        RecognitionHistoryRepository.init()
        AchievementManager.init()
        DownloadManager.init()
        AlbumResolver.init()

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

        // 6. Daily streak check (ON_RESUME on Android).
        AchievementManager.checkDailyStreak()
    }
}
