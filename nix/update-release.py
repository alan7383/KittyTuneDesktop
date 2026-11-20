"""Update the Nix package using trusted GitHub release metadata on stdin."""

import base64
import json
from pathlib import Path
import re
import sys


def replace_once(content, pattern, replacement):
    updated, count = re.subn(pattern, replacement, content, flags=re.MULTILINE)
    if count != 1:
        raise ValueError(f"Expected one match in package.nix for {pattern!r}")
    return updated


def update_package(content, release):
    tag = release["tag_name"]
    if not re.fullmatch(r"v[0-9]+\.[0-9]+\.[0-9]+", tag):
        raise ValueError(f"Invalid KittyTune release tag: {tag!r}")

    version = tag[1:]
    version_pattern = r'^(  version = ")([0-9]+\.[0-9]+\.[0-9]+)(";)$'
    current_version = re.search(version_pattern, content, re.MULTILINE)
    if current_version is None:
        raise ValueError("Could not find the current version in package.nix")
    if tuple(map(int, version.split("."))) < tuple(map(int, current_version[2].split("."))):
        raise ValueError(f"Refusing to downgrade Nix from {current_version[2]} to {version}")

    updated = replace_once(content, version_pattern, rf"\g<1>{version}\g<3>")
    for arch in ("amd64", "arm64"):
        name = f"kitty-tune_{version}_{arch}.deb"
        assets = [asset for asset in release["assets"] if asset["name"] == name]
        if len(assets) != 1 or assets[0]["state"] != "uploaded":
            raise ValueError(f"Expected exactly one uploaded asset named {name}")
        digest = re.fullmatch(r"sha256:([0-9a-fA-F]{64})", assets[0].get("digest") or "")
        if digest is None:
            raise ValueError(f"Missing or invalid SHA-256 digest for {name}")

        nix_hash = base64.b64encode(bytes.fromhex(digest[1])).decode("ascii")
        hash_pattern = rf'^(      arch = "{arch}";\n      hash = "sha256-)[^"]+(";)$'
        updated = replace_once(updated, hash_pattern, rf"\g<1>{nix_hash}\g<2>")

    return updated


if __name__ == "__main__":
    package = Path(__file__).with_name("package.nix")
    original = package.read_text(encoding="utf-8")
    updated = update_package(original, json.load(sys.stdin))
    if updated != original:
        package.write_text(updated, encoding="utf-8", newline="\n")
