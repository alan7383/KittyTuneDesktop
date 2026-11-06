package com.alananasss.kittytune.data

import com.alananasss.kittytune.core.AppIconVariants
import com.alananasss.kittytune.core.AppDirs
import com.alananasss.kittytune.core.AtomicFiles
import com.alananasss.kittytune.core.LinuxAppIdHelper
import com.alananasss.kittytune.domain.Track
import org.freedesktop.dbus.TypeRef
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusProperty
import org.freedesktop.dbus.annotations.DBusProperty.Access.READ
import org.freedesktop.dbus.annotations.PropertiesEmitsChangedSignal.EmitChangeSignal.FALSE
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
import java.io.File
import java.net.URI
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

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
    private val onRate: ((Double) -> Unit)? = null,
    private val busName: String = BUS_NAME,
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
    @Volatile private var lastSeekedSignalMs: Long = 0L
    @Volatile private var currentRate: Double = 1.0
    @Volatile private var overrideDurationMs: Long? = null
    @Volatile private var currentVolume: Double = 1.0
    @Volatile private var currentShuffle: Boolean = false
    @Volatile private var currentLoopStatus: LoopStatus = LoopStatus.None

    // Last announced values, so PropertiesChanged carries only real deltas.
    @Volatile private var announcedMetadata: Map<String, Variant<*>> = buildMetadata(null)
    @Volatile private var announcedPlaybackStatus: String = "Stopped"

    private var connection: DBusConnection? = null

    // Preserve D-Bus command order and keep player mutations from running concurrently.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val actions = Channel<() -> Unit>(Channel.UNLIMITED)
    private val actionWorker = serviceScope.launch {
        for (action in actions) {
            runCatching { action() }.onFailure { e ->
                println("MPRIS action failed: ${e.message}")
            }
        }
    }

    /** The .desktop entry's id, which is how shells find the icon to show next to the widget. */
    private val desktopEntry: String = DEFAULT_DESKTOP_ENTRY

    init {
        if (isLinux()) {
            runCatching {
                val conn = DBusConnectionBuilder.forSessionBus().withShared(false).build()
                connection = conn
                conn.exportObject(OBJECT_PATH, Mpris2Object())
                conn.requestBusName(busName)
            }.onFailure { e ->
                println("MPRIS: could not register $busName: ${e.message}")
                runCatching { connection?.disconnect() }
                connection = null
            }
        }
    }

    /** Called whenever the track, the playing state or the position baseline changes. */
    fun updateMedia(track: Track?, playing: Boolean, positionMs: Long, durationMs: Long? = null) {
        val currentMs = currentPositionUs() / 1000L
        val previousTrackId = currentTrack?.id
        currentTrack = track
        isPlaying = playing
        overrideDurationMs = durationMs
        val lengthMs = lengthUs()?.let { it / 1000L }
        val clamped = if (lengthMs != null && lengthMs > 0L) positionMs.coerceIn(0L, lengthMs) else positionMs.coerceAtLeast(0L)
        val now = System.currentTimeMillis()
        val movedBackward = clamped < currentMs - 500L
        val jumpedForward = clamped > currentMs + 1500L

        positionAtUpdateMs = clamped
        lastUpdateTimeMs = now

        val metadata = buildMetadata(track, overrideDurationMs)
        val metadataChanged = !metadataEquals(metadata, announcedMetadata)
        // Cleared by playback or unloading: a track change while paused or stopped (e.g. an
        // MPRIS Next) must not clear it, or "remains stopped" would be unimplementable.
        if (playing || track == null) stopped = false

        val changes = HashMap<String, Variant<*>>()
        if (metadataChanged) {
            announcedMetadata = metadata
            changes["Metadata"] = Variant(HashMap(metadata), "a{sv}")
        }
        val status = playbackStatus()
        if (status != announcedPlaybackStatus) {
            announcedPlaybackStatus = status
            changes["PlaybackStatus"] = Variant(status, "s")
        }
        if (changes.isNotEmpty()) sendPlayerPropertiesChanged(changes)

        if (track != null && previousTrackId == track.id && (movedBackward || jumpedForward)) {
            lastSeekedSignalMs = now
            send(MprisPlayerInterface.Seeked(OBJECT_PATH, clamped * 1000L))
        }
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

    fun updateRate(rate: Double) {
        val clamped = rate.coerceIn(0.5, 2.0)
        if (kotlin.math.abs(clamped - currentRate) < 1e-4) return
        val currentPos = currentPositionUs()
        val now = System.currentTimeMillis()
        positionAtUpdateMs = currentPos / 1000L
        lastUpdateTimeMs = now
        currentRate = clamped
        broadcastPlayerProperty("Rate", Variant(currentRate, "d"))
    }

    /** Emits Seeked when track position changes non-linearly (e.g. from the UI). */
    fun updatePosition(positionMs: Long, emitSeeked: Boolean = true) {
        val lengthMs = lengthUs()?.let { it / 1000L }
        val clamped = if (lengthMs != null && lengthMs > 0L) positionMs.coerceIn(0L, lengthMs) else positionMs.coerceAtLeast(0L)
        val now = System.currentTimeMillis()
        positionAtUpdateMs = clamped
        lastUpdateTimeMs = now
        if (emitSeeked) {
            lastSeekedSignalMs = now
            send(MprisPlayerInterface.Seeked(OBJECT_PATH, clamped * 1000L))
        }
    }

    /**
     * Resynchronizes MPRIS clients with the actual playback engine when drift exceeds threshold.
     * Emits Seeked per MPRIS 2.2 spec ("If the playback progresses in a way that is inconsistent with
     * the Rate property, the Seeked signal is emitted").
     */
    fun syncPosition(positionMs: Long, maxDriftMs: Long = 1000L) {
        val lengthMs = lengthUs()?.let { it / 1000L }
        val clamped = if (lengthMs != null && lengthMs > 0L) positionMs.coerceIn(0L, lengthMs) else positionMs.coerceAtLeast(0L)
        val currentMs = currentPositionUs() / 1000L
        val driftMs = kotlin.math.abs(currentMs - clamped)
        if (driftMs >= maxDriftMs) {
            positionAtUpdateMs = clamped
            lastUpdateTimeMs = System.currentTimeMillis()
            send(MprisPlayerInterface.Seeked(OBJECT_PATH, clamped * 1000L))
        }
    }

    fun updatePlaybackState(playing: Boolean) {
        if (isPlaying == playing && (if (playing) !stopped else true)) return
        positionAtUpdateMs = currentPositionUs() / 1000L
        lastUpdateTimeMs = System.currentTimeMillis()
        isPlaying = playing
        if (playing) stopped = false
        val status = playbackStatus()
        if (status != announcedPlaybackStatus) {
            announcedPlaybackStatus = status
            sendPlayerPropertiesChanged(mapOf("PlaybackStatus" to Variant(status, "s")))
        }
    }

    override fun close() {
        actions.close()
        runCatching { serviceScope.cancel() }
        runCatching { connection?.disconnect() }
        connection = null
    }

    private fun currentPositionUs(): Long {
        val lengthUs = lengthUs()
        val elapsedMs = if (isPlaying && !stopped) {
            ((System.currentTimeMillis() - lastUpdateTimeMs) * currentRate).toLong()
        } else {
            0L
        }
        val posUs = (positionAtUpdateMs + elapsedMs) * 1000L
        return if (lengthUs != null && lengthUs > 0L) posUs.coerceIn(0L, lengthUs) else posUs.coerceAtLeast(0L)
    }

    private fun dispatchAction(action: () -> Unit) {
        actions.trySend(action)
    }

    private fun lengthUs(): Long? =
        (overrideDurationMs ?: currentTrack?.effectiveFullDurationMs ?: currentTrack?.durationMs)?.takeIf { it > 0 }?.let { it * 1000L }

    private fun applySeek(targetMs: Long) {
        val lengthMs = lengthUs()?.let { it / 1000L }
        val clamped = if (lengthMs != null && lengthMs > 0L) targetMs.coerceIn(0L, lengthMs) else targetMs.coerceAtLeast(0L)
        val now = System.currentTimeMillis()
        positionAtUpdateMs = clamped
        lastUpdateTimeMs = now
        lastSeekedSignalMs = now
        send(MprisPlayerInterface.Seeked(OBJECT_PATH, clamped * 1000L))
        dispatchAction { onSeek(clamped) }
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
        "Rate" to Variant(currentRate, "d"),
        "Shuffle" to Variant(currentShuffle, "b"),
        "Metadata" to Variant(HashMap(buildMetadata(currentTrack, overrideDurationMs)), "a{sv}"),
        "Volume" to Variant(currentVolume, "d"),
        "Position" to Variant(currentPositionUs(), "x"),
        "MinimumRate" to Variant(0.5, "d"),
        "MaximumRate" to Variant(2.0, "d"),
        "CanGoNext" to Variant(true, "b"),
        "CanGoPrevious" to Variant(true, "b"),
        "CanPlay" to Variant(true, "b"),
        "CanPause" to Variant(true, "b"),
        "CanSeek" to Variant(true, "b"),
        "CanControl" to Variant(true, "b"),
    )

    interface StringList : TypeRef<List<String>>
    interface MetadataMap : TypeRef<Map<String, Variant<*>>>

    // Quickshell and other clients must see the same contract in introspection and GetAll.
    @DBusProperty(name = "CanQuit", type = Boolean::class, access = READ, emitChangeSignal = FALSE)
    @DBusProperty(name = "CanRaise", type = Boolean::class, access = READ, emitChangeSignal = FALSE)
    @DBusProperty(name = "HasTrackList", type = Boolean::class, access = READ, emitChangeSignal = FALSE)
    @DBusProperty(name = "Identity", type = String::class, access = READ, emitChangeSignal = FALSE)
    @DBusProperty(name = "DesktopEntry", type = String::class, access = READ, emitChangeSignal = FALSE)
    @DBusProperty(name = "SupportedUriSchemes", type = StringList::class, access = READ, emitChangeSignal = FALSE)
    @DBusProperty(name = "SupportedMimeTypes", type = StringList::class, access = READ, emitChangeSignal = FALSE)
    @DBusProperty(name = "CanSetFullscreen", type = Boolean::class, access = READ, emitChangeSignal = FALSE)
    @DBusProperty(name = "Fullscreen", type = Boolean::class, access = READ, emitChangeSignal = FALSE)
    @DBusInterfaceName("org.mpris.MediaPlayer2")
    interface MprisRootInterface : DBusInterface {
        fun Raise()
        fun Quit()
    }

    @DBusProperty(name = "PlaybackStatus", type = String::class, access = READ)
    @DBusProperty(name = "LoopStatus", type = String::class)
    @DBusProperty(name = "Rate", type = Double::class)
    @DBusProperty(name = "Shuffle", type = Boolean::class)
    @DBusProperty(name = "Metadata", type = MetadataMap::class, access = READ)
    @DBusProperty(name = "Volume", type = Double::class)
    @DBusProperty(name = "Position", type = Long::class, access = READ, emitChangeSignal = FALSE)
    @DBusProperty(name = "MinimumRate", type = Double::class, access = READ)
    @DBusProperty(name = "MaximumRate", type = Double::class, access = READ)
    @DBusProperty(name = "CanGoNext", type = Boolean::class, access = READ)
    @DBusProperty(name = "CanGoPrevious", type = Boolean::class, access = READ)
    @DBusProperty(name = "CanPlay", type = Boolean::class, access = READ)
    @DBusProperty(name = "CanPause", type = Boolean::class, access = READ)
    @DBusProperty(name = "CanSeek", type = Boolean::class, access = READ)
    @DBusProperty(name = "CanControl", type = Boolean::class, access = READ, emitChangeSignal = FALSE)
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

        override fun Raise() {
            if (EventQueue.isDispatchThread()) onRaise() else EventQueue.invokeLater(onRaise)
        }
        override fun Quit() = Unit

        override fun Play() {
            if (currentTrack != null) {
                val now = System.currentTimeMillis()
                positionAtUpdateMs = currentPositionUs() / 1000L
                lastUpdateTimeMs = now
                stopped = false
                isPlaying = true
                val status = "Playing"
                if (announcedPlaybackStatus != status) {
                    announcedPlaybackStatus = status
                    sendPlayerPropertiesChanged(mapOf("PlaybackStatus" to Variant(status, "s")))
                }
            }
            dispatchAction(onPlay)
        }

        override fun Pause() {
            if (currentTrack != null) {
                val now = System.currentTimeMillis()
                positionAtUpdateMs = currentPositionUs() / 1000L
                lastUpdateTimeMs = now
                isPlaying = false
                val status = "Paused"
                if (announcedPlaybackStatus != status) {
                    announcedPlaybackStatus = status
                    sendPlayerPropertiesChanged(mapOf("PlaybackStatus" to Variant(status, "s")))
                }
            }
            dispatchAction(onPause)
        }

        override fun PlayPause() {
            if (currentTrack != null) {
                val now = System.currentTimeMillis()
                positionAtUpdateMs = currentPositionUs() / 1000L
                lastUpdateTimeMs = now
                val willPlay = stopped || !isPlaying
                stopped = false
                isPlaying = willPlay
                val status = if (willPlay) "Playing" else "Paused"
                if (announcedPlaybackStatus != status) {
                    announcedPlaybackStatus = status
                    sendPlayerPropertiesChanged(mapOf("PlaybackStatus" to Variant(status, "s")))
                }
            }
            dispatchAction(onPlayPause)
        }

        override fun Stop() {
            if (currentTrack == null || stopped) return
            val previousPosition = currentPositionUs()
            positionAtUpdateMs = 0L
            lastUpdateTimeMs = System.currentTimeMillis()
            stopped = true
            isPlaying = false
            announcedPlaybackStatus = "Stopped"
            sendPlayerPropertiesChanged(mapOf("PlaybackStatus" to Variant("Stopped", "s")))
            if (previousPosition != 0L) send(MprisPlayerInterface.Seeked(OBJECT_PATH, 0L))
            dispatchAction(onStop)
        }

        override fun Next() = dispatchAction(onNext)
        override fun Previous() = dispatchAction(onPrevious)
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
            val currentTrackPath = trackObjectPath(current.id)
            if (TrackId.path != currentTrackPath) return
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
                        dispatchAction { onVolume(clamped) }
                    }
                    "Shuffle" -> (raw as? Boolean)?.let { s ->
                        if (s == currentShuffle) return
                        currentShuffle = s
                        broadcastPlayerProperty("Shuffle", Variant(s, "b"))
                        dispatchAction { onShuffle(s) }
                    }
                    "LoopStatus" -> (raw as? String)?.let { name ->
                        val status = LoopStatus.fromMprisName(name) ?: return
                        if (status == currentLoopStatus) return
                        currentLoopStatus = status
                        broadcastPlayerProperty("LoopStatus", Variant(status.mprisName, "s"))
                        dispatchAction { onLoopStatus(status) }
                    }
                    // Rate 0.0 is defined as Pause by the spec. Other values set playback speed.
                    "Rate" -> (raw as? Double)?.let { r ->
                        if (r == 0.0) {
                            if (currentTrack != null) {
                                val now = System.currentTimeMillis()
                                positionAtUpdateMs = currentPositionUs() / 1000L
                                lastUpdateTimeMs = now
                                isPlaying = false
                                val status = "Paused"
                                if (announcedPlaybackStatus != status) {
                                    announcedPlaybackStatus = status
                                    sendPlayerPropertiesChanged(mapOf("PlaybackStatus" to Variant(status, "s")))
                                }
                            }
                            dispatchAction(onPause)
                        } else if (r > 0.0) {
                            val clamped = r.coerceIn(0.5, 2.0)
                            if (kotlin.math.abs(clamped - currentRate) >= 1e-4) {
                                positionAtUpdateMs = currentPositionUs() / 1000L
                                lastUpdateTimeMs = System.currentTimeMillis()
                                currentRate = clamped
                                broadcastPlayerProperty("Rate", Variant(currentRate, "d"))
                                onRate?.let { cb -> dispatchAction { cb(clamped) } }
                            }
                        }
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
        const val DEFAULT_DESKTOP_ENTRY = LinuxAppIdHelper.APP_ID

        fun isLinux(): Boolean = System.getProperty("os.name").lowercase().let { "linux" in it || "unix" in it }

        /** D-Bus object paths only allow [A-Za-z0-9_], so ids hash to their unsigned digits. */
        fun trackObjectPath(id: Long): String = "$OBJECT_PATH/Track/${java.lang.Long.toUnsignedString(id)}"

        /** Discard random UI placeholders; a missing cover uses KittyTune's own icon. */
        fun usableArtUrl(url: String): Boolean =
            url.isNotBlank() && !url.contains("picsum.photos")

        // A stable, packaged local image also clears stale art in clients that retain the
        // previous URL when artUrl disappears. No network or desktop configuration needed.
        private val fallbackArtworkUri: String? by lazy {
            if (!isLinux()) null else runCatching {
                val bytes = MprisService::class.java.getResourceAsStream("/" + AppIconVariants.resourcePath(AppIconVariants.DEFAULT_KEY))
                    ?.use { it.readBytes() } ?: return@runCatching null
                val directory = File(AppDirs.cacheDir, "mpris").apply { mkdirs() }
                val target = File(directory, "kittytune.png")
                if (!target.isFile || !target.readBytes().contentEquals(bytes)) {
                    val temporary = File.createTempFile("kittytune-", ".png", directory)
                    try {
                        temporary.writeBytes(bytes)
                        AtomicFiles.replace(temporary, target)
                    } finally {
                        temporary.delete()
                    }
                }
                target.toPath().toUri().toASCIIString()
            }.getOrNull()
        }

        /** MPRIS exposes URLs; encode local paths so spaces/# and CoverArt's file:// check work. */
        fun artworkUri(url: String): String? {
            if (!usableArtUrl(url)) return null
            return runCatching {
                when {
                    File(url).isAbsolute -> File(url).toPath().toUri().toASCIIString()
                    url.startsWith("file:") -> Path.of(URI(url)).toUri().toASCIIString()
                    else -> url
                }
            }.getOrNull()
        }

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

        fun buildMetadata(track: Track?, durationMsOverride: Long? = null): HashMap<String, Variant<*>> {
            val meta = HashMap<String, Variant<*>>()
            if (track == null) {
                // Plasma rejects metadata without a trackid, even when nothing is loaded.
                meta["mpris:trackid"] = Variant(DBusPath(NO_TRACK_PATH), "o")
                fallbackArtworkUri?.let { meta["mpris:artUrl"] = Variant(it, "s") }
                return meta
            }
            meta["mpris:trackid"] = Variant(DBusPath(trackObjectPath(track.id)), "o")
            meta["xesam:title"] = Variant(track.title ?: "", "s")
            val artist = track.displayArtist.ifBlank { track.user?.username.orEmpty() }
            if (artist.isNotBlank()) meta["xesam:artist"] = Variant(arrayOf(artist), "as")
            val albumArtist = track.publisherMetadata?.artist?.takeIf { it.isNotBlank() }
            if (!albumArtist.isNullOrBlank()) meta["xesam:albumArtist"] = Variant(arrayOf(albumArtist), "as")
            val album = track.publisherMetadata?.albumTitle ?: track.publisherMetadata?.releaseTitle
            if (!album.isNullOrBlank()) meta["xesam:album"] = Variant(album, "s")
            track.permalinkUrl?.takeIf { it.isNotBlank() }?.let { meta["xesam:url"] = Variant(it, "s") }
            // Local covers must remain literal paths: the SoundCloud size substitution in
            // fullResArtwork would also rename files/directories containing "large".
            val artwork = track.artworkUrl?.takeIf { File(it).isAbsolute || it.startsWith("file:") }
                ?: track.fullResArtwork
            (artworkUri(artwork) ?: fallbackArtworkUri)?.let { meta["mpris:artUrl"] = Variant(it, "s") }
            val effectiveDurationMs = durationMsOverride ?: track.effectiveFullDurationMs ?: track.durationMs
            effectiveDurationMs?.takeIf { it > 0 }?.let { meta["mpris:length"] = Variant(it * 1000L, "x") }
            return meta
        }
    }
}
