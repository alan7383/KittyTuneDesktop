package com.alananasss.kittytune.audio.ai

import com.alananasss.kittytune.audio.AudioFormat
import com.alananasss.kittytune.audio.BaseAudioProcessor
import com.alananasss.kittytune.data.local.PlayerPreferences
import java.nio.ByteBuffer

/**
 * Desktop port of the Android AiDetectionAudioProcessor (issue #41).
 *
 * A lightweight pass-through AudioProcessor that taps into the PCM stream
 * and feeds it to [AiDetectionManager] for ArtifactNet AI music detection.
 *
 * Completely transparent — never modifies audio data.
 * Zero-allocation during playback via pre-allocated PCM buffer.
 * Configurable analysis window:
 * - 4 seconds (Accurate, Recommended): Full native 4.0s ArtifactNet segment without tiling
 * - 1 second (Ultra-fast): ~1.0s segment tiled with loop crossfading for immediate skips
 *
 * The desktop engine always outputs 44.1 kHz stereo, which is exactly
 * [AiDetectionManager.TARGET_SR], so resampling is a no-op in practice.
 */
class AiDetectionAudioProcessor(
    private var preferences: PlayerPreferences? = null,
) : BaseAudioProcessor() {

    companion object {
        // Maximum collection buffer: 4.5 seconds @ 44.1/48 kHz stereo 16-bit
        private const val MAX_BUFFER_BYTES = 800_000

        // Sampling checkpoints: 0s, 4s, 8s (early transition coverage), then 20s, 40s, 60s (spaced late-onset monitoring)
        val DEFAULT_CHECKPOINTS_4S = doubleArrayOf(0.0, 4.0, 8.0, 20.0, 40.0, 60.0)
        val DEFAULT_CHECKPOINTS_1S = doubleArrayOf(0.0, 1.0, 2.0, 6.0, 12.0, 20.0)
        const val MAX_CHECKPOINTS = 6
    }

    private val preallocatedBuffer = ByteArray(MAX_BUFFER_BYTES)
    private var totalCollected = 0
    private var currentPositionSec = 0.0
    private var checkpointCount = 0
    private var nextCheckpointStartSec = 0.0
    @Volatile private var isAiConfirmed = false
    @Volatile private var allCheckpointsDone = false
    @Volatile private var cachedTargetSeconds = 4
    @Volatile private var warmupTriggered = false

    /** Live prefs, so a window change in settings applies to the next track with no wiring. */
    private val defaultPrefs by lazy { PlayerPreferences() }

    fun setPreferences(prefs: PlayerPreferences) {
        this.preferences = prefs
        this.cachedTargetSeconds = prefs.aiDetectionWindow.seconds
    }

    /** Called when a new track starts. */
    fun resetForNewTrack() {
        synchronized(this) {
            totalCollected = 0
            currentPositionSec = 0.0
            checkpointCount = 0
            nextCheckpointStartSec = 0.0
            isAiConfirmed = false
            allCheckpointsDone = false
            warmupTriggered = false
            cachedTargetSeconds = (preferences ?: defaultPrefs).aiDetectionWindow.seconds
        }
    }

    /** Called on seek to adjust playback position and schedule an immediate sample at the new position. */
    fun notifySeek(seekMs: Long) {
        synchronized(this) {
            totalCollected = 0
            currentPositionSec = seekMs.coerceAtLeast(0) / 1000.0
            if (!isAiConfirmed && !AiDetectionManager.isAiConfirmed() && !AiDetectionManager.isVerdictFinal() && !AiDetectionManager.isAnalyzingUpfront()) {
                allCheckpointsDone = false
                checkpointCount = 0
                nextCheckpointStartSec = currentPositionSec
                AiDetectionManager.notifySeek(currentPositionSec)
            }
        }
    }

    internal fun getCurrentPositionSec(): Double = synchronized(this) { currentPositionSec }
    internal fun isAllCheckpointsDone(): Boolean = synchronized(this) { allCheckpointsDone }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat = inputAudioFormat

    override fun onFlush() {
        // Reset collection on seek/flush so pre-seek and post-seek audio are not concatenated.
        val isFinal = AiDetectionManager.isVerdictFinal()
        val isAi = isAiConfirmed || AiDetectionManager.isAiConfirmed() || AiDetectionManager.result.value?.isAi == true
        synchronized(this) {
            totalCollected = 0
            if (isAi) {
                isAiConfirmed = true
            }
            if (isFinal) {
                allCheckpointsDone = true
            }
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        // Always pass audio to output buffer
        val output = replaceOutputBuffer(remaining)

        // Fast path: if all checkpoints completed or final verdict has been reached,
        // direct buffer transfer with zero heap allocation!
        if (allCheckpointsDone || AiDetectionManager.isVerdictFinal() || isAiConfirmed || AiDetectionManager.isAiConfirmed() || AiDetectionManager.isAnalyzingUpfront()) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        val targetSeconds = cachedTargetSeconds
        val fmt = inputAudioFormat
        val bytesPerSec = fmt.sampleRate * fmt.channelCount * 2 // 16-bit PCM = 2 bytes per sample
        if (bytesPerSec <= 0) {
            output.put(inputBuffer)
            output.flip()
            return
        }
        val targetBytes = (targetSeconds * bytesPerSec).coerceIn(176_400, preallocatedBuffer.size)
        val chunkSec = remaining.toDouble() / bytesPerSec

        // Non-blocking engine warm-up: NEVER call synchronous init() on the audio thread!
        if (!AiDetectionManager.isModelLoaded()) {
            if (!warmupTriggered) {
                warmupTriggered = true
                if (AiDetectionManager.isModelReady()) {
                    AiDetectionManager.initAsync()
                }
            }
            synchronized(this) {
                currentPositionSec += chunkSec
            }
            output.put(inputBuffer)
            output.flip()
            return
        }

        var snapshotToAnalyze: ByteArray? = null

        synchronized(this) {
            if (allCheckpointsDone || AiDetectionManager.isVerdictFinal() || isAiConfirmed || AiDetectionManager.isAiConfirmed() || AiDetectionManager.isAnalyzingUpfront()) {
                allCheckpointsDone = true
                output.put(inputBuffer)
                output.flip()
                return
            }

            // If we are waiting for the next checkpoint window, fast pass-through without copying
            if (currentPositionSec < nextCheckpointStartSec && totalCollected == 0) {
                currentPositionSec += chunkSec
                output.put(inputBuffer)
                output.flip()
                return
            }

            // At or past nextCheckpointStartSec: collect incoming audio
            val toCollect = minOf(remaining, targetBytes - totalCollected)
            if (toCollect > 0) {
                inputBuffer.get(preallocatedBuffer, totalCollected, toCollect)
                totalCollected += toCollect
                output.put(preallocatedBuffer, totalCollected - toCollect, toCollect)
            }
            if (inputBuffer.hasRemaining()) {
                output.put(inputBuffer)
            }
            output.flip()

            currentPositionSec += chunkSec

            if (totalCollected >= targetBytes) {
                snapshotToAnalyze = preallocatedBuffer.copyOf(totalCollected)
                totalCollected = 0
                checkpointCount++

                if (checkpointCount >= MAX_CHECKPOINTS || isAiConfirmed || AiDetectionManager.isAiConfirmed()) {
                    allCheckpointsDone = true
                } else {
                    val schedule = if (targetSeconds == 1) DEFAULT_CHECKPOINTS_1S else DEFAULT_CHECKPOINTS_4S
                    val scheduledSec = schedule.getOrNull(checkpointCount)
                    if (scheduledSec != null) {
                        nextCheckpointStartSec = maxOf(scheduledSec, currentPositionSec)
                    } else {
                        allCheckpointsDone = true
                    }
                }
            }
        }

        snapshotToAnalyze?.let { snapshot ->
            AiDetectionManager.analyzeAsync(
                pcmBytes = snapshot,
                sampleRate = fmt.sampleRate,
                channelCount = fmt.channelCount,
            )
        }
    }
}
