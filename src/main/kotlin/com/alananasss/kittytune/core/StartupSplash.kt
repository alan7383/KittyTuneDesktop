package com.alananasss.kittytune.core

import java.awt.Color
import java.awt.SplashScreen
import java.util.concurrent.atomic.AtomicBoolean
import javax.imageio.ImageIO
import kotlin.concurrent.thread

internal object StartupSplash {
    val progress = StartupProgress()
    private val started = AtomicBoolean(false)
    private val languages = listOf("en", "de", "fr", "hu", "pt-BR", "ru", "tr", "vi")

    var transition: StartupTransition
        get() = StartupTransition.fromStored(Prefs.getString("startup_transition", null))
        set(value) = Prefs.putString("startup_transition", value.name)

    fun advance(stage: StartupStage) = progress.advance(stage)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        thread(name = "startup-splash", isDaemon = true, priority = Thread.MIN_PRIORITY) {
            runCatching {
                val splash = SplashScreen.getSplashScreen() ?: return@thread
                val text = StartupSplash::class.java.getResourceAsStream("/startup/text.png")
                    ?.use { ImageIO.read(it) } ?: return@thread
                val graphics = splash.createGraphics() ?: return@thread
                try {
                    graphics.scale(splash.size.width / 360.0, splash.size.height / 252.0)
                    var drawn = -1
                    while (splash.isVisible) {
                        val stage = progress.stage
                        val percent = stage.ordinal * 100 / StartupStage.READY.ordinal
                        val language = languages.indexOf(Strings.resolvedLanguage).coerceAtLeast(0)
                        val row = language * StartupStage.entries.size + stage.ordinal
                        if (row != drawn) {
                            graphics.color = Color.BLACK
                            graphics.fillRect(16, 194, 328, 42)
                            graphics.drawImage(text, 24, 194, 336, 220, 0, row * 26, 312, row * 26 + 26, null)
                            graphics.color = Color(0x242424)
                            graphics.fillRoundRect(24, 229, 312, 3, 3, 3)
                            graphics.color = Color(0xf5f5f5)
                            graphics.fillRoundRect(24, 229, 312 * percent / 100, 3, 3, 3)
                            splash.update()
                            drawn = row
                        }
                        if (stage == StartupStage.READY) break
                        Thread.sleep(125)
                    }
                } finally {
                    graphics.dispose()
                    text.flush()
                }
            }
        }
    }
}
