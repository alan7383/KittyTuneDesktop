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
import com.alananasss.kittytune.data.drm.ProtectedBackend
import com.alananasss.kittytune.data.drm.WslSidecarSetup
import com.alananasss.kittytune.data.local.AppThemeMode
import com.alananasss.kittytune.ui.theme.SoundTuneTheme
import com.alananasss.kittytune.ui.theme.getDynamicTypography
import java.io.File
import javax.imageio.ImageIO
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Renders the WSL steps of the protected-tracks assistant offscreen, to build/previews, with a made-up
 * machine: nothing is detected, enabled or installed on the machine running the test.
 */
class DrmWizardRenderTest {

    @Test
    fun methodChoiceWithTheAdvancedWslChoiceOpen() = render("drm-choose-fr", "fr") {
        // Version 2 opens the advanced options, so both versions show.
        ChooseStep(choice = ProtectedBackend.WSL, onChoose = {}, version = 2, onVersion = {}, onSkip = {})
    }

    @Test
    fun methodChoiceInEnglish() = render("drm-choose-en", "en") {
        ChooseStep(choice = ProtectedBackend.WSL, onChoose = {}, version = 1, onVersion = {}, onSkip = {})
    }

    @Test
    fun enableStepWhenTheWsl1ComponentIsOff() = render("drm-enable-fr", "fr") {
        WslCheckStep(
            WslWizardState().apply {
                detection = WslSidecarSetup.Detection(
                    WslSidecarSetup.State.FEATURE_OFF, wsl1Available = false, wsl2Available = true,
                )
            },
            showVersionChoice = true,
        )
    }

    @Test
    fun setupStepWhileItRuns() = render("drm-setup-fr", "fr") {
        WslSetupStep(
            WslWizardState().apply {
                running = true
                states[WslSidecarSetup.Step.DOWNLOAD.ordinal] = StepState.DONE
                states[WslSidecarSetup.Step.IMPORT.ordinal] = StepState.DONE
                states[WslSidecarSetup.Step.CDM.ordinal] = StepState.RUNNING
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
