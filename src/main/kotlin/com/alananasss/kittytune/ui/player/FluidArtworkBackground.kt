package com.alananasss.kittytune.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.graphics.graphicsLayer
import coil3.BitmapImage
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Shader
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Apple Music's fullscreen background, which is the sleeve itself rather than colours taken from it.
 *
 * ## Why the orbs were wrong
 *
 * The style this replaces drew five soft radial lights in the cover's own shades and drifted them around —
 * a mesh gradient, which is what everyone builds when they assume Apple sampled the artwork for a palette.
 * Apple does not. Sam Henri Gold's teardown of the Metal version, and the reverse-engineered web version
 * that follows it, both say the same thing: *the picture is still the picture*. Several copies of the album
 * art are stacked at different sizes, each one turning, the small ones sliding along circular tracks, and
 * the whole pile is twisted and then blurred until nothing of the photograph is legible — only its colours,
 * in the places the photograph put them. That is why a cover with a red top and a black bottom gives a
 * screen that is red at the top: no palette in the world reproduces that, because the information is
 * spatial, and a list of five colours has thrown it away.
 *
 * ## Where every number here comes from
 *
 * Not from taste. Apple's web player ships the whole scene in one bundle, and both Aadish Verma's
 * write-up ("Reverse engineering Apple Music's background gradient") and the AMLL project's `PixiRenderer`
 * are reconstructions of it. The sizes, the rotation rates and the colour grade below are that scene's:
 *
 *  - **Four copies**, at `√2`, `0.8`, `0.5` and `0.25` of the canvas's longer side, drawn largest first so
 *    the small ones sit on top. `√2` is not decoration — a square that size, centred, covers the canvas at
 *    every rotation, so there is never a gap to fill with a guess.
 *  - **Rotation** of `+0.06`, `-0.12`, `+0.06`, `-0.08` rad/s, alternating direction so neighbouring copies
 *    shear against each other instead of turning as one plate.
 *  - **Two circular tracks**: the third copy orbits at a quarter of the canvas width, the fourth much
 *    tighter. Only the small ones travel; the big two turn in place.
 *  - **The colour grade**, in Pixi's terms `saturate(1.2)`, `brightness(0.6)`, `contrast(0.3)` — which works
 *    out to pushing chroma to 2.2×, then dropping the whole thing to 60% and pulling contrast back around
 *    mid-grey. Apple oversaturates hard and then darkens hard, and both halves matter: the saturation is
 *    what makes a washed-out sleeve give a screen worth looking at, and the darkening is what lets white
 *    lyrics sit on top of it.
 *
 * Two places knowingly depart from AMLL's transcription. Its third copy drives *both* x and y from `cos`,
 * so it slides along a diagonal rather than orbiting, and its fourth multiplies the orbit radius by nothing
 * at all, so it travels about one pixel; the article describes circular tracks, so both are circles here.
 * [FLOW_SPEED] then scales the lot, because at the source's own speed the screen takes two minutes to
 * change and the request was "je veux vraiment que ça ressemble à la vidéo, le fond est animé genre un truc
 * de ouf" (issue #33).
 *
 * ## What runs where
 *
 * One SkSL pass does the stack, the twist and the grade — it is a coordinate transform followed by two
 * texture reads, so its cost does not depend on how many copies there are. The blur is a separate Skia
 * `ImageFilter` on the layer rather than taps in the shader, because Skia's own blur downsamples for large
 * sigmas and a 90-pixel Gaussian written by hand does not. The twist is applied to the *coordinate* before
 * the copies are read, which is the same thing as twisting the composited image and costs one rotation
 * instead of a second pass.
 */
@Language("GLSL")
internal val FLUID_SKSL = """
    uniform shader curArt;
    uniform shader prevArt;
    uniform float2 curArtSize;
    uniform float2 prevArtSize;
    uniform float fade;

    // Per copy, largest first: side length in px and how far it has turned.
    uniform float4 sides;
    uniform float4 angles;
    uniform float2 centre0;
    uniform float2 centre1;
    uniform float2 centre2;
    uniform float2 centre3;

    uniform float2 twistA;
    uniform float2 twistB;
    uniform float twistRadius;
    uniform float twistAngle;

    uniform float saturation;
    uniform float brightness;
    uniform float contrast;

    // Sam Henri Gold's description of the Metal one, and the shader Apple's own bundle turned out to
    // contain, to the line: rotate around an offset by an angle that grows with distance, modulated by the
    // square of the ratio of distance to radius. The square is what keeps the edge of the affected disc
    // invisible — the rotation and its first derivative both reach zero there.
    float2 twist(float2 coord, float2 offset, float radius, float angle) {
        float2 d = coord - offset;
        float dist = length(d);
        if (dist < radius) {
            float ratio = (radius - dist) / radius;
            float a = ratio * ratio * angle;
            float s = sin(a);
            float c = cos(a);
            d = float2(d.x * c - d.y * s, d.x * s + d.y * c);
        }
        return d + offset;
    }

    // Where a point falls inside one copy of the sleeve, in 0..1 texture space.
    float2 localOf(float2 p, float2 centre, float angle, float side) {
        float2 d = p - centre;
        float c = cos(-angle);
        float s = sin(-angle);
        return float2(d.x * c - d.y * s, d.x * s + d.y * c) / side + 0.5;
    }

    bool covers(float2 uv) {
        return uv.x >= 0.0 && uv.x <= 1.0 && uv.y >= 0.0 && uv.y <= 1.0;
    }

    half4 main(float2 fragCoord) {
        float2 p = twist(fragCoord, twistA, twistRadius, twistAngle);
        p = twist(p, twistB, twistRadius, -twistAngle);

        // The stack, resolved without compositing: the largest copy always covers the canvas, so it is the
        // floor, and each smaller one overwrites it where it lands. Same result as drawing four opaque
        // squares in order, one read instead of four.
        float2 uv = clamp(localOf(p, centre0, angles.x, sides.x), 0.0, 1.0);
        float2 u1 = localOf(p, centre1, angles.y, sides.y);
        float2 u2 = localOf(p, centre2, angles.z, sides.z);
        float2 u3 = localOf(p, centre3, angles.w, sides.w);
        if (covers(u1)) uv = u1;
        if (covers(u2)) uv = u2;
        if (covers(u3)) uv = u3;

        // A track change is a dissolve between two sleeves on one geometry, so the field carries on moving
        // through it rather than restarting on the new cover.
        float3 cur = float3(curArt.eval(uv * curArtSize).rgb);
        float3 prv = float3(prevArt.eval(uv * prevArtSize).rgb);
        float3 c = mix(prv, cur, fade);

        float mid = (c.r + c.g + c.b) / 3.0;
        c = float3(mid) + (c - float3(mid)) * saturation;
        c *= brightness;
        c = c * (1.0 + contrast) - float3(0.5 * contrast);
        return half4(half3(clamp(c, 0.0, 1.0)), 1.0);
    }
""".trimIndent()

private val fluidEffect: RuntimeEffect? by lazy {
    try {
        RuntimeEffect.makeForShader(FLUID_SKSL)
    } catch (_: Exception) {
        null
    }
}

/** One copy of the sleeve in the stack: where its centre is, how wide its square is, how far it has turned. */
internal data class FluidCopy(
    val centreX: Float,
    val centreY: Float,
    val side: Float,
    val angle: Float,
)

/**
 * The four copies at [seconds], on a canvas of [width] x [height] px, laid out largest first.
 *
 * Pure so the arrangement can be checked without a window: what a test can assert is exactly what matters
 * on screen — that the largest copy covers the canvas at every rotation (a gap would be a hole of flat
 * colour that no amount of blur closes), that the small ones stay on the canvas, and that no two of them
 * turn at the same rate.
 *
 * [seed] only decides where in its turn each copy starts, the way Apple's scene gives each sprite
 * `Math.random() * 2π`: without it every session opens on four squares in identical alignment, which reads
 * as one square.
 */
internal fun fluidCopies(width: Float, height: Float, seconds: Float, seed: Int): List<FluidCopy> {
    val longest = maxOf(width, height)
    val t = seconds * FLOW_SPEED
    val cx = width / 2f
    val cy = height / 2f
    val orbit = { rate: Float, radius: Float, phase: Float ->
        val a = rate * t + phase
        cx + width * radius * cos(a) to cy + width * radius * sin(a)
    }
    val (x3, y3) = orbit(ORBIT_RATE_MID, ORBIT_RADIUS_MID, startPhase(seed, 4))
    val (x4, y4) = orbit(ORBIT_RATE_SMALL, ORBIT_RADIUS_SMALL, startPhase(seed, 5))
    return listOf(
        // Covers the canvas whatever it does, so it is the ground the other three sit on.
        FluidCopy(cx, cy, longest * SQRT_2, startPhase(seed, 0) + ROTATION[0] * t),
        // Off centre, and the only one that is: the source puts it at 2/5 of each side.
        FluidCopy(width / 2.5f, height / 2.5f, longest * 0.8f, startPhase(seed, 1) + ROTATION[1] * t),
        FluidCopy(x3, y3, longest * 0.5f, startPhase(seed, 2) + ROTATION[2] * t),
        FluidCopy(x4, y4, longest * 0.25f, startPhase(seed, 3) + ROTATION[3] * t),
    )
}

/** A stable angle in 0..2π for copy [index] of the arrangement [seed] belongs to. */
private fun startPhase(seed: Int, index: Int): Float =
    (((seed ushr (index * 5)) and 0x1F) / 32f) * TWO_PI

private const val TWO_PI = 6.2831855f
private val SQRT_2 = sqrt(2f)

/**
 * How fast each copy turns, in rad/s before [FLOW_SPEED], largest copy first.
 *
 * The source's own rates. Alternating signs are the point of them: two copies turning the same way at
 * similar rates look like one thicker copy, and the whole illusion of a fluid is neighbouring regions
 * moving against each other.
 */
private val ROTATION = floatArrayOf(0.06f, -0.12f, 0.06f, -0.08f)

/** How far the two travelling copies stray from the centre, as a fraction of the canvas width. */
private const val ORBIT_RADIUS_MID = 0.25f
private const val ORBIT_RADIUS_SMALL = 0.10f

/**
 * How fast they go round, rad/s before [FLOW_SPEED].
 *
 * The mid one is the source's (`(time / 1000) * 0.75` on a per-frame clock). The small one's is chosen: the
 * source pairs its rate with a radius it forgot to multiply in, so the rate was never doing anything, and a
 * shade quicker than the mid one keeps the two from beating against each other.
 */
private const val ORBIT_RATE_MID = 0.045f
private const val ORBIT_RATE_SMALL = 0.09f

/**
 * Everything above, multiplied.
 *
 * The web player's default is 1, at which the mid copy takes 140 seconds to come back round — correct, and
 * far too slow for a screen someone asked to look alive. At 2.5 the fastest copy turns once in about 21
 * seconds, which is roughly what the old mesh's cycle was and what the reference video reads as.
 */
private const val FLOW_SPEED = 2.5f

/**
 * The colour grade, as Pixi's filters express it and as this shader consumes it.
 *
 * `saturate(1.2)` in Pixi is a matrix whose own-channel coefficient is 1.8 and cross-channel is -0.4, which
 * is chroma about the mean at 2.2 — a lot, and deliberately so. `brightness(0.6)` and `contrast(0.3)`
 * follow, in that order, because the order is what makes the result dark *and* still coloured.
 */
private const val SATURATION = 2.2f
private const val BRIGHTNESS = 0.6f
private const val CONTRAST = 0.3f

/**
 * How hard the two twists bite, and where.
 *
 * The centres are the source's, expressed as fractions of the canvas: one low left, one high right, so the
 * swirl runs diagonally across the screen rather than pulling towards the middle where the words are.
 *
 * The reach is the source's `(longer side + shorter side) / 2`, which on any ordinary window is larger than
 * the distance from either centre to the far corner — so the disc's edge, where the twist fades to nothing,
 * falls off screen. A twist you can see the edge of reads as a lens rather than as flow.
 */
private const val TWIST_ANGLE = 1.6f
private const val TWIST_REACH = 1.0f

/**
 * How much blur, as a fraction of the canvas's shorter side.
 *
 * This is the number that decides whether the screen is Apple's or a rotating photograph. Their filter
 * chain stacks seven Gaussians up to a 320-pixel radius; nothing of the sleeve survives it. A tenth of the
 * shorter side lands in the same place on any window size, which is the reason for expressing it as a
 * fraction rather than in pixels: the same 90 px that erases a 1080p window leaves the artwork readable on
 * a 4K one.
 */
private const val BLUR_FRACTION = 0.10f
private const val BLUR_SIGMA_MIN = 24f
private const val BLUR_SIGMA_MAX = 220f

/**
 * How large the sleeve is uploaded as a texture.
 *
 * Small on purpose. Everything drawn from it is blurred by tens of pixels, so detail beyond this is spent
 * on nothing, and a 256-pixel texture is a quarter of a megabyte where a full-resolution sleeve is several.
 * It also means the request usually resolves out of Coil's own cache, since the cover is already on screen.
 */
private const val TEXTURE_PX = 256

/** How many sleeves stay decoded, so that skipping back a track does not decode again. */
private const val TEXTURE_CACHE = 3

/** Long enough to read as the field travelling to the next record, short enough to be over before the chorus. */
private const val CROSSFADE_MS = 1400

/** The first sleeve arrives after the screen does, and fading it in is what hides the swap from the orbs. */
private const val APPEAR_MS = 800

/** 30 FPS, which is what the web player's ticker is capped at, and invisible at this much blur. */
private const val FRAME_SECONDS = 1f / 30f

/** A decoded sleeve, with the shader that reads it built once rather than per frame. */
internal class FluidTexture(
    /** Held so the texture outlives every shader made from it, rather than relying on native ref counts. */
    val image: Image,
    val shader: Shader,
    val width: Float,
    val height: Float,
)

/**
 * The last few sleeves, keyed by URL.
 *
 * Nothing is closed on eviction, and that is on purpose: the entry being dropped may still be the one this
 * frame's crossfade is reading from, and closing a Skia object out from under a shader is a crash rather
 * than a leak. Three textures of [TEXTURE_PX] square is under a megabyte, and skiko's cleaner frees them
 * once nothing refers to them.
 */
private val textureCache = LinkedHashMap<String, FluidTexture>()

/**
 * The sleeve as a texture, through Coil so that the copy already on screen is the copy used.
 *
 * Coil rather than [ArtworkPalette.load] deliberately: the palette extractor fetches with its own client
 * and no cache, which is defensible for something that runs once per track, and would be a second download
 * of the same full-resolution image here.
 */
private suspend fun loadTexture(url: String): FluidTexture? = withContext(Dispatchers.IO) {
    // Re-inserted on a hit, so the entry that gets evicted is the one nothing has asked for in longest.
    synchronized(textureCache) {
        textureCache.remove(url)?.also { textureCache[url] = it }
    }?.let { return@withContext it }

    val request = ImageRequest.Builder(PlatformContext.INSTANCE)
        .data(url)
        .size(TEXTURE_PX)
        .build()
    val result = runCatching {
        SingletonImageLoader.get(PlatformContext.INSTANCE).execute(request)
    }.getOrNull()
    val bitmap = ((result as? SuccessResult)?.image as? BitmapImage)?.bitmap ?: return@withContext null

    val texture = runCatching {
        val image = Image.makeFromBitmap(bitmap)
        FluidTexture(
            image = image,
            shader = image.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.LINEAR),
            width = image.width.toFloat(),
            height = image.height.toFloat(),
        )
    }.getOrNull() ?: return@withContext null

    synchronized(textureCache) {
        textureCache[url] = texture
        while (textureCache.size > TEXTURE_CACHE) {
            textureCache.remove(textureCache.keys.first())
        }
    }
    texture
}

/**
 * Everything the shader needs for one frame.
 *
 * Extracted from the draw block so that it is drivable without a window — the arrangement is checkable as
 * numbers by [fluidCopies], but whether those numbers are handed to the *right uniforms* is only visible in
 * a rendered frame, and a copy of this wiring in a test would be checking the copy.
 */
internal fun RuntimeShaderBuilder.setFluidUniforms(
    width: Float,
    height: Float,
    seconds: Float,
    seed: Int,
    current: FluidTexture,
    previous: FluidTexture,
    fade: Float,
) {
    val copies = fluidCopies(width, height, seconds, seed)
    child("curArt", current.shader)
    child("prevArt", previous.shader)
    uniform("curArtSize", current.width, current.height)
    uniform("prevArtSize", previous.width, previous.height)
    uniform("fade", fade)
    uniform("sides", copies[0].side, copies[1].side, copies[2].side, copies[3].side)
    uniform("angles", copies[0].angle, copies[1].angle, copies[2].angle, copies[3].angle)
    // Written out rather than looped over an index: this runs 30 times a second, and building the four
    // uniform names each time would be the only allocation in the frame.
    uniform("centre0", copies[0].centreX, copies[0].centreY)
    uniform("centre1", copies[1].centreX, copies[1].centreY)
    uniform("centre2", copies[2].centreX, copies[2].centreY)
    uniform("centre3", copies[3].centreX, copies[3].centreY)
    // Low left and high right, so the swirl runs across the corners the words do not occupy.
    uniform("twistA", width * 0.25f, height)
    uniform("twistB", width * 0.75f, 0f)
    uniform("twistRadius", (maxOf(width, height) + minOf(width, height)) / 2f * TWIST_REACH)
    uniform("twistAngle", TWIST_ANGLE)
    uniform("saturation", SATURATION)
    uniform("brightness", BRIGHTNESS)
    uniform("contrast", CONTRAST)
}

/** How hard to blur a canvas this size. See [BLUR_FRACTION] for why it is a fraction and not pixels. */
internal fun fluidBlurSigma(width: Float, height: Float): Float =
    (minOf(width, height) * BLUR_FRACTION).coerceIn(BLUR_SIGMA_MIN, BLUR_SIGMA_MAX)

/**
 * The background itself: four copies of [artworkUrl], turning, twisted and blurred past recognition.
 *
 * [fallback] holds the screen until the sleeve has been fetched and decoded, and keeps it for good if the
 * shader will not compile on this machine's driver or if the track has no artwork at all. It is dropped the
 * moment the real thing is opaque, so nothing keeps animating underneath something that hides it.
 */
@Composable
fun FluidArtworkBackground(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit,
) {
    val effect = fluidEffect
    if (effect == null) {
        Box(modifier) { fallback() }
        return
    }

    var current by remember { mutableStateOf<FluidTexture?>(null) }
    var previous by remember { mutableStateOf<FluidTexture?>(null) }
    val crossfade = remember { Animatable(1f) }
    val appear = remember { Animatable(0f) }

    // One arrangement per time the screen is opened, not per track: the copies keep turning through a track
    // change and only the texture they are read from dissolves, so re-rolling the phases here would make the
    // whole field jump at the exact moment it is supposed to be continuous.
    val seed = remember { Random.nextInt() }

    LaunchedEffect(artworkUrl) {
        val url = artworkUrl?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val texture = loadTexture(url) ?: return@LaunchedEffect
        val showing = current
        current = texture
        if (showing != null && showing !== texture) {
            previous = showing
            crossfade.snapTo(0f)
            crossfade.animateTo(1f, tween(CROSSFADE_MS))
            previous = null
        }
    }

    LaunchedEffect(current != null) {
        if (current != null) appear.animateTo(1f, tween(APPEAR_MS))
    }

    var seconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        var pending = 0f
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) pending += ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
                last = now
            }
            // The clock ticks with the display, but the state only moves at 30 FPS, and it is the state that
            // costs a redraw of a full-screen blur.
            if (pending >= FRAME_SECONDS) {
                seconds += pending
                pending = 0f
            }
        }
    }

    // Both live as long as the screen does. Compose's own ShaderBrush cannot carry a Skia shader — the
    // wrapper's constructor is internal to compose-ui — so the fill goes through Skia's canvas directly,
    // which is the same paint the brush would have built.
    val builder = remember(effect) { RuntimeShaderBuilder(effect) }
    val paint = remember { Paint() }
    DisposableEffect(builder, paint) {
        onDispose {
            builder.close()
            paint.close()
        }
    }

    Box(modifier) {
        if (appear.value < 1f) fallback()

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = appear.value
                    val sigma = fluidBlurSigma(size.width, size.height)
                    renderEffect = ImageFilter
                        .makeBlur(sigma, sigma, FilterTileMode.CLAMP)
                        .asComposeRenderEffect()
                }
                .drawBehind {
                    val cur = current ?: return@drawBehind
                    builder.setFluidUniforms(
                        width = size.width,
                        height = size.height,
                        seconds = seconds,
                        seed = seed,
                        current = cur,
                        previous = previous ?: cur,
                        fade = crossfade.value,
                    )
                    paint.shader = builder.makeShader(null)
                    drawIntoCanvas { canvas ->
                        canvas.skiaCanvas.drawRect(Rect.makeWH(size.width, size.height), paint)
                    }
                }
        )
    }
}
