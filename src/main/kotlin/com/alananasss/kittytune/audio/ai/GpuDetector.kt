package com.alananasss.kittytune.audio.ai

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * GPU vendor classification for UI badges and driver provider dispatch.
 */
enum class GpuVendor(val displayName: String) {
    AMD("AMD"),
    NVIDIA("NVIDIA"),
    INTEL("Intel"),
    APPLE("Apple"),
    OTHER("Other");

    companion object {
        fun fromString(text: String): GpuVendor {
            val lower = text.lowercase(Locale.ROOT)
            return when {
                lower.contains("amd") || lower.contains("radeon") || lower.contains("ati") -> AMD
                lower.contains("nvidia") || lower.contains("geforce") || lower.contains("rtx") || lower.contains("gtx") -> NVIDIA
                lower.contains("intel") || lower.contains("iris") || lower.contains("arc") || lower.contains("uhd") -> INTEL
                lower.contains("apple") || lower.contains("m1") || lower.contains("m2") || lower.contains("m3") || lower.contains("m4") -> APPLE
                else -> OTHER
            }
        }
    }
}

/**
 * Represents a GPU card or compute device detected on the system.
 */
data class GpuDevice(
    val id: String,
    val name: String,
    val vendor: GpuVendor,
    val isDiscrete: Boolean,
    val driverInfo: String = "",
    val apiSupport: String = "",
) {
    val isSpecial: Boolean get() = id == ID_AUTO || id == ID_CPU

    companion object {
        const val ID_AUTO = "auto"
        const val ID_CPU = "cpu"

        val AUTO = GpuDevice(
            id = ID_AUTO,
            name = "Auto (Best Available GPU)",
            vendor = GpuVendor.OTHER,
            isDiscrete = false,
            apiSupport = "DirectML / Vulkan / GPU",
        )

        val CPU = GpuDevice(
            id = ID_CPU,
            name = "CPU Only (Software)",
            vendor = GpuVendor.OTHER,
            isDiscrete = false,
            apiSupport = "SIMD / XNNPACK",
        )
    }
}

/**
 * Hardware detection service for identifying graphics cards and accelerators
 * across Linux (Vulkan/DRM/lspci), Windows (DirectX/DXGI/PowerShell), and macOS (Metal).
 *
 * Safe and non-blocking: executes lightweight system queries with strict timeouts
 * and caches results in-memory.
 */
object GpuDetector {

    private val lock = Any()
    private var cachedGpus: List<GpuDevice>? = null

    /**
     * Get all detected physical GPU devices.
     */
    fun getDetectedGpus(forceRefresh: Boolean = false): List<GpuDevice> {
        synchronized(lock) {
            if (!forceRefresh && cachedGpus != null) {
                return cachedGpus!!
            }
            val detected = detectSystemGpus()
            cachedGpus = detected
            return detected
        }
    }

    /**
     * Resolves the user-selected GPU ID into an actual [GpuDevice].
     * "auto" resolves to the best discrete GPU found (or first integrated GPU, or CPU).
     */
    fun resolveGpu(selectedId: String?): GpuDevice {
        val gpus = getDetectedGpus()
        if (selectedId == null || selectedId == GpuDevice.ID_AUTO) {
            // Prefer discrete GPU
            val discrete = gpus.firstOrNull { it.isDiscrete }
            if (discrete != null) return discrete
            val first = gpus.firstOrNull()
            if (first != null) return first
            return GpuDevice.AUTO
        }
        if (selectedId == GpuDevice.ID_CPU) {
            return GpuDevice.CPU
        }
        return gpus.firstOrNull { it.id == selectedId } ?: resolveGpu(GpuDevice.ID_AUTO)
    }

    private fun detectSystemGpus(): List<GpuDevice> {
        val os = System.getProperty("os.name", "").lowercase(Locale.ROOT)
        return try {
            when {
                os.contains("win") -> detectWindowsGpus()
                os.contains("mac") -> detectMacGpus()
                else -> detectLinuxGpus()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun detectLinuxGpus(): List<GpuDevice> {
        // 1. Try vulkaninfo --summary
        val vulkanDevices = detectVulkanGpus()
        if (vulkanDevices.isNotEmpty()) {
            return vulkanDevices
        }

        // 2. Fallback to lspci
        return detectLspciGpus()
    }

    private fun detectVulkanGpus(): List<GpuDevice> {
        val devices = mutableListOf<GpuDevice>()
        val output = runCommandWithTimeout(listOf("vulkaninfo", "--summary"), timeoutSeconds = 2) ?: return emptyList()

        var currentId = ""
        var currentName = ""
        var currentType = ""
        var currentDriver = ""
        var currentVendorId = ""
        var currentDriverInfo = ""
        var inDevices = false

        for (line in output.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Devices:")) {
                inDevices = true
                continue
            }
            if (!inDevices) continue

            if (trimmed.matches(Regex("GPU\\d+:"))) {
                if (currentName.isNotEmpty()) {
                    devices.add(createVulkanGpu(devices.size, currentName, currentType, currentVendorId, currentDriver, currentDriverInfo))
                }
                currentId = trimmed.removeSuffix(":")
                currentName = ""
                currentType = ""
                currentDriver = ""
                currentVendorId = ""
                currentDriverInfo = ""
                continue
            }

            val parts = trimmed.split("=", limit = 2).map { it.trim() }
            if (parts.size == 2) {
                when (parts[0]) {
                    "deviceName" -> currentName = parts[1]
                    "deviceType" -> currentType = parts[1]
                    "vendorID" -> currentVendorId = parts[1]
                    "driverName" -> currentDriver = parts[1]
                    "driverInfo" -> currentDriverInfo = parts[1]
                }
            }
        }

        if (currentName.isNotEmpty()) {
            devices.add(createVulkanGpu(devices.size, currentName, currentType, currentVendorId, currentDriver, currentDriverInfo))
        }

        return devices
    }

    private fun createVulkanGpu(
        index: Int,
        rawName: String,
        deviceType: String,
        vendorId: String,
        driverName: String,
        driverInfo: String,
    ): GpuDevice {
        val isDiscrete = deviceType.contains("DISCRETE", ignoreCase = true)
        val vendor = when {
            vendorId.equals("0x1002", ignoreCase = true) -> GpuVendor.AMD
            vendorId.equals("0x10de", ignoreCase = true) -> GpuVendor.NVIDIA
            vendorId.equals("0x8086", ignoreCase = true) -> GpuVendor.INTEL
            else -> GpuVendor.fromString(rawName)
        }
        val cleanDriver = listOf(driverName, driverInfo).filter { it.isNotBlank() }.joinToString(" - ")
        return GpuDevice(
            id = "gpu:$index",
            name = rawName,
            vendor = vendor,
            isDiscrete = isDiscrete,
            driverInfo = cleanDriver,
            apiSupport = "Vulkan / DirectML",
        )
    }

    private fun detectLspciGpus(): List<GpuDevice> {
        val devices = mutableListOf<GpuDevice>()
        val output = runCommandWithTimeout(listOf("lspci"), timeoutSeconds = 2) ?: return emptyList()

        var index = 0
        for (line in output.lines()) {
            val lower = line.lowercase(Locale.ROOT)
            if (lower.contains("vga compatible controller") || lower.contains("3d controller") || lower.contains("display controller")) {
                val colonIdx = line.indexOf(": ")
                val desc = if (colonIdx >= 0) line.substring(colonIdx + 2).trim() else line.trim()
                val vendor = GpuVendor.fromString(desc)

                // Clean name e.g. "Advanced Micro Devices, Inc. [AMD/ATI] Navi 32 [Radeon RX 7700 XT / 7800 XT]"
                val cleanName = parsePciName(desc)
                val isDiscrete = vendor == GpuVendor.NVIDIA ||
                    cleanName.contains("Radeon RX", ignoreCase = true) ||
                    cleanName.contains("Arc", ignoreCase = true) ||
                    cleanName.contains("GeForce", ignoreCase = true)

                devices.add(
                    GpuDevice(
                        id = "gpu:$index",
                        name = cleanName,
                        vendor = vendor,
                        isDiscrete = isDiscrete,
                        driverInfo = "PCI",
                        apiSupport = "Vulkan / DirectML",
                    )
                )
                index++
            }
        }
        return devices
    }

    private fun parsePciName(desc: String): String {
        // Extract inner bracket name if available, e.g. [Radeon RX 7700 XT / 7800 XT]
        val matches = Regex("\\[(.*?)\\]").findAll(desc).map { it.groupValues[1] }.toList()
        return when {
            matches.size >= 2 -> "${matches[0].substringBefore('/')} ${matches[1]}"
            matches.size == 1 -> matches[0]
            else -> desc.substringAfter(": ").ifEmpty { desc }
        }
    }

    private fun detectWindowsGpus(): List<GpuDevice> {
        val vulkanDevices = detectVulkanGpus()
        if (vulkanDevices.isNotEmpty()) {
            return vulkanDevices.map { it.copy(apiSupport = "DirectML (DirectX 12) / Vulkan") }
        }

        val devices = mutableListOf<GpuDevice>()
        val cmd = listOf("powershell", "-NoProfile", "-Command", "Get-CimInstance Win32_VideoController | Select-Object -ExpandProperty Name")
        var output = runCommandWithTimeout(cmd, timeoutSeconds = 3)
        if (output.isNullOrBlank()) {
            output = runCommandWithTimeout(listOf("wmic", "path", "win32_VideoController", "get", "name"), timeoutSeconds = 3)
        }
        if (output == null) return emptyList()

        var index = 0
        for (line in output.lines()) {
            val name = line.trim()
            if (name.isBlank() || name.equals("Name", ignoreCase = true)) continue
            val vendor = GpuVendor.fromString(name)
            val isDiscrete = vendor == GpuVendor.NVIDIA ||
                name.contains("Radeon RX", ignoreCase = true) ||
                name.contains("Radeon Pro", ignoreCase = true) ||
                name.contains("Arc", ignoreCase = true) ||
                name.contains("GeForce", ignoreCase = true) ||
                name.contains("RTX", ignoreCase = true) ||
                name.contains("GTX", ignoreCase = true)

            devices.add(
                GpuDevice(
                    id = "gpu:$index",
                    name = name,
                    vendor = vendor,
                    isDiscrete = isDiscrete,
                    driverInfo = "WDDM",
                    apiSupport = "DirectML (DirectX 12)",
                )
            )
            index++
        }
        return devices
    }

    private fun detectMacGpus(): List<GpuDevice> {
        val devices = mutableListOf<GpuDevice>()
        val output = runCommandWithTimeout(listOf("system_profiler", "SPDisplaysDataType"), timeoutSeconds = 3) ?: return emptyList()
        var index = 0
        for (line in output.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Chipset Model:", ignoreCase = true)) {
                val name = trimmed.substringAfter(":").trim()
                val vendor = GpuVendor.fromString(name)
                devices.add(
                    GpuDevice(
                        id = "gpu:$index",
                        name = name,
                        vendor = vendor,
                        isDiscrete = vendor != GpuVendor.INTEL,
                        driverInfo = "Metal",
                        apiSupport = "Metal / CoreML",
                    )
                )
                index++
            }
        }
        return devices
    }

    private fun runCommandWithTimeout(command: List<String>, timeoutSeconds: Long): String? {
        return try {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                return null
            }
            if (process.exitValue() != 0) {
                return null
            }
            BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }
}
