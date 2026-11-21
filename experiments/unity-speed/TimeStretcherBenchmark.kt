package com.alananasss.kittytune.experiments

import com.alananasss.kittytune.audio.TimeStretcher
import java.lang.management.ManagementFactory

object TimeStretcherBenchmark {
    @Volatile private var sink: Any? = null

    @JvmStatic fun main(args: Array<String>) {
        val variant = args[0]
        val pcm = Array(32) { block -> ShortArray(2048) { ((it * 173 + block * 541) xor (it shl 3)).toShort() } }
        val output = ShortArray(4096)
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        threads.isThreadAllocatedMemoryEnabled = true
        val os = ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean
        val id = Thread.currentThread().threadId()

        for (mode in listOf("unity", "pitch", "stretch")) {
            val stage = TimeStretcher(44100, 2)
            when (mode) {
                "pitch" -> stage.setParameters(1.25f, 1.25f)
                "stretch" -> stage.setParameters(1.25f, 1f)
            }
            val blocks = if (mode == "unity") 16384 else 64
            fun run(): Long {
                stage.flush()
                var checksum = 0L
                repeat(blocks) { block ->
                    val input = pcm[block % pcm.size]
                    stage.queue(input, input.size)
                    while (stage.available() > 0) {
                        val n = stage.drain(output, output.size)
                        checksum = checksum * 31 + n + output[0] + output[n - 1]
                    }
                }
                sink = checksum
                return checksum
            }
            repeat(if (mode == "unity") 3 else 12) { run() }
            repeat(5) { sample ->
                val allocated = threads.getThreadAllocatedBytes(id)
                val cpu = os.processCpuTime
                val start = System.nanoTime()
                val checksum = run()
                val wall = System.nanoTime() - start
                val cpuDelta = os.processCpuTime - cpu
                val allocationDelta = threads.getThreadAllocatedBytes(id) - allocated
                println("{\"variant\":\"$variant\",\"mode\":\"$mode\",\"sample\":$sample,\"wall_ms\":${wall / 1e6},\"cpu_ms\":${cpuDelta / 1e6},\"allocated_bytes\":$allocationDelta,\"checksum\":$checksum}")
            }
        }

        repeat(3) {
            repeat(32) { sink = TimeStretcher(44100, 2).also { it.queue(pcm[0], pcm[0].size); it.flush() } }
        }
        repeat(5) { sample ->
            val before = threads.getThreadAllocatedBytes(id)
            repeat(64) { sink = TimeStretcher(44100, 2).also { it.queue(pcm[0], pcm[0].size); it.flush() } }
            val allocated = (threads.getThreadAllocatedBytes(id) - before) / 64
            println("{\"variant\":\"$variant\",\"mode\":\"first-block\",\"sample\":$sample,\"allocated_bytes\":$allocated}")
        }
    }
}
