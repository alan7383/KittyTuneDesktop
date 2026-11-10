package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.data.drm.NativeCdmSetup
import com.alananasss.kittytune.data.local.AppThemeMode
import com.alananasss.kittytune.ui.theme.SoundTuneTheme
import com.alananasss.kittytune.ui.theme.getDynamicTypography
import java.io.File
import javax.imageio.ImageIO
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Renders the steps of the protected-tracks assistant offscreen, to build/previews, with a made-up
 * state: nothing is downloaded, installed or probed on the machine running the test.
 */
class DrmWizardRenderTest {

    @Test
    fun howStepInFrench() = render("drm-how-fr", "fr") {
        HowStep(windows = true)
    }

    @Test
    fun howStepInEnglish() = render("drm-how-en", "en") {
        HowStep(windows = true)
    }

    @Test
    fun setupStepWhileItRuns() = render("drm-setup-fr", "fr") {
        NativeSetupStep(
            NativeWizardState().apply {
                running = true
                states[NativeCdmSetup.Step.HOST.ordinal] = StepState.DONE
                states[NativeCdmSetup.Step.DOWNLOAD.ordinal] = StepState.DONE
                states[NativeCdmSetup.Step.EXTRACT.ordinal] = StepState.RUNNING
            },
        )
    }

    @Test
    fun setupStepWhenTheModuleIsAlreadyThere() = render("drm-setup-done-fr", "fr") {
        NativeSetupStep(
            NativeWizardState().apply {
                succeeded = true
                for (i in states.indices) states[i] = StepState.DONE
            },
        )
    }

    private fun render(name: String, language: String, content: @Composable ColumnScope.() -> Unit) {
        val previous = Strings.appLanguage
        Strings.appLanguage = language
        try {
            val scene = ImageComposeScene(width = 640, height = 1300, density = Density(1f)) {
                SoundTuneTheme(
                    themeMode = AppThemeMode.DARK, dynamicColor = false, pureBlack = false,
                    keyColor = 0xFF7A5AF8.toInt(), colorStyle = "Expressive",
                    typography = getDynamicTypography(true, 400, 100f, 0f, 0f, 0f, 14f),
                ) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(
                            Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) { content() }
                    }
                }
            }
            try {
                val output = File("build/previews/$name.png").apply { parentFile.mkdirs() }
                scene.render(500_000_000L).use { image ->
                    image.encodeToData(EncodedImageFormat.PNG)!!.use { data -> output.writeBytes(data.bytes) }
                }
                assertEquals(640, ImageIO.read(output).width)
            } finally {
                scene.close()
            }
        } finally {
            Strings.appLanguage = previous
        }
    }
}
