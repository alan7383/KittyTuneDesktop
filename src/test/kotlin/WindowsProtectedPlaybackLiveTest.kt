package com.alananasss.kittytune.audio

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Opt-in real Windows/PlayReady test. No account database, browser or third-party binary is read. */
class WindowsProtectedPlaybackLiveTest {
    @Test fun closingOneNativeProcessDoesNotStopAnotherSession() {
        assumeTrue(System.getenv("KITTYTUNE_PLAYREADY_LIVE") == "1")
        val request = JSONObject(File(System.getenv("KITTYTUNE_PLAYREADY_REQUEST")).readText())
        val root = requireNotNull(WindowsPlayReadyRuntime.bridgeDirectory)
        val failure = AtomicReference<Throwable>()
        val a = AtomicReference<WindowsProtectedPlayback.Status>()
        val b = AtomicReference<WindowsProtectedPlayback.Status>()
        val first = WindowsProtectedPlayback(root, request.getString("url"), request.getString("licenseAuthToken"), 0, a::set, failure::set)
        val second = WindowsProtectedPlayback(root, request.getString("url"), request.getString("licenseAuthToken"), 0, b::set, failure::set)
        fun await(predicate: () -> Boolean) {
            val deadline = System.nanoTime() + 25_000_000_000L
            while (System.nanoTime() < deadline) {
                failure.get()?.let { throw AssertionError("Concurrent native sessions", it) }
                if (predicate()) return
                Thread.sleep(50)
            }
            throw AssertionError("Concurrent native sessions timed out")
        }
        try {
            first.volume(0.03); second.volume(0.03)
            first.play(); second.play()
            await { (a.get()?.positionMs ?: 0) > 1200 && (b.get()?.positionMs ?: 0) > 1200 }
            first.close()
            val position = b.get().positionMs
            await { (b.get()?.positionMs ?: 0) > position + 1500 }
        } finally { first.close(); second.close() }
    }

    @Test fun nativeSpatialAndGateEffectsChangeTheWindowsRendererControls() {
        assumeTrue(System.getenv("KITTYTUNE_PLAYREADY_LIVE") == "1")
        val request = JSONObject(File(System.getenv("KITTYTUNE_PLAYREADY_REQUEST")).readText())
        val root = requireNotNull(WindowsPlayReadyRuntime.bridgeDirectory)
        val latest = AtomicReference<WindowsProtectedPlayback.Status>()
        val failure = AtomicReference<Throwable>()
        val playback = WindowsProtectedPlayback(root, request.getString("url"), request.getString("licenseAuthToken"), 0, latest::set, failure::set)
        fun await(predicate: (WindowsProtectedPlayback.Status) -> Boolean) {
            val end = System.nanoTime() + 25_000_000_000L
            while (System.nanoTime() < end) {
                failure.get()?.let { throw AssertionError("Native effects", it) }
                if (latest.get()?.let(predicate) == true) return
                Thread.sleep(50)
            }
            throw AssertionError("Native effect timed out")
        }
        fun sample(field: (WindowsProtectedPlayback.Status) -> Double): List<Double> {
            val values = mutableListOf<Double>()
            repeat(35) { latest.get()?.let { values += field(it) };Thread.sleep(50) }
            return values
        }
        try {
            playback.volume(0.05);playback.rate(2f);playback.play()
            await { it.positionMs > 1200 }
            val initialPosition = latest.get().positionMs
            val initialTime = System.nanoTime()
            Thread.sleep(1600)
            val initialRate = (latest.get().positionMs - initialPosition) / ((System.nanoTime() - initialTime) / 1_000_000.0)
            assertEquals("Speed set before loading must affect the playback clock", 2.0, initialRate, 0.20)
            playback.rate(1f)
            playback.effects(com.alananasss.kittytune.ui.player.AudioEffectsState(is8DEnabled = true, eightDSpeed = 1f))
            await { kotlin.math.abs(it.balance) > 0.1 }
            val pan = sample { it.balance }
            assertTrue("8D moves the native stereo balance", pan.max() - pan.min() > 0.3)
            playback.effects(com.alananasss.kittytune.ui.player.AudioEffectsState(isTranceGateEnabled = true, tranceGatePattern = 1f, tranceGateMix = 1f))
            await { kotlin.math.abs(it.balance) < 0.001 && it.outputVolume < 0.01 }
            val gain = sample { it.outputVolume }
            assertTrue("Gate modulates native volume", gain.max() - gain.min() > 0.035)
            playback.effects(com.alananasss.kittytune.ui.player.AudioEffectsState())
            await { kotlin.math.abs(it.balance) < 0.001 && kotlin.math.abs(it.outputVolume - 0.05) < 0.001 }
        } finally { playback.close() }
    }

    @Test fun audioEngineRoutesTheResolvedStreamThroughPlayReady() {
        assumeTrue(System.getenv("KITTYTUNE_PLAYREADY_LIVE") == "1")
        val request = JSONObject(File(System.getenv("KITTYTUNE_PLAYREADY_REQUEST")).readText())
        assertTrue("Native companion available", WindowsPlayReadyRuntime.isAvailable)
        val url = request.getString("url")
        WindowsPlayReadyRuntime.rememberStream(url, request.getString("licenseAuthToken"))
        val failure = AtomicReference<Throwable>()
        val engine = AudioEngine()
        fun await(description: String, predicate: () -> Boolean) {
            val deadline = System.nanoTime() + 25_000_000_000L
            while (System.nanoTime() < deadline) {
                failure.get()?.let { throw AssertionError(description, it) }
                if (predicate()) return
                Thread.sleep(50)
            }
            throw AssertionError("Timed out: $description; state=${engine.state}, position=${engine.positionMs}")
        }
        try {
            engine.onError = failure::set
            engine.setVolume(0.04f)
            engine.setMediaItem(url)
            engine.prepare()
            engine.play()
            await("AudioEngine playing protected stream") { engine.isPlaying && engine.durationMs > 60_000 && engine.positionMs > 1200 }
            engine.pause()
            Thread.sleep(400)
            val pausedPosition = engine.positionMs
            Thread.sleep(400)
            assertTrue(kotlin.math.abs(engine.positionMs - pausedPosition) < 150)
            engine.seekTo(60_000)
            engine.play()
            await("AudioEngine resumed after seek") { engine.isPlaying && engine.positionMs > 61_000 }
            for (requestedRate in listOf(2f, 0.5f)) {
                engine.applyEffects(com.alananasss.kittytune.ui.player.AudioEffectsState(speed = requestedRate))
                Thread.sleep(400)
                val startPosition = engine.positionMs
                val startTime = System.nanoTime()
                Thread.sleep(1800)
                val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0
                val actual = (engine.positionMs - startPosition) / elapsedMs
                assertEquals("Playback clock must change, not just the reported rate", requestedRate.toDouble(), actual, 0.20)
            }
        } finally { engine.release() }
        assertNull(failure.get())
    }

    @Test fun nativeProtectedPlaybackSupportsPauseSeekResumeAndCleanup() {
        assumeTrue(System.getenv("KITTYTUNE_PLAYREADY_LIVE") == "1")
        val request = JSONObject(File(System.getenv("KITTYTUNE_PLAYREADY_REQUEST")).readText())
        // Exercises extraction/registration of the actual bundled companion as well as IPC.
        val root = requireNotNull(WindowsPlayReadyRuntime.bridgeDirectory) { "Bundled PlayReady companion unavailable: ${WindowsPlayReadyRuntime.failureReason}" }
        WindowsPlayReadyRuntime.launch(root)
        // A directory can be visible to the native scanner before its first command exists.
        // It must not get a fatal status that poisons the subsequently published session.
        val unpublished = File(root, java.util.UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            Thread.sleep(400)
            assertFalse("An unpublished session is ignored", File(unpublished, "status.json").exists())
        } finally { unpublished.delete() }
        val latest = AtomicReference<WindowsProtectedPlayback.Status>()
        val failure = AtomicReference<Throwable>()
        val playback = WindowsProtectedPlayback(root, request.getString("url"), request.getString("licenseAuthToken"), 0,
            latest::set, failure::set)
        fun await(description: String, predicate: (WindowsProtectedPlayback.Status) -> Boolean): WindowsProtectedPlayback.Status {
            val deadline = System.nanoTime() + 25_000_000_000L
            while (System.nanoTime() < deadline) {
                failure.get()?.let { throw AssertionError(description, it) }
                latest.get()?.let { if (predicate(it)) return it }
                Thread.sleep(50)
            }
            throw AssertionError("Timed out: $description; status=${latest.get()}")
        }
        try {
            playback.volume(0.05)
            playback.play()
            val playing = await("playing") { it.state == 3 && it.positionMs > 1200 }
            val nativeStatus = root.listFiles()!!.map { File(it, "status.json") }.filter { it.isFile }
                .mapNotNull { runCatching { JSONObject(java.nio.file.Files.readString(it.toPath())) }.getOrNull() }
                .first { it.optInt("state") == 3 }
            assertFalse("Renderer is not an AppContainer", nativeStatus.getBoolean("appContainer"))
            assertFalse("Renderer has no MSIX identity", nativeStatus.getBoolean("hasPackageIdentity"))
            assertTrue(playing.durationMs > 60_000)
            playback.pause()
            val paused = await("paused") { it.state == 4 }
            Thread.sleep(400)
            assertTrue(kotlin.math.abs(latest.get().positionMs - paused.positionMs) < 150)
            playback.seek(45_000)
            await("seek while paused") { it.state == 4 && it.positionMs in 44_900..45_100 }
            playback.rate(1.25f)
            playback.play()
            await("resumed") { it.state == 3 && it.positionMs > 46_000 }
        } finally { playback.close() }
        assertNull(failure.get())
    }
}
