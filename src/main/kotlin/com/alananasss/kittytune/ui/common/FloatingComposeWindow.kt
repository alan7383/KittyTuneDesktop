package com.alananasss.kittytune.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.AwtWindow
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.awt.RenderSettings
import androidx.compose.ui.awt.SwingWindow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import com.alananasss.kittytune.core.LinuxWindowHelper
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsConfiguration
import java.awt.GraphicsEnvironment
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import javax.swing.JFrame
import kotlin.math.ceil
import kotlin.math.roundToInt

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun FloatingComposeWindow(
    onCloseRequest: () -> Unit,
    state: WindowState,
    title: String,
    transparent: Boolean,
    resizable: Boolean,
    visible: Boolean = true,
    alwaysOnTop: Boolean = false,
    closeOnEscape: Boolean = false,
    init: (Window) -> Unit = {},
    content: @Composable WindowScope.() -> Unit,
) {
    val currentClose by rememberUpdatedState(onCloseRequest)
    // On XWayland a transparent on-screen GLX drawable can block the AWT thread in
    // glXSwapBuffers (JetBrains SKIKO-1097). SwingGraphics uses OpenGL offscreen and
    // presents the result through AWT, which keeps the alpha channel and avoids that swap.
    if (!transparent || !LinuxWindowHelper.isWayland || System.getenv("KITTYTUNE_FLOATING_RENDERER") == "direct") {
        SwingWindow(
            onCloseRequest = onCloseRequest,
            state = state,
            title = title,
            decoration = WindowDecoration.Undecorated(),
            transparent = transparent,
            resizable = resizable,
            visible = visible,
            alwaysOnTop = alwaysOnTop,
            onKeyEvent = { event ->
                if (closeOnEscape && event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    currentClose()
                    true
                } else {
                    false
                }
            },
            init = init,
            content = content,
        )
        return
    }

    val currentState by rememberUpdatedState(state)
    val currentContent by rememberUpdatedState(content)
    val coroutineContext = rememberCoroutineScope().coroutineContext

    AwtWindow(
        visible = visible,
        create = {
            val frame = FloatingFrame(screenFor(state.position))
            val scope = object : WindowScope { override val window: Window = frame }
            val panel = ComposePanel(renderSettings = RenderSettings.SwingGraphics(), coroutineContext = coroutineContext)
            frame.title = title
            frame.isResizable = resizable
            frame.isAlwaysOnTop = alwaysOnTop
            frame.contentPane = panel
            frame.background = Color(0, 0, 0, 0)
            frame.defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
            frame.addWindowListener(object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent) = currentClose()
            })
            init(frame)
            frame.applyBounds(state)
            frame.trackBounds { currentState }
            panel.setContent {
                if (closeOnEscape) {
                    DisposableEffect(frame) {
                        val keyboard = KeyboardFocusManager.getCurrentKeyboardFocusManager()
                        // ComposePanel can consume Escape before Swing gets to the root pane's bindings.
                        val dispatcher = KeyEventDispatcher { event ->
                            if (event.id == KeyEvent.KEY_PRESSED && event.keyCode == KeyEvent.VK_ESCAPE &&
                                keyboard.focusedWindow === frame
                            ) {
                                currentClose()
                                true
                            } else {
                                false
                            }
                        }
                        keyboard.addKeyEventDispatcher(dispatcher)
                        onDispose { keyboard.removeKeyEventDispatcher(dispatcher) }
                    }
                }
                scope.currentContent()
            }
            // ComposePanel starts composing when it gets a peer. The overlay content must be able
            // to finish its setup and request visibility even if the frame starts hidden.
            frame.addNotify()
            frame.validate()
            frame
        },
        dispose = { it.dispose() },
        update = { frame ->
            if (frame.title != title) frame.title = title
            if (frame.isResizable != resizable) frame.isResizable = resizable
            if (frame.isAlwaysOnTop != alwaysOnTop) frame.isAlwaysOnTop = alwaysOnTop
            frame.applyBounds(state)
        },
    )
}

private fun screenFor(position: WindowPosition): GraphicsConfiguration {
    val environment = GraphicsEnvironment.getLocalGraphicsEnvironment()
    if (position is WindowPosition.Absolute) {
        val x = position.x.value.roundToInt()
        val y = position.y.value.roundToInt()
        environment.screenDevices.firstOrNull { it.defaultConfiguration.bounds.contains(x, y) }
            ?.let { return it.defaultConfiguration }
    }
    return KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow?.graphicsConfiguration
        ?: environment.defaultScreenDevice.defaultConfiguration
}

private class FloatingFrame(config: GraphicsConfiguration) : JFrame(config) {
    private var appliedSize: DpSize? = null
    private var appliedPosition: WindowPosition? = null
    private var paintBuffer: BufferedImage? = null

    init {
        isUndecorated = true
        // RepaintManager can render the root pane into its own opaque buffer during a native repaint.
        rootPane.isDoubleBuffered = false
    }

    override fun paint(graphics: Graphics) {
        if (graphics !is Graphics2D || width <= 0 || height <= 0) {
            super.paint(graphics)
            return
        }

        val transform = graphics.transform
        val scaleX = transform.scaleX
        val scaleY = transform.scaleY
        val pixelWidth = ceil(width * scaleX).toInt()
        val pixelHeight = ceil(height * scaleY).toInt()
        val image = paintBuffer?.takeIf { it.width == pixelWidth && it.height == pixelHeight }
            ?: BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_INT_ARGB_PRE).also { paintBuffer = it }

        // AWT clears translucent windows before painting their children. Keep that clear offscreen
        // so the compositor sees the previous image until the next one is ready.
        val bufferGraphics = image.createGraphics()
        try {
            bufferGraphics.scale(scaleX, scaleY)
            bufferGraphics.clip = graphics.clip
            super.paint(bufferGraphics)
        } finally {
            bufferGraphics.dispose()
        }

        val windowGraphics = graphics.create() as Graphics2D
        try {
            windowGraphics.composite = AlphaComposite.Src
            windowGraphics.drawImage(image, AffineTransform.getScaleInstance(1 / scaleX, 1 / scaleY), null)
        } finally {
            windowGraphics.dispose()
        }
    }

    fun applyBounds(state: WindowState) {
        val size = state.size
        if (size != appliedSize) {
            setSize(size.width.value.roundToInt(), size.height.value.roundToInt())
            appliedSize = size
        }
        val position = state.position
        if (position != appliedPosition) {
            when (position) {
                is WindowPosition.Absolute -> setLocation(position.x.value.roundToInt(), position.y.value.roundToInt())
                is WindowPosition.Aligned -> {
                    val config = graphicsConfiguration
                    val bounds = config.bounds
                    val insets = toolkit.getScreenInsets(config)
                    val available = IntSize(
                        bounds.width - insets.left - insets.right,
                        bounds.height - insets.top - insets.bottom,
                    )
                    val offset = position.alignment.align(IntSize(width, height), available, LayoutDirection.Ltr)
                    setLocation(bounds.x + insets.left + offset.x, bounds.y + insets.top + offset.y)
                }
                WindowPosition.PlatformDefault -> if (!isShowing) setLocationByPlatform(true)
            }
            appliedPosition = position
        }
    }

    fun trackBounds(currentState: () -> WindowState) {
        addComponentListener(object : ComponentAdapter() {
            override fun componentMoved(e: ComponentEvent) {
                if (!isDisplayable) return
                val position = WindowPosition(x.dp, y.dp)
                appliedPosition = position
                currentState().position = position
            }

            override fun componentResized(e: ComponentEvent) {
                if (!isDisplayable) return
                val size = DpSize(width.dp, height.dp)
                appliedSize = size
                currentState().size = size
            }
        })
    }
}
