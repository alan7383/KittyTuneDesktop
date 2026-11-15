package com.alananasss.kittytune.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.audio.SourceAudioFormat
import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.data.local.AppThemeMode
import com.alananasss.kittytune.ui.theme.SoundTuneTheme
import com.alananasss.kittytune.ui.theme.getDynamicTypography
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class AudioPathRenderTest {

    private val opus = SourceAudioFormat("OPUS", 48_000, null, 160_000L, 2)

    @Test
    fun `badge names codec bitrate and rate`() {
        assertEquals("OPUS • 160 kbps • 48 kHz", opus.badgeText())
        assertEquals("FLAC • 44.1 kHz", SourceAudioFormat("FLAC", 44_100, 24, 0L, 2).badgeText())
    }

    @Test
    fun `ffmpeg codec names read like the android badge`() {
        assertEquals("MP3", SourceAudioFormat.codecLabel("mp3float"))
        assertEquals("AAC", SourceAudioFormat.codecLabel("aac"))
        assertEquals("PCM", SourceAudioFormat.codecLabel("pcm_s16le"))
        assertEquals("OPUS", SourceAudioFormat.codecLabel("opus"))
    }

    @Test
    fun `a 48 kHz source is resampled to the 44 1 kHz bus, a 44 1 kHz one is direct`() {
        fun snap(rate: Int) = AudioPathSnapshot(opus.copy(sampleRateHz = rate), 44_100, false, 16, false, null, null, emptyList())
        assertEquals(AudioPathStatus.RESAMPLED, snap(48_000).status)
        assertEquals(AudioPathStatus.DIRECT, snap(44_100).status)
    }

    @Test
    fun diagnosticsSheetContent() {
        assumeTrue("run through headlessTest (isolated profile)", System.getenv("APPDATA")?.contains("test-profile") == true)
        val snapshot = AudioPathSnapshot(
            format = opus, busRateHz = 44_100, eqEnabled = true, eqBands = 16,
            normalizeEnabled = true, normalizeTargetLufs = "−14 LUFS", outputName = null,
            stages = listOf("AI-detection tap" to false, "Live-beat tap" to true, "Equalizer" to true, "Peak limiter" to true),
        )
        val previous = Strings.appLanguage
        Strings.appLanguage = "fr"
        val scene = ImageComposeScene(width = 560, height = 1000, density = Density(1f)) {
            SoundTuneTheme(
                themeMode = AppThemeMode.DARK, dynamicColor = false, pureBlack = false,
                keyColor = 0xFFFF7A1A.toInt(), colorStyle = "Expressive",
                typography = getDynamicTypography(true, 400, 100f, 0f, 0f, 0f, 14f),
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Box(Modifier.width(560.dp).padding(top = 8.dp)) { AudioPathDiagnosticContent(snapshot) }
                }
            }
        }
        try {
            val output = File("build/previews/audio-path-diagnostics.png").apply { parentFile.mkdirs() }
            scene.render(0L).close()
            scene.render(2_000_000_000L).use { image ->
                image.encodeToData(EncodedImageFormat.PNG)!!.use { data -> output.writeBytes(data.bytes) }
            }
        } finally {
            scene.close()
            Strings.appLanguage = previous
        }
    }
}
