package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.alananasss.kittytune.data.drm.WslSidecarSetup
import com.alananasss.kittytune.data.drm.WslSidecarSetup.UninstallStep
import com.alananasss.kittytune.data.local.AppThemeMode
import com.alananasss.kittytune.ui.theme.SoundTuneTheme
import com.alananasss.kittytune.ui.theme.getDynamicTypography
import java.io.File
import javax.imageio.ImageIO
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Renders the WSL removal assistant's pages offscreen, with a made-up machine, to build/previews: nothing is
 * looked at or removed on the machine running the test.
 */
class WslUninstallRenderTest {

    @Test
    fun whatPageOffersTurningTheFeatureOff() = render("wsl-uninstall-what") {
        WhatPage(UninstallState().apply { featureRemovable = true; removeFeature = true })
    }

    @Test
    fun removePageListsTheChosenSteps() = render("wsl-uninstall-running") {
        RemovePage(UninstallState().apply {
            steps = WslSidecarSetup.uninstallSteps(removeFeature = true)
            states.addAll(listOf(StepState.DONE, StepState.DONE, StepState.RUNNING, StepState.PENDING))
            running = true
        }, onRetry = {})
    }

    @Test
    fun donePageOffersTheRestart() = render("wsl-uninstall-done") {
        DonePage(UninstallState().apply { restartNeeded = true })
    }

    private fun render(name: String, content: @Composable () -> Unit) {
        val previous = Strings.appLanguage
        Strings.appLanguage = "fr"
        try {
            val scene = ImageComposeScene(width = 640, height = 1100, density = Density(1f)) {
                SoundTuneTheme(
                    themeMode = AppThemeMode.DARK, dynamicColor = false, pureBlack = false,
                    keyColor = 0xFFFF7A1A.toInt(), colorStyle = "Expressive",
                    typography = getDynamicTypography(true, 400, 100f, 0f, 0f, 0f, 14f),
                ) {
                    // A Surface, as the assistant's dialog is: it is what gives the text its colour.
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
