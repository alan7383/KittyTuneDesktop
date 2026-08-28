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


enum class AppThemeMode { SYSTEM, LIGHT, DARK }
enum class StartDestination { HOME, LIBRARY }

/**
 * Which half the info panel opens on: the comments, the lyrics, or whichever was last chosen
 * (issue #33).
 *
 * Asked for by analogy with the start-screen setting, and it also settles a bug: the choice used to
 * live in a `rememberSaveable`, whose registry goes away with the panel, so picking the lyrics and
 * closing the panel got you the comments back on the next open.
 */
enum class InfoPanelHalf { COMMENTS, LYRICS, REMEMBER }
enum class LyricsAlignment { LEFT, CENTER, RIGHT }

/**
 * How much the lyrics views set the line being sung apart from the rest (issue #33).
 *
 * [STANDARD] is what the app has always done: the current line is brighter. [SCALE] also grows
 * it, and [FOCUS] pushes the rest well back instead — the two treatments asked for, each useful
 * on a different kind of lyric sheet.
 */
enum class LyricsDisplayStyle { STANDARD, SCALE, FOCUS }

enum class DiscordStatusDisplay { ACTIVITY, SOUNDCLOUD, ARTIST, SONG }

enum class AppLanguage(val code: String) {
    SYSTEM("system"),
    FRENCH("fr"),
    ENGLISH("en"),
    HUNGARIAN("hu"),
    RUSSIAN("ru")
}

val DEFAULT_PINNED_AUDIO_FX = listOf(
    "bass_boost",
    "earrape",
    "eight_d",
    "muffled",
    "reverb",
    "rain"
)

class PlayerPreferences {
    private val gson = Gson()
    private val queueFile = File(AppDirs.dataDir, "queue_cache.json")

    companion object {
        val DEFAULT_PINNED_AUDIO_FX = listOf(
            "bass_boost",
            "earrape",
            "eight_d",
            "muffled",
            "reverb",
            "rain"
        )
        const val KEY_LISTENING_STATS_ENABLED = "listening_stats_enabled"
        private const val KEY_PINNED_AUDIO_FX = "pinned_audio_fx_list_v1"
        private const val KEY_SOUNDCLOUD_HISTORY_SYNC = "soundcloud_history_sync_enabled"
        private const val KEY_TRACK_JSON = "last_track_json"
        private const val KEY_POSITION = "last_position"
        private const val KEY_EFFECTS = "audio_effects"
        private const val KEY_CONTEXT_JSON = "last_context_json"
        private const val KEY_SHUFFLE_MODE = "shuffle_mode_enabled"
        private const val KEY_REPEAT_MODE = "repeat_mode_state"
        private const val KEY_DOWNLOAD_DIR = "download_directory_uri"
        private const val KEY_AUTOPLAY_STATION = "autoplay_station_enabled"
        private const val KEY_CONTINUOUS_PLAYBACK = "continuous_playback_enabled"
        private const val KEY_AUDIO_QUALITY = "audio_quality_pref"
        private const val KEY_PERSISTENT_QUEUE = "persistent_queue_enabled"
        private const val KEY_SAVE_POSITION = "save_position_enabled"
        private const val KEY_START_DESTINATION = "start_destination_pref"
        private const val KEY_DYNAMIC_THEME = "dynamic_theme_enabled"
        private const val KEY_PLAYER_VOLUME = "player_volume"
        private const val KEY_VERTICAL_VOLUME_SLIDER = "vertical_volume_slider"
        private const val KEY_APP_ICON_VARIANT = "app_icon_variant"
        private const val KEY_THEME_MODE = "app_theme_mode"
        private const val KEY_PURE_BLACK = "pure_black_enabled"
        private const val KEY_LOCAL_MEDIA_ENABLED = "local_media_enabled"
        private const val KEY_LOCAL_MEDIA_URIS_SET = "local_media_uris_set_v2"
        private const val KEY_PLAYER_BAR_BUTTONS = "player_bar_buttons"

        const val PLAYER_BAR_BUTTON_LIKE = "like"
        const val PLAYER_BAR_BUTTON_PANEL = "panel"
        const val PLAYER_BAR_BUTTON_QUEUE = "queue"

        val PLAYER_BAR_BUTTONS_DEFAULT =
            setOf(PLAYER_BAR_BUTTON_LIKE, PLAYER_BAR_BUTTON_PANEL, PLAYER_BAR_BUTTON_QUEUE)

        const val SEEK_WHEEL_SECONDS_MIN = 1f
        const val SEEK_WHEEL_SECONDS_MAX = 60f

        const val LYRICS_WHEEL_LINES_MIN = 1f
        const val LYRICS_WHEEL_LINES_MAX = 12f

        const val MENU_TRACK = "track"
        const val MENU_PLAYLIST = "playlist"

        private const val KEY_INFO_PANEL_HALF = "info_panel_half"
        private const val KEY_INFO_PANEL_LAST_LYRICS = "info_panel_last_lyrics"

        private const val KEY_PANEL_TABS_HIDDEN = "now_playing_tabs_hidden"

        const val PANEL_TAB_TRACK = "track"
        const val PANEL_TAB_QUEUE = "queue"
        const val PANEL_TAB_LYRICS = "lyrics"
        const val PANEL_TAB_EFFECTS = "effects"

        private const val KEY_LIBRARY_TILES_HIDDEN = "library_tiles_hidden"

        const val LIBRARY_TILE_LIKES = "likes"
        const val LIBRARY_TILE_DOWNLOADS = "downloads"
        const val LIBRARY_TILE_LOCAL = "local"

        /** In the order they appear in the library, which is the order the settings list them in. */
        val LIBRARY_TILES =
            listOf(LIBRARY_TILE_LIKES, LIBRARY_TILE_DOWNLOADS, LIBRARY_TILE_LOCAL)

        private const val KEY_SIDEBAR_NAV_HIDDEN = "sidebar_nav_hidden"

        const val SIDEBAR_NAV_FEED = "feed"
        const val SIDEBAR_NAV_EXPLORE = "explore"
        const val SIDEBAR_NAV_RECOGNITION = "recognition"

        /**
         * Device sync, in the sidebar rather than only at the bottom of the settings page.
         *
         * Shown by default — a new key is absent from the hidden set — because the thing sync needs most is
         * to be found once. It can be switched off like the others for anyone who has paired and is done
         * thinking about it.
         */
        const val SIDEBAR_NAV_SYNC = "sync"

        /** Home is deliberately absent: see [getHiddenSidebarNav]. */
        val SIDEBAR_NAV_ITEMS =
            listOf(SIDEBAR_NAV_FEED, SIDEBAR_NAV_EXPLORE, SIDEBAR_NAV_RECOGNITION, SIDEBAR_NAV_SYNC)

        private const val KEY_LIBRARY_BUTTONS_HIDDEN = "library_buttons_hidden"

        const val LIBRARY_BUTTON_CREATE = "create"
        const val LIBRARY_BUTTON_HISTORY = "history"

        val LIBRARY_BUTTONS = listOf(LIBRARY_BUTTON_CREATE, LIBRARY_BUTTON_HISTORY)

        private const val KEY_LYRICS_PREFER_LOCAL = "lyrics_prefer_local"
        private const val KEY_LYRICS_ALIGNMENT = "lyrics_alignment"
        private const val KEY_LYRICS_DISPLAY_STYLE = "lyrics_display_style"
        private const val KEY_LYRICS_FONT_SIZE = "lyrics_font_size"
        private const val KEY_LYRICS_APPLE_EFFECT = "lyrics_apple_effect"
        private const val KEY_LYRICS_WORD_SYNC = "lyrics_word_sync"
        private const val KEY_LYRICS_TRANSLATION_ENABLED = "lyrics_translation_enabled"
        private const val KEY_LYRICS_TRANSLATION_LANG = "lyrics_translation_lang"
        private const val KEY_APP_LANGUAGE = "app_language_code"
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
        private const val KEY_AUDIO_DEVICE = "audio_output_device"

        private const val KEY_DISCORD_ASSET_LOGO = "discord_asset_logo"
        private const val KEY_DISCORD_STATUS_DISPLAY = "discord_status_display"
        private const val KEY_CUSTOM_FONT_ENABLED = "custom_font_enabled"
        private const val KEY_UI_SCALE = "ui_scale_preference"
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
        private const val KEY_HAS_COMPLETED_SETUP = "has_completed_setup"

        private const val KEY_PROXY_ENABLED = "proxy_enabled"
        private const val KEY_PROXY_TYPE = "proxy_type"
        private const val KEY_PROXY_HOST = "proxy_host"
        private const val KEY_PROXY_PORT = "proxy_port"
        private const val KEY_PROXY_AUTH_ENABLED = "proxy_auth_enabled"
        private const val KEY_PROXY_USERNAME = "proxy_username"
        private const val KEY_PROXY_PASSWORD = "proxy_password"
        private const val KEY_PROXY_PROFILES = "proxy_profiles_json"
        private const val KEY_SELECTED_PROXY_PROFILE_ID = "selected_proxy_profile_id"

        private val queueLock = Any()
    }

    fun getSyncLikesEnabled(): Boolean = Prefs.getBoolean(KEY_SYNC_LIKES, true)
    fun setSyncLikesEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_SYNC_LIKES, enabled)

    fun getHasCompletedSetup(): Boolean = Prefs.getBoolean(KEY_HAS_COMPLETED_SETUP, false)
    fun setHasCompletedSetup(completed: Boolean) = Prefs.putBoolean(KEY_HAS_COMPLETED_SETUP, completed)

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

    fun getPreciseSpeedEnabled(): Boolean = Prefs.getBoolean(KEY_PRECISE_SPEED, false)
    fun setPreciseSpeedEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_PRECISE_SPEED, enabled)

    fun getUiScale(): Float = Prefs.getFloat(KEY_UI_SCALE, 1.0f).coerceIn(0.7f, 1.3f)
    fun setUiScale(scale: Float) = Prefs.putFloat(KEY_UI_SCALE, scale.coerceIn(0.7f, 1.3f))
    fun uiScaleFlow(): Flow<Float> = Prefs.floatFlow(KEY_UI_SCALE, 1.0f).map { it?.coerceIn(0.7f, 1.3f) ?: 1.0f }

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

    fun getAudioDevice(): String = Prefs.getString(KEY_AUDIO_DEVICE, "") ?: ""
    fun setAudioDevice(deviceName: String) = Prefs.putString(KEY_AUDIO_DEVICE, deviceName)

    fun getLyricsAlignment(): LyricsAlignment {
        val name = Prefs.getString(KEY_LYRICS_ALIGNMENT, LyricsAlignment.LEFT.name)
        return try { LyricsAlignment.valueOf(name!!) } catch (_: Exception) { LyricsAlignment.LEFT }
    }
    fun setLyricsAlignment(align: LyricsAlignment) = Prefs.putString(KEY_LYRICS_ALIGNMENT, align.name)

    fun getLyricsDisplayStyle(): LyricsDisplayStyle {
        val name = Prefs.getString(KEY_LYRICS_DISPLAY_STYLE, LyricsDisplayStyle.STANDARD.name)
        return LyricsDisplayStyle.entries.find { it.name == name } ?: LyricsDisplayStyle.STANDARD
    }

    fun setLyricsDisplayStyle(style: LyricsDisplayStyle) =
        Prefs.putString(KEY_LYRICS_DISPLAY_STYLE, style.name)



    fun getLyricsFontSize(): Float = Prefs.getFloat(KEY_LYRICS_FONT_SIZE, 42f)
    fun setLyricsFontSize(size: Float) = Prefs.putFloat(KEY_LYRICS_FONT_SIZE, size)
    fun getLocalMediaEnabled(): Boolean = Prefs.getBoolean(KEY_LOCAL_MEDIA_ENABLED, false)
    fun setLocalMediaEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_LOCAL_MEDIA_ENABLED, enabled)
    fun getLocalMediaUris(): Set<String> = Prefs.getStringSet(KEY_LOCAL_MEDIA_URIS_SET, emptySet())
    fun addLocalMediaUri(uri: String) { val c = getLocalMediaUris().toMutableSet(); c.add(uri); Prefs.putStringSet(KEY_LOCAL_MEDIA_URIS_SET, c) }
    fun removeLocalMediaUri(uri: String) { val c = getLocalMediaUris().toMutableSet(); c.remove(uri); Prefs.putStringSet(KEY_LOCAL_MEDIA_URIS_SET, c) }
    fun getStartDestination(): StartDestination { val n = Prefs.getString(KEY_START_DESTINATION, StartDestination.HOME.name); return try { StartDestination.valueOf(n!!) } catch (_: Exception) { StartDestination.HOME } }
    fun setStartDestination(dest: StartDestination) = Prefs.putString(KEY_START_DESTINATION, dest.name)

    fun getInfoPanelHalf(): InfoPanelHalf {
        val name = Prefs.getString(KEY_INFO_PANEL_HALF, InfoPanelHalf.REMEMBER.name)
        return try { InfoPanelHalf.valueOf(name!!) } catch (_: Exception) { InfoPanelHalf.REMEMBER }
    }

    fun setInfoPanelHalf(half: InfoPanelHalf) = Prefs.putString(KEY_INFO_PANEL_HALF, half.name)

    /**
     * The last half picked by hand, for [InfoPanelHalf.REMEMBER].
     *
     * Deliberately not keyed on the track: someone who opened the panel for the lyrics wants the
     * lyrics on the next track too.
     */
    fun getInfoPanelLastLyrics(): Boolean = Prefs.getBoolean(KEY_INFO_PANEL_LAST_LYRICS, false)

    fun setInfoPanelLastLyrics(lyrics: Boolean) = Prefs.putBoolean(KEY_INFO_PANEL_LAST_LYRICS, lyrics)

    /** Resolves the setting and the remembered choice into the half to open on. */
    fun infoPanelOpensOnLyrics(): Boolean = when (getInfoPanelHalf()) {
        InfoPanelHalf.COMMENTS -> false
        InfoPanelHalf.LYRICS -> true
        InfoPanelHalf.REMEMBER -> getInfoPanelLastLyrics()
    }
    fun getDynamicTheme(): Boolean = Prefs.getBoolean(KEY_DYNAMIC_THEME, true)
    fun setDynamicTheme(enabled: Boolean) = Prefs.putBoolean(KEY_DYNAMIC_THEME, enabled)

    /** Also decides whether the fixed library tiles follow the palette. See [LIBRARY_TILES]. */
    fun dynamicThemeFlow(): Flow<Boolean> = Prefs.booleanFlow(KEY_DYNAMIC_THEME, true)

    // Persisted volume so the app reopens at the level used when it was closed.
    fun getSavedVolume(): Float = Prefs.getFloat(KEY_PLAYER_VOLUME, 1f)
    fun saveVolume(value: Float) = Prefs.putFloat(KEY_PLAYER_VOLUME, value.coerceIn(0f, 1f))

    fun getVerticalVolumeSlider(): Boolean = Prefs.getBoolean(KEY_VERTICAL_VOLUME_SLIDER, false)
    fun setVerticalVolumeSlider(enabled: Boolean) = Prefs.putBoolean(KEY_VERTICAL_VOLUME_SLIDER, enabled)

    // Alternate app icon switcher (mirrors the Android activity-alias feature).
    fun getAppIconVariant(): String = Prefs.getString(KEY_APP_ICON_VARIANT, "default") ?: "default"
    fun setAppIconVariant(key: String) = Prefs.putString(KEY_APP_ICON_VARIANT, key)
    fun appIconVariantFlow(): Flow<String> =
        Prefs.stringFlow(KEY_APP_ICON_VARIANT, "default").map { it ?: "default" }
    fun getThemeMode(): AppThemeMode { val n = Prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name); return try { AppThemeMode.valueOf(n!!) } catch (_: Exception) { AppThemeMode.SYSTEM } }
    fun setThemeMode(mode: AppThemeMode) = Prefs.putString(KEY_THEME_MODE, mode.name)
    fun getPureBlack(): Boolean = Prefs.getBoolean(KEY_PURE_BLACK, false)
    fun setPureBlack(enabled: Boolean) = Prefs.putBoolean(KEY_PURE_BLACK, enabled)
    fun getAutoplayEnabled(): Boolean = Prefs.getBoolean(KEY_AUTOPLAY_STATION, true)
    fun setAutoplayEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_AUTOPLAY_STATION, enabled)
    fun getContinuousPlaybackEnabled(): Boolean = Prefs.getBoolean(KEY_CONTINUOUS_PLAYBACK, true)
    fun setContinuousPlaybackEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_CONTINUOUS_PLAYBACK, enabled)
    fun getListeningStatsEnabled(): Boolean = Prefs.getBoolean(KEY_LISTENING_STATS_ENABLED, true)
    fun setListeningStatsEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_LISTENING_STATS_ENABLED, enabled)
    fun getAudioQuality(): String = Prefs.getString(KEY_AUDIO_QUALITY, "HIGH") ?: "HIGH"
    fun setAudioQuality(quality: String) = Prefs.putString(KEY_AUDIO_QUALITY, quality)
    fun getPersistentQueueEnabled(): Boolean = Prefs.getBoolean(KEY_PERSISTENT_QUEUE, true)
    fun setPersistentQueueEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_PERSISTENT_QUEUE, enabled)
    fun getSavePositionEnabled(): Boolean = Prefs.getBoolean(KEY_SAVE_POSITION, true)
    fun setSavePositionEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_SAVE_POSITION, enabled)

    fun getRightPanelWidth(): Float = Prefs.getFloat("right_panel_width", RIGHT_PANEL_DEFAULT_WIDTH).coerceIn(RIGHT_PANEL_MIN_WIDTH, RIGHT_PANEL_MAX_WIDTH)
    fun setRightPanelWidth(width: Float) = Prefs.putFloat("right_panel_width", width.coerceIn(RIGHT_PANEL_MIN_WIDTH, RIGHT_PANEL_MAX_WIDTH))

    fun getKeyColor(): Int = Prefs.getInt(KEY_KEY_COLOR, 0)
    fun setKeyColor(color: Int) = Prefs.putInt(KEY_KEY_COLOR, color)

    fun getColorStyle(): String {
        val style = Prefs.getString(KEY_COLOR_STYLE, "System") ?: "System"
        val isWindowsOS = System.getProperty("os.name").lowercase().contains("win")
        if (!isWindowsOS && style.contains("windows", ignoreCase = true)) {
            return "Vibrant"
        }
        return style
    }
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
    fun stopOnTaskClearFlow(): Flow<Boolean> = Prefs.booleanFlow(KEY_STOP_ON_TASK_CLEAR, true)

    fun getLyricsAppleEffectEnabled(): Boolean = Prefs.getBoolean(KEY_LYRICS_APPLE_EFFECT, true)
    fun setLyricsAppleEffectEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_LYRICS_APPLE_EFFECT, enabled)

    fun getLyricsWordSyncEnabled(): Boolean = Prefs.getBoolean(KEY_LYRICS_WORD_SYNC, true)
    fun setLyricsWordSyncEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_LYRICS_WORD_SYNC, enabled)

    fun getLyricsTranslationEnabled(): Boolean = Prefs.getBoolean(KEY_LYRICS_TRANSLATION_ENABLED, false)
    fun setLyricsTranslationEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_LYRICS_TRANSLATION_ENABLED, enabled)

    fun getLyricsTranslationLang(): String {
        val code = Prefs.getString(KEY_LYRICS_TRANSLATION_LANG, null)
        if (code != null) return code
        return java.util.Locale.getDefault().language.take(2).lowercase()
    }
    fun setLyricsTranslationLang(lang: String) = Prefs.putString(KEY_LYRICS_TRANSLATION_LANG, lang)

    /**
     * Which optional buttons the player bar shows on the right, and the heart on the left.
     *
     * Rather than removing the queue button for everyone — the panel behind the settings button has
     * a queue tab, so it is genuinely redundant for some people and one click for others — the row
     * is configurable and ships as it was (issue #33). The lyrics button is not in here: it already
     * had its own switch in the lyrics settings.
     */
    fun getPlayerBarButtons(): Set<String> {
        val raw = Prefs.getString(KEY_PLAYER_BAR_BUTTONS, null) ?: return PLAYER_BAR_BUTTONS_DEFAULT
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun setPlayerBarButtons(buttons: Set<String>) =
        Prefs.putString(KEY_PLAYER_BAR_BUTTONS, buttons.joinToString(","))

    /**
     * The order of the tiles in an options menu, and which of them are hidden (issue #33).
     *
     * "As in gboard, that you can move the tiles: if you need one thing in the first place, then just
     * move it. You can also add hiding unnecessary buttons."
     *
     * Only the tiles actually moved are stored, in order; anything absent keeps its place in the
     * built-in sequence, after the ones that were arranged. That is what lets a tile added in a later
     * version appear at all instead of falling off the end of a saved list — and it means the stored
     * value stays empty for everyone who never touches this.
     *
     * @param menu [MENU_TRACK] or [MENU_PLAYLIST]. The two menus keep separate arrangements: they
     *   share several tiles but not the reasons anyone would reach for them.
     */
    fun getMenuTileOrder(menu: String): List<String> {
        val raw = Prefs.getString(keyMenuTileOrder(menu), null) ?: return emptyList()
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun setMenuTileOrder(menu: String, order: List<String>) =
        Prefs.putString(keyMenuTileOrder(menu), order.joinToString(","))

    fun getHiddenMenuTiles(menu: String): Set<String> {
        val raw = Prefs.getString(keyHiddenMenuTiles(menu), null) ?: return emptySet()
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun setHiddenMenuTiles(menu: String, tiles: Set<String>) =
        Prefs.putString(keyHiddenMenuTiles(menu), tiles.joinToString(","))

    /** Back to the built-in order and nothing hidden. */
    fun resetMenuTiles(menu: String) {
        Prefs.putString(keyMenuTileOrder(menu), null)
        Prefs.putString(keyHiddenMenuTiles(menu), null)
    }

    private fun keyMenuTileOrder(menu: String) = "menu_tile_order_$menu"
    private fun keyHiddenMenuTiles(menu: String) = "menu_tiles_hidden_$menu"

    /**
     * Which tabs the Now Playing panel offers (issue #33).
     *
     * The panel had four and no way to drop one, so four labels fought over the width of a side
     * panel and all four came out truncated — which is also why nobody could tell what they were.
     * Someone who never touches the effects can take that tab out and give the room back to the
     * three they use.
     *
     * Hidden ones are stored rather than visible ones, so a tab added later shows up by default
     * instead of staying silently hidden for everyone who had already saved a selection. The Track
     * tab can be hidden too — the panel simply falls back to the first one still standing, and it
     * refuses to hide the last.
     */
    fun getHiddenPanelTabs(): Set<String> {
        val raw = Prefs.getString(KEY_PANEL_TABS_HIDDEN, null) ?: return emptySet()
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun setHiddenPanelTabs(tabs: Set<String>) =
        Prefs.putString(KEY_PANEL_TABS_HIDDEN, tabs.joinToString(","))

    fun hiddenPanelTabsFlow(): Flow<Set<String>> =
        Prefs.stringFlow(KEY_PANEL_TABS_HIDDEN, null).map { raw ->
            raw?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
        }

    /**
     * The three fixed tiles at the top of the library — Liked, Downloads, Local files.
     *
     * They were the one part of the library nobody could touch: always present, always their own
     * purple, green and blue, always the icon we picked. Someone who never downloads anything had a
     * tile they could not remove, and the three of them were the only thing on the page that
     * ignored the app's own colours (issue #33).
     *
     * Hidden ones are stored rather than visible ones, so a tile added later shows up by default
     * instead of silently staying hidden for everyone who had already saved a selection.
     */
    fun getHiddenLibraryTiles(): Set<String> {
        val raw = Prefs.getString(KEY_LIBRARY_TILES_HIDDEN, null) ?: return emptySet()
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun setHiddenLibraryTiles(tiles: Set<String>) =
        Prefs.putString(KEY_LIBRARY_TILES_HIDDEN, tiles.joinToString(","))

    fun hiddenLibraryTilesFlow(): Flow<Set<String>> =
        Prefs.stringFlow(KEY_LIBRARY_TILES_HIDDEN, null).map { raw ->
            raw?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
        }

    /** Path to the image standing in for a tile's built-in icon, or null for the built-in one. */
    fun getLibraryTileIcon(tile: String): String? =
        Prefs.getString(keyLibraryTileIcon(tile), null)?.takeIf { it.isNotBlank() }

    fun setLibraryTileIcon(tile: String, path: String?) =
        Prefs.putString(keyLibraryTileIcon(tile), path)

    fun libraryTileIconFlow(tile: String): Flow<String?> =
        Prefs.stringFlow(keyLibraryTileIcon(tile), null).map { it?.takeIf { p -> p.isNotBlank() } }

    private fun keyLibraryTileIcon(tile: String) = "library_tile_icon_$tile"

    /**
     * Which of the sidebar's navigation rows are hidden.
     *
     * Home is not in here and cannot be hidden: it is where the app starts and where every "go
     * back to the beginning" ends up, so a sidebar without it has no anchor. The other three are
     * whole sections of the app somebody may simply never open (issue #33).
     */
    fun getHiddenSidebarNav(): Set<String> {
        val raw = Prefs.getString(KEY_SIDEBAR_NAV_HIDDEN, null) ?: return emptySet()
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun setHiddenSidebarNav(items: Set<String>) =
        Prefs.putString(KEY_SIDEBAR_NAV_HIDDEN, items.joinToString(","))

    fun hiddenSidebarNavFlow(): Flow<Set<String>> =
        Prefs.stringFlow(KEY_SIDEBAR_NAV_HIDDEN, null).map { raw ->
            raw?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
        }

    /**
     * Which of the library header's own buttons are hidden — creating, and the listening history.
     *
     * Both were permanent fixtures next to a search field, and neither is something everybody uses
     * (issue #33). The import button stays: it is the only way into that screen.
     */
    fun getHiddenLibraryButtons(): Set<String> {
        val raw = Prefs.getString(KEY_LIBRARY_BUTTONS_HIDDEN, null) ?: return emptySet()
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun setHiddenLibraryButtons(buttons: Set<String>) =
        Prefs.putString(KEY_LIBRARY_BUTTONS_HIDDEN, buttons.joinToString(","))

    fun hiddenLibraryButtonsFlow(): Flow<Set<String>> =
        Prefs.stringFlow(KEY_LIBRARY_BUTTONS_HIDDEN, null).map { raw ->
            raw?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
        }


    /**
     * Whether the Windows title bar is painted in the app's colours rather than left to the system.
     * On by default — that was the point of the request — but reversible, because some people want
     * the stock bar (issue #33). Has no effect off Windows.
     */
    fun getThemedTitleBar(): Boolean = Prefs.getBoolean("themed_title_bar", true)
    fun setThemedTitleBar(enabled: Boolean) = Prefs.putBoolean("themed_title_bar", enabled)

    /**
     * Auto-scroll for lyrics with no timings, and how fast. Off by default: unsynced lyrics are
     * the case where the reader sets their own pace, so scrolling has to be asked for (issue #33).
     */
    fun getLyricsPlainAutoScroll(): Boolean = Prefs.getBoolean("lyrics_plain_autoscroll", false)
    fun setLyricsPlainAutoScroll(enabled: Boolean) = Prefs.putBoolean("lyrics_plain_autoscroll", enabled)

    /** Multiplier on the base auto-scroll rate, clamped to the range the slider offers. */
    fun getLyricsPlainAutoScrollSpeed(): Float =
        Prefs.getFloat("lyrics_plain_autoscroll_speed", 1.5f).coerceIn(0.25f, 4f)

    fun setLyricsPlainAutoScrollSpeed(speed: Float) =
        Prefs.putFloat("lyrics_plain_autoscroll_speed", speed.coerceIn(0.25f, 4f))

    /**
     * How far one notch of the mouse wheel moves the lyrics, in lines (issue #33).
     *
     * "I would also add an adjustment for how much the mouse wheel scrolling adds." Expressed in
     * lines rather than pixels so it means the same thing at a 42 sp full screen and in a side panel
     * drawing the same text a third of the size — the unit the auto-scroll rate already uses.
     *
     * Three is roughly what a desktop wheel notch does elsewhere in the app, so the default changes
     * nothing for anyone who does not go looking for it.
     */
    /**
     * How far one notch of the wheel moves the playhead, in seconds (issue #33).
     *
     * "If you hover over the slider showing how long the track is, you can use the mouse wheel to
     * rewind and fast-forward the track." Five seconds is the step every player uses for its skip
     * buttons, so it is the one that will feel like nothing new.
     */
    fun getSeekWheelSeconds(): Float =
        Prefs.getFloat("seek_wheel_seconds", 5f).coerceIn(SEEK_WHEEL_SECONDS_MIN, SEEK_WHEEL_SECONDS_MAX)

    fun setSeekWheelSeconds(seconds: Float) = Prefs.putFloat(
        "seek_wheel_seconds",
        seconds.coerceIn(SEEK_WHEEL_SECONDS_MIN, SEEK_WHEEL_SECONDS_MAX),
    )

    fun getLyricsWheelLines(): Float =
        Prefs.getFloat("lyrics_wheel_lines", 3f).coerceIn(LYRICS_WHEEL_LINES_MIN, LYRICS_WHEEL_LINES_MAX)

    fun setLyricsWheelLines(lines: Float) = Prefs.putFloat(
        "lyrics_wheel_lines",
        lines.coerceIn(LYRICS_WHEEL_LINES_MIN, LYRICS_WHEEL_LINES_MAX),
    )

    fun getLyricsRomanizationEnabled(): Boolean = Prefs.getBoolean("lyrics_romanization_enabled", false)
    fun setLyricsRomanizationEnabled(enabled: Boolean) = Prefs.putBoolean("lyrics_romanization_enabled", enabled)

    fun getLyricsProvider(): com.alananasss.kittytune.ui.player.LyricsProvider {
        val name = Prefs.getString("lyrics_provider", com.alananasss.kittytune.ui.player.LyricsProvider.MAX_QUALITY.name)
        return try { com.alananasss.kittytune.ui.player.LyricsProvider.valueOf(name!!) } catch(_: Exception) { com.alananasss.kittytune.ui.player.LyricsProvider.MAX_QUALITY }
    }
    fun setLyricsProvider(provider: com.alananasss.kittytune.ui.player.LyricsProvider) = Prefs.putString("lyrics_provider", provider.name)

    fun savePlaybackState(track: Track?, position: Long, queue: List<Track>, context: PlaybackContext?, shuffleEnabled: Boolean, repeatMode: RepeatMode, saveQueue: Boolean = true) {
        if (!getPersistentQueueEnabled()) {
            Prefs.putBoolean(KEY_SHUFFLE_MODE, shuffleEnabled)
            Prefs.putString(KEY_REPEAT_MODE, repeatMode.name)
            Prefs.remove(KEY_TRACK_JSON)
            if (queueFile.exists()) queueFile.delete()
            Prefs.remove(KEY_POSITION)
            Prefs.remove(KEY_CONTEXT_JSON)
            return
        }

        if (saveQueue && queue.isNotEmpty()) {
            synchronized(queueLock) {
                try {
                    val tempFile = File(queueFile.parentFile, "queue_cache.tmp")
                    tempFile.writeText(gson.toJson(queue))
                    tempFile.renameTo(queueFile)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        track?.let { Prefs.putString(KEY_TRACK_JSON, gson.toJson(it)) }
        Prefs.putString(KEY_CONTEXT_JSON, gson.toJson(context))
        if (getSavePositionEnabled()) Prefs.putLong(KEY_POSITION, position) else Prefs.remove(KEY_POSITION)
        Prefs.putBoolean(KEY_SHUFFLE_MODE, shuffleEnabled)
        Prefs.putString(KEY_REPEAT_MODE, repeatMode.name)
    }

    fun savePosition(position: Long) {
        if (getSavePositionEnabled()) {
            Prefs.putLong(KEY_POSITION, position)
        }
    }

    fun saveEffects(state: AudioEffectsState) = Prefs.putString(KEY_EFFECTS, gson.toJson(state))
    fun saveDownloadLocation(uriString: String?) = if (uriString != null) Prefs.putString(KEY_DOWNLOAD_DIR, uriString) else Prefs.remove(KEY_DOWNLOAD_DIR)
    fun getDownloadLocation(): String? = Prefs.getString(KEY_DOWNLOAD_DIR, null)
    fun getLastTrack(): Track? { if (!getPersistentQueueEnabled()) return null; val json = Prefs.getString(KEY_TRACK_JSON, null) ?: return null; return try { gson.fromJson(json, Track::class.java) } catch (_: Exception) { null } }
    fun getLastPosition(): Long = if (getSavePositionEnabled()) Prefs.getLong(KEY_POSITION, 0L) else 0L
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
    fun getLastEffects(): AudioEffectsState { 
        val json = Prefs.getString(KEY_EFFECTS, null) ?: return AudioEffectsState()
        return try { 
            val state = gson.fromJson(json, AudioEffectsState::class.java)
            state.copy(normalizationLevel = state.normalizationLevel ?: com.alananasss.kittytune.ui.player.NormalizationLevel.NORMAL)
        } catch (_: Exception) { AudioEffectsState() } 
    }

    fun getPinnedAudioFx(): List<String> {
        val json = Prefs.getString(KEY_PINNED_AUDIO_FX, null) ?: return DEFAULT_PINNED_AUDIO_FX
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            val list: List<String>? = gson.fromJson(json, type)
            if (list.isNullOrEmpty()) DEFAULT_PINNED_AUDIO_FX else list
        } catch (_: Exception) {
            DEFAULT_PINNED_AUDIO_FX
        }
    }

    fun setPinnedAudioFx(fxIds: List<String>) {
        Prefs.putString(KEY_PINNED_AUDIO_FX, gson.toJson(fxIds))
    }

    fun getSoundCloudHistorySyncEnabled(): Boolean = Prefs.getBoolean(KEY_SOUNDCLOUD_HISTORY_SYNC, true)

    fun setSoundCloudHistorySyncEnabled(enabled: Boolean) {
        Prefs.putBoolean(KEY_SOUNDCLOUD_HISTORY_SYNC, enabled)
    }

    fun getCachedUserUploads(): List<Track> {
        val json = Prefs.getString("cached_user_uploads", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Track>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun setCachedUserUploads(tracks: List<Track>) {
        try {
            val json = gson.toJson(tracks)
            Prefs.putString("cached_user_uploads", json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getProxyEnabled(): Boolean = Prefs.getBoolean(KEY_PROXY_ENABLED, false)
    fun setProxyEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_PROXY_ENABLED, enabled)

    fun getProxyType(): String = Prefs.getString(KEY_PROXY_TYPE, "HTTP") ?: "HTTP"
    fun setProxyType(type: String) = Prefs.putString(KEY_PROXY_TYPE, type)

    fun getProxyHost(): String = Prefs.getString(KEY_PROXY_HOST, "") ?: ""
    fun setProxyHost(host: String) = Prefs.putString(KEY_PROXY_HOST, host.trim())

    fun getProxyPort(): Int = Prefs.getInt(KEY_PROXY_PORT, 8080)
    fun setProxyPort(port: Int) = Prefs.putInt(KEY_PROXY_PORT, port)

    fun getProxyAuthEnabled(): Boolean = Prefs.getBoolean(KEY_PROXY_AUTH_ENABLED, false)
    fun setProxyAuthEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_PROXY_AUTH_ENABLED, enabled)

    fun getProxyUsername(): String = Prefs.getString(KEY_PROXY_USERNAME, "") ?: ""
    fun setProxyUsername(username: String) = Prefs.putString(KEY_PROXY_USERNAME, username.trim())

    fun getProxyPassword(): String = Prefs.getString(KEY_PROXY_PASSWORD, "") ?: ""
    fun setProxyPassword(password: String) = Prefs.putString(KEY_PROXY_PASSWORD, password)

    // Proxy Profiles (Multi-proxy list)
    fun getSavedProxyProfiles(): List<com.alananasss.kittytune.data.network.ProxyProfile> {
        val json = Prefs.getString(KEY_PROXY_PROFILES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<com.alananasss.kittytune.data.network.ProxyProfile>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveProxyProfiles(profiles: List<com.alananasss.kittytune.data.network.ProxyProfile>) {
        Prefs.putString(KEY_PROXY_PROFILES, gson.toJson(profiles))
    }

    fun addOrUpdateProxyProfile(profile: com.alananasss.kittytune.data.network.ProxyProfile) {
        val list = getSavedProxyProfiles().toMutableList()
        val index = list.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            list[index] = profile
        } else {
            list.add(profile)
        }
        saveProxyProfiles(list)
    }

    fun deleteProxyProfile(profileId: String) {
        val list = getSavedProxyProfiles().filterNot { it.id == profileId }
        saveProxyProfiles(list)
        if (getSelectedProxyProfileId() == profileId) {
            setSelectedProxyProfileId(null)
        }
    }

    fun getSelectedProxyProfileId(): String? = Prefs.getString(KEY_SELECTED_PROXY_PROFILE_ID, null)

    fun setSelectedProxyProfileId(id: String?) {
        if (id != null) Prefs.putString(KEY_SELECTED_PROXY_PROFILE_ID, id)
        else Prefs.remove(KEY_SELECTED_PROXY_PROFILE_ID)
    }

    fun getLikedSpotifyArtists(): Set<String> = Prefs.getStringSet("liked_spotify_artists", emptySet())

    fun isSpotifyArtistLiked(artistId: String): Boolean = getLikedSpotifyArtists().contains(artistId)

    fun toggleLikeSpotifyArtist(artistId: String): Boolean {
        val current = getLikedSpotifyArtists().toMutableSet()
        val isNowLiked = if (current.contains(artistId)) {
            current.remove(artistId)
            false
        } else {
            current.add(artistId)
            true
        }
        Prefs.putStringSet("liked_spotify_artists", current)
        return isNowLiked
    }

    fun saveSpotifyArtistMapping(numericId: Long, spotifyId: String) {
        Prefs.putString("spotify_artist_mapping_$numericId", spotifyId)
    }

    fun getSpotifyArtistIdForStableId(numericId: Long): String? = Prefs.getString("spotify_artist_mapping_$numericId", null)

    fun removeSpotifyArtistMapping(numericId: Long) {
        Prefs.remove("spotify_artist_mapping_$numericId")
    }
}

const val RIGHT_PANEL_MIN_WIDTH = 280f
const val RIGHT_PANEL_MAX_WIDTH = 440f
const val RIGHT_PANEL_DEFAULT_WIDTH = 340f
