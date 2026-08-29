package com.alananasss.kittytune.data.applemusic

/**
 * Pulling the web player's own catalogue credential out of its JavaScript (issue #33).
 *
 * ## Why this shape, and why not the Android app
 *
 * "Many artists don't upload their music to soundcloud, youtube, spotify, I think it's worth adding Apple
 * Music and Yandex Music."
 *
 * Two routes were considered. The decompiled Android app turned out to be a dead end for a specific
 * reason: it carries no credential at all — `DeveloperToken` there is a *response* type, fetched at
 * runtime through Apple's native `amskit` store client, which proves to Apple that the caller is Apple's
 * own signed app. Reproducing that is not interoperability, it is impersonating their client. The only
 * two JWT-looking strings in 173 MB of decompiled sources decode to `{"kid":"1322222229"}` and an `exp`
 * in 2018: documentation examples.
 *
 * The web player is a different situation, and the same one this app already handles for SoundCloud in
 * [com.alananasss.kittytune.data.ClientIdScraper]: a public page hands every anonymous visitor a token so
 * that the public catalogue can be read, and reading the public catalogue with it is what a browser does.
 * This file is deliberately shaped like that scraper, because it is the same technique.
 *
 * ## What was verified before any of this was written
 *
 * `music.apple.com` serves `src="/assets/index~<hash>.js"`. That bundle contains three JWTs. The first
 * one tried answered 401 and the second answered 200, which is the whole reason [candidatesIn] returns a
 * list and the caller probes them in order rather than trusting a single match. Both live tokens carried
 * an expiry about two months out, so this is worth persisting rather than scraping per session.
 *
 * The endpoint is `https://amp-api.music.apple.com/v1/catalog/<storefront>/search`, and it needs
 * `Origin: https://music.apple.com` alongside the bearer — without the origin header it is rejected.
 */
object AppleMusicTokens {

    /** The web player's entry bundle, as its own HTML references it. */
    val bundleUrlRegex = Regex("""src="(/assets/index~[A-Za-z0-9]+\.js)"""")

    /**
     * Anything shaped like a signed JWT.
     *
     * Deliberately loose. The bundle holds several and only one of them is the catalogue's; which one is
     * not something the file says, so the caller finds out by asking the API. A tighter pattern here
     * would only mean failing when Apple adds a fourth.
     */
    private val jwtRegex =
        Regex("""eyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}""")

    /** Where the bundle lives, given what the page said. */
    fun bundleUrlIn(html: String): String? =
        bundleUrlRegex.find(html)?.groupValues?.get(1)?.let { "$WEB_PLAYER$it" }

    /** Every token the bundle carries, in the order it carries them. */
    fun candidatesIn(bundleJs: String): List<String> =
        jwtRegex.findAll(bundleJs).map { it.value }.distinct().toList()

    const val WEB_PLAYER = "https://music.apple.com"

    const val API = "https://amp-api.music.apple.com"

    /**
     * The storefront to read the catalogue from.
     *
     * Apple keys availability, titles and artwork to a country. Following the app's own language rather
     * than the machine's locale, for the same reason dates do: a French app on an English system should
     * not be reading the American storefront (issue #33).
     */
    fun storefrontFor(language: String): String = when (language) {
        "fr" -> "fr"
        "hu" -> "hu"
        "ru" -> "ru"
        else -> "us"
    }
}
