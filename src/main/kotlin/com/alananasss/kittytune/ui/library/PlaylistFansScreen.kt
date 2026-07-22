package com.alananasss.kittytune.ui.library

import androidx.compose.material3.IconButtonDefaults

import androidx.compose.material3.ButtonDefaults

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alananasss.kittytune.core.AppInstance
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.ui.track.UserList // shared with TrackDetailScreen
import kotlinx.coroutines.launch

@Composable
fun PlaylistFansScreen(
    playlistId: String,
    initialTab: Int = 0,
    onBackClick: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: PlaylistInfoViewModel = viewModel(key = "playlist_fans_$playlistId") { PlaylistInfoViewModel(AppInstance.application) }
) {
    val pagerState = rememberPagerState(initialPage = initialTab) { 2 }
    val scope = rememberCoroutineScope()
    val tabs = listOf(
        str("detail_likers"),
        str("detail_reposters")
    )

    LaunchedEffect(playlistId) {
        viewModel.loadPlaylistDetails(playlistId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(str("menu_details"), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            SecondaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier.widthIn(max = 420.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(text = title) }
                    )
                }
            }

            HorizontalPager(state = pagerState) { page ->
                when (page) {
                    0 -> UserList(
                        users = viewModel.likers,
                        onNavigate = onNavigate,
                        onLoadMore = { viewModel.loadMoreLikers() },
                        isLoadingMore = viewModel.isLikersLoadingMore
                    )
                    1 -> UserList(
                        users = viewModel.reposters,
                        onNavigate = onNavigate,
                        onLoadMore = { viewModel.loadMoreReposters() },
                        isLoadingMore = viewModel.isRepostersLoadingMore
                    )
                }
            }
        }
    }
}
