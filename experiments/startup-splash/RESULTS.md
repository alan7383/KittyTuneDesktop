# Animated startup — Windows comparison

| Endpoint | Original median / p95 | Splash median / p95 | Paired median delta (95% bootstrap) |
|---|---:|---:|---:|
| First visible feedback | 7.340 / 8.357 s | 0.150 / 0.152 s | -7190.8 ms (-8100.5, -7093.2) |
| Underlying main frame | 7.365 / 8.370 s | 7.288 / 7.643 s | -40.5 ms (-721.7, -2.8) |
| Fully revealed main window | 7.365 / 8.370 s | 7.562 / 7.891 s | 233.0 ms (-462.0, 268.4) |

Negative differences favor the splash candidate. The fully revealed endpoint includes the default fade; early feedback is not counted as player readiness.
Publication gate passed: **False**. See PROTOCOL.md for criteria fixed before the paired run.

| Pair | Order | Original main / revealed ms | Splash main / revealed ms | Splash feedback ms |
|---:|---|---:|---:|---:|
| 1 | original first | 8266.7 / 8266.7 | 7545.1 / 7804.7 | 147.8 |
| 2 | splash first | 8425.6 / 8425.6 | 7186.8 / 7503.8 | 151.9 |
| 3 | original first | 7281.0 / 7281.0 | 7261.9 / 7538.7 | 149.7 |
| 4 | splash first | 7333.8 / 7333.8 | 7313.3 / 7584.7 | 146.2 |
| 5 | original first | 7151.8 / 7151.8 | 7149.0 / 7420.2 | 150.0 |
| 6 | splash first | 7258.9 / 7258.9 | 7695.1 / 7937.0 | 151.9 |
| 7 | original first | 7395.3 / 7395.3 | 7334.8 / 7610.3 | 151.6 |
| 8 | splash first | 7661.0 / 7661.0 | 7028.8 / 7287.4 | 148.2 |

Method and limitations:

- Equivalent 1.8.0 development builds from104f39bd; main JARs repacked identically with identical native DSP resources, launcher and runtime. This is not a comparison with the minified installed release.
- Eight alternating AB/BA pairs; one warmup per variant excluded. Restarted processes, warm filesystem caches, no reboot/cache eviction. Every accepted slow trial retained.
- Same isolated setup-complete guest seed with empty cache/library. Both arms disable global media keys, Discord, Windows SMTC registration and Zapret configuration writes. No audio playback started and no installed-app changes.
- Below-normal priority and a40% system CPU gate for three seconds before launch allow concurrent user playback. Background load samples are retained. These results do not establish a universal zero-overhead guarantee.
- Actual renderer, dimensions and DPI verified after timed windows. CPU/private memory/working set samples include five seconds after reveal; observer attachment is excluded. GPU memory and physical audio quality are not measured.
- The main-frame endpoint requires main router composition, content drawn and owned main visibility. Full reveal additionally requires a draw with fade cover at zero. Neither measures online feed readiness or validates every input action.
- p95 and seeded bootstrap intervals are descriptive for eight pairs. No Linux runtime, reboot-cold-disk or other hardware performance claims.

## Accepted visual transition

After reviewing these results, the project contributor explicitly accepted the approximately271ms main-frame-to-reveal interval for the default fade, provided underlying startup did not regress. The initial strict full-reveal gate above remains recorded as failed; the measurements and criterion have not been rewritten. Underlying main-frame time was not slower in these eight paired observations. This is not a guarantee of zero overhead on every machine.

Local validation:486 headless tests,0 failures,0 errors,3 skips; native DSP built locally. Two additional deterministic fade render cases passed in a focused runner, covering intermediate frames, first-draw modifier removal, disabled fade and an already drawn window. A fresh static review found no actionable defects. Actual mixed-DPI handoff/white-flash capture and Linux runtime behavior have not been verified.

The measured base is104f39bd822a1bafd1e2f96d3ca9422558997b54(v1.8.0). Upstream subsequently advanced by four changes to lyrics, likes, stats artwork and queue archiving; none changes the splash integration files. CI on the pull request will exercise the merge with current master; these historical measurements are not relabeled as a benchmark of that newer base.
