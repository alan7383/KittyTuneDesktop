{
  description = "KittyTune Desktop - Complete, native desktop music player built with Kotlin & Compose Multiplatform";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  };

  outputs = { self, nixpkgs }:
    let
      systems = [ "x86_64-linux" "aarch64-linux" ];
      forAllSystems = f: nixpkgs.lib.genAttrs systems (system: f (import nixpkgs {
        inherit system;
        config = { allowUnfree = true; };
      }));
    in
    {
      packages = forAllSystems (pkgs: rec {
        default = kitty-tune;
        kitty-tune = pkgs.callPackage ./nix/package.nix { };
      });

      apps = forAllSystems (pkgs:
        let
          pkg = self.packages.${pkgs.system}.default;
        in {
          default = {
            type = "app";
            program = "${pkg}/bin/kitty-tune";
          };
          kitty-tune = {
            type = "app";
            program = "${pkg}/bin/kitty-tune";
          };
        });

      devShells = forAllSystems (pkgs: {
        default = import ./nix/devshell.nix { inherit pkgs; };
      });

      overlays.default = final: prev: {
        kitty-tune = final.callPackage ./nix/package.nix { };
      };

      nixosModules.default = import ./nix/module.nix { inherit self; };
      nixosModules.kittytune = import ./nix/module.nix { inherit self; };
    };
}
