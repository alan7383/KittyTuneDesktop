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
import com.alananasss.kittytune.data.lyrics.providers.PreferredLyricsProvider
import com.alananasss.kittytune.data.lyrics.providers.DefaultLyricsProviderOrder
import com.alananasss.kittytune.data.lyrics.providers.deserializeLyricsProviderOrder
import com.alananasss.kittytune.data.lyrics.providers.serializeLyricsProviderOrder
import com.alananasss.kittytune.data.lyrics.clients.PaxsenixClient


enum class AppThemeMode { SYSTEM, LIGHT, DARK }
enum class StartDestination { HOME, LIBRARY }
enum class MiniPlayerStyle { STANDARD, ELONGATED }
enum class LyricsScreenContext { MAIN, FULLSCREEN, SIDEBAR }

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
enum class LyricsUiStyle { ENHANCED, CLASSIC }
enum class LyricsFont { APPLE, APP_DEFAULT }
enum class PlayerSliderStyle { BAR, WAVY, SLIM, SQUIGGLY }

/**
 * How much the lyrics views set the line being sung apart from the rest (issue #33).
 *
 * [STANDARD] is what the app has always done: the current line is brighter. [SCALE] also grows
 * it, and [FOCUS] pushes the rest well back instead — the two treatments asked for, each useful
 * on a different kind of lyric sheet.
 */
enum class LyricsDisplayStyle { STANDARD, SCALE, FOCUS, SCALE_FOCUS }

enum class DiscordStatusDisplay { ACTIVITY, SOUNDCLOUD, ARTIST, SONG }
/**
 * What the full-screen player draws behind the words.
 *
 * [APPLE_MUSIC] was called `ORBS` and drew soft radial lights in the cover's shades; it is now the album
 * art itself, stacked and twisted the way Apple Music's own background is — the same effect, not an
 * impression of it, which is why it is named after it. See the legacy branch in
 * [PlayerPreferences.getFullPlayerBgStyle] for what happens to a saved `ORBS`.
 */
enum class FullPlayerBgStyle { APPLE_MUSIC, BLUR, GRADIENT, PURE_BLACK }

enum class PlayerBarStyle { DEFAULT, ROUNDED, FLOATING }

/**
 * The floating bar's shape: its corner (0 is square, 40 a pill), how much of the window's width it takes,
 * how far above the bottom edge it floats, and whether the page shows through it.
 */
data class FloatingBarLook(
    val cornerDp: Int,
    val widthPercent: Int,
    val marginDp: Int,
    val isTranslucent: Boolean,
) {
    companion object {
        val DEFAULT = FloatingBarLook(cornerDp = 40, widthPercent = 100, marginDp = 16, isTranslucent = true)
    }
}

enum class AppLanguage(val code: String) {
    SYSTEM("system"),
    FRENCH("fr"),
    ENGLISH("en"),
    GERMAN("de"),
    HUNGARIAN("hu"),
    RUSSIAN("ru"),
    VIETNAMESE("vi")
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
        private const val KEY_QUEUE_PRESERVE_UPCOMING_ON_JUMP = "queue_preserve_upcoming_on_jump"
        private const val KEY_MIX_DISLIKED_TRACK_IDS = "mix_disliked_track_ids"
        private const val KEY_MIX_PRIORITIZE_TRUSTED = "mix_prioritize_trusted"
        private const val KEY_MINI_PLAYER_ENABLED = "mini_player_enabled"
        private const val KEY_MINI_PLAYER_X = "mini_player_pos_x"
        private const val KEY_MINI_PLAYER_Y = "mini_player_pos_y"
        private const val KEY_MINI_PLAYER_WIDTH = "mini_player_width"
        private const val KEY_MINI_PLAYER_HEIGHT = "mini_player_height"
        const val MINI_PLAYER_MIN_WIDTH = 340
        const val MINI_PLAYER_MAX_WIDTH = 1000
        const val MINI_PLAYER_MIN_HEIGHT = 68
        const val MINI_PLAYER_MAX_HEIGHT = 120
        private const val KEY_MINI_PLAYER_SHOW_COVER = "mini_player_show_cover"
        private const val KEY_MINI_PLAYER_SHOW_PLAYBACK_CONTROLS = "mini_player_show_playback_controls"
        private const val KEY_MINI_PLAYER_SHOW_ADDITIONAL_CONTROLS = "mini_player_show_additional_controls"
        private const val KEY_MINI_PLAYER_CONTROLS_ON_HOVER = "mini_player_controls_on_hover"
        private const val KEY_MINI_PLAYER_HOVER_EFFECT = "mini_player_hover_effect"
        private const val KEY_MINI_PLAYER_HOVER_ILLUMINATION = "mini_player_hover_illumination"
        private const val KEY_MINI_PLAYER_SHOW_PROGRESS = "mini_player_show_progress"
        private const val KEY_MINI_PLAYER_STYLE = "mini_player_style"
        const val DEFAULT_MINI_PLAYER_STYLE = "STANDARD"
        private const val KEY_MINI_PLAYER_TRANSPARENT_BG = "mini_player_transparent_bg"
        private const val KEY_MINI_PLAYER_ELONGATED_X = "mini_player_elongated_pos_x"
        private const val KEY_MINI_PLAYER_ELONGATED_Y = "mini_player_elongated_pos_y"
        private const val KEY_MINI_PLAYER_ELONGATED_WIDTH = "mini_player_elongated_width"
        private const val KEY_MINI_PLAYER_ELONGATED_HEIGHT = "mini_player_elongated_height"
        const val MINI_PLAYER_ELONGATED_MIN_WIDTH = 240
        const val MINI_PLAYER_ELONGATED_MAX_WIDTH = 1400
        const val MINI_PLAYER_ELONGATED_MIN_HEIGHT = 30
        const val MINI_PLAYER_ELONGATED_MAX_HEIGHT = 56
        const val MINI_PLAYER_ELONGATED_DEFAULT_HEIGHT = 38
        const val MINI_PLAYER_ELONGATED_DEFAULT_WIDTH = 500
        private const val KEY_FULL_PLAYER_BG_STYLE = "full_player_bg_style"

        /** What [FullPlayerBgStyle.APPLE_MUSIC] was written as before it drew the sleeve rather than orbs. */
        private const val LEGACY_ORBS_STYLE = "ORBS"
        private const val KEY_FULL_PLAYER_COVER_SCALE = "full_player_cover_scale"
        private const val KEY_FULL_PLAYER_LYRICS_ALIGN = "full_player_lyrics_align"
        private const val KEY_SAVE_POSITION = "save_position_enabled"
        private const val KEY_START_DESTINATION = "start_destination_pref"
        private const val KEY_DYNAMIC_THEME = "dynamic_theme_enabled"
        private const val KEY_PLAYER_VOLUME = "player_volume"
        private const val KEY_VOLUME_IS_PERCEPTUAL = "player_volume_perceptual"
        private const val KEY_VERTICAL_VOLUME_SLIDER = "vertical_volume_slider"
        private const val KEY_APP_ICON_VARIANT = "app_icon_variant"
        private const val KEY_THEME_MODE = "app_theme_mode"
        private const val KEY_PURE_BLACK = "pure_black_enabled"
        private const val KEY_LOCAL_MEDIA_ENABLED = "local_media_enabled"
        private const val KEY_LOCAL_MEDIA_URIS_SET = "local_media_uris_set_v2"
        private const val KEY_PLAYER_BAR_BUTTONS = "player_bar_buttons"
        private const val KEY_PLAYER_BAR_STYLE = "player_bar_style"

        const val PLAYER_BAR_BUTTON_LIKE = "like"
        const val PLAYER_BAR_BUTTON_PANEL = "panel"
        const val PLAYER_BAR_BUTTON_QUEUE = "queue"
        const val PLAYER_BAR_BUTTON_LYRICS = "lyrics"
        const val PLAYER_BAR_BUTTON_MINIPLAYER = "miniplayer"
        const val PLAYER_BAR_BUTTON_SHUFFLE = "shuffle"
        const val PLAYER_BAR_BUTTON_REPEAT = "repeat"

        val PLAYER_BAR_BUTTONS_DEFAULT =
            setOf(
                PLAYER_BAR_BUTTON_LIKE,
                PLAYER_BAR_BUTTON_PANEL,
                PLAYER_BAR_BUTTON_QUEUE,
                PLAYER_BAR_BUTTON_LYRICS,
                PLAYER_BAR_BUTTON_MINIPLAYER,
                PLAYER_BAR_BUTTON_SHUFFLE,
                PLAYER_BAR_BUTTON_REPEAT,
            )

        const val SEEK_WHEEL_SECONDS_MIN = 1f
        const val SEEK_WHEEL_SECONDS_MAX = 60f

        const val LYRICS_WHEEL_LINES_MIN = 1f
        const val LYRICS_WHEEL_LINES_MAX = 12f

        const val MENU_TRACK = "track"
        const val MENU_PLAYLIST = "playlist"

        private const val KEY_INFO_PANEL_HALF = "info_panel_half"
        private const val KEY_INFO_PANEL_LAST_LYRICS = "info_panel_last_lyrics"
        private const val KEY_RIGHT_PANEL_OPEN = "right_panel_open"

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

        const val SIDEBAR_NAV_HOME = "home"

        /** The rows shown by default. Home can be switched off too, as long as one row stays on. */
        val SIDEBAR_NAV_ITEMS =
            listOf(SIDEBAR_NAV_HOME, SIDEBAR_NAV_FEED, SIDEBAR_NAV_EXPLORE, SIDEBAR_NAV_RECOGNITION, SIDEBAR_NAV_SYNC)

        // Optional destinations the sidebar can carry; off until someone switches them on.
        const val SIDEBAR_NAV_STATS = "stats"
        const val SIDEBAR_NAV_HISTORY = "history"
        const val SIDEBAR_NAV_SETTINGS = "settings"
        const val SIDEBAR_NAV_SEARCH = "search"
        val SIDEBAR_NAV_EXTRAS = listOf(SIDEBAR_NAV_SEARCH, SIDEBAR_NAV_STATS, SIDEBAR_NAV_HISTORY, SIDEBAR_NAV_SETTINGS)

        private const val KEY_SIDEBAR_NAV_LAYOUT = "sidebar_nav_layout"

        private const val KEY_LIBRARY_BUTTONS_HIDDEN = "library_buttons_hidden"

        const val LIBRARY_BUTTON_CREATE = "create"
        const val LIBRARY_BUTTON_HISTORY = "history"

        val LIBRARY_BUTTONS = listOf(LIBRARY_BUTTON_CREATE, LIBRARY_BUTTON_HISTORY)

        private const val KEY_LYRICS_PREFER_LOCAL = "lyrics_prefer_local"
        private const val KEY_LYRICS_ALIGNMENT = "lyrics_alignment"
        private const val KEY_LYRICS_FULLSCREEN_ALIGNMENT = "lyrics_fullscreen_alignment"
        private const val KEY_LYRICS_SIDEBAR_ALIGNMENT = "lyrics_sidebar_alignment"
        private const val KEY_LYRICS_DISPLAY_STYLE = "lyrics_display_style"
        private const val KEY_LYRICS_FULLSCREEN_DISPLAY_STYLE = "lyrics_fullscreen_display_style"
        private const val KEY_LYRICS_SIDEBAR_DISPLAY_STYLE = "lyrics_sidebar_display_style"
        private const val KEY_LYRICS_FONT_SIZE = "lyrics_font_size"
        private const val KEY_LYRICS_FULLSCREEN_FONT_SIZE = "lyrics_fullscreen_font_size"
        private const val KEY_LYRICS_SIDEBAR_FONT_SIZE = "lyrics_sidebar_font_size"
        private const val KEY_LYRICS_APPLE_EFFECT = "lyrics_apple_effect"
        private const val KEY_LYRICS_DUET_VIEW = "lyrics_duet_view"
        private const val KEY_LYRICS_DUET_BLACKLIST = "lyrics_duet_blacklist"

        private const val KEY_LYRICS_WORD_SYNC = "lyrics_word_sync"
        private const val KEY_LYRICS_UI_STYLE = "lyrics_ui_style"
        private const val KEY_LYRICS_FULLSCREEN_UI_STYLE = "lyrics_fullscreen_ui_style"
        private const val KEY_LYRICS_SIDEBAR_UI_STYLE = "lyrics_sidebar_ui_style"
        private const val KEY_LYRICS_FONT = "lyrics_font"
        const val KEY_PLAYER_SLIDER_STYLE = "player_slider_style"
        private const val KEY_LYRICS_LINE_BLUR = "lyrics_line_blur_enabled"
        private const val KEY_LYRICS_FULLSCREEN_LINE_BLUR = "lyrics_fullscreen_line_blur_enabled"
        private const val KEY_LYRICS_SIDEBAR_LINE_BLUR = "lyrics_sidebar_line_blur_enabled"
        private const val KEY_LYRICS_LRC_BOUNCE_ENABLED = "lyrics_lrc_bounce_enabled"
        private const val KEY_LYRICS_BOUNCE_FACTOR = "lyrics_bounce_factor"
        private const val KEY_LYRICS_GLOW_FACTOR = "lyrics_glow_factor"
        private const val KEY_LYRICS_FILL_TRANSITION_WIDTH = "lyrics_fill_transition_width"
        private const val KEY_LYRICS_LINE_SPACING = "lyrics_line_spacing"
        private const val KEY_LYRICS_FULLSCREEN_LINE_SPACING = "lyrics_fullscreen_line_spacing"
        private const val KEY_LYRICS_SIDEBAR_LINE_SPACING = "lyrics_sidebar_line_spacing"
        private const val KEY_LYRICS_ACTIVE_SCALE = "lyrics_active_scale"
        private const val KEY_LYRICS_FULLSCREEN_ACTIVE_SCALE = "lyrics_fullscreen_active_scale"
        private const val KEY_LYRICS_SIDEBAR_ACTIVE_SCALE = "lyrics_sidebar_active_scale"
        private const val KEY_LYRICS_HORIZONTAL_MARGIN = "lyrics_horizontal_margin"
        private const val KEY_LYRICS_FULLSCREEN_HORIZONTAL_MARGIN = "lyrics_fullscreen_horizontal_margin"
        private const val KEY_LYRICS_SIDEBAR_HORIZONTAL_MARGIN = "lyrics_sidebar_horizontal_margin"
        private const val KEY_LYRICS_VERTICAL_OFFSET = "lyrics_vertical_offset"
        private const val KEY_LYRICS_FULLSCREEN_VERTICAL_OFFSET = "lyrics_fullscreen_vertical_offset"
        private const val KEY_LYRICS_SIDEBAR_VERTICAL_OFFSET = "lyrics_sidebar_vertical_offset"
        private const val KEY_LYRICS_TRANSLATION_ENABLED = "lyrics_translation_enabled"
        private const val KEY_LYRICS_TRANSLATION_LANG = "lyrics_translation_lang"
        private const val KEY_APP_LANGUAGE = "app_language_code"
        private const val KEY_PRECISE_SPEED = "precise_speed_enabled"
        private const val KEY_AUTO_UPDATE = "auto_update_enabled"
        private const val KEY_YOUTUBE_FALLBACK = "youtube_fallback_enabled"
        private const val KEY_DOWNLOAD_DRM_STREAMS = "download_drm_streams_enabled"
        private const val KEY_SHOW_LYRICS_BUTTON = "show_lyrics_button_enabled"
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
        private const val KEY_CROSSFADE_GAPLESS = "crossfade_gapless"
        const val KEY_AUTOMIX_ENABLED = "automix_enabled"
        const val KEY_AUTOMIX_DEBUG_OVERLAY = "automix_debug_overlay"
        private const val KEY_AUTOMIX_TEMPO_MATCH = "automix_tempo_match"
        private const val KEY_AUTOMIX_HARMONIC_MIX = "automix_harmonic_mix"
        private const val KEY_AUTOMIX_DYNAMIC_MIX_POINTS = "automix_dynamic_mix_points"
        private const val KEY_AUTOMIX_BASS_DUCKING = "automix_bass_ducking"
        private const val KEY_AUTOMIX_OVERLAP_MODE = "automix_overlap_mode"
        const val AUTOMIX_START_OFFSET_AUTO = 0
        const val AUTOMIX_START_OFFSET_BEGINNING = 1
        const val AUTOMIX_START_OFFSET_CUSTOM = 2
        private const val KEY_AUTOMIX_START_OFFSET_MODE = "automix_start_offset_mode"
        private const val KEY_AUTOMIX_START_OFFSET_CUSTOM_SEC = "automix_start_offset_custom_sec"
        private const val KEY_KEY_COLOR = "key_color"
        private const val KEY_COLOR_STYLE = "color_style"
        private const val KEY_COLOR_SPEC = "color_spec"
        private const val KEY_SLEEP_TIMER_FADE_DURATION = "sleep_timer_fade_duration"
        private const val KEY_SLEEP_TIMER_FADE_ENABLED = "sleep_timer_fade_enabled"
        private const val KEY_CACHED_USER_ID = "cached_user_id"
        private const val KEY_CACHED_USERNAME = "cached_username"
        private const val KEY_TRACK_DYNAMIC_THEME = "track_dynamic_theme"
        private const val KEY_ANIMATED_COVERS = "animated_covers_enabled"
        private const val KEY_ANIMATED_COVERS_FADE_UI = "animated_covers_fade_ui_enabled"
        private const val KEY_ANIMATED_ARTIST_PROFILES = "animated_artist_profiles_enabled"

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
        private const val KEY_SYNC_DISCLAIMER_DISMISSED = "sync_disclaimer_dismissed"

        const val KEY_AUDIO_PROVIDER_ORDER = "audio_provider_order"
        const val KEY_QOBUZ_COUNTRY = "qobuz_country"
        const val KEY_QOBUZ_CUSTOM_INSTANCES = "qobuz_custom_instances"
        const val KEY_QOBUZ_QUALITY = "qobuz_quality"
        const val KEY_TIDAL_RESOLVER_ENDPOINTS = "tidal_resolver_endpoints"
        const val KEY_TIDAL_AUDIO_QUALITY = "tidal_audio_quality"
        const val KEY_TIDAL_COOKIE = "tidal_cookie"
        const val KEY_DEEZER_RESOLVER_URL = "deezer_resolver_url"
        const val KEY_DEEZER_AUDIO_QUALITY = "deezer_audio_quality"
        const val KEY_DEEZER_FAST_MODE = "deezer_fast_mode"
        const val KEY_DEEZER_PROXY_MODE = "deezer_proxy_mode"
        const val KEY_DEEZER_PROXY_URL = "deezer_proxy_url"
        const val KEY_DEEZER_COOKIE = "deezer_cookie"
        const val KEY_DEEZER_USE_ACCOUNT = "deezer_use_account"

        private val queueLock = Any()
    }

    fun isSyncDisclaimerDismissed(): Boolean = Prefs.getBoolean(KEY_SYNC_DISCLAIMER_DISMISSED, false)
    fun setSyncDisclaimerDismissed(dismissed: Boolean) = Prefs.putBoolean(KEY_SYNC_DISCLAIMER_DISMISSED, dismissed)

    fun getSyncLikesEnabled(): Boolean = Prefs.getBoolean(KEY_SYNC_LIKES, true)

    /** Whether listens are shared with paired devices. Off keeps them on this device only. */
    fun getSyncListensEnabled(): Boolean = Prefs.getBoolean("sync_listens_enabled", true)
    fun setSyncListensEnabled(enabled: Boolean) = Prefs.putBoolean("sync_listens_enabled", enabled)
    fun setSyncLikesEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_SYNC_LIKES, enabled)

    fun getHasCompletedSetup(): Boolean = Prefs.getBoolean(KEY_HAS_COMPLETED_SETUP, false)
    fun setHasCompletedSetup(completed: Boolean) = Prefs.putBoolean(KEY_HAS_COMPLETED_SETUP, completed)

    fun getCrossfadeEnabled(): Boolean = Prefs.getBoolean(KEY_CROSSFADE_ENABLED, false)
    fun setCrossfadeEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_CROSSFADE_ENABLED, enabled)

    fun getCrossfadeDuration(): Int = Prefs.getInt(KEY_CROSSFADE_DURATION, 5)
    fun setCrossfadeDuration(seconds: Int) = Prefs.putInt(KEY_CROSSFADE_DURATION, seconds.coerceIn(1, 12))

    fun getCrossfadeGapless(): Boolean = Prefs.getBoolean(KEY_CROSSFADE_GAPLESS, true)
    fun setCrossfadeGapless(enabled: Boolean) = Prefs.putBoolean(KEY_CROSSFADE_GAPLESS, enabled)

    fun getAutomixEnabled(): Boolean = Prefs.getBoolean(KEY_AUTOMIX_ENABLED, false)
    fun setAutomixEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_AUTOMIX_ENABLED, enabled)
    fun automixEnabledFlow(): kotlinx.coroutines.flow.Flow<Boolean> = Prefs.booleanFlow(KEY_AUTOMIX_ENABLED, false)

    fun getAutomixDebugOverlayEnabled(): Boolean = Prefs.getBoolean(KEY_AUTOMIX_DEBUG_OVERLAY, false)
    fun setAutomixDebugOverlayEnabled(enabled: Boolean) {
        Prefs.putBoolean(KEY_AUTOMIX_DEBUG_OVERLAY, enabled)
        com.alananasss.kittytune.audio.automix.AutomixManager.setDebugOverlayEnabled(enabled)
    }
    fun automixDebugOverlayEnabledFlow(): kotlinx.coroutines.flow.Flow<Boolean> = Prefs.booleanFlow(KEY_AUTOMIX_DEBUG_OVERLAY, false)

    fun getAutomixTempoMatchEnabled(): Boolean = Prefs.getBoolean(KEY_AUTOMIX_TEMPO_MATCH, true)
    fun setAutomixTempoMatchEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_AUTOMIX_TEMPO_MATCH, enabled)

    fun getAutomixHarmonicMixEnabled(): Boolean = Prefs.getBoolean(KEY_AUTOMIX_HARMONIC_MIX, true)
    fun setAutomixHarmonicMixEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_AUTOMIX_HARMONIC_MIX, enabled)

    fun getAutomixDynamicMixPointsEnabled(): Boolean = Prefs.getBoolean(KEY_AUTOMIX_DYNAMIC_MIX_POINTS, true)
    fun setAutomixDynamicMixPointsEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_AUTOMIX_DYNAMIC_MIX_POINTS, enabled)

    fun getAutomixBassDuckingEnabled(): Boolean = Prefs.getBoolean(KEY_AUTOMIX_BASS_DUCKING, true)
    fun setAutomixBassDuckingEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_AUTOMIX_BASS_DUCKING, enabled)

    fun getAutomixOverlapMode(): Int = Prefs.getInt(KEY_AUTOMIX_OVERLAP_MODE, 0)
    fun setAutomixOverlapMode(mode: Int) = Prefs.putInt(KEY_AUTOMIX_OVERLAP_MODE, mode)

    fun getAutomixStartOffsetMode(): Int = Prefs.getInt(KEY_AUTOMIX_START_OFFSET_MODE, AUTOMIX_START_OFFSET_AUTO)
    fun setAutomixStartOffsetMode(mode: Int) = Prefs.putInt(KEY_AUTOMIX_START_OFFSET_MODE, mode)

    fun getAutomixStartOffsetCustomSec(): Int = Prefs.getInt(KEY_AUTOMIX_START_OFFSET_CUSTOM_SEC, 10)
    fun setAutomixStartOffsetCustomSec(seconds: Int) = Prefs.putInt(KEY_AUTOMIX_START_OFFSET_CUSTOM_SEC, seconds.coerceIn(0, 60))

    fun getCustomFontEnabled() = Prefs.getBoolean(KEY_CUSTOM_FONT_ENABLED, true)

    /** The app's typeface id (see [com.alananasss.kittytune.ui.theme.AppFont]); follows the old switch until set. */
    fun getAppFont(): String = Prefs.getString("app_font", null) ?: if (getCustomFontEnabled()) "flex" else "default"
    fun setAppFont(id: String) = Prefs.putString("app_font", id)
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

    fun getLyricsFullScreenAlignment(): LyricsAlignment {
        val name = Prefs.getString(KEY_LYRICS_FULLSCREEN_ALIGNMENT, LyricsAlignment.LEFT.name)
        return try { LyricsAlignment.valueOf(name!!) } catch (_: Exception) { LyricsAlignment.LEFT }
    }
    fun setLyricsFullScreenAlignment(align: LyricsAlignment) = Prefs.putString(KEY_LYRICS_FULLSCREEN_ALIGNMENT, align.name)

    fun getLyricsSidebarAlignment(): LyricsAlignment {
        val name = Prefs.getString(KEY_LYRICS_SIDEBAR_ALIGNMENT, LyricsAlignment.LEFT.name)
        return try { LyricsAlignment.valueOf(name ?: LyricsAlignment.LEFT.name) } catch (_: Exception) { LyricsAlignment.LEFT }
    }
    fun setLyricsSidebarAlignment(align: LyricsAlignment) = Prefs.putString(KEY_LYRICS_SIDEBAR_ALIGNMENT, align.name)

    fun getLyricsDisplayStyle(): LyricsDisplayStyle {
        val name = Prefs.getString(KEY_LYRICS_DISPLAY_STYLE, LyricsDisplayStyle.STANDARD.name)
        return LyricsDisplayStyle.entries.find { it.name == name } ?: LyricsDisplayStyle.STANDARD
    }

    fun setLyricsDisplayStyle(style: LyricsDisplayStyle) =
        Prefs.putString(KEY_LYRICS_DISPLAY_STYLE, style.name)

    fun getLyricsFullScreenDisplayStyle(): LyricsDisplayStyle {
        val name = Prefs.getString(KEY_LYRICS_FULLSCREEN_DISPLAY_STYLE, LyricsDisplayStyle.STANDARD.name)
        return LyricsDisplayStyle.entries.find { it.name == name } ?: LyricsDisplayStyle.STANDARD
    }

    fun setLyricsFullScreenDisplayStyle(style: LyricsDisplayStyle) =
        Prefs.putString(KEY_LYRICS_FULLSCREEN_DISPLAY_STYLE, style.name)

    fun getLyricsSidebarDisplayStyle(): LyricsDisplayStyle {
        val name = Prefs.getString(KEY_LYRICS_SIDEBAR_DISPLAY_STYLE, LyricsDisplayStyle.STANDARD.name)
        return LyricsDisplayStyle.entries.find { it.name == name } ?: LyricsDisplayStyle.STANDARD
    }

    fun setLyricsSidebarDisplayStyle(style: LyricsDisplayStyle) =
        Prefs.putString(KEY_LYRICS_SIDEBAR_DISPLAY_STYLE, style.name)

    fun getLyricsDuetViewEnabled(): Boolean = Prefs.getBoolean(KEY_LYRICS_DUET_VIEW, true)
    fun setLyricsDuetViewEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_LYRICS_DUET_VIEW, enabled)

    fun getLyricsDuetBlacklist(): Set<String> = Prefs.getStringSet(KEY_LYRICS_DUET_BLACKLIST, emptySet())
    fun setLyricsDuetBlacklist(blacklist: Set<String>) = Prefs.putStringSet(KEY_LYRICS_DUET_BLACKLIST, blacklist)
    fun isTrackDuetBlacklisted(trackId: Long): Boolean = getLyricsDuetBlacklist().contains(trackId.toString())
    fun setTrackDuetBlacklisted(trackId: Long, blacklisted: Boolean) {
        val current = getLyricsDuetBlacklist().toMutableSet()
        if (blacklisted) {
            current.add(trackId.toString())
        } else {
            current.remove(trackId.toString())
        }
        setLyricsDuetBlacklist(current)
    }

    fun setCachedUserId(id: Long) {
        Prefs.putLong(KEY_CACHED_USER_ID, id)
    }

    fun getCachedUserId(): Long {
        return Prefs.getLong(KEY_CACHED_USER_ID, 0L)
    }

    fun setCachedUsername(username: String?) {
        Prefs.putString(KEY_CACHED_USERNAME, username)
    }

    fun getCachedUsername(): String? {
        return Prefs.getString(KEY_CACHED_USERNAME, null)
    }

    fun getTrackDynamicTheme(): Boolean = Prefs.getBoolean(KEY_TRACK_DYNAMIC_THEME, false)
    fun setTrackDynamicTheme(enabled: Boolean) = Prefs.putBoolean(KEY_TRACK_DYNAMIC_THEME, enabled)

    fun getAnimatedCoversEnabled(): Boolean = Prefs.getBoolean(KEY_ANIMATED_COVERS, true)
    fun setAnimatedCoversEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_ANIMATED_COVERS, enabled)
    fun animatedCoversFlow(): Flow<Boolean> = Prefs.booleanFlow(KEY_ANIMATED_COVERS, true)
    fun getAnimatedCoversFlow(): Flow<Boolean> = animatedCoversFlow()

    fun getAnimatedCoversFadeUiEnabled(): Boolean = Prefs.getBoolean(KEY_ANIMATED_COVERS_FADE_UI, false)
    fun setAnimatedCoversFadeUiEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_ANIMATED_COVERS_FADE_UI, enabled)

    fun getAnimatedArtistProfilesEnabled(): Boolean = Prefs.getBoolean(KEY_ANIMATED_ARTIST_PROFILES, true)
    fun setAnimatedArtistProfilesEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_ANIMATED_ARTIST_PROFILES, enabled)
    fun animatedArtistProfilesFlow(): Flow<Boolean> = Prefs.booleanFlow(KEY_ANIMATED_ARTIST_PROFILES, true)
    fun getAnimatedArtistProfilesFlow(): Flow<Boolean> = animatedArtistProfilesFlow()



    fun getLyricsFontSize(): Float = Prefs.getFloat(KEY_LYRICS_FONT_SIZE, 42f)
    fun setLyricsFontSize(size: Float) = Prefs.putFloat(KEY_LYRICS_FONT_SIZE, size)

    fun getLyricsFullScreenFontSize(): Float = Prefs.getFloat(KEY_LYRICS_FULLSCREEN_FONT_SIZE, 42f)
    fun setLyricsFullScreenFontSize(size: Float) = Prefs.putFloat(KEY_LYRICS_FULLSCREEN_FONT_SIZE, size)

    fun getLyricsSidebarFontSize(): Float =
        Prefs.getFloat(KEY_LYRICS_SIDEBAR_FONT_SIZE, 22f)
    fun setLyricsSidebarFontSize(size: Float) = Prefs.putFloat(KEY_LYRICS_SIDEBAR_FONT_SIZE, size)

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
    /**
     * The volume slider's position. Saved values from before the perceptual curve were amplitudes;
     * they are converted once, so an update does not change how loud anyone's music is.
     */
    fun getSavedVolume(): Float {
        val saved = Prefs.getFloat(KEY_PLAYER_VOLUME, 1f)
        if (Prefs.getBoolean(KEY_VOLUME_IS_PERCEPTUAL, false)) return saved
        val migrated = com.alananasss.kittytune.audio.VolumeCurve.amplitudeToSlider(saved)
        Prefs.putFloat(KEY_PLAYER_VOLUME, migrated)
        Prefs.putBoolean(KEY_VOLUME_IS_PERCEPTUAL, true)
        return migrated
    }
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
    fun getQueuePreserveUpcomingOnJump(): Boolean = Prefs.getBoolean(KEY_QUEUE_PRESERVE_UPCOMING_ON_JUMP, true)
    fun setQueuePreserveUpcomingOnJump(enabled: Boolean) = Prefs.putBoolean(KEY_QUEUE_PRESERVE_UPCOMING_ON_JUMP, enabled)
    fun getMixDislikedTrackIds(): Set<Long> {
        val json = Prefs.getString(KEY_MIX_DISLIKED_TRACK_IDS, null) ?: return emptySet()
        return try {
            gson.fromJson(json, object : com.google.gson.reflect.TypeToken<Set<Long>>() {}.type) ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }
    }
    fun addMixDislikedTrack(trackId: Long) {
        val current = getMixDislikedTrackIds().toMutableSet()
        current.add(trackId)
        Prefs.putString(KEY_MIX_DISLIKED_TRACK_IDS, gson.toJson(current))
    }
    fun removeMixDislikedTrack(trackId: Long) {
        val current = getMixDislikedTrackIds().toMutableSet()
        current.remove(trackId)
        Prefs.putString(KEY_MIX_DISLIKED_TRACK_IDS, gson.toJson(current))
    }
    fun isMixTrackDisliked(trackId: Long): Boolean = getMixDislikedTrackIds().contains(trackId)
    fun getMixPrioritizeTrusted(): Boolean = Prefs.getBoolean(KEY_MIX_PRIORITIZE_TRUSTED, true)
    fun setMixPrioritizeTrusted(enabled: Boolean) = Prefs.putBoolean(KEY_MIX_PRIORITIZE_TRUSTED, enabled)

    fun getMiniPlayerEnabled(): Boolean = Prefs.getBoolean(KEY_MINI_PLAYER_ENABLED, false)
    fun setMiniPlayerEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_MINI_PLAYER_ENABLED, enabled)
    fun miniPlayerEnabledFlow() = Prefs.booleanFlow(KEY_MINI_PLAYER_ENABLED, false)

    fun getMiniPlayerX(): Int? = Prefs.getInt(KEY_MINI_PLAYER_X, -1).takeIf { it >= 0 }
    fun getMiniPlayerY(): Int? = Prefs.getInt(KEY_MINI_PLAYER_Y, -1).takeIf { it >= 0 }
    fun getMiniPlayerWidth(): Int = Prefs.getInt(KEY_MINI_PLAYER_WIDTH, 560).coerceIn(MINI_PLAYER_MIN_WIDTH, MINI_PLAYER_MAX_WIDTH)
    fun getMiniPlayerHeight(): Int = Prefs.getInt(KEY_MINI_PLAYER_HEIGHT, 82).coerceIn(MINI_PLAYER_MIN_HEIGHT, MINI_PLAYER_MAX_HEIGHT)

    fun setMiniPlayerBounds(x: Int, y: Int, width: Int, height: Int) {
        Prefs.putInt(KEY_MINI_PLAYER_X, x)
        Prefs.putInt(KEY_MINI_PLAYER_Y, y)
        Prefs.putInt(KEY_MINI_PLAYER_WIDTH, width.coerceIn(MINI_PLAYER_MIN_WIDTH, MINI_PLAYER_MAX_WIDTH))
        Prefs.putInt(KEY_MINI_PLAYER_HEIGHT, height.coerceIn(MINI_PLAYER_MIN_HEIGHT, MINI_PLAYER_MAX_HEIGHT))
    }

    fun setMiniPlayerBounds(x: Int, y: Int, width: Int) {
        setMiniPlayerBounds(x, y, width, getMiniPlayerHeight())
    }

    fun getMiniPlayerShowCover(): Boolean = Prefs.getBoolean(KEY_MINI_PLAYER_SHOW_COVER, true)
    fun setMiniPlayerShowCover(show: Boolean) = Prefs.putBoolean(KEY_MINI_PLAYER_SHOW_COVER, show)
    fun miniPlayerShowCoverFlow() = Prefs.booleanFlow(KEY_MINI_PLAYER_SHOW_COVER, true)

    fun getMiniPlayerShowPlaybackControls(): Boolean = Prefs.getBoolean(KEY_MINI_PLAYER_SHOW_PLAYBACK_CONTROLS, true)
    fun setMiniPlayerShowPlaybackControls(show: Boolean) = Prefs.putBoolean(KEY_MINI_PLAYER_SHOW_PLAYBACK_CONTROLS, show)
    fun miniPlayerShowPlaybackControlsFlow() = Prefs.booleanFlow(KEY_MINI_PLAYER_SHOW_PLAYBACK_CONTROLS, true)

    fun getMiniPlayerShowAdditionalControls(): Boolean = Prefs.getBoolean(KEY_MINI_PLAYER_SHOW_ADDITIONAL_CONTROLS, true)
    fun setMiniPlayerShowAdditionalControls(show: Boolean) = Prefs.putBoolean(KEY_MINI_PLAYER_SHOW_ADDITIONAL_CONTROLS, show)
    fun miniPlayerShowAdditionalControlsFlow() = Prefs.booleanFlow(KEY_MINI_PLAYER_SHOW_ADDITIONAL_CONTROLS, true)

    fun getMiniPlayerControlsOnHover(): Boolean = Prefs.getBoolean(KEY_MINI_PLAYER_CONTROLS_ON_HOVER, true)
    fun setMiniPlayerControlsOnHover(onHover: Boolean) = Prefs.putBoolean(KEY_MINI_PLAYER_CONTROLS_ON_HOVER, onHover)
    fun miniPlayerControlsOnHoverFlow() = Prefs.booleanFlow(KEY_MINI_PLAYER_CONTROLS_ON_HOVER, true)

    fun getMiniPlayerHoverEffect(): Boolean = Prefs.getBoolean(KEY_MINI_PLAYER_HOVER_EFFECT, true)
    fun setMiniPlayerHoverEffect(enabled: Boolean) = Prefs.putBoolean(KEY_MINI_PLAYER_HOVER_EFFECT, enabled)
    fun miniPlayerHoverEffectFlow() = Prefs.booleanFlow(KEY_MINI_PLAYER_HOVER_EFFECT, true)

    fun getMiniPlayerHoverIllumination(): Boolean = Prefs.getBoolean(KEY_MINI_PLAYER_HOVER_ILLUMINATION, true)
    fun setMiniPlayerHoverIllumination(enabled: Boolean) = Prefs.putBoolean(KEY_MINI_PLAYER_HOVER_ILLUMINATION, enabled)
    fun miniPlayerHoverIlluminationFlow() = Prefs.booleanFlow(KEY_MINI_PLAYER_HOVER_ILLUMINATION, true)

    fun getMiniPlayerShowProgress(): Boolean = Prefs.getBoolean(KEY_MINI_PLAYER_SHOW_PROGRESS, true)
    fun setMiniPlayerShowProgress(show: Boolean) = Prefs.putBoolean(KEY_MINI_PLAYER_SHOW_PROGRESS, show)
    fun miniPlayerShowProgressFlow() = Prefs.booleanFlow(KEY_MINI_PLAYER_SHOW_PROGRESS, true)

    fun getMiniPlayerStyle(): MiniPlayerStyle {
        val name = Prefs.getString(KEY_MINI_PLAYER_STYLE, MiniPlayerStyle.STANDARD.name)
        return try {
            MiniPlayerStyle.valueOf(name ?: MiniPlayerStyle.STANDARD.name)
        } catch (_: Exception) {
            MiniPlayerStyle.STANDARD
        }
    }
    fun setMiniPlayerStyle(style: MiniPlayerStyle) = Prefs.putString(KEY_MINI_PLAYER_STYLE, style.name)
    fun miniPlayerStyleFlow(): Flow<MiniPlayerStyle> = Prefs.flow.map { getMiniPlayerStyle() }

    fun getMiniPlayerTransparentBg(): Boolean = Prefs.getBoolean(KEY_MINI_PLAYER_TRANSPARENT_BG, false)
    fun setMiniPlayerTransparentBg(transparent: Boolean) = Prefs.putBoolean(KEY_MINI_PLAYER_TRANSPARENT_BG, transparent)
    fun miniPlayerTransparentBgFlow() = Prefs.booleanFlow(KEY_MINI_PLAYER_TRANSPARENT_BG, false)

    fun getMiniPlayerElongatedX(): Int? = Prefs.getInt(KEY_MINI_PLAYER_ELONGATED_X, -1).takeIf { it >= 0 }
    fun getMiniPlayerElongatedY(): Int? = Prefs.getInt(KEY_MINI_PLAYER_ELONGATED_Y, -1).takeIf { it >= 0 }
    fun getMiniPlayerElongatedWidth(): Int = Prefs.getInt(KEY_MINI_PLAYER_ELONGATED_WIDTH, MINI_PLAYER_ELONGATED_DEFAULT_WIDTH).coerceIn(MINI_PLAYER_ELONGATED_MIN_WIDTH, MINI_PLAYER_ELONGATED_MAX_WIDTH)
    fun getMiniPlayerElongatedHeight(): Int = Prefs.getInt(KEY_MINI_PLAYER_ELONGATED_HEIGHT, MINI_PLAYER_ELONGATED_DEFAULT_HEIGHT).coerceIn(MINI_PLAYER_ELONGATED_MIN_HEIGHT, MINI_PLAYER_ELONGATED_MAX_HEIGHT)

    fun setMiniPlayerElongatedBounds(x: Int, y: Int, width: Int, height: Int) {
        Prefs.putInt(KEY_MINI_PLAYER_ELONGATED_X, x)
        Prefs.putInt(KEY_MINI_PLAYER_ELONGATED_Y, y)
        Prefs.putInt(KEY_MINI_PLAYER_ELONGATED_WIDTH, width.coerceIn(MINI_PLAYER_ELONGATED_MIN_WIDTH, MINI_PLAYER_ELONGATED_MAX_WIDTH))
        Prefs.putInt(KEY_MINI_PLAYER_ELONGATED_HEIGHT, height.coerceIn(MINI_PLAYER_ELONGATED_MIN_HEIGHT, MINI_PLAYER_ELONGATED_MAX_HEIGHT))
    }

        fun getFullPlayerBgStyle(): FullPlayerBgStyle {
        val name = Prefs.getString(KEY_FULL_PLAYER_BG_STYLE, FullPlayerBgStyle.APPLE_MUSIC.name)
        // "ORBS" is what this style was called when it drew radial lights instead of the sleeve. Anyone who
        // ever opened the setting has it written down, and dropping them to the default would look like the
        // preference had been forgotten rather than renamed.
        if (name == LEGACY_ORBS_STYLE) return FullPlayerBgStyle.APPLE_MUSIC
        return try {
            FullPlayerBgStyle.valueOf(name ?: FullPlayerBgStyle.APPLE_MUSIC.name)
        } catch (_: Exception) {
            FullPlayerBgStyle.APPLE_MUSIC
        }
    }
    fun setFullPlayerBgStyle(style: FullPlayerBgStyle) = Prefs.putString(KEY_FULL_PLAYER_BG_STYLE, style.name)

    fun getFullPlayerCoverScale(): Float = Prefs.getFloat(KEY_FULL_PLAYER_COVER_SCALE, 1.0f).coerceIn(0.6f, 1.4f)
    fun setFullPlayerCoverScale(scale: Float) = Prefs.putFloat(KEY_FULL_PLAYER_COVER_SCALE, scale.coerceIn(0.6f, 1.4f))

    fun getFullPlayerLyricsAlign(): LyricsAlignment {
        val name = Prefs.getString(KEY_FULL_PLAYER_LYRICS_ALIGN, LyricsAlignment.LEFT.name)
        return try {
            LyricsAlignment.valueOf(name ?: LyricsAlignment.LEFT.name)
        } catch (_: Exception) {
            LyricsAlignment.LEFT
        }
    }
    fun setFullPlayerLyricsAlign(align: LyricsAlignment) = Prefs.putString(KEY_FULL_PLAYER_LYRICS_ALIGN, align.name)

    fun getSavePositionEnabled(): Boolean = Prefs.getBoolean(KEY_SAVE_POSITION, true)
    fun setSavePositionEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_SAVE_POSITION, enabled)

    fun getRightPanelWidth(): Float = Prefs.getFloat("right_panel_width", RIGHT_PANEL_DEFAULT_WIDTH).coerceIn(RIGHT_PANEL_MIN_WIDTH, RIGHT_PANEL_MAX_WIDTH)
    fun setRightPanelWidth(width: Float) = Prefs.putFloat("right_panel_width", width.coerceIn(RIGHT_PANEL_MIN_WIDTH, RIGHT_PANEL_MAX_WIDTH))

    fun getRightPanelOpen(): Boolean = Prefs.getBoolean(KEY_RIGHT_PANEL_OPEN, true)
    fun setRightPanelOpen(open: Boolean) = Prefs.putBoolean(KEY_RIGHT_PANEL_OPEN, open)

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

    fun getLyricsUiStyle(): LyricsUiStyle {
        val name = Prefs.getString(KEY_LYRICS_UI_STYLE, LyricsUiStyle.ENHANCED.name)
        return try {
            LyricsUiStyle.valueOf(name ?: LyricsUiStyle.ENHANCED.name)
        } catch (_: Exception) {
            LyricsUiStyle.ENHANCED
        }
    }
    fun setLyricsUiStyle(style: LyricsUiStyle) = Prefs.putString(KEY_LYRICS_UI_STYLE, style.name)

    fun getLyricsFullScreenUiStyle(): LyricsUiStyle {
        val name = Prefs.getString(KEY_LYRICS_FULLSCREEN_UI_STYLE, LyricsUiStyle.ENHANCED.name)
        return try {
            LyricsUiStyle.valueOf(name ?: LyricsUiStyle.ENHANCED.name)
        } catch (_: Exception) {
            LyricsUiStyle.ENHANCED
        }
    }
    fun setLyricsFullScreenUiStyle(style: LyricsUiStyle) = Prefs.putString(KEY_LYRICS_FULLSCREEN_UI_STYLE, style.name)

    fun getLyricsSidebarUiStyle(): LyricsUiStyle {
        val name = Prefs.getString(KEY_LYRICS_SIDEBAR_UI_STYLE, LyricsUiStyle.CLASSIC.name)
        return try {
            LyricsUiStyle.valueOf(name ?: LyricsUiStyle.CLASSIC.name)
        } catch (_: Exception) {
            LyricsUiStyle.CLASSIC
        }
    }
    fun setLyricsSidebarUiStyle(style: LyricsUiStyle) = Prefs.putString(KEY_LYRICS_SIDEBAR_UI_STYLE, style.name)

    fun getLyricsFont(): LyricsFont {
        val name = Prefs.getString(KEY_LYRICS_FONT, LyricsFont.APPLE.name)
        return try {
            LyricsFont.valueOf(name ?: LyricsFont.APPLE.name)
        } catch (_: Exception) {
            LyricsFont.APPLE
        }
    }
    fun setLyricsFont(font: LyricsFont) = Prefs.putString(KEY_LYRICS_FONT, font.name)

    fun getPlayerSliderStyle(): PlayerSliderStyle {
        val name = Prefs.getString(KEY_PLAYER_SLIDER_STYLE, PlayerSliderStyle.WAVY.name)
        return try {
            PlayerSliderStyle.valueOf(name ?: PlayerSliderStyle.WAVY.name)
        } catch (_: Exception) {
            PlayerSliderStyle.WAVY
        }
    }
    /** The volume track's own style, or null to follow the seek bar's. */
    fun getVolumeSliderStyle(): PlayerSliderStyle? =
        Prefs.getString("volume_slider_style", null)?.let { runCatching { PlayerSliderStyle.valueOf(it) }.getOrNull() }
    fun setVolumeSliderStyle(style: PlayerSliderStyle?) = Prefs.putString("volume_slider_style", style?.name)

    /** How the floating player bar is drawn; see [FloatingBarLook]. */
    fun getFloatingBarLook(): FloatingBarLook = FloatingBarLook(
        cornerDp = Prefs.getInt("floating_bar_corner", FloatingBarLook.DEFAULT.cornerDp),
        widthPercent = Prefs.getInt("floating_bar_width", FloatingBarLook.DEFAULT.widthPercent),
        marginDp = Prefs.getInt("floating_bar_margin", FloatingBarLook.DEFAULT.marginDp),
        isTranslucent = Prefs.getBoolean("floating_bar_translucent", FloatingBarLook.DEFAULT.isTranslucent),
    )

    fun setFloatingBarLook(look: FloatingBarLook) {
        Prefs.putInt("floating_bar_corner", look.cornerDp)
        Prefs.putInt("floating_bar_width", look.widthPercent)
        Prefs.putInt("floating_bar_margin", look.marginDp)
        Prefs.putBoolean("floating_bar_translucent", look.isTranslucent)
    }

    fun playerSliderStyleFlow(): Flow<PlayerSliderStyle> =
        Prefs.stringFlow(KEY_PLAYER_SLIDER_STYLE, PlayerSliderStyle.WAVY.name).map { name ->
            try {
                PlayerSliderStyle.valueOf(name ?: PlayerSliderStyle.WAVY.name)
            } catch (_: Exception) {
                PlayerSliderStyle.WAVY
            }
        }
    fun setPlayerSliderStyle(style: PlayerSliderStyle) = Prefs.putString(KEY_PLAYER_SLIDER_STYLE, style.name)

    fun getLyricsLineBlurEnabled(): Boolean = Prefs.getBoolean(KEY_LYRICS_LINE_BLUR, true)
    fun setLyricsLineBlurEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_LYRICS_LINE_BLUR, enabled)

    fun getLyricsFullScreenLineBlurEnabled(): Boolean =
        Prefs.getBoolean(KEY_LYRICS_FULLSCREEN_LINE_BLUR, true)
    fun setLyricsFullScreenLineBlurEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_LYRICS_FULLSCREEN_LINE_BLUR, enabled)

    fun getLyricsSidebarLineBlurEnabled(): Boolean =
        Prefs.getBoolean(KEY_LYRICS_SIDEBAR_LINE_BLUR, true)
    fun setLyricsSidebarLineBlurEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_LYRICS_SIDEBAR_LINE_BLUR, enabled)

    fun getLyricsLrcBounceEnabled(): Boolean = Prefs.getBoolean(KEY_LYRICS_LRC_BOUNCE_ENABLED, true)
    fun setLyricsLrcBounceEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_LYRICS_LRC_BOUNCE_ENABLED, enabled)

    fun getLyricsBounceFactor(): Float = Prefs.getFloat(KEY_LYRICS_BOUNCE_FACTOR, 1.0f)
    fun setLyricsBounceFactor(factor: Float) = Prefs.putFloat(KEY_LYRICS_BOUNCE_FACTOR, factor)

    fun getLyricsGlowFactor(): Float = Prefs.getFloat(KEY_LYRICS_GLOW_FACTOR, 1.0f)
    fun setLyricsGlowFactor(factor: Float) = Prefs.putFloat(KEY_LYRICS_GLOW_FACTOR, factor)

    fun getLyricsFillTransitionWidth(): Float = Prefs.getFloat(KEY_LYRICS_FILL_TRANSITION_WIDTH, 8.0f)
    fun setLyricsFillTransitionWidth(width: Float) = Prefs.putFloat(KEY_LYRICS_FILL_TRANSITION_WIDTH, width)

    fun getLyricsLineSpacing(): Float = Prefs.getFloat(KEY_LYRICS_LINE_SPACING, 0.0f).coerceIn(0f, 48f)
    fun setLyricsLineSpacing(spacing: Float) = Prefs.putFloat(KEY_LYRICS_LINE_SPACING, spacing.coerceIn(0f, 48f))

    fun getLyricsFullScreenLineSpacing(): Float = Prefs.getFloat(KEY_LYRICS_FULLSCREEN_LINE_SPACING, 0.0f).coerceIn(0f, 64f)
    fun setLyricsFullScreenLineSpacing(spacing: Float) = Prefs.putFloat(KEY_LYRICS_FULLSCREEN_LINE_SPACING, spacing.coerceIn(0f, 64f))

    fun getLyricsSidebarLineSpacing(): Float = Prefs.getFloat(KEY_LYRICS_SIDEBAR_LINE_SPACING, 0.0f).coerceIn(0f, 32f)
    fun setLyricsSidebarLineSpacing(spacing: Float) = Prefs.putFloat(KEY_LYRICS_SIDEBAR_LINE_SPACING, spacing.coerceIn(0f, 32f))

    fun getLyricsActiveScale(): Float = Prefs.getFloat(KEY_LYRICS_ACTIVE_SCALE, 1.00f).coerceIn(1.00f, 1.30f)
    fun setLyricsActiveScale(scale: Float) = Prefs.putFloat(KEY_LYRICS_ACTIVE_SCALE, scale.coerceIn(1.00f, 1.30f))

    fun getLyricsFullScreenActiveScale(): Float = Prefs.getFloat(KEY_LYRICS_FULLSCREEN_ACTIVE_SCALE, 1.00f).coerceIn(1.00f, 1.30f)
    fun setLyricsFullScreenActiveScale(scale: Float) = Prefs.putFloat(KEY_LYRICS_FULLSCREEN_ACTIVE_SCALE, scale.coerceIn(1.00f, 1.30f))

    fun getLyricsSidebarActiveScale(): Float = Prefs.getFloat(KEY_LYRICS_SIDEBAR_ACTIVE_SCALE, 1.00f).coerceIn(1.00f, 1.30f)
    fun setLyricsSidebarActiveScale(scale: Float) = Prefs.putFloat(KEY_LYRICS_SIDEBAR_ACTIVE_SCALE, scale.coerceIn(1.00f, 1.30f))

    fun getLyricsHorizontalMargin(): Float = Prefs.getFloat(KEY_LYRICS_HORIZONTAL_MARGIN, 0.0f).coerceIn(0f, 64f)
    fun setLyricsHorizontalMargin(margin: Float) = Prefs.putFloat(KEY_LYRICS_HORIZONTAL_MARGIN, margin.coerceIn(0f, 64f))

    fun getLyricsFullScreenHorizontalMargin(): Float = Prefs.getFloat(KEY_LYRICS_FULLSCREEN_HORIZONTAL_MARGIN, 0.0f).coerceIn(0f, 160f)
    fun setLyricsFullScreenHorizontalMargin(margin: Float) = Prefs.putFloat(KEY_LYRICS_FULLSCREEN_HORIZONTAL_MARGIN, margin.coerceIn(0f, 160f))

    fun getLyricsSidebarHorizontalMargin(): Float = Prefs.getFloat(KEY_LYRICS_SIDEBAR_HORIZONTAL_MARGIN, 0.0f).coerceIn(0f, 48f)
    fun setLyricsSidebarHorizontalMargin(margin: Float) = Prefs.putFloat(KEY_LYRICS_SIDEBAR_HORIZONTAL_MARGIN, margin.coerceIn(0f, 48f))

    fun getLyricsVerticalOffset(): Float = Prefs.getFloat(KEY_LYRICS_VERTICAL_OFFSET, 0.38f).coerceIn(0.20f, 0.60f)
    fun setLyricsVerticalOffset(offset: Float) = Prefs.putFloat(KEY_LYRICS_VERTICAL_OFFSET, offset.coerceIn(0.20f, 0.60f))

    fun getLyricsFullScreenVerticalOffset(): Float = Prefs.getFloat(KEY_LYRICS_FULLSCREEN_VERTICAL_OFFSET, 0.38f).coerceIn(0.20f, 0.60f)
    fun setLyricsFullScreenVerticalOffset(offset: Float) = Prefs.putFloat(KEY_LYRICS_FULLSCREEN_VERTICAL_OFFSET, offset.coerceIn(0.20f, 0.60f))

    fun getLyricsSidebarVerticalOffset(): Float = Prefs.getFloat(KEY_LYRICS_SIDEBAR_VERTICAL_OFFSET, 0.38f).coerceIn(0.20f, 0.60f)
    fun setLyricsSidebarVerticalOffset(offset: Float) = Prefs.putFloat(KEY_LYRICS_SIDEBAR_VERTICAL_OFFSET, offset.coerceIn(0.20f, 0.60f))


    fun getLyricsWordSyncEnabled(): Boolean = Prefs.getBoolean(KEY_LYRICS_WORD_SYNC, true)
    fun setLyricsWordSyncEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_LYRICS_WORD_SYNC, enabled)

    fun getLyricsTranslationEnabled(): Boolean = Prefs.getBoolean(KEY_LYRICS_TRANSLATION_ENABLED, false)
    fun setLyricsTranslationEnabled(enabled: Boolean) = Prefs.putBoolean(KEY_LYRICS_TRANSLATION_ENABLED, enabled)

    fun getLyricsTranslationLang(): String {
        val code = Prefs.getString(KEY_LYRICS_TRANSLATION_LANG, null)
        if (code != null) return code
        val appLang = getAppLanguage()
        if (appLang != AppLanguage.SYSTEM) return appLang.code
        return com.alananasss.kittytune.utils.LocaleUtils.getCurrentLocale().language.take(2).lowercase()
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
        val saved = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val hasNewKeys = saved.any {
            it == PLAYER_BAR_BUTTON_LYRICS || it == PLAYER_BAR_BUTTON_MINIPLAYER ||
                it == PLAYER_BAR_BUTTON_SHUFFLE || it == PLAYER_BAR_BUTTON_REPEAT
        }
        return if (!hasNewKeys) {
            saved + setOf(
                PLAYER_BAR_BUTTON_LYRICS,
                PLAYER_BAR_BUTTON_MINIPLAYER,
                PLAYER_BAR_BUTTON_SHUFFLE,
                PLAYER_BAR_BUTTON_REPEAT,
            )
        } else {
            saved
        }
    }

    fun setPlayerBarButtons(buttons: Set<String>) =
        Prefs.putString(KEY_PLAYER_BAR_BUTTONS, buttons.joinToString(","))

    fun getPlayerBarStyle(): PlayerBarStyle {
        val raw = Prefs.getString(KEY_PLAYER_BAR_STYLE, PlayerBarStyle.DEFAULT.name)
        return try {
            PlayerBarStyle.valueOf(raw ?: PlayerBarStyle.DEFAULT.name)
        } catch (_: Exception) {
            PlayerBarStyle.DEFAULT
        }
    }

    fun setPlayerBarStyle(style: PlayerBarStyle) =
        Prefs.putString(KEY_PLAYER_BAR_STYLE, style.name)

    fun playerBarStyleFlow(): Flow<PlayerBarStyle> =
        Prefs.stringFlow(KEY_PLAYER_BAR_STYLE, PlayerBarStyle.DEFAULT.name).map { raw ->
            try {
                PlayerBarStyle.valueOf(raw ?: PlayerBarStyle.DEFAULT.name)
            } catch (_: Exception) {
                PlayerBarStyle.DEFAULT
            }
        }

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

    /**
     * The sidebar's rows below Home, in order, each shown or hidden. Stored as `key` or `!key` per row.
     *
     * Rows missing from what was stored — every row, before this setting existed, and any row added by a
     * later version — are appended: the original four follow the older hidden set, the optional ones start off.
     */
    fun getSidebarNavLayout(): List<SidebarNavEntry> =
        parseSidebarNavLayout(Prefs.getString(KEY_SIDEBAR_NAV_LAYOUT, null), getHiddenSidebarNav())

    fun setSidebarNavLayout(entries: List<SidebarNavEntry>) =
        Prefs.putString(
            KEY_SIDEBAR_NAV_LAYOUT,
            entries.joinToString(",") { if (it.isVisible) it.key else "!${it.key}" },
        )

    fun sidebarNavLayoutFlow(): Flow<List<SidebarNavEntry>> =
        Prefs.stringFlow(KEY_SIDEBAR_NAV_LAYOUT, null).map { parseSidebarNavLayout(it, getHiddenSidebarNav()) }

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

    /**
     * Whether hovering over the collapsed sidebar automatically expands it temporarily (issue #33).
     * Off by default so it has to be chosen in settings.
     */
    fun isSidebarHoverExpandEnabled(): Boolean = Prefs.getBoolean("sidebar_hover_expand", false)
    fun setSidebarHoverExpandEnabled(enabled: Boolean) = Prefs.putBoolean("sidebar_hover_expand", enabled)
    fun sidebarHoverExpandFlow(): Flow<Boolean> = Prefs.booleanFlow("sidebar_hover_expand", false)

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

    fun getLyricsProviderOrder(): List<PreferredLyricsProvider> {
        val raw = Prefs.getString("lyrics_provider_order", null)
        return deserializeLyricsProviderOrder(raw)
    }

    fun setLyricsProviderOrder(order: List<PreferredLyricsProvider>) {
        Prefs.putString("lyrics_provider_order", serializeLyricsProviderOrder(order))
    }

    fun getLyricsProviderEnabled(provider: PreferredLyricsProvider): Boolean {
        return Prefs.getBoolean("enable_lyrics_provider_" + provider.name.lowercase(), true)
    }

    fun setLyricsProviderEnabled(provider: PreferredLyricsProvider, enabled: Boolean) {
        Prefs.putBoolean("enable_lyrics_provider_" + provider.name.lowercase(), enabled)
    }

    fun getPaxsenixApiKey(): String {
        val key = Prefs.getString("paxsenix_api_key", "") ?: ""
        PaxsenixClient.setApiKey(key)
        return key
    }

    fun setPaxsenixApiKey(key: String) {
        PaxsenixClient.setApiKey(key)
        Prefs.putString("paxsenix_api_key", key)
    }

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

    // Audio Provider Order
    fun getAudioProviderOrder(): List<com.alananasss.kittytune.audio.providers.AudioProviderOrderItem> {
        val raw = Prefs.getString(KEY_AUDIO_PROVIDER_ORDER, null)
        return com.alananasss.kittytune.audio.providers.AudioProviderOrder.deserialize(raw)
    }

    fun setAudioProviderOrder(order: List<com.alananasss.kittytune.audio.providers.AudioProviderOrderItem>) {
        Prefs.putString(KEY_AUDIO_PROVIDER_ORDER, com.alananasss.kittytune.audio.providers.AudioProviderOrder.serialize(order))
    }

    // Qobuz
    fun getQobuzCountry(): String = Prefs.getString(KEY_QOBUZ_COUNTRY, "US") ?: "US"
    fun setQobuzCountry(country: String) = Prefs.putString(KEY_QOBUZ_COUNTRY, country.trim().uppercase(java.util.Locale.US))

    fun getQobuzCustomInstances(): String = Prefs.getString(KEY_QOBUZ_CUSTOM_INSTANCES, com.alananasss.kittytune.audio.providers.qobuz.QobuzAudioProvider.DEFAULT_INSTANCE) ?: com.alananasss.kittytune.audio.providers.qobuz.QobuzAudioProvider.DEFAULT_INSTANCE
    fun setQobuzCustomInstances(instances: String) = Prefs.putString(KEY_QOBUZ_CUSTOM_INSTANCES, instances.trim())

    fun getQobuzQuality(): Int = Prefs.getInt(KEY_QOBUZ_QUALITY, 27)
    fun setQobuzQuality(quality: Int) = Prefs.putInt(KEY_QOBUZ_QUALITY, quality)

    // Tidal
    fun getTidalResolverEndpoints(): String = Prefs.getString(KEY_TIDAL_RESOLVER_ENDPOINTS, "") ?: ""
    fun setTidalResolverEndpoints(endpoints: String) = Prefs.putString(KEY_TIDAL_RESOLVER_ENDPOINTS, endpoints.trim())

    fun getTidalAudioQuality(): com.alananasss.kittytune.audio.providers.tidal.TidalAudioQuality {
        val raw = Prefs.getString(KEY_TIDAL_AUDIO_QUALITY, com.alananasss.kittytune.audio.providers.tidal.TidalAudioQuality.AAC_320.name)
        return try {
            com.alananasss.kittytune.audio.providers.tidal.TidalAudioQuality.valueOf(raw ?: com.alananasss.kittytune.audio.providers.tidal.TidalAudioQuality.AAC_320.name)
        } catch (_: Exception) {
            com.alananasss.kittytune.audio.providers.tidal.TidalAudioQuality.AAC_320
        }
    }
    fun setTidalAudioQuality(quality: com.alananasss.kittytune.audio.providers.tidal.TidalAudioQuality) =
        Prefs.putString(KEY_TIDAL_AUDIO_QUALITY, quality.name)

    fun getTidalCookie(): String = Prefs.getString(KEY_TIDAL_COOKIE, "") ?: ""
    fun setTidalCookie(cookie: String) = Prefs.putString(KEY_TIDAL_COOKIE, cookie.trim())

    // Deezer
    fun getDeezerResolverUrl(): String = Prefs.getString(KEY_DEEZER_RESOLVER_URL, com.alananasss.kittytune.audio.providers.deezer.DeezerAudioProvider.DEFAULT_RESOLVER_URL) ?: com.alananasss.kittytune.audio.providers.deezer.DeezerAudioProvider.DEFAULT_RESOLVER_URL
    fun setDeezerResolverUrl(url: String) = Prefs.putString(KEY_DEEZER_RESOLVER_URL, url.trim())

    fun getDeezerAudioQuality(): com.alananasss.kittytune.audio.providers.deezer.DeezerAudioQuality {
        val raw = Prefs.getString(KEY_DEEZER_AUDIO_QUALITY, com.alananasss.kittytune.audio.providers.deezer.DeezerAudioQuality.MP3_128.name)
        return try {
            com.alananasss.kittytune.audio.providers.deezer.DeezerAudioQuality.valueOf(raw ?: com.alananasss.kittytune.audio.providers.deezer.DeezerAudioQuality.MP3_128.name)
        } catch (_: Exception) {
            com.alananasss.kittytune.audio.providers.deezer.DeezerAudioQuality.MP3_128
        }
    }
    fun setDeezerAudioQuality(quality: com.alananasss.kittytune.audio.providers.deezer.DeezerAudioQuality) =
        Prefs.putString(KEY_DEEZER_AUDIO_QUALITY, quality.name)

    fun getDeezerFastMode(): Boolean = Prefs.getBoolean(KEY_DEEZER_FAST_MODE, false)
    fun setDeezerFastMode(fastMode: Boolean) = Prefs.putBoolean(KEY_DEEZER_FAST_MODE, fastMode)

    fun getDeezerProxyMode(): com.alananasss.kittytune.audio.providers.deezer.DeezerProxyMode {
        val raw = Prefs.getString(KEY_DEEZER_PROXY_MODE, com.alananasss.kittytune.audio.providers.deezer.DeezerProxyMode.DIRECT.name)
        return try {
            com.alananasss.kittytune.audio.providers.deezer.DeezerProxyMode.valueOf(raw ?: com.alananasss.kittytune.audio.providers.deezer.DeezerProxyMode.DIRECT.name)
        } catch (_: Exception) {
            com.alananasss.kittytune.audio.providers.deezer.DeezerProxyMode.DIRECT
        }
    }
    fun setDeezerProxyMode(mode: com.alananasss.kittytune.audio.providers.deezer.DeezerProxyMode) =
        Prefs.putString(KEY_DEEZER_PROXY_MODE, mode.name)

    fun getDeezerProxyUrl(): String = Prefs.getString(KEY_DEEZER_PROXY_URL, "") ?: ""
    fun setDeezerProxyUrl(url: String) = Prefs.putString(KEY_DEEZER_PROXY_URL, url.trim())

    fun getDeezerCookie(): String = Prefs.getString(KEY_DEEZER_COOKIE, "") ?: ""
    fun setDeezerCookie(cookie: String) = Prefs.putString(KEY_DEEZER_COOKIE, cookie.trim())

    fun getDeezerUseAccount(): Boolean = Prefs.getBoolean(KEY_DEEZER_USE_ACCOUNT, true)
    fun setDeezerUseAccount(useAccount: Boolean) = Prefs.putBoolean(KEY_DEEZER_USE_ACCOUNT, useAccount)
}

const val RIGHT_PANEL_MIN_WIDTH = 280f
const val RIGHT_PANEL_MAX_WIDTH = 440f
const val RIGHT_PANEL_DEFAULT_WIDTH = 340f

/** One row of the sidebar's navigation: which destination, and whether it is shown. */
data class SidebarNavEntry(val key: String, val isVisible: Boolean)

internal fun parseSidebarNavLayout(raw: String?, legacyHidden: Set<String>): List<SidebarNavEntry> {
    val known = PlayerPreferences.SIDEBAR_NAV_ITEMS + PlayerPreferences.SIDEBAR_NAV_EXTRAS
    val stored = raw.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { token ->
        val key = token.removePrefix("!")
        if (key in known) SidebarNavEntry(key, isVisible = !token.startsWith("!")) else null
    }.distinctBy { it.key }
    val missing = known.filter { key -> stored.none { it.key == key } }.map { key ->
        SidebarNavEntry(key, isVisible = key in PlayerPreferences.SIDEBAR_NAV_ITEMS && key !in legacyHidden)
    }
    val (home, rest) = missing.partition { it.key == PlayerPreferences.SIDEBAR_NAV_HOME }
    val layout = home + stored + rest
    // Never nothing at all: with every row off the sidebar would have no way anywhere.
    return if (layout.none { it.isVisible }) layout.map { if (it.key == PlayerPreferences.SIDEBAR_NAV_HOME) it.copy(isVisible = true) else it } else layout
}
