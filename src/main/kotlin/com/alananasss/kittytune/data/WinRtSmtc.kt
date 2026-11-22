package com.alananasss.kittytune.data

import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference
import java.io.Closeable
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * System Media Transport Controls, driven from inside the app's own process through the WinRT ABI.
 *
 * KittyTune used to start a small helper executable for this, and antivirus engines flagged that
 * unsigned helper (`Trojan:Win32/Wacatac.B!ml`, issue #145). There is no helper any more: the session is
 * created here, through `combase.dll` with JNA.
 *
 * The session belongs to a hidden window (`ISystemMediaTransportControlsInterop::GetForWindow`) that
 * carries [appUserModelId]. That is how the media flyout names it "KittyTune" with the app's icon,
 * while the process itself keeps no explicit ID (the taskbar needs that to follow the chosen icon).
 * The window is never shown and has no taskbar button. Its thread owns every WinRT call: [update] and
 * [close] hand their work to it.
 *
 * Every vtable slot below is the interface's method index (IInspectable's six come first), checked
 * against the Windows SDK headers and the Windows metadata.
 */
internal class WinRtSmtc(
    private val appUserModelId: String,
    private val launcher: File?,
    private val onButton: (Button) -> Unit,
    private val onSeek: (Long) -> Unit,
) : Closeable {

    enum class Button { PLAY, PAUSE, STOP, NEXT, PREVIOUS, OTHER }

    @Suppress("FunctionName")
    private interface Combase : Library {
        fun RoActivateInstance(classId: Pointer, instance: PointerByReference): Int
        fun RoGetActivationFactory(classId: Pointer, iid: Pointer, factory: PointerByReference): Int
        fun WindowsCreateString(source: WString, length: Int, string: PointerByReference): Int
        fun WindowsDeleteString(string: Pointer?): Int
    }

    @Suppress("FunctionName")
    private interface Shell32Ext : Library {
        fun SHGetPropertyStoreForWindow(hwnd: Pointer, iid: Pointer, store: PointerByReference): Int
    }

    @Suppress("FunctionName")
    private interface Shlwapi : Library {
        fun SHCreateStreamOnFileEx(file: WString, mode: Int, attributes: Int, create: Boolean, template: Pointer?, stream: PointerByReference): Int
    }

    @Suppress("FunctionName")
    private interface Shcore : Library {
        fun CreateRandomAccessStreamOverStream(stream: Pointer, options: Int, iid: Pointer, out: PointerByReference): Int
    }

    private val combase: Combase = Native.load("combase", Combase::class.java)
    private val shlwapi: Shlwapi = Native.load("shlwapi", Shlwapi::class.java)
    private val shcore: Shcore = Native.load("shcore", Shcore::class.java)
    private val shell32: Shell32Ext = Native.load("shell32", Shell32Ext::class.java)

    // Only the window thread touches these.
    private var hwnd: WinDef.HWND? = null
    private var smtc: Pointer? = null
    private var smtc2: Pointer? = null
    private var buttonToken = 0L
    private var seekToken = 0L
    private val buttonHandler = Delegate(IID_BUTTON_HANDLER) { _, args -> onButtonPressed(args) }
    private val seekHandler = Delegate(IID_SEEK_HANDLER) { _, args -> onSeekRequested(args) }
    private var lastMetadata: List<String>? = null
    private var lastArtwork: String? = null
    private var lastStatus = -1
    private var lastRate = Double.NaN

    private val tasks = LinkedBlockingQueue<Runnable>()
    @Volatile private var threadId = 0
    @Volatile private var closed = false
    private val thread: Thread

    init {
        val ready = CompletableFuture<Unit>()
        thread = Thread({ windowThread(ready) }, "WindowsSmtc-Window").apply {
            isDaemon = true
            start()
        }
        try {
            ready.get(15, TimeUnit.SECONDS)
        } catch (e: ExecutionException) {
            close()
            throw e.cause ?: e
        } catch (e: Throwable) {
            close()
            throw e
        }
    }

    private fun windowThread(ready: CompletableFuture<Unit>) {
        Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED)
        try {
            try {
                threadId = Kernel32.INSTANCE.GetCurrentThreadId()
                // Before the session exists: the flyout resolves the owner's identity when it appears.
                runCatching { WindowsShortcutIdentity.ensure(appUserModelId, launcher) }
                    .onFailure { println("Windows SMTC: shortcut identity: ${it.message}") }
                open()
                ready.complete(Unit)
            } catch (t: Throwable) {
                ready.completeExceptionally(t)
                return
            }
            val msg = WinUser.MSG()
            while (User32.INSTANCE.GetMessage(msg, null, 0, 0) > 0) {
                if (msg.hWnd == null && msg.message == WM_RUN_TASKS) {
                    runPendingTasks()
                    continue
                }
                User32.INSTANCE.TranslateMessage(msg)
                User32.INSTANCE.DispatchMessage(msg)
            }
            runPendingTasks()
        } finally {
            teardown()
            Ole32.INSTANCE.CoUninitialize()
        }
    }

    private fun runPendingTasks() {
        while (true) (tasks.poll() ?: return).run()
    }

    private fun open() {
        val window = User32.INSTANCE.CreateWindowEx(
            WS_EX_TOOLWINDOW or WS_EX_NOACTIVATE, "STATIC", "KittyTune", WS_POPUP,
            0, 0, 0, 0, null, null, null, null,
        ) ?: throw IllegalStateException("CreateWindowEx failed: ${Kernel32.INSTANCE.GetLastError()}")
        hwnd = window
        setWindowAppUserModelId(window)

        val interop = factory("Windows.Media.SystemMediaTransportControls", IID_SMTC_INTEROP)
        val controls = try {
            val out = PointerByReference()
            check(call(interop, 6, window.pointer, IID_SMTC, out), "GetForWindow")
            out.value ?: throw IllegalStateException("GetForWindow returned null")
        } finally { release(interop) }
        smtc = controls
        smtc2 = query(controls, IID_SMTC2)

        check(call(controls, 11, 1), "IsEnabled")
        check(call(controls, 13, 1), "IsPlayEnabled")
        check(call(controls, 15, 1), "IsStopEnabled")
        check(call(controls, 17, 1), "IsPauseEnabled")
        check(call(controls, 25, 1), "IsPreviousEnabled")
        check(call(controls, 27, 1), "IsNextEnabled")

        val token = LongByReference()
        check(call(controls, 32, buttonHandler.pointer, token), "add_ButtonPressed")
        buttonToken = token.value
        check(call(smtc2!!, 13, seekHandler.pointer, token), "add_PlaybackPositionChangeRequested")
        seekToken = token.value
    }

    /** PKEY_AppUserModel_ID on the hidden window: the session's identity, and only the session's. */
    private fun setWindowAppUserModelId(window: WinDef.HWND) {
        val out = PointerByReference()
        check(shell32.SHGetPropertyStoreForWindow(window.pointer, IID_PROPERTY_STORE, out), "SHGetPropertyStoreForWindow")
        val store = out.value
        try {
            val text = Memory((appUserModelId.length + 1) * 2L).apply { setWideString(0, appUserModelId) }
            val value = Memory(24).apply {
                clear()
                setShort(0, VT_LPWSTR.toShort())
                setPointer(8, text)
            }
            check(call(store, 6, PKEY_APP_USER_MODEL_ID, value), "SetValue(AppUserModel.ID)") // copies the value
            call(store, 7) // Commit
        } finally { release(store) }
    }

    /** Runs [block] on the window thread and waits for it. */
    private fun <T> onWindowThread(block: () -> T): T? {
        if (closed) return null
        if (Thread.currentThread() === thread) return block()
        val result = CompletableFuture<T>()
        tasks.add(Runnable { runCatching(block).fold({ result.complete(it) }, { result.completeExceptionally(it) }) })
        User32.INSTANCE.PostThreadMessage(threadId, WM_RUN_TASKS, WinDef.WPARAM(0), WinDef.LPARAM(0))
        return try {
            result.get(10, TimeUnit.SECONDS)
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }

    /** Publishes the track, the play state and the timeline. Unchanged parts are not sent again. */
    fun update(
        title: String,
        artist: String,
        album: String,
        artworkUrl: String,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        playbackRate: Double,
    ) {
        onWindowThread { publish(title, artist, album, artworkUrl, isPlaying, positionMs, durationMs, playbackRate) }
    }

    private fun publish(
        title: String,
        artist: String,
        album: String,
        artworkUrl: String,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        playbackRate: Double,
    ) {
        val controls = smtc ?: return
        val metadata = listOf(title, artist, album, artworkUrl)
        if (metadata != lastMetadata) {
            val updater = getPointer(controls, 8) // get_DisplayUpdater
            try {
                check(call(updater, 7, MEDIA_PLAYBACK_TYPE_MUSIC), "DisplayUpdater.Type")
                val music = getPointer(updater, 12) // get_MusicProperties
                try {
                    withHString(title) { check(call(music, 7, it), "Title") }
                    withHString(artist) { check(call(music, 11, it), "Artist") }
                    val music2 = query(music, IID_MUSIC_PROPERTIES2)
                    try { withHString(album) { check(call(music2, 7, it), "AlbumTitle") } } finally { release(music2) }
                } finally { release(music) }
                if (artworkUrl != lastArtwork) {
                    val thumbnail = thumbnailFor(artworkUrl)
                    try { check(call(updater, 11, thumbnail), "Thumbnail") } finally { thumbnail?.let(::release) }
                    lastArtwork = artworkUrl
                }
                check(call(updater, 17), "DisplayUpdater.Update")
            } finally { release(updater) }
            lastMetadata = metadata
        }

        val rate = playbackRate.takeIf { it > 0 && it.isFinite() } ?: 1.0
        if (rate != lastRate) {
            check(call(smtc2!!, 11, rate), "PlaybackRate")
            lastRate = rate
        }
        val status = if (isPlaying) MEDIA_PLAYBACK_STATUS_PLAYING else MEDIA_PLAYBACK_STATUS_PAUSED
        if (status != lastStatus) {
            check(call(controls, 7, status), "PlaybackStatus")
            lastStatus = status
        }

        val timeline = activate("Windows.Media.SystemMediaTransportControlsTimelineProperties")
        try {
            val props = query(timeline, IID_TIMELINE_PROPERTIES)
            try {
                if (durationMs > 0) {
                    val end = durationMs * TICKS_PER_MS
                    check(call(props, 7, 0L), "StartTime")
                    check(call(props, 9, end), "EndTime")
                    check(call(props, 11, 0L), "MinSeekTime")
                    check(call(props, 13, end), "MaxSeekTime")
                    check(call(props, 15, positionMs.coerceIn(0L, durationMs) * TICKS_PER_MS), "Position")
                }
                check(call(smtc2!!, 12, props), "UpdateTimelineProperties")
            } finally { release(props) }
        } finally { release(timeline) }
    }

    /**
     * The cover. A web address is handed to Windows, which fetches it; a file on disk (a local track's
     * cover, a cached one) is opened here and handed over as a stream, since `CreateFromUri` takes no
     * `file:` address.
     */
    private fun thumbnailFor(artwork: String): Pointer? {
        if (artwork.isBlank()) return null
        if (artwork.startsWith("http://", true) || artwork.startsWith("https://", true)) return thumbnailFromUri(artwork)
        val file = runCatching {
            if (artwork.startsWith("file:", true)) File(java.net.URI(artwork)) else File(artwork)
        }.getOrNull()?.takeIf { it.isFile } ?: return null
        return thumbnailFromFile(file)
    }

    private fun thumbnailFromUri(text: String): Pointer? {
        val uriFactory = factory("Windows.Foundation.Uri", IID_URI_FACTORY)
        val uri = try {
            withHString(text) { value ->
                val out = PointerByReference()
                if (call(uriFactory, 6, value, out) < 0) null else out.value
            }
        } finally { release(uriFactory) } ?: return null
        return try {
            val statics = factory("Windows.Storage.Streams.RandomAccessStreamReference", IID_STREAM_REFERENCE_STATICS)
            try {
                val out = PointerByReference()
                if (call(statics, 7, uri, out) < 0) null else out.value // CreateFromUri
            } finally { release(statics) }
        } finally { release(uri) }
    }

    private fun thumbnailFromFile(file: File): Pointer? {
        val stream = PointerByReference()
        if (shlwapi.SHCreateStreamOnFileEx(WString(file.absolutePath), STGM_READ or STGM_SHARE_DENY_NONE, 0, false, null, stream) < 0) return null
        try {
            val randomAccess = PointerByReference()
            if (shcore.CreateRandomAccessStreamOverStream(stream.value, BSOS_DEFAULT, IID_RANDOM_ACCESS_STREAM, randomAccess) < 0) return null
            try {
                val statics = factory("Windows.Storage.Streams.RandomAccessStreamReference", IID_STREAM_REFERENCE_STATICS)
                try {
                    val out = PointerByReference()
                    return if (call(statics, 8, randomAccess.value, out) < 0) null else out.value // CreateFromStream
                } finally { release(statics) }
            } finally { release(randomAccess.value) }
        } finally { release(stream.value) }
    }

    private fun onButtonPressed(args: Pointer?) {
        args ?: return
        val button = IntByReference()
        if (call(args, 6, button) < 0) return
        onButton(
            when (button.value) {
                0 -> Button.PLAY
                1 -> Button.PAUSE
                2 -> Button.STOP
                6 -> Button.NEXT
                7 -> Button.PREVIOUS
                else -> Button.OTHER
            },
        )
    }

    private fun onSeekRequested(args: Pointer?) {
        args ?: return
        val position = LongByReference()
        if (call(args, 6, position) < 0) return
        onSeek(position.value / TICKS_PER_MS)
    }

    override fun close() {
        if (closed) return
        closed = true
        if (threadId != 0) User32.INSTANCE.PostThreadMessage(threadId, WM_QUIT, WinDef.WPARAM(0), WinDef.LPARAM(0))
        if (Thread.currentThread() !== thread) runCatching { thread.join(3000) }
    }

    /** On the window thread, as its loop ends: the flyout entry goes away with the window. */
    private fun teardown() {
        runCatching { smtc?.let { if (buttonToken != 0L) call(it, 33, buttonToken) } }
        runCatching { smtc2?.let { if (seekToken != 0L) call(it, 14, seekToken) } }
        runCatching { smtc?.let { call(it, 11, 0) } } // IsEnabled = false
        smtc2?.let(::release)
        smtc?.let(::release)
        smtc2 = null
        smtc = null
        hwnd?.let { User32.INSTANCE.DestroyWindow(it) }
        hwnd = null
    }

    // ── WinRT plumbing ──────────────────────────────────────────────────────────────────────────

    private fun activate(className: String): Pointer = withHString(className) { name ->
        val out = PointerByReference()
        check(combase.RoActivateInstance(name!!, out), "activate $className")
        out.value
    }

    private fun factory(className: String, iid: Memory): Pointer = withHString(className) { name ->
        val out = PointerByReference()
        check(combase.RoGetActivationFactory(name!!, iid, out), "factory $className")
        out.value
    }

    private fun <T> withHString(text: String, block: (Pointer?) -> T): T {
        if (text.isEmpty()) return block(null) // the null HSTRING is the empty string
        val out = PointerByReference()
        check(combase.WindowsCreateString(WString(text), text.length, out), "WindowsCreateString")
        try { return block(out.value) } finally { combase.WindowsDeleteString(out.value) }
    }

    private interface QueryInterfaceFn : Callback {
        fun invoke(self: Pointer, riid: Pointer, out: Pointer): Int
    }

    private interface RefCountFn : Callback {
        fun invoke(self: Pointer): Int
    }

    private interface InvokeFn : Callback {
        fun invoke(self: Pointer, sender: Pointer?, args: Pointer?): Int
    }

    /** A TypedEventHandler implemented in Kotlin: IUnknown plus Invoke, nothing else. */
    private class Delegate(private val iid: Memory, private val onInvoke: (Pointer?, Pointer?) -> Unit) {
        // Strong references: the functions must outlive every call WinRT can still make into them.
        private val queryInterface = object : QueryInterfaceFn {
            override fun invoke(self: Pointer, riid: Pointer, out: Pointer): Int {
                val wanted = riid.getByteArray(0, 16)
                return if (wanted.contentEquals(iid.getByteArray(0, 16)) ||
                    wanted.contentEquals(IID_UNKNOWN.getByteArray(0, 16)) ||
                    wanted.contentEquals(IID_AGILE_OBJECT.getByteArray(0, 16))
                ) {
                    out.setPointer(0, self)
                    0
                } else {
                    out.setPointer(0, null)
                    E_NOINTERFACE
                }
            }
        }
        // Owned by this object for the app's lifetime: the count only has to look plausible.
        private val addRef = object : RefCountFn {
            override fun invoke(self: Pointer): Int = 1
        }
        private val release = object : RefCountFn {
            override fun invoke(self: Pointer): Int = 1
        }
        private val invoke = object : InvokeFn {
            override fun invoke(self: Pointer, sender: Pointer?, args: Pointer?): Int {
                runCatching { onInvoke(sender, args) }
                return 0
            }
        }
        private val vtable = Memory(4L * Native.POINTER_SIZE).apply {
            listOf<Callback>(queryInterface, addRef, release, invoke).forEachIndexed { i, cb ->
                setPointer(i.toLong() * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(cb))
            }
        }
        val pointer = Memory(Native.POINTER_SIZE.toLong()).apply { setPointer(0, vtable) }
    }

    companion object {
        private const val WM_QUIT = 0x0012
        private const val WM_RUN_TASKS = 0x8000 + 0x4B // WM_APP + 'K'
        private const val WS_POPUP = 0x80000000.toInt()
        private const val WS_EX_TOOLWINDOW = 0x00000080
        private const val WS_EX_NOACTIVATE = 0x08000000
        private const val VT_LPWSTR = 31
        private const val STGM_READ = 0x0
        private const val STGM_SHARE_DENY_NONE = 0x40
        private const val BSOS_DEFAULT = 0
        private const val E_NOINTERFACE = 0x80004002.toInt()
        private const val MEDIA_PLAYBACK_TYPE_MUSIC = 1
        private const val MEDIA_PLAYBACK_STATUS_PLAYING = 3
        private const val MEDIA_PLAYBACK_STATUS_PAUSED = 4
        /** Windows.Foundation.TimeSpan counts 100 ns ticks. */
        private const val TICKS_PER_MS = 10_000L

        private fun guid(text: String): Memory {
            val uuid = UUID.fromString(text)
            val msb = uuid.mostSignificantBits
            val lsb = uuid.leastSignificantBits
            return Memory(16).apply {
                setInt(0, (msb ushr 32).toInt())
                setShort(4, (msb ushr 16).toShort())
                setShort(6, msb.toShort())
                for (i in 0 until 8) setByte(8L + i, (lsb ushr (56 - 8 * i)).toByte())
            }
        }

        private val IID_UNKNOWN = guid("00000000-0000-0000-c000-000000000046")
        private val IID_AGILE_OBJECT = guid("94ea2b94-e9cc-49e0-c0ff-ee64ca8f5b90")
        private val IID_SMTC = guid("99fa3ff4-1742-42a6-902e-087d41f965ec")
        private val IID_SMTC2 = guid("ea98d2f6-7f3c-4af2-a586-72889808efb1")
        private val IID_SMTC_INTEROP = guid("ddb0472d-c911-4a1f-86d9-dc3d71a95f5a")
        private val IID_PROPERTY_STORE = guid("886d8eeb-8cf2-4446-8d02-cdba1dbdcf99")
        private val IID_MUSIC_PROPERTIES2 = guid("00368462-97d3-44b9-b00f-008afcefaf18")
        private val IID_TIMELINE_PROPERTIES = guid("5125316a-c3a2-475b-8507-93534dc88f15")
        private val IID_URI_FACTORY = guid("44a9796f-723e-4fdf-a218-033e75b0c084")
        private val IID_STREAM_REFERENCE_STATICS = guid("857309dc-3fbf-4e7d-986f-ef3b1a07a964")
        private val IID_RANDOM_ACCESS_STREAM = guid("905a0fe1-bc53-11df-8c49-001e4fc686da")
        private val IID_BUTTON_HANDLER = guid("0557e996-7b23-5bae-aa81-ea0d671143a4")
        private val IID_SEEK_HANDLER = guid("44e34f15-bdc0-50a7-ace4-39e91fb753f1")

        /** PKEY_AppUserModel_ID: {9F4C2855-9F79-4B39-A8D0-E1D42DE1D5F3}, 5. */
        private val PKEY_APP_USER_MODEL_ID = Memory(20).apply {
            write(0, guid("9f4c2855-9f79-4b39-a8d0-e1d42de1d5f3").getByteArray(0, 16), 0, 16)
            setInt(16, 5)
        }

        private fun function(obj: Pointer, slot: Int): Function =
            Function.getFunction(obj.getPointer(0).getPointer(slot.toLong() * Native.POINTER_SIZE))

        /** Calls vtable [slot] of [obj] and returns its HRESULT. */
        private fun call(obj: Pointer, slot: Int, vararg args: Any?): Int =
            function(obj, slot).invokeInt(arrayOf(obj, *args))

        private fun getPointer(obj: Pointer, slot: Int): Pointer {
            val out = PointerByReference()
            check(call(obj, slot, out), "slot $slot")
            return out.value ?: throw IllegalStateException("slot $slot returned null")
        }

        private fun query(obj: Pointer, iid: Memory): Pointer {
            val out = PointerByReference()
            check(call(obj, 0, iid, out), "QueryInterface")
            return out.value
        }

        private fun release(obj: Pointer) {
            runCatching { call(obj, 2) }
        }

        private fun check(hr: Int, what: String) {
            if (hr < 0) throw IllegalStateException("$what failed: 0x${Integer.toHexString(hr)}")
        }
    }
}
