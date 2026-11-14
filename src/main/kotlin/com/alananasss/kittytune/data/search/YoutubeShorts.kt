package com.alananasss.kittytune.data.search

/**
 * A YouTube search result is a Short when the extractor flags it as short-form content or when
 * its URL is a /shorts/ link (older result shapes only carry the URL).
 */
fun isYoutubeShort(shortFormContent: Boolean, url: String?): Boolean =
    shortFormContent || url?.contains("/shorts/") == true
