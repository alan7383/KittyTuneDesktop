package com.alananasss.kittytune.core

import java.util.concurrent.atomic.AtomicInteger

internal enum class StartupStage {
    STARTING, SETTINGS, DATABASE, LIBRARY, PLAYER, WINDOW, LAYOUT, READY
}

internal class StartupProgress {
    private val completed = AtomicInteger(0)
    val stage: StartupStage get() = StartupStage.entries[completed.get()]
    val percent: Int get() = completed.get() * 100 / StartupStage.READY.ordinal
    fun advance(stage: StartupStage) {
        completed.accumulateAndGet(stage.ordinal, ::maxOf)
    }
}

internal enum class StartupTransition {
    FADE, NONE;

    companion object {
        fun fromStored(value: String?): StartupTransition = entries.firstOrNull { it.name == value } ?: FADE
    }
}
