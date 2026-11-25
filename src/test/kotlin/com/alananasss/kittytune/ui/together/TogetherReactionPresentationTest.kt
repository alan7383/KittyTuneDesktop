package com.alananasss.kittytune.ui.together

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alananasss.kittytune.core.Application
import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.local.FullPlayerBgStyle
import com.alananasss.kittytune.data.local.FullPlayerInfoAlign
import com.alananasss.kittytune.data.local.FullPlayerLayout
import com.alananasss.kittytune.data.together.Together
import com.alananasss.kittytune.data.together.TogetherMessage
import com.alananasss.kittytune.data.together.TogetherReaction
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.player.FullPlayerScreen
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.player.fullPlayerReactionsAtEnd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TogetherReactionPresentationTest {
    @Test
    fun mirrorsTheFlightWhenTheLyricsMoveAndUsesTheCreditInCoverOnlyLayouts() {
        assertTrue(fullPlayerReactionsAtEnd(FullPlayerLayout.LYRICS_LEFT, true, FullPlayerInfoAlign.END))
        assertFalse(fullPlayerReactionsAtEnd(FullPlayerLayout.LYRICS_RIGHT, true, FullPlayerInfoAlign.START))
        assertTrue(fullPlayerReactionsAtEnd(FullPlayerLayout.LYRICS_RIGHT, false, FullPlayerInfoAlign.START))
        assertFalse(fullPlayerReactionsAtEnd(FullPlayerLayout.COVER_AND_LINE, false, FullPlayerInfoAlign.END))
        assertTrue(fullPlayerReactionsAtEnd(FullPlayerLayout.LYRICS_CENTRED, false, null))
    }

    @Test
    fun loadsEveryBundledReactionImage() {
        for (reaction in TogetherReaction.entries) {
            val path = "/together/reactions/${reaction.wireId}.png"
            val resource = checkNotNull(javaClass.getResourceAsStream(path)) { "Missing reaction image $path" }
            val image = resource.use { ImageIO.read(it) }
            assertTrue(image.width >= 40 && image.colorModel.hasAlpha(), "Invalid reaction image $path")
        }
    }

    @Test
    fun placesIncomingReactionsInTheFullscreenAndWindowedPlayer() = runBlocking(Dispatchers.Main) {
        val room = "K7M9P3Q4"
        val width = 960
        val height = 600
        val firstLine = "Everything comes back around"
        val panelWidth = mutableStateOf(240)
        val panelColor = 0xFF25303D.toInt()
        val previousPrefs = Prefs.snapshot()
        val viewModels = ViewModelStore()
        try {
            Prefs.restore(emptyMap())
            Together.resetForTesting()
            val factory = viewModelFactory { initializer { PlayerViewModel(Application()) } }
            val player = ViewModelProvider.create(viewModels, factory)[PlayerViewModel::class]
            player.currentTrack = Track(
                id = -1L, title = "Afterglow", artworkUrl = "", durationMs = 180_000L, user = null, source = "local",
            )
            player.duration = 180_000L
            player.rawPlainLyrics = "$firstLine\nWe get another turn"
            player.isLyricsFullScreen = true
            player.updateFullPlayerBgStyle(FullPlayerBgStyle.PURE_BLACK)
            player.updateFullPlayerLayout(FullPlayerLayout.LYRICS_LEFT)
            player.updateFullPlayerScreensaverEnabled(false)

            Together.resetForTesting(activeCode = room)
            val reaction = TogetherMessage.Reaction(
                "guest", System.currentTimeMillis(), TogetherReaction.HEART.wireId, byName = "Maya",
            )

            val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    if (player.isLyricsFullScreen) {
                        FullPlayerScreen(player, onExitFullScreen = { player.isLyricsFullScreen = false })
                    } else {
                        Row(Modifier.fillMaxSize().background(Color(0xFF241923))) {
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                WindowedTogetherReactionOverlay(Modifier.matchParentSize())
                            }
                            Box(Modifier.width(panelWidth.value.dp).fillMaxHeight().background(Color(panelColor)))
                        }
                    }
                }
            }
            try {
                scene.advanceFrames(0L)
                Together.handle(room, reaction)
                scene.advanceFrames(100_000_000L)
                val early = scene.frame(205_000_000L)
                val initial = early.reactionPixels()
                assertTrue(initial.size > 200, "An incoming reaction should be visible in the full player")
                assertTrue(initial.all { (x, y) -> x >= width - 104 && y > height - 120 })
                val initialBottom = initial.maxOf { it.second }
                assertTrue(early.hasNicknameBelow(initial), "The full player should show the nickname underneath the emoji")

                val risen = scene.frame(520_000_000L)
                assertTrue(risen.reactionPixels().maxOf { it.second } < initialBottom - 60, "The emoji should rise")
                assertTrue(scene.textCenterX(firstLine) < width / 2, "The lyrics should be visible on the left")
                val preview = File("build/previews/together-lyrics-left.png").apply { parentFile.mkdirs() }
                ImageIO.write(risen, "png", preview)

                player.updateFullPlayerLayout(FullPlayerLayout.LYRICS_RIGHT)
                scene.advanceFrames(550_000_000L)
                val mirrored = scene.frame(740_000_000L)
                val moved = mirrored.reactionPixels()
                assertTrue(moved.isNotEmpty() && moved.all { it.first < 104 }, "The reaction should move opposite the lyrics")
                assertTrue(scene.textCenterX(firstLine) > width / 2, "The lyrics should be visible on the right")
                ImageIO.write(mirrored, "png", File(preview.parentFile, "together-lyrics-right.png"))

                player.isLyricsFullScreen = false
                scene.advanceFrames(800_000_000L)
                Together.handle(room, reaction)
                scene.advanceFrames(900_000_000L)
                val windowed = scene.frame(1_010_000_000L)
                val contentEnd = width - panelWidth.value
                val windowedReaction = windowed.reactionPixels()
                assertTrue(windowedReaction.isNotEmpty() && windowedReaction.all { (x, y) ->
                    x in (contentEnd - 116) until contentEnd && y > height - 120
                }, "The reaction should start at the bottom right of the main content")
                assertTrue(windowed.hasNicknameBelow(windowedReaction), "The windowed player should show the nickname underneath the emoji")

                panelWidth.value += 80
                val resized = scene.frame(1_280_000_000L)
                val resizedContentEnd = width - panelWidth.value
                val resizedReaction = resized.reactionPixels()
                assertTrue(resizedReaction.isNotEmpty() && resizedReaction.all { (x, _) -> x < resizedContentEnd })
                assertTrue((resizedContentEnd until width).all { x ->
                    (0 until height).all { y -> resized.getRGB(x, y) == panelColor }
                }, "The reaction should stay outside the right panel as it resizes")
                assertTrue(scene.frame(2_000_000_000L).reactionPixels().isEmpty(), "The reaction should fade out")
            } finally {
                scene.close()
            }
        } finally {
            viewModels.clear()
            Together.resetForTesting()
            Prefs.restore(previousPrefs)
            MusicManager.releasePlayer()
        }
    }

    @Test
    fun showsRepeatedReactionsTogetherWithoutAVisualBacklog() = runBlocking(Dispatchers.Main) {
        val room = "K7M9P3Q4"
        val width = 360
        val previousPrefs = Prefs.snapshot()
        Together.resetForTesting(activeCode = room)
        val scene = ImageComposeScene(width = width, height = 400, density = Density(1f)) {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(Modifier.fillMaxSize().background(Color(0xFF241923))) {
                    WindowedTogetherReactionOverlay(Modifier.matchParentSize())
                }
            }
        }
        try {
            val reaction = TogetherMessage.Reaction("guest", System.currentTimeMillis(), TogetherReaction.HEART.wireId)
            scene.advanceFrames(0L)
            repeat(8) { index ->
                Together.handle(room, reaction)
                scene.render(100_000_000L + index * 24_000_000L).close()
            }
            val burst = scene.frame(390_000_000L)
            val visible = burst.reactionPixels()
            assertTrue(visible.size > 2_000, "Several repeated reactions should be visible during the burst")
            assertTrue(visible.maxOf { it.first } - visible.minOf { it.first } > 60, "The burst should spread out")
            assertTrue(visible.maxOf { it.second } - visible.minOf { it.second } > 75, "The burst should rise at varied speeds")
            assertTrue(visible.all { (x, _) -> x in (width - 116) until width })

            val preview = File("build/previews/together-reaction-burst.png").apply { parentFile.mkdirs() }
            ImageIO.write(burst, "png", preview)
            assertTrue(scene.frame(1_600_000_000L).reactionPixels().isEmpty(), "An old burst should leave no replay")
        } finally {
            scene.close()
            Together.resetForTesting()
            Prefs.restore(previousPrefs)
        }
    }

    private fun ImageComposeScene.advanceFrames(nanos: Long) {
        repeat(6) { render(nanos + it * 16_000_000L).close() }
    }

    private fun ImageComposeScene.textCenterX(text: String): Float {
        val node = semanticsOwners.firstNotNullOfOrNull { it.rootSemanticsNode.findText(text) }
        return checkNotNull(node) { "Missing lyrics line: $text" }.boundsInRoot.center.x
    }

    private fun SemanticsNode.findText(text: String): SemanticsNode? =
        if (config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true) this
        else children.firstNotNullOfOrNull { it.findText(text) }

    private fun ImageComposeScene.frame(nanos: Long): BufferedImage = render(nanos).use { image ->
        image.encodeToData(EncodedImageFormat.PNG)!!.use { data -> ImageIO.read(data.bytes.inputStream()) }
    }

    private fun BufferedImage.hasNicknameBelow(emojiPixels: List<Pair<Int, Int>>): Boolean {
        val columns = emojiPixels.minOf { it.first }..emojiPixels.maxOf { it.first }
        val bottom = emojiPixels.maxOf { it.second }
        return (bottom + 1 until height).any { y ->
            columns.any { x ->
                val rgb = getRGB(x, y)
                (rgb shr 16 and 0xff) >= 230 && (rgb shr 8 and 0xff) >= 230 && (rgb and 0xff) >= 230
            }
        }
    }

    private fun BufferedImage.reactionPixels(): List<Pair<Int, Int>> = buildList {
        for (y in 0 until height) for (x in 0 until width) {
            val color = getRGB(x, y)
            val red = color shr 16 and 0xff
            val green = color shr 8 and 0xff
            if (red > 160 && red - green > 70) add(x to y)
        }
    }
}
