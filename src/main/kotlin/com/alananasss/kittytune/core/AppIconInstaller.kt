package com.alananasss.kittytune.core

import java.io.File

/** `SHChangeNotify` is not in JNA's bundled Shell32 mapping, so declare the one call we need. */
private interface WindowsShellNotifier : com.sun.jna.win32.StdCallLibrary {
    fun SHChangeNotify(eventId: Int, flags: Int, item1: com.sun.jna.Pointer?, item2: com.sun.jna.Pointer?)
}

private const val SHCNE_ASSOCCHANGED = 0x08000000
private const val SHCNF_IDLIST = 0x0000

/**
 * Applies the selected app icon variant beyond the running window:
 *
 *  - **Linux** (Arch/KDE/Debian/Ubuntu/GNOME/…): writes the chosen PNG at every standard
 *    size into the user's hicolor icon theme (~/.local/share/icons) so pinned launchers and
 *    dock entries pick it up — the freedesktop-standard way to override a system package icon.
 *  - **Windows**: rewrites the IconLocation of KittyTune shortcuts (.lnk) found in the Start
 *    Menu, on the Desktop and in the taskbar pins, pointing them at a generated multi-size
 *    .ico under %APPDATA%\KittyTune. The icon embedded in the exe itself cannot change
 *    without repackaging, but every shortcut the user actually clicks does switch.
 *  - **macOS**: replaces the .icns inside the running .app bundle and re-registers it with
 *    LaunchServices, which is what Finder, Launchpad and the Dock read.
 *
 * The running window's own icon switches on every OS (Main.kt), and the Dock image plus the
 * multi-size window icons are handled by [AppIconRuntime].
 */
object AppIconInstaller {

    /**
     * Installs the variant for launchers, off the calling thread.
     *
     * Every platform's path ends in shell commands that have to be waited on — kbuildsycoca,
     * update-desktop-database, lsregister, ie4uinit — and together they take long enough to
     * stutter the UI if run from a click handler, which is where this is called from.
     */
    fun apply(variantKey: String) {
        Thread({ install(variantKey) }, "kittytune-icon-install").apply {
            isDaemon = true
            start()
        }
    }

    private fun install(variantKey: String) {
        val os = System.getProperty("os.name").lowercase()
        when {
            os.contains("win") -> applyWindows(variantKey)
            os.contains("mac") || os.contains("darwin") -> applyMacOs(variantKey)
            os.contains("linux") || os.contains("nix") -> applyLinux(variantKey)
        }
    }

    private fun variantPng(variantKey: String): ByteArray? =
        Thread.currentThread().contextClassLoader
            ?.getResourceAsStream(AppIconVariants.resourcePath(variantKey))
            ?.use { it.readBytes() }

    // ------------------------------------------------------------------ linux

    /**
     * Sizes written into hicolor. Panels and launchers each pick their own, and a size that is
     * missing falls back to whatever the *system* theme has — i.e. the old icon — so writing
     * only one size leaves stale icons in some slots.
     */
    private val LINUX_SIZES = intArrayOf(16, 22, 24, 32, 48, 64, 128, 256, 512)

    /** Must match the WM_CLASS Main.kt sets via `sun.java2d.wm.className`. */
    private const val LINUX_WM_CLASS = "kitty-tune"

    private fun applyLinux(variantKey: String) {
        try {
            val pngBytes = variantPng(variantKey) ?: return
            val source = IconEncoders.decode(pngBytes) ?: return

            val iconsRoot = File(System.getProperty("user.home"), ".local/share/icons")
            val hicolor = File(iconsRoot, "hicolor")

            for (size in LINUX_SIZES) {
                if (size > maxOf(source.width, source.height)) continue
                val dir = File(hicolor, "${size}x${size}/apps")
                if (!dir.isDirectory && !dir.mkdirs()) continue
                val scaled = IconEncoders.png(IconEncoders.scaled(source, size))
                // Variant-specific icon name: launchers cache pixmaps per icon NAME,
                // so switching variant must switch name to bust the cache.
                File(dir, "kittytune-$variantKey.png").writeBytes(scaled)
                // Keep the plain name in sync for other consumers (taskbar pins, GTK).
                File(dir, "kittytune.png").writeBytes(scaled)
            }

            // User-level .desktop override pointing at the variant icon — takes
            // precedence over the package's desktop file, per XDG spec.
            overrideDesktopFile(variantKey)
            // Same for shortcuts living on the desktop itself (real files or
            // symlinks to the system entry — symlinks are replaced by real files
            // so the icon name can change).
            rewriteDesktopShortcuts(variantKey)

            // Gentle cache invalidation — no shell restart:
            //  - GTK apps: icon theme cache
            //  - KDE: KSycoca (app entries) + icon-cache.kcache (KIconLoader disk cache)
            //  - touch the theme dirs so inotify watchers notice (writing only the
            //    leaf apps/ dir does not bump the watched theme root mtime)
            val userApps = File(System.getProperty("user.home"), ".local/share/applications")
            listOf(
                ProcessBuilder("gtk-update-icon-cache", "-f", "-t", hicolor.canonicalPath),
                ProcessBuilder("update-desktop-database", userApps.canonicalPath),
                ProcessBuilder("kbuildsycoca6", "--noincremental"),
                ProcessBuilder("kbuildsycoca5", "--noincremental")
            ).forEach { cmd ->
                runCatching {
                    cmd.redirectErrorStream(true)
                    cmd.start().waitFor()
                }
            }

            runCatching {
                File(System.getProperty("user.home"), ".cache/icon-cache.kcache").delete()
            }
            runCatching {
                val now = System.currentTimeMillis()
                LINUX_SIZES.forEach { size ->
                    File(hicolor, "${size}x${size}/apps").setLastModified(now)
                    File(hicolor, "${size}x${size}").setLastModified(now)
                }
                hicolor.setLastModified(now)
                iconsRoot.setLastModified(now)
            }
        } catch (e: Throwable) {
            println("[AppIconInstaller] Failed to install launcher icon: ${e.message}")
        }
    }

    /**
     * Copies the system KittyTune .desktop into ~/.local/share/applications with
     * `Icon=kittytune-<variant>` so the launcher resolves a fresh (uncached) icon
     * name on every switch. User desktop files shadow system ones per XDG spec.
     */
    private fun overrideDesktopFile(variantKey: String) {
        val dataDirs = listOf(
            File("/usr/share/applications"),
            File("/usr/local/share/applications"),
            File(System.getProperty("user.home"), ".local/share/applications")
        ) + (System.getenv("XDG_DATA_DIRS") ?: "")
            .split(":").filter { it.isNotBlank() }
            .map { File(it, "applications") }

        val systemDesktop = dataDirs
            .filter { it.isDirectory }
            .flatMap { dir -> dir.listFiles { f -> f.isFile && f.extension == "desktop" }?.toList() ?: emptyList() }
            .filter { it.name.contains("kitty", ignoreCase = true) && it.name.contains("tune", ignoreCase = true) }
            .firstOrNull { df ->
                val txt = runCatching { df.readText() }.getOrDefault("")
                // Skip our own auth-handler stubs (NoDisplay=true).
                !txt.contains("NoDisplay=true") &&
                    (txt.contains("Name=KittyTune") || txt.contains("itty-tune", ignoreCase = true))
            } ?: run {
            println("[AppIconInstaller] No system .desktop found; skipping override")
            return
        }

        val userApps = File(System.getProperty("user.home"), ".local/share/applications").apply { mkdirs() }
        val userDesktop = File(userApps, systemDesktop.name)
        val original = systemDesktop.readLines()

        val rewritten = original.map { line ->
            if (line.startsWith("Icon=")) "Icon=kittytune-$variantKey" else line
        }.toMutableList()

        if (original.none { it.startsWith("Icon=") }) {
            rewritten += "Icon=kittytune-$variantKey"
        }
        // Without StartupWMClass the shell has to guess which entry an open window belongs to.
        // KDE guesses right because the WM_CLASS Main.kt sets matches the entry's file name,
        // but GNOME and wlroots-based shells do not, and then the running window shows a
        // generic icon while the pinned launcher shows the variant. Stating it removes the guess.
        if (original.none { it.startsWith("StartupWMClass=") }) {
            rewritten += "StartupWMClass=$LINUX_WM_CLASS"
        }

        userDesktop.writeText(rewritten.joinToString("\n") + "\n")
    }

    /** XDG desktop directory (handles localized names like ~/Bureau), fallback ~/Desktop. */
    private fun resolveDesktopDir(): File {
        val dirsFile = File(System.getProperty("user.home"), ".config/user-dirs.dirs")
        if (dirsFile.exists()) {
            runCatching {
                val m = Regex("XDG_DESKTOP_DIR\\s*=\\s*\"([^\"]+)\"").find(dirsFile.readText())
                if (m != null) {
                    val path = m.groupValues[1]
                        .replace("\$HOME", System.getProperty("user.home"))
                        .replaceFirst("~", System.getProperty("user.home"))
                    val f = File(path)
                    if (f.isDirectory) return f
                }
            }
        }
        return File(System.getProperty("user.home"), "Desktop")
    }

    /**
     * Rewrites Icon= in KittyTune shortcuts found on the desktop. Plasma "add to
     * desktop" may create either a real copy or a symlink to the system entry —
     * symlinks are replaced by real files (a symlink would keep showing the
     * system icon name, which is pixmap-cached stale).
     */
    private fun rewriteDesktopShortcuts(variantKey: String) {
        val desktopDir = resolveDesktopDir()
        val shortcuts = desktopDir.listFiles { f ->
            f.isFile || java.nio.file.Files.isSymbolicLink(f.toPath())
        }?.filter {
            it.name.endsWith(".desktop") &&
                it.name.contains("kitty", ignoreCase = true) &&
                it.name.contains("tune", ignoreCase = true)
        } ?: return

        for (shortcut in shortcuts) {
            runCatching {
                val target = if (java.nio.file.Files.isSymbolicLink(shortcut.toPath())) shortcut.canonicalFile else shortcut
                val lines = target.readLines()
                if (lines.any { it.trim() == "NoDisplay=true" }) return@runCatching

                val content = lines.joinToString("\n") { line ->
                    if (line.startsWith("Icon=")) "Icon=kittytune-$variantKey" else line
                } + if (lines.any { it.startsWith("Icon=") }) "" else "\nIcon=kittytune-$variantKey"

                if (java.nio.file.Files.isSymbolicLink(shortcut.toPath())) {
                    shortcut.delete()
                    shortcut.writeText(content + "\n")
                    shortcut.setExecutable(true)   // Plasma "trusted" flag
                } else {
                    runCatching { shortcut.setWritable(true) }
                    shortcut.writeText(content + "\n")
                    shortcut.setExecutable(true)
                }
                desktopDir.setLastModified(System.currentTimeMillis())
            }
        }
    }

    // ---------------------------------------------------------------- windows

    private fun applyWindows(variantKey: String) {
        try {
            val pngBytes = variantPng(variantKey) ?: return
            val source = IconEncoders.decode(pngBytes) ?: return

            val iconDir = File(AppDirs.dataDir, "icons").apply { mkdirs() }
            // Variant-specific file name, for the same reason the Linux path uses a
            // variant-specific icon name: Explorer caches by path, and pointing a shortcut at
            // a path it has never seen is the one cache bust that always works.
            val icoFile = File(iconDir, "app_icon_$variantKey.ico")
            icoFile.writeBytes(IconEncoders.ico(source))

            // Drop the ICOs for other variants so the directory does not grow without bound.
            iconDir.listFiles { f ->
                f.isFile && f.name.startsWith("app_icon") && f.name != icoFile.name
            }?.forEach { runCatching { it.delete() } }

            val links = findWindowsShortcuts()
            if (links.isNotEmpty()) updateShortcutIcons(links, icoFile)

            notifyWindowsShell()
        } catch (e: Throwable) {
            println("[AppIconInstaller] Failed to update Windows shortcut icons: ${e.message}")
        }
    }

    /**
     * Tells the shell its per-file association data changed. `SHChangeNotify` is the documented
     * way and costs nothing; `ie4uinit` rebuilds the icon cache; the Start Menu keeps its own
     * in-memory copy in StartMenuExperienceHost, which only lets go when it restarts — that is
     * safe (Windows brings it back on demand, and open windows and the taskbar are untouched).
     */
    private fun notifyWindowsShell() {
        runCatching {
            val shell = com.sun.jna.Native.load(
                "shell32", WindowsShellNotifier::class.java,
                com.sun.jna.win32.W32APIOptions.DEFAULT_OPTIONS
            )
            shell.SHChangeNotify(SHCNE_ASSOCCHANGED, SHCNF_IDLIST, null, null)
        }
        listOf(
            listOf("ie4uinit.exe", "-show"),
            listOf("ie4uinit.exe", "-ClearIconCache"),
            listOf("taskkill", "/f", "/im", "StartMenuExperienceHost.exe")
        ).forEach { cmd ->
            runCatching { ProcessBuilder(cmd).start() }
        }
    }

    private fun findWindowsShortcuts(): List<File> {
        val appData = System.getenv("APPDATA")
        val userHome = System.getProperty("user.home")
        val oneDrive = System.getenv("OneDrive")
        val programData = System.getenv("ProgramData")

        val searchDirs = buildList {
            appData?.let {
                add(File(it, "Microsoft/Windows/Start Menu/Programs"))
                add(File(it, "Microsoft/Windows/Start Menu/Programs/Startup"))
                add(File(it, "Microsoft/Internet Explorer/Quick Launch"))
                add(File(it, "Microsoft/Internet Explorer/Quick Launch/User Pinned/TaskBar"))
                add(File(it, "Microsoft/Internet Explorer/Quick Launch/User Pinned/startMenu"))
            }
            add(File(userHome, "Desktop"))
            oneDrive?.let { add(File(it, "Desktop")) }
            programData?.let {
                add(File(it, "Microsoft/Windows/Start Menu/Programs"))
                add(File(it, "Desktop"))   // Public Desktop (all-users shortcuts)
            }
        }.filter { it.isDirectory }

        return searchDirs.flatMap { dir ->
            dir.walkTopDown().maxDepth(4)
                .filter { it.isFile && it.extension.equals("lnk", ignoreCase = true) }
                .toList()
        }.distinctBy { it.canonicalPath }.filter { mentionsOurLauncher(it) }
    }

    /** The launcher jpackage produces from `packageName = "KittyTune"`. */
    private const val WINDOWS_EXE = "KittyTune.exe"

    /**
     * Points each of our .lnk files' IconLocation at [icoFile].
     *
     * Shortcuts are matched on their TARGET, not their name: a shortcut the user renamed still
     * gets caught, and — the reason a name match is not good enough — an unrelated app whose
     * name happens to contain "kitty" does not. KiTTY and the kitty terminal both exist, and
     * rewriting their icons would be a nasty thing to do.
     *
     * The shortcut list is handed over in a file rather than interpolated into the command, so
     * neither an apostrophe in a user name nor a hundred shortcuts can break the invocation.
     */
    private fun updateShortcutIcons(links: List<File>, icoFile: File) {
        val listFile = File(icoFile.parentFile, "shortcuts.txt")
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
            ${d}ws = New-Object -ComObject WScript.Shell
            ${d}exe = ${quote(ownExe)}
            ${d}ico = ${quote(icoFile.canonicalPath)}
            foreach (${d}p in (Get-Content -LiteralPath ${quote(listFile.canonicalPath)} -Encoding UTF8)) {
                if (-not ${d}p) { continue }
                try {
                    ${d}l = ${d}ws.CreateShortcut(${d}p)
                    ${d}t = ${d}l.TargetPath
                    if (-not ${d}t) { continue }
                    ${d}hit = ${d}false
                    if (${d}exe -and (${d}t -ieq ${d}exe)) { ${d}hit = ${d}true }
                    elseif ([System.IO.Path]::GetFileName(${d}t) -ieq '$WINDOWS_EXE') { ${d}hit = ${d}true }
                    if (${d}hit) {
                        ${d}l.IconLocation = ${d}ico + ',0'
                        ${d}l.Save()
                    }
                } catch {}
            }
        """.trimIndent()

        runCatching {
            ProcessBuilder(
                "powershell", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
                "-Command", script
            ).apply { redirectErrorStream(true) }.start().waitFor()
        }.onFailure { println("[AppIconInstaller] PowerShell shortcut update failed: ${it.message}") }
        runCatching { listFile.delete() }
    }

    /**
     * Cheap pre-filter so the PowerShell pass only opens a handful of shortcuts instead of
     * every .lnk in the Start Menu: a .lnk embeds its target path, so the launcher name shows
     * up in the raw bytes as ASCII, UTF-16 or both.
     */
    private fun mentionsOurLauncher(lnk: File): Boolean {
        val bytes = runCatching {
            if (lnk.length() > 512 * 1024) return false
            lnk.readBytes()
        }.getOrNull() ?: return false
        val name = WINDOWS_EXE
        val ascii = name.toByteArray(Charsets.US_ASCII)
        val utf16 = name.toByteArray(Charsets.UTF_16LE)
        return contains(bytes, ascii) || contains(bytes, utf16)
    }

    private fun contains(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty() || haystack.size < needle.size) return false
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                // Shortcut paths are case-preserved but not case-stable; compare loosely.
                val a = haystack[i + j].toInt().let { if (it in 65..90) it + 32 else it }
                val b = needle[j].toInt().let { if (it in 65..90) it + 32 else it }
                if (a != b) continue@outer
            }
            return true
        }
        return false
    }

    // ------------------------------------------------------------------ macos

    /**
     * Replaces the .icns inside the running .app bundle. Finder, Launchpad, the Dock's
     * not-running tile and the Cmd-Tab switcher all read the bundle, not the window, so this
     * is the persistent half; [AppIconRuntime] handles the Dock while the app is running.
     */
    private fun applyMacOs(variantKey: String) {
        try {
            val bundle = locateMacAppBundle() ?: run {
                // Running from Gradle or a bare JAR: there is no bundle to rewrite, and the
                // live Dock icon is already handled elsewhere.
                println("[AppIconInstaller] Not running from an .app bundle; skipping bundle icon")
                return
            }
            val pngBytes = variantPng(variantKey) ?: return
            val source = IconEncoders.decode(pngBytes) ?: return

            val resources = File(bundle, "Contents/Resources")
            if (!resources.isDirectory && !resources.mkdirs()) return
            val plist = File(bundle, "Contents/Info.plist")

            val icnsName = bundleIconName(plist, resources)
            val target = File(resources, icnsName)
            if (!target.canWrite() && target.exists() && !target.setWritable(true)) {
                println("[AppIconInstaller] $icnsName is not writable; skipping bundle icon")
                return
            }
            target.writeBytes(IconEncoders.icns(source))

            if (plist.isFile && !plist.readText().contains("CFBundleIconFile")) {
                addPlistIconKey(plist, icnsName)
            }

            // Finder decides whether to re-read a bundle from its modification date, and
            // LaunchServices keeps its own copy keyed on the bundle path.
            val now = System.currentTimeMillis()
            listOf(resources, File(bundle, "Contents"), bundle).forEach {
                runCatching { it.setLastModified(now) }
            }
            listOf(
                listOf(LSREGISTER, "-f", bundle.absolutePath),
                // The Dock caches the tile image in memory; it comes back within a second and
                // nothing is lost, which is the same trade as kbuildsycoca on KDE.
                listOf("killall", "-HUP", "Dock")
            ).forEach { cmd ->
                runCatching {
                    ProcessBuilder(cmd).redirectErrorStream(true).start().waitFor()
                }
            }
        } catch (e: Throwable) {
            println("[AppIconInstaller] Failed to update macOS bundle icon: ${e.message}")
        }
    }

    private const val LSREGISTER =
        "/System/Library/Frameworks/CoreServices.framework/Frameworks/" +
            "LaunchServices.framework/Support/lsregister"

    /**
     * Finds the .app we are executing from. jpackage passes the launcher path in
     * `jpackage.app-path`; failing that, walk up from the bundled runtime.
     */
    private fun locateMacAppBundle(): File? {
        val candidates = listOfNotNull(
            System.getProperty("jpackage.app-path"),
            System.getProperty("java.home"),
        )
        for (start in candidates) {
            var dir: File? = File(start).absoluteFile
            while (dir != null) {
                if (dir.name.endsWith(".app") && File(dir, "Contents").isDirectory) return dir
                dir = dir.parentFile
            }
        }
        return null
    }

    /** CFBundleIconFile if the plist names one, else any existing .icns, else the bundle name. */
    private fun bundleIconName(plist: File, resources: File): String {
        val declared = runCatching {
            Regex(
                "<key>CFBundleIconFile</key>\\s*<string>([^<]+)</string>",
                RegexOption.IGNORE_CASE
            ).find(plist.readText())?.groupValues?.get(1)?.trim()
        }.getOrNull()

        val name = declared?.takeIf { it.isNotEmpty() }
            ?: resources.listFiles { f -> f.isFile && f.extension.equals("icns", true) }
                ?.firstOrNull()?.name
            ?: "KittyTune"

        return if (name.endsWith(".icns", ignoreCase = true)) name else "$name.icns"
    }

    private fun addPlistIconKey(plist: File, icnsName: String) {
        runCatching {
            val text = plist.readText()
            val marker = "<dict>"
            val at = text.indexOf(marker)
            if (at < 0) return
            val insertion = "\n  <key>CFBundleIconFile</key>\n  <string>$icnsName</string>"
            plist.writeText(text.substring(0, at + marker.length) + insertion + text.substring(at + marker.length))
        }
    }
}

