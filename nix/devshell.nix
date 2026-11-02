{ pkgs }:

let
  libPath = with pkgs; lib.makeLibraryPath [
    stdenv.cc.cc.lib
    alsa-lib
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
  ];

  shellHook = ''
    export JAVA_HOME="${pkgs.jdk21}"
    export LD_LIBRARY_PATH="${libPath}''${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"

    echo "(=^･ω･^=) KittyTune Desktop Nix development environment loaded!"
    echo "Java: $(java --version | head -n 1)"
  '';
}
