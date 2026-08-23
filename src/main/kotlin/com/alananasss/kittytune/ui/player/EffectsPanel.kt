@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alananasss.kittytune.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.onClick
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.roundToInt
import com.alananasss.kittytune.core.EscapableAlertDialog
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class AudioFxDefinition(
    val id: String,
    val titleKey: String,
    val icon: ImageVector,
    val categoryKey: String,
    val isActive: (AudioEffectsState) -> Boolean,
    val onToggle: (PlayerViewModel, onEarrapeWarning: () -> Unit) -> Unit,
    val onOpenDialog: (() -> Unit)? = null,
    val activeColor: @Composable () -> Color = { MaterialTheme.colorScheme.primary },
    val activeContentColor: @Composable () -> Color = { MaterialTheme.colorScheme.onPrimary }
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EffectsPanel(viewModel: PlayerViewModel, modifier: Modifier = Modifier) {
    val isPrecise = viewModel.isPreciseSpeedEnabled

    var showBassBoostDialog by remember { mutableStateOf(false) }
    var showEarrapeDialog by remember { mutableStateOf(false) }
    var showEarrapeWarning by remember { mutableStateOf(false) }
    var showEightDDialog by remember { mutableStateOf(false) }
    var showMuffledDialog by remember { mutableStateOf(false) }
    var showReverbDialog by remember { mutableStateOf(false) }
    var showRainVolumeDialog by remember { mutableStateOf(false) }
    var showNormalizationDialog by remember { mutableStateOf(false) }
    var showVintageMp3Dialog by remember { mutableStateOf(false) }
    var showVocalRemoverDialog by remember { mutableStateOf(false) }
    var showVocalBoostDialog by remember { mutableStateOf(false) }
    var showFlangerDialog by remember { mutableStateOf(false) }
    var showPartyNextDoorDialog by remember { mutableStateOf(false) }
    var showSuperWideDialog by remember { mutableStateOf(false) }
    var showVinylLoFiDialog by remember { mutableStateOf(false) }
    var showPhaserDialog by remember { mutableStateOf(false) }
    var showMegaphoneDialog by remember { mutableStateOf(false) }
    var showRobotVocoderDialog by remember { mutableStateOf(false) }
    var showChorusDialog by remember { mutableStateOf(false) }
    var showUnderwaterDialog by remember { mutableStateOf(false) }
    var showTranceGateDialog by remember { mutableStateOf(false) }
    var showPingPongDelayDialog by remember { mutableStateOf(false) }
    var showChiptuneDialog by remember { mutableStateOf(false) }
    var showShimmerReverbDialog by remember { mutableStateOf(false) }
    var showRotarySpeakerDialog by remember { mutableStateOf(false) }
    var showTapeSaturationDialog by remember { mutableStateOf(false) }
    var showSubOctaverDialog by remember { mutableStateOf(false) }
    var showEmptyMallDialog by remember { mutableStateOf(false) }
    var showGramophoneDialog by remember { mutableStateOf(false) }
    var showReverseEchoDialog by remember { mutableStateOf(false) }
    var showStadiumDialog by remember { mutableStateOf(false) }
    var showWalkmanDialog by remember { mutableStateOf(false) }
    var showAsmrVocalDialog by remember { mutableStateOf(false) }
    var showNightDriveDialog by remember { mutableStateOf(false) }
    var showStudioEditSheet by remember { mutableStateOf(false) }

    val allEffects = remember {
        listOf(
            AudioFxDefinition(
                id = "bass_boost",
                titleKey = "effect_bass_boost",
                icon = Icons.Rounded.Bolt,
                categoryKey = "category_power_eq",
                isActive = { it.isBassBoostEnabled },
                onToggle = { vm, _ -> vm.toggleBassBoost() },
                onOpenDialog = { showBassBoostDialog = true },
                activeColor = { MaterialTheme.colorScheme.primary },
                activeContentColor = { MaterialTheme.colorScheme.onPrimary }
            ),
            AudioFxDefinition(
                id = "sub_octaver",
                titleKey = "effect_sub_octaver",
                icon = Icons.Rounded.Speaker,
                categoryKey = "category_power_eq",
                isActive = { it.isSubOctaverEnabled },
                onToggle = { vm, _ -> vm.toggleSubOctaver() },
                onOpenDialog = { showSubOctaverDialog = true },
                activeColor = { Color(0xFFD500F9) },
                activeContentColor = { Color.White }
            ),
            AudioFxDefinition(
                id = "tape_saturation",
                titleKey = "effect_tape_saturation",
                icon = Icons.Rounded.Whatshot,
                categoryKey = "category_power_eq",
                isActive = { it.isTapeSaturationEnabled },
                onToggle = { vm, _ -> vm.toggleTapeSaturation() },
                onOpenDialog = { showTapeSaturationDialog = true },
                activeColor = { Color(0xFFFF6E40) },
                activeContentColor = { Color(0xFF3E1200) }
            ),
            AudioFxDefinition(
                id = "vocal_boost",
                titleKey = "effect_vocal_boost",
                icon = Icons.Rounded.RecordVoiceOver,
                categoryKey = "category_power_eq",
                isActive = { it.isVocalBoostEnabled },
                onToggle = { vm, _ -> vm.toggleVocalBoost() },
                onOpenDialog = { showVocalBoostDialog = true },
                activeColor = { Color(0xFF00B0FF) },
                activeContentColor = { Color(0xFF002244) }
            ),
            AudioFxDefinition(
                id = "vocal_remover",
                titleKey = "effect_vocal_remover",
                icon = Icons.Rounded.MicOff,
                categoryKey = "category_power_eq",
                isActive = { it.isVocalRemoverEnabled },
                onToggle = { vm, _ -> vm.toggleVocalRemover() },
                onOpenDialog = { showVocalRemoverDialog = true },
                activeColor = { Color(0xFFE91E63) },
                activeContentColor = { Color.White }
            ),
            AudioFxDefinition(
                id = "normalization",
                titleKey = "pref_norm_title",
                icon = Icons.AutoMirrored.Rounded.VolumeDown,
                categoryKey = "category_power_eq",
                isActive = { it.isNormalizationEnabled },
                onToggle = { vm, _ -> vm.toggleNormalization() },
                onOpenDialog = { showNormalizationDialog = true },
                activeColor = { MaterialTheme.colorScheme.primary },
                activeContentColor = { MaterialTheme.colorScheme.onPrimary }
            ),
            AudioFxDefinition(
                id = "earrape",
                titleKey = "btn_earrape",
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                categoryKey = "category_power_eq",
                isActive = { it.isEarrapeEnabled },
                onToggle = { vm, showWarn ->
                    if (!vm.hasSeenEarrapeWarning()) showWarn() else vm.toggleEarrape()
                },
                onOpenDialog = { showEarrapeDialog = true },
                activeColor = { MaterialTheme.colorScheme.error },
                activeContentColor = { MaterialTheme.colorScheme.onError }
            ),

            AudioFxDefinition(
                id = "eight_d",
                titleKey = "effect_8d",
                icon = Icons.Rounded.SurroundSound,
                categoryKey = "category_spatial",
                isActive = { it.is8DEnabled },
                onToggle = { vm, _ -> vm.toggle8D() },
                onOpenDialog = { showEightDDialog = true },
                activeColor = { MaterialTheme.colorScheme.tertiary },
                activeContentColor = { MaterialTheme.colorScheme.onTertiary }
            ),
            AudioFxDefinition(
                id = "super_wide",
                titleKey = "effect_super_wide",
                icon = Icons.Rounded.SurroundSound,
                categoryKey = "category_spatial",
                isActive = { it.isSuperWideEnabled },
                onToggle = { vm, _ -> vm.toggleSuperWide() },
                onOpenDialog = { showSuperWideDialog = true },
                activeColor = { Color(0xFF26C6DA) },
                activeContentColor = { Color(0xFF00363A) }
            ),
            AudioFxDefinition(
                id = "chorus",
                titleKey = "effect_chorus",
                icon = Icons.Rounded.Grain,
                categoryKey = "category_spatial",
                isActive = { it.isChorusEnabled },
                onToggle = { vm, _ -> vm.toggleChorus() },
                onOpenDialog = { showChorusDialog = true },
                activeColor = { Color(0xFF5C6BC0) },
                activeContentColor = { Color.White }
            ),
            AudioFxDefinition(
                id = "flanger",
                titleKey = "effect_flanger",
                icon = Icons.Rounded.Air,
                categoryKey = "category_spatial",
                isActive = { it.isFlangerEnabled },
                onToggle = { vm, _ -> vm.toggleFlanger() },
                onOpenDialog = { showFlangerDialog = true },
                activeColor = { Color(0xFF00E5FF) },
                activeContentColor = { Color(0xFF003840) }
            ),
            AudioFxDefinition(
                id = "phaser",
                titleKey = "effect_phaser",
                icon = Icons.Rounded.Waves,
                categoryKey = "category_spatial",
                isActive = { it.isPhaserEnabled },
                onToggle = { vm, _ -> vm.togglePhaser() },
                onOpenDialog = { showPhaserDialog = true },
                activeColor = { Color(0xFF7C4DFF) },
                activeContentColor = { Color.White }
            ),
            AudioFxDefinition(
                id = "ping_pong",
                titleKey = "effect_ping_pong",
                icon = Icons.Rounded.SyncAlt,
                categoryKey = "category_spatial",
                isActive = { it.isPingPongDelayEnabled },
                onToggle = { vm, _ -> vm.togglePingPongDelay() },
                onOpenDialog = { showPingPongDelayDialog = true },
                activeColor = { Color(0xFF64DD17) },
                activeContentColor = { Color(0xFF1B3B00) }
            ),
            AudioFxDefinition(
                id = "reverse_echo",
                titleKey = "effect_reverse_echo",
                icon = Icons.AutoMirrored.Rounded.CompareArrows,
                categoryKey = "category_spatial",
                isActive = { it.isReverseEchoEnabled },
                onToggle = { vm, _ -> vm.toggleReverseEcho() },
                onOpenDialog = { showReverseEchoDialog = true },
                activeColor = { Color(0xFF00E5FF) },
                activeContentColor = { Color(0xFF003B46) }
            ),
            AudioFxDefinition(
                id = "reverb",
                titleKey = "effect_reverb",
                icon = Icons.Rounded.GraphicEq,
                categoryKey = "category_spatial",
                isActive = { it.isReverbEnabled },
                onToggle = { vm, _ -> vm.toggleReverb() },
                onOpenDialog = { showReverbDialog = true },
                activeColor = { MaterialTheme.colorScheme.primary },
                activeContentColor = { MaterialTheme.colorScheme.onPrimary }
            ),
            AudioFxDefinition(
                id = "shimmer_reverb",
                titleKey = "effect_shimmer_reverb",
                icon = Icons.Rounded.Flare,
                categoryKey = "category_spatial",
                isActive = { it.isShimmerReverbEnabled },
                onToggle = { vm, _ -> vm.toggleShimmerReverb() },
                onOpenDialog = { showShimmerReverbDialog = true },
                activeColor = { Color(0xFFFF4081) },
                activeContentColor = { Color.White }
            ),
            AudioFxDefinition(
                id = "stadium",
                titleKey = "effect_stadium",
                icon = Icons.Rounded.SurroundSound,
                categoryKey = "category_spatial",
                isActive = { it.isStadiumEnabled },
                onToggle = { vm, _ -> vm.toggleStadium() },
                onOpenDialog = { showStadiumDialog = true },
                activeColor = { Color(0xFF00E676) },
                activeContentColor = { Color(0xFF003815) }
            ),
            AudioFxDefinition(
                id = "rotary_speaker",
                titleKey = "effect_rotary_speaker",
                icon = Icons.AutoMirrored.Rounded.RotateRight,
                categoryKey = "category_spatial",
                isActive = { it.isRotarySpeakerEnabled },
                onToggle = { vm, _ -> vm.toggleRotarySpeaker() },
                onOpenDialog = { showRotarySpeakerDialog = true },
                activeColor = { Color(0xFFFF6D00) },
                activeContentColor = { Color(0xFF3E1200) }
            ),
            AudioFxDefinition(
                id = "asmr_vocal",
                titleKey = "effect_asmr_vocal",
                icon = Icons.Rounded.RecordVoiceOver,
                categoryKey = "category_spatial",
                isActive = { it.isAsmrVocalEnabled },
                onToggle = { vm, _ -> vm.toggleAsmrVocal() },
                onOpenDialog = { showAsmrVocalDialog = true },
                activeColor = { Color(0xFFFF4081) },
                activeContentColor = { Color.White }
            ),
            AudioFxDefinition(
                id = "mono",
                titleKey = "pref_audio_mono",
                icon = Icons.Rounded.Headphones,
                categoryKey = "category_spatial",
                isActive = { it.isMonoEnabled },
                onToggle = { vm, _ -> vm.toggleMono() },
                onOpenDialog = null,
                activeColor = { MaterialTheme.colorScheme.secondary },
                activeContentColor = { MaterialTheme.colorScheme.onSecondary }
            ),

            AudioFxDefinition(
                id = "rain",
                titleKey = "effect_ambient_sound",
                icon = Icons.Rounded.WaterDrop,
                categoryKey = "category_ambience_filters",
                isActive = { it.isRainEnabled },
                onToggle = { vm, _ -> vm.toggleRain() },
                onOpenDialog = { showRainVolumeDialog = true },
                activeColor = { Color(0xFF81D4FA) },
                activeContentColor = { Color(0xFF004BA0) }
            ),
            AudioFxDefinition(
                id = "underwater",
                titleKey = "effect_underwater",
                icon = Icons.Rounded.Waves,
                categoryKey = "category_ambience_filters",
                isActive = { it.isUnderwaterEnabled },
                onToggle = { vm, _ -> vm.toggleUnderwater() },
                onOpenDialog = { showUnderwaterDialog = true },
                activeColor = { Color(0xFF00838F) },
                activeContentColor = { Color.White }
            ),
            AudioFxDefinition(
                id = "empty_mall",
                titleKey = "effect_empty_mall",
                icon = Icons.Rounded.Storefront,
                categoryKey = "category_ambience_filters",
                isActive = { it.isEmptyMallEnabled },
                onToggle = { vm, _ -> vm.toggleEmptyMall() },
                onOpenDialog = { showEmptyMallDialog = true },
                activeColor = { Color(0xFF00BFA5) },
                activeContentColor = { Color(0xFF003730) }
            ),
            AudioFxDefinition(
                id = "party_next_door",
                titleKey = "effect_party_next_door",
                icon = Icons.Rounded.MeetingRoom,
                categoryKey = "category_ambience_filters",
                isActive = { it.isPartyNextDoorEnabled },
                onToggle = { vm, _ -> vm.togglePartyNextDoor() },
                onOpenDialog = { showPartyNextDoorDialog = true },
                activeColor = { Color(0xFFAB47BC) },
                activeContentColor = { Color.White }
            ),
            AudioFxDefinition(
                id = "night_drive",
                titleKey = "effect_night_drive",
                icon = Icons.Rounded.DirectionsCar,
                categoryKey = "category_ambience_filters",
                isActive = { it.isNightDriveEnabled },
                onToggle = { vm, _ -> vm.toggleNightDrive() },
                onOpenDialog = { showNightDriveDialog = true },
                activeColor = { Color(0xFF2979FF) },
                activeContentColor = { Color.White }
            ),
            AudioFxDefinition(
                id = "muffled",
                titleKey = "effect_muffled",
                icon = Icons.Rounded.BlurOn,
                categoryKey = "category_ambience_filters",
                isActive = { it.isMuffledEnabled },
                onToggle = { vm, _ -> vm.toggleMuffled() },
                onOpenDialog = { showMuffledDialog = true },
                activeColor = { MaterialTheme.colorScheme.secondary },
                activeContentColor = { MaterialTheme.colorScheme.onSecondary }
            ),

            AudioFxDefinition(
                id = "cassette_walkman",
                titleKey = "effect_cassette_walkman",
                icon = Icons.Rounded.Radio,
                categoryKey = "category_retro_vintage",
                isActive = { it.isWalkmanEnabled },
                onToggle = { vm, _ -> vm.toggleWalkman() },
                onOpenDialog = { showWalkmanDialog = true },
                activeColor = { Color(0xFFFFAB00) },
                activeContentColor = { Color(0xFF3E2700) }
            ),
            AudioFxDefinition(
                id = "vinyl_lofi",
                titleKey = "effect_vinyl_lofi",
                icon = Icons.Rounded.Album,
                categoryKey = "category_retro_vintage",
                isActive = { it.isVinylLoFiEnabled },
                onToggle = { vm, _ -> vm.toggleVinylLoFi() },
                onOpenDialog = { showVinylLoFiDialog = true },
                activeColor = { Color(0xFFFFB300) },
                activeContentColor = { Color(0xFF3E2723) }
            ),
            AudioFxDefinition(
                id = "gramophone",
                titleKey = "effect_gramophone",
                icon = Icons.Rounded.History,
                categoryKey = "category_retro_vintage",
                isActive = { it.isGramophoneEnabled },
                onToggle = { vm, _ -> vm.toggleGramophone() },
                onOpenDialog = { showGramophoneDialog = true },
                activeColor = { Color(0xFF8D6E63) },
                activeContentColor = { Color.White }
            ),
            AudioFxDefinition(
                id = "vintage_mp3",
                titleKey = "effect_vintage_mp3",
                icon = Icons.Rounded.Radio,
                categoryKey = "category_retro_vintage",
                isActive = { it.isVintageMp3Enabled },
                onToggle = { vm, _ -> vm.toggleVintageMp3() },
                onOpenDialog = { showVintageMp3Dialog = true },
                activeColor = { Color(0xFFFFB74D) },
                activeContentColor = { Color(0xFF5D2B00) }
            ),
            AudioFxDefinition(
                id = "chiptune",
                titleKey = "effect_chiptune",
                icon = Icons.Rounded.Gamepad,
                categoryKey = "category_retro_vintage",
                isActive = { it.isChiptuneEnabled },
                onToggle = { vm, _ -> vm.toggleChiptune() },
                onOpenDialog = { showChiptuneDialog = true },
                activeColor = { Color(0xFFE040FB) },
                activeContentColor = { Color.White }
            ),
            AudioFxDefinition(
                id = "megaphone",
                titleKey = "effect_megaphone",
                icon = Icons.Rounded.Campaign,
                categoryKey = "category_retro_vintage",
                isActive = { it.isMegaphoneEnabled },
                onToggle = { vm, _ -> vm.toggleMegaphone() },
                onOpenDialog = { showMegaphoneDialog = true },
                activeColor = { Color(0xFFFF7043) },
                activeContentColor = { Color(0xFF3E1200) }
            ),
            AudioFxDefinition(
                id = "robot_vocoder",
                titleKey = "effect_robot_vocoder",
                icon = Icons.Rounded.SmartToy,
                categoryKey = "category_retro_vintage",
                isActive = { it.isRobotVocoderEnabled },
                onToggle = { vm, _ -> vm.toggleRobotVocoder() },
                onOpenDialog = { showRobotVocoderDialog = true },
                activeColor = { Color(0xFF00E676) },
                activeContentColor = { Color(0xFF003314) }
            ),
            AudioFxDefinition(
                id = "trance_gate",
                titleKey = "effect_trance_gate",
                icon = Icons.Rounded.ElectricBolt,
                categoryKey = "category_retro_vintage",
                isActive = { it.isTranceGateEnabled },
                onToggle = { vm, _ -> vm.toggleTranceGate() },
                onOpenDialog = { showTranceGateDialog = true },
                activeColor = { Color(0xFFFF9100) },
                activeContentColor = { Color(0xFF3E1A00) }
            )
        )
    }

    val pinnedTiles = remember(viewModel.pinnedAudioFx, allEffects) {
        viewModel.pinnedAudioFx.mapNotNull { id -> allEffects.find { it.id == id } }
    }
    val pages = remember(pinnedTiles) {
        if (pinnedTiles.isEmpty()) emptyList() else pinnedTiles.chunked(6)
    }
    val pagerState = rememberPagerState(pageCount = { maxOf(1, pages.size) })
    val coroutineScope = rememberCoroutineScope()

    if (showStudioEditSheet) {
        AudioFxStudioSheet(
            viewModel = viewModel,
            allEffects = allEffects,
            onDismiss = { showStudioEditSheet = false }
        )
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = str("player_audio_settings"),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Speed + Pitch Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Speed,
                            null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "${viewModel.effectsState.speed}x",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val isPitchActive = viewModel.effectsState.isPitchEnabled
                    val pitchContainerColor by animateColorAsState(
                        targetValue = if (isPitchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        label = "pitchContainer"
                    )
                    val pitchContentColor by animateColorAsState(
                        targetValue = if (isPitchActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        label = "pitchContent"
                    )

                    Surface(
                        onClick = { viewModel.togglePitchEnabled(!isPitchActive) },
                        shape = CircleShape,
                        color = pitchContainerColor,
                        border = if (isPitchActive) null else BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        contentColor = pitchContentColor
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedVisibility(visible = isPitchActive) {
                                Row {
                                    Icon(
                                        Icons.Default.Check,
                                        null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                            }
                            Text(
                                text = str("player_pitch"),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                Slider(
                    value = viewModel.effectsState.speed,
                    onValueChange = { viewModel.setCustomSpeed(it) },
                    valueRange = 0.5f..2.0f,
                    steps = if (isPrecise) 29 else 14,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(28.dp))

            // Special Effects Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = str("player_special_effects"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = str("audio_fx_long_press_hint"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )
                }

                if (pages.size > 1) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            },
                            enabled = pagerState.currentPage > 0,
                            shapes = IconButtonDefaults.shapes(),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.NavigateBefore,
                                contentDescription = "Previous Page",
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Text(
                            text = "${pagerState.currentPage + 1}/${pages.size}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            },
                            enabled = pagerState.currentPage < pages.lastIndex,
                            shapes = IconButtonDefaults.shapes(),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.NavigateNext,
                                contentDescription = "Next Page",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            if (pages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = str("edit_tiles_subtitle"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val rowHeightDp = 84.dp
                val rowSpacingDp = 12.dp

                val calculatePageHeight: (Int) -> Dp = { pageIdx ->
                    val itemsCount = pages.getOrNull(pageIdx)?.size ?: 0
                    if (itemsCount == 0) 0.dp else {
                        val rows = (itemsCount + 1) / 2
                        rowHeightDp * rows + rowSpacingDp * (rows - 1)
                    }
                }

                val currentPage = pagerState.currentPage
                val offsetFraction = pagerState.currentPageOffsetFraction
                val targetPage = if (offsetFraction > 0f) {
                    (currentPage + 1).coerceAtMost(pages.lastIndex)
                } else if (offsetFraction < 0f) {
                    (currentPage - 1).coerceAtLeast(0)
                } else {
                    currentPage
                }

                val currentHeight = calculatePageHeight(currentPage)
                val targetHeight = calculatePageHeight(targetPage)
                val fraction = abs(offsetFraction).coerceIn(0f, 1f)
                val pagerHeight = currentHeight + (targetHeight - currentHeight) * fraction

                HorizontalPager(
                    state = pagerState,
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(pagerHeight)
                ) { pageIndex ->
                    val pageItems = pages.getOrElse(pageIndex) { emptyList() }
                    val pageOffset = abs((pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction)
                    val pageAlpha = (1f - pageOffset * 0.35f).coerceIn(0f, 1f)
                    val pageScale = (1f - pageOffset * 0.04f).coerceIn(0.95f, 1f)

                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = pageAlpha
                                scaleX = pageScale
                                scaleY = pageScale
                            }
                    ) {
                        pageItems.chunked(2).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowItems.forEach { fx ->
                                    FxTile(
                                        label = str(fx.titleKey),
                                        icon = fx.icon,
                                        isActive = fx.isActive(viewModel.effectsState),
                                        onClick = { fx.onToggle(viewModel) { showEarrapeWarning = true } },
                                        onLongClick = fx.onOpenDialog,
                                        modifier = if (rowItems.size == 1) Modifier.fillMaxWidth() else Modifier.weight(1f),
                                        activeColor = fx.activeColor(),
                                        activeContentColor = fx.activeContentColor()
                                    )
                                }
                            }
                        }
                    }
                }

                if (pages.size > 1) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(pages.size) { iteration ->
                            val isSelected = pagerState.currentPage == iteration
                            val indicatorWidth by animateDpAsState(
                                targetValue = if (isSelected) 22.dp else 6.dp,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "indicatorWidth"
                            )
                            val indicatorColor by animateColorAsState(
                                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                animationSpec = tween(200),
                                label = "indicatorColor"
                            )
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .height(6.dp)
                                    .width(indicatorWidth)
                                    .clip(CircleShape)
                                    .background(indicatorColor)
                                    .clickable {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(iteration)
                                        }
                                    }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            OutlinedButton(
                onClick = { showStudioEditSheet = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.DashboardCustomize,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = str("btn_explore_all_effects"),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${pinnedTiles.size}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Dialogs ──
    if (showBassBoostDialog) {
        BassBoostDialog(viewModel = viewModel, onDismiss = { showBassBoostDialog = false })
    }

    if (showEarrapeDialog) {
        EarrapeDialog(viewModel = viewModel, onShowWarning = { showEarrapeWarning = true }, onDismiss = { showEarrapeDialog = false })
    }

    if (showEarrapeWarning) {
        EarrapeWarningDialog(viewModel = viewModel, onDismiss = { showEarrapeWarning = false })
    }

    if (showEightDDialog) {
        EightDDialog(viewModel = viewModel, onDismiss = { showEightDDialog = false })
    }

    if (showMuffledDialog) {
        MuffledDialog(viewModel = viewModel, onDismiss = { showMuffledDialog = false })
    }

    if (showReverbDialog) {
        ReverbDialog(viewModel = viewModel, onDismiss = { showReverbDialog = false })
    }

    if (showRainVolumeDialog) {
        AmbientSoundscapeDialog(viewModel = viewModel, onDismiss = { showRainVolumeDialog = false })
    }

    if (showNormalizationDialog) {
        NormalizationDialog(viewModel = viewModel, onDismiss = { showNormalizationDialog = false })
    }

    if (showVintageMp3Dialog) {
        VintageMp3Dialog(viewModel = viewModel, onDismiss = { showVintageMp3Dialog = false })
    }

    if (showVocalRemoverDialog) {
        VocalRemoverDialog(viewModel = viewModel, onDismiss = { showVocalRemoverDialog = false })
    }

    if (showVocalBoostDialog) {
        VocalBoostDialog(viewModel = viewModel, onDismiss = { showVocalBoostDialog = false })
    }

    if (showFlangerDialog) {
        FlangerDialog(viewModel = viewModel, onDismiss = { showFlangerDialog = false })
    }

    if (showPartyNextDoorDialog) {
        PartyNextDoorDialog(viewModel = viewModel, onDismiss = { showPartyNextDoorDialog = false })
    }

    if (showSuperWideDialog) {
        SuperWideDialog(viewModel = viewModel, onDismiss = { showSuperWideDialog = false })
    }

    if (showVinylLoFiDialog) {
        VinylLoFiDialog(viewModel = viewModel, onDismiss = { showVinylLoFiDialog = false })
    }

    if (showPhaserDialog) {
        PhaserDialog(viewModel = viewModel, onDismiss = { showPhaserDialog = false })
    }

    if (showMegaphoneDialog) {
        MegaphoneDialog(viewModel = viewModel, onDismiss = { showMegaphoneDialog = false })
    }

    if (showRobotVocoderDialog) {
        RobotVocoderDialog(viewModel = viewModel, onDismiss = { showRobotVocoderDialog = false })
    }

    if (showChorusDialog) {
        ChorusDialog(viewModel = viewModel, onDismiss = { showChorusDialog = false })
    }

    if (showUnderwaterDialog) {
        UnderwaterDialog(viewModel = viewModel, onDismiss = { showUnderwaterDialog = false })
    }

    if (showTranceGateDialog) {
        TranceGateDialog(viewModel = viewModel, onDismiss = { showTranceGateDialog = false })
    }

    if (showPingPongDelayDialog) {
        PingPongDelayDialog(viewModel = viewModel, onDismiss = { showPingPongDelayDialog = false })
    }

    if (showChiptuneDialog) {
        ChiptuneDialog(viewModel = viewModel, onDismiss = { showChiptuneDialog = false })
    }

    if (showShimmerReverbDialog) {
        ShimmerReverbDialog(viewModel = viewModel, onDismiss = { showShimmerReverbDialog = false })
    }

    if (showRotarySpeakerDialog) {
        RotarySpeakerDialog(viewModel = viewModel, onDismiss = { showRotarySpeakerDialog = false })
    }

    if (showTapeSaturationDialog) {
        TapeSaturationDialog(viewModel = viewModel, onDismiss = { showTapeSaturationDialog = false })
    }

    if (showSubOctaverDialog) {
        SubOctaverDialog(viewModel = viewModel, onDismiss = { showSubOctaverDialog = false })
    }

    if (showEmptyMallDialog) {
        EmptyMallDialog(viewModel = viewModel, onDismiss = { showEmptyMallDialog = false })
    }

    if (showGramophoneDialog) {
        GramophoneDialog(viewModel = viewModel, onDismiss = { showGramophoneDialog = false })
    }

    if (showReverseEchoDialog) {
        ReverseEchoDialog(viewModel = viewModel, onDismiss = { showReverseEchoDialog = false })
    }

    if (showStadiumDialog) {
        StadiumDialog(viewModel = viewModel, onDismiss = { showStadiumDialog = false })
    }

    if (showWalkmanDialog) {
        WalkmanDialog(viewModel = viewModel, onDismiss = { showWalkmanDialog = false })
    }

    if (showAsmrVocalDialog) {
        AsmrVocalDialog(viewModel = viewModel, onDismiss = { showAsmrVocalDialog = false })
    }

    if (showNightDriveDialog) {
        NightDriveDialog(viewModel = viewModel, onDismiss = { showNightDriveDialog = false })
    }
}

// ── FxTile (Matching Android FxTile) ──
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FxTile(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    activeContentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    val containerColor by animateColorAsState(
        targetValue = if (isActive) activeColor else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(300), label = "containerColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isActive) activeContentColor else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(300), label = "contentColor"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isActive) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessMedium
        ), label = "iconScale"
    )

    val interactionSource = remember { MutableInteractionSource() }
    var longPressConsumed by remember { mutableStateOf(false) }

    if (onLongClick != null) {
        LaunchedEffect(interactionSource, onLongClick) {
            var longPressJob: Job? = null
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        longPressConsumed = false
                        longPressJob = launch {
                            delay(500)
                            longPressConsumed = true
                            onLongClick()
                        }
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        longPressJob?.cancel()
                    }
                }
            }
        }
    }

    FilledTonalButton(
        onClick = {
            if (!longPressConsumed) onClick()
            longPressConsumed = false
        },
        modifier = modifier
            .height(84.dp)
            .onClick(matcher = PointerMatcher.mouse(PointerButton.Secondary)) {
                onLongClick?.invoke()
            },
        shapes = ButtonDefaults.shapes(),
        interactionSource = interactionSource,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── AudioFxStudioSheet (Matching Android Studio Sheet) ──
@Composable
fun AudioFxStudioSheet(
    viewModel: PlayerViewModel,
    allEffects: List<AudioFxDefinition>,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    val categories = remember(allEffects) {
        listOf(
            Triple("category_power_eq", Icons.Rounded.Bolt, allEffects.filter { it.categoryKey == "category_power_eq" }),
            Triple("category_spatial", Icons.Rounded.SurroundSound, allEffects.filter { it.categoryKey == "category_spatial" }),
            Triple("category_ambience_filters", Icons.Rounded.WaterDrop, allEffects.filter { it.categoryKey == "category_ambience_filters" }),
            Triple("category_retro_vintage", Icons.Rounded.Radio, allEffects.filter { it.categoryKey == "category_retro_vintage" })
        )
    }

    val pinnedDefs = remember(viewModel.pinnedAudioFx, allEffects) {
        viewModel.pinnedAudioFx.mapNotNull { id -> allEffects.find { it.id == id } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 36.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDismiss,
                shapes = IconButtonDefaults.shapes()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Surface(
                onClick = { viewModel.resetPinnedAudioFx() },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = str("btn_reset"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = str("edit_tiles_title"),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = str("edit_tiles_subtitle"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = str("audio_fx_long_press_hint"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
        )

        Spacer(Modifier.height(20.dp))

        // Pinned Tiles Container
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                DraggablePinnedTilesGrid(
                    pinnedList = viewModel.pinnedAudioFx,
                    allEffects = allEffects,
                    viewModel = viewModel,
                    onRemoveFx = { fxId ->
                        viewModel.togglePinAudioFx(fxId)
                    }
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        categories.forEach { (catKey, catIcon, fxList) ->
            if (fxList.isNotEmpty()) {
                AvailableCategorySection(
                    categoryTitleKey = catKey,
                    categoryIcon = catIcon,
                    effects = fxList,
                    viewModel = viewModel,
                    onToggleFx = { fxId -> viewModel.togglePinAudioFx(fxId) }
                )
            }
        }
    }
}

@Composable
fun DraggablePinnedTilesGrid(
    pinnedList: List<String>,
    allEffects: List<AudioFxDefinition>,
    viewModel: PlayerViewModel,
    onDragStateChanged: (Boolean) -> Unit = {},
    onRemoveFx: (String) -> Unit
) {
    val density = LocalDensity.current

    var currentOrder by remember(pinnedList) { mutableStateOf(pinnedList) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragTouchOffsetInItem by remember { mutableStateOf(Offset.Zero) }
    var currentFingerPos by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(draggedId) {
        onDragStateChanged(draggedId != null)
    }

    val pinnedDefs = remember(currentOrder, allEffects) {
        currentOrder.mapNotNull { id -> allEffects.find { it.id == id } }
    }

    if (pinnedDefs.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = str("edit_tiles_subtitle"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val spacingDp = 10.dp
    val itemHeightDp = 76.dp

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val totalWidthPx = constraints.maxWidth.toFloat()
        val spacingPx = with(density) { spacingDp.toPx() }
        val itemHeightPx = with(density) { itemHeightDp.toPx() }
        val itemWidthPx = (totalWidthPx - spacingPx) / 2f
        val itemWidthDp = with(density) { itemWidthPx.toDp() }

        val totalRows = (currentOrder.size + 1) / 2
        val totalHeightDp = if (totalRows == 0) 0.dp else (itemHeightDp * totalRows + spacingDp * (totalRows - 1))

        Box(modifier = Modifier.fillMaxWidth().height(totalHeightDp)) {
            pinnedDefs.forEach { fx ->
                val index = currentOrder.indexOf(fx.id)
                if (index == -1) return@forEach

                val isDragging = draggedId == fx.id

                val slotCol = index % 2
                val slotRow = index / 2
                val slotXPx = slotCol * (itemWidthPx + spacingPx)
                val slotYPx = slotRow * (itemHeightPx + spacingPx)

                val targetXPx = if (isDragging) (currentFingerPos.x - dragTouchOffsetInItem.x) else slotXPx
                val targetYPx = if (isDragging) (currentFingerPos.y - dragTouchOffsetInItem.y) else slotYPx

                val animatedXPx by animateFloatAsState(
                    targetValue = targetXPx,
                    animationSpec = if (isDragging) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "itemX_${fx.id}"
                )
                val animatedYPx by animateFloatAsState(
                    targetValue = targetYPx,
                    animationSpec = if (isDragging) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "itemY_${fx.id}"
                )

                val scale by animateFloatAsState(
                    targetValue = if (isDragging) 1.08f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy),
                    label = "itemScale_${fx.id}"
                )
                val zIndex = if (isDragging) 100f else 1f

                Box(
                    modifier = Modifier
                        .offset { IntOffset(animatedXPx.roundToInt(), animatedYPx.roundToInt()) }
                        .width(itemWidthDp)
                        .height(itemHeightDp)
                        .zIndex(zIndex)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            shadowElevation = if (isDragging) 16.dp.toPx() else 0f
                            shape = RoundedCornerShape(22.dp)
                            clip = false
                        }
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offsetInItem ->
                                    val curIdx = currentOrder.indexOf(fx.id)
                                    if (curIdx != -1) {
                                        val col = curIdx % 2
                                        val row = curIdx / 2
                                        val itemOriginX = col * (itemWidthPx + spacingPx)
                                        val itemOriginY = row * (itemHeightPx + spacingPx)

                                        draggedId = fx.id
                                        dragTouchOffsetInItem = offsetInItem
                                        currentFingerPos = Offset(itemOriginX + offsetInItem.x, itemOriginY + offsetInItem.y)
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (draggedId == fx.id) {
                                        currentFingerPos += dragAmount

                                        val hoveredCol = if (currentFingerPos.x > totalWidthPx / 2f) 1 else 0
                                        val maxRow = (currentOrder.size - 1) / 2
                                        val hoveredRow = (currentFingerPos.y / (itemHeightPx + spacingPx)).toInt().coerceIn(0, maxRow)
                                        val targetIdx = (hoveredRow * 2 + hoveredCol).coerceIn(0, currentOrder.lastIndex)

                                        val curIdx = currentOrder.indexOf(fx.id)
                                        if (curIdx != -1 && targetIdx != curIdx) {
                                            val updated = currentOrder.toMutableList().apply {
                                                removeAt(curIdx)
                                                add(targetIdx, fx.id)
                                            }
                                            currentOrder = updated
                                        }
                                    }
                                },
                                onDragEnd = {
                                    if (draggedId == fx.id) {
                                        viewModel.updatePinnedAudioFx(currentOrder)
                                        draggedId = null
                                    }
                                },
                                onDragCancel = {
                                    if (draggedId == fx.id) {
                                        viewModel.updatePinnedAudioFx(currentOrder)
                                        draggedId = null
                                    }
                                }
                            )
                        }
                ) {
                    ActiveQSTile(
                        fx = fx,
                        isActive = fx.isActive(viewModel.effectsState),
                        onRemove = { onRemoveFx(fx.id) }
                    )
                }
            }
        }
    }
}

// ── ActiveQSTile (Matching Android ActiveQSTile) ──
@Composable
fun ActiveQSTile(
    fx: AudioFxDefinition,
    isActive: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = fx.activeColor()
    val activeContentColor = fx.activeContentColor()

    val containerColor by animateColorAsState(
        targetValue = if (isActive) activeColor else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = tween(250),
        label = "activeContainerColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isActive) activeContentColor else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(250),
        label = "activeContentColor"
    )

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = containerColor,
        contentColor = contentColor,
        border = if (isActive) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isActive) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = fx.icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (isActive) activeContentColor else MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = str(fx.titleKey),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onRemove,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(30.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Remove,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── AvailableCategorySection (Matching Android Category Section) ──
@Composable
fun AvailableCategorySection(
    categoryTitleKey: String,
    categoryIcon: ImageVector,
    effects: List<AudioFxDefinition>,
    viewModel: PlayerViewModel,
    onToggleFx: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        ) {
            Icon(
                imageVector = categoryIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = str(categoryTitleKey),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            effects.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowItems.forEach { fx ->
                        val isPinned = viewModel.isAudioFxPinned(fx.id)
                        AvailableTile(
                            fx = fx,
                            isPinned = isPinned,
                            onClick = { onToggleFx(fx.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowItems.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// ── AvailableTile (Matching Android AvailableTile) ──
@Composable
fun AvailableTile(
    fx: AudioFxDefinition,
    isPinned: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (isPinned) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = tween(250),
        label = "availContainerColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isPinned) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(250),
        label = "availContentColor"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isPinned) 0.2f else 0.4f)),
        modifier = modifier.height(72.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isPinned) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = fx.icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (isPinned) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = str(fx.titleKey),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = CircleShape,
                color = if (isPinned) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
                contentColor = if (isPinned) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPinned) Icons.Rounded.Check else Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

// ── Complete Dialog Suite (Matching Android exactly) ──

@Composable
private fun BassBoostDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Bolt, null) },
        title = { Text(str("effect_bass_boost")) },
        text = {
            Column {
                Text(
                    str("label_intensity", (viewModel.effectsState.bassBoostIntensity * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(16.dp))
                Slider(
                    value = viewModel.effectsState.bassBoostIntensity,
                    onValueChange = {
                        viewModel.setBassBoostIntensity(it)
                        if (!viewModel.effectsState.isBassBoostEnabled) viewModel.toggleBassBoost()
                    },
                    valueRange = 0f..5.0f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                val presets = remember {
                    listOf(
                        0.5f to "50%",
                        1.0f to "100%",
                        2.0f to "200%",
                        5.0f to "500%"
                    )
                }
                val selectedPreset = presets.firstOrNull {
                    kotlin.math.abs(it.first - viewModel.effectsState.bassBoostIntensity) < 0.05f
                }
                ExpressiveConnectedButtonGroup(
                    options = presets,
                    selectedOption = selectedPreset,
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                    onOptionSelected = { (value, _) ->
                        viewModel.setBassBoostIntensity(value)
                        if (!viewModel.effectsState.isBassBoostEnabled) viewModel.toggleBassBoost()
                    },
                    labelProvider = { (_, label) ->
                        Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_ok")) } }
    )
}

@Composable
private fun EarrapeDialog(viewModel: PlayerViewModel, onShowWarning: () -> Unit, onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Rounded.VolumeUp, null) },
        title = { Text(str("btn_earrape")) },
        text = {
            Column {
                Text(
                    str("label_intensity", (viewModel.effectsState.earrapeIntensity * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(16.dp))
                Slider(
                    value = viewModel.effectsState.earrapeIntensity,
                    onValueChange = {
                        viewModel.setEarrapeIntensity(it)
                        if (!viewModel.effectsState.isEarrapeEnabled) {
                            if (!viewModel.hasSeenEarrapeWarning()) onShowWarning() else viewModel.toggleEarrape()
                        }
                    },
                    valueRange = 0f..5.0f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                val presets = remember {
                    listOf(
                        0.5f to "50%",
                        1.0f to "100%",
                        2.0f to "200%",
                        5.0f to "500%"
                    )
                }
                val selectedPreset = presets.firstOrNull {
                    kotlin.math.abs(it.first - viewModel.effectsState.earrapeIntensity) < 0.05f
                }
                ExpressiveConnectedButtonGroup(
                    options = presets,
                    selectedOption = selectedPreset,
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                    onOptionSelected = { (value, _) ->
                        viewModel.setEarrapeIntensity(value)
                        if (!viewModel.effectsState.isEarrapeEnabled) {
                            if (!viewModel.hasSeenEarrapeWarning()) onShowWarning() else viewModel.toggleEarrape()
                        }
                    },
                    labelProvider = { (_, label) ->
                        Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_ok")) } }
    )
}

@Composable
private fun EarrapeWarningDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    var countdown by remember { mutableStateOf(5) }
    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
    }
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str("warning_title")) },
        text = { Text(str("earrape_warning")) },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.setHasSeenEarrapeWarning(true)
                    viewModel.toggleEarrape()
                    onDismiss()
                },
                enabled = countdown == 0
            ) {
                Text(if (countdown > 0) "${str("btn_ok")} (${countdown}s)" else str("btn_ok"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str("btn_cancel")) } }
    )
}

@Composable
private fun EightDDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.SurroundSound, null) },
        title = { Text(str("effect_8d")) },
        text = {
            Column {
                Text(
                    str("label_speed_8d", (viewModel.effectsState.eightDSpeed * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(16.dp))
                Slider(
                    value = viewModel.effectsState.eightDSpeed,
                    onValueChange = {
                        viewModel.setEightDSpeed(it)
                        if (!viewModel.effectsState.is8DEnabled) viewModel.toggle8D()
                    },
                    valueRange = 0f..1f
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_ok")) } }
    )
}

@Composable
private fun MuffledDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.BlurOn, null) },
        title = { Text(str("effect_muffled")) },
        text = {
            Column {
                Text(
                    str("label_cutoff", (viewModel.effectsState.muffledIntensity * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(16.dp))
                Slider(
                    value = viewModel.effectsState.muffledIntensity,
                    onValueChange = {
                        viewModel.setMuffledIntensity(it)
                        if (!viewModel.effectsState.isMuffledEnabled) viewModel.toggleMuffled()
                    },
                    valueRange = 0f..1f
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_ok")) } }
    )
}

@Composable
private fun ReverbDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.GraphicEq, null) },
        title = { Text(str("effect_reverb")) },
        text = {
            Column {
                Text(
                    str("label_intensity", (viewModel.effectsState.reverbIntensity * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(16.dp))
                Slider(
                    value = viewModel.effectsState.reverbIntensity,
                    onValueChange = {
                        viewModel.setReverbIntensity(it)
                        if (!viewModel.effectsState.isReverbEnabled) viewModel.toggleReverb()
                    },
                    valueRange = 0f..1f
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_ok")) } }
    )
}

@Composable
private fun AmbientSoundscapeDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.WaterDrop, null) },
        title = { Text(str("effect_ambient_sound")) },
        text = {
            Column {
                val currentType = viewModel.effectsState.ambientType
                val volume = viewModel.effectsState.rainVolume

                val rainLabel = str("ambient_rain")
                val fireLabel = str("ambient_fireplace")
                val oceanLabel = str("ambient_ocean")
                val cafeLabel = str("ambient_cafe")

                val ambientOptions = remember(rainLabel, fireLabel, oceanLabel, cafeLabel) {
                    listOf(
                        "rain" to rainLabel,
                        "fireplace" to fireLabel,
                        "ocean" to oceanLabel,
                        "cafe" to cafeLabel
                    )
                }
                val selectedOption = ambientOptions.firstOrNull { it.first == currentType } ?: ambientOptions.first()

                ExpressiveConnectedButtonGroup(
                    options = ambientOptions,
                    selectedOption = selectedOption,
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                    onOptionSelected = { (type, _) ->
                        viewModel.setAmbientType(type)
                        if (!viewModel.effectsState.isRainEnabled) viewModel.toggleRain()
                    },
                    labelProvider = { (_, label) ->
                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                Text(
                    str("label_ambient_volume", (volume * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = volume,
                    onValueChange = {
                        viewModel.setRainVolume(it)
                        if (!viewModel.effectsState.isRainEnabled) viewModel.toggleRain()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = str("fx_ambient_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_ok")) } }
    )
}

@Composable
private fun NormalizationDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Rounded.VolumeDown, null) },
        title = { Text(str("pref_norm_title")) },
        text = {
            Column {
                Text(str("pref_norm_sub"), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                val normOptions = listOf(
                    NormalizationLevel.QUIET to str("pref_norm_level_quiet"),
                    NormalizationLevel.NORMAL to str("pref_norm_level_normal"),
                    NormalizationLevel.LOUD to str("pref_norm_level_loud")
                )
                val selectedOption = normOptions.firstOrNull { it.first == viewModel.effectsState.normalizationLevel } ?: normOptions[1]
                ExpressiveConnectedButtonGroup(
                    options = normOptions,
                    selectedOption = selectedOption,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    onOptionSelected = { (level, _) ->
                        viewModel.setNormalizationLevel(level)
                        if (!viewModel.effectsState.isNormalizationEnabled) viewModel.toggleNormalization()
                    },
                    labelProvider = { (_, label) ->
                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_ok")) } }
    )
}

@Composable
private fun VintageMp3Dialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Radio, null) },
        title = { Text(str("effect_vintage_mp3")) },
        text = {
            Column {
                val compression = viewModel.effectsState.vintageMp3Compression
                val percent = (compression * 100).toInt()
                val bitrateDesc = when {
                    compression < 0.15f -> "128 kbps"
                    compression < 0.40f -> "64 kbps"
                    compression < 0.65f -> "32 kbps"
                    compression < 0.90f -> "16 kbps"
                    else -> "8 kbps"
                }
                Text(
                    str("label_vintage_mp3_compression", percent, bitrateDesc),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(16.dp))
                Slider(
                    value = compression,
                    onValueChange = {
                        viewModel.setVintageMp3Compression(it)
                        if (!viewModel.effectsState.isVintageMp3Enabled) viewModel.toggleVintageMp3()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                val presets = remember {
                    listOf(
                        0.25f to "64k",
                        0.50f to "32k",
                        0.75f to "16k",
                        1.00f to "8k"
                    )
                }
                val selectedPreset = presets.firstOrNull {
                    kotlin.math.abs(it.first - compression) < 0.12f
                }
                ExpressiveConnectedButtonGroup(
                    options = presets,
                    selectedOption = selectedPreset,
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                    onOptionSelected = { (value, _) ->
                        viewModel.setVintageMp3Compression(value)
                        if (!viewModel.effectsState.isVintageMp3Enabled) viewModel.toggleVintageMp3()
                    },
                    labelProvider = { (_, label) ->
                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = str("fx_vintage_mp3_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_ok")) } }
    )
}

@Composable
private fun VocalRemoverDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.MicOff, null) },
        title = { Text(str("effect_vocal_remover")) },
        text = {
            Column {
                val level = viewModel.effectsState.vocalRemoverLevel
                val percent = (level * 100).toInt()
                Text(
                    str("label_vocal_remover_level", percent),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(16.dp))
                Slider(
                    value = level,
                    onValueChange = {
                        viewModel.setVocalRemoverLevel(it)
                        if (!viewModel.effectsState.isVocalRemoverEnabled) viewModel.toggleVocalRemover()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                val presets = remember {
                    listOf(
                        0.50f to "50%",
                        0.80f to "80%",
                        1.00f to "100%"
                    )
                }
                val selectedPreset = presets.firstOrNull {
                    kotlin.math.abs(it.first - level) < 0.05f
                }
                ExpressiveConnectedButtonGroup(
                    options = presets,
                    selectedOption = selectedPreset,
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                    onOptionSelected = { (value, _) ->
                        viewModel.setVocalRemoverLevel(value)
                        if (!viewModel.effectsState.isVocalRemoverEnabled) viewModel.toggleVocalRemover()
                    },
                    labelProvider = { (_, label) ->
                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = str("fx_vocal_remover_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_ok")) } }
    )
}

@Composable
private fun VocalBoostDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.RecordVoiceOver, null) },
        title = { Text(str("effect_vocal_boost")) },
        text = {
            Column {
                val level = viewModel.effectsState.vocalBoostIntensity
                val percent = (level * 100).toInt()
                Text(
                    str("label_vocal_boost_level", percent),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(16.dp))
                Slider(
                    value = level,
                    onValueChange = {
                        viewModel.setVocalBoostIntensity(it)
                        if (!viewModel.effectsState.isVocalBoostEnabled) viewModel.toggleVocalBoost()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                val presets = remember {
                    listOf(
                        0.50f to "50%",
                        0.75f to "75%",
                        1.00f to "100%"
                    )
                }
                val selectedPreset = presets.firstOrNull {
                    kotlin.math.abs(it.first - level) < 0.05f
                }
                ExpressiveConnectedButtonGroup(
                    options = presets,
                    selectedOption = selectedPreset,
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                    onOptionSelected = { (value, _) ->
                        viewModel.setVocalBoostIntensity(value)
                        if (!viewModel.effectsState.isVocalBoostEnabled) viewModel.toggleVocalBoost()
                    },
                    labelProvider = { (_, label) ->
                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = str("fx_vocal_boost_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_ok")) } }
    )
}

@Composable
private fun FlangerDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Air, null) },
        title = { Text(str("effect_flanger")) },
        text = {
            Column {
                val intensity = viewModel.effectsState.flangerIntensity
                val speed = viewModel.effectsState.flangerSpeed

                Text(
                    str("label_flanger_intensity", (intensity * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = intensity,
                    onValueChange = {
                        viewModel.setFlangerIntensity(it)
                        if (!viewModel.effectsState.isFlangerEnabled) viewModel.toggleFlanger()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                Text(
                    str("label_flanger_speed", (speed * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = speed,
                    onValueChange = {
                        viewModel.setFlangerSpeed(it)
                        if (!viewModel.effectsState.isFlangerEnabled) viewModel.toggleFlanger()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                val slowLabel = str("preset_flanger_slow")
                val classicLabel = str("preset_flanger_classic")
                val turbineLabel = str("preset_flanger_turbine")
                val presets = remember(slowLabel, classicLabel, turbineLabel) {
                    listOf(
                        Triple(0.60f, 0.25f, slowLabel),
                        Triple(0.75f, 0.50f, classicLabel),
                        Triple(0.95f, 0.85f, turbineLabel)
                    )
                }
                val selectedPreset = presets.firstOrNull {
                    kotlin.math.abs(it.first - intensity) < 0.1f && kotlin.math.abs(it.second - speed) < 0.1f
                }
                ExpressiveConnectedButtonGroup(
                    options = presets,
                    selectedOption = selectedPreset,
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                    onOptionSelected = { (presetIntensity, presetSpeed, _) ->
                        viewModel.setFlangerIntensity(presetIntensity)
                        viewModel.setFlangerSpeed(presetSpeed)
                        if (!viewModel.effectsState.isFlangerEnabled) viewModel.toggleFlanger()
                    },
                    labelProvider = { (_, _, label) ->
                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = str("fx_flanger_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_ok")) } }
    )
}

@Composable
private fun PartyNextDoorDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.MeetingRoom, null) },
        title = { Text(str("effect_party_next_door")) },
        text = {
            Column {
                val isolation = viewModel.effectsState.partyNextDoorIsolation
                val reverb = viewModel.effectsState.partyNextDoorReverb
                val rumble = viewModel.effectsState.partyNextDoorBassRumble

                Text(
                    str("label_pnd_isolation", (isolation * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = isolation,
                    onValueChange = {
                        viewModel.setPartyNextDoorIsolation(it)
                        if (!viewModel.effectsState.isPartyNextDoorEnabled) viewModel.togglePartyNextDoor()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))

                Text(
                    str("label_pnd_reverb", (reverb * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = reverb,
                    onValueChange = {
                        viewModel.setPartyNextDoorReverb(it)
                        if (!viewModel.effectsState.isPartyNextDoorEnabled) viewModel.togglePartyNextDoor()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))

                Text(
                    str("label_pnd_rumble", (rumble * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = rumble,
                    onValueChange = {
                        viewModel.setPartyNextDoorBassRumble(it)
                        if (!viewModel.effectsState.isPartyNextDoorEnabled) viewModel.togglePartyNextDoor()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                val bathroomLabel = str("preset_pnd_bathroom")
                val hallwayLabel = str("preset_pnd_hallway")
                val nextDoorLabel = str("preset_pnd_next_door")
                val presets = remember(bathroomLabel, hallwayLabel, nextDoorLabel) {
                    listOf(
                        Triple(0.60f, 0.70f, bathroomLabel),
                        Triple(0.30f, 0.35f, hallwayLabel),
                        Triple(0.90f, 0.45f, nextDoorLabel)
                    )
                }
                val selectedPreset = presets.firstOrNull {
                    kotlin.math.abs(it.first - isolation) < 0.1f && kotlin.math.abs(it.second - reverb) < 0.1f
                }
                ExpressiveConnectedButtonGroup(
                    options = presets,
                    selectedOption = selectedPreset,
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                    onOptionSelected = { (presetIso, presetRev, _) ->
                        val targetRumble = when {
                            presetIso > 0.8f -> 0.90f
                            presetIso < 0.4f -> 0.50f
                            else -> 0.70f
                        }
                        viewModel.setPartyNextDoorIsolation(presetIso)
                        viewModel.setPartyNextDoorReverb(presetRev)
                        viewModel.setPartyNextDoorBassRumble(targetRumble)
                        if (!viewModel.effectsState.isPartyNextDoorEnabled) viewModel.togglePartyNextDoor()
                    },
                    labelProvider = { (_, _, label) ->
                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = str("fx_party_next_door_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_ok")) } }
    )
}

@Composable
private fun SuperWideDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.SurroundSound, null) },
        title = { Text(str("effect_super_wide")) },
        text = {
            Column {
                val width = viewModel.effectsState.superWideWidth
                val depth = viewModel.effectsState.superWideDepth

                Text(
                    str("label_sw_width", (width * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = width,
                    onValueChange = {
                        viewModel.setSuperWideWidth(it)
                        if (!viewModel.effectsState.isSuperWideEnabled) viewModel.toggleSuperWide()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))

                Text(
                    str("label_sw_depth", (depth * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = depth,
                    onValueChange = {
                        viewModel.setSuperWideDepth(it)
                        if (!viewModel.effectsState.isSuperWideEnabled) viewModel.toggleSuperWide()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                val naturalLabel = str("preset_sw_natural")
                val cinematicLabel = str("preset_sw_cinematic")
                val holographicLabel = str("preset_sw_holographic")
                val presets = remember(naturalLabel, cinematicLabel, holographicLabel) {
                    listOf(
                        Triple(0.45f, 0.35f, naturalLabel),
                        Triple(0.70f, 0.60f, cinematicLabel),
                        Triple(1.00f, 0.85f, holographicLabel)
                    )
                }
                val selectedPreset = presets.firstOrNull {
                    kotlin.math.abs(it.first - width) < 0.08f && kotlin.math.abs(it.second - depth) < 0.08f
                }
                ExpressiveConnectedButtonGroup(
                    options = presets,
                    selectedOption = selectedPreset,
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                    onOptionSelected = { (presetWidth, presetDepth, _) ->
                        viewModel.setSuperWideWidth(presetWidth)
                        viewModel.setSuperWideDepth(presetDepth)
                        if (!viewModel.effectsState.isSuperWideEnabled) viewModel.toggleSuperWide()
                    },
                    labelProvider = { (_, _, label) ->
                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = str("fx_super_wide_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_ok")) } }
    )
}

@Composable
private fun VinylLoFiDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Album, null) },
        title = { Text(str("effect_vinyl_lofi")) },
        text = {
            Column {
                val crackles = viewModel.effectsState.vinylCrackles
                val flutter = viewModel.effectsState.vinylFlutter

                Text(
                    str("label_vinyl_crackles", (crackles * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = crackles,
                    onValueChange = {
                        viewModel.setVinylCrackles(it)
                        if (!viewModel.effectsState.isVinylLoFiEnabled) viewModel.toggleVinylLoFi()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))

                Text(
                    str("label_vinyl_flutter", (flutter * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = flutter,
                    onValueChange = {
                        viewModel.setVinylFlutter(it)
                        if (!viewModel.effectsState.isVinylLoFiEnabled) viewModel.toggleVinylLoFi()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                val chillLabel = str("preset_vinyl_chill")
                val vintageLabel = str("preset_vinyl_vintage")
                val cassetteLabel = str("preset_vinyl_cassette")
                val presets = remember(chillLabel, vintageLabel, cassetteLabel) {
                    listOf(
                        Triple(0.40f, 0.35f, chillLabel),
                        Triple(0.75f, 0.45f, vintageLabel),
                        Triple(0.35f, 0.80f, cassetteLabel)
                    )
                }
                val selectedPreset = presets.firstOrNull {
                    kotlin.math.abs(it.first - crackles) < 0.08f && kotlin.math.abs(it.second - flutter) < 0.08f
                }
                ExpressiveConnectedButtonGroup(
                    options = presets,
                    selectedOption = selectedPreset,
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                    onOptionSelected = { (presetCrackles, presetFlutter, _) ->
                        viewModel.setVinylCrackles(presetCrackles)
                        viewModel.setVinylFlutter(presetFlutter)
                        if (!viewModel.effectsState.isVinylLoFiEnabled) viewModel.toggleVinylLoFi()
                    },
                    labelProvider = { (_, _, label) ->
                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = str("fx_vinyl_lofi_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_ok")) } }
    )
}

@Composable
private fun PhaserDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Waves, null) },
        title = { Text(str("effect_phaser")) },
        text = {
            Column {
                val speed = viewModel.effectsState.phaserSpeed
                val feedback = viewModel.effectsState.phaserFeedback

                Text(
                    str("label_phaser_speed", (speed * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = speed,
                    onValueChange = {
                        viewModel.setPhaserSpeed(it)
                        if (!viewModel.effectsState.isPhaserEnabled) viewModel.togglePhaser()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))

                Text(
                    str("label_phaser_feedback", (feedback * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = feedback,
                    onValueChange = {
                        viewModel.setPhaserFeedback(it)
                        if (!viewModel.effectsState.isPhaserEnabled) viewModel.togglePhaser()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                val cosmicLabel = str("preset_phaser_cosmic")
                val liquidLabel = str("preset_phaser_liquid")
                val daftLabel = str("preset_phaser_daft")
                val presets = remember(cosmicLabel, liquidLabel, daftLabel) {
                    listOf(
                        Triple(0.25f, 0.60f, cosmicLabel),
                        Triple(0.50f, 0.75f, liquidLabel),
                        Triple(0.80f, 0.85f, daftLabel)
                    )
                }
                val selectedPreset = presets.firstOrNull {
                    kotlin.math.abs(it.first - speed) < 0.08f && kotlin.math.abs(it.second - feedback) < 0.08f
                }
                ExpressiveConnectedButtonGroup(
                    options = presets,
                    selectedOption = selectedPreset,
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                    onOptionSelected = { (presetSpeed, presetFeedback, _) ->
                        viewModel.setPhaserSpeed(presetSpeed)
                        viewModel.setPhaserFeedback(presetFeedback)
                        if (!viewModel.effectsState.isPhaserEnabled) viewModel.togglePhaser()
                    },
                    labelProvider = { (_, _, label) ->
                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = str("fx_phaser_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_ok")) } }
    )
}

@Composable
private fun MegaphoneDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Campaign, null) },
        title = { Text(str("effect_megaphone")) },
        text = {
            Column {
                val tone = viewModel.effectsState.megaphoneTone
                val drive = viewModel.effectsState.megaphoneDrive

                Text(
                    str("label_megaphone_tone", (tone * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = tone,
                    onValueChange = {
                        viewModel.setMegaphoneTone(it)
                        if (!viewModel.effectsState.isMegaphoneEnabled) viewModel.toggleMegaphone()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))

                Text(
                    str("label_megaphone_drive", (drive * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = drive,
                    onValueChange = {
                        viewModel.setMegaphoneDrive(it)
                        if (!viewModel.effectsState.isMegaphoneEnabled) viewModel.toggleMegaphone()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                val hornLabel = str("preset_megaphone_horn")
                val radioLabel = str("preset_megaphone_radio")
                val walkieLabel = str("preset_megaphone_walkie")
                val presets = remember(hornLabel, radioLabel, walkieLabel) {
                    listOf(
                        Triple(1.00f, 0.85f, hornLabel),
                        Triple(0.05f, 0.20f, radioLabel),
                        Triple(0.60f, 0.60f, walkieLabel)
                    )
                }
                val selectedPreset = presets.firstOrNull {
                    kotlin.math.abs(it.first - tone) < 0.08f && kotlin.math.abs(it.second - drive) < 0.08f
                }
                ExpressiveConnectedButtonGroup(
                    options = presets,
                    selectedOption = selectedPreset,
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                    onOptionSelected = { (presetTone, presetDrive, _) ->
                        viewModel.setMegaphoneTone(presetTone)
                        viewModel.setMegaphoneDrive(presetDrive)
                        if (!viewModel.effectsState.isMegaphoneEnabled) viewModel.toggleMegaphone()
                    },
                    labelProvider = { (_, _, label) ->
                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = str("fx_megaphone_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_ok")) } }
    )
}

@Composable
private fun RobotVocoderDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.SmartToy, null) },
        title = { Text(str("effect_robot_vocoder")) },
        text = {
            Column {
                val frequency = viewModel.effectsState.robotFrequency
                val mix = viewModel.effectsState.robotMix

                Text(
                    str("label_robot_frequency", (frequency * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = frequency,
                    onValueChange = {
                        viewModel.setRobotFrequency(it)
                        if (!viewModel.effectsState.isRobotVocoderEnabled) viewModel.toggleRobotVocoder()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))

                Text(
                    str("label_robot_mix", (mix * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = mix,
                    onValueChange = {
                        viewModel.setRobotMix(it)
                        if (!viewModel.effectsState.isRobotVocoderEnabled) viewModel.toggleRobotVocoder()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                val cyborgLabel = str("preset_robot_cyborg")
                val daftLabel = str("preset_robot_daft")
                val alienLabel = str("preset_robot_alien")
                val presets = remember(cyborgLabel, daftLabel, alienLabel) {
                    listOf(
                        Triple(0.15f, 0.80f, cyborgLabel),
                        Triple(0.38f, 0.75f, daftLabel),
                        Triple(0.82f, 0.90f, alienLabel)
                    )
                }
                val selectedPreset = presets.firstOrNull {
                    kotlin.math.abs(it.first - frequency) < 0.08f && kotlin.math.abs(it.second - mix) < 0.08f
                }
                ExpressiveConnectedButtonGroup(
                    options = presets,
                    selectedOption = selectedPreset,
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                    onOptionSelected = { (presetFreq, presetMix, _) ->
                        viewModel.setRobotFrequency(presetFreq)
                        viewModel.setRobotMix(presetMix)
                        if (!viewModel.effectsState.isRobotVocoderEnabled) viewModel.toggleRobotVocoder()
                    },
                    labelProvider = { (_, _, label) ->
                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = str("fx_robot_vocoder_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_ok")) } }
    )
}

@Composable
private fun ChorusDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Grain, null) },
        title = { Text(str("effect_chorus")) },
        text = {
            Column {
                val rate = viewModel.effectsState.chorusRate
                val depth = viewModel.effectsState.chorusDepth

                Text(
                    str("label_chorus_rate", (rate * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = rate,
                    onValueChange = {
                        viewModel.setChorusRate(it)
                        if (!viewModel.effectsState.isChorusEnabled) viewModel.toggleChorus()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))

                Text(
                    str("label_chorus_depth", (depth * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = depth,
                    onValueChange = {
                        viewModel.setChorusDepth(it)
                        if (!viewModel.effectsState.isChorusEnabled) viewModel.toggleChorus()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                val junoLabel = str("preset_chorus_juno")
                val dreamLabel = str("preset_chorus_dream")
                val shimmerLabel = str("preset_chorus_shimmer")
                val presets = remember(junoLabel, dreamLabel, shimmerLabel) {
                    listOf(
                        Triple(0.30f, 0.70f, junoLabel),
                        Triple(0.15f, 0.90f, dreamLabel),
                        Triple(0.75f, 0.45f, shimmerLabel)
                    )
                }
                val selectedPreset = presets.firstOrNull {
                    kotlin.math.abs(it.first - rate) < 0.08f && kotlin.math.abs(it.second - depth) < 0.08f
                }
                ExpressiveConnectedButtonGroup(
                    options = presets,
                    selectedOption = selectedPreset,
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                    onOptionSelected = { (pRate, pDepth, _) ->
                        viewModel.setChorusRate(pRate)
                        viewModel.setChorusDepth(pDepth)
                        if (!viewModel.effectsState.isChorusEnabled) viewModel.toggleChorus()
                    },
                    labelProvider = { (_, _, label) ->
                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = str("fx_chorus_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_ok")) } }
    )
}

@Composable
private fun UnderwaterDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Waves, null) },
        title = { Text(str("effect_underwater")) },
        text = {
            Column {
                val depth = viewModel.effectsState.underwaterDepth
                val bubbles = viewModel.effectsState.underwaterBubbles

                Text(
                    str("label_underwater_depth", (depth * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = depth,
                    onValueChange = {
                        viewModel.setUnderwaterDepth(it)
                        if (!viewModel.effectsState.isUnderwaterEnabled) viewModel.toggleUnderwater()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))

                Text(
                    str("label_underwater_bubbles", (bubbles * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = bubbles,
                    onValueChange = {
                        viewModel.setUnderwaterBubbles(it)
                        if (!viewModel.effectsState.isUnderwaterEnabled) viewModel.toggleUnderwater()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                val submergedLabel = str("preset_underwater_submerged")
                val abyssLabel = str("preset_underwater_abyss")
                val scubaLabel = str("preset_underwater_scuba")
                val presets = remember(submergedLabel, abyssLabel, scubaLabel) {
                    listOf(
                        Triple(0.45f, 0.30f, submergedLabel),
                        Triple(0.85f, 0.60f, abyssLabel),
                        Triple(0.50f, 0.90f, scubaLabel)
                    )
                }
                val selectedPreset = presets.firstOrNull {
                    kotlin.math.abs(it.first - depth) < 0.08f && kotlin.math.abs(it.second - bubbles) < 0.08f
                }
                ExpressiveConnectedButtonGroup(
                    options = presets,
                    selectedOption = selectedPreset,
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                    onOptionSelected = { (pDepth, pBubbles, _) ->
                        viewModel.setUnderwaterDepth(pDepth)
                        viewModel.setUnderwaterBubbles(pBubbles)
                        if (!viewModel.effectsState.isUnderwaterEnabled) viewModel.toggleUnderwater()
                    },
                    labelProvider = { (_, _, label) ->
                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = str("fx_underwater_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_ok")) } }
    )
}

@Composable
private fun TranceGateDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.ElectricBolt, null) },
        title = { Text(str("effect_trance_gate")) },
        text = {
            Column {
                val speed = viewModel.effectsState.tranceGateSpeed
                val pattern = viewModel.effectsState.tranceGatePattern
                val mix = viewModel.effectsState.tranceGateMix

                Text(
                    str("label_trance_gate_speed", (speed * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = speed,
                    onValueChange = {
                        viewModel.setTranceGateSpeed(it)
                        if (!viewModel.effectsState.isTranceGateEnabled) viewModel.toggleTranceGate()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    str("label_trance_gate_pattern", (pattern * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = pattern,
                    onValueChange = {
                        viewModel.setTranceGatePattern(it)
                        if (!viewModel.effectsState.isTranceGateEnabled) viewModel.toggleTranceGate()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    str("label_trance_gate_mix", (mix * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = mix,
                    onValueChange = {
                        viewModel.setTranceGateMix(it)
                        if (!viewModel.effectsState.isTranceGateEnabled) viewModel.toggleTranceGate()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                val t16Label = str("preset_trance_gate_16")
                val dropLabel = str("preset_trance_gate_drop")
                val tremoloLabel = str("preset_trance_gate_tremolo")
                val presets = remember(t16Label, dropLabel, tremoloLabel) {
                    listOf(
                        Triple(0.65f, 0.90f, t16Label),
                        Triple(0.40f, 1.00f, dropLabel),
                        Triple(0.30f, 0.05f, tremoloLabel)
                    )
                }
                val selectedPreset = presets.firstOrNull {
                    kotlin.math.abs(it.first - speed) < 0.08f && kotlin.math.abs(it.second - pattern) < 0.08f
                }
                ExpressiveConnectedButtonGroup(
                    options = presets,
                    selectedOption = selectedPreset,
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                    onOptionSelected = { (pSpeed, pPattern, _) ->
                        viewModel.setTranceGateSpeed(pSpeed)
                        viewModel.setTranceGatePattern(pPattern)
                        viewModel.setTranceGateMix(0.90f)
                        if (!viewModel.effectsState.isTranceGateEnabled) viewModel.toggleTranceGate()
                    },
                    labelProvider = { (_, _, label) ->
                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = str("fx_trance_gate_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_ok")) } }
    )
}

@Composable
private fun PingPongDelayDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.SyncAlt, null) },
        title = { Text(str("effect_ping_pong")) },
        text = {
            Column {
                val delayTime = viewModel.effectsState.pingPongDelayTime
                val feedback = viewModel.effectsState.pingPongFeedback

                Text(
                    str("label_ping_pong_time", (delayTime * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = delayTime,
                    onValueChange = {
                        viewModel.setPingPongDelayTime(it)
                        if (!viewModel.effectsState.isPingPongDelayEnabled) viewModel.togglePingPongDelay()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))

                Text(
                    str("label_ping_pong_feedback", (feedback * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = feedback,
                    onValueChange = {
                        viewModel.setPingPongFeedback(it)
                        if (!viewModel.effectsState.isPingPongDelayEnabled) viewModel.togglePingPongDelay()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                val dubLabel = str("preset_ping_pong_dub")
                val bounceLabel = str("preset_ping_pong_bounce")
                val slapLabel = str("preset_ping_pong_slap")
                val presets = remember(dubLabel, bounceLabel, slapLabel) {
                    listOf(
                        Triple(0.45f, 0.65f, dubLabel),
                        Triple(0.70f, 0.72f, bounceLabel),
                        Triple(0.10f, 0.35f, slapLabel)
                    )
                }
                val selectedPreset = presets.firstOrNull {
                    kotlin.math.abs(it.first - delayTime) < 0.08f && kotlin.math.abs(it.second - feedback) < 0.08f
                }
                ExpressiveConnectedButtonGroup(
                    options = presets,
                    selectedOption = selectedPreset,
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                    onOptionSelected = { (pTime, pFb, _) ->
                        viewModel.setPingPongDelayTime(pTime)
                        viewModel.setPingPongFeedback(pFb)
                        if (!viewModel.effectsState.isPingPongDelayEnabled) viewModel.togglePingPongDelay()
                    },
                    labelProvider = { (_, _, label) ->
                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = str("fx_ping_pong_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_ok")) } }
    )
}

@Composable
private fun ChiptuneDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Gamepad, null) },
        title = { Text(str("effect_chiptune")) },
        text = {
            Column {
                val bits = viewModel.effectsState.chiptuneBits
                val sr = viewModel.effectsState.chiptuneSampleRate

                Text(
                    str("label_chiptune_bits", (bits * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = bits,
                    onValueChange = {
                        viewModel.setChiptuneBits(it)
                        if (!viewModel.effectsState.isChiptuneEnabled) viewModel.toggleChiptune()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))

                Text(
                    str("label_chiptune_sr", (sr * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = sr,
                    onValueChange = {
                        viewModel.setChiptuneSampleRate(it)
                        if (!viewModel.effectsState.isChiptuneEnabled) viewModel.toggleChiptune()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                val gameboyLabel = str("preset_chiptune_gameboy")
                val nesLabel = str("preset_chiptune_nes")
                val arcadeLabel = str("preset_chiptune_arcade")
                val presets = remember(gameboyLabel, nesLabel, arcadeLabel) {
                    listOf(
                        Triple(0.45f, 0.40f, gameboyLabel),
                        Triple(0.70f, 0.65f, nesLabel),
                        Triple(0.90f, 0.85f, arcadeLabel)
                    )
                }
                val selectedPreset = presets.firstOrNull {
                    kotlin.math.abs(it.first - bits) < 0.08f && kotlin.math.abs(it.second - sr) < 0.08f
                }
                ExpressiveConnectedButtonGroup(
                    options = presets,
                    selectedOption = selectedPreset,
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                    onOptionSelected = { (pBits, pSr, _) ->
                        viewModel.setChiptuneBits(pBits)
                        viewModel.setChiptuneSampleRate(pSr)
                        if (!viewModel.effectsState.isChiptuneEnabled) viewModel.toggleChiptune()
                    },
                    labelProvider = { (_, _, label) ->
                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = str("fx_chiptune_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_ok")) } }
    )
}

@Composable
private fun ShimmerReverbDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Flare, null) },
        title = { Text(str("effect_shimmer_reverb")) },
        text = {
            Column {
                val size = viewModel.effectsState.shimmerSize
                val mix = viewModel.effectsState.shimmerMix

                Text(
                    str("label_shimmer_size", (size * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = size,
                    onValueChange = {
                        viewModel.setShimmerSize(it)
                        if (!viewModel.effectsState.isShimmerReverbEnabled) viewModel.toggleShimmerReverb()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))

                Text(
                    str("label_shimmer_mix", (mix * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = mix,
                    onValueChange = {
                        viewModel.setShimmerMix(it)
                        if (!viewModel.effectsState.isShimmerReverbEnabled) viewModel.toggleShimmerReverb()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                val cloudLabel = str("preset_shimmer_cloud")
                val angelLabel = str("preset_shimmer_angel")
                val hallLabel = str("preset_shimmer_hall")
                val presets = remember(cloudLabel, angelLabel, hallLabel) {
                    listOf(
                        Triple(0.65f, 0.60f, cloudLabel),
                        Triple(0.85f, 0.85f, angelLabel),
                        Triple(0.50f, 0.25f, hallLabel)
                    )
                }
                val selectedPreset = presets.firstOrNull {
                    kotlin.math.abs(it.first - size) < 0.08f && kotlin.math.abs(it.second - mix) < 0.08f
                }
                ExpressiveConnectedButtonGroup(
                    options = presets,
                    selectedOption = selectedPreset,
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                    onOptionSelected = { (pSize, pMix, _) ->
                        viewModel.setShimmerSize(pSize)
                        viewModel.setShimmerMix(pMix)
                        if (!viewModel.effectsState.isShimmerReverbEnabled) viewModel.toggleShimmerReverb()
                    },
                    labelProvider = { (_, _, label) ->
                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = str("fx_shimmer_reverb_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_ok")) } }
    )
}

@Composable
private fun RotarySpeakerDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Rounded.RotateRight, null) },
        title = { Text(str("effect_rotary_speaker")) },
        text = {
            Column {
                val speed = viewModel.effectsState.rotarySpeed
                val depth = viewModel.effectsState.rotaryDepth

                Text(
                    str("label_rotary_speed", (speed * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = speed,
                    onValueChange = {
                        viewModel.setRotarySpeed(it)
                        if (!viewModel.effectsState.isRotarySpeakerEnabled) viewModel.toggleRotarySpeaker()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))

                Text(
                    str("label_rotary_depth", (depth * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = depth,
                    onValueChange = {
                        viewModel.setRotaryDepth(it)
                        if (!viewModel.effectsState.isRotarySpeakerEnabled) viewModel.toggleRotarySpeaker()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                val choraleLabel = str("preset_rotary_chorale")
                val tremoloLabel = str("preset_rotary_tremolo")
                val psychLabel = str("preset_rotary_psychedelic")
                val presets = remember(choraleLabel, tremoloLabel, psychLabel) {
                    listOf(
                        Triple(0.15f, 0.65f, choraleLabel),
                        Triple(0.75f, 0.80f, tremoloLabel),
                        Triple(0.50f, 0.95f, psychLabel)
                    )
                }
                val selectedPreset = presets.firstOrNull {
                    kotlin.math.abs(it.first - speed) < 0.08f && kotlin.math.abs(it.second - depth) < 0.08f
                }
                ExpressiveConnectedButtonGroup(
                    options = presets,
                    selectedOption = selectedPreset,
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                    onOptionSelected = { (pSpeed, pDepth, _) ->
                        viewModel.setRotarySpeed(pSpeed)
                        viewModel.setRotaryDepth(pDepth)
                        if (!viewModel.effectsState.isRotarySpeakerEnabled) viewModel.toggleRotarySpeaker()
                    },
                    labelProvider = { (_, _, label) ->
                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = str("fx_rotary_speaker_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_ok")) } }
    )
}

@Composable
private fun TapeSaturationDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Whatshot, null) },
        title = { Text(str("effect_tape_saturation")) },
        text = {
            Column {
                val warmth = viewModel.effectsState.tapeWarmth
                val exciter = viewModel.effectsState.tapeExciter

                Text(
                    str("label_tape_warmth", (warmth * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = warmth,
                    onValueChange = {
                        viewModel.setTapeWarmth(it)
                        if (!viewModel.effectsState.isTapeSaturationEnabled) viewModel.toggleTapeSaturation()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))

                Text(
                    str("label_tape_exciter", (exciter * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = exciter,
                    onValueChange = {
                        viewModel.setTapeExciter(it)
                        if (!viewModel.effectsState.isTapeSaturationEnabled) viewModel.toggleTapeSaturation()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                val warmLabel = str("preset_tape_warm")
                val studerLabel = str("preset_tape_studer")
                val exciterLabel = str("preset_tape_exciter")
                val presets = remember(warmLabel, studerLabel, exciterLabel) {
                    listOf(
                        Triple(0.60f, 0.40f, warmLabel),
                        Triple(0.80f, 0.65f, studerLabel),
                        Triple(0.40f, 0.90f, exciterLabel)
                    )
                }
                val selectedPreset = presets.firstOrNull {
                    kotlin.math.abs(it.first - warmth) < 0.08f && kotlin.math.abs(it.second - exciter) < 0.08f
                }
                ExpressiveConnectedButtonGroup(
                    options = presets,
                    selectedOption = selectedPreset,
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                    onOptionSelected = { (pWarmth, pExciter, _) ->
                        viewModel.setTapeWarmth(pWarmth)
                        viewModel.setTapeExciter(pExciter)
                        if (!viewModel.effectsState.isTapeSaturationEnabled) viewModel.toggleTapeSaturation()
                    },
                    labelProvider = { (_, _, label) ->
                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = str("fx_tape_saturation_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_ok")) } }
    )
}

@Composable
private fun SubOctaverDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Speaker, null) },
        title = { Text(str("effect_sub_octaver")) },
        text = {
            Column {
                val level = viewModel.effectsState.subOctaverLevel
                val cutoff = viewModel.effectsState.subOctaverCutoff

                Text(
                    str("label_sub_level", (level * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = level,
                    onValueChange = {
                        viewModel.setSubOctaverLevel(it)
                        if (!viewModel.effectsState.isSubOctaverEnabled) viewModel.toggleSubOctaver()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))

                Text(
                    str("label_sub_cutoff", (cutoff * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = cutoff,
                    onValueChange = {
                        viewModel.setSubOctaverCutoff(it)
                        if (!viewModel.effectsState.isSubOctaverEnabled) viewModel.toggleSubOctaver()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                val b808Label = str("preset_sub_808")
                val deepLabel = str("preset_sub_deep")
                val punchLabel = str("preset_sub_punch")
                val presets = remember(b808Label, deepLabel, punchLabel) {
                    listOf(
                        Triple(0.80f, 0.65f, b808Label),
                        Triple(0.65f, 0.20f, deepLabel),
                        Triple(0.90f, 0.85f, punchLabel)
                    )
                }
                val selectedPreset = presets.firstOrNull {
                    kotlin.math.abs(it.first - level) < 0.08f && kotlin.math.abs(it.second - cutoff) < 0.08f
                }
                ExpressiveConnectedButtonGroup(
                    options = presets,
                    selectedOption = selectedPreset,
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                    onOptionSelected = { (pLevel, pCutoff, _) ->
                        viewModel.setSubOctaverLevel(pLevel)
                        viewModel.setSubOctaverCutoff(pCutoff)
                        if (!viewModel.effectsState.isSubOctaverEnabled) viewModel.toggleSubOctaver()
                    },
                    labelProvider = { (_, _, label) ->
                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = str("fx_sub_octaver_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_ok")) } }
    )
}

@Composable
private fun EmptyMallDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Storefront, null) },
        title = { Text(str("effect_empty_mall")) },
        text = {
            Column {
                val distance = viewModel.effectsState.emptyMallDistance
                val reverb = viewModel.effectsState.emptyMallReverb

                Text(
                    str("label_empty_mall_distance", (distance * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = distance,
                    onValueChange = {
                        viewModel.setEmptyMallDistance(it)
                        if (!viewModel.effectsState.isEmptyMallEnabled) viewModel.toggleEmptyMall()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))

                Text(
                    str("label_empty_mall_reverb", (reverb * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = reverb,
                    onValueChange = {
                        viewModel.setEmptyMallReverb(it)
                        if (!viewModel.effectsState.isEmptyMallEnabled) viewModel.toggleEmptyMall()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                val m1995Label = str("preset_mall_1995")
                val distantLabel = str("preset_mall_distant")
                val liminalLabel = str("preset_mall_liminal")
                val presets = remember(m1995Label, distantLabel, liminalLabel) {
                    listOf(
                        Triple(0.65f, 0.60f, m1995Label),
                        Triple(0.90f, 0.75f, distantLabel),
                        Triple(0.45f, 0.85f, liminalLabel)
                    )
                }
                val selectedPreset = presets.firstOrNull {
                    kotlin.math.abs(it.first - distance) < 0.08f && kotlin.math.abs(it.second - reverb) < 0.08f
                }
                ExpressiveConnectedButtonGroup(
                    options = presets,
                    selectedOption = selectedPreset,
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                    onOptionSelected = { (pDist, pRev, _) ->
                        viewModel.setEmptyMallDistance(pDist)
                        viewModel.setEmptyMallReverb(pRev)
                        if (!viewModel.effectsState.isEmptyMallEnabled) viewModel.toggleEmptyMall()
                    },
                    labelProvider = { (_, _, label) ->
                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = str("fx_empty_mall_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_ok")) } }
    )
}

@Composable
private fun GramophoneDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.History, null) },
        title = { Text(str("effect_gramophone")) },
        text = {
            Column {
                val age = viewModel.effectsState.gramophoneAge
                val horn = viewModel.effectsState.gramophoneHorn

                Text(
                    str("label_gramophone_age", (age * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = age,
                    onValueChange = {
                        viewModel.setGramophoneAge(it)
                        if (!viewModel.effectsState.isGramophoneEnabled) viewModel.toggleGramophone()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))

                Text(
                    str("label_gramophone_horn", (horn * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = horn,
                    onValueChange = {
                        viewModel.setGramophoneHorn(it)
                        if (!viewModel.effectsState.isGramophoneEnabled) viewModel.toggleGramophone()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                val shellacLabel = str("preset_gramo_shellac")
                val cylLabel = str("preset_gramo_cylinder")
                val falloutLabel = str("preset_gramo_fallout")
                val presets = remember(shellacLabel, cylLabel, falloutLabel) {
                    listOf(
                        Triple(0.60f, 0.65f, shellacLabel),
                        Triple(0.85f, 0.90f, cylLabel),
                        Triple(0.40f, 0.40f, falloutLabel)
                    )
                }
                val selectedPreset = presets.firstOrNull {
                    kotlin.math.abs(it.first - age) < 0.08f && kotlin.math.abs(it.second - horn) < 0.08f
                }
                ExpressiveConnectedButtonGroup(
                    options = presets,
                    selectedOption = selectedPreset,
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                    onOptionSelected = { (pAge, pHorn, _) ->
                        viewModel.setGramophoneAge(pAge)
                        viewModel.setGramophoneHorn(pHorn)
                        if (!viewModel.effectsState.isGramophoneEnabled) viewModel.toggleGramophone()
                    },
                    labelProvider = { (_, _, label) ->
                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = str("fx_gramophone_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_ok")) } }
    )
}

@Composable
private fun ReverseEchoDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Rounded.CompareArrows, null) },
        title = { Text(str("effect_reverse_echo")) },
        text = {
            Column {
                val time = viewModel.effectsState.reverseEchoTime
                val fb = viewModel.effectsState.reverseEchoFeedback

                Text(
                    str("label_reverse_time", (time * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = time,
                    onValueChange = {
                        viewModel.setReverseEchoTime(it)
                        if (!viewModel.effectsState.isReverseEchoEnabled) viewModel.toggleReverseEcho()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))

                Text(
                    str("label_reverse_feedback", (fb * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = fb,
                    onValueChange = {
                        viewModel.setReverseEchoFeedback(it)
                        if (!viewModel.effectsState.isReverseEchoEnabled) viewModel.toggleReverseEcho()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                val psychLabel = str("preset_reverse_psych")
                val ghostLabel = str("preset_reverse_ghost")
                val tameLabel = str("preset_reverse_tame")
                val presets = remember(psychLabel, ghostLabel, tameLabel) {
                    listOf(
                        Triple(0.55f, 0.60f, psychLabel),
                        Triple(0.85f, 0.75f, ghostLabel),
                        Triple(0.35f, 0.45f, tameLabel)
                    )
                }
                val selectedPreset = presets.firstOrNull {
                    kotlin.math.abs(it.first - time) < 0.08f && kotlin.math.abs(it.second - fb) < 0.08f
                }
                ExpressiveConnectedButtonGroup(
                    options = presets,
                    selectedOption = selectedPreset,
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                    onOptionSelected = { (pTime, pFb, _) ->
                        viewModel.setReverseEchoTime(pTime)
                        viewModel.setReverseEchoFeedback(pFb)
                        if (!viewModel.effectsState.isReverseEchoEnabled) viewModel.toggleReverseEcho()
                    },
                    labelProvider = { (_, _, label) ->
                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = str("fx_reverse_echo_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_ok")) } }
    )
}

@Composable
private fun StadiumDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.SurroundSound, null) },
        title = { Text(str("effect_stadium")) },
        text = {
            Column {
                val size = viewModel.effectsState.stadiumSize
                val atmosphere = viewModel.effectsState.stadiumAtmosphere

                Text(
                    str("label_stadium_size", (size * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = size,
                    onValueChange = {
                        viewModel.setStadiumSize(it)
                        if (!viewModel.effectsState.isStadiumEnabled) viewModel.toggleStadium()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))

                Text(
                    str("label_stadium_atmosphere", (atmosphere * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = atmosphere,
                    onValueChange = {
                        viewModel.setStadiumAtmosphere(it)
                        if (!viewModel.effectsState.isStadiumEnabled) viewModel.toggleStadium()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                val s50kLabel = str("preset_stadium_50k")
                val arenaLabel = str("preset_stadium_arena")
                val festivalLabel = str("preset_stadium_festival")
                val presets = remember(s50kLabel, arenaLabel, festivalLabel) {
                    listOf(
                        Triple(0.75f, 0.70f, s50kLabel),
                        Triple(0.55f, 0.50f, arenaLabel),
                        Triple(0.90f, 0.85f, festivalLabel)
                    )
                }
                val selectedPreset = presets.firstOrNull {
                    kotlin.math.abs(it.first - size) < 0.08f && kotlin.math.abs(it.second - atmosphere) < 0.08f
                }
                ExpressiveConnectedButtonGroup(
                    options = presets,
                    selectedOption = selectedPreset,
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                    onOptionSelected = { (pSize, pAtm, _) ->
                        viewModel.setStadiumSize(pSize)
                        viewModel.setStadiumAtmosphere(pAtm)
                        if (!viewModel.effectsState.isStadiumEnabled) viewModel.toggleStadium()
                    },
                    labelProvider = { (_, _, label) ->
                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = str("fx_stadium_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_ok")) } }
    )
}

@Composable
private fun WalkmanDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Radio, null) },
        title = { Text(str("effect_cassette_walkman")) },
        text = {
            Column {
                val drive = viewModel.effectsState.walkmanDrive
                val hiss = viewModel.effectsState.walkmanHiss

                Text(
                    str("label_walkman_drive", (drive * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = drive,
                    onValueChange = {
                        viewModel.setWalkmanDrive(it)
                        if (!viewModel.effectsState.isWalkmanEnabled) viewModel.toggleWalkman()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))

                Text(
                    str("label_walkman_hiss", (hiss * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = hiss,
                    onValueChange = {
                        viewModel.setWalkmanHiss(it)
                        if (!viewModel.effectsState.isWalkmanEnabled) viewModel.toggleWalkman()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                val w1984Label = str("preset_walkman_1984")
                val chromeLabel = str("preset_walkman_chrome")
                val lofiLabel = str("preset_walkman_lofi")
                val presets = remember(w1984Label, chromeLabel, lofiLabel) {
                    listOf(
                        Triple(0.65f, 0.40f, w1984Label),
                        Triple(0.45f, 0.20f, chromeLabel),
                        Triple(0.85f, 0.75f, lofiLabel)
                    )
                }
                val selectedPreset = presets.firstOrNull {
                    kotlin.math.abs(it.first - drive) < 0.08f && kotlin.math.abs(it.second - hiss) < 0.08f
                }
                ExpressiveConnectedButtonGroup(
                    options = presets,
                    selectedOption = selectedPreset,
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                    onOptionSelected = { (pDrive, pHiss, _) ->
                        viewModel.setWalkmanDrive(pDrive)
                        viewModel.setWalkmanHiss(pHiss)
                        if (!viewModel.effectsState.isWalkmanEnabled) viewModel.toggleWalkman()
                    },
                    labelProvider = { (_, _, label) ->
                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = str("fx_walkman_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_ok")) } }
    )
}

@Composable
private fun AsmrVocalDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.RecordVoiceOver, null) },
        title = { Text(str("effect_asmr_vocal")) },
        text = {
            Column {
                val proximity = viewModel.effectsState.asmrProximity
                val air = viewModel.effectsState.asmrAir

                Text(
                    str("label_asmr_proximity", (proximity * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = proximity,
                    onValueChange = {
                        viewModel.setAsmrProximity(it)
                        if (!viewModel.effectsState.isAsmrVocalEnabled) viewModel.toggleAsmrVocal()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))

                Text(
                    str("label_asmr_air", (air * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = air,
                    onValueChange = {
                        viewModel.setAsmrAir(it)
                        if (!viewModel.effectsState.isAsmrVocalEnabled) viewModel.toggleAsmrVocal()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                val whisperLabel = str("preset_asmr_whisper")
                val studioLabel = str("preset_asmr_studio")
                val sheenLabel = str("preset_asmr_sheen")
                val presets = remember(whisperLabel, studioLabel, sheenLabel) {
                    listOf(
                        Triple(0.85f, 0.60f, whisperLabel),
                        Triple(0.50f, 0.45f, studioLabel),
                        Triple(0.70f, 0.90f, sheenLabel)
                    )
                }
                val selectedPreset = presets.firstOrNull {
                    kotlin.math.abs(it.first - proximity) < 0.08f && kotlin.math.abs(it.second - air) < 0.08f
                }
                ExpressiveConnectedButtonGroup(
                    options = presets,
                    selectedOption = selectedPreset,
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                    onOptionSelected = { (pProx, pAir, _) ->
                        viewModel.setAsmrProximity(pProx)
                        viewModel.setAsmrAir(pAir)
                        if (!viewModel.effectsState.isAsmrVocalEnabled) viewModel.toggleAsmrVocal()
                    },
                    labelProvider = { (_, _, label) ->
                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = str("fx_asmr_vocal_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_ok")) } }
    )
}

@Composable
private fun NightDriveDialog(viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.DirectionsCar, null) },
        title = { Text(str("effect_night_drive")) },
        text = {
            Column {
                val cabin = viewModel.effectsState.nightDriveCabin
                val road = viewModel.effectsState.nightDriveRoad

                Text(
                    str("label_night_drive_cabin", (cabin * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = cabin,
                    onValueChange = {
                        viewModel.setNightDriveCabin(it)
                        if (!viewModel.effectsState.isNightDriveEnabled) viewModel.toggleNightDrive()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))

                Text(
                    str("label_night_drive_road", (road * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = road,
                    onValueChange = {
                        viewModel.setNightDriveRoad(it)
                        if (!viewModel.effectsState.isNightDriveEnabled) viewModel.toggleNightDrive()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                val sedanLabel = str("preset_night_sedan")
                val hwyLabel = str("preset_night_highway")
                val coupeLabel = str("preset_night_coupe")
                val presets = remember(sedanLabel, hwyLabel, coupeLabel) {
                    listOf(
                        Triple(0.60f, 0.45f, sedanLabel),
                        Triple(0.80f, 0.70f, hwyLabel),
                        Triple(0.45f, 0.35f, coupeLabel)
                    )
                }
                val selectedPreset = presets.firstOrNull {
                    kotlin.math.abs(it.first - cabin) < 0.08f && kotlin.math.abs(it.second - road) < 0.08f
                }
                ExpressiveConnectedButtonGroup(
                    options = presets,
                    selectedOption = selectedPreset,
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                    onOptionSelected = { (pCab, pRoad, _) ->
                        viewModel.setNightDriveCabin(pCab)
                        viewModel.setNightDriveRoad(pRoad)
                        if (!viewModel.effectsState.isNightDriveEnabled) viewModel.toggleNightDrive()
                    },
                    labelProvider = { (_, _, label) ->
                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = str("fx_night_drive_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_ok")) } }
    )
}
