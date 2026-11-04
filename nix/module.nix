{ self }:

{ config, lib, pkgs, ... }:

let
  cfg = config.programs.kittytune;
in
{
  options.programs.kittytune = {
    enable = lib.mkEnableOption "KittyTune Desktop music player";

    package = lib.mkOption {
      type = lib.types.package;
      default = self.packages.${pkgs.stdenv.hostPlatform.system}.default or (pkgs.callPackage ./package.nix { });
      defaultText = lib.literalMD "`packages.default` from KittyTune flake";
      description = "The KittyTune package to install.";
    };
  };

  config = lib.mkIf cfg.enable {
    environment.systemPackages = [ cfg.package ];
  };
}
