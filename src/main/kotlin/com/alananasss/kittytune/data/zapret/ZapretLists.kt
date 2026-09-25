package com.alananasss.kittytune.data.zapret

import java.io.File

/**
 * A zapret installation and the host lists KittyTune reads and writes in it.
 *
 * Two layouts are understood: the Windows bundle (`lists/list-general.txt` plus the user's own
 * `lists/list-general-user.txt`, read by `winws.exe`) and zapret on Linux (`ipset/zapret-hosts-user.txt`,
 * read by `nfqws`). KittyTune only ever touches the user list, and only between its own marker lines, so
 * removing what it added leaves everything else in the file exactly as it was.
 */
class ZapretInstall(val folder: File) {

    /** The list KittyTune adds to. */
    val userList: File = listOf(
        File(folder, "lists/list-general-user.txt"),
        File(folder, "ipset/zapret-hosts-user.txt"),
    ).firstOrNull { it.isFile } ?: File(folder, "lists/list-general-user.txt")

    /** Lists shipped with zapret; a domain already in one of them needs nothing from us. */
    private val bundledLists: List<File> = listOf(
        File(folder, "lists/list-general.txt"),
        File(folder, "lists/list-google.txt"),
        File(folder, "ipset/zapret-hosts.txt"),
    ).filter { it.isFile }

    val isValid: Boolean get() = userList.isFile || File(folder, "lists").isDirectory || File(folder, "ipset").isDirectory

    /** Every domain zapret already handles, from its own lists and the user's. */
    fun coveredDomains(): Set<String> =
        (bundledLists + userList).filter { it.isFile }.flatMap { ZapretHostList.domains(it.readText()) }.toSet()

    /** The domains KittyTune added, in the order they were written. */
    fun kittyTuneDomains(): List<String> =
        if (userList.isFile) ZapretHostList.ownDomains(userList.readText()) else emptyList()

    /** Adds [domains] (those not covered already) to KittyTune's block of the user list; returns what was added. */
    fun add(domains: Collection<String>): List<String> {
        val covered = coveredDomains()
        val missing = domains.map { it.lowercase().trim() }.distinct().filter { !ZapretHostList.isCovered(it, covered) }
        if (missing.isEmpty()) return emptyList()
        val text = if (userList.isFile) userList.readText() else ""
        userList.parentFile?.mkdirs()
        userList.writeText(ZapretHostList.withOwnDomains(text, ZapretHostList.ownDomains(text) + missing))
        return missing
    }

    /** Takes KittyTune's block out of the user list again. */
    fun removeOwn() {
        if (!userList.isFile) return
        userList.writeText(ZapretHostList.withOwnDomains(userList.readText(), emptyList()))
    }
}

/** Reading and rewriting a zapret host list as text. Pure, so it can be tested without files. */
object ZapretHostList {
    const val BEGIN = "# KittyTune — begin"
    const val END = "# KittyTune — end"

    fun domains(text: String): List<String> = text.lineSequence()
        .map { it.substringBefore('#').trim().lowercase() }
        .filter { it.isNotEmpty() }
        .toList()

    /** Whether [domain] or one of its parents is in [covered] — host lists match subdomains. */
    fun isCovered(domain: String, covered: Set<String>): Boolean {
        var candidate = domain
        while (true) {
            if (candidate in covered) return true
            val dot = candidate.indexOf('.')
            if (dot < 0 || candidate.indexOf('.', dot + 1) < 0) return false
            candidate = candidate.substring(dot + 1)
        }
    }

    fun ownDomains(text: String): List<String> {
        val lines = text.lines()
        val begin = lines.indexOfFirst { it.trim() == BEGIN }
        if (begin < 0) return emptyList()
        val end = lines.drop(begin + 1).indexOfFirst { it.trim() == END }.let { if (it < 0) lines.size else begin + 1 + it }
        return domains(lines.subList(begin + 1, end).joinToString("\n"))
    }

    /**
     * [text] with KittyTune's block replaced by [own] (or removed when empty). Everything outside the block is
     * kept as it was, and a file that did not end in a newline gets one before the block.
     */
    fun withOwnDomains(text: String, own: List<String>): String {
        val lines = text.lines().toMutableList()
        val begin = lines.indexOfFirst { it.trim() == BEGIN }
        if (begin >= 0) {
            val endOffset = lines.drop(begin + 1).indexOfFirst { it.trim() == END }
            val end = if (endOffset < 0) lines.size - 1 else begin + 1 + endOffset
            repeat(end - begin + 1) { lines.removeAt(begin) }
        }
        while (lines.isNotEmpty() && lines.last().isBlank()) lines.removeAt(lines.lastIndex)
        if (own.isNotEmpty()) {
            lines += BEGIN
            lines += own
            lines += END
        }
        // zapret refuses an empty list, which is why the stock file carries a placeholder domain.
        if (lines.none { it.substringBefore('#').isNotBlank() }) lines += "domain.example.abc"
        return lines.joinToString("\n", postfix = "\n")
    }
}
