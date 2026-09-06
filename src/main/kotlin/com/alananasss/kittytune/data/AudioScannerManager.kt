package com.alananasss.kittytune.data

import com.alananasss.kittytune.audio.NormalizationAudioProcessor
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.FrameGrabber
import java.io.File
import java.nio.FloatBuffer
import java.nio.ShortBuffer

data class TrackLoudnessInfo(
    val integratedLufs: Float,
    val truePeakDb: Float
)

object AudioScannerManager {

    init {
        NormalizationAudioProcessor.loadNativeLibrary()
    }

    private external fun nativeCreateAnalyzer(channels: Int, sampleRate: Int): Long
    private external fun nativeDestroyAnalyzer(handle: Long)
    private external fun nativeAddFramesFloat(handle: Long, samples: FloatArray, numFrames: Int)
    private external fun nativeAddFramesShort(handle: Long, samples: ShortArray, numFrames: Int)
    private external fun nativeGetAnalyzedLoudness(handle: Long): Float
    private external fun nativeGetAnalyzedTruePeakDb(handle: Long): Float

    fun scanAudioFile(file: File): TrackLoudnessInfo? {
        if (!file.exists()) return null
        return scanSource(file.absolutePath)
    }

    fun scanStream(url: String, headers: Map<String, String> = emptyMap()): TrackLoudnessInfo? {
        return scanSource(url, headers)
    }

    private fun scanSource(source: String, headers: Map<String, String> = emptyMap()): TrackLoudnessInfo? {
        var grabber: FFmpegFrameGrabber? = null
        var analyzer: Long = 0L
        try {
            grabber = FFmpegFrameGrabber(source).apply {
                if (headers.isNotEmpty()) {
                    val headerBlob = headers.entries.joinToString("\r\n") { "${it.key}: ${it.value}" }
                    setOption("headers", headerBlob + "\r\n")
                }
                setOption("timeout", "5000000") // 5s timeout
                sampleRate = 44100
                audioChannels = 2
                sampleMode = FrameGrabber.SampleMode.FLOAT
            }
            grabber.start()

            val channels = grabber.audioChannels
            val sr = grabber.sampleRate
            if (channels <= 0 || sr <= 0) return null

            analyzer = nativeCreateAnalyzer(channels, sr)
            if (analyzer == 0L) return null

            var floatBuf = FloatArray(8192)

            while (true) {
                val frame: Frame = grabber.grabSamples() ?: break
                val buffers = frame.samples ?: continue
                if (buffers.isEmpty()) continue

                val first = buffers[0]
                val planar = buffers.size == channels && channels > 1
                val len = first.remaining()
                val required = if (planar) len * channels else len
                if (required <= 0) continue
                if (floatBuf.size < required) floatBuf = FloatArray(required)

                if (!planar && first is FloatBuffer) {
                    val pos = first.position()
                    first.get(floatBuf, 0, len)
                    first.position(pos)
                    val numFrames = len / channels
                    if (numFrames > 0) {
                        nativeAddFramesFloat(analyzer, floatBuf, numFrames)
                    }
                } else if (planar) {
                    for (ch in 0 until channels) {
                        val b = buffers[ch] as? FloatBuffer ?: continue
                        val bPos = b.position()
                        for (i in 0 until len) {
                            floatBuf[i * channels + ch] = b.get(bPos + i)
                        }
                    }
                    nativeAddFramesFloat(analyzer, floatBuf, len)
                }
            }

            val lufs = nativeGetAnalyzedLoudness(analyzer)
            val peak = nativeGetAnalyzedTruePeakDb(analyzer)

            if (lufs > -60f && lufs < 0f) {
                return TrackLoudnessInfo(lufs, peak)
            }
            return null
        } catch (_: Throwable) {
            return null
        } finally {
            if (analyzer != 0L) {
                try { nativeDestroyAnalyzer(analyzer) } catch (_: Throwable) {}
            }
            try {
                grabber?.stop()
                grabber?.release()
            } catch (_: Throwable) {}
        }
    }
}
