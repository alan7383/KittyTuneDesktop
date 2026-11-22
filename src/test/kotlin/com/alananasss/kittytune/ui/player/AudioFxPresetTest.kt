package com.alananasss.kittytune.ui.player

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alananasss.kittytune.core.Application
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.local.PlayerPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFxPresetTest {
    @Test
    fun namedPresetRestoresExactEffectsSpeedPitchAndEqualizer() = runBlocking(Dispatchers.Main) {
        val prefs = PlayerPreferences()
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
        val factory = viewModelFactory { initializer { PlayerViewModel(Application()) } }
        val player = ViewModelProvider.create(owner, factory)[PlayerViewModel::class]
        val previous = AudioFxPreset(player.effectsState, player.equalizerState)
        val previousWarning = player.hasSeenEarrapeWarning()
        val name = "Late night ${System.nanoTime()}"
        val preset = AudioFxPreset(
            effects = AudioEffectsState(
                speed = 0.9f,
                isPitchEnabled = true,
                isBassBoostEnabled = true,
                bassBoostIntensity = 0.2f,
                isEarrapeEnabled = true,
                earrapeIntensity = 0.1f,
            ),
            equalizer = EqualizerState(
                isEnabled = true,
                preampDb = -2f,
                bandGainsDb = List(16) { if (it == 2) 3f else 0f },
            ),
        )

        try {
            assertTrue(player.saveAudioFxPreset("  $name  ", preset))
            assertFalse(player.saveAudioFxPreset(name.uppercase(), preset))
            val saved = prefs.getAudioFxPresets().getValue(name)
            assertEquals(preset, saved)

            player.setHasSeenEarrapeWarning(false)
            assertFalse(player.applyAudioFxPreset(saved))
            assertEquals(previous.effects, player.effectsState)

            player.setHasSeenEarrapeWarning(true)
            assertTrue(player.applyAudioFxPreset(saved))
            assertEquals(preset.effects, player.effectsState)
            assertEquals(preset.equalizer, player.equalizerState)

            player.deleteAudioFxPreset(name)
            assertFalse(prefs.getAudioFxPresets().containsKey(name))
        } finally {
            player.deleteAudioFxPreset(name)
            player.setHasSeenEarrapeWarning(previousWarning)
            owner.viewModelStore.clear()
            prefs.saveEffects(previous.effects)
            prefs.saveEqualizerState(previous.equalizer)
            MusicManager.releasePlayer()
        }
    }
}
