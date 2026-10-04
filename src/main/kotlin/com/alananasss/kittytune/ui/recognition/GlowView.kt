package com.alananasss.kittytune.ui.recognition

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

private val glowEffect: RuntimeEffect? by lazy {
    try {
        val src = AnimationCache::class.java.getResourceAsStream("/raw/glow.agsl")
            ?.bufferedReader()?.use { it.readText() }
            ?: return@lazy null
        RuntimeEffect.makeForShader(src)
    } catch (_: Exception) {
        null
    }
}

/**
 * Desktop port of the Android `GlowView` (AGSL simplex-noise glow at the bottom of the
 * recognition screen). AGSL is SkSL-derived so the same source runs unchanged on Skia.
 *
 * @param color the glow tint — [androidx.compose.material3.MaterialTheme.colorScheme.primary]
 *   on the recognition screen, matching Android.
 */
@Composable
fun GlowView(
    modifier: Modifier = Modifier,
    color: Color
) {
    val effect = glowEffect
    if (effect == null) {
        // Driver refused the shader: soft vertical fade so the layout never goes flat.
        Box(
            modifier.drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, color.copy(alpha = 0.35f))
                    )
                )
            }
        )
        return
    }

    var seconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = System.nanoTime()
        while (true) {
            kotlinx.coroutines.delay(33)
            val now = System.nanoTime()
            seconds += ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
            last = now
        }
    }

    val builder = remember(effect) { RuntimeShaderBuilder(effect) }
    val paint = remember { Paint() }
    DisposableEffect(builder, paint) {
        onDispose {
            builder.close()
            paint.close()
        }
    }
    // Snapshot so the draw scope (non-composable) sees stable values.
    val glowColor = color

    Box(
        modifier.drawBehind {
            builder.uniform("iResolution", size.width, size.height)
            builder.uniform("iTime", seconds)
            builder.uniform("iColor", glowColor.red, glowColor.green, glowColor.blue, glowColor.alpha)
            val shader = runCatching { builder.makeShader(null) }.getOrNull() ?: return@drawBehind
            paint.shader = shader
            drawIntoCanvas { canvas ->
                canvas.skiaCanvas.drawRect(Rect.makeWH(size.width, size.height), paint)
            }
        }
    )
}
