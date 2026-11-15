package com.alananasss.kittytune.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.audio.automix.TransitionPlan
import com.alananasss.kittytune.audio.automix.TransitionPreset
import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.data.local.AppThemeMode
import com.alananasss.kittytune.data.mix.MixPlaylistAnalyzer
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.player.mix.EditTransitionScreen
import com.alananasss.kittytune.ui.theme.SoundTuneTheme
import com.alananasss.kittytune.ui.theme.getDynamicTypography
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/** Renders the Mix Mode pieces and the transition editor offscreen to build/previews. */
class MixRenderTest {

    private fun requireTestProfile() =
        assumeTrue("run through headlessTest (isolated profile)", System.getenv("APPDATA")?.contains("test-profile") == true)

    // Local, negative ids: no stream or waveform is ever fetched for them.
    private fun track(id: Long, title: String, artist: String) = Track(
        id = id, title = title, artworkUrl = null, durationMs = 215_000L,
        user = User(0L, artist, null), source = "local_file",
    )

    @Test
    fun mixRowPiecesAndConnector() {
        requireTestProfile()
        render("mix-row-pieces", 900, 420) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MixCdjPlayButton(onClick = {})
                    MixModeToggleButton(enabled = false, onToggle = {})
                    MixModeToggleButton(enabled = true, onToggle = {})
                    MixSmartSortButton(onClick = {})
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Habits (Stay High) — Tove Lo", Modifier.weight(1f))
                    MixTrackMeta(MixBadgeUi(124, "8A", MixPlaylistAnalyzer.MixKeyFit.SAME))
                    Text("3:35", Modifier.padding(start = 12.dp))
                }
                MixTransitionConnector(plan = TransitionPlan(), onClick = {})
                MixTransitionConnector(plan = TransitionPlan().withPresetSelected(TransitionPreset.BLEND), onClick = {})
                MixTransitionConnector(plan = TransitionPlan().withPresetSelected(TransitionPreset.CUSTOM), onClick = {})
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Unanalysed track", Modifier.weight(1f))
                    MixTrackMeta(null)
                    Text("4:02", Modifier.padding(start = 12.dp))
                }
            }
        }
    }

    @Test
    fun transitionEditor() {
        requireTestProfile()
        render("mix-transition-editor", 1100, 900) {
            EditTransitionScreen(
                trackA = track(-9_100_001L, "Habits (Stay High)", "Tove Lo"),
                trackB = track(-9_100_002L, "Faded", "Alan Walker"),
                initialPlan = TransitionPlan(),
                playlistKey = "render-test",
                onSave = {},
                onDismiss = {},
            )
        }
    }

    private fun render(name: String, width: Int, height: Int, content: @Composable () -> Unit) {
        val previous = Strings.appLanguage
        Strings.appLanguage = "fr"
        try {
            val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
                SoundTuneTheme(
                    themeMode = AppThemeMode.DARK, dynamicColor = false, pureBlack = false,
                    keyColor = 0xFFFF7A1A.toInt(), colorStyle = "Expressive",
                    typography = getDynamicTypography(true, 400, 100f, 0f, 0f, 0f, 14f),
                ) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) { content() }
                }
            }
            try {
                val output = File("build/previews/$name.png").apply { parentFile.mkdirs() }
                scene.render(0L).close()
                scene.render(2_000_000_000L).use { image ->
                    image.encodeToData(EncodedImageFormat.PNG)!!.use { data -> output.writeBytes(data.bytes) }
                }
                assertEquals(width, ImageIO.read(output).width)
            } finally {
                scene.close()
            }
        } finally {
            Strings.appLanguage = previous
        }
    }
}
