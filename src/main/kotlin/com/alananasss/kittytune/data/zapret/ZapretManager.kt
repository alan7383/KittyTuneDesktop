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
     * Where zapret is, without asking: the folder of a running `winws.exe` on Windows, the usual install
     * path on Linux.
     */
    fun detect(): File? {
        if (!isWindows) return listOf("/opt/zapret", "/usr/local/zapret").map(::File).firstOrNull { ZapretInstall(it).isValid }
        return (sequenceOf(runningWinwsPath(), serviceImagePath()).filterNotNull().map { File(it).parentFile?.parentFile }
            + commonFolders())
            .filterNotNull()
            .firstOrNull { ZapretInstall(it).isValid }
    }

    /** The running winws.exe's path, when this process may read it (it may not, when winws runs elevated). */
    private fun runningWinwsPath(): String? = ProcessHandle.allProcesses().toList().firstNotNullOfOrNull { process ->
        process.info().command().orElse(null)?.takeIf { it.endsWith("winws.exe", ignoreCase = true) }
    }

    /**
     * The zapret service's executable, from the registry. `service.bat` installs winws as a service; the
     * service's configuration is readable without administrator rights even when the process itself is not.
     */
    private fun serviceImagePath(): String? = listOf("zapret", "winws1", "winws").firstNotNullOfOrNull { name ->
        runCatching {
            val process = ProcessBuilder("reg", "query", "HKLM\\SYSTEM\\CurrentControlSet\\Services\\$name", "/v", "ImagePath")
                .redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            Regex("([A-Za-z]:\\\\[^\"]*?winws\\.exe)", RegexOption.IGNORE_CASE).find(output)?.groupValues?.get(1)
        }.getOrNull()
    }

    /** Where people unpack zapret: the desktop, downloads, documents, the drive's root — a level or two deep. */
    private fun commonFolders(): Sequence<File> {
        val home = File(System.getProperty("user.home"))
        val roots = listOf(File(home, "Desktop"), File(home, "Downloads"), File(home, "Documents"), home, File("C:\\"), File("D:\\"))
        return roots.asSequence().filter { it.isDirectory }.flatMap { root ->
            val first = root.listFiles { f -> f.isDirectory }.orEmpty().asSequence()
            first + first.filter { it.name.contains("zapret", true) }.flatMap { it.listFiles { f -> f.isDirectory }.orEmpty().asSequence() }
        }.filter { File(it, "bin/winws.exe").isFile }
    }

    /**
     * Whether zapret is running now. By name through `tasklist` on Windows: an elevated winws hides its path
     * from this process, but not its name.
     */
    fun isRunning(): Boolean {
        if (isWindows) {
            return runCatching {
                val process = ProcessBuilder("tasklist", "/FI", "IMAGENAME eq winws.exe", "/NH").redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()
                output.contains("winws.exe", ignoreCase = true)
            }.getOrDefault(false)
        }
        return ProcessHandle.allProcesses().anyMatch { (it.info().command().orElse("") ?: "").endsWith("/nfqws") }
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
