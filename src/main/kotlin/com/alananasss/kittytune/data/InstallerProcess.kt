package com.alananasss.kittytune.data

internal fun awaitInstallerResult(
    process: Process,
    needsAuth: Boolean,
    onInstalling: () -> Unit,
): UpdateStatus {
    val reader = Thread {
        runCatching {
            process.inputStream.bufferedReader().useLines { lines ->
                var announced = false
                for (line in lines) {
                    if (!announced) {
                        announced = true
                        if (needsAuth) onInstalling()
                    }
                }
            }
        }
    }.apply { isDaemon = true; start() }
    if (!needsAuth) onInstalling()
    val exitCode = process.waitFor()
    reader.join(2_000)
    return when {
        exitCode == 0 -> UpdateStatus.READY_TO_INSTALL
        needsAuth && exitCode in 126..127 -> UpdateStatus.AUTH_FAILED
        else -> UpdateStatus.ERROR
    }
}
