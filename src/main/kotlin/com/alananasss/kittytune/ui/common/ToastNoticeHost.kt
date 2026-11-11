package com.alananasss.kittytune.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.Toaster
import com.alananasss.kittytune.core.str
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** A new message replaces the previous one and restarts its reading time. */
@Composable
fun ToastNoticeHost(modifier: Modifier = Modifier) {
    var message by remember { mutableStateOf<String?>(null) }
    // Retain the text during the exit animation.
    var displayedMessage by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        Toaster.messages.collectLatest { next ->
            if (next.isBlank()) return@collectLatest
            displayedMessage = next
            message = next
            delay((next.length * 60L).coerceIn(4_000L, 10_000L))
            message = null
        }
    }
    ConfirmationHost()
    AnimatedVisibility(visible = message != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = 8.dp,
            tonalElevation = 2.dp,
            modifier = Modifier.widthIn(max = 600.dp),
        ) {
            Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(displayedMessage, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false).semantics { liveRegion = LiveRegionMode.Polite })
                IconButton(onClick = { message = null }) {
                    Icon(Icons.Rounded.Close, contentDescription = str("btn_close"))
                }
            }
        }
    }
}

/**
 * [Toaster.confirm] notices, drawn as the pill the "AI track skipped" notice uses, minus its Undo button.
 *
 * The bar closes itself when its countdown runs out; the counter is its key, so confirming the same thing twice
 * in a row starts the countdown again instead of being ignored.
 */
@Composable
private fun ConfirmationHost() {
    var shown by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var count by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        Toaster.confirmations.collect { next ->
            if (next.isBlank()) return@collect
            text = next
            count++
            shown = true
        }
    }
    AnimatedVisibility(
        visible = shown,
        enter = androidx.compose.animation.slideInVertically(
            androidx.compose.animation.core.tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it } +
            fadeIn(androidx.compose.animation.core.tween(300)),
        exit = androidx.compose.animation.slideOutVertically(
            androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it } +
            fadeOut(androidx.compose.animation.core.tween(250)),
    ) {
        key(count) {
            com.alananasss.kittytune.ui.player.pixel.DismissUndoBar(
                text = text,
                leadingIcon = Icons.Rounded.CheckCircle,
                onUndo = null,
                onClose = { shown = false },
                durationMillis = 2500L,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.widthIn(min = 260.dp, max = 640.dp),
            )
        }
    }
}
