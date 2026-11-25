import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.data.local.AppThemeMode
import com.alananasss.kittytune.ui.pet.PetActivity
import com.alananasss.kittytune.ui.pet.PetBarContent
import com.alananasss.kittytune.ui.pet.PetHint
import com.alananasss.kittytune.ui.pet.PetStreak
import com.alananasss.kittytune.ui.theme.SoundTuneTheme
import com.alananasss.kittytune.ui.theme.getDynamicTypography
import com.alananasss.kittytune.ui.theme.withUiScale
import java.io.File
import javax.imageio.ImageIO
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** The cat on the player bar: drawn standing on the bar's top edge, with no card or window around it. */
class PetBarRenderTest {

    private fun render(name: String, activity: PetActivity, streak: PetStreak.Status, menu: Boolean = false, hint: PetHint? = null): java.awt.image.BufferedImage {
        val previousLanguage = Strings.appLanguage
        Strings.appLanguage = "en"
        val width = 900
        val height = 420
        val barTop = 320f
        try {
            val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
                CompositionLocalProvider(LocalDensity provides LocalDensity.current.withUiScale(1f)) {
                    SoundTuneTheme(
                        themeMode = AppThemeMode.DARK, dynamicColor = false, pureBlack = false,
                        keyColor = 0xFF5B8CFF.toInt(), colorStyle = "Expressive",
                        typography = getDynamicTypography(true, 400, 100f, 0f, 0f, 0f, 14f)
                    ) {
                        Box(Modifier.fillMaxSize().background(Color(0xFF0E1116))) {
                            Box(
                                Modifier.offset(y = barTop.dp).fillMaxWidth().height(88.dp)
                                    .clip(RoundedCornerShape(24.dp)).background(Color(0xFF1B2130))
                            )
                            PetBarContent(
                                bar = Rect(0f, barTop, width.toFloat(), barTop + 88f),
                                onOpenSettings = {}, onPlayPause = {},
                                forcedActivity = activity, forcedStreak = streak, initialMenuOpen = menu, forcedHint = hint,
                            )
                        }
                    }
                }
            }
            try {
                val output = File("build/previews/pet-bar-$name.png").apply { parentFile.mkdirs() }
                scene.render().use { image ->
                    image.encodeToData(EncodedImageFormat.PNG)!!.use { data -> output.writeBytes(data.bytes) }
                }
                return ImageIO.read(output)
            } finally {
                scene.close()
            }
        } finally {
            Strings.appLanguage = previousLanguage
        }
    }

    @Test
    fun theCatStandsOnTheBar() {
        val fed = PetStreak.Status(PetStreak.Mood.FED, 12, PetStreak.DAILY_GOAL_MS)
        val image = render("dance", PetActivity.DANCE_BOB, fed)
        val background = image.getRGB(4, 4)
        // Something is drawn just above the bar, in its right-hand part: the cat's corner, away from the song's name.
        val catPixels = (600 until 880).flatMap { x -> (270 until 320).map { y -> image.getRGB(x, y) } }.count { it != background }
        assertNotEquals("The cat must be drawn above the right of the bar", 0, catPixels)
        val leftPixels = (40 until 300).flatMap { x -> (270 until 320).map { y -> image.getRGB(x, y) } }.count { it != background }
        assertEquals("Nothing over the song's name on the left", 0, leftPixels)
        render("walk", PetActivity.WALK, fed)
        render("stand", PetActivity.STAND, fed)
        render("wait-hint", PetActivity.WAIT_BUTTON, fed, hint = PetHint.PLAY)
        render("repeat-hint", PetActivity.POINT, fed, hint = PetHint.REPEAT)
        render("grumpy", PetActivity.GRUMPY, fed)
        render("sleep", PetActivity.SLEEP, fed)
        render("card", PetActivity.SAD_LOAF, PetStreak.Status(PetStreak.Mood.RESTORABLE, 9, 4 * 60_000L, missedDays = 1, restoresLeft = 2), menu = true)
        render("gone", PetActivity.LOAF, PetStreak.Status(PetStreak.Mood.GONE, 0, 0L, missedDays = 5), menu = true)
    }
}
