# PERF audit — frame-budget claims (PERF-05..10, R3)

Read-only audit, 2026 session. Repo: `/mnt/ALPH/code/viptv-org` (app = `app/`).
No files edited, no gradle/adb/emulator/build executed. All `file:line` citations
are as-found; the working tree is being edited concurrently (`git status` in
`app/` shows `M` on `TvCatalogCard.kt`, `TvHomeScreen.kt`, `App.kt`, settings
sources and others), so line numbers can drift — they were re-read for this
report, not taken from build artifacts.

Sources read: `app/docs/TV_FRAME_BUDGET.md`, `app/docs/ISSUES.md` (§0 rules,
§1 performance), `app/docs/OPEN_TASKS.md` (§Performance), `app/docs/HANDOFF-PROMPT.md`,
`app/docs/STATUS.md`, `app/docs/evidence/` inventory, `app/tools/tv_ab.sh`,
`tv_bench.sh`, `tv_frames.sh`, `tv_framestats.sh`, `tv_trace.sh`,
`tv_cadence.sh`, `tv_state_shot.sh`, `tv_cycle.sh`, `qa_desktop_run.py`,
`verify_desktop.py`, `qa_performance_run.py`, `features/home/TvHomeScreen.kt`,
`TvHomeShelf.kt`, `TvCatalogCard.kt`, `TvHomeBackdrop.kt`, `HomeBackdrop.kt`,
`SoftArtwork.kt`, `BufferedArtwork.kt`, `components/TvShelfScroll.kt`,
`TvHomeVisibility.kt`, `images/{ImageLoading,ImageDiagnostics,PublicArtworkMapper,ImageCache}.kt`,
`androidApp/build.gradle.kts`, `androidApp/src/main/.../MainActivity.kt`,
`androidApp/src/debug/.../NativeQaActivity.kt`, `composeApp/build.gradle.kts`,
`composeApp/src/desktopTest/.../TvCardVisualTest.kt`, `TvShelfScrollVisualTest.kt`.

---

## 1. What measurement machinery actually exists

| Tool | What it measures | Notes / citation |
|---|---|---|
| `tools/tv_ab.sh` | Missed-vsync rate + p50/p90 frame duration, gpu50, janky% aggregated over `BURSTS=5` bursts × `PRESSES=16` D-pad presses (8 right, 8 left) in one app session | `tv_ab.sh:8-15,24-28,41-57`. Launches whatever `org.viptv.experiment` is installed (`:12,17-18`); does **not** build, install, or check build type. Raw dumps go to `/tmp/ab_<label>_<i>.txt` (`:28`) — transient, never committed. |
| `tools/tv_bench.sh` | Older single-direction percentile grep from `dumpsys gfxinfo` | Not the R3 metric. |
| `tools/tv_framestats.sh` | Per-frame phase breakdown (`dumpsys gfxinfo framestats`): duration percentiles, missed vsync, input/anim/layout+record/submit/swap_wait medians | Good for attribution (layout cost, GPU submit proxy). |
| `tools/tv_trace.sh` | atrace 8 s (`view gfx` + app tags) of the same nav path | `tv_trace.sh:23`; parse `animation`, `traversal`, `Record View#draw()`, `Compose:semantics:*` per TV_FRAME_BUDGET.md:17-19. |
| `tools/tv_frames.sh` | Greps gfxinfo incl. `Number Slow bitmap upload`, `Number Slow issue draw` | The only committed decode/upload-cost probe. |
| `tools/tv_cadence.sh` | SurfaceFlinger `--latency` present-gap histogram (true display cadence; 33 ms gap = dropped frame) | The only tool that can see frames *before* a gfxinfo reset. |
| `tools/tv_state_shot.sh` | Deterministic nav screenshot (visual, not perf) | |
| `tools/tv_cycle.sh` | Intended: build release APK → install → benchmark | **Broken.** `tv_cycle.sh:9` calls `adb_t` and `$SERIAL`, neither defined in that script (they are local to each `tv_*.sh`), so under `set -eu` it dies with *command not found* before installing. It also runs `tv_bench.sh` (`:10`), not the R3 `tv_ab.sh` metric. |
| `TvCardVisualTest` / `TvShelfScrollVisualTest` | Deterministic fixture PNG capture (`VIPTV_CARD_CAPTURE_DIR` / `VIPTV_SHELF_CAPTURE_DIR`), virtual frame clock | Capture harness only — **no byte assertions, no committed goldens** (`TvCardVisualTest.kt:46-70`; `TvShelfScrollVisualTest.kt:54,73`). Byte-identity is an external manual comparison (TV_FRAME_BUDGET.md:25-29). This is R2 machinery, not perf. |
| `android-performance-2026-10-15` evidence | Startup marks (`first_draw_ms`, `profile_to_home_ms`…) on an **AVD**, via `qa_performance_run.py` | Not frame budget, not the ONN TV, not the home-scroll path. |

Nothing in the repo measures desktop UI frame pacing (`qa_desktop_run.py` is an
Xvfb playback probe; `verify_desktop.py` checks package bytes) or browser long
tasks. `STATUS.md:121`/`:224` honestly state "Drawing cadence and broader
desktop GUI performance remain unqualified."

## 2. TV_FRAME_BUDGET.md — measured vs asserted

**Measured, method committed, raw data NOT retained** (numbers are hand-copied
into prose; `/tmp/ab_*` files are gone, no `docs/evidence/` dir exists for any
tv_ab/trace/framestats run — checked the full evidence listing):

- Before/after table: missed vsyncs 7.3–7.8% → 1.6–4.0% (3 vs 5 runs, asymmetric
  sampling), UI p90 36 → 32-34 ms, median 22 → 21-23 ms, median UI-thread 6.6 →
  5.3 ms — `TV_FRAME_BUDGET.md:31-38`.
- Per-frame phase costs (UI ~5 ms, RenderThread ~9 ms, ~6 ms buffer/handoff) —
  `TV_FRAME_BUDGET.md:44-46`.
- Rejected shelf-scroll experiment table (cpu50/gpu50/missed-vsync for the kept
  vs two hoisted variants) — `TV_FRAME_BUDGET.md:69-77`.
- "two nodes per card measurably regressed the miss rate back to baseline" —
  `TV_FRAME_BUDGET.md:56-57`, no numbers anywhere.

**Asserted only (no measurement behind them):**

- "every change below was proven pixel-identical" (`:4-5`, `:25-29`): the
  *procedure* exists (capture harness), but the compared PNGs and the
  comparison results are not in the repo; the tests themselves assert nothing.
- "~6 Mpx per frame" headroom in hero/backdrop fill (`:79-81`) — arithmetic
  estimate, no trace/gpu number for the current backdrop composition.
- "the TV has `flar2.homebutton` bound" (`:50-51`) — environment claim, fine,
  but it is why the a11y numbers do not transfer to other TVs.
- Build provenance: the doc never states which build the numbers came from;
  `HANDOFF-PROMPT.md:57` asserts "Measured (release, interleaved A/B…)" but
  nothing in the toolchain records or enforces release (see §3).

**Number drift between docs** (symptom of hand-copied, raw-less claims):
after-value "1.6–4.0%" (`TV_FRAME_BUDGET.md:35`, `OPEN_TASKS.md:6`) vs
"1.6-3.9%" (`HANDOFF-PROMPT.md:57-58`); baseline "7.3–7.8%" vs "7.5%"
(`OPEN_TASKS.md:6`).

## 3. R3 compliance (release + tv_ab.sh + interleaved A/B + missed-vsync)

R3 text: `ISSUES.md:15-16`. Findings:

1. **Release-build requirement is procedural, not enforced.** `tv_ab.sh` never
   builds/installs/fingerprints; it launches the installed
   `org.viptv.experiment` (`tv_ab.sh:12,17-18`). A debug install measures
   identically from the script's point of view (the androidApp debug source set
   even adds extra debug-only classes, `androidApp/build.gradle.kts:22-28,37-45`).
2. **The only release-build path is broken.** `tv_cycle.sh:7-10` builds
   `:androidApp:assembleRelease` then dies on the undefined `adb_t`/`SERIAL`
   before installing, and would run `tv_bench.sh` (percentile grep), not the
   R3 missed-vsync metric. Fix (not applied): copy the `ADB_TIMEOUT`/`adb_t`/
   `SERIAL` block from `tv_ab.sh:8-11` into `tv_cycle.sh` and call
   `tools/tv_ab.sh "$LABEL" 5 16`.
3. **"Interleaved A/B, same session" is not reproducible from the committed
   tooling** (`TV_FRAME_BUDGET.md:31`). `tv_ab.sh` measures one installed build
   per session; interleaving two code versions requires alternating installs
   (i.e., different sessions). No script alternates builds.
4. **Raw evidence is not retained.** R8 (`ISSUES.md:23`) asks for V-evidence per
   issue; no `/tmp/ab_*.txt` copy, console log, or trace file for the frame-budget
   work exists under `docs/evidence/`. All PERF numbers are unfalsifiable today.
5. **Fingerprinting is absent.** Recommended with any future run:
   `adb shell dumpsys package org.viptv.experiment | grep -E "versionName|flags"`
   plus `sha256sum` of the installed APK, saved next to the raw dumps.

## 4. PERF-05..10 — evidence status and exact measurement plan

**Bottom line: none of PERF-05..10 has committed evidence.** PERF-01..04 are
marked DONE in `ISSUES.md:27-30`, but even their numbers share the
no-raw-artifact problem above; PERF-05..10 are all open with zero runs recorded.

Build type for every measurement below: **release**
(`./gradlew :androidApp:assembleRelease --max-workers=2`, install
`androidApp/build/outputs/apk/release/androidApp-release.apk`; release exists
with `isMinifyEnabled=true` and debug signing at `androidApp/build.gradle.kts:14-19`).
Every device call must stay behind `timeout`/`adb_t` (R4, `ISSUES.md:17-18`).

### PERF-05 — shelf re-measures ~2.5×/key press (~4.5 ms each)
- **Status:** open (`ISSUES.md:31`, `OPEN_TASKS.md:10` "measured; no cheaper
  path found yet"). The 2.5×/press and 4.5 ms figures come from the trace work
  summarized in `TV_FRAME_BUDGET.md:60-77` — no trace file or per-press count is
  committed. Re-measure happens **by design**: `rememberTvShelfScroll`
  (`TvHomeShelf.kt:34`) animates the `LazyListState` offset
  (`TvShelfScroll.kt:46`), and the cheaper hoisting variants were measured
  slower on this GPU and rejected (`TV_FRAME_BUDGET.md:69-77`). The AC as
  written ("no LazyRow measure attributable to a focus move",
  `ISSUES.md:31`) **conflicts with the accepted design** — re-scope or close
  with evidence.
- **The stated V method is unimplementable:** it counts a trace section
  `shelfmeasure` that does not exist anywhere in the code (repo-wide grep finds
  only `ISSUES.md:31` itself; `tv_trace.sh` captures only `view gfx` + app tags).
- **How to measure:**
  1. Fix `tv_cycle.sh` (§3.2) and install the release build.
  2. Add the trace section (a code change with no pixel effect): wrap the
     `LazyRow` in `TvHomeShelf.kt:39` with `Modifier.trace("shelfmeasure")`
     (ui-android, `androidx.tracing`) — then re-prove pixel identity with the
     card/shelf capture harness (`VIPTV_CARD_CAPTURE_DIR=…` +
     `:composeApp:desktopTest`, external `compare_images`, byte-identical bar
     per `TV_FRAME_BUDGET.md:25-29`).
  3. Capture: `tools/tv_trace.sh /tmp/shelf.txt 40` (40 presses on the settled
     first portrait shelf, same deterministic path as `tv_ab.sh:17-26`).
     Count: `grep -c shelfmeasure /tmp/shelf.txt` ÷ 40 presses; split presses
     that trigger the safe-window scroll (`TvShelfScroll.kt:38-47`) from those
     that do not.
  4. Cost cross-check: `tools/tv_framestats.sh shelf` while sweeping vs while
     idle — the `layout+record` median delta is the per-press measure cost.
  5. **Pass/fail:** (a) presses that do not scroll ⇒ `shelfmeasure` count = 0;
     (b) during the 300 ms scroll tween, measures are expected — PASS if
     `layout+record` p99 stays ≤ 8 ms and no frame exceeds 24 ms total, FAIL
     otherwise; (c) if the AC stays "no measure at all", the honest closure is
     to record the baseline and mark PERF-05 *accepted cost* with the
     `TV_FRAME_BUDGET.md:69-77` GPU regression as the reason, moving the
     acceptance to the frame-level metric (missed vsync within the current
     1.6–4.0% band, `TV_FRAME_BUDGET.md:35`).

### PERF-06 — 60 fps on first render (no warm-up)
- **Status:** no evidence; and **`tv_ab.sh` cannot measure it as committed.**
  After `am start` it sleeps 13 s, presses select (choosing the *Default
  profile*), sleeps 13 s, then two downs (`tv_ab.sh:17-21`): the first
  `gfxinfo reset` lands ≈30 s after launch — the entire "first 30 s" window is
  excluded by construction. A fresh install also breaks the nav script: the
  keyevent-23 step assumes an existing profile list, not first-run
  pairing/sign-in.
- **How to measure (new script, e.g. `tools/tv_first_render.sh`):**
  1. Install the release APK. Define and record the state: (a) *true first run*
     (fresh install, includes pairing — home may not render in 30 s, which is
     itself a finding) or (b) *cold process, restored session*
     (`am force-stop` then start — session restore is the AUTH-03 path). The
     AC text "after install" implies (a); report both if possible.
  2. gfxinfo window: `adb_t shell am force-stop $PKG; adb_t shell dumpsys
     gfxinfo $PKG reset; adb_t shell am start -n $PKG/.MainActivity; sleep 30;
     adb_t shell dumpsys gfxinfo $PKG` — parse with the same regex block as
     `tv_ab.sh:41-57` (frames, missed_vsync, p50/p90). Frames rendered before
     the reset lands are lost, so pair it with:
  3. Present-cadence window (captures from t0): before launch run
     `dumpsys SurfaceFlinger --latency-clear <layer>` (layer discovery as in
     `tv_cadence.sh`), then pull `--latency` after 30 s and compute the
     present-gap histogram (`tv_cadence.sh` python block).
  4. **Pass/fail:** missed vsync < 1.0% of frames in the 30 s window
     (`ISSUES.md:32`) AND no present gap > 33 ms after the first content frame;
     FAIL otherwise. Run ≥ 3 times; keep raw dumps + APK sha256 in
     `docs/evidence/<date>-tv-first-render/`.

### PERF-07 — desktop scroll/interaction frame budget
- **Status:** no evidence, no tool. `STATUS.md:113,121,224` leave desktop GUI
  performance unqualified; `qa_desktop_run.py`/`verify_desktop.py` measure
  playback/package integrity, not UI frame pacing.
- **How to measure:**
  1. Build: same JVM artifact for both sides of an A/B — the desktop app is
     `compose.desktop.application { mainClass = "org.viptv.app.MainKt" }`
     (`composeApp/build.gradle.kts:117-119`); launch via
     `./gradlew :composeApp:run` (confirm available run variants with
     `./gradlew :composeApp:tasks --all | grep -i run` — do not assume a
     `runRelease` task exists; none is declared in the build script).
  2. Instrument the frame clock (the desktop clock already drives
     `withFrameNanos`, used e.g. in `TvHomeScreen.kt:66-69`): add a probe
     composable (system-property-gated so production is unaffected) that loops
     `withFrameNanos { }` and logs inter-frame gaps to a file, plus the display
     refresh rate from `java.awt.GraphicsEnvironment`.
  3. Drive deterministic input: `xdotool` wheel-scroll and arrow keys over
     Home and Browse for 30 s per run, 5 runs per build, **alternating builds
     A/B/A/B…** between fresh launches (this is the interleaving R3 asks for).
  4. **Pass/fail:** zero inter-frame gaps > one refresh interval + 8 ms during
     active scrolling in every run (the "no dropped frames while scrolling"
     AC, `ISSUES.md:33`); p99 gap ≤ 2 refresh intervals. FAIL if any run shows
     > 0.5% of gaps beyond that. Save the gap logs as evidence.
  5. No-visual-change proof is not required for a measurement-only change, but
     the probe must be property-gated and removed or kept inert afterwards.

### PERF-08 — web (wasm) interaction budget
- **Status:** no evidence; no Chrome profile committed anywhere under
  `docs/evidence/`.
- **How to measure:**
  1. Build **into the isolated output only** (the default
     `composeApp/build/dist/wasmJs/productionExecutable` is the physical TV's
     live bundle — `AGENTS.md:72-74`,
     `docs/AGENT_HANDOFF_2026-10-14.md:106,117`):
     `VIPTV_WEB_OUTPUT=/tmp/viptv-kmp-browser-<id>/dist ./gradlew
     :composeApp:wasmJsBrowserDistribution --init-script
     /tmp/viptv-isolated-web.gradle` (wasm target is `browser(); binaries.executable()`,
     `composeApp/build.gradle.kts:14`). The init script is not currently in
     `/tmp` — recreate it from the handoff doc (redirect
     `Sync.destinationDir`) before building.
  2. Serve that directory on a local origin; open in Chrome/Chromium.
  3. Long-task collection (reproducible, no DevTools hand-waving): in the
     console run
     `const l=[];new PerformanceObserver(e=>l.push(...e.getEntries().map(x=>[x.startTime,x.duration]))).observe({entryTypes:['longtask']});`
     then a scripted 30 s scroll loop over Home (e.g.
     `setInterval(()=>document.querySelector('[data-scroll]')?.scrollBy(0,120),100)`
     adapted to the actual scroll container, plus keyboard events). Dump
     `JSON.stringify(l)`.
  4. Optionally a DevTools Performance recording saved as `.json` alongside.
  5. **Pass/fail:** zero `longtask` entries with duration > 50 ms during the
     30 s scroll window (`ISSUES.md:34`); FAIL on any single entry > 50 ms.
     Repeat 3×; commit the JSON arrays as evidence.

### PERF-09 — image decode spikes when a new card enters
- **Status:** no evidence. Code facts that shape the measurement:
  - `TvCatalogCard.kt:83` uses `AsyncImage` with no explicit request size (Coil
    resolves from layout constraints; decode is off the main thread by default).
  - Caches: 64 MB memory / 256 MB disk (`ImageLoading.kt:23-26`); public
    artwork is re-encoded to WebP q=90 by `PublicArtworkMapper` — smaller
    decodes, but still full-bitmap for the card size.
  - The comment at `TvHomeShelf.kt:28-31` claims one card is kept composed on
    each side of the viewport, but the `LazyRow` (`TvHomeShelf.kt:39`) sets no
    `beyondBoundsCount` — only Compose's built-in scroll prefetch applies, so
    the claim is stronger than the code.
  - Decode timings exist only in the **debug** source set:
    `ImageDiagnostics.kt:22-34` logs `total_ms/fetch_ms/decode_ms`, wired via
    `NativeQaActivity.kt:81` (tag `VIPTV_IMAGE_QA`); the release
    `MainActivity.kt:57` passes no diagnostics. R3 forbids debug-build claims.
- **How to measure (release):**
  1. Install the release APK. Establish a **cold image cache** without wiping
     pairing: cache lives in `context.cacheDir/images`
     (`androidMain/images/ImageCache.kt`) — remove it via a fresh install or
     `pm clear` + re-pair (record which; a warm-cache run must accompany it as
     the control).
  2. Navigate the deterministic path (`tv_ab.sh:17-26`: start → 13 s → select →
     13 s → down ×2 lands on the first portrait shelf), then sweep right across
     the shelf: `tools/tv_frames.sh 22 60 0.12 cardentry` — captures
     `Number Slow bitmap upload`, `Number Slow issue draw`, percentiles.
  3. Per-frame check: `tools/tv_framestats.sh cardentry` on the same window —
     every frame's `FrameCompleted − IntendedVsync`.
  4. Attribution ("caused by a card entering"): run `tools/tv_trace.sh` in the
     same window and correlate frames containing `Record View#draw()` /
     bitmap-upload sections with card-entry timestamps. Diagnostic-only decode
     timings may come from a debug-build `VIPTV_IMAGE_QA` log, but the claim
     itself must rest on the release numbers.
  5. **Pass/fail:** no frame > 24 ms (`ISSUES.md:35`) in the sweep window AND
     `Slow bitmap upload` count ≈ 0 in that window; FAIL on any frame > 24 ms
     attributable to a card entering. Keep both cold and warm results.

### PERF-10 — backdrop blur cost bounded, visually identical
- **Status:** no evidence, and **no baseline exists to be "unchanged or lower"
  against.** The doc's only GPU numbers (gpu50 9–16 ms) belong to the rejected
  shelf-scroll variants (`TV_FRAME_BUDGET.md:69-77`), not the backdrop. The
  TV backdrop (`TvHomeBackdrop.kt:27-45`) is: full-bleed hero `BufferedArtwork`
  + gradient masks, a 1160×620 browsing plane, and (portrait) the quarter-res
  `SoftArtwork` blur (`SoftArtwork.kt` — 1/16 area, radius/4). The full-size
  60 dp blur in `HomeBackdrop.kt:20-22` is **non-TV only** (`HomeScreen.kt:38`),
  so PERF-10's TV scope is the SoftArtwork path. R5 forbids removing the blur
  (`ISSUES.md:19-20`).
- **How to measure:**
  1. Baseline the current release build in both backdrop states:
     `tools/tv_ab.sh backdrop-hero 5 16` (settled home, hero visible) and
     `tools/tv_ab.sh backdrop-browse 5 16` (after down ×2, portrait backdrop) —
     the script prints `gpu50 median` parsed from `50th gpu percentile`
     (`tv_ab.sh:42,53`). Copy `/tmp/ab_*` + console output + APK sha256 into
     `docs/evidence/<date>-tv-ab-backdrop/` — this becomes the reference.
  2. After any change touching `TvHomeBackdrop`/`SoftArtwork`/`BufferedArtwork`:
     rerun the same two labels.
  3. **Pass/fail:** gpu50 median ≤ baseline (no increase > 1 ms) AND missed
     vsync stays within the current 1.6–4.0% band (`TV_FRAME_BUDGET.md:35`).
     Visual identity is mandatory (R2/R5): fixture-capture comparison
     (extend `TvCardVisualTest`-style scene to the backdrop, or
     `tools/tv_state_shot.sh` same-session pairs) must be byte-identical.
  4. Caveat: if the device's gfxinfo omits `50th gpu percentile`, `tv_ab.sh`
     prints no gpu50 — fall back to the `submit` phase
     (`IssueDrawCommandsStart→SwapBuffers`) median from
     `tools/tv_framestats.sh` as the GPU proxy and document the substitution.

## 5. Cross-cutting recommendations (not applied — read-only audit)

1. Fix `tools/tv_cycle.sh` (define `ADB_TIMEOUT`/`adb_t`/`SERIAL`, call
   `tv_ab.sh`) so the R3 release path actually runs.
2. Add build fingerprinting (APK sha256 + `dumpsys package` flags) to every
   recorded perf run; save raw `/tmp/ab_*`, traces and console output under
   `docs/evidence/<date>-tv-ab-<label>/` (R8).
3. Reconcile the number drift between `TV_FRAME_BUDGET.md:35`,
   `OPEN_TASKS.md:6` and `HANDOFF-PROMPT.md:57-58` once raw data exists.
4. Re-scope PERF-05's AC (it contradicts the measured-and-rejected hoisting
   experiment) and PERF-06's V method (`tv_ab.sh` structurally cannot see the
   first 30 s).
5. Correct or implement the `TvHomeShelf.kt:28-31` prefetch comment
   (`beyondBoundsCount` is not set), since PERF-09's exposure depends on it.
6. The "interleaved A/B" wording in R3/`TV_FRAME_BUDGET.md:31` needs a concrete
   procedure: alternate installs of the two release APKs, 5 runs each, fresh
   session per run, same label scheme.

## 6. Verdict

The frame-budget work is real engineering with a genuine method (deterministic
nav, timed adb, missed-vsync metric, rejected experiments documented) — but
**every number in `TV_FRAME_BUDGET.md` is asserted, not retained**: no raw
dumps, traces, or build fingerprints are committed, the release-build rule (R3)
is enforced by nothing, the only release-install script is broken, and **all six
open items PERF-05..10 have zero committed evidence**. PERF-05's verification
method references a trace section that does not exist; PERF-06's method is
structurally impossible with the committed script; PERF-07/08 have no tooling at
all; PERF-09's decode instrumentation is debug-only; PERF-10 has no baseline to
compare against.
