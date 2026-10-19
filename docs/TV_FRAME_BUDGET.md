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
