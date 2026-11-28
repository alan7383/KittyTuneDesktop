package com.alananasss.kittytune.ui.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.alananasss.kittytune.core.PerformancePreset
import com.alananasss.kittytune.core.PerformanceSettings
import com.alananasss.kittytune.audio.providers.AudioProviderOrderItem
import com.alananasss.kittytune.audio.ai.AiDetectionManager
import com.alananasss.kittytune.audio.ai.karaoke.KaraokeManager
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.data.local.PlayerBarStyle
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.sync.SyncPeers
import com.alananasss.kittytune.ui.main.MenuTiles
import com.alananasss.kittytune.ui.main.SidebarDestinations
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.profile.SettingsSubPage.*
import com.alananasss.kittytune.ui.theme.AppFont
import com.alananasss.kittytune.ui.theme.AppFonts

internal fun buildSettingsSearchIndex(
    os: String = System.getProperty("os.name"),
    fonts: List<AppFont> = AppFonts.pickerOptions(),
): List<IndexedSetting> {
    val fontChoices = fonts.map { font ->
        SettingsSearchEntry(
            titleKey = if (font == AppFont.Default) "font_default" else fontSearchKey(font),
            place = THEMES.place,
            highlightKey = fontSearchKey(font),
            icon = Icons.Rounded.TextFields,
            groupKey = "pref_font",
            aliases = if (font is AppFont.UserFile) listOf(font.fileName) else emptyList(),
            displayTitle = if (font == AppFont.Default) null else AppFonts.nameOf(font),
        )
    }
    return (buildSettingsSearchCatalog(os) + fontChoices).map(::IndexedSetting)
}

internal const val FONT_SEARCH_PREFIX = "font_choice/"
internal fun fontSearchKey(font: AppFont): String = FONT_SEARCH_PREFIX + font.id

/** Each control uses the same highlight key as its row, slider or button. Pages come from navigation. */
internal fun buildSettingsSearchCatalog(os: String): List<SettingsSearchEntry> = buildList {
    val windows = os.startsWith("Windows", ignoreCase = true)
    for (category in SettingsCategory.entries) {
        add(SettingsSearchEntry(category.titleKey, SettingsPlace(category), highlightKey = null, aliases = when (category) {
            SettingsCategory.INTERFACE -> listOf("UI", "appearance", "apparence")
            SettingsCategory.MISC -> listOf("general", "options")
            SettingsCategory.CREDITS -> listOf("about", "authors", "team")
            else -> emptyList()
        }))
    }
    for (page in SettingsSubPage.entries) {
        add(SettingsSearchEntry(page.titleKey, page.place, page.subtitleKey, highlightKey = null, aliases = when (page) {
            MINI_PLAYER -> listOf("mini player", "pip", "picture in picture")
            PET -> listOf("pet", "cat", "kitty", "tamagotchi", "chat", "chaton", "katze", "кот", "kedi", "mèo", "ivy")
            CUSTOM_THEME -> listOf("custom theme", "accent", "custom colours")
            SOURCES_SERVICES -> listOf("streaming services", "providers")
            AUDIO_KARAOKE -> listOf("karaoke", "vocal remover", "stems", "instrumental")
            MISC_DRM -> listOf("drm", "protected tracks", "license", "CDM", if (windows) "PlayReady" else "Widevine")
            else -> emptyList()
        }))
    }

    controls(THEMES.place) {
        control("startup_fade_title", "startup_fade_sub", aliases = listOf("startup", "splash", "loading screen", "fade", "boot"))
        control(
            "pref_dynamic_theme_merged",
            "pref_dynamic_theme_merged_sub",
            highlightKey = "pref_dynamic_theme",
            icon = Icons.Rounded.AutoAwesome,
            aliases = listOf("dynamic theme", "thème dynamique", "couleurs dynamiques", "palette", "cover color", "динамическая тема"),
        )
        control("pref_zoom_level", "pref_zoom_level_sub", aliases = listOf("scale", "font size", "text size", "interface scaling"))
        control(
            "pref_font",
            icon = Icons.Rounded.TextFields, aliases = listOf("app font", "typeface", "typography", "police application", "typographie", "шрифт"),
        )
        control("pref_app_icon", icon = Icons.Rounded.Apps, aliases = listOf("application icon", "app logo", "icône application"))
        control(
            "pref_animated_covers",
            "pref_animated_covers_desc",
            icon = Icons.Rounded.PlayCircle,
            aliases = listOf("animated cover", "covers animées", "pochette animée", "pochettes animées", "anime", "animé", "animated", "video", "анимированные обложки"),
        )
        control(
            "pref_animated_covers_fade_ui",
            "pref_animated_covers_fade_ui_desc",
            icon = Icons.Rounded.Opacity,
            aliases = listOf("fade", "fondu", "transparence", "ui", "fade ui"),
        )
        control(
            "pref_feed_video_samples",
            "pref_feed_video_samples_desc",
            icon = Icons.Rounded.PlayCircle,
            aliases = listOf("feed", "youtube", "video", "samples", "extraits"),
        )
        control(
            "pref_animated_artist_profiles",
            "pref_animated_artist_profiles_desc",
            icon = Icons.Rounded.AccountCircle,
            aliases = listOf("artist", "artiste", "artistes", "profile", "profiles", "profil", "profils", "anime", "animé", "animés", "animated", "video", "анимированные профили"),
        )
        control(
            "wave_title",
            "pref_home_my_wave_desc",
            highlightKey = "pref_home_my_wave",
            icon = Icons.Rounded.Waves,
            aliases = listOf("my wave", "wave", "mywave", "home", "cards", "vague", "accueil", "cartes", "волна", "главный экран"),
        )
        control(
            "pref_home_charts",
            "pref_home_charts_desc",
            icon = Icons.Rounded.Leaderboard,
            aliases = listOf("charts", "chart", "top", "home", "cards", "classements", "accueil", "cartes", "чарты", "главный экран"),
        )
        control(
            "listening_stats_title",
            "pref_home_listening_stats_desc",
            highlightKey = "pref_home_listening_stats",
            icon = Icons.Rounded.BarChart,
            aliases = listOf("listening stats", "stats", "statistics", "home", "cards", "statistiques", "écoute", "accueil", "cartes", "статистика", "главный экран"),
        )
        control(
            "mix_title",
            "pref_home_your_mix_desc",
            highlightKey = "pref_home_your_mix",
            icon = Icons.Rounded.AutoAwesome,
            aliases = listOf("mix", "your mix", "personalized", "home", "cards", "ton mix", "personnalisé", "accueil", "cartes", "микс", "персональный микс"),
        )
        if (windows) control("pref_themed_title_bar", "pref_themed_title_bar_sub")
        control("theme_presets_title")
        control("theme_system", groupKey = "theme_presets_title")
        control("theme_light", groupKey = "theme_presets_title", aliases = listOf("light mode", "mode clair"))
        control("theme_dark", groupKey = "theme_presets_title", aliases = listOf("dark mode", "mode sombre"))
        control("theme_amoled", groupKey = "theme_presets_title", aliases = listOf("pure black", "noir pur", "OLED"))
        for (key in listOf("ocean", "forest", "sunset", "rose", "lavender", "mint")) {
            control("theme_preset_$key", groupKey = "theme_presets_title")
        }
        control("font_add", groupKey = "pref_font", aliases = listOf("import font", "custom font", "ttf", "otf"))
        control("dialog_font_settings_title", groupKey = "pref_font", aliases = listOf("variable font", "font axes"))
        for (axis in listOf("weight", "width", "slant", "roundness")) {
            control("dialog_font_$axis", groupKey = "dialog_font_settings_title")
        }
    }
    controls(CUSTOM_THEME.place) {
        control("theme_preset_accents_title", "theme_preset_accents_subtitle")
        control("pref_custom_seed_title")
        control("color_hex_label", aliases = listOf("hex", "hexadecimal", "RGB"))
        control("color_hue_label")
        control("color_saturation_label")
        control("color_brightness_label")
        control("pref_color_style_title")
        control("pref_color_spec_title", aliasKeys = listOf("color_spec_2021_label", "color_spec_2025_label"))
    }
    controls(PLAYER.place) {
        control(
            "pref_full_player_source_title",
            "pref_full_player_source_desc",
            highlightKey = "pref_full_player_source",
            icon = Icons.Rounded.GraphicEq,
            aliases = listOf("source", "soundcloud", "youtube", "logo", "badge", "fullscreen", "plein écran", "titre", "point"),
        )
        control(
            "pref_screensaver_title",
            "pref_screensaver_desc",
            highlightKey = "pref_screensaver",
            icon = Icons.Rounded.DarkMode,
            aliases = listOf("screensaver", "écran de veille", "veille", "sleep", "dim", "fullscreen", "plein écran", "заставка", "экранная заставка"),
        )
        control(
            "full_player_cover_feather",
            "full_player_cover_feather_desc",
            icon = Icons.Rounded.BlurOn,
            aliases = listOf("feather", "soften", "cover", "blur", "edges", "adoucir", "bords", "pochette", "plein écran", "fullscreen", "размытие", "края обложки"),
        )
        control("player_shape_title", "player_shape_desc")
        control("player_shape_default", "player_shape_default_desc", groupKey = "player_shape_title")
        control("player_shape_rounded", "player_shape_rounded_desc", groupKey = "player_shape_title")
        control("player_shape_floating", "player_shape_floating_desc", groupKey = "player_shape_title")
        control("floating_bar_corner", groupKey = "floating_bar_title")
        control("floating_bar_width", groupKey = "floating_bar_title")
        control("floating_bar_margin", groupKey = "floating_bar_title")
        control("floating_bar_translucent", "floating_bar_translucent_sub", groupKey = "floating_bar_title")
        control("btn_reset", highlightKey = "floating_bar_reset", groupKey = "floating_bar_title")
        control("pref_slider_style", "pref_slider_style_desc")
        for (style in listOf("bar", "wavy", "slim", "squiggly")) {
            val key = "slider_style_$style"
            control(key, groupKey = "pref_slider_style")
            control(key, highlightKey = "volume_$key", groupKey = "volume_style_title")
        }
        control("volume_style_same", groupKey = "volume_style_title")
        control("volume_style_title", groupKey = "pref_volume_slider_title")
        control("pref_vertical_volume_slider", aliasKeys = listOf("pref_volume_slider_title", "volume_horizontal", "volume_vertical"))
        control("pref_show_remaining_time", "pref_show_remaining_time_desc")
        control("pref_seek_wheel", groupKey = "player_advanced_title")
        control("screensaver_timeout_title", aliasKeys = listOf("pref_screensaver_title"))
        control("full_player_cover_feather_amount", aliasKeys = listOf("full_player_cover_feather"))
        control("player_buttons_title", "player_buttons_desc")
        val buttons = listOf(
            Triple("like", "player_button_like", "player_button_like_desc"),
            Triple("lyrics", "player_button_lyrics", "player_button_lyrics_desc"),
            Triple("miniplayer", "mini_player_title", "player_button_miniplayer_desc"),
            Triple("pet", "pet_settings_title", "pet_desk_bar_button_desc"),
            Triple("panel", "player_button_panel", "player_button_panel_desc"),
            Triple("queue", "player_button_queue", "player_button_queue_desc"),
            Triple("devices", "player_button_devices", "player_button_devices_desc"),
            Triple("together", "together_live_title", "together_live_subtitle"),
            Triple("together_reactions", "together_reactions", "player_button_together_reactions_desc"),
            Triple("shuffle", "player_button_shuffle", "player_button_shuffle_desc"),
            Triple("repeat", "player_button_repeat", "player_button_repeat_desc"),
        )
        buttons.forEach { (id, title, description) ->
            control(title, description, highlightKey = "player_button_$id", groupKey = "player_buttons_title")
        }
        for ((group, tiles) in listOf("menu_tiles_track" to MenuTiles.TRACK, "menu_tiles_playlist" to MenuTiles.PLAYLIST)) {
            control(group, "menu_tiles_desc")
            control("menu_tiles_reset", highlightKey = "${group}_reset", groupKey = group)
            for (tile in tiles) control(tile.labelKey, highlightKey = "${group}_${tile.id}", groupKey = group)
        }
    }
    controls(LEFT_PANEL.place) {
        control(
            "pref_sidebar_hover_expand",
            "pref_sidebar_hover_expand_sub",
            icon = Icons.Rounded.ViewSidebar,
            aliases = listOf("sidebar", "hover", "survol", "déplier", "expand", "раскрывать при наведении"),
        )
        control("settings_group_sidebar_items", "sidebar_items_hint", aliasKeys = listOf("action_reorder"))
        for ((id, destination) in SidebarDestinations.ALL) {
            if (id != "home") control(destination.labelKey, highlightKey = "sidebar_$id", groupKey = "settings_group_sidebar_items")
        }
        control("lib_create", groupKey = "customize_section_library")
        control("history_title", groupKey = "customize_section_library")
        control("pref_library_tiles")
        for (key in listOf("lib_liked_tracks", "lib_downloads", "together_title", "lib_local_media")) {
            control(key, groupKey = "pref_library_tiles", aliasKeys = listOf("lib_tile_choose_icon", "lib_tile_reset_icon"))
        }
    }
    controls(RIGHT_PANEL.place) {
        control("pref_info_half")
        control("panel_tabs_title")
        for (key in listOf("detail_track_title", "player_queue", "player_lyrics", "player_effects", "connect_devices")) {
            control(key, highlightKey = "panel_tab_$key", groupKey = "panel_tabs_title")
        }
        control("panel_track_stats_title")
        for (key in listOf("detail_stats_plays", "detail_stats_likes", "detail_stats_reposts", "menu_comments", "detail_track_title")) {
            control(key, highlightKey = "panel_stat_$key", groupKey = "panel_track_stats_title")
        }
    }
    controls(LYRICS.place) {
        control("pref_lyrics_autoscroll", "pref_lyrics_autoscroll_sub")
        control("pref_lyrics_autoscroll_speed")
        control("pref_lyrics_wheel_step")
        control(
            "pref_lyrics_one_line_title",
            "pref_lyrics_one_line_sub",
            highlightKey = "pref_lyrics_one_line",
            icon = Icons.Rounded.Subtitles,
            aliases = listOf("one line", "floating lyrics", "lyrics bar", "une ligne", "paroles flottantes", "одна строка", "плавающий текст"),
        )
        control("pref_lyrics_translation_lang")
    }
    listOf(LYRICS_FULLSCREEN.place, LYRICS_CENTRAL.place, LYRICS_SIDEBAR.place).forEach { modePlace ->
        controls(modePlace) {
            if (modePlace == LYRICS_FULLSCREEN.place) {
                control(
                    "pref_screensaver_title",
                    "pref_screensaver_desc",
                    highlightKey = "pref_screensaver",
                    icon = Icons.Rounded.DarkMode,
                    aliases = listOf("screensaver", "écran de veille", "veille", "sleep", "dim", "fullscreen", "plein écran", "заставка", "экранная заставка"),
                )
            }
            control("pref_lyrics_font_title", icon = Icons.Rounded.TextFields, aliasKeys = listOf("pref_lyrics_font_apple", "pref_lyrics_font_app_default"))
            control("pref_lyrics_line_blur_title", "pref_lyrics_line_blur_desc")
            if (modePlace == LYRICS_CENTRAL.place) {
                control(
                    "pref_lyrics_position_title",
                    icon = Icons.Rounded.ViewWeek,
                    aliasKeys = listOf("align_left", "align_center_simple", "align_right"),
                    aliases = listOf("location", "position", "emplacement", "расположение"),
                )
            }
            control("pref_lyrics_align")
            control("pref_lyrics_size")
            control("pref_lyrics_line_spacing_title")
            control("pref_lyrics_horizontal_margin_title")
            control("pref_lyrics_vertical_offset_title")
            control("pref_lyrics_active_scale_title")
            control("pref_lyrics_reset_typography", "pref_lyrics_reset_typography_desc")
            control("pref_lyrics_word_sync", "pref_lyrics_word_sync_sub")
            control("pref_lyrics_apple_effect", "pref_lyrics_apple_effect_sub")
            control("pref_lyrics_duet_title", "pref_lyrics_duet_desc")
            control(
                "pref_lyrics_backing_title",
                "pref_lyrics_backing_sub",
                highlightKey = "pref_lyrics_backing",
                icon = Icons.Rounded.RecordVoiceOver,
                aliases = listOf("backing vocals", "backing", "choeurs", "voix secondaires", "бэк вокал"),
            )
            control(
                "pref_lyrics_reveal_words_title",
                "pref_lyrics_reveal_words_sub",
                highlightKey = "pref_lyrics_reveal_words",
                icon = Icons.Rounded.Animation,
                aliases = listOf("reveal words", "word by word", "animation", "mot à mot", "пословное появление"),
            )
            control(
                "pref_lyrics_line_animation",
                "pref_lyrics_line_animation_sub",
                icon = Icons.Rounded.Lyrics,
                aliases = listOf("sweep", "dégradé", "karaoké"),
            )
            control("pref_instrumental_indicator", icon = Icons.Rounded.Lyrics, aliases = listOf("interlude", "breathing dots", "points", "vague"))
            control("pref_lyrics_romanization", "pref_lyrics_romanization_sub")
            control("pref_lyrics_translation_title", "pref_lyrics_translation_sub")
            control("pref_lyrics_lrc_bounce_title", "pref_lyrics_lrc_bounce_desc")
            control("pref_lyrics_bounce_factor_title")
            control("pref_lyrics_glow_factor_title")
            control("pref_lyrics_fill_transition_title")
        }
    }
    controls(MINI_PLAYER.place) {
        control("mini_player_content_mode")
        control("mini_player_style_elongated", "mini_player_style_elongated_desc")
        control("mini_player_transparent_bg", "mini_player_transparent_bg_desc")
        control("mini_player_hover_illumination", "mini_player_hover_illumination_desc")
        control("mini_player_show_cover", "mini_player_show_cover_desc")
        control("mini_player_cover_as_background", "mini_player_cover_as_background_desc")
        control("mini_player_progress_fill", "mini_player_progress_fill_desc")
        control("mini_player_show_playback_controls", "mini_player_show_playback_controls_desc")
        control("mini_player_show_additional_controls", "mini_player_show_additional_controls_desc")
        control("mini_player_show_progress", "mini_player_show_progress_desc")
        control("mini_player_controls_on_hover", "mini_player_controls_on_hover_desc")
        control("mini_player_hover_effect", "mini_player_hover_effect_desc")
    }
    controls(PET.place) {
        control("pet_settings_name")
        control("pet_settings_room_decor", "pet_settings_room_decor_sub")
        control("pet_settings_holidays", "pet_settings_holidays_sub")
        control("pet_settings_seasonal", "pet_settings_seasonal_sub")
        control("pet_settings_show_name")
        control("pet_settings_show_hearts")
        control("pet_settings_show_status")
        control("pet_settings_sad_after")
        control("pet_settings_sleep_after")
        control("pet_settings_night_sleep", "pet_settings_night_sleep_sub")
        control("pet_settings_events", "pet_settings_events_sub")
        control("pet_settings_exclusives", "pet_desk_exclusives_sub")
        control("pet_settings_rares", "pet_settings_rares_sub")
        control("pet_desk_placement")
        control("pet_settings_replay", "pet_settings_replay_sub")
        control("pet_settings_reset", "pet_settings_reset_sub")
        control("pet_editor_open")
        control("pet_photo_card_title", "pet_desk_photo_card_body")
        for (key in listOf("color", "pattern", "eyes", "hat", "neck", "wall", "speed", "personality")) {
            control("pet_settings_$key")
        }
        control("pet_desk_tap")
        control("pet_bar_show", "pet_bar_show_sub", highlightKey = "pet_visibility")
        control("pet_desk_inside_show", "pet_desk_inside_show_sub", highlightKey = "pet_visibility")
        control("pet_desk_show", "pet_desk_show_sub", highlightKey = "pet_visibility")
    }
    controls(AUDIO_PLAYBACK.place) {
        control(
            "pref_autoplay",
            "pref_autoplay_sub",
            icon = Icons.Rounded.PlayArrow,
            aliases = listOf("autoplay", "lecture automatique", "station", "radio", "enchaîner", "автовоспроизведение"),
        )
        control(
            "pref_continuous_playback",
            "pref_continuous_playback_sub",
            icon = Icons.Rounded.Repeat,
            aliases = listOf("continuous", "lecture continue", "suite", "track", "непрерывное воспроизведение"),
        )
        control("pref_stop_on_task_clear")
        control(
            "pref_persist_queue",
            "pref_persist_queue_sub",
            icon = Icons.Rounded.QueueMusic,
            aliases = listOf("queue", "persist", "file d'attente", "garder", "sauvegarder", "сохранять очередь"),
        )
        control(
            "pref_queue_preserve_upcoming",
            "pref_queue_preserve_upcoming_sub",
            icon = Icons.Rounded.SkipNext,
            aliases = listOf("queue", "jump", "preserve", "saut", "titres suivants", "file"),
        )
        control(
            "pref_queue_auto_archive",
            "pref_queue_auto_archive_sub",
            icon = Icons.Rounded.History,
            aliases = listOf("queue", "history", "archive", "historique", "sauvegarde", "file"),
        )
        control(
            "pref_keep_played_tracks_visible",
            "pref_keep_played_tracks_visible_sub",
            icon = Icons.Rounded.History,
            aliases = listOf("queue", "played", "compact", "file d'attente", "historique", "déjà joué"),
        )
        control(
            "pref_save_position",
            "pref_save_position_sub",
            icon = Icons.Rounded.Save,
            aliases = listOf("position", "save position", "reprendre", "sauvegarder la position", "сохранять позицию"),
        )
        control(
            "pref_youtube_fallback",
            "pref_youtube_fallback_sub",
            icon = Icons.Rounded.SmartDisplay,
            aliases = listOf("youtube", "fallback", "secours", "alternative", "ютуб"),
        )
        control(
            "pref_precise_speed",
            "pref_precise_speed_sub",
            icon = Icons.Rounded.Speed,
            aliases = listOf("speed", "vitesse", "précise", "tempo", "rate", "точная скорость"),
        )
        control("pref_hide_video_songs", "pref_hide_video_songs_sub", icon = Icons.Rounded.VisibilityOff)
        control("pref_hide_youtube_shorts", "pref_hide_youtube_shorts_sub", icon = Icons.Rounded.VisibilityOff)
        control("pref_sc_sync_title", "pref_sc_sync_sub")
        control("pref_seek_wheel_step", icon = Icons.Rounded.FastForward, aliases = listOf("seek", "wheel", "колесико", "перемотка"))
        control("pref_skip_silence", "pref_skip_silence_sub", icon = Icons.Rounded.FastForward)
        control("pref_instant_skip_silence", "pref_instant_skip_silence_sub", icon = Icons.Rounded.FastForward)
        control("pref_pause_listen_history", "pref_pause_listen_history_sub", icon = Icons.Rounded.PlayArrow)
        control("pref_pause_search_history", "pref_pause_search_history_sub", icon = Icons.Rounded.Search)
    }
    controls(AUDIO_QUALITY.place) {
        control("pref_audio_device_title", icon = Icons.Rounded.Speaker, aliases = listOf("device", "speaker", "output", "устройство", "вывод"))
        control(
            "pref_quality",
            icon = Icons.Rounded.Tune,
            aliases = listOf("quality", "bitrate", "high", "low", "flac", "aac", "качество"),
            aliasKeys = listOf("quality_high", "quality_low"),
        )
        control("pref_audio_mono", "pref_audio_mono_sub")
        control(
            "equalizer_title",
            "equalizer_subtitle",
            highlightKey = "equalizer",
            icon = Icons.Rounded.Equalizer,
            aliases = listOf("equalizer", "eq", "autoeq", "auto eq", "headphones", "bluetooth", "bass", "treble", "égaliseur", "эквалайзер"),
        )
        control(
            "pref_norm_title",
            "pref_norm_sub",
            highlightKey = "pref_norm",
            icon = Icons.Rounded.VolumeUp,
            aliases = listOf("normalization", "volume", "loudness", "gain", "normalisation", "громкость", "нормализация"),
        )
        control("diag_hero_title", aliases = listOf("audio path", "codec", "bitrate", "resampling"))
    }
    controls(AUDIO_TRANSITIONS.place) {
        control(
            "pref_crossfade_title",
            "pref_crossfade_duration",
            highlightKey = "pref_crossfade",
            icon = Icons.Rounded.Shuffle,
            aliases = listOf("crossfade", "fade", "transition", "fondu", "enchaîné", "кроссфейд", "плавный переход"),
        )
        control("label_duration", highlightKey = "crossfade_duration", aliasKeys = listOf("pref_crossfade_title"))
        control(
            "automix",
            "automix_desc",
            highlightKey = "pref_automix",
            icon = Icons.Rounded.AutoAwesome,
            aliases = listOf("automix", "dj", "tempo", "harmonic", "transition", "mix", "автомикс"),
        )
        control("automix_overlap_mode")
        control("automix_start_offset")
        control("automix_tempo_match", "automix_tempo_match_desc")
        control("automix_harmonic_mix", "automix_harmonic_mix_desc")
        control("automix_dynamic_mix", "automix_dynamic_mix_desc")
        control("automix_bass_ducking", "automix_bass_ducking_desc")
        control("automix_debug", "automix_debug_desc")
    }
    controls(AUDIO_KARAOKE.place) {
        control("block_gpu_accel_title", "karaoke_gpu_desc", highlightKey = "karaoke_gpu_acceleration")
        control("block_gpu_device_title", highlightKey = "karaoke_gpu_device")
        control("karaoke_auto_separate_label", "karaoke_auto_separate_sub", highlightKey = "karaoke_auto_separate")
        control("karaoke_tab_model")
        control("karaoke_tab_stems")
        control("karaoke_tab_voice_boost")
        control("karaoke_cache_clear_btn", groupKey = "karaoke_tab_stems")
        control("karaoke_cache_title", "karaoke_cache_desc", groupKey = "karaoke_tab_stems")
        control("karaoke_acapella_title", "karaoke_acapella_desc", groupKey = "karaoke_tab_voice_boost")
        control("karaoke_enable_vocal_boost_title", groupKey = "karaoke_tab_voice_boost")
        control("label_vocal_boost_level", groupKey = "karaoke_tab_voice_boost")
        control("karaoke_isolate_now")
        control("karaoke_engine_select_title", "karaoke_engine_select_desc", aliases = listOf("stems", "model", "roformer"))
        control("karaoke_engine_dsp_title", "karaoke_engine_dsp_desc", groupKey = "karaoke_engine_select_title")
        control("karaoke_engine_roformer_title", "karaoke_engine_roformer_desc", groupKey = "karaoke_engine_select_title")
    }
    controls(AUDIO_SLEEP_TIMER.place) {
        control("sleep_timer_fade_title", "sleep_timer_fade_subtitle")
        control("label_duration", highlightKey = "sleep_fade_duration", aliasKeys = listOf("sleep_timer_fade_title"))
    }
    controls(PERFORMANCE_PRESETS.place) {
        for (preset in PerformancePreset.entries) {
            control(preset.titleKey, preset.descriptionKey)
        }
        control("performance_reset")
    }
    controls(PERFORMANCE_GRAPHICS.place) {
        control("performance_vsync", "performance_vsync_note",
            aliases = listOf("vsync", "vertical sync", "vertical synchronization"))
        control("performance_renderer", "performance_renderer_note",
            aliases = PerformanceSettings.renderers(os).map { it.name } + listOf("renderer", "rendering mode"),
            aliasKeys = listOf("performance_software"))
        control("performance_gpu", "performance_gpu_note",
            aliases = listOf("gpu", "preferred gpu", "graphics card", "video card"),
            aliasKeys = listOf("performance_integrated", "performance_discrete"))
        if (windows) control("performance_windows_gpu", "performance_windows_gpu_note")
    }
    controls(PERFORMANCE_CACHES.place) {
        control("performance_image_cache", "performance_cache_note",
            aliases = listOf("artwork memory cache", "artwork ram cache"))
        control("performance_gpu_cache", "performance_cache_note",
            aliases = listOf("graphics resource cache", "gpu cache", "vram cache"))
        control("performance_disk_cache", "performance_cache_note",
            aliases = listOf("artwork disk cache"))
    }
    controls(SOURCES_SERVICES.place) {
        control("SoundCloud", highlightKey = "sources_soundcloud", aliases = listOf("soundcloud sign in", "account"))
        control("Qobuz", highlightKey = null, route = "qobuz_settings", aliases = listOf("qobuz country", "qobuz instance"))
        control("TIDAL", highlightKey = null, route = "tidal_settings", aliases = listOf("tidal quality", "tidal hi-res"))
        control("Deezer", highlightKey = null, route = "deezer_settings", aliases = listOf("deezer quality", "arl"))
        control("sources_yandex", "pref_yandex_token_sub")
        control("pref_yandex_token", "pref_yandex_token_sub")
        val providerControls = mapOf(
            "qobuz" to listOf("country", "custom_instances", "audio_quality", "account"),
            "tidal" to listOf("web_login", "audio_quality", "resolver_endpoints", "cookie_title", "clear_cookie"),
            "deezer" to listOf("web_login", "cookie_title", "resolver_url", "proxy_mode", "audio_quality",
                "fast_mode", "use_account", "reset_resolver", "clear_proxy", "clear_cookie"),
        )
        for ((provider, keys) in providerControls) {
            val route = "${provider}_settings"
            val group = "${provider}_integration"
            control("provider_enabled_title", "provider_enabled_desc", groupKey = group, route = route)
            for (key in keys) {
                val title = "${provider}_$key"
                val description = when (title) {
                    "qobuz_country", "deezer_resolver_url", "deezer_fast_mode", "deezer_use_account",
                    "deezer_web_login", "tidal_web_login" -> "${title}_desc"
                    "qobuz_custom_instances" -> "qobuz_custom_instances_helper"
                    "qobuz_account" -> "qobuz_account_desc_disconnected"
                    "tidal_resolver_endpoints" -> "tidal_resolver_endpoints_helper"
                    "tidal_cookie_title" -> "tidal_cookie_helper"
                    "deezer_cookie_title" -> "deezer_cookie_helper"
                    else -> null
                }
                val optionKeys = when (title) {
                    "qobuz_audio_quality" -> listOf("qobuz_quality_hires_192", "qobuz_quality_hires_96", "qobuz_quality_cd", "qobuz_quality_mp3_320")
                    "tidal_audio_quality" -> listOf("tidal_quality_hires", "tidal_quality_flac", "tidal_quality_aac_320")
                    "deezer_audio_quality" -> listOf("deezer_quality_flac", "deezer_quality_mp3_320", "deezer_quality_mp3_128")
                    "deezer_proxy_mode" -> listOf("deezer_proxy_direct", "deezer_proxy_render", "deezer_proxy_custom")
                    else -> emptyList()
                }
                control(title, description, aliasKeys = optionKeys, groupKey = group, route = route)
            }
        }
    }
    controls(SOURCES_ORDER.place) {
        control("provider_order", highlightKey = null, icon = Icons.Rounded.SwapVert,
            aliases = listOf("provider order", "priority", "ordre", "приоритет", "порядок"),
            route = "provider_order")
        control("provider_order_reset", route = "provider_order", groupKey = "provider_order")
        for (provider in AudioProviderOrderItem.entries) {
            control("audio_provider_${provider.name.lowercase(java.util.Locale.ROOT)}", highlightKey = "provider_order_${provider.name}",
                route = "provider_order", groupKey = "provider_order")
        }
        control(
            "music_import_title",
            "music_import_settings_subtitle",
            highlightKey = null,
            icon = Icons.Rounded.ImportExport,
            aliases = listOf("import", "spotify", "playlist import", "импорт"),
            route = "music_import",
        )
    }
    controls(SOURCES_LYRICS.place) {
        control("pref_lyrics_local", "pref_lyrics_local_sub")
        control("pref_lyrics_provider_title")
        control("pref_lyrics_order", "pref_lyrics_order_sub")
        control("pref_lyrics_paxsenix_key", "pref_lyrics_paxsenix_key_sub")
    }
    controls(STORAGE_CACHE.place) {
        control("storage_cache_audio", "storage_cache_audio_sub")
        control("storage_cache_limit", "storage_cache_used")
        control("storage_cache_audio_size", aliasKeys = listOf("storage_clear"), aliases = listOf("clear audio cache"))
        control("storage_cache_images", aliasKeys = listOf("storage_clear"), aliases = listOf("clear image cache"))
    }
    controls(STORAGE_DOWNLOADS.place) {
        control("storage_downloaded_music", "storage_downloaded_summary")
        control("pref_storage_location")
        control("pref_storage_downloads_clear")
    }
    controls(STORAGE_LOCAL_FILES.place) {
        control("pref_local_enable", "pref_local_enable_sub")
        control("pref_local_add")
        control("pref_local_scan", aliasKeys = listOf("pref_local_folders"))
    }
    controls(STORAGE_BACKUP.place) {
        control("storage_backup_create", "storage_backup_create_sub")
        control("storage_backup_restore", "storage_backup_restore_sub")
    }
    controls(SYNC_DEVICES.place) {
        control("sync_pair_device")
        control("sync_now")
        control("sync_forget_device", highlightKey = "sync_devices")
        if (windows) control("sync_firewall_allow")
    }
    controls(SYNC_OPTIONS.place) {
        control("sync_listens_title", "sync_listens_sub")
        control("sync_likes_title", "sync_likes_sub")
        control("sync_playback_title", "sync_playback_sub")
        control("connect_internet_title", "connect_internet_sub")
    }
    controls(SYNC_ADVANCED.place) {
        control("sync_listener", "sync_listener_sub")
        control("sync_address")
        control("sync_events_held_title")
        control("sync_device_name")
        control("sync_copy_code")
        control("sync_copy_diagnostics")
        control("sync_regenerate_code")
    }
    controls(NETWORK_PROXY.place) {
        control("proxy_enable")
        control("proxy_auth_enable", "proxy_auth_enable_desc")
        control("proxy_import_clipboard")
        control("proxy_type")
        control("proxy_host")
        control("proxy_port")
        control("proxy_username")
        control("proxy_password")
        control("proxy_preset_v2ray")
        control("proxy_preset_tor")
        control("proxy_preset_http")
        control("proxy_preset_socks")
        control("proxy_test_connection")
        control("proxy_profile_save_as")
    }
    controls(NETWORK_ZAPRET.place) {
        control("zapret_choose")
        control("zapret_detect")
        control("zapret_check")
        control("zapret_add_blocked")
        control("zapret_remove_own")
    }
    controls(MISC_GENERAL.place) {
        control(
            "onboard_replay",
            "onboard_replay_body",
            icon = Icons.AutoMirrored.Rounded.MenuBook,
            aliases = listOf("setup", "onboarding", "welcome", "configuration", "initiale", "bienvenue"),
        )
        control("pref_language", icon = Icons.Rounded.Translate, aliases = listOf("language", "lang", "locale", "langue", "язык", "локализация"))
        control("pref_start_screen", icon = Icons.Rounded.Home, aliases = listOf("start screen", "home", "library", "стартовый экран"))
        control(
            "pref_auto_update",
            "pref_auto_update_subtitle",
            icon = Icons.Rounded.SystemUpdate,
            aliases = listOf("update", "version", "mise à jour", "обновление", "auto update"),
        )
        control(
            "pref_verified_filter",
            "pref_verified_filter_sub",
            icon = Icons.Rounded.Verified,
            aliases = listOf("verified", "verification", "reupload", "official", "cover", "search", "vérifié", "officiel", "проверено", "официальный"),
        )
        control(
            "pref_show_codec_on_player",
            "pref_show_codec_on_player_sub",
            icon = Icons.Rounded.GraphicEq,
            aliases = listOf("codec", "badge", "bitrate", "FLAC", "Opus", "AAC"),
        )
    }
    controls(MISC_DISCORD.place) {
        control("discord_enable_rpc", highlightKey = "pref_discord")
        control("pref_discord_status_display")
        control("discord_show_song_title", "discord_show_song_desc", aliases = listOf("discord", "track", "song"))
        control("discord_show_browsing_title", "discord_show_browsing_desc", aliases = listOf("discord", "browsing", "search", "navigation", "recherche"))
        control("discord_show_search_query_title", "discord_show_search_query_desc", aliases = listOf("discord", "search", "query", "recherche"))
        control("discord_hide_paused_title", "discord_hide_paused_desc", aliases = listOf("discord", "pause", "paused"))
        control("discord_show_timestamps_title", "discord_show_timestamps_desc", aliases = listOf("discord", "timestamp", "time", "duration"))
        control("discord_show_cover_title", "discord_show_cover_desc", aliases = listOf("discord", "cover", "artwork", "pochette"))
        control("discord_show_links_title", "discord_show_links_desc", aliases = listOf("discord", "link", "button", "soundcloud"))
        control("discord_title_parsing_title", "discord_title_parsing_desc", aliases = listOf("discord", "title", "parsing", "feat", "artist"))
        control("discord_show_effects_title", "discord_show_effects_desc", aliases = listOf("discord", "effects", "fx", "bass boost", "pitch", "slowed", "nightcore", "reverb", "eq"))
        control(
            "discord_listen_along_title",
            "discord_listen_along_desc",
            icon = Icons.Rounded.Headphones,
            aliases = listOf("discord", "listen along", "together", "live", "écouter ensemble", "salon", "lien"),
        )
        control(
            "discord_auto_live_title",
            "discord_auto_live_desc",
            icon = Icons.Rounded.Podcasts,
            aliases = listOf("discord", "auto live", "broadcast", "together", "direct", "écouter ensemble"),
        )
        control("together_copy_link")
        control("together_reset_link", "together_reset_link_desc")
        control("sync_copy_diagnostics")
    }
    controls(MISC_CONTENT_FILTER.place) {
        control(
            "block_ai_auto_skip",
            "block_ai_auto_skip_sub",
            highlightKey = "ai_auto_skip",
            icon = Icons.Rounded.SkipNext,
            aliases = listOf("auto skip", "ai skip", "passer auto", "автопро пропуск"),
        )
        control(
            "block_ai_auto_block",
            "block_ai_auto_block_sub",
            highlightKey = "ai_auto_block",
            icon = Icons.Rounded.Block,
            aliases = listOf("auto block", "block list", "bloquer auto", "автоблокировка"),
        )
        control(
            "block_ai_spare_favorites",
            "block_ai_spare_favorites_sub",
            highlightKey = "ai_spare_favorites",
            icon = Icons.Rounded.Favorite,
            aliases = listOf("spare favorites", "favoris", "épargner", "избранное"),
        )
        control(
            "block_ai_threshold",
            highlightKey = "ai_threshold",
            icon = Icons.Rounded.Tune,
            aliases = listOf("threshold", "seuil", "порог", "confidence"),
        )
        control(
            "block_ai_window_title",
            highlightKey = "ai_detection_window",
            icon = Icons.Rounded.Speed,
            aliases = listOf("window", "fenêtre", "detection", "analyse", "4s", "1s", "accurate", "fast", "окно", "анализ"),
        )
        control(
            "pref_ai_show_badge",
            "pref_ai_show_badge_sub",
            highlightKey = "ai_show_badge",
            icon = Icons.Rounded.SmartToy,
            aliases = listOf("badge", "tag", "player", "плеер"),
        )
        control(
            "pref_ai_show_human_badge",
            "pref_ai_show_human_badge_sub",
            highlightKey = "ai_show_human_badge",
            icon = Icons.Rounded.VerifiedUser,
            aliases = listOf("human badge", "humain", "аудио человек"),
        )
        control("block_gpu_enable", "block_gpu_enable_sub", highlightKey = "gpu_acceleration")
        control("block_gpu_device_title", highlightKey = "gpu_device_select")
        control("block_model_card_title", aliases = listOf("suno", "udio", "artifactnet", "ai model"))
        control("block_model_download_btn")
        control("block_model_delete_btn")
        control("block_section_ai")
        control("block_tab_tracks", "block_empty_tracks", highlightKey = "block_section_tracks")
        control("block_tab_artists", "block_empty_artists", highlightKey = "block_section_artists")
        control("block_clear_all_title", highlightKey = "block_clear_all_tracks", groupKey = "block_tab_tracks")
        control("block_clear_all_title", highlightKey = "block_clear_all_artists", groupKey = "block_tab_artists")
    }
    controls(MISC_DRM.place) {
        control("pref_drm_test", "pref_drm_test_desc")
        control("pref_drm_tour_title", "pref_drm_tour_desc")
        control("pref_drm_attempt_title")
        control("pref_drm_log_title", "pref_drm_log_desc")
        if (windows) {
            control("pref_drm_native_setup_title", "pref_drm_native_setup_desc", highlightKey = "pref_drm_native_setup",
                aliasKeys = listOf("pref_drm_native_redo_title"))
            control("pref_drm_native_remove_title", "pref_drm_native_remove_desc")
            control("pref_drm_wsl_cleanup_title", "pref_drm_wsl_cleanup_desc")
        } else {
            control("pref_drm_cdm_path")
            control("pref_drm_autodetect", "pref_drm_autodetect_desc")
            control("pref_drm_browse", "pref_drm_browse_desc")
        }
    }
}

/** When a row requires another setting, take the user to the prerequisite while it is hidden. */
internal fun SettingsSearchEntry.targetKey(prefs: PlayerPreferences, player: PlayerViewModel): String? = when (highlightKey) {
    "pref_animated_covers_fade_ui" -> if (prefs.getAnimatedCoversEnabled()) highlightKey else "pref_animated_covers"
    "crossfade_duration" -> if (prefs.getCrossfadeEnabled()) highlightKey else "pref_crossfade"
    "sleep_fade_duration" -> if (prefs.getSleepTimerFadeEnabled()) highlightKey else "sleep_timer_fade_title"
    "automix_overlap_mode", "automix_start_offset", "automix_tempo_match", "automix_harmonic_mix",
    "automix_dynamic_mix", "automix_bass_ducking", "automix_debug" ->
        if (prefs.getAutomixEnabled()) highlightKey else "pref_automix"
    "pref_instant_skip_silence" -> if (prefs.getSkipSilence()) highlightKey else "pref_skip_silence"
    "pref_sc_sync_title" -> if (TokenManager.isGuestMode()) "pref_pause_listen_history" else highlightKey
    "floating_bar_corner", "floating_bar_width", "floating_bar_margin", "floating_bar_translucent", "floating_bar_reset" ->
        if (prefs.getPlayerBarStyle() == PlayerBarStyle.FLOATING) highlightKey else "player_shape_floating"
    "screensaver_timeout_title" -> if (player.fullPlayerScreensaverEnabled) highlightKey else "pref_screensaver"
    "full_player_cover_feather_amount" -> if (player.fullPlayerCoverFeather) highlightKey else "full_player_cover_feather"
    "pref_lyrics_autoscroll_speed" ->
        if (player.isPlainAutoScrollEnabled) highlightKey else "pref_lyrics_autoscroll"
    "pref_lyrics_translation_lang" ->
        if (player.anyTranslationEnabled) highlightKey else "pref_lyrics_translation_title"
    "pref_lyrics_lrc_bounce_title", "pref_lyrics_bounce_factor_title", "pref_lyrics_glow_factor_title",
    "pref_lyrics_fill_transition_title", "pref_lyrics_active_scale_title", "pref_lyrics_reset_typography",
    "pref_lyrics_line_blur_title" -> highlightKey
    "pref_local_add" -> if (prefs.getLocalMediaEnabled()) highlightKey else "pref_local_enable"
    "pref_local_scan" -> when {
        !prefs.getLocalMediaEnabled() -> "pref_local_enable"
        prefs.getLocalMediaUris().isEmpty() -> "pref_local_add"
        else -> highlightKey
    }
    "sync_now", "sync_devices" -> if (SyncPeers.all().isEmpty()) "sync_pair_device" else highlightKey
    "pref_discord_status_display", "discord_show_song_title", "discord_show_browsing_title",
    "discord_hide_paused_title", "discord_show_timestamps_title", "discord_show_cover_title",
    "discord_show_links_title", "discord_title_parsing_title", "discord_show_effects_title",
    "discord_listen_along_title", "discord_auto_live_title" ->
        if (prefs.getDiscordRpcEnabled()) highlightKey else "pref_discord"
    "discord_show_search_query_title" -> when {
        !prefs.getDiscordRpcEnabled() -> "pref_discord"
        !prefs.getDiscordShowBrowsing() -> "discord_show_browsing_title"
        else -> highlightKey
    }
    "together_copy_link", "together_reset_link" -> when {
        !prefs.getDiscordRpcEnabled() -> "pref_discord"
        !prefs.getDiscordListenAlongEnabled() -> "discord_listen_along_title"
        else -> highlightKey
    }
    "proxy_username", "proxy_password" -> when {
        !prefs.getProxyEnabled() -> "proxy_enable"
        !prefs.getProxyAuthEnabled() -> "proxy_auth_enable"
        else -> highlightKey
    }
    "proxy_import_clipboard", "proxy_type", "proxy_host", "proxy_port", "proxy_preset_v2ray",
    "proxy_preset_tor", "proxy_preset_http", "proxy_preset_socks", "proxy_auth_enable",
    "proxy_test_connection", "proxy_profile_save_as" -> if (prefs.getProxyEnabled()) highlightKey else "proxy_enable"
    "ai_auto_block", "ai_spare_favorites", "ai_threshold" -> if (prefs.aiAutoSkip) highlightKey else "ai_auto_skip"
    "ai_show_human_badge" -> if (prefs.aiShowBadge && AiDetectionManager.isModelReady()) highlightKey else "ai_show_badge"
    "block_model_download_btn" -> if (AiDetectionManager.isModelReady()) "block_model_card_title" else highlightKey
    "block_model_delete_btn" -> if (AiDetectionManager.isModelReady()) highlightKey else "block_model_card_title"
    "gpu_device_select" -> if (prefs.aiGpuAccelerationEnabled) highlightKey else "gpu_acceleration"
    "karaoke_cache_clear_btn" -> if (KaraokeManager.getCachedStemsCount() > 0) highlightKey else "karaoke_cache_title"
    else -> highlightKey
}

private fun MutableList<SettingsSearchEntry>.controls(place: SettingsPlace, block: SettingsControls.() -> Unit) {
    SettingsControls(this, place).block()
}

private class SettingsControls(private val entries: MutableList<SettingsSearchEntry>, private val place: SettingsPlace) {
    fun control(
        titleKey: String,
        descriptionKey: String? = null,
        highlightKey: String? = titleKey,
        icon: ImageVector? = null,
        aliases: List<String> = emptyList(),
        aliasKeys: List<String> = emptyList(),
        groupKey: String? = null,
        route: String? = null,
    ) {
        entries += SettingsSearchEntry(titleKey, place, descriptionKey, highlightKey, icon, aliases, aliasKeys, groupKey, route)
    }
}
