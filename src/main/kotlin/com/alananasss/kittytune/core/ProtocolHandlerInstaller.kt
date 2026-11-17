package com.alananasss.kittytune.core

import com.alananasss.kittytune.data.together.TogetherLinks
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Registers the `kittytune://` custom URL protocol for the current user so clicking
 * "Open KittyTune" on the Live Listen Along web invite page launches or wakes KittyTune Desktop
 * on Windows, Linux, and macOS without requiring administrator privileges.
 */
object ProtocolHandlerInstaller {

    private const val BUNDLE_ID = "com.alananasss.kittytune"
    private const val STAMP_FILE_NAME = "protocol_handler_stamp.txt"

    @Volatile
    private var started = false

    fun registerAsync() {
        if (started) return
        started = true
        thread(isDaemon = true, name = "kittytune-protocol-installer") {
            runCatching { installForCurrentOs() }
        }
    }

    internal fun installForCurrentOs() {
        val os = System.getProperty("os.name")?.lowercase().orEmpty()
        when {
            os.contains("win") -> registerWindows()
            os.contains("mac") || os.contains("darwin") -> registerMacOs()
            os.contains("linux") || os.contains("nix") -> registerLinux()
        }
    }

    internal fun buildWindowsOpenCommand(exePath: String): String = "\"$exePath\" \"%1\""

    private fun registerWindows() {
        val exe = locateWindowsExecutable() ?: return
        val exePath = exe.canonicalPath
        val commandValue = buildWindowsOpenCommand(exePath)
        val stampFile = File(AppDirs.dataDir.apply { mkdirs() }, STAMP_FILE_NAME)
        val expectedStamp = "win|${TogetherLinks.APP_SCHEME}|$commandValue"
        if (stampFile.isFile && runCatching { stampFile.readText() }.getOrNull() == expectedStamp) {
            return
        }

        val regExe = "${System.getenv("SystemRoot") ?: "C:\\Windows"}\\System32\\reg.exe"
        val baseKey = "HKCU\\Software\\Classes\\${TogetherLinks.APP_SCHEME}"

        fun regAdd(key: String, vararg args: String): Boolean {
            val proc = ProcessBuilder(listOf(regExe, "add", key, "/f") + args)
                .redirectErrorStream(true)
                .start()
            proc.inputStream.bufferedReader().use { it.readText() }
            return proc.waitFor(4, TimeUnit.SECONDS) && proc.exitValue() == 0
        }

        val ok = regAdd(baseKey, "/ve", "/d", "URL:KittyTune Protocol") &&
            regAdd(baseKey, "/v", "URL Protocol", "/d", "") &&
            regAdd("$baseKey\\DefaultIcon", "/ve", "/d", "\"$exePath\",0") &&
            regAdd("$baseKey\\shell\\open\\command", "/ve", "/d", commandValue)

        if (ok) {
            runCatching { stampFile.writeText(expectedStamp) }
        }
    }

    internal fun locateWindowsExecutable(): File? {
        val candidates = buildList {
            System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() }?.let { add(File(it)) }
            runCatching { ProcessHandle.current().info().command().orElse(null) }.getOrNull()
                ?.takeIf { it.endsWith("KittyTune.exe", ignoreCase = true) }
                ?.let { add(File(it)) }
            System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }?.let { localAppData ->
                add(File(localAppData, "KittyTune\\KittyTune.exe"))
                add(File(localAppData, "Programs\\KittyTune\\KittyTune.exe"))
            }
            System.getenv("ProgramFiles")?.takeIf { it.isNotBlank() }?.let { pf ->
                add(File(pf, "KittyTune\\KittyTune.exe"))
            }
        }
        return candidates.firstOrNull { it.isFile && it.name.equals("KittyTune.exe", ignoreCase = true) }
    }

    private fun registerLinux() {
        val stampFile = File(AppDirs.dataDir.apply { mkdirs() }, STAMP_FILE_NAME)
        val expectedStamp = "linux|${TogetherLinks.APP_SCHEME}|${LinuxDesktopIdentity.DESKTOP_FILE}"
        if (stampFile.isFile && runCatching { stampFile.readText() }.getOrNull() == expectedStamp) {
            return
        }
        runCatching {
            val proc = ProcessBuilder(
                "xdg-mime",
                "default",
                LinuxDesktopIdentity.DESKTOP_FILE,
                "x-scheme-handler/${TogetherLinks.APP_SCHEME}",
            ).redirectErrorStream(true).start()
            proc.inputStream.bufferedReader().use { it.readText() }
            if (proc.waitFor(4, TimeUnit.SECONDS) && proc.exitValue() == 0) {
                stampFile.writeText(expectedStamp)
            }
        }
    }

    private fun registerMacOs() {
        val bundle = locateMacAppBundle() ?: return
        val cf = Native.load("CoreFoundation", CoreFoundation::class.java)
        val ls = Native.load("CoreServices", LaunchServices::class.java)
        val bytes = bundle.absolutePath.toByteArray(Charsets.UTF_8)
        val url = cf.CFURLCreateFromFileSystemRepresentation(null, bytes, bytes.size.toLong(), 1) ?: return
        val scheme = cf.CFStringCreateWithCString(null, TogetherLinks.APP_SCHEME, 0x08000100)
        val identifier = cf.CFStringCreateWithCString(null, BUNDLE_ID, 0x08000100)
        try {
            ls.LSRegisterURL(url, 1)
            ls.LSSetDefaultHandlerForURLScheme(scheme, identifier)
        } finally {
            cf.CFRelease(url)
            cf.CFRelease(scheme)
            cf.CFRelease(identifier)
        }
    }

    private fun locateMacAppBundle(): File? {
        val starts = listOfNotNull(
            System.getProperty("jpackage.app-path"),
            System.getProperty("java.home"),
        )
        for (start in starts) {
            var dir: File? = File(start).absoluteFile
            while (dir != null) {
                if (dir.name.endsWith(".app") && File(dir, "Contents/Info.plist").isFile) return dir
                dir = dir.parentFile
            }
        }
        return null
    }

    private interface CoreFoundation : Library {
        fun CFURLCreateFromFileSystemRepresentation(allocator: Pointer?, bytes: ByteArray, length: Long, directory: Byte): Pointer?
        fun CFStringCreateWithCString(allocator: Pointer?, value: String, encoding: Int): Pointer
        fun CFRelease(value: Pointer)
    }

    private interface LaunchServices : Library {
        fun LSRegisterURL(url: Pointer, update: Byte): Int
        fun LSSetDefaultHandlerForURLScheme(scheme: Pointer, handler: Pointer): Int
    }
}
