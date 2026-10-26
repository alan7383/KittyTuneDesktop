package com.alananasss.kittytune.data.audio

import com.alananasss.kittytune.util.LinuxAudioManager
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A pair of headphones or a headset the equalizer can recognize. */
data class ConnectedHeadphone(
    /** Stable key for saved AutoEq profiles; deliberately based on the model, not the transport. */
    val key: String,
    val name: String,
    val connectionLabel: String,
    val isBluetooth: Boolean,
)

/**
 * Cross-platform output-device detection for the equalizer.
 *
 * The DSP itself is output-agnostic: it runs before the line is opened, so it applies just as well to
 * a wired jack, a USB headset or Bluetooth. This detector only finds the *name* of the active pair so
 * AutoEq can reopen the right saved profile. It uses the OS' own view of the default output first and
 * falls back to Java Sound mixers, which covers the cases where the JVM does not expose a Bluetooth
 * endpoint (notably some macOS JVMs).
 *
 * Everything runs on [Dispatchers.IO]; callers only observe [device].
 */
object HeadphoneDetector {
    private val _device = MutableStateFlow<ConnectedHeadphone?>(null)
    val device: StateFlow<ConnectedHeadphone?> = _device.asStateFlow()

    private var job: Job? = null
    private var lastProbe = 0L
    private var lastOsCandidate: ConnectedHeadphone? = null

    private val isLinux get() = System.getProperty("os.name").lowercase().contains("linux")
    private val isWindows get() = System.getProperty("os.name").lowercase().contains("windows")
    private val isMac get() = System.getProperty("os.name").lowercase().contains("mac")

    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        job = coroutineScope.launch {
            while (isActive) {
                runCatching { _device.value = detect() }
                delay(4_000)
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        _device.value = null
    }

    /** Forces a probe; useful when the user opens the equalizer or changes output device. */
    suspend fun refresh(): ConnectedHeadphone? = withContext(Dispatchers.IO) {
        val result = runCatching { detect() }.getOrNull()
        _device.value = result
        result
    }

    private fun detect(): ConnectedHeadphone? {
        // Linux needs pactl to know which *sink* is default; Windows/macOS are usually named
        // correctly by the JVM, so start with the cheap Java Sound list and only spend a
        // PowerShell/system_profiler process when that list has no headset.
        if (isLinux) {
            val now = System.currentTimeMillis()
            if (now - lastProbe > 2_500) {
                lastProbe = now
                lastOsCandidate = linuxDefaultOutput()
            }
            return lastOsCandidate ?: javaMixerCandidate(requireHeadphone = true)
        }

        val now = System.currentTimeMillis()
        if (now - lastProbe > 15_000) {
            lastProbe = now
            lastOsCandidate = when {
                isWindows -> windowsOutput()
                isMac -> macOutput()
                else -> null
            }
        }
        return lastOsCandidate ?: javaMixerCandidate(requireHeadphone = true)
    }

    /**
     * PulseAudio/PipeWire is the only backend through which Linux exposes the *default* sink, and it
     * also tells us whether that sink is a Bluetooth endpoint or the analog headphone port.
     */
    private fun linuxDefaultOutput(): ConnectedHeadphone? {
        val defaultSink = LinuxAudioManager.getDefaultSinkId()
        val sinks = LinuxAudioManager.getOutputSinks()
        if (defaultSink.isNullOrBlank() && sinks.isEmpty()) return null

        val default = sinks.firstOrNull { it.id == defaultSink } ?: defaultSink?.let {
            detectLinuxSinkById(it)
        } ?: return null
        val id = default.id
        val description = default.description.ifBlank { LinuxAudioManager.cleanName(id) }
        val lowerId = id.lowercase()
        val lowerDesc = description.lowercase()
        val activePort = linuxActivePort(id).lowercase()
        val isMicrophone = lowerId.contains("microphone") || lowerDesc.contains("microphone")
        if (isMicrophone) return null
        val isBluetooth = lowerId.contains("bluez") || lowerId.contains("bluetooth") ||
            lowerDesc.contains("bluetooth") || lowerDesc.contains("airdots") ||
            lowerDesc.contains("airpods") || lowerDesc.contains("wireless")
        // "analog-stereo" is also the name of ordinary desktop speakers. Only the active port
        // distinguishes a plugged-in headset from the line-out on the back of the PC.
        val looksWired = activePort.contains("headphone") || activePort.contains("headset") ||
            looksLikeHeadphone(description) || lowerId.contains("usb") &&
            !lowerDesc.contains("speaker") && !lowerDesc.contains("line-out")
        if (!isBluetooth && !looksWired) return null

        return ConnectedHeadphone(
            key = modelKey(description.ifBlank { id }),
            name = cleanDeviceName(description.ifBlank { id }),
            connectionLabel = if (isBluetooth) "Bluetooth" else if (lowerId.contains("usb")) "USB" else "Wired",
            isBluetooth = isBluetooth,
        )
    }

    /** Some pactl versions omit a stale description; ask again for this one sink. */
    private fun detectLinuxSinkById(id: String): com.alananasss.kittytune.util.LinuxAudioSink? {
        return runCatching {
            val process = ProcessBuilder("pactl", "list", "sinks").start()
            val text = process.inputStream.bufferedReader().readText()
            process.waitFor(2, TimeUnit.SECONDS)
            val block = text.split(Regex("(?=Sink #)")).firstOrNull { it.contains("Name: $id") } ?: return@runCatching null
            val desc = block.lineSequence()
                .firstOrNull { it.trim().startsWith("Description:") }
                ?.substringAfter("Description:")?.trim().orEmpty()
            com.alananasss.kittytune.util.LinuxAudioSink(id, desc)
        }.getOrNull()
    }

    /** The active PulseAudio port, e.g. analog-output-headphones or analog-output-lineout. */
    private fun linuxActivePort(sinkId: String): String {
        return runCatching {
            val process = ProcessBuilder("pactl", "list", "sinks").start()
            val text = process.inputStream.bufferedReader().readText()
            process.waitFor(2, TimeUnit.SECONDS)
            text.split(Regex("(?=Sink #)"))
                .firstOrNull { it.contains("Name: $sinkId") }
                ?.lineSequence()
                ?.firstOrNull { it.trim().startsWith("Active Port:") }
                ?.substringAfter("Active Port:")?.trim()
                .orEmpty()
        }.getOrDefault("")
    }

    /**
     * Windows exposes audio endpoints to Java Sound under names such as
     * "Headphones (HyperX Cloud II)". If the JVM only restored the Realtek default, the
     * PnP endpoint list still names the connected headset, including its Bluetooth transport.
     */
    private fun windowsOutput(): ConnectedHeadphone? {
        val output = runPowerShell(
            "Get-PnpDevice -Class AudioEndpoint -Status OK -ErrorAction SilentlyContinue | " +
                "Select-Object -ExpandProperty FriendlyName"
        )
        val bluetooth = runPowerShell(
            "Get-PnpDevice -Class Bluetooth -Status OK -ErrorAction SilentlyContinue | " +
                "Select-Object -ExpandProperty FriendlyName"
        )
        val btNames = bluetooth.lines().filter { it.isNotBlank() }
        val candidate = output.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && looksLikeHeadphone(it) }
            .firstOrNull() ?: return null
        val isBt = btNames.any { bt ->
            bt.length > 3 && candidate.contains(bt, ignoreCase = true)
        } || candidate.contains("bluetooth", ignoreCase = true) ||
            candidate.contains("wireless", ignoreCase = true)

        return ConnectedHeadphone(
            key = modelKey(candidate),
            name = cleanDeviceName(candidate),
            connectionLabel = if (isBt) "Bluetooth" else "USB / Wired",
            isBluetooth = isBt,
        )
    }

    /**
     * CoreAudio devices are visible through system_profiler even when the current JVM's mixer list
     * only has "Built-in Output". The command is only run when Java Sound found nothing.
     */
    private fun macOutput(): ConnectedHeadphone? {
        val text = runCommand(listOf("system_profiler", "SPAudioDataType"), 8)
        if (text.isBlank()) return null

        // system_profiler prints an indented block per device:
        //   HyperX Cloud II:
        //     ...
        //     Transport: USB
        var currentName: String? = null
        var currentTransport: String? = null
        fun flush(): ConnectedHeadphone? {
            val name = currentName ?: return null
            val transport = currentTransport.orEmpty()
            if (!looksLikeHeadphone(name) && !transport.contains("bluetooth", true) &&
                !transport.contains("usb", true)
            ) return null
            val isBt = transport.contains("bluetooth", true)
            return ConnectedHeadphone(
                key = modelKey(name),
                name = cleanDeviceName(name),
                connectionLabel = if (isBt) "Bluetooth" else "Wired / USB",
                isBluetooth = isBt,
            )
        }
        val lines = text.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (line.startsWith("      ") && trimmed.endsWith(":") && !trimmed.contains(": ")) {
                val previous = flush()
                if (previous != null) return previous
                currentName = trimmed.removeSuffix(":").trim()
                currentTransport = null
            } else if (trimmed.startsWith("Transport:")) {
                currentTransport = trimmed.substringAfter("Transport:").trim()
            }
        }
        return flush()
    }

    /** Java Sound fallback. It works on all three OSes, with whatever names the JVM exposes. */
    private fun javaMixerCandidate(requireHeadphone: Boolean = false): ConnectedHeadphone? {
        val playback = DataLine.Info(SourceDataLine::class.java, null)
        val candidates = runCatching {
            AudioSystem.getMixerInfo().mapNotNull { info ->
                val name = info.name.trim()
                if (name.isBlank() || name.contains("Port", ignoreCase = true)) return@mapNotNull null
                if (requireHeadphone && !looksLikeHeadphone(name)) return@mapNotNull null
                val supportsPlayback = runCatching {
                    AudioSystem.getMixer(info).isLineSupported(playback)
                }.getOrDefault(false)
                name.takeIf { supportsPlayback }
            }
        }.getOrDefault(emptyList())
        val name = candidates
            .sortedByDescending { scoreMixerName(it) }
            .firstOrNull() ?: return null
        val isBt = name.contains("bluetooth", true) || name.contains("wireless", true) ||
            name.contains("airpods", true) || name.contains("buds", true)
        return ConnectedHeadphone(
            key = modelKey(name),
            name = cleanDeviceName(name),
            connectionLabel = if (isBt) "Bluetooth" else "Wired / USB",
            isBluetooth = isBt,
        )
    }

    private fun scoreMixerName(name: String): Int {
        val lower = name.lowercase()
        var score = 0
        if (lower.contains("headphone")) score += 6
        if (lower.contains("headset")) score += 6
        if (lower.contains("bluetooth")) score += 5
        if (lower.contains("wireless")) score += 4
        if (lower.contains("airpods") || lower.contains("buds")) score += 5
        if (lower.contains("speaker")) score -= 4
        if (lower.contains("hdmi") || lower.contains("display")) score -= 5
        return score
    }

    private fun looksLikeHeadphone(name: String): Boolean {
        val lower = name.lowercase()
        val words = listOf(
            "headphone", "headset", "earphone", "earbud", "airpod", "buds", "casque",
            "kopfhörer", "наушник", "гарнитур", "tai nghe", "hyperx", "cloud ii",
            "bluetooth", "wireless", "wh-1000", "wf-1000", "quietcomfort", "soundcore",
        )
        if (words.any { lower.contains(it) }) return true
        // A bare model name (e.g. "HyperX Cloud II") is still a headset; Java mixers on Windows
        // often omit the word "Headphones" but keep the endpoint's product name.
        return lower.contains("hyperx") || lower.contains("cloud ii") || lower.contains("arctis")
    }

    private fun cleanDeviceName(raw: String): String {
        var name = raw.trim()
        // Strip Windows endpoint decorations: "Headphones (HyperX Cloud II)" -> "HyperX Cloud II".
        Regex("""^(Headphones|Headset|Speakers|Earphones)\s*\((.+)\)$""", RegexOption.IGNORE_CASE)
            .find(name)?.let { name = it.groupValues[2] }
        name = name.replace(Regex("""\s*\((?:Bluetooth|Wireless|Hands-Free AG Audio|Stereo)\)\s*$""", RegexOption.IGNORE_CASE), "")
        // PulseAudio appends the profile to the product name: "HyperX Virtual Surround Sound
        // Stéréo analogique". It is not part of the model name AutoEq matches on.
        name = name.replace(
            Regex(
                """\s+(?:St[ée]r[ée]o\s+(?:analogique|num[ée]rique)|Analog\s+Stereo|Digital\s+Stereo|Stereo\s+analogique)\s*$""",
                RegexOption.IGNORE_CASE,
            ),
            "",
        )
        return name.trim().ifBlank { raw.trim() }
    }

    /** Stable, URL-safe profile key based on the model name rather than a MAC address. */
    private fun modelKey(raw: String): String {
        val cleaned = cleanDeviceName(raw)
            .lowercase()
            .replace(Regex("""[^a-z0-9]+"""), "-")
            .trim('-')
        return cleaned.ifBlank { "unknown-headphone" }
    }

    private fun runPowerShell(script: String): String {
        val encoded = java.util.Base64.getEncoder()
            .encodeToString(script.toByteArray(Charsets.UTF_16LE))
        return runCommand(
            listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded),
            5,
        )
    }

    private fun runCommand(command: List<String>, timeoutSeconds: Long): String {
        return runCatching {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            val text = process.inputStream.bufferedReader().use { it.readText() }
            if (!finished) process.destroyForcibly()
            text
        }.getOrDefault("")
    }
}
