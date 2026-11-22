package com.alananasss.kittytune.audio.ai.karaoke

import org.junit.Test
import kotlin.test.*

class NativeDemucsPlatformTest {
    @Test fun `linux x64 aliases select the published GNU asset`() {
        for (arch in listOf("amd64", "x86_64", "x64")) {
            val platform = assertNotNull(nativeDemucsPlatform("Linux", arch))
            assertEquals("linux-x86_64", platform.resourceSubdir)
            assertEquals("demucs-x86_64-unknown-linux-gnu.tar.gz", platform.archive)
        }
    }

    @Test fun `windows x64 retains its executable and archive`() {
        val platform = assertNotNull(nativeDemucsPlatform("Windows 11", "amd64"))
        assertEquals("demucs.exe", platform.executable)
        assertEquals("demucs-x86_64-pc-windows-msvc.zip", platform.archive)
    }

    @Test fun `apple silicon aliases select the published mac asset`() {
        for (arch in listOf("aarch64", "arm64")) {
            val platform = assertNotNull(nativeDemucsPlatform("Mac OS X", arch))
            assertEquals("macos-aarch64", platform.resourceSubdir)
            assertEquals("demucs-aarch64-apple-darwin.tar.gz", platform.archive)
        }
    }

    @Test fun `unsupported architectures never select an incompatible helper`() {
        for ((os, arch) in listOf("Linux" to "aarch64", "Linux" to "arm64", "Linux" to "i386",
            "Mac OS X" to "x86_64", "Windows 11" to "aarch64", "FreeBSD" to "amd64")) {
            assertNull(nativeDemucsPlatform(os, arch), "$os $arch")
        }
    }

    @Test fun `darwin is not treated as windows`() {
        assertNull(nativeDemucsPlatform("Darwin", "aarch64"))
    }
}
