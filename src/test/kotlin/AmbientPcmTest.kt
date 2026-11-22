package com.alananasss.kittytune.data.ambient

import com.sun.management.ThreadMXBean
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.ffmpeg.global.avcodec
import javax.sound.sampled.AudioSystem
import java.lang.management.ManagementFactory
import java.security.MessageDigest

class AmbientPcmTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun originalFingerprint(resource: String): String {
        val mp3 = tmp.newFile(resource)
        javaClass.getResourceAsStream("/raw/$resource")!!.use { input -> mp3.outputStream().use(input::copyTo) }
        val wav = tmp.newFile("$resource.wav")
        FFmpegFrameGrabber(mp3).use { grabber ->
            grabber.sampleRate = 44100
            grabber.audioChannels = 2
            grabber.start()
            FFmpegFrameRecorder(wav, 2).use { recorder ->
                recorder.format = "wav"
                recorder.sampleRate = 44100
                recorder.audioChannels = 2
                recorder.audioCodec = avcodec.AV_CODEC_ID_PCM_S16LE
                recorder.start()
                while (true) {
                    val frame = grabber.grabSamples() ?: break
                    recorder.recordSamples(frame.sampleRate, frame.audioChannels, *frame.samples)
                }
            }
        }
        val hash = MessageDigest.getInstance("SHA-256")
        var total = 0L
        AudioSystem.getAudioInputStream(wav).use { input ->
            val bytes = ByteArray(8192)
            while (true) {
                val count = input.read(bytes)
                if (count < 0) break
                hash.update(bytes, 0, count)
                total += count
            }
        }
        return "$total:" + hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    private fun fingerprint(resource: String): String {
        val hash = MessageDigest.getInstance("SHA-256")
        var total = 0L
        BundledAmbientPcm(resource).use { source ->
            val block = ByteArray(8192)
            while (true) {
                val n = source.read(block)
                if (n < 0) break
                assertTrue(n > 0 && n <= block.size && n % 4 == 0)
                hash.update(block, 0, n)
                total += n
            }
        }
        return "$total:" + hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    @Test fun rainMatchesOriginalPcm() = assertEquals(originalFingerprint("rain.mp3"), fingerprint("rain.mp3"))
    @Test fun oceanMatchesOriginalPcm() = assertEquals(originalFingerprint("ocean.mp3"), fingerprint("ocean.mp3"))
    @Test fun fireplaceMatchesOriginalPcm() = assertEquals(originalFingerprint("fireplace.mp3"), fingerprint("fireplace.mp3"))
    @Test fun cafeMatchesOriginalPcm() = assertEquals(originalFingerprint("cafe.mp3"), fingerprint("cafe.mp3"))

    @Test fun firstBlockDoesNotAllocateTheWholeDecodedLoop() {
        val bean = ManagementFactory.getThreadMXBean()
        assumeTrue(bean is ThreadMXBean && bean.isThreadAllocatedMemorySupported)
        bean as ThreadMXBean
        bean.isThreadAllocatedMemoryEnabled = true
        BundledAmbientPcm("rain.mp3").use { it.read(ByteArray(8192)) }
        val id = Thread.currentThread().threadId()
        val before = bean.getThreadAllocatedBytes(id)
        BundledAmbientPcm("rain.mp3").use { source ->
            assertTrue(source.read(ByteArray(8192)) > 0)
            val allocated = bean.getThreadAllocatedBytes(id) - before
            assertTrue("Allocated $allocated bytes before the first block", allocated < 8L * 1024 * 1024)
        }
    }
}
