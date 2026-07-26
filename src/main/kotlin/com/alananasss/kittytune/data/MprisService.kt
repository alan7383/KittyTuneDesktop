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

class MprisService(
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onPlayPause: () -> Unit,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
    private val onSeek: (Long) -> Unit
) : Closeable {

    private var connection: DBusConnection? = null
    var isConnected: Boolean = false
        private set

    @Volatile private var currentTrack: Track? = null
    @Volatile private var isPlaying: Boolean = false
    @Volatile private var positionAtUpdateMs: Long = 0L
    @Volatile private var lastUpdateTimeMs: Long = System.currentTimeMillis()

    private val positionScheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "MPRIS-PositionUpdater").apply { isDaemon = true }
    }

    init {
        try {
            val os = System.getProperty("os.name").lowercase()
            if (os.contains("linux") || os.contains("unix")) {
                connection = DBusConnectionBuilder.forSessionBus().build()
                connection?.requestBusName("org.mpris.MediaPlayer2.kittytune")
                val mprisObject = MprisPlayerObject()
                connection?.exportObject("/org/mpris/MediaPlayer2", mprisObject)
                isConnected = true
                startPositionBroadcaster()
                println("MPRIS [init] service registered as org.mpris.MediaPlayer2.kittytune")
            }
        } catch (e: Exception) {
            println("MPRIS [init] FAILED: ${e.message}")
            connection = null
            isConnected = false
        }
    }

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
        if (EventQueue.isDispatchThread()) {
            action()
        } else {
            EventQueue.invokeLater { action() }
        }
    }

    private fun startPositionBroadcaster() {
        positionScheduler.scheduleAtFixedRate({
            if (!isConnected || connection == null || !isPlaying) return@scheduleAtFixedRate
            try {
                val changes = HashMap<String, Variant<*>>()
                changes["Position"] = Variant(currentPositionUs(), "x")
                connection?.sendMessage(
                    Properties.PropertiesChanged(
                        "/org/mpris/MediaPlayer2",
                        "org.mpris.MediaPlayer2.Player",
                        changes,
                        ArrayList()
                    )
                )
            } catch (_: Exception) {}
        }, 1, 1, TimeUnit.SECONDS)
    }

    private fun emitSeeked(positionUs: Long) {
        try {
            println("MPRIS [Seeked] emitting Seeked signal: position=${positionUs}us (${positionUs / 1000000}s)")
            val signal = DBusSignal(
                null,
                null,
                "/org/mpris/MediaPlayer2",
                "org.mpris.MediaPlayer2.Player",
                "Seeked",
                positionUs
            )
            connection?.sendMessage(signal)
        } catch (e: Exception) {
            println("MPRIS [Seeked] FAILED: ${e.message}")
        }
    }

    fun updateMedia(track: Track?, isPlaying: Boolean, positionMs: Long) {
        val trackChanged = currentTrack?.id != track?.id
        this.currentTrack = track
        this.isPlaying = isPlaying
        this.positionAtUpdateMs = positionMs
        this.lastUpdateTimeMs = System.currentTimeMillis()
        println("MPRIS [updateMedia] track=${track?.title}, playing=$isPlaying, pos=${positionMs}ms, trackChanged=$trackChanged")
        if (!isConnected || connection == null) return
        try {
            val changes = buildPropertiesChanged(full = trackChanged)
            connection?.sendMessage(changes)
        } catch (e: Exception) {
            println("MPRIS [updateMedia] send FAILED: ${e.message}")
        }
    }

    private fun buildMetadata(): HashMap<String, Variant<*>> {
        val metadata = HashMap<String, Variant<*>>()
        val track = currentTrack
        if (track != null) {
            metadata["xesam:title"] = Variant(track.title ?: "Unknown Title", "s")
            val artist = track.publisherMetadata?.artist ?: track.user?.username ?: "Unknown Artist"
            metadata["xesam:artist"] = Variant(arrayOf(artist), "as")
            val album = track.publisherMetadata?.albumTitle ?: track.publisherMetadata?.releaseTitle
            if (!album.isNullOrBlank()) {
                metadata["xesam:album"] = Variant(album, "s")
            }
            metadata["mpris:artUrl"] = Variant(track.fullResArtwork, "s")
            metadata["mpris:length"] = Variant((track.durationMs ?: 0L) * 1000L, "x")
            metadata["mpris:trackid"] = Variant("/org/mpris/MediaPlayer2/Track/${track.id}", "o")
        }
        return metadata
    }

    private fun buildPlaybackStatus(): String {
        return if (isPlaying) "Playing" else "Paused"
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
        try {
            connection?.disconnect()
        } catch (_: Exception) {}
        connection = null
        isConnected = false
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
    }

    @DBusInterfaceName("org.mpris.MediaPlayer2")
    interface MprisRootInterface : DBusInterface {
        fun Raise()
        fun Quit()
    }

    private inner class MprisPlayerObject : MprisPlayerInterface, MprisRootInterface, Properties {
        override fun getObjectPath(): String = "/org/mpris/MediaPlayer2"

        override fun Play() {
            println("MPRIS [D-Bus] Play called")
            dispatchToMain { onPlay() }
        }
        override fun Pause() {
            println("MPRIS [D-Bus] Pause called")
            dispatchToMain { onPause() }
        }
        override fun PlayPause() {
            println("MPRIS [D-Bus] PlayPause called")
            dispatchToMain { onPlayPause() }
        }
        override fun Stop() {
            println("MPRIS [D-Bus] Stop called")
            dispatchToMain { onPause() }
        }
        override fun Next() {
            println("MPRIS [D-Bus] Next called")
            dispatchToMain { onNext() }
        }
        override fun Previous() {
            println("MPRIS [D-Bus] Previous called")
            dispatchToMain { onPrevious() }
        }
        override fun Seek(Offset: Long) {
            println("MPRIS [D-Bus] Seek called: offset=${Offset}us (${Offset / 1000}ms)")
            dispatchToMain {
                println("MPRIS [Seek] dispatching to main: offset=${Offset}ms")
                val targetMs = Offset / 1000L
                onSeek(targetMs)
                positionAtUpdateMs = targetMs
                lastUpdateTimeMs = System.currentTimeMillis()
                emitSeeked(Offset)
            }
        }
        override fun SetPosition(TrackId: DBusPath, Position: Long) {
            println("MPRIS [D-Bus] SetPosition called: trackId=${TrackId.path}, pos=${Position}us (${Position / 1000}ms)")
            dispatchToMain {
                println("MPRIS [SetPosition] dispatching to main: pos=${Position}us")
                val targetMs = Position / 1000L
                onSeek(targetMs)
                positionAtUpdateMs = targetMs
                lastUpdateTimeMs = System.currentTimeMillis()
                emitSeeked(Position)
            }
        }

        override fun Raise() { println("MPRIS [D-Bus] Raise called") }
        override fun Quit() { println("MPRIS [D-Bus] Quit called") }

        override fun isRemote(): Boolean = false

        override fun <A> Get(iface: String, property: String): A {
            println("MPRIS [D-Bus] Get called: iface=$iface, property=$property")
            @Suppress("UNCHECKED_CAST")
            return when (iface) {
                "org.mpris.MediaPlayer2.Player" -> when (property) {
                    "PlaybackStatus" -> buildPlaybackStatus() as A
                    "Metadata" -> buildMetadata() as A
                    "Position" -> currentPositionUs() as A
                    "CanPlay" -> true as A
                    "CanPause" -> true as A
                    "CanGoNext" -> true as A
                    "CanGoPrevious" -> true as A
                    "CanSeek" -> true as A
                    "CanControl" -> true as A
                    else -> {
                        println("MPRIS [D-Bus] Get UNKNOWN property: $property")
                        throw org.freedesktop.dbus.errors.UnknownProperty("Unknown property: $property")
                    }
                }
                "org.mpris.MediaPlayer2" -> when (property) {
                    "DesktopEntry" -> "kittytune" as A
                    "SupportedUriSchemes" -> emptyArray<String>() as A
                    "SupportedMimeTypes" -> emptyArray<String>() as A
                    else -> {
                        println("MPRIS [D-Bus] Get UNKNOWN property: $property")
                        throw org.freedesktop.dbus.errors.UnknownProperty("Unknown property: $property")
                    }
                }
                else -> {
                    println("MPRIS [D-Bus] Get UNKNOWN iface: $iface")
                    throw org.freedesktop.dbus.errors.UnknownProperty("Unknown interface: $iface")
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        override fun <A> Set(iface: String, property: String, value: A) {
            println("MPRIS [D-Bus] Set called: iface=$iface, property=$property, value=$value (${value?.javaClass?.simpleName})")
            when (iface) {
                "org.mpris.MediaPlayer2.Player" -> when (property) {
                    "Position" -> {
                        val posUs = (value as? Long) ?: run {
                            println("MPRIS [Set] Position value is not Long: ${value?.javaClass}")
                            return
                        }
                        println("MPRIS [Set] Position=$posUs us (${posUs / 1000}ms)")
                        dispatchToMain {
                            val targetMs = posUs / 1000L
                            onSeek(targetMs)
                            positionAtUpdateMs = targetMs
                            lastUpdateTimeMs = System.currentTimeMillis()
                            emitSeeked(posUs)
                        }
                        return
                    }
                }
            }
            println("MPRIS [Set] Property not writable: $iface.$property")
            throw org.freedesktop.dbus.errors.PropertyReadOnly("Property not writable")
        }

        override fun GetAll(iface: String): Map<String, Variant<*>> {
            println("MPRIS [D-Bus] GetAll called: iface=$iface")
            return when (iface) {
                "org.mpris.MediaPlayer2.Player" -> mapOf(
                    "PlaybackStatus" to Variant(buildPlaybackStatus(), "s"),
                    "Metadata" to Variant(buildMetadata(), "a{sv}"),
                    "Position" to Variant(currentPositionUs(), "x"),
                    "CanPlay" to Variant(true, "b"),
                    "CanPause" to Variant(true, "b"),
                    "CanGoNext" to Variant(true, "b"),
                    "CanGoPrevious" to Variant(true, "b"),
                    "CanSeek" to Variant(true, "b"),
                    "CanControl" to Variant(true, "b")
                )
                "org.mpris.MediaPlayer2" -> mapOf(
                    "DesktopEntry" to Variant("kittytune", "s"),
                    "SupportedUriSchemes" to Variant(emptyArray<String>(), "as"),
                    "SupportedMimeTypes" to Variant(emptyArray<String>(), "as")
                )
                else -> emptyMap()
            }
        }
    }
}
