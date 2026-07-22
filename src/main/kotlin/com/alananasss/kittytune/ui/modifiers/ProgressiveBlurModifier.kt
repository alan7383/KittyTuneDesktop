package com.alananasss.kittytune.ui.modifiers

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import org.intellij.lang.annotations.Language
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

enum class BlurDirection {
    TOP, BOTTOM
}

// Same shader as the Android app — AGSL is SkSL-derived, so it runs unchanged
// on the desktop Skia renderer.
@Language("GLSL")
private val PROGRESSIVE_BLUR_SKSL = """
    uniform shader content;
    uniform float blurRadius;
    uniform float height;
    uniform float contentHeight;
    uniform int isTop;

    half4 main(float2 fragCoord) {
        float progress;
        if (isTop == 1) {
            progress = 1.0 - clamp(fragCoord.y / height, 0.0, 1.0);
        } else {
            progress = 1.0 - clamp((contentHeight - fragCoord.y) / height, 0.0, 1.0);
        }

        // Easing curve for smoother transition (power curve)
        progress = pow(progress, 1.5);

        float radius = progress * blurRadius;

        if (radius <= 0.0) {
            return content.eval(fragCoord);
        }

        half4 accum = half4(0.0);
        float weightSum = 0.0;

        // Random value for dithering based on pixel coordinates
        float dither = fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453);
        float2 jitter = float2(dither - 0.5, fract(dither * 1.618) - 0.5);

        const int SAMPLES = 4;
        float offsetScale = radius / float(SAMPLES);

        for (int x = -SAMPLES; x <= SAMPLES; x++) {
            for (int y = -SAMPLES; y <= SAMPLES; y++) {
                // Apply jittered sampling with dither
                float2 offset = (float2(float(x), float(y)) + jitter) * offsetScale;

                float distSq = dot(offset, offset);
                float radiusSq = radius * radius;

                if (distSq <= radiusSq) {
                    float weight = exp(-3.0 * distSq / radiusSq);
                    accum += content.eval(fragCoord + offset) * weight;
                    weightSum += weight;
                }
            }
        }

        return accum / weightSum;
    }
""".trimIndent()

private val progressiveBlurEffect: RuntimeEffect? by lazy {
    try {
        RuntimeEffect.makeForShader(PROGRESSIVE_BLUR_SKSL)
    } catch (_: Exception) {
        null
    }
}

/**
 * Applies a progressive blur to the specified edge of the element.
 * Desktop port of the Android AGSL RuntimeShader version (identical shader source).
 */
fun Modifier.progressiveBlur(
    blurRadius: Float,
    height: Float,
    direction: BlurDirection = BlurDirection.TOP,
    showGradientOverlay: Boolean = true
): Modifier = composed {
    val overlayColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.65f)

    val effect = progressiveBlurEffect
    val blurModifier = if (effect != null && blurRadius > 0f) {
        Modifier.graphicsLayer {
            val builder = RuntimeShaderBuilder(effect)
            builder.uniform("blurRadius", blurRadius)
            builder.uniform("height", height)
            builder.uniform("contentHeight", size.height)
            builder.uniform("isTop", if (direction == BlurDirection.TOP) 1 else 0)

            renderEffect = ImageFilter.makeRuntimeShader(
                runtimeShaderBuilder = builder,
                shaderName = "content",
                input = null,
            ).asComposeRenderEffect()
        }
    } else Modifier

    val gradientModifier = if (showGradientOverlay) {
        Modifier.drawWithContent {
            drawContent()
            val (brush, _) = when (direction) {
                BlurDirection.TOP -> {
                    Brush.verticalGradient(
                        colors = listOf(overlayColor, Color.Transparent),
                        endY = height
                    ) to height
                }
                BlurDirection.BOTTOM -> {
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, overlayColor),
                        startY = size.height - height
                    ) to height
                }
            }
            drawRect(brush = brush)
        }
    } else Modifier

    this.then(blurModifier).then(gradientModifier)
}
