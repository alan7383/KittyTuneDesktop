import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.data.local.AppThemeMode
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.home.ChartCountry
import com.alananasss.kittytune.ui.home.ChartEntry
import com.alananasss.kittytune.ui.home.ChartKind
import com.alananasss.kittytune.ui.home.ChartPreviewSection
import com.alananasss.kittytune.ui.home.ChartsContent
import com.alananasss.kittytune.ui.home.ChartsViewModel
import com.alananasss.kittytune.ui.theme.SoundTuneTheme
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ChartHeaderVisualCheck {
    private val output = File("build/previews").apply { mkdirs() }

    @Test
    fun onlySeeAllNavigatesAndBothLayoutsDisplayTheSelectedChart() {
        val previousLanguage = Strings.appLanguage
        try {
            Strings.appLanguage = "en"
            val entries = listOf(
                "Fille à Papa" to "Vacra, La Rvfleuze",
                "Flower In A Hurricane" to "Justice, Tame Impala, SZA",
                "Bodycount" to "Le Crime, TK",
                "Mélo Décalé" to "Tiakola",
                "Jet Lag" to "Tiakola, Josman",
                "Glass Hour" to "Nia Haze",
            ).mapIndexed { index, (title, artist) ->
                val track = Track(
                    id = (index + 1).toLong(),
                    title = title,
                    artworkUrl = File(output, "chart-fixtures/cover-$index.png")
                        .takeIf(File::isFile)?.absoluteFile?.toPath()?.toUri()?.toString(),
                    durationMs = 200_000L,
                    user = null,
                    playbackCount = listOf(46000000, 76800000, 17000000, 58400000, 17600000, 159000)[index],
                    publisherMetadata = Track.PublisherMetadata(artist = artist),
                )
                ChartEntry(index + 1, track, 0.0)
            }
            SwingUtilities.invokeAndWait { Thread.currentThread().contextClassLoader = javaClass.classLoader }
            var full by mutableStateOf(false)
            var kind by mutableStateOf(ChartKind.COUNTRY)
            var played: Int? = null
            val scene = ImageComposeScene(width = 680, height = 522, density = Density(1f)) {
                SoundTuneTheme(themeMode = AppThemeMode.DARK, dynamicColor = false, keyColor = 0xFF537BCC.toInt()) {
                    Surface {
                        if (full) {
                            ChartsContent(
                                kind = kind,
                                genre = ChartsViewModel.chartGenres.first(),
                                country = ChartCountry.FR,
                                entries = entries,
                                isLoading = false,
                                currentTrack = entries[1].track,
                                onKindChange = { kind = it },
                                onGenreChange = {},
                                onCountryChange = {},
                                onRetry = {},
                                onPlayFrom = { played = it },
                                onArtistClick = { _, _ -> },
                            )
                        } else {
                            Column(Modifier.fillMaxSize().padding(top = 10.dp)) {
                                ChartPreviewSection(
                                    kind = kind,
                                    entries = entries.take(5),
                                    isLoading = false,
                                    currentTrack = entries[1].track,
                                    onKindChange = { kind = it },
                                    onPlayFrom = { played = it },
                                    onArtistClick = { _, _ -> },
                                    onSeeAll = { full = true },
                                )
                            }
                        }
                    }
                }
            }
            try {
                warm(scene)
                lateinit var original: BufferedImage
                SwingUtilities.invokeAndWait {
                    original = frame(scene, "chart-home-flat.png")
                    scene.sendPointerEvent(PointerEventType.Move, Offset(610f, 34f))
                    val emptyRightHovered = frame(scene)
                    assertContentEquals(
                        original.getRGB(20, 10, 400, 45, null, 0, 400),
                        emptyRightHovered.getRGB(20, 10, 400, 45, null, 0, 400),
                    )
                    click(scene, 610f, 34f)
                    click(scene, 320f, 34f)
                    assertEquals(false, full)
                    click(scene, 340f, 92f)
                    assertEquals(ChartKind.TOP, kind)
                    scene.render().close()
                    click(scene, 518f, 152f)
                    assertEquals(0, played)
                    scene.sendPointerEvent(PointerEventType.Move, Offset(50f, 34f))
                }
                warm(scene)
                SwingUtilities.invokeAndWait {
                    val headerHovered = frame(scene, "chart-home-flat.png")
                    assertEquals(
                        false,
                        original.getRGB(20, 10, 400, 45, null, 0, 400).contentEquals(
                            headerHovered.getRGB(20, 10, 400, 45, null, 0, 400),
                        ),
                    )
                    click(scene, 50f, 34f)
                    assertEquals(true, full)
                    assertEquals(ChartKind.TOP, kind)
                }
                warm(scene)
                SwingUtilities.invokeAndWait { frame(scene, "chart-full-flat.png") }
            } finally {
                SwingUtilities.invokeAndWait { scene.close() }
            }
        } finally {
            Strings.appLanguage = previousLanguage
        }
    }

    private fun warm(scene: ImageComposeScene) {
        repeat(7) {
            SwingUtilities.invokeAndWait { scene.render().close() }
            Thread.sleep(45)
        }
    }

    private fun click(scene: ImageComposeScene, x: Float, y: Float) {
        val point = Offset(x, y)
        scene.sendPointerEvent(PointerEventType.Press, point)
        scene.sendPointerEvent(PointerEventType.Release, point)
    }

    private fun frame(scene: ImageComposeScene, name: String? = null): BufferedImage =
        scene.render().use { image ->
            checkNotNull(image.encodeToData(EncodedImageFormat.PNG)).use { data ->
                if (name != null) File(output, name).writeBytes(data.bytes)
                ImageIO.read(data.bytes.inputStream())
            }
        }
}
