import com.alananasss.kittytune.data.AppProcessInfo
import com.alananasss.kittytune.data.otherKittyTuneProcessIds
import org.junit.Assert.assertEquals
import org.junit.Test

class KittyTuneProcessesTest {
    @Test
    fun windowsLauncherAndItsMediaBridgeAreNotOtherInstances() {
        val processes = listOf(
            AppProcessInfo(10, null, "C:\\Windows\\explorer.exe"),
            AppProcessInfo(100, 10, "C:\\KittyTune\\KittyTune.exe"),
            AppProcessInfo(101, 100, "C:\\KittyTune\\KittyTune.exe"),
            AppProcessInfo(102, 101, "C:\\Temp\\kittytune_smtc\\hash\\KittyTune.exe"),
            AppProcessInfo(200, 10, "D:\\Other copy\\KittyTune.exe"),
        )
        assertEquals(setOf(200L), otherKittyTuneProcessIds(101, processes))
    }

    @Test
    fun unixAndWindowsExecutablePathsAreRecognized() {
        val processes = listOf(
            AppProcessInfo(100, null, "/opt/KittyTune/KittyTune"),
            AppProcessInfo(200, null, "/usr/bin/kitty-tune"),
            AppProcessInfo(300, null, "C:\\Programs\\KittyTune.exe"),
            AppProcessInfo(400, null, "kittytune"),
        )
        assertEquals(setOf(200L, 300L, 400L), otherKittyTuneProcessIds(100, processes))
    }

    @Test
    fun javaDevelopmentInstancesAreRecognizedWithoutMatchingUnrelatedJava() {
        val processes = listOf(
            AppProcessInfo(100, null, "kittytune"),
            AppProcessInfo(200, null, "/jdk/bin/java", "java com.alananasss.kittytune.MainKt"),
            AppProcessInfo(300, null, "C:\\jdk\\bin\\javaw.exe", "javaw com.alananasss.kittytune.MainKt"),
            AppProcessInfo(400, null, "/jdk/bin/java", "java org.gradle.launcher.daemon.bootstrap.GradleDaemon kittytune"),
            AppProcessInfo(500, null, "/jdk/bin/java", "java OtherApp"),
        )
        assertEquals(setOf(200L, 300L), otherKittyTuneProcessIds(100, processes))
    }

    @Test
    fun missingParentsDoNotHideUnrelatedInstances() {
        assertEquals(setOf(200L), otherKittyTuneProcessIds(100, listOf(
            AppProcessInfo(100, 99, "kittytune"),
            AppProcessInfo(200, 99, "kittytune"),
        )))
    }
}
