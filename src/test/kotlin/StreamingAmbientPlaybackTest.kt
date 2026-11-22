package com.alananasss.kittytune.data.ambient

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.FloatControl

class StreamingAmbientPlaybackTest {
    private class Output(private val blockWrites: Boolean = false, private val gain: FloatControl? = null) {
        val bytes = ByteArrayOutputStream()
        val writing = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val starts = AtomicInteger()
        val line = Proxy.newProxyInstance(SourceDataLine::class.java.classLoader, arrayOf(SourceDataLine::class.java)) { _, method, args ->
            when (method.name) {
                "start" -> { starts.incrementAndGet(); null }
                "close" -> { closed.countDown(); null }
                "isControlSupported" -> gain != null
                "getControl" -> gain
                "write" -> {
                    writing.countDown()
                    if (blockWrites) {
                        closed.await(5, TimeUnit.SECONDS)
                        0
                    } else {
                        val count = minOf(args!![2] as Int, 4)
                        bytes.write(args[0] as ByteArray, args[1] as Int, count)
                        count
                    }
                }
                else -> null
            }
        } as SourceDataLine
    }

    private fun source(bytes: ByteArray, close: () -> Unit = {}): AmbientPcm = object : AmbientPcm {
        var done = false
        override fun read(buffer: ByteArray): Int {
            if (done || bytes.isEmpty()) return -1
            done = true
            bytes.copyInto(buffer)
            return bytes.size
        }
        override fun close() = close.invoke()
    }

    @Test fun loopsStayOnOneLineAndHandlePartialWrites() {
        val output = Output()
        val opens = AtomicInteger()
        val closes = AtomicInteger()
        val ready = AtomicInteger()
        val pcm = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val playback = StreamingAmbientPlayback(
            { source(if (opens.incrementAndGet() <= 2) pcm else byteArrayOf()) { closes.incrementAndGet() } },
            { output.line }, 0.4f, { ready.incrementAndGet() },
        )
        playback.start()
        playback.start()
        assertTrue(output.closed.await(5, TimeUnit.SECONDS))
        assertArrayEquals(pcm + pcm, output.bytes.toByteArray())
        assertEquals(3, opens.get())
        assertEquals(3, closes.get())
        assertEquals(1, output.starts.get())
        assertEquals(1, ready.get())
        playback.close()
    }

    @Test fun stoppingUnblocksTheLineAndReleasesDecoderOnItsWorker() {
        val output = Output(blockWrites = true)
        val sourceClosed = CountDownLatch(1)
        var closeThread: String? = null
        val playback = StreamingAmbientPlayback(
            { source(ByteArray(8)) { closeThread = Thread.currentThread().name; sourceClosed.countDown() } },
            { output.line }, 1f,
        )
        playback.start()
        assertTrue(output.writing.await(5, TimeUnit.SECONDS))
        playback.close()
        assertTrue(sourceClosed.await(5, TimeUnit.SECONDS))
        assertEquals("ambient-audio", closeThread)
    }

    @Test fun stoppingDuringDeviceOpenClosesTheLateLineWithoutOpeningADecoder() {
        val output = Output()
        val opening = CountDownLatch(1)
        val finishOpen = CountDownLatch(1)
        val sources = AtomicInteger()
        val playback = StreamingAmbientPlayback(
            { sources.incrementAndGet(); source(ByteArray(8)) },
            {
                opening.countDown()
                while (true) {
                    try { if (finishOpen.await(5, TimeUnit.SECONDS)) break } catch (_: InterruptedException) { }
                }
                output.line
            }, 1f,
        )
        playback.start()
        assertTrue(opening.await(5, TimeUnit.SECONDS))
        playback.close()
        finishOpen.countDown()
        assertTrue(output.closed.await(5, TimeUnit.SECONDS))
        assertEquals(0, sources.get())
    }

    @Test fun closingBeforeStartDoesNotOpenAnything() {
        val opens = AtomicInteger()
        val playback = StreamingAmbientPlayback({ error("Unexpected decoder") }, { opens.incrementAndGet(); Output().line }, 1f)
        playback.close()
        playback.start()
        assertEquals(0, opens.get())
    }

    @Test fun gainIsSetBeforePlaybackAndTracksVolumeChanges() {
        val gain = object : FloatControl(FloatControl.Type.MASTER_GAIN, -80f, 6f, 0.1f, -1, 0f, "dB") { }
        val output = Output(blockWrites = true, gain = gain)
        val playback = StreamingAmbientPlayback({ source(ByteArray(8)) }, { output.line }, 0.5f)
        playback.start()
        assertTrue(output.writing.await(5, TimeUnit.SECONDS))
        assertEquals(-6.0206f, gain.value, 0.001f)
        playback.setVolume(0f)
        assertEquals(-80f, gain.value, 0f)
        playback.close()
    }
}
