package com.alananasss.kittytune.data

import com.alananasss.kittytune.core.AppIconInstaller
import com.alananasss.kittytune.domain.Track
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.errors.PropertyReadOnly
import org.freedesktop.dbus.errors.UnknownProperty
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.Variant
import java.awt.EventQueue
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The player as Linux desktops see it: GNOME's and KDE's media widgets, playerctl, media keys, and
 * end4's illogical-impulse bar all read the same MPRIS2 object.
 *
 * There used to be two of these, on `org.mpris.MediaPlayer2.kittytune` and `…kittytune.kde`, so every
 * desktop listed KittyTune twice, each copy with its own D-Bus connection and position timer. This is
 * the spec-complete one, on the canonical name, so the end4 integration that looks the player up by
 * that name keeps working.
 */
class MprisService(
    private val onRaise: () -> Unit,
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onPlayPause: () -> Unit,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
    private val onSeek: (Long) -> Unit,
    private val onVolume: (Double) -> Unit,
    private val onShuffle: (Boolean) -> Unit,
    private val onLoopStatus: (LoopStatus) -> Unit,
) : Closeable {

    enum class LoopStatus(val mprisName: String) {
        None("None"),
        Track("Track"),
        Playlist("Playlist");

        companion object {
            fun fromMprisName(name: String): LoopStatus = entries.firstOrNull { it.mprisName == name } ?: None
        }
    }

    // Written from the UI thread, read from D-Bus threads.
    @Volatile private var currentTrack: Track? = null
    @Volatile private var isPlaying: Boolean = false
    @Volatile private var positionAtUpdateMs: Long = 0L
    @Volatile private var lastUpdateTimeMs: Long = System.currentTimeMillis()
    @Volatile private var currentVolume: Double = 1.0
    @Volatile private var currentShuffle: Boolean = false
    @Volatile private var currentLoopStatus: LoopStatus = LoopStatus.None

    private var connection: DBusConnection? = null
    private var positionScheduler: java.util.concurrent.ScheduledExecutorService? = null

    /** The .desktop entry's id, which is how shells find the icon to show next to the widget. */
    private val desktopEntry: String by lazy {
        AppIconInstaller.findInstalledDesktopFile()?.nameWithoutExtension ?: DEFAULT_DESKTOP_ENTRY
    }

    init {
        if (isLinux()) {
            runCatching {
                val conn = DBusConnectionBuilder.forSessionBus().build()
                conn.requestBusName(BUS_NAME)
                conn.exportObject(OBJECT_PATH, Mpris2Object())
                connection = conn
                startPositionBroadcaster()
            }.onFailure { e ->
                println("MPRIS: could not register $BUS_NAME: ${e.message}")
                runCatching { connection?.disconnect() }
                connection = null
            }
        }
    }

    /** Called whenever the track, the playing state or the position jumps. */
    fun updateMedia(track: Track?, playing: Boolean, positionMs: Long) {
        val trackChanged = currentTrack?.id != track?.id
        currentTrack = track
        isPlaying = playing
        positionAtUpdateMs = positionMs
        lastUpdateTimeMs = System.currentTimeMillis()
        send(buildPropertiesChanged(includeTrackProperties = trackChanged))
    }

    fun updateVolume(volume: Double) {
        val clamped = volume.coerceIn(0.0, 1.0)
        if (clamped == currentVolume) return
        currentVolume = clamped
        broadcastPlayerProperty("Volume", Variant(clamped, "d"))
    }

    fun updateShuffle(shuffle: Boolean) {
        if (shuffle == currentShuffle) return
        currentShuffle = shuffle
        broadcastPlayerProperty("Shuffle", Variant(shuffle, "b"))
    }

    fun updateLoopStatus(status: LoopStatus) {
        if (status == currentLoopStatus) return
        currentLoopStatus = status
        broadcastPlayerProperty("LoopStatus", Variant(status.mprisName, "s"))
    }

    override fun close() {
        positionScheduler?.shutdownNow()
        positionScheduler = null
        runCatching { connection?.disconnect() }
        connection = null
    }

    private fun currentPositionUs(): Long {
        val elapsedMs = if (isPlaying) System.currentTimeMillis() - lastUpdateTimeMs else 0L
        return (positionAtUpdateMs + elapsedMs) * 1000L
    }

    private fun dispatchToMain(action: () -> Unit) {
        if (EventQueue.isDispatchThread()) action() else EventQueue.invokeLater(action)
    }

    /**
     * The spec only requires Position on request, but several bars (end4's among them) redraw their
     * progress from PropertiesChanged, so it is pushed once a second while something is playing.
     */
    private fun startPositionBroadcaster() {
        positionScheduler = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "MPRIS-Position").apply { isDaemon = true }
        }.apply {
            scheduleAtFixedRate({
                if (isPlaying) broadcastPlayerProperty("Position", Variant(currentPositionUs(), "x"))
            }, 1, 1, TimeUnit.SECONDS)
        }
    }

    private fun seekTo(targetMs: Long) {
        val clamped = targetMs.coerceAtLeast(0L)
        onSeek(clamped)
        positionAtUpdateMs = clamped
        lastUpdateTimeMs = System.currentTimeMillis()
        send(MprisPlayerInterface.Seeked(OBJECT_PATH, clamped * 1000L))
    }

    private fun broadcastPlayerProperty(name: String, value: Variant<*>) {
        send(Properties.PropertiesChanged(OBJECT_PATH, PLAYER_INTERFACE, hashMapOf(name to value), ArrayList()))
    }

    private fun send(message: DBusSignal) {
        val conn = connection ?: return
        runCatching { conn.sendMessage(message) }
            .onFailure { e -> println("MPRIS: signal ${message.name} failed: ${e.message}") }
    }

    private fun buildMetadata(): HashMap<String, Variant<*>> {
        val meta = HashMap<String, Variant<*>>()
        val track = currentTrack
        if (track == null) {
            // Plasma rejects metadata without a trackid, even when nothing is loaded.
            meta["mpris:trackid"] = Variant("$OBJECT_PATH/Track/None", "o")
            return meta
        }
        meta["mpris:trackid"] = Variant("$OBJECT_PATH/Track/${track.id}", "o")
        meta["xesam:title"] = Variant(track.title ?: "", "s")
        val artist = track.displayArtist.ifBlank { track.user?.username.orEmpty() }
        if (artist.isNotBlank()) meta["xesam:artist"] = Variant(arrayOf(artist), "as")
        val album = track.publisherMetadata?.albumTitle ?: track.publisherMetadata?.releaseTitle
        if (!album.isNullOrBlank()) meta["xesam:album"] = Variant(album, "s")
        meta["mpris:artUrl"] = Variant(track.fullResArtwork, "s")
        track.durationMs?.takeIf { it > 0 }?.let { meta["mpris:length"] = Variant(it * 1000L, "x") }
        return meta
    }

    private fun playbackStatus(): String = when {
        currentTrack == null -> "Stopped"
        isPlaying -> "Playing"
        else -> "Paused"
    }

    private fun rootProperties(): Map<String, Variant<*>> = mapOf(
        "CanQuit" to Variant(false, "b"),
        "CanRaise" to Variant(true, "b"),
        "HasTrackList" to Variant(false, "b"),
        "Identity" to Variant(IDENTITY, "s"),
        "DesktopEntry" to Variant(desktopEntry, "s"),
        "SupportedUriSchemes" to Variant(emptyArray<String>(), "as"),
        "SupportedMimeTypes" to Variant(emptyArray<String>(), "as"),
    )

    private fun playerProperties(): Map<String, Variant<*>> = mapOf(
        "PlaybackStatus" to Variant(playbackStatus(), "s"),
        "LoopStatus" to Variant(currentLoopStatus.mprisName, "s"),
        "Rate" to Variant(1.0, "d"),
        "Shuffle" to Variant(currentShuffle, "b"),
        "Metadata" to Variant(buildMetadata(), "a{sv}"),
        "Volume" to Variant(currentVolume, "d"),
        "Position" to Variant(currentPositionUs(), "x"),
        "MinimumRate" to Variant(1.0, "d"),
        "MaximumRate" to Variant(1.0, "d"),
        "CanGoNext" to Variant(true, "b"),
        "CanGoPrevious" to Variant(true, "b"),
        "CanPlay" to Variant(true, "b"),
        "CanPause" to Variant(true, "b"),
        "CanSeek" to Variant(true, "b"),
        "CanControl" to Variant(true, "b"),
    )

    private fun buildPropertiesChanged(includeTrackProperties: Boolean): Properties.PropertiesChanged {
        val changes = HashMap<String, Variant<*>>()
        if (includeTrackProperties) changes.putAll(playerProperties())
        changes["PlaybackStatus"] = Variant(playbackStatus(), "s")
        changes["Position"] = Variant(currentPositionUs(), "x")
        return Properties.PropertiesChanged(OBJECT_PATH, PLAYER_INTERFACE, changes, ArrayList())
    }

    @DBusInterfaceName("org.mpris.MediaPlayer2")
    interface MprisRootInterface : DBusInterface {
        fun Raise()
        fun Quit()
    }

    @DBusInterfaceName(PLAYER_INTERFACE)
    interface MprisPlayerInterface : DBusInterface {
        fun Play()
        fun Pause()
        fun PlayPause()
        fun Stop()
        fun Next()
        fun Previous()
        fun Seek(Offset: Long)
        fun SetPosition(TrackId: DBusPath, Position: Long)
        fun OpenUri(Uri: String)

        class Seeked(path: String, val Position: Long) :
            DBusSignal(null, path, PLAYER_INTERFACE, "Seeked", "x", Position)
    }

    private inner class Mpris2Object : MprisRootInterface, MprisPlayerInterface, Properties {
        override fun getObjectPath(): String = OBJECT_PATH
        override fun isRemote(): Boolean = false

        override fun Raise() = dispatchToMain(onRaise)
        override fun Quit() = Unit

        override fun Play() = dispatchToMain(onPlay)
        override fun Pause() = dispatchToMain(onPause)
        override fun PlayPause() = dispatchToMain(onPlayPause)
        override fun Stop() = dispatchToMain(onPause)
        override fun Next() = dispatchToMain(onNext)
        override fun Previous() = dispatchToMain(onPrevious)
        override fun OpenUri(Uri: String) = Unit

        override fun Seek(Offset: Long) = dispatchToMain { seekTo((currentPositionUs() + Offset) / 1000L) }

        override fun SetPosition(TrackId: DBusPath, Position: Long) {
            // The spec says to ignore a SetPosition aimed at a track that is no longer current.
            val current = currentTrack ?: return
            if (TrackId.path != "$OBJECT_PATH/Track/${current.id}") return
            dispatchToMain { seekTo(Position / 1000L) }
        }

        @Suppress("UNCHECKED_CAST")
        override fun <A> Get(iface: String, property: String): A {
            val all = GetAll(iface)
            val value = all[property] ?: throw UnknownProperty("Unknown property: $iface.$property")
            return value.value as A
        }

        override fun <A> Set(iface: String, property: String, value: A) {
            val raw = (value as? Variant<*>)?.value ?: value
            if (iface == PLAYER_INTERFACE) {
                when (property) {
                    // Each change is applied, then announced: the app's own update* calls see an
                    // unchanged value afterwards and stay quiet, so this is the only signal sent.
                    "Volume" -> (raw as? Double)?.let { v ->
                        currentVolume = v.coerceIn(0.0, 1.0)
                        broadcastPlayerProperty("Volume", Variant(currentVolume, "d"))
                        dispatchToMain { onVolume(currentVolume) }
                    }
                    "Shuffle" -> (raw as? Boolean)?.let { s ->
                        currentShuffle = s
                        broadcastPlayerProperty("Shuffle", Variant(s, "b"))
                        dispatchToMain { onShuffle(s) }
                    }
                    "LoopStatus" -> (raw as? String)?.let { name ->
                        val status = LoopStatus.fromMprisName(name)
                        currentLoopStatus = status
                        broadcastPlayerProperty("LoopStatus", Variant(status.mprisName, "s"))
                        dispatchToMain { onLoopStatus(status) }
                    }
                    "Rate" -> Unit
                    else -> throw PropertyReadOnly("Property not writable: $iface.$property")
                }
                return
            }
            throw PropertyReadOnly("Property not writable: $iface.$property")
        }

        override fun GetAll(iface: String): Map<String, Variant<*>> = when (iface) {
            "org.mpris.MediaPlayer2" -> rootProperties()
            PLAYER_INTERFACE -> playerProperties()
            else -> emptyMap()
        }
    }

    private companion object {
        const val BUS_NAME = "org.mpris.MediaPlayer2.kittytune"
        const val OBJECT_PATH = "/org/mpris/MediaPlayer2"
        const val PLAYER_INTERFACE = "org.mpris.MediaPlayer2.Player"
        const val IDENTITY = "KittyTune"
        const val DEFAULT_DESKTOP_ENTRY = "kitty-tune"

        fun isLinux(): Boolean = System.getProperty("os.name").lowercase().let { "linux" in it || "unix" in it }
    }
}
