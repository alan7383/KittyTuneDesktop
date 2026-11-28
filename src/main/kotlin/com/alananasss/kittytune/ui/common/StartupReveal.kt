package com.alananasss.kittytune.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import com.alananasss.kittytune.utils.StartupTimeline

internal fun Modifier.startupReveal(drawn: Boolean, fade: Boolean): Modifier = composed {
    val cover = remember { Animatable(if (fade && !drawn) 1f else 0f) }
    LaunchedEffect(drawn, fade) {
        if (!drawn) return@LaunchedEffect
        if (fade) cover.animateTo(0f, tween(140)) else cover.snapTo(0f)
        StartupTimeline.markOnce("startup reveal finished")
    }
    drawWithContent {
        drawContent()
        if (cover.value > 0f) drawRect(Color.Black, alpha = cover.value)
        else if (drawn) StartupTimeline.markOnce("startup reveal drawn")
    }
}
