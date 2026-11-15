package com.alananasss.kittytune.ui.utils

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class FadingEdgeRenderTest {
    private fun assertSame(message: String, expected: ByteArray, actual: ByteArray) {
        if (!expected.contentEquals(actual)) {
            val directory = java.io.File("build/previews/fading").apply { mkdirs() }
            java.io.File(directory, "expected.png").writeBytes(expected)
            java.io.File(directory, "actual.png").writeBytes(actual)
        }
        assertArrayEquals(message, expected, actual)
    }
    private fun render(horizontal: Boolean, reference: Boolean, length: Float,
                       backward: Boolean, forward: Boolean, enabled: Boolean = true,
                       change: Boolean = false, resize: Boolean = false): List<ByteArray> {
        val back = mutableStateOf(backward)
        val next = mutableStateOf(forward)
        val dimensions = mutableStateOf(81 to 63)
        val scene = ImageComposeScene(width = 145, height = 119) {
            Box(Modifier.size(145.dp, 119.dp).background(Color(0xff29465a)).padding(20.dp)) {
                val fade = if (reference) {
                    if (!enabled) Modifier else if (horizontal) Modifier.referenceFadedHorizontalEdges(back.value, next.value, length.dp)
                    else Modifier.referenceFadedVerticalEdges(back.value, next.value, length.dp)
                } else if (horizontal) Modifier.fadedHorizontalEdges(back.value, next.value, length.dp, enabled)
                else Modifier.fadedVerticalEdges(back.value, next.value, length.dp, enabled)
                Box(Modifier.size(dimensions.value.first.dp, dimensions.value.second.dp).then(fade).drawBehind {
                    drawRect(Color(0xcced6a5e), topLeft = Offset(-10f, -10f), size = Size(size.width + 20f, size.height + 20f))
                    for (i in 0..5) drawRect(Color(0x6634ccaa), topLeft = Offset(i * 13f, i * 9f), size = Size(13f, 11f))
                })
            }
        }
        try {
            val images = mutableListOf<ByteArray>()
            for (frame in 0..32) {
                if (change && frame == 4) { back.value = !backward; next.value = !forward }
                if (resize && frame == 4) dimensions.value = 96 to 47
                scene.render(frame * 16_666_667L).use { image ->
                    if (frame in listOf(3, 8, 16, 32)) image.encodeToData(EncodedImageFormat.PNG)!!.use { images += it.bytes }
                }
            }
            return images
        } finally { scene.close() }
    }

    @Test fun zeroLengthDoesNotClipContentWhenNoFadeIsRequested() {
        for (horizontal in listOf(false, true)) {
            val expected = render(horizontal, false, 0f, false, true, enabled = false)
            val actual = render(horizontal, false, 0f, false, true)
            expected.zip(actual).forEach { (a, b) -> assertSame("zero-length fade clipped content", a, b) }
        }
    }

    @Test fun masksMatchTheOriginalAcrossDirectionsAndLengths() {
        for (horizontal in listOf(false, true)) for (length in listOf(0.03f, 1f, 17.5f, 28f, 200f)) {
            for ((backward, forward) in listOf(false to true, true to false, true to true, false to false)) {
                val expected = render(horizontal, true, length, backward, forward)
                val actual = render(horizontal, false, length, backward, forward)
                expected.zip(actual).forEach { (a, b) -> assertSame("mask changed: horizontal=$horizontal length=$length back=$backward forward=$forward", a, b) }
            }
        }
    }

    @Test fun cachedMasksFollowFadeAnimations() {
        for (horizontal in listOf(false, true)) for ((backward, forward) in listOf(false to true, true to false)) {
            val expected = render(horizontal, true, 17.5f, backward, forward, change = true)
            val actual = render(horizontal, false, 17.5f, backward, forward, change = true)
            expected.zip(actual).forEach { (a, b) -> assertSame("animated fade changed", a, b) }
        }
    }

    @Test fun cachedMasksFollowSizeChanges() {
        for (horizontal in listOf(false, true)) {
            val expected = render(horizontal, true, 17.5f, true, true, resize = true)
            val actual = render(horizontal, false, 17.5f, true, true, resize = true)
            expected.zip(actual).forEach { (a, b) -> assertSame("resized fade changed", a, b) }
        }
    }
}
