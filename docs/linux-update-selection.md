# Linux update asset selection

The updater previously recognized distro families from a few marker files. Systems such as openSUSE could fall through to AppImage selection, which picked the first AppImage regardless of CPU. Native-package branches also accepted a different CPU's package when a matching one was absent; the generic fallback could select an unrelated asset.

Selection now reads `/etc/os-release`, falling back to `/usr/lib/os-release` when the former is missing. It checks `ID` first, then ordered `ID_LIKE` ancestry, then the existing marker files. This follows the [os-release specification](https://www.freedesktop.org/software/systemd/man/latest/os-release.html). Metadata is parsed as data and never executed.

| Metadata family | Preferred existing artifact | Fallback |
| --- | --- | --- |
| Debian/Ubuntu ancestry | `.deb` | matching AppImage |
| Fedora/RHEL/CentOS/SUSE/openSUSE ancestry | `.rpm` | matching AppImage |
| Arch ancestry | `.pkg.tar.zst` | matching AppImage |
| Unknown | matching AppImage | no arbitrary native installer |

Architecture labels recognize x64 aliases (`x86_64`, `amd64`, `x64`) and ARM64 aliases (`aarch64`, `arm64`). Explicit mismatches and common unsupported CPU labels are excluded. Tagged compatible artifacts take precedence over legacy artifacts without a recognized architecture label. Unsupported host architectures return no asset. No new CPU architecture, native binary or package format is introduced.

Ten focused tests passed, including mixed-architecture releases with shuffled ordering, mismatch rejection, legacy names, native format preference, AppImage fallback, metadata priority and quoted ancestry, marker fallback, unsupported hosts, and example openSUSE Leap/Tumbleweed, Rocky, AlmaLinux, EndeavourOS and Mint metadata. Eight of the original nine checks failed with the former selection behavior. Current UpdateManager, the new helper and current WindowsSmtcService compiled locally against the installed dependency classpath.

This verifies selection and metadata compatibility; it does not certify launching/audio/graphics on every listed distro. Linux x64/ARM64 and Windows headless CI include these tests. Windows/macOS selection and privileged installer commands are unchanged. No performance benchmark is claimed for this compatibility fix.
