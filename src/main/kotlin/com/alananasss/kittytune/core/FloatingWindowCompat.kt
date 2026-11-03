package com.alananasss.kittytune.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sun.jna.Native
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.Tlhelp32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import org.jetbrains.skiko.GraphicsApi
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.geom.RoundRectangle2D
import java.io.File

/**
 * Compatibility mode for KittyTune's own floating windows (Kitty pet, mini player and its settings, floating
 * menus): opaque, their rounded corners cut by the window's shape, and drawn in software.
 *
 * Why: with RivaTuner Statistics Server (RTSS, MSI Afterburner's overlay) hooking the app, showing a
 * *transparent* window froze the whole process on the spot — whether Skiko drew it with Direct3D or in
 * software through a layered window. Opaque windows are untouched by that. Drawn in software they also
 * keep RTSS from attaching its overlay to them, which otherwise made the overlay flicker on the main window
 * every time the mini player repainted.
 *
 * On when, in this order:
 * - the Java option `-Dkittytune.floating.opaque=true|false` says so (testing, support);
 * - the user turned it on or off in the mini player's settings;
 * - otherwise (auto): RTSS is running without an exclusion profile, or the app was killed while a floating
 *   window was opening — which is what a user does when that froze it, so the next launch does not freeze again.
 *
 * Only Windows; elsewhere the windows stay transparent.
 */
object FloatingWindowCompat {
    enum class Mode { AUTO, ON, OFF }

    private const val KEY_MODE = "floating_windows_compat"
    private const val KEY_OPENING = "floating_window_opening"
    private const val KEY_TRIPPED = "floating_windows_compat_tripped"

    private val isWindows = System.getProperty("os.name").lowercase().contains("win")
    private val forced: Boolean? = System.getProperty("kittytune.floating.opaque")?.toBooleanStrictOrNull()

    private val RTSS_DIRS = listOf(
        File("C:\\Program Files (x86)\\RivaTuner Statistics Server"),
        File("C:\\Program Files\\RivaTuner Statistics Server"),
    )

    /** What transparent windows are drawn with on Windows; see [TransparentWindowRendering]. */
    val transparentRenderApi: GraphicsApi = System.getProperty("kittytune.floating.renderApi")
        ?.let { name -> GraphicsApi.entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) } }
        ?: GraphicsApi.SOFTWARE_COMPAT

    /** The reason auto mode is on, or null. Fixed at startup. */
    var autoReason: String? = null
        private set

    /** The same reason as a string key for the settings, or null. */
    var autoReasonKey: String? = null
        private set

    /** Whether the Java option decides, in which case the setting cannot. */
    val isForced: Boolean get() = forced != null

    /** Only Windows has the problem, so only Windows shows the setting. */
    val isAvailable: Boolean get() = isWindows

    var mode by mutableStateOf(readMode())
        private set

    /** Whether the floating windows are opaque right now. Read by them when they are created. */
    val opaque: Boolean
        get() = isWindows && (forced ?: when (mode) {
            Mode.ON -> true
            Mode.OFF -> false
            Mode.AUTO -> autoReason != null
        })

    fun changeMode(value: Mode) {
        mode = value
        Prefs.putString(KEY_MODE, value.name)
    }

    /** Call once at startup, before any window. */
    fun init() {
        if (isWindows) {
            // Still set from last time: that session ended while a floating window was opening.
            if (Prefs.getBoolean(KEY_OPENING, false)) {
                Prefs.putBoolean(KEY_OPENING, false)
                Prefs.putBoolean(KEY_TRIPPED, true)
                // Turned off by hand and it froze again: back to auto, or every launch would freeze.
                if (mode == Mode.OFF) changeMode(Mode.AUTO)
                Prefs.flush(force = true)
            }
            when {
                isRtssRunning() -> {
                    if (!isRtssExempted()) {
                        autoReason = "RivaTuner Statistics Server is running without exemption"
                        autoReasonKey = "floating_compat_auto_rtss"
                    }
                }
                Prefs.getBoolean(KEY_TRIPPED, false) -> {
                    autoReason = "a floating window froze the app before"
                    autoReasonKey = "floating_compat_auto_freeze"
                }
            }
        }
    }

    /**
     * Brackets the opening of a transparent floating window: [opening] is written to disk before the window
     * is shown, [opened] clears it once the window has been up for a moment. A freeze in between leaves it
     * set, and [init] turns compatibility mode on at the next launch.
     */
    fun opening() {
        if (!isWindows || opaque) return
        Prefs.putBoolean(KEY_OPENING, true)
        Prefs.flush(force = true)
    }

    fun opened() {
        if (!Prefs.getBoolean(KEY_OPENING, false)) return
        Prefs.putBoolean(KEY_OPENING, false)
        Prefs.flush(force = true)
    }

    /**
     * Keeps [window] cut to a rounded rectangle of corner radius [radius], inset by [inset] on every side
     * (both in the window's own units), following its size. Returns a function that removes the listener.
     */
    fun keepRoundedShape(window: Window, inset: Double = 0.0, radius: () -> Double): () -> Unit {
        fun apply() {
            runCatching {
                val r = radius()
                window.shape = RoundRectangle2D.Double(
                    inset, inset,
                    window.width - inset * 2, window.height - inset * 2,
                    r * 2, r * 2,
                )
            }
        }
        val listener = object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) = apply()
            override fun componentShown(e: ComponentEvent?) = apply()
        }
        window.addComponentListener(listener)
        apply()
        return { window.removeComponentListener(listener) }
    }

    private fun readMode(): Mode =
        Prefs.getString(KEY_MODE, null)?.let { saved -> Mode.entries.firstOrNull { it.name == saved } } ?: Mode.AUTO

    /**
     * Checks if RTSS has an active profile for KittyTune with EnableHooking = 0.
     * Also attempts to install the profile into RTSS's Profiles directory if missing and permissions allow.
     */
    private fun isRtssExempted(): Boolean {
        var exempted = false
        for (dir in RTSS_DIRS) {
            if (!dir.exists()) continue
            val profilesDir = File(dir, "Profiles")
            val templatesDir = File(dir, "ProfileTemplates")
            val profileFile = File(profilesDir, "KittyTune.exe.cfg")
            val templateFile = File(templatesDir, "KittyTune.exe.cfg")

            // If profile is missing in Profiles directory, attempt to deploy it
            if (profilesDir.exists() && (!profileFile.exists() || !isHookingDisabledIn(profileFile))) {
                runCatching {
                    profileFile.writeText("[Hooking]\r\nEnableHooking = 0\r\n")
                }
            }

            if (isHookingDisabledIn(profileFile) || isHookingDisabledIn(templateFile)) {
                exempted = true
            }
        }
        return exempted
    }

    private fun isHookingDisabledIn(file: File): Boolean {
        if (!file.exists()) return false
        return runCatching {
            file.useLines { lines ->
                lines.any { line ->
                    val clean = line.replace(" ", "").replace("\t", "").lowercase()
                    clean == "enablehooking=0"
                }
            }
        }.getOrDefault(false)
    }

    /**
     * Whether RTSS.exe is running. Read from a process snapshot, which lists the names of elevated processes
     * too (RTSS usually runs as administrator, so its command line cannot be queried).
     */
    private fun isRtssRunning(): Boolean = runCatching {
        val kernel = Kernel32.INSTANCE
        val snapshot = kernel.CreateToolhelp32Snapshot(Tlhelp32.TH32CS_SNAPPROCESS, WinDef.DWORD(0))
        if (snapshot == null || WinNT.INVALID_HANDLE_VALUE == snapshot) return@runCatching false
        try {
            val entry = Tlhelp32.PROCESSENTRY32.ByReference()
            var more = kernel.Process32First(snapshot, entry)
            while (more) {
                if (Native.toString(entry.szExeFile).equals("RTSS.exe", ignoreCase = true)) return@runCatching true
                more = kernel.Process32Next(snapshot, entry)
            }
            false
        } finally {
            kernel.CloseHandle(snapshot)
        }
    }.getOrDefault(false)
}
