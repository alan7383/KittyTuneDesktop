# KittyTune — UI Layer Technical Report (for Compose for Desktop / Material 3 port)

Source root: `app/src/main/java/com/alananasss/kittytune/ui` (~32,800 LOC) + `MainActivity.kt`, `KittyTuneApp.kt`. Material 3 version: **1.5.0-alpha22** (Compose BOM 2026.06.00) — the app leans heavily on **Material 3 Expressive** APIs (`MaterialExpressiveTheme`, `MotionScheme.expressive()`, `ButtonDefaults.shapes()`, `IconButtonDefaults.shapes()`, wavy progress indicators, `HorizontalFloatingToolbar`).

---

## 1. Complete Screen Inventory

### App shell
| Component | File | Notes |
|---|---|---|
| `MainActivity` | `MainActivity.kt` | Single activity; edge-to-edge transparent bars; SharedPreferences listener rebuilding `ThemeState` on theme-key changes (`dynamic_theme_enabled`, `app_theme_mode`, `pure_black_enabled`, `custom_font_enabled`, `font_*`, `key_color`, `color_style`, `color_spec`); handles `open_search` intent extra (SearchWidget) and SoundCloud OAuth `?code=` redirect; ON_RESUME session/likes/reposts sync + daily achievement streak; auto-update check; wraps everything in `SoundTuneTheme { Surface { MainScreen() } }`. |
| `KittyTuneApp` | `KittyTuneApp.kt` | Trivial `Application`: `Config.init`, YouTube locale (US/en), static instance. |
| `MainScreen` | `ui/MainScreen.kt` (1334) | NavHost + bottom bar + expanded-player overlay + lyrics overlay + all PlayerViewModel-driven ModalBottomSheets (menu/add-to-playlist/details/comments/profile-menu) + achievement popups + UltimateCompletionOverlay + UpdateScreen dialog + ghost/captcha WebViews + pill snackbar (animated bottom padding 16→90dp with mini player). |

### Home / Search area
| Screen | File | ViewModel | Content & interactions |
|---|---|---|---|
| **HomeScreen** | `home/HomeScreen.kt` (1848) | `HomeViewModel` (1141) | M3 `SearchBar` (docked, expands to full search mode — **search is a mode of Home, no separate SearchScreen**); avatar/recognition buttons in trailing icons; animated offline banner (errorContainer); live `ConnectivityManager` network callback; `PullToRefreshBox` with custom `ContainedLoadingIndicator` indicator + content translation/scale parallax; content = `LazyColumn` of dynamic `HomeSection`s (`SectionType`: TRACKS_ROW, STATIONS_ROW, ARTISTS_ROW, HIGHLIGHT_ROW, DISCOVERY_ROW) with fixed ordering (filter chips row → Recently played `HistoryCard`s → habits → rediscovery → stations → albums → similar → discovery carousel → recommended → rest → `ExplorerSection`); `DiscoverySectionCarousel` = HorizontalPager of full-bleed 320dp cards with scale/alpha page-offset lerp; `ExplorerSection` = 3 tonal square buttons (New releases / Charts / Moods & genres); `StaggeredItem` entrance animation (30ms/index stagger, alpha+translateY+scale springs, skipped past index 8 or while scrolling); `HomeScreenShimmer` skeleton. Personalized category chips (fallback: first 10 moods). |
| **Search mode (in Home)** | same | same | See §6. |
| **GenresScreen** ("Explore" tab, route `genres`) | `home/GenresScreen.kt` | `GenresViewModel` | Moods carousel + genre grid of `SearchCategoryCard`s → `genre_detail/{name}/{query}` or `genre_playlists/...`. |
| **GenreDetailScreen** | `home/GenreDetailScreen.kt` | `GenreDetailViewModel` | Popular tracks list + "official playlists" (country selector `ModalBottomSheet` with flag emoji cards) + link to community playlists. |
| **GenrePlaylistsScreen** | `home/GenrePlaylistsScreen.kt` | `GenrePlaylistsViewModel` | Grid/list of `CinematicPlaylistCard`s for a mood/genre query. |
| **ChartsScreen** | `home/ChartsScreen.kt` (612) | `ChartsViewModel` | Country charts (static `ChartsData.charts` with flag emojis); country selector ModalBottomSheet; artist rank rows with menu options; chart playlist cards. **No graph/chart drawing — "charts" = music top lists.** |
| **NewReleasesScreen** | `home/NewReleasesScreen.kt` | `NewReleasesViewModel` | Popular track rows + new-release playlist cards. |
| **TagScreen** (route `tag/{tagName}`) | `home/TagScreen.kt` | `TagViewModel` | `TabRow` (tracks/playlists) results for a `#tag`, staggered items. |

### Library area
| Screen | File | ViewModel | Content |
|---|---|---|---|
| **LibraryScreen** | `library/LibraryScreen.kt` (726) | `LibraryViewModel` | Search field header w/ avatar; offline (errorContainer) + guest (primaryContainer, tap→login) banners; `FilterChipsRow` (Playlists / Albums / Artists / Stations); sort + grid⇄list layout toggle (`SortAndLayoutControls`); `LazyVerticalGrid` with static cards (Liked tracks — w/ syncing subtitle, Downloads, Local media if enabled) + `DynamicPlaylistCard` / `ArtistLibraryCard`; `ExtendedFloatingActionButton` "Create playlist" (collapses on scroll, offset above bottom bar+mini player) → create dialog (polls API up to 15×1s until playlist resolvable, then navigates); shimmer grid; network callback auto-reload; ON_RESUME refresh. |
| **PlaylistDetailScreen** (route `playlist_detail/{playlistId}`) | `library/PlaylistDetailScreen.kt` (1678) | `PlaylistInfoViewModel`/`PlaylistInfoFullViewModel`/`YoutubeRadioViewModel` | The universal track-list screen. Special ids: `likes` (paginated user likes, LIKES_BATCH_ID download batch), `downloads` (incl. downloaded-playlists grid), `local_files`, `yt_radio:<url>` (infinite YouTube radio, loads more 5-from-end), `station:` / `station_artist:` / `liked_by:` / `system_playlist:`. Blurred cover header (blur 100.dp, alpha 0.6) inside a ModalBottomSheet detail; play/shuffle; per-track download progress; drag-reorder via `sh.calvin.reorderable` for user-owned playlists; `PlaylistOptionsSheet`; `TrackListItem` (shared, also used by Home search results); `EmptyPlaylistView` with kaomoji empty states; `PlaybackContext` set per source ("Playing from likes/downloads/station X"). |
| **PlaylistDetailsSheet** | `library/PlaylistDetailsSheet.kt` (472) | — | Info sheet: artwork, stats, `UserSection` avatar rows, `ExpandableDescription`. |
| **EditPlaylistScreen** | `library/EditPlaylistScreen.kt` | — | Title/description editing + two `ExposedDropdownMenu`s (privacy, type/genre). |
| **PlaylistFansScreen** (route `playlist_fans/{id}?tab=`) | `library/PlaylistFansScreen.kt` | — | `TabRow` + pager: likers / reposters of a playlist. |

### Player area — see §4.

### Profile / Social area
| Screen | File | ViewModel | Content |
|---|---|---|---|
| **ProfileScreen** (route `profile/{userId}`) | `profile/ProfileScreen.kt` (1340) | `ProfileViewModel` (492) | `ModernProfileHeader`: banner (bannerUrl ?: avatar) background, avatar, follower/following counts (clickable → `followers:`/`followings:`), verified badge; own profile: Edit button → `EditProfileSheet` (ModalBottomSheet: banner picker + `BannerCropDialog`, avatar picker + `AvatarCropDialog` — both pan/zoom `detectTransformGestures` croppers with Canvas overlay masks — name/city/bio fields, delete-banner confirm); other profile: follow/unfollow heart. Sections: Popular tracks, Latest tracks, Albums, Reposts, Comments, Similar artists — each with "more" → in-screen `FullListScreen`/`FullCommentListScreen` sub-pages (own TopAppBars). `ArtistAvatar` (fallback Person icon), `SquareCard`, `ExpandableDescription` w/ link+@mention handling. `ProfileScreenShimmer`. |
| **UserListScreen** (routes `followers/{id}`, `followings/{id}`) | `profile/UserListScreen.kt` | `UserListViewModel` | LargeTopAppBar + `UserRow` list. |
| **NotificationsScreen** | `profile/NotificationsScreen.kt` | `NotificationsViewModel` | LargeTopAppBar; `NotificationItemCard`s of SoundCloud activity (likes/reposts/follows/comments) → navigation. |
| **ConversationsScreen** | `profile/ConversationsScreen.kt` | `ConversationsViewModel` | DM conversation list cards. |
| **ChatScreen** (route `chat/{convId}/{otherUserId}/{username}`) | `profile/ChatScreen.kt` (546) | `ChatViewModel` | CenterAlignedTopAppBar; message bubbles (rotated-tail via graphicsLayer), `SoundCloudPreviewCard` for shared track links, `ChatInputBar`; bottom bar hidden on this route. |
| **AchievementsScreen** (route `achievements`) | `profile/AchievementsScreen.kt` (550) | — (AchievementManager) | LargeTopAppBar, `LevelProgressCard` (level/XP progress), category `PaddingTitle`s, `AchievementTile`s w/ progress, `SecretAchievementTile`. |
| **ListeningStatsScreen** (route `listening_stats`) | `profile/ListeningStatsScreen.kt` (1023) | `ListeningStatsViewModel` | `SingleChoiceSegmentedButtonRow` period selector (WEEK / MONTH / ALL_TIME); `HeroStatsCard` (time listened + `MiniStatChip`s: plays, unique tracks, unique artists); Top tracks / Top artists cards; `HabitsGrid` of `HabitCard`s (manual replays, skip rate, completion rate, repeat loops, avg listen, total sessions); insights section; empty state. **No drawn charts — stat cards/chips only** (BarChart is just an icon). |
| **ProfileMenuSheet** | `profile/ProfileMenuSheet.kt` | — | ModalBottomSheet menu: view profile / notifications / messages / achievements / listening stats / settings / logout-or-login. |

### Recognition
| Screen | File | Content |
|---|---|---|
| **RecognitionScreen** (route `recognition`) | `recognition/RecognitionScreen.kt` (577) | Shazam-style: RECORD_AUDIO runtime permission; state machine Idle → Listening (pulse animation) → Processing (rotation) → Success (`shazamResult` + resolved SoundCloud track w/ artwork, play/save actions) / Error(retry); animated background color; **`GlowView`** at bottom — AGSL RuntimeShader animated simplex-noise glow (API 33+, silently absent below). Bottom bar hidden. |
| **RecognitionHistoryScreen** | `recognition/RecognitionHistoryScreen.kt` | History rows of past recognitions. |

### Login / Onboarding
| Screen | File | Content |
|---|---|---|
| **WelcomeScreen** (route `welcome`, start dest when logged out) | `login/WelcomeScreen.kt` (414) | Header art, notification-permission card, buttons: SoundCloud login / **continue as guest** (guest loading state, 8s fallback timer in MainScreen). |
| **LoginScreen** (route `login`) | `login/LoginScreen.kt` (378) | SoundCloud OAuth in **WebView**; token exchange (`/oauth/token`, fallback base URL), cookie injection (`oauth_token`, `refresh_token`), `TokenManager` persistence. |

### Settings — see §5.

### Misc / special — see §7.

---

## 2. Navigation Structure

- **String-route navigation-compose**, single `NavHost` in `MainScreen`. All destinations registered via custom **`clippedComposable`** (`navigation/AnimatedNavUtils.kt`): wraps `composable` in `ClippedScreen`, which animates the clip **corner radius = physical device screen corner radius** (queried via `WindowManager.currentWindowMetrics.windowInsets.getRoundedCorner`, API 31+, fallback 28.dp) — enter keyframes: deviceCorner@0ms → deviceCorner@300ms → 0.dp@400ms; exit: tween(50) back. *Android-only; on desktop use a fixed radius or drop.*
- **NavHost transitions**: enter `slideInHorizontally { it }`; exit `slideOutHorizontally { -it/4 } + fadeOut`; popEnter `slideInHorizontally { -it/4 } + fadeIn`; popExit `scaleOut(0.9f) + fadeOut`.
- **Start destination**: `welcome` if no token && not guest; else `home` or `library` per `prefs.getStartDestination()`.
- **Full route table**: `welcome`, `home`, `library`, `login`, `expanded_queue`, `genres`, `genre_detail/{genreName}/{genreQuery}`, `charts`, `new_releases`, `playlist_detail/{playlistId}` (string arg; magic values `likes`, `downloads`, `local_files`, `yt_radio:<enc-url>`, `station:*`, `station_artist:*`, `liked_by:*`, `system_playlist:*`, `local_playlist:<negId>`), `genre_playlists/{genreTitle}/{query}`, `profile/{userId}`, `followers/{userId}`, `followings/{userId}`, `tag/{tagName}`, `track_detail/{trackId}?tab={tabIndex}` (Long+Int), `playlist_fans/{playlistId}?tab={tabIndex}`, `notifications`, `conversations`, `chat/{conversationId}/{otherUserId}/{username}`, `achievements`, `listening_stats`, `recognition`, `recognition_history`, `settings`, `backup_restore`, `audio_settings`, `drm_explanation`, `lyrics_settings`, `local_media_settings`, `appearance_settings`, `bottom_bar_settings`, `fab_settings`, `color_palette`, `about`, `licenses`, `storage`, `discord_settings`, `discord_login`.
- **`Screen` sealed class** (`navigation/Screen.kt`): Welcome, Home, Library, Search, Explore("genres"), Login, Recognition, RecognitionHistory — with titleResId + icon for tabs.
- **Bottom bar** (`KittyUnifiedBottomBar`): tab keys `["home","search","genres","library"]`; per-tab visibility from `prefs.bottomMenuItemsFlow()`. **"Search" is a virtual tab** — navigates Home and calls `homeViewModel.activateSearch()`; tapping Home while searching clears search. Bar hidden on login/welcome/update/chat/recognition and while player expanded. Two styles (§4). Configurable FAB (modern style) from `prefs.bottomMenuFabFlow()`: `profile`(default sheet)/`settings`/`recognition`/`achievements`/`stats`/`liked`/`downloads`/`local`/`playlist:<id>`.
- **Nested nav**: none — flat graph; "sub-pages" of Profile are internal composable state (`expandedSection`).
- **Overlays, not routes**: expanded player (`AnimatedVisibility`, slideInVertically 400ms / out 350ms), lyrics sheet (full-screen zIndex 10 with blurred artwork bg + keep-screen-on), all ModalBottomSheets, UpdateScreen (full-size `Dialog`), achievement popups, UltimateCompletionOverlay, captcha WebViews.
- **Deep links**: none registered on the graph. External entry points: `open_search` intent extra (SearchWidget) and SoundCloud OAuth redirect `?code=` → `AuthFlowManager.setAuthCode`. In-app "deep linking" happens via `PlayerViewModel.navigateToPlaylistId` string channel with prefixes `expanded_queue` / `profile:` / `tag:` / `track_detail:` / else playlist id.

---

## 3. Theme System

### `theme/Theme.kt` (complete behavior)
- `SoundTuneTheme(themeMode, dynamicColor, pureBlack, keyColor, colorStyle, colorSpec, typography)`; `AppThemeMode { SYSTEM, LIGHT, DARK }`.
- Default seed: `KittyTuneDefaultSeedColor = Color(0xFFFF7A1A)` (KittyTune orange).
- Scheme resolution:
  1. `colorStyle == "System" && dynamicColor && keyColor == 0 && SDK ≥ S` → platform `dynamicDarkColorScheme`/`dynamicLightColorScheme` (Monet). *Android-only — desktop fallback needed.*
  2. Otherwise → **materialkolor** `rememberDynamicColorScheme(seedColor, isDark, isAmoled = pureBlack, style = PaletteStyle (parse of pref, fallback `Expressive`), specVersion = SPEC_2021|SPEC_2025, platform = DynamicScheme.Platform.PHONE)`. Seed = `Color(keyColor)` if non-zero, else platform dynamic primary (SDK ≥ S) else orange. materialkolor is **multiplatform** → works on desktop directly.
- `ThemeState.previewKeyColor` (global `mutableStateOf<Int?>`) overrides seed live during color-picker dragging.
- **AMOLED** `withAmoledSurfaces()` (dark+pureBlack): background/surface/surfaceContainerLow/surfaceContainer → `Black`; surfaceContainerHigh → `0xFF121212`; surfaceContainerHighest → `0xFF181818`.
- Renders `MaterialExpressiveTheme(colorScheme, typography, motionScheme = MotionScheme.expressive())`. SideEffect sets transparent system bars + light/dark icons (*Android-only*).

### `theme/Color.kt`
Only the unused template constants (Purple80/PurpleGrey80/Pink80/Purple40/PurpleGrey40/Pink40). **The entire real palette is generated** — nothing else to port.

### Typography (`theme/Type.kt`)
- Default `Typography()` (M3 defaults) when custom font off.
- `getDynamicTypography(useCustomFont, wght, wdth, slnt, rond, grad, opsz)`: builds two `FontFamily`s from **variable font `R.font.google_sans_flex`** via `Font(resId, variationSettings = FontVariation.Settings(wght, wdth, slnt, ROND, GRAD, opsz))` — a "rounded" family (ROND forced 100) for display/headline/title styles, standard for body/label. Defaults: wght 400, wdth 100, slnt 0, rond 0, grad 0, opsz 14. *Desktop: `Font(File/resource)` with variation settings is supported in Compose 1.6+ (Skia), keep the ttf.*

### ColorPaletteScreen (route `color_palette`, 853 LOC) — the palette editor
- **ThemePreviewCard**: 200dp phone mockup rendered from a locally computed `rememberSoundTuneColorScheme(...)`, all 9 sampled colors animated with `animateColorAsState`.
- **SeedPaletteCard**: 6 grouped preset categories (Expressive `Button` chips w/ haptic): Deep blues (10 seeds incl. Deep Blue 0xFF141A4C, Abyss 0x0A0E29, Navy 0x0D47A1…), Blues & cyans (5), Greens & teals (6), Sunset warm (6, incl. Kitty Orange 0xFFFF7A1A), Pinks & purples (8), Neutrals (5) — 40 named seeds total, each a `ColorButtonMaterial` circle drawn as 3 Canvas arcs (seed + 2 HSV-derived tints), selection ring + Android-16-style check badge; plus an "auto" swatch (keyColor=0) drawn from the actual generated scheme (primary/secondary/tertiary arcs).
- **CustomSeedPickerCard**: 72dp swatch + hex `OutlinedTextField` (#RRGGBB/#AARRGGBB parse) + 3 gradient-track `PickerSlider`s (Hue 0–360 rainbow, Saturation, Brightness — transparent M3 Slider over a gradient bar); realtime updates go through `ThemeState.previewKeyColor`, commit on release.
- **ColorGenerationCard**: dropdown rows for **Style** (`"System"` + all `PaletteStyle.entries`: TonalSpot, Neutral, Vibrant, Expressive, Rainbow, FruitSalad, Monochrome, Fidelity, Content, …) and **Spec** (`MaterialKolorColorSpecOptions` = SPEC_2021 / SPEC_2025).
- Uses `android.graphics.Color.colorToHSV/HSVToColor` (*replace with java.awt.Color.RGBtoHSB or manual HSV on desktop*).

---

## 4. Player UI (complete)

### Mini player (`player/MiniPlayer.kt`)
64dp bar, top corners 16dp, `surfaceContainer`, click → expand. 48dp artwork (corner 8), `PremiumMarqueeText` title + artist, play/pause + next buttons, bottom 2dp `LinearProgressIndicator` (transparent track) with smart `Animatable`: track change → animate to 0 (280ms FastOutSlowIn) then to target (500ms linear); backward/large jump → 150ms; steady state → 1000ms linear.

### Expanded player (`player/PlayerScreen.kt`, 2879 LOC) — overlay, not a route
Two designs switched live on pref `new_player_design_enabled`:

**Shared elements (both designs)**
- **Backgrounds** (`PlayerBackgroundStyle` pref): `BLUR` — artwork `Crossfade`(1s) with `blur(80.dp)`, alpha 0.6 over black, white content color; `GRADIENT` — vertical gradient from `viewModel.backgroundColor` (extracted per-track, animated 1s); `THEME` — plain scheme colors.
- **Artwork pager**: `HorizontalPager` over the whole queue (settled page → `skipToQueueItem`), pageSpacing 16, contentPadding 24, aspectRatio 1, corner 20dp, shadow 24dp.
- `AnimatedContent` artwork ⇄ **inline lyrics** (400ms fade) — inline `SyncedLyricsView`/`PlainLyricsView` variants with WrongLyricsButton; keep-screen-on while shown.
- `PremiumMarqueeText`: custom SubcomposeLayout marquee — RTL support, edge fade via `BlendMode.DstOut` gradient, Animatable loop (2s start delay, 30dp/s).
- Animated like button (haptic CLOCK_TICK, scale/fade heart), verified badge, lyrics toggle button (gated on `hasLyrics && show_lyrics_button_enabled`).
- `PlayerHeader`: close chevron, "Playing now" + clickable playback-context marquee (`navigateToContext`), overflow menu.
- **Progress bar** (`PlayerProgress`): Animatable, lastValidDuration fallback 180000; track-change transition flag (1500ms, snap to 0); reset tween 600 / jump>2s tween 300 / else linear 1000ms; drag scrubbing w/ CLOCK_TICK haptics + `updateScrubPosition`, seek on release; `makeTimeString` labels.
- `SleepTimerDialog`: slider 5–120min (22 steps, haptics), stop-time preview, chips 15/30/45/60/90, end-of-track chip, custom input, active banner + cancel.

**New design (`NewPlayerScreen`) controls** — Metrolist-style:
- Main row: three weighted pills in a full-width Row, 68dp tall, `RoundedCornerShape(50)`: prev (weight 0.45→0.65 pressed), play/pause (weight 1.3→1.9 pressed, spring damping 0.6/stiffness 500, animated main color bg, icon + "Play"/"Pause" label, `LoadingIndicator` while loading), next. Play-icon content color by luminance (>0.4 → 0xFF1D1B20 else White).
- Secondary row: two split-pill pairs (asymmetric corners 50dp outer / 3dp inner), `FilledIconButton`s 42dp — left: effects (Equalizer) + shuffle; right: repeat (Repeat/RepeatOne) + queue.

**Old design (`OldPlayerScreen`)**: centered row [effects | prev 48dp | **morphing play pill** (width 72↔110dp when playing, spring MediumBouncy/StiffnessLow, CircleShape) | next | queue]; effects and queue open in `ModalBottomSheet`s.

**Audio effects dock (`AudioControlDock`)** — UI for `AudioEffectsState`:
speed slider 0.5–2.0 (14 steps, 29 if precise-speed pref), pitch-preserve toggle chip; "Special effects" FlowRow of `FxTile`s (FilledTonalButton 84dp, `ButtonDefaults.shapes()`, animated colors + bouncy icon scale 1.2): **Bass Boost** (Bolt), **8D** (SurroundSound), **Muffled** (BlurOn), **Reverb** (GraphicEq), **Rain** (WaterDrop, custom blue). Tap toggles; **custom long-press** (interactionSource + `ViewConfiguration.getLongPressTimeout()`) opens per-effect intensity AlertDialog (0–1 slider). Bass dialog contains **Earrape** button gated behind a warning AlertDialog whose OK is disabled for a 5s countdown (`hasSeenEarrapeWarning` pref).

**Menu sheet (`MenuSheetContent`)**: 3-column `LazyVerticalGrid` of dock items — shuffle/repeat (player context), play next / add to queue, comments, repost (RepostDialog: 140-char caption + delete confirm), details, lyrics, add to playlist, go to artist, track radio (YT vs SC), share, remove from playlist (local), sleep timer, download (with `CircularWavyProgressIndicator` progress / cancel / delete). Special-cases local files and `source=="youtube"`.

**Comments sheet**: full Scaffold-in-sheet; sort dropdown (`CommentSort` enum); nested replies (56dp indent); per-comment avatar/verified/relative-time/timestamp-seek-chip (`playTrackAtPosition`)/like (pink)/reply/delete-own(confirm); guest-mode disabled inputs + Toasts; pagination via `commentNextHref`; `ContainedLoadingIndicator`; imePadding input with ImeAction.Send.

**Details sheet**: local mode = file format/bitrate(computed)/size/duration + monospace location card (path cleanup, contentResolver resolution); remote mode = stats row (plays/likes/reposts → track_detail tabs), see-similar, comments button, release date, genre, `ExpandableDescription` (URL + @mention ClickableText spans), tag `AssistChip`s → `tag/` route.

### Queue
- **QueueContent** (sheet, 70% height): reorderable with drag handles + haptics, current-track highlight, expand button → route `expanded_queue`.
- **ExpandedQueueScreen** (`player/ExpandedQueueScreen.kt`): Scaffold + CenterAlignedTopAppBar (shuffle/repeat actions); auto-scroll to current−2; `ReorderableItem` rows (72dp, artwork 56dp) — drag: LONG_PRESS/GESTURE_END haptics, SEGMENT_FREQUENT_TICK per move, 8dp drag elevation; repeat-ONE dims other rows to alpha 0.3; **custom `SwipeToDeleteItem`**: manual `awaitEachGesture` horizontal-lock (3px threshold) + `VelocityTracker`, clamp −200dp..0, delete at ≥120dp or fling < −1000 (animates off-screen), errorContainer + Delete icon fade-in.

### Lyrics (`player/lyrics/`)
- **Format**: **LINE-synced LRC only** (`[mm:ss.xx]`), **no word-by-word**. `LyricLine(text, startTime, endTime)`; `parseLrc` (2–3-digit ms; endTime = next start ?: total). Sources: local ID3v2 USLT via **mp3agic** (`extractLocalLyrics`), online **LrcLib** search (`LrcLibResponse` with `syncedLyrics`/plain; manual search UI; precise-search pref).
- **LyricsScreen** (full-screen overlay over blurred artwork, MainScreen adds blur-80 bg + 0.6 scrim + FLAG_KEEP_SCREEN_ON): white top bar (Close / Tune=offset controls, primary-tinted when offset≠0 / Search); `LyricsModeSelector` floating pill (SYNCED/PLAIN) shown when both exist; **SyncedLyricsView** = karaoke-centered LazyColumn (contentPadding half-height), active line scale 1.05/alpha 1 vs 0.95/0.5 + 1.dp blur on inactive, tap-to-seek, auto `animateScrollToItem` unless user-scrolling, font size (12–48sp pref) & alignment (LEFT/CENTER/RIGHT pref), fading edges; `LyricsOffsetControls` panel: ±0.1s `RepeatingIconButton`s (press-hold auto-repeat: 400ms delay then 100ms) + RESET, "+X.Xs" display; **PlainLyricsView** with copy-all FAB (`LocalClipboardManager` — portable); `EmptyLyricsState` → manual search; `SearchLyricsView`: query field, `LinearWavyProgressIndicator`, LrcLib result cards (green Timer icon = synced available), tap to select.

### Bottom bar (`navigation/KittyUnifiedBottomBar.kt`)
- **classic**: full-width mini-player bar + standard M3 `NavigationBar`/`NavigationBarItem`s (secondaryContainer indicator).
- **modern**: floating mini-player card + **`HorizontalFloatingToolbar(expanded=true)`** (≤480dp, surfaceContainer) with `FloatingToolbarDefaults.VibrantFloatingActionButton` (tertiaryContainer) and custom animated pill tab chips (selected: secondaryContainer + icon+label `animateContentSize`; unselected icon-only; `expand/shrinkHorizontally` for visibility).

### Artwork/scrim effects
- `modifiers/ProgressiveBlurModifier.kt`: `Modifier.progressiveBlur(radius, height, TOP|BOTTOM)` — **AGSL RuntimeShader** (dithered 4-sample gaussian, `pow(progress,1.5)` easing) applied as `RenderEffect` **only on API 33+**; always draws a `surfaceContainer(0.65)→transparent` gradient overlay as fallback/companion. Applied to NavHost top (40f, statusBar×1.15) always, and bottom (150dp) when modern bar + blur pref. *Desktop: reimplement in SkSL (Skia `RuntimeEffect`) or ship gradient-only.*
- Player blur backdrop uses standard `Modifier.blur` (portable).

---

## 5. Settings — every setting

Shared components (`common/SettingsComponents.kt`): `SettingsScaffold` (LargeTopAppBar exitUntilCollapsed, FilledTonalIconButton back), `SettingsGroup`/`SettingsGroupTitle`, `SettingsItem` (76dp card, surfaceContainerHigh, optional icon-in-circle / subtitle / trailingText / M3 Switch with ✓/✕ thumb icons / slider), `SplitSettingsItem` (click zone | divider | switch), `getSettingsShape(size,index)` — grouped-corner shapes 24dp outer / 4dp inner (the "expressive settings" look).

### SettingsScreen (hub, route `settings`)
Pure navigation list: **Appearance** → `appearance_settings`; **Lyrics** → `lyrics_settings`; **Audio** → `audio_settings`; **Discord** → `discord_settings`; **Local media** → `local_media_settings`; **Storage** → `storage`; **Backup** → `backup_restore`; **About** → `about`.

### AppearanceSettingsScreen
- **Theme selector**: 3 `FilledTonalIconToggleButton`s (System/Light/Dark) with the "ReVanced effect" — `IconToggleButtonShapes(shape=Circle, pressedShape=Rounded16, checkedShape=Rounded16)`.
- **Language** (dialog, restarts app): System / French / English / Hungarian.
- **Dynamic theme** (switch — Monet/dynamic color).
- **Pure black (AMOLED)** (switch; only visible when effective theme is dark, AnimatedVisibility).
- **Color palette** → `color_palette` (see §3).
- **Custom font** (switch) + (when on) **Font variations** dialog: presets (Default 400/100/0/0/0/14, Rounded 600+ROND100, Elegant 250/105, Chunky 900/110/ROND50) + sliders Weight 100–1000, Width 25–151, Slant −10–0, Roundness 0–100; Reset.
- **Bottom menu** → `bottom_bar_settings`.
- **Start screen** (dialog): Home / Library.
- **Auto-update** (switch).
- **Achievement popups** (switch).
- **New player design** (switch).
- **Player style** (dialog): Theme / Gradient / Blur.

### BottomBarSettingsScreen
- **Style** (dialog): modern / classic.
- (modern only) **FAB action** → `fab_settings`; **Blur** behind bar (switch).
- **Tabs**: 4 switches (Home / Search / Explore / Library) — at least one must stay on, order preserved.

### FabSettingsScreen
Single-choice list (check icon): profile (default) / settings / recognition / achievements / stats / liked / downloads / local (if local media enabled) / **any user playlist** (rows w/ artwork; `playlist:<id>`, `playlist:local_playlist:<negId>`, or encoded `yt_radio:` radio shortcut); hint footer.

### LyricsSettingsScreen
- **Prefer local lyrics** (ID3 USLT) (switch).
- **Precise lyrics search** (`playerViewModel.togglePreciseLyricsSearch`) (switch).
- **Show lyrics button** in player (switch) + (when on) **Inline lyrics** in player (switch).
- **Alignment** (dialog): left / center / right.
- **Font size** (Dialog with ± buttons + slider 12–48sp, 17 steps, reset to 26).

### AudioSettingsScreen (route `audio_settings`)
**Covered by another agent — exists** (249 LOC, `profile/AudioSettingsScreen.kt`, plus related `player/audio/AudioProcessors.kt` and `drm_explanation` route/`DrmExplanationScreen` linked from it). Not detailed here.

### LocalMediaSettingsScreen
- **Enable local media** (switch); when on: **Add folder** (SAF `OpenDocumentTree` + persistable URI permission — *desktop: JFileChooser/FileKit*), folder list w/ animated delete, **Scan** button (LoadingIndicator + progress text from `LocalMediaRepository.isScanning/scanProgress`), info footer.

### StorageScreen
- `DetailedStorageGauge`: animated stacked horizontal bar + legend for Audio/Images/Cache/Database + free space; total headline.
- **Location**: current path card; change (folder picker) / reset-to-default (when external).
- Per-category rows with delete-confirm dialogs: **Clean audio**, **Clean images**, **Clean cache**; Database row (not deletable).

### BackupRestoreScreen
- **Backup** (SAF CreateDocument, `BackupManager.getBackupFileName()`), **Restore** (OpenDocument `*/*`); info cards (incl. guest note); status message; full-screen loading overlay.

### DiscordSettingsScreen (+ DiscordLoginScreen)
- Connection status card (connect → `discord_login`); **Logout**.
- (logged in) **Enable Rich Presence** (switch, animated corner morph) + **Status display** (dialog: Activity / Artist / Song) — pokes `PlaybackService.ACTION_FORCE_UPDATE`.
- `DiscordLoginScreen`: **WebView** to Discord login; extracts token via injected JS iframe/localStorage snippet + `onJsAlert` intercept. *Android-only; desktop needs JCEF/manual token.*

### AboutScreen (425)
Version header; **Check for update** (Toast on none/error); contributor cards (GitHub links); GitHub / Bug report links (`uriHandler`); **Licenses** → `licenses`; translate CTA; "made with ♥"; `ExpandableTechInfo` (package/build info); contributors ModalBottomSheet.

### LicensesScreen (405)
LargeTopAppBar + custom search bar + expressive library cards (`LibraryItemExpressive`) of OSS licenses.

---

## 6. Search

- Lives inside **HomeScreen/HomeViewModel** (`activateSearch()` / `isSearching` / `clearSearch()`, `searchTrigger` for focus retrigger; BackHandler: first close keyboard, then exit search).
- **Sources** (`SearchSource { SOUNDCLOUD, YOUTUBE }`): `SearchSourceSelector` = FilledTonalIconButton (CloudQueue vs SmartDisplay icon) + DropdownMenu. YouTube search via `zionhuang innertube` `YouTube.search(query, FILTER_VIDEO)`, results mapped to `Track(source="youtube", permalinkUrl="https://youtube.com/watch?v=…")`.
- **Filters** (SoundCloud only, hidden for YouTube): `SearchFilter { ALL, TRACKS, ARTISTS, PLAYLISTS }` rendered by a fully custom `SearchFilters` row — animated `drawBehind` secondaryContainer indicator that springs (damping 0.8/stiffness 350) between measured `onPlaced` bounds, corner radius morph 50↔12dp on press, `TextHandleMove` haptic per select.
- **Debounce**: 500ms after typing; filter change re-triggers immediately.
- **Link pasting** in the query field (`onSearchQueryChanged`):
  - `https://soundcloud.com` / `https://on.soundcloud.com` → un-shorten (OkHttp HEAD follow-redirects), URL-decode, regex `track-stations:(\d+)` / `artist-stations:(\d+)` → `station:` / `station_artist:` routes; else `api.resolveUrl`: kind `track` → play immediately, `playlist|album` → `playlist_detail/`, `user` → `profile/`, `system-playlist` → station regexes on `uri`; fallback → plain search.
  - `youtube.com` / `youtu.be` → `handleYoutubeUrl` (plays/resolves the video, incl. `yt_radio:` navigation).
- **Empty-query state**: `SearchCategoriesGrid` — 2-column grid of Moods + Genres `SearchCategoryCard`s (secondaryContainer, oversized rotated ghost icon, arrow chip) → `genre_playlists/`.
- **Results** (`SearchResultsList`): AnimatedContent between categories/loading/results (fade+scale 0.96); SoundCloud: sectioned Artists (rows or LazyRow circles depending on filter) / Tracks (`TrackListItem` with download state) / Playlists (`DynamicPlaylistCard` or `SquareCard` LazyRow); infinite scroll (`loadMoreSearchResults` at 5-from-end, non-ALL filters) + loading footer; YouTube: flat track list; `StaggeredItem` entrance animation; "no results" states.
- Trailing icons when idle: recognition mic (primary tint) and avatar → profile menu / login.

---

## 7. Special UI

- **Achievement popups** (`common/AchievementPopup.kt` + `AchievementNotificationManager` SharedFlow): pill Card (dark 0xFF2E2E2E @0.9), emoji in green (0xFF50C878) stroked circle, title + gold "XP" + subtitle; shown by MainScreen top-center for 5s with bouncy spring in/out; suppressible via `achievement_popups_enabled` (sleep-timer "🌙" one always shows).
- **UltimateCompletionOverlay** (`common/UltimateCompletionOverlay.kt`, 305): full-screen black celebration when *all* achievements unlock. Canvas background: 12 rotating gold sweep-gradient light rays (20s loop) + 30 floating gold particles (orbital drift, rising). Phase 0 (0–5s): pulsing gold vinyl medallion (radial gradients + groove strokes) + "LEGEND" 56sp Black text with gold vertical-gradient brush, breathing scale; Phase 1 (5s+): 👑 + thanks text; dismissible only after 10s (fade-in "continue" hint).
- **Shimmer** (`common/Shimmer.kt`): infinite 1000ms linear-gradient translate brush (`shimmerBackground(shape)` modifier); primitives ShimmerLine/ShimmerBox; skeletons TrackListItemShimmer, SquareCardShimmer, ArtistCircleShimmer (used by Home/Library/Profile shimmer screens).
- **UpdateScreen** (`common/UpdateScreen.kt`): full Scaffold in a Dialog; version header, GitHub release notes via **compose-markdown** (`MarkdownText`, `com.github.jeziellago:compose-markdown:0.3.1` — *Android-only, needs multiplatform markdown lib*), `LinearWavyProgressIndicator` + byte progress in title while downloading; Download/Cancel bottom button; APK install flow (*n/a desktop*).
- **ListeningStatsScreen**: stat cards/segmented period selector only — **no drawn charts**.
- **ChartsScreen**: music top-lists, no graphs.
- **Recognition UI**: state-machine screen + **GlowView** AGSL noise-glow shader (API 33+); RECORD_AUDIO permission; Shazam-backed results resolved to SoundCloud.
- **Avatar/BannerCropDialogs**: pan/zoom crop with `detectTransformGestures` + Canvas mask overlay (portable logic; replace `android.graphics.Bitmap` with Skia `ImageBitmap` ops).
- **Widgets** (`ui/widget/`, ~1200 LOC): Glance `MusicWidget` (380), `MiniMusicWidget` (198), `SearchWidget` (107), `WidgetConfigActivity` (331), `WidgetActions`, 3 receivers — **Android-only (Glance AppWidget), no desktop equivalent; omit** (desktop analogue would be tray/media-key integration).
- **Ghost/captcha WebViews** in MainScreen: hidden offscreen WebView for SoundCloud session keep-alive, full-screen captcha WebView with Done/Cancel pills, second Dialog WebView for `captchaUrl` — *Android WebView; desktop needs JCEF or rethink.*

---

## 8. Android-only APIs needing desktop replacements

| Android API | Where | Desktop replacement |
|---|---|---|
| **Coil 2.x** `coil.compose.AsyncImage` | everywhere (~all screens) | **Coil 3** (multiplatform, near-identical API) or Kamel |
| `enableEdgeToEdge` / `SystemBarStyle` / `WindowCompat` / `statusBarsPadding`/`navigationBarsPadding`/`imePadding` | MainActivity, most Scaffolds | Drop; window decorations; imePadding no-op |
| `BackHandler` | Home search, player, lyrics, sheets | Esc-key handling / custom back dispatcher |
| `ModalBottomSheet` (material3) | ~12 usages | Exists in CfD material3, but consider dialogs/popovers for desktop UX |
| `AndroidView` + **WebView** | LoginScreen (SoundCloud OAuth), DiscordLoginScreen, ghost/captcha in MainScreen | JCEF / system browser + loopback OAuth |
| Haptics (all types) | player, queue, filters, palette, FAB settings | No-op stub |
| **AGSL `RuntimeShader`/`RenderEffect`** | ProgressiveBlurModifier, GlowView | Skia `RuntimeEffect` (SkSL — AGSL is SkSL-derived, ports nearly 1:1) or gradient fallback (already built-in) |
| `WindowInsets.getRoundedCorner` (API 31) | AnimatedNavUtils clip transition | Fixed 28.dp or skip clip animation |
| Platform dynamic color (SDK ≥ S) | Theme.kt "System" style | Fall back to materialkolor Expressive with orange seed |
| `ViewConfiguration.getLongPressTimeout()` | FxTile/DockButton long-press | Constant (~500ms) |
| `FLAG_KEEP_SCREEN_ON` | lyrics | Power-management API or skip |
| `Toast` | comments guest mode, About | Snackbar |
| `android.net.Uri` | route args, FAB keys | `java.net.URLEncoder`/`URI` |
| `ConnectivityManager.NetworkCallback` | Home, Library offline mode | Periodic ping or `java.net.NetworkInterface` watcher |
| SAF pickers + `contentResolver` | local media, storage, backup, avatar/banner | `java.io.File` + file choosers (FileKit/JFileChooser) |
| `android.graphics.Color.colorToHSV/HSVToColor`, `android.graphics.Bitmap` | ColorPaletteScreen, crop dialogs | `java.awt.Color.RGBtoHSB` / Skia bitmaps |
| Glance widgets, APK self-update, RECORD_AUDIO permission, locale restart | widgets, UpdateScreen, recognition, language | Omit / re-architect (JVM audio capture via `javax.sound.sampled`; `Locale.setDefault` + recomposition) |
| `Font(resId, FontVariation.Settings)` variable font | Type.kt | Supported in CfD ≥ 1.6 — keep `google_sans_flex.ttf` |
| Portable already | `LocalClipboardManager`, `LocalUriHandler`, materialkolor, reorderable, mp3agic, all M3/animation/pager/lazy APIs | — |

---

## 9. Image loading

**Coil 2.7.0** — `AsyncImage` used uniformly across every screen. Port path: Coil 3 multiplatform with OkHttp/Ktor network fetcher; API is drop-in for the `AsyncImage(model, contentDescription, contentScale, modifier)` call shape used here.

---

## 10. String resources

- **906 strings** in base `res/values/strings.xml` (English).
- **3 locales total**: `values` (en), `values-fr` (French), `values-hu` (Hungarian) — matching the in-app language picker (System/French/English/Hungarian).
- Locale switching: pref → `LocaleUtils.updateBaseContextLocale` + app restart. *Desktop: manual map of the 3 XML files with `Locale.setDefault` + state-driven recomposition.*

---

### Key porting notes (summary)
1. **materialkolor, reorderable, mp3agic, all M3 Expressive components are already multiplatform-compatible** — theme system and queue drag-reorder port nearly as-is (verify Expressive API availability in the CfD material3 artifact version — app uses 1.5.0-alpha22).
2. The two AGSL shaders (progressive blur, recognition glow) translate almost line-for-line to SkSL `RuntimeEffect` on desktop Skia; both already have graceful fallbacks.
3. Biggest re-architecture items: WebView-based auth (SoundCloud OAuth, Discord token, captcha/ghost session), SAF/file access, Glance widgets (drop), APK self-update (replace with desktop updater), audio recognition capture.
4. Search is not a screen — replicate the `SearchBar`-expands-in-Home pattern or promote it to a real desktop pane.
5. Navigation is a flat string-route graph with overlay player/lyrics — maps cleanly to any CfD navigation solution; keep `PlayerViewModel.navigateToPlaylistId` prefix protocol (`profile:`, `tag:`, `track_detail:`, `expanded_queue`, station/likes/downloads/local/yt_radio magic ids).
