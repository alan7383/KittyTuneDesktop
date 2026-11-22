package com.alananasss.kittytune.ui.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alananasss.kittytune.core.Application
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.local.AppThemeMode
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.theme.SoundTuneTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AudioFxPresetTest {
    @Test
    fun namedPresetRestoresExactEffectsSpeedPitchAndEqualizer() = runBlocking(Dispatchers.Main) {
        val prefs = PlayerPreferences()
        val previousActivation = prefs.getAudioFxPresetActivation()
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
        val factory = viewModelFactory { initializer { PlayerViewModel(Application()) } }
        val player = ViewModelProvider.create(owner, factory)[PlayerViewModel::class]
        val previous = AudioFxPreset(player.effectsState, player.equalizerState)
        val previousWarning = player.hasSeenEarrapeWarning()
        val name = "Night mix"
        val alternateName = "Alternate mix"
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
            assertFalse(player.toggleAudioFxPreset(name))
            assertEquals(previous.effects, player.effectsState)

            player.setHasSeenEarrapeWarning(true)
            val alternatePreset = preset.copy(effects = preset.effects.copy(speed = 1.2f))
            assertTrue(player.saveAudioFxPreset(alternateName, alternatePreset))
            assertTrue(player.toggleAudioFxPreset(name))
            assertTrue(player.toggleAudioFxPreset(alternateName))
            assertEquals(alternatePreset, player.currentAudioFxPreset)
            assertEquals(previous, prefs.getAudioFxPresetActivation()?.previous)
            assertTrue(player.toggleAudioFxPreset(alternateName))
            assertEquals(previous, player.currentAudioFxPreset)
            player.deleteAudioFxPreset(alternateName)

            verifyPresetInEffectsGrid(player, name, preset)
            assertFalse(prefs.getAudioFxPresets().containsKey(name))
        } finally {
            player.deleteAudioFxPreset(name)
            player.deleteAudioFxPreset(alternateName)
            player.setHasSeenEarrapeWarning(previousWarning)
            owner.viewModelStore.clear()
            prefs.saveEffects(previous.effects)
            prefs.saveEqualizerState(previous.equalizer)
            prefs.saveAudioFxPresetActivation(previousActivation)
            MusicManager.releasePlayer()
        }
    }

    private fun verifyPresetInEffectsGrid(player: PlayerViewModel, name: String, preset: AudioFxPreset) {
        val previous = player.currentAudioFxPreset
        val scene = ImageComposeScene(width = 440, height = 780) {
            SoundTuneTheme(
                themeMode = AppThemeMode.DARK,
                dynamicColor = false,
                keyColor = 0xFF6382FF.toInt(),
                colorStyle = "Expressive",
            ) {
                Surface(Modifier.fillMaxSize()) { EffectsPanel(player) }
            }
        }
        try {
            scene.render(0L).close()
            val presetTile = scene.findLabel(name)
            val presetBounds = presetTile.boundsInRoot
            val bassBoostBounds = scene.findLabel(str("effect_bass_boost")).boundsInRoot
            assertEquals(bassBoostBounds.top, presetBounds.top, 1f)
            assertEquals(bassBoostBounds.width, presetBounds.width, 1f)
            assertEquals(bassBoostBounds.height, presetBounds.height, 1f)
            assertTrue(presetBounds.right < bassBoostBounds.left)

            assertTrue(presetTile.config[SemanticsActions.OnClick].action?.invoke() == true)
            assertEquals(preset.effects, player.effectsState)
            assertEquals(preset.equalizer, player.equalizerState)
            repeat(32) { scene.render((it + 1) * 16_000_000L).close() }
            scene.render(530_000_000L).use { image ->
                assertTrue(scene.findLabel(name).config[SemanticsProperties.Selected])
                val preview = File("build/previews/audio-fx-preset-grid.png").apply { parentFile.mkdirs() }
                image.encodeToData(EncodedImageFormat.PNG)!!.use { preview.writeBytes(it.bytes) }
            }

            assertTrue(scene.findLabel(name).config[SemanticsActions.OnClick].action?.invoke() == true)
            assertEquals(previous, player.currentAudioFxPreset)
            assertNull(player.playerPrefs.getAudioFxPresetActivation())
            scene.render(550_000_000L).close()
            assertFalse(scene.findLabel(name).config[SemanticsProperties.Selected])

            assertTrue(scene.findLabel(name).config[SemanticsActions.OnLongClick].action?.invoke() == true)
            scene.render(570_000_000L).close()
            scene.findLabel(str("audio_fx_delete_preset"))
            assertTrue(scene.findLabel(str("btn_delete")).config[SemanticsActions.OnClick].action?.invoke() == true)
            assertFalse(player.audioFxPresets.containsKey(name))
        } finally {
            scene.close()
        }
    }

    private fun ImageComposeScene.findLabel(label: String): SemanticsNode =
        semanticsOwners.firstNotNullOfOrNull { it.rootSemanticsNode.findLabel(label) }
            ?: error("Missing Audio FX label: $label")

    private fun SemanticsNode.findLabel(label: String): SemanticsNode? =
        if (config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true) this
        else children.firstNotNullOfOrNull { it.findLabel(label) }
}
