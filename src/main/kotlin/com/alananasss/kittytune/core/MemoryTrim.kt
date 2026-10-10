package com.alananasss.kittytune.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.sun.management.HotSpotDiagnosticMXBean
import java.awt.Frame
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.lang.management.ManagementFactory
import kotlinx.coroutines.delay

/** How long the window has to stay put away before its memory is given back, so a quick minimise costs nothing. */
private const val TRIM_AFTER_HIDDEN_MS = 10_000L

/**
 * Gives memory back while the window is minimised or closed to the tray.
 *
 * A player spends most of its life out of sight, and it held on to about a gigabyte there all the same
 * (issue #66). See [releaseMemoryNow] for what is returned, and [restoreMemorySizing] for undoing it.
 *
 * Only minimising and hiding count. A window merely covered by another is a click away and keeps its caches.
 */
@Composable
fun TrimMemoryWhileHidden(window: Window) {
    var isPutAway by remember(window) { mutableStateOf(isPutAway(window)) }

    DisposableEffect(window) {
        fun recheck() {
            isPutAway = isPutAway(window)
        }
        val windowListener = object : WindowAdapter() {
            override fun windowIconified(e: WindowEvent) = recheck()
            override fun windowDeiconified(e: WindowEvent) = recheck()
            override fun windowStateChanged(e: WindowEvent) = recheck()
        }
        val componentListener = object : ComponentAdapter() {
            override fun componentShown(e: ComponentEvent) = recheck()
            override fun componentHidden(e: ComponentEvent) = recheck()
        }
        window.addWindowListener(windowListener)
        window.addWindowStateListener(windowListener)
        window.addComponentListener(componentListener)
        onDispose {
            window.removeWindowListener(windowListener)
            window.removeWindowStateListener(windowListener)
            window.removeComponentListener(componentListener)
        }
    }

    LaunchedEffect(isPutAway) {
        if (!isPutAway) {
            restoreMemorySizing()
            return@LaunchedEffect
        }
        delay(TRIM_AFTER_HIDDEN_MS)
        releaseMemoryNow()
    }
}

private fun isPutAway(window: Window): Boolean =
    !window.isVisible || (window is Frame && (window.extendedState and Frame.ICONIFIED) != 0)

/**
 * Hands back what can be rebuilt cheaply: the decoded covers, and every heap page the live data does not need.
 *
 * The covers in Coil's memory cache are native memory the heap limit never sees, and they come back from the
 * disk cache in milliseconds. The heap is the larger part: G1 only shrinks it at the end of a collection
 * cycle, and only down to about three times the live data (`MaxHeapFreeRatio`, 70% free by default) — so a
 * collection alone, measured, gave back nothing at all. While nobody is looking the ratio is tightened, then a
 * cycle is requested. That cycle runs concurrently (the app is launched with `-XX:+ExplicitGCInvokesConcurrent`),
 * so music playing in the background never waits on it.
 *
 * The tight ratio is only safe out of sight. Kept on while the interface allocates it was what forced G1 into
 * full, stop-the-world collections twice a minute (see build.gradle.kts), and a hidden player that is only
 * decoding audio allocates next to nothing. [restoreMemorySizing] puts the defaults back on the way in.
 */
fun releaseMemoryNow() {
    SingletonImageLoader.get(PlatformContext.INSTANCE).memoryCache?.clear()
    HeapSizing.tighten()
    System.gc()
}

/** Gives G1 its usual headroom back, for an interface that is about to allocate again. */
fun restoreMemorySizing() {
    HeapSizing.restore()
}

/**
 * The two heap free ratios, which HotSpot lets a running process change. Min is lowered before Max on the
 * way down and raised after it on the way up, since the JVM refuses a minimum above the maximum.
 */
private object HeapSizing {
    private const val MIN_FREE = "MinHeapFreeRatio"
    private const val MAX_FREE = "MaxHeapFreeRatio"
    private const val TIGHT_MIN_FREE = "5"
    private const val TIGHT_MAX_FREE = "15"

    private val bean: HotSpotDiagnosticMXBean? =
        runCatching { ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean::class.java) }.getOrNull()
    private val defaultMinFree = runCatching { bean?.getVMOption(MIN_FREE)?.value }.getOrNull()
    private val defaultMaxFree = runCatching { bean?.getVMOption(MAX_FREE)?.value }.getOrNull()

    @Volatile
    private var isTight = false

    @Synchronized
    fun tighten() {
        if (isTight) return
        set(MIN_FREE, TIGHT_MIN_FREE)
        set(MAX_FREE, TIGHT_MAX_FREE)
        isTight = true
    }

    @Synchronized
    fun restore() {
        if (!isTight) return
        defaultMaxFree?.let { set(MAX_FREE, it) }
        defaultMinFree?.let { set(MIN_FREE, it) }
        isTight = false
    }

    private fun set(name: String, value: String) {
        runCatching { bean?.setVMOption(name, value) }
            .onFailure { com.alananasss.kittytune.utils.Logger.w("MemoryTrim", "Could not set $name=$value: ${it.message}") }
    }
}
