package com.alananasss.kittytune.core

import java.awt.event.ComponentEvent
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.JRootPane
import javax.swing.SwingUtilities
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MacWindowIntegrationTest {
    @Test
    fun themedTitleBarKeepsNativeContentInsetAndCanBeDisabled() {
        val root = JRootPane()
        MacWindowIntegration.configureTitleBar(root, true)
        assertEquals(true, root.getClientProperty("apple.awt.transparentTitleBar"))
        assertEquals(false, root.getClientProperty("apple.awt.windowTitleVisible"))
        assertNull(root.getClientProperty("apple.awt.fullWindowContent"))
        MacWindowIntegration.configureTitleBar(root, false)
        assertEquals(false, root.getClientProperty("apple.awt.transparentTitleBar"))
        assertEquals(true, root.getClientProperty("apple.awt.windowTitleVisible"))
    }

    @Test
    fun minimizingAndHidingReleaseFullscreenAndDisposalRemovesListeners() {
        SwingUtilities.invokeAndWait {
            val frame = JFrame()
            try {
                var exits = 0
                val remove = MacWindowIntegration.install(frame, macOS = true) { exits++ }
                frame.dispatchEvent(WindowEvent(frame, WindowEvent.WINDOW_ICONIFIED))
                assertEquals(1, exits)
                frame.dispatchEvent(ComponentEvent(frame, ComponentEvent.COMPONENT_HIDDEN))
                assertEquals(2, exits)
                remove()
                frame.dispatchEvent(WindowEvent(frame, WindowEvent.WINDOW_ICONIFIED))
                frame.dispatchEvent(ComponentEvent(frame, ComponentEvent.COMPONENT_HIDDEN))
                assertEquals(2, exits)
            } finally { frame.dispose() }
        }
    }

    @Test
    fun otherPlatformsKeepTheirWindowLifecycle() {
        SwingUtilities.invokeAndWait {
            val frame = JFrame()
            try {
                var exits = 0
                val remove = MacWindowIntegration.install(frame, macOS = false) { exits++ }
                frame.dispatchEvent(WindowEvent(frame, WindowEvent.WINDOW_ICONIFIED))
                frame.dispatchEvent(ComponentEvent(frame, ComponentEvent.COMPONENT_HIDDEN))
                assertEquals(0, exits)
                remove()
            } finally { frame.dispose() }
        }
    }
}
