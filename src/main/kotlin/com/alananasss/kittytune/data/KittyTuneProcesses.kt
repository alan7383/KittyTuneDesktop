package com.alananasss.kittytune.data

import java.util.Locale

internal data class AppProcessInfo(
    val pid: Long,
    val parentPid: Long?,
    val command: String,
    val commandLine: String = "",
)

internal fun otherKittyTuneProcessIds(currentPid: Long, processes: List<AppProcessInfo>): Set<Long> {
    val byPid = processes.associateBy { it.pid }
    val ancestors = mutableSetOf(currentPid)
    var parent = byPid[currentPid]?.parentPid
    while (parent != null && ancestors.add(parent)) parent = byPid[parent]?.parentPid

    val descendants = mutableSetOf(currentPid)
    do {
        val added = processes.filter { it.parentPid in descendants }.map { it.pid }
            .filter { descendants.add(it) }
    } while (added.isNotEmpty())

    return processes.filter { process ->
        if (process.pid in ancestors || process.pid in descendants) return@filter false
        val commandLine = process.commandLine.lowercase(Locale.ROOT)
        if (commandLine.contains("gradledaemon") || commandLine.contains("gradleworkermain") ||
            commandLine.contains("gradlewrappermain")) return@filter false
        val name = process.command.substringAfterLast('/').substringAfterLast('\\').lowercase(Locale.ROOT)
        name in setOf("kittytune.exe", "kittytune", "kitty-tune") ||
            (name in setOf("java.exe", "javaw.exe", "java") &&
                commandLine.contains("com.alananasss.kittytune.mainkt"))
    }.mapTo(mutableSetOf()) { it.pid }
}
