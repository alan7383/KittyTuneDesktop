package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.data.local.AppThemeMode
import com.alananasss.kittytune.data.stats.ActivityBucket
import com.alananasss.kittytune.data.stats.ListeningReport
import com.alananasss.kittytune.data.stats.ReportArtist
import com.alananasss.kittytune.data.stats.ReportPeriod
import com.alananasss.kittytune.data.stats.ReportTrack
import com.alananasss.kittytune.data.stats.ReportWindow
import com.alananasss.kittytune.ui.theme.SoundTuneTheme
import java.io.File
import java.time.DayOfWeek
import javax.swing.SwingUtilities
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Test

class ListeningStatsLayoutTest {
    private val output = File("build/previews").apply { mkdirs() }

    @Test
    fun fullscreenListeningStatsHeaderOverviewAndStoryFillWideScreenCleanly() {
        val previousLanguage = Strings.appLanguage
        try {
            Strings.appLanguage = "en"
            val dayMs = 86_400_000L
            val start = 1_795_000_000_000L
            val window = ReportWindow(start, start + 7 * dayMs)
            val activity = (0 until 7).map { i ->
                ActivityBucket(start + i * dayMs, listOf(45, 92, 34, 128, 76, 110, 88)[i] * 60_000L, isMonth = false)
            }
            val hours = (0 until 24).map { h ->
                when (h) {
                    in 0..5 -> 5 * 60_000L
                    in 6..11 -> 18 * 60_000L
                    in 12..17 -> 34 * 60_000L
                    else -> 42 * 60_000L
                }
            }
            val topTracks = (1..8).map { i ->
                ReportTrack(
                    trackId = i.toLong(),
                    title = "Track Title #$i",
                    artistName = "Artist $i",
                    artworkUrl = null,
                    source = "soundcloud",
                    plays = 20 - i,
                    listenMs = (60L - i * 4) * 60_000L,
                )
            }
            val topArtists = (1..8).map { i ->
                ReportArtist(
                    name = "Artist $i",
                    imageUrl = null,
                    artistId = i.toLong(),
                    permalink = null,
                    source = "soundcloud",
                    plays = 30 - i,
                    listenMs = (90L - i * 5) * 60_000L,
                )
            }
            val report = ListeningReport(
                window = window,
                totalListenMs = activity.sumOf { it.listenMs },
                previousListenMs = 420 * 60_000L,
                plays = 245,
                skips = 18,
                completed = 227,
                uniqueTracks = 94,
                uniqueArtists = 31,
                activity = activity,
                hours = hours,
                topTracks = topTracks,
                topArtists = topArtists,
                longestStreakDays = 14,
                activeDays = 7,
                daysElapsed = 7,
                busiestWeekday = DayOfWeek.THURSDAY,
            )

            SwingUtilities.invokeAndWait { Thread.currentThread().contextClassLoader = javaClass.classLoader }

            fun render(fileName: String, body: @androidx.compose.runtime.Composable () -> Unit) {
                val scene = ImageComposeScene(width = 1600, height = 980, density = Density(1f)) {
                    SoundTuneTheme(themeMode = AppThemeMode.DARK, dynamicColor = false, keyColor = 0xFF537BCC.toInt()) {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            Column(Modifier.fillMaxSize()) {
                                StatsHeader(
                                    report = report,
                                    period = ReportPeriod.WEEK,
                                    periodOffset = 0,
                                    canGoBack = true,
                                    canGoForward = false,
                                    style = StatsStyle.OVERVIEW,
                                    onSelect = {},
                                    onPrevious = {},
                                    onNext = {},
                                    onReset = {},
                                    onStyle = {},
                                    onPrivacy = {},
                                )
                                body()
                            }
                        }
                    }
                }
                try {
                    SwingUtilities.invokeAndWait {
                        var t = 1_000_000_000L
                        repeat(15) {
                            scene.render(t).close()
                            t += 100_000_000L
                        }
                        scene.render(t).use { image ->
                            checkNotNull(image.encodeToData(EncodedImageFormat.PNG)).use { data ->
                                File(output, fileName).writeBytes(data.bytes)
                            }
                        }
                    }
                } finally {
                    SwingUtilities.invokeAndWait { scene.close() }
                }
            }

            render("stats-overview-fullscreen.png") {
                OverviewStats(
                    report = report,
                    period = ReportPeriod.WEEK,
                    onOpen = {},
                    onTrackClick = {},
                    onArtistClick = {},
                )
            }

            render("stats-story-fullscreen.png") {
                StoryStats(
                    report = report,
                    period = ReportPeriod.WEEK,
                    onOpen = {},
                    onTrackClick = {},
                    onArtistClick = {},
                )
            }
        } finally {
            Strings.appLanguage = previousLanguage
        }
    }
}
