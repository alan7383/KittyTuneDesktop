package com.alananasss.kittytune.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Desktop replacement for Media3's AudioProcessor contract.
 * PCM audio format descriptor + a minimal processor base class that mirrors
 * androidx.media3.common.audio.BaseAudioProcessor closely enough that the
 * KittyTune DSP processors port over verbatim.
 */
data class AudioFormat(
    val sampleRate: Int,
    val channelCount: Int,
    val bytesPerFrame: Int = channelCount * 2, // 16-bit PCM
)

interface AudioProcessor {
    fun configure(inputFormat: AudioFormat): AudioFormat
    fun queueInput(input: ByteBuffer)
    fun getOutput(): ByteBuffer
    fun flush()
}

/**
 * Mirror of Media3 BaseAudioProcessor: manages a reusable little-endian output
 * ByteBuffer and the input/output format, so subclasses only implement the DSP.
 */
abstract class BaseAudioProcessor : AudioProcessor {

    protected var inputAudioFormat: AudioFormat = AudioFormat(44100, 2)
        private set

    private var buffer: ByteBuffer = EMPTY
    private var outputBuffer: ByteBuffer = EMPTY

    final override fun configure(inputFormat: AudioFormat): AudioFormat {
        inputAudioFormat = inputFormat
        return onConfigure(inputFormat)
    }

    protected open fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat = inputAudioFormat

    protected open fun onFlush() {}

    final override fun flush() {
        outputBuffer = EMPTY
        onFlush()
    }

    /** Allocates (or reuses) a little-endian output buffer of [size] bytes. */
    protected fun replaceOutputBuffer(size: Int): ByteBuffer {
        if (buffer.capacity() < size) {
            buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        } else {
            buffer.clear()
        }
        outputBuffer = buffer
        return buffer
    }

    final override fun getOutput(): ByteBuffer {
        val out = outputBuffer
        outputBuffer = EMPTY
        return out
    }

    companion object {
        val EMPTY: ByteBuffer = ByteBuffer.allocate(0).order(ByteOrder.LITTLE_ENDIAN)
    }
}
