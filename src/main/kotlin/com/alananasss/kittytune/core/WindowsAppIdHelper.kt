package com.alananasss.kittytune.core

import java.io.File
import kotlin.concurrent.thread

/**
 * Ensures the Windows Shell and Taskbar associate KittyTune windows
 * with the correct Application User Model ID ("KittyTune.KittyTune"),
 * group taskbar buttons under pinned shortcuts, and display the correct icon.
 *
 * Windows uses the AppUserModelID (AUMID) to group taskbar windows and associate
 * them with pinned shortcuts. For this to work properly:
 * 1. SetCurrentProcessExplicitAppUserModelID MUST be called early at process startup
 *    BEFORE any AWT / Swing / Compose UI windows are created.
 * 2. Shortcuts (.lnk) pinned to the taskbar, in the Start Menu, or on Desktop
 *    must have the System.AppUserModel.ID property ({9F4C2855-9F79-4B39-A8D0-E1D42DE1D5F3}, 5)
 *    set to the same matching AppID ("KittyTune.KittyTune").
 */
object WindowsAppIdHelper {

    const val APP_ID = "KittyTune.KittyTune"

    @Volatile
    private var applied = false

    fun apply(appId: String = APP_ID) {
        val os = System.getProperty("os.name")?.lowercase().orEmpty()
        if (!os.contains("win")) return
        if (applied) return
        applied = true

        // 1. Process-level AppUserModelID MUST be set before creating any window / UI
        runCatching {
            com.sun.jna.platform.win32.Shell32.INSTANCE.SetCurrentProcessExplicitAppUserModelID(
                com.sun.jna.WString(appId)
            )
        }.onFailure {
            println("[WindowsAppIdHelper] SetCurrentProcessExplicitAppUserModelID failed: ${it.message}")
        }

        // 2. Ensure shortcuts on Taskbar, Start Menu, Desktop have System.AppUserModel.ID set
        thread(isDaemon = true, name = "WindowsAppIdHelper-ShortcutSync") {
            runCatching { ensureShortcutsHaveAppId(appId) }
        }
    }

    /**
     * Inspects pinned Taskbar, Start Menu, and Desktop shortcuts for KittyTune,
     * and stamps them with the AppUserModelID if missing or outdated.
     */
    fun ensureShortcutsHaveAppId(appId: String = APP_ID) {
        val os = System.getProperty("os.name")?.lowercase().orEmpty()
        if (!os.contains("win")) return

        val targets = findShortcutFiles()
        if (targets.isEmpty()) return

        patchShortcuts(targets, appId = appId, icoPath = null)
    }

    /**
     * Updates shortcut files with AppUserModelID and optionally an icon path.
     */
    fun patchShortcuts(links: List<File>, appId: String = APP_ID, icoPath: String? = null) {
        if (links.isEmpty()) return
        val tempDir = File(System.getProperty("java.io.tmpdir"), "kittytune_appids")
        tempDir.mkdirs()
        val listFile = File(tempDir, "shortcuts_${System.currentTimeMillis()}.txt")
        runCatching {
            listFile.writeText(links.joinToString("\n") { it.canonicalPath }, Charsets.UTF_8)
        }.onFailure { return }

        val ownExe = System.getProperty("jpackage.app-path")?.takeIf {
            it.endsWith(".exe", ignoreCase = true)
        }.orEmpty()

        fun quote(s: String) = "'" + s.replace("'", "''") + "'"
        val d = "${'$'}"
        val script = """
            ${d}ErrorActionPreference = 'SilentlyContinue'
            ${d}code = @"
            using System;
            using System.Text;
            using System.Runtime.InteropServices;
            using System.Runtime.InteropServices.ComTypes;

            [ComImport, Guid("00021401-0000-0000-C000-000000000046")]
            public class ShellLinkCoClassAppIdHelper {}

            [ComImport, Guid("000214F9-0000-0000-C000-000000000046"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
            public interface IShellLinkWAppIdHelper {
                void GetPath([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder pszFile, int cch, IntPtr pfd, uint fFlags);
                void GetIDList(out IntPtr ppidl);
                void SetIDList(IntPtr pidl);
                void GetDescription([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder pszName, int cch);
                void SetDescription([MarshalAs(UnmanagedType.LPWStr)] string pszName);
                void GetWorkingDirectory([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder pszDir, int cch);
                void SetWorkingDirectory([MarshalAs(UnmanagedType.LPWStr)] string pszDir);
                void GetArguments([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder pszArgs, int cch);
                void SetArguments([MarshalAs(UnmanagedType.LPWStr)] string pszArgs);
                void GetHotkey(out short pwHotkey);
                void SetHotkey(short wHotkey);
                void GetShowCmd(out int piShowCmd);
                void SetShowCmd(int iShowCmd);
                void GetIconLocation([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder pszIconPath, int cch, out int piIcon);
                void SetIconLocation([MarshalAs(UnmanagedType.LPWStr)] string pszIconPath, int iIcon);
                void SetRelativePath([MarshalAs(UnmanagedType.LPWStr)] string pszPathRel, uint dwReserved);
                void Resolve(IntPtr hwnd, uint fFlags);
                void SetPath([MarshalAs(UnmanagedType.LPWStr)] string pszFile);
            }

            [ComImport, Guid("886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
            public interface IPropertyStoreAppIdHelper {
                void GetCount(out uint cProps);
                void GetAt(uint iProp, out PROPERTYKEYAppIdHelper pkey);
                void GetValue(ref PROPERTYKEYAppIdHelper key, out PROPVARIANTAppIdHelper pv);
                void SetValue(ref PROPERTYKEYAppIdHelper key, ref PROPVARIANTAppIdHelper pv);
                void Commit();
            }

            [StructLayout(LayoutKind.Sequential)]
            public struct PROPERTYKEYAppIdHelper {
                public Guid fmtid;
                public uint pid;
            }

            [StructLayout(LayoutKind.Explicit, Size = 24)]
            public struct PROPVARIANTAppIdHelper {
                [FieldOffset(0)] public ushort vt;
                [FieldOffset(8)] public IntPtr pwszVal;
            }

            public class NativeShortcutPatcher {
                [DllImport("ole32.dll")]
                public static extern int PropVariantClear(ref PROPVARIANTAppIdHelper pvar);

                static readonly Guid AppIdGuid = new Guid("9F4C2855-9F79-4B39-A8D0-E1D42DE1D5F3");

                public static bool Patch(string path, string appId, string iconPath) {
                    try {
                        var link = (IShellLinkWAppIdHelper)new ShellLinkCoClassAppIdHelper();
                        var file = (IPersistFile)link;
                        file.Load(path, 2); // STGM_READWRITE

                        if (!string.IsNullOrEmpty(iconPath)) {
                            link.SetIconLocation(iconPath, 0);
                        }

                        if (!string.IsNullOrEmpty(appId)) {
                            var store = (IPropertyStoreAppIdHelper)link;
                            var key = new PROPERTYKEYAppIdHelper { fmtid = AppIdGuid, pid = 5 };
                            PROPVARIANTAppIdHelper val = new PROPVARIANTAppIdHelper();
                            val.vt = 31; // VT_LPWSTR
                            val.pwszVal = Marshal.StringToCoTaskMemUni(appId);
                            try {
                                store.SetValue(ref key, ref val);
                                store.Commit();
                            } finally {
                                PropVariantClear(ref val);
                            }
                        }

                        file.Save(null, true);
                        Marshal.ReleaseComObject(file);
                        Marshal.ReleaseComObject(link);
                        return true;
                    } catch {
                        return false;
                    }
                }
            }
"@
            Add-Type -TypeDefinition ${d}code
            ${d}appId = ${quote(appId)}
            ${d}ico = ${quote(icoPath.orEmpty())}
            ${d}lines = Get-Content -LiteralPath ${quote(listFile.canonicalPath)} -Encoding UTF8
            foreach (${d}p in ${d}lines) {
                if (-not ${d}p) { continue }
                [NativeShortcutPatcher]::Patch(${d}p, ${d}appId, ${d}ico)
            }
        """.trimIndent()

        runCatching {
            val process = ProcessBuilder(
                "powershell", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
                "-Command", script
            ).apply { redirectErrorStream(true) }.start()
            process.waitFor()
        }
        runCatching { listFile.delete() }
    }

    private fun findShortcutFiles(): List<File> {
        val appData = System.getenv("APPDATA")
        val userHome = System.getProperty("user.home")
        val oneDrive = System.getenv("OneDrive")
        val programData = System.getenv("ProgramData")

        val searchDirs = buildList {
            appData?.let {
                add(File(it, "Microsoft/Internet Explorer/Quick Launch/User Pinned/TaskBar"))
                add(File(it, "Microsoft/Windows/Start Menu/Programs"))
                add(File(it, "Microsoft/Windows/Start Menu/Programs/Startup"))
                add(File(it, "Microsoft/Internet Explorer/Quick Launch"))
                add(File(it, "Microsoft/Internet Explorer/Quick Launch/User Pinned/startMenu"))
            }
            userHome?.let { add(File(it, "Desktop")) }
            oneDrive?.let { add(File(it, "Desktop")) }
            programData?.let {
                add(File(it, "Microsoft/Windows/Start Menu/Programs"))
                add(File(it, "Desktop"))
            }
        }.filter { it.isDirectory }

        return searchDirs.flatMap { dir ->
            dir.walkTopDown().maxDepth(4)
                .filter { it.isFile && it.extension.equals("lnk", ignoreCase = true) }
                .toList()
        }.distinctBy { it.canonicalPath }.filter { isKittyTuneShortcut(it) }
    }

    private const val WINDOWS_EXE = "KittyTune.exe"

    private fun isKittyTuneShortcut(file: File): Boolean {
        if (file.name.contains("KittyTune", ignoreCase = true) || file.name.contains("Kitty Tune", ignoreCase = true)) {
            return true
        }
        val bytes = runCatching {
            if (file.length() > 512 * 1024) return false
            file.readBytes()
        }.getOrNull() ?: return false
        val needle = WINDOWS_EXE.toByteArray(Charsets.US_ASCII)
        return bytes.indices.any { i ->
            i + needle.size <= bytes.size && needle.indices.all { j ->
                bytes[i + j].toInt().toChar().equals(needle[j].toInt().toChar(), ignoreCase = true)
            }
        }
    }
}
