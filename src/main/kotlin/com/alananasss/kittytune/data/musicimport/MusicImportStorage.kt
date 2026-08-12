package com.alananasss.kittytune.data.musicimport

import com.alananasss.kittytune.core.Application
import com.alananasss.kittytune.core.NamedPrefs
import com.google.gson.Gson

/**
 * Persists the connected musicapi integration for each platform, plus the
 * state needed to manage an in-progress transfer (Transfer your gems).
 *
 * Desktop port: Android SharedPreferences("music_import") -> NamedPrefs("music_import")
 * (a JSON file in the app data dir, same name for backup compatibility).
 */
class MusicImportStorage(@Suppress("UNUSED_PARAMETER") application: Application? = null) {
    private val prefs = NamedPrefs("music_import")

    private val gson = Gson()

    private fun platformKey(platform: String): String = "auth_$platform"
    private fun revertKey(platform: String): String = "revert_$platform"

    fun saveAuth(platform: String, auth: MusicApiAuth) {
        prefs.putString(platformKey(platform), gson.toJson(auth))
    }

    fun getAuth(platform: String): MusicApiAuth? {
        val raw = prefs.getString(platformKey(platform), null) ?: return null
        return runCatching { gson.fromJson(raw, MusicApiAuth::class.java) }.getOrNull()
    }

    fun clearAuth(platform: String) {
        prefs.remove(platformKey(platform))
    }

    fun setLikesRevertState(platform: String, syncId: String) {
        prefs.putString(revertKey(platform), syncId)
    }

    fun getLikesRevertState(platform: String): String? = prefs.getString(revertKey(platform), null)
}
