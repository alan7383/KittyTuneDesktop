package com.alananasss.kittytune.ui.theme

import androidx.compose.ui.unit.Density

/** Keep the monitor's native pixel density; app zoom scales dp and sp once each. */
internal fun Density.withUiScale(uiScale: Float): Density = Density(
    density = density * uiScale,
    // sp already includes density. Multiplying this again made text zoom quadratically.
    fontScale = fontScale,
)
