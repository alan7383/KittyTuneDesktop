@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.alananasss.kittytune.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import com.alananasss.kittytune.ui.common.SettingsSwitch
import com.alananasss.kittytune.ui.common.getSettingsShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.BackHandler
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.core.trackTextInput
import com.alananasss.kittytune.ui.common.Tip

/** What the full effect list shows: everything, what is on, what the grid holds, or one category. */
internal object AudioFxFilter {
    const val ALL = "all"
    const val ACTIVE = "active"
    const val PINNED = "pinned"
    fun category(categoryKey: String) = "category:$categoryKey"
}

private data class AudioFxCategory(val titleKey: String, val shortKey: String, val icon: ImageVector)

private val audioFxCategories = listOf(
    AudioFxCategory("category_power_eq", "fx_cat_power", Icons.Rounded.Bolt),
    AudioFxCategory("category_spatial", "fx_cat_spatial", Icons.Rounded.SurroundSound),
    AudioFxCategory("category_ambience_filters", "fx_cat_ambience", Icons.Rounded.WaterDrop),
    AudioFxCategory("category_retro_vintage", "fx_cat_retro", Icons.Rounded.Radio),
)

/**
 * Saving the current sound and opening the full effect list, as one connected Material 3 button group:
 * round outside, tight between the two, each morphing as it is pressed.
 */
@Composable
internal fun AudioFxActionsRow(onSave: () -> Unit, onExplore: () -> Unit, modifier: Modifier = Modifier) {
    val leading = ButtonGroupDefaults.connectedLeadingButtonShapes()
    val trailing = ButtonGroupDefaults.connectedTrailingButtonShapes()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        FilledTonalButton(
            onClick = onSave,
            shapes = ButtonShapes(leading.shape, leading.pressedShape),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            modifier = Modifier.weight(1f).heightIn(min = 60.dp),
        ) {
            Icon(Icons.Rounded.BookmarkAdd, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(str("audio_fx_save_preset"), style = MaterialTheme.typography.labelLarge, maxLines = 2, textAlign = TextAlign.Center)
        }
        FilledTonalButton(
            onClick = onExplore,
            shapes = ButtonShapes(trailing.shape, trailing.pressedShape),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            modifier = Modifier.weight(1f).heightIn(min = 60.dp),
        ) {
            Icon(Icons.Rounded.DashboardCustomize, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(str("btn_explore_all_effects"), style = MaterialTheme.typography.labelLarge, maxLines = 2, textAlign = TextAlign.Center)
        }
    }
}

/**
 * Every effect, to turn on and off where it is, pin to the grid or adjust; the grid itself sits on top,
 * where its tiles are dragged into order. [effects] holds the effects only, not the saved presets.
 * Escape goes back to the panel.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AudioFxExplorer(
    viewModel: PlayerViewModel,
    effects: List<AudioFxDefinition>,
    initialFilter: String,
    onToggle: (AudioFxDefinition) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(initialFilter) }
    var isDraggingTile by remember { mutableStateOf(false) }
    BackHandler(onBack = { if (query.isNotEmpty()) query = "" else onDismiss() })
    val state = viewModel.effectsState
    val pinned = viewModel.pinnedAudioFx
    val activeCount = effects.count { it.isActive(state) }
    val needle = query.trim()
    val shown = effects.filter { fx ->
        val matchesFilter = when (filter) {
            AudioFxFilter.ALL -> true
            AudioFxFilter.ACTIVE -> fx.isActive(state)
            AudioFxFilter.PINNED -> fx.id in pinned
            else -> AudioFxFilter.category(fx.categoryKey) == filter
        }
        matchesFilter && (needle.isEmpty() ||
            str(fx.titleKey).contains(needle, ignoreCase = true) ||
            audioFxDescription(fx.id).orEmpty().contains(needle, ignoreCase = true))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState(), enabled = !isDraggingTile)
            .padding(horizontal = 24.dp)
            .padding(top = 4.dp, bottom = 36.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tip(str("btn_back")) {
                IconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = str("btn_back"))
                }
            }
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(activeCount > 0, enter = fadeIn(), exit = fadeOut()) {
                TextButton(onClick = { viewModel.resetAllEffects() }, shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.PowerSettingsNew, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(str("fx_turn_all_off"))
                }
            }
        }
        Text(
            str("fx_explore_title"),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            str("fx_explore_subtitle"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().trackTextInput(),
            placeholder = { Text(str("fx_search_hint")) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                AnimatedVisibility(query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                    IconButton(onClick = { query = "" }, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.Close, contentDescription = str("btn_clear"))
                    }
                }
            },
            singleLine = true,
            shape = CircleShape,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedBorderColor = Color.Transparent,
            ),
        )
        Spacer(Modifier.height(12.dp))

        // Two connected groups: what to show, then which family of effects.
        val scopeFilters = listOf(AudioFxFilter.ALL, AudioFxFilter.ACTIVE, AudioFxFilter.PINNED)
        ExpressiveConnectedButtonGroup(
            options = scopeFilters,
            selectedOption = filter.takeIf { it in scopeFilters },
            onOptionSelected = { filter = it },
            fillMaxWidth = true,
            uncheckedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
            labelProvider = { id ->
                Text(
                    when (id) {
                        AudioFxFilter.ALL -> str("fx_filter_all")
                        AudioFxFilter.ACTIVE -> str("fx_filter_active", activeCount)
                        else -> str("fx_filter_pinned", pinned.size)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier,
                )
            },
        )
        ExpressiveConnectedButtonGroup(
            options = audioFxCategories,
            selectedOption = audioFxCategories.firstOrNull { AudioFxFilter.category(it.titleKey) == filter },
            onOptionSelected = { category ->
                val id = AudioFxFilter.category(category.titleKey)
                filter = if (filter == id) AudioFxFilter.ALL else id
            },
            fillMaxWidth = true,
            uncheckedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
            labelProvider = { category ->
                Text(
                    str(category.shortKey),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
        Spacer(Modifier.height(20.dp))

        if (filter == AudioFxFilter.ALL && needle.isEmpty()) {
            AudioFxSectionHeader(
                title = str("fx_your_grid"),
                subtitle = str("fx_your_grid_hint"),
                icon = Icons.Rounded.DashboardCustomize,
                action = {
                    TextButton(onClick = { viewModel.resetPinnedAudioFx() }, shapes = ButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.RestartAlt, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(str("btn_reset"))
                    }
                },
            )
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(Modifier.padding(12.dp)) {
                    DraggablePinnedTilesGrid(
                        pinnedList = pinned,
                        allEffects = effects,
                        viewModel = viewModel,
                        onDragStateChanged = { isDraggingTile = it },
                        onRemoveFx = { viewModel.togglePinAudioFx(it) },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        if (shown.isEmpty()) {
            AudioFxEmptyState(
                when {
                    needle.isNotEmpty() -> str("fx_no_results", needle)
                    filter == AudioFxFilter.ACTIVE -> str("fx_none_active")
                    else -> str("edit_tiles_subtitle")
                }
            )
        } else {
            audioFxCategories.forEach { category ->
                val inCategory = shown.filter { it.categoryKey == category.titleKey }
                if (inCategory.isEmpty()) return@forEach
                AudioFxSectionHeader(title = str(category.titleKey), icon = category.icon)
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    inCategory.forEachIndexed { index, fx ->
                        androidx.compose.runtime.key(fx.id) {
                            AudioFxRow(
                                fx = fx,
                                shape = getSettingsShape(inCategory.size, index),
                                isActive = fx.isActive(state),
                                isPinned = fx.id in pinned,
                                onToggle = { onToggle(fx) },
                                onTogglePin = { viewModel.togglePinAudioFx(fx.id) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun AudioFxSectionHeader(
    title: String,
    icon: ImageVector,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
        action?.invoke()
    }
}

@Composable
private fun AudioFxRow(
    fx: AudioFxDefinition,
    shape: androidx.compose.ui.graphics.Shape,
    isActive: Boolean,
    isPinned: Boolean,
    onToggle: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val danger = fx.isDanger
    val accent = if (danger) scheme.error else scheme.primary
    val onAccent = if (danger) scheme.onError else scheme.onPrimary
    val container = com.alananasss.kittytune.ui.theme.animatePaletteColor(
        selected = isActive,
        selectedColor = if (danger) scheme.errorContainer else scheme.secondaryContainer,
        unselectedColor = scheme.surfaceContainerHigh,
        label = "fxRowContainer",
    )
    val content = com.alananasss.kittytune.ui.theme.animatePaletteColor(
        selected = isActive,
        selectedColor = if (danger) scheme.onErrorContainer else scheme.onSecondaryContainer,
        unselectedColor = scheme.onSurface,
        label = "fxRowContent",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isActive) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "fxIconScale",
    )
    val description = audioFxDescription(fx.id)
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    Card(
        onClick = onToggle,
        colors = CardDefaults.cardColors(
            containerColor = container,
            contentColor = content,
        ),
        shape = shape,
        interactionSource = interactionSource,
        modifier = Modifier.fillMaxWidth().testTag("audio-fx-row-${fx.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(if (isActive) MaterialShapes.Cookie9Sided.toShape() else CircleShape)
                    .background(if (isActive) accent else scheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    fx.icon,
                    contentDescription = null,
                    tint = if (isActive) onAccent else scheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp).graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = str(fx.titleKey),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (description != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isActive) content.copy(alpha = 0.85f) else scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val pinLabel = str(if (isPinned) "fx_unpin" else "fx_pin")
            Tip(pinLabel) {
                IconButton(onClick = onTogglePin, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(40.dp)) {
                    Icon(
                        if (isPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                        contentDescription = pinLabel,
                        tint = if (isPinned) accent else content.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            val settings = fx.onOpenDialog
            if (settings != null) {
                Tip(str("fx_settings")) {
                    IconButton(onClick = settings, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.Tune, contentDescription = str("fx_settings"), tint = content.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                    }
                }
            } else {
                // Keeps the pin in line with the rows that have settings.
                Spacer(Modifier.width(40.dp))
            }
            Spacer(Modifier.width(6.dp))
            SettingsSwitch(
                checked = isActive,
                onCheckedChange = { onToggle() },
                interactionSource = interactionSource,
                colors = if (danger) SwitchDefaults.colors(
                    checkedTrackColor = scheme.error,
                    checkedThumbColor = scheme.onError,
                    checkedIconColor = scheme.error,
                ) else null,
                checkedIconTint = if (danger) scheme.error else scheme.primary,
            )
        }
    }
}

@Composable
private fun AudioFxEmptyState(text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(MaterialShapes.Cookie12Sided.toShape())
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.SearchOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The one-line description of an effect (`effect_tip_<id>`), when it has one. */
private fun audioFxDescription(id: String): String? {
    val key = "effect_tip_$id"
    return str(key).takeIf { it != key }
}
