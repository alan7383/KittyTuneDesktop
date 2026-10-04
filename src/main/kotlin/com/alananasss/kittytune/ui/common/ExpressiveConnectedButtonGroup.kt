package com.alananasss.kittytune.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ExpressiveConnectedButtonGroup(
    options: List<T>,
    selectedOption: T?,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    fillMaxWidth: Boolean = false,
    contentPadding: PaddingValues? = null,
    // Optional Android-parity styling (recognition screen). Null = library defaults (current look).
    checkedContainerColor: Color? = null,
    uncheckedContainerColor: Color? = null,
    checkedContentColor: Color? = null,
    uncheckedContentColor: Color? = null,
    border: BorderStroke? = null,
    iconSpacing: Dp = 4.dp,
    labelProvider: @Composable (T) -> Unit,
    iconProvider: (@Composable (T) -> Unit)? = null
) {
    val rowModifier = if (fillMaxWidth || contentPadding != null) {
        modifier.fillMaxWidth().padding(vertical = 4.dp)
    } else {
        modifier.padding(vertical = 4.dp)
    }
    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            val buttonModifier = if (fillMaxWidth) Modifier.weight(1f) else Modifier
            val isChecked = selectedOption != null && selectedOption == option
            // State-driven container/content so the checked segment wears the accent fill,
            // exactly like Android. All null → plain library defaults (previous behavior).
            val defaults = ToggleButtonDefaults.colors()
            ToggleButton(
                checked = isChecked,
                onCheckedChange = { onOptionSelected(option) },
                modifier = buttonModifier,
                // Was accepted and then only used to decide the row's width, so every caller that passed it
                // to squeeze a long label into a narrow segment got the default 24 dp either side anyway and
                // watched the label wrap mid-word. It reaches the button now.
                contentPadding = contentPadding ?: ButtonDefaults.ContentPadding,
                border = border,
                colors = defaults.copy(
                    containerColor = if (isChecked) checkedContainerColor ?: defaults.checkedContainerColor else uncheckedContainerColor ?: defaults.containerColor,
                    contentColor = if (isChecked) checkedContentColor ?: defaults.checkedContentColor else uncheckedContentColor ?: defaults.contentColor,
                    checkedContainerColor = checkedContainerColor ?: defaults.checkedContainerColor,
                    checkedContentColor = checkedContentColor ?: defaults.checkedContentColor
                ),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (iconProvider != null) {
                        iconProvider(option)
                        Spacer(Modifier.width(iconSpacing))
                    }
                    labelProvider(option)
                }
            }
        }
    }
}
