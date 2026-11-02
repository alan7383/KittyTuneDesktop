package com.alananasss.kittytune.ui.pet

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.str

/**
 * Port of the Android setup bottom bar (ui/login/SetupBottomBar.kt) used by the pet setup: a step
 * counter and a morphing, spinning next button. The smooth-corner shapes of the Android library
 * become plain rounded corners (the desktop has no smooth-corner shape).
 */
@Composable
internal fun PetSetupBottomBar(
    pagerState: PagerState,
    onNextClicked: () -> Unit,
    onFinishClicked: () -> Unit,
    modifier: Modifier = Modifier,
    onBackClicked: (() -> Unit)? = null,
) {
    val morphAnimationSpec = tween<Float>(durationMillis = 600, easing = FastOutSlowInEasing)
    val rotationAnimationSpec = tween<Float>(durationMillis = 900, easing = FastOutSlowInEasing)

    // corner percentages of the morphing button: circle, rounded square, leaf
    val targetShapeValues = when (pagerState.currentPage % 3) {
        0 -> listOf(50f, 50f, 50f, 50f)
        1 -> listOf(26f, 26f, 26f, 26f)
        else -> listOf(18f, 50f, 18f, 50f)
    }
    val animatedTopStart by animateFloatAsState(targetShapeValues[0], morphAnimationSpec, label = "TopStart")
    val animatedTopEnd by animateFloatAsState(targetShapeValues[1], morphAnimationSpec, label = "TopEnd")
    val animatedBottomStart by animateFloatAsState(targetShapeValues[2], morphAnimationSpec, label = "BottomStart")
    val animatedBottomEnd by animateFloatAsState(targetShapeValues[3], morphAnimationSpec, label = "BottomEnd")
    val animatedRotation by animateFloatAsState(
        targetValue = pagerState.currentPage * 360f,
        animationSpec = rotationAnimationSpec,
        label = "Rotation"
    )
    val shape = RoundedCornerShape(topEnd = 36.dp, topStart = 36.dp)

    Surface(
        modifier = modifier.shadow(elevation = 8.dp, shape = shape, clip = true),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = shape,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isLastPage = pagerState.currentPage == pagerState.pageCount - 1
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    AnimatedVisibility(
                        visible = pagerState.currentPage > 0 && onBackClicked != null,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally()
                    ) {
                        IconButton(onClick = { onBackClicked?.invoke() }, modifier = Modifier.size(44.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = str("btn_back"),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    AnimatedContent(
                        targetState = pagerState.currentPage,
                        modifier = Modifier
                            .padding(start = if (pagerState.currentPage > 0 && onBackClicked != null) 4.dp else 16.dp)
                            .then(
                                when {
                                    isLastPage -> Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = onFinishClicked
                                    )
                                    pagerState.currentPage == 0 -> Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = onNextClicked
                                    )
                                    else -> Modifier
                                }
                            ),
                        transitionSpec = {
                            if (targetState > initialState) {
                                (slideInVertically { h -> h } + fadeIn()).togetherWith(slideOutVertically { h -> -h } + fadeOut())
                            } else {
                                (slideInVertically { h -> -h } + fadeIn()).togetherWith(slideOutVertically { h -> h } + fadeOut())
                            }.using(SizeTransform(clip = false))
                        },
                        label = "StepTextAnimation"
                    ) { targetPage ->
                        when (targetPage) {
                            0 -> Text(
                                text = str("setup_lets_go"),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            pagerState.pageCount - 1 -> Text(
                                text = str("setup_finish_btn"),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            else -> Text(
                                text = str("setup_step_format", targetPage, pagerState.pageCount - 1),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                FloatingActionButton(
                    onClick = if (isLastPage) onFinishClicked else onNextClicked,
                    shape = RoundedCornerShape(
                        topStart = animatedTopStart.toInt().dp,
                        topEnd = animatedTopEnd.toInt().dp,
                        bottomStart = animatedBottomStart.toInt().dp,
                        bottomEnd = animatedBottomEnd.toInt().dp,
                    ),
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(56.dp).rotate(animatedRotation)
                ) {
                    // counter-rotated so the icon stays upright
                    AnimatedContent(
                        modifier = Modifier.rotate(-animatedRotation),
                        targetState = pagerState.currentPage < pagerState.pageCount - 1,
                        transitionSpec = {
                            ContentTransform(
                                targetContentEnter = fadeIn(animationSpec = tween(220, delayMillis = 90)) + scaleIn(initialScale = 0.9f, animationSpec = tween(220, delayMillis = 90)),
                                initialContentExit = fadeOut(animationSpec = tween(90)) + scaleOut(targetScale = 0.9f, animationSpec = tween(90))
                            ).using(SizeTransform(clip = false))
                        },
                        label = "AnimatedFabIcon"
                    ) { isNextPage ->
                        if (isNextPage) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = str("common_next"))
                        } else {
                            Icon(Icons.Rounded.Check, contentDescription = str("btn_save"))
                        }
                    }
                }
            }
        }
    }
}
