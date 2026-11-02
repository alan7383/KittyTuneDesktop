{ pkgs ? import <nixpkgs> { config.allowUnfree = true; } }:

import ./nix/devshell.nix { inherit pkgs; }
