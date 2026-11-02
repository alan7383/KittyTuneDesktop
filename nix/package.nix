{ lib
, stdenv
, fetchurl
, dpkg
, autoPatchelfHook
, makeWrapper
, alsa-lib
, alsa-plugins
, libpulseaudio
, gtk3
, glib
, cairo
, pango
, atk
, gdk-pixbuf
, libGL
, fontconfig
, freetype
, libxkbcommon
, wayland
, dbus
, udev
, zlib
, xorg
}:

let
  pname = "kitty-tune";
  version = "1.4.9";

  sources = {
    x86_64-linux = {
      arch = "amd64";
      hash = "sha256-lH5tMPDXEvnjYwiky+F809p/tkAxdI3iPinoWif9c/w=";
    };
    aarch64-linux = {
      arch = "arm64";
      hash = "sha256-soFX6+VYx/OL3AADdYQsLyQxEoP1KAjbsbh3rOohptk=";
    };
  };

  currentSystem = stdenv.hostPlatform.system;
  currentSource = sources.${currentSystem} or (throw "Unsupported system for kitty-tune: ${currentSystem}");

  runtimeLibs = [
    stdenv.cc.cc.lib
    alsa-lib
    alsa-plugins
    libpulseaudio
    gtk3
    glib
    cairo
    pango
    atk
    gdk-pixbuf
    libGL
    fontconfig
    freetype
    libxkbcommon
    wayland
    dbus
    udev
    zlib
    xorg.libX11
    xorg.libXext
    xorg.libXrender
    xorg.libXtst
    xorg.libXi
    xorg.libXcursor
    xorg.libXrandr
    xorg.libXxf86vm
    xorg.libxcb
  ];

in stdenv.mkDerivation rec {
  inherit pname version;

  src = fetchurl {
    url = "https://github.com/alan7383/KittyTuneDesktop/releases/download/v${version}/kitty-tune_${version}_${currentSource.arch}.deb";
    hash = currentSource.hash;
  };

  nativeBuildInputs = [
    dpkg
    autoPatchelfHook
    makeWrapper
  ];

  buildInputs = runtimeLibs;

  # Allow build to succeed even if optional runtime dependencies loaded dynamically (dlopen) are absent during build
  autoPatchelfIgnoreMissingDeps = [ "*" ];

  # Do not strip binaries to avoid breaking JNI bindings or JRE components
  dontStrip = true;

  unpackPhase = ''
    runHook preUnpack
    dpkg-deb -x "$src" source
    runHook postUnpack
  '';

  installPhase = ''
    runHook preInstall

    mkdir -p "$out/opt"
    cp -r source/opt/kitty-tune "$out/opt/kitty-tune"

    # Ensure executable permissions on jpackage launcher and bundled Java runtime
    chmod +x "$out/opt/kitty-tune/bin/KittyTune"
    if [ -d "$out/opt/kitty-tune/lib/runtime/bin" ]; then
      chmod +x "$out/opt/kitty-tune/lib/runtime/bin/"* || true
    fi

    # Install application icons
    mkdir -p "$out/share/icons/hicolor/256x256/apps" "$out/share/pixmaps"
    if [ -f "$out/opt/kitty-tune/lib/KittyTune.png" ]; then
      cp "$out/opt/kitty-tune/lib/KittyTune.png" "$out/share/icons/hicolor/256x256/apps/kitty-tune.png"
      cp "$out/opt/kitty-tune/lib/KittyTune.png" "$out/share/pixmaps/kitty-tune.png"
    fi

    # Install desktop entry
    mkdir -p "$out/share/applications"
    cat << 'EOF' > "$out/share/applications/kitty-tune.desktop"
[Desktop Entry]
Name=KittyTune
GenericName=Music Player
Comment=KittyTune Desktop Music Player - SoundCloud streaming, lossless upgrades & Apple-style synchronized lyrics
Exec=kitty-tune %U
Icon=kitty-tune
Terminal=false
Type=Application
Categories=AudioVideo;Audio;Player;Music;
MimeType=audio/mpeg;audio/flac;audio/mp4;audio/aac;audio/ogg;audio/opus;audio/wav;audio/x-ms-wma;
Keywords=music;player;soundcloud;streaming;lyrics;lossless;karaoke;
StartupWMClass=KittyTune
EOF

    # Default ALSA configuration fallback for environments without physical ALSA soundcards (e.g. WSL2 / WSLg)
    mkdir -p "$out/etc"
    cat << 'EOF' > "$out/etc/asound.conf"
pcm.!default {
    type pulse
}
ctl.!default {
    type pulse
}
EOF

    # Create binary wrapper with full dynamic library paths (Skiko, ALSA, GTK, X11, Wayland, PulseAudio)
    mkdir -p "$out/bin"
    makeWrapper "$out/opt/kitty-tune/bin/KittyTune" "$out/bin/kitty-tune" \
      --prefix LD_LIBRARY_PATH : "${lib.makeLibraryPath runtimeLibs}" \
      --prefix XDG_DATA_DIRS : "${gtk3}/share/gsettings-schemas/${gtk3.name}:$XDG_DATA_DIRS" \
      --prefix ALSA_PLUGIN_DIR : "${alsa-plugins}/lib/alsa-lib" \
      --run 'if [ -e /mnt/wslg/PulseServer ] && [ -z "$PULSE_SERVER" ]; then export PULSE_SERVER=unix:/mnt/wslg/PulseServer; fi' \
      --run 'if [ ! -f /etc/asound.conf ] && [ ! -f ~/.asoundrc ] && [ -f "'$out'/etc/asound.conf" ]; then export ALSA_CONFIG_PATH="'$out'/etc/asound.conf"; fi' \
      --set-default ORT_DISABLE_TELEMETRY "1"

    # Alias KittyTune (capitalized) to kitty-tune
    ln -s "$out/bin/kitty-tune" "$out/bin/KittyTune"

    runHook postInstall
  '';

  meta = with lib; {
    description = "Complete, native desktop music player built with Kotlin & Compose Multiplatform";
    longDescription = ''
      KittyTune Desktop is a native desktop music player built with Kotlin and Compose Multiplatform.
      Features SoundCloud account sync, automatic lossless upgrades (Qobuz, TIDAL, Deezer, YouTube Music),
      real-time synchronized lyrics, DJ automix transitions, 30+ audio DSP effects rack, and Linux MPRIS integration.
    '';
    homepage = "https://github.com/alan7383/KittyTuneDesktop";
    license = licenses.mit;
    platforms = [ "x86_64-linux" "aarch64-linux" ];
    mainProgram = "kitty-tune";
  };
}
