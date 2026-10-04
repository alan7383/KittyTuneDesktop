package com.alananasss.kittytune.ui.recognition

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.dynamic.rememberLottieDynamicProperties
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter

/**
 * Desktop port of the Android recognition background.
 *
 * Same `background_animation.json` as Android, same behavior:
 * - blob fills re-tinted to the Material [primary]/[secondary]/[tertiary] colors
 *   (Android `LottieProperty.COLOR` on `**.primary|secondary|tertiary.**`, 95/95/90 opacity),
 * - the whole field zooms 1→5x over 1000 ms on emphasized-decelerate when listening starts
 *   (Android `HomeFragment.java`: scale on `motionEasingEmphasizedDecelerate`).
 *
 * @param active true while listening: the background blooms to 5x.
 */
@Composable
fun BlobBackgroundView(
    modifier: Modifier = Modifier,
    active: Boolean = false,
    primary: Color = Color.Unspecified,
    secondary: Color = Color.Unspecified,
    tertiary: Color = Color.Unspecified,
) {
    val jsonString = AnimationCache.backgroundJson
    val composition by rememberLottieComposition(LottieCompositionSpec.JsonString(jsonString))

    // Original HomeFragment.java: lottie scales 1→5 on motionEasingEmphasizedDecelerate over 1000ms.
    val lottieScale = remember { Animatable(1f) }
    LaunchedEffect(active) {
        if (active) {
            lottieScale.animateTo(
                5f,
                tween(1000, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f))
            )
        } else {
            lottieScale.snapTo(1f)
        }
    }

    val hasTint = primary != Color.Unspecified && secondary != Color.Unspecified && tertiary != Color.Unspecified
    val dynamicProperties = if (hasTint) {
        rememberLottieDynamicProperties(primary, secondary, tertiary) {
            shapeLayer(".primary") {
                fill("Fill 1") {
                    color { _ -> primary }
                    opacity { _ -> 0.95f }
                }
            }
            shapeLayer(".secondary") {
                fill("Fill 1") {
                    color { _ -> secondary }
                    opacity { _ -> 0.95f }
                }
            }
            shapeLayer(".tertiary") {
                fill("Fill 1") {
                    color { _ -> tertiary }
                    opacity { _ -> 0.90f }
                }
            }
        }
    } else null

    val painter = rememberLottiePainter(
        composition = composition,
        dynamicProperties = dynamicProperties,
        iterations = Compottie.IterateForever
    )

    Image(
        painter = painter,
        contentDescription = "Background Animation",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = lottieScale.value
                scaleY = lottieScale.value
            }
    )
}
