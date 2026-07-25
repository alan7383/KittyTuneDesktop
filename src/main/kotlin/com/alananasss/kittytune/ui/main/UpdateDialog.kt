package com.alananasss.kittytune.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
import com.alananasss.kittytune.core.BackHandler
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.UpdateManager
import com.alananasss.kittytune.data.UpdateStatus
import com.alananasss.kittytune.data.network.GithubRelease
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateDialog(
    release: GithubRelease?,
    status: UpdateStatus,
    progress: Float,
    totalSize: Long,
    onDismiss: () -> Unit
) {
    if (release == null && status != UpdateStatus.DOWNLOADING && status != UpdateStatus.INSTALLING && status != UpdateStatus.READY_TO_INSTALL) return

    BackHandler(onBack = onDismiss)

    val scope = rememberCoroutineScope()
    val isDownloading = status == UpdateStatus.DOWNLOADING
    val isPaused = status == UpdateStatus.PAUSED
    val isInstalling = status == UpdateStatus.INSTALLING
    val isReady = status == UpdateStatus.READY_TO_INSTALL
    val isMultiInstance = status == UpdateStatus.MULTIPLE_INSTANCES

    Dialog(onDismissRequest = {
        if (!isDownloading && !isInstalling) {
            onDismiss()
        }
    }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.width(640.dp).padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                isReady -> str("update_success_title")
                                isInstalling -> str("update_installing_title")
                                isDownloading -> str("update_downloading", (progress * 100).toInt())
                                isPaused -> str("update_paused_title")
                                isMultiInstance -> str("update_multi_instance_title")
                                else -> str("update_available_title")
                            },
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if ((isDownloading || isPaused) && totalSize > 0) {
                            val downloadedBytes = (progress * totalSize).toLong()
                            Text(
                                text = "${formatSize(downloadedBytes)} / ${formatSize(totalSize)} (${(progress * 100).toInt()}%)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        } else if (isInstalling) {
                            Text(
                                text = str("update_installing_step"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (!isDownloading && !isInstalling) {
                        IconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Default.Close, contentDescription = str("btn_cancel"))
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Expressive Wavy Progress Indicator during download / installation
                if (isDownloading || isPaused) {
                    LinearWavyProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        color = if (isPaused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(16.dp))
                } else if (isInstalling) {
                    LinearWavyProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(16.dp))
                }

                if (isMultiInstance) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.NewReleases,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = str("update_multi_instance_desc"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // Release info / notes
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NewReleases,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = (release?.versionName ?: release?.tagName ?: "KittyTune").removePrefix("v"),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = "KittyTune ${(release?.versionName ?: release?.tagName ?: "").removePrefix("v")}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val bodyText = release?.body
                    if (!bodyText.isNullOrBlank()) {
                        MarkdownText(markdown = bodyText)
                    } else {
                        Text(
                            text = str("update_release_notes"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (status == UpdateStatus.AVAILABLE) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                UpdateManager.setAutoUpdateEnabled(false)
                                onDismiss()
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                            shapes = ButtonDefaults.shapes()
                        ) {
                            Text(
                                text = str("update_btn_dont_remind"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // Action buttons with M3 Expressive shapes parameter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (status) {
                        UpdateStatus.AVAILABLE -> {
                            OutlinedButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                                Text(str("btn_cancel"))
                            }
                            Spacer(Modifier.width(12.dp))
                            FilledTonalButton(
                                onClick = { scope.launch { UpdateManager.downloadUpdate() } },
                                shapes = ButtonDefaults.shapes()
                            ) {
                                Icon(Icons.Outlined.InstallMobile, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = str("update_btn_download"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        UpdateStatus.DOWNLOADING -> {
                            OutlinedButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                                Text(str("update_btn_minimize"))
                            }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = { UpdateManager.pauseDownload() }, shapes = ButtonDefaults.shapes()) {
                                Text(str("update_btn_pause"))
                            }
                            Spacer(Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = { UpdateManager.dismiss() },
                                shapes = ButtonDefaults.shapes()
                            ) {
                                Icon(Icons.Outlined.Cancel, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = str("btn_cancel"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        UpdateStatus.PAUSED -> {
                            OutlinedButton(onClick = { UpdateManager.dismiss() }, shapes = ButtonDefaults.shapes()) {
                                Text(str("btn_cancel"))
                            }
                            Spacer(Modifier.width(12.dp))
                            Button(
                                onClick = { scope.launch { UpdateManager.downloadUpdate() } },
                                shapes = ButtonDefaults.shapes()
                            ) {
                                Text(
                                    text = str("update_btn_resume"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        UpdateStatus.MULTIPLE_INSTANCES -> {
                            OutlinedButton(onClick = { UpdateManager.cancelDownload() }, shapes = ButtonDefaults.shapes()) {
                                Text(str("btn_cancel"))
                            }
                            Spacer(Modifier.width(12.dp))
                            Button(
                                onClick = { UpdateManager.killInstancesAndContinue() },
                                shapes = ButtonDefaults.shapes(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Outlined.Cancel, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = str("update_btn_close_instances"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        UpdateStatus.INSTALLING -> {
                            FilledTonalButton(
                                onClick = {},
                                enabled = false,
                                shapes = ButtonDefaults.shapes()
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(str("update_installing_btn"))
                            }
                        }
                        UpdateStatus.READY_TO_INSTALL -> {
                            Button(
                                onClick = { UpdateManager.restartApp() },
                                shapes = ButtonDefaults.shapes(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = str("update_btn_restart"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        else -> {
                            Button(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                                Text("OK")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val linkColor = MaterialTheme.colorScheme.primary

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val lines = markdown.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            when {
                trimmed.startsWith("### ") -> {
                    val annotated = parseInlineMarkdown(trimmed.removePrefix("### "), linkColor)
                    ClickableAnnotatedText(annotated, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), defaultColor = MaterialTheme.colorScheme.primary)
                }
                trimmed.startsWith("## ") -> {
                    val annotated = parseInlineMarkdown(trimmed.removePrefix("## "), linkColor)
                    ClickableAnnotatedText(annotated, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), defaultColor = MaterialTheme.colorScheme.primary)
                }
                trimmed.startsWith("# ") -> {
                    val annotated = parseInlineMarkdown(trimmed.removePrefix("# "), linkColor)
                    ClickableAnnotatedText(annotated, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), defaultColor = MaterialTheme.colorScheme.primary)
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            style = style,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        val annotated = parseInlineMarkdown(trimmed.substring(2), linkColor)
                        ClickableAnnotatedText(annotated, style = style, defaultColor = color)
                    }
                }
                else -> {
                    val annotated = parseInlineMarkdown(trimmed, linkColor)
                    ClickableAnnotatedText(annotated, style = style, defaultColor = color)
                }
            }
        }
    }
}

@Composable
private fun ClickableAnnotatedText(
    annotatedString: AnnotatedString,
    style: TextStyle,
    defaultColor: Color
) {
    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }
    val hasUrl = remember(annotatedString) {
        annotatedString.getStringAnnotations(tag = "URL", start = 0, end = annotatedString.length).isNotEmpty()
    }
    val isHoveringLink = remember { mutableStateOf(false) }

    Text(
        text = annotatedString,
        style = style,
        color = defaultColor,
        onTextLayout = { layoutResult.value = it },
        modifier = Modifier
            .pointerHoverIcon(if (isHoveringLink.value || hasUrl) PointerIcon.Hand else PointerIcon.Default)
            .pointerInput(annotatedString) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pos = event.changes.firstOrNull()?.position
                        if (pos != null && layoutResult.value != null) {
                            val layout = layoutResult.value!!
                            if (pos.x >= 0 && pos.y >= 0 && pos.x <= layout.size.width && pos.y <= layout.size.height) {
                                val offset = layout.getOffsetForPosition(pos)
                                isHoveringLink.value = annotatedString
                                    .getStringAnnotations(tag = "URL", start = offset, end = offset)
                                    .isNotEmpty()
                            } else {
                                isHoveringLink.value = false
                            }
                        }
                    }
                }
            }
            .pointerInput(annotatedString) {
                detectTapGestures { pos ->
                    layoutResult.value?.let { layout ->
                        val offset = layout.getOffsetForPosition(pos)
                        annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                            .firstOrNull()?.let { annotation ->
                                com.alananasss.kittytune.core.openUrl(annotation.item)
                            }
                    }
                }
            }
    )
}

private fun parseInlineMarkdown(text: String, linkColor: Color): AnnotatedString {
    return buildAnnotatedString {
        val regex = Regex("""(\[(.*?)]\((https?://[^\s)]+)\))|(https?://[^\s]+)|(\*\*(.*?)\*\*)""")
        var lastIndex = 0

        regex.findAll(text).forEach { match ->
            if (match.range.first > lastIndex) {
                append(text.substring(lastIndex, match.range.first))
            }

            val mdLinkMatch = match.groups[1]
            val rawUrlMatch = match.groups[4]
            val boldMatch = match.groups[5]

            when {
                mdLinkMatch != null -> {
                    val label = match.groups[2]?.value ?: ""
                    val url = match.groups[3]?.value ?: ""
                    pushStringAnnotation(tag = "URL", annotation = url)
                    withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold)) {
                        append(label)
                    }
                    pop()
                }
                rawUrlMatch != null -> {
                    val url = rawUrlMatch.value
                    pushStringAnnotation(tag = "URL", annotation = url)
                    withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Normal)) {
                        append(url)
                    }
                    pop()
                }
                boldMatch != null -> {
                    val boldText = match.groups[6]?.value ?: ""
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(boldText)
                    }
                }
            }
            lastIndex = match.range.last + 1
        }

        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        kb >= 1.0 -> String.format("%.0f KB", kb)
        else -> "$bytes B"
    }
}
