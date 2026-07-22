package com.alananasss.kittytune.ui.recognition

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.LottieConstants
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter

@Composable
fun BlobBackgroundView(modifier: Modifier = Modifier) {
    // 1. On lit ton fichier JSON directement depuis les ressources du bureau
    val jsonString = remember {
        object {}.javaClass.getResourceAsStream("/raw/background_animation.json")
            ?.bufferedReader()
            ?.readText() ?: ""
    }

    // 2. On charge la composition Lottie
    val composition by rememberLottieComposition(LottieCompositionSpec.JsonString(jsonString))
    
    // 3. On crée le Painter animé en boucle infinie
    val painter = rememberLottiePainter(
        composition = composition
    )

    // 4. On l'affiche en remplissant tout l'écran
    Image(
        painter = painter,
        contentDescription = "Background Animation",
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize()
    )
}
