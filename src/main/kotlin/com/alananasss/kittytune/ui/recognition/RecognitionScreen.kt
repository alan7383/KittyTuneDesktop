@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alananasss.kittytune.ui.recognition

import androidx.compose.material3.IconButtonDefaults

import androidx.compose.material3.ButtonDefaults

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alananasss.kittytune.core.str
import coil3.compose.AsyncImage
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.music.recognition.RecognitionViewModel
import com.alananasss.kittytune.music.recognition.RecognitionState
import com.alananasss.kittytune.data.LikeRepository
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History

@Composable
fun RecognitionScreen(
    onBackClick: () -> Unit,
    playerViewModel: PlayerViewModel,
    onNavigate: (String) -> Unit
) {
    val viewModel: RecognitionViewModel = androidx.lifecycle.viewmodel.compose.viewModel { RecognitionViewModel() }
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Android parity: Recording + Processing are one "searching" UI state.
    // There is NO separate analyzing screen, matching the official Pixel app.
    val isSearching = state is RecognitionState.Recording || state is RecognitionState.Processing
    val isErrorOrSuccess = state is RecognitionState.Error || state is RecognitionState.Success
    // Matches Android: idle = secondaryContainer, searching = primaryContainer, result = surface
    val bgColor = when {
        isErrorOrSuccess -> MaterialTheme.colorScheme.surface
        isSearching -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val animatedBgColor by animateColorAsState(
        targetValue = bgColor,
        animationSpec = tween(1000),
        label = "bg_color"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(animatedBgColor)
    ) {
        AnimatedVisibility(
            visible = !isErrorOrSuccess,
            enter = fadeIn(tween(800)),
            exit = fadeOut(tween(500))
        ) {
            BlobBackgroundView(
                modifier = Modifier.fillMaxSize(),
                active = isSearching,
                primary = MaterialTheme.colorScheme.primary,
                secondary = MaterialTheme.colorScheme.secondary,
                tertiary = MaterialTheme.colorScheme.tertiary
            )
        }

        AnimatedVisibility(
            visible = !isErrorOrSuccess,
            enter = fadeIn(tween(800)),
            exit = fadeOut(tween(500)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            GlowView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }

        FilledTonalIconButton(
            onClick = {
                if (isSearching) {
                    viewModel.cancelRecognition()
                } else {
                    onBackClick()
                }
            },
            shapes = IconButtonDefaults.shapes(),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = str("btn_back")
            )
        }

        FilledTonalIconButton(
            shapes = IconButtonDefaults.shapes(),
            onClick = { onNavigate("recognition_history") },

            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp)
        ) {
            Icon(
                Icons.Rounded.History,
                contentDescription = "Historique"
            )
        }

        val currentPage = when (val s = state) {
            is RecognitionState.Idle, is RecognitionState.Recording, is RecognitionState.Processing -> RecognitionUiPage.Main
            is RecognitionState.Success -> RecognitionUiPage.Success(s)
            is RecognitionState.Error -> RecognitionUiPage.Error(s.message)
        }

        AnimatedContent(
            targetState = currentPage,
            transitionSpec = {
                (fadeIn(animationSpec = tween(400)) +
                        slideInVertically(animationSpec = tween(400), initialOffsetY = { it / 16 })) togetherWith
                (fadeOut(animationSpec = tween(300)) +
                        slideOutVertically(animationSpec = tween(300), targetOffsetY = { -it / 16 }))
            },
            label = "state_content",
            modifier = Modifier.fillMaxSize()
        ) { page ->
            Box(modifier = Modifier.fillMaxSize()) {
                when (page) {
                    is RecognitionUiPage.Main -> RecognitionHomeView(
                        isSearching = isSearching,
                        viewModel = viewModel,
                        onTap = { viewModel.startRecognition() },
                        onCancel = { viewModel.cancelRecognition() }
                    )
                    is RecognitionUiPage.Success -> SuccessView(
                        state = page.state,
                        onPlayClick = {
                            page.state.soundcloudTrack?.let { track ->
                                playerViewModel.playPlaylist(listOf(track), 0)
                                onBackClick()
                            }
                        },
                        onRetry = { viewModel.startRecognition() }
                    )
                    is RecognitionUiPage.Error -> ErrorView(
                        error = page.message,
                        onRetry = { viewModel.startRecognition() }
                    )
                }
            }
        }
    }
}

private sealed class RecognitionUiPage {
    object Main : RecognitionUiPage()
    data class Success(val state: RecognitionState.Success) : RecognitionUiPage()
    data class Error(val message: String) : RecognitionUiPage()
}

@Composable
private fun RecognitionHomeView(
    isSearching: Boolean,
    viewModel: RecognitionViewModel,
    onTap: () -> Unit,
    onCancel: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val btnScale by animateFloatAsState(targetValue = if (isPressed && !isSearching) 0.92f else 1f, label = "scale")

    val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()
    val isDesktopAudio = selectedDevice?.isDesktopAudio == true

    val buttonColor = if (isSearching)
        MaterialTheme.colorScheme.onPrimaryContainer  // dark blob on primaryContainer bg
    else
        MaterialTheme.colorScheme.secondary           // circle on secondaryContainer bg
    val iconTint = if (isSearching)
        MaterialTheme.colorScheme.primaryContainer    // light icon on dark blob
    else
        MaterialTheme.colorScheme.onSecondary         // icon on secondary circle

    val labelColor by animateColorAsState(
        targetValue = if (isSearching)
            MaterialTheme.colorScheme.onPrimaryContainer
        else
            MaterialTheme.colorScheme.onSecondaryContainer,
        animationSpec = tween(300),
        label = "label_color"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Spacer(modifier = Modifier.weight(1f))

        NowPlayingListenButton(
            active = isSearching,
            color = buttonColor,
            haloColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .size(180.dp)
                .scale(btnScale)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        if (isSearching) {
                            onCancel()
                        } else {
                            onTap()
                        }
                    }
                ),
        ) {
            // Button src = avd_nowplaying_searching (animated 3-bar icon) when searching,
            // or the music note when idle.
            if (isSearching) {
                SearchingBarsIcon(
                    color = iconTint,
                    modifier = Modifier.size(56.dp)
                )
            } else {
                Icon(
                    imageVector = NowPlayingNote,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = iconTint
                )
            }
        }

        // Official Pixel Now Playing label animation (gow.java lines 1331-1336 & 1580-1586):
        // HomeLabelTopPadding oscillates between -31dp (idle) and +21dp (searching), sliding 52dp down
        // over 1000ms with CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f) as the button blooms.
        val homeLabelTopPadding by animateDpAsState(
            targetValue = if (isSearching) 21.dp else (-31).dp,
            animationSpec = tween(1000, easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)),
            label = "HomeLabelTopPadding"
        )

        Spacer(Modifier.height(67.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset { IntOffset(x = 0, y = homeLabelTopPadding.roundToPx()) }
        ) {
            // Title: fades smoothly matching official 300ms transition (ggp.java case 13)
            val titleText = if (isSearching) {
                str("recognition_listening")
            } else if (isDesktopAudio) {
                str("recognition_tap_to_identify_device")
            } else {
                str("recognition_tap_to_identify")
            }
            AnimatedContent(
                targetState = titleText,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "title_crossfade"
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleLarge,
                    color = labelColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            // Subtitle: fades smoothly via alpha so layout height remains stable with zero reflow
            val subtitleAlpha by animateFloatAsState(
                targetValue = if (isSearching) 0f else 1f,
                animationSpec = tween(250),
                label = "subtitle_alpha"
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (isDesktopAudio) str("recognition_listening_device_desc") else str("recognition_listening_desc"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f * subtitleAlpha),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .graphicsLayer { alpha = subtitleAlpha }
            )
        }

        // Source selector right under the text: fades out and slightly slides down via graphicsLayer,
        // preserving its layout height so the Column NEVER collapses and the top items NEVER jump!
        val controlsAlpha by animateFloatAsState(
            targetValue = if (isSearching) 0f else 1f,
            animationSpec = tween(250, easing = FastOutSlowInEasing),
            label = "controls_alpha"
        )
        val controlsSlideY by animateFloatAsState(
            targetValue = if (isSearching) 24f else 0f,
            animationSpec = tween(250, easing = FastOutSlowInEasing),
            label = "controls_slide"
        )

        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = controlsAlpha
                    translationY = controlsSlideY
                }
        ) {
            AudioDeviceSelector(
                viewModel = viewModel,
                enabled = !isSearching,
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

private enum class AudioSourceCategory { MIC, DESKTOP }

/** Height kept for the device picker under the source switch. */
private val DEVICE_CHIP_SLOT = 40.dp

@Composable
private fun AudioDeviceSelector(
    viewModel: RecognitionViewModel,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val availableDevices by viewModel.availableDevices.collectAsStateWithLifecycle()
    val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()

    if (availableDevices.isEmpty()) return

    val micDevices = remember(availableDevices) { availableDevices.filter { !it.isDesktopAudio } }
    val desktopDevices = remember(availableDevices) { availableDevices.filter { it.isDesktopAudio } }
    val selectedCategory = if (selectedDevice?.isDesktopAudio == true) AudioSourceCategory.DESKTOP else AudioSourceCategory.MIC

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (micDevices.isNotEmpty() && desktopDevices.isNotEmpty()) {
            // Android parity: one full-width connected toggle (Microphone / This device),
            // then a compact picker only when the category holds several devices.
            com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup(
                options = listOf(AudioSourceCategory.MIC, AudioSourceCategory.DESKTOP),
                selectedOption = selectedCategory,
                onOptionSelected = { category ->
                    if (!enabled || category == selectedCategory) return@ExpressiveConnectedButtonGroup
                    val first = if (category == AudioSourceCategory.DESKTOP) desktopDevices.firstOrNull() else micDevices.firstOrNull()
                    if (first != null) viewModel.selectDevice(first)
                },
                fillMaxWidth = true,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                iconSpacing = 6.dp,
                checkedContainerColor = MaterialTheme.colorScheme.primary,
                uncheckedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                uncheckedContentColor = MaterialTheme.colorScheme.onSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                iconProvider = { category ->
                    Icon(
                        imageVector = when (category) {
                            AudioSourceCategory.MIC -> Icons.Rounded.Mic
                            AudioSourceCategory.DESKTOP -> Icons.Rounded.GraphicEq
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                labelProvider = { category ->
                    Text(
                        text = when (category) {
                            AudioSourceCategory.MIC -> str("recognition_source_mic")
                            AudioSourceCategory.DESKTOP -> str("recognition_source_device")
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selectedCategory == category) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )

            // The picker's room is kept whether or not the chosen kind has several devices: appearing and going
            // away, it moved the listen button up and down on every switch (issue #66).
            val categoryDevices = if (selectedCategory == AudioSourceCategory.DESKTOP) desktopDevices else micDevices
            Spacer(Modifier.height(8.dp))
            Box(Modifier.height(DEVICE_CHIP_SLOT), contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = categoryDevices.size > 1,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(150)),
                ) {
                    DeviceDropdownChip(
                        viewModel = viewModel,
                        devices = categoryDevices,
                        selectedDeviceId = selectedDevice?.id,
                        enabled = enabled
                    )
                }
            }
        } else {
            // Single category on this machine: straight to the device picker.
            DeviceDropdownChip(
                viewModel = viewModel,
                devices = availableDevices,
                selectedDeviceId = selectedDevice?.id,
                enabled = enabled
            )
        }
    }
}

@Composable
private fun DeviceDropdownChip(
    viewModel: RecognitionViewModel,
    devices: List<com.alananasss.kittytune.music.recognition.AudioInputDevice>,
    selectedDeviceId: String?,
    enabled: Boolean
) {
    val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }

    fun cleanName(name: String): String {
        val withoutEmojis = name.replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF\\u2600-\\u27BF]"), "").trim()
        return com.alananasss.kittytune.util.LinuxAudioManager.cleanName(withoutEmojis)
    }

    Box(contentAlignment = Alignment.Center) {
        InputChip(
            selected = true,
            enabled = enabled,
            onClick = { if (enabled) expanded = true },
            label = {
                Text(
                    text = cleanName(selectedDevice?.name ?: str("recognition_default_source")),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = if (selectedDevice?.isDesktopAudio == true) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Rounded.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            shape = RoundedCornerShape(16.dp),
            colors = InputChipDefaults.inputChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                labelColor = MaterialTheme.colorScheme.onSurface,
                leadingIconColor = MaterialTheme.colorScheme.primary,
                trailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            border = InputChipDefaults.inputChipBorder(
                enabled = enabled,
                selected = true,
                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                selectedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                borderWidth = 1.dp,
                selectedBorderWidth = 1.dp
            )
        )

        DropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(min = 220.dp, max = 340.dp)
        ) {
            Text(
                text = str("audio_source_header"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp))
            devices.forEach { device ->
                val isSelected = device.id == selectedDeviceId
                val displayName = cleanName(device.name)
                DropdownMenuItem(
                    text = {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (device.isDesktopAudio) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else null,
                    onClick = {
                        viewModel.selectDevice(device)
                        expanded = false
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }
    }
}


/**
 * What was heard, as a card to act on (issue #66): the cover first, then the song, the one artist Shazam credits
 * with how many people listen to them, where it comes from and when it came out, the track's numbers on
 * SoundCloud, and the way to play it here. The account that uploaded it is not the artist: it used to stand in
 * for one, so a song by one singer read as by two.
 */
@Composable
private fun SuccessView(
    state: RecognitionState.Success,
    onPlayClick: () -> Unit,
    onRetry: () -> Unit
) {
    val shazamResult = state.result
    val soundcloudTrack = state.soundcloudTrack
    val imageUrl = shazamResult.coverArtHqUrl ?: soundcloudTrack?.fullResArtwork ?: shazamResult.coverArtUrl
    val title = shazamResult.title.ifBlank { soundcloudTrack?.title.orEmpty() }
    val artist = shazamResult.artist.ifBlank { soundcloudTrack?.displayArtist.orEmpty() }
    val leadArtist = remember(artist) { com.alananasss.kittytune.data.lyrics.GeniusVoices.splitNames(artist).firstOrNull() ?: artist }
    val recognizedAt = remember(state) {
        java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    }
    val released = remember(shazamResult, soundcloudTrack) {
        (shazamResult.releaseDate ?: soundcloudTrack?.releaseDate)?.takeIf { it.isNotBlank() }
    }

    val likedTracks by LikeRepository.likedTracks.collectAsStateWithLifecycle()
    val isLiked = soundcloudTrack?.let { track -> likedTracks.any { it.id == track.id } } == true

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.statusBarsPadding())
            Spacer(modifier = Modifier.height(64.dp))

            ElevatedCard(
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 18.dp),
                modifier = Modifier.size(240.dp)
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.MusicNote, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = artist,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            com.alananasss.kittytune.ui.common.MonthlyListenersText(
                artistName = leadArtist,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )

            val facts = listOfNotNull(
                shazamResult.album?.takeIf { it.isNotBlank() && !it.equals(title, ignoreCase = true) },
                released?.let { str("recognition_released", com.alananasss.kittytune.ui.main.formatReleaseDate(it)) },
                shazamResult.genre?.takeIf { it.isNotBlank() },
            )
            if (facts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = facts.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (soundcloudTrack != null) {
                Spacer(modifier = Modifier.height(20.dp))
                TrackStatsRow(soundcloudTrack)
            }

            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    shapes = ButtonDefaults.shapes(),
                    onClick = if (soundcloudTrack != null) onPlayClick else onRetry,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Icon(if (soundcloudTrack != null) Icons.Rounded.PlayArrow else Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (soundcloudTrack != null) str("recognition_listen_on_kittytune") else str("btn_retry"),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (soundcloudTrack != null) {
                    FilledTonalIconButton(
                        shapes = IconButtonDefaults.shapes(),
                        onClick = {
                            if (isLiked) LikeRepository.removeLike(soundcloudTrack.id) else LikeRepository.addLike(soundcloudTrack)
                        },
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = str("player_like_action"),
                            tint = if (isLiked) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                    FilledTonalIconButton(
                        shapes = IconButtonDefaults.shapes(),
                        onClick = onRetry,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = str("btn_retry"))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = str("recognition_recognized_at", recognizedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(modifier = Modifier.height(120.dp))
        }
    }
}

/** The track's plays, likes, reposts and comments on SoundCloud, each a small tile with its icon. */
@Composable
private fun TrackStatsRow(track: com.alananasss.kittytune.domain.Track) {
    val format = remember { java.text.NumberFormat.getCompactNumberInstance(com.alananasss.kittytune.core.Strings.locale(), java.text.NumberFormat.Style.SHORT) }
    val stats = listOf(
        Triple(Icons.Rounded.PlayArrow, track.playbackCount, str("stat_plays")),
        Triple(Icons.Rounded.Favorite, track.likesCount, str("stat_likes")),
        Triple(Icons.Rounded.Repeat, track.repostsCount, str("stat_reposts")),
        Triple(Icons.Rounded.ChatBubble, track.commentCount, str("stat_comments")),
    ).filter { it.second > 0 }
    if (stats.isEmpty()) return
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.forEach { (icon, count, label) ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        format.format(count),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}


@Composable
private fun ErrorView(error: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Faithful reproduction of home_not_found_illustration from Google Pixel Now Playing
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = str("recognition_track_not_found"),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(Modifier.height(48.dp))

        FilledTonalButton(
            onClick = onRetry,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier
                .height(56.dp)
                .defaultMinSize(minWidth = 0.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text(
                text = str("btn_retry"),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
