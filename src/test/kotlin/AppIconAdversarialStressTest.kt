import com.alananasss.kittytune.core.AppIconInstaller
import com.alananasss.kittytune.core.AppIconRuntime
import com.alananasss.kittytune.core.AppIconVariants
import com.alananasss.kittytune.core.IconEncoders
import com.alananasss.kittytune.core.WindowsAppIdHelper
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.Frame
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AppIconAdversarialStressTest {

    private val isWindows = System.getProperty("os.name").lowercase().contains("win")

    // -------------------------------------------------------------------------
    // 1. AppIconRuntime.apply: Scaling Ladder & Window Edge Cases
    // -------------------------------------------------------------------------

    @Test
    fun testScalingLadderAcrossAllStandardWindowsDimensions() {
        val standardDimensions = intArrayOf(16, 20, 24, 32, 40, 48, 64, 128, 256)

        val testVariants = listOf(AppIconVariants.DEFAULT_KEY, "og", "black", "sunset")

        for (variant in testVariants) {
            val stream = Thread.currentThread().contextClassLoader
                ?.getResourceAsStream(AppIconVariants.resourcePath(variant))
            assertNotNull(stream, "Variant resource '$variant' must be resolvable")

            val source = IconEncoders.decode(stream.readBytes())
            assertNotNull(source, "Source image for '$variant' must decode cleanly")
            assertTrue(source.width > 0 && source.height > 0, "Source image dimensions must be positive")

            for (dim in standardDimensions) {
                val scaled = IconEncoders.scaled(source, dim)
                assertNotNull(scaled, "Scaled image for dimension $dim must not be null")
                assertEquals(dim, scaled.width, "Scaled width for $variant must be exactly $dim")
                assertEquals(dim, scaled.height, "Scaled height for $variant must be exactly $dim")
                assertEquals(BufferedImage.TYPE_INT_ARGB, scaled.type, "Scaled image type must be TYPE_INT_ARGB")
            }
        }
    }

    @Test
    fun testAppIconRuntimeApplyWithNullWindowWhenNoWindowsExist() {
        AppIconRuntime.apply(AppIconVariants.DEFAULT_KEY, window = null)
        AppIconRuntime.apply("og", window = null)
        AppIconRuntime.apply("nonexistent_variant_key_999", window = null)
    }

    @Test
    fun testAppIconRuntimeApplyWithMultipleAndUnmappedWindows() {
        val frame1 = Frame("TestFrame1")
        val frame2 = Frame("TestFrame2")
        val frame3 = Frame("TestFrame3")

        try {
            assertFalse(frame1.isDisplayable)

            frame2.pack()
            assertTrue(frame2.isDisplayable)
            assertFalse(frame2.isVisible)

            frame3.pack()
            frame3.dispose()

            AppIconRuntime.apply(AppIconVariants.DEFAULT_KEY, frame1)
            assertTrue(frame1.iconImages.isNotEmpty(), "Unmapped frame should accept icon images ladder")

            AppIconRuntime.apply("og", window = null)

            assertTrue(frame2.iconImages.isNotEmpty(), "Frame 2 should have received icon images")
            val ladderSizes = frame2.iconImages.map { it.getWidth(null) }
            assertTrue(ladderSizes.contains(16), "Ladder must contain 16px")
            assertTrue(ladderSizes.contains(32), "Ladder must contain 32px")
            assertTrue(ladderSizes.contains(256), "Ladder must contain 256px")
        } finally {
            runCatching { frame1.dispose() }
            runCatching { frame2.dispose() }
            runCatching { frame3.dispose() }
        }
    }

    // -------------------------------------------------------------------------
    // 2. Shell Notifications: Error Handling, Timeouts, and Rapid Sequential
    // -------------------------------------------------------------------------

    @Test
    fun testRapidSequentialShellNotifications() {
        assumeTrue("Windows shell notification requires Windows OS", isWindows)

        val iterations = 10
        val startTime = System.currentTimeMillis()

        for (i in 1..iterations) {
            AppIconInstaller.notifyWindowsShell()
        }

        val elapsed = System.currentTimeMillis() - startTime
        println("Executed $iterations sequential shell notifications in ${elapsed}ms")
    }

    @Test
    fun testConcurrentShellNotificationsUnderHeavyLoad() {
        assumeTrue("Windows shell notification requires Windows OS", isWindows)

        val threadCount = 6
        val latch = CountDownLatch(threadCount)
        val errors = AtomicInteger(0)

        for (i in 0 until threadCount) {
            Thread({
                try {
                    AppIconInstaller.notifyWindowsShell()
                } catch (t: Throwable) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }, "StressNotificationThread-$i").start()
        }

        val completed = latch.await(20, TimeUnit.SECONDS)
        assertTrue(completed, "Concurrent shell notifications should complete within 20s")
        assertEquals(0, errors.get(), "No exceptions should be thrown during concurrent shell notifications")
    }

    // -------------------------------------------------------------------------
    // 3. Non-ASCII & Unicode Path Stress in WindowsAppIdHelper.patchShortcuts
    // -------------------------------------------------------------------------

    @Test
    fun testPatchShortcutsWithAccentedAndUnicodeIcoPath() {
        assumeTrue("Windows shortcut patching requires Windows OS", isWindows)

        val tempDir = Files.createTempDirectory("kittytune_unicode_ico").toFile()
        try {
            val testLnk = File(tempDir, "KittyTune.lnk")
            createSimpleShortcut(testLnk, "C:\\Windows\\notepad.exe")

            val unicodeDir = File(tempDir, "Donnees_Eleve_Music")
            unicodeDir.mkdirs()
            val unicodeIco = File(unicodeDir, "app_icon_og.ico").apply {
                writeBytes(byteArrayOf(0x00, 0x00, 0x01, 0x00, 0x01, 0x00))
            }

            WindowsAppIdHelper.patchShortcuts(
                listOf(testLnk),
                appId = WindowsAppIdHelper.APP_ID,
                icoPath = unicodeIco.canonicalPath
            )

            val aumid = readShortcutAumid(testLnk)
            assertEquals("KittyTune.KittyTune", aumid, "AUMID should be set even with Unicode ICO paths")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun createSimpleShortcut(targetLnk: File, targetExe: String) {
        val psScript = File.createTempFile("create_lnk_", ".ps1").apply { deleteOnExit() }
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val scriptContent = """
            ${'$'}wsh = New-Object -ComObject WScript.Shell
            ${'$'}s = ${'$'}wsh.CreateShortcut('${targetLnk.canonicalPath.replace("'", "''")}')
            ${'$'}s.TargetPath = '${targetExe.replace("'", "''")}'
            ${'$'}s.Save()
        """.trimIndent().toByteArray(Charsets.UTF_8)

        psScript.writeBytes(bom + scriptContent)
        try {
            val pb = ProcessBuilder(
                "powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-File", psScript.absolutePath
            ).apply { redirectErrorStream(true) }
            val p = pb.start()
            p.waitFor()
        } finally {
            psScript.delete()
        }
    }

    private fun readShortcutAumid(lnk: File): String {
        val psScript = File.createTempFile("read_aumid_", ".ps1").apply { deleteOnExit() }
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val script = """
            ${'$'}code = @'
            using System;
            using System.Text;
            using System.Runtime.InteropServices;
            using System.Runtime.InteropServices.ComTypes;

            [ComImport, Guid("00021401-0000-0000-C000-000000000046")]
            public class ShellLinkReader {}

            [ComImport, Guid("886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
            public interface IPropertyStoreReader {
                void GetCount(out uint cProps);
                void GetAt(uint iProp, out PROPERTYKEYReader pkey);
                void GetValue(ref PROPERTYKEYReader key, out PROPVARIANTReader pv);
                void SetValue(ref PROPERTYKEYReader key, ref PROPVARIANTReader pv);
                void Commit();
            }

            [StructLayout(LayoutKind.Sequential)]
            public struct PROPERTYKEYReader {
                public Guid fmtid;
                public uint pid;
            }

            [StructLayout(LayoutKind.Explicit, Size = 24)]
            public struct PROPVARIANTReader {
                [FieldOffset(0)] public ushort vt;
                [FieldOffset(8)] public IntPtr pwszVal;
            }

            public class Helper {
                static readonly Guid AppIdGuid = new Guid("9F4C2855-9F79-4B39-A8D0-E1D42DE1D5F3");

                public static string Read(string path) {
                    try {
                        var link = (IPersistFile)new ShellLinkReader();
                        link.Load(path, 0);
                        var store = (IPropertyStoreReader)link;
                        var key = new PROPERTYKEYReader { fmtid = AppIdGuid, pid = 5 };
                        PROPVARIANTReader val;
                        store.GetValue(ref key, out val);
                        if (val.vt == 31 && val.pwszVal != IntPtr.Zero) {
                            return Marshal.PtrToStringUni(val.pwszVal);
                        }
                        return "";
                    } catch (Exception ex) {
                        return "ERR: " + ex.Message;
                    }
                }
            }
            '@
            Add-Type -TypeDefinition ${'$'}code
            Write-Output ([Helper]::Read('${lnk.canonicalPath.replace("'", "''")}'))
        """.trimIndent().toByteArray(Charsets.UTF_8)

        psScript.writeBytes(bom + script)
        try {
            val pb = ProcessBuilder(
                "powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-File", psScript.absolutePath
            ).apply { redirectErrorStream(true) }
            val p = pb.start()
            val out = p.inputStream.bufferedReader(Charsets.UTF_8).readText().trim()
            p.waitFor()
            return out
        } finally {
            psScript.delete()
        }
    }
}
