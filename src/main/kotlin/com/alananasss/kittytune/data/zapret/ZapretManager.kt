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
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
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
        if (isWindows) {
            val running = ProcessHandle.allProcesses().toList().firstNotNullOfOrNull { process ->
                process.info().command().orElse(null)?.takeIf { it.endsWith("winws.exe", ignoreCase = true) }
            }
            // winws.exe lives in <zapret>/bin.
            return running?.let { File(it).parentFile?.parentFile }?.takeIf { ZapretInstall(it).isValid }
        }
        return listOf("/opt/zapret", "/usr/local/zapret").map(::File).firstOrNull { ZapretInstall(it).isValid }
    }

    /** Whether zapret is running now, so the page can say whether the lists are in use. */
    fun isRunning(): Boolean = ProcessHandle.allProcesses().anyMatch { process ->
        val command = process.info().command().orElse("") ?: ""
        command.endsWith("winws.exe", ignoreCase = true) || command.endsWith("/nfqws")
    }

    /** Probes every service in parallel. */
    suspend fun check(): List<ServiceCheck> = coroutineScope {
        val covered = withContext(Dispatchers.IO) { install?.coveredDomains().orEmpty() }
        ZapretServices.ALL.map { service ->
            async(Dispatchers.IO) {
                ServiceCheck(
                    service = service,
                    reachability = probe(service.probeUrl),
                    isCovered = service.domains.all { ZapretHostList.isCovered(it, covered) },
                )
            }
        }.awaitAll()
    }

    /** Any answer at all — a 403 included — means the connection got through; a timeout or reset does not. */
    private fun probe(url: String): Reachability = runCatching {
        probeClient.newCall(Request.Builder().url(url).head().build()).execute().use { Reachability.REACHABLE }
    }.getOrDefault(Reachability.BLOCKED)

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
