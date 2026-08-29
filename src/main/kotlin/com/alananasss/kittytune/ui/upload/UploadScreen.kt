package com.alananasss.kittytune.ui.upload

import com.alananasss.kittytune.core.trackTextInput
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.upload.*
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import java.awt.FileDialog
import java.awt.Frame
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.*
import java.awt.image.BufferedImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.alananasss.kittytune.audio.AudioEngine
import com.alananasss.kittytune.data.StreamResolver
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(
    viewModel: UploadViewModel,
    trackIdToEdit: String? = null,
    onBackClick: () -> Unit,
    onNavigateToProfile: () -> Unit = onBackClick,
    onLoginClick: () -> Unit = {}
) {
    LaunchedEffect(trackIdToEdit) {
        if (trackIdToEdit != null) {
            viewModel.loadTrackForEditing(trackIdToEdit)
        }
    }

    val uploadState by viewModel.uploadState.collectAsState()

    var showGenrePicker by remember { mutableStateOf(false) }
    var showStorefrontSheet by remember { mutableStateOf(false) }
    var showLicenseSelector by remember { mutableStateOf(false) }
    var showCountryPicker by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showDiscardChangesDialog by remember { mutableStateOf(false) }

    var isDraggingOver by remember { mutableStateOf(false) }

    DisposableEffect(viewModel.selectedFile) {
        val dropTargetListener = object : DropTargetListener {
            override fun dragEnter(dtde: DropTargetDragEvent) {
                if (isDragAcceptable(dtde.currentDataFlavors)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY)
                    isDraggingOver = true
                } else {
                    dtde.rejectDrag()
                }
            }

            override fun dragOver(dtde: DropTargetDragEvent) {
                if (isDragAcceptable(dtde.currentDataFlavors)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY)
                    isDraggingOver = true
                } else {
                    dtde.rejectDrag()
                }
            }

            override fun dropActionChanged(dtde: DropTargetDragEvent) {}

            override fun dragExit(dte: DropTargetEvent) {
                isDraggingOver = false
            }

            override fun drop(dtde: DropTargetDropEvent) {
                isDraggingOver = false
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY)
                    val files = extractFilesFromTransferable(dtde.transferable)
                    val audioFile = files.firstOrNull { file ->
                        val ext = file.extension.lowercase()
                        ext in listOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "aiff")
                    }
                    val imageFile = files.firstOrNull { file ->
                        val ext = file.extension.lowercase()
                        ext in listOf("jpg", "jpeg", "png", "webp")
                    }

                    if (audioFile != null) {
                        viewModel.onFileSelected(audioFile)
                        dtde.dropComplete(true)
                    } else if (imageFile != null) {
                        viewModel.onArtworkSelected(imageFile)
                        dtde.dropComplete(true)
                    } else {
                        dtde.dropComplete(false)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    dtde.dropComplete(false)
                }
            }
        }

        val cleanupList = mutableListOf<Pair<java.awt.Component, DropTarget?>>()
        val windows = java.awt.Window.getWindows()
        windows.forEach { win ->
            attachDropTargetRecursively(win, dropTargetListener, cleanupList)
        }

        onDispose {
            cleanupList.forEach { (comp, oldTarget) ->
                comp.dropTarget = oldTarget
            }
        }
    }

    if (!viewModel.isLoggedIn) {
        LoginRequiredScreen(
            onBackClick = onBackClick,
            onLoginClick = onLoginClick
        )
        return
    }

    fun handleBack() {
        if (viewModel.hasUnsavedChanges) {
            showDiscardChangesDialog = true
        } else {
            viewModel.reset()
            onBackClick()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (viewModel.isEditMode) str("edit_track_title") else str("upload_screen_title"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (viewModel.isEditMode) {
                            OutlinedButton(
                                onClick = { viewModel.showDeleteConfirmationDialog = true },
                                shapes = ButtonDefaults.shapes(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.padding(end = 12.dp)
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(str("edit_track_delete_button"))
                            }
                        }

                        Button(
                            onClick = {
                                if (viewModel.isEditMode) {
                                    viewModel.saveTrackEdits {
                                        viewModel.reset()
                                        com.alananasss.kittytune.ui.profile.ProfileViewModel.triggerRefresh()
                                        onNavigateToProfile()
                                    }
                                } else {
                                    viewModel.startUpload()
                                }
                            },
                            enabled = viewModel.canSubmit,
                            shapes = ButtonDefaults.shapes()
                        ) {
                            if (viewModel.isSavingEdit) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Icon(
                                if (viewModel.isEditMode) Icons.Rounded.Save else Icons.Rounded.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (viewModel.isEditMode) str("edit_track_save_button") else str("upload_btn_submit"))
                        }
                    }
                }
            }

            // Main Content Area
            if (!viewModel.isEditMode && viewModel.selectedFile == null) {
                // Hero file dropzone / picker with Drag & Drop
                HeroFilePickerSection(
                    isDraggingOver = isDraggingOver,
                    onFileSelected = { file -> viewModel.onFileSelected(file) }
                )
            } else {
                // 2-Column Desktop Editor
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Left Column (380dp)
                    Column(
                        modifier = Modifier
                            .width(380.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // File info card
                        FileInfoCard(
                            fileName = if (viewModel.isEditMode) viewModel.editingTrack?.title ?: viewModel.title else viewModel.selectedFileName,
                            fileSize = viewModel.selectedFileSizeBytes,
                            durationSeconds = viewModel.trackDurationSeconds,
                            isEditMode = viewModel.isEditMode,
                            onChangeFile = {
                                openNativeAudioFileChooser { file ->
                                    viewModel.onFileSelected(file)
                                }
                            }
                        )

                        // Artwork card
                        ArtworkSelectorCard(
                            bitmap = viewModel.artworkBitmap,
                            existingUrl = viewModel.existingArtworkUrl,
                            onPickArtwork = {
                                openNativeImageFileChooser { file ->
                                    viewModel.onArtworkSelected(file)
                                }
                            },
                            onRemoveArtwork = {
                                viewModel.removeArtwork()
                            }
                        )

                        // Checklist card
                        TrackChecklistCard(viewModel = viewModel)
                    }

                    // Right Column (Tabs & Forms)
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp)
                        ) {
                            // Category tabs
                            CategoryTabs(
                                selectedTab = viewModel.selectedCategoryTab,
                                onTabSelected = { viewModel.selectedCategoryTab = it }
                            )

                            Spacer(Modifier.height(18.dp))

                            // Tab content
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                when (viewModel.selectedCategoryTab) {
                                    0 -> BasicInfoTabContent(
                                        viewModel = viewModel,
                                        onOpenGenrePicker = { showGenrePicker = true }
                                    )
                                    1 -> MetadataTabContent(
                                        viewModel = viewModel,
                                        onOpenStorefrontSheet = { showStorefrontSheet = true }
                                    )
                                    2 -> PermissionsTabContent(
                                        viewModel = viewModel,
                                        onOpenCountryPicker = { showCountryPicker = true }
                                    )
                                    3 -> AdvancedTabContent(
                                        viewModel = viewModel
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Overlays & Dialogs
        if (showGenrePicker) {
            GenrePickerDialog(
                selectedGenre = viewModel.genre,
                onGenreSelected = { viewModel.genre = it },
                onDismiss = { showGenrePicker = false }
            )
        }

        if (showStorefrontSheet) {
            ArtistStorefrontDialog(
                viewModel = viewModel,
                onDismiss = { showStorefrontSheet = false }
            )
        }

        if (viewModel.tempArtworkBitmap != null) {
            TrackArtworkCropDialog(
                bitmap = viewModel.tempArtworkBitmap,
                onDismiss = { viewModel.tempArtworkBitmap = null },
                onSave = { cropped ->
                    viewModel.artworkBitmap = cropped
                    viewModel.tempArtworkBitmap = null
                }
            )
        }

        if (showLicenseSelector) {
            LicenseSelectorDialog(
                currentLicense = viewModel.license,
                onLicenseSelected = {
                    viewModel.license = it
                    showLicenseSelector = false
                },
                onDismiss = { showLicenseSelector = false }
            )
        }

        if (showCountryPicker) {
            CountryPickerDialog(
                mode = viewModel.geoBlockingMode,
                regions = viewModel.geoBlockingRegions,
                onToggleCountry = { viewModel.toggleCountryCode(it) },
                onDismiss = { showCountryPicker = false }
            )
        }

        if (showScheduleDialog) {
            ScheduleReleaseDialog(
                currentDateMs = viewModel.scheduledEpochMs,
                currentTimezone = viewModel.scheduledTimezone,
                onConfirm = { epochMs, tz ->
                    viewModel.scheduledEpochMs = epochMs
                    viewModel.scheduledTimezone = tz
                    viewModel.isSchedulingEnabled = true
                    showScheduleDialog = false
                },
                onDismiss = { showScheduleDialog = false }
            )
        }

        if (viewModel.showDeleteConfirmationDialog) {
            DeleteTrackConfirmationDialog(
                isDeleting = viewModel.isDeletingTrack,
                onConfirm = {
                    viewModel.deleteTrack {
                        viewModel.reset()
                        com.alananasss.kittytune.ui.profile.ProfileViewModel.triggerRefresh()
                        onNavigateToProfile()
                    }
                },
                onDismiss = { viewModel.showDeleteConfirmationDialog = false }
            )
        }

        if (showDiscardChangesDialog) {
            DiscardChangesDialog(
                onConfirmDiscard = {
                    showDiscardChangesDialog = false
                    viewModel.reset()
                    onBackClick()
                },
                onDismiss = { showDiscardChangesDialog = false }
            )
        }

        // Upload progress / status overlay
        if (uploadState is UploadState.Uploading || uploadState is UploadState.Success || uploadState is UploadState.Error) {
            UploadProgressOverlay(
                state = uploadState,
                s3Progress = viewModel.uploadFileProgress,
                hasArtwork = viewModel.artworkBitmap != null || !viewModel.existingArtworkUrl.isNullOrBlank(),
                onCancel = {
                    viewModel.cancelUpload()
                },
                onDismiss = {
                    viewModel.reset()
                    com.alananasss.kittytune.ui.profile.ProfileViewModel.triggerRefresh()
                    onNavigateToProfile()
                },
                onUploadAnother = {
                    viewModel.reset()
                },
                onRetry = {
                    viewModel.resetToFileSelected()
                    viewModel.startUpload()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroFilePickerSection(
    isDraggingOver: Boolean,
    onFileSelected: (File) -> Unit
) {
    val containerColor by animateColorAsState(
        if (isDraggingOver) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surfaceContainerLow
    )
    val borderColor by animateColorAsState(
        if (isDraggingOver) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = BorderStroke(if (isDraggingOver) 2.5.dp else 1.dp, borderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isDraggingOver) 12.dp else 4.dp),
            modifier = Modifier
                .width(640.dp)
                .wrapContentHeight()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = if (isDraggingOver) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(120.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isDraggingOver) Icons.Rounded.Download else Icons.Rounded.AudioFile,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = if (isDraggingOver) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))

                Text(
                    text = if (isDraggingOver) str("upload_drop_file") else str("upload_picker_hero_title"),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = if (isDraggingOver) "Glissez-déposez le fichier audio pour commencer" else str("upload_picker_hero_subtitle"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(32.dp))

                Button(
                    onClick = {
                        openNativeAudioFileChooser { file ->
                            onFileSelected(file)
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.height(52.dp).padding(horizontal = 24.dp)
                ) {
                    Icon(Icons.Rounded.FileUpload, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(str("upload_picker_btn"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = str("upload_picker_max_size"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FileInfoCard(
    fileName: String,
    fileSize: Long,
    durationSeconds: Int = 0,
    isEditMode: Boolean,
    onChangeFile: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Audiotrack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileName.ifBlank { str("track_details_file_name") },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (fileSize > 0) {
                            Text(
                                text = formatFileSize(fileSize),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (durationSeconds > 0) {
                            Text(
                                text = "•  ${formatSeconds(durationSeconds)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            TextButton(
                onClick = onChangeFile,
                shapes = ButtonDefaults.shapes()
            ) {
                Text(if (isEditMode) str("track_details_replace_file") else str("upload_file_change"))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ArtworkSelectorCard(
    bitmap: BufferedImage?,
    existingUrl: String?,
    onPickArtwork: () -> Unit,
    onRemoveArtwork: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable(onClick = onPickArtwork),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.toComposeImageBitmap(),
                        contentDescription = str("upload_artwork_label"),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (!existingUrl.isNullOrBlank()) {
                    coil3.compose.AsyncImage(
                        model = existingUrl,
                        contentDescription = str("upload_artwork_label"),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Rounded.AddPhotoAlternate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = str("upload_artwork_label"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPickArtwork,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(str("upload_artwork_change"), maxLines = 1)
                }

                if (bitmap != null || !existingUrl.isNullOrBlank()) {
                    IconButton(
                        onClick = onRemoveArtwork,
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove Artwork", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackChecklistCard(viewModel: UploadViewModel) {
    val totalSteps = 4
    val completedSteps = viewModel.completedChecklistCount

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = str("track_info_checklist_banner_title"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$completedSteps/$totalSteps",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { completedSteps.toFloat() / totalSteps.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            ChecklistItem(
                title = str("track_info_checklist_bottomsheet_label_title"),
                subtitle = str("track_info_checklist_bottomsheet_label_title_tip"),
                isDone = viewModel.isTitleDone
            )
            ChecklistItem(
                title = str("track_info_checklist_bottomsheet_label_artwork"),
                subtitle = null,
                isDone = viewModel.isArtworkDone
            )
            ChecklistItem(
                title = str("upload_field_genre"),
                subtitle = null,
                isDone = viewModel.isGenreDone
            )
            ChecklistItem(
                title = str("upload_field_description"),
                subtitle = null,
                isDone = viewModel.isDescriptionDone
            )
        }
    }
}

@Composable
private fun ChecklistItem(title: String, subtitle: String?, isDone: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isDone) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isDone) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isDone) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!subtitle.isNullOrBlank() && !isDone) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun CategoryTabs(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabTitles = listOf(
        str("upload_tab_basic_info"),
        str("upload_tab_metadata"),
        str("upload_tab_permissions"),
        str("upload_tab_advanced")
    )

    ExpressiveConnectedButtonGroup(
        options = (0..3).toList(),
        selectedOption = selectedTab,
        onOptionSelected = onTabSelected,
        fillMaxWidth = true,
        labelProvider = { index ->
            Text(
                text = tabTitles.getOrElse(index) { "Tab $index" },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium
            )
        },
        iconProvider = { index ->
            val isSelected = selectedTab == index
            if (index == 3) {
                BadgedBox(
                    badge = {
                        Badge(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(6.dp)
                        )
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = when (index) {
                        0 -> Icons.Rounded.Info
                        1 -> Icons.Rounded.Sell
                        2 -> Icons.Rounded.Shield
                        else -> Icons.Rounded.Info
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    )
}

// ---------------- TAB 1: BASIC INFO ----------------
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BasicInfoTabContent(
    viewModel: UploadViewModel,
    onOpenGenrePicker: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        OutlinedTextField(
            value = viewModel.title,
            onValueChange = { viewModel.onTitleChanged(it) },
            label = { Text(str("upload_field_title")) },
            placeholder = { Text(str("upload_field_track_title")) },
            singleLine = true,
            isError = !viewModel.isTitleValid && viewModel.title.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().trackTextInput(),
            shape = RoundedCornerShape(12.dp)
        )

        // Artist
        OutlinedTextField(
            value = viewModel.artist,
            onValueChange = { viewModel.artist = it },
            label = { Text(str("upload_field_artist")) },
            placeholder = { Text(str("upload_field_artist_name")) },
            supportingText = { Text(str("upload_field_hint_artist")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().trackTextInput(),
            shape = RoundedCornerShape(12.dp)
        )

        // Track Permalink
        OutlinedTextField(
            value = viewModel.permalink,
            onValueChange = { viewModel.onPermalinkChanged(it) },
            label = { Text(str("upload_field_permalink")) },
            visualTransformation = PermaLinkVisualTransformation(
                prefix = "soundcloud.com/${if (viewModel.userPermalink.isNotBlank()) viewModel.userPermalink else "user"}/",
                prefixColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            ),
            singleLine = true,
            isError = !viewModel.isPermalinkValid,
            supportingText = {
                if (!viewModel.isPermalinkValid) {
                    Text(str("upload_field_permalink_invalid"), color = MaterialTheme.colorScheme.error)
                }
            },
            modifier = Modifier.fillMaxWidth().trackTextInput(),
            shape = RoundedCornerShape(12.dp)
        )

        // Quick Genres & Genre Picker Field
        Column {
            Text(
                text = str("upload_field_genre"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            // Quick chips
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SOUNDCLOUD_QUICK_GENRES.take(10).forEach { genreName ->
                    val isSelected = viewModel.genre.equals(genreName, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            viewModel.genre = if (isSelected) "" else genreName
                        },
                        label = {
                            val key = getGenreStringKey(genreName)
                            Text(if (key != null) str(key) else genreName)
                        }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            OutlinedCard(
                onClick = onOpenGenrePicker,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = str("upload_field_genre_pick"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (viewModel.genre.isNotBlank()) {
                                val key = getGenreStringKey(viewModel.genre)
                                if (key != null) str(key) else viewModel.genre
                            } else str("upload_field_genre_pick"),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (viewModel.genre.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                        )
                    }
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                }
            }
        }

        // Tags
        Column {
            OutlinedTextField(
                value = viewModel.tagInput,
                onValueChange = { viewModel.tagInput = it },
                label = { Text(str("upload_field_tags")) },
                placeholder = { Text(str("upload_add_tag_hint")) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .trackTextInput()
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                            viewModel.addTag(viewModel.tagInput)
                            true
                        } else false
                    },
                trailingIcon = {
                    if (viewModel.tagInput.isNotBlank()) {
                        IconButton(onClick = { viewModel.addTag(viewModel.tagInput) }) {
                            Icon(Icons.Rounded.Add, contentDescription = "Add tag")
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp)
            )

            if (viewModel.tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    viewModel.tags.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = { },
                            label = { Text("#$tag") },
                            trailingIcon = {
                                IconButton(
                                    onClick = { viewModel.removeTag(tag) },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Remove")
                                }
                            }
                        )
                    }
                }
            }
        }

        // Description
        OutlinedTextField(
            value = viewModel.description,
            onValueChange = { viewModel.description = it },
            label = { Text(str("upload_field_description")) },
            placeholder = { Text(str("upload_field_description_hint")) },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth().trackTextInput(),
            shape = RoundedCornerShape(12.dp)
        )

        // Caption / Légende (max 140 chars)
        OutlinedTextField(
            value = viewModel.caption,
            onValueChange = { if (it.length <= 140) viewModel.caption = it },
            label = { Text(str("upload_field_caption")) },
            placeholder = { Text(str("upload_field_caption_hint")) },
            supportingText = {
                Row(
                    modifier = Modifier.fillMaxWidth().trackTextInput(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = str("upload_field_caption_helper"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    Text(
                        text = "${viewModel.caption.length}/140",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        // Privacy Section (Public / Private with ExpressiveConnectedButtonGroup)
        Text(
            text = str("upload_visibility_title"),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ExpressiveConnectedButtonGroup<TrackPrivacy>(
            options = listOf(TrackPrivacy.PUBLIC, TrackPrivacy.PRIVATE),
            selectedOption = viewModel.privacy,
            onOptionSelected = { privacy: TrackPrivacy ->
                viewModel.privacy = privacy
                if (privacy == TrackPrivacy.PUBLIC) {
                    viewModel.toggleScheduling(false)
                }
            },
            fillMaxWidth = true,
            labelProvider = { privacy: TrackPrivacy ->
                Text(
                    text = when (privacy) {
                        TrackPrivacy.PUBLIC -> str("upload_visibility_public")
                        TrackPrivacy.PRIVATE -> str("upload_visibility_private")
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (viewModel.privacy == privacy) FontWeight.Bold else FontWeight.Medium
                )
            },
            iconProvider = { privacy: TrackPrivacy ->
                Icon(
                    imageVector = when (privacy) {
                        TrackPrivacy.PUBLIC -> Icons.Rounded.Public
                        TrackPrivacy.PRIVATE -> Icons.Rounded.Lock
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        )

        // Schedule Release Section
        ScheduleSection(viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleSection(
    viewModel: UploadViewModel,
    modifier: Modifier = Modifier
) {
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showTimePickerDialog by remember { mutableStateOf(false) }
    val currentEpoch = viewModel.scheduledEpochMs ?: (System.currentTimeMillis() + 86400000L)

    val locale = Locale.getDefault()
    val dateFormat = remember(locale) {
        SimpleDateFormat("d MMMM yyyy", locale)
    }
    val timeFormat = remember(locale) {
        SimpleDateFormat("HH:mm", locale)
    }
    val dateText = remember(currentEpoch, locale) {
        dateFormat.format(Date(currentEpoch))
    }
    val timeText = remember(currentEpoch, locale) {
        timeFormat.format(Date(currentEpoch))
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = str("upload_schedule_section_title"),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = str("upload_schedule_toggle_title"),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = str("upload_schedule_toggle_subtitle"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = viewModel.isSchedulingEnabled,
                        onCheckedChange = { viewModel.toggleScheduling(it) }
                    )
                }

                AnimatedVisibility(
                    visible = viewModel.isSchedulingEnabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDatePickerDialog = true },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.weight(1.3f)
                        ) {
                            Icon(
                                Icons.Rounded.CalendarToday,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(text = dateText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        OutlinedButton(
                            onClick = { showTimePickerDialog = true },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.weight(0.9f)
                        ) {
                            Icon(
                                Icons.Rounded.AccessTime,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(text = timeText, maxLines = 1)
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(top = 2.dp)
                        )
                        Text(
                            text = str("upload_schedule_free_notice"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }

    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = currentEpoch
        )
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selected ->
                            val cal = Calendar.getInstance().apply { timeInMillis = currentEpoch }
                            val hours = cal.get(Calendar.HOUR_OF_DAY)
                            val minutes = cal.get(Calendar.MINUTE)
                            val newCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = selected
                                set(Calendar.HOUR_OF_DAY, hours)
                                set(Calendar.MINUTE, minutes)
                            }
                            viewModel.scheduledEpochMs = newCal.timeInMillis
                        }
                        showDatePickerDialog = false
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(str("btn_save"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePickerDialog = false },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(str("upload_btn_cancel"))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePickerDialog) {
        val cal = remember(currentEpoch) {
            Calendar.getInstance().apply { timeInMillis = currentEpoch }
        }
        var selectedHour by remember { mutableIntStateOf(cal.get(Calendar.HOUR_OF_DAY)) }
        var selectedMinute by remember { mutableIntStateOf(cal.get(Calendar.MINUTE)) }

        Dialog(onDismissRequest = { showTimePickerDialog = false }) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.width(360.dp).padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = str("upload_publish_time"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Hours
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = { selectedHour = (selectedHour + 1) % 24 }) {
                                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null)
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = String.format(Locale.US, "%02d", selectedHour),
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            IconButton(onClick = { selectedHour = if (selectedHour - 1 < 0) 23 else selectedHour - 1 }) {
                                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
                            }
                        }

                        Text(
                            text = ":",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        // Minutes
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = { selectedMinute = (selectedMinute + 5) % 60 }) {
                                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null)
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = String.format(Locale.US, "%02d", selectedMinute),
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            IconButton(onClick = { selectedMinute = if (selectedMinute - 5 < 0) 55 else (selectedMinute - 5) }) {
                                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { showTimePickerDialog = false },
                            shapes = ButtonDefaults.shapes()
                        ) {
                            Text(str("upload_btn_cancel"))
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val updatedCal = Calendar.getInstance().apply {
                                    timeInMillis = currentEpoch
                                    set(Calendar.HOUR_OF_DAY, selectedHour)
                                    set(Calendar.MINUTE, selectedMinute)
                                    set(Calendar.SECOND, 0)
                                }
                                viewModel.scheduledEpochMs = updatedCal.timeInMillis
                                showTimePickerDialog = false
                            },
                            shapes = ButtonDefaults.shapes()
                        ) {
                            Text(str("btn_save"))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCheckboxRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(top = 2.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------- TAB 2: METADATA & DETAILS ----------------
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MetadataTabContent(
    viewModel: UploadViewModel,
    onOpenStorefrontSheet: () -> Unit
) {
    var showReleaseDatePicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Contient de la musique
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        str("upload_field_contains_music"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = viewModel.containsMusic,
                        onCheckedChange = { viewModel.containsMusic = it }
                    )
                }
                Text(
                    str("upload_field_contains_music_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = viewModel.albumTitle,
                        onValueChange = { viewModel.albumTitle = it },
                        label = { Text(str("upload_field_album_title")) },
                        placeholder = { Text(str("upload_field_album_title_hint")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).trackTextInput(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = viewModel.releaseTitle,
                        onValueChange = { viewModel.releaseTitle = it },
                        label = { Text(str("upload_field_release_title")) },
                        placeholder = { Text(str("upload_field_release_title_hint")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).trackTextInput(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Release Date Field with DatePicker & Clear icon
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showReleaseDatePicker = true }
                ) {
                    OutlinedTextField(
                        value = viewModel.releaseDate,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(str("upload_field_release_date")) },
                        placeholder = { Text(str("upload_field_release_date_hint")) },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp).trackTextInput()
                            )
                        },
                        trailingIcon = {
                            if (viewModel.releaseDate.isNotBlank()) {
                                IconButton(onClick = { viewModel.releaseDate = "" }) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else {
                                IconButton(onClick = { showReleaseDatePicker = true }) {
                                    Icon(
                                        Icons.Rounded.Event,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledLeadingIconColor = MaterialTheme.colorScheme.primary,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        // 2. Droits d'auteur & Métadonnées
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Rounded.Business,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        str("upload_section_copyright"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = viewModel.labelName,
                        onValueChange = { viewModel.labelName = it },
                        label = { Text(str("upload_field_record_label")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).trackTextInput(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = viewModel.publisher,
                        onValueChange = { viewModel.publisher = it },
                        label = { Text(str("upload_field_publisher_meta")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).trackTextInput(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                OutlinedTextField(
                    value = viewModel.composer,
                    onValueChange = { viewModel.composer = it },
                    label = { Text(str("upload_field_composer_meta")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().trackTextInput(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = viewModel.pLine,
                        onValueChange = { viewModel.pLine = it },
                        label = { Text(str("upload_field_p_line")) },
                        placeholder = { Text(str("upload_field_p_line_hint")) },
                        supportingText = {
                            Text(
                                text = str("upload_field_p_line_info"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f).trackTextInput(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = viewModel.cLine,
                        onValueChange = { viewModel.cLine = it },
                        label = { Text(str("upload_field_c_line")) },
                        placeholder = { Text(str("upload_field_c_line_hint")) },
                        supportingText = {
                            Text(
                                text = str("upload_field_c_line_info"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f).trackTextInput(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        // 3. Codes et identifiants
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Rounded.QrCode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        str("upload_section_codes_ids"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = viewModel.isrc,
                        onValueChange = { viewModel.isrc = it },
                        label = { Text(str("upload_field_isrc")) },
                        placeholder = { Text(str("upload_field_isrc_hint")) },
                        supportingText = {
                            Text(
                                text = str("upload_field_isrc_info"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f).trackTextInput(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = viewModel.iswc,
                        onValueChange = { viewModel.iswc = it },
                        label = { Text(str("upload_field_iswc")) },
                        placeholder = { Text(str("upload_field_iswc_hint")) },
                        supportingText = {
                            Text(
                                text = str("upload_field_iswc_info"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f).trackTextInput(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                OutlinedTextField(
                    value = viewModel.upcOrEan,
                    onValueChange = { viewModel.upcOrEan = it },
                    label = { Text(str("upload_field_upc_or_ean")) },
                    placeholder = { Text(str("upload_field_upc_or_ean_hint")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().trackTextInput(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // 4. Contenu explicite & Licence
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Rounded.Explicit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        str("upload_toggle_explicit"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = viewModel.explicitContent,
                        onCheckedChange = { viewModel.explicitContent = it }
                    )
                }
                Text(
                    str("upload_field_explicit_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))
                Text(
                    str("upload_license_group_title"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    str("upload_license_info"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { viewModel.license = TrackLicense.ALL_RIGHTS_RESERVED }
                        .padding(vertical = 6.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = viewModel.license == TrackLicense.ALL_RIGHTS_RESERVED,
                        onClick = { viewModel.license = TrackLicense.ALL_RIGHTS_RESERVED }
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = str("upload_license_all_rights"),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (viewModel.license == TrackLicense.ALL_RIGHTS_RESERVED) FontWeight.Bold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (viewModel.license == TrackLicense.ALL_RIGHTS_RESERVED) {
                                viewModel.license = TrackLicense.CC_BY_NC_SA
                            }
                        }
                        .padding(vertical = 6.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = viewModel.license != TrackLicense.ALL_RIGHTS_RESERVED,
                        onClick = {
                            if (viewModel.license == TrackLicense.ALL_RIGHTS_RESERVED) {
                                viewModel.license = TrackLicense.CC_BY_NC_SA
                            }
                        }
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = str("upload_license_cc"),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (viewModel.license != TrackLicense.ALL_RIGHTS_RESERVED) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (viewModel.license != TrackLicense.ALL_RIGHTS_RESERVED) {
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ) {
                                    Text(
                                        text = viewModel.license.displayName,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = str("upload_license_cc_some_rights"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AnimatedVisibility(
                    visible = viewModel.license != TrackLicense.ALL_RIGHTS_RESERVED,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            PermissionCheckboxRow(
                                title = str("upload_license_attribution"),
                                description = str("upload_license_attribution_desc"),
                                checked = viewModel.license.isBy,
                                onCheckedChange = { isChecked ->
                                    viewModel.license = TrackLicense.fromCreativeCommons(
                                        by = isChecked,
                                        nc = viewModel.license.isNc,
                                        nd = viewModel.license.isNd,
                                        sa = viewModel.license.isSa
                                    )
                                }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                            PermissionCheckboxRow(
                                title = str("upload_license_noncommercial"),
                                description = str("upload_license_noncommercial_desc"),
                                checked = viewModel.license.isNc,
                                onCheckedChange = { isChecked ->
                                    viewModel.license = TrackLicense.fromCreativeCommons(
                                        by = viewModel.license.isBy,
                                        nc = isChecked,
                                        nd = viewModel.license.isNd,
                                        sa = viewModel.license.isSa
                                    )
                                }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                            PermissionCheckboxRow(
                                title = str("upload_license_no_derivatives"),
                                description = str("upload_license_no_derivatives_desc"),
                                checked = viewModel.license.isNd,
                                onCheckedChange = { isChecked ->
                                    viewModel.license = TrackLicense.fromCreativeCommons(
                                        by = viewModel.license.isBy,
                                        nc = viewModel.license.isNc,
                                        nd = isChecked,
                                        sa = if (isChecked) false else viewModel.license.isSa
                                    )
                                }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                            PermissionCheckboxRow(
                                title = str("upload_license_share_alike"),
                                description = str("upload_license_share_alike_desc"),
                                checked = viewModel.license.isSa,
                                onCheckedChange = { isChecked ->
                                    viewModel.license = TrackLicense.fromCreativeCommons(
                                        by = viewModel.license.isBy,
                                        nc = viewModel.license.isNc,
                                        nd = if (isChecked) false else viewModel.license.isNd,
                                        sa = isChecked
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // 5. Commerce & Vitrine de vente
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Rounded.ShoppingCart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        str("upload_section_commerce_title"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                ExpressiveConnectedButtonGroup<CommerceOption>(
                    options = listOf(CommerceOption.BUY_LINK, CommerceOption.STOREFRONT),
                    selectedOption = viewModel.selectedCommerceOption,
                    onOptionSelected = { option: CommerceOption ->
                        viewModel.selectedCommerceOption = option
                        if (option == CommerceOption.STOREFRONT) {
                            viewModel.hasStorefront = true
                        }
                    },
                    fillMaxWidth = true,
                    labelProvider = { option: CommerceOption ->
                        Text(
                            text = when (option) {
                                CommerceOption.BUY_LINK -> str("upload_commerce_choice_buy_link")
                                CommerceOption.STOREFRONT -> str("upload_commerce_choice_storefront")
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (viewModel.selectedCommerceOption == option) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    iconProvider = { option: CommerceOption ->
                        Icon(
                            imageVector = when (option) {
                                CommerceOption.BUY_LINK -> Icons.Rounded.ShoppingCart
                                CommerceOption.STOREFRONT -> Icons.Rounded.Storefront
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )

                if (viewModel.selectedCommerceOption == CommerceOption.BUY_LINK) {
                    OutlinedTextField(
                        value = viewModel.purchaseTitle,
                        onValueChange = { viewModel.purchaseTitle = it },
                        label = { Text(str("upload_field_purchase_title")) },
                        placeholder = { Text(str("upload_field_purchase_title_hint")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().trackTextInput(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = viewModel.purchaseUrl,
                        onValueChange = { viewModel.purchaseUrl = it },
                        label = { Text(str("upload_field_purchase_url")) },
                        placeholder = { Text(str("upload_field_url_hint")) },
                        singleLine = true,
                        isError = !viewModel.isPurchaseUrlValid,
                        supportingText = {
                            if (!viewModel.isPurchaseUrlValid) {
                                Text(str("upload_field_url_invalid"), color = MaterialTheme.colorScheme.error)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().trackTextInput(),
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    StorefrontSectionContent(
                        viewModel = viewModel,
                        onOpenStorefrontSheet = onOpenStorefrontSheet
                    )
                }
            }
        }
    }

    if (showReleaseDatePicker) {
        val initialDateMillis = remember(viewModel.releaseDate) {
            if (viewModel.releaseDate.isNotBlank()) {
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                    sdf.parse(viewModel.releaseDate)?.time ?: System.currentTimeMillis()
                } catch (_: Exception) {
                    System.currentTimeMillis()
                }
            } else {
                System.currentTimeMillis()
            }
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDateMillis
        )
        DatePickerDialog(
            onDismissRequest = { showReleaseDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selected ->
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                                timeZone = TimeZone.getTimeZone("UTC")
                            }
                            viewModel.releaseDate = sdf.format(Date(selected))
                        }
                        showReleaseDatePicker = false
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(str("btn_save"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showReleaseDatePicker = false },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(str("upload_btn_cancel"))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StorefrontSectionContent(
    viewModel: UploadViewModel,
    onOpenStorefrontSheet: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        Text(
            text = str("upload_storefront_banner_subtitle"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (viewModel.hasStorefront && viewModel.storefrontTitle.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val bitmap = viewModel.storefrontBitmap
                    val imgUrl = viewModel.storefrontImageUrl
                    if (bitmap != null || !imgUrl.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow),
                            contentAlignment = Alignment.Center
                        ) {
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.toComposeImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (!imgUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = imgUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ShoppingBag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = viewModel.storefrontTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = viewModel.storefrontType.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (viewModel.storefrontPrice.isNotBlank()) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = viewModel.storefrontPrice,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Button(
            onClick = onOpenStorefrontSheet,
            shapes = ButtonDefaults.shapes(),
            colors = if (viewModel.hasStorefront && viewModel.storefrontTitle.isNotBlank()) {
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            } else {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = if (viewModel.hasStorefront && viewModel.storefrontTitle.isNotBlank()) Icons.Rounded.Edit else Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = str(
                    if (viewModel.hasStorefront && viewModel.storefrontTitle.isNotBlank()) "upload_storefront_btn_edit"
                    else "upload_storefront_btn_add"
                ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ArtistStorefrontDialog(
    viewModel: UploadViewModel,
    onDismiss: () -> Unit
) {
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var tempStorefrontBitmap by remember { mutableStateOf<BufferedImage?>(null) }

    if (tempStorefrontBitmap != null) {
        TrackArtworkCropDialog(
            bitmap = tempStorefrontBitmap,
            onDismiss = { tempStorefrontBitmap = null },
            onSave = { cropped ->
                viewModel.storefrontBitmap = cropped
                viewModel.storefrontImageFile = null
                tempStorefrontBitmap = null
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(str("upload_storefront_btn_delete")) },
            text = { Text(str("upload_storefront_delete_confirmation")) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteStorefront(onSuccess = onDismiss)
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(str("btn_delete"))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = false },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(str("upload_btn_cancel"))
                }
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier
                .width(520.dp)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = str("upload_storefront_sheet_title"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = str("btn_close"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = str("upload_storefront_preview_title"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val bitmap = viewModel.storefrontBitmap
                        val imgUrl = viewModel.storefrontImageUrl

                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.toComposeImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (!imgUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = imgUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Cloud,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "· ${viewModel.storefrontType.displayName}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = viewModel.storefrontTitle.ifBlank { str("upload_storefront_field_title") },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (viewModel.storefrontPrice.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = viewModel.storefrontPrice,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }

                        IconButton(
                            onClick = {},
                            enabled = viewModel.storefrontLink.isNotBlank()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.OpenInNew,
                                contentDescription = null,
                                tint = if (viewModel.storefrontLink.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = 0.4f
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val bitmap = viewModel.storefrontBitmap
                    val imgUrl = viewModel.storefrontImageUrl

                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable {
                                openNativeImageFileChooser { file ->
                                    try {
                                        val img = ImageIO.read(file)
                                        if (img != null) {
                                            tempStorefrontBitmap = img
                                        }
                                    } catch (_: Exception) {}
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.toComposeImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else if (!imgUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = imgUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.PhotoCamera,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    OutlinedTextField(
                        value = viewModel.storefrontPrice,
                        onValueChange = {
                            if (it.length <= 15) viewModel.storefrontPrice = it
                        },
                        label = { Text(str("upload_storefront_field_price") + " *") },
                        supportingText = {
                            Text(
                                text = "${viewModel.storefrontPrice.length}/15",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth().trackTextInput()
                            )
                        },
                        singleLine = true,
                        isError = viewModel.storefrontPrice.isBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(BuyModuleType.entries) { type ->
                        val isSelected = viewModel.storefrontType == type
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.storefrontType = type },
                            label = {
                                Text(
                                    text = type.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = viewModel.storefrontTitle,
                    onValueChange = {
                        if (it.length <= 25) viewModel.storefrontTitle = it
                    },
                    label = { Text(str("upload_storefront_field_title") + " *") },
                    supportingText = {
                        Text(
                            text = "${viewModel.storefrontTitle.length}/25",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth().trackTextInput()
                        )
                    },
                    singleLine = true,
                    isError = viewModel.storefrontTitle.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = viewModel.storefrontDescription,
                    onValueChange = {
                        if (it.length <= 140) viewModel.storefrontDescription = it
                    },
                    label = { Text(str("upload_storefront_field_description")) },
                    supportingText = {
                        Text(
                            text = "${viewModel.storefrontDescription.length}/140",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth().trackTextInput()
                        )
                    },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(12.dp))
                val isLinkValid = viewModel.storefrontLink.isBlank() || UploadViewModel.isValidUrl(viewModel.storefrontLink)
                OutlinedTextField(
                    value = viewModel.storefrontLink,
                    onValueChange = { viewModel.storefrontLink = it },
                    label = { Text(str("upload_storefront_field_link") + " *") },
                    placeholder = { Text(str("upload_field_url_hint")) },
                    singleLine = true,
                    isError = viewModel.storefrontLink.isBlank() || !isLinkValid,
                    supportingText = {
                        if (viewModel.storefrontLink.isNotBlank() && !isLinkValid) {
                            Text(
                                text = str("upload_field_url_invalid"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().trackTextInput(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = viewModel.storefrontLinkTitle,
                    onValueChange = {
                        if (it.length <= 25) viewModel.storefrontLinkTitle = it
                    },
                    label = { Text(str("upload_storefront_field_button_title")) },
                    placeholder = { Text(str("upload_storefront_field_button_title_hint")) },
                    supportingText = {
                        Text(
                            text = "${viewModel.storefrontLinkTitle.length}/25",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth().trackTextInput()
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (!viewModel.storefrontErrorMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = viewModel.storefrontErrorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (viewModel.hasStorefront) {
                        OutlinedButton(
                            onClick = { showDeleteConfirmDialog = true },
                            enabled = !viewModel.isSavingStorefront && !viewModel.isDeletingStorefront,
                            shapes = ButtonDefaults.shapes(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (viewModel.isDeletingStorefront) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = str("upload_storefront_btn_delete"),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    val canSave = viewModel.storefrontTitle.isNotBlank() &&
                            viewModel.storefrontPrice.isNotBlank() &&
                            viewModel.storefrontLink.isNotBlank() &&
                            UploadViewModel.isValidUrl(viewModel.storefrontLink) &&
                            !viewModel.isSavingStorefront &&
                            !viewModel.isDeletingStorefront

                    Button(
                        onClick = {
                            if (viewModel.isEditMode) {
                                viewModel.saveStorefront(onSuccess = onDismiss)
                            } else {
                                viewModel.hasStorefront = true
                                onDismiss()
                            }
                        },
                        enabled = canSave,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(if (viewModel.hasStorefront) 1f else 2f)
                    ) {
                        if (viewModel.isSavingStorefront) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(
                                text = str("upload_storefront_btn_save"),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------- TAB 3: PERMISSIONS & GEOBLOCKING ----------------
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PermissionsTabContent(
    viewModel: UploadViewModel,
    onOpenCountryPicker: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Accès
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Rounded.VpnLock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        str("upload_perm_section_access"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(8.dp))
                PermissionCheckboxRow(
                    title = str("upload_perm_direct_downloads"),
                    description = if (viewModel.downloadable) str("upload_perm_direct_downloads_desc_on") else str("upload_perm_direct_downloads_desc_off"),
                    checked = viewModel.downloadable,
                    onCheckedChange = { viewModel.downloadable = it }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                PermissionCheckboxRow(
                    title = str("upload_perm_offline_listening"),
                    description = if (viewModel.offlineListening) str("upload_perm_offline_listening_desc_on") else str("upload_perm_offline_listening_desc_off"),
                    checked = viewModel.offlineListening,
                    onCheckedChange = { viewModel.offlineListening = it }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                PermissionCheckboxRow(
                    title = str("upload_perm_rss_feed"),
                    description = if (viewModel.feedable) str("upload_perm_rss_feed_desc_on") else str("upload_perm_rss_feed_desc_off"),
                    checked = viewModel.feedable,
                    onCheckedChange = { viewModel.feedable = it }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                PermissionCheckboxRow(
                    title = str("upload_perm_embed_code"),
                    description = if (viewModel.embeddable) str("upload_perm_embed_code_desc_on") else str("upload_perm_embed_code_desc_off"),
                    checked = viewModel.embeddable,
                    onCheckedChange = { viewModel.embeddable = it }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                PermissionCheckboxRow(
                    title = str("upload_perm_app_playback"),
                    description = if (viewModel.apiStreamable) str("upload_perm_app_playback_desc_on") else str("upload_perm_app_playback_desc_off"),
                    checked = viewModel.apiStreamable,
                    onCheckedChange = { viewModel.apiStreamable = it }
                )
            }
        }

        // 2. Mode silencieux
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Rounded.Comment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        str("upload_perm_section_quiet_mode"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(8.dp))
                PermissionCheckboxRow(
                    title = str("upload_perm_enable_comments"),
                    description = if (viewModel.commentable) str("upload_perm_enable_comments_desc_on") else str("upload_perm_enable_comments_desc_off"),
                    checked = viewModel.commentable,
                    onCheckedChange = { viewModel.commentable = it }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                PermissionCheckboxRow(
                    title = str("upload_perm_reveal_comments"),
                    description = if (viewModel.revealComments) str("upload_perm_reveal_comments_desc_on") else str("upload_perm_reveal_comments_desc_off"),
                    checked = viewModel.revealComments,
                    onCheckedChange = { viewModel.revealComments = it }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                PermissionCheckboxRow(
                    title = str("upload_perm_display_stats"),
                    description = if (viewModel.revealStats) str("upload_perm_display_stats_desc_on") else str("upload_perm_display_stats_desc_off"),
                    checked = viewModel.revealStats,
                    onCheckedChange = { viewModel.revealStats = it }
                )
            }
        }

        // 3. Géoblocage
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Rounded.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        str("upload_perm_geo_title"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    str("upload_perm_geo_subtitle"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))

                ExpressiveConnectedButtonGroup<GeoBlockingMode>(
                    options = listOf(GeoBlockingMode.EVERYWHERE, GeoBlockingMode.EXCLUSIVE, GeoBlockingMode.BLOCKED),
                    selectedOption = viewModel.geoBlockingMode,
                    onOptionSelected = { mode: GeoBlockingMode -> viewModel.geoBlockingMode = mode },
                    fillMaxWidth = true,
                    labelProvider = { mode: GeoBlockingMode ->
                        Text(
                            text = when (mode) {
                                GeoBlockingMode.EVERYWHERE -> str("upload_perm_geo_mode_everywhere")
                                GeoBlockingMode.EXCLUSIVE -> str("upload_perm_geo_mode_exclusive")
                                GeoBlockingMode.BLOCKED -> str("upload_perm_geo_mode_blocked")
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (viewModel.geoBlockingMode == mode) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    iconProvider = { mode: GeoBlockingMode ->
                        Icon(
                            imageVector = when (mode) {
                                GeoBlockingMode.EVERYWHERE -> Icons.Rounded.Public
                                GeoBlockingMode.EXCLUSIVE -> Icons.Rounded.CheckCircleOutline
                                GeoBlockingMode.BLOCKED -> Icons.Rounded.Block
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )

                if (viewModel.geoBlockingMode != GeoBlockingMode.EVERYWHERE) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = viewModel.geoBlockingRegions,
                        onValueChange = { viewModel.geoBlockingRegions = it },
                        label = {
                            Text(
                                if (viewModel.geoBlockingMode == GeoBlockingMode.EXCLUSIVE)
                                    str("upload_perm_geo_field_exclusive_label")
                                else
                                    str("upload_perm_geo_field_blocked_label")
                            )
                        },
                        placeholder = { Text("FR, US, DE, GB") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().trackTextInput(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(Modifier.height(10.dp))

                    Button(
                        onClick = onOpenCountryPicker,
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Icon(Icons.Rounded.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(str("upload_perm_geo_suggestions_label"))
                    }
                }
            }
        }
    }
}

// ---------------- TAB 4: ADVANCED & SNIPPET ----------------
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AdvancedTabContent(
    viewModel: UploadViewModel,
    modifier: Modifier = Modifier
) {
    val hasAudio = viewModel.selectedFile != null || viewModel.isEditMode
    val snippetDuration = 20
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val surfaceContainerLow = MaterialTheme.colorScheme.surfaceContainer
    val surfaceContainerLowest = MaterialTheme.colorScheme.surfaceContainerLowest
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    var waveformSamples by remember { mutableStateOf<FloatArray?>(null) }
    var waveformLoading by remember { mutableStateOf(false) }

    val waveformUrl = viewModel.waveformUrl
    LaunchedEffect(waveformUrl) {
        if (waveformUrl.isNullOrBlank()) return@LaunchedEffect
        waveformLoading = true
        waveformSamples = null
        withContext(Dispatchers.IO) {
            try {
                val client = com.alananasss.kittytune.data.network.ProxyManager.getOkHttpClient()
                val req = Request.Builder().url(waveformUrl).build()
                val body = client.newCall(req).execute().use { resp ->
                    resp.body?.string()
                } ?: return@withContext
                val json = JSONObject(body)
                val height = json.optDouble("height", 140.0)
                val samplesArr = json.getJSONArray("samples")
                val count = samplesArr.length()
                if (count == 0) return@withContext
                val result = FloatArray(count) { i ->
                    val s = samplesArr.getDouble(i)
                    Math.pow((s / height).coerceIn(0.0, 1.0), 1.5).toFloat().coerceIn(0.02f, 1f)
                }
                waveformSamples = result
            } catch (_: Exception) {
            }
        }
        waveformLoading = false
    }

    val fallbackBars = remember {
        val rng = java.util.Random(42L)
        val arr = FloatArray(120)
        for (i in arr.indices) {
            val base = (Math.sin(i * 0.15) * 0.35 + 0.5).toFloat()
            val noise = (rng.nextFloat() - 0.5f) * 0.3f
            arr[i] = (base + noise).coerceIn(0.08f, 0.95f)
        }
        arr
    }

    val audioEngine = remember { AudioEngine() }
    var isPlaying by remember { mutableStateOf(false) }
    var isLoadingAudio by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var playJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(audioEngine) {
        audioEngine.onPlayingChanged = { playing ->
            isPlaying = playing
            if (playing) {
                isLoadingAudio = false
            }
        }
        audioEngine.onCompletion = {
            isPlaying = false
            isLoadingAudio = false
        }
        audioEngine.onError = {
            isPlaying = false
            isLoadingAudio = false
        }
        onDispose {
            playJob?.cancel()
            try {
                audioEngine.stop()
                audioEngine.release()
            } catch (_: Throwable) {
            }
        }
    }

    fun stopAudioPreview() {
        playJob?.cancel()
        playJob = null
        try {
            audioEngine.stop()
        } catch (_: Throwable) {
        }
        isPlaying = false
        isLoadingAudio = false
    }

    fun startAudioPreview() {
        stopAudioPreview()
        isLoadingAudio = true
        playJob = scope.launch {
            try {
                val filePathOrUrl: String? = when {
                    viewModel.selectedFile != null -> viewModel.selectedFile?.absolutePath
                    viewModel.isEditMode -> {
                        val cleanId = viewModel.editingTrackUrn?.substringAfterLast(":")
                        if (cleanId.isNullOrBlank()) null
                        else {
                            withContext(Dispatchers.IO) {
                                val trackId = cleanId.toLongOrNull() ?: 0L
                                val track = Track(
                                    id = trackId,
                                    title = viewModel.title,
                                    artworkUrl = null,
                                    durationMs = viewModel.trackDurationSeconds.toLong() * 1000L,
                                    user = null
                                )
                                StreamResolver.resolveStream(track)
                            }
                        }
                    }
                    else -> null
                }

                if (filePathOrUrl == null) {
                    isLoadingAudio = false
                    return@launch
                }

                val startMs = (viewModel.snippetStartSeconds * 1000L).coerceAtLeast(0L)
                audioEngine.setMediaItem(url = filePathOrUrl, startPositionMs = startMs)
                audioEngine.prepare()
                audioEngine.play()
                isLoadingAudio = false
                isPlaying = true

                delay(snippetDuration * 1000L)
                if (audioEngine.isPlaying) {
                    audioEngine.stop()
                }
                isPlaying = false
            } catch (_: Throwable) {
                isPlaying = false
            } finally {
                isLoadingAudio = false
            }
        }
    }

    if (!hasAudio) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceContainerLow),
            modifier = modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(32.dp)
            ) {
                Icon(Icons.Rounded.Audiotrack, null, tint = onSurfaceVariant, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    str("upload_advanced_snippet_no_audio"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val duration = viewModel.trackDurationSeconds.coerceAtLeast(60)
    val startFrac = (viewModel.snippetStartSeconds.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    val windowFrac = (snippetDuration.toFloat() / duration.toFloat()).coerceIn(0.01f, 1f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceContainerLow),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = primaryColor, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        str("upload_advanced_snippet_title"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.weight(1f))
                    if (waveformLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = primaryColor
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = primaryContainer
                    ) {
                        Text(
                            str("upload_advanced_snippet_current"),
                            style = MaterialTheme.typography.labelSmall,
                            color = onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                var waveformWidthPx by remember { mutableStateOf(1f) }
                val barWidthDp = 2.dp
                val spaceWidthDp = 1.dp
                var accumulatedSeconds by remember(viewModel.snippetStartSeconds) {
                    mutableStateOf(viewModel.snippetStartSeconds.toFloat())
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .pointerInput(duration) {
                            waveformWidthPx = size.width.toFloat()
                            detectDragGestures(
                                onDragStart = { offset ->
                                    if (waveformWidthPx > 0f) {
                                        val clickFraction = (offset.x / waveformWidthPx).coerceIn(0f, 1f)
                                        val clickSec = clickFraction * duration
                                        val currentStart = viewModel.snippetStartSeconds.toFloat()
                                        val currentEnd = currentStart + snippetDuration
                                        if (clickSec < currentStart || clickSec > currentEnd) {
                                            val newAccum = (clickSec - snippetDuration / 2f)
                                                .coerceIn(0f, (duration - snippetDuration).coerceAtLeast(0).toFloat())
                                            accumulatedSeconds = newAccum
                                            val newStart = newAccum.toInt()
                                            viewModel.snippetStartSeconds = newStart
                                            viewModel.snippetEndSeconds = newStart + snippetDuration
                                            viewModel.isSnippetCustomized = true
                                            if (isPlaying) stopAudioPreview()
                                        } else {
                                            accumulatedSeconds = viewModel.snippetStartSeconds.toFloat()
                                        }
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (waveformWidthPx > 0f) {
                                        val secDelta = (dragAmount.x / waveformWidthPx) * duration
                                        val newAccum = (accumulatedSeconds + secDelta)
                                            .coerceIn(0f, (duration - snippetDuration).coerceAtLeast(0).toFloat())
                                        accumulatedSeconds = newAccum
                                        val newStart = newAccum.toInt()
                                        if (newStart != viewModel.snippetStartSeconds) {
                                            viewModel.snippetStartSeconds = newStart
                                            viewModel.snippetEndSeconds = newStart + snippetDuration
                                            viewModel.isSnippetCustomized = true
                                            if (isPlaying) {
                                                stopAudioPreview()
                                            }
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    waveformWidthPx = size.width
                    val cW = size.width
                    val cH = size.height

                    val barWidthPx = barWidthDp.toPx()
                    val spaceWidthPx = spaceWidthDp.toPx()
                    val stepPx = barWidthPx + spaceWidthPx
                    val totalBars = (cW / stepPx).toInt().coerceAtLeast(10)
                    val baselineY = cH * 0.68f
                    val gapPx = 1.5.dp.toPx()
                    val raw = waveformSamples
                    val barHeights = FloatArray(totalBars) { i ->
                        if (raw != null && raw.isNotEmpty()) {
                            val startIdx = (i * raw.size) / totalBars
                            val endIdx =
                                (((i + 1) * raw.size) / totalBars).coerceAtMost(raw.size).coerceAtLeast(startIdx + 1)
                            var maxV = 0f
                            for (idx in startIdx until endIdx) {
                                if (raw[idx] > maxV) maxV = raw[idx]
                            }
                            maxV.coerceIn(0.04f, 1f)
                        } else {
                            fallbackBars[i % fallbackBars.size]
                        }
                    }

                    val winL = startFrac * cW
                    val winR = (startFrac + windowFrac).coerceAtMost(1f) * cW
                    for (i in 0 until totalBars) {
                        val x = i * stepPx
                        val h = barHeights[i]

                        val topBarH = (baselineY * h * 0.95f).coerceAtLeast(2f)
                        val bottomBarH = ((cH - baselineY - gapPx) * h * 0.70f).coerceAtLeast(1.5f)

                        val inWindow = (x + barWidthPx >= winL && x <= winR)

                        val topColor = if (inWindow) primaryColor else onSurfaceVariant.copy(alpha = 0.35f)
                        val bottomColor =
                            if (inWindow) primaryColor.copy(alpha = 0.60f) else onSurfaceVariant.copy(alpha = 0.16f)
                        drawRoundRect(
                            color = topColor,
                            topLeft = Offset(x, baselineY - topBarH),
                            size = Size(barWidthPx, topBarH),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidthPx / 2f)
                        )
                        drawRoundRect(
                            color = bottomColor,
                            topLeft = Offset(x, baselineY + gapPx),
                            size = Size(barWidthPx, bottomBarH),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidthPx / 2f)
                        )
                    }
                    val frameWidth = (winR - winL).coerceAtLeast(10f)
                    drawRoundRect(
                        color = primaryContainer.copy(alpha = 0.20f),
                        topLeft = Offset(winL, 0f),
                        size = Size(frameWidth, cH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                    )
                    drawRoundRect(
                        color = primaryColor,
                        topLeft = Offset(winL, 0f),
                        size = Size(frameWidth, cH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                    val hw = 8.dp.toPx()
                    val midY = cH / 2f
                    val strokeW = 2.5.dp.toPx()
                    drawLine(
                        primaryColor,
                        Offset(winL + hw, midY - 10.dp.toPx()),
                        Offset(winL + 2.dp.toPx(), midY),
                        strokeW,
                        StrokeCap.Round
                    )
                    drawLine(
                        primaryColor,
                        Offset(winL + 2.dp.toPx(), midY),
                        Offset(winL + hw, midY + 10.dp.toPx()),
                        strokeW,
                        StrokeCap.Round
                    )
                    drawLine(
                        primaryColor,
                        Offset(winR - hw, midY - 10.dp.toPx()),
                        Offset(winR - 2.dp.toPx(), midY),
                        strokeW,
                        StrokeCap.Round
                    )
                    drawLine(
                        primaryColor,
                        Offset(winR - 2.dp.toPx(), midY),
                        Offset(winR - hw, midY + 10.dp.toPx()),
                        strokeW,
                        StrokeCap.Round
                    )
                }

                Spacer(Modifier.height(10.dp))
                val s = viewModel.snippetStartSeconds
                val e = viewModel.snippetEndSeconds
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "%d:%02d".format(s / 60, s % 60),
                        style = MaterialTheme.typography.labelLarge,
                        color = primaryColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = str("upload_advanced_snippet_current"),
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "%d:%02d".format(e / 60, e % 60),
                        style = MaterialTheme.typography.labelLarge,
                        color = primaryColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalButton(
                        onClick = {
                            if (isPlaying) {
                                stopAudioPreview()
                            } else {
                                startAudioPreview()
                            }
                        },
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isPlaying) primaryColor else primaryContainer,
                            contentColor = if (isPlaying) onPrimaryColor else onPrimaryContainer
                        )
                    ) {
                        if (isLoadingAudio) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = if (isPlaying) onPrimaryColor else onPrimaryContainer
                            )
                        } else {
                            Icon(
                                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isPlaying) str("upload_advanced_snippet_pause") else str("upload_advanced_snippet_play"),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val newStart = (viewModel.snippetStartSeconds - 5).coerceAtLeast(0)
                            viewModel.snippetStartSeconds = newStart
                            viewModel.snippetEndSeconds = newStart + snippetDuration
                            viewModel.isSnippetCustomized = true
                            if (isPlaying) stopAudioPreview()
                        },
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Text("- 5s")
                    }

                    OutlinedButton(
                        onClick = {
                            val maxStart = (duration - snippetDuration).coerceAtLeast(0)
                            val newStart = (viewModel.snippetStartSeconds + 5).coerceAtMost(maxStart)
                            viewModel.snippetStartSeconds = newStart
                            viewModel.snippetEndSeconds = newStart + snippetDuration
                            viewModel.isSnippetCustomized = true
                            if (isPlaying) stopAudioPreview()
                        },
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Text("+ 5s")
                    }

                    Spacer(Modifier.weight(1f))

                    if (viewModel.isSnippetCustomized) {
                        TextButton(
                            onClick = {
                                viewModel.snippetStartSeconds = 0
                                viewModel.snippetEndSeconds = snippetDuration
                                viewModel.isSnippetCustomized = false
                                if (isPlaying) stopAudioPreview()
                            },
                            shapes = ButtonDefaults.shapes()
                        ) {
                            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                str("upload_advanced_snippet_reset"),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceContainerLowest),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Info, null, tint = primaryColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    str("upload_advanced_snippet_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

// ---------------- DIALOGS & OVERLAYS ----------------
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LicenseSelectorDialog(
    currentLicense: TrackLicense,
    onLicenseSelected: (TrackLicense) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.width(460.dp).padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = str("upload_field_license"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))

                TrackLicense.entries.forEach { lic ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onLicenseSelected(lic) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(lic.displayName, style = MaterialTheme.typography.bodyLarge)
                        if (lic == currentLicense) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                        Text(str("upload_btn_cancel"))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CountryPickerDialog(
    mode: GeoBlockingMode,
    regions: String,
    onToggleCountry: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val commonCountries = listOf(
        "FR" to "France",
        "US" to "United States",
        "DE" to "Germany",
        "GB" to "United Kingdom",
        "JP" to "Japan",
        "CA" to "Canada",
        "BE" to "Belgium",
        "CH" to "Switzerland",
        "ES" to "Spain",
        "IT" to "Italy",
        "RU" to "Russia",
        "BR" to "Brazil",
        "AU" to "Australia",
        "NL" to "Netherlands",
        "SE" to "Sweden",
        "NO" to "Norway",
        "FI" to "Finland",
        "DK" to "Denmark",
        "PL" to "Poland",
        "MX" to "Mexico",
        "KR" to "South Korea",
        "CN" to "China"
    )

    val activeList = regions.split(",").map { it.trim().uppercase() }.filter { it.isNotBlank() }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.width(480.dp).padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = str("upload_perm_geo_suggestions_label"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))

                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(commonCountries) { (code, name) ->
                        val isSelected = activeList.contains(code)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onToggleCountry(code) }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("$name ($code)", style = MaterialTheme.typography.bodyLarge)
                            Checkbox(checked = isSelected, onCheckedChange = { onToggleCountry(code) })
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                        Text(str("btn_save"))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScheduleReleaseDialog(
    currentDateMs: Long?,
    currentTimezone: String,
    onConfirm: (Long, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedEpochMs by remember { mutableStateOf(currentDateMs ?: (System.currentTimeMillis() + 86400000L)) }
    var selectedTz by remember { mutableStateOf(currentTimezone) }

    val formattedDate = remember(selectedEpochMs) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        sdf.format(Date(selectedEpochMs))
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.width(460.dp).padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = str("upload_schedule_date_picker_title"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = str("upload_schedule_free_notice"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                OutlinedCard(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Release Date & Time (UTC/Local)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { selectedEpochMs += 86400000L },
                                shapes = ButtonDefaults.shapes()
                            ) {
                                Text(str("upload_schedule_plus_day"))
                            }
                            OutlinedButton(
                                onClick = { selectedEpochMs += 86400000L * 7 },
                                shapes = ButtonDefaults.shapes()
                            ) {
                                Text(str("upload_schedule_plus_week"))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                        Text(str("upload_btn_cancel"))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onConfirm(selectedEpochMs, selectedTz)
                        },
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Text(str("btn_save"))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeleteTrackConfirmationDialog(
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = { if (!isDeleting) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.width(440.dp).padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = str("edit_track_delete_dialog_title"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = str("edit_track_delete_dialog_message"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isDeleting,
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Text(str("upload_btn_cancel"))
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = onConfirm,
                        enabled = !isDeleting,
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        if (isDeleting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onError
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(str("edit_track_delete_button"), color = MaterialTheme.colorScheme.onError)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DiscardChangesDialog(
    onConfirmDiscard: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.width(420.dp).padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Discard unsaved changes?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "You have modified track details. If you leave now, your changes will be discarded.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                        Text(str("upload_btn_cancel"))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onConfirmDiscard,
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(str("btn_discard"))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UploadProgressOverlay(
    state: UploadState,
    s3Progress: Float,
    hasArtwork: Boolean,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onUploadAnother: () -> Unit,
    onRetry: () -> Unit
) {
    Dialog(
        onDismissRequest = {
            if (state is UploadState.Success) onDismiss()
            else if (state is UploadState.Error) onCancel()
        }
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.width(460.dp).padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (state) {
                    is UploadState.Uploading -> {
                        val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = state.progress.coerceIn(0f, 1f),
                            animationSpec = androidx.compose.animation.core.tween(300),
                            label = "upload_progress"
                        )

                        val steps = remember(hasArtwork) {
                            buildList {
                                add(UploadStep.FETCHING_POLICY)
                                add(UploadStep.UPLOADING_FILE)
                                add(UploadStep.CREATING_TRACK)
                                add(UploadStep.TRANSCODING)
                                if (hasArtwork) {
                                    add(UploadStep.UPLOADING_ARTWORK)
                                }
                            }
                        }
                        val currentStepIndex = steps.indexOf(state.step).let { if (it == -1) steps.size else it }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(110.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.size(110.dp),
                                strokeWidth = 7.dp,
                                strokeCap = StrokeCap.Round,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${(animatedProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(Modifier.height(24.dp))

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                steps.forEachIndexed { index, itemStep ->
                                    val isDone = index < currentStepIndex || state.step == UploadStep.DONE
                                    val isCurrent = index == currentStepIndex && state.step != UploadStep.DONE

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            when {
                                                isDone -> {
                                                    Icon(
                                                        imageVector = Icons.Rounded.CheckCircle,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                                isCurrent -> {
                                                    CircularProgressIndicator(
                                                        strokeWidth = 2.5.dp,
                                                        modifier = Modifier.size(18.dp),
                                                        color = MaterialTheme.colorScheme.primary,
                                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                                    )
                                                }
                                                else -> {
                                                    Icon(
                                                        imageVector = Icons.Rounded.RadioButtonUnchecked,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.outlineVariant,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }

                                        val labelText = if (itemStep == UploadStep.UPLOADING_FILE && isCurrent) {
                                            "${str(itemStep.labelKey)} (${(s3Progress * 100).toInt()}%)"
                                        } else {
                                            str(itemStep.labelKey)
                                        }

                                        Text(
                                            text = labelText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isCurrent) FontWeight.Bold else if (isDone) FontWeight.Medium else FontWeight.Normal,
                                            color = when {
                                                isCurrent -> MaterialTheme.colorScheme.onSurface
                                                isDone -> MaterialTheme.colorScheme.onSurfaceVariant
                                                else -> MaterialTheme.colorScheme.outline
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        OutlinedButton(
                            onClick = onCancel,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.fillMaxWidth(0.6f)
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(str("upload_btn_cancel"))
                        }
                    }

                    is UploadState.Success -> {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(96.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = str("upload_success_title"),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = String.format(str("upload_success_message"), state.trackTitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(28.dp))

                        Button(
                            onClick = onDismiss,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(str("upload_btn_back_to_profile"), fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(Modifier.height(10.dp))

                        FilledTonalButton(
                            onClick = onUploadAnother,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(str("upload_btn_upload_another"))
                        }
                    }

                    is UploadState.Error -> {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.size(96.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(18.dp))
                        Text(
                            text = str("upload_error_title"),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        val errorText = if (state.formatArg != null) {
                            try {
                                String.format(str(state.messageKey), state.formatArg)
                            } catch (_: Exception) {
                                "${str(state.messageKey)}: ${state.formatArg}"
                            }
                        } else {
                            str(state.messageKey)
                        }
                        Text(
                            text = errorText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = onCancel,
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(str("upload_btn_cancel"))
                            }
                            Button(
                                onClick = onRetry,
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(str("upload_btn_retry"))
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoginRequiredScreen(
    onBackClick: () -> Unit,
    onLoginClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.width(480.dp).padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = str("upload_login_required_title"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = str("upload_login_required_subtitle"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onBackClick, shapes = ButtonDefaults.shapes()) {
                        Text(str("upload_btn_cancel"))
                    }
                    Button(onClick = onLoginClick, shapes = ButtonDefaults.shapes()) {
                        Text(str("upload_login_btn"))
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotBlank()) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ---------------- FILE CHOOSER HELPERS ----------------
private fun openNativeAudioFileChooser(onFileSelected: (File) -> Unit) {
    try {
        val fileDialog = FileDialog(null as Frame?, str("upload_choose_audio"), FileDialog.LOAD)
        fileDialog.setFilenameFilter { _, name ->
            val ext = name.substringAfterLast(".", "").lowercase()
            ext in listOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "aiff")
        }
        fileDialog.isVisible = true
        val file = fileDialog.file
        val dir = fileDialog.directory
        if (file != null && dir != null) {
            onFileSelected(File(dir, file))
        }
    } catch (e: Exception) {
        val chooser = JFileChooser()
        chooser.dialogTitle = str("upload_choose_audio")
        chooser.fileFilter = FileNameExtensionFilter("Audio Files (*.mp3, *.wav, *.flac, *.aac, *.ogg, *.m4a, *.aiff)", "mp3", "wav", "flac", "aac", "ogg", "m4a", "aiff")
        val result = chooser.showOpenDialog(null)
        if (result == JFileChooser.APPROVE_OPTION && chooser.selectedFile != null) {
            onFileSelected(chooser.selectedFile)
        }
    }
}

private fun openNativeImageFileChooser(onFileSelected: (File) -> Unit) {
    try {
        val fileDialog = FileDialog(null as Frame?, str("upload_choose_artwork"), FileDialog.LOAD)
        fileDialog.setFilenameFilter { _, name ->
            val ext = name.substringAfterLast(".", "").lowercase()
            ext in listOf("jpg", "jpeg", "png", "webp")
        }
        fileDialog.isVisible = true
        val file = fileDialog.file
        val dir = fileDialog.directory
        if (file != null && dir != null) {
            onFileSelected(File(dir, file))
        }
    } catch (e: Exception) {
        val chooser = JFileChooser()
        chooser.dialogTitle = str("upload_choose_artwork")
        chooser.fileFilter = FileNameExtensionFilter("Image Files (*.jpg, *.jpeg, *.png, *.webp)", "jpg", "jpeg", "png", "webp")
        val result = chooser.showOpenDialog(null)
        if (result == JFileChooser.APPROVE_OPTION && chooser.selectedFile != null) {
            onFileSelected(chooser.selectedFile)
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun formatSeconds(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.US, "%02d:%02d", m, s)
}

// ---------------- DRAG & DROP LINUX / MACOS / WINDOWS HELPERS ----------------
private fun isDragAcceptable(flavors: Array<DataFlavor>?): Boolean {
    if (flavors == null) return false
    return flavors.any { flavor ->
        flavor == DataFlavor.javaFileListFlavor ||
        flavor.mimeType.contains("text/uri-list", ignoreCase = true) ||
        flavor.mimeType.contains("application/x-java-file-list", ignoreCase = true) ||
        flavor.isMimeTypeEqual(DataFlavor.stringFlavor)
    }
}

private fun extractFilesFromTransferable(transferable: java.awt.datatransfer.Transferable): List<File> {
    val result = mutableListOf<File>()

    // 1. javaFileListFlavor
    try {
        if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            val list = transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>
            list?.filterIsInstance<File>()?.let { result.addAll(it) }
            if (result.isNotEmpty()) return result
        }
    } catch (_: Exception) {}

    // 2. text/uri-list (Linux KDE Plasma / Dolphin / GNOME Nautilus)
    try {
        val uriFlavor = DataFlavor("text/uri-list;class=java.lang.String")
        if (transferable.isDataFlavorSupported(uriFlavor)) {
            val raw = transferable.getTransferData(uriFlavor) as? String
            if (!raw.isNullOrBlank()) {
                raw.lines().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                        try {
                            val uri = java.net.URI(trimmed)
                            val f = File(uri)
                            if (f.exists()) result.add(f)
                        } catch (_: Exception) {
                            val path = if (trimmed.startsWith("file://")) trimmed.removePrefix("file://") else trimmed
                            val f = File(path)
                            if (f.exists()) result.add(f)
                        }
                    }
                }
            }
            if (result.isNotEmpty()) return result
        }
    } catch (_: Exception) {}

    // 3. String flavor fallback
    try {
        if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            val raw = transferable.getTransferData(DataFlavor.stringFlavor) as? String
            if (!raw.isNullOrBlank()) {
                raw.lines().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                        try {
                            val uri = java.net.URI(trimmed)
                            val f = File(uri)
                            if (f.exists()) result.add(f)
                        } catch (_: Exception) {
                            val path = if (trimmed.startsWith("file://")) trimmed.removePrefix("file://") else trimmed
                            val f = File(path)
                            if (f.exists()) result.add(f)
                        }
                    }
                }
            }
        }
    } catch (_: Exception) {}

    return result
}

private fun attachDropTargetRecursively(
    component: java.awt.Component,
    listener: DropTargetListener,
    cleanupList: MutableList<Pair<java.awt.Component, DropTarget?>>
) {
    cleanupList.add(component to component.dropTarget)
    component.dropTarget = DropTarget(component, DnDConstants.ACTION_COPY_OR_MOVE, listener, true)
    if (component is java.awt.Container) {
        for (child in component.components) {
            attachDropTargetRecursively(child, listener, cleanupList)
        }
    }
}
