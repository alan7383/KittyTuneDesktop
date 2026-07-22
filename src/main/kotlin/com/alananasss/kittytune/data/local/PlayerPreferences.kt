package com.alananasss.kittytune.data.local

import com.alananasss.kittytune.core.AppDirs
import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.player.AudioEffectsState
import com.alananasss.kittytune.ui.player.PlaybackContext
import com.alananasss.kittytune.ui.player.RepeatMode
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.FileReader
import java.io.FileWriter

enum class AppThemeMode { SYSTEM, LIGHT, DARK }
enum class PlayerBackgroundStyle { THEME, GRADIENT, BLUR }
enum class StartDestination { HOME, LIBRARY }
enum class LyricsAlignment { LEFT, CENTER, RIGHT }

enum class DiscordStatusDisplay { ACTIVITY, SOUNDCLOUD, ARTIST, SONG }

enum class AppLanguage(val code: String) {
    SYSTEM("system"),
    FRENCH("fr"),
    ENGLISH("en"),
    HUNGARIAN("hu")
}

/**
 * Desktop port of the Android PlayerPreferences.
 * SharedPreferences("player_state") -> the global [Prefs] JSON store (same keys/defaults).
 * queue_cache.json kept in the app data dir; Flows come from Prefs' reactive map.
 */
class PlayerPreferences {
    private val gson = Gson()
    private val queueFile = File(AppDirs.dataDir, "queue_cache.json")

    companion object {
        const val KEY_LISTENING_STATS_ENABLED = "listening_stats_enabled"
        private const val KEY_TRACK_JSON = "last_track_json"
        private const val KEY_POSITION = "last_position"
        private const val KEY_EFFECTS = "audio_effects"
        private const val KEY_CONTEXT_JSON = "last_context_json"
        private const val KEY_SHUFFLE_MODE = "shuffle_mode_enabled"
        private const val KEY_REPEAT_MODE = "repeat_mode_state"
        private const val KEY_DOWNLOAD_DIR = "download_directory_uri"
        private const val KEY_AUTOPLAY_STATION = "autoplay_station_enabled"
        private const val KEY_AUDIO_QUALITY = "audio_quality_pref"
        private const val KEY_PERSISTENT_QUEUE = "persistent_queue_enabled"
        private const val KEY_START_DESTINATION = "start_destination_pref"
        private const val KEY_DYNAMIC_THEME = "dynamic_theme_enabled"
        private const val KEY_THEME_MODE = "app_theme_mode"
        private const val KEY_PURE_BLACK = "pure_black_enabled"
        private const val KEY_PLAYER_STYLE = "player_background_style"
        private const val KEY_LOCAL_MEDIA_ENABLED = "local_media_enabled"
        private const val KEY_LOCAL_MEDIA_URIS_SET = "local_media_uris_set_v2"
        private const val KEY_LYRICS_PREFER_LOCAL = "lyrics_prefer_local"
        private const val KEY_LYRICS_ALIGNMENT = "lyrics_alignment"
        private const val KEY_LYRICS_FONT_SIZE = "lyrics_font_size"
        private const val KEY_APP_LANGUAGE = "app_language_code"
        private const val KEY_ACHIEVEMENT_POPUPS = "achievement_popups_enabled"
        private const val KEY_PRECISE_SPEED = "precise_speed_enabled"
        private const val KEY_AUTO_UPDATE = "auto_update_enabled"
        private const val KEY_YOUTUBE_FALLBACK = "youtube_fallback_enabled"
        private const val KEY_DOWNLOAD_DRM_STREAMS = "download_drm_streams_enabled"
        private const val KEY_SHOW_LYRICS_BUTTON = "show_lyrics_button_enabled"
        private const val KEY_INLINE_LYRICS = "inline_lyrics_enabled"
        private const val KEY_DISCORD_TOKEN = "discord_token"
        private const val KEY_DISCORD_ENABLED = "discord_rpc_enabled"
        private const val KEY_PRECISE_LYRICS_SEARCH = "precise_lyrics_search_enabled"
        private const val KEY_EARRAPE_WARNING = "has_seen_earrape_warning"

        private const val KEY_DISCORD_ASSET_LOGO = "discord_asset_logo"
        private const val KEY_DISCORD_STATUS_DISPLAY = "discord_status_display"
        private const val KEY_CUSTOM_FONT_ENABLED = "custom_font_enabled"
        private const val KEY_FONT_WGHT = "font_wght"
        private const val KEY_FONT_WDTH = "font_wdth"
        private const val KEY_FONT_SLNT = "font_slnt"
        private const val KEY_FONT_ROND = "font_rond"
        private const val KEY_FONT_GRAD = "font_grad"
        private const val KEY_FONT_OPSZ = "font_opsz"
        private const val KEY_SYNC_LIKES = "sync_likes_enabled"
        private const val KEY_CROSSFADE_ENABLED = "crossfade_enabled"
        private const val KEY_CROSSFADE_DURATION = "crossfade_duration"
        private const val KEY_KEY_COLOR = "key_color"
        private const val KEY_COLOR_STYLE = "color_style"
        private const val KEY_COLOR_SPEC = "color_spec"
        private const val KEY_SLEEP_TIMER_FADE_DURATION = "sleep_timer_fade_duration"
        private const val KEY_SLEEP_TIMER_FADE_ENABLED = "sleep_timer_fade_enabled"

        const val SLEEP_TIMER_FADE_DURATION_MIN = 0
        const val SLEEP_TIMER_FADE_DURATION_MAX = 30
        const val SLEEP_TIMER_FADE_DURATION_DEFAULT = 30
        const val SLEEP_TIMER_FADE_UPDATE_INTERVAL_MS = 50L

        private const val KEY_BOTTOM_MENU_STYLE = "bottom_menu_style"
        private const val KEY_BOTTOM_MENU_ITEMS = "bottom_menu_items_csv"
        private const val KEY_BOTTOM_MENU_FAB = "bottom_menu_fab"
        private const val KEY_BOTTOM_MENU_BLUR = "bottom_menu_blur_enabled"
        private const val KEY_STOP_ON_TASK_CLEAR = "stop_on_task_clear"
        private const val KEY_NEW_PLAYER_DESIGN = "new_player_design_enabled"

        private val queueLock = Any()
    }

    fun getSyncLikesEnabled(): Boolean = Prefs.getBoolean(KEY_SYNC_LIKES, true)
    fun setSyncLikesEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_SYNC_LIKES, enabled)

    fun getCrossfadeEnabled(): Boolean = Prefs.getBoolean(KEY_CROSSFADE_ENABLED, false)
    fun setCrossfadeEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_CROSSFADE_ENABLED, enabled)

    fun getCrossfadeDuration(): Int = Prefs.getInt(KEY_CROSSFADE_DURATION, 5)
    fun setCrossfadeDuration(seconds: Int) = Prefs.putInt(KEY_CROSSFADE_DURATION, seconds.coerceIn(1, 12))

    fun getCustomFontEnabled() = Prefs.getBoolean(KEY_CUSTOM_FONT_ENABLED, true)
    fun setCustomFontEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_CUSTOM_FONT_ENABLED, enabled)

    fun getFontWght() = Prefs.getInt(KEY_FONT_WGHT, 400)
    fun setFontWght(value: Int) = Prefs.putInt(KEY_FONT_WGHT, value)

    fun getFontWdth() = Prefs.getFloat(KEY_FONT_WDTH, 100f)
    fun setFontWdth(value: Float) = Prefs.putFloat(KEY_FONT_WDTH, value)

    fun getFontSlnt() = Prefs.getFloat(KEY_FONT_SLNT, 0f)
    fun setFontSlnt(value: Float) = Prefs.putFloat(KEY_FONT_SLNT, value)

    fun getFontRond() = Prefs.getFloat(KEY_FONT_ROND, 0f)
    fun setFontRond(value: Float) = Prefs.putFloat(KEY_FONT_ROND, value)

    fun getFontGrad() = Prefs.getFloat(KEY_FONT_GRAD, 0f)
    fun setFontGrad(value: Float) = Prefs.putFloat(KEY_FONT_GRAD, value)

    fun getFontOpsz() = Prefs.getFloat(KEY_FONT_OPSZ, 14f)
    fun setFontOpsz(value: Float) = Prefs.putFloat(KEY_FONT_OPSZ, value)

    fun getDiscordStatusDisplay(): DiscordStatusDisplay {
        val name = Prefs.getString(KEY_DISCORD_STATUS_DISPLAY, DiscordStatusDisplay.ACTIVITY.name)
        return try { DiscordStatusDisplay.valueOf(name!!) } catch (_: Exception) { DiscordStatusDisplay.ACTIVITY }
    }
    fun setDiscordStatusDisplay(display: DiscordStatusDisplay) = Prefs.putString(KEY_DISCORD_STATUS_DISPLAY, display.name)

    fun getDiscordToken(): String? = Prefs.getString(KEY_DISCORD_TOKEN, null)
    fun setDiscordToken(token: String?) = Prefs.putString(KEY_DISCORD_TOKEN, token)

    fun getDiscordRpcEnabled(): Boolean = Prefs.getBoolean(KEY_DISCORD_ENABLED, false)
    fun setDiscordRpcEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_DISCORD_ENABLED, enabled)

    fun getDiscordAssetLogo(): String? = Prefs.getString(KEY_DISCORD_ASSET_LOGO, null)
    fun setDiscordAssetLogo(assetId: String?) = Prefs.putString(KEY_DISCORD_ASSET_LOGO, assetId)

    fun getInlineLyricsEnabled(): Boolean = Prefs.getBoolean(KEY_INLINE_LYRICS, true)
    fun setInlineLyricsEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_INLINE_LYRICS, enabled)

    fun getShowLyricsButtonEnabled(): Boolean = Prefs.getBoolean(KEY_SHOW_LYRICS_BUTTON, true)
    fun setShowLyricsButtonEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_SHOW_LYRICS_BUTTON, enabled)

    fun getYouTubeFallbackEnabled(): Boolean = Prefs.getBoolean(KEY_YOUTUBE_FALLBACK, true)
    fun setYouTubeFallbackEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_YOUTUBE_FALLBACK, enabled)

    fun getDownloadDrmStreamsEnabled(): Boolean = Prefs.getBoolean(KEY_DOWNLOAD_DRM_STREAMS, true)
    fun setDownloadDrmStreamsEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_DOWNLOAD_DRM_STREAMS, enabled)
    fun getAutoUpdateEnabled(): Boolean = Prefs.getBoolean(KEY_AUTO_UPDATE, true)
    fun setAutoUpdateEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_AUTO_UPDATE, enabled)

    fun getAchievementPopupsEnabled(): Boolean = Prefs.getBoolean(KEY_ACHIEVEMENT_POPUPS, false)
    fun setAchievementPopupsEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_ACHIEVEMENT_POPUPS, enabled)

    fun getPreciseSpeedEnabled(): Boolean = Prefs.getBoolean(KEY_PRECISE_SPEED, false)
    fun setPreciseSpeedEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_PRECISE_SPEED, enabled)

    fun getAppLanguage(): AppLanguage {
        val code = Prefs.getString(KEY_APP_LANGUAGE, AppLanguage.SYSTEM.code)
        return AppLanguage.entries.find { it.code == code } ?: AppLanguage.SYSTEM
    }
    fun setAppLanguage(language: AppLanguage) = Prefs.putString(KEY_APP_LANGUAGE, language.code)

    fun getLyricsPreferLocal(): Boolean = Prefs.getBoolean(KEY_LYRICS_PREFER_LOCAL, false)
    fun setLyricsPreferLocal(enabled: Boolean) = Prefs.putBoolean(KEY_LYRICS_PREFER_LOCAL, enabled)

    fun getPreciseLyricsSearchEnabled(): Boolean = Prefs.getBoolean(KEY_PRECISE_LYRICS_SEARCH, true)
    fun setPreciseLyricsSearchEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_PRECISE_LYRICS_SEARCH, enabled)

    fun hasSeenEarrapeWarning(): Boolean = Prefs.getBoolean(KEY_EARRAPE_WARNING, false)
    fun setHasSeenEarrapeWarning(seen: Boolean) = Prefs.putBoolean(KEY_EARRAPE_WARNING, seen)

    fun getLyricsAlignment(): LyricsAlignment {
        val name = Prefs.getString(KEY_LYRICS_ALIGNMENT, LyricsAlignment.LEFT.name)
        return try { LyricsAlignment.valueOf(name!!) } catch (_: Exception) { LyricsAlignment.LEFT }
    }
    fun setLyricsAlignment(align: LyricsAlignment) = Prefs.putString(KEY_LYRICS_ALIGNMENT, align.name)



    fun getLyricsFontSize(): Float = Prefs.getFloat(KEY_LYRICS_FONT_SIZE, 42f)
    fun setLyricsFontSize(size: Float) = Prefs.putFloat(KEY_LYRICS_FONT_SIZE, size)
    fun getLocalMediaEnabled(): Boolean = Prefs.getBoolean(KEY_LOCAL_MEDIA_ENABLED, false)
    fun setLocalMediaEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_LOCAL_MEDIA_ENABLED, enabled)
    fun getLocalMediaUris(): Set<String> = Prefs.getStringSet(KEY_LOCAL_MEDIA_URIS_SET, emptySet())
    fun addLocalMediaUri(uri: String) { val c = getLocalMediaUris().toMutableSet(); c.add(uri); Prefs.putStringSet(KEY_LOCAL_MEDIA_URIS_SET, c) }
    fun removeLocalMediaUri(uri: String) { val c = getLocalMediaUris().toMutableSet(); c.remove(uri); Prefs.putStringSet(KEY_LOCAL_MEDIA_URIS_SET, c) }
    fun getStartDestination(): StartDestination { val n = Prefs.getString(KEY_START_DESTINATION, StartDestination.HOME.name); return try { StartDestination.valueOf(n!!) } catch (_: Exception) { StartDestination.HOME } }
    fun setStartDestination(dest: StartDestination) = Prefs.putString(KEY_START_DESTINATION, dest.name)
    fun getDynamicTheme(): Boolean = Prefs.getBoolean(KEY_DYNAMIC_THEME, true)
    fun setDynamicTheme(enabled: Boolean) = Prefs.putBoolean(KEY_DYNAMIC_THEME, enabled)
    fun getThemeMode(): AppThemeMode { val n = Prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name); return try { AppThemeMode.valueOf(n!!) } catch (_: Exception) { AppThemeMode.SYSTEM } }
    fun setThemeMode(mode: AppThemeMode) = Prefs.putString(KEY_THEME_MODE, mode.name)
    fun getPureBlack(): Boolean = Prefs.getBoolean(KEY_PURE_BLACK, false)
    fun setPureBlack(enabled: Boolean) = Prefs.putBoolean(KEY_PURE_BLACK, enabled)
    fun getPlayerStyle(): PlayerBackgroundStyle { val n = Prefs.getString(KEY_PLAYER_STYLE, PlayerBackgroundStyle.BLUR.name); return try { PlayerBackgroundStyle.valueOf(n!!) } catch (_: Exception) { PlayerBackgroundStyle.BLUR } }
    fun setPlayerStyle(style: PlayerBackgroundStyle) = Prefs.putString(KEY_PLAYER_STYLE, style.name)
    fun getAutoplayEnabled(): Boolean = Prefs.getBoolean(KEY_AUTOPLAY_STATION, true)
    fun setAutoplayEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_AUTOPLAY_STATION, enabled)
    fun getListeningStatsEnabled(): Boolean = Prefs.getBoolean(KEY_LISTENING_STATS_ENABLED, true)
    fun setListeningStatsEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_LISTENING_STATS_ENABLED, enabled)
    fun getAudioQuality(): String = Prefs.getString(KEY_AUDIO_QUALITY, "HIGH") ?: "HIGH"
    fun setAudioQuality(quality: String) = Prefs.putString(KEY_AUDIO_QUALITY, quality)
    fun getPersistentQueueEnabled(): Boolean = Prefs.getBoolean(KEY_PERSISTENT_QUEUE, true)
    fun setPersistentQueueEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_PERSISTENT_QUEUE, enabled)

    fun getRightPanelWidth(): Float = Prefs.getFloat("right_panel_width", RIGHT_PANEL_DEFAULT_WIDTH).coerceIn(RIGHT_PANEL_MIN_WIDTH, RIGHT_PANEL_MAX_WIDTH)
    fun setRightPanelWidth(width: Float) = Prefs.putFloat("right_panel_width", width.coerceIn(RIGHT_PANEL_MIN_WIDTH, RIGHT_PANEL_MAX_WIDTH))

    fun getKeyColor(): Int = Prefs.getInt(KEY_KEY_COLOR, 0)
    fun setKeyColor(color: Int) = Prefs.putInt(KEY_KEY_COLOR, color)

    fun getColorStyle(): String = Prefs.getString(KEY_COLOR_STYLE, "System") ?: "System"
    fun setColorStyle(style: String) = Prefs.putString(KEY_COLOR_STYLE, style)

    fun getColorSpec(): String = Prefs.getString(KEY_COLOR_SPEC, "SPEC_2025") ?: "SPEC_2025"
    fun setColorSpec(spec: String) = Prefs.putString(KEY_COLOR_SPEC, spec)

    fun getSleepTimerFadeEnabled(): Boolean = Prefs.getBoolean(KEY_SLEEP_TIMER_FADE_ENABLED, false)
    fun setSleepTimerFadeEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_SLEEP_TIMER_FADE_ENABLED, enabled)

    fun getSleepTimerFadeDuration(): Int = Prefs.getInt(KEY_SLEEP_TIMER_FADE_DURATION, SLEEP_TIMER_FADE_DURATION_DEFAULT)
    fun setSleepTimerFadeDuration(seconds: Int) =
        Prefs.putInt(KEY_SLEEP_TIMER_FADE_DURATION, seconds.coerceIn(SLEEP_TIMER_FADE_DURATION_MIN, SLEEP_TIMER_FADE_DURATION_MAX))

    fun getBottomMenuStyle(): String = Prefs.getString(KEY_BOTTOM_MENU_STYLE, "modern") ?: "modern"
    fun setBottomMenuStyle(style: String) = Prefs.putString(KEY_BOTTOM_MENU_STYLE, style)
    fun bottomMenuStyleFlow(): Flow<String> = Prefs.stringFlow(KEY_BOTTOM_MENU_STYLE, "modern").map { it ?: "modern" }

    fun getBottomMenuItems(): List<String> {
        val csv = Prefs.getString(KEY_BOTTOM_MENU_ITEMS, "home,search,genres,library") ?: "home,search,genres,library"
        return csv.split(",").filter { it.isNotBlank() }
    }
    fun setBottomMenuItems(items: List<String>) = Prefs.putString(KEY_BOTTOM_MENU_ITEMS, items.joinToString(","))
    fun bottomMenuItemsFlow(): Flow<List<String>> =
        Prefs.stringFlow(KEY_BOTTOM_MENU_ITEMS, "home,search,genres,library").map { csv ->
            (csv ?: "home,search,genres,library").split(",").filter { it.isNotBlank() }
        }

    fun getBottomMenuFab(): String = Prefs.getString(KEY_BOTTOM_MENU_FAB, "settings") ?: "settings"
    fun setBottomMenuFab(fab: String) = Prefs.putString(KEY_BOTTOM_MENU_FAB, fab)
    fun bottomMenuFabFlow(): Flow<String> = Prefs.stringFlow(KEY_BOTTOM_MENU_FAB, "settings").map { it ?: "settings" }

    fun getBottomMenuBlurEnabled(): Boolean = Prefs.getBoolean(KEY_BOTTOM_MENU_BLUR, true)
    fun setBottomMenuBlurEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_BOTTOM_MENU_BLUR, enabled)
    fun bottomMenuBlurFlow(): Flow<Boolean> = Prefs.booleanFlow(KEY_BOTTOM_MENU_BLUR, true)

    fun getStopOnTaskClear(): Boolean = Prefs.getBoolean(KEY_STOP_ON_TASK_CLEAR, true)
    fun setStopOnTaskClear(enabled: Boolean) = Prefs.putBoolean(KEY_STOP_ON_TASK_CLEAR, enabled)
    fun getNewPlayerDesignEnabled(): Boolean = Prefs.getBoolean(KEY_NEW_PLAYER_DESIGN, true)
    fun setNewPlayerDesignEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_NEW_PLAYER_DESIGN, enabled)

    fun savePlaybackState(track: Track?, position: Long, queue: List<Track>, context: PlaybackContext?, shuffleEnabled: Boolean, repeatMode: RepeatMode) {
        if (!getPersistentQueueEnabled()) {
            Prefs.putBoolean(KEY_SHUFFLE_MODE, shuffleEnabled)
            Prefs.putString(KEY_REPEAT_MODE, repeatMode.name)
            Prefs.remove(KEY_TRACK_JSON)
            if (queueFile.exists()) queueFile.delete()
            Prefs.remove(KEY_POSITION)
            Prefs.remove(KEY_CONTEXT_JSON)
            return
        }

        if (queue.isNotEmpty()) {
            synchronized(queueLock) {
                try {
                    val tempFile = File(queueFile.parentFile, "queue_cache.tmp")
                    FileWriter(tempFile).use { writer -> gson.toJson(queue, writer) }
                    tempFile.copyTo(queueFile, overwrite = true)
                    tempFile.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        track?.let { Prefs.putString(KEY_TRACK_JSON, gson.toJson(it)) }
        Prefs.putString(KEY_CONTEXT_JSON, gson.toJson(context))
        Prefs.putLong(KEY_POSITION, position)
        Prefs.putBoolean(KEY_SHUFFLE_MODE, shuffleEnabled)
        Prefs.putString(KEY_REPEAT_MODE, repeatMode.name)
    }

    fun saveEffects(state: AudioEffectsState) = Prefs.putString(KEY_EFFECTS, gson.toJson(state))
    fun saveDownloadLocation(uriString: String?) = if (uriString != null) Prefs.putString(KEY_DOWNLOAD_DIR, uriString) else Prefs.remove(KEY_DOWNLOAD_DIR)
    fun getDownloadLocation(): String? = Prefs.getString(KEY_DOWNLOAD_DIR, null)
    fun getLastTrack(): Track? { if (!getPersistentQueueEnabled()) return null; val json = Prefs.getString(KEY_TRACK_JSON, null) ?: return null; return try { gson.fromJson(json, Track::class.java) } catch (_: Exception) { null } }
    fun getLastPosition(): Long = Prefs.getLong(KEY_POSITION, 0L)
    fun getLastQueue(): List<Track> {
        if (!getPersistentQueueEnabled()) return emptyList()
        if (queueFile.exists()) {
            return synchronized(queueLock) {
                try {
                    val type = object : TypeToken<List<Track>>() {}.type
                    FileReader(queueFile).use { reader -> gson.fromJson(reader, type) ?: emptyList() }
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }
        val json = Prefs.getString("last_queue_full_json", null) ?: return emptyList()
        val type = object : TypeToken<List<Track>>() {}.type
        return try { gson.fromJson(json, type) ?: emptyList() } catch (_: Exception) { emptyList() }
    }
    fun getLastContext(): PlaybackContext? { if (!getPersistentQueueEnabled()) return null; val json = Prefs.getString(KEY_CONTEXT_JSON, null) ?: return null; return try { gson.fromJson(json, PlaybackContext::class.java) } catch (_: Exception) { null } }
    fun getLastShuffleEnabled(): Boolean = Prefs.getBoolean(KEY_SHUFFLE_MODE, false)
    fun getLastRepeatMode(): RepeatMode { val modeName = Prefs.getString(KEY_REPEAT_MODE, RepeatMode.NONE.name); return try { RepeatMode.valueOf(modeName ?: RepeatMode.NONE.name) } catch (_: Exception) { RepeatMode.NONE } }
    fun getLastEffects(): AudioEffectsState { val json = Prefs.getString(KEY_EFFECTS, null) ?: return AudioEffectsState(); return try { gson.fromJson(json, AudioEffectsState::class.java) } catch (_: Exception) { AudioEffectsState() } }
}

const val RIGHT_PANEL_MIN_WIDTH = 280f
const val RIGHT_PANEL_MAX_WIDTH = 440f
const val RIGHT_PANEL_DEFAULT_WIDTH = 340f
