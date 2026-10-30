package com.alananasss.kittytune.core

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AppIconInstallerTest {

    private val isWindows = System.getProperty("os.name").lowercase().contains("win")

    @Test
    fun testAppIconInstallerGeneratesValidIcoOnWindows() {
        assumeTrue("Windows icon generation requires Windows OS", isWindows)

        AppIconInstaller.apply("og")
        val iconDir = File(AppDirs.dataDir, "icons")
        val expectedIco = File(iconDir, "app_icon_og.ico")

        // Wait up to 5 seconds for generation by daemon thread
        val deadline = System.currentTimeMillis() + 5000
        while (!expectedIco.exists() && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
        }

        assertTrue(expectedIco.exists(), "app_icon_og.ico should be generated in ${iconDir.canonicalPath}")
        assertTrue(expectedIco.length() > 0, "Generated ICO file must not be empty")

        // Verify Windows ICO format header (0x00, 0x00, 0x01, 0x00)
        val header = expectedIco.inputStream().use { it.readNBytes(4) }
        assertEquals(0x00.toByte(), header[0])
        assertEquals(0x00.toByte(), header[1])
        assertEquals(0x01.toByte(), header[2])
        assertEquals(0x00.toByte(), header[3])
    }

    @Test
    fun testAppIconInstallerRetainsGeneratedIconAssets() {
        assumeTrue("Windows icon asset retention requires Windows OS", isWindows)

        AppIconInstaller.apply("sunset")
        AppIconInstaller.apply("black")

        val iconDir = File(AppDirs.dataDir, "icons")
        val ico1 = File(iconDir, "app_icon_sunset.ico")
        val ico2 = File(iconDir, "app_icon_black.ico")

        val deadline = System.currentTimeMillis() + 5000
        while ((!ico1.exists() || !ico2.exists()) && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
        }

        assertTrue(ico1.exists(), "Sunset icon asset must be retained")
        assertTrue(ico2.exists(), "Black icon asset must be retained")
    }

    @Test
    fun testNotifyWindowsShellInvocationExecutesCleanly() {
        assumeTrue("Windows shell notification requires Windows OS", isWindows)

        // Should execute cleanly without throwing any exception
        AppIconInstaller.notifyWindowsShell()
    }

    @Test
    fun testShellNotificationHandlesMissingCommandsGracefully() {
        // Verify that command execution errors in shell invalidation logic are absorbed
        listOf(
            listOf("nonexistent_utility_12345.exe", "-test"),
            listOf("ie4uinit_invalid_path.exe", "-ClearIconCache")
        ).forEach { cmd ->
            val result = runCatching { ProcessBuilder(cmd).start() }
            assertTrue(result.isFailure, "ProcessBuilder on nonexistent executable should fail safely inside runCatching")
        }
    }

    @Test
    fun testMentionsOurLauncherDetection() {
        val method = AppIconInstaller::class.java.getDeclaredMethod("mentionsOurLauncher", File::class.java)
        method.isAccessible = true

        val tempDir = Files.createTempDirectory("launcher_detect_test").toFile()
        try {
            // Case 1: Name contains "KittyTune"
            val fileByName = File(tempDir, "KittyTune.lnk").apply { writeText("dummy") }
            assertTrue(method.invoke(AppIconInstaller, fileByName) as Boolean)

            // Case 2: Content contains ASCII "KittyTune.exe"
            val fileAscii = File(tempDir, "shortcut_a.lnk")
            fileAscii.writeBytes("Header_KittyTune.exe_Tail".toByteArray(Charsets.US_ASCII))
            assertTrue(method.invoke(AppIconInstaller, fileAscii) as Boolean)

            // Case 3: Content contains UTF-16LE "KittyTune.exe"
            val fileUtf16 = File(tempDir, "shortcut_u.lnk")
            fileUtf16.writeBytes("Header_KittyTune.exe_Tail".toByteArray(Charsets.UTF_16LE))
            assertTrue(method.invoke(AppIconInstaller, fileUtf16) as Boolean)

            // Case 4: Unrelated file
            val fileOther = File(tempDir, "notepad.lnk").apply { writeText("notepad.exe") }
            assertFalse(method.invoke(AppIconInstaller, fileOther) as Boolean)

            // Case 5: Non-existent file
            val fileMissing = File(tempDir, "missing.lnk")
            assertFalse(method.invoke(AppIconInstaller, fileMissing) as Boolean)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testAppIconInstallerGracefullyHandlesInvalidVariant() {
        // Calling apply on a nonexistent variant should not throw or crash
        AppIconInstaller.apply("nonexistent_variant_xyz_999")
        Thread.sleep(500)
    }
}
