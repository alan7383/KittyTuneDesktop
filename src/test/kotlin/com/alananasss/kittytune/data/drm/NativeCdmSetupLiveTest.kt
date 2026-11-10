package com.alananasss.kittytune.data.drm

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * The native route, from nothing to a decrypted fragment.
 *
 * It downloads Chrome's package (≈140 MB) and leaves the module installed, so it only runs when asked:
 * `KITTYTUNE_CDM_INSTALL_TEST=1 ./gradlew test --tests "*NativeCdmSetupLiveTest*"`.
 *
 * The last step is the one that matters: [DrmSelfTest] resolves `bladee1000/it-girl`, gets a licence
 * through the installed module, decrypts the first fragment and decodes it with FFmpeg — the same
 * chain the player runs.
 */
class NativeCdmSetupLiveTest {

    @Before
    fun requireOptIn() {
        assumeTrue("set KITTYTUNE_CDM_INSTALL_TEST=1 to run", System.getenv("KITTYTUNE_CDM_INSTALL_TEST") == "1")
        assumeTrue("the native route is a Windows feature", NativeCdmSetup.windows)
    }

    @Test
    fun `installs the module and decrypts a protected track`() {
        val log = StringBuilder()
        val installed = kotlinx.coroutines.runBlocking {
            NativeCdmSetup.provision(
                onStep = { println("step: $it") },
                onLine = { line ->
                    synchronized(log) { log.append(line).append('\n') }
                    println(line)
                },
            )
        }
        assertTrue("the install failed:\n$log", installed)
        assertTrue("the module is missing", NativeCdmSetup.moduleFile.isFile)
        assertTrue("the module is too small", NativeCdmSetup.moduleFile.length() > 8L * 1024 * 1024)
        assertTrue("the configuration names a daemon that is not there", NativeCdmSetup.state() == NativeCdmSetup.State.INSTALLED)
        assertTrue("the configuration is missing", NativeCdmSetup.configFile.isFile)

        CdmSidecar.reset()
        assertTrue("the daemon does not answer", CdmSidecar.verify())

        val report = StringBuilder()
        val decoded = kotlinx.coroutines.runBlocking {
            DrmSelfTest.run { line ->
                synchronized(report) { report.append(line).append('\n') }
                println(line)
            }
        }
        assertTrue("the protected track did not decode:\n$report", decoded)
    }
}
