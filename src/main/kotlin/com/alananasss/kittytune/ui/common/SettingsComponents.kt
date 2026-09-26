package com.alananasss.kittytune.ui.common

import androidx.compose.material3.IconButtonDefaults

import androidx.compose.material3.ButtonDefaults
import com.alananasss.kittytune.core.str

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Removed getSettingsShape

fun getSettingsShape(groupSize: Int, index: Int): Shape {
    if (groupSize <= 1) return RoundedCornerShape(24.dp)
    val large = 24.dp
    val small = 4.dp
    return when (index) {
        0 -> RoundedCornerShape(topStart = large, topEnd = large, bottomEnd = small, bottomStart = small)
        groupSize - 1 -> RoundedCornerShape(topStart = small, topEnd = small, bottomEnd = large, bottomStart = large)
        else -> RoundedCornerShape(small)
    }
}

@Composable
fun SettingsGroupTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 8.dp, bottom = 12.dp, top = 24.dp)
    )
}

@Composable
fun SettingsGroup(
    title: String? = null,
    items: List<@Composable (Shape) -> Unit>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (title != null) {
            SettingsGroupTitle(title)
        }

        Column(
            modifier = Modifier.clip(RoundedCornerShape(24.dp)),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items.forEachIndexed { index, itemContent ->
                itemContent(getSettingsShape(items.size, index))
            }
        }
    }
}

/**
 * A row in a settings group.
 *
 * The mark is [icon] for the Material icons nearly every row uses, or [mark] for a drawable — the
 * only way to get a brand's own logo, since Material has no Discord. Both are optional because most
 * rows genuinely have none: a switch row, a slider row, a plain link.
 *
 * There was a third spelling, `iconRes: String?`, and it had no callers at all. With three ways in,
 * whichever matched first won and the rest were dropped without a word, so a row handed an `iconRes`
 * that did not exist drew nothing and looked deliberate. Two ways in, one resolution, one place the
 * mark is drawn.
 */
@Composable
fun SettingsItem(
    shape: Shape,
    title: String,
    subtitle: String? = null,
    trailingText: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    icon: ImageVector? = null,
    mark: androidx.compose.ui.graphics.painter.Painter? = null,
    onClick: (() -> Unit)? = null,
    hasSwitch: Boolean = false,
    switchState: Boolean = false,
    onSwitchChange: ((Boolean) -> Unit)? = null,
    hasSlider: Boolean = false,
    sliderValue: Float = 0f,
    sliderRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onSliderChange: ((Float) -> Unit)? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val interactionSource = remember { MutableInteractionSource() }

    val onToggleOrClick = {
        if (hasSwitch && onSwitchChange != null) {
            onSwitchChange(!switchState)
        } else {
            onClick?.invoke()
        }
    }

    // Decided once, here, and drawn once below. A vector is turned into a painter rather than
    // branching at the draw site, so both kinds take one path through it instead of two.
    val rowMark = icon?.let { androidx.compose.ui.graphics.vector.rememberVectorPainter(it) } ?: mark

    Card(
        onClick = { onToggleOrClick() },
        enabled = onClick != null || hasSwitch,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = shape,
        modifier = Modifier.fillMaxWidth(),
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (rowMark != null) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            painter = rowMark,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = titleColor
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (hasSlider && onSliderChange != null) {
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = sliderValue,
                        onValueChange = onSliderChange,
                        valueRange = sliderRange,
                        modifier = Modifier.fillMaxWidth().padding(end = 16.dp)
                    )
                }
            }

            if (trailingText != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = trailingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            if (hasSwitch) {
                // Without a handler the switch still shows the state, it just cannot be changed here.
                SettingsSwitch(
                    checked = switchState,
                    onCheckedChange = onSwitchChange,
                    interactionSource = interactionSource,
                )
            } else if (trailingContent != null) {
                trailingContent()
            } else if (onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun SettingsScaffold(
    title: String,
    onBackClick: (() -> Unit)? = null,
    scrollState: androidx.compose.foundation.ScrollState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    androidx.compose.runtime.LaunchedEffect(scrollState) {
        if (scrollState != null) {
            androidx.compose.runtime.snapshotFlow { scrollState.value }.collect { value ->
                if (scrollBehavior.state.heightOffsetLimit < 0f) {
                    scrollBehavior.state.heightOffset = (-value.toFloat()).coerceIn(scrollBehavior.state.heightOffsetLimit, 0f)
                }
            }
        }
    }

    val topBarContent = @Composable {
        LargeTopAppBar(
            title = { Text(title, fontWeight = FontWeight.Bold, maxLines = 1) },
            navigationIcon = {
                if (onBackClick != null) {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                }
            },
            actions = actions,
            scrollBehavior = scrollBehavior,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        )
    }

    if (scrollState == null) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = topBarContent,
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            content(padding)
        }
    } else {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // Content
            content(PaddingValues(top = 152.dp))

            // TopAppBar
            topBarContent()

            // Scrollbar OVER TopAppBar
            androidx.compose.foundation.VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp, horizontal = 2.dp),
                adapter = androidx.compose.foundation.rememberScrollbarAdapter(scrollState = scrollState)
            )
        }
    }
}

@Composable
fun SplitSettingsItem(
    shape: Shape,
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    switchState: Boolean,
    onSwitchChange: (Boolean) -> Unit,
    titleColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = shape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = titleColor
                    )
                    if (subtitle != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(Modifier.width(8.dp))
            }
            
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(32.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Switch(
                    checked = switchState,
                    onCheckedChange = onSwitchChange,
                    thumbContent = {
                        if (switchState) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                                tint = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                )
            }
        }
    }
}

/**
 * The settings' switch: a check or a cross in the thumb, so its state reads without relying on colour.
 *
 * [enabled] is separate from [onCheckedChange] on purpose. A Material `Switch` derives its enabled
 * state from whether it has a callback, so a switch that is *shown* but whose row owns the click
 * would render greyed out. Passing `enabled = true` alongside a null callback is how a row keeps a
 * live-looking switch while the row stays the single click target — which is the Material pattern
 * for a list item that toggles, and the only arrangement in which a click cannot fire twice.
 */
@Composable
fun SettingsSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    interactionSource: MutableInteractionSource? = null,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        interactionSource = interactionSource,
        thumbContent = {
            Icon(
                imageVector = if (checked) Icons.Rounded.Check else Icons.Rounded.Close,
                contentDescription = null,
                modifier = Modifier.size(SwitchDefaults.IconSize),
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        },
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    )
}
