package com.alananasss.kittytune.audio

import com.alananasss.kittytune.ui.player.AudioEffectsState
import java.io.File

/**
 * Manual smoke test for the audio engine: decodes the bundled rain.mp3 through the full
 * FFmpeg -> DSP -> SourceDataLine pipeline for a few seconds, exercising a couple of effects.
 * Run with: gradlew runAudioTest   (see build.gradle.kts)
 */
object AudioEngineTest {
    @JvmStatic
    fun main(args: Array<String>) {
        // extract bundled rain.mp3 to a temp file for FFmpeg
        val tmp = File.createTempFile("kittytune-rain", ".mp3")
        tmp.deleteOnExit()
        AudioEngineTest::class.java.getResourceAsStream("/raw/rain.mp3")!!.use { input ->
            tmp.outputStream().use { input.copyTo(it) }
        }

        val engine = AudioEngine()
        engine.onStateChanged = { println("state -> $it") }
        engine.onError = { it.printStackTrace() }
        engine.onCompletion = { println("completed") }

        println("Playing rain.mp3 (duration probe)...")
        engine.setMediaItem(tmp.absolutePath)
        engine.applyEffects(AudioEffectsState(isBassBoostEnabled = true, bassBoostIntensity = 0.8f))
        engine.prepare()
        engine.play()

        Thread.sleep(1500)
        println("pos=${engine.positionMs}ms dur=${engine.durationMs}ms playing=${engine.isPlaying}")

        println("Enabling 8D + reverb, speeding up to 1.3x (pitch follows)...")
        engine.applyEffects(
            AudioEffectsState(
                speed = 1.3f, isPitchEnabled = true,
                is8DEnabled = true, isReverbEnabled = true, reverbIntensity = 0.6f,
            )
        )
        Thread.sleep(2000)
        println("pos=${engine.positionMs}ms")

        println("Seeking to 5s...")
        engine.seekTo(5000)
        Thread.sleep(1500)
        println("pos=${engine.positionMs}ms")

        engine.stop()
        engine.release()
        println("done")
    }
}
