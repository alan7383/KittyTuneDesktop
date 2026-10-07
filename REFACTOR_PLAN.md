# Refactoring and lightweight builds

Requested scope: refactor and optimize the Android/Windows implementation before renewed upstream review; add optional lightweight builds with SoundCloud/local audio only. Preserve the full build. No claim of globally optimal performance without measurements.

## Required outcome

1. Review the introduced Connect code and its interaction with persistence, player state, queues, UI, network lifecycle and native Windows audio.
2. Remove repeated queue copies/hashing/serialization from frequent player updates, move expensive packet work off the UI thread, bound pending work, and test reconnect/handoff/cancellation invariants.
3. Keep Android idle/background costs bounded: no new wake lock, scans, service or recurring discovery. Verify actual device behavior where connected hardware permits it.
4. Offer Android and Windows lightweight artifacts with SoundCloud, local files, queues, downloads and Connect. Other music providers and optional service/social integrations must be excluded from their reachable behavior and unnecessary dependencies/resources removed from those builds.
5. Keep build modes explicit and reproducible; full upstream behavior and local user data must survive.
6. Record before/after measurements, meaningful regression evidence and the limits of validation. Update both upstream proposals only after required changes are verified.
7. Publish updated source and reviewable artifacts, with accurate author attribution and no user credentials/profile files.

## Findings and work log

- Baseline reviewed: Windows PR 75 at `0b21665`; Android Connect proposal at `a2606769`; fork release at `ff547519`. Android PR returned to draft for this work.
- Frequent Connect capture copies the queue with `drop().take()`; persistent snapshots also allocate two ID lists per comparison. Every outbound update hashes the full queue and formats a hex fingerprint before deciding to suppress the message.
- Encryption/JSON/gzip currently run synchronously in the caller, including main-thread player actions.
- `SyncPeers.find()` repeatedly parses the whole preferences JSON; reconnects and incoming messages re-enter that path.
- Pending remote commands have a timeout but are not associated with the transport that will acknowledge them. Link teardown cannot fail the right pending commands immediately.
- Device lifecycle, transfer rollback/cancellation, UI command routing, relay limits, clean-build dependencies and packaging still require implementation/review and verification.
- Implemented cached immutable queue windows, session-local fingerprint/publication deduplication, compact snapshot persistence, cached peer parsing, bounded outbound/inbound workers, session-owned pending ACKs, and callback-based player preparation on both platforms.
- Network migration now uses Android default-network capability/link events, invalidates old connection generations, binds Internet DNS/sockets to the announced route and coalesces the short callback burst (150 ms, no polling). LAN retains its existing direct socket path. The PC keeps its relay socket on standby during LAN playback and promotes it only after an authenticated phone hello.
- Fixed eager player-cache initialization: a restored nonempty queue is captured during PlayerViewModel init, so the cache must be initialized before that block. Verified by launching both applications with the real restored queue after the fix.
- Device selector rows now show connection type instead of duplicating the track shown by the main player.

## Evidence

`connectBenchmark` uses a synthetic 500-track queue and reports fingerprint CPU/allocation and full/compressed/delta packet sizes. It does not open windows, use personal profiles or play sound. Record runs before and after changes; do not use wall-clock timing as a flaky test gate.

Measured on the same host/JDK: baseline fingerprint 29,840 ns / 70,131 allocated bytes per 500-track call; refactored standalone fingerprint 10,834 ns / 27,389 bytes. A captured, unchanged 500-track queue with publication suppressed measured 38 ns / 84 bytes per call (includes snapshot construction). This is an isolated microbenchmark, not a battery or whole-application performance measurement. Packet sizes remained compatible: full 62,276 bytes, gzip 4,156, delta 564.

Final checkpoint: Windows headless suite 152 tests, zero failures/errors; Android focused Connect/SyncPlayback suite 34 tests, zero failures/errors; Android debug APK and Windows release distributable built successfully. Live LAN handoff copied 467 tracks and preserved track/index/position, with only the destination playing. The first Internet-to-Internet Wi-Fi/cellular migration delivered the full phone state to the PC in approximately 4–5 seconds. The user subsequently reported that the installed build works well and requested this commit/push checkpoint. The final automated LAN-to-cellular-to-LAN cycle was not completed because USB ADB repeatedly disconnected; do not treat a relay health response as an end-to-end playback test.

USB ADB on the connected phone repeatedly becomes offline; wireless ADB remains available on Wi-Fi. Read-only debug-only diagnostics are DUMP-permission protected and expose route/state counts without pairing secrets or URLs. They are absent from release source sets.

Status: in progress. Baseline release remains available; it is not the optimized/lightweight deliverable.
