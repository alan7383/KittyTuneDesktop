package com.alananasss.kittytune.experiments

import com.alananasss.kittytune.data.ambient.BundledAmbientPcm
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import java.io.File
import java.lang.management.ManagementFactory
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

object AmbientMemoryProbe {
    private val format = AudioFormat(44100f, 16, 2, true, false)

    @JvmStatic fun main(args: Array<String>) {
        val mode = args[0]
        val root = File(args[1])
        val wav = File(root, "rain.wav")
        if (mode == "prepare") {
            val mp3 = File(root, "rain.mp3")
            javaClass.getResourceAsStream("/raw/rain.mp3")!!.use { input -> mp3.outputStream().use(input::copyTo) }
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
            println("PREPARED ${wav.length()}")
            return
        }
        fun openClip() = AudioSystem.getClip().also { clip ->
            AudioSystem.getAudioInputStream(wav).use { clip.open(it) }
        }
        fun openLine() = AudioSystem.getSourceDataLine(format).also { it.open(format, 88_200) }
        repeat(2) {
            openClip().close()
            openLine().close()
            BundledAmbientPcm("rain.mp3").use { it.read(ByteArray(8192)) }
        }
        val commands = System.`in`.bufferedReader()
        fun report(phase: String) {
            repeat(2) { System.gc(); Thread.sleep(100) }
            println("{\"phase\":\"$phase\",\"heap_used\":${ManagementFactory.getMemoryMXBean().heapMemoryUsage.used}}")
            System.out.flush()
            check(commands.readLine() == "continue")
        }
        report("before")
        if (mode == "baseline") {
            openClip().use { report("open") }
        } else {
            openLine().use {
                BundledAmbientPcm("rain.mp3").use { source ->
                    check(source.read(ByteArray(8192)) > 0)
                    report("open")
                }
            }
        }
        report("closed")
        repeat(3) { sample ->
            val cpuBefore = ManagementFactory.getThreadMXBean().currentThreadCpuTime
            val start = System.nanoTime()
            var bytes = 0L
            BundledAmbientPcm("rain.mp3").use { source ->
                val buffer = ByteArray(8192)
                while (true) {
                    val n = source.read(buffer)
                    if (n < 0) break
                    bytes += n
                }
            }
            val wall = (System.nanoTime() - start) / 1e6
            val cpu = (ManagementFactory.getThreadMXBean().currentThreadCpuTime - cpuBefore) / 1e6
            println("{\"phase\":\"decode\",\"sample\":$sample,\"pcm_bytes\":$bytes,\"wall_ms\":$wall,\"cpu_ms\":$cpu}")
        }
    }
}
