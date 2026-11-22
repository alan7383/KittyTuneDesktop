{ lib, stdenv, fetchurl, autoPatchelfHook, makeWrapper, openssl, vulkan-loader }:

stdenv.mkDerivation {
  pname = "kittytune-demucs";
  version = "0.3.4";
  src = fetchurl {
    url = "https://github.com/nikhilunni/demucs-rs/releases/download/v0.3.4/demucs-x86_64-unknown-linux-gnu.tar.gz";
    hash = "sha256-OLse24Er33oiEWy3Gw40c2YrEgZLPYyOempWUHyahn0=";
  };
  sourceRoot = ".";
  nativeBuildInputs = [ autoPatchelfHook makeWrapper ];
  buildInputs = [ openssl stdenv.cc.cc.lib ];
  installPhase = ''
    runHook preInstall
    install -Dm755 demucs "$out/bin/demucs"
    wrapProgram "$out/bin/demucs" \
      --prefix LD_LIBRARY_PATH : "${lib.makeLibraryPath [ vulkan-loader ]}"
    runHook postInstall
  '';
  meta = {
    description = "Native Demucs helper for KittyTune stem separation";
    homepage = "https://github.com/nikhilunni/demucs-rs";
    license = lib.licenses.asl20;
    platforms = [ "x86_64-linux" ];
    mainProgram = "demucs";
  };
}
