package com.alananasss.kittytune.utils

import com.alananasss.kittytune.core.NetworkMonitor

object NetworkUtils {
    fun isInternetAvailable(): Boolean = NetworkMonitor.isOnline.value
}
