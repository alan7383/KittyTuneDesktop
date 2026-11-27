import androidx.compose.ui.ImageComposeScene
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.alananasss.kittytune.R
import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.data.local.AppThemeMode
import com.alananasss.kittytune.ui.login.WelcomeScreen
import com.alananasss.kittytune.ui.main.HomeStatsDays
import com.alananasss.kittytune.ui.main.HomeStatsSummary
import com.alananasss.kittytune.ui.main.StatsCardContent
import com.alananasss.kittytune.ui.main.listenedLabel
import com.alananasss.kittytune.ui.theme.SoundTuneTheme
import com.alananasss.kittytune.ui.theme.getDynamicTypography
import com.alananasss.kittytune.ui.theme.withUiScale
import java.awt.Font
import java.io.File
import java.time.LocalDate
import javax.imageio.ImageIO
import org.jetbrains.skia.Color
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font as SkiaFont
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontVariation
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface
import org.jetbrains.skia.shaper.Shaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Render the real Russian first-launch screen offscreen at desktop display densities. */
class DesktopRenderTest {
    @Test
    fun theBundledFontRendersCyrillicInSkiaAndAwt() {
        val bytes = flexFontBytes()
        val codePoints = listOf(
            0x0400..0x052F, 0x1C80..0x1C8A, 0x2DE0..0x2DFF, 0xA640..0xA69F,
            0xFE2E..0xFE2F, 0x1E030..0x1E06D,
        ).flatten() + listOf(0x030F, 0x0311, 0x1D2B, 0x1D78, 0x1E08F, 0x20BD)

        val awtFont = Font.createFont(Font.TRUETYPE_FONT, bytes.inputStream())
        assertEquals("Missing AWT glyphs", emptyList<Int>(), codePoints.filterNot(awtFont::canDisplay))
        Data.makeFromBytes(bytes).use { data ->
            checkNotNull(FontMgr.default.makeFromData(data)).use { typeface ->
                val missing = codePoints.filter { typeface.getUTF32Glyph(it).toInt() == 0 }
                assertEquals("Missing Skia glyphs", emptyList<Int>(), missing)
                assertEquals(6, typeface.variationAxes?.size)
            }
        }
    }

    @Test
    fun heavyCyrillicPreservesTheOpeningsInEfAndBarredO() {
        Data.makeFromBytes(flexFontBytes()).use { data ->
            checkNotNull(FontMgr.default.makeFromData(data)).use { typeface ->
                for ((weight, roundness) in listOf(900f to 100f, 1000f to 100f, 1000f to 0f)) {
                    val variations = arrayOf(
                        FontVariation("wght", weight), FontVariation("ROND", roundness),
                        FontVariation("opsz", 14f),
                    )
                    checkNotNull(typeface.makeClone(variations)).use { heavy ->
                        SkiaFont(heavy, 44f).use { font ->
                            for ((letter, vertical) in listOf("Ф" to false, "ф" to false, "ө" to true, "ꚛ" to false)) {
                                assertTrue("$letter at weight $weight, roundness $roundness",
                                    hasTwoOpenings(font, letter, vertical))
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun russianTextPairsKernInSkia() {
        Data.makeFromBytes(flexFontBytes()).use { data ->
            checkNotNull(FontMgr.default.makeFromData(data)).use { typeface ->
                for (roundness in listOf(0f, 100f)) {
                    val variations = arrayOf(
                        FontVariation("wght", 600f), FontVariation("ROND", roundness),
                        FontVariation("opsz", 14f),
                    )
                    checkNotNull(typeface.makeClone(variations)).use { variant ->
                        SkiaFont(variant, 80f).use { font ->
                            Shaper.make().use { shaper ->
                                fun width(text: String) = shaper.shapeLine(text, font).use { it.width }
                                for (pair in listOf("тр", "тв", "св")) {
                                    val separated = width(pair.take(1)) + width(pair.takeLast(1))
                                    val adjustment = separated - width(pair)
                                    assertTrue("$pair at roundness $roundness kerned by $adjustment pixels", adjustment > 1f)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun smallTextHasRoomBetweenLetters() {
        Data.makeFromBytes(flexFontBytes()).use { data ->
            checkNotNull(FontMgr.default.makeFromData(data)).use { typeface ->
                val settings = arrayOf(
                    FontVariation("wght", 500f), FontVariation("opsz", 14f), FontVariation("ROND", 100f),
                )
                checkNotNull(typeface.makeClone(settings)).use { variant ->
                    SkiaFont(variant, 80f).use { font ->
                        Shaper.make().use { shaper ->
                            val clearances = listOf("Ск" to 4.5f, "ть" to 4.5f, "ïï" to 3.2f)
                            for ((pair, minimumGap) in clearances) {
                                shaper.shapeLine(pair, font).use { line ->
                                    val glyphs = line.glyphs
                                    val left = checkNotNull(font.getPath(glyphs[0])).use { it.computeTightBounds() }
                                    val right = checkNotNull(font.getPath(glyphs[1])).use { it.computeTightBounds() }
                                    val positions = line.positions
                                    val gap = (positions[2] + right.left) - (positions[0] + left.right)
                                    assertTrue("The letter pair '$pair' has a $gap px ink gap at 80 px", gap >= minimumGap)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun flexFontBytes() =
        checkNotNull(DesktopRenderTest::class.java.getResourceAsStream(R.font.kittytune_flex))
            .use { it.readBytes() }

    private fun hasTwoOpenings(font: SkiaFont, letter: String, vertical: Boolean): Boolean {
        val bitmap = Surface.makeRasterN32Premul(100, 80).use { surface ->
            surface.canvas.clear(Color.WHITE)
            Paint().use { paint ->
                paint.color = Color.BLACK
                paint.isAntiAlias = true
                surface.canvas.drawString(letter, 18f, 58f, font, paint)
            }
            surface.makeImageSnapshot().use { image ->
                image.encodeToData(EncodedImageFormat.PNG)!!.use { ImageIO.read(it.bytes.inputStream()) }
            }
        }
        val lines = if (vertical) bitmap.width else bitmap.height
        val size = if (vertical) bitmap.height else bitmap.width
        return (0 until lines).any { line ->
            val ink = (0 until size).filter { offset ->
                val pixel = if (vertical) bitmap.getRGB(line, offset) else bitmap.getRGB(offset, line)
                (pixel and 0xFF) < 96
            }
            ink.zipWithNext().count { (a, b) -> b - a >= 4 } >= 2
        }
    }

    @Test
    fun russianListeningStatsCardShowsDurationsWithoutDots() {
        val previousLanguage = Strings.appLanguage
        Strings.appLanguage = "ru"
        try {
            val start = LocalDate.of(2026, 11, 19)
            val days = listOf(90, 150, 55, 180, 75, 120, 110).mapIndexed { index, minutes ->
                HomeStatsDays.Day(start.plusDays(index.toLong()), minutes * 60_000L)
            }
            val stats = HomeStatsSummary(days.sumOf { it.listenedMs }, 245, 31, null, days)
            assertEquals("13 ч 0 м", listenedLabel(stats.listenedMs))
            assertEquals("42 м", listenedLabel(42 * 60_000L))
            val scene = ImageComposeScene(width = 476, height = 194) {
                SoundTuneTheme(
                    themeMode = AppThemeMode.DARK, dynamicColor = false, pureBlack = false,
                    keyColor = 0xFF829ADF.toInt(), colorStyle = "Expressive",
                    typography = getDynamicTypography(true, 400, 100f, 0f, 0f, 0f, 14f)
                ) {
                    StatsCardContent(stats, onClick = {})
                }
            }
            try {
                val output = File("build/previews/kittytune-stats-ru.png").apply { parentFile.mkdirs() }
                scene.render().use { image ->
                    image.encodeToData(EncodedImageFormat.PNG)!!.use { data -> output.writeBytes(data.bytes) }
                }
            } finally {
                scene.close()
            }
        } finally {
            Strings.appLanguage = previousLanguage
        }
    }

    @Test
    fun nativeWelcomeScreenRendersWithRussianTextAndDesktopTheme() {
        renderWelcome(1280, 800, 1f, "desktop-welcome")
    }

    @Test
    fun qhdAt125PercentRendersInNativePhysicalPixels() {
        renderWelcome(2560, 1440, 1.25f, "desktop-welcome-qhd-125")
    }

    @Test
    fun qhdAt150PercentRendersInNativePhysicalPixels() {
        renderWelcome(2560, 1440, 1.5f, "desktop-welcome-qhd-150")
    }

    private fun renderWelcome(width: Int, height: Int, monitorScale: Float, name: String) {
        val previousLanguage = Strings.appLanguage
        Strings.appLanguage = "ru"
        try {
            val scene = ImageComposeScene(width = width, height = height, density = Density(monitorScale)) {
                CompositionLocalProvider(LocalDensity provides LocalDensity.current.withUiScale(1f)) {
                    SoundTuneTheme(
                        themeMode = AppThemeMode.DARK, dynamicColor = false, pureBlack = false,
                        keyColor = 0xFFFF7A1A.toInt(), colorStyle = "Expressive",
                        typography = getDynamicTypography(true, 400, 100f, 0f, 0f, 0f, 14f)
                    ) {
                        WelcomeScreen(onLoginClick = {}, onGuestClick = {}, isGuestLoading = false)
                    }
                }
            }
            try {
                val output = File("build/previews/$name.png").apply { parentFile.mkdirs() }
                scene.render().use { image ->
                    image.encodeToData(EncodedImageFormat.PNG)!!.use { data -> output.writeBytes(data.bytes) }
                }
                val bitmap = ImageIO.read(output)
                assertEquals(width, bitmap.width)
                assertEquals(height, bitmap.height)
                assertNotEquals("The central welcome card must be visible",
                    bitmap.getRGB(20, height / 2),
                    bitmap.getRGB(width / 2 - (210 * monitorScale).toInt(), height / 2))
            } finally { scene.close() }
        } finally { Strings.appLanguage = previousLanguage }
    }
}
