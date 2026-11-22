package com.alananasss.kittytune.data

import com.alananasss.kittytune.data.network.GithubAsset
import org.junit.Assert.*
import org.junit.Test

class LinuxUpdateAssetsTest {
    private fun asset(name: String) = GithubAsset(name, "api", "download", "application/octet-stream", 123L)
    private fun choose(arch: String, family: LinuxPackageFamily, vararg names: String) =
        selectLinuxUpdateAsset(names.map(::asset), arch, family)?.name

    @Test fun unknownDistroChoosesTheMatchingAppImageArchitecture() {
        assertEquals("KittyTune-x86_64.AppImage", choose("amd64", LinuxPackageFamily.OTHER, "KittyTune-aarch64.AppImage", "KittyTune-x86_64.AppImage"))
        assertEquals("KittyTune-aarch64.AppImage", choose("arm64", LinuxPackageFamily.OTHER, "KittyTune-x86_64.AppImage", "KittyTune-aarch64.AppImage"))
    }

    @Test fun mismatchedPackagesAreNeverUsedAsFallback() {
        assertNull(choose("amd64", LinuxPackageFamily.DEBIAN, "kitty-tune_arm64.deb"))
        assertNull(choose("aarch64", LinuxPackageFamily.RPM, "kitty-tune.x86_64.rpm"))
        assertNull(choose("amd64", LinuxPackageFamily.OTHER, "KittyTune-armhf.AppImage"))
    }

    @Test fun unsupportedHostArchitectureDoesNotDownloadAnUnusableBuild() {
        for (arch in listOf("armv7l", "i686", "riscv64"))
            assertNull(choose(arch, LinuxPackageFamily.OTHER, "KittyTune-x86_64.AppImage", "KittyTune-aarch64.AppImage"))
    }

    @Test fun matchingAppImageIsTheFallbackWhenTheNativePackageIsMissing() {
        assertEquals("KittyTune-x64.AppImage", choose("x86_64", LinuxPackageFamily.DEBIAN, "kitty-tune_arm64.deb", "KittyTune-x64.AppImage"))
    }

    @Test fun nativeFormatWinsAndTaggedAssetsBeatLegacyUntaggedOnes() {
        assertEquals("kitty-tune.amd64.rpm", choose("x86_64", LinuxPackageFamily.RPM, "KittyTune-x64.AppImage", "kitty-tune.rpm", "kitty-tune.amd64.rpm"))
        assertEquals("kitty-tune.deb", choose("amd64", LinuxPackageFamily.DEBIAN, "kitty-tune.deb"))
        assertEquals("KittyTune.AppImage", choose("aarch64", LinuxPackageFamily.OTHER, "KittyTune.AppImage"))
    }

    @Test fun selectionIsIndependentOfMixedArchitectureReleaseOrder() {
        val names = listOf("kitty-tune_arm64.deb", "kitty-tune_amd64.deb", "KittyTune-aarch64.AppImage", "KittyTune-x86_64.AppImage")
        for (seed in 0..20) {
            val shuffled = names.shuffled(kotlin.random.Random(seed))
            assertEquals("kitty-tune_amd64.deb", selectLinuxUpdateAsset(shuffled.map(::asset), "AMD64", LinuxPackageFamily.DEBIAN)?.name)
            assertEquals("KittyTune-aarch64.AppImage", selectLinuxUpdateAsset(shuffled.map(::asset), "aarch64", LinuxPackageFamily.OTHER)?.name)
        }
    }

    @Test fun distroAncestryRecognizesExistingPackageFamilies() {
        for (id in listOf("opensuse", "suse", "fedora", "rhel", "centos"))
            assertEquals(id, LinuxPackageFamily.RPM, linuxPackageFamily("ID=derivative\nID_LIKE=\"$id\"", false, false, false))
        assertEquals(LinuxPackageFamily.DEBIAN, linuxPackageFamily("ID=linuxmint\nID_LIKE='ubuntu debian'", false, false, false))
        assertEquals(LinuxPackageFamily.ARCH, linuxPackageFamily("ID=derivative\nID_LIKE=arch", false, false, false))
    }

    @Test fun explicitMetadataPrecedesMarkerFallbacksAndClosestAncestorWins() {
        assertEquals(LinuxPackageFamily.RPM, linuxPackageFamily("ID=opensuse\nID_LIKE=debian", false, true, false))
        assertEquals(LinuxPackageFamily.DEBIAN, linuxPackageFamily("ID=unknown\nID_LIKE=\"debian arch\"", false, false, false))
        assertEquals(LinuxPackageFamily.ARCH, linuxPackageFamily("", true, false, false))
    }

    @Test fun unknownMetadataNeverChoosesAnArbitraryInstaller() {
        assertEquals(LinuxPackageFamily.OTHER, linuxPackageFamily("# ID=debian\nID=unknown\nNAME=Fedora", false, false, false))
        assertNull(choose("amd64", LinuxPackageFamily.OTHER, "readme.txt", "kitty-tune_amd64.deb"))
    }

    @Test fun derivativeDistroMetadataUsesTheClosestSupportedFamily() {
        for ((id, ancestors, expected) in listOf(
            Triple("opensuse-tumbleweed", "opensuse suse", LinuxPackageFamily.RPM),
            Triple("opensuse-leap", "suse opensuse", LinuxPackageFamily.RPM),
            Triple("rocky", "rhel centos fedora", LinuxPackageFamily.RPM),
            Triple("almalinux", "rhel centos fedora", LinuxPackageFamily.RPM),
            Triple("endeavouros", "arch", LinuxPackageFamily.ARCH),
            Triple("linuxmint", "ubuntu debian", LinuxPackageFamily.DEBIAN),
        )) assertEquals(id, expected, linuxPackageFamily("ID=$id\nID_LIKE=\"$ancestors\"", false, false, false))
    }
}
