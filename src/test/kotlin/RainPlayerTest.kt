package com.alananasss.kittytune.data

import com.alananasss.kittytune.data.ambient.AmbientPlayback
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RainPlayerTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun settingsAndDeviceChangesKeepOnePlaybackOwner() {
        val events = mutableListOf<String>()
        val player = RainPlayer { type, volume ->
            events += "create:$type:$volume"
            object : AmbientPlayback {
                override fun start() { events += "start" }
                override fun setVolume(volume: Float) { events += "volume:$volume" }
                override fun close() { events += "close" }
            }
        }
        player.setVolume(0.3f)
        player.setAmbientType("OCEAN")
        player.reloadDevice()
        assertTrue(events.isEmpty())
        player.setEnabled(true)
        player.setEnabled(true)
        player.setVolume(4f)
        player.setAmbientType("cafe")
        player.reloadDevice()
        player.setEnabled(false)
        player.release()
        assertEquals(listOf("create:ocean:0.3", "start", "volume:1.0", "close", "create:cafe:1.0", "start", "close", "create:cafe:1.0", "start", "close"), events)
    }

    @Test fun unknownSoundStillUsesRain() {
        assertEquals("rain.mp3", RainPlayer.resourceName("unknown"))
        assertEquals("fireplace.mp3", RainPlayer.resourceName("fireplace"))
    }

    @Test fun cleanupOnlyRemovesKnownGeneratedWavs() {
        for (name in listOf("rain", "ocean", "fireplace", "cafe")) File(tmp.root, "$name.wav").writeBytes(byteArrayOf(1))
        val keep = tmp.newFile("recording.wav")
        val folder = tmp.newFolder("audio")
        val song = File(folder, "rain.wav").apply { writeText("download") }
        RainPlayer.removeLegacyWavs(tmp.root)
        assertTrue(keep.exists())
        assertTrue(song.exists())
        assertEquals(setOf("recording.wav", "audio"), tmp.root.list()!!.toSet())
    }
}
