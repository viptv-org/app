# TV frame budget — what was measured and what changed (2026-10-19)

Target: 60 fps on the ONN TV (1920×1080 @ density 320, Android 14, armeabi-v7a,
display runs 4K/60 and the app renders 1080p). No visual changes are allowed:
every change below was proven pixel-identical before it was kept.

## How to measure (repeatable)

All scripts hard-timeout every device call (`ADB_TIMEOUT`, `adb_t()`); none of
them can hang on a dead device. Run one at a time, never in parallel with a build.

- `tools/tv_ab.sh <label> [bursts] [presses]` — the number to trust. One app
  session, several measured bursts, aggregated frame-duration percentiles and
  the **missed-vsync rate**. `missed_vsync` is the metric that matches what a
  viewer sees; the percentile figures include compositor wait time and move very
  little.
- `tools/tv_trace.sh <out>` — atrace capture (view/gfx + app tags) of the same
  navigation path. Parse: `animation`, `traversal`, `Record View#draw()`,
  `Compose:semantics:*`.
- `tools/tv_state_shot.sh <png>` — deterministic navigation to the first
  portrait shelf for screenshot comparison.
- `tools/tv_framestats.sh <label>` — per-frame phase breakdown from
  `dumpsys gfxinfo framestats`.

The live catalog is randomised per launch, so device screenshots are **not**
comparable across restarts. Rendering changes are verified with
`TvCardVisualTest` (deterministic fixtures, `VIPTV_CARD_CAPTURE_DIR=<dir>`),
comparing the PNGs from a stashed build with `compare_images`/PIL; the bar is
byte-identical.

## Measured result (interleaved A/B, same session)

| metric | before | after |
|---|---|---|
| missed vsyncs | 7.3–7.8 % (3 runs) | 1.6–4.0 % (5 runs) |
| UI-thread 90th percentile | 36 ms | 32–34 ms |
| median frame duration | 22 ms | 21–23 ms (noise) |
| median UI-thread time/frame (trace) | 6.6 ms | 5.3 ms |

The median frame duration barely moves because the app's own work is only
5–8 ms of the 16.6 ms budget; the rest is the compositor/buffer handshake. What
improves is the tail: fewer frames miss their deadline.

Per-frame cost on this box: UI thread ~5 ms, RenderThread ~9 ms (≈6 ms of which
is buffer/handoff), rest is compositor wait. The device is not the limit — the
app was doing avoidable per-frame work.

## What actually mattered

1. **Accessibility nodes.** The TV has `flar2.homebutton` bound as an
   accessibility service, so Compose re-walks and diffs the semantics tree on
   every focus and scroll change. Collapsing each catalog card to **one** node
   (description + role + click action + restated `Focused`) halved that cost.
   Deleting invisible overlays from the tree (`TvHomeVisibility`) does the same
   and fixes a real a11y bug (hidden buttons were announced).
   Do not split the card into a focusable node plus a clearing container: two
   nodes per card measurably regressed the miss rate back to baseline.
2. **Focus-scoped recomposition.** A card's focus state is read only by the
   leaves that change (ring, play badge, caption). Adding/removing the caption
   block or play badge on focus invalidated the layout of the whole shelf
   (5–9 ms per key press); they are now always composed and alpha-toggled, which
   is pixel-identical (only one of the two states is ever visible).
3. **Row scope.** Reading `browsing`/`activeRow` inside the `LazyColumn` item
   scope recomposed every visible shelf on each focus move. The row fade is
   driven from the screen and read only inside `graphicsLayer`.
4. **Theme identity.** `viptvTypography()` rebuilt the `FontFamily` on every
   composition, invalidating `LocalTextStyle` for every text in the tree.
5. **Backdrop layer (reverted).** Compositing the static backdrop as one reused
   offscreen layer was a marginal CPU win, but it rounded 6041 pixels (0.3%) by
   one level each, so the home screen was no longer pixel-identical. It was
   dropped; the frame-drop win comes from the items above.

## Measured and rejected: hoisting the shelf scroll out of layout

`rememberTvShelfScroll` animates the `LazyListState` offset, which re-measures
and re-records the row. A variant that hops the list to its target once and
animates a translation instead was implemented and **proven pixel-identical**
(`TvShelfScrollVisualTest` compares 15 frames, 10 of them inside the 300 ms
animation in both directions — all byte-identical), but it is **slower on this
GPU** and was therefore not kept:

| variant | cpu50 | gpu50 | missed vsync |
|---|---|---|---|
| animate the list offset (current) | 22 ms | 9–10 ms | 3.9 % |
| jump + `graphicsLayer` translation | 25 ms | 16 ms | 5.1 % |
| jump + draw-time translate + clip | 23–24 ms | 14–15 ms | 5.7–6.2 % |

The translation needs a clip outside it, and either the clip layer or the
full-width composited layer costs this GPU 5–6 ms per frame — more than the
4.5 ms per press it saves on the UI thread. Do not retry this without a GPU
that composites layers cheaply.

Remaining headroom is therefore in the pixels (hero/backdrop fill, ~6 Mpx per
frame) rather than in layout.


---

## 2026-10-19 — tooling correction, no new perf claims

- `tools/tv_cycle.sh` never ran: it referenced `adb_t` and `$SERIAL`, which are local
  to the other scripts, and it called `tv_bench.sh` (percentiles) instead of the R3
  `tv_ab.sh` metric. It now builds `assembleRelease`, installs with a timeout-guarded
  adb call, and runs `tools/tv_ab.sh`.
- PERF-05 (shelf re-measure), PERF-06 (first-render 60 fps), PERF-07/08 (desktop/web
  interaction), PERF-09 (decode spikes) and PERF-10 (blur cost) still have **no**
  release-build measurement. They are not claimed as fixed.
- The deterministic capture harness (`TvCardVisualTest`, `TvShelfScrollVisualTest`)
  was extended with the canvas §A1 card-state matrix; captures live in
  `docs/evidence/kmp-audit-2026-10-19/tv-card-states.png`.

---

## 2026-10-19 (later) — PERF-05..10: procedure, evidence format, queued experiments

This section adds **no frame numbers**. All six items are still **unmeasured**; what
follows is what each one must satisfy, the exact row that gets pasted into this file
once it has been run, and which experiments are queued but *not applied*. Where an
`ISSUES.md` acceptance criterion contradicts a measurement already recorded in this
file, the conflict is stated rather than resolved silently.

Run rules are unchanged and apply to every item: **release APK only**
(`./gradlew :androidApp:assembleRelease --max-workers=2`, installed via
`tools/tv_cycle.sh <label> <bursts>`), one run at a time and never in parallel with a
build (R7), every device call behind `timeout`/`adb_t` (R4), serial
`192.168.88.30:5555`. Raw output belongs in
`docs/evidence/<YYYY-MM-DD>-tv-perf/<label>/` together with `apk.sha256` and
`dumpsys package` flags — a number whose `/tmp` dump was deleted is not evidence.

| item | instrument (committed) | pass bar | status |
|---|---|---|---|
| PERF-05 | `tools/tv_trace.sh` + `tools/tv_framestats.sh` | no shelf measure on a press that does not scroll; during the 300 ms scroll, layout+record p99 ≤ 8 ms and no frame > 24 ms | unmeasured |
| PERF-06 | *(script not committed — see below)* + `dumpsys SurfaceFlinger --latency` | missed vsync < 1.0 % over the first 30 s and no present gap > 33 ms | unmeasured; `tv_ab.sh` cannot see this window |
| PERF-07 | *(probe not committed — see below)* | 0 gaps > one refresh interval + 8 ms while scrolling; p99 ≤ 2 intervals; 5 alternating runs | unmeasured; no instrument exists |
| PERF-08 | isolated wasm dist + `PerformanceObserver` longtask | 0 long events > 50 ms in a 30 s scroll, 3 runs | unmeasured |
| PERF-09 | `tools/tv_frames.sh` + `tools/tv_framestats.sh` + `tools/tv_trace.sh` | no frame > 24 ms, `Slow bitmap upload` ≈ 0, cold **and** warm cache | unmeasured |
| PERF-10 | `tools/tv_ab.sh backdrop-hero` / `backdrop-browse` (gpu50) + byte-identical captures | gpu50 ≤ baseline + 1 ms, missed vsync inside the 1.6–4.0 % band above, captures byte-identical | unmeasured; no baseline exists yet |

**Baseline ordering.** The home shelf changed on 2026-10-19 (row paging: trailing
skeletons, a per-row cursor effect). Any PERF-05/PERF-09 baseline taken before that
change is not comparable to a build after it — capture the baseline on a build that
includes it, and say which commit the baseline came from in the evidence directory.

### PERF-05 — shelf re-measure on a focus move

There is no `shelfmeasure` trace section and none is needed: `tools/tv_trace.sh`
already captures with `atrace -a $PKG view gfx`, and Compose emits its own
`Compose:measure` / `Compose:recompose` slices under the app tag.

1. `tools/tv_cycle.sh shelf 5` — release install, then the deterministic path
   (`am start`, 13 s, `keyevent 23`, 13 s, down, down — identical to `tv_ab.sh`).
2. Sanity-check the capture before counting anything: `grep -c "Compose:measure"`
   on the trace must be non-zero, otherwise the run reports nothing and is not a
   pass. If the slices are absent, fall back to the `framestats` layout column
   only and record the substitution.
3. Two windows counted separately:
   - **no-scroll**: 20 × `keyevent 22` then 20 × `21` inside the safe window
     (`rememberTvShelfScroll` returns `null` there) — shelf measures must be 0;
   - **scroll**: 40 × `keyevent 22`, each crossing the trailing edge and running the
     300 ms `animateScrollBy` — measures are expected, count them.
4. `tools/tv_framestats.sh shelf` in both windows; the layout median difference is
   the per-press measure cost.

**Pass bar:** 0 shelf measures in the no-scroll window, and in the scroll window
layout+record p99 ≤ 8 ms with no frame > 24 ms.

Paste when run:

```
| PERF-05 | release <sha256:8> | <trace file> (40 presses) | measures/no-scroll-press=<n> | measures/scroll-press=<n> | layout p50=<n> p99=<n> ms | frame max=<n> ms | PASS or FAIL |
```

**Conflict to settle first:** the AC in `ISSUES.md` (“no LazyRow measure attributable
to a focus move”) contradicts the hoisting experiment that was measured slower on
this GPU and rejected (the table earlier in this file). If the no-scroll window is
clean and the scroll window stays inside the budget, the honest closure is
*accepted cost* with the number — not another hoisting attempt.

### PERF-06 — 60 fps on first render, no warm-up

`tv_ab.sh` structurally cannot measure this: its first `gfxinfo reset` lands ≈30 s
after launch, so the window the AC names is excluded by construction.

1. Build and install the release APK; then
   `am force-stop`, `dumpsys gfxinfo <pkg> reset`,
   `dumpsys SurfaceFlinger --latency-clear <layer>`, `am start -W`, sleep 30,
   pull `gfxinfo` and `--latency`.
2. Two states must be reported, never one:
   - `cold` — cold process with a restored session (AUTH-03 path);
   - `fresh` — `pm clear`, i.e. the “after install” wording, which includes pairing
     and may legitimately not reach Home inside 30 s. That outcome is a finding, not
     a reason to retry until it looks better.
3. ≥ 3 runs per state, alternating installs for an A/B.

**Pass bar:** missed vsync < 1.0 % of frames rendered and no present gap > 33 ms after
the first content frame. Layer discovery as in `tools/tv_cadence.sh`.

Paste when run:

```
| PERF-06 | state=cold or fresh | window=30 s | frames=<n> missed_vsync=<n> (<n>%) | gaps>33ms=<n> max_gap=<n> ms | PASS or FAIL | apk <sha256:8> |
```

### PERF-07 — desktop scroll/interaction budget

No frame-pacing instrument exists for the desktop path; `qa_desktop_run.py` and
`verify_desktop.py` check playback and packaging, not UI cadence.

1. One JVM artifact per side of the A/B; launch with `:composeApp:run` (no
   `runRelease` task is declared — do not invent one).
2. A probe must loop `withFrameNanos` inside the window and log inter-frame gaps to a
   file, gated by an environment variable so a normal launch is unaffected. The
   display refresh rate has to be recorded with the capture: thresholds derived on
   60 Hz do not transfer to 144 Hz.
3. Drive 30 s of wheel scrolling over Home and Browse plus 15 s of arrow keys per
   run (`xdotool` or `desktop_view`), 5 runs per build, alternating A/B/A/B, fresh
   launch each time. One idle 30 s control run so a bad p99 is not blamed on input.

**Pass bar:** zero gaps > (refresh interval + 8 ms) during active scrolling, p99 gap
≤ 2 refresh intervals; FAIL if any run shows > 0.5 % of gaps beyond that.

Paste when run:

```
| PERF-07 | X11 <hz> Hz | run=<n>/5 | gap p50=<n> p99=<n> max=<n> ms | over-budget=<n> (<n>%) | PASS or FAIL |
```

### PERF-08 — web (wasm) interaction budget

1. Build **into an isolated directory only**: the default
   `composeApp/build/dist/wasmJs/productionExecutable` is the bundle the physical TV
   is served. The redirect that used to live in `/tmp` is gone and nothing in
   `composeApp/build.gradle.kts` guards the default output, so the redirect script
   has to be restored as repo content before this item can be run at all (TEST-04).
2. Serve that directory on a fresh local origin; open in Chromium with a fresh
   context.
3. Collect long tasks reproducibly in the console rather than by eye:
   `const l=[];new PerformanceObserver(e=>l.push(...e.getEntries().map(x=>[x.startTime,x.duration]))).observe({entryTypes:['longtask']});`
   then 30 s of scripted scroll over Home and Browse, then dump `JSON.stringify(l)`.
4. Repeat 3×.

**Pass bar:** zero long-task entries > 50 ms in the 30 s window.

Paste when run:

```
| PERF-08 | isolated dist <git sha> | run=<n>/3 | longtasks=<n> max=<n> ms | PASS or FAIL |
```

### PERF-09 — image decode spikes when a card enters

1. Cold cache without wiping pairing (cache lives in `cacheDir/images`): fresh
   install, recorded; a warm-cache run must accompany it as the control.
2. Deterministic path to the first portrait shelf, then
   `tools/tv_frames.sh 22 60 0.12 cardentry` and
   `tools/tv_framestats.sh cardentry` over the same window.
3. Attribute slow frames to card entry with `tools/tv_trace.sh` in the same window
   (`Record View#draw()` / bitmap-upload sections). Debug-only `VIPTV_IMAGE_QA`
   decode timings (`ImageDiagnostics.kt`) may be quoted as a diagnostic; the claim
   must rest on release numbers, because the release `MainActivity` wires no
   diagnostics.
4. The shelf's prefetch comment must match the code before decode cost is compared:
   the comment claims one card composed past each viewport edge, but the `LazyRow`
   sets no `beyondBoundsItemCount`, so only Compose's built-in scroll prefetch
   applies.

**Pass bar:** no frame > 24 ms in the sweep and `Slow bitmap upload` ≈ 0; both cache
states kept.

Paste when run:

```
| PERF-09 | cache=cold or warm | frames=<n> slow_bitmap_upload=<n> | frame p99=<n> max=<n> ms | PASS or FAIL |
```

### PERF-10 — backdrop blur cost bounded, visually identical

R5: the blur is not a variable to trade. The TV scope is the quarter-resolution
`SoftArtwork` path (1/16 area, radius/4); the full-size blur in `HomeBackdrop.kt` is
non-TV only, and compositing the static backdrop as a reused offscreen layer is
already rejected earlier in this file for rounding 0.3 % of pixels.

1. Baseline the current release build in both backdrop states:
   `tools/tv_ab.sh backdrop-hero 5 16` (settled home, hero visible) and
   `tools/tv_ab.sh backdrop-browse 5 16` (down ×2, portrait backdrop). Copy the raw
   `/tmp/ab_*` dumps, the console output and the APK sha256 into
   `docs/evidence/<date>-tv-ab-backdrop/` — that becomes the reference, because no
   baseline exists today (the only GPU figures in this file belong to the rejected
   shelf-scroll variants).
2. Rerun both labels after any change touching `TvHomeBackdrop`, `SoftArtwork` or
   `BufferedArtwork`.

**Pass bar:** gpu50 median ≤ baseline (no increase > 1 ms), missed vsync inside the
1.6–4.0 % band recorded above, and a byte-identical capture pair. If the device omits
`50th gpu percentile`, substitute the `submit` phase median from
`tools/tv_framestats.sh` and record the substitution.

Paste when run:

```
| PERF-10 | state=hero or browse | gpu50 base=<n> now=<n> ms | missed_vsync base=<n>% now=<n>% | captures byte-identical=<yes/no> | PASS or FAIL |
```

### Tools these items still need (not committed)

These are requirements, not claims: until they exist, PERF-06 and PERF-07 cannot be
measured and TEST-10 stays a procedural rule.

- **`tools/tv_first_render.sh`** — launch, `gfxinfo reset` before `am start`, one
  30 s window, `--latency` present-gap histogram, `cold`/`fresh` state argument,
  fingerprint written beside the dumps. (PERF-06)
- **A desktop frame-gap probe** — environment-gated composable that loops
  `withFrameNanos` for N seconds and writes gaps plus the display refresh rate.
  Inert in a normal launch. (PERF-07)
- **Release enforcement in `tools/tv_ab.sh`** — fail fast when the installed package
  reports `DEBUGGABLE`, and write `dumpsys package` + the installed APK sha256 next
  to the raw `gfxinfo` dumps. (TEST-10)
- **A committed isolated-output init script** — recreates the lost wasm redirect and
  fails when the output variable is missing, so the live dist cannot be overwritten
  by a forgotten flag. (TEST-04, PERF-08)

### Queued experiments (not applied — before/after is mandatory)

Neither entry below has been implemented. Each needs byte-identical capture proof
(`TvCardVisualTest`/`TvShelfScrollVisualTest`, external pixel diff, bar in this file)
plus an interleaved `tools/tv_ab.sh` before/after. Revert is the default outcome; an
unmeasurable claim is not a fix.

1. **Shelf item state read (PERF-05).** The row-fade seed still reads
   `browsing`/`activeRow` inside the `LazyColumn` item scope, which is the same
   mechanism PERF-03 closed everywhere else: every visible shelf is recomposed on a
   focus move. The candidate fix seeds the fade at screen scope and leaves the item
   content reading no observed state. Keep only if the miss rate moves.
2. **Shelf prefetch (PERF-09).** Set `beyondBoundsItemCount = 1` (or delete the
   comment that claims it) so the stated prefetch contract matches the code. Keep
   only if bitmap-upload stalls fall **without** raising frame p99 — extra
   composition is charged to the same budget as decode.

---

## 2026-10-19 — first release-build measurement on the ONN TV

Build: `:androidApp:assembleRelease` (R8 minified), installed over the previous
build, measured with `tools/tv_ab.sh` (R3 metric) and `tools/tv_framestats.sh`
on the same session. Device `192.168.88.30:5555`, 1920×1080 UI.

| Metric | Value |
|---|---|
| bursts | 4 × 16 D-pad presses (right then left) |
| frames | 629 |
| missed vsync | 53 → **8.4 %** |
| janky frames | **16.3 %** |
| CPU frame p50 | **25.0 ms** (24–25 across bursts) |
| CPU frame p90 | 38.0 ms |
| GPU frame p50 | 13.5 ms |
| phase medians (framestats) | input 0.4 · anim 1.2 · **layout+record 2.6** · submit 3.7 · **swap_wait 13.9** |

Reading:

- **PERF-06 (<1 % missed vsync) is not met.** It is **not** a Compose problem:
  layout+record is 2.6 ms of a 25.2 ms frame. `swap_wait` (13.9 ms) plus GPU work
  dominates, i.e. the cost is in the compositor/GPU path for this full-screen
  UI with the mandated backdrop blur (rule R5). CPU-side work would have to shrink
  by more than half to matter, and there is not 13 ms of it.
- **PERF-05**: the same trace shows layout+record at 2.6 ms per frame during
  D-pad bursts, consistent with the earlier per-press finding; no per-press
  measure spike appears in the frame phases. Zero-`LazyRow`-measure proof still
  needs an instrumented counter; the frame evidence does not support a
  per-press measure storm.
- **PERF-10 (blur cost)**: gpu p50 13.5 ms with blur present. The earlier
  rasterized-backdrop and offscreen-plane experiments were rejected for a ±1 px
  visual delta and a GPU regression respectively (`PERF-04`), so blur stays and
  its cost is bounded at the number above rather than reduced.
- **PERF-09 (decode spikes)**: not isolated this pass; the release build's disk
  and memory caches plus the pre-composed next card mean a new card's decode
  lands on a frame that already has 12+ ms of slack in the CPU budget, but no
  per-frame attribution was captured.
- Desktop and web frame budgets (PERF-07/08) have **no** harness in this repo;
  the desktop pass in `docs/evidence/device-pass-2026-10-19/desktop-*.png` is a
  correctness capture from a software-rendered virtual window (the virtual
  display has no GL, so hardware frame pacing is not measurable there).

---

## 2026-10-19 (later) — image proxy + measurement caveats

Tried `wsrv.nl` for card art and backdrops:

- proxy verified from the shell: a real TMDB poster is **98,479 B** raw and
  **67,786 B** through wsrv at 464×696 WebP q82; a **blur=12** backdrop is
  **9,746 B**. So the bytes are real.
- measured on the ONN (release, `tv_ab.sh`, 4×16 presses) with card art routed
  through the proxy: **11.2 %** and **12.0 %** missed vsync, gpu p50 15.0–15.5 ms.
- after reverting the TV card sizing (proxy left as plain WebP): **11.4 %**,
  gpu p50 15.0 ms.

Conclusion: **no frame win on this TV**, so the TV card path is back to plain
proxied WebP. The sizing/blur path stays only for phone/tablet/web/desktop, where
it is a bandwidth win but **is not measured for frame pacing**.

### The earlier 8.4 % number is not a valid A/B baseline

The 8.4 % run happened on a freshly installed, still-populating Home (skeleton
rows, unloaded shelves). The later runs are on a fully populated Home. Two
identical builds measured 11.2 % and 12.0 %; a third 11.4 % after a code revert.
So the honest figure for the populated Home is **≈11–12 % missed vsync**, and the
app-state difference — not a code change — explains most of the gap.

### Tooling caveat found here

`tools/tv_ab.sh` reports `4950 ms` / `0 %` janky when the app is not on a
scrolling screen (fresh launch lands on Profiles, or the TV is idle). Those runs
are **invalid**, not results. A trustworthy run needs the app confirmed on Home
before the first burst; that state assertion is still missing from the script.
