# KittyTune Audio Engine — Porting Spec (Android Media3/ExoPlayer → Desktop JVM)

## 1. Player Architecture
- **MusicManager** (data/): singleton, owns the single ExoPlayer + DSP processors + DRM token cache + current track + contextFlow.
- **PlayerViewModel** (2197 lines): **owns the queue** (ExoPlayer kept at 1–2 items only). `_originalQueue`, `_queue`, `queueState`, `currentQueueIndex`.
- **StreamResolver**: Track → playable URL (local file → SoundCloud transcoding → YouTube/NewPipe fallback).

### ExoPlayer data source chain
CacheDataSource(200MB LRU) → ResolvingDataSource → DefaultDataSource(DefaultHttpDataSource).
- UA `"SoundCloud/2025.12.10-release (Android 10; Android)"`, cross-protocol redirects, `Authorization: OAuth <token>`.
- Custom URI scheme `soundtune://track/<id>` resolved lazily at load: local DB → in-memory currentTrack → network getTracksByIds + StreamResolver.
- `exo_cache://<trackId>::<streamUrl>::<licenseAuthTokenOrKeySetIdBase64>` for offline.

### Audio sink DSP chain order
`FxAudioProcessor(muffle→bass) → ReverbAudioProcessor → EightDAudioProcessor → EarrapeAudioProcessor`.
Speed/pitch are NOT processors — they use ExoPlayer PlaybackParameters (Sonic time-stretcher), applied BEFORE the chain.

### Queue logic (pure Kotlin, portable)
- Shuffle: keep played part [0..currentIndex], shuffle upcoming remainder. Revert restores originalQueue by id.
- Repeat NONE/ALL/ONE. NONE at end → autoplay station/radio (SoundCloud getTrackStation OR YouTube Mix `RD<videoId>` via NewPipe), append de-duped.
- smartPrevious: pos>3000ms restart else prev index.
- playNext: pos>2000ms counts a play; records stats events SKIP_NEXT/SKIP_PREVIOUS/MANUAL_REPLAY/PLAY_COMPLETE/REPEAT_ONE_LOOP.
- playRobustly(index): resolve off-main, single MediaItem (embedded JPEG q90), setMediaItem+prepare+play, apply effects, preloadNextTrack.

### Position persistence
PlayerPreferences ("player_state" prefs + filesDir/queue_cache.json). savePlaybackState debounced 500ms. restoreSession restores paused at last position.

### UI observation
Compose mutableStateOf on ViewModel. Sources: Player.Listener, MusicManager.onTrackChange, contextFlow, 1s progress polling coroutine (pushes recently-played to SC at 30s).

## 2. Audio Effects — EXACT DSP (ui/player/audio/AudioProcessors.kt)

All 4 processors extend BaseAudioProcessor, operate on **interleaved 16-bit signed PCM shorts** at native rate/channels. Disabled = byte-for-byte pass-through.

```kotlin
data class AudioEffectsState(
    val speed: Float = 1f,
    val isPitchEnabled: Boolean = true,     // pitch follows speed
    val is8DEnabled: Boolean = false,
    val isMuffledEnabled: Boolean = false,
    val isBassBoostEnabled: Boolean = false,
    val isReverbEnabled: Boolean = false,
    val isRainEnabled: Boolean = false,
    val rainVolume: Float = 1.0f,
    val bassBoostIntensity: Float = 0.5f,   // all intensities 0..1
    val eightDSpeed: Float = 0.5f,
    val reverbIntensity: Float = 0.5f,
    val muffledIntensity: Float = 0.5f,
    val isEarrapeEnabled: Boolean = false
)
```
Application: `pitch = if (isPitchEnabled) speed else 1f; player.playbackParameters = PlaybackParameters(speed, pitch)`.

### 2.1 Speed & pitch
Speed = rate multiplier. UI quantizes round(speed*factor)/factor, factor=10 (20 if precise-speed pref). No semitone control. pitch=speed → nightcore/daycore; pitch=1 → time-stretch preserved. JVM: Sonic library setSpeed/setPitch.

### 2.2 8D (EightDAudioProcessor) — auto-pan LFO
Stereo only (mono pass-through). Per frame: `time += rotationSpeed; pan = sin(time); leftVol=(1-pan)/2; rightVol=(1+pan)/2; L'=L*leftVol; R'=R*rightVol` (truncate to short).
`rotationSpeed = clamp(0.000002 + s*0.000038, 0.000002, 0.00004)` rad/frame. Default s=0.5 → ~6.8s cycle. Phase resets on flush/disable. Linear pan law (center = both ×0.5).

### 2.3 Muffled/low-pass (FxAudioProcessor stage 1)
RBJ biquad LP, Q=0.707, `f0 = clamp(400 + s*1100, 400, 1500) Hz` (default 950).
fs=max(sampleRate,44100), w0=2π·f0/fs, alpha=sin(w0)/(2Q), a0=1+alpha:
```
b0=((1−cos w0)/2)/a0  b1=(1−cos w0)/a0  b2=b0
a1=(−2cos w0)/a0      a2=(1−alpha)/a0
```
Direct Form I: y=b0·x+b1·x1+b2·x2−a1·y1−a2·y2. **QUIRK: single filter state shared across interleaved L/R samples** (they alternate through same x1,x2,y1,y2). Reset on flush/toggle/param change.

### 2.4 Bass boost (FxAudioProcessor stage 2, after muffle)
RBJ low-shelf f0=100Hz, S=1, `gain_dB = clamp(4 + s*12, 4, 16)` (default 10).
A=10^(gain/40), w0=2π·100/fs, alpha=sin(w0)/2·√((A+1/A)(1/S−1)+2), beta=2√A·alpha, a0=(A+1)+(A−1)cos w0+beta:
```
b0=A((A+1)−(A−1)cos w0+beta)/a0
b1=2A((A−1)−(A+1)cos w0)/a0
b2=A((A+1)−(A−1)cos w0−beta)/a0
a1=−2((A−1)+(A+1)cos w0)/a0
a2=((A+1)+(A−1)cos w0−beta)/a0
```
Same shared-state DF-I. Clamp to short range then truncate.

### 2.5 Reverb (ReverbAudioProcessor) — single feedback delay
Delay fixed 150ms. buffer = sampleRate*0.150*channelCount shorts (interleaved, single circular buffer).
`decay = clamp(0.2 + s*0.6, 0.2, 0.8)` (default 0.5).
Per sample: `out = clamp(in + buffer[cursor]*decay); buffer[cursor]=out; cursor++ wrap`. Zeroed on flush.

### 2.6 Earrape (EarrapeAudioProcessor) — two-stage hard clip
```
s = clamp(in*40, ±32767)   // stage 1
s = clamp(s*20, ±32767)    // stage 2 → near square
out = (short)(s*0.25)      // −12dB makeup
```
Pref `has_seen_earrape_warning` gates UI.

### 2.7 NON-EXISTENT (don't chase): normalization/loudness/ReplayGain = none. Crossfade prefs exist but nothing reads them. Fade only in sleep timer.

## 3. Rain (RainPlayer)
Second independent ExoPlayer, res/raw/rain.mp3, REPEAT_MODE_ONE, lazy. setEnabled/setVolume from isRainEnabled/rainVolume. Mixes at OS level, unaffected by effects, keeps looping while music paused. JVM: javax.sound Clip LOOP_CONTINUOUSLY + gain.

## 4. Sleep Timer (PlayerViewModel)
1. Duration mode: coroutine ticking 50ms. preFadeVolume captured. If fade (pref, default off, 0–30s default 30): final fadeDurationMs → `volume = preFadeVolume*(remaining/fadeDuration)²` quadratic. At expiry volume=0, pause, restore volume silently.
2. End-of-track mode: flag checked in STATE_ENDED + onTrackChange.
UI: AchievementNotificationManager "🌙" island popup.

## 5. Music Recognition (music/recognition + :shazamkit)
- **AudioRecorder**: MIC 16kHz mono 16-bit PCM LE, up to 9000ms, onProgress every 3s. JVM: TargetDataLine AudioFormat(16000,16,1,signed,LE).
- **ShazamSignatureGenerator**: pure-JVM Shazam/vibra fingerprint. SAMPLE_RATE 16000, FFT_SIZE 2048, 1025 bins, MAX_PEAKS 255, MAX_TIME 12s. Hanning window w[i]=0.5(1−cos(2π(i+1)/2049)), radix-2 FFT, freq+time spreading, peak recognition, binary encoding → `data:audio/vnd.shazam.sig;base64,...`. Only Android deps: android.util.Base64/Log → swap for java.util.Base64.
- **:shazamkit** = plain kotlin("jvm") module, reusable. `Shazam.recognize(sig, ms)` → POST https://amp.shazam.com/discovery/v5/en/US/android/-/tag/{UUID}/{uuid} query sync=true&webv3=true... random Dalvik UA. Body: geolocation+signature+timestamp+timezone. Max 2 concurrent, ≥1000ms between, 3 retries expo backoff, 5min cache.
- Result: trackId, title, artist, album/label/releaseDate, coverArt, genre, lyrics, shazamUrl, appleMusicUrl, spotifyUrl, isrc, youtubeVideoId.
- Flow (RecognitionViewModel): every 3s chunk → sig → recognize → on hit searchTracks("<title> <artist>",5) first result to make playable → add to RecognitionHistory.

## 6. Discord Rich Presence (:kizzy — pure kotlin("jvm"), ports unchanged)
- **Gateway WebSocket** (user token, not IPC): wss://gateway.discord.gg/?v=10&encoding=json via Ktor. Token in pref discord_token, toggle discord_rpc_enabled (default off).
- Opcodes: DISPATCH0/HEARTBEAT1/IDENTIFY2/PRESENCE_UPDATE3/RESUME6/RECONNECT7/INVALID_SESSION9/HELLO10/HEARTBEAT_ACK11.
- HELLO→RESUME if seq>0&sessionId else IDENTIFY; heartbeat loop. IDENTIFY {capabilities:65, compress:false, largeThreshold:100, properties:{browser:"Discord Client",device:"ktor",os:"Windows"}, token}.
- READY→store session_id, resume_gateway_url. RECONNECT→close 4000→reconnect 200ms. INVALID_SESSION→150ms re-IDENTIFY.
- Presence op3: activities[{name, state, details, type:2 LISTENING, timestamps{start,end}, assets{large_image,large_text}, buttons:["Listen"], metadata{button_urls:[permalink]}, application_id:"1473071817693331540", url:null}], afk:true, status:"online".
- Artwork via kizzy proxy https://kizzy-api.cjjdxhdjd.workers.dev/image?url=... → {id:"mp:external/..."}. Fallback logo asset 1473370878195794073.
- Timestamps only while playing & duration>0: start=now−pos, end=start+dur. Display modes ARTIST/SONG/ACTIVITY(default). Debounced 500ms. getUserInfo → https://discord.com/api/v9/users/@me.

## 7. Download Engine (DownloadManager.kt)
- StreamResolver forDownload=true: progressive mp3 → hls → (if DRM pref) ctr/cbc-encrypted-hls, preset aac_160k>aac_96k>abr_sq. YouTube fallback: NewPipe search best M4A.
- 3 paths: (1) Progressive MP3: 3 parallel range chunks, mp3agic ID3v24 tagging + setAlbumImage jpeg. Name "<artist> - <title>.mp3" sanitized [\/:*?"<>|]→_. (2) YouTube M4A: same downloader, no tagging. (3) HLS/DRM: Media3 HlsDownloader → ExoCache, Widevine OfflineLicenseHelper → keySetId base64. **DRM not portable to desktop — skip or stream-only.**
- Storage: SAF tree URI or filesDir. Artwork also filesDir/art_<id>.jpg. Room downloadDao.
- Progress: downloadProgress StateFlow<Map<Long,Int>>, playlistDownloadProgress StateFlow<Map<Long,Float>>. Batch Semaphore(4).

## 8. Android→JVM
- ExoPlayer+BaseAudioProcessor → FFmpeg/JavaCV decode + run §2 processors on PCM shorts + javax.sound SourceDataLine output.
- PlaybackParameters(Sonic) → Sonic Java library.
- HLS → FFmpeg (m3u8 native) or `ffmpeg -i m3u8 -c copy out.m4a`.
- Widevine DRM → **no JVM equivalent**, rely on non-DRM transcodings + YouTube fallback.
- MediaSession/notification/MediaButtonReceiver → Windows SMTC via JNI/WinRT, or tray + global hotkeys (JNativeHook). Keep 3s smart-previous.
- AudioRecord → TargetDataLine. NewPipe/mp3agic/kizzy/shazamkit already pure Java, reuse directly.

### CAVEATS
- Fx biquads share ONE state across L/R (slight HF crosstalk) — reproduce for parity.
- 8D phase per-frame → rotation period scales with sample rate.
- Earrape makeup 0.25 + two-stage order matters.
- Crossfade/normalization = dead prefs, no impl.
