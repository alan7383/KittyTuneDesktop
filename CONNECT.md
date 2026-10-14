# Connect playback

Connect controls and moves playback between paired Android and desktop clients.
This desktop implementation builds on the Windows packaging and rendering work
merged in [#75](https://github.com/alan7383/KittyTuneDesktop/pull/75).
The matching Android implementation is proposed in
[kittytune#54](https://github.com/alan7383/kittytune/pull/54).
Updated clients are required at both ends; stock clients can still exchange the
existing history/library sync fields, but cannot provide live Connect controls.

## Pair and choose an output

1. Open **Device sync** on the desktop and enable **Accept connections**. Pair
   the phone using the existing pairing code or QR flow. Direct LAN control uses
   TCP port 47654 alongside the existing sync listener.
2. Click the **Devices** button in the player bar. The right sidebar can show
   devices even before a track is playing. Select a connected device to move
   the current track, queue and position; select **This device** to bring them
   back. The player, keyboard shortcuts, queue and mini-player display and
   control the selected output.
3. **Play independently** keeps the device's own playback and queue, disabling
   live remote control and playback snapshots while leaving the existing
   history/favorites sync options available. It does not add an offline cache.

The destination prepares the track and acknowledges it before the source is
paused. A failed preparation keeps the source playing. A failed start only
rolls back after the destination acknowledges pause. Commands acknowledge their
result, and seek/queue guards reject stale track or queue references.

## Optional Internet relay

Direct Android/desktop LAN control needs no relay. In the device panel's
settings, **Internet connection** accepts an `https://` or `wss://` relay address.
The self-hosted relay forwards encrypted control/state frames; it does not
forward audio. Each renderer resolves and streams music from its own sources.
There is no default public server or committed personal endpoint.

If the relay runs on the desktop host, **Server runs on this computer** lets
that desktop connect through `ws://127.0.0.1:8787`, while the public address is
shared with the phone. Plain `ws://` is accepted only for loopback addresses.
The relay and hosting PC/tunnel must remain available for Internet control.

Relay source and setup:
[relay/](https://github.com/VeXEveryOne/kittytune/tree/codex/playback-handoff/relay).

## Bounds and checks

Frames use pair-derived AES-GCM encryption, fresh connection challenges and
ordered session messages. Payload/decompression sizes and command queues are
bounded. Queue entries contain source references and display metadata; larger
states negotiate gzip, unchanged queues are omitted, and playhead projection
uses a monotonic clock. Reconnection uses bounded backoff.

- Queues larger than 500 tracks transfer a window around the active item.
- Local files are not copied; the destination needs its own access to a source.
- Together session membership, audio effects and AI results are not carried in
  the playback snapshot. Mixed Connect/Together sessions need interactive
  validation. Local Together callbacks retain their local command path.
- Direct LAN initiation is provided by Android. Desktop-to-desktop live control
  can use the optional relay.

Run the isolated suite and Windows package with
`scripts/build-windows.ps1 -JavaHome <jdk-21>`; see [WINDOWS.md](WINDOWS.md).
`headlessTest` includes codec/replay, handoff failure, reconnect, queue bounds,
localization and offscreen device-picker checks. Its LAN integration test uses
an ephemeral loopback port, a synthetic renderer and no audio engine, verifying
encrypted commands, state-before-ack ordering, stale-track rejection and
invalid-token refusal. It never opens the real fixed-port sync listener.

`connectBenchmark -PdesktopOnly=true` reports isolated packet/allocation
baselines, without a CI timing threshold or a battery-life claim. Real-device
handoff, Internet transport, AI inference and macOS/Linux builds must be checked
separately from this headless suite.
