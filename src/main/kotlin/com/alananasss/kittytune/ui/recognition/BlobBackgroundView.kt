package com.alananasss.kittytune.ui.recognition

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import io.github.alexzhirkevich.compottie.LottieCompositionSpec

import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter

@Composable
fun BlobBackgroundView(modifier: Modifier = Modifier) {
    val jsonString = AnimationCache.backgroundJson
    val composition by rememberLottieComposition(LottieCompositionSpec.JsonString(jsonString))

    val painter = rememberLottiePainter(
        composition = composition,
        iterations = io.github.alexzhirkevich.compottie.Compottie.IterateForever
    )

    Image(
        painter = painter,
        contentDescription = "Background Animation",
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize()
    )
}
