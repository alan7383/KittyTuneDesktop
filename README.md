<p align="center">
  <img src="images/logo.png" width="128" alt="KittyTune Desktop Logo">
</p>

<h1 align="center">KittyTune Desktop (・∀・)ﾉ</h1>

<p align="center">
  <a href="https://github.com/alan7383/KittyTuneDesktop/blob/master/LICENSE">
    <img src="https://img.shields.io/github/license/alan7383/KittyTuneDesktop?style=for-the-badge&logo=github" alt="License">
  </a>
  <a href="https://github.com/alan7383/KittyTuneDesktop/releases">
    <img src="https://img.shields.io/github/v/tag/alan7383/KittyTuneDesktop?style=for-the-badge&logo=github&color=orange" alt="Release">
  </a>
  <img src="https://img.shields.io/badge/Kotlin-2.4.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin Badge">
  <img src="https://img.shields.io/badge/Compose_Desktop-1.11.1-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Compose Desktop Badge">
  <img src="https://img.shields.io/badge/Linux_|_Windows_|_macOS-000000?style=for-the-badge&logo=linux&logoColor=white" alt="Platform Badge">
</p>

<p align="center">
  <code>(ฅ^•⩊•^ฅ) ♪♫ continuous soundcloud vibes for your desktop ♫♪</code><br><br>
  <strong>A full desktop port of KittyTune Android for Linux, Windows, and macOS.</strong><br>
  SoundCloud-first music streaming with full account & likes sync, YouTube fallback, zero ads, sync lyrics, crossfade, MPRIS, Discord RPC & end4 dotfiles integration.
</p>

---

```
   /\_/\
  ( o.o )  kittytune desktop
   > ^ <   lightweight JVM music player for soundcloud + youtube
```

---

### ~ what is this

KittyTune Desktop is a complete, native desktop port of the KittyTune Android music app. Built from scratch with **Kotlin 2.4.0**, **Compose Multiplatform (Skiko)**, and **Material 3 Expressive**, it brings the mobile experience straight to Linux, Windows, and macOS.

No heavy web wrappers or Electron bloat here. Just a fast, lightweight JVM client that streams high-quality audio directly from **SoundCloud** (with full account sync for likes, reposts, and playlists), falls back to **YouTube** when needed, renders live karaoke lyrics, shuffles full playlists without web lazy-load traps, and syncs seamlessly with your system colors.

---

### * features

<details open>
<summary><b>~ audio streaming & soundcloud sync</b></summary>

* **full soundcloud account sync**: link your account to sync liked tracks, playlists, reposts, and history in real time.
* **soundcloud-first audio engine**: native SoundCloud playback with seamless YouTube audio fallback whenever a track is missing.
* **audio crossfade**: smooth transitions between songs with customizable crossfade duration (in seconds).
* **high-fidelity playback**: FFmpeg & JavaFX Media pipelines with buffer optimization and gapless audio support.
* **true full-playlist shuffle**: shuffles your entire library or playlist at once — no lazy-load limits or repeated tracks.
* **search & discovery**: instant search across tracks, artists, albums, and playlists with instant filters.
* **history & stats**: local playback history, play counts, and listening statistics persistence.
</details>

<details>
<summary><b>> synchronized lyrics & visualizer</b></summary>

* **real-time synchronized lyrics**: word-by-word and line-by-line karaoke tracking via LrcLib and KuGou scrapers.
* **variable font tuning**: customize font weight, width, slant, roundness, optical size, and grade for lyrics typography.
* **immersive artwork colors**: dynamic UI theme adaptation matching the current track's album cover art.
</details>

<details>
<summary><b>+ desktop integration & media controls</b></summary>

* **linux MPRIS D-Bus integration**: native media controls for Waybar, Quickshell, KDE, GNOME, and Linux desktop panels.
* **windows & macOS media keys**: system media key controls, overlay popups, and hardware hotkeys.
* **discord rich presence (rpc)**: show off your currently playing music, artist, elapsed time, and album artwork on Discord.
* **customizable global shortcuts**: control playback, volume, and tracks anywhere on your desktop.
</details>

<details>
<summary><b># end4 hyprland & material you system theme</b></summary>

* **auto end4 dotfile detection**: automatically senses if [end4's Hyprland dotfiles](https://github.com/end-4/dots-hyprland) are installed on your Linux system.
* **exclusive end4 theme**: unlocks the dedicated `end4 (Material You)` palette under Settings -> Theme.
* **live matugen reloading**: reads color tokens from `~/.local/state/quickshell/user/generated/colors.json` and updates application colors in real time when wallpaper changes.
* **pure amoled mode**: true `#000000` pitch black background toggle for OLED displays.
</details>

---

### > end4 hyprland dotfiles & material you integration

KittyTune Desktop integrates natively with [end4's Hyprland dotfiles](https://github.com/end-4/dots-hyprland).

Since both KittyTune Desktop and end4's `illogical-impulse` environment share **Material 3 (Material You / Monet)** design tokens, your music player stays in perfect visual sync with your system wallpaper.

<p align="center">
  <img src="images/option-end4-color.png" width="750" style="border-radius: 12px;" alt="end4 Material You palette option">
  <br><em>exclusive end4 (Material You) color palette option.</em>
  <br><br>
  <img src="images/musicpanelmpris.png" width="750" style="border-radius: 12px;" alt="Quickshell MPRIS Integration">
  <br><em>Quickshell MPRIS media control panel integration.</em>
</p>

---

### + screenshots

<p align="center">
  <img src="images/homescreen.png" width="750" style="border-radius: 12px;" alt="KittyTune Desktop Home Screen">
  <br><em>home screen — user library, recommendations, and recent tracks.</em>
</p>

<br>

<p align="center">
  <img src="images/lyrics.png" width="750" style="border-radius: 12px;" alt="Synchronized Lyrics View">
  <br><em>synchronized karaoke lyrics with word-by-word tracking.</em>
</p>

<br>

<p align="center">
  <img src="images/playlist.png" width="750" style="border-radius: 12px;" alt="Playlist & Album View">
  <br><em>playlist view — track listings, duration, and batch playback.</em>
</p>

---

### * keyboard shortcuts

| shortcut | action |
| :--- | :--- |
| `Spacebar` | play / pause toggle |
| `N` / `Shift + Right` | next track |
| `P` / `Shift + Left` | previous track |
| `L` | toggle like / favorite |
| `R` | toggle repeat mode |
| `S` | toggle shuffle mode |
| `Shift + Up` | increase volume |
| `Shift + Down` | decrease volume |
| `M` | mute / unmute audio |
| `Escape` | back / close modal |

---

### # download & installation

pre-built binaries for linux, windows, and macos are available on the [releases page](https://github.com/alan7383/KittyTuneDesktop/releases).

#### **arch linux (aur & pkg)**
```bash
sudo pacman -U kitty-tune-1.0.17-1-x86_64.pkg.tar.zst
```

#### **debian / ubuntu / linux mint (.deb)**
```bash
sudo apt install ./kitty-tune_1.0.17_amd64.deb
```

#### **fedora / opensuse / rhel (.rpm)**
```bash
sudo dnf install ./kitty-tune-1.0.17-1.x86_64.rpm
```

#### **universal portable linux (.AppImage)**
```bash
chmod +x KittyTune-1.0.17-x86_64.AppImage
./KittyTune-1.0.17-x86_64.AppImage
```

#### **windows & macos**
grab the windows installer (`.msi`), portable archive (`.zip`), or macos disk image (`.dmg`).

---

### % building from source

#### **prerequisites**
* JDK 21 or higher
* Git

```bash
# clone repo & navigate
git clone https://github.com/alan7383/KittyTuneDesktop.git
cd KittyTuneDesktop

# run application
./gradlew run

# package binary for your OS
./gradlew packageDistributionForCurrentOS
```

---

### ? why kittytune desktop? (kittytune vs soundcloud web)

most desktop music clients are heavy electron apps that gobble up gigabytes of RAM. KittyTune Desktop was created as a native, lightweight alternative that runs smoothly with low CPU and memory footprint, while providing features the official web player misses out on:

| feature | official soundcloud web | kittytune desktop |
| :--- | :--- | :--- |
| **true full-playlist shuffle** | no (limited by lazy loading) | yes (instant full shuffle) |
| **offline music downloads** | no (requires Go+ sub) | yes (free offline cache & local files) |
| **ad-free experience** | no (audio/video ads) | yes (100% ad-free) |
| **synchronized karaoke lyrics** | no | yes (LrcLib & KuGou word/line sync) |
| **audio crossfade** | no | yes (customizable transition duration) |
| **youtube audio fallback** | no (stream fails if unavailable) | yes (automatic fallback routing) |
| **system-wide theme sync** | no (fixed web UI) | yes (end-4 Hyprland / matugen live sync) |
| **native linux MPRIS D-Bus** | no | yes (full panel & waybar controls) |
| **discord rich presence (rpc)** | no (needs 3rd party extension) | yes (native discord status & cover art) |

---

### * credits & license

* based on KittyTune for Android.
* material colors powered by [end-4/dots-hyprland](https://github.com/end-4/dots-hyprland) and `matugen`.
* built with [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform) & [MaterialKolor](https://github.com/aj-alt/MaterialKolor).

licensed under the **MIT License**.

---

<p align="center">
  made with ( ˘▽˘)っ♨ and a bit of chaos by <a href="https://github.com/alan7383">alan7383</a> (´･ω･`)
</p>
