package com.alananasss.kittytune.data.together

import com.alananasss.kittytune.core.Toaster
import com.alananasss.kittytune.core.str
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.net.URI
import java.net.URLDecoder

/**
 * Shareable links, clipboard helpers, and deep-link parsing for Live Listen Along / Together sessions.
 *
 * Uses the HTTPS KittyTune website URL (`https://alan7383.github.io/kittytune-website/?together=ABCD-EFGH`)
 * as the primary invite URL so that:
 * 1. Discord Rich Presence accepts it in `metadata.button_urls` (Discord requires `https://`).
 * 2. Every chat app (Discord, WhatsApp, Telegram, SMS) renders it as a clickable link.
 * 3. Opening the link shows the KittyTune Live Invite page, which triggers `kittytune://together/ABCD-EFGH?join=1`
 *    to wake or launch KittyTune on Android, Windows, Linux, or macOS.
 */
object TogetherLinks {

    const val WEB_BASE_URL = "https://alan7383.github.io/kittytune-website/"
    const val APP_SCHEME = "kittytune"
    const val APP_HOST_TOGETHER = "together"

    private val URL_QUERY_REGEX = Regex(
        """(?:[?&](?:together|listen_along)=([A-Za-z0-9-]{8,12}))""",
        RegexOption.IGNORE_CASE
    )
    private val DIRECT_LINK_REGEX = Regex(
        """(?:(?:kittytune|sc)://(?:together|listen)(?:/|\?(?:code|together)=)|kittytune-website/(?:together|listen)(?:/|\?(?:code|together)=))([A-Za-z0-9-]{8,12})""",
        RegexOption.IGNORE_CASE
    )
    private val LABELED_CODE_REGEX = Regex(
        """(?:code|kode|kod|kód|código|код|mã)\s*[:=]\s*([A-Za-z0-9]{4}-[A-Za-z0-9]{4}|[A-Za-z0-9]{8})""",
        RegexOption.IGNORE_CASE
    )
    private val HYPHENATED_CODE_REGEX = Regex(
        """\b([A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4})\b""",
        RegexOption.IGNORE_CASE
    )

    fun inviteUrl(code: String): String {
        val formatted = TogetherWire.displayCode(code)
        return "${WEB_BASE_URL}?together=$formatted"
    }

    fun appDeepLink(code: String, autoJoin: Boolean = false): String {
        val formatted = TogetherWire.displayCode(code)
        val suffix = if (autoJoin) "?join=1" else ""
        return "$APP_SCHEME://$APP_HOST_TOGETHER/$formatted$suffix"
    }

    fun isCustomSchemeUri(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val trimmed = raw.trim()
        return trimmed.startsWith("$APP_SCHEME://", ignoreCase = true) ||
            trimmed.startsWith("sc://together", ignoreCase = true) ||
            trimmed.startsWith("sc://listen", ignoreCase = true)
    }

    fun isTogetherUri(raw: String?): Boolean = extractCodeFromUri(raw) != null

    fun isAutoJoinUri(uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return false
        val query = runCatching { URI(uriString.trim()).rawQuery }.getOrNull()
            ?: uriString.substringAfter('?', "")
        if (query.isBlank()) return false
        for (pair in query.split('&')) {
            val key = pair.substringBefore('=').lowercase()
            val value = pair.substringAfter('=', "").lowercase()
            if ((key == "join" || key == "autojoin") && (value == "1" || value == "true")) {
                return true
            }
        }
        return false
    }

    fun extractCodeFromUri(uriString: String?): String? {
        if (uriString.isNullOrBlank()) return null
        return extractCode(uriString, allowRawCode = false)
    }

    /**
     * Extracts a normalized 8-character Together room code from a URL, deep link, shared message,
     * or (when [allowRawCode] is true) a raw user-typed code like `ABCD-EFGH` or `ABCDEFGH`.
     */
    fun extractCode(input: String?, allowRawCode: Boolean = true): String? {
        if (input.isNullOrBlank()) return null
        val decoded = runCatching { URLDecoder.decode(input.trim(), "UTF-8") }.getOrDefault(input.trim())

        URL_QUERY_REGEX.find(decoded)?.groupValues?.getOrNull(1)?.let { candidate ->
            validNormalized(candidate)?.let { return it }
        }

        DIRECT_LINK_REGEX.find(decoded)?.groupValues?.getOrNull(1)?.let { candidate ->
            validNormalized(candidate)?.let { return it }
        }

        if (allowRawCode) {
            LABELED_CODE_REGEX.find(decoded)?.groupValues?.getOrNull(1)?.let { candidate ->
                validNormalized(candidate)?.let { return it }
            }
            HYPHENATED_CODE_REGEX.find(decoded)?.groupValues?.getOrNull(1)?.let { candidate ->
                validNormalized(candidate)?.let { return it }
            }
            val direct = TogetherWire.normalizeCode(decoded)
            if (direct.length == TogetherWire.CODE_LENGTH && decoded.length <= 12) {
                return direct
            }
        }

        return null
    }

    private fun validNormalized(raw: String): String? {
        val normalized = TogetherWire.normalizeCode(raw)
        return normalized.takeIf { it.length == TogetherWire.CODE_LENGTH }
    }

    fun copyCode(code: String) {
        val formatted = TogetherWire.displayCode(code)
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(formatted), null)
            Toaster.show(str("together_code_copied"))
        }
    }

    fun copyInviteLink(code: String) {
        val url = inviteUrl(code)
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(url), null)
            Toaster.show(str("together_link_copied"))
        }
    }

    fun copyShareMessage(code: String) {
        val url = inviteUrl(code)
        val display = TogetherWire.displayCode(code)
        val message = str("together_share_message", url, display)
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(message), null)
            Toaster.show(str("together_link_copied"))
        }
    }

    fun readClipboardInviteCode(): String? = runCatching {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) return null
        val text = clipboard.getData(DataFlavor.stringFlavor) as? String ?: return null
        extractCode(text, allowRawCode = false)
    }.getOrNull()
}
