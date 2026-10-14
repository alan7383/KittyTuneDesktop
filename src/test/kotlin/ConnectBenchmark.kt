import com.alananasss.kittytune.data.sync.ConnectCredentials
import com.alananasss.kittytune.data.sync.ConnectMessage
import com.alananasss.kittytune.data.sync.ConnectWire
import com.alananasss.kittytune.data.sync.PlaybackSnapshot
import com.alananasss.kittytune.data.sync.ConnectQueueWindow
import com.alananasss.kittytune.data.sync.ConnectStatePublisher
import com.alananasss.kittytune.domain.Track
import java.lang.management.ManagementFactory

/** Reproducible isolated microbenchmark; reports observations, not a CI timing gate. */
object ConnectBenchmark {
    @JvmStatic fun main(args: Array<String>) {
        val state = PlaybackSnapshot("benchmark-device", 1L,
            (1L..500L).map { Track(id = it, title = "Track $it", artworkUrl = null,
                durationMs = 180000, user = null, source = "soundcloud") },
            250, 42000, true, false, "NONE")
        val bean = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
        val thread = Thread.currentThread().id
        if (bean?.isThreadAllocatedMemorySupported == true) bean.isThreadAllocatedMemoryEnabled = true
        repeat(1000) { ConnectWire.queueVersion(state) }
        val allocated = bean?.getThreadAllocatedBytes(thread) ?: 0L
        val start = System.nanoTime()
        var checksum = ""
        repeat(10000) { checksum = ConnectWire.queueVersion(state) }
        val elapsed = System.nanoTime() - start
        val bytes = (bean?.getThreadAllocatedBytes(thread) ?: 0L) - allocated
        check(checksum.length == 24)
        println("{\"benchmark\":\"queueFingerprint500\",\"iterations\":10000,\"nsPerCall\":${elapsed / 10000},\"bytesPerCall\":${bytes / 10000}}")
        val credentials = ConnectCredentials.derive("benchmark-device", "a".repeat(32), "benchmark-peer", "b".repeat(32))
        val full = ConnectMessage(kind = "state", sender = state.deviceId,
            session = "benchmark-session-1234", sequence = 1, state = state)
        val delta = full.copy(state = state.copy(queue = emptyList()))
        println("{\"benchmark\":\"wireBytes500\",\"full\":${ConnectWire.seal(credentials, full).length},\"compressed\":${ConnectWire.seal(credentials, full, true).length},\"delta\":${ConnectWire.seal(credentials, delta).length}}")
        val capture = ConnectQueueWindow()
        val publisher = ConnectStatePublisher()
        val paused = state.copy(isPlaying = false)
        fun tick() = publisher.plan(paused.copy(queue = capture.capture(state.queue, state.currentIndex)!!.queue), nowNanos = 1)
        tick()
        repeat(10000) { check(tick() == null) }
        val beforeTicks = bean?.getThreadAllocatedBytes(thread) ?: 0L
        val tickStart = System.nanoTime()
        repeat(100000) { check(tick() == null) }
        val tickElapsed = System.nanoTime() - tickStart
        val tickBytes = (bean?.getThreadAllocatedBytes(thread) ?: 0L) - beforeTicks
        println("{\"benchmark\":\"suppressedCapture500\",\"iterations\":100000,\"nsPerCall\":${tickElapsed / 100000},\"bytesPerCall\":${tickBytes / 100000}}")
    }
}
