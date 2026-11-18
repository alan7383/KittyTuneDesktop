the shared audio engine built and configured every effect processor during player construction, even before any PCM existed. saved FX/EQ settings also updated the DSP directly from the caller's thread.

this creates the effect graph on the decoder's first PCM block and applies pending FX, EQ and stretch settings at block boundaries. it keeps the latest pre-play settings and automix ratios, configures processors before use, and releases a graph that loses a race with engine disposal. stopping or releasing an idle engine does not create DSP resources.

this is shared code for Linux and Windows. the six new tests run in the Windows headless job and both Linux jobs. no UI, DSP equations or signal order changed.

validation:

- the idle-allocation regression failed on the old engine. six new tests now pass, covering idle close, first-block mono/EQ, later changes, automix ratios and use after release.
- 73 relevant local audio, limiter, trim, HLS, DRM-session and updater regressions passed. these used the installed 1.6.0 dependencies; a fresh full source build remains for CI.
- a separate review found no important lifecycle or audio-output issues.
- five alternating pairs of fresh JVM launches, with two excluded warmups, the same runtime/dependencies/profile and AOT disabled for both: median main-screen drawn + visible startup was 6.376 s before and 6.109 s after (4.2%). one pair was slower. this is a modest whole-window change, not a large whole-app speedup.

| pair | before, ms | after, ms |
|---|---:|---:|
| 1 | 6918 | 6185 |
| 2 | 6376 | 6079 |
| 3 | 6269 | 6109 |
| 4 | 7206 | 7355 |
| 5 | 6168 | 5942 |

filesystem caches were warm. all paired samples were retained, launches were gated on quiet CPU, and the endpoint required the actual main screen's draw marker and a visible window owned by that process. these timings are from Windows hardware; Linux coverage is functional CI, not a claimed Linux timing result. preparing the already-existing AOT cache for the personal installed build is separate work and its gain is not attributed to this PR.
