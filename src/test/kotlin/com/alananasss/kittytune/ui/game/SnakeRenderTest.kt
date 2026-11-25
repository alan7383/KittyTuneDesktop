package com.alananasss.kittytune.ui.game

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.getOrNull
import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.domain.Track
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(InternalComposeUiApi::class)
class SnakeRenderTest {
    @Test
    fun `keyboard starts the playlist and pause resumes the same song`() {
        val previousLanguage = Strings.appLanguage
        Strings.appLanguage = "fr"
        val previousMusic = SnakeGameLauncher.playMusic
        SnakeGameLauncher.playMusic = true // songs on: this test is about the song a bite plays
        val tracks = listOf(
            Track(-11, "Premier morceau", null, 120_000L, null, source = "local"),
            Track(-12, "Morceau suivant", null, 60_000L, null, source = "local"),
        )
        val played = mutableListOf<Triple<List<Track>, Int, Long>>()
        var playerOpened = 0
        val scene = ImageComposeScene(width = 620, height = 720) {
            MaterialTheme(colorScheme = darkColorScheme()) {
                SnakeGameScreen(
                    request = SnakeGameRequest("Playlist du soir", SnakeGameMode.PLAYLIST, tracks),
                    onPlayTrack = { queue, index, start, _ -> played += Triple(queue, index, start) },
                    onOpenPlayer = { playerOpened++ },
                    onClose = {},
                )
            }
        }
        var frameTime = 0L
        fun renderFrames() {
            repeat(14) {
                frameTime += 16_000_000L
                scene.render(frameTime).close()
            }
        }
        try {
            renderFrames()
            assertTrue(played.isEmpty())
            val preview = File("build/previews/desktop-snake.png").apply { parentFile.mkdirs() }
            scene.render(frameTime).use { image ->
                image.encodeToData(EncodedImageFormat.PNG)!!.use { data -> preview.writeBytes(data.bytes) }
            }

            assertTrue(scene.sendKeyEvent(KeyEvent(Key.DirectionRight, KeyEventType.KeyDown)))
            scene.sendKeyEvent(KeyEvent(Key.DirectionRight, KeyEventType.KeyUp))
            renderFrames()
            assertEquals(listOf(Triple(tracks, 0, 36_000L)), played)

            repeat(2) {
                assertTrue(scene.sendKeyEvent(KeyEvent(Key.Spacebar, KeyEventType.KeyDown)))
                assertTrue(scene.sendKeyEvent(KeyEvent(Key.Spacebar, KeyEventType.KeyUp)))
                renderFrames()
            }
            assertEquals(1, played.size)

            // The song card opens the player, wherever the layout puts it.
            val nowPlaying = Strings.get("game_now_playing")
            val banner = scene.semanticsOwners.firstNotNullOfOrNull { it.rootSemanticsNode.find(nowPlaying) }
                ?: error("Missing now playing card")
            val center = banner.boundsInRoot.center
            scene.sendPointerEvent(PointerEventType.Press, center)
            scene.sendPointerEvent(PointerEventType.Release, center)
            renderFrames()
            assertEquals(1, playerOpened)
        } finally {
            scene.close()
            SnakeGameLauncher.playMusic = previousMusic
            Strings.appLanguage = previousLanguage
        }
    }
}

private fun androidx.compose.ui.semantics.SemanticsNode.find(label: String): androidx.compose.ui.semantics.SemanticsNode? {
    val texts = config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text).orEmpty()
    if (texts.any { it.text == label }) return this
    return children.firstNotNullOfOrNull { it.find(label) }
}
