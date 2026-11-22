package com.alananasss.kittytune.data

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.ptr.PointerByReference
import java.io.File
import java.util.UUID

/**
 * Gives the media session's AppUserModelID something to resolve to.
 *
 * The Windows media flyout shows a session's owner by finding a shortcut carrying the same ID and
 * taking its name and icon; without one it shows "Unknown Application" (issue #66). An existing
 * KittyTune shortcut gets the ID added in place (and is repointed if a portable install moved); when
 * there is none, one is created in the Start Menu pointing at [launcher]. Dev runs (no launcher) only
 * patch what exists.
 *
 * Done in-process through the shell's COM objects: no PowerShell, no helper executable. Must run on a
 * thread that called `CoInitializeEx(COINIT_APARTMENTTHREADED)`.
 */
internal object WindowsShortcutIdentity {

    fun ensure(appUserModelId: String, launcher: File?) {
        val dirs = shortcutDirs()
        var found = false
        val named = dirs.flatMap { dir ->
            listOf("KittyTune.lnk", "Kitty Tune.lnk", "KittyTune/KittyTune.lnk", "Kitty Tune/Kitty Tune.lnk").map { File(dir, it) }
        }
        val all = dirs.flatMap { dir ->
            (dir.listFiles().orEmpty().toList() + dir.listFiles().orEmpty().filter { it.isDirectory }.flatMap { it.listFiles().orEmpty().toList() })
                .filter { it.isFile && it.extension.equals("lnk", true) }
        }.sortedByDescending { it.name.contains("Kitty", true) || it.name.contains("Tune", true) }.take(512)
        for (link in (named.filter { it.isFile } + all).distinctBy { it.absolutePath.lowercase() }) {
            if (patch(link, appUserModelId, launcher)) found = true
        }
        if (found || launcher == null) return
        val created = File(dirs.first(), "KittyTune.lnk")
        created.parentFile.mkdirs()
        create(created, appUserModelId, launcher)
    }

    private fun shortcutDirs(): List<File> {
        val appData = System.getenv("APPDATA")
        val programData = System.getenv("ProgramData")
        val home = System.getProperty("user.home")
        return listOfNotNull(
            appData?.let { File(it, "Microsoft/Windows/Start Menu/Programs") },
            programData?.let { File(it, "Microsoft/Windows/Start Menu/Programs") },
            appData?.let { File(it, "Microsoft/Internet Explorer/Quick Launch/User Pinned/TaskBar") },
            home?.let { File(it, "Desktop") },
            System.getenv("PUBLIC")?.let { File(it, "Desktop") },
        )
    }

    /** True when [file] is a KittyTune shortcut, now carrying [appUserModelId]. */
    private fun patch(file: File, appUserModelId: String, launcher: File?): Boolean {
        val link = createShellLink() ?: return false
        try {
            val persist = query(link, IID_PERSIST_FILE) ?: return false
            try {
                if (call(persist, 5, WString(file.absolutePath), STGM_READWRITE) < 0) return false // Load
                val target = targetOf(link)
                if (!target.contains("KittyTune", ignoreCase = true)) return false
                val hasId = readAppUserModelId(link).equals(appUserModelId, ignoreCase = true)
                val repoint = launcher != null && !File(target).isFile
                if (hasId && !repoint) return true
                if (repoint) {
                    call(link, 20, WString(launcher!!.absolutePath)) // SetPath
                    call(link, 17, WString(launcher.absolutePath), 0) // SetIconLocation
                }
                if (!writeAppUserModelId(link, appUserModelId)) return false
                return call(persist, 6, null, 1) >= 0 // Save(null, fRemember = TRUE)
            } finally { release(persist) }
        } catch (_: Throwable) {
            return false
        } finally { release(link) }
    }

    private fun create(file: File, appUserModelId: String, launcher: File) {
        val link = createShellLink() ?: return
        try {
            call(link, 20, WString(launcher.absolutePath)) // SetPath
            call(link, 7, WString("KittyTune")) // SetDescription
            call(link, 17, WString(launcher.absolutePath), 0) // SetIconLocation
            writeAppUserModelId(link, appUserModelId)
            val persist = query(link, IID_PERSIST_FILE) ?: return
            try { call(persist, 6, WString(file.absolutePath), 0) } finally { release(persist) } // Save
        } finally { release(link) }
    }

    private fun targetOf(link: Pointer): String {
        val buffer = Memory(2L * 1024)
        buffer.clear()
        val findData = Memory(640).apply { clear() }
        call(link, 3, buffer, 1024, findData, 0) // GetPath
        return buffer.getWideString(0)
    }

    private fun readAppUserModelId(link: Pointer): String? {
        val store = query(link, IID_PROPERTY_STORE) ?: return null
        val value = Memory(24).apply { clear() }
        try {
            if (call(store, 5, PKEY_APP_USER_MODEL_ID, value) < 0) return null // GetValue
            if (value.getShort(0).toInt() != VT_LPWSTR) return null
            return value.getPointer(8)?.getWideString(0)
        } finally {
            runCatching { ole32Ext.PropVariantClear(value) }
            release(store)
        }
    }

    private fun writeAppUserModelId(link: Pointer, appUserModelId: String): Boolean {
        val store = query(link, IID_PROPERTY_STORE) ?: return false
        val text = Memory((appUserModelId.length + 1) * 2L).apply { setWideString(0, appUserModelId) }
        val value = Memory(24).apply {
            clear()
            setShort(0, VT_LPWSTR.toShort())
            setPointer(8, text)
        }
        try {
            // SetValue copies the value, so the memory here stays ours.
            return call(store, 6, PKEY_APP_USER_MODEL_ID, value) >= 0 && call(store, 7) >= 0 // SetValue, Commit
        } finally { release(store) }
    }

    private fun createShellLink(): Pointer? {
        val out = PointerByReference()
        val hr = Ole32.INSTANCE.CoCreateInstance(
            com.sun.jna.platform.win32.Guid.GUID(CLSID_SHELL_LINK), null, CLSCTX_INPROC_SERVER,
            com.sun.jna.platform.win32.Guid.GUID(IID_SHELL_LINK_W), out,
        )
        return if (hr.toInt() < 0) null else out.value
    }

    private interface Ole32Ext : com.sun.jna.Library {
        @Suppress("FunctionName")
        fun PropVariantClear(value: Pointer): Int
    }

    private val ole32Ext: Ole32Ext by lazy { Native.load("ole32", Ole32Ext::class.java) }

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

    private fun query(obj: Pointer, iid: Memory): Pointer? {
        val out = PointerByReference()
        return if (call(obj, 0, iid, out) < 0) null else out.value
    }

    private fun call(obj: Pointer, slot: Int, vararg args: Any?): Int =
        Function.getFunction(obj.getPointer(0).getPointer(slot.toLong() * Native.POINTER_SIZE)).invokeInt(arrayOf(obj, *args))

    private fun release(obj: Pointer) {
        runCatching { call(obj, 2) }
    }

    private const val CLSCTX_INPROC_SERVER = 1
    private const val STGM_READWRITE = 2
    private const val VT_LPWSTR = 31
    private const val CLSID_SHELL_LINK = "{00021401-0000-0000-C000-000000000046}"
    private const val IID_SHELL_LINK_W = "{000214F9-0000-0000-C000-000000000046}"
    private val IID_PERSIST_FILE = guid("0000010b-0000-0000-c000-000000000046")
    private val IID_PROPERTY_STORE = guid("886d8eeb-8cf2-4446-8d02-cdba1dbdcf99")

    /** PKEY_AppUserModel_ID: {9F4C2855-9F79-4B39-A8D0-E1D42DE1D5F3}, 5. */
    private val PKEY_APP_USER_MODEL_ID = Memory(20).apply {
        write(0, guid("9f4c2855-9f79-4b39-a8d0-e1d42de1d5f3").getByteArray(0, 16), 0, 16)
        setInt(16, 5)
    }
}
