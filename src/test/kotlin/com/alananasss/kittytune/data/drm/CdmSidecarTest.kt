package com.alananasss.kittytune.data.drm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Drives [SidecarTransport] against a stand-in daemon over a real pipe.
 *
 * The C++ half (`src/main/cpp/cdmsidecar/CdmSidecar.cpp`) is not exercised here — it needs a real
 * `libwidevinecdm.so`. What this pins down is the half a bug would hide in: that the framing the
 * JVM writes is the framing the daemon document describes, that a decrypt carries the KID and the
 * subsample table, and that a refusal on the far end reaches the player as a
 * [DrmLicenseException] instead of a hang.
 *
 * The stand-in is this file's own `main`, started with the runtime the tests already use, so the
 * pipe, the reader thread and the timeouts are all the production ones.
 */
class CdmSidecarTest {

    /**
     * The classpath the stand-in needs: this file's classes, the production classes, and the Kotlin
     * runtime. The test JVM's own classpath is past the Windows command-line limit
     * (`CreateProcess error=206`), and Gradle hands the worker a manifest jar rather than the real
     * entries, so each piece is located from the class that lives in it.
     */
    private fun locationOf(type: Class<*>): String? = runCatching {
        File(type.protectionDomain.codeSource.location.toURI()).absolutePath
    }.getOrNull()

    private fun daemonClasspath(): String = listOfNotNull(
        locationOf(CdmSidecarTest::class.java),
        locationOf(WidevineCdm::class.java),
        locationOf(Unit::class.java),
    ).distinct().joinToString(File.pathSeparator)

    private fun daemonCommand(): List<String> = listOf(
        File(System.getProperty("java.home"), "bin/java").absolutePath,
        "-cp", daemonClasspath(),
        "com.alananasss.kittytune.data.drm.CdmSidecarTestKt",
    )

    @Test
    fun licenseRequestCarriesThePsshAndReturnsTheDaemonAnswer() {
        val cdm = SidecarWidevineCdm(daemonCommand(), "test daemon")
        try {
            val session = cdm.openSession(byteArrayOf(1, 2, 3, 4))
            assertEquals("REQ:01020304", String(session.licenseRequest(), Charsets.UTF_8))
        } finally {
            cdm.release()
        }
    }

    @Test
    fun decryptSendsDataIvSubsamplesAndKeyId() {
        val cdm = SidecarWidevineCdm(daemonCommand(), "test daemon")
        try {
            val session = cdm.openSession(ByteArray(0))
            (session as SidecarWidevineSession).useKeyId(byteArrayOf(0xAB.toByte(), 0xCD.toByte()))
            val plain = session.decryptSample(
                data = byteArrayOf(9, 8, 7),
                iv = byteArrayOf(0x10, 0x20),
                subsamples = listOf(1 to 2, 3 to 4),
            )
            assertEquals("data=090807;iv=1020;subs=1:2,3:4;kid=abcd", String(plain, Charsets.UTF_8))
        } finally {
            cdm.release()
        }
    }

    @Test
    fun aRefusalOnTheFarEndBecomesALicenseException() {
        val cdm = SidecarWidevineCdm(daemonCommand(), "test daemon")
        try {
            val session = cdm.openSession(ByteArray(0))
            val failure = runCatching { session.provideLicense("refuse".toByteArray()) }.exceptionOrNull()
            assertNotNull("a daemon refusal must not come back as a value", failure)
            assertTrue(failure is DrmLicenseException)
            assertTrue(failure!!.message.orEmpty().contains("service certificate rejected"))
        } finally {
            cdm.release()
        }
    }

    @Test
    fun aDaemonThatCannotStartIsAnUnavailableCdmNotACrash() {
        val cdm = SidecarWidevineCdm(listOf("definitely-not-a-real-binary-ktcdm"), "missing daemon")
        val failure = runCatching { cdm.openSession(ByteArray(0)) }.exceptionOrNull()
        assertTrue(failure is DrmUnavailableException)
    }

    @Test
    fun aCommandLineKeepsQuotedPathsTogether() {
        assertEquals(
            listOf("wsl.exe", "-d", "Ubuntu", "--", "/home/me/.local/share/kittytune/ktcdm"),
            CdmSidecar.splitCommand("wsl.exe -d Ubuntu -- /home/me/.local/share/kittytune/ktcdm"),
        )
        assertEquals(
            listOf("C:\\Program Files\\ktcdm.exe", "C:\\Program Files\\libwidevinecdm.so"),
            CdmSidecar.splitCommand("\"C:\\Program Files\\ktcdm.exe\" \"C:\\Program Files\\libwidevinecdm.so\""),
        )
        assertEquals(listOf("a", "b"), CdmSidecar.splitCommand("  a   b  "))
    }
}

/**
 * The stand-in daemon: reads the same frames the C++ one reads and answers in the same shape.
 *
 * Deliberately not a mock object — it is a second process, so the test fails if the framing or the
 * flushing is wrong, which is exactly the failure a mock would hide.
 */
fun main() {
    val input = System.`in`
    val output = System.out

    fun readFully(target: ByteArray): Boolean {
        var offset = 0
        while (offset < target.size) {
            val read = input.read(target, offset, target.size - offset)
            if (read < 0) return false
            offset += read
        }
        return true
    }

    fun reply(status: Int, body: ByteArray) {
        output.write(status)
        val size = body.size
        output.write(byteArrayOf(
            (size and 0xFF).toByte(),
            ((size shr 8) and 0xFF).toByte(),
            ((size shr 16) and 0xFF).toByte(),
            ((size shr 24) and 0xFF).toByte(),
        ))
        output.write(body)
        output.flush()
    }

    fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    while (true) {
        val header = ByteArray(5)
        if (!readFully(header)) return
        val opcode = header[0].toInt() and 0xFF
        val size = (header[1].toInt() and 0xFF) or
            ((header[2].toInt() and 0xFF) shl 8) or
            ((header[3].toInt() and 0xFF) shl 16) or
            ((header[4].toInt() and 0xFF) shl 24)
        val payload = ByteArray(size)
        if (size > 0 && !readFully(payload)) return

        when (opcode) {
            0 -> { reply(0, ByteArray(0)); return }
            1 -> reply(0, "REQ:${hex(payload)}".toByteArray())
            2 -> if (String(payload, Charsets.UTF_8) == "refuse") {
                reply(1, "service certificate rejected".toByteArray())
            } else {
                reply(0, ByteArray(0))
            }
            4 -> {
                var cursor = 0
                fun word(): Int {
                    val value = (payload[cursor].toInt() and 0xFF) or
                        ((payload[cursor + 1].toInt() and 0xFF) shl 8) or
                        ((payload[cursor + 2].toInt() and 0xFF) shl 16) or
                        ((payload[cursor + 3].toInt() and 0xFF) shl 24)
                    cursor += 4
                    return value
                }
                fun bytes(): ByteArray {
                    val length = word()
                    val value = payload.copyOfRange(cursor, cursor + length)
                    cursor += length
                    return value
                }
                val data = bytes()
                val iv = bytes()
                val pairs = word()
                val subsamples = (0 until pairs).joinToString(",") { "${word()}:${word()}" }
                val kid = bytes()
                reply(0, "data=${hex(data)};iv=${hex(iv)};subs=$subsamples;kid=${hex(kid)}".toByteArray())
            }
            5 -> reply(0, ByteArray(0))
            else -> reply(1, "unknown opcode".toByteArray())
        }
    }
}
