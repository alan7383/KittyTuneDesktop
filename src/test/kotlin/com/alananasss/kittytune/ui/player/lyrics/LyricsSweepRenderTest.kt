package com.alananasss.kittytune.ui.player.lyrics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.data.local.AppThemeMode
import com.alananasss.kittytune.data.local.InstrumentalIndicatorStyle
import com.alananasss.kittytune.ui.theme.SoundTuneTheme
import com.alananasss.kittytune.ui.theme.getDynamicTypography
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Renders a half-sung line with the Sweep animation and the three gap indicators to build/previews. */
class LyricsSweepRenderTest {

    @Test
    fun sweepAndIndicators() {
        assumeTrue("run through headlessTest (isolated profile)", System.getenv("APPDATA")?.contains("test-profile") == true)
        val line = LyricLine(text = "Spend my days locked in a haze", startTime = 0L, endTime = 4_000L)
        val scene = ImageComposeScene(width = 800, height = 520, density = Density(1f)) {
            SoundTuneTheme(
                themeMode = AppThemeMode.DARK, dynamicColor = false, pureBlack = false,
                keyColor = 0xFFFF7A1A.toInt(), colorStyle = "Expressive",
                typography = getDynamicTypography(true, 400, 100f, 0f, 0f, 0f, 14f),
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        val style = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 46.sp)
                        LyricLineText(
                            line = line, isActive = true, positionMs = { 2_000f }, wordSync = false, fillEffect = false,
                            activeStyle = style, inactiveStyle = style, activeColor = Color.White,
                            inactiveColor = Color.Gray, unsungColor = Color.Gray, textAlign = TextAlign.Start,
                            sweep = LyricSweepSpec(Color.White, Color.White.copy(alpha = 0.45f), progress = { 0.5f }),
                        )
                        listOf(
                            InstrumentalIndicatorStyle.NOTE_WAVE,
                            InstrumentalIndicatorStyle.CIRCLE,
                            InstrumentalIndicatorStyle.DOTS,
                        ).forEach { style ->
                            InstrumentalLineIndicator(
                                style = style, startMs = 0L, endMs = 10_000L, positionProvider = { 4_000L },
                                textAlign = TextAlign.Start, accentColor = Color.White, contentColor = Color.Gray,
                            )
                        }
                    }
                }
            }
        }
        try {
            val output = File("build/previews/lyrics-sweep-indicators.png").apply { parentFile.mkdirs() }
            // Several frames: the gap indicators fade in over a few hundred milliseconds.
            for (ms in 0L..1_500L step 100L) scene.render(ms * 1_000_000L).close()
            scene.render(2_000_000_000L).use { image ->
                image.encodeToData(EncodedImageFormat.PNG)!!.use { data -> output.writeBytes(data.bytes) }
            }
        } finally {
            scene.close()
        }
    }
}
