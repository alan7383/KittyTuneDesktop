package com.alananasss.kittytune.experiments

import com.alananasss.kittytune.data.LocalMediaRepository
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.PlayerPreferences
import kotlinx.coroutines.runBlocking
import java.io.File
import java.lang.management.ManagementFactory

object LocalScanBenchmark {
    @JvmStatic fun main(args: Array<String>) = runBlocking {
        check(System.getenv("KITTYTUNE_SCAN_BENCHMARK") == "1") { "Use the isolated benchmark runner" }
        val root = File(args[0])
        val expectedCount = args[1].toInt()
        val label = args[2]
        require(root.isDirectory)
        AppDatabase.init()
        val prefs = PlayerPreferences()
        require(prefs.getLocalMediaUris().isEmpty()) { "Use a fresh, isolated profile" }
        prefs.addLocalMediaUri(root.absolutePath)
        LocalMediaRepository.scanLocalMedia()
        val expected = AppDatabase.downloadDao.getAllTracksList().sortedBy { it.id }
        check(expected.size == expectedCount) { "Imported ${expected.size}, expected $expectedCount" }
        repeat(2) { LocalMediaRepository.scanLocalMedia() }

        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        threads.isThreadAllocatedMemoryEnabled = true
        val process = ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean
        fun allocated() = threads.getThreadAllocatedBytes(threads.allThreadIds).filter { it >= 0 }.sum()

        repeat(5) { sample ->
            val allocatedBefore = allocated()
            val cpuBefore = process.processCpuTime
            val start = System.nanoTime()
            LocalMediaRepository.scanLocalMedia()
            val elapsed = System.nanoTime() - start
            val cpu = process.processCpuTime - cpuBefore
            val bytes = allocated() - allocatedBefore
            check(!LocalMediaRepository.isScanning.value)
            check(AppDatabase.downloadDao.getAllTracksList().sortedBy { it.id } == expected)
            println("{\"variant\":\"$label\",\"sample\":$sample,\"wall_ms\":${elapsed / 1e6},\"cpu_ms\":${cpu / 1e6},\"allocated_bytes\":$bytes,\"tracks\":${expected.size}}")
        }
    }
}
