package com.alananasss.kittytune

import com.alananasss.kittytune.data.MprisService
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.errors.PropertyReadOnly
import org.freedesktop.dbus.errors.UnknownInterface
import org.freedesktop.dbus.errors.UnknownProperty
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.DBusSigHandler
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.matchrules.DBusMatchRuleBuilder
import org.freedesktop.dbus.types.Variant
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Talks to [MprisService] over a real (private) session bus, with playerctl as the
 * strict third-party oracle. Run under dbus-run-session:
 *
 *   dbus-run-session -- ./gradlew test --tests "*MprisServiceTest*" --no-daemon
 */
class MprisServiceTest {

    @DBusInterfaceName("org.mpris.MediaPlayer2.Player")
    interface PlayerCalls : DBusInterface {
        fun SetPosition(TrackId: DBusPath, Position: Long)
        fun Stop()
        fun Play()
        fun Next()
        fun Previous()
    }

    data class Changed(val iface: String, val keys: Set<String>)

    companion object {
        private const val BUS = "org.mpris.MediaPlayer2.kittytune"
        private const val PATH = "/org/mpris/MediaPlayer2"
        private const val PLAYER = "org.mpris.MediaPlayer2.Player"
        private const val ROOT = "org.mpris.MediaPlayer2"

        private lateinit var service: MprisService
        private lateinit var client: DBusConnection
        private lateinit var props: Properties
        private lateinit var player: PlayerCalls

        private val events = LinkedBlockingQueue<String>()
        private val changed = LinkedBlockingQueue<Changed>()
        private val seeked = LinkedBlockingQueue<Long>()

        private var busAvailable = false
        private var playerctlAvailable = false

        @JvmStatic
        @BeforeClass
        fun start() {
            busAvailable = runCatching {
                DBusConnectionBuilder.forSessionBus().build().disconnect()
                true
            }.getOrDefault(false)
            assumeTrue("no D-Bus session bus (run under dbus-run-session)", busAvailable)

            service = MprisService(
                onRaise = { events.offer("raise") },
                onPlay = { events.offer("play") },
                onPause = { events.offer("pause") },
                onPlayPause = { events.offer("playpause") },
                onNext = { events.offer("next") },
                onPrevious = { events.offer("prev") },
                onStop = { events.offer("stop") },
                onSeek = { events.offer("seek:$it") },
                onVolume = { events.offer("volume:$it") },
                onShuffle = { events.offer("shuffle:$it") },
                onLoopStatus = { events.offer("loop:${it.mprisName}") },
            )
            client = DBusConnectionBuilder.forSessionBus().build()
            props = client.getRemoteObject(BUS, PATH, Properties::class.java, false)
            player = client.getRemoteObject(BUS, PATH, PlayerCalls::class.java, false)

            client.addSigHandler(
                DBusMatchRuleBuilder.create()
                    .withType("signal").withPath(PATH)
                    .withInterface("org.freedesktop.DBus.Properties")
                    .withMember("PropertiesChanged").build(),
                DBusSigHandler<Properties.PropertiesChanged> { s ->
                    changed.offer(Changed(s.interfaceName, s.propertiesChanged.keys.toSet()))
                },
            )
            client.addSigHandler(
                DBusMatchRuleBuilder.create()
                    .withType("signal").withPath(PATH)
                    .withInterface(PLAYER).withMember("Seeked").build(),
                DBusSigHandler<MprisService.MprisPlayerInterface.Seeked> { s ->
                    seeked.offer(s.Position)
                },
            )

            playerctlAvailable = runCatching {
                ProcessBuilder("playerctl", "--version").start().waitFor() == 0
            }.getOrDefault(false)
        }

        @JvmStatic
        @AfterClass
        fun stop() {
            if (!busAvailable) return
            runCatching { client.disconnect() }
            runCatching { service.close() }
        }

        private fun ctl(vararg args: String): String {
            val proc = ProcessBuilder(listOf("playerctl", "-p", "kittytune") + args)
                .redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            check(proc.waitFor() == 0) { "playerctl ${args.joinToString(" ")} failed: $out" }
            return out
        }

        private fun reset() {
            service.updateMedia(null, false, 0L)
            service.updateVolume(1.0)
            service.updateShuffle(false)
            service.updateLoopStatus(MprisService.LoopStatus.None)
            events.clear()
            changed.clear()
            seeked.clear()
        }

        private fun nextEvent(): String {
            val e = events.poll(5, TimeUnit.SECONDS)
            if (e == null) fail("timed out waiting for player callback")
            return e
        }

        private fun nextSeeked(): Long {
            val p = seeked.poll(5, TimeUnit.SECONDS)
            if (p == null) fail("timed out waiting for Seeked")
            return p
        }

        private fun user(name: String = "someone", avatar: String? = null) = User(7L, name, avatar)

        private fun metaValue(meta: Map<String, Any?>, key: String): Any? {
            // The client binding unwraps the inner variants of a{sv}: accept both shapes.
            val raw = meta[key]
            return (raw as? Variant<*>)?.value ?: raw
        }

        private fun track(
            id: Long = 1L,
            artwork: String? = "https://i1.sndcdn.com/artworks-abc-large.jpg",
            durationMs: Long? = 200_000L,
        ) = Track(
            id = id,
            title = "Song",
            artworkUrl = artwork,
            durationMs = durationMs,
            user = user(),
            permalinkUrl = "https://soundcloud.com/someone/song",
            publisherMetadata = Track.PublisherMetadata(artist = "Alice", albumTitle = "Album"),
        )
    }

    @Test
    fun `root exposes the full 2_2 property set`() {
        reset()
        @Suppress("UNCHECKED_CAST")
        val all = props.GetAll(ROOT) as Map<String, Variant<*>>
        assertEquals(
            setOf(
                "CanQuit", "CanRaise", "HasTrackList", "Identity", "DesktopEntry",
                "SupportedUriSchemes", "SupportedMimeTypes", "CanSetFullscreen", "Fullscreen",
            ),
            all.keys,
        )
        assertEquals("KittyTune", all.getValue("Identity").value)
        assertEquals("kitty-tune", all.getValue("DesktopEntry").value)
        assertEquals(false, all.getValue("CanQuit").value)
        assertEquals(false, all.getValue("CanSetFullscreen").value)
        assertEquals(false, all.getValue("Fullscreen").value)
    }

    @Test
    fun `player exposes every property with exact signatures`() {
        reset()
        @Suppress("UNCHECKED_CAST")
        val all = props.GetAll(PLAYER) as Map<String, Variant<*>>
        val sigs = all.mapValues { it.value.getSig() }
        assertEquals(
            mapOf(
                "PlaybackStatus" to "s", "LoopStatus" to "s", "Rate" to "d",
                "Shuffle" to "b", "Metadata" to "a{sv}", "Volume" to "d",
                "Position" to "x", "MinimumRate" to "d", "MaximumRate" to "d",
                "CanGoNext" to "b", "CanGoPrevious" to "b", "CanPlay" to "b",
                "CanPause" to "b", "CanSeek" to "b", "CanControl" to "b",
            ),
            sigs,
        )
    }

    @Test
    fun `no track means Stopped with the NoTrack id`() {
        reset()
        @Suppress("UNCHECKED_CAST")
        val all = props.GetAll(PLAYER) as Map<String, Variant<*>>
        assertEquals("Stopped", all.getValue("PlaybackStatus").value)
        @Suppress("UNCHECKED_CAST")
        val meta = all.getValue("Metadata").value as Map<String, Variant<*>>
        assertEquals(
            "/org/mpris/MediaPlayer2/TrackList/NoTrack",
            (meta.getValue("mpris:trackid").value as DBusPath).path,
        )
    }

    @Test
    fun `track metadata round-trips with native types`() {
        reset()
        service.updateMedia(track(), playing = true, positionMs = 1_000L)
        @Suppress("UNCHECKED_CAST")
        val meta = (props.Get(PLAYER, "Metadata") as Map<String, Any?>)
        assertEquals("/org/mpris/MediaPlayer2/Track/1", (metaValue(meta, "mpris:trackid") as DBusPath).path)
        assertEquals("Song", metaValue(meta, "xesam:title"))
        val artists = metaValue(meta, "xesam:artist")
        val artistList = when (artists) {
            is Array<*> -> artists.toList()
            is List<*> -> artists
            else -> fail("xesam:artist has unexpected type ${artists?.javaClass}")
        }
        assertEquals(listOf("Alice"), artistList)
        assertEquals("Album", metaValue(meta, "xesam:album"))
        assertEquals(200_000_000L, metaValue(meta, "mpris:length"))
        assertEquals(
            "https://i1.sndcdn.com/artworks-abc-t500x500.jpg",
            metaValue(meta, "mpris:artUrl"),
        )
    }

    @Test
    fun `negative ids stay valid object paths`() {
        reset()
        service.updateMedia(track(id = -12345L), playing = false, positionMs = 0L)
        @Suppress("UNCHECKED_CAST")
        val meta = (props.Get(PLAYER, "Metadata") as Map<String, Any?>)
        val path = (metaValue(meta, "mpris:trackid") as DBusPath).path
        assertFalse(path.contains("-"))
        assertEquals(MprisService.trackObjectPath(-12345L), path)
    }

    @Test
    fun `coverless tracks omit artUrl instead of faking a cover`() {
        reset()
        val coverless = track(artwork = null).copy(user = user(avatar = null))
        service.updateMedia(coverless, playing = false, positionMs = 0L)
        @Suppress("UNCHECKED_CAST")
        val meta = (props.Get(PLAYER, "Metadata") as Map<String, Any?>)
        assertFalse(meta.containsKey("mpris:artUrl"))
    }

    @Test
    fun `stop reports Stopped at zero and play resumes from the start`() {
        assumeTrue(playerctlAvailable)
        reset()
        service.updateMedia(track(), playing = true, positionMs = 30_000L)
        events.clear()
        ctl("stop")
        assertEquals("stop", nextEvent())
        // The engine answers stop with pause + rewind, like the app wires onStop.
        service.updateMedia(track(), playing = false, positionMs = 0L)
        assertEquals("Stopped", ctl("status"))
        assertEquals(0.0, ctl("position").toDouble(), 0.001)
        ctl("play")
        assertEquals("play", nextEvent())
        service.updateMedia(track(), playing = true, positionMs = 0L)
        assertEquals("Playing", ctl("status"))
    }

    @Test
    fun `button methods reach the player`() {
        assumeTrue(playerctlAvailable)
        reset()
        service.updateMedia(track(), playing = true, positionMs = 10_000L)
        events.clear()
        ctl("pause"); assertEquals("pause", nextEvent())
        ctl("play"); assertEquals("play", nextEvent())
        ctl("play-pause"); assertEquals("playpause", nextEvent())
        ctl("next"); assertEquals("next", nextEvent())
        ctl("previous"); assertEquals("prev", nextEvent())
    }

    @Test
    fun `seek moves relatively and emits Seeked`() {
        assumeTrue(playerctlAvailable)
        reset()
        service.updateMedia(track(), playing = false, positionMs = 60_000L)
        events.clear(); seeked.clear()
        ctl("position", "5+")
        assertEquals("seek:65000", nextEvent())
        assertEquals(65_000_000L, nextSeeked())
        assertEquals(65.0, ctl("position").toDouble(), 1.5)
    }

    @Test
    fun `setPosition validates track and range`() {
        reset()
        service.updateMedia(track(), playing = false, positionMs = 10_000L)
        events.clear(); seeked.clear()
        // Stale track: ignored, no callback, no signal.
        player.SetPosition(DBusPath("/org/mpris/MediaPlayer2/Track/999"), 50_000_000L)
        // Beyond the known length: ignored.
        player.SetPosition(DBusPath(MprisService.trackObjectPath(1L)), 999_000_000L)
        // Negative: ignored.
        player.SetPosition(DBusPath(MprisService.trackObjectPath(1L)), -5_000L)
        assertTrue(events.poll(500, TimeUnit.MILLISECONDS) == null)
        assertTrue(seeked.poll(500, TimeUnit.MILLISECONDS) == null)
        // Current track, in range: applied.
        player.SetPosition(DBusPath(MprisService.trackObjectPath(1L)), 50_000_000L)
        assertEquals("seek:50000", nextEvent())
        assertEquals(50_000_000L, nextSeeked())
    }

    @Test
    fun `volume shuffle and loop round-trip`() {
        reset()
        props.Set(PLAYER, "Volume", Variant(0.42, "d"))
        assertEquals("volume:0.42", nextEvent())
        assertEquals(0.42, props.Get(PLAYER, "Volume") as Double, 1e-9)

        props.Set(PLAYER, "Shuffle", Variant(true, "b"))
        assertEquals("shuffle:true", nextEvent())
        assertEquals(true, props.Get(PLAYER, "Shuffle") as Boolean)

        props.Set(PLAYER, "LoopStatus", Variant("Track", "s"))
        assertEquals("loop:Track", nextEvent())
        assertEquals("Track", props.Get(PLAYER, "LoopStatus") as String)
    }

    @Test
    fun `bogus loop value is ignored and rate zero pauses`() {
        reset()
        props.Set(PLAYER, "LoopStatus", Variant("Bogus", "s"))
        assertTrue(events.poll(500, TimeUnit.MILLISECONDS) == null)
        assertEquals("None", props.Get(PLAYER, "LoopStatus") as String)

        service.updateMedia(track(), playing = true, positionMs = 5_000L)
        events.clear()
        props.Set(PLAYER, "Rate", Variant(0.0, "d"))
        assertEquals("pause", nextEvent())
        assertEquals(1.0, props.Get(PLAYER, "Rate") as Double, 1e-9)
    }

    @Test
    fun `position and canControl are never announced`() {
        reset()
        changed.clear()
        service.updateMedia(track(), playing = true, positionMs = 1_000L)
        service.updateMedia(track(), playing = false, positionMs = 2_000L)
        service.updateVolume(0.7)
        service.updateShuffle(true)
        service.updateLoopStatus(MprisService.LoopStatus.Playlist)
        // Let delivery catch up, then collect everything the scenario announced.
        Thread.sleep(800)
        val announced = generateSequence { changed.poll() }.toList().flatMap { it.keys }
        assertTrue(announced.contains("Metadata"))
        assertTrue(announced.contains("PlaybackStatus"))
        assertFalse(announced.contains("Position"))
        assertFalse(announced.contains("CanControl"))

        // Same state again: the bus must stay silent.
        service.updateMedia(track(), playing = false, positionMs = 2_000L)
        service.updateVolume(0.7)
        service.updateShuffle(true)
        service.updateLoopStatus(MprisService.LoopStatus.Playlist)
        assertTrue(changed.poll(1500, TimeUnit.MILLISECONDS) == null)
    }

    @Test
    fun `next while stopped stays stopped until playback resumes`() {
        reset()
        service.updateMedia(track(), playing = true, positionMs = 10_000L)
        events.clear()
        player.Stop()
        assertEquals("stop", nextEvent())
        service.updateMedia(track(), playing = false, positionMs = 0L)
        assertEquals("Stopped", props.Get(PLAYER, "PlaybackStatus") as String)

        // Paused-prepare of the next track: still Stopped. The new Metadata is
        // announced, but PlaybackStatus must not be re-announced.
        changed.clear()
        val next = track(id = 2L)
        service.updateMedia(next, playing = false, positionMs = 0L)
        assertEquals("Stopped", props.Get(PLAYER, "PlaybackStatus") as String)
        Thread.sleep(500)
        val keys = generateSequence { changed.poll() }.toList().flatMap { it.keys }
        assertTrue(keys.contains("Metadata"))
        assertFalse(keys.contains("PlaybackStatus"))

        // Resuming clears it.
        service.updateMedia(next, playing = true, positionMs = 0L)
        assertEquals("Playing", props.Get(PLAYER, "PlaybackStatus") as String)
    }

    @Test
    fun `unknown names raise the precise errors`() {
        reset()
        try {
            props.GetAll("org.mpris.MediaPlayer2.Nope")
            fail("expected UnknownInterface")
        } catch (e: UnknownInterface) { /* expected */ }
        try {
            props.Get<Any>(PLAYER, "Nope")
            fail("expected UnknownProperty")
        } catch (e: UnknownProperty) { /* expected */ }
        try {
            props.Set(PLAYER, "PlaybackStatus", Variant("Playing", "s"))
            fail("expected PropertyReadOnly")
        } catch (e: PropertyReadOnly) { /* expected */ }
        try {
            props.Set(ROOT, "Fullscreen", Variant(true, "b"))
            fail("expected PropertyReadOnly")
        } catch (e: PropertyReadOnly) { /* expected */ }
    }
}
