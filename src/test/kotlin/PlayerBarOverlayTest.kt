package com.alananasss.kittytune.ui.common

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerBarOverlayTest {

    @Test
    fun `overlapPx is zero when bounds are null`() {
        val overlay = PlayerBarOverlay()
        val overlap = PlayerBarOverlap(overlay)
        assertEquals(0f, overlap.overlapPx, 0.001f)
    }

    @Test
    fun `overlapPx is zero when overlay is null`() {
        val overlap = PlayerBarOverlap(null)
        assertEquals(0f, overlap.overlapPx, 0.001f)
    }

    @Test
    fun `overlapPx is safe when bounds contain NaN`() {
        val overlay = PlayerBarOverlay()
        overlay.bounds = Rect(Float.NaN, Float.NaN, Float.NaN, Float.NaN)
        val overlap = PlayerBarOverlap(overlay)
        assertEquals(0f, overlap.overlapPx, 0.001f)
    }

    @Test
    fun `overlapPx is safe when bounds are empty`() {
        val overlay = PlayerBarOverlay()
        overlay.bounds = Rect(0f, 0f, 0f, 0f)
        val overlap = PlayerBarOverlap(overlay)
        assertEquals(0f, overlap.overlapPx, 0.001f)
    }
}
