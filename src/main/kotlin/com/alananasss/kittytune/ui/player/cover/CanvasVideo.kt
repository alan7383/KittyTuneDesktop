package com.alananasss.kittytune.ui.player.cover

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.alananasss.kittytune.data.cover.HlsVariantResolver
import com.alananasss.kittytune.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import kotlin.math.max

/** Decodes are app-wide and shared: every CanvasVideo showing one URL splits one stream. */
private val canvasHubScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private val canvasHub = SharedDecodeHub<ImageBitmap>(canvasHubScope) { url, emit ->
    decodeCanvasFrames(url, emit)
}

@Composable
fun CanvasVideo(
    canvasUrl: String,
    isPlaying: Boolean = true,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    var currentBitmap by remember(canvasUrl) { mutableStateOf<ImageBitmap?>(null) }
    var isVideoReady by remember(canvasUrl) { mutableStateOf(false) }

    val alpha by animateFloatAsState(
        targetValue = if (isVideoReady) 1f else 0f,
        animationSpec = tween(durationMillis = 350)
    )

    LaunchedEffect(canvasUrl, isPlaying) {
        if (canvasUrl.isBlank() || !isPlaying) return@LaunchedEffect
        // StateFlow replays the latest frame, so resuming shows an image immediately
        // instead of flashing the placeholder while the decoder spins back up.
        val frames = canvasHub.flowFor(canvasUrl)
        try {
            frames.collect { frame ->
                if (frame != null) {
                    currentBitmap = frame
                    isVideoReady = true
                }
            }
        } finally {
            canvasHub.release(canvasUrl)
        }
    }

    Box(modifier = modifier) {
        currentBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(alpha)
            )
        }
    }
}

/**
 * The single decode behind every viewer of [canvasUrl]: open, downscale, then emit frames
 * until cancelled. Errors end the job; the hub drops it so the next viewer starts fresh.
 */
private suspend fun CoroutineScope.decodeCanvasFrames(canvasUrl: String, emit: (ImageBitmap?) -> Unit) {
    withContext(Dispatchers.IO) {
        // Silence FFmpeg low-level C logging
        runCatching { avutil.av_log_set_level(avutil.AV_LOG_ERROR) }

        // Resolve master HLS playlist to single optimal stream to avoid probing all 20+ variants
        val streamUrl = HlsVariantResolver.resolveOptimalVariant(canvasUrl)
        val isApple = streamUrl.contains("apple.com") || streamUrl.contains("itunes.apple.com")

        var grabber: FFmpegFrameGrabber? = null
        var converter: Java2DFrameConverter? = null
        try {
            grabber = openCanvasGrabber(streamUrl, isApple, decodeSize = null)

            // A canvas is ambience, always drawn downscaled: decoding it full-res feeds
            // full-size frames through H.264, a Java2D conversion and a Skia upload for
            // nothing. Probe once, reopen scaled when worth it.
            val probed = grabber.imageWidth to grabber.imageHeight
            val longest = max(probed.first, probed.second)
            if (probed.first > 0 && probed.second > 0 && longest > CANVAS_MAX_DECODE_DIM) {
                val scale = CANVAS_MAX_DECODE_DIM.toDouble() / longest
                runCatching { grabber.stop() }
                runCatching { grabber.release() }
                grabber = openCanvasGrabber(
                    streamUrl, isApple,
                    decodeSize = (probed.first * scale).toInt() to (probed.second * scale).toInt(),
                )
            }

            converter = Java2DFrameConverter()
            val fps = grabber.frameRate.takeIf { it > 0 && it.isFinite() } ?: 30.0
            val targetFrameDelayMs = max(CANVAS_TARGET_FRAME_DELAY_MS, (1000.0 / fps).toLong())

            while (isActive) {
                val frameStart = System.currentTimeMillis()
                var frame = grabber.grabImage()
                if (frame == null) {
                    // Loop video
                    runCatching {
                        grabber.setTimestamp(0)
                    }.onFailure {
                        runCatching { grabber.restart() }
                    }
                    frame = grabber.grabImage()
                    if (frame == null) break
                }

                val bufferedImage = converter.convert(frame)
                if (bufferedImage != null) {
                    emit(bufferedImage.toComposeImageBitmap())
                }

                val elapsed = System.currentTimeMillis() - frameStart
                val sleepMs = max(4L, targetFrameDelayMs - elapsed)
                delay(sleepMs)
            }
        } catch (e: Exception) {
            Logger.w("CanvasVideo", "Error decoding canvas video: ${e.message}")
        } finally {
            runCatching { converter?.close() }
            runCatching {
                grabber?.stop()
                grabber?.release()
            }
        }
    }
}

/**
 * Canvas video is ambience, not cinema: 20 fps is indistinguishable on what is usually a
 * blurred backdrop, and halves decode, conversion and recomposition cost versus full rate.
 */
private const val CANVAS_TARGET_FRAME_DELAY_MS = 50L

/**
 * Longest edge decoded, in pixels. The draw is always downscaled, so anything above this
 * only feeds bigger frames through the decoder, the Java2D conversion and the Skia upload.
 */
private const val CANVAS_MAX_DECODE_DIM = 480

private fun openCanvasGrabber(
    streamUrl: String,
    isApple: Boolean,
    decodeSize: Pair<Int, Int>?,
): FFmpegFrameGrabber = FFmpegFrameGrabber(streamUrl).apply {
    setOption("user_agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
    setOption("rw_timeout", "8000000")
    setOption("reconnect", "1")
    setOption("reconnect_streamed", "1")
    setOption("reconnect_delay_max", "2")
    if (isApple) {
        setOption("headers", "Origin: https://music.apple.com\r\nReferer: https://music.apple.com/\r\n")
    }
    if (decodeSize != null && decodeSize.first > 0 && decodeSize.second > 0) {
        setImageWidth(decodeSize.first)
        setImageHeight(decodeSize.second)
    }
    start()
}
