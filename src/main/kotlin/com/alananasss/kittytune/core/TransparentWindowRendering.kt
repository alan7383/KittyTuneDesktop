package com.alananasss.kittytune.core

import org.jetbrains.skiko.DelicateSkikoApi
import org.jetbrains.skiko.GraphicsApi
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.transparentWindowBackgroundHack
import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.HierarchyEvent
import java.util.Collections
import java.util.WeakHashMap
import javax.swing.SwingUtilities

/** Keeps floating windows' native transparency compatible with their renderer on Windows. */
@OptIn(DelicateSkikoApi::class)
object TransparentWindowRendering {
    private val API: GraphicsApi get() = FloatingWindowCompat.transparentRenderApi

    @Volatile private var installed = false
    private val trackedLayers = Collections.newSetFromMap(WeakHashMap<SkiaLayer, Boolean>())

    private fun syncBackground(layer: SkiaLayer) {
        val window = SwingUtilities.getWindowAncestor(layer) as? androidx.compose.ui.awt.ComposeWindow ?: return
        if (window.isTransparent) window.background = transparentWindowBackgroundHack(layer.renderApi)
    }

    private val listener = AWTEventListener { event ->
        if (event !is HierarchyEvent) return@AWTEventListener
        if (event.changeFlags and HierarchyEvent.DISPLAYABILITY_CHANGED.toLong() == 0L) return@AWTEventListener
        val layer = event.component as? SkiaLayer ?: return@AWTEventListener
        if (!layer.isDisplayable) return@AWTEventListener
        runCatching {
            val window = SwingUtilities.getWindowAncestor(layer) ?: return@runCatching
            val transparent = (window as? androidx.compose.ui.awt.ComposeWindow)?.isTransparent
                ?: (window.background?.alpha?.let { it < 255 } ?: false)
            if (!transparent) {
                val undecorated = (window as? java.awt.Frame)?.isUndecorated ?: (window as? java.awt.Dialog)?.isUndecorated ?: false
                // Compatibility mode: the floating windows (all undecorated, unlike the main one) are opaque now,
                // and drawn in software so RTSS keeps its overlay on the main window instead of flickering.
                if (undecorated && FloatingWindowCompat.opaque && layer.renderApi != GraphicsApi.SOFTWARE_COMPAT) {
                    layer.renderApi = GraphicsApi.SOFTWARE_COMPAT
                }
                return@runCatching
            }
            if (trackedLayers.add(layer)) {
                // Fallback happens after Compose's initial transparency setup.
                layer.onStateChanged(SkiaLayer.PropertyKind.Renderer) { syncBackground(it) }
            }
            if (layer.renderApi != API) layer.renderApi = API
            syncBackground(layer)
        }
    }

    /** Call once, before any window is created. Does nothing outside Windows. */
    fun install() {
        if (installed) return
        if (!System.getProperty("os.name").lowercase().contains("win")) return
        installed = true
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.HIERARCHY_EVENT_MASK)
    }
}
