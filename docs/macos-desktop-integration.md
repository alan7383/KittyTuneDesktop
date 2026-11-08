# macOS desktop integration validation

The integration now updates the live Dock icon using `Taskbar.ICON_IMAGE`, avoids
rewriting the bundle on startup and no longer restarts the Dock on an icon change.
The status menu uses the native app menu; playback transport remains in macOS Now Playing.
The themed title bar keeps the native traffic lights and content inset. Minimizing or
hiding the main window exits requested fullscreen and releases any AWT exclusive owner.
Lyrics spring placement retains fractional coordinates rather than rounding each frame;
turning blur off also clears the previously assigned render effect.

## Automated checks

```powershell
.\gradlew.bat :test --tests '*MacWindowIntegrationTest' --tests MacDockIconTest --tests LyricsSpringPlacementTest --tests AppIconRuntimeTest
```

The lifecycle test drives real AWT hide/iconify events and verifies listener removal.
The lyrics rendering test samples motion at 60 and 120 Hz with 2x display density.
The Dock test runs only on macOS and checks the actual taskbar icon after applying the
KittyTune icon. Other checks run on Windows as well. The macOS integration workflow
runs these tests and packages the app, in addition to its existing OAuth checks.

## Validation on a Mac

These changes were developed on Windows; native Cocoa presentation and third-party
notch integration require a Mac. Use an installed app built from this revision:

1. Launch from Finder. Check the cat icon in the Dock and Cmd-Tab, then select an
   alternate icon and restore the default. Check again after relaunching.
2. Use a dark app theme, then a light one. Check the title bar and traffic lights,
   drag the window, resize it and double-click its title bar. Disable themed title
   bars and check that the native title reappears.
3. Play music and open macOS Now Playing. Verify play/pause, previous/next and seek.
   Open KittyTune's status menu: only app actions should appear there, with no second
   playback panel. Show/hide the mini player and pet, and restore the main window.
4. Check the notch utility in normal windowed and maximized modes. Enter fullscreen
   with the player, exit it, minimize the app, hide it with the close button and
   reopen it. Repeat with the native green button, including on a second display.
   The utility should return whenever the app leaves visible fullscreen.
5. Play synced lyrics on a Retina display at 60 Hz and ProMotion refresh rates.
   Observe line transitions and the end of spring motion. Scroll manually, seek,
   pause/resume and toggle blur. Check that lines neither jitter nor retain blur
   after it is disabled.

A passing offscreen test does not establish that a particular notch utility works;
record macOS version, utility/version, display scaling and a recording if it persists.
