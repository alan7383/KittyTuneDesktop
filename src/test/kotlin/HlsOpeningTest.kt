package com.alananasss.kittytune.audio

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.*
import org.junit.Test
import java.awt.EventQueue
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class HlsOpeningTest {
    private fun onUi(action: () -> Unit) = CompletableFuture<Unit>().also { future ->
        EventQueue.invokeLater {
            try { action(); future.complete(Unit) } catch (e: Throwable) { future.completeExceptionally(e) }
        }
    }

    private fun blockedRequestDoesNotBlockSelection(path: String) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val requests = AtomicInteger()
        for ((route, body) in listOf("/track.m3u8" to "#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:2.0,\nfragment.m4s\n#EXT-X-ENDLIST", "/init.mp4" to "fixture")) {
            server.createContext(route) { exchange ->
                requests.incrementAndGet()
                if (route == path) { entered.countDown(); finish.await(5, TimeUnit.SECONDS) }
                val bytes = body.toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        }
        server.start()
        val engine = AudioEngine()
        var completed = false
        try {
            val selected = onUi { engine.setMediaItem("http://127.0.0.1:${server.address.port}/track.m3u8") }
            completed = try { selected.get(1, TimeUnit.SECONDS); true }
                catch (_: java.util.concurrent.TimeoutException) { false }
            if (completed) {
                assertEquals("selection started network work before prepare", 0, requests.get())
                assertEquals(AudioEngine.State.BUFFERING, engine.state)
            }
        } finally {
            finish.countDown()
            onUi { engine.release() }.get(3, TimeUnit.SECONDS)
            server.stop(0)
        }
        assertTrue("selecting an HLS track waited for $path on the UI thread", completed)
    }

    @Test fun selectionDoesNotDownloadThePlaylistOnTheUiThread() = blockedRequestDoesNotBlockSelection("/track.m3u8")
    @Test fun selectionDoesNotDownloadTheInitOnTheUiThread() = blockedRequestDoesNotBlockSelection("/init.mp4")

    private fun lateOpeningIsDiscarded(path: String, release: Boolean, repeatSource: Boolean = false) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val fragments = AtomicInteger()
        for ((route, text) in listOf("/track.m3u8" to "#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:2.0,\nfragment.m4s\n#EXT-X-ENDLIST", "/init.mp4" to "fixture", "/fragment.m4s" to "not audio")) {
            server.createContext(route) { exchange ->
                if (route == path) { entered.countDown(); finish.await(5, TimeUnit.SECONDS) }
                if (route == "/fragment.m4s") fragments.incrementAndGet()
                val bytes = text.toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        }
        server.start()
        val engine = AudioEngine()
        val errors = AtomicInteger()
        engine.onError = { errors.incrementAndGet() }
        var worker: Thread? = null
        try {
            val url = "http://127.0.0.1:${server.address.port}/track.m3u8"
            val next = if (repeatSource) url else "next.mp3"
            onUi { engine.setMediaItem(url); engine.prepare() }.get(2, TimeUnit.SECONDS)
            assertTrue("the decoder did not start loading", entered.await(2, TimeUnit.SECONDS))
            worker = AudioEngine::class.java.getDeclaredField("worker").apply { isAccessible = true }.get(engine) as Thread
            onUi { if (release) engine.release() else engine.setMediaItem(next) }.get(1, TimeUnit.SECONDS)
            finish.countDown()
            worker.join(3000)
            assertFalse("the superseded decoder did not exit", worker.isAlive)
            assertEquals(if (release) null else next, engine.currentUrl)
            assertNull("an old opening replaced the selected source", AudioEngine::class.java.getDeclaredField("hlsAdapter").apply { isAccessible = true }.get(engine))
            assertEquals("a discarded opening reached the decoder", 0, fragments.get())
            assertEquals("a discarded opening reported an error for the new track", 0, errors.get())
        } finally {
            finish.countDown()
            worker?.join(3000)
            onUi { engine.release() }.get(3, TimeUnit.SECONDS)
            server.stop(0)
        }
    }

    @Test fun switchingTracksDuringPlaylistLoadingDiscardsTheOldOpening() = lateOpeningIsDiscarded("/track.m3u8", false)
    @Test fun releasingDuringInitLoadingDiscardsTheOldOpening() = lateOpeningIsDiscarded("/init.mp4", true)
    @Test fun selectingTheSameUrlAgainStillDiscardsTheOldOpening() = lateOpeningIsDiscarded("/track.m3u8", false, true)

    @Test fun decoderOpeningPreservesHeadersTokenAndReusesTheAdapter() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requests = AtomicInteger()
        val headers = java.util.concurrent.CopyOnWriteArrayList<String?>()
        for ((route, text) in listOf("/track.m3u8" to "#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:2.0,\nfragment.m4s\n#EXT-X-ENDLIST", "/init.mp4" to "fixture")) {
            server.createContext(route) { exchange ->
                requests.incrementAndGet()
                headers += exchange.requestHeaders.getFirst("X-Source-Test")
                val bytes = text.toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        }
        server.start()
        val engine = AudioEngine()
        try {
            val url = "http://127.0.0.1:${server.address.port}/track.m3u8"
            val requestHeaders = mapOf("X-Source-Test" to "selected")
            engine.setMediaItem(url, requestHeaders)
            assertEquals(0, requests.get())
            AudioEngine::class.java.getDeclaredField("stopFlag").apply { isAccessible = true }.setBoolean(engine, false)
            AudioEngine::class.java.getDeclaredField("licenseToken").apply { isAccessible = true }.set(engine, "fixture-token")
            val id = AudioEngine::class.java.getDeclaredField("activeWorkerId").apply { isAccessible = true }.getLong(engine)
            val create = AudioEngine::class.java.declaredMethods.single { it.name == "createGrabber" }.apply { isAccessible = true }
            val first = create.invoke(engine, id, url, requestHeaders, 0L) as org.bytedeco.javacv.FFmpegFrameGrabber
            try {
                val adapter = AudioEngine::class.java.getDeclaredField("hlsAdapter").apply { isAccessible = true }.get(engine) as HlsStreamAdapter
                assertEquals("fixture-token", adapter.licenseToken)
                assertEquals(2000L, adapter.totalDurationMs)
                assertEquals("mp4", first.format)
                assertEquals(listOf("selected", "selected"), headers.toList())
                val second = create.invoke(engine, id, url, requestHeaders, 1000L) as org.bytedeco.javacv.FFmpegFrameGrabber
                try {
                    assertSame(adapter, AudioEngine::class.java.getDeclaredField("hlsAdapter").apply { isAccessible = true }.get(engine))
                    assertEquals("a seek fetched the playlist/init again", 2, requests.get())
                } finally { second.release() }
            } finally { first.release() }
        } finally { engine.release(); server.stop(0) }
    }

    @Test fun selectionStillConsumesThePrefetchedHead() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requests = AtomicInteger()
        for ((route, text) in listOf("/track.m3u8" to "#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:2.0,\nfragment.m4s\n#EXT-X-ENDLIST", "/init.mp4" to "fixture", "/fragment.m4s" to "fixture")) {
            server.createContext(route) { exchange ->
                requests.incrementAndGet()
                val bytes = text.toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        }
        server.start()
        val engine = AudioEngine()
        try {
            val url = "http://127.0.0.1:${server.address.port}/track.m3u8"
            HlsHeadCache.warm(url, emptyMap())
            val ready = HlsHeadCache::class.java.getDeclaredField("ready").apply { isAccessible = true }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            var cached: Any? = null
            while (cached == null && System.nanoTime() < deadline) {
                cached = synchronized(HlsHeadCache) { (ready.get(HlsHeadCache) as Map<*, *>)[url] }
                if (cached == null) Thread.sleep(5)
            }
            assertNotNull("head prefetch did not finish", cached)
            val before = requests.get()
            onUi { engine.setMediaItem(url) }.get(1, TimeUnit.SECONDS)
            assertSame(cached, AudioEngine::class.java.getDeclaredField("hlsAdapter").apply { isAccessible = true }.get(engine))
            assertEquals(before, requests.get())
        } finally { engine.release(); server.stop(0) }
    }
}
