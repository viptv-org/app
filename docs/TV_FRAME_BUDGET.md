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
| missed vsyncs | 7.3–7.8 % | 1.6–3.9 % |
| UI-thread 90th percentile | 36 ms | 32 ms |
| median frame duration | 22 ms | 21 ms |
| median UI-thread time/frame (trace) | 6.6 ms | 5.3 ms |

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
5. **Backdrop layer.** The static backdrop now composites as one reused
   offscreen layer instead of base fill + artwork + two full-screen gradients
   every frame.

## Not done (next step if more is needed)

`rememberTvShelfScroll` still animates the `LazyListState` offset, so the shelf
is re-measured ~2.5 times per key press (≈4.5 ms each). Hoisting the scroll to a
`graphicsLayer` translation (jump the list, animate the offset delta) should
remove those passes. It needs an over-scanning LazyRow (extra width, shifted
`contentPadding`, clipping parent) and mid-animation frame comparison against
the desktop fixture scene, because a naive translation clips cards sliding in
from the edge.
