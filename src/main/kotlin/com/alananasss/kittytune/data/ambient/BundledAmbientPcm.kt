package com.alananasss.kittytune.data.ambient

import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import java.io.ByteArrayInputStream
import java.nio.ShortBuffer

internal interface AmbientPcm : AutoCloseable {
    fun read(buffer: ByteArray): Int
}

internal class BundledAmbientPcm(resourceName: String) : AmbientPcm {
    private val grabber: FFmpegFrameGrabber
    private var samples: ShortBuffer? = null
    private var closed = false

    init {
        val compressed = BundledAmbientPcm::class.java.getResourceAsStream("/raw/$resourceName")!!.use { it.readBytes() }
        // Seekable compressed input preserves MP3 encoder-padding removal at the loop boundary.
        grabber = FFmpegFrameGrabber(ByteArrayInputStream(compressed), compressed.size)
        try {
            grabber.sampleRate = 44100
            grabber.audioChannels = 2
            grabber.sampleMode = FrameGrabber.SampleMode.SHORT
            grabber.start()
        } catch (failure: Throwable) {
            runCatching { grabber.close() }
            throw failure
        }
    }

    override fun read(buffer: ByteArray): Int {
        check(!closed)
        require(buffer.size >= 4 && buffer.size % 4 == 0)
        while (samples?.hasRemaining() != true) {
            val frame = grabber.grabSamples() ?: return -1
            val buffers = frame.samples ?: continue
            samples = buffers.singleOrNull() as? ShortBuffer ?: error("Expected stereo S16 PCM")
        }
        val input = samples!!
        val count = minOf(buffer.size / 2, input.remaining())
        check(count % 2 == 0)
        for (i in 0 until count) {
            val value = input.get().toInt()
            buffer[i * 2] = value.toByte()
            buffer[i * 2 + 1] = (value shr 8).toByte()
        }
        return count * 2
    }

    override fun close() {
        if (closed) return
        closed = true
        samples = null
        grabber.close()
    }
}
