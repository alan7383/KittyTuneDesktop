package com.alananasss.kittytune.ui.profile

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.ui.common.pressScale

/**
 * One row of chips to choose the order of a list by (round 2 of the tester's list, item 8). Scrolls sideways when
 * the window is too narrow for all of them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> SortChips(
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    sidePadding: androidx.compose.ui.unit.Dp = 24.dp,
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = sidePadding, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(labelOf(option), maxLines = 1) },
            )
        }
    }
}

/**
 * Releases of an artist laid out as covers: all of their albums, or singles, or whatever [title] says, in the order
 * the chips ask for (round 2 of the tester's list, item 8.1). A row of five covers on the page said little about a
 * record count of thirty; this is all of them at once.
 *
 * @param hasMore a catalogue pages its discography: more is asked for at the foot of the grid.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ReleaseGridScreen(
    title: String,
    releases: List<Playlist>,
    initialSort: ArtistOrder.ReleaseSort,
    onBack: () -> Unit,
    onOpen: (Playlist) -> Unit,
    hasMore: Boolean = false,
    onLoadMore: () -> Unit = {},
) {
    var sort by remember { mutableStateOf(initialSort) }
    val ordered = remember(releases, sort) { ArtistOrder.sortReleases(sort, releases) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = str("btn_back"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 168.dp),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "sort") {
                SortChips(
                    options = ArtistOrder.ReleaseSort.entries,
                    selected = sort,
                    labelOf = { str(it.labelKey) },
                    onSelect = { sort = it },
                    sidePadding = 0.dp,
                )
            }
            items(ordered, key = { it.id }) { release -> ReleaseCard(release) { onOpen(release) } }
            if (hasMore) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "more") {
                    OutlinedButton(
                        onClick = onLoadMore,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text(str("btn_load_more")) }
                }
            }
        }
    }
}

/** A release's cover with its name, and under it what it is and when it came out. */
@Composable
private fun ReleaseCard(release: Playlist, onClick: () -> Unit) {
    ReleaseTile(release.title.orEmpty(), release.fullResArtwork, releaseDetails(release), onClick)
}

@Composable
private fun ReleaseTile(title: String, artworkUrl: String?, details: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background by animateColorAsState(
        if (hovered) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
        label = "releaseCard",
    )
    Column(
        Modifier
            .pressScale(interaction, pressedScale = 0.98f)
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClick = onClick)
            .padding(10.dp)
    ) {
        AsyncImage(
            model = artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.height(10.dp))
        // Two lines reserved, so a row of cards is one height whether or not a name wraps.
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (details.isNotBlank()) {
            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** "Album · 2025 · 12 tracks": the kind, the year and the length, whichever of them are known. */
@Composable
private fun releaseDetails(release: Playlist): String {
    val kind = when (release.setType?.lowercase() ?: release.playlistType?.lowercase()) {
        "album" -> str("release_kind_album")
        "ep" -> str("release_kind_ep")
        "single" -> str("release_kind_single")
        "compilation" -> str("release_kind_compilation")
        else -> if (release.isRealAlbum) str("release_kind_album") else null
    }
    val year = ReleaseDate.parse(release.releaseDate ?: release.createdAt)?.year?.toString()
    val count = release.trackCount?.takeIf { it > 0 }?.let { str("playlist_num_tracks", it) }
    return listOfNotNull(kind, year, count).joinToString(" · ")
}

/**
 * Everything an artist has put out, songs and records together, newest first or oldest first (round 3 of the tester list,
 * item 8.1). The page "new releases" opened only the albums, which left out the singles that had never been put on a record.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun AllReleasesScreen(
    title: String,
    releases: List<ArtistRelease>,
    onBack: () -> Unit,
    onOpen: (ArtistRelease) -> Unit,
    hasMore: Boolean = false,
    onLoadMore: () -> Unit = {},
) {
    var newestFirst by remember { mutableStateOf(true) }
    val ordered = remember(releases, newestFirst) {
        val dated = releases.sortedBy { ReleaseDate.parse(it.date) ?: java.time.LocalDate.MIN }
        if (newestFirst) dated.reversed() else dated
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = str("btn_back"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 168.dp),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "sort") {
                SortChips(
                    options = listOf(true, false),
                    selected = newestFirst,
                    labelOf = { str(if (it) "sort_newest" else "sort_oldest") },
                    onSelect = { newestFirst = it },
                    sidePadding = 0.dp,
                )
            }
            items(ordered, key = { it.stableKey() }) { release ->
                val year = ReleaseDate.parse(release.date)?.year?.toString()
                val details = listOfNotNull(release.kindLabel(), year, release.sizeLabel()).joinToString(" · ")
                ReleaseTile(release.title, release.artworkUrl, details) { onOpen(release) }
            }
            if (hasMore) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "more") {
                    OutlinedButton(
                        onClick = onLoadMore,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text(str("btn_load_more")) }
                }
            }
        }
    }
}

private fun ArtistRelease.stableKey(): String = when (this) {
    is ArtistRelease.Song -> "song:${track.id}"
    is ArtistRelease.Record -> "record:${playlist.id}"
}
