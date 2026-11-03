package com.alananasss.kittytune.data.drm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The CDM search, against browser layouts written to a temporary home.
 *
 * The real proof (a CDM actually loading) needs a Widevine library on the machine and lives in
 * `NativeWidevineLiveTest`. What is checkable everywhere is the layout part: a browser we have never
 * heard of still stores its CDM under `WidevineCdm/<version>/_platform_specific/…`, and that has to be
 * found — Opera GX is the reason this exists, since it is not in anyone's mental list of browsers.
 */
class CdmDiscoveryTest {

    private fun cdmAt(root: File, relative: String): File {
        val file = File(root, relative)
        file.parentFile.mkdirs()
        file.writeText("")
        return file
    }

    private fun tempHome(): File = Files.createTempDirectory("kittytune-cdm-home").toFile()

    @Test
    fun `an opera gx profile is found`() {
        val home = tempHome()
        try {
            val cdm = cdmAt(
                home,
                ".config/opera-gx/WidevineCdm/4.10.3112.0/_platform_specific/linux_x64/libwidevinecdm.so",
            )
            val found = NativeWidevineCdm.scanForCdm(listOf(home.absolutePath), maxDepth = 8, budgetMs = 5_000)
            assertEquals(cdm.absolutePath, found)
        } finally {
            home.deleteRecursively()
        }
    }

    @Test
    fun `a firefox profile is found`() {
        val home = tempHome()
        try {
            val cdm = cdmAt(
                home,
                ".mozilla/firefox/abcd1234.default/gmp-widevinecdm/4.10.3112.0/libwidevinecdm.so",
            )
            val found = NativeWidevineCdm.scanForCdm(listOf(home.absolutePath), maxDepth = 8, budgetMs = 5_000)
            assertEquals(cdm.absolutePath, found)
        } finally {
            home.deleteRecursively()
        }
    }

    @Test
    fun `caches are not walked`() {
        val home = tempHome()
        try {
            // Same file name, in a directory the walk must refuse to enter. Returning it would mean the
            // scan crawls the gigabytes of cache a browser keeps next to its profile.
            cdmAt(home, "Cache/libwidevinecdm.so")
            cdmAt(home, ".config/Browser/Code Cache/libwidevinecdm.so")
            val found = NativeWidevineCdm.scanForCdm(listOf(home.absolutePath), maxDepth = 8, budgetMs = 5_000)
            assertNull(found)
        } finally {
            home.deleteRecursively()
        }
    }

    @Test
    fun `the depth limit is respected`() {
        val home = tempHome()
        try {
            cdmAt(home, "a/b/c/d/libwidevinecdm.so")
            assertNull(NativeWidevineCdm.scanForCdm(listOf(home.absolutePath), maxDepth = 2, budgetMs = 5_000))
        } finally {
            home.deleteRecursively()
        }
    }

    @Test
    fun `the time budget is respected`() {
        val home = tempHome()
        try {
            cdmAt(home, "a/b/c/d/libwidevinecdm.so")
            val started = System.currentTimeMillis()
            // A budget of zero has to come back immediately rather than after the walk.
            NativeWidevineCdm.scanForCdm(listOf(home.absolutePath), maxDepth = 8, budgetMs = 0)
            val elapsed = System.currentTimeMillis() - started
            assertEquals("a zero budget should not scan", true, elapsed < 500)
        } finally {
            home.deleteRecursively()
        }
    }
}
