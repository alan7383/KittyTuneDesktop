import com.alananasss.kittytune.core.LinuxDesktopIdentity
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinuxDesktopIdentityTest {
    @Test
    fun `migration preserves custom commands and desktop actions without duplicate menu entries`() {
        val root = Files.createTempDirectory("kittytune-launchers-").toFile()
        try {
            val apps = File(root, "applications").apply { mkdirs() }
            val legacy = File(apps, "kitty-tune.desktop").apply {
                writeText("""
                    [Desktop Entry]
                    Name=KittyTune
                    Exec=/custom/KittyTune --flag %U
                    Icon=kitty-tune
                    Categories=Audio;Custom;
                    MimeType=audio/flac;
                    Actions=Play;
                    [Desktop Action Play]
                    Name=Play
                    Exec=/custom/KittyTune --play
                    Icon=media-playback-start
                """.trimIndent())
            }
            val auth = File(apps, "kittytune-sc.desktop").apply {
                writeText("[Desktop Entry]\nName=KittyTune SC Auth\nNoDisplay=true\nExec=auth %u\n")
            }
            val originalAuth = auth.readText()
            LinuxDesktopIdentity.install(apps, emptyList(), "fallback", "sunset")
            val canonical = File(apps, "kittytune.desktop")
            val text = canonical.readText()
            assertTrue(text.contains("Exec=/custom/KittyTune --flag %U"))
            assertTrue(text.contains("StartupWMClass=kittytune\n"))
            assertTrue(text.contains("Icon=kittytune-sunset\n"))
            assertTrue(text.contains("MimeType=audio/flac;"))
            assertTrue(text.contains("Custom;"))
            assertTrue(text.contains("[Desktop Action Play]\nName=Play\nExec=/custom/KittyTune --play\nIcon=media-playback-start"))
            assertTrue(legacy.readText().contains("NoDisplay=true"))
            assertTrue(legacy.readText().contains("Exec=/custom/KittyTune --flag %U"))
            assertEquals(originalAuth, auth.readText())
            val visible = apps.listFiles()!!.filter(LinuxDesktopIdentity::isLauncher)
                .filter { !it.readText().contains("NoDisplay=true") }
            assertEquals(listOf("kittytune.desktop"), visible.map { it.name })
            LinuxDesktopIdentity.install(apps, emptyList(), "other", "sunset")
            assertEquals(text, canonical.readText(), "Migration must be idempotent")
            LinuxDesktopIdentity.install(apps, emptyList(), "other", "blue")
            assertTrue(canonical.readText().contains("Icon=kittytune-blue"))
        } finally { root.deleteRecursively() }
    }

    @Test
    fun `packaged launcher is copied without writing to the package`() {
        val root = Files.createTempDirectory("kittytune-package-").toFile()
        try {
            val installed = File(root, "kitty-tune-KittyTune.desktop").apply {
                writeText("[Desktop Entry]\nName=KittyTune\nExec=/opt/kitty-tune/bin/KittyTune %U\nIcon=/opt/icon.png\n")
            }
            val original = installed.readText()
            val apps = File(root, "user/applications")
            LinuxDesktopIdentity.install(apps, listOf(installed), "fallback", "default")
            assertEquals(original, installed.readText())
            assertTrue(File(apps, "kittytune.desktop").readText().contains("Exec=/opt/kitty-tune/bin/KittyTune %U"))
            assertTrue(File(apps, installed.name).readText().contains("NoDisplay=true"))
            assertFalse(File(apps, "kittytune.desktop").readText().contains("NoDisplay=true"))
        } finally { root.deleteRecursively() }
    }
    @Test
    fun `quickshell resolves both identities without falling back to the kitty terminal`() {
        org.junit.Assume.assumeTrue(runCatching {
            ProcessBuilder("qs", "--version").start().waitFor() == 0
        }.getOrDefault(false))
        val root = Files.createTempDirectory("kittytune-icons-").toFile()
        var process: Process? = null
        try {
            val data = File(root, "data")
            LinuxDesktopIdentity.install(File(data, "applications"), emptyList(), "kitty-tune", "default")
            val icons = File(data, "icons/hicolor/96x96/apps").apply { mkdirs() }
            val source = javax.imageio.ImageIO.read(checkNotNull(javaClass.getResource("/icons/kittytune.png")))
            val scaled = com.alananasss.kittytune.core.IconEncoders.scaled(source, 96)
            val expected = File(icons, "kittytune.png")
            javax.imageio.ImageIO.write(scaled, "png", expected)
            expected.copyTo(File(icons, "kittytune-default.png"))
            val qml = File(root, "shell.qml").apply {
                writeText(checkNotNull(this@LinuxDesktopIdentityTest.javaClass.getResource("/mpris/desktop-icon.qml")).readText())
            }
            val capture = File(root, "capture.png")
            val log = File(root, "output.log")
            process = ProcessBuilder("qs", "-p", qml.absolutePath, "--no-color").apply {
                environment()["QT_QPA_PLATFORM"] = "offscreen"
                environment()["XDG_DATA_HOME"] = data.absolutePath
                environment()["KITTYTUNE_WINDOW_ID"] = com.alananasss.kittytune.core.LinuxAppIdHelper.APP_ID
                environment()["KITTYTUNE_ICON_CAPTURE"] = capture.absolutePath
            }.redirectErrorStream(true).redirectOutput(log).start()
            assertTrue(process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS), "Icon probe timed out")
            assertEquals(0, process.exitValue(), log.readText())
            File("build/reports").mkdirs()
            capture.copyTo(File("build/reports/linux-icon-capture.png"), overwrite = true)
            val image = javax.imageio.ImageIO.read(capture)
            for (offset in listOf(0, 96)) {
                var difference = 0L
                for (y in 0 until 96) for (x in 0 until 96) {
                    val actual = image.getRGB(offset + x, y)
                    val reference = image.getRGB(192 + x, y)
                    for (shift in listOf(0, 8, 16, 24)) {
                        difference += kotlin.math.abs(((actual ushr shift) and 255) - ((reference ushr shift) and 255))
                    }
                }
                assertTrue(difference.toDouble() / (96 * 96 * 4) > 10.0,
                    "Resolved icon incorrectly matches the Kitty terminal (column $offset, error=${difference.toDouble() / (96 * 96 * 4)})")
            }
        } finally { process?.destroyForcibly(); root.deleteRecursively() }
    }

}
