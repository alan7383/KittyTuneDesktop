{ self }:

{ config, lib, pkgs, ... }:

let
  cfg = config.programs.kittytune;
in
{
  options.programs.kittytune = {
    enable = lib.mkEnableOption "KittyTune Desktop music player";

    package = lib.mkPackageOption pkgs "kitty-tune" {
      default = self.packages.${pkgs.system}.default or (pkgs.callPackage ./package.nix { });
    };
  };

  config = lib.mkIf cfg.enable {
    environment.systemPackages = [ cfg.package ];
  };
}
