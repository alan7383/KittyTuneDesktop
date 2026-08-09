package com.alananasss.kittytune.ui.recognition

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter

@Composable
fun GlowView(modifier: Modifier = Modifier, color: Color = Color.Transparent) {
    val jsonString = AnimationCache.backgroundJson

    if (jsonString.isNotEmpty()) {
        val composition by rememberLottieComposition(LottieCompositionSpec.JsonString(jsonString))
        
        Image(
            painter = rememberLottiePainter(composition = composition),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize()
        )
    }
}