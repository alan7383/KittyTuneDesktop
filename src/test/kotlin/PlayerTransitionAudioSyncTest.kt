package com.alananasss.kittytune

import com.alananasss.kittytune.audio.AudioEngine
import com.alananasss.kittytune.media.MediaItem
import com.alananasss.kittytune.media.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests verifying that track transitions, stop, and queue actions never leak previous audio
 * or leave the previous track's engine playing while new track metadata is displayed (issue #66).
 */
class PlayerTransitionAudioSyncTest {

    @Test
    fun testAudioEngineStopResetsCurrentUrlAndPosition() {
        val engine = AudioEngine()
        engine.setMediaItem("https://example.com/audio1.mp3", emptyMap(), 15000L)
        assertEquals("https://example.com/audio1.mp3", engine.currentUrl)
        assertEquals(15000L, engine.positionMs)

        engine.stop()

        assertNull("AudioEngine.stop() must reset currentUrl to null", engine.currentUrl)
        assertEquals(0L, engine.positionMs)
        assertEquals(0L, engine.durationMs)
        assertEquals(AudioEngine.State.IDLE, engine.state)
        assertFalse(engine.isPlaying)

        // Calling prepare() after stop() must NOT resurrect or start playback on the old URL
        engine.prepare()
        assertNull(engine.currentUrl)
        assertEquals(AudioEngine.State.IDLE, engine.state)

        engine.release()
    }

    @Test
    fun testPlayerCompatSetMediaItemDirectUrlSetsActiveEngineImmediately() {
        val player = Player()
        val item1 = MediaItem.Builder()
            .setMediaId("1")
            .setUri("https://example.com/track1.mp3")
            .build()

        player.setMediaItem(item1, 5000L)

        // activeEngine must synchronously have the new URL set
        assertEquals("https://example.com/track1.mp3", player.activeEngine.currentUrl)
        assertEquals(5000L, player.activeEngine.positionMs)

        // Stopping the player must reset activeEngine's URL
        player.stop()
        assertNull(player.activeEngine.currentUrl)
        assertFalse(player.isCrossfadingOut)

        player.release()
    }

    @Test
    fun testPlayerCompatSetMediaItemNonDirectUrlStopsActiveEngineSynchronously() {
        val player = Player()
        val directItem = MediaItem.Builder()
            .setMediaId("1")
            .setUri("https://example.com/track1.mp3")
            .build()
        player.setMediaItem(directItem, 0L)
        assertEquals("https://example.com/track1.mp3", player.activeEngine.currentUrl)

        // Now set a placeholder / unresolved item (soundtune://)
        val unresolvedItem = MediaItem.Builder()
            .setMediaId("2")
            .setUri("soundtune://track/2")
            .build()

        player.setMediaItem(unresolvedItem, 0L)

        // activeEngine must be stopped immediately so track 1 does not keep playing while resolving
        assertNull("activeEngine.currentUrl must be cleared while resolving non-direct item", player.activeEngine.currentUrl)
        assertEquals(AudioEngine.State.IDLE, player.activeEngine.state)

        player.release()
    }

    @Test
    fun testCrossfadeToMediaItemReleasesPreviousFadingEngine() {
        val player = Player()
        val item1 = MediaItem.Builder().setMediaId("1").setUri("https://example.com/1.mp3").build()
        val item2 = MediaItem.Builder().setMediaId("2").setUri("https://example.com/2.mp3").build()
        val item3 = MediaItem.Builder().setMediaId("3").setUri("https://example.com/3.mp3").build()

        player.setMediaItem(item1)
        player.crossfadeToMediaItem(item2, 0L, 3000L)
        assertTrue(player.isCrossfadingOut)

        // Initiating another crossfade or track change must stop previous fading engine and not leak
        player.crossfadeToMediaItem(item3, 0L, 3000L)
        assertTrue(player.isCrossfadingOut)

        player.stop()
        assertFalse(player.isCrossfadingOut)
        assertNull(player.activeEngine.currentUrl)

        player.release()
    }
}
