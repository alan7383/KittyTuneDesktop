package com.alananasss.kittytune.ui.main

import androidx.compose.material3.ButtonDefaults

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.savedstate.read
import com.alananasss.kittytune.ui.modifiers.squish
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.core.trackTextInput
import com.alananasss.kittytune.domain.getHighResAvatarUrl
import com.alananasss.kittytune.ui.home.HomeViewModel
import com.alananasss.kittytune.ui.player.PlayerViewModel

/**
 * Top bar of the content panel: back/forward navigation, centered search field
 * (embedded Home search, same as the Android app), avatar on the right.
 */
object NavigationTracker {
    val forwardStack = androidx.compose.runtime.mutableStateListOf<String>()
    var isNavigatingBackOrForward = false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopBar(
    navController: NavController,
    homeViewModel: HomeViewModel,
    playerViewModel: PlayerViewModel,
) {
    val vm = homeViewModel

    // Observe back stack entry changes to clear forward stack on normal navigation
    val currentEntry by navController.currentBackStackEntryAsState()
    
    androidx.compose.runtime.LaunchedEffect(currentEntry) {
        if (!NavigationTracker.isNavigatingBackOrForward) {
            NavigationTracker.forwardStack.clear()
        }
        // reset the flag
        NavigationTracker.isNavigatingBackOrForward = false
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val canGoBack = navController.previousBackStackEntry != null
        val backInteractionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        FilledTonalIconButton(
            shapes = IconButtonDefaults.shapes(),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            enabled = canGoBack,
            interactionSource = backInteractionSource,
            onClick = {
                NavigationTracker.isNavigatingBackOrForward = true
                val entry = navController.currentBackStackEntry
                val route = entry?.destination?.route
                if (route != null) {
                    var resolved: String = route
                    try {
                        val args = entry.arguments
                        if (args != null) {
                            val possibleKeys = listOf("playlistId", "userId", "query", "albumId", "trackId")
                            possibleKeys.forEach { key ->
                                val value = args?.toString() ?: "" //key)
                                if (value != null) {
                                    resolved = resolved.replace("{$key}", value)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    NavigationTracker.forwardStack.add(resolved)
                }
                navController.popBackStack()
            }
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = str("btn_back"))
        }

        androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))

        val canGoForward = NavigationTracker.forwardStack.isNotEmpty()
        val forwardInteractionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        FilledTonalIconButton(
            shapes = IconButtonDefaults.shapes(),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            enabled = canGoForward,
            interactionSource = forwardInteractionSource,
            onClick = {
                if (NavigationTracker.forwardStack.isNotEmpty()) {
                    NavigationTracker.isNavigatingBackOrForward = true
                    val nextRoute = NavigationTracker.forwardStack.removeLast()
                    navController.navigate(nextRoute)
                }
            }
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
        }

        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))

        OutlinedTextField(
            value = vm.searchQuery,
            onValueChange = {
                vm.isSearching = it.isNotBlank()
                vm.onSearchQueryChanged(it)
            },
            placeholder = { Text(str("search_hint")) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (vm.searchQuery.isNotBlank()) {
                    IconButton(
                        shapes = IconButtonDefaults.shapes(),
                        onClick = {
                            vm.clearSearch()
                        }
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                    }
                }
            },
            singleLine = true,
            shape = CircleShape,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
            modifier = Modifier.widthIn(min = 320.dp, max = 480.dp).trackTextInput(),
        )

        androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))

        FilledTonalIconButton(
            shapes = IconButtonDefaults.shapes(),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            onClick = {
                navController.navigate("recognition")
            }
        ) {
            Icon(
                androidx.compose.material.icons.Icons.Rounded.GraphicEq,
                contentDescription = str("pref_bottom_menu_fab_recognition")
            )
        }

        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))

        // Avatar -> profile dropdown
        androidx.compose.foundation.layout.Box {
            var showProfileMenu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

            androidx.compose.runtime.LaunchedEffect(Unit) {
                if (playerViewModel.currentUser == null) {
                    playerViewModel.fetchUserProfile()
                }
            }

            val rawAvatar = playerViewModel.currentUser?.avatarUrl
            val avatarUrl = rawAvatar.getHighResAvatarUrl()

            IconButton(
                shapes = IconButtonDefaults.shapes(),
                onClick = { showProfileMenu = true }
            ) {
                if (!avatarUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp).clip(CircleShape),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            androidx.compose.material3.DropdownMenu(
                expanded = showProfileMenu,
                onDismissRequest = { showProfileMenu = false }
            ) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(str("nav_profile")) },
                    onClick = {
                        showProfileMenu = false
                        playerViewModel.navigateToPlaylistId = "profile:${playerViewModel.currentUserId}"
                    }
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(str("profile_menu_settings")) },
                    onClick = {
                        showProfileMenu = false
                        navController.navigate("settings")
                    }
                )
                androidx.compose.material3.HorizontalDivider()
                
                val isGuest = playerViewModel.currentUserId == 0L
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(if (isGuest) str("profile_menu_login") else str("profile_menu_logout")) },
                    onClick = {
                        showProfileMenu = false
                        com.alananasss.kittytune.data.TokenManager.logout()
                    }
                )
            }
        }
    }
}

