package com.alananasss.kittytune.audio

import com.sun.management.ThreadMXBean
import com.sun.net.httpserver.HttpServer
import java.awt.EventQueue
import java.lang.management.ManagementFactory
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.bytedeco.javacv.FFmpegFrameGrabber

object HlsOpeningBenchmark {
    @JvmStatic fun main(args: Array<String>) {
        val warmups = args[0].toInt()
        val samples = args[1].toInt()
        val delayMs = args[2].toLong() / 2
        FFmpegFrameGrabber.tryLoad()
        val requests = ConcurrentHashMap<String, AtomicInteger>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val parts = exchange.requestURI.path.trim('/').split('/')
            val scenario = parts[0]
            val key = "$scenario:${parts[1]}"
            val file = parts[2]
            requests.computeIfAbsent(key) { AtomicInteger() }.incrementAndGet()
            if (scenario == "cold-delayed" && file != "fragment.m4s") Thread.sleep(delayMs)
            val body = if (file == "track.m3u8")
                "#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:2.0,\nfragment.m4s\n#EXT-X-ENDLIST" else "fixture"
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val engine = AudioEngine()
        val bean = ManagementFactory.getThreadMXBean() as ThreadMXBean
        bean.isThreadAllocatedMemoryEnabled = true
        bean.isThreadCpuTimeEnabled = true
        val factory = AudioEngine::class.java.declaredMethods.single { it.name == "createGrabber" }.apply { isAccessible = true }
        val stopFlag = AudioEngine::class.java.getDeclaredField("stopFlag").apply { isAccessible = true }
        val workerId = AudioEngine::class.java.getDeclaredField("activeWorkerId").apply { isAccessible = true }
        val ready = HlsHeadCache::class.java.getDeclaredField("ready").apply { isAccessible = true }
        val loader = Executors.newSingleThreadExecutor { Thread(it, "source-fixture").apply { isDaemon = true } }
        try {
            EventQueue.invokeAndWait { engine.setMediaItem("warmup.mp3") }
            println("scenario,sample,operation_ns,heartbeat_ns,ui_cpu_ns,ui_allocated_bytes,source_ready_ns")
            for (scenario in listOf("cached-head", "cold-local", "cold-delayed")) {
                repeat(warmups + samples) { iteration ->
                    val url = "http://127.0.0.1:${server.address.port}/$scenario/$iteration/track.m3u8"
                    val key = "$scenario:$iteration"
                    val headers = mapOf("X-Source-Fixture" to scenario)
                    if (scenario == "cached-head") {
                        HlsHeadCache.warm(url, headers)
                        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
                        while (synchronized(HlsHeadCache) { !(ready.get(HlsHeadCache) as Map<*, *>).containsKey(url) }) {
                            check(System.nanoTime() < deadline) { "head prefetch timed out" }
                            Thread.sleep(2)
                        }
                    }
                    val selected = CompletableFuture<LongArray>()
                    val heartbeat = CompletableFuture<Long>()
                    val submitted = System.nanoTime()
                    EventQueue.invokeLater {
                        val started = System.nanoTime()
                        val cpu = bean.currentThreadCpuTime
                        val allocated = bean.getThreadAllocatedBytes(Thread.currentThread().threadId())
                        try {
                            engine.setMediaItem(url, headers)
                            selected.complete(longArrayOf(System.nanoTime() - started,
                                bean.currentThreadCpuTime - cpu,
                                bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - allocated))
                        } catch (e: Throwable) { selected.completeExceptionally(e) }
                    }
                    EventQueue.invokeLater { heartbeat.complete(System.nanoTime() - submitted) }
                    val result = selected.get(5, TimeUnit.SECONDS)
                    val heartbeatDelay = heartbeat.get(5, TimeUnit.SECONDS)
                    stopFlag.setBoolean(engine, false)
                    val id = workerId.getLong(engine)
                    loader.submit {
                        val arguments = if (factory.parameterCount == 4) arrayOf(id, url, headers, 0L) else arrayOf(url, headers, 0L)
                        val grabber = factory.invoke(engine, *arguments) as FFmpegFrameGrabber
                        grabber.release()
                    }.get(5, TimeUnit.SECONDS)
                    val sourceReady = System.nanoTime() - submitted
                    val expected = if (scenario == "cached-head") 3 else 2
                    check(requests[key]?.get() == expected) { "network work changed: $key ${requests[key]?.get()} != $expected" }
                    if (iteration >= warmups) println("$scenario,${iteration - warmups},${result[0]},$heartbeatDelay,${result[1]},${result[2]},$sourceReady")
                }
            }
        } finally {
            engine.release()
            loader.shutdownNow()
            server.stop(0)
        }
    }
}
