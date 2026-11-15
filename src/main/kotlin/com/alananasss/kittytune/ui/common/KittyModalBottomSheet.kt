package com.alananasss.kittytune.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Desktop counterpart of the Android option sheet: the same content, shown as a centred card a mouse can reach
 * (a sheet sliding up from the bottom of a large window is far from the pointer). Escape and a click outside
 * dismiss it, like a swipe down on Android.
 */
@Composable
fun KittyModalBottomSheet(
    onDismissRequest: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = containerColor,
            modifier = Modifier.widthIn(min = 360.dp, max = 560.dp).padding(16.dp),
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp), content = content)
        }
    }
}
