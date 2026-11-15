import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.alananasss.kittytune.ui.utils.fadedHorizontalEdges
import com.alananasss.kittytune.ui.utils.fadedVerticalEdges
import java.awt.AWTEvent
import java.awt.EventQueue
import java.awt.Toolkit
import java.lang.management.ManagementFactory
import java.util.concurrent.CountDownLatch
import javax.swing.SwingUtilities
import javax.swing.Timer
import org.jetbrains.skia.Canvas
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoRenderDelegate

object ScrollEdgeBenchmark {
    @JvmStatic fun main(args: Array<String>) {
        val warmupMs = args[0].toInt()
        val measureMs = args[1].toInt()
        val done = CountDownLatch(1)
        val queue = TimingQueue()
        Toolkit.getDefaultToolkit().systemEventQueue.push(queue)
        SwingUtilities.invokeAndWait {
            val phase = mutableIntStateOf(0)
            val rows = mutableIntStateOf(1)
            val window = ComposeWindow()
            window.title = "KittyTune scroll edge benchmark"
            window.setSize(1280, 720)
            window.setContent {
                Column(Modifier.fillMaxSize().fadedVerticalEdges(true, true)) {
                    repeat(rows.intValue) { row ->
                        val horizontal = if (rows.intValue > 1) Modifier.fadedHorizontalEdges(true, true) else Modifier
                        Box(Modifier.fillMaxWidth().weight(1f).then(horizontal).drawBehind {
                            val shift = phase.intValue % 80
                            drawRect(Color(0xff243648))
                            repeat(24) { index ->
                                val x = index * 68f - shift
                                val y = (row * 13 + index * 5) % 24f
                                drawRect(Color(0xff94bbd9), Offset(x, y), Size(46f, 32f))
                                drawRect(Color(0xff557986), Offset(x, y + 40f), Size(58f, 8f))
                            }
                        })
                    }
                }
            }
            window.addNotify()
            fun findLayer(component: java.awt.Component): SkiaLayer? = when (component) {
                is SkiaLayer -> component
                is java.awt.Container -> component.components.firstNotNullOfOrNull(::findLayer)
                else -> null
            }
            val layer = checkNotNull(findLayer(window))
            val delegate = checkNotNull(layer.renderDelegate)
            layer.renderDelegate = object : SkikoRenderDelegate {
                override fun onRender(canvas: Canvas, width: Int, height: Int, nanoTime: Long) {
                    val start = System.nanoTime()
                    delegate.onRender(canvas, width, height, nanoTime)
                    if (queue.measuring) queue.frameTimes += System.nanoTime() - start
                }
            }
            window.isVisible = true
            val repaint = Timer(16) { phase.intValue++ }.apply { start() }
            fun runCase(count: Int) {
                rows.intValue = count
                Timer(warmupMs) {
                    queue.reset()
                    queue.measuring = true
                    Timer(measureMs) {
                        queue.measuring = false
                        val frames = queue.frameTimes.sorted()
                        val dispatch = queue.dispatchTimes.sorted()
                        fun percentile(values: List<Long>, fraction: Double) =
                            values[(kotlin.math.ceil(values.size * fraction).toInt() - 1).coerceIn(0, values.lastIndex)]
                        check(frames.size >= measureMs / 100) { "too few redraws: ${frames.size}" }
                        val scenario = if (count == 1) "single-list" else "six-carousels"
                        println("{\"scenario\":\"$scenario\",\"renderer\":\"${window.renderApi}\",\"frames\":${frames.size},\"edtCpuNs\":${queue.cpu},\"edtBytes\":${queue.bytes},\"dispatchP95Ns\":${percentile(dispatch, .95)},\"frameRecordMedianNs\":${percentile(frames, .5)},\"frameRecordP95Ns\":${percentile(frames, .95)},\"dispatchOver16ms\":${dispatch.count { it > 16_666_667 }},\"dispatchTimesNs\":[${queue.dispatchTimes.joinToString()}],\"frameTimesNs\":[${queue.frameTimes.joinToString()}]}")
                        if (count == 1) runCase(6) else {
                            repaint.stop()
                            window.dispose()
                            done.countDown()
                        }
                    }.apply { isRepeats = false; start() }
                }.apply { isRepeats = false; start() }
            }
            runCase(1)
        }
        done.await()
        kotlin.system.exitProcess(0)
    }

    private class TimingQueue : EventQueue() {
        private val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        var measuring = false
        var cpu = 0L
        var bytes = 0L
        val dispatchTimes = mutableListOf<Long>()
        val frameTimes = mutableListOf<Long>()
        init { bean.isThreadAllocatedMemoryEnabled = true }
        fun reset() { cpu = 0; bytes = 0; dispatchTimes.clear(); frameTimes.clear() }
        override fun dispatchEvent(event: AWTEvent) {
            if (!measuring) { super.dispatchEvent(event); return }
            val id = Thread.currentThread().threadId()
            val allocated = bean.getThreadAllocatedBytes(id)
            val cpuStart = bean.currentThreadCpuTime
            val start = System.nanoTime()
            try { super.dispatchEvent(event) } finally {
                cpu += bean.currentThreadCpuTime - cpuStart
                bytes += bean.getThreadAllocatedBytes(id) - allocated
                dispatchTimes += System.nanoTime() - start
            }
        }
    }
}
