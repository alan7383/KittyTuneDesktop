package com.alananasss.kittytune.data

import com.alananasss.kittytune.core.AppIconInstaller
import com.alananasss.kittytune.domain.Track
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.errors.PropertyReadOnly
import org.freedesktop.dbus.errors.UnknownInterface
import org.freedesktop.dbus.errors.UnknownProperty
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.Variant
import java.awt.EventQueue
import java.io.Closeable

/**
 * MPRIS 2.2 player on `org.mpris.MediaPlayer2.kittytune`.
 *
 * Strictly event-driven: [Properties.PropertiesChanged] is emitted only for properties whose
 * value actually changed, and never for `Position` or `CanControl`, for which the spec
 * mandates no signal. Clients (playerctl, Quickshell, GNOME/KDE widgets) read the progressing
 * position with `Get` and extrapolate it with `Rate` between seeks.
 */
class MprisService(
    private val onRaise: () -> Unit,
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onPlayPause: () -> Unit,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
    private val onStop: () -> Unit,
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
            fun fromMprisName(name: String): LoopStatus? = entries.firstOrNull { it.mprisName == name }
        }
    }

    // Written from the UI thread, read from D-Bus threads.
    @Volatile private var currentTrack: Track? = null
    @Volatile private var isPlaying: Boolean = false
    @Volatile private var stopped: Boolean = false
    @Volatile private var positionAtUpdateMs: Long = 0L
    @Volatile private var lastUpdateTimeMs: Long = System.currentTimeMillis()
    @Volatile private var currentVolume: Double = 1.0
    @Volatile private var currentShuffle: Boolean = false
    @Volatile private var currentLoopStatus: LoopStatus = LoopStatus.None

    // Last announced values, so PropertiesChanged carries only real deltas.
    @Volatile private var announcedMetadata: Map<String, Variant<*>> = buildMetadata(null)
    @Volatile private var announcedPlaybackStatus: String = "Stopped"

    private var connection: DBusConnection? = null

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
            }.onFailure { e ->
                println("MPRIS: could not register $BUS_NAME: ${e.message}")
                runCatching { connection?.disconnect() }
                connection = null
            }
        }
    }

    /** Called whenever the track, the playing state or the position baseline changes. */
    fun updateMedia(track: Track?, playing: Boolean, positionMs: Long) {
        currentTrack = track
        isPlaying = playing
        positionAtUpdateMs = positionMs
        lastUpdateTimeMs = System.currentTimeMillis()

        val metadata = buildMetadata(track)
        val trackChanged = !metadataEquals(metadata, announcedMetadata)
        // Cleared by playback alone: a track change while paused or stopped (e.g. an
        // MPRIS Next) must not clear it, or "remains stopped" would be unimplementable.
        if (playing) stopped = false

        val changes = HashMap<String, Variant<*>>()
        if (trackChanged) {
            announcedMetadata = metadata
            changes["Metadata"] = Variant(HashMap(metadata), "a{sv}")
        }
        val status = playbackStatus()
        if (status != announcedPlaybackStatus) {
            announcedPlaybackStatus = status
            changes["PlaybackStatus"] = Variant(status, "s")
        }
        if (changes.isNotEmpty()) sendPlayerPropertiesChanged(changes)
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
        runCatching { connection?.disconnect() }
        connection = null
    }

    private fun currentPositionUs(): Long {
        val elapsedMs = if (isPlaying && !stopped) System.currentTimeMillis() - lastUpdateTimeMs else 0L
        return (positionAtUpdateMs + elapsedMs) * 1000L
    }

    private fun dispatchToMain(action: () -> Unit) {
        if (EventQueue.isDispatchThread()) action() else EventQueue.invokeLater(action)
    }

    private fun lengthUs(): Long? =
        currentTrack?.durationMs?.takeIf { it > 0 }?.let { it * 1000L }

    private fun applySeek(targetMs: Long) {
        val clamped = targetMs.coerceAtLeast(0L)
        positionAtUpdateMs = clamped
        lastUpdateTimeMs = System.currentTimeMillis()
        send(MprisPlayerInterface.Seeked(OBJECT_PATH, clamped * 1000L))
        dispatchToMain { onSeek(clamped) }
    }

    private fun broadcastPlayerProperty(name: String, value: Variant<*>) {
        sendPlayerPropertiesChanged(mapOf(name to value))
    }

    private fun sendPlayerPropertiesChanged(changed: Map<String, Variant<*>>) {
        send(Properties.PropertiesChanged(OBJECT_PATH, PLAYER_INTERFACE, HashMap(changed), ArrayList()))
    }

    private fun send(message: DBusSignal) {
        val conn = connection ?: return
        runCatching { conn.sendMessage(message) }
            .onFailure { e -> println("MPRIS: signal ${message.name} failed: ${e.message}") }
    }

    private fun playbackStatus(): String = when {
        currentTrack == null || stopped -> "Stopped"
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
        "CanSetFullscreen" to Variant(false, "b"),
        "Fullscreen" to Variant(false, "b"),
    )

    private fun playerProperties(): Map<String, Variant<*>> = mapOf(
        "PlaybackStatus" to Variant(playbackStatus(), "s"),
        "LoopStatus" to Variant(currentLoopStatus.mprisName, "s"),
        "Rate" to Variant(1.0, "d"),
        "Shuffle" to Variant(currentShuffle, "b"),
        "Metadata" to Variant(HashMap(buildMetadata(currentTrack)), "a{sv}"),
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

        class Seeked(path: String, val Position: Long) : DBusSignal(path, Position)
    }

    private inner class Mpris2Object : MprisRootInterface, MprisPlayerInterface, Properties {
        override fun getObjectPath(): String = OBJECT_PATH
        override fun isRemote(): Boolean = false

        override fun Raise() = dispatchToMain(onRaise)
        override fun Quit() = Unit

        override fun Play() = dispatchToMain(onPlay)
        override fun Pause() = dispatchToMain(onPause)
        override fun PlayPause() = dispatchToMain(onPlayPause)

        override fun Stop() {
            if (currentTrack == null || stopped) return
            stopped = true
            announcedPlaybackStatus = "Stopped"
            sendPlayerPropertiesChanged(mapOf("PlaybackStatus" to Variant("Stopped", "s")))
            if (currentPositionUs() != 0L) send(MprisPlayerInterface.Seeked(OBJECT_PATH, 0L))
            dispatchToMain(onStop)
        }

        override fun Next() = dispatchToMain(onNext)
        override fun Previous() = dispatchToMain(onPrevious)
        override fun OpenUri(Uri: String) = Unit

        override fun Seek(Offset: Long) {
            if (currentTrack == null) return
            val lengthMs = lengthUs()?.let { it / 1000L }
            val targetMs = (currentPositionUs() + Offset) / 1000L
            val clamped = if (lengthMs != null) targetMs.coerceIn(0L, lengthMs) else targetMs.coerceAtLeast(0L)
            applySeek(clamped)
        }

        override fun SetPosition(TrackId: DBusPath, Position: Long) {
            // The spec says to ignore a SetPosition aimed at a track that is no longer current.
            val current = currentTrack ?: return
            if (TrackId.path != trackObjectPath(current.id)) return
            if (Position < 0) return
            val lengthUs = lengthUs()
            if (lengthUs != null && Position > lengthUs) return
            applySeek(Position / 1000L)
        }

        @Suppress("UNCHECKED_CAST")
        override fun <A> Get(iface: String, property: String): A {
            val all = propertiesFor(iface)
            // The reply to Get is itself a variant: hand the Variant over, the binding
            // unwraps it. Returning the bare value breaks maps (Metadata) at marshal time.
            val value = all[property] ?: throw UnknownProperty("Unknown property: $iface.$property")
            return value as A
        }

        override fun <A> Set(iface: String, property: String, value: A) {
            val raw = (value as? Variant<*>)?.value ?: value
            if (iface == PLAYER_INTERFACE) {
                when (property) {
                    // Each change is applied, then announced: the app's own update* calls see an
                    // unchanged value afterwards and stay quiet, so this is the only signal sent.
                    "Volume" -> (raw as? Double)?.let { v ->
                        val clamped = v.coerceIn(0.0, 1.0)
                        if (clamped == currentVolume) return
                        currentVolume = clamped
                        broadcastPlayerProperty("Volume", Variant(clamped, "d"))
                        dispatchToMain { onVolume(clamped) }
                    }
                    "Shuffle" -> (raw as? Boolean)?.let { s ->
                        if (s == currentShuffle) return
                        currentShuffle = s
                        broadcastPlayerProperty("Shuffle", Variant(s, "b"))
                        dispatchToMain { onShuffle(s) }
                    }
                    "LoopStatus" -> (raw as? String)?.let { name ->
                        val status = LoopStatus.fromMprisName(name) ?: return
                        if (status == currentLoopStatus) return
                        currentLoopStatus = status
                        broadcastPlayerProperty("LoopStatus", Variant(status.mprisName, "s"))
                        dispatchToMain { onLoopStatus(status) }
                    }
                    // No variable rate: anything but 1.0 is a best-fit keep, except 0.0
                    // which the spec defines as Pause.
                    "Rate" -> (raw as? Double)?.let { r ->
                        if (r == 0.0) dispatchToMain(onPause)
                    }
                    else -> {
                        if (playerProperties().containsKey(property)) {
                            throw PropertyReadOnly("Property not writable: $iface.$property")
                        }
                        throw UnknownProperty("Unknown property: $iface.$property")
                    }
                }
                return
            }
            if (iface == ROOT_INTERFACE) {
                // Fullscreen stays false: CanSetFullscreen is false, so setting it is a no-op.
                if (rootProperties().containsKey(property)) {
                    throw PropertyReadOnly("Property not writable: $iface.$property")
                }
                throw UnknownProperty("Unknown property: $iface.$property")
            }
            throw UnknownInterface("Unknown interface: $iface")
        }

        override fun GetAll(iface: String): Map<String, Variant<*>> = propertiesFor(iface)

        private fun propertiesFor(iface: String): Map<String, Variant<*>> = when (iface) {
            ROOT_INTERFACE -> rootProperties()
            PLAYER_INTERFACE -> playerProperties()
            else -> throw UnknownInterface("Unknown interface: $iface")
        }
    }

    companion object {
        const val BUS_NAME = "org.mpris.MediaPlayer2.kittytune"
        const val OBJECT_PATH = "/org/mpris/MediaPlayer2"
        const val ROOT_INTERFACE = "org.mpris.MediaPlayer2"
        const val PLAYER_INTERFACE = "org.mpris.MediaPlayer2.Player"
        const val NO_TRACK_PATH = "/org/mpris/MediaPlayer2/TrackList/NoTrack"
        const val IDENTITY = "KittyTune"
        const val DEFAULT_DESKTOP_ENTRY = "kitty-tune"

        fun isLinux(): Boolean = System.getProperty("os.name").lowercase().let { "linux" in it || "unix" in it }

        /** D-Bus object paths only allow [A-Za-z0-9_], so ids hash to their unsigned digits. */
        fun trackObjectPath(id: Long): String = "$OBJECT_PATH/Track/${java.lang.Long.toUnsignedString(id)}"

        /** Placeholders are for the UI, never for the bus: omit instead of faking a cover. */
        fun usableArtUrl(url: String): Boolean =
            url.isNotBlank() && !url.contains("picsum.photos")

        private fun variantValueEquals(a: Any?, b: Any?): Boolean {
            if (a is Array<*> && b is Array<*>) return a.contentDeepEquals(b)
            return a == b
        }

        fun metadataEquals(a: Map<String, Variant<*>>, b: Map<String, Variant<*>>): Boolean {
            if (a.keys != b.keys) return false
            return a.all { (k, v) ->
                val other = b[k] ?: return@all false
                v.getSig() == other.getSig() && variantValueEquals(v.value, other.value)
            }
        }

        fun buildMetadata(track: Track?): HashMap<String, Variant<*>> {
            val meta = HashMap<String, Variant<*>>()
            if (track == null) {
                // Plasma rejects metadata without a trackid, even when nothing is loaded.
                meta["mpris:trackid"] = Variant(DBusPath(NO_TRACK_PATH), "o")
                return meta
            }
            meta["mpris:trackid"] = Variant(DBusPath(trackObjectPath(track.id)), "o")
            meta["xesam:title"] = Variant(track.title ?: "", "s")
            val artist = track.displayArtist.ifBlank { track.user?.username.orEmpty() }
            if (artist.isNotBlank()) meta["xesam:artist"] = Variant(arrayOf(artist), "as")
            val album = track.publisherMetadata?.albumTitle ?: track.publisherMetadata?.releaseTitle
            if (!album.isNullOrBlank()) meta["xesam:album"] = Variant(album, "s")
            track.permalinkUrl?.takeIf { it.isNotBlank() }?.let { meta["xesam:url"] = Variant(it, "s") }
            if (usableArtUrl(track.fullResArtwork)) meta["mpris:artUrl"] = Variant(track.fullResArtwork, "s")
            track.durationMs?.takeIf { it > 0 }?.let { meta["mpris:length"] = Variant(it * 1000L, "x") }
            return meta
        }
    }
}
