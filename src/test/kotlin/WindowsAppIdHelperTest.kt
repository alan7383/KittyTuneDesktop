import com.alananasss.kittytune.AppBootstrap
import com.alananasss.kittytune.core.WindowsAppIdHelper
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WindowsAppIdHelperTest {

    private val isWindows = System.getProperty("os.name").lowercase().contains("win")

    @Test
    fun testWindowsAppIdHelperConstants() {
        assertEquals("KittyTune.KittyTune", WindowsAppIdHelper.APP_ID)
    }

    @Test
    fun testWindowsAppIdHelperAppliesWithoutError() {
        WindowsAppIdHelper.apply()
        // Calling apply multiple times must be idempotent and safe
        WindowsAppIdHelper.apply()
        assertEquals("KittyTune.KittyTune", WindowsAppIdHelper.APP_ID)
    }

    @Test
    fun testEnsureShortcutsHaveAppIdWithoutExistingShortcuts() {
        // Should execute smoothly without throwing exceptions even if no shortcuts exist
        WindowsAppIdHelper.ensureShortcutsHaveAppId()
    }

    @Test
    fun testPatchShortcutsWithEmptyListDoesNotFail() {
        // Empty list must be a safe no-op
        WindowsAppIdHelper.patchShortcuts(emptyList())
        WindowsAppIdHelper.patchShortcuts(emptyList(), appId = "Custom.AppId", icoPath = "C:\\dummy\\icon.ico")
    }

    @Test
    fun testJvmStartupCleanWithoutWindowsAppIdHelperApply() {
        // Verify that JVM startup path operates cleanly
        AppBootstrap.init()
        val doneField = AppBootstrap::class.java.getDeclaredField("done").apply { isAccessible = true }
        assertTrue(doneField.getBoolean(AppBootstrap), "AppBootstrap.init() should mark initialization done without throwing")
    }

    @Test
    fun testIsKittyTuneShortcutDetection() {
        val method = WindowsAppIdHelper::class.java.getDeclaredMethod("isKittyTuneShortcut", File::class.java)
        method.isAccessible = true

        val tempDir = Files.createTempDirectory("kittytune_detect_test").toFile()
        try {
            // Case 1: Name contains "KittyTune"
            val fileByName1 = File(tempDir, "KittyTune.lnk").apply { writeText("dummy") }
            assertTrue(method.invoke(WindowsAppIdHelper, fileByName1) as Boolean)

            // Case 2: Name contains "Kitty Tune"
            val fileByName2 = File(tempDir, "Kitty Tune - Player.lnk").apply { writeText("dummy") }
            assertTrue(method.invoke(WindowsAppIdHelper, fileByName2) as Boolean)

            // Case 3: Binary contains "KittyTune.exe"
            val fileByBinary = File(tempDir, "shortcut_custom.lnk")
            fileByBinary.writeBytes("HeaderPrefix_KittyTune.exe_Suffix".toByteArray(Charsets.US_ASCII))
            assertTrue(method.invoke(WindowsAppIdHelper, fileByBinary) as Boolean)

            // Case 4: Unrelated shortcut
            val fileUnrelated = File(tempDir, "Notepad.lnk").apply { writeText("notepad.exe") }
            assertFalse(method.invoke(WindowsAppIdHelper, fileUnrelated) as Boolean)

            // Case 5: Non-existent file
            val fileNonExistent = File(tempDir, "ghost.lnk")
            assertFalse(method.invoke(WindowsAppIdHelper, fileNonExistent) as Boolean)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testPatchShortcutsUpdatesIconLocationAndPreservesAppUserModelId() {
        assumeTrue("Windows shortcut patching requires Windows OS", isWindows)

        val tempDir = Files.createTempDirectory("kittytune_patch_test").toFile()
        try {
            val testLnk = File(tempDir, "KittyTune.lnk")
            createTestShortcut(testLnk, "C:\\Windows\\notepad.exe")
            assertTrue(testLnk.exists(), "Test shortcut .lnk should have been created")

            val dummyIco1 = File(tempDir, "test_icon_variant_1.ico").apply {
                writeBytes(byteArrayOf(0x00, 0x00, 0x01, 0x00, 0x01, 0x00))
            }
            val dummyIco2 = File(tempDir, "test_icon_variant_2.ico").apply {
                writeBytes(byteArrayOf(0x00, 0x00, 0x01, 0x00, 0x01, 0x00))
            }

            // Step 1: Patch with initial icon and default AppUserModelID
            WindowsAppIdHelper.patchShortcuts(
                listOf(testLnk),
                appId = WindowsAppIdHelper.APP_ID,
                icoPath = dummyIco1.canonicalPath
            )

            val iconLoc1 = getShortcutIconLocation(testLnk)
            assertTrue(
                iconLoc1.contains("test_icon_variant_1.ico", ignoreCase = true),
                "Shortcut IconLocation should point to variant 1 ICO, but was: $iconLoc1"
            )

            // Verify AppUserModelID is set
            val aumid1 = getShortcutAppUserModelId(testLnk)
            assertEquals("KittyTune.KittyTune", aumid1, "PKEY_AppUserModel_ID must match KittyTune.KittyTune")

            // Step 2: Dynamic icon switch to variant 2
            WindowsAppIdHelper.patchShortcuts(
                listOf(testLnk),
                appId = WindowsAppIdHelper.APP_ID,
                icoPath = dummyIco2.canonicalPath
            )

            // Verify icon location updated to dummyIco2
            val iconLoc2 = getShortcutIconLocation(testLnk)
            assertTrue(
                iconLoc2.contains("test_icon_variant_2.ico", ignoreCase = true),
                "Shortcut IconLocation should update to variant 2 ICO, but was: $iconLoc2"
            )

            // Verify AppUserModelID is STILL preserved as KittyTune.KittyTune
            val aumid2 = getShortcutAppUserModelId(testLnk)
            assertEquals("KittyTune.KittyTune", aumid2, "PKEY_AppUserModel_ID must be preserved after icon switch")

            // Binary verification: ensure AppUserModelID UTF-16LE sequence is present in file
            val bytes = testLnk.readBytes()
            val needle = "KittyTune.KittyTune".toByteArray(Charsets.UTF_16LE)
            assertTrue(
                containsSubarray(bytes, needle),
                "Shortcut binary must contain serialized AppUserModelID UTF-16LE bytes"
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testPatchShortcutsWithNullIcoPathPreservesAppUserModelId() {
        assumeTrue("Windows shortcut patching requires Windows OS", isWindows)

        val tempDir = Files.createTempDirectory("kittytune_patch_null_ico").toFile()
        try {
            val testLnk = File(tempDir, "KittyTune.lnk")
            createTestShortcut(testLnk, "C:\\Windows\\notepad.exe")

            // Patch with null icoPath
            WindowsAppIdHelper.patchShortcuts(
                listOf(testLnk),
                appId = "KittyTune.CustomId",
                icoPath = null
            )

            val aumid = getShortcutAppUserModelId(testLnk)
            assertEquals("KittyTune.CustomId", aumid, "PKEY_AppUserModel_ID should be set even when icoPath is null")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // --- Helper functions for Windows .lnk shortcut manipulation ---

    private fun createTestShortcut(targetLnk: File, targetExe: String): File {
        val psScript = File.createTempFile("create_lnk_", ".ps1").apply { deleteOnExit() }
        psScript.writeText(
            """
            param([string]${'$'}lnk, [string]${'$'}target)
            ${'$'}wsh = New-Object -ComObject WScript.Shell
            ${'$'}s = ${'$'}wsh.CreateShortcut(${'$'}lnk)
            ${'$'}s.TargetPath = ${'$'}target
            ${'$'}s.Save()
            """.trimIndent()
        )
        try {
            val pb = ProcessBuilder(
                "powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-File", psScript.absolutePath, targetLnk.canonicalPath, targetExe
            ).apply { redirectErrorStream(true) }
            val p = pb.start()
            p.waitFor()
        } finally {
            psScript.delete()
        }
        return targetLnk
    }

    private fun getShortcutIconLocation(lnk: File): String {
        val psScript = File.createTempFile("read_icon_", ".ps1").apply { deleteOnExit() }
        psScript.writeText(
            """
            param([string]${'$'}lnk)
            ${'$'}wsh = New-Object -ComObject WScript.Shell
            ${'$'}s = ${'$'}wsh.CreateShortcut(${'$'}lnk)
            Write-Output ${'$'}s.IconLocation
            """.trimIndent()
        )
        try {
            val pb = ProcessBuilder(
                "powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-File", psScript.absolutePath, lnk.canonicalPath
            ).apply { redirectErrorStream(true) }
            val p = pb.start()
            val output = p.inputStream.bufferedReader().readText().trim()
            p.waitFor()
            return output
        } finally {
            psScript.delete()
        }
    }

    private fun getShortcutAppUserModelId(lnk: File): String {
        val psScript = File.createTempFile("read_aumid_", ".ps1").apply { deleteOnExit() }
        psScript.writeText(
            """
            param([string]${'$'}lnk)
            ${'$'}code = @'
            using System;
            using System.Text;
            using System.Runtime.InteropServices;
            using System.Runtime.InteropServices.ComTypes;

            [ComImport, Guid("00021401-0000-0000-C000-000000000046")]
            public class ShellLinkCoClassTestReader {}

            [ComImport, Guid("886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
            public interface IPropertyStoreTestReader {
                void GetCount(out uint cProps);
                void GetAt(uint iProp, out PROPERTYKEYTestReader pkey);
                void GetValue(ref PROPERTYKEYTestReader key, out PROPVARIANTTestReader pv);
                void SetValue(ref PROPERTYKEYTestReader key, ref PROPVARIANTTestReader pv);
                void Commit();
            }

            [StructLayout(LayoutKind.Sequential)]
            public struct PROPERTYKEYTestReader {
                public Guid fmtid;
                public uint pid;
            }

            [StructLayout(LayoutKind.Explicit, Size = 24)]
            public struct PROPVARIANTTestReader {
                [FieldOffset(0)] public ushort vt;
                [FieldOffset(8)] public IntPtr pwszVal;
            }

            public class AumidReader {
                static readonly Guid AppIdGuid = new Guid("9F4C2855-9F79-4B39-A8D0-E1D42DE1D5F3");

                public static string Read(string path) {
                    try {
                        var link = (IPersistFile)new ShellLinkCoClassTestReader();
                        link.Load(path, 0); // STGM_READ
                        var store = (IPropertyStoreTestReader)link;
                        var key = new PROPERTYKEYTestReader { fmtid = AppIdGuid, pid = 5 };
                        PROPVARIANTTestReader val;
                        store.GetValue(ref key, out val);
                        if (val.vt == 31 && val.pwszVal != IntPtr.Zero) {
                            return Marshal.PtrToStringUni(val.pwszVal);
                        }
                        return "";
                    } catch (Exception ex) {
                        return "ERROR: " + ex.Message;
                    }
                }
            }
            '@
            Add-Type -TypeDefinition ${'$'}code
            Write-Output ([AumidReader]::Read(${'$'}lnk))
            """.trimIndent()
        )
        try {
            val pb = ProcessBuilder(
                "powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-File", psScript.absolutePath, lnk.canonicalPath
            ).apply { redirectErrorStream(true) }
            val p = pb.start()
            val output = p.inputStream.bufferedReader().readText().trim()
            p.waitFor()
            return output
        } finally {
            psScript.delete()
        }
    }

    private fun containsSubarray(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty() || haystack.size < needle.size) return false
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return true
        }
        return false
    }
}
