package com.alananasss.kittytune.ui.main

import com.alananasss.kittytune.ui.common.escapeDismisses
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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.core.trackTextInput
import com.alananasss.kittytune.ui.home.HomeViewModel
import com.alananasss.kittytune.ui.player.PlayerViewModel

import com.alananasss.kittytune.ui.common.Tip

/**
 * Top bar of the content panel: back/forward navigation, centered search field
 * (embedded Home search, same as the Android app), right panel toggle arrow on the right.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopBar(
    navController: NavController,
    homeViewModel: HomeViewModel,
    playerViewModel: PlayerViewModel,
    historyNavigator: HistoryNavigator? = null,
    isRightPanelOpen: Boolean = false,
    onToggleRightPanel: () -> Unit = {},
) {
    val vm = homeViewModel
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val canGoBack = historyNavigator?.canGoBack ?: (navController.previousBackStackEntry != null)
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
                if (historyNavigator != null) {
                    historyNavigator.back()
                } else {
                    navController.popBackStack()
                }
            }
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = str("btn_back"))
        }

        androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))

        val canGoForward = historyNavigator?.canGoForward ?: false
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
                historyNavigator?.forward()
            }
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
        }

        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))

        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        OutlinedTextField(
            value = vm.searchQuery,
            onValueChange = {
                vm.isSearching = it.isNotBlank()
                vm.onSearchQueryChanged(it)
                if (currentRoute != "home" && it.isNotBlank()) {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = false }
                        launchSingleTop = true
                    }
                }
            },
            // The hint is a full sentence and it is longer in some languages than in English,
            // so it has to be allowed to truncate: left to wrap it makes the field two lines
            // tall and bends the pill out of shape whenever the bar is tight (issue #33).
            placeholder = {
                Text(str("search_hint"), maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
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
            // A low minimum: the field would rather be narrow than push the buttons around it
            // off the bar when the window shrinks or the UI scale goes up.
            modifier = Modifier
                .widthIn(min = 160.dp, max = 480.dp)
                .trackTextInput()
                // Escape only. This field is always on the bar, so it has no closed state to return to
                // — and a click that took the query with it would clear the search every time somebody
                // clicked one of its own results. Escape is the gesture that means "and I am done":
                // it empties the field and leaves the results, which is the exit he could not find
                // ("you will be able to close it only after returning from the tabs", issue #33).
                .escapeDismisses {
                    vm.clearSearch()
                    vm.isSearching = false
                    focusManager.clearFocus()
                },
        )

        androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))

        FilledTonalIconButton(
            shapes = IconButtonDefaults.shapes(),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            onClick = {
                if (currentRoute != "recognition") {
                    navController.navigate("recognition")
                }
            }
        ) {
            Icon(
                androidx.compose.material.icons.Icons.Rounded.GraphicEq,
                contentDescription = str("pref_bottom_menu_fab_recognition")
            )
        }

        // Spacer to balance the centered search field
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))

        // Permanent icon on the right panel when open to collapse it, and when closed to open it (issue #33).
        // Located where the avatar used to be, visible on all pages.
        Tip(if (isRightPanelOpen) str("panel_collapse") else str("panel_expand")) {
            FilledTonalIconButton(
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                onClick = onToggleRightPanel
            ) {
                RightPanelToggleIcon(isOpen = isRightPanelOpen)
            }
        }
    }
}

/**
 * Custom sidebar toggle icon matching the user interface specifications (issue #33).
 * Draws a panel layout icon with an integrated collapse/expand indicator.
 */
@Composable
fun RightPanelToggleIcon(
    isOpen: Boolean,
    modifier: Modifier = Modifier.size(19.dp),
    tint: androidx.compose.ui.graphics.Color = androidx.compose.material3.LocalContentColor.current
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val strokeWidth = 1.65.dp.toPx()
        val cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.5.dp.toPx(), 3.5.dp.toPx())
        val padding = 1.5.dp.toPx()
        val rectLeft = padding
        val rectTop = padding
        val rectRight = size.width - padding
        val rectBottom = size.height - padding
        val rectWidth = rectRight - rectLeft
        val rectHeight = rectBottom - rectTop

        // Outer rounded rectangle
        drawRoundRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(rectLeft, rectTop),
            size = androidx.compose.ui.geometry.Size(rectWidth, rectHeight),
            cornerRadius = cornerRadius,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
        )

        // Vertical divider separating the main area from the right panel (at ~62% width)
        val dividerX = rectLeft + rectWidth * 0.62f
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(dividerX, rectTop),
            end = androidx.compose.ui.geometry.Offset(dividerX, rectBottom),
            strokeWidth = strokeWidth
        )

        // Chevron inside the right panel partition
        val panelCenterX = dividerX + (rectRight - dividerX) * 0.5f
        val centerY = rectTop + rectHeight * 0.5f
        val arrowHalfH = 2.8.dp.toPx()
        val arrowW = 2.0.dp.toPx()

        val path = androidx.compose.ui.graphics.Path()
        if (isOpen) {
            // Arrow pointing right > (collapse towards edge)
            path.moveTo(panelCenterX - arrowW * 0.5f, centerY - arrowHalfH)
            path.lineTo(panelCenterX + arrowW * 0.5f, centerY)
            path.lineTo(panelCenterX - arrowW * 0.5f, centerY + arrowHalfH)
        } else {
            // Arrow pointing left < (expand into view)
            path.moveTo(panelCenterX + arrowW * 0.5f, centerY - arrowHalfH)
            path.lineTo(panelCenterX - arrowW * 0.5f, centerY)
            path.lineTo(panelCenterX + arrowW * 0.5f, centerY + arrowHalfH)
        }

        drawPath(
            path = path,
            color = tint,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokeWidth * 0.9f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )
    }
}


