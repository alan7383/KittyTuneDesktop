package com.alananasss.kittytune.data

import com.alananasss.kittytune.data.network.GithubAsset

internal enum class LinuxPackageFamily { ARCH, DEBIAN, RPM, OTHER }

internal fun linuxPackageFamily(osRelease: String, archMarker: Boolean, debianMarker: Boolean, rpmMarker: Boolean): LinuxPackageFamily {
    val fields = osRelease.lineSequence().map(String::trim).filterNot { it.startsWith('#') }
        .mapNotNull { line ->
            val pair = line.split('=', limit = 2)
            if (pair.size == 2 && pair[0] in setOf("ID", "ID_LIKE"))
                pair[0] to pair[1].trim().removeSurrounding("\"").removeSurrounding("'").lowercase()
            else null
        }.toMap()
    val ancestry = listOfNotNull(fields["ID"]) + fields["ID_LIKE"].orEmpty().split(Regex("\\s+"))
    for (id in ancestry) {
        when (id) {
            "arch" -> return LinuxPackageFamily.ARCH
            "debian", "ubuntu" -> return LinuxPackageFamily.DEBIAN
            "fedora", "rhel", "centos", "opensuse", "suse" -> return LinuxPackageFamily.RPM
        }
    }
    return when {
        archMarker -> LinuxPackageFamily.ARCH
        debianMarker -> LinuxPackageFamily.DEBIAN
        rpmMarker -> LinuxPackageFamily.RPM
        else -> LinuxPackageFamily.OTHER
    }
}

private fun supportedArchitecture(arch: String): String? = when (arch.lowercase()) {
    "amd64", "x86_64", "x64" -> "x64"
    "aarch64", "arm64" -> "arm64"
    else -> null
}

private val architectureLabel = Regex(
    "(?:^|[-_.])(x86_64|amd64|x64|aarch64|arm64|armv[5-8][a-z]*|armhf|armel|i[3-6]86|x86|riscv[0-9]+|ppc[0-9]+(?:le)?|s390x|mips(?:64)?(?:el)?|loongarch64)(?=$|[-_.])",
    RegexOption.IGNORE_CASE,
)

internal fun selectLinuxUpdateAsset(assets: List<GithubAsset>, osArch: String, family: LinuxPackageFamily): GithubAsset? {
    val host = supportedArchitecture(osArch) ?: return null
    val candidates = assets.mapNotNull { asset ->
        val labels = architectureLabel.findAll(asset.name).map { supportedArchitecture(it.groupValues[1]) }.toList()
        if (labels.any { it != host }) null else asset to labels.isNotEmpty()
    }
    val extensions = when (family) {
        LinuxPackageFamily.ARCH -> listOf(".pkg.tar.zst", ".AppImage")
        LinuxPackageFamily.DEBIAN -> listOf(".deb", ".AppImage")
        LinuxPackageFamily.RPM -> listOf(".rpm", ".AppImage")
        LinuxPackageFamily.OTHER -> listOf(".AppImage")
    }
    for (extension in extensions) {
        val matching = candidates.filter { it.first.name.endsWith(extension, true) }
        (matching.firstOrNull { it.second } ?: matching.firstOrNull())?.let { return it.first }
    }
    return null
}
