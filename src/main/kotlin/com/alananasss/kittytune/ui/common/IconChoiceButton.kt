package com.alananasss.kittytune.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.IconButtonDefaults

/**
 * A round button that opens a menu, drawn the same way wherever a list is filtered or sorted.
 *
 * Those buttons were each their own thing: a sort icon that never showed which sort was on, a text button
 * reading "All", another reading "Recent" over a menu that had no "Recent" in it (issue #66). This one shows
 * the icon of whatever is chosen, names it in a tooltip, and opens a menu built from [ChoiceMenuItem]s.
 */
@Composable
fun IconMenuButton(
    icon: ImageVector,
    tooltip: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    onExpandedChange: (Boolean) -> Unit = {},
    menu: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(expanded) { onExpandedChange(expanded) }
    Box(modifier) {
        Tip(tooltip) {
            FilledTonalIconButton(shapes = IconButtonDefaults.shapes(), onClick = { expanded = true }, modifier = Modifier.size(size)) {
                AnimatedContent(
                    targetState = icon,
                    transitionSpec = {
                        (fadeIn(tween(160)) + scaleIn(tween(200), initialScale = 0.7f)) togetherWith fadeOut(tween(100))
                    },
                    label = "iconMenuButton",
                ) { shown ->
                    Icon(shown, contentDescription = tooltip, modifier = Modifier.size(iconSize))
                }
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            menu { expanded = false }
        }
    }
}

/** A menu row with its own icon, accented with a tick when it is the one in force. */
@Composable
fun ChoiceMenuItem(text: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    val tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    DropdownMenuItem(
        text = { Text(text, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = tint) },
        trailingIcon = if (isSelected) ({ Icon(Icons.Rounded.Check, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp)) }) else null,
        onClick = onClick,
    )
}

/** A quiet heading over a group of [ChoiceMenuItem]s. */
@Composable
fun ChoiceMenuHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

/** [IconMenuButton] for a plain choice of one option out of several. */
@Composable
fun <T> IconChoiceButton(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    icon: (T) -> ImageVector,
    label: @Composable (T) -> String,
    tooltip: @Composable (T) -> String = label,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    onExpandedChange: (Boolean) -> Unit = {},
) {
    IconMenuButton(icon(selected), tooltip(selected), modifier, size, iconSize, onExpandedChange) { dismiss ->
        options.forEach { option ->
            ChoiceMenuItem(label(option), icon(option), option == selected) {
                onSelect(option)
                dismiss()
            }
        }
    }
}
