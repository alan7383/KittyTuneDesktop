package com.alananasss.kittytune.data

import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The media session lives in this process now (issue #145): checks it the way Windows sees it, through
 * the system's session manager, and presses its buttons the way the flyout and the media keys do.
 */
class WindowsSmtcServiceTest {
    private val windows = System.getProperty("os.name").lowercase().contains("win")

    private fun probe(title: String, press: String = ""): Map<String, String> {
        val script = File(javaClass.getResource("/smtc-probe.ps1")!!.toURI())
        val process = ProcessBuilder(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
            "-File", script.absolutePath, "-Title", title, "-Press", press,
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        println("probe($press): $output")
        return output.lines().mapNotNull { line -> line.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }.toMap()
    }

    private fun waitFor(timeoutMs: Long = 5000, condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (condition()) return true
            Thread.sleep(100)
        }
        return condition()
    }

    @Test fun theSessionIsPublishedInProcessAndAnswersTheFlyout() {
        assumeTrue(windows)
        val events = CopyOnWriteArrayList<String>()
        val service = WindowsSmtcService(
            onPlay = { events += "play" },
            onPause = { events += "pause" },
            onPlayPause = { events += "toggle" },
            onNext = { events += "next" },
            onPrevious = { events += "previous" },
            onSeek = { events += "seek:$it" },
        )
        val title = "KittyTune SMTC probe ${System.nanoTime()}"
        try {
            val track = Track(id = 7, title = title, artworkUrl = null, durationMs = 180_000, user = User(1, "Probe Artist", null))
            service.updateMedia(track, isPlaying = true, positionMs = 30_000, durationMs = 180_000, playbackRate = 1.25f)
            var seen = emptyMap<String, String>()
            assertTrue(waitFor(10_000) { seen = probe(title); seen["FOUND"] == "1" }, "session not visible: $seen")
            // The hidden window's ID, not the process's: what the flyout resolves to "KittyTune".
            assertEquals(WindowsSmtcService.APP_USER_MODEL_ID, seen["APP"])
            assertEquals("Probe Artist", seen["ARTIST"])
            assertEquals("Playing", seen["STATUS"])
            assertEquals("180000", seen["END_MS"])
            assertEquals("1.25", seen["RATE"])
            assertEquals("1", seen["NEXT_ENABLED"])

            service.updateMedia(track, isPlaying = false, positionMs = 31_000, durationMs = 180_000, playbackRate = 1.25f)
            assertTrue(waitFor { probe(title)["STATUS"] == "Paused" })

            // A cover on disk (a local track's, a cached one) reaches the flyout too.
            val cover = File(System.getProperty("java.io.tmpdir"), "smtc-cover.png").apply {
                javax.imageio.ImageIO.write(java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", this)
            }
            service.updateMedia(track.copy(artworkUrl = cover.absolutePath), isPlaying = false, positionMs = 31_000, durationMs = 180_000, playbackRate = 1.25f)
            assertTrue(waitFor { probe(title)["HAS_THUMBNAIL"] == "1" }, "no thumbnail")

            // As the player does: each button changes the state the session shows, and Windows only offers
            // the buttons that make sense for it (no Pause on a paused session).
            for ((press, expected) in listOf("play" to "play", "pause" to "pause", "next" to "next", "previous" to "previous", "seek" to "seek:42000")) {
                val playing = press != "play"
                service.updateMedia(track, isPlaying = playing, positionMs = 31_000, durationMs = 180_000, playbackRate = 1.25f)
                assertTrue(waitFor { probe(title)["STATUS"] == if (playing) "Playing" else "Paused" })
                probe(title, press)
                assertTrue(waitFor { expected in events }, "$press did not reach the app: $events")
            }
        } finally {
            service.close()
        }
        // Closing takes the session away with it.
        assertTrue(waitFor { probe(title)["FOUND"] == "0" }, "session outlived the service")
    }
}
