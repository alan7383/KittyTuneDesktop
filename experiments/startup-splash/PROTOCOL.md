# Startup splash comparison

Compare identical-base development images on Windows, with the unchanged control and full animated-splash candidate. Repack both main JARs identically and use the same native DSP bytes, launcher, dependencies, JVM flags and runtime. The candidate adds the packaged GIF, baked text atlas, real milestone notifications, settings option and default fade. This is not a comparison between a development build and a minified published release.

Eight alternating AB/BA pairs after one excluded warmup per variant. Restart processes without evicting filesystem caches. Save every accepted trial, including slow ones. Each launch waits for three one-second system CPU readings below40%. The installed player may keep playing; test processes run below normal priority. Record load throughout. No UI automation, builds or unrelated benchmarks during timed runs.

Use the same isolated, setup-complete guest seed in both arms, with an empty library/cache and refused loopback HTTP proxy. Disable global shortcuts and Discord in the seed. The same Java agent in both arms prevents Windows SMTC registration and Zapret configuration writes; discovery remains unchanged. Required transformations are asserted. Do not touch the installed application or its profile, play test audio, or send media keys.

Record independently:

1. First owned visible main or JavaSplash window (visual feedback).
2. Main content drawn, main router composed, and owned main window visible (underlying main frame).
3. Candidate's first drawn frame with the fade cover completely gone, plus main visibility (fully revealed). Control uses endpoint2.

Also save process-tree CPU/private memory/working set until five seconds after reveal. GPU memory and physical audio quality are not measured. Observe actual renderer, main dimensions and display scale after measurements; reject missing or unequal renderer metadata. Do not infer rendering from a requested flag.

Report median/p95 per endpoint and paired median differences with a seeded paired bootstrap95% interval. Eight samples give descriptive estimates, not a universal no-overhead guarantee. Predeclared publication gate: first feedback materially earlier; fully revealed paired median difference no greater than zero and upper bootstrap bound no greater than100ms. Also report the underlying-frame difference; do not hide a slower reveal behind a faster splash. If the gate fails or evidence is inconclusive, do not open the requested performance-neutral PR without further evidence or the user's agreement to a tradeoff.

Keep visual/behavioral checks separate from performance trials: real percentage rendering, missing asset, second launch, settings None, close/reopen, DPI and white-flash handoff. Do not claim Linux runtime verification from Windows results or packaging code alone.

Subsequent decision: the strict full-reveal gate failed. After the complete results were disclosed, the contributor explicitly accepted the short visual fade interval on28November2026. See RESULTS.md; the original gate and raw data remain unchanged.
