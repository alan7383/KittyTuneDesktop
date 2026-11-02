package com.alananasss.kittytune.audio.beat

import com.alananasss.kittytune.audio.AudioFormat
import com.alananasss.kittytune.audio.BaseAudioProcessor
import java.nio.ByteBuffer

/**
 * Pass-through processor feeding [LiveBeatTracker] (one per AudioEngine). It reads the PCM without
 * touching it. Unlike Media3, the desktop chain's flush carries no media position, so the engine
 * anchors the tracker itself ([LiveBeatTracker.onFlush]) wherever it starts or seeks a stream.
 */
class LiveBeatAudioProcessor(val tracker: LiveBeatTracker) : BaseAudioProcessor() {

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        tracker.configure(inputAudioFormat.sampleRate)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        analyze(inputBuffer)
        val out = replaceOutputBuffer(remaining)
        out.put(inputBuffer)
        out.flip()
    }

    private fun analyze(buffer: ByteBuffer) {
        // the desktop chain always carries 16-bit PCM
        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        val frameBytes = 2 * channels
        val start = buffer.position()
        val frames = buffer.remaining() / frameBytes
        val used = minOf(channels, 2)
        for (i in 0 until frames) {
            val p = start + i * frameBytes
            var sum = 0f
            for (ch in 0 until used) sum += buffer.getShort(p + ch * 2) / 32768f
            tracker.push(sum / used)
        }
        tracker.onBufferDone()
    }

    override fun onReset() {
        tracker.onFlush(0.0)
    }
}
