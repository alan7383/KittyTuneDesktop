package com.alananasss.kittytune.audio

import com.sun.net.httpserver.HttpServer
import com.alananasss.kittytune.data.drm.IsoBmff
import com.alananasss.kittytune.data.drm.WidevineLicenseSession
import com.alananasss.kittytune.data.drm.ContentKey
import org.junit.Assert.*
import org.junit.Test
import java.awt.EventQueue
import java.net.InetSocketAddress
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class HlsRetirementTest {
    private fun onUi(action: () -> Unit): CompletableFuture<Unit> = CompletableFuture<Unit>().also { result ->
        EventQueue.invokeLater {
            try { action(); result.complete(Unit) } catch (e: Throwable) { result.completeExceptionally(e) }
        }
    }

    private fun withAdapter(protected: Boolean = false, action: (HlsStreamAdapter) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val fixture = javaClass.classLoader.getResourceAsStream("drm/cenc_small.mp4")!!.use { it.readBytes() }
        val firstFragment = IsoBmff.boxes(fixture, 0, fixture.size).first { it.type == "moof" }.start
        val init = fixture.copyOfRange(0, firstFragment)
        val segment = fixture.copyOfRange(firstFragment, fixture.size)
        val key = if (protected) "#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"data:text/plain;base64,AQ==\",KEYFORMAT=\"urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed\"\n#EXT-X-MAP:URI=\"init.mp4\"\n" else ""
        server.createContext("/track.m3u8") { exchange ->
            val bytes = "#EXTM3U\n${key}#EXTINF:2.0,\nsegment.aac\n#EXTINF:2.0,\nsegment.aac\n#EXT-X-ENDLIST\n".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        for ((path, bytes) in listOf("/init.mp4" to init, "/segment.aac" to segment)) {
            server.createContext(path) { exchange ->
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        }
        server.start()
        try {
            action(HlsStreamAdapter("http://127.0.0.1:${server.address.port}/track.m3u8", emptyMap()))
        } finally { server.stop(0) }
    }

    private fun assertEngineDoesNotWaitForAdapter(release: Boolean) = withAdapter { adapter ->
        val engine = AudioEngine()
        AudioEngine::class.java.getDeclaredField("hlsAdapter").apply { isAccessible = true }.set(engine, adapter)
        val entered = CountDownLatch(1)
        val unblock = CountDownLatch(1)
        val worker = thread(name = "blocked-hls-fixture") {
            synchronized(adapter) {
                entered.countDown()
                check(unblock.await(5, TimeUnit.SECONDS))
            }
        }
        var completedBeforeUnblock = false
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            val operation = onUi {
                if (release) engine.release() else engine.setMediaItem("fixture.mp3")
            }
            completedBeforeUnblock = try {
                operation.get(1, TimeUnit.SECONDS)
                true
            } catch (_: TimeoutException) { false }
        } finally {
            unblock.countDown()
            worker.join(2000)
            onUi { engine.release() }.get(3, TimeUnit.SECONDS)
        }
        assertTrue("the UI waited for the discarded adapter's monitor", completedBeforeUnblock)
    }

    @Test fun changingTrackDoesNotWaitForThePreviousAdapter() = assertEngineDoesNotWaitForAdapter(false)

    @Test fun releasingEngineDoesNotWaitForTheAdapter() = assertEngineDoesNotWaitForAdapter(true)

    private fun session(onClose: () -> Unit) = object : WidevineLicenseSession {
        override fun licenseRequest() = byteArrayOf(1)
        override fun provideLicense(license: ByteArray) = emptyList<ContentKey>()
        override fun decryptSample(data: ByteArray, iv: ByteArray, subsamples: List<Pair<Int, Int>>) = data
        override fun close() = onClose()
    }

    @Test fun retiringDuringALicenceClosesTheLateSessionAndRejectsFurtherReads() = withAdapter(true) { adapter ->
        val engine = AudioEngine()
        AudioEngine::class.java.getDeclaredField("hlsAdapter").apply { isAccessible = true }.set(engine, adapter)
        val entered = CountDownLatch(1)
        val finishLicence = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val opened = AtomicInteger()
        val closes = AtomicInteger()
        adapter.licenseToken = "fixture"
        adapter.sessionProvider = { _, _ ->
            opened.incrementAndGet()
            entered.countDown()
            check(finishLicence.await(5, TimeUnit.SECONDS))
            session { closes.incrementAndGet(); closed.countDown() }
        }
        adapter.sessionRelease = { it.close() }
        val read = CompletableFuture<Throwable?>()
        val reader = thread(name = "licensing-fixture") {
            read.complete(runCatching { adapter.getInputStream(0).use { it.readBytes() } }.exceptionOrNull())
        }
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            onUi { engine.release() }.get(1, TimeUnit.SECONDS)
            finishLicence.countDown()
            assertTrue("the retired reader should fail", read.get(2, TimeUnit.SECONDS) is IOException)
            assertTrue("the late session was leaked", closed.await(2, TimeUnit.SECONDS))
            assertThrows(IOException::class.java) { adapter.getInputStream(0).use { it.readBytes() } }
            assertEquals("a discarded reader opened another licence", 1, opened.get())
            assertEquals(1, closes.get())
        } finally {
            finishLicence.countDown()
            reader.join(2000)
            onUi { engine.release() }.get(3, TimeUnit.SECONDS)
        }
    }

    @Test fun retiringAnOpenSessionDoesNotWaitForItsClose() = withAdapter(true) { adapter ->
        val engine = AudioEngine()
        AudioEngine::class.java.getDeclaredField("hlsAdapter").apply { isAccessible = true }.set(engine, adapter)
        val closing = CountDownLatch(1)
        val finishClose = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val closes = AtomicInteger()
        adapter.licenseToken = "fixture"
        adapter.sessionProvider = { _, _ -> session {
            closes.incrementAndGet()
            closing.countDown()
            check(finishClose.await(5, TimeUnit.SECONDS))
            closed.countDown()
        } }
        adapter.sessionRelease = { it.close() }
        try {
            adapter.getInputStream(0).use { input ->
                val bytes = ByteArray(64 * 1024)
                assertTrue(input.read(bytes) > 0)
                assertTrue(input.read(bytes) > 0)
            }
            onUi { engine.setMediaItem("fixture.mp3") }.get(1, TimeUnit.SECONDS)
            assertTrue(closing.await(2, TimeUnit.SECONDS))
            adapter.retire()
            adapter.retire()
            onUi { engine.release() }.get(1, TimeUnit.SECONDS)
        } finally {
            finishClose.countDown()
            onUi { engine.release() }.get(3, TimeUnit.SECONDS)
        }
        assertTrue(closed.await(2, TimeUnit.SECONDS))
        assertEquals(1, closes.get())
    }
}
