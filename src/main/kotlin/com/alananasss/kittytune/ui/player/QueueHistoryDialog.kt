package com.alananasss.kittytune.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.BackHandler
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.QueueHistoryRepository
import com.alananasss.kittytune.data.local.QueueHistorySummary
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
import com.alananasss.kittytune.utils.makeTimeString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun QueueHistoryDialog(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val summaries by QueueHistoryRepository.getSummaries().collectAsState(initial = emptyList())
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var expandedQueueId by remember { mutableStateOf<String?>(null) }
    var snapshotSavedRecently by remember { mutableStateOf(false) }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(str("queue_history_clear_title")) },
            text = { Text(str("queue_history_clear_msg")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        QueueHistoryRepository.clearAll()
                        showClearConfirmDialog = false
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(str("queue_history_delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirmDialog = false },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(str("btn_cancel"))
                }
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .width(640.dp)
                .fillMaxHeight(0.85f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = str("queue_history"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (summaries.size == 1) {
                                str("queue_history_count_one")
                            } else {
                                str("queue_history_count", summaries.size)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (summaries.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearConfirmDialog = true },
                            shapes = IconButtonDefaults.shapes(),
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = str("queue_history_clear_title"),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = str("btn_close"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Snapshot Current Queue Banner
                val currentQueue = viewModel.uiQueueState
                if (currentQueue.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (snapshotSavedRecently) Icons.Rounded.Check else Icons.Rounded.BookmarkAdd,
                                contentDescription = null,
                                tint = if (snapshotSavedRecently) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = str("queue_history_snapshot"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (currentQueue.size == 1) {
                                        "1 track currently in queue"
                                    } else {
                                        "${currentQueue.size} tracks currently in queue"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.saveCurrentQueueToHistory()
                                    snapshotSavedRecently = true
                                },
                                shapes = ButtonDefaults.shapes(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text(
                                    text = if (snapshotSavedRecently) "Saved" else "Snapshot",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                // Items list or empty state
                if (summaries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = str("queue_history_empty"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = str("queue_history_empty_desc"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            count = summaries.size,
                            key = { idx -> summaries[idx].id }
                        ) { idx ->
                            val summary = summaries[idx]
                            QueueHistoryCard(
                                summary = summary,
                                isExpanded = expandedQueueId == summary.id,
                                onToggleExpand = {
                                    expandedQueueId = if (expandedQueueId == summary.id) null else summary.id
                                },
                                onRestore = {
                                    viewModel.restoreQueueFromHistory(summary.id)
                                    onDismiss()
                                },
                                onDelete = {
                                    QueueHistoryRepository.deleteQueue(summary.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueHistoryCard(
    summary: QueueHistorySummary,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val relativeTime = remember(summary.savedAt) {
        formatRelativeTime(summary.savedAt)
    }

    var tracksInQueue by remember(summary.id, isExpanded) { mutableStateOf<List<Track>?>(null) }

    if (isExpanded && tracksInQueue == null) {
        LaunchedEffect(summary.id) {
            val entity = QueueHistoryRepository.getQueueById(summary.id)
            if (entity != null) {
                tracksInQueue = QueueHistoryRepository.deserializeTracks(entity.tracksJson)
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggleExpand),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cover art thumbnail
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    if (!summary.firstTrackArtworkUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = summary.firstTrackArtworkUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                // Title and details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(2.dp))

                    val subtitle = buildString {
                        append("${summary.trackCount} tracks")
                        if (!summary.firstTrackTitle.isNullOrBlank()) {
                            append(" • ")
                            append(summary.firstTrackTitle)
                        }
                    }

                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = relativeTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Actions: Restore button & Expand toggle & Delete
                FilledTonalIconButton(
                    onClick = onRestore,
                    modifier = Modifier.size(36.dp).pointerHoverIcon(PointerIcon.Hand),
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = str("queue_history_restore"),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onToggleExpand,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(36.dp).pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(36.dp).pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = str("queue_history_delete"),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Expanded Preview of Tracks
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    val tracks = tracksInQueue
                    if (tracks == null) {
                        Text(
                            text = "Loading tracks...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        val previewTracks = tracks.take(8)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            previewTracks.forEachIndexed { idx, track ->
                                val isCurrentTrack = idx == summary.currentIndex
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isCurrentTrack) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                            else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${idx + 1}.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isCurrentTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(24.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isCurrentTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = track.displayArtist,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    val duration = track.durationMs ?: 0L
                                    if (duration > 0L) {
                                        Text(
                                            text = makeTimeString(duration),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            if (tracks.size > 8) {
                                Text(
                                    text = "+ ${tracks.size - 8} more tracks",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onRestore,
                                modifier = Modifier.weight(1f).pointerHoverIcon(PointerIcon.Hand),
                                shapes = ButtonDefaults.shapes(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(str("queue_history_restore"))
                            }

                            OutlinedButton(
                                onClick = onDelete,
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = str("queue_history_delete"),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val diff = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> {
            val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
