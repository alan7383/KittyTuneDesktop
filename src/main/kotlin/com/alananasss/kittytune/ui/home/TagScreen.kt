@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alananasss.kittytune.ui.home

import androidx.compose.material3.IconButtonDefaults

import androidx.compose.material3.ButtonDefaults

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alananasss.kittytune.core.AppInstance
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.DownloadManager
import com.alananasss.kittytune.ui.library.TrackListItem
import com.alananasss.kittytune.ui.player.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TagScreen(
    tagName: String,
    onBackClick: () -> Unit,
    playerViewModel: PlayerViewModel,
    tagViewModel: TagViewModel = viewModel(key = "tag_$tagName") { TagViewModel(AppInstance.application) }
) {
    val downloadProgress by DownloadManager.downloadProgress.collectAsState()
    val downloadedIds by DownloadManager.downloadedIds.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(tagName) {
        tagViewModel.loadTag(tagName)
    }

    val currentTracks = if (tagViewModel.selectedTabIndex == 0) {
        tagViewModel.popularTracks
    } else {
        tagViewModel.recentTracks
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            "#${tagName.uppercase()}",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Desktop: pill segmented tabs instead of a full-width mobile TabRow.
                SecondaryTabRow(
                    selectedTabIndex = tagViewModel.selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.widthIn(max = 420.dp)
                ) {
                    Tab(
                        selected = tagViewModel.selectedTabIndex == 0,
                        onClick = { tagViewModel.onTabSelected(0) },
                        text = { Text(str("profile_tab_popular")) }
                    )
                    Tab(
                        selected = tagViewModel.selectedTabIndex == 1,
                        onClick = { tagViewModel.onTabSelected(1) },
                        text = { Text(str("profile_latest_tracks")) }
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            AnimatedContent(
                targetState = tagViewModel.uiState,
                transitionSpec = {
                    (fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.96f))
                        .togetherWith(fadeOut(tween(200)))
                },
                label = "tagContent",
                modifier = Modifier.fillMaxSize()
            ) { state ->
                when (state) {
                    "LOADING" -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularWavyProgressIndicator()
                        }
                    }
                    "EMPTY" -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(str("no_results"))
                        }
                    }
                    "ERROR" -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(str("error_generic"))
                        }
                    }
                    "SUCCESS" -> {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            itemsIndexed(currentTracks) { index, track ->
                                if (index >= currentTracks.size - 3) {
                                    LaunchedEffect(Unit) {
                                        tagViewModel.loadMore()
                                    }
                                }

                                val progress = downloadProgress[track.id]
                                val isDownloaded = remember(track.id, downloadedIds) {
                                    (track.id < 0 && track.source != "youtube") || downloadedIds.contains(track.id)
                                }

                                TagStaggeredItem(
                                    index = index,
                                    key = tagName,
                                    isScrolling = listState.isScrollInProgress
                                ) {
                                    TrackListItem(
                                        track = track,
                                        currentlyPlayingTrack = playerViewModel.currentTrack,
                                        index = index,
                                        isDownloading = progress != null,
                                        isDownloaded = isDownloaded,
                                        downloadProgress = progress ?: 0,
                                        onClick = {
                                            playerViewModel.playPlaylist(currentTracks.toList(), index, context = null)
                                        },
                                        onOptionClick = { playerViewModel.showTrackOptions(track) }
                                    )
                                }
                            }

                            item {
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TagStaggeredItem(
    index: Int,
    key: Any? = null,
    isScrolling: Boolean = false,
    content: @Composable () -> Unit
) {
    var hasAnimated by rememberSaveable(key) { mutableStateOf(false) }

    val skipAnimation = hasAnimated || index >= 8 || isScrolling

    val alpha = remember(key) { Animatable(if (skipAnimation) 1f else 0f) }
    val offsetY = remember(key) { Animatable(if (skipAnimation) 0f else 20f) }
    val scale = remember(key) { Animatable(if (skipAnimation) 1f else 0.95f) }

    LaunchedEffect(key) {
        if (!skipAnimation) {
            val staggerDelay = index * 30L
            delay(staggerDelay)
            val a = launch { alpha.animateTo(1f, animationSpec = tween(200)) }
            val o = launch { offsetY.animateTo(0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) }
            val s = launch { scale.animateTo(1f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) }
            a.join()
            o.join()
            s.join()
            hasAnimated = true
        }
    }

    Box(
        modifier = Modifier
            .graphicsLayer {
                this.alpha = alpha.value
                this.translationY = offsetY.value * density
                this.scaleX = scale.value
                this.scaleY = scale.value
            }
    ) {
        content()
    }
}
