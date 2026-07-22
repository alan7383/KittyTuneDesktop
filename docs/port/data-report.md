# KittyTune Data Layer — Porting Spec (Android → Desktop JVM)

App `com.alananasss.kittytune` versionName 2.52.1 (code 35). Backend = SoundCloud private API + YouTube InnerTube + LrcLib + GitHub.

## 0. Cross-cutting Android deps
- Context → AppContext/AppDirs holder.
- SharedPreferences(name, MODE_PRIVATE) → one JSON file per namespace (see §8). Desktop `Prefs` object done.
- Room → SQLite JDBC (recreate schema §1.2).
- filesDir/cacheDir/getExternalFilesDir → AppDirs.
- android.net.Uri + DocumentFile (SAF) → java.io.File/Path.
- MediaMetadataRetriever → jaudiotagger/FFmpeg.
- Media3 ExoPlayer/SimpleCache/HLS/Widevine → custom engine; DRM unportable.
- Gson → keep (pure JVM). Retrofit/OkHttp → keep, only CookieManager+Build swap.
- WebView/CookieManager → JCEF/JavaFX WebView or OkHttp CookieJar (§6).

## 1. data/local/
### AppDatabase — "soundtune_db" v14, fallbackToDestructiveMigration, exportSchema=false.
Entities: LocalTrack, LocalPlaylist, PlaylistTrackCrossRef, HistoryItem, LocalArtist, ListeningStatsEvent, RecognitionHistoryItem.
DAOs: downloadDao, recognitionHistoryDao.

### Tables
**downloaded_tracks** (LocalTrack, PK id:Long): title, artist, artworkUrl, duration:Long, localAudioPath(""=not dl; may be exo_cache://), localArtworkPath, downloadedAt=now.
**downloaded_playlists** (LocalPlaylist, PK id:Long): title, artist, artworkUrl, trackCount:Int, isUserCreated=false, localCoverPath:String?=null, permalinkUrl:String?=null, isAlbum=false, addedAt=now. **Negative ids = local/user; positive = SC server.**
**playlist_track_cross_ref** (composite PK [playlistId,trackId]): playlistId, trackId, addedAt=now (**doubles as sort key**, reorder=rewrite addedAt).
**saved_artists** (LocalArtist, PK id): username, avatarUrl, trackCount, savedAt=now.
**play_history** (HistoryItem, PK id:String e.g. "track:123","playlist:-1","likes","downloads",yt_radio:url): numericId:Long, title, subtitle, imageUrl, type(TRACK/PLAYLIST/STATION/PROFILE), timestamp=now, isVerified=false, source="soundcloud", originalUrl:String?=null. **LIMIT 20.**
**recognition_history** (autoGen id:Long=0): trackId:Long?, title, artist, artworkUrl:String?, timestamp=now.
**listening_stats** (autoGen id:Long=0): trackId, trackTitle, artistName, artistId:Long?, artistPermalink:String?, artistAvatarUrl:String?, artworkUrl, source="soundcloud", eventType, listenDurationMs=0, trackDurationMs=0, timestamp=now.
  eventType ∈ PLAY_COMPLETE,SKIP_NEXT,SKIP_PREVIOUS,MANUAL_REPLAY,REPEAT_ONE_LOOP. Plays = PLAY_COMPLETE+MANUAL_REPLAY+REPEAT_ONE_LOOP. Top-artist SUM(listen)>=60000. Unique-tracks SUM(listen)>3000.
DAO projections: TopTrackResult(trackId,trackTitle,artistName,artworkUrl?,source?,playCount,totalListenMs), TopArtistResult(artistName,artworkUrl?,artistId?,artistPermalink?,source?,playCount,totalListenMs).

DownloadDao: track CRUD, playlist CRUD, cross-refs (ORDER BY addedAt ASC), artists, history (LIMIT 20), stats aggregation queries. All suspend or Flow.
RecognitionHistoryDao: insertItem REPLACE, getAllItems Flow, clearHistory, deleteItem.

### PlayerPreferences — "player_state" prefs + queue_cache.json. Enums: AppThemeMode{SYSTEM,LIGHT,DARK}, PlayerBackgroundStyle{THEME,GRADIENT,BLUR}, StartDestination{HOME,LIBRARY}, LyricsAlignment{LEFT,CENTER,RIGHT}, DiscordStatusDisplay{ACTIVITY,ARTIST,SONG}, AppLanguage(system/fr/en/hu).

FULL PREF TABLE (accessor | key | type | default):
- listeningStatsEnabled | listening_stats_enabled | Bool | true
- last_track_json | String(Track JSON) | null
- last_position | Long | 0
- audio_effects | String(AudioEffectsState JSON) | AudioEffectsState()
- last_context_json | String(PlaybackContext JSON) | null
- lastShuffleEnabled | shuffle_mode_enabled | Bool | false
- lastRepeatMode | repeat_mode_state | String(enum) | NONE
- downloadLocation | download_directory_uri | String? | null
- autoplayEnabled | autoplay_station_enabled | Bool | true
- audioQuality | audio_quality_pref | String | "HIGH"
- persistentQueueEnabled | persistent_queue_enabled | Bool | true
- startDestination | start_destination_pref | String | HOME
- dynamicTheme | dynamic_theme_enabled | Bool | true
- themeMode | app_theme_mode | String | SYSTEM
- pureBlack | pure_black_enabled | Bool | false
- playerStyle | player_background_style | String | BLUR
- localMediaEnabled | local_media_enabled | Bool | false
- localMediaUris | local_media_uris_set_v2 | StringSet | ∅
- lyricsPreferLocal | lyrics_prefer_local | Bool | false
- lyricsAlignment | lyrics_alignment | String | CENTER
- lyricsFontSize | lyrics_font_size | Float | 26f
- appLanguage | app_language_code | String | "system"
- achievementPopupsEnabled | achievement_popups_enabled | Bool | false
- preciseSpeedEnabled | precise_speed_enabled | Bool | false
- autoUpdateEnabled | auto_update_enabled | Bool | true
- youTubeFallbackEnabled | youtube_fallback_enabled | Bool | true
- downloadDrmStreamsEnabled | download_drm_streams_enabled | Bool | true
- showLyricsButtonEnabled | show_lyrics_button_enabled | Bool | true
- inlineLyricsEnabled | inline_lyrics_enabled | Bool | true
- discordToken | discord_token | String? | null
- discordRpcEnabled | discord_rpc_enabled | Bool | false
- preciseLyricsSearchEnabled | precise_lyrics_search_enabled | Bool | true
- hasSeenEarrapeWarning | has_seen_earrape_warning | Bool | false
- discordAssetLogo | discord_asset_logo | String? | null
- discordStatusDisplay | discord_status_display | String | ACTIVITY
- customFontEnabled | custom_font_enabled | Bool | true
- fontWght | font_wght | Int | 400
- fontWdth | font_wdth | Float | 100f
- fontSlnt | font_slnt | Float | 0f
- fontRond | font_rond | Float | 0f
- fontGrad | font_grad | Float | 0f
- fontOpsz | font_opsz | Float | 14f
- syncLikesEnabled | sync_likes_enabled | Bool | true
- crossfadeEnabled | crossfade_enabled | Bool | false
- crossfadeDuration | crossfade_duration | Int(1..12) | 5
- keyColor | key_color | Int(ARGB) | 0
- colorStyle | color_style | String | "System"
- colorSpec | color_spec | String | "SPEC_2025"
- sleepTimerFadeDuration | sleep_timer_fade_duration | Int(0..30) | 30
- sleepTimerFadeEnabled | sleep_timer_fade_enabled | Bool | false
- bottomMenuStyle | bottom_menu_style | String | "modern"
- bottomMenuItems | bottom_menu_items_csv | String(CSV) | "home,search,genres,library"
- bottomMenuFab | bottom_menu_fab | String | "settings"
- bottomMenuBlurEnabled | bottom_menu_blur_enabled | Bool | true
- stopOnTaskClear | stop_on_task_clear | Bool | true
- newPlayerDesignEnabled | new_player_design_enabled | Bool | true
Consts: SLEEP_TIMER_FADE_DURATION_MIN=0,MAX=30,DEFAULT=30,UPDATE_INTERVAL_MS=50L.
savePlaybackState writes queue→queue_cache.json (Gson List<Track>), rest to prefs; clears if persistentQueue off. bottomMenu*Flow uses OnSharedPreferenceChangeListener → use MutableStateFlow.

### ExoCacheManager: SimpleCache cacheDir/exo_offline_cache, 200MB LRU. exo_cache:// scheme meaningless off-Media3.

## 2. Repositories (object singletons, NO DI framework)
Init in MainActivity.onCreate + PlaybackService.onCreate + KittyTuneApp. Each owns CoroutineScope(Dispatchers.IO) (Achievement uses Main → UI dispatcher).

### LikeRepository — "soundtune_likes_v3": liked_tracks_full(JSON List<Track>), liked_playlists_ids(StringSet Long), locally_unliked_ids(StringSet blacklist).
State: likedTracks StateFlow, likedPlaylists StateFlow<Set<Long>>, isSyncing StateFlow. Caches userId from getMe().
API: init, addLike (optimistic+likeTrack, sort likedAt desc), removeLike (blacklist+unlikeTrack), isTrackLiked, isPlaylistLiked, setLikedPlaylists, togglePlaylistLike (URN soundcloud:playlists:/system-playlists:artist-stations:/track-stations:), replaceAllLikes (merge server+local dedupe max likedAt, honor blacklist), setSyncing. 401→SessionManager.requestSessionRefresh(force). Gated by syncLikesEnabled + !isGuestMode.

### HistoryRepository — Room. init, addToHistory(Track), addToHistory(Playlist,isStation,isProfile) (special ids -1L=likes,-2L=downloads,yt_radio:), removeFromHistory, getHistory Flow. Uses R.string → i18n keys.

### ListeningStatsRepository — Room. init, recordEvent(track,eventType,listenMs=0), getTopTracks/Artists(since,limit=10), getTopTracksBetween/getTopArtistsBetween(since,until,limit=1), getTotalListenTime, getEventCount, getTotalEvents, getUniqueTracks, getUniqueArtists, getEvents, clearStats.

### RepostRepository — network-only. repostedTrackIds StateFlow<Set<Long>>. init, clearUser, refreshReposts (paginated getUserReposts limit=200 + next_href), addRepost/removeRepost (optimistic+rollback), syncLocalState.

### LocalMediaRepository — isScanning StateFlow, scanProgress StateFlow<String>. scanLocalMedia walks SAF URIs, finds audio (mp3/flac/wav/m4a/aac/ogg/wma/opus/amr/mp4), MediaMetadataRetriever tags, art→filesDir/local_art_<hash>.jpg, LocalTrack **negative id = -abs(uri.hashCode())**. isLocalTrack = id<0 && id>-9e18. JVM: Files.walk + jaudiotagger.

### AchievementManager — "achievements_prefs". Achievement(id,category,titleResId,descriptionResId,iconEmoji,targetValue,isSecret=false,xpReward=10). Categories TIME,VOLUME,LOYALTY,COLLECTION,PLAYER,HARDCORE,SECRET.
Keys: curr_<id>(Int), unlocked_<id>(Bool), time_<id>(Long). Streak: last_streak_day_of_year, last_streak_year, current_streak_count.
State: isAllUnlocked StateFlow, progressFlow StateFlow<Map<String,AchievementProgress>>.
API: init, resetAll, increment(id,amount=1), addPlayTime(seconds,isGuest,speed), checkDailyStreak, checkTrackNameSecret(title), trackSkipped, resetSessionAchievements, getLevelInfo Triple<level,xpIntoLevel,xpForNext>.
Leveling: level1, xpForNext=1000, each level *=1.2. totalXP = Σ unlocked xpReward.

ALL ACHIEVEMENTS (id·cat·emoji·target·XP·secret):
time_1h TIME 🎧 3600 10; time_24h TIME 🌙 86400 100; time_100h TIME 🔥 360000 500; time_500h TIME ⚡ 1800000 2000; time_1000h TIME 🎖️ 3600000 5000; time_2500h TIME 🧘 9000000 15000; time_5000h TIME 🌌 18000000 50000; time_10000h TIME 🧠 36000000 100000;
plays_1 VOLUME 🎵 1 5; plays_100 VOLUME 💿 100 50; plays_1000 VOLUME 🧭 1000 500; plays_5000 VOLUME 🌊 5000 2000; plays_10000 VOLUME 🤖 10000 5000; plays_20000 VOLUME 👁️ 20000 10000; plays_50000 VOLUME 🔮 50000 25000; plays_100000 VOLUME 👑 100000 100000;
streak_7 LOYALTY 🗓️ 7 100; streak_30 LOYALTY 📅 30 500; streak_100 LOYALTY 💯 100 2000; streak_200 LOYALTY ⚔️ 200 5000; streak_365 LOYALTY 🏆 365 20000; streak_500 LOYALTY 🛡️ 500 50000; early_bird LOYALTY 🌅 10 100; night_owl LOYALTY 🦉 10 100; lunch_break LOYALTY 🍔 10 100;
liker_50 COLLECTION 💖 50 100; liker_1000 COLLECTION 🏆 1000 2000; liker_5000 COLLECTION ♾️ 5000 10000; playlist_creator COLLECTION 💾 5 100; playlist_god COLLECTION 🏗️ 50 2500; download_100 COLLECTION 📦 100 500; download_1000 COLLECTION 🗄️ 1000 5000;
skipper_100 PLAYER ⏭️ 100 100; skipper_1000 PLAYER 🙅 1000 1000; bass_addict PLAYER 🤯 36000 2000; speed_demon PLAYER 🏎️ 3600 500; social_star PLAYER 🌐 50 1000;
obsessed_50 HARDCORE 🔄 50 1000; obsessed_200 HARDCORE 😵‍💫 200 10000; night_shift_pro HARDCORE 🧛 28800 15000; marathon HARDCORE 🏃 28800 5000; no_skip_50 HARDCORE 🧘 50 1000;
developer SECRET 💻 10 1000 (secret); weekend_warrior SECRET 🎉 2 200 (secret); ghost SECRET 👻 86400 5000 (secret); lucky7 SECRET 🎰 7 7777 (secret); glitch SECRET 👾 1 1337 (secret).
Behavior: addPlayTime increments TIME + ghost(if guest) + night_shift_pro(22-06h else reset 0) + marathon + speed_demon(if speed>=1.2). checkDailyStreak day-of-year consecutive, weekend_warrior Sat/Sun, push to all streak_*. checkTrackNameSecret: title has 777/Lucky→lucky7; Error/Glitch→glitch. Session-reset: marathon, no_skip_50. Unlock→AchievementNotificationManager popup (gated achievementPopupsEnabled). JVM: Calendar→java.time, Dispatchers.Main→UI, @StringRes→keys.

### BackupManager — file "SoundTune_Backup_<yyyyMMdd_HHmm>.backup" = Gson FullBackupData:
{version:1, timestamp:Long, localTracks:[LocalTrack], localPlaylists:[LocalPlaylist], playlistRefs:[PlaylistTrackCrossRef], savedArtists:[LocalArtist], history:[HistoryItem≤20], likedTracks:[Track], achievements:{key:val raw dump achievements_prefs}, playerPrefs:{key:val raw dump player_state}, listeningStats:[ListeningStatsEvent]|[]}.
Restore: insert all (stats id=0 re-autogen), replaceAllLikes, restorePrefs CLEARS each pref then re-puts. **restorePrefs coercion (Gson numbers=Double):** key time_* or last_position→Long; font_wdth/slnt/rond/grad/opsz/lyrics_font_size→Float; List→StringSet; Bool/String as-is; other numbers→Int.

### UpdateManager — "update_cache" key last_check_time. UpdateStatus{IDLE,CHECKING,AVAILABLE,DOWNLOADING,READY_TO_INSTALL,ERROR,NO_UPDATE}. checkForUpdate cooldown 15min, compares AppUtils.getAppVersion vs getLatestRelease().tagName dotted isNewerVersion. APK install Android-only → desktop installer. Keep GitHub check+version compare.

### GenreData — SearchCategory(id,title,query,icon:ImageVector). getMoods (12), getGenres (~65 sorted). query = SC search string. icon→desktop icon type, title→R.string.category_*.

### RainPlayer — 2nd ExoPlayer loops res/raw/rain. setVolume/setEnabled/release.

### Others: SessionManager(§6), DownloadManager(§7), RecognitionHistoryRepository (thin Room wrapper), DiscordRPC (app id 1473071817693331540, logo 1473370878195794073), MediaButtonReceiver, PlaybackService (notif 1001, channel soundtune_playback_channel), KittyTuneMediaLibrarySessionCallback (Android Auto, desktop-irrelevant).

## 3. data/network/
### RetrofitClient — create(context):SoundCloudApi, baseUrl Config.BASE_URL. 4 interceptors:
1. cookieInterceptor — Cookie header from WebView CookieManager + DataDome. → OkHttp CookieJar.
2. authInterceptor — token via SessionManager.harvestStoredSession ∥ TokenManager; blocking refresh; client_id OFFICIAL_CLIENT_ID(authed) vs CLIENT_ID(guest); UA "SoundCloud/2025.12.10-release (Android <rel>; <model>)", App-Version:330120, UDID=Config.getOrCreateSoundCloudDeviceId, Authorization:OAuth <token>.
3. sessionRecoveryInterceptor — 401/403 refresh+retry, else guest retry safe GETs (excludes /me,track_likes,playlist_likes,track_reposts,conversations).
4. HttpLoggingInterceptor(BASIC). Timeouts 30s.

### SoundCloudApi — ~90 endpoints (api-v2/api-mobile/api.soundcloud.com + graph.soundcloud.com/graphql). me/profile/charts/stream/search/likes/reposts/follows/playlists CRUD/stations/comments/history/GraphQL. Already ported.
### LrcLibApi (lrclib.net get/search), GithubApi (repos/alan7383/kittytune/releases/latest), LongIdAdapter (string/num→Long). Already ported.

## 4. domain/Models.kt — Gson DTOs. Track(fullResArtwork large→t500x500), Playlist(@JsonAdapter LongIdAdapter), User(numericId from urn), SystemPlaylist(station urn), Media/Transcoding/Format, StreamUrlResponse(url,licenseAuthToken). Track.source default "soundcloud", likedAt:Long?. Already ported.

## 5. Config — "app_config" key dynamic_client_id. FALLBACK_ID="7K3no7iJj8d02d20Z26Z26Z26Z26Z26", OFFICIAL_CLIENT_ID="QOFuKCOeAXIph267vzqj3B1wb65cZVAQ", OFFICIAL_CLIENT_SECRET="EhBDsGIj9EbuBbRf0QkhH9Fq9BX3yN4B", BASE_URL="https://api-v2.soundcloud.com/", OFFICIAL_CLIENT_SIGNATURE=base64url(SHA256("id:secret")). CLIENT_ID mutable persisted. getOrCreateSoundCloudDeviceId ("soundcloud_auth_flow" key soundcloud_device_id) from ANDROID_ID MD5 or random UUID. **Embedded secrets required — carry over.**

## 6. SessionManager — hardest port (WebView auth). Headless WebView logs into m.soundcloud.com, harvests oauth_token/refresh_token, snags client_id, solves DataDome CAPTCHA. Direct OAuth refresh_token grant → api-auth.soundcloud.com/oauth/token (pure OkHttp, portable). Refresh 20min, timeout 12s, "soundcloud_datadome" key datadome_cookie. Surface: harvestStoredSession, requestSessionRefresh(force), refreshSessionBlocking, awaitFreshAccessToken, awaitDataDomeChallenge, extractDataDomeCaptchaUrl, getStoredDataDomeCookie, flows isClientIdValid/sessionReadyEvent/showCaptchaFlow. JVM: JCEF/JavaFX WebView for login+captcha, pure-OkHttp refresh for the rest.

## 7. DownloadManager — Semaphore(4). downloadProgress Map<Long,Int>, playlistDownloadProgress Map<Long,Float>, storageTrigger, libraryUpdated SharedFlow, deletedPlaylistIds, downloadedIds StateFlow. LIKES_BATCH_ID=-1L. Playlists CRUD (Room + SC sync), tracks add/remove/reorder (addedAt), artists. Download: non-HLS 3-chunk ranged HTTP → cacheDir → mp3agic ID3v24 tagging embedded cover, YouTube→.m4a. HLS/DRM→HlsDownloader+ExoCache+Widevine (unportable). art→filesDir/art_<id>.jpg, playlist cover filesDir/playlist_cover_<id>.jpg.

## 8. Storage layout (JVM)
Pref namespaces → one file each: player_state, soundtune_likes_v3, achievements_prefs, update_cache, soundcloud_datadome, app_config, soundcloud_auth_flow.
DB: soundtune_db SQLite 7 tables.
Files: queue_cache.json, art_<id>.jpg, local_art_<hash>.jpg, playlist_cover_<id>.jpg.
Cache: exo_offline_cache/, temp download files.
Freeze JSON: backup, queue_cache.json (List<Track>), liked tracks (List<Track>), audio_effects (AudioEffectsState), last_context (PlaybackContext).

**Port risk ranking: (1) SessionManager WebView+DataDome; (2) Widevine/HLS downloads; (3) RetrofitClient WebView-cookie interceptors; (4) SAF→filesystem; (5) Media3 playback/cache. Rest = mechanical.**
