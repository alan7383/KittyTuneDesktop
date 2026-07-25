package com.alananasss.kittytune.data

import com.alananasss.kittytune.domain.Track
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.types.Variant
import java.io.Closeable

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

    init {
        try {
            val os = System.getProperty("os.name").lowercase()
            if (os.contains("linux") || os.contains("unix")) {
                connection = DBusConnectionBuilder.forSessionBus().build()
                connection?.requestBusName("org.mpris.MediaPlayer2.kittytune")
                val mprisObject = MprisPlayerObject()
                connection?.exportObject("/org/mpris/MediaPlayer2", mprisObject)
                isConnected = true
                println("MPRIS service registered on D-Bus as org.mpris.MediaPlayer2.kittytune")
            }
        } catch (e: Exception) {
            println("MPRIS initialization skipped/failed: ${e.message}")
            connection = null
            isConnected = false
        }
    }

    fun updateMedia(track: Track?, isPlaying: Boolean, positionMs: Long) {
        if (!isConnected || connection == null) return
        try {
            val metadata = HashMap<String, Variant<*>>()
            if (track != null) {
                metadata["xesam:title"] = Variant(track.title ?: "Unknown Title")
                metadata["xesam:artist"] = Variant(arrayOf(track.user?.username ?: "Unknown Artist"))
                metadata["mpris:artUrl"] = Variant(track.fullResArtwork)
                metadata["mpris:length"] = Variant((track.durationMs ?: 0L) * 1000L)
                metadata["mpris:trackid"] = Variant("/org/mpris/MediaPlayer2/Track/${track.id}")
            }
            val status = if (isPlaying) "Playing" else "Paused"
            val changes = HashMap<String, Variant<*>>()
            changes["PlaybackStatus"] = Variant(status)
            changes["Metadata"] = Variant(metadata)
            changes["Position"] = Variant(positionMs * 1000L)

            val propertiesChangedSignal = org.freedesktop.dbus.interfaces.Properties.PropertiesChanged(
                "/org/mpris/MediaPlayer2",
                "org.mpris.MediaPlayer2.Player",
                changes,
                ArrayList<String>()
            )
            connection?.sendMessage(propertiesChangedSignal)
        } catch (e: Exception) {
            println("MPRIS updateMedia failed: ${e.message}")
        }
    }

    override fun close() {
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
        fun SetPosition(TrackId: String, Position: Long)
    }

    @DBusInterfaceName("org.mpris.MediaPlayer2")
    interface MprisRootInterface : DBusInterface {
        fun Raise()
        fun Quit()
    }

    private inner class MprisPlayerObject : MprisPlayerInterface, MprisRootInterface {
        override fun getObjectPath(): String = "/org/mpris/MediaPlayer2"

        override fun Play() { onPlay() }
        override fun Pause() { onPause() }
        override fun PlayPause() { onPlayPause() }
        override fun Stop() { onPause() }
        override fun Next() { onNext() }
        override fun Previous() { onPrevious() }
        override fun Seek(Offset: Long) { onSeek(Offset / 1000L) }
        override fun SetPosition(TrackId: String, Position: Long) { onSeek(Position / 1000L) }

        override fun Raise() {}
        override fun Quit() {}

        override fun isRemote(): Boolean = false
    }
}
