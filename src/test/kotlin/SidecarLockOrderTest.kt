package com.alananasss.kittytune.data.drm

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class SidecarLockOrderTest {
    private fun withoutDrmMonitor(action: () -> Unit) {
        val done = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val worker = Thread({
            try { action() } catch (e: Throwable) { failure.set(e) } finally { done.countDown() }
        }, "sidecar-lock-test").apply { isDaemon = true }
        try {
            synchronized(SoundCloudDrm) {
                worker.start()
                assertTrue("sidecar operation waits for the DRM monitor", done.await(3, TimeUnit.SECONDS))
            }
        } finally { worker.join(4000) }
        failure.get()?.let { throw AssertionError(it) }
    }

    @Test fun resetDoesNotInvertTheDownloadLockOrder() {
        withoutDrmMonitor { CdmSidecar.reset() }
    }

    @Test fun registrationAndFailureDoNotInvertTheDownloadLockOrder() {
        assumeTrue(System.getenv("KITTYTUNE_CDM_SIDECAR").isNullOrBlank())
        val base = System.getenv("LOCALAPPDATA")
        assumeTrue(base != null)
        val config = File(base!!, "KittyTune/cdm-sidecar.json")
        val previous = config.takeIf { it.isFile }?.readBytes()
        CdmSidecar.reset()
        try {
            config.parentFile.mkdirs()
            config.writeText("""{"command":["unused-fixture-daemon","unused-fixture-module"]}""")
            withoutDrmMonitor { assertTrue(CdmSidecar.installIfConfigured()) }
            val cdm = WidevineCdmRegistry.current() as SidecarWidevineCdm
            withoutDrmMonitor { CdmSidecar.markFailed(cdm, "fixture failure") }
            assertFalse(CdmSidecar.isInstalled)
        } finally {
            CdmSidecar.reset()
            if (previous == null) config.delete() else config.writeBytes(previous)
        }
    }
}
