package com.alananasss.kittytune.ui.game

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.fillMaxSize
import com.alananasss.kittytune.core.Strings
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Test
import java.io.File

/** Renders the arcade and each game to build/previews, and drives a few inputs, so a broken screen fails here. */
@OptIn(InternalComposeUiApi::class)
class ArcadeRenderTest {
    private fun render(name: String, game: GameId?, dark: Boolean = true, w: Int = 1000, h: Int = 760, drive: (ImageComposeScene, () -> Unit) -> Unit = { _, _ -> }) {
        val previous = Strings.appLanguage
        Strings.appLanguage = "fr"
        val scene = ImageComposeScene(width = w, height = h, density = Density(1f)) {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                ArcadeScreen(current = game, onSelect = {}, onBack = {}, onClose = {}, modifier = Modifier.fillMaxSize())
            }
        }
        var time = 0L
        val frames = {
            repeat(40) {
                time += 16_000_000L
                scene.render(time).close()
            }
        }
        try {
            frames()
            drive(scene, frames)
            val out = File("build/previews/arcade-$name.png").apply { parentFile.mkdirs() }
            scene.render(time).use { img -> img.encodeToData(EncodedImageFormat.PNG)!!.use { out.writeBytes(it.bytes) } }
        } finally {
            scene.close()
            Strings.appLanguage = previous
        }
    }

    @Test fun hub() = render("hub", null)
    @Test fun hubLight() = render("hub-light", null, dark = false, w = 700, h = 900)
    @Test fun blocks() = render("blocks", GameId.BLOCKS) { scene, frames ->
        // Pick up the middle piece of the tray and hold it over the board.
        scene.sendPointerEvent(PointerEventType.Press, Offset(500f, 680f))
        frames()
        scene.sendPointerEvent(PointerEventType.Move, Offset(470f, 380f))
        frames()
    }
    @Test fun blocksDrop() = render("blocks-drop", GameId.BLOCKS) { scene, frames ->
        // Drop each tray piece somewhere on the board: placements, clears and refills must all hold.
        for (slot in 0..2) for (target in listOf(Offset(330f, 260f), Offset(500f, 420f), Offset(620f, 560f))) {
            val from = Offset(500f + (slot - 1) * 147f, 680f)
            scene.sendPointerEvent(PointerEventType.Press, from)
            scene.sendPointerEvent(PointerEventType.Move, target)
            scene.sendPointerEvent(PointerEventType.Release, target)
            frames()
        }
    }
    @Test fun mines() = render("mines", GameId.MINES) { scene, frames ->
        scene.sendPointerEvent(PointerEventType.Press, Offset(500f, 420f))
        scene.sendPointerEvent(PointerEventType.Release, Offset(500f, 420f))
        frames()
    }
    @Test fun bricks() = render("bricks", GameId.BRICKS) { scene, frames ->
        scene.sendPointerEvent(PointerEventType.Move, Offset(380f, 300f))
        frames()
    }
    @Test fun merge() = render("merge", GameId.MERGE)
    @Test fun stack() = render("stack", GameId.STACK) { scene, frames ->
        // A few moves, a turn and a drop from the keyboard.
        for (k in listOf(Key.DirectionLeft, Key.DirectionUp, Key.Spacebar, Key.DirectionRight, Key.DirectionRight, Key.Spacebar)) {
            scene.sendKeyEvent(KeyEvent(k, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(k, KeyEventType.KeyUp))
            frames()
        }
    }
}
