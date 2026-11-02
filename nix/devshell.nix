{ pkgs }:

let
  libPath = with pkgs; lib.makeLibraryPath [
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
in
pkgs.mkShell {
  name = "kittytune-dev-shell";

  packages = with pkgs; [
    jdk21
    gradle
    git
    pkg-config
    alsa-plugins
    libpulseaudio
  ];

  shellHook = ''
    export JAVA_HOME="${pkgs.jdk21}"
    export LD_LIBRARY_PATH="${libPath}''${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
    export ALSA_PLUGIN_DIR="${pkgs.alsa-plugins}/lib/alsa-lib"
    if [ -e /mnt/wslg/PulseServer ] && [ -z "$PULSE_SERVER" ]; then
      export PULSE_SERVER=unix:/mnt/wslg/PulseServer
    fi

    echo "(=^･ω･^=) KittyTune Desktop Nix development environment loaded!"
    echo "Java: $(java --version | head -n 1)"
  '';
}
