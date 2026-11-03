package com.alananasss.kittytune.core

import org.jetbrains.skiko.GraphicsApi
import org.jetbrains.skiko.SkiaLayer
import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.HierarchyEvent
import javax.swing.SwingUtilities

/**
 * On Windows, draws KittyTune's transparent windows (Kitty pet, mini player and its settings, floating
 * menus) in software instead of Direct3D.
 *
 * Skiko renders a transparent window through its own Direct3D path, and on some GPU drivers the very
 * first frame of such a window never comes back: the native `flushAndSubmit` blocks forever. That frame
 * is drawn on the event thread (while the window is laid out, before it is shown), so the whole app
 * froze the moment the pet or the mini player opened, and again on every launch since both are
 * remembered.
 *
 * These windows are small, so drawing them on the CPU costs next to nothing, and the main window
 * (opaque) keeps Direct3D.
 *
 * Compose gives no access to a window's SkiaLayer before its first frame, so this listens to AWT itself:
 * SkiaLayer.addNotify() runs JComponent.addNotify() — which reports the layer becoming displayable — and
 * only then creates its renderer. Setting the API at that moment means Direct3D is never even started
 * for these windows.
 */
object TransparentWindowRendering {
    private val API: GraphicsApi get() = FloatingWindowCompat.transparentRenderApi

    @Volatile private var installed = false

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
            if (layer.renderApi != API) layer.renderApi = API
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
