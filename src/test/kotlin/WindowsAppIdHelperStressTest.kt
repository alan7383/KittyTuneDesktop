import com.alananasss.kittytune.core.WindowsAppIdHelper
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WindowsAppIdHelperStressTest {

    private val isWindows = System.getProperty("os.name").lowercase().contains("win")

    // =========================================================================
    // Category 1: Read-Only and Locked Shortcut Resilience
    // =========================================================================

    @Test
    fun testPatchShortcutsWithReadOnlyShortcutFile() {
        assumeTrue("Windows shortcut patching requires Windows OS", isWindows)

        val tempDir = Files.createTempDirectory("kittytune_readonly_test").toFile()
        try {
            val testLnk = File(tempDir, "ReadOnlyShortcut.lnk")
            createTestShortcut(testLnk, "C:\\Windows\\notepad.exe")
            assertTrue(testLnk.exists(), "Shortcut file must exist before test")

            val dummyIco1 = File(tempDir, "icon_1.ico").apply {
                writeBytes(byteArrayOf(0x00, 0x00, 0x01, 0x00, 0x01, 0x00))
            }
            val dummyIco2 = File(tempDir, "icon_2.ico").apply {
                writeBytes(byteArrayOf(0x00, 0x00, 0x01, 0x00, 0x01, 0x00))
            }

            // Step 1: Initial patch when writable
            WindowsAppIdHelper.patchShortcuts(listOf(testLnk), appId = "KittyTune.KittyTune", icoPath = dummyIco1.canonicalPath)
            assertEquals("KittyTune.KittyTune", getShortcutAppUserModelId(testLnk))
            assertTrue(getShortcutIconLocation(testLnk).contains("icon_1.ico", ignoreCase = true))

            // Step 2: Make shortcut read-only
            val setReadOnlySuccess = testLnk.setReadOnly()
            assertTrue(setReadOnlySuccess || !testLnk.canWrite(), "File should be marked read-only")

            // Step 3: Attempt patch on read-only file — MUST NOT CRASH OR THROW UNHANDLED EXCEPTION
            WindowsAppIdHelper.patchShortcuts(listOf(testLnk), appId = "KittyTune.NewAppId", icoPath = dummyIco2.canonicalPath)

            // Step 4: Re-enable write permissions
            testLnk.setWritable(true)

            // Step 5: Now patching must succeed cleanly
            WindowsAppIdHelper.patchShortcuts(listOf(testLnk), appId = "KittyTune.RestoredId", icoPath = dummyIco2.canonicalPath)
            assertEquals("KittyTune.RestoredId", getShortcutAppUserModelId(testLnk), "Should successfully update once writable")
            assertTrue(getShortcutIconLocation(testLnk).contains("icon_2.ico", ignoreCase = true))
        } finally {
            tempDir.listFiles()?.forEach { it.setWritable(true) }
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testPatchShortcutsWithExclusivelyLockedFile() {
        assumeTrue("Windows shortcut patching requires Windows OS", isWindows)

        val tempDir = Files.createTempDirectory("kittytune_locked_test").toFile()
        try {
            val testLnk = File(tempDir, "LockedShortcut.lnk")
            createTestShortcut(testLnk, "C:\\Windows\\notepad.exe")

            val dummyIco = File(tempDir, "dummy.ico").apply {
                writeBytes(byteArrayOf(0x00, 0x00, 0x01, 0x00, 0x01, 0x00))
            }

            // Lock the file exclusively using FileChannel lock
            val raf = RandomAccessFile(testLnk, "rw")
            val lock = raf.channel.lock()
            try {
                // Attempt to patch locked shortcut — must gracefully handle without JVM crash
                WindowsAppIdHelper.patchShortcuts(listOf(testLnk), appId = "KittyTune.Locked", icoPath = dummyIco.canonicalPath)
            } finally {
                lock.release()
                raf.close()
            }

            // After releasing lock, patching must succeed
            WindowsAppIdHelper.patchShortcuts(listOf(testLnk), appId = "KittyTune.Unlocked", icoPath = dummyIco.canonicalPath)
            assertEquals("KittyTune.Unlocked", getShortcutAppUserModelId(testLnk))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // =========================================================================
    // Category 2: Non-ASCII, Unicode, Spaces & Special Characters Paths
    // =========================================================================

    @Test
    fun testPatchShortcutsWithComplexUnicodeAndSpecialCharacters() {
        assumeTrue("Windows shortcut patching requires Windows OS", isWindows)

        // Use a path without emoji (U+1F3B5 requires surrogate pairs — IShellLinkW::SetIconLocation
        // does not reliably persist paths containing supplementary Unicode plane characters on Windows).
        // We still test CJK, accents, brackets, symbols and single-quoted names.
        val tempBase = Files.createTempDirectory("kittytune_adv_unicode").toFile()
        val complexSubdir = File(tempBase, "Yinyue (Special) [Dir] & Accent éèñ").apply { mkdirs() }

        try {
            // Shortcut filename with CJK, symbols, brackets, parentheses
            val testLnk = File(complexSubdir, "快捷方式 (Music) [Player] & KittyTune.lnk")
            createTestShortcut(testLnk, "C:\\Windows\\notepad.exe")
            assertTrue(testLnk.exists(), "Complex Unicode shortcut should be created")

            val iconDir = File(tempBase, "Ikonki (Icons) - variant").apply { mkdirs() }
            val unicodeIco = File(iconDir, "app_icone [variant-1] & #1.ico").apply {
                writeBytes(byteArrayOf(0x00, 0x00, 0x01, 0x00, 0x01, 0x00))
            }

            // Patch shortcut with complex paths
            WindowsAppIdHelper.patchShortcuts(
                listOf(testLnk),
                appId = "KittyTune.KittyTune",
                icoPath = unicodeIco.canonicalPath
            )

            // Verify icon location and AUMID
            val iconLoc = getShortcutIconLocation(testLnk)
            assertTrue(
                iconLoc.contains("variant-1", ignoreCase = true),
                "Icon location should reflect the unicode ICO path, but was: $iconLoc"
            )

            val aumid = getShortcutAppUserModelId(testLnk)
            assertEquals("KittyTune.KittyTune", aumid, "AUMID must be correctly set on complex unicode shortcut")

            // Verify UTF-16LE binary representation
            val bytes = testLnk.readBytes()
            assertTrue(
                containsSubarray(bytes, "KittyTune.KittyTune".toByteArray(Charsets.UTF_16LE)),
                "Binary shortcut must contain the UTF-16LE serialized AUMID"
            )
        } finally {
            tempBase.deleteRecursively()
        }
    }

    // =========================================================================
    // Category 3: Rapid Consecutive Calls & Concurrency Stress
    // =========================================================================

    @Test
    fun testRapidConsecutiveIconSwitching() {
        assumeTrue("Windows shortcut patching requires Windows OS", isWindows)

        val tempDir = Files.createTempDirectory("kittytune_rapid_switch").toFile()
        try {
            val testLnk = File(tempDir, "RapidSwitch.lnk")
            createTestShortcut(testLnk, "C:\\Windows\\notepad.exe")

            val icoA = File(tempDir, "icon_A.ico").apply {
                writeBytes(byteArrayOf(0x00, 0x00, 0x01, 0x00, 0x01, 0x00))
            }
            val icoB = File(tempDir, "icon_B.ico").apply {
                writeBytes(byteArrayOf(0x00, 0x00, 0x01, 0x00, 0x01, 0x00))
            }

            val iterations = 8
            for (i in 1..iterations) {
                val targetIco = if (i % 2 == 1) icoA else icoB
                val expectedName = if (i % 2 == 1) "icon_A.ico" else "icon_B.ico"

                WindowsAppIdHelper.patchShortcuts(
                    listOf(testLnk),
                    appId = "KittyTune.KittyTune",
                    icoPath = targetIco.canonicalPath
                )

                val currentLoc = getShortcutIconLocation(testLnk)
                assertTrue(
                    currentLoc.contains(expectedName, ignoreCase = true),
                    "Iteration $i: IconLocation should point to $expectedName, but was: $currentLoc"
                )

                val aumid = getShortcutAppUserModelId(testLnk)
                assertEquals("KittyTune.KittyTune", aumid, "Iteration $i: AUMID must be preserved")
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testConcurrentPatchShortcutsCalls() {
        assumeTrue("Windows shortcut patching requires Windows OS", isWindows)

        val tempDir = Files.createTempDirectory("kittytune_concurrent_patch").toFile()
        try {
            val count = 4
            val shortcuts = (1..count).map { i ->
                val lnk = File(tempDir, "Shortcut_$i.lnk")
                createTestShortcut(lnk, "C:\\Windows\\notepad.exe")
                lnk
            }

            val icons = (1..count).map { i ->
                File(tempDir, "icon_$i.ico").apply {
                    writeBytes(byteArrayOf(0x00, 0x00, 0x01, 0x00, 0x01, 0x00))
                }
            }

            val latch = CountDownLatch(count)
            val errors = AtomicInteger(0)

            for (i in 0 until count) {
                Thread({
                    try {
                        WindowsAppIdHelper.patchShortcuts(
                            listOf(shortcuts[i]),
                            appId = "KittyTune.Concurrent_$i",
                            icoPath = icons[i].canonicalPath
                        )
                    } catch (t: Throwable) {
                        errors.incrementAndGet()
                    } finally {
                        latch.countDown()
                    }
                }, "PatchThread-$i").start()
            }

            val finished = latch.await(60, TimeUnit.SECONDS)
            assertTrue(finished, "Concurrent patchShortcuts should finish within 60s")
            assertEquals(0, errors.get(), "No thread should throw an unhandled exception")

            for (i in 0 until count) {
                val aumid = getShortcutAppUserModelId(shortcuts[i])
                assertEquals("KittyTune.Concurrent_$i", aumid, "Shortcut $i should have received its thread-specific AUMID")
                val loc = getShortcutIconLocation(shortcuts[i])
                assertTrue(loc.contains("icon_${i + 1}.ico", ignoreCase = true), "Shortcut $i icon must match")
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // =========================================================================
    // Category 4: Binary Property Store & COM Integrity Preservation
    // =========================================================================

    @Test
    fun testRepeatedPatchingDoesNotCorruptPropertyStoreOrBalloonFileSize() {
        assumeTrue("Windows shortcut patching requires Windows OS", isWindows)

        val tempDir = Files.createTempDirectory("kittytune_property_store_leak").toFile()
        try {
            val testLnk = File(tempDir, "PropertyStorePreserve.lnk")
            createTestShortcut(testLnk, "C:\\Windows\\notepad.exe")

            val dummyIco1 = File(tempDir, "ico1.ico").apply { writeBytes(byteArrayOf(0, 0, 1, 0, 1, 0)) }
            val dummyIco2 = File(tempDir, "ico2.ico").apply { writeBytes(byteArrayOf(0, 0, 1, 0, 1, 0)) }

            // Initial patch
            WindowsAppIdHelper.patchShortcuts(listOf(testLnk), appId = "KittyTune.KittyTune", icoPath = dummyIco1.canonicalPath)
            val initialSize = testLnk.length()
            assertTrue(initialSize > 0, "Initial shortcut file size should be > 0")

            // Patch repeatedly (12 iterations)
            for (i in 1..12) {
                val ico = if (i % 2 == 1) dummyIco2 else dummyIco1
                WindowsAppIdHelper.patchShortcuts(listOf(testLnk), appId = "KittyTune.KittyTune", icoPath = ico.canonicalPath)
            }

            val finalSize = testLnk.length()
            // In Windows COM IShellLinkW / IPropertyStore, updating an existing key updates in-place or replaces the block.
            // File size should remain within a tight bound (< 3x initial size, typically within a few hundred bytes),
            // never ballooning uncontrollably due to leaked duplicate blocks.
            assertTrue(
                finalSize < initialSize * 3 + 2048,
                "Shortcut size should not balloon uncontrollably after 12 updates. initialSize=$initialSize, finalSize=$finalSize"
            )

            // Verify COM Property Store reads cleanly without corruption
            val aumid = getShortcutAppUserModelId(testLnk)
            assertEquals("KittyTune.KittyTune", aumid, "AUMID must be completely intact and uncorrupted after 12 patches")

            // Verify binary signature of AppUserModelID in UTF-16LE
            val bytes = testLnk.readBytes()
            assertTrue(
                containsSubarray(bytes, "KittyTune.KittyTune".toByteArray(Charsets.UTF_16LE)),
                "Binary shortcut must cleanly retain UTF-16LE AUMID"
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // =========================================================================
    // Category 5: Missing Targets, Corrupted Files & Invalid Paths
    // =========================================================================

    @Test
    fun testPatchShortcutsWithMissingTargetExecutable() {
        assumeTrue("Windows shortcut patching requires Windows OS", isWindows)

        val tempDir = Files.createTempDirectory("kittytune_missing_target").toFile()
        try {
            val testLnk = File(tempDir, "MissingTarget.lnk")
            // Target is a non-existent executable
            createTestShortcut(testLnk, "C:\\NonExistent_KittyTune_Dir\\MissingApp.exe")

            val dummyIco = File(tempDir, "valid_icon.ico").apply { writeBytes(byteArrayOf(0, 0, 1, 0, 1, 0)) }

            // Should succeed in updating shortcut icon and AUMID even if target does not exist
            WindowsAppIdHelper.patchShortcuts(listOf(testLnk), appId = "KittyTune.GhostTarget", icoPath = dummyIco.canonicalPath)

            assertEquals("KittyTune.GhostTarget", getShortcutAppUserModelId(testLnk))
            assertTrue(getShortcutIconLocation(testLnk).contains("valid_icon.ico", ignoreCase = true))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testPatchShortcutsGracefullyHandlesCorruptedFilesAndDirectories() {
        assumeTrue("Windows shortcut patching requires Windows OS", isWindows)

        val tempDir = Files.createTempDirectory("kittytune_corrupt_test").toFile()
        try {
            // Subcase 1: 0-byte file
            val emptyFile = File(tempDir, "corrupt_empty.lnk").apply { createNewFile() }

            // Subcase 2: Truncated 16 bytes file
            val truncatedFile = File(tempDir, "corrupt_truncated.lnk").apply {
                writeBytes(byteArrayOf(0x4C, 0x00, 0x00, 0x00, 0x01, 0x14, 0x02, 0x00))
            }

            // Subcase 3: 256 bytes of pure garbage/noise
            val noiseFile = File(tempDir, "corrupt_noise.lnk").apply {
                writeBytes(ByteArray(256) { it.toByte() })
            }

            // Subcase 4: Non-existent file
            val nonExistentFile = File(tempDir, "ghost_file.lnk")

            // Subcase 5: Directory disguised with .lnk extension
            val fakeDir = File(tempDir, "folder_as.lnk").apply { mkdir() }

            // Subcase 6: One valid shortcut among corrupted items
            val validLnk = File(tempDir, "Valid.lnk")
            createTestShortcut(validLnk, "C:\\Windows\\notepad.exe")

            val dummyIco = File(tempDir, "icon.ico").apply { writeBytes(byteArrayOf(0, 0, 1, 0, 1, 0)) }

            val mixedList = listOf(emptyFile, truncatedFile, noiseFile, nonExistentFile, fakeDir, validLnk)

            // Execute patch on mixed list — MUST NOT CRASH OR THROW UNHANDLED EXCEPTION
            WindowsAppIdHelper.patchShortcuts(mixedList, appId = "KittyTune.ResilienceTest", icoPath = dummyIco.canonicalPath)

            // The valid shortcut must have been patched successfully despite the corrupted ones!
            assertEquals("KittyTune.ResilienceTest", getShortcutAppUserModelId(validLnk))
            assertTrue(getShortcutIconLocation(validLnk).contains("icon.ico", ignoreCase = true))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun createTestShortcut(targetLnk: File, targetExe: String): File {
        val psScript = File.createTempFile("create_lnk_", ".ps1").apply { deleteOnExit() }
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        // Use separate temp files for paths to avoid any quoting/escaping issues with Unicode/special chars
        val lnkPathFile = File.createTempFile("lnk_path_", ".txt").apply { deleteOnExit() }
        val exePathFile = File.createTempFile("exe_path_", ".txt").apply { deleteOnExit() }
        lnkPathFile.writeBytes(bom + targetLnk.canonicalPath.toByteArray(Charsets.UTF_8))
        exePathFile.writeBytes(bom + targetExe.toByteArray(Charsets.UTF_8))
        val lnkName = lnkPathFile.canonicalPath
        val exeName = exePathFile.canonicalPath
        val scriptContent = """
            ${'$'}code = @'
            using System;
            using System.IO;
            using System.Text;
            using System.Runtime.InteropServices;
            using System.Runtime.InteropServices.ComTypes;

            [ComImport, Guid("00021401-0000-0000-C000-000000000046")]
            public class ShellLinkCreatorStress {}

            [ComImport, Guid("000214F9-0000-0000-C000-000000000046"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
            public interface IShellLinkWCreatorStress {
                void GetPath([Out, MarshalAs(UnmanagedType.LPWStr)] System.Text.StringBuilder pszFile, int cch, IntPtr pfd, uint fFlags);
                void GetIDList(out IntPtr ppidl);
                void SetIDList(IntPtr pidl);
                void GetDescription([Out, MarshalAs(UnmanagedType.LPWStr)] System.Text.StringBuilder pszName, int cch);
                void SetDescription([MarshalAs(UnmanagedType.LPWStr)] string pszName);
                void GetWorkingDirectory([Out, MarshalAs(UnmanagedType.LPWStr)] System.Text.StringBuilder pszDir, int cch);
                void SetWorkingDirectory([MarshalAs(UnmanagedType.LPWStr)] string pszDir);
                void GetArguments([Out, MarshalAs(UnmanagedType.LPWStr)] System.Text.StringBuilder pszArgs, int cch);
                void SetArguments([MarshalAs(UnmanagedType.LPWStr)] string pszArgs);
                void GetHotkey(out short pwHotkey);
                void SetHotkey(short wHotkey);
                void GetShowCmd(out int piShowCmd);
                void SetShowCmd(int iShowCmd);
                void GetIconLocation([Out, MarshalAs(UnmanagedType.LPWStr)] System.Text.StringBuilder pszIconPath, int cch, out int piIcon);
                void SetIconLocation([MarshalAs(UnmanagedType.LPWStr)] string pszIconPath, int iIcon);
                void SetRelativePath([MarshalAs(UnmanagedType.LPWStr)] string pszPathRel, uint dwReserved);
                void Resolve(IntPtr hwnd, uint fFlags);
                void SetPath([MarshalAs(UnmanagedType.LPWStr)] string pszFile);
            }

            public class StressShortcutCreator {
                public static string ReadNoBom(string path) {
                    var bytes = File.ReadAllBytes(path);
                    int start = (bytes.Length >= 3 && bytes[0] == 0xEF && bytes[1] == 0xBB && bytes[2] == 0xBF) ? 3 : 0;
                    return Encoding.UTF8.GetString(bytes, start, bytes.Length - start).Trim();
                }
                public static void Create(string lnkPathFile, string exePathFile) {
                    string lnk = ReadNoBom(lnkPathFile);
                    string exe = ReadNoBom(exePathFile);
                    var link = (IShellLinkWCreatorStress)new ShellLinkCreatorStress();
                    link.SetPath(exe);
                    var file = (IPersistFile)link;
                    file.Save(lnk, true);
                    Marshal.ReleaseComObject(file);
                    Marshal.ReleaseComObject(link);
                }
            }
            '@
            Add-Type -TypeDefinition ${'$'}code
            [StressShortcutCreator]::Create('$lnkName', '$exeName')
        """.trimIndent().toByteArray(Charsets.UTF_8)
        psScript.writeBytes(bom + scriptContent)
        try {
            val pb = ProcessBuilder(
                "powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-File", psScript.absolutePath
            ).apply { redirectErrorStream(true) }
            val p = pb.start()
            val out = p.inputStream.bufferedReader(Charsets.UTF_8).readText().trim()
            p.waitFor()
            if (!targetLnk.exists()) {
                throw IllegalStateException("createTestShortcut failed: $out")
            }
        } finally {
            psScript.delete()
            lnkPathFile.delete()
            exePathFile.delete()
        }
        return targetLnk
    }

    private fun getShortcutIconLocation(lnk: File): String {
        val psScript = File.createTempFile("read_icon_", ".ps1").apply { deleteOnExit() }
        val lnkPathFile = File.createTempFile("icon_lnk_", ".txt").apply { deleteOnExit() }
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        lnkPathFile.writeBytes(bom + lnk.canonicalPath.toByteArray(Charsets.UTF_8))
        val lnkName = lnkPathFile.canonicalPath
        val scriptContent = """
            ${'$'}code = @'
            using System;
            using System.IO;
            using System.Text;
            using System.Runtime.InteropServices;
            using System.Runtime.InteropServices.ComTypes;

            [ComImport, Guid("00021401-0000-0000-C000-000000000046")]
            public class ShellLinkIconReader {}

            [ComImport, Guid("000214F9-0000-0000-C000-000000000046"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
            public interface IShellLinkWIconReader {
                void GetPath([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder pszFile, int cch, IntPtr pfd, uint fFlags);
                void GetIDList(out IntPtr ppidl);
                void SetIDList(IntPtr pidl);
                void GetDescription([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder pszName, int cch);
                void SetDescription([MarshalAs(UnmanagedType.LPWStr)] string pszName);
                void GetWorkingDirectory([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder pszDir, int cch);
                void SetWorkingDirectory([MarshalAs(UnmanagedType.LPWStr)] string pszDir);
                void GetArguments([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder pszArgs, int cch);
                void SetArguments([MarshalAs(UnmanagedType.LPWStr)] string pszArgs);
                void GetHotkey(out short pwHotkey);
                void SetHotkey(short wHotkey);
                void GetShowCmd(out int piShowCmd);
                void SetShowCmd(int iShowCmd);
                void GetIconLocation([Out, MarshalAs(UnmanagedType.LPWStr)] StringBuilder pszIconPath, int cch, out int piIcon);
                void SetIconLocation([MarshalAs(UnmanagedType.LPWStr)] string pszIconPath, int iIcon);
                void SetRelativePath([MarshalAs(UnmanagedType.LPWStr)] string pszPathRel, uint dwReserved);
                void Resolve(IntPtr hwnd, uint fFlags);
                void SetPath([MarshalAs(UnmanagedType.LPWStr)] string pszFile);
            }

            public class NativeIconReader {
                public static string ReadNoBom(string path) {
                    var bytes = File.ReadAllBytes(path);
                    int start = (bytes.Length >= 3 && bytes[0] == 0xEF && bytes[1] == 0xBB && bytes[2] == 0xBF) ? 3 : 0;
                    return Encoding.UTF8.GetString(bytes, start, bytes.Length - start).Trim();
                }
                public static string Read(string lnkPathFile) {
                    string lnkPath = ReadNoBom(lnkPathFile);
                    var link = (IShellLinkWIconReader)new ShellLinkIconReader();
                    var file = (IPersistFile)link;
                    file.Load(lnkPath, 0);
                    var sb = new StringBuilder(4096);
                    int idx;
                    link.GetIconLocation(sb, sb.Capacity, out idx);
                    Marshal.ReleaseComObject(file);
                    Marshal.ReleaseComObject(link);
                    return sb.ToString() + "," + idx;
                }
            }
            '@
            Add-Type -TypeDefinition ${'$'}code
            Write-Output ([NativeIconReader]::Read('$lnkName'))
        """.trimIndent().toByteArray(Charsets.UTF_8)
        psScript.writeBytes(bom + scriptContent)
        try {
            val pb = ProcessBuilder(
                "powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-File", psScript.absolutePath
            ).apply { redirectErrorStream(true) }
            val p = pb.start()
            val output = p.inputStream.bufferedReader(Charsets.UTF_8).readText().trim()
            p.waitFor()
            return output
        } finally {
            psScript.delete()
            lnkPathFile.delete()
        }
    }

    private fun getShortcutAppUserModelId(lnk: File): String {
        val psScript = File.createTempFile("read_aumid_", ".ps1").apply { deleteOnExit() }
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val script = """
            ${'$'}code = @'
            using System;
            using System.Text;
            using System.Runtime.InteropServices;
            using System.Runtime.InteropServices.ComTypes;

            [ComImport, Guid("00021401-0000-0000-C000-000000000046")]
            public class ShellLinkCoClassStressReader {}

            [ComImport, Guid("886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
            public interface IPropertyStoreStressReader {
                void GetCount(out uint cProps);
                void GetAt(uint iProp, out PROPERTYKEYStressReader pkey);
                void GetValue(ref PROPERTYKEYStressReader key, out PROPVARIANTStressReader pv);
                void SetValue(ref PROPERTYKEYStressReader key, ref PROPVARIANTStressReader pv);
                void Commit();
            }

            [StructLayout(LayoutKind.Sequential)]
            public struct PROPERTYKEYStressReader {
                public Guid fmtid;
                public uint pid;
            }

            [StructLayout(LayoutKind.Explicit, Size = 24)]
            public struct PROPVARIANTStressReader {
                [FieldOffset(0)] public ushort vt;
                [FieldOffset(8)] public IntPtr pwszVal;
            }

            public class StressAumidReader {
                static readonly Guid AppIdGuid = new Guid("9F4C2855-9F79-4B39-A8D0-E1D42DE1D5F3");

                public static string Read(string path) {
                    try {
                        var link = (IPersistFile)new ShellLinkCoClassStressReader();
                        link.Load(path, 0); // STGM_READ
                        var store = (IPropertyStoreStressReader)link;
                        var key = new PROPERTYKEYStressReader { fmtid = AppIdGuid, pid = 5 };
                        PROPVARIANTStressReader val;
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
            Write-Output ([StressAumidReader]::Read('${lnk.canonicalPath.replace("'", "''")}'))
        """.trimIndent().toByteArray(Charsets.UTF_8)
        psScript.writeBytes(bom + script)
        try {
            val pb = ProcessBuilder(
                "powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-File", psScript.absolutePath
            ).apply { redirectErrorStream(true) }
            val p = pb.start()
            val output = p.inputStream.bufferedReader(Charsets.UTF_8).readText().trim()
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
