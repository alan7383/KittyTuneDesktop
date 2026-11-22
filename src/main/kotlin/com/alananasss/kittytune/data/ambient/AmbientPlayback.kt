package com.alananasss.kittytune.data.ambient

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine

internal interface AmbientPlayback : AutoCloseable {
    fun start()
    fun setVolume(volume: Float)
}

internal class StreamingAmbientPlayback(
    private val openSource: () -> AmbientPcm,
    private val openLine: () -> SourceDataLine,
    initialVolume: Float,
    private val onReady: () -> Unit = {},
) : AmbientPlayback {
    private val stopped = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val output = AtomicReference<SourceDataLine?>()
    @Volatile private var volume = initialVolume
    private val worker = Thread(::run, "ambient-audio").apply { isDaemon = true }

    override fun start() {
        if (!stopped.get() && started.compareAndSet(false, true)) worker.start()
    }

    @Synchronized override fun setVolume(volume: Float) {
        this.volume = volume
        output.get()?.let(::applyVolume)
    }

    @Synchronized private fun applyVolume(line: SourceDataLine) {
        runCatching {
            if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                val gain = line.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
                gain.value = if (volume <= 0.0001f) gain.minimum
                    else (20.0 * Math.log10(volume.toDouble())).toFloat().coerceIn(gain.minimum, gain.maximum)
            }
        }
    }

    private fun run() {
        var line: SourceDataLine? = null
        try {
            if (stopped.get()) return
            val active = openLine()
            line = active
            output.set(active)
            if (stopped.get()) return
            applyVolume(active)
            val buffer = ByteArray(8192)
            var ready = false
            while (!stopped.get()) {
                var loopBytes = 0L
                openSource().use { source ->
                    while (!stopped.get()) {
                        val size = source.read(buffer)
                        if (size < 0) break
                        check(size > 0 && size <= buffer.size && size % 4 == 0)
                        if (stopped.get()) break
                        if (!ready) {
                            runCatching(onReady)
                            if (stopped.get()) break
                            active.start()
                            ready = true
                        }
                        var offset = 0
                        while (offset < size && !stopped.get()) {
                            val count = active.write(buffer, offset, size - offset)
                            check(count > 0 || stopped.get()) { "Ambient output stopped accepting audio" }
                            offset += count
                        }
                        loopBytes += offset
                    }
                }
                if (loopBytes == 0L) break
            }
        } catch (failure: Exception) {
            if (!stopped.get()) failure.printStackTrace()
        } finally {
            line?.let { active ->
                output.compareAndSet(active, null)
                runCatching { active.close() }
            }
        }
    }

    override fun close() {
        stopped.set(true)
        runCatching { output.getAndSet(null)?.close() }
        worker.interrupt()
    }
}
