package com.alananasss.kittytune.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.player.ai.AiDetectionDialog
import com.alananasss.kittytune.ui.profile.SettingsCategory
import com.alananasss.kittytune.ui.profile.SettingsNavigation
import com.alananasss.kittytune.ui.profile.SettingsPlace
import com.alananasss.kittytune.ui.profile.SettingsSubPage
import kotlinx.coroutines.delay

/**
 * Desktop hosts for the content-filtering overlays (issue #41):
 * the AI-detection details dialog and the post-auto-skip undo bar
 * (Android: bottom sheet + snackbar).
 */
@Composable
fun AiDetectionOverlays(viewModel: PlayerViewModel, navController: NavController) {
    if (viewModel.showAiDetectionSheet) {
        AiDetectionDialog(
            viewModel = viewModel,
            onDismissRequest = { viewModel.showAiDetectionSheet = false },
            onOpenSettings = {
                viewModel.showAiDetectionSheet = false
                SettingsNavigation.go(
                    SettingsPlace(
                        SettingsCategory.MISC,
                        listOf(SettingsSubPage.MISC_CONTENT_FILTER),
                    ),
                )
                navController.navigate("settings")
            },
        )
    }

    val bottomClearance by animateDpAsState(
        targetValue = if (viewModel.isPlayerExpanded) 32.dp else 116.dp,
        label = "aiSkipClearance",
    )
    Box(
        modifier = Modifier.fillMaxSize().padding(bottom = bottomClearance),
        contentAlignment = Alignment.BottomCenter,
    ) {
        var lastTrackTitle by remember { mutableStateOf<String?>(null) }
        val skipped = viewModel.aiSkippedTrack
        if (skipped != null && !skipped.title.isNullOrBlank()) {
            lastTrackTitle = skipped.title
        }
        val currentTitle = skipped?.title ?: lastTrackTitle
        val baseMsg = str("ai_skip_undo_message")
        val message = if (!currentTitle.isNullOrBlank()) "$baseMsg • $currentTitle" else baseMsg

        AnimatedVisibility(
            visible = viewModel.showAiSkipUndoBar,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
            ) + fadeIn(animationSpec = tween(durationMillis = 300)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            ) + fadeOut(animationSpec = tween(durationMillis = 250)),
            modifier = Modifier.zIndex(20f),
        ) {
            com.alananasss.kittytune.ui.player.pixel.DismissUndoBar(
                text = message,
                leadingIcon = Icons.Rounded.SmartToy,
                onUndo = { viewModel.undoAiSkip() },
                onClose = { viewModel.hideAiSkipUndoBar() },
                durationMillis = 6000L,
                modifier = Modifier.widthIn(min = 380.dp, max = 640.dp),
            )
        }
    }
}
