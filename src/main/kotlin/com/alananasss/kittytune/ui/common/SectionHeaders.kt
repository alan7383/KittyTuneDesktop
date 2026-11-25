package com.alananasss.kittytune.ui.common

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.str

private val HeaderPillShape = RoundedCornerShape(14.dp)

/**
 * Left-aligned section header that places section navigation or secondary actions directly inline
 * with the title instead of stranding them on the far right edge of the viewport.
 */
@Composable
fun InlineSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    countText: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconSize: Dp = 20.dp,
    titleStyle: TextStyle = MaterialTheme.typography.titleLarge,
    titleFontWeight: FontWeight = FontWeight.Bold,
    actionTooltip: String = str("btn_see_all"),
    arrowSize: Dp = 22.dp,
    onAction: (() -> Unit)? = null,
    inlineTrailingAction: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (onAction != null) {
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                val pressed by interaction.collectIsPressedAsState()
                val arrowShift by animateDpAsState(
                    targetValue = if (hovered) 3.dp else 0.dp,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 550f),
                    label = "sectionHeaderArrowShift",
                )
                val pressScale by animateFloatAsState(
                    targetValue = if (pressed) 0.97f else 1f,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 600f),
                    label = "sectionHeaderPressScale",
                )
                val titleColor = if (hovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                val arrowColor = if (hovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                val pillColor = if (hovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent

                Tip(actionTooltip) {
                    Row(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .graphicsLayer {
                                scaleX = pressScale
                                scaleY = pressScale
                            }
                            .clip(HeaderPillShape)
                            .background(pillColor)
                            .hoverable(interaction)
                            .clickable(
                                interactionSource = interaction,
                                indication = ripple(),
                                role = Role.Button,
                                onClick = onAction,
                            )
                            .pointerHoverIcon(PointerIcon.Hand)
                            .padding(start = 4.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HeaderLeadingContent(
                            title = title,
                            countText = countText,
                            icon = icon,
                            iconTint = iconTint,
                            iconSize = iconSize,
                            titleStyle = titleStyle,
                            titleFontWeight = titleFontWeight,
                            titleColor = titleColor,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = actionTooltip.ifBlank { null },
                            tint = arrowColor,
                            modifier = Modifier.offset(x = arrowShift).size(arrowSize),
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeaderLeadingContent(
                        title = title,
                        countText = countText,
                        icon = icon,
                        iconTint = iconTint,
                        iconSize = iconSize,
                        titleStyle = titleStyle,
                        titleFontWeight = titleFontWeight,
                        titleColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }

            if (inlineTrailingAction != null) {
                inlineTrailingAction()
            }
        }

        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = if (onAction != null) Modifier.padding(start = 4.dp) else Modifier,
            )
        }
    }
}

@Composable
private fun HeaderLeadingContent(
    title: String,
    countText: String?,
    icon: ImageVector?,
    iconTint: Color,
    iconSize: Dp,
    titleStyle: TextStyle,
    titleFontWeight: FontWeight,
    titleColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = iconTint,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = title,
            style = titleStyle,
            fontWeight = titleFontWeight,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (!countText.isNullOrBlank()) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = countText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
