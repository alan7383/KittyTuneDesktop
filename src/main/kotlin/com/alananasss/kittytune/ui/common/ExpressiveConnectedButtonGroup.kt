package com.alananasss.kittytune.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.PaddingValues
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
            ToggleButton(
                checked = selectedOption != null && selectedOption == option,
                onCheckedChange = { onOptionSelected(option) },
                modifier = buttonModifier,
                // Was accepted and then only used to decide the row's width, so every caller that passed it
                // to squeeze a long label into a narrow segment got the default 24 dp either side anyway and
                // watched the label wrap mid-word. It reaches the button now.
                contentPadding = contentPadding ?: ToggleButtonDefaults.ContentPadding,
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
                        Spacer(Modifier.width(4.dp))
                    }
                    labelProvider(option)
                }
            }
        }
    }
}
