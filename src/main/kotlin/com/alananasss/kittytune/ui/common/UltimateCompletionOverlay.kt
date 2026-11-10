    package com.alananasss.kittytune.ui.common
import com.alananasss.kittytune.core.str
    
    import androidx.compose.animation.AnimatedVisibility
    import androidx.compose.animation.core.*
    import androidx.compose.animation.fadeIn
    import androidx.compose.animation.fadeOut
    import androidx.compose.animation.scaleIn
    import androidx.compose.animation.scaleOut
    import androidx.compose.foundation.Canvas
    import androidx.compose.foundation.background
    import androidx.compose.foundation.clickable
    import androidx.compose.foundation.interaction.MutableInteractionSource
    import androidx.compose.foundation.layout.*
    import androidx.compose.material3.MaterialTheme
    import androidx.compose.material3.Text
    import androidx.compose.runtime.*
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.draw.scale
    import androidx.compose.ui.geometry.Offset
    import androidx.compose.ui.graphics.Brush
    import androidx.compose.ui.graphics.Color
    import androidx.compose.ui.graphics.TileMode
    import androidx.compose.ui.graphics.drawscope.Stroke
    import androidx.compose.ui.graphics.graphicsLayer
    import androidx.compose.ui.text.ExperimentalTextApi
    import androidx.compose.ui.text.TextStyle
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.text.style.TextAlign
    import androidx.compose.ui.unit.dp
    import androidx.compose.ui.unit.sp
        import kotlinx.coroutines.delay
    import kotlin.math.cos
    import kotlin.math.sin
    import kotlin.random.Random
    
    @OptIn(ExperimentalTextApi::class)
    @Composable
    fun UltimateCompletionOverlay(onDismiss: () -> Unit) {
        var phase by remember { mutableIntStateOf(0) } // 0: LEGEND animation, 1: THANKS
        var canDismiss by remember { mutableStateOf(false) } // Locks the click
    
        // --- Time sequence (5s + 5s = 10s total) ---
        LaunchedEffect(Unit) {
            delay(5000) // 5 seconds on the LEGEND screen
            phase = 1   // Transition to thanks
            delay(5000) // 5 seconds on the thanks text (reading)
            canDismiss = true // Unlocks output
        }
    
        // --- Endless decorations, on the shared ambient pulse (see AmbientClock): pulsing only while the
        // window can be seen, and at thirty frames a second rather than the display's rate. ---
        // Rays rotation
        val raysRotation = rememberAmbientLoop(
            periodMs = 20_000, from = 0f, to = 360f, slow = true,
        )

        // Disk pulsation (Breathing)
        val pulseScale = rememberAmbientLoop(
            periodMs = 1_500, from = 1f, to = 1.1f,
            repeatMode = RepeatMode.Reverse, easing = FastOutSlowInEasing,
        )

        // Particles (One-time random generation)
        val particles = remember { List(30) { ParticleData.random() } }
        // Animation to move particles
        val particleAnim = rememberAmbientLoop(
            periodMs = 5_000, from = 0f, to = 2f * Math.PI.toFloat(), slow = true,
        )
    
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black) // Pure black background for contrast
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (canDismiss) onDismiss()
                },
            contentAlignment = Alignment.Center
        ) {
    
            // --- ANIMATED BACKGROUND (Common to both phases) ---
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = this.center
    
                // 1. Divine light rays (Rotating slowly)
                val rayGradient = Brush.sweepGradient(
                    colors = listOf(Color(0xFFD4AF37), Color(0xFFFFD700), Color.Transparent),
                    center = center
                )
    
                // Fix here: Removed unnecessary with(drawContext.canvas.nativeCanvas)
                val count = 12
                for (i in 0 until count) {
                    val angle = raysRotation.value + (i * (360f / count))
                    val rad = Math.toRadians(angle.toDouble())
                    val endX = center.x + (size.width * 1.5f * cos(rad)).toFloat()
                    val endY = center.y + (size.height * 1.5f * sin(rad)).toFloat()
    
                    drawLine(
                        brush = rayGradient,
                        start = center,
                        end = Offset(endX, endY),
                        strokeWidth = 150f, // Rayons plus larges
                        alpha = 0.05f // Très subtil
                    )
                }
    
                // 2. Floating gold particles (Stardust)
                particles.forEach { p ->
                    // Slight orbital movement
                    val phase = particleAnim.value
                    val offsetX = cos(phase + p.offsetSeed) * 20.dp.toPx()
                    val offsetY = sin(phase + p.offsetSeed) * 20.dp.toPx()
    
                    drawCircle(
                        color = Color(0xFFFFD700),
                        radius = p.radius.dp.toPx(),
                        center = Offset(
                            x = (size.width * p.relX) + offsetX,
                            y = (size.height * p.relY) - (phase * 50) // Rises slowly
                        ),
                        alpha = p.alpha
                    )
                }
            }
    
            // --- PHASE 1: LEGEND (0s -> 5s) ---
            AnimatedVisibility(
                visible = phase == 0,
                enter = scaleIn(initialScale = 0.8f) + fadeIn(),
                exit = scaleOut(targetScale = 1.2f) + fadeOut()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // The Gold Record / Medallion
                    Box(contentAlignment = Alignment.Center) {
                        // External halo (Glow)
                        Canvas(
                            modifier = Modifier.size(240.dp).graphicsLayer {
                                scaleX = pulseScale.value
                                scaleY = pulseScale.value
                            }
                        ) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0xFFFFD700).copy(alpha = 0.3f), Color.Transparent)
                                )
                            )
                        }
    
                        // The Record
                        Canvas(modifier = Modifier.size(180.dp)) {
                            // Record body (Metallic radial gradient)
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0xFFFFE599), Color(0xFFB8860B), Color(0xFF8B6508))
                                )
                            )
                            // Vinyl grooves
                            drawCircle(Color.Black.copy(0.1f), style = Stroke(width = 30f), radius = size.width * 0.25f)
                            drawCircle(Color.Black.copy(0.1f), style = Stroke(width = 2f), radius = size.width * 0.35f)
                            drawCircle(Color.Black.copy(0.1f), style = Stroke(width = 2f), radius = size.width * 0.40f)
                            // Black center
                            drawCircle(Color.Black, radius = size.width * 0.12f)
                        }
                    }
    
                    Spacer(Modifier.height(48.dp))
    
                    // LEGEND text with Gradient (Expressive Typography)
                    val goldTextGradient = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFE599), // Light gold
                            Color(0xFFFFD700), // Pure gold
                            Color(0xFFB8860B)  // Dark gold
                        ),
                        tileMode = TileMode.Mirror
                    )
    
                    Text(
                        text = str("completion_legend").uppercase(),
                        style = TextStyle(
                            brush = goldTextGradient,
                            fontSize = 56.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 6.sp,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier.graphicsLayer {
                            shadowElevation = 20f
                            scaleX = pulseScale.value // The text also breathes slightly
                            scaleY = pulseScale.value
                        }
                    )
    
                    Spacer(Modifier.height(16.dp))
    
                    Text(
                        text = str("completion_message"),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 1.sp
                    )
                }
            }
    
            // --- PHASE 2: THANKS (5s -> Infinity) ---
            AnimatedVisibility(
                visible = phase == 1,
                enter = fadeIn(animationSpec = tween(1000)) + scaleIn(initialScale = 0.9f)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(48.dp)
                ) {
                    // Small crown or heart icon
                    Text(
                        text = "👑",
                        fontSize = 48.sp,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
    
                    Text(
                        text = str("completion_thanks"),
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD700)
                        )
                    )
    
                    Spacer(Modifier.height(32.dp))
    
                    // More readable message
                    Text(
                        text = str("completion_thanks_message"),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            lineHeight = 32.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color.White.copy(0.9f),
                        textAlign = TextAlign.Center
                    )
    
                    Spacer(Modifier.height(64.dp))
    
                    // Exit indicator (Appears only after total 10s)
                    AnimatedVisibility(
                        visible = canDismiss,
                        enter = fadeIn(animationSpec = tween(500)) + scaleIn()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = str("completion_continue").uppercase(),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    letterSpacing = 2.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White.copy(0.5f)
                            )
                            // Small arrow or dot animation to encourage click
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color.White.copy(0.3f), androidx.compose.foundation.shape.CircleShape)
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Simple data for particles
    private data class ParticleData(
        val relX: Float,
        val relY: Float,
        val radius: Float,
        val alpha: Float,
        val offsetSeed: Float
    ) {
        companion object {
            fun random() = ParticleData(
                relX = Random.nextFloat(),
                relY = Random.nextFloat(),
                radius = Random.nextFloat() * 4f + 1f, // Size 1dp to 5dp
                alpha = Random.nextFloat() * 0.6f + 0.1f,
                offsetSeed = Random.nextFloat() * 10f
            )
        }
    }


