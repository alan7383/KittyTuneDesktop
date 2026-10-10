import com.alananasss.kittytune.core.LinuxAppIdHelper
import com.alananasss.kittytune.core.AppIconInstaller
import com.alananasss.kittytune.core.AppIconVariants
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LinuxAppIdHelperTest {

    @Test
    fun testLinuxAppIdHelperAppliesWithoutError() {
        // Calling apply() must be safe and idempotent across platforms
        LinuxAppIdHelper.apply()
        assertEquals("kitty-tune", LinuxAppIdHelper.APP_ID)
    }

    @Test
    fun testAppIconVariantsResourcePath() {
        val defaultPath = AppIconVariants.resourcePath("default")
        assertEquals("icons/kittytune.png", defaultPath)

        val bluePath = AppIconVariants.resourcePath("blue")
        assertEquals("icons/variants/blue.png", bluePath)
    }

    @Test
    fun testAppIconInstallerApply() {
        AppIconInstaller.apply("sunset")
        Thread.sleep(1500) // apply runs in daemon thread

        val hicolor = File(System.getProperty("user.home"), ".local/share/icons/hicolor")
        assertTrue(File(hicolor, "256x256/apps/kitty-tune-sunset.png").exists(), "kitty-tune-sunset.png should exist")
        assertTrue(File(hicolor, "256x256/apps/kitty-tune.png").exists(), "kitty-tune.png should exist")
        assertTrue(File(hicolor, "256x256/apps/kittytune-sunset.png").exists(), "kittytune-sunset.png should exist")
        assertTrue(File(hicolor, "256x256/apps/com-alananasss-kittytune-MainKt.png").exists(), "com-alananasss-kittytune-MainKt.png should exist")

        val userApps = File(System.getProperty("user.home"), ".local/share/applications")
        val kittyTuneDesktop = File(userApps, "kitty-tune.desktop")
        assertTrue(kittyTuneDesktop.exists(), "kitty-tune.desktop should exist")
        val content = kittyTuneDesktop.readText()
        assertTrue(content.contains("Icon=kitty-tune-sunset"), "Desktop file should point to variant icon")
        assertTrue(content.contains("StartupWMClass=kitty-tune"), "Desktop file should have StartupWMClass=kitty-tune")
    }

    @Test
    fun testWindowClassName() {
        LinuxAppIdHelper.apply()
        val frame = javax.swing.JFrame("KittyTune Hyprland Test")
        frame.size = java.awt.Dimension(300, 200)
        frame.isVisible = true
        Thread.sleep(1000)

        val proc = Runtime.getRuntime().exec(arrayOf("hyprctl", "clients"))
        val output = proc.inputStream.bufferedReader().readText()

        frame.dispose()

        assertTrue(output.contains("class: kitty-tune"), "Hyprland should register window with class kitty-tune, got output:\n$output")
    }
}
