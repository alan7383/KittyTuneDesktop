package com.alananasss.kittytune.audio

import com.alananasss.kittytune.data.drm.ContentKey
import com.alananasss.kittytune.data.drm.IsoBmff
import com.alananasss.kittytune.data.drm.WidevineLicenseSession
import com.sun.management.ThreadMXBean
import com.sun.net.httpserver.HttpServer
import java.awt.EventQueue
import java.lang.management.ManagementFactory
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

object HlsRetirementBenchmark {
    @JvmStatic fun main(args: Array<String>) {
        val warmups = args[0].toInt()
        val samples = args[1].toInt()
        val holdMs = args[2].toLong()
        val fixture = HlsRetirementBenchmark::class.java.classLoader.getResourceAsStream("drm/cenc_small.mp4")!!.use { it.readBytes() }
        val firstFragment = IsoBmff.boxes(fixture, 0, fixture.size).first { it.type == "moof" }.start
        val init = fixture.copyOfRange(0, firstFragment)
        val segment = fixture.copyOfRange(firstFragment, fixture.size)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val playlist = """
            #EXTM3U
            #EXT-X-KEY:METHOD=SAMPLE-AES,URI="data:text/plain;base64,AQ==",KEYFORMAT="urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:2.0,
            segment.m4s
            #EXTINF:2.0,
            segment.m4s
            #EXT-X-ENDLIST
        """.trimIndent().toByteArray()
        for ((path, bytes) in listOf("/track.m3u8" to playlist, "/init.mp4" to init, "/segment.m4s" to segment)) {
            server.createContext(path) { exchange ->
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        }
        server.start()
        val timer = Executors.newSingleThreadScheduledExecutor()
        val engine = AudioEngine()
        val adapterField = AudioEngine::class.java.getDeclaredField("hlsAdapter").apply { isAccessible = true }
        val bean = ManagementFactory.getThreadMXBean() as ThreadMXBean
        bean.isThreadAllocatedMemoryEnabled = true
        bean.isThreadCpuTimeEnabled = true
        try {
            EventQueue.invokeAndWait { engine.setMediaItem("warmup.mp3") }
            println("scenario,sample,operation_ns,heartbeat_ns,ui_cpu_ns,ui_allocated_bytes,held_ns")
            for (scenario in listOf("idle", "pending-license", "closing-session")) {
                repeat(warmups + samples) { iteration ->
                    val adapter = HlsStreamAdapter("http://127.0.0.1:${server.address.port}/track.m3u8", emptyMap())
                    adapter.licenseToken = "fixture"
                    val licenceEntered = CountDownLatch(1)
                    val gate = CountDownLatch(1)
                    val closed = CountDownLatch(1)
                    val heldNs = AtomicLong()
                    adapter.sessionProvider = { _, _ ->
                        licenceEntered.countDown()
                        if (scenario == "pending-license") check(gate.await(5, TimeUnit.SECONDS))
                        object : WidevineLicenseSession {
                            override fun licenseRequest() = byteArrayOf(1)
                            override fun provideLicense(license: ByteArray) = emptyList<ContentKey>()
                            override fun decryptSample(data: ByteArray, iv: ByteArray, subsamples: List<Pair<Int, Int>>) = data
                            override fun close() {
                                if (scenario == "closing-session") check(gate.await(5, TimeUnit.SECONDS))
                                closed.countDown()
                            }
                        }
                    }
                    adapter.sessionRelease = { it.close() }
                    val reader = thread(name = "licence-fixture") {
                        runCatching {
                            adapter.getInputStream(0).use { input ->
                                val buffer = ByteArray(64 * 1024)
                                check(input.read(buffer) > 0)
                                check(input.read(buffer) > 0)
                            }
                        }
                    }
                    check(licenceEntered.await(3, TimeUnit.SECONDS))
                    if (scenario != "pending-license") {
                        reader.join(3000)
                        check(!reader.isAlive)
                    }
                    adapterField.set(engine, adapter)
                    val operation = CompletableFuture<LongArray>()
                    val heartbeat = CompletableFuture<Long>()
                    val submitted = System.nanoTime()
                    if (scenario != "idle") timer.schedule({
                        heldNs.set(System.nanoTime() - submitted)
                        gate.countDown()
                    }, holdMs, TimeUnit.MILLISECONDS)
                    try {
                        EventQueue.invokeLater {
                            val start = System.nanoTime()
                            val cpu = bean.currentThreadCpuTime
                            val allocated = bean.getThreadAllocatedBytes(Thread.currentThread().threadId())
                            try {
                                engine.setMediaItem("fixture.mp3")
                                operation.complete(longArrayOf(System.nanoTime() - start,
                                    bean.currentThreadCpuTime - cpu,
                                    bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - allocated))
                            } catch (e: Throwable) { operation.completeExceptionally(e) }
                        }
                        EventQueue.invokeLater { heartbeat.complete(System.nanoTime() - submitted) }
                        val result = operation.get(5, TimeUnit.SECONDS)
                        val delay = heartbeat.get(5, TimeUnit.SECONDS)
                        reader.join(3000)
                        check(!reader.isAlive)
                        check(closed.await(3, TimeUnit.SECONDS)) { "discarded session was not released" }
                        if (iteration >= warmups) println("$scenario,${iteration - warmups},${result[0]},$delay,${result[1]},${result[2]},${heldNs.get()}")
                    } finally {
                        gate.countDown()
                        reader.join(3000)
                        adapter.releaseDrm()
                    }
                }
            }
        } finally {
            EventQueue.invokeAndWait { engine.release() }
            timer.shutdownNow()
            server.stop(0)
        }
    }
}
