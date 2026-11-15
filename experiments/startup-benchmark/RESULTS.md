# Validation so far

End-to-end startup timing is pending a quiet desktop session. Exploratory
launches overlapped other CPU-heavy work and are not used as evidence of a
startup speedup. No wall-clock startup or CPU reduction percentage is claimed.

| Check | Baseline | Candidate |
|---|---:|---:|
| PowerShell processes per Windows device probe | 2 | 0 |
| Bluetooth enumeration when no headset matches | Always | Skipped |
| Audio endpoint names on the test PC | 5 | Same 5 |
| Process handles before/after 200 native endpoint reads | Not measured | 510 / 510 |

The first two rows describe the executed query path, not measured startup
latency. Endpoint names matched `Get-PnpDevice -Class AudioEndpoint -Status OK`
on the same Windows PC. No device names or account information are published.

The full local headless suite passed: 308 tests, 0 failures, 0 errors, 3 skipped.
The six new selection tests passed separately; the speaker-only regression
failed before deferring the Bluetooth query and passed afterwards.

Native endpoint reads were exercised on Windows. Physical headset hotplug,
audio playback and installer packaging have not been verified for this change.
The local headless build reused unchanged Widevine/CDM native resources because
MinGW is unavailable; the DSP module was built with MSVC.

When timing resumes, use the runner in this directory, retain every measured
pair, and add median/p95 drawn-window latency and per-probe latency alongside
the sanitized raw samples. Reboot/cold-disk performance needs a separate test.
