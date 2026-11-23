package com.alananasss.kittytune.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Lets a phone reach this PC through Windows Defender Firewall.
 *
 * The usual way pairing "works but then both devices show offline": Windows drops incoming connections
 * on a network marked Public, and when its "allow access?" prompt is dismissed it adds rules that
 * *block* KittyTune, which win over any allow rule. Outgoing calls from the PC still work, so pairing and
 * the odd sync went through while the phone could never call in.
 *
 * [allow] runs one elevated script (one UAC prompt) that adds inbound rules for the sync, Connect and
 * discovery ports, limited to the local subnet, and removes inbound block rules that name this
 * executable. Nothing else is touched.
 */
object WindowsFirewall {
    const val RULE_TCP = "KittyTune devices (TCP)"
    const val RULE_UDP = "KittyTune devices (UDP)"

    val isWindows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    /** Whether our rules are in place. Null when it cannot be told (not Windows, or netsh failed). */
    suspend fun rulesPresent(): Boolean? = withContext(Dispatchers.IO) {
        if (!isWindows) return@withContext null
        runCatching {
            val process = ProcessBuilder("netsh", "advfirewall", "firewall", "show", "rule", "name=$RULE_TCP")
                .redirectErrorStream(true).start()
            val out = process.inputStream.bufferedReader().readText()
            if (!process.waitFor(10, TimeUnit.SECONDS)) { process.destroy(); return@runCatching null }
            // netsh exits 1 with "No rules match" when the rule does not exist.
            process.exitValue() == 0 && out.contains(RULE_TCP)
        }.getOrNull()
    }

    /** The executable Windows sees listening (KittyTune.exe in an install, java.exe in development). */
    private fun executable(): String? = ProcessHandle.current().info().command().orElse(null)

    /** The elevated PowerShell script, kept separate so its content can be checked without running it. */
    internal fun script(tcpPorts: List<Int>, udpPort: Int, program: String?): String = buildString {
        fun q(value: String) = "'" + value.replace("'", "''") + "'"
        appendLine("\$ErrorActionPreference = 'SilentlyContinue'")
        appendLine("Remove-NetFirewallRule -DisplayName ${q(RULE_TCP)}")
        appendLine("Remove-NetFirewallRule -DisplayName ${q(RULE_UDP)}")
        appendLine(
            "New-NetFirewallRule -DisplayName ${q(RULE_TCP)} -Direction Inbound -Action Allow -Protocol TCP " +
                "-LocalPort ${tcpPorts.distinct().joinToString(",")} -RemoteAddress LocalSubnet -Profile Any | Out-Null"
        )
        appendLine(
            "New-NetFirewallRule -DisplayName ${q(RULE_UDP)} -Direction Inbound -Action Allow -Protocol UDP " +
                "-LocalPort $udpPort -RemoteAddress LocalSubnet -Profile Any | Out-Null"
        )
        if (program != null) {
            // Block rules win over allow rules; these are the ones Windows adds when its prompt is dismissed.
            appendLine(
                "Get-NetFirewallApplicationFilter -Program ${q(program)} | Get-NetFirewallRule | " +
                    "Where-Object { \$_.Direction -eq 'Inbound' -and \$_.Action -eq 'Block' } | Remove-NetFirewallRule"
            )
        }
        appendLine("exit 0")
    }

    /**
     * Adds the rules, asking Windows for administrator rights once.
     *
     * @return true when the rules are in place afterwards (false when the prompt was refused).
     */
    suspend fun allow(): Boolean = withContext(Dispatchers.IO) {
        if (!isWindows) return@withContext false
        val file = File.createTempFile("kittytune-firewall-", ".ps1")
        try {
            file.writeText(script(listOf(SyncService.port, ConnectLanPort), SyncDiscovery.DISCOVERY_PORT, executable()))
            val launcher = "Start-Process -FilePath powershell.exe -Verb RunAs -Wait -WindowStyle Hidden " +
                "-ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File','\"${file.absolutePath}\"')"
            val process = ProcessBuilder("powershell.exe", "-NoProfile", "-Command", launcher)
                .redirectErrorStream(true).start()
            process.inputStream.bufferedReader().readText()
            if (!process.waitFor(2, TimeUnit.MINUTES)) process.destroy()
            val ok = rulesPresent() == true
            if (ok) NetLog.i("sync", "Windows Firewall rules added") else NetLog.w("sync", "Windows Firewall rules were not added")
            ok
        } catch (t: Throwable) {
            NetLog.w("sync", "Could not update Windows Firewall", t)
            false
        } finally {
            file.delete()
        }
    }
}
