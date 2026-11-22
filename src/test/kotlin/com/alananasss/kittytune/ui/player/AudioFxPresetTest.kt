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
import com.alananasss.kittytune.core.Prefs
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AudioFxPresetTest {

    private val preset = AudioFxPreset(
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

    private fun <T> withPlayer(block: (PlayerViewModel, PlayerPreferences, AudioFxPreset) -> T): T = runBlocking(Dispatchers.Main) {
        val prefs = PlayerPreferences()
        val snapshot = Prefs.snapshot()
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
        val factory = viewModelFactory { initializer { PlayerViewModel(Application()) } }
        val player = ViewModelProvider.create(owner, factory)[PlayerViewModel::class]
        val previous = AudioFxPreset(player.effectsState, player.equalizerState)
        try {
            player.audioFxPresets.keys.toList().forEach(player::deleteAudioFxPreset)
            block(player, prefs, previous)
        } finally {
            owner.viewModelStore.clear()
            Prefs.restore(snapshot)
            MusicManager.releasePlayer()
        }
    }

    @Test
    fun presetsTurnOnAndOffAndRestoreWhatPlayedBefore() = withPlayer { player, prefs, previous ->
        val name = "Night mix"
        val alternateName = "Alternate mix"
        assertTrue(player.saveAudioFxPreset("  $name  ", preset))
        assertFalse(player.saveAudioFxPreset(name.uppercase(), preset))
        assertEquals(preset.portable(), prefs.getAudioFxPresets().getValue(name))

        // The loud-volume effect needs its warning first, and nothing changes until then.
        player.setHasSeenEarrapeWarning(false)
        assertFalse(player.toggleAudioFxPreset(name))
        assertEquals(previous.effects, player.effectsState)

        player.setHasSeenEarrapeWarning(true)
        val alternatePreset = preset.copy(effects = preset.effects.copy(speed = 1.2f))
        assertTrue(player.saveAudioFxPreset(alternateName, alternatePreset))
        assertTrue(player.toggleAudioFxPreset(name))
        assertEquals(name, player.activeAudioFxPresetName)
        assertTrue(player.toggleAudioFxPreset(alternateName))
        assertEquals(alternatePreset, player.currentAudioFxPreset)
        assertEquals(previous, prefs.getAudioFxPresetActivation()?.previous)
        // Switching between presets still goes back to what played before the first one.
        assertTrue(player.toggleAudioFxPreset(alternateName))
        assertEquals(previous, player.currentAudioFxPreset)
        assertNull(prefs.getAudioFxPresetActivation())
        assertNull(player.activeAudioFxPresetName)
    }

    @Test
    fun savingTheCurrentSoundMakesThatPresetActiveAndOffMeansNoEffects() = withPlayer { player, _, _ ->
        player.setHasSeenEarrapeWarning(true)
        player.resetAllEffects()
        player.toggleReverb()
        player.setCustomSpeed(1.5f)
        assertTrue(player.saveAudioFxPreset("Hall"))
        assertTrue(player.isAudioFxPresetActive("Hall"))
        assertEquals("Hall", player.activeAudioFxPresetName)

        assertTrue(player.toggleAudioFxPreset("Hall"))
        assertFalse(player.effectsState.isReverbEnabled)
        assertEquals(1f, player.effectsState.speed)
        assertFalse(player.isAudioFxPresetActive("Hall"))

        assertTrue(player.toggleAudioFxPreset("Hall"))
        assertTrue(player.effectsState.isReverbEnabled)
        assertEquals(1.5f, player.effectsState.speed)

        // Changing anything means the preset no longer plays as saved.
        player.toggleBassBoost()
        assertNull(player.activeAudioFxPresetName)
    }

    @Test
    fun presetsAreRenamedInPlaceUpdatedAndValidated() = withPlayer { player, prefs, _ ->
        player.setHasSeenEarrapeWarning(true)
        assertTrue(player.saveAudioFxPreset("First", preset))
        assertTrue(player.saveAudioFxPreset("Second", preset.copy(effects = preset.effects.copy(speed = 1.5f))))
        assertTrue(player.saveAudioFxPreset("Third", AudioFxPreset(AudioEffectsState(), EqualizerState())))

        assertEquals(AudioFxPresetNameError.EMPTY, player.audioFxPresetNameError("   "))
        assertEquals(AudioFxPresetNameError.TAKEN, player.audioFxPresetNameError("second"))
        assertEquals(AudioFxPresetNameError.TOO_LONG, player.audioFxPresetNameError("x".repeat(AudioFxPreset.MAX_NAME_LENGTH + 1)))
        assertNull(player.audioFxPresetNameError("SECOND", except = "Second"))

        assertTrue(player.toggleAudioFxPreset("Second"))
        assertFalse(player.renameAudioFxPreset("Second", "first"))
        assertTrue(player.renameAudioFxPreset("Second", "Late night"))
        assertEquals(listOf("First", "Late night", "Third"), prefs.getAudioFxPresets().keys.toList())
        assertEquals("Late night", player.activeAudioFxPresetName)
        assertEquals("Late night", prefs.getAudioFxPresetActivation()?.name)

        player.toggleReverb()
        assertNull(player.activeAudioFxPresetName)
        assertTrue(player.updateAudioFxPreset("Late night"))
        assertTrue(prefs.getAudioFxPresets().getValue("Late night").effects.isReverbEnabled)
        assertEquals("Late night", player.activeAudioFxPresetName)

        player.deleteAudioFxPreset("Late night")
        assertFalse(prefs.getAudioFxPresets().containsKey("Late night"))
        // Deleting a preset leaves the sound as it is.
        assertTrue(player.effectsState.isReverbEnabled)
    }

    @Test
    fun takingAnEffectOffTheGridKeepsItPlaying() = withPlayer { player, prefs, _ ->
        player.resetAllEffects()
        player.updatePinnedAudioFx(listOf("reverb", "bass_boost"))
        player.toggleReverb()
        player.updatePinnedAudioFx(listOf("bass_boost"))
        assertTrue(player.effectsState.isReverbEnabled)
        assertEquals(listOf("bass_boost"), prefs.getPinnedAudioFx())
        player.togglePinAudioFx("reverb")
        assertEquals(listOf("bass_boost", "reverb"), prefs.getPinnedAudioFx())
    }

    @Test
    fun presetsFromOlderOrDamagedSavesStillLoad() {
        val previous = Prefs.snapshot()
        try {
            val prefs = PlayerPreferences()
            Prefs.putString(
                "audio_fx_presets",
                """
                {
                  "Old": {"effects": {"speed": 1.1, "isReverbEnabled": true}, "equalizer": {"isEnabled": true, "bandGainsDb": [4.0, 2.0]}},
                  "Broken numbers": {"effects": {"speed": "NaN", "normalizationLevel": "SOMETHING_NEW", "isChorusEnabled": true}},
                  "  ": {"effects": {}},
                  "old": {"effects": {"isMonoEnabled": true}},
                  "Not a preset": 42
                }
                """.trimIndent(),
            )
            val presets = prefs.getAudioFxPresets()
            assertEquals(listOf("Old", "Broken numbers"), presets.keys.toList())

            val old = presets.getValue("Old")
            assertEquals(1.1f, old.effects.speed)
            assertTrue(old.effects.isReverbEnabled)
            assertEquals("rain", old.effects.ambientType)
            assertEquals(16, old.equalizer.bandGainsDb.size)
            assertEquals(listOf(4f, 2f) + List(14) { 0f }, old.equalizer.bandGainsDb)
            assertEquals(1, old.activeEffectCount() - 1)

            val broken = presets.getValue("Broken numbers")
            assertEquals(1f, broken.effects.speed)
            assertEquals(NormalizationLevel.NORMAL, broken.effects.normalizationLevel)
            assertTrue(broken.effects.isChorusEnabled)
            assertNotNull(broken.equalizer)
        } finally {
            Prefs.restore(previous)
        }
    }

    @Test
    fun legacyGridDropsSavedPresetsAndRemembersAnEmptyGrid() {
        val previous = Prefs.snapshot()
        try {
            val prefs = PlayerPreferences()
            val empty = AudioFxPreset(AudioEffectsState(), EqualizerState())
            prefs.saveAudioFxPresets(linkedMapOf("Rain" to empty, "Night mix" to empty))
            Prefs.remove("pinned_audio_fx_list_v2")
            Prefs.putString("pinned_audio_fx_list_v1", """["reverb","preset:Rain","bass_boost"]""")

            assertEquals(listOf("reverb", "bass_boost"), prefs.getPinnedAudioFx())

            prefs.setPinnedAudioFx(emptyList())
            assertTrue(prefs.getPinnedAudioFx().isEmpty())
            assertEquals(setOf("Rain", "Night mix"), prefs.getAudioFxPresets().keys)
        } finally {
            Prefs.restore(previous)
        }
    }

    @Test
    fun panelShowsPresetCardsAndTheExplorerTurnsEffectsOn() = withPlayer { player, _, _ ->
        player.setHasSeenEarrapeWarning(true)
        player.resetAllEffects()
        assertTrue(player.saveAudioFxPreset("Night mix", preset))
        assertTrue(player.saveAudioFxPreset("Lo-fi", AudioFxPreset(AudioEffectsState(isVinylLoFiEnabled = true), EqualizerState())))
        val scene = ImageComposeScene(width = 460, height = 1500) {
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
            assertTrue(scene.findLabel("Night mix").config[SemanticsActions.OnClick].action?.invoke() == true)
            assertEquals(preset.portable(), player.currentAudioFxPreset.portable())
            repeat(40) { scene.render((it + 1) * 16_000_000L).close() }
            scene.render(700_000_000L).use { image ->
                assertTrue(scene.findLabel("Night mix").config[SemanticsProperties.Selected])
                image.save("audio-fx-panel.png")
            }

            assertTrue(scene.findLabel("Night mix").config[SemanticsActions.OnLongClick].action?.invoke() == true)
            scene.render(720_000_000L).close()
            scene.findLabel(str("audio_fx_rename"))
            scene.findLabel(str("audio_fx_update_preset"))
            scene.render(1_000_000_000L).use { it.save("audio-fx-preset-menu.png") }
            scene.close()
        } catch (t: Throwable) {
            scene.close()
            throw t
        }

        val explorer = ImageComposeScene(width = 460, height = 2600) {
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
            explorer.render(0L).close()
            assertTrue(explorer.findLabel(str("btn_explore_all_effects")).config[SemanticsActions.OnClick].action?.invoke() == true)
            explorer.render(16_000_000L).close()
            val chorus = explorer.findLabel(str("effect_chorus"))
            assertFalse(player.effectsState.isChorusEnabled)
            assertTrue(chorus.config[SemanticsActions.OnClick].action?.invoke() == true)
            assertTrue(player.effectsState.isChorusEnabled)
            repeat(40) { explorer.render((it + 2) * 16_000_000L).close() }
            explorer.render(1_000_000_000L).use { it.save("audio-fx-explorer.png") }
        } finally {
            explorer.close()
        }
    }

    private fun org.jetbrains.skia.Image.save(name: String) {
        val preview = File("build/previews/$name").apply { parentFile.mkdirs() }
        encodeToData(EncodedImageFormat.PNG)!!.use { preview.writeBytes(it.bytes) }
    }

    private fun ImageComposeScene.findLabel(label: String): SemanticsNode =
        findNode { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true }
            ?: error("Missing Audio FX label: $label")

    private fun ImageComposeScene.findNode(matches: (SemanticsNode) -> Boolean): SemanticsNode? =
        semanticsOwners.firstNotNullOfOrNull { it.rootSemanticsNode.findNode(matches) }

    private fun SemanticsNode.findNode(matches: (SemanticsNode) -> Boolean): SemanticsNode? =
        if (matches(this)) this else children.firstNotNullOfOrNull { it.findNode(matches) }
}
