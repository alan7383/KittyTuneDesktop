package com.alananasss.kittytune.data.drm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * The daemon, exercised the way the app does, in a loop.
 *
 * This is the shape that failed once: register, verify, then a *second* daemon for the download
 * session, open a session (which resets the CDM instance) and ask for a licence request. Anything
 * that kills a daemon — a concurrent reset, a grace-timer racing a fresh session, the process dying
 * at startup — shows up here as a failed round instead of a rare bug report.
 *
 * No network: the PSSH is synthetic, and the licence server is never called.
 */
class CdmSidecarStabilityTest {

    @Before
    fun requireInstall() {
        assumeTrue("the native module is not installed", NativeCdmSetup.isInstalled())
    }

    @Test
    fun `repeated register, verify and session cycles all answer`() {
        val failures = mutableListOf<String>()
        val rounds = 25
        repeat(rounds) { round ->
            try {
                CdmSidecar.reset()
                assertTrue("round $round: verify failed (${CdmSidecar.lastFailure})", CdmSidecar.verify())
                val cdm = CdmSidecar.secondaryCdm() ?: error("no second daemon in round $round")
                cdm.openSession(syntheticPssh()).use { session ->
                    val request = session.licenseRequest()
                    assertTrue("round $round: request is ${request.size} bytes", request.size > 1500)
                }
            } catch (e: Throwable) {
                failures += "round $round: ${e.message}"
            } finally {
                CdmSidecar.reset()
            }
        }
        assertEquals("failed rounds:\n" + failures.joinToString("\n"), 0, failures.size)
    }

    /**
     * The daemon disappears between opening the session and asking for the licence — a method switch,
     * a CDM reset, a process that died. Before any key exists there is nothing to lose, so the session
     * starts a replacement and asks again instead of failing the track.
     */
    @Test
    fun `a daemon that stops before the licence is restarted`() {
        CdmSidecar.reset()
        assertTrue("verify failed: ${CdmSidecar.lastFailure}", CdmSidecar.verify())
        val cdm = CdmSidecar.secondaryCdm() ?: error("no second daemon")
        val session = cdm.openSession(syntheticPssh())
        CdmSidecar.reset() // closes the daemons behind its back, session included
        val request = session.licenseRequest()
        session.close()
        assertTrue("request is ${request.size} bytes after the restart", request.size > 1500)
    }

    /** A minimal Widevine PSSH: v0 box, the system id, one key id. */
    private fun syntheticPssh(): ByteArray {
        val out = ByteArrayOutputStream()
        val systemId = byteArrayOf(
            0xed.toByte(), 0xef.toByte(), 0x8b.toByte(), 0xa9.toByte(), 0x79.toByte(), 0xd6.toByte(),
            0x4a.toByte(), 0xce.toByte(), 0xa3.toByte(), 0xc8.toByte(), 0x27.toByte(), 0xdc.toByte(),
            0xd5.toByte(), 0x1d.toByte(), 0x21.toByte(), 0xed.toByte(),
        )
        val keyId = ByteArray(16) { (it + 1).toByte() }
        val data = byteArrayOf(0x12, 0x10) + keyId
        val body = byteArrayOf(0) + ByteArray(3) + systemId + length(data.size) + data
        out.write(length(8 + body.size))
        out.write("pssh".toByteArray())
        out.write(body)
        return out.toByteArray()
    }

    private fun length(value: Int) = byteArrayOf(
        ((value shr 24) and 0xFF).toByte(), ((value shr 16) and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(), (value and 0xFF).toByte(),
    )
}
