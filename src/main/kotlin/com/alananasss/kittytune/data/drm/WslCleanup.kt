package com.alananasss.kittytune.data.drm

import com.alananasss.kittytune.utils.Logger
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Removes what the removed WSL backend left on a machine.
 *
 * Protected tracks used to run the daemon inside a dedicated Linux distribution imported by the app.
 * That backend is gone — the native module does the same job in one process, with no Windows feature,
 * no administrator prompt and no restart — but people who installed the earlier version still have
 * the distribution, its folder and a configuration pointing at `wsl.exe`.
 *
 * Nothing here touches anything that is not ours: only the distributions this app imported
 * ([DISTROS]), its own folder, and its own configuration file. The Windows features WSL needed are
 * left exactly as they are: other software may use them, and turning a Windows component off behind
 * the user's back is not this app's business.
 */
internal object WslCleanup {

    private const val TAG = "WslCleanup"

    /** Every distribution name this app ever imported. Older builds used the second one. */
    val DISTROS = listOf("kittytune-cdm", "ktw1", "ktw2")

    private const val CONFIG_NAME = "cdm-sidecar.json"

    private val localAppData: File?
        get() = System.getenv("LOCALAPPDATA")?.let { File(it) }

    /** The folder the imported distribution lived in. */
    private val wslFolder: File?
        get() = localAppData?.let { File(it, "KittyTune/wsl") }

    private val configFile: File?
        get() = localAppData?.let { File(it, "KittyTune/$CONFIG_NAME") }

    private val wslExe: File?
        get() = System.getenv("SystemRoot")?.let { File(it, "System32/wsl.exe") }?.takeIf { it.isFile }

    /** True on the platform the sidecar exists on at all. */
    val windows: Boolean get() = System.getProperty("os.name").orEmpty().startsWith("Windows", true)

    /** Whether Windows has WSL at all (the stub `wsl.exe` counts as absent: it only installs). */
    val wslInstalled: Boolean get() = windows && wslExe != null

    /**
     * What an older version left, in the words the settings page shows — empty when there is nothing.
     *
     * Reading this never starts anything: it lists distributions (which only reads the registry
     * through `wsl.exe`), and checks two paths.
     */
    suspend fun leftovers(): List<String> = runCatching { find() }.getOrDefault(emptyList())

    /** The same, synchronously, for a page that only wants to know whether to show a row. */
    fun hasLeftoversNow(): Boolean = runCatching { find() }.getOrDefault(emptyList()).isNotEmpty()

    private fun find(): List<String> {
        val found = ArrayList<String>()
        if (!windows) return found
        val registered = registeredDistros()
        DISTROS.filter { name -> registered.any { it.equals(name, true) } }
            .forEach { found += "distribution WSL « $it »" }
        if (wslFolder?.isDirectory == true && wslFolder?.listFiles()?.isNotEmpty() == true) {
            found += "dossier ${wslFolder?.absolutePath}"
        }
        if (configPointsAtWsl()) found += "configuration pointant vers wsl.exe"
        return found
    }

    /**
     * Deletes what belongs to the old backend.
     *
     * Order matters: the configuration goes first (so nothing tries to start the daemon again), then
     * the distributions are unregistered (`wsl --unregister` deletes their filesystem), then the
     * folder the app owned.
     *
     * @param onLine called with each step, for the page's log.
     * @return true when nothing of the old backend is left.
     */
    suspend fun remove(onLine: (String) -> Unit = {}): Boolean {
        if (!windows) return true
        Logger.d(TAG, "removing the WSL backend leftovers")

        // The daemon may still be running inside the distribution: stopping it here is what lets
        // --unregister delete the files instead of failing.
        CdmSidecar.reset()

        if (configPointsAtWsl()) {
            val file = configFile
            onLine("configuration: ${file?.absolutePath ?: "?"} (supprimée)")
            runCatching { file?.delete() }
        }

        var ok = true
        val registered = registeredDistros()
        for (name in DISTROS.filter { distro -> registered.any { it.equals(distro, true) } }) {
            onLine("distribution $name : « wsl --unregister »")
            val result = run(listOf("--unregister", name))
            if (result == null || result != 0) {
                onLine("  échec (code $result)")
                ok = false
            } else {
                onLine("  supprimée")
            }
        }

        val folder = wslFolder
        if (folder != null && folder.exists()) {
            onLine("dossier: ${folder.absolutePath}")
            val deleted = runCatching { folder.deleteRecursively() }.getOrDefault(false)
            onLine(if (deleted) "  supprimé" else "  incomplet (fichiers en cours d'utilisation ?)")
            if (!deleted) ok = false
        }

        val left = find()
        if (left.isNotEmpty()) {
            onLine("reste: ${left.joinToString(", ")}")
            ok = false
        } else {
            onLine("résultat: plus rien de l'ancienne méthode")
        }
        return ok
    }

    /** Distribution names WSL knows about, or an empty list when it knows none. */
    private fun registeredDistros(): List<String> = runCatching {
        val output = runCapture(listOf("--list", "--quiet")) ?: return@runCatching emptyList()
        output.split('\n', '\r')
            .map { it.replace("\u0000", "").trim() }
            .filter { it.isNotEmpty() && !it.startsWith("Windows Subsystem") }
    }.getOrDefault(emptyList())

    private fun configPointsAtWsl(): Boolean = runCatching {
        val file = configFile ?: return false
        if (!file.isFile) return false
        val text = file.readText()
        text.contains("wsl.exe", ignoreCase = true) || text.contains("\"wsl", ignoreCase = true)
    }.getOrDefault(false)

    /** Runs wsl.exe and returns its exit code, or null when it could not be started at all. */
    private fun run(arguments: List<String>): Int? = runCatching {
        val exe = wslExe ?: return@runCatching null
        val process = ProcessBuilder(listOf(exe.absolutePath) + arguments)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return@runCatching -1
        }
        process.exitValue()
    }.getOrNull()

    private fun runCapture(arguments: List<String>): String? = runCatching {
        val exe = wslExe ?: return@runCatching null
        val process = ProcessBuilder(listOf(exe.absolutePath) + arguments)
            .redirectErrorStream(true)
            .start()
        process.outputStream.close()
        val text = process.inputStream.bufferedReader(Charsets.UTF_16LE).readText()
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return@runCatching null
        }
        text
    }.getOrNull()
}
