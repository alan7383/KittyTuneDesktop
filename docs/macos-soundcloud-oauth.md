# SoundCloud OAuth on macOS

KittyTune 1.4.3 registered `sc://` wrappers on Windows and Linux only. A browser
could complete SoundCloud authentication without delivering the redirect to the
waiting macOS app.

## Implementation

The packaged **main KittyTune.app** declares `sc` in `CFBundleURLTypes`. Java's
`Desktop.setOpenURIHandler` receives macOS URL events in the existing JVM. Before
opening the browser, KittyTune registers its actual jpackage bundle with Launch
Services and selects its bundle ID for `sc` links. There is no OAuth helper app,
script generation, external catcher, runtime compiler, or user-specific path.
The existing Now Playing helper is unrelated and remains unchanged.

A live attempt holds only its random state and ephemeral port in memory. The URL
event is checked against that state and the complete encoded callback is POSTed
to `127.0.0.1` using a direct connection, with redirects and proxies disabled.
The receiver validates the URI authority/path, decodes query fields exactly once,
rejects duplicates and state mismatches, limits input to 16 KiB, and accepts only
one code **or** provider error. Parameter-free `sc://auth` probes are ignored.
The code belongs to the current screen's PKCE attempt, rather than a global flow.

The server binds only to IPv4 loopback. The listener and active macOS forwarding
target are closed on cancellation, timeout, error, or receipt of a valid code.
Retry creates a new state, verifier, challenge, and server. macOS scheme
registration persists as normal app metadata; there are no callback files or
credentials to remove. Failed setup does not launch the browser. The UI reports
denial, timeout, and exchange errors and offers a fresh attempt. Token responses
and OAuth state are not logged by this flow.

Windows/Linux retain their existing registration mechanisms and token exchange;
the wrappers now also forward provider errors and use explicit IPv4 loopback.
Their callbacks use the same validation and cleanup. macOS development sign-in
requires a packaged `.app`; `gradlew run` intentionally reports an actionable
error instead of silently opening a browser with no registered app bundle.

## Automated checks

```sh
bash gradlew :test --tests '*SoundCloudOAuthTest'
bash gradlew packageDmg
```

The OAuth tests exercise decoding, invalid/ambiguous input, wrong/missing/stale
state, a real loopback POST and GET, provider denial, replay rejection, bounded
input, disposal, and native symbol availability on macOS. They do not change the
user's registered protocol handlers or contact SoundCloud. The macOS PR workflow
also checks the packaged URL scheme. OAuth tests are included in the Windows
headless test selection (native-symbol test skips outside macOS).

Additional localization verification: `LocalizationParityTest` has an existing
failure on the base commit `2596bda`: German, Hungarian, and Vietnamese each lack
29 English keys. Comparing the base and this branch gives identical missing-key
sets; the five new OAuth strings are present in all six supported languages.
This unrelated baseline failure is not repaired in this OAuth change.

## Manual release gate (not covered by the automated tests)

Use a fresh macOS test account or VM so that existing KittyTune data, protocol
handlers, and working installations remain intact. Test the final signed release
DMG, not only a development build.

1. Copy KittyTune.app from the DMG to Applications, launch, and choose SoundCloud
   sign-in. Confirm that Safari's permission prompt identifies KittyTune and
   that the redirect finishes login in the already running app.
2. Repeat from an installation path containing spaces and non-ASCII characters.
3. Check a parameter-free `sc://auth` probe: it must not report successful login.
4. Decline access in SoundCloud; check the visible error and successful fresh
   retry. Also cancel the KittyTune screen, reopen it, and try an old callback:
   it must not log in the new attempt.
5. Leave sign-in pending for five minutes; verify timeout and retry. Close the
   screen/app mid-login; no listener should remain or accept the old callback.
6. With another app registered for `sc`, verify that starting KittyTune sign-in
   selects the installed KittyTune bundle. Test with Safari and another browser.
7. Verify guest-data transfer after login, persisted login after restart, and
   sign-out followed by sign-in. Confirm Now Playing still works.
8. Repeat on Intel macOS and smoke-test Windows/Linux login and denial forwarding.

The actual account login, Launch Services delivery/default selection, competing
handlers, signed-DMG Gatekeeper behavior, Intel, and Windows/Linux interactive
flows require manual verification before release.

References: [Compose native distributions and macOS deep links](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-native-distribution.html),
[Java Desktop URL events](https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/java/awt/Desktop.html#setOpenURIHandler(java.awt.desktop.OpenURIHandler)),
[Apple CFBundleURLTypes](https://developer.apple.com/library/archive/documentation/General/Reference/InfoPlistKeyReference/Articles/CoreFoundationKeys.html).
