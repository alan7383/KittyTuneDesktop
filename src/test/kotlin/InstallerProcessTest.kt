import com.alananasss.kittytune.data.UpdateStatus
import com.alananasss.kittytune.data.awaitInstallerResult
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class InstallerProcessTest {
    @Test
    fun packageManagerDiagnosticCannotTurnFailureIntoSuccess() {
        assertEquals(UpdateStatus.ERROR, result(1, true, "dpkg: dependency problems prevent configuration\n"))
        assertEquals(UpdateStatus.ERROR, result(1, true, ""))
    }

    @Test
    fun authenticationFailureIsDetectedEvenWhenPkexecPrintsAnError() {
        for (code in 126..127) {
            assertEquals(UpdateStatus.AUTH_FAILED, result(code, true, "Not authorized\n"))
            assertEquals(UpdateStatus.AUTH_FAILED, result(code, true, ""))
        }
    }

    @Test
    fun nonPrivilegedInstallerFailureIsNotReportedAsReady() {
        assertEquals(UpdateStatus.ERROR, result(1, false, "hdiutil: attach failed\n"))
        assertEquals(UpdateStatus.ERROR, result(127, false, ""))
    }

    @Test
    fun onlySuccessfulExitIsReadyToInstall() {
        assertEquals(UpdateStatus.READY_TO_INSTALL, result(0, true, ""))
        assertEquals(UpdateStatus.READY_TO_INSTALL, result(0, true, "Installation complete\n"))
        assertEquals(UpdateStatus.READY_TO_INSTALL, result(0, false, ""))
    }

    private fun result(code: Int, auth: Boolean, output: String): UpdateStatus = awaitInstallerResult(
        object : Process() {
            override fun getInputStream() = ByteArrayInputStream(output.toByteArray())
            override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())
            override fun getOutputStream() = ByteArrayOutputStream()
            override fun waitFor() = code
            override fun exitValue() = code
            override fun destroy() {}
        }, auth, {},
    )
}
