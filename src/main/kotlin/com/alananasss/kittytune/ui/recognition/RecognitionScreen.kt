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
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alananasss.kittytune.core.str
import coil3.compose.AsyncImage
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.music.recognition.RecognitionViewModel
import com.alananasss.kittytune.music.recognition.RecognitionState
import com.alananasss.kittytune.data.LikeRepository
import com.alananasss.kittytune.data.AchievementManager
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History

import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.ArrowDropDown
import com.alananasss.kittytune.music.recognition.AudioInputDevice

@Composable
fun RecognitionScreen(
    onBackClick: () -> Unit,
    playerViewModel: PlayerViewModel,
    onNavigate: (String) -> Unit
) {
    val viewModel: RecognitionViewModel = androidx.lifecycle.viewmodel.compose.viewModel { RecognitionViewModel() }
    val state by viewModel.state.collectAsStateWithLifecycle()

    val hasPermission = true

    val isErrorOrSuccess = state is RecognitionState.Error || state is RecognitionState.Success
    val bgColor = if (isErrorOrSuccess) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        AnimatedVisibility(
            visible = !isErrorOrSuccess,
            enter = fadeIn(tween(800)),
            exit = fadeOut(tween(500))
        ) {
            BlobBackgroundView(modifier = Modifier.fillMaxSize())
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
                    .height(280.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            )
        }

        FilledTonalIconButton(
            shapes = IconButtonDefaults.shapes(),
            onClick = onBackClick,
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

        AnimatedContent(
            targetState = state,
            transitionSpec = {
                (fadeIn(animationSpec = tween(400)) + 
                        slideInVertically(animationSpec = tween(400), initialOffsetY = { it / 16 })) togetherWith
                (fadeOut(animationSpec = tween(300)) + 
                        slideOutVertically(animationSpec = tween(300), targetOffsetY = { -it / 16 }))
            },
            label = "state_content",
            modifier = Modifier.fillMaxSize()
        ) { currentState ->
            Box(modifier = Modifier.fillMaxSize()) {
                when (currentState) {
                    is RecognitionState.Idle -> IdleView(
                        viewModel = viewModel,
                        onTap = {
                            viewModel.startRecognition()
                        }
                    )
                    is RecognitionState.Recording  -> ListeningView()
                    is RecognitionState.Processing -> ProcessingView()
                    is RecognitionState.Success -> SuccessView(
                        state = currentState,
                        onPlayClick = {
                            currentState.soundcloudTrack?.let { track ->
                                playerViewModel.playPlaylist(listOf(track), 0)
                                onBackClick()
                            }
                        },
                        onRetry = { viewModel.startRecognition() }
                    )
                    is RecognitionState.Error -> ErrorView(
                        error = currentState.message,
                        onRetry = { viewModel.startRecognition() }
                    )
                }
            }
        }
    }
}


@Composable
private fun IdleView(viewModel: RecognitionViewModel, onTap: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val btnScale by animateFloatAsState(targetValue = if (isPressed) 0.92f else 1f, label = "scale")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        Spacer(modifier = Modifier.weight(0.35f))
        
        Box(
            modifier = Modifier
                .size(180.dp)
                .scale(btnScale)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                .clip(CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onTap
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        
        Spacer(Modifier.height(32.dp))
        
        Text(
            text = str("recognition_tap_to_identify"),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(Modifier.height(24.dp))

        AudioDeviceSelector(viewModel = viewModel)
        
        Spacer(modifier = Modifier.weight(0.45f))
    }
}

@Composable
private fun AudioDeviceSelector(
    viewModel: RecognitionViewModel,
    modifier: Modifier = Modifier
) {
    val availableDevices by viewModel.availableDevices.collectAsStateWithLifecycle()
    val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }

    fun cleanName(name: String): String {
        return name.replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF\\u2600-\\u27BF]"), "").trim()
    }

    if (availableDevices.isNotEmpty()) {
        Box(modifier = modifier) {
            InputChip(
                selected = true,
                onClick = { expanded = true },
                label = {
                    Text(
                        text = cleanName(selectedDevice?.name ?: "Source audio"),
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
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                    labelColor = MaterialTheme.colorScheme.onSurface,
                    leadingIconColor = MaterialTheme.colorScheme.primary,
                    trailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            DropdownMenu(
                expanded = expanded,
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
                availableDevices.forEach { device ->
                    val isSelected = device.id == selectedDevice?.id
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
}

@Composable
private fun ListeningView() {
    val infiniteTransition = rememberInfiniteTransition(label = "listening")
    val pulseScale by infiniteTransition.animateFloat(
        0.95f, 1.15f,
        infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        Spacer(modifier = Modifier.weight(0.417f))
        
        Box(
            modifier = Modifier
                .size(180.dp)
                .scale(pulseScale)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
        
        Spacer(Modifier.height(36.dp))
        
        Text(
            text = str("recognition_listening"),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = str("recognition_listening_desc"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.weight(0.583f))
    }
}

@Composable
private fun ProcessingView() {
    val infiniteTransition = rememberInfiniteTransition(label = "processing")
    val rotation by infiniteTransition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "rot"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        Spacer(modifier = Modifier.weight(0.417f))
        
        Box(
            modifier = Modifier
                .size(180.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            CircularWavyProgressIndicator(
                modifier = Modifier.size(80.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        
        Spacer(Modifier.height(36.dp))
        
        Text(
            text = str("recognition_processing"),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.weight(0.583f))
    }
}


@Composable
private fun SuccessView(
    state: RecognitionState.Success,
    onPlayClick: () -> Unit,
    onRetry: () -> Unit
) {
    val shazamResult = state.result
    val soundcloudTrack = state.soundcloudTrack
    val imageUrl = soundcloudTrack?.fullResArtwork ?: shazamResult.coverArtHqUrl ?: shazamResult.coverArtUrl
    val title = soundcloudTrack?.title ?: shazamResult.title
    val artist = soundcloudTrack?.user?.username ?: shazamResult.artist

    val likedTracks by LikeRepository.likedTracks.collectAsStateWithLifecycle()
    val isLiked = soundcloudTrack?.let { track -> likedTracks.any { it.id == track.id } } == true

    Box(modifier = Modifier.fillMaxSize()) {

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.statusBarsPadding())
            Spacer(modifier = Modifier.height(72.dp))
            
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = artist,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (soundcloudTrack != null) {
                    Button(
                        shapes = ButtonDefaults.shapes(),
                        onClick = onPlayClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),

                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            str("recognition_listen_on_kittytune"),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Button(
                        shapes = ButtonDefaults.shapes(),
                        onClick = onRetry,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),

                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            str("btn_retry"),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                Button(
                    shapes = ButtonDefaults.shapes(),
                    onClick = {
                        soundcloudTrack?.let { track ->
                            if (isLiked) {
                                LikeRepository.removeLike(track.id)
                            } else {
                                LikeRepository.addLike(track)
                                AchievementManager.increment("liker_50")
                                AchievementManager.increment("liker_1000")
                                AchievementManager.increment("liker_5000")
                            }
                        }
                    },
                    modifier = Modifier.height(52.dp),

                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = str("player_like_action"), 
                        modifier = Modifier.padding(horizontal = 24.dp),
                        tint = if (isLiked) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
                
                if (soundcloudTrack != null) {
                    Button(
                        shapes = ButtonDefaults.shapes(),
                        onClick = onRetry,
                        modifier = Modifier.height(52.dp),

                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = str("btn_retry"), modifier = Modifier.padding(horizontal = 24.dp))
                    }
                }
            }
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                ElevatedCard(
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 18.dp),
                    modifier = Modifier.size(260.dp)
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
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.MusicNote, null,
                                Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(120.dp))
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
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            modifier = Modifier
                .size(120.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                .padding(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = str("recognition_track_not_found"),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(48.dp))

        Button(onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),

        ) {
            Text(str("btn_retry"))
        }
    }
}
