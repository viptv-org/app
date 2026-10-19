# Handoff — TV backdrop revert + current state (2026-10-19)

## What happened
A rasterization optimization of the TV home backdrop (`TvHomeBackdrop.kt`)
broke visual fidelity several times (stretched art, wrong poster position,
missing blur, missing crossfade, missing gradients). Per the owner's decision,
it was fully reverted.

## Current state (commit 3c3b96c, branch experiment/kmp-v2, pushed)
- `TvHomeBackdrop.kt` and `SoftArtwork.kt` are restored **byte-identical**
  from commit d02fedf (the original layered implementation with runtime blur,
  gradients, BufferedArtwork crossfades). Verified with `git diff d02fedf` = 0.
- The plane-rasterization helper files were deleted.
- The release APK with the reverted backdrop is installed on the ONN TV
  (192.168.88.30, wireless adb, armeabi-v7a, Android 14).

## What remains in the tree (non-visual, kept intentionally)
These do not change what the UI looks like, only how fast it runs:
1. `@Immutable` annotations on the wire data classes (EngineWire.kt) — lets
   Compose skip unchanged cards during recomposition.
2. Removed the infinite `basicMarquee` on focused TV card titles.
3. `focused` state in TvHomeScreen is a `State<Card?>` read only inside
   `rememberTvBillboardSelection`, so focus moves don't recompose the whole
   home screen.
4. Sources sheet overlays the still-composed details page (no page swap,
   no image reload flash). TV details hero shows real resume state.
5. Debrid stream discovery deadline is 25s (torrentio+TorBox needs 10–20s).
6. IPTV provider sign-in is removed everywhere per the owner's decision
   (the design canvas re-export removed those boards too).
7. TV source integrations are full-screen settings pages (no dialogs).

## Performance numbers (ONN TV, release build, gfxinfo)
- Original debug build: 69ms median horizontal nav, 56% janky.
- Current (original visuals + the kept work above): ~24ms median horizontal,
  ~21ms row changes, GPU ~8ms.
- Remaining gap to 16.6ms is CPU-side: LazyRow relayout during the 300ms
  shelf scroll animation. The owner wants 60fps on first render with **no
  visual changes** — the next steps (LazyRow contentType hints, graphicsLayer
  translation for scroll, focus recomposition profiling) must be verified
  one-to-one against the original rendering, ideally with pixel comparisons
  in matched app states, and reverted immediately if anything differs.

## Verification workflow that should be used for ANY visual change
1. Screenshot the same screen/state before and after (same content, same
   focus) on the TV.
2. `compare_images` on the pair — structural diffs must be zero outside the
   intended region.
3. If not identical: revert.

## Design state (design repo, main, pushed)
- 2026-10-19 re-export imported: 264 boards (source integrations, browse
  pages, parental, recovery, states; IPTV sign-in boards removed).
- Design requests for anything still missing: `plans/DESIGN_REQUEST-2026-10-18.md`.
- Canvas data dictionary: `reference/canvas-data-dictionary.md`.

## Known open items
- 60fps target not yet reached (~21-24ms median; see above for the safe path).
- Spider-Man "Could not load sources" diagnosis: now logged — reproduce and
  read `adb logcat -s VIPTV_SOURCES:W VIPTV_ADDON:I`.
- copy.md / components.md / decisions.md still describe the older export.
