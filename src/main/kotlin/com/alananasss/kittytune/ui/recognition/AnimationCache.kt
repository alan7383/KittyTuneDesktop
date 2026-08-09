package com.alananasss.kittytune.ui.recognition

object AnimationCache {
    val backgroundJson: String by lazy {
        AnimationCache::class.java.getResourceAsStream("/raw/background_animation.json")
            ?.bufferedReader()?.use { it.readText() } ?: ""
    }
}
