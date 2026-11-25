package com.alananasss.kittytune.ui.pet

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb

/**
 * The colour an app-coloured cat ([PetColor.AUTO]) wears: white like KittyTune's cover, with the theme's primary
 * colour mixed in — a hint in the coat, more in the shade and the markings — so it changes when the theme does.
 */
object PetThemeTint {
    /** The theme's primary colour, kept by [PetThemeSync]; KittyTune's purple until the first frame. */
    @Volatile
    var seed: Int = 0xFF7C5CE0.toInt()
        private set

    fun update(primary: Int) {
        if (primary == seed) return
        seed = primary
        PetConfig.themeChanged()
    }

    fun palette(seed: Int): PetPalette = PetPalette(
        fur = mix(0xFFFFFFFF.toInt(), seed, 0.05f),
        shade = mix(0xFFF1F1F6.toInt(), seed, 0.20f),
        mark = mix(0xFFFFFFFF.toInt(), seed, 0.42f),
        white = 0xFFFFFFFF.toInt(),
    )

    internal fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(c: Int, shift: Int) = (c shr shift) and 0xFF
        fun lerp(shift: Int) = (ch(a, shift) + (ch(b, shift) - ch(a, shift)) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (lerp(16) shl 16) or (lerp(8) shl 8) or lerp(0)
    }
}

/** Put once inside the app theme: an app-coloured cat follows the theme's primary colour. */
@Composable
fun PetThemeSync() {
    val primary = MaterialTheme.colorScheme.primary.toArgb()
    SideEffect { PetThemeTint.update(primary) }
}
