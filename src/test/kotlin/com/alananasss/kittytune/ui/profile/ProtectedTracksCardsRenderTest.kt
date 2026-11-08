package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.data.drm.ProtectedTracksNotice
import com.alananasss.kittytune.data.local.AppThemeMode
import com.alananasss.kittytune.ui.theme.SoundTuneTheme
import com.alananasss.kittytune.ui.theme.getDynamicTypography
import java.io.File
import javax.imageio.ImageIO
import org.jetbrains.skia.EncodedImageFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** Renders the protected tracks card and notice offscreen to build/previews, as an update would show them. */
class ProtectedTracksCardsRenderTest {

    private val keys = listOf("protected_tracks_enabled", "protected_tracks_announced", "protected_tracks_hint_shown", "drm_onboarding_done")


    /** These read and write preferences: only ever against headlessTest's throwaway profile, never a real one. */
    private fun requireTestProfile() =
        assumeTrue("run through headlessTest (isolated profile)", System.getenv("APPDATA")?.contains("test-profile") == true)

    @Before
    @After
    fun clean() {
        requireTestProfile()
        keys.forEach(Prefs::remove)
        ProtectedTracksNotice.dismiss()
    }

    @Test
    fun announcementCardOnTheHomeScreen() = render("protected-announcement", height = 420) {
        Text("Bonsoir", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        ProtectedTracksAnnouncementCard()
    }

    @Test
    fun noticeForATrackThatCouldNotBePlayed() = render("protected-notice", height = 200) {
        ProtectedTracksNotice.onProtectedTrackUnavailable()
        ProtectedTracksNoticeHost()
    }

    private fun render(name: String, height: Int, content: @Composable () -> Unit) {
        val previous = Strings.appLanguage
        Strings.appLanguage = "fr"
        try {
            val scene = ImageComposeScene(width = 900, height = height, density = Density(1f)) {
                SoundTuneTheme(
                    themeMode = AppThemeMode.DARK, dynamicColor = false, pureBlack = false,
                    keyColor = 0xFFFF7A1A.toInt(), colorStyle = "Expressive",
                    typography = getDynamicTypography(true, 400, 100f, 0f, 0f, 0f, 14f),
                ) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
                    }
                }
            }
            try {
                val output = File("build/previews/$name.png").apply { parentFile.mkdirs() }
                // Two frames: the first composes, the second lets the enter animations settle.
                scene.render(0L).close()
                scene.render(2_000_000_000L).use { image ->
                    image.encodeToData(EncodedImageFormat.PNG)!!.use { data -> output.writeBytes(data.bytes) }
                }
                assertEquals(900, ImageIO.read(output).width)
            } finally {
                scene.close()
            }
        } finally {
            Strings.appLanguage = previous
        }
    }
}
