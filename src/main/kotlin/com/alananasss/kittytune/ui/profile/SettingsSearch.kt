package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.together.Together
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn
import com.alananasss.kittytune.ui.common.SettingsHighlight
import com.alananasss.kittytune.ui.common.SettingsHighlightScope
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.getSettingsShape
import com.alananasss.kittytune.ui.player.PlayerViewModel
import java.text.Normalizer
import java.util.Locale

internal data class SettingsSearchEntry(
    val titleKey: String,
    val place: SettingsPlace,
    val descriptionKey: String? = null,
    val highlightKey: String? = titleKey,
    val icon: ImageVector? = null,
    val aliases: List<String> = emptyList(),
    val aliasKeys: List<String> = emptyList(),
    val groupKey: String? = null,
    val route: String? = null,
    val displayTitle: String? = null,
)

internal const val SETTINGS_SEARCH_TARGET = "settings_search_target"

@Composable
internal fun SettingsSearchDestination(backStackEntry: NavBackStackEntry, content: @Composable () -> Unit) {
    val state = backStackEntry.savedStateHandle
    val key by remember(state) { state.getStateFlow<String?>(SETTINGS_SEARCH_TARGET, null) }.collectAsState()
    val request = remember(key) {
        key?.let { SettingsHighlight(it) { state.set<String?>(SETTINGS_SEARCH_TARGET, null) } }
    }
    SettingsHighlightScope(request, content)
}

internal class IndexedSetting(val entry: SettingsSearchEntry) {
    val title = entry.displayTitle ?: str(entry.titleKey).withoutFormatArguments()
    private val contextKeys = (entry.place.titleKeys + listOfNotNull(entry.groupKey)).distinct()
    val breadcrumb = contextKeys.filter { it != entry.titleKey }.joinToString(" › ") { str(it) }.ifBlank { null }
    private val titlePhrase = normalizeSearchText(title)
    private val englishTitle = normalizeSearchText(entry.displayTitle ?: Strings.english(entry.titleKey).withoutFormatArguments())
    private val titleWords = (titlePhrase.words() + englishTitle.words()).distinct()
    private val phrases = (entry.aliases + entry.aliasKeys.flatMap { listOf(str(it), Strings.english(it)) })
        .map(::normalizeSearchText)
    private val aliasWords = phrases.flatMap { it.words() }.distinct()
    private val descriptionWords = entry.descriptionKey?.let { normalizeSearchText(str(it).withoutFormatArguments()).words() }.orEmpty()
    private val breadcrumbWords = contextKeys
        .flatMap { normalizeSearchText(str(it)).words() + normalizeSearchText(Strings.english(it)).words() }

    fun rank(phrase: String, tokens: List<String>): Int? {
        if (phrase == titlePhrase) return 0
        if (phrase == englishTitle) return 1
        if (tokens.all(titleWords::hasPrefix)) return 2
        if (phrase in phrases) return 3
        if (tokens.all { titleWords.hasPrefix(it) || aliasWords.hasPrefix(it) }) return 4
        if (tokens.any { it in titleWords } && tokens.all {
                titleWords.hasPrefix(it) || aliasWords.hasPrefix(it) || breadcrumbWords.hasPrefix(it)
            }
        ) return 5
        if (tokens.all { titleWords.matches(it) || aliasWords.matches(it) }) return 6

        val hasControlMatch = tokens.any { titleWords.matches(it) || aliasWords.matches(it) }
        if (hasControlMatch && tokens.all {
                titleWords.matches(it) || aliasWords.matches(it) || descriptionWords.matches(it) || breadcrumbWords.matches(it)
            }
        ) return 7
        if (tokens.all { descriptionWords.matches(it) }) return 8
        return null
    }
}

private val formatArgument = Regex("""%(?:\d+\$)?[a-zA-Z]""")
private fun String.withoutFormatArguments(): String =
    replace(formatArgument, "").trimEnd(' ', ':', '%').trim()

private val combiningMarks = Regex("""\p{M}+""")
private val separators = Regex("""[^\p{L}\p{N}]+""")
private fun normalizeSearchText(text: String): String =
    Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(combiningMarks, "")
        .replace("œ", "oe")
        .replace("æ", "ae")
        .replace("đ", "d")
        .replace("ı", "i")
        .replace("ß", "ss")
        .replace(separators, " ")
        .trim()

private fun String.words(): List<String> = if (isEmpty()) emptyList() else split(' ')

private fun List<String>.hasPrefix(token: String): Boolean = any { it.startsWith(token) }

private fun List<String>.matches(token: String): Boolean =
    any { it.startsWith(token) || (token.length >= 3 && token in it) }

internal fun searchSettings(index: List<IndexedSetting>, query: String): List<IndexedSetting> {
    val phrase = normalizeSearchText(query)
    if (phrase.isEmpty()) return emptyList()
    val tokens = phrase.words().distinct()
    return index.mapNotNull { item -> item.rank(phrase, tokens)?.let { it to item } }
        .sortedWith(compareBy({ it.first }, { it.second.entry.highlightKey == null }))
        .map { it.second }
}

@Composable
internal fun SettingsSearchResults(
    query: String,
    playerViewModel: PlayerViewModel,
    onOpen: (SettingsSearchEntry) -> Unit,
) {
    val language = Strings.resolvedLanguage
    val index = remember(language) { buildSettingsSearchIndex() }
    val matches = remember(query, index) { searchSettings(index, query) }
    if (matches.isEmpty()) {
        Column(
            Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Rounded.SearchOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                str("settings_search_no_results"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "\"${query.trim()}\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        return
    }

    val prefs = remember { PlayerPreferences() }
    Prefs.flow.collectAsState().value
    val switches = settingsSearchSwitches(prefs, playerViewModel)
    val listState = rememberLazyListState()
    LaunchedEffect(query) { listState.scrollToItem(0) }
    ScrollableLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(matches) { index, item ->
            val entry = item.entry
            val toggle = switches[entry.titleKey]
            SettingsItem(
                shape = getSettingsShape(matches.size, index),
                title = item.title,
                subtitle = item.breadcrumb,
                icon = entry.icon ?: entry.place.subPage?.icon ?: entry.place.category.icon,
                hasSwitch = toggle != null,
                switchState = toggle?.checked == true,
                onSwitchChange = toggle?.onChange,
                onClick = { onOpen(entry.copy(highlightKey = entry.targetKey(prefs, playerViewModel))) },
            )
        }
    }
}

private data class SearchSwitch(val checked: Boolean, val onChange: (Boolean) -> Unit)

private fun settingsSearchSwitches(prefs: PlayerPreferences, player: PlayerViewModel): Map<String, SearchSwitch> = buildMap {
    fun toggle(key: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        put(key, SearchSwitch(checked, onChange))
    }

    toggle("pref_animated_artist_profiles", prefs.getAnimatedArtistProfilesEnabled(), prefs::setAnimatedArtistProfilesEnabled)
    toggle("pref_animated_covers", prefs.getAnimatedCoversEnabled(), prefs::setAnimatedCoversEnabled)
    if (prefs.getAnimatedCoversEnabled()) {
        toggle("pref_animated_covers_fade_ui", prefs.getAnimatedCoversFadeUiEnabled(), prefs::setAnimatedCoversFadeUiEnabled)
    }
    toggle("pref_feed_video_samples", prefs.getFeedVideoSamplesEnabled(), prefs::setFeedVideoSamplesEnabled)
    toggle("pref_dynamic_theme_merged", prefs.getTrackDynamicTheme()) {
        prefs.setTrackDynamicTheme(it)
        prefs.setDynamicTheme(it)
    }
    toggle("wave_title", prefs.getShowHomeMyWave(), prefs::setShowHomeMyWave)
    toggle("pref_home_charts", prefs.getShowHomeCharts(), prefs::setShowHomeCharts)
    toggle("listening_stats_title", prefs.getShowHomeListeningStats(), prefs::setShowHomeListeningStats)
    toggle("mix_title", prefs.getShowHomeYourMix(), prefs::setShowHomeYourMix)
    toggle("pref_sidebar_hover_expand", prefs.isSidebarHoverExpandEnabled(), prefs::setSidebarHoverExpandEnabled)
    toggle("pref_vertical_volume_slider", prefs.getVerticalVolumeSlider(), prefs::setVerticalVolumeSlider)
    toggle("pref_show_remaining_time", prefs.getShowRemainingTime(), prefs::setShowRemainingTime)
    toggle("pref_screensaver_title", player.fullPlayerScreensaverEnabled, player::updateFullPlayerScreensaverEnabled)
    toggle("pref_full_player_source_title", player.fullPlayerSourceIndicatorEnabled, player::updateFullPlayerSourceIndicatorEnabled)
    toggle("full_player_cover_feather", player.fullPlayerCoverFeather, player::updateFullPlayerCoverFeather)
    toggle("pref_lyrics_one_line_title", prefs.getOneLineLyricsEnabled(), prefs::setOneLineLyricsEnabled)
    toggle("pref_lyrics_backing_title", player.lyricsSplitBackingVocals, player::updateLyricsSplitBackingVocals)
    toggle("pref_lyrics_reveal_words_title", player.lyricsRevealWords, player::updateLyricsRevealWords)
    toggle("pref_norm_title", player.effectsState.isNormalizationEnabled, player::toggleNormalization)
    toggle("pref_crossfade_title", prefs.getCrossfadeEnabled(), prefs::setCrossfadeEnabled)
    if (Together.mayChangeAutomix()) {
        toggle("automix", prefs.getAutomixEnabled()) { if (Together.mayChangeAutomix()) prefs.setAutomixEnabled(it) }
    }
    toggle("pref_autoplay", prefs.getAutoplayEnabled(), prefs::setAutoplayEnabled)
    toggle("pref_continuous_playback", prefs.getContinuousPlaybackEnabled(), prefs::setContinuousPlaybackEnabled)
    toggle("pref_persist_queue", prefs.getPersistentQueueEnabled(), prefs::setPersistentQueueEnabled)
    toggle("pref_queue_preserve_upcoming", player.isQueuePreserveUpcomingEnabled, player::toggleQueuePreserveUpcoming)
    toggle("pref_queue_auto_archive", player.isQueueAutoArchiveEnabled, player::toggleQueueAutoArchive)
    toggle("pref_keep_played_tracks_visible", prefs.getKeepPlayedTracksVisible(), prefs::setKeepPlayedTracksVisible)
    toggle("pref_save_position", prefs.getSavePositionEnabled(), prefs::setSavePositionEnabled)
    toggle("pref_youtube_fallback", prefs.getYouTubeFallbackEnabled(), prefs::setYouTubeFallbackEnabled)
    toggle("pref_precise_speed", player.isPreciseSpeedEnabled, player::togglePreciseSpeedEnabled)
    toggle("pref_local_enable", prefs.getLocalMediaEnabled(), prefs::setLocalMediaEnabled)
    toggle("pref_auto_update", prefs.getAutoUpdateEnabled(), prefs::setAutoUpdateEnabled)
    toggle("pref_verified_filter", prefs.getVerifiedFilterEnabled(), prefs::setVerifiedFilterEnabled)
    toggle("discord_enable_rpc", prefs.getDiscordRpcEnabled(), prefs::setDiscordRpcEnabled)
    if (prefs.getDiscordRpcEnabled()) {
        toggle("discord_listen_along_title", prefs.getDiscordListenAlongEnabled()) {
            prefs.setDiscordListenAlongEnabled(it)
            player.updateDiscordPresence()
        }
        toggle("discord_auto_live_title", prefs.getDiscordAutoLiveEnabled()) {
            prefs.setDiscordAutoLiveEnabled(it)
            player.updateDiscordPresence()
        }
    }
    if (prefs.aiAutoSkip) {
        toggle("block_ai_auto_block", prefs.aiAutoBlock) { prefs.aiAutoBlock = it }
        toggle("block_ai_spare_favorites", prefs.aiSpareFavorites) { prefs.aiSpareFavorites = it }
    }
    toggle("pref_ai_show_human_badge", prefs.aiShowHumanBadge) { prefs.aiShowHumanBadge = it }
}
