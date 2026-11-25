package com.alananasss.kittytune.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.data.local.AppThemeMode
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.theme.SoundTuneTheme
import java.io.File
import javax.swing.SwingUtilities
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Test
import kotlin.test.assertEquals

class ChartTrackRowTest {
    private val output = File("build/previews").apply { mkdirs() }

    @Test
    fun segmentedShapeUsesOuterRadiusAtEndsAndInnerRadiusBetween() {
        val single = chartGroupItemShape(index = 0, totalCount = 1)
        assertEquals(single.topStart, single.bottomStart)

        val first = chartGroupItemShape(index = 0, totalCount = 5)
        val middle = chartGroupItemShape(index = 2, totalCount = 5)
        val last = chartGroupItemShape(index = 4, totalCount = 5)

        assertEquals(first.topStart, last.bottomStart)
        assertEquals(first.bottomStart, middle.topStart)
        assertEquals(middle.bottomStart, last.topStart)
    }

    @Test
    fun chartTrackRowHandlesLeftClickOptionsButtonAndRightClick() {
        val previousLanguage = Strings.appLanguage
        try {
            Strings.appLanguage = "en"
            val entries = listOf(
                "Flower In A Hurricane" to "Justice, Tame Impala, SZA",
                "Fille à Papa" to "Vacra, La Rvfleuze",
                "Bodycount" to "Le Crime, TK",
                "Mélo Décalé" to "Tiakola",
                "Jet Lag" to "Tiakola, Josman",
            ).mapIndexed { index, (title, artist) ->
                val track = Track(
                    id = (index + 1).toLong(),
                    title = title,
                    artworkUrl = File(output, "chart-fixtures/cover-$index.png")
                        .takeIf(File::isFile)?.absoluteFile?.toPath()?.toUri()?.toString(),
                    durationMs = 200_000L,
                    user = null,
                    playbackCount = listOf(76800000, 46000000, 17000000, 58400000, 17600000)[index],
                    publisherMetadata = Track.PublisherMetadata(artist = artist),
                )
                ChartEntry(index + 1, track, 0.0)
            }

            SwingUtilities.invokeAndWait { Thread.currentThread().contextClassLoader = javaClass.classLoader }
            var playedTrackId: Long? = null
            var optionTrackId: Long? = null
            val scene = ImageComposeScene(width = 680, height = 522, density = Density(1f)) {
                SoundTuneTheme(themeMode = AppThemeMode.DARK, dynamicColor = false, keyColor = 0xFF537BCC.toInt()) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            entries.forEachIndexed { index, entry ->
                                ChartTrackRow(
                                    track = entry.track,
                                    rank = entry.rank,
                                    currentlyPlayingTrack = entries[3].track,
                                    onClick = { playedTrackId = entry.track.id },
                                    index = index,
                                    totalCount = entries.size,
                                    isPlaying = true,
                                    onOptionClick = { optionTrackId = entry.track.id },
                                    onArtistClick = { _, _ -> },
                                )
                            }
                        }
                    }
                }
            }
            try {
                repeat(7) {
                    SwingUtilities.invokeAndWait { scene.render().close() }
                    Thread.sleep(30)
                }
                SwingUtilities.invokeAndWait {
                    scene.render().use { image ->
                        checkNotNull(image.encodeToData(EncodedImageFormat.PNG)).use { data ->
                            File(output, "chart-rows-with-options.png").writeBytes(data.bytes)
                        }
                    }

                    // Left-click main row body on row 0 (y ~ 56)
                    val rowPoint = Offset(300f, 56f)
                    scene.sendPointerEvent(PointerEventType.Press, rowPoint, buttons = PointerButtons(isPrimaryPressed = true))
                    scene.sendPointerEvent(PointerEventType.Release, rowPoint, buttons = PointerButtons(isPrimaryPressed = false))
                    assertEquals(1L, playedTrackId)

                    // Click trailing MoreVert button on row 1 (y ~ 130, x ~ 628)
                    val optionsButtonPoint = Offset(628f, 130f)
                    scene.sendPointerEvent(PointerEventType.Press, optionsButtonPoint, buttons = PointerButtons(isPrimaryPressed = true))
                    scene.sendPointerEvent(PointerEventType.Release, optionsButtonPoint, buttons = PointerButtons(isPrimaryPressed = false))
                    assertEquals(2L, optionTrackId)

                    // Secondary (right) click on row 2 body (y ~ 204)
                    val rightClickPoint = Offset(300f, 204f)
                    scene.sendPointerEvent(
                        PointerEventType.Press,
                        rightClickPoint,
                        buttons = PointerButtons(isSecondaryPressed = true),
                        button = PointerButton.Secondary,
                    )
                    scene.sendPointerEvent(
                        PointerEventType.Release,
                        rightClickPoint,
                        buttons = PointerButtons(isSecondaryPressed = false),
                        button = PointerButton.Secondary,
                    )
                    assertEquals(3L, optionTrackId)
                }
            } finally {
                SwingUtilities.invokeAndWait { scene.close() }
            }
        } finally {
            Strings.appLanguage = previousLanguage
        }
    }

    @Test
    fun fullscreenPreviewAndChartsFillWideLayoutWithoutDeadSideMargins() {
        val previousLanguage = Strings.appLanguage
        try {
            Strings.appLanguage = "en"
            val entries = (1..14).map { rank ->
                val title = listOf(
                    "Flower In A Hurricane", "Fille à Papa", "Bodycount", "Mélo Décalé",
                    "Jet Lag", "Glass Hour", "Midnight Echo", "Velvet Sky",
                    "Neon Drift", "Afterglow", "Mirage", "Cold Rain", "Pulse", "Dawn",
                )[rank - 1]
                val artist = listOf(
                    "Justice, Tame Impala", "Vacra", "Le Crime", "Tiakola",
                    "Tiakola, Josman", "Nia Haze", "M83", "Kavinsky",
                    "Gesaffelstein", "Lorn", "Moderat", "Bicep", "Overmono", "Jamie xx",
                )[rank - 1]
                val genre = listOf("Electronic", "French Rap", "Rap", "Afropop", "Hip-Hop")[rank % 5]
                ChartEntry(
                    rank = rank,
                    track = Track(
                        id = rank.toLong(),
                        title = title,
                        artworkUrl = File(output, "chart-fixtures/cover-${(rank - 1) % 6}.png")
                            .takeIf(File::isFile)?.absoluteFile?.toPath()?.toUri()?.toString(),
                        durationMs = (175 + rank * 9) * 1000L,
                        genre = genre,
                        user = null,
                        playbackCount = (80_000_000 / rank),
                        publisherMetadata = Track.PublisherMetadata(artist = artist),
                    ),
                    score = 0.0,
                )
            }
            SwingUtilities.invokeAndWait { Thread.currentThread().contextClassLoader = javaClass.classLoader }

            fun renderScene(width: Int, height: Int, fileName: String, content: @androidx.compose.runtime.Composable () -> Unit) {
                val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
                    SoundTuneTheme(themeMode = AppThemeMode.DARK, dynamicColor = false, keyColor = 0xFF537BCC.toInt()) {
                        Surface(color = MaterialTheme.colorScheme.background) { content() }
                    }
                }
                try {
                    repeat(7) {
                        SwingUtilities.invokeAndWait { scene.render().close() }
                        Thread.sleep(30)
                    }
                    SwingUtilities.invokeAndWait {
                        scene.render().use { image ->
                            checkNotNull(image.encodeToData(EncodedImageFormat.PNG)).use { data ->
                                File(output, fileName).writeBytes(data.bytes)
                            }
                        }
                    }
                } finally {
                    SwingUtilities.invokeAndWait { scene.close() }
                }
            }

            renderScene(1600, 440, "chart-preview-fullscreen.png") {
                Column(Modifier.fillMaxSize().padding(top = 16.dp)) {
                    ChartPreviewSection(
                        kind = ChartKind.COUNTRY,
                        entries = entries.take(5),
                        isLoading = false,
                        currentTrack = entries[2].track,
                        onKindChange = {},
                        onPlayFrom = {},
                        onArtistClick = { _, _ -> },
                        onOptionClick = {},
                        onSeeAll = {},
                    )
                }
            }

            renderScene(1600, 820, "charts-fullscreen.png") {
                ChartsContent(
                    kind = ChartKind.TOP,
                    genre = ChartsViewModel.chartGenres.first(),
                    country = ChartCountry.FR,
                    entries = entries,
                    isLoading = false,
                    currentTrack = entries[0].track,
                    onKindChange = {},
                    onGenreChange = {},
                    onCountryChange = {},
                    onRetry = {},
                    onPlayFrom = {},
                    onArtistClick = { _, _ -> },
                    isPlaying = true,
                    onOptionClick = {},
                )
            }
        } finally {
            Strings.appLanguage = previousLanguage
        }
    }
}
