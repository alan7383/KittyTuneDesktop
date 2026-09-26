package com.alananasss.kittytune.core

import com.alananasss.kittytune.utils.Logger
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.WTypes
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.awt.Toolkit
import java.io.File

/**
 * "Open file" through the dialog the rest of Windows uses.
 *
 * AWT's [FileDialog] calls the legacy `GetOpenFileName` with a hook, and a hooked dialog is drawn in its old
 * style: no navigation pane, no breadcrumb bar, no search — the "cut-down Explorer" the icon picker was reported
 * for (round 5). `IFileOpenDialog` is the one Explorer itself opens, so on Windows the pickers go through it and
 * fall back to AWT only when COM refuses. Elsewhere AWT's dialog is already the platform's own.
 *
 * The dialog runs on a thread of its own inside a secondary loop, the way AWT runs its own: the caller still gets
 * an answer synchronously, and the app's window keeps painting behind the dialog instead of going white.
 */
object NativeFileDialog {

    /** A file type the dialog offers: its name, and the extensions it covers without dots. */
    data class FileType(val name: String, val extensions: List<String>)

    /** The chosen file, or null when the dialog was dismissed. */
    fun openFile(title: String, type: FileType): File? {
        if (isWindows) {
            val owner = ownerHandle()
            val answer = runOffEventThread { WindowsOpenDialog.show(title, type, owner) }
            answer.onSuccess { return it }
            Logger.e("NativeFileDialog", "IFileOpenDialog failed, falling back to AWT: ${answer.exceptionOrNull()}")
        }
        return awtOpenFile(title, type)
    }

    private val isWindows = System.getProperty("os.name").lowercase().contains("win")

    private fun awtOpenFile(title: String, type: FileType): File? {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        dialog.file = type.extensions.joinToString(";") { "*.$it" }
        dialog.setFilenameFilter { _, name -> type.extensions.any { name.endsWith(".$it", ignoreCase = true) } }
        dialog.isVisible = true
        return dialog.files.firstOrNull()
    }

    private fun ownerHandle(): WinDef.HWND? {
        val window = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
            ?: java.awt.Window.getWindows().firstOrNull { it.isShowing }
            ?: return null
        return com.alananasss.kittytune.data.theme.WindowsFullScreen.handleOf(window)
    }

    /** Runs [block] on a fresh thread; if called on the event thread, keeps AWT's events flowing meanwhile. */
    private fun <T> runOffEventThread(block: () -> T): Result<T> {
        val result = java.util.concurrent.atomic.AtomicReference<Result<T>>()
        val onEventThread = java.awt.EventQueue.isDispatchThread()
        val loop = if (onEventThread) Toolkit.getDefaultToolkit().systemEventQueue.createSecondaryLoop() else null
        val worker = Thread({
            result.set(runCatching(block))
            loop?.exit()
        }, "native-file-dialog")
        worker.isDaemon = true
        worker.start()
        // Pumps AWT events until the worker exits the loop; returns at once if it already has.
        loop?.enter()
        worker.join()
        return result.get()
    }
}

/** The COM side: `IFileOpenDialog` and the `IShellItem` it answers with, by their vtable slots. */
private object WindowsOpenDialog {
    private val CLSID_FileOpenDialog = Guid.GUID("{DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7}")
    private val IID_IFileOpenDialog = Guid.GUID("{D57C7288-D4AD-4768-BE02-9D969532D960}")

    // IFileOpenDialog: IUnknown 0–2, IModalWindow::Show 3, then IFileDialog in declaration order.
    private const val SHOW = 3
    private const val SET_FILE_TYPES = 4
    private const val SET_OPTIONS = 9
    private const val GET_OPTIONS = 10
    private const val SET_TITLE = 17
    private const val GET_RESULT = 20

    // IShellItem::GetDisplayName.
    private const val GET_DISPLAY_NAME = 5
    private const val SIGDN_FILESYSPATH = 0x80058000.toInt()

    private const val FOS_FORCEFILESYSTEM = 0x40
    private const val FOS_PATHMUSTEXIST = 0x800
    private const val FOS_FILEMUSTEXIST = 0x1000

    /** HRESULT_FROM_WIN32(ERROR_CANCELLED): the reader closed the dialog. */
    private const val HR_CANCELLED = 0x800704C7.toInt()

    private class ComObject(pointer: Pointer) : Unknown(pointer) {
        fun call(slot: Int, vararg args: Any?): Int = _invokeNativeInt(slot, arrayOf(pointer, *args))
    }

    @Structure.FieldOrder("pszName", "pszSpec")
    class FilterSpec : Structure() {
        @JvmField var pszName: WString? = null
        @JvmField var pszSpec: WString? = null
    }

    fun show(title: String, type: NativeFileDialog.FileType, owner: WinDef.HWND?): File? {
        val coInit = Ole32.INSTANCE.CoInitializeEx(Pointer.NULL, Ole32.COINIT_APARTMENTTHREADED).toInt()
        check(coInit == 0 || coInit == 1) { "CoInitializeEx: 0x%08X".format(coInit) }
        try {
            val created = PointerByReference()
            checkHr(
                Ole32.INSTANCE.CoCreateInstance(
                    CLSID_FileOpenDialog, Pointer.NULL, WTypes.CLSCTX_INPROC_SERVER, IID_IFileOpenDialog, created
                ).toInt(),
                "CoCreateInstance"
            )
            val dialog = ComObject(created.value)
            try {
                configure(dialog, title, type)
                val shown = dialog.call(SHOW, owner?.pointer)
                if (shown == HR_CANCELLED) return null
                checkHr(shown, "Show")

                val item = PointerByReference()
                checkHr(dialog.call(GET_RESULT, item), "GetResult")
                return ComObject(item.value).useAndRelease { it.filesystemPath() }?.let(::File)
            } finally {
                dialog.Release()
            }
        } finally {
            Ole32.INSTANCE.CoUninitialize()
        }
    }

    private fun configure(dialog: ComObject, title: String, type: NativeFileDialog.FileType) {
        val specs = FilterSpec().toArray(1).map { it as FilterSpec }
        specs[0].pszName = WString(type.name)
        specs[0].pszSpec = WString(type.extensions.joinToString(";") { "*.$it" })
        specs.forEach { it.write() }
        checkHr(dialog.call(SET_FILE_TYPES, specs.size, specs[0].pointer), "SetFileTypes")

        val options = IntByReference()
        checkHr(dialog.call(GET_OPTIONS, options), "GetOptions")
        val wanted = options.value or FOS_FORCEFILESYSTEM or FOS_PATHMUSTEXIST or FOS_FILEMUSTEXIST
        checkHr(dialog.call(SET_OPTIONS, wanted), "SetOptions")
        checkHr(dialog.call(SET_TITLE, WString(title)), "SetTitle")
    }

    private fun ComObject.filesystemPath(): String? {
        val name = PointerByReference()
        checkHr(call(GET_DISPLAY_NAME, SIGDN_FILESYSPATH, name), "GetDisplayName")
        val text = name.value ?: return null
        return try {
            text.getWideString(0)
        } finally {
            Ole32.INSTANCE.CoTaskMemFree(text)
        }
    }

    private inline fun <T> ComObject.useAndRelease(block: (ComObject) -> T): T =
        try { block(this) } finally { Release() }

    private fun checkHr(hr: Int, what: String) {
        check(hr >= 0) { "$what: 0x%08X".format(hr) }
    }
}
