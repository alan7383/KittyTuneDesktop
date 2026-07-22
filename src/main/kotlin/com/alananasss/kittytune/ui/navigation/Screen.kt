package com.alananasss.kittytune.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Explore
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val titleResId: String, val icon: ImageVector?) {
    data object Welcome : Screen("welcome", "nav_welcome", null)
    data object Home : Screen("home", "nav_home", Icons.Default.Home)
    data object Library : Screen("library", "nav_library", Icons.Default.LibraryMusic)
    data object Search : Screen("search", "nav_search", Icons.Default.Search)
    data object Explore : Screen("genres", "explorer_title", Icons.Default.Explore)
    data object Login : Screen("login", "nav_login", null)
    data object Recognition : Screen("recognition", "nav_search", null)
    data object RecognitionHistory : Screen("recognition_history", "nav_search", null)
}
