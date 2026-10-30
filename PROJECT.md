# Project: KittyTuneDesktop - Restore Dynamic Icon Refresh & Shell Integration

## Architecture
- **Desktop Application (JVM / Compose Multiplatform)**:
  - `src/main/kotlin/com/alananasss/kittytune/Main.kt` & `AppBootstrap.kt`: Application entry points, lifecycle, window creation, theme and icon variant observation.
  - `src/main/kotlin/com/alananasss/kittytune/core/AppIconRuntime.kt`: Manages runtime AWT/Win32 window icon ladders (`target.iconImages`) via `WM_SETICON` (16, 20, 24, 32, 48, 64, 128, 256 px).
  - `src/main/kotlin/com/alananasss/kittytune/core/AppIconInstaller.kt` & `WindowsAppIdHelper.kt`: Windows shortcut patching, multi-res ICO generation in `%APPDATA%\KittyTune\icons\`, and shell notification (`SHChangeNotify`, `ie4uinit`, `StartMenuExperienceHost`).
  - `src/main/kotlin/com/alananasss/kittytune/ui/screens/settings/AppearanceSettingsScreen.kt`: UI for selecting icon variants.
- **SMTC Subsystem (.NET Bridge)**:
  - `src/main/resources/native/WindowsSmtcBridge.cs` (compiled to `WindowsSmtcBridge.exe`): Standalone process managing WinRT `MediaPlayer` and `SystemMediaTransportControls`.
  - Sets its own `SetCurrentProcessExplicitAppUserModelID("KittyTune.KittyTune")` and ensures a Start Menu shortcut with `System.AppUserModel.ID` exists so the Windows media flyout identifies the player as "KittyTune".
  - Communicates with JVM `WindowsSmtcService.kt` via stdio JSON messages.

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Dynamic Taskbar Icon Update | Changing app icon updates the taskbar button icon dynamically without restarting app or explorer | M1 | ORIGINAL_REQUEST §R2 |
| 2 | Dynamic Window Icon Update | Title bar, Alt-Tab, and window HWND reflect the active icon variant via `WM_SETICON` | M1 | ORIGINAL_REQUEST §R2 |
| 3 | Remove Redundant JVM AppUserModelID | Remove early `SetCurrentProcessExplicitAppUserModelID` from JVM startup to allow Taskbar to read `WM_SETICON` | M1 | Survey consensus |
| 4 | Streamline SMTC Bridge Delay | Remove awkward 2500ms delay in `PlayerViewModel.kt` on icon change | M1 | Survey consensus |
| 5 | Shortcut Icon Updating | Desktop and Start Menu `.lnk` files point to the active `.ico` file | M1 | ORIGINAL_REQUEST §R2 |
| 6 | Shortcut AppUserModelID Preservation | Start Menu shortcut retains `PKEY_AppUserModel_ID` ("KittyTune.KittyTune") for SMTC identity | M1 | ORIGINAL_REQUEST §R1, R2 |
| 7 | Windows Shell Cache Invalidation | Shell notifications (`SHChangeNotify`, `ie4uinit`, Start Menu host refresh) force Explorer to display updated shortcut icons | M1 | ORIGINAL_REQUEST §R2 |
| 8 | Retain Generated Icon Assets | Keep previously generated variant `.ico` files in app data so shortcuts/pins don't show broken icons | M1 | Survey consensus |
| 9 | Automated Verification Suite | Unit/Integration tests verifying shortcut patching, property store preservation, and shell notification | M2 | ORIGINAL_REQUEST Acceptance Criteria |
| 10 | Regression & Startup Crash Prevention | Ensure application startup is clean, fast, and free of startup crashes or SMTC regressions | M3 | ORIGINAL_REQUEST Acceptance Criteria |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Restore Dynamic Icon Refresh Implementation | Remove early JVM `SetCurrentProcessExplicitAppUserModelID`; restore `WM_SETICON` taskbar updates; streamline `AppIconInstaller` shortcut updating with `WScript.Shell`; preserve Start Menu AUMID; notify Windows shell; retain icon assets | none | DONE |
| M2 | Dual-Track Test Suite Creation | Create comprehensive tests in `src/test/kotlin/` verifying shortcut `.lnk` patching, `IPropertyStore` preservation, and Windows Shell notification | none | DONE |
| M3 | Full Verification, Adversarial Hardening & Audit | 100% test pass; Reviewer approval; Challenger stress-testing; Forensic Auditor verification | M1, M2 | IN_PROGRESS |

## Interface Contracts
### `AppIconRuntime` ↔ `AWT / Win32 Window`
- `AppIconRuntime.apply(variantKey: String, window: Window?)`:
  - Generates scaled `BufferedImage` ladder (`[16, 20, 24, 32, 48, 64, 128, 256]`).
  - Sets `target.iconImages = images` on `Window.getWindows()`.
  - Native AWT peer dispatches `WM_SETICON` (`ICON_SMALL` & `ICON_BIG`).

### `AppIconInstaller` ↔ Windows Shortcuts & Shell
- `AppIconInstaller.apply(variantKey: String)`:
  - Generates multi-res ICO to `%APPDATA%\KittyTune\icons\app_icon_$variantKey.ico`.
  - Updates matching shortcuts' `IconLocation` to the new ICO.
  - Preserves `System.AppUserModel.ID` (`{9F4C2855-9F79-4B39-A8D0-E1D42DE1D5F3}`, 5 = `"KittyTune.KittyTune"`) on the Start Menu shortcut.
  - Calls `notifyWindowsShell()`: `SHChangeNotify(SHCNE_ASSOCCHANGED)`, `ie4uinit`, and `StartMenuExperienceHost` restart.

### `WindowsSmtcService` ↔ `WindowsSmtcBridge.exe`
- Stdio JSON protocol.
- Bridge independently calls `SetCurrentProcessExplicitAppUserModelID("KittyTune.KittyTune")` and ensures Start Menu shortcut presence.
- Main JVM does not participate in process AUMID registration.

## Code Layout
- `src/main/kotlin/com/alananasss/kittytune/Main.kt`
- `src/main/kotlin/com/alananasss/kittytune/AppBootstrap.kt`
- `src/main/kotlin/com/alananasss/kittytune/core/AppIconRuntime.kt`
- `src/main/kotlin/com/alananasss/kittytune/core/AppIconInstaller.kt`
- `src/main/kotlin/com/alananasss/kittytune/core/WindowsAppIdHelper.kt`
- `src/main/kotlin/com/alananasss/kittytune/core/WindowsSmtcService.kt`
- `src/main/kotlin/com/alananasss/kittytune/ui/player/PlayerViewModel.kt`
- `src/main/resources/native/WindowsSmtcBridge.cs`
- `src/test/kotlin/com/alananasss/kittytune/core/WindowsAppIdHelperTest.kt`
- `src/test/kotlin/com/alananasss/kittytune/core/AppIconRuntimeTest.kt`
