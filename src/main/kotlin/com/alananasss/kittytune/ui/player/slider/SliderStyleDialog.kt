package com.alananasss.kittytune.ui.player.slider

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.PlayerSliderStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SliderStyleDialog(
    currentStyle: PlayerSliderStyle,
    onStyleSelected: (PlayerSliderStyle) -> Unit,
    onDismiss: () -> Unit
) {
    val previewColors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(str("pref_slider_style", "Style du curseur"))
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(str("btn_cancel", "Annuler"))
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Bar
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .aspectRatio(1f)
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                1.dp,
                                if (currentStyle == PlayerSliderStyle.BAR) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                onStyleSelected(PlayerSliderStyle.BAR)
                                onDismiss()
                            }
                            .padding(12.dp)
                    ) {
                        Slider(
                            value = 0.35f,
                            valueRange = 0f..1f,
                            onValueChange = {},
                            colors = previewColors,
                            enabled = false,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = str("slider_style_bar", "Bar"),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // 2. Wavy
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .aspectRatio(1f)
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                1.dp,
                                if (currentStyle == PlayerSliderStyle.WAVY) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                onStyleSelected(PlayerSliderStyle.WAVY)
                                onDismiss()
                            }
                            .padding(12.dp)
                    ) {
                        WavySlider(
                            value = 0.5f,
                            valueRange = 0f..1f,
                            onValueChange = {},
                            colors = previewColors,
                            isPlaying = true,
                            enabled = false,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = str("slider_style_wavy", "Wavy"),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 3. Slim
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .aspectRatio(1f)
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                1.dp,
                                if (currentStyle == PlayerSliderStyle.SLIM) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                onStyleSelected(PlayerSliderStyle.SLIM)
                                onDismiss()
                            }
                            .padding(12.dp)
                    ) {
                        androidx.compose.material3.Slider(
                            value = 0.65f,
                            onValueChange = {},
                            valueRange = 0f..1f,
                            thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                            track = { sliderState ->
                                PlayerSliderTrack(
                                    sliderState = sliderState,
                                    colors = previewColors,
                                    trackHeight = 10.dp
                                )
                            },
                            colors = previewColors,
                            enabled = false,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = str("slider_style_slim", "Slim"),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // 4. Squiggly
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .aspectRatio(1f)
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                1.dp,
                                if (currentStyle == PlayerSliderStyle.SQUIGGLY) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                onStyleSelected(PlayerSliderStyle.SQUIGGLY)
                                onDismiss()
                            }
                            .padding(12.dp)
                    ) {
                        SquigglySlider(
                            value = 0.5f,
                            valueRange = 0f..1f,
                            onValueChange = {},
                            colors = previewColors,
                            isPlaying = true,
                            enabled = false,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = str("slider_style_squiggly", "Squiggly"),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    )
}
