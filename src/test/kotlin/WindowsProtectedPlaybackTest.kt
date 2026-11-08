package com.alananasss.kittytune.audio

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList

class WindowsProtectedPlaybackTest {
    private fun await(message: String, predicate: () -> Boolean) {
        val deadline = System.nanoTime() + 3_000_000_000L
        while (System.nanoTime() < deadline) {
            if (predicate()) return
            Thread.sleep(15)
        }
        assertTrue(message, predicate())
    }
    private fun command(directory: File) = runCatching { JSONObject(File(directory, "command.json").readText()) }.getOrNull()

    @Test fun stoppingAnOldEngineCannotStopItsReplacementOrRetainItsToken() {
        val root = Files.createTempDirectory("playready-sessions").toFile()
        val failures = CopyOnWriteArrayList<Throwable>()
        val first = WindowsProtectedPlayback(root, "https://example.test/a.m3u8", "first-token", 0, {}, failures::add, {}, prepare = { _, _ -> JSONObject() })
        val second = WindowsProtectedPlayback(root, "https://example.test/b.m3u8", "second-token", 5000, {}, failures::add, {}, prepare = { _, _ -> JSONObject() })
        try {
            second.play()
            await("Both engines publish independently") { root.listFiles()?.count { command(it) != null } == 2 }
            val oldDirectory = root.listFiles()!!.single { command(it)?.optString("licenseAuthToken") == "first-token" }
            val newDirectory = root.listFiles()!!.single { it != oldDirectory }
            first.close()
            second.volume(0.25)
            second.seek(45_000)
            await("Replacement keeps playing after old engine stops") {
                command(newDirectory)?.let { it.getBoolean("playing") && it.getLong("positionMs") == 45_000L && it.getDouble("volume") == 0.25 } == true
            }
            val stopped = command(oldDirectory)!!
            assertTrue(stopped.getBoolean("closed"))
            assertEquals("", stopped.getString("licenseAuthToken"))
            assertEquals("", stopped.getString("url"))
            assertTrue(failures.isEmpty())
        } finally { first.close(); second.close(); root.deleteRecursively() }
    }

    @Test fun staleNativeProcessFailsOnceAndRevokesItsSession() {
        val root = Files.createTempDirectory("playready-stale").toFile()
        val failures = CopyOnWriteArrayList<Throwable>()
        val statuses = CopyOnWriteArrayList<WindowsProtectedPlayback.Status>()
        val playback = WindowsProtectedPlayback(root, "https://example.test/a.m3u8", "private-token", 0, statuses::add, failures::add, {}, prepare = { _, _ -> JSONObject() })
        try {
            playback.play()
            await("Command published") { root.listFiles()?.any { command(it) != null } == true }
            val session = root.listFiles()!!.single()
            File(session, "status.json").writeText(JSONObject().put("updatedAt", System.currentTimeMillis() - 20_000).toString())
            await("Stale native process detected") { failures.isNotEmpty() }
            await("Failed session revoked") { command(session)?.optBoolean("closed") == true }
            assertEquals(1, failures.size)
            assertFalse(failures.single().message.orEmpty().contains("private-token"))
            assertTrue(statuses.isEmpty())
            assertEquals("", command(session)!!.getString("licenseAuthToken"))
        } finally { playback.close(); root.deleteRecursively() }
    }
}
