package com.alananasss.kittytune.data.zapret

import com.alananasss.kittytune.core.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.Proxy
import java.util.concurrent.TimeUnit

enum class Reachability { REACHABLE, BLOCKED }

data class ServiceCheck(val service: ZapretService, val reachability: Reachability, val isCovered: Boolean)

/**
 * Finding zapret, checking which services get through, and adding the domains of those that do not.
 */
object ZapretManager {
    private const val KEY_FOLDER = "zapret_folder"
    private const val KEY_AUTO_CHECKED = "zapret_auto_checked"

    private val isWindows = System.getProperty("os.name").lowercase().contains("win")

    /**
     * Direct connections only: the check is about whether the network lets a service through, so a proxy
     * configured in the app would hide the answer.
     */
    private val probeClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .followRedirects(false)
            .build()
    }

    var folder: File?
        get() = Prefs.getString(KEY_FOLDER, null)?.let(::File)?.takeIf { it.isDirectory }
        set(value) = Prefs.putString(KEY_FOLDER, value?.absolutePath)

    val install: ZapretInstall? get() = folder?.let(::ZapretInstall)?.takeIf { it.isValid }

    /** Whether the automatic first check has run for the current folder. */
    var wasAutoChecked: Boolean
        get() = Prefs.getBoolean(KEY_AUTO_CHECKED, false)
        set(value) = Prefs.putBoolean(KEY_AUTO_CHECKED, value)

    /**
     * Where zapret is, without asking: running winws process on Windows (including elevated),
     * Windows services, scheduled tasks, autorun entries, and common user/drive folders;
     * or nfqws/tpws and usual install paths on Linux.
     */
    fun detect(): File? {
        if (!isWindows) {
            val runningLinux = runningProcessHandlePaths().mapNotNull(::resolveZapretRoot).firstOrNull()
            if (runningLinux != null) return runningLinux

            val home = File(System.getProperty("user.home"))
            val linuxPaths = listOf(
                "/opt/zapret",
                "/usr/local/zapret",
                "/etc/zapret",
                File(home, "zapret").absolutePath,
                File(home, ".local/share/zapret").absolutePath,
            )
            return linuxPaths.map(::File).firstNotNullOfOrNull(::resolveZapretRoot)
        }

        val candidateSequence: Sequence<File> = sequence {
            // 1. Direct running process handle (fastest, when readable)
            yieldAll(runningProcessHandlePaths())
            // 2. Known registry services (very fast)
            yieldAll(knownServicesRegistryPaths())
            // 3. PowerShell running winws query (detects elevated processes in user session)
            yieldAll(powershellProcessPaths())
            // 4. WMIC running winws query
            yieldAll(wmicProcessPaths())
            // 5. PowerShell running cmd.exe query with zapret/winws batch scripts
            yieldAll(powershellCmdPaths())
            // 6. Tasklist window titles
            yieldAll(tasklistPaths())
            // 7. PowerShell services query
            yieldAll(powershellServicePaths())
            // 8. Recursive registry services query
            yieldAll(recursiveServiceRegistryPaths())
            // 9. Scheduled tasks (task_install)
            yieldAll(scheduledTaskPaths())
            // 10. Startup registry keys
            yieldAll(startupRegistryPaths())
            // 11. Filesystem scan (Desktop, Downloads, OneDrive, all drive roots, Program Files, Tools)
            yieldAll(commonFolders())
        }

        return candidateSequence.mapNotNull(::resolveZapretRoot).firstOrNull()
    }

    /** Resolves the root zapret installation folder from any file or nested directory inside it. */
    fun resolveZapretRoot(fileOrDir: File?): File? {
        if (fileOrDir == null) return null
        var current: File? = if (fileOrDir.isDirectory) fileOrDir else fileOrDir.parentFile
        var depth = 0
        while (current != null && depth < 5) {
            if (ZapretInstall(current).isValid) return current
            current = current.parentFile
            depth++
        }
        return null
    }

    private val PATH_REGEX = Regex(
        """(?:[A-Za-z]:[/\\]|%[A-Za-z0-9_()]+%[/\\])[^"'\r\n<>|:*?]+"""
    )

    fun expandEnvVars(path: String): String {
        val envRegex = Regex("%([A-Za-z0-9_()]+)%")
        return envRegex.replace(path) { match ->
            val varName = match.groupValues[1]
            val envValue = System.getenv(varName)
            if (envValue != null) return@replace envValue
            when (varName.lowercase()) {
                "systemdrive" -> "C:"
                "systemroot", "windir" -> "C:\\Windows"
                "userprofile" -> System.getProperty("user.home") ?: "C:\\Users\\Default"
                "programfiles" -> "C:\\Program Files"
                "programfiles(x86)" -> "C:\\Program Files (x86)"
                "localappdata" -> "${System.getProperty("user.home")}\\AppData\\Local"
                "appdata" -> "${System.getProperty("user.home")}\\AppData\\Roaming"
                else -> match.value
            }
        }
    }

    fun extractPaths(text: String): Sequence<File> =
        PATH_REGEX.findAll(text).mapNotNull { match ->
            var raw = match.value.trim()
            val spaceIndex = raw.indexOf(" -")
            if (spaceIndex > 0) raw = raw.substring(0, spaceIndex)
            val slashFlagIndex = raw.indexOf(" /")
            if (slashFlagIndex > 0) raw = raw.substring(0, slashFlagIndex)
            val cleaned = expandEnvVars(raw.trim('"', '\'', ' ', '\t'))
            if (cleaned.isNotBlank()) File(cleaned) else null
        }

    fun runCommand(vararg command: String, timeoutMs: Long = 3000): String? = runCatching {
        val process = ProcessBuilder(*command)
            .redirectErrorStream(true)
            .start()
        val output = StringBuilder()
        val reader = process.inputStream.bufferedReader(Charsets.UTF_8)
        val thread = Thread {
            runCatching {
                reader.forEachLine { line ->
                    output.appendLine(line)
                }
            }
        }
        thread.isDaemon = true
        thread.start()
        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            thread.interrupt()
            return null
        }
        thread.join(300)
        output.toString()
    }.getOrNull()

    /** Running process via Java ProcessHandle API. */
    private fun runningProcessHandlePaths(): Sequence<File> =
        ProcessHandle.allProcesses().toList().asSequence().mapNotNull { process ->
            val cmd = process.info().command().orElse(null) ?: return@mapNotNull null
            if (cmd.endsWith("winws.exe", ignoreCase = true) || cmd.endsWith("/nfqws") || cmd.endsWith("/tpws")) {
                File(cmd)
            } else null
        }

    /** Known service names in Windows registry. */
    private fun knownServicesRegistryPaths(): Sequence<File> {
        val names = listOf(
            "zapret", "winws", "winws1", "winws2",
            "zapret-discord", "zapret-youtube", "zapret-discord-youtube",
            "zapret_service", "zapret2"
        )
        return names.asSequence().mapNotNull { name ->
            val out = runCommand("reg", "query", "HKLM\\SYSTEM\\CurrentControlSet\\Services\\$name", "/v", "ImagePath")
            out?.let { extractPaths(it).firstOrNull() }
        }
    }

    /** PowerShell querying Win32_Process for winws.exe. */
    private fun powershellProcessPaths(): Sequence<File> {
        val out = runCommand(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command",
            "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8; Get-CimInstance Win32_Process -Filter \"Name = 'winws.exe'\" | ForEach-Object { \$_.ExecutablePath; \$_.CommandLine }"
        ) ?: return emptySequence()
        return extractPaths(out)
    }

    /** WMIC fallback for winws.exe process. */
    private fun wmicProcessPaths(): Sequence<File> {
        val out = runCommand("wmic", "process", "where", "name='winws.exe'", "get", "ExecutablePath,CommandLine", "/format:list")
            ?: return emptySequence()
        return extractPaths(out)
    }

    /** PowerShell querying cmd.exe running zapret scripts. */
    private fun powershellCmdPaths(): Sequence<File> {
        val out = runCommand(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command",
            "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8; Get-CimInstance Win32_Process -Filter \"Name = 'cmd.exe'\" | Where-Object { \$_.CommandLine -match 'zapret|winws|preset|blockcheck|service' } | ForEach-Object { \$_.CommandLine }"
        ) ?: return emptySequence()
        return extractPaths(out)
    }

    /** Window titles from tasklist. */
    private fun tasklistPaths(): Sequence<File> {
        val out = runCommand("tasklist", "/v", "/fo", "csv") ?: return emptySequence()
        return extractPaths(out)
    }

    /** PowerShell query for Win32_Service containing winws. */
    private fun powershellServicePaths(): Sequence<File> {
        val out = runCommand(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command",
            "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8; (Get-CimInstance Win32_Service | Where-Object { \$_.PathName -like '*winws*' }).PathName"
        ) ?: return emptySequence()
        return extractPaths(out)
    }

    /** Recursive search across Windows services key for winws.exe. */
    private fun recursiveServiceRegistryPaths(): Sequence<File> {
        val out = runCommand("reg", "query", "HKLM\\SYSTEM\\CurrentControlSet\\Services", "/s", "/d", "/f", "winws.exe")
            ?: return emptySequence()
        return extractPaths(out)
    }

    /** Windows scheduled tasks for zapret. */
    private fun scheduledTaskPaths(): Sequence<File> {
        val out = runCommand(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command",
            "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8; Get-ScheduledTask | Where-Object { \$_.TaskName -match 'zapret|winws' -or \$_.Actions.Execute -match 'winws' } | ForEach-Object { \$_.Actions.Execute; \$_.Actions.WorkingDirectory }"
        ) ?: runCommand("schtasks", "/query", "/fo", "csv", "/v")
        ?: return emptySequence()
        return extractPaths(out)
    }

    /** Windows Run/Startup keys. */
    private fun startupRegistryPaths(): Sequence<File> {
        val hkcu = runCommand("reg", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run")
        val hklm = runCommand("reg", "query", "HKLM\\Software\\Microsoft\\Windows\\CurrentVersion\\Run")
        val combined = (hkcu.orEmpty() + "\n" + hklm.orEmpty())
        return extractPaths(combined)
    }

    /** Default folder to open in file picker. */
    fun defaultSearchFolder(): File {
        val home = File(System.getProperty("user.home"))
        val oneDriveDesktop = System.getenv("OneDrive")?.let { File(it, "Desktop") }
            ?: File(home, "OneDrive/Desktop")
        if (oneDriveDesktop.isDirectory) return oneDriveDesktop
        val desktop = File(home, "Desktop")
        if (desktop.isDirectory) return desktop
        return home
    }

    /** Where people unpack zapret: Desktop (including OneDrive), Downloads, Documents, all drives roots, Program Files, Tools. */
    private fun commonFolders(): Sequence<File> {
        val home = File(System.getProperty("user.home"))
        val oneDrive = System.getenv("OneDrive")?.let(::File)
        val userProfile = System.getenv("USERPROFILE")?.let(::File)

        val userFolders = listOfNotNull(
            File(home, "Desktop"),
            File(home, "Downloads"),
            File(home, "Documents"),
            oneDrive?.let { File(it, "Desktop") },
            oneDrive?.let { File(it, "Downloads") },
            oneDrive?.let { File(it, "Documents") },
            File(home, "OneDrive/Desktop"),
            File(home, "OneDrive/Downloads"),
            File(home, "OneDrive/Documents"),
            File(home, "OneDrive - Personal/Desktop"),
            File(home, "OneDrive - Personal/Documents"),
            userProfile?.let { File(it, "Desktop") },
            userProfile?.let { File(it, "Downloads") },
            userProfile?.let { File(it, "Documents") },
            home,
            File(home, "Tools"),
            File(home, "zapret"),
            File(home, "AppData/Local/Programs"),
        ).filter { it.isDirectory }.distinct()

        val driveRoots = File.listRoots()?.toList().orEmpty()
            .ifEmpty { listOf(File("C:\\"), File("D:\\")) }
            .filter { it.isDirectory }

        val driveSpecificPaths = driveRoots.flatMap { root ->
            listOf(
                File(root, "zapret"),
                File(root, "zapret-win-bundle"),
                File(root, "zapret-win-bundle-master"),
                File(root, "zapret-discord-youtube"),
                File(root, "Program Files/zapret"),
                File(root, "Program Files (x86)/zapret"),
                File(root, "Tools/zapret"),
                File(root, "Tools"),
                File(root, "DPI/zapret"),
                File(root, "DPI"),
                File(root, "bypass/zapret"),
                File(root, "opt/zapret"),
            )
        }

        return sequence {
            // 1. Direct drive paths
            for (target in driveSpecificPaths) {
                if (target.isDirectory) yield(target)
            }

            // 2. User folders (Desktop, Downloads, etc.)
            for (folder in userFolders) {
                yield(folder)
                val children = folder.listFiles { f -> f.isDirectory }.orEmpty()
                for (child in children) {
                    yield(child)
                    val isInteractive = folder.name.equals("Downloads", true) || folder.name.equals("Desktop", true)
                    val matchesKeyword = child.name.contains("zapret", true) ||
                        child.name.contains("winws", true) ||
                        child.name.contains("flowseal", true) ||
                        child.name.contains("discord", true) ||
                        child.name.contains("bypass", true) ||
                        child.name.contains("dpi", true)
                    if (isInteractive || matchesKeyword) {
                        val grandChildren = child.listFiles { f -> f.isDirectory }.orEmpty()
                        for (grandChild in grandChildren) {
                            yield(grandChild)
                        }
                    }
                }
            }

            // 3. Drive roots with matching keywords
            for (root in driveRoots) {
                val children = root.listFiles { f ->
                    f.isDirectory && (
                        f.name.contains("zapret", true) ||
                        f.name.contains("winws", true) ||
                        f.name.contains("flowseal", true) ||
                        f.name.contains("bypass", true) ||
                        f.name.contains("dpi", true)
                    )
                }.orEmpty()
                for (child in children) {
                    yield(child)
                }
            }
        }
    }

    /**
     * Whether zapret is running now. By name through `tasklist` on Windows: an elevated winws hides its path
     * from ProcessHandle in non-elevated callers, but not its process name.
     */
    fun isRunning(): Boolean {
        if (isWindows) {
            val tasklistRunning = runCatching {
                val output = runCommand("tasklist", "/FI", "IMAGENAME eq winws.exe", "/NH", timeoutMs = 2000) ?: ""
                output.contains("winws.exe", ignoreCase = true)
            }.getOrDefault(false)
            if (tasklistRunning) return true

            return ProcessHandle.allProcesses().anyMatch { process ->
                val cmd = process.info().command().orElse("") ?: ""
                cmd.endsWith("winws.exe", ignoreCase = true)
            }
        }
        return ProcessHandle.allProcesses().anyMatch { process ->
            val cmd = process.info().command().orElse("") ?: ""
            cmd.endsWith("/nfqws") || cmd.endsWith("/tpws") || cmd == "nfqws" || cmd == "tpws"
        }
    }

    /** Probes every service in parallel. */
    suspend fun check(): List<ServiceCheck> = coroutineScope {
        val covered = withContext(Dispatchers.IO) { install?.coveredDomains().orEmpty() }
        ZapretServices.ALL.map { service ->
            async(Dispatchers.IO) {
                ServiceCheck(
                    service = service,
                    reachability = if (service.probeUrls.all { probe(it) == Reachability.REACHABLE }) Reachability.REACHABLE else Reachability.BLOCKED,
                    isCovered = service.domains.all { ZapretHostList.isCovered(it, covered) },
                )
            }
        }.awaitAll()
    }

    /** Any answer at all — a 403 included — means the connection got through; a timeout or reset does not. */
    /**
     * A real request, with its body read: blocking by traffic inspection often lets the handshake and a few
     * kilobytes through and then stalls the connection, so a HEAD with an empty answer said "works" for services
     * whose searches never returned. Any status counts — a 401 from an API is it answering.
     */
    private fun probe(url: String): Reachability = runCatching {
        probeClient.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()).execute().use { response ->
            response.body.byteStream().use { input ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (total < PROBE_BYTES) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                }
            }
            Reachability.REACHABLE
        }
    }.getOrDefault(Reachability.BLOCKED)

    private const val PROBE_BYTES = 64L * 1024

    /** Adds the domains of [services] to the user list; returns the domains written. */
    suspend fun addDomains(services: Collection<ZapretService>): List<String> = withContext(Dispatchers.IO) {
        install?.add(services.flatMap { it.domains }).orEmpty()
    }

    suspend fun removeOwnDomains() = withContext(Dispatchers.IO) { install?.removeOwn() }

    /**
     * The first time a zapret folder is known: probe everything and add only what is blocked. Runs once;
     * after that the page's buttons are the way to change the list.
     */
    suspend fun autoConfigureOnce(): List<String> {
        if (wasAutoChecked || install == null) return emptyList()
        val results = check()
        // Nothing reachable at all is no network, not a blocklist: try again next launch.
        if (results.none { it.reachability == Reachability.REACHABLE }) return emptyList()
        val blocked = results.filter { it.reachability == Reachability.BLOCKED && !it.isCovered }.map { it.service }
        wasAutoChecked = true
        return if (blocked.isEmpty()) emptyList() else addDomains(blocked)
    }
}
