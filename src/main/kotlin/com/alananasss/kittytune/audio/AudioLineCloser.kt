package com.alananasss.kittytune.audio

import java.util.concurrent.Executors
import javax.sound.sampled.SourceDataLine

internal class AudioLineCloser : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "audio-line-closer").apply { isDaemon = true }
    }

    fun closeLater(line: SourceDataLine) {
        executor.execute {
            try {
                line.close()
            } catch (_: Exception) {}
        }
    }

    override fun close() {
        executor.shutdown()
    }
}
