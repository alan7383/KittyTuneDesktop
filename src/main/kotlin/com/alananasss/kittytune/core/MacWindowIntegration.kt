package com.alananasss.kittytune.core

import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JRootPane
import javax.swing.RootPaneContainer

/** Cocoa window options supported by the bundled JDK, without replacing native window controls. */
internal object MacWindowIntegration {
    private val isMacOS: Boolean
        get() = System.getProperty("os.name").lowercase().contains("mac")

    /** Must precede toolkit initialization; otherwise Aqua keeps the legacy light appearance. */
    fun configureApplication() {
        if (!isMacOS) return
        System.setProperty("apple.awt.application.appearance", "system")
        System.setProperty("apple.awt.application.name", "KittyTune")
        System.setProperty("apple.laf.useScreenMenuBar", "true")
    }

    fun themeTitleBar(window: Window, enabled: Boolean) {
        if (!isMacOS) return
        val rootPane = (window as? RootPaneContainer)?.rootPane ?: return
        configureTitleBar(rootPane, enabled)
    }

    internal fun configureTitleBar(rootPane: JRootPane, enabled: Boolean) {
        // Transparency uses the themed AWT window background. Keep the native top inset:
        // fullWindowContent would put the sidebar buttons underneath the traffic lights.
        rootPane.putClientProperty("apple.awt.transparentTitleBar", enabled)
        rootPane.putClientProperty("apple.awt.windowTitleVisible", !enabled)
    }

    /** Exit the full-screen Space on hide/minimize, before the hidden Compose window is disposed. */
    fun install(window: Window, macOS: Boolean = isMacOS, onInactive: () -> Unit): () -> Unit {
        if (!macOS) return {}
        val windowListener = object : WindowAdapter() {
            override fun windowIconified(event: WindowEvent) = onInactive()
        }
        val componentListener = object : ComponentAdapter() {
            override fun componentHidden(event: ComponentEvent) = onInactive()
        }
        window.addWindowListener(windowListener)
        window.addComponentListener(componentListener)
        return {
            window.removeWindowListener(windowListener)
            window.removeComponentListener(componentListener)
        }
    }

    fun releaseFullScreen(window: Window) {
        if (!isMacOS) return
        runCatching {
            val device = window.graphicsConfiguration?.device
            if (device?.fullScreenWindow === window) device.fullScreenWindow = null
            // Native Cocoa fullscreen handles the window level; no topmost overlay should survive it.
            window.isAlwaysOnTop = false
        }
    }
}
