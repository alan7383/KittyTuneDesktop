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
  <strong>A full desktop port of KittyTune Android for Linux, Windows, and macOS.</strong><br>
  SoundCloud-first music streaming with YouTube fallback, zero ads, synchronized lyrics, MPRIS media controls, Discord RPC, and native Material You end4 dotfiles integration.
</p>

---

### ~ what is this

KittyTune Desktop is a complete, native desktop port of the KittyTune Android music application. Built from the ground up using **Kotlin 2.4.0**, **Compose Multiplatform (Skiko)**, and **Material 3 Expressive**, it brings the full mobile experience natively to Linux, Windows, and macOS desktops.

Instead of running heavy web wrappers (Electron or Chromium PWA), KittyTune Desktop operates as a lightweight JVM desktop client. It streams high-quality audio with **SoundCloud as its primary music source** and **YouTube as an automatic fallback**, renders real-time synchronized karaoke lyrics, integrates with system media controls (MPRIS / SMTC), and seamlessly synchronizes with system-wide Material You themes.

---

### * features

<details open>
<summary><b>~ audio streaming & music sources</b></summary>

*   **SoundCloud-First Audio Streaming**: Native SoundCloud integration for tracks, playlists, user likes, reposts, and history, with seamless YouTube fallback when needed.
*   **High-fidelity audio engine**: Built-in FFmpeg and JavaFX Media playback pipelines with buffer optimization and gapless playback.
*   **Search & Discovery**: Instant search across tracks, artists, albums, and playlists with rich suggestion filters.
*   **History & Stats**: Local listening history, stats tracking, and automatic play counter persistence.
</details>

<details>
<summary><b>> synchronized lyrics & visuals</b></summary>

*   **Real-time synchronized lyrics**: Powered by LrcLib and KuGou scrapers with word-by-word / line-by-line karaoke highlights.
*   **Variable Font customization**: Fine-tune variable typography parameters (font weight, width, slant, roundness, opsz, and grade).
*   **Immersive Player UI**: Dynamic background gradient blurs matching current album artwork.
</details>

<details>
<summary><b>+ desktop integration & controls</b></summary>

*   **Linux MPRIS D-Bus integration**: Full integration with Linux desktop panels (Waybar, Quickshell, KDE, GNOME) for play, pause, skip, track metadata, and album art display.
*   **Windows & macOS System Media Controls**: Native media key support and system overlay controls.
*   **Discord Rich Presence (RPC)**: Broadcast your currently playing music to Discord with album artwork and time elapsed.
*   **Global Keyboard Shortcuts**: Control playback instantly with customizable hotkeys across your desktop workspace.
</details>

<details>
<summary><b># end4 hyprland dotfiles & material you system theme</b></summary>

*   **Automatic end4 Dotfile Detection**: Automatically detects if [end4's Hyprland dotfiles](https://github.com/end4/dots-hyprland) are installed on your Linux system.
*   **Exclusive Theme Option**: Unlocks the dedicated `end4 (Material You)` palette style in Settings -> Theme.
*   **Real-Time Live Reloading**: Listens to `matugen` color updates in `~/.local/state/quickshell/user/generated/colors.json`. When you change wallpapers or color schemes in Hyprland, KittyTune Desktop updates its entire color scheme live in real time without restart.
*   **Pure Black AMOLED Mode**: True `#000000` background toggle for OLED displays.
</details>

---

### > end4 hyprland dotfiles & material you system integration

KittyTune Desktop features native integration with [end4's Hyprland dotfiles (illogical-impulse)](https://github.com/end4/dots-hyprland).

#### **why is kittytune desktop specially tailored for end4 dotfiles?**

The end4 Hyprland dotfiles (`illogical-impulse`) are built from the ground up around **Material 3 (Material You / Monet)** design guidelines. Every component in end4—from Quickshell bars and launchers to notifications and GTK apps—uses dynamic Material 3 color roles generated on the fly by `matugen`.

Because KittyTune Desktop is also designed natively around **Material 3 Expressive UI**, it naturally shares the exact same aesthetic and design philosophy. Connecting KittyTune Desktop to end4 dotfiles creates complete visual harmony across your entire Linux environment.

When installed on a system running end4 dotfiles, KittyTune Desktop automatically detects the setup and unlocks the **`end4 (Material You)`** palette option in Settings.

<p align="center">
  <img src="images/option-end4-color.png" width="750" style="border-radius: 12px;" alt="end4 Material You palette option in KittyTune Desktop">
  <br><em>The exclusive end4 (Material You) palette option unlocked in KittyTune Desktop settings.</em>
</p>

<br>

<p align="center">
  <img src="images/musicpanelmpris.png" width="750" style="border-radius: 12px;" alt="Quickshell MPRIS Integration">
  <br><em>Quickshell MPRIS media control panel integration powered by end4 dotfiles.</em>
</p>

#### **key advantages of end4 dotfile integration**

*   **System-Wide Color Harmony**: KittyTune Desktop extracts the exact Material 3 color tokens generated by `matugen` (`primary`, `surface`, `surfaceContainer`, `outline`, etc.) used across Quickshell, Fuzzel, and GTK.
*   **Live Hot-Reloading**: Change your wallpaper using Hyprland's wallpaper selector, and KittyTune Desktop instantly morphs its colors in real time while music is playing.
*   **Native Wayland Window Icon**: Pre-configured Wayland `app_id` (`kitty-tune`) and `StartupWMClass` matching `kitty-tune.desktop` so taskbars and docks render the crisp native logo out-of-the-box.

Check out the official dotfile repository: [end4/dots-hyprland (github)](https://github.com/end4/dots-hyprland)

---

### + screenshots

<p align="center">
  <img src="images/homescreen.png" width="750" style="border-radius: 12px;" alt="KittyTune Desktop Home Screen">
  <br><em>Main Home Screen displaying user library, recommendations, and recent tracks.</em>
</p>

<br>

<p align="center">
  <img src="images/lyrics.png" width="750" style="border-radius: 12px;" alt="Synchronized Lyrics View">
  <br><em>Synchronized Karaoke Lyrics view with dynamic artwork background blur.</em>
</p>

<br>

<p align="center">
  <img src="images/playlist.png" width="750" style="border-radius: 12px;" alt="Playlist & Album View">
  <br><em>Playlist view with track listings, duration, and batch playback controls.</em>
</p>

---

### * keyboard shortcuts

| Shortcut | Action |
| :--- | :--- |
| `Spacebar` | Play / Pause toggle |
| `N` / `Shift + Right` | Next track |
| `P` / `Shift + Left` | Previous track |
| `L` | Toggle Like / Favorite |
| `R` | Toggle Repeat mode |
| `S` | Toggle Shuffle mode |
| `Shift + Up` | Increase Volume |
| `Shift + Down` | Decrease Volume |
| `M` | Mute / Unmute audio |
| `Escape` | Navigate back / Close modal |

---

### # download & installation

#### **arch linux / cachyos (aur & pkg)**
Download pre-built Arch packages from the [Releases page](https://github.com/alan7383/KittyTuneDesktop/releases):

```bash
sudo pacman -U kitty-tune-1.0.16-1-x86_64.pkg.tar.zst
```

#### **universal linux (appimage, deb, rpm)**
Grab `.AppImage`, `.deb`, or `.rpm` packages directly from GitHub Releases.

#### **windows & macos**
Download the Windows `.msi` installer or portable `.zip`, and macOS `.dmg` bundles directly from [Releases](https://github.com/alan7383/KittyTuneDesktop/releases).

---

### % building from source

#### **prerequisites**
*   JDK 21 or higher
*   Git

```bash
# Clone repository
git clone https://github.com/alan7383/KittyTuneDesktop.git
cd KittyTuneDesktop

# Run desktop application
./gradlew run

# Package distribution for your operating system
./gradlew packageDistributionForCurrentOS
```

---

### ? why kittytune desktop?

Most desktop music streaming clients are heavy Electron applications that consume gigabytes of RAM. KittyTune Desktop was created as a native, lightweight alternative that runs smoothly with minimal CPU and memory footprint, while offering features web players lack: MPRIS system integration, live Material You dotfile synchronization, synchronized karaoke lyrics, and offline database caching.

---

### * credits & license

*   Based on KittyTune for Android.
*   Material You color system powered by [end4/dots-hyprland](https://github.com/end4/dots-hyprland) and `matugen`.
*   Built with [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform) and [MaterialKolor](https://github.com/aj-alt/MaterialKolor).

Licensed under the **MIT License**.

---

<p align="center">
  made with ( ˘▽˘)っ♨ and a bit of chaos by <a href="https://github.com/alan7383">alan7383</a> (´･ω･`)
</p>
