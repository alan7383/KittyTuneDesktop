package com.alananasss.kittytune.data

import com.alananasss.kittytune.domain.Track
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.Variant
import java.awt.EventQueue
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * KDE Plasma MPRIS2 service — fully spec-compliant implementation.
 *
 * The existing [MprisService] is reserved for the End4 dotfile integration and
 * must not be modified.  This service lives on a separate D-Bus name
 * (`org.mpris.MediaPlayer2.kittytune.kde`) so both can coexist.
 *
 * Differences from [MprisService] that make KDE Plasma recognise the player:
 *  - All **required** root-interface properties are present in GetAll:
 *    Identity, CanRaise, CanQuit, HasTrackList, SupportedUriSchemes,
 *    SupportedMimeTypes, DesktopEntry.
 *  - All **required** player-interface properties:
 *    PlaybackStatus, LoopStatus, Rate, Shuffle, Metadata, Volume,
 *    Position, MinimumRate, MaximumRate, CanGoNext, CanGoPrevious,
 *    CanPlay, CanPause, CanSeek, CanControl.
 *  - LoopStatus and Shuffle are **settable** (KDE sends Set for them).
 *  - Volume is **settable** (KDE media player widget has a volume slider).
 *  - Seeked signal is emitted correctly after each seek.
 *  - PropertiesChanged is broadcast on every state change.
 */
class KdeMpris2Service(
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onPlayPause: () -> Unit,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
    private val onSeek: (Long) -> Unit,
    private val onVolume: (Double) -> Unit,
    private val onShuffle: (Boolean) -> Unit,
    private val onLoopStatus: (LoopStatus) -> Unit
) : Closeable {

    // ---------------------------------------------------------------------------
    // Public enums
    // ---------------------------------------------------------------------------

    enum class LoopStatus { None, Track, Playlist }

    // ---------------------------------------------------------------------------
    // Volatile state (written from main thread, read from D-Bus threads)
    // ---------------------------------------------------------------------------

    @Volatile var currentTrack: Track? = null
        private set
    @Volatile var isPlaying: Boolean = false
        private set
    @Volatile var positionAtUpdateMs: Long = 0L
        private set
    @Volatile private var lastUpdateTimeMs: Long = System.currentTimeMillis()

    @Volatile var currentVolume: Double = 1.0
        private set
    @Volatile var currentShuffle: Boolean = false
        private set
    @Volatile var currentLoopStatus: LoopStatus = LoopStatus.None
        private set

    // ---------------------------------------------------------------------------
    // D-Bus connection
    // ---------------------------------------------------------------------------

    private var connection: DBusConnection? = null
    var isConnected: Boolean = false
        private set

    private val positionScheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "KDE-MPRIS2-PositionUpdater").apply { isDaemon = true }
    }

    init {
        try {
            val os = System.getProperty("os.name").lowercase()
            if (os.contains("linux") || os.contains("unix")) {
                connection = DBusConnectionBuilder.forSessionBus().build()
                // Distinct bus name — does NOT conflict with MprisService
                connection?.requestBusName("org.mpris.MediaPlayer2.kittytune.kde")
                val obj = Mpris2Object()
                connection?.exportObject("/org/mpris/MediaPlayer2", obj)
                isConnected = true
                startPositionBroadcaster()
                println("KDE-MPRIS2 [init] registered as org.mpris.MediaPlayer2.kittytune.kde")
            }
        } catch (e: Exception) {
            println("KDE-MPRIS2 [init] FAILED: ${e.message}")
            connection = null
            isConnected = false
        }
    }

    // ---------------------------------------------------------------------------
    // State update API (called from PlayerViewModel on the main thread)
    // ---------------------------------------------------------------------------

    /**
     * Called whenever track, playback state, or seek position changes.
     */
    fun updateMedia(track: Track?, playing: Boolean, positionMs: Long) {
        val trackChanged = currentTrack?.id != track?.id
        currentTrack = track
        isPlaying = playing
        positionAtUpdateMs = positionMs
        lastUpdateTimeMs = System.currentTimeMillis()
        if (!isConnected || connection == null) return
        try {
            connection?.sendMessage(buildPropertiesChanged(full = trackChanged))
        } catch (e: Exception) {
            println("KDE-MPRIS2 [updateMedia] send FAILED: ${e.message}")
        }
    }

    /**
     * Sync volume from the app side (so the widget slider stays in sync).
     */
    fun updateVolume(volume: Double) {
        currentVolume = volume.coerceIn(0.0, 1.0)
        broadcastPlayerProperty("Volume", Variant(currentVolume, "d"))
    }

    /**
     * Sync shuffle state from the app side.
     */
    fun updateShuffle(shuffle: Boolean) {
        currentShuffle = shuffle
        broadcastPlayerProperty("Shuffle", Variant(shuffle, "b"))
    }

    /**
     * Sync loop/repeat state from the app side.
     */
    fun updateLoopStatus(status: LoopStatus) {
        currentLoopStatus = status
        broadcastPlayerProperty("LoopStatus", Variant(status.toMprisString(), "s"))
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    private fun currentPositionUs(): Long {
        val baseUs = positionAtUpdateMs * 1000L
        return if (isPlaying) {
            val elapsed = System.currentTimeMillis() - lastUpdateTimeMs
            baseUs + elapsed * 1000L
        } else {
            baseUs
        }
    }

    private fun dispatchToMain(action: () -> Unit) {
        if (EventQueue.isDispatchThread()) action()
        else EventQueue.invokeLater { action() }
    }

    private fun startPositionBroadcaster() {
        positionScheduler.scheduleAtFixedRate({
            if (!isConnected || connection == null || !isPlaying) return@scheduleAtFixedRate
            try {
                broadcastPlayerProperty("Position", Variant(currentPositionUs(), "x"))
            } catch (_: Exception) {}
        }, 1, 1, TimeUnit.SECONDS)
    }

    private fun emitSeeked(positionUs: Long) {
        try {
            val signal = DBusSignal(
                null,
                "/org/mpris/MediaPlayer2",
                "org.mpris.MediaPlayer2.Player",
                "Seeked",
                "x",
                positionUs
            )
            connection?.sendMessage(signal)
        } catch (e: Exception) {
            println("KDE-MPRIS2 [Seeked] FAILED: ${e.message}")
        }
    }

    private fun broadcastPlayerProperty(name: String, value: Variant<*>) {
        if (!isConnected || connection == null) return
        try {
            val changes = HashMap<String, Variant<*>>()
            changes[name] = value
            connection?.sendMessage(
                Properties.PropertiesChanged(
                    "/org/mpris/MediaPlayer2",
                    "org.mpris.MediaPlayer2.Player",
                    changes,
                    ArrayList<String>()
                )
            )
        } catch (e: Exception) {
            println("KDE-MPRIS2 [broadcast] FAILED: ${e.message}")
        }
    }

    private fun buildMetadata(): HashMap<String, Variant<*>> {
        val meta = HashMap<String, Variant<*>>()
        val track = currentTrack
        if (track != null) {
            meta["xesam:title"] = Variant(track.title ?: "Unknown Title", "s")
            val artist = track.publisherMetadata?.artist ?: track.user?.username ?: "Unknown Artist"
            meta["xesam:artist"] = Variant(arrayOf(artist), "as")
            val album = track.publisherMetadata?.albumTitle ?: track.publisherMetadata?.releaseTitle
            if (!album.isNullOrBlank()) meta["xesam:album"] = Variant(album, "s")
            meta["mpris:artUrl"] = Variant(track.fullResArtwork, "s")
            meta["mpris:length"] = Variant((track.durationMs ?: 0L) * 1000L, "x")
            // Use String with "o" signature — same pattern as MprisService.kt (DBusPath in nested
            // maps can cause serialisation exceptions with some dbus-java versions)
            meta["mpris:trackid"] = Variant("/org/mpris/MediaPlayer2/Track/${track.id}", "o")
        } else {
            // KDE requires a valid trackid even when nothing is playing
            meta["mpris:trackid"] = Variant("/org/mpris/MediaPlayer2/Track/None", "o")
        }
        return meta
    }

    private fun buildPlaybackStatus(): String = when {
        currentTrack == null -> "Stopped"
        isPlaying -> "Playing"
        else -> "Paused"
    }

    private fun buildPropertiesChanged(full: Boolean = true): Properties.PropertiesChanged {
        val changes = HashMap<String, Variant<*>>()
        changes["PlaybackStatus"] = Variant(buildPlaybackStatus(), "s")
        changes["Position"] = Variant(currentPositionUs(), "x")
        if (full) {
            changes["Metadata"] = Variant(buildMetadata(), "a{sv}")
            changes["CanPlay"] = Variant(true, "b")
            changes["CanPause"] = Variant(true, "b")
            changes["CanGoNext"] = Variant(true, "b")
            changes["CanGoPrevious"] = Variant(true, "b")
            changes["CanSeek"] = Variant(true, "b")
            changes["CanControl"] = Variant(true, "b")
            changes["Shuffle"] = Variant(currentShuffle, "b")
            changes["LoopStatus"] = Variant(currentLoopStatus.toMprisString(), "s")
            changes["Volume"] = Variant(currentVolume, "d")
            changes["Rate"] = Variant(1.0, "d")
        }
        return Properties.PropertiesChanged(
            "/org/mpris/MediaPlayer2",
            "org.mpris.MediaPlayer2.Player",
            changes,
            ArrayList()
        )
    }

    override fun close() {
        positionScheduler.shutdownNow()
        try { connection?.disconnect() } catch (_: Exception) {}
        connection = null
        isConnected = false
        println("KDE-MPRIS2 [close] disconnected")
    }

    // ---------------------------------------------------------------------------
    // LoopStatus helper
    // ---------------------------------------------------------------------------

    private fun LoopStatus.toMprisString(): String = when (this) {
        LoopStatus.None -> "None"
        LoopStatus.Track -> "Track"
        LoopStatus.Playlist -> "Playlist"
    }

    private fun String.toLoopStatus(): LoopStatus = when (this) {
        "Track" -> LoopStatus.Track
        "Playlist" -> LoopStatus.Playlist
        else -> LoopStatus.None
    }

    // ---------------------------------------------------------------------------
    // D-Bus interface definitions
    // ---------------------------------------------------------------------------

    @DBusInterfaceName("org.mpris.MediaPlayer2")
    interface MprisRootInterface : DBusInterface {
        fun Raise()
        fun Quit()
    }

    @DBusInterfaceName("org.mpris.MediaPlayer2.Player")
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
    }

    // ---------------------------------------------------------------------------
    // D-Bus exported object
    // ---------------------------------------------------------------------------

    private inner class Mpris2Object : MprisRootInterface, MprisPlayerInterface, Properties {

        override fun getObjectPath(): String = "/org/mpris/MediaPlayer2"
        override fun isRemote(): Boolean = false

        // --- Root interface ---------------------------------------------------

        override fun Raise() { println("KDE-MPRIS2 [D-Bus] Raise") }
        override fun Quit()  { println("KDE-MPRIS2 [D-Bus] Quit") }

        // --- Player interface -------------------------------------------------

        override fun Play()      { println("KDE-MPRIS2 [D-Bus] Play");      dispatchToMain { onPlay() } }
        override fun Pause()     { println("KDE-MPRIS2 [D-Bus] Pause");     dispatchToMain { onPause() } }
        override fun PlayPause() { println("KDE-MPRIS2 [D-Bus] PlayPause"); dispatchToMain { onPlayPause() } }
        override fun Stop()      { println("KDE-MPRIS2 [D-Bus] Stop");      dispatchToMain { onPause() } }
        override fun Next()      { println("KDE-MPRIS2 [D-Bus] Next");      dispatchToMain { onNext() } }
        override fun Previous()  { println("KDE-MPRIS2 [D-Bus] Previous");  dispatchToMain { onPrevious() } }
        override fun OpenUri(Uri: String) { println("KDE-MPRIS2 [D-Bus] OpenUri: $Uri") }

        override fun Seek(Offset: Long) {
            println("KDE-MPRIS2 [D-Bus] Seek offset=${Offset}us")
            dispatchToMain {
                val targetMs = (currentPositionUs() + Offset) / 1000L
                onSeek(targetMs.coerceAtLeast(0))
                positionAtUpdateMs = targetMs.coerceAtLeast(0)
                lastUpdateTimeMs = System.currentTimeMillis()
                emitSeeked(targetMs * 1000L)
            }
        }

        override fun SetPosition(TrackId: DBusPath, Position: Long) {
            println("KDE-MPRIS2 [D-Bus] SetPosition trackId=${TrackId.path}, pos=${Position}us")
            dispatchToMain {
                val targetMs = Position / 1000L
                onSeek(targetMs)
                positionAtUpdateMs = targetMs
                lastUpdateTimeMs = System.currentTimeMillis()
                emitSeeked(Position)
            }
        }

        // --- Properties interface ---------------------------------------------

        @Suppress("UNCHECKED_CAST")
        override fun <A> Get(iface: String, property: String): A {
            println("KDE-MPRIS2 [D-Bus] Get iface=$iface property=$property")
            return when (iface) {
                "org.mpris.MediaPlayer2" -> when (property) {
                    "CanQuit"             -> false as A
                    "CanRaise"            -> false as A
                    "HasTrackList"        -> false as A
                    "Identity"            -> "KittyTune" as A
                    "DesktopEntry"        -> "kittytune" as A
                    "SupportedUriSchemes" -> emptyArray<String>() as A
                    "SupportedMimeTypes"  -> emptyArray<String>() as A
                    else -> throw org.freedesktop.dbus.errors.UnknownProperty("Unknown root property: $property")
                }
                "org.mpris.MediaPlayer2.Player" -> when (property) {
                    "PlaybackStatus" -> buildPlaybackStatus() as A
                    "LoopStatus"     -> currentLoopStatus.toMprisString() as A
                    "Rate"           -> 1.0 as A
                    "Shuffle"        -> currentShuffle as A
                    "Metadata"       -> buildMetadata() as A
                    "Volume"         -> currentVolume as A
                    "Position"       -> currentPositionUs() as A
                    "MinimumRate"    -> 1.0 as A
                    "MaximumRate"    -> 1.0 as A
                    "CanGoNext"      -> true as A
                    "CanGoPrevious"  -> true as A
                    "CanPlay"        -> true as A
                    "CanPause"       -> true as A
                    "CanSeek"        -> true as A
                    "CanControl"     -> true as A
                    else -> throw org.freedesktop.dbus.errors.UnknownProperty("Unknown player property: $property")
                }
                else -> throw org.freedesktop.dbus.errors.UnknownProperty("Unknown interface: $iface")
            }
        }

        @Suppress("UNCHECKED_CAST")
        override fun <A> Set(iface: String, property: String, value: A) {
            println("KDE-MPRIS2 [D-Bus] Set iface=$iface property=$property value=$value")
            
            val unwrappedValue = if (value is org.freedesktop.dbus.types.Variant<*>) {
                value.value
            } else {
                value
            }
            
            when (iface) {
                "org.mpris.MediaPlayer2.Player" -> when (property) {
                    "Volume" -> {
                        val v = (unwrappedValue as? Double) ?: return
                        currentVolume = v.coerceIn(0.0, 1.0)
                        dispatchToMain { onVolume(currentVolume) }
                        return
                    }
                    "Shuffle" -> {
                        val s = (unwrappedValue as? Boolean) ?: return
                        currentShuffle = s
                        dispatchToMain { onShuffle(s) }
                        return
                    }
                    "LoopStatus" -> {
                        val ls = ((unwrappedValue as? String) ?: return).toLoopStatus()
                        currentLoopStatus = ls
                        dispatchToMain { onLoopStatus(ls) }
                        return
                    }
                    "Rate" -> return // read-only effectively; we only support 1.0
                }
            }
            throw org.freedesktop.dbus.errors.PropertyReadOnly("Property not writable: $iface.$property")
        }

        override fun GetAll(iface: String): Map<String, Variant<*>> {
            println("KDE-MPRIS2 [D-Bus] GetAll iface=$iface")
            return when (iface) {
                "org.mpris.MediaPlayer2" -> mapOf(
                    "CanQuit"             to Variant(false, "b"),
                    "CanRaise"            to Variant(false, "b"),
                    "HasTrackList"        to Variant(false, "b"),
                    "Identity"            to Variant("KittyTune", "s"),
                    "DesktopEntry"        to Variant("kittytune", "s"),
                    "SupportedUriSchemes" to Variant(emptyArray<String>(), "as"),
                    "SupportedMimeTypes"  to Variant(emptyArray<String>(), "as")
                )
                "org.mpris.MediaPlayer2.Player" -> mapOf(
                    "PlaybackStatus" to Variant(buildPlaybackStatus(), "s"),
                    "LoopStatus"     to Variant(currentLoopStatus.toMprisString(), "s"),
                    "Rate"           to Variant(1.0, "d"),
                    "Shuffle"        to Variant(currentShuffle, "b"),
                    "Metadata"       to Variant(buildMetadata(), "a{sv}"),
                    "Volume"         to Variant(currentVolume, "d"),
                    "Position"       to Variant(currentPositionUs(), "x"),
                    "MinimumRate"    to Variant(1.0, "d"),
                    "MaximumRate"    to Variant(1.0, "d"),
                    "CanGoNext"      to Variant(true, "b"),
                    "CanGoPrevious"  to Variant(true, "b"),
                    "CanPlay"        to Variant(true, "b"),
                    "CanPause"       to Variant(true, "b"),
                    "CanSeek"        to Variant(true, "b"),
                    "CanControl"     to Variant(true, "b")
                )
                else -> emptyMap()
            }
        }
    }
}
