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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import kotlin.math.max

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

        withContext(Dispatchers.IO) {
            // Silence FFmpeg low-level C logging
            runCatching { avutil.av_log_set_level(avutil.AV_LOG_ERROR) }

            // Resolve master HLS playlist to single optimal stream to avoid probing all 20+ variants
            val streamUrl = HlsVariantResolver.resolveOptimalVariant(canvasUrl)

            var grabber: FFmpegFrameGrabber? = null
            var converter: Java2DFrameConverter? = null
            try {
                val isApple = streamUrl.contains("apple.com") || streamUrl.contains("itunes.apple.com")
                grabber = FFmpegFrameGrabber(streamUrl).apply {
                    setOption("user_agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    setOption("rw_timeout", "8000000")
                    setOption("reconnect", "1")
                    setOption("reconnect_streamed", "1")
                    setOption("reconnect_delay_max", "2")
                    if (isApple) {
                        setOption("headers", "Origin: https://music.apple.com\r\nReferer: https://music.apple.com/\r\n")
                    }
                    start()
                }

                converter = Java2DFrameConverter()
                val fps = grabber.frameRate.takeIf { it > 0 && it.isFinite() } ?: 30.0
                val targetFrameDelayMs = max(16L, (1000.0 / fps).toLong())

                while (isActive && isPlaying) {
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
                        val composeBitmap = bufferedImage.toComposeImageBitmap()
                        currentBitmap = composeBitmap
                        if (!isVideoReady) {
                            isVideoReady = true
                        }
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
