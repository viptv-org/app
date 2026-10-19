# VIPTV KMP — handoff prompt: remaining work (2026-10-19)

You are continuing work on the VIPTV Compose Multiplatform app
(`/mnt/ALPH/code/viptv-org/app`, branch `experiment/kmp-v2`, clean, pushed).
Read `app/docs/TV-BACKDROP-REVERT-HANDOFF.md` first — it explains a reverted
visual change and the mandatory verification workflow. Everything below is
**not yet done**. Do not change anything that alters visuals without a 1:1
same-state screenshot comparison (before/after, `compare_images`), and revert
immediately on any diff.

## 1. Implement the 2026-10-19 design boards (the big one)

The design repo (`/mnt/ALPH/code/viptv-org/design`, branch `main`, pushed)
now contains 264 imported boards including 71 new ones the app does NOT
implement yet. References live at
`viptv-design-system/reference/screens/html/<platform>/<id>.html` (exact
markup) and `img/<platform>/<id>.webp` (renders). The canvas data dictionary
is `reference/canvas-data-dictionary.md`. Implement, per platform (TV, phone,
tablet, desktop, web in commonMain where possible):

- **Settings → Source integrations** (D3/M3/T3/Tv3/W3SetSources +
  SourcesViptv/SourcesM3u/SourcesXtream/SourcesRemove/SourcesEmpty/
  SourcesTorrent). The app currently has improvised full-screen TV pages
  (`TvSourceIntegrationForm.kt`, `TvSourceIntegrationList` inside
  `SourceIntegrationSettings.kt`) that do NOT match the boards — rebuild them
  to the boards exactly (fonts, sizes, focus, copy). Data shapes are in the
  design request: `plans/DESIGN_REQUEST-2026-10-18.md`.
- **Movies/Series browse pages** (D3/M3/W3Browse*, BrowseCatalogs,
  BrowseEmpty/BrowseError/BrowseLoading, M3BrowseFilters, Tv3Movies* set:
  Catalogs/Empty/Error/Filters/FilterText/Loading). The app's
  `BrowseScreen`/`TvBrowseScreen` need the catalog switcher, facet filters
  (QueryControl kinds: Text/SingleChoice/MultiChoice/Toggle/Number/Date),
  skeleton/empty/error states, and scroll-appending grids per the boards.
  Card/row states: D3/M3/Tv3CardStates + RowStates (badges, no-art fallback,
  progress, certification chips — the exact drawing rules are in
  `canvas-data-dictionary.md` §A1).
- **Parental controls content pages** (D3/M3/W3Parental, Tv3SetParental).
- **Recovery codes screen** (D3/M3/W3Recovery) — app has
  `RecoveryCodesScreen.kt` with no board; align it.
- **About + Display settings** (D3SetAbout, M3About, M3SetDisplay,
  Tv3SetAbout, Tv3SetDisplay).
- **Sources sheet states** (D3/M3/Tv3SourcesEmpty + SourcesTorrent) — the
  app's current empty/torrent-only copy lives in `SourcesScreen.kt` /
  `TvSourcesScreen.kt`; match the boards' copy and layout.
- **TV add-on manual entry** (Tv3AddonEntry, Tv3AddonInstall) — the app uses
  a plain AlertDialog in `TvSettingsScreen.kt`; match the boards.

## 2. 60fps on the ONN TV (no visual changes allowed)

Current state (release build, `gfxinfo`): ~24ms median horizontal navigation,
~21ms row changes, GPU ~8ms — the remaining gap to 16.6ms is CPU-side:
LazyRow relayout during the 300ms shelf scroll animation and focus
recomposition. Safe next steps, each verified 1:1 and reverted on any visual
diff: `contentType` hints on LazyRow items; hoisting the shelf scroll to a
`graphicsLayer` translation instead of item relayout; profiling focus-change
recomposition with Compose compiler metrics. Debug builds are ~3x slower —
always measure release. The owner requires 60fps **on first render** (no
warm-up-dependent behavior).

## 3. Home page polish (from the earlier design audit, never applied)

Per the D3HomeWin/Tv3Home boards: hero "FEATURED · 1 OF 5" label format and
pagination dots, "New episode" badge on Continue Watching cards, card density
(4.5–5 cards visible), three-dot overflow menu on Continue Watching cards,
"New" badges on catalog rows.

## 4. Diagnose "Could not load sources" (Spider-Man case)

Reproduce on the TV (open Spider-Man, press Play) and read
`adb logcat -s VIPTV_SOURCES:W VIPTV_ADDON:I` — the app now logs which path
failed (addons vs the VIPTV Sources integration). Likely the TMDB key in the
VIPTV Sources integration; fix per findings. Also: the user's Torrentio TB
(TorBox) is installed; with a valid key its sources should now appear
(25s discovery deadline). Plain Torrentio only returns torrent links —
filtered by design; the panel explains the debrid requirement.

## 5. Title page data gaps (need a decision or backend work)

- Cast line ("Starring …") — the wire `Card` has no cast field; either add
  cast to the engine's detail projection or drop from the board
  implementation.
- Trailer and Share circles on the TV/desktop hero — no backing data/actions.
- HD/5.1/CC chips — only certification exists in metadata; the boards draw
  them from `metadata.certification` + `badges[]` (the canvas dictionary
  explains the priority).

## 6. Design docs follow-up

`viptv-design-system/copy.md`, `components.md` and `decisions.md` still
describe the pre-2026-10-18 export. They need passes against the 264-board
set (exact copy, component rules, decisions), following
`docs/process/DESIGN_SYNC.md`.

## Environment facts

- ONN TV: wireless adb at `192.168.88.30:5555` (authorized for this host),
  armeabi-v7a, Android 14, 1920×1080 @ density 320. uiautomator dumps fail
  on it — use dpad keyevents + `screencap` pulls + vision. Release builds
  install over the current one (`:androidApp:assembleRelease`, debug-signed).
- Emulator profile `viptv-native-tv-qa` for TV flows with uiautomator.
- UI preview harness: `VIPTV_UI_PREVIEW=1 VIPTV_UI_SCREEN=Details
  VIPTV_UI_VARIANT=Series VIPTV_UI_WIDTH=1440 VIPTV_UI_HEIGHT=900
  ./gradlew :composeApp:run` (fast design-vs-app checks without app state).
- Tools: start with `viptv_status`; use `viptv_gate` for
  desktop_compile/desktop_test/android_compile/android_apk/ios_klib;
  `viptv_desktop` + `desktop_view` for desktop checks.
- Build serialization: only one viptv_gate job at a time; wait for job exit
  before reading logs (stale logs mislead).
- IPTV provider sign-in is REMOVED by owner decision (app + design boards);
  M3U/Xtream remain as Live TV source integrations in Settings. Do not
  reintroduce.
