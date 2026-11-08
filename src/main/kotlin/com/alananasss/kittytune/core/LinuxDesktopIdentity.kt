package com.alananasss.kittytune.core

import java.io.File

/** One name for WM_CLASS, desktop ID and icon lookup; no `kitty-*` theme fallback. */
internal object LinuxDesktopIdentity {
    const val APP_ID = "kittytune"
    const val DESKTOP_FILE = "$APP_ID.desktop"
    private val legacyNames = setOf("kitty-tune.desktop", "com-alananasss-kittytune-MainKt.desktop")

    fun isLauncher(file: File): Boolean = runCatching {
        file.extension == "desktop" && file.readLines().any { it == "Name=KittyTune" }
    }.getOrDefault(false)

    /** Keep old launch URLs usable, but expose only the canonical launcher in menus. */
    fun install(userApps: File, installed: List<File>, fallbackExec: String, variant: String) {
        userApps.mkdirs()
        val canonical = File(userApps, DESKTOP_FILE)
        val local = userApps.listFiles()?.filter(::isLauncher).orEmpty()
        val template = canonical.takeIf(::isLauncher)
            ?: local.firstOrNull { !it.readText().contains("NoDisplay=true") } ?: local.firstOrNull()
            ?: installed.firstOrNull(::isLauncher)
        val base = template?.readText() ?: "[Desktop Entry]\nName=KittyTune\nType=Application\nTerminal=false\n"
        val content = updateEntry(base, fallbackExec, variant, hidden = false)
        write(canonical, content)
        val aliases = (installed + local).filter(::isLauncher).map { it.name }.toSet() + legacyNames
        for (name in aliases - DESKTOP_FILE) {
            val previous = File(userApps, name).takeIf { it.isFile }?.readText()
                ?: installed.firstOrNull { it.name == name }?.readText() ?: base
            write(File(userApps, name), updateEntry(previous, fallbackExec, variant, hidden = true))
        }
    }

    internal fun updateEntry(text: String, fallbackExec: String, variant: String, hidden: Boolean): String {
        val lines = text.lines().toMutableList()
        val start = lines.indexOfFirst { it.trim() == "[Desktop Entry]" }
        require(start >= 0) { "Missing [Desktop Entry]" }
        var end = (start + 1 until lines.size).firstOrNull { lines[it].trim().startsWith('[') } ?: lines.size
        fun get(key: String) = (start + 1 until end).firstOrNull { lines[it].startsWith("$key=") }
        fun set(key: String, value: String) {
            val index = get(key)
            if (index != null) lines[index] = "$key=$value"
            else { lines.add(end, "$key=$value"); end++ }
        }
        if (get("Exec")?.let { lines[it].substringAfter('=').isNotBlank() } != true) set("Exec", fallbackExec)
        val categories = get("Categories")?.let { lines[it].substringAfter('=').split(';') }.orEmpty()
        set("Categories", (categories + listOf("AudioVideo", "Audio", "Music", "Player"))
            .filter { it.isNotBlank() }.distinct().joinToString(";", postfix = ";"))
        set("Icon", "$APP_ID-$variant")
        set("StartupWMClass", APP_ID)
        set("NoDisplay", hidden.toString())
        set("Hidden", "false")
        return lines.joinToString("\n").trimEnd() + "\n"
    }

    private fun write(target: File, content: String) {
        if (target.isFile && target.readText() == content) return
        val temporary = File.createTempFile("kittytune-launcher-", ".tmp", target.parentFile)
        try {
            temporary.writeText(content)
            AtomicFiles.replace(temporary, target)
        } finally { temporary.delete() }
    }
}
