# Desktop navigation/history audit — navBack / navForward model

**Scope:** `state/ControllerNavigation.kt`, `state/ApplicationState.kt`, `desktopMain/Main.kt`, `DesktopTitleBar.kt`, plus every state writer that touches `screen` (`ControllerCatalog.kt`, `ControllerPlayback.kt`, `ControllerLive.kt`, `ControllerProfiles.kt`, `ControllerSession.kt`, `ControllerParentControls.kt`), `App.kt` chrome wiring, `DetailNavigationTest.kt`, docs (`OPEN_TASKS.md`, `UI_INVENTORY.md`) and design boards (`D3HomeWin`, `D3Shortcuts`). Read-only: source + docs + boards only, no builds, no emulators. All paths below are relative to `app/composeApp/src/`.

**Working-tree state at audit time:** the stacks are *uncommitted* changes — `git status` shows `M commonMain/kotlin/org/viptv/app/state/ControllerNavigation.kt` and `M .../ApplicationState.kt`. `git diff HEAD` confirms `navigate(screen, record)`/`popHistory()`/`navigateForward()` are new, and that the previous `navigateBack()` was `Screen.Details -> backDetails()` unconditionally. Findings below cite the code as it stands right now.

---

## How the model works (as written)

- `ApplicationState.kt:19-20` — `navBack`/`navForward: List<Screen>`, defaults empty.
- `ControllerNavigation.kt:6-49` — `navigate()` records `previousScreen` onto `navBack` (capped `takeLast(32)`) and clears `navForward` only when `record && screen != previousScreen` (lines 29-33). `record=false` is used only by pop/forward.
- `ControllerNavigation.kt:67-79` — `navigateBack()`: Sources→`backSources`, Player→`closePlayer`, Details→`backDetails` **only if** `detailHistory` is non-empty, else `popHistory`; everything else pops or fabricates a Home entry (line 76).
- `ControllerNavigation.kt:82-87 / 89-94` — forward pops `navForward` pushing the *current* screen onto `navBack`; back is the mirror.
- **Only `navigate()` maintains the stacks.** Details (`beginDetails`, ControllerCatalog.kt:195-201), Sources (ControllerCatalog.kt:106, ControllerLive.kt:117), Player (ControllerPlayback.kt:73) are entered by direct `mutable.value.copy(screen = …)` and never touch them.

---

## Findings

### F1 — CRITICAL (regression): title-bar Back is dead on a first-level Details page
`ControllerNavigation.kt:72`: `Screen.Details -> if (state.detailHistory.isNotEmpty()) backDetails() else popHistory()`.

`beginDetails()` (ControllerCatalog.kt:195-201) enters Details *without* recording anything, so on the very first details opened from Home, `detailHistory` is empty **and** `navBack` is empty → `popHistory()` (line 91) returns without doing anything. The Back chevron is enabled (Main.kt:88: `state.screen != Screen.Home`) but the click is a no-op.

Repro: launch → Home (`navBack=[]`) → click any card (`open()` → `beginDetails`) → click the title-bar Back chevron. Nothing happens. The in-page back (`backDetails`, App.kt:106) still works via its `detailReturnScreen` fallback (ControllerNavigation.kt:57).

This is also a **test regression**: `desktopTest/kotlin/org/viptv/app/DetailNavigationTest.kt:38-44` constructs `screen=Details, detailHistory=[prior], detailReturnScreen=Search`, calls `navigateBack()` twice and asserts the second lands on `Screen.Search`. The first call pops `detailHistory` to `[]` (line 56); the second now takes the new `else popHistory()` branch, `navBack` is empty, and the screen stays `Details` — the assertion fails. At HEAD the branch was `Screen.Details -> backDetails()`, whose empty-history fallback navigates to `detailReturnScreen` — that is the behavior the test encodes. The new stack logic silently broke the documented contract. (Static analysis only — no gradle run, per audit rules; the code path is unambiguous.)

### F2 — HIGH: two Back affordances disagree (title-bar Back skips the origin)
Because Details entry never records (F1's root cause), the screen-level stack is one hop stale. `popHistory` pops the last *recorded* screen, which is the screen before the origin.

Repro: Home → Movies (`navBack=[Home]`) → open a title (Details, `detailReturnScreen=Movies`) → title-bar Back → lands on **Home** (`navForward=[Details]`), skipping Movies; the in-page Back returns to Movies. Evidence: ControllerNavigation.kt:72 vs :57; ControllerCatalog.kt:195-201. Same divergence from Search: Search → open card → title-bar Back → Home instead of Search (navBack's last entry is the pre-Search screen).

### F3 — HIGH: duplicate Details entries — Back loops on itself
`backDetails()`'s fallback (ControllerNavigation.kt:57) calls `navigate(detailReturnScreen)` with `record=true`, pushing **Screen.Details** onto `navBack`. Later, a Details page with empty `detailHistory` backs into that entry via `popHistory` (line 91) — target == current screen.

Repro: Movies → open title → in-page Back (navBack gains `Details`, line 57) → open a title again → title-bar Back → pops `Details`, `navForward=[Details]`, screen stays **Details** with the identical content. Every further Back appends another `Details` to `navForward` (line 92). Back appears frozen; Forward "returns" to the same page. Two history systems (detailHistory/detailReturnScreen vs navBack/navForward) double-book the same hop.

### F4 — HIGH: forward stack is not cleared on new drill-downs
The clear-on-new-destination rule exists only in `navigate()` (ControllerNavigation.kt:29-31). The "new destination" transitions that users perceive as navigation — entering Details (`beginDetails`, ControllerCatalog.kt:195-201), the Sources sheet (ControllerCatalog.kt:106, ControllerLive.kt:117), Player (ControllerPlayback.kt:73) — bypass `navigate()` and leave `navForward` untouched. In browser semantics, following a new link drops the forward stack.

Repro: Home → Movies → Home → Back (now on Movies, `navForward=[Home]`) → open a card (Details) → **Forward chevron still enabled** and jumps to Home — unrelated to the details you just opened. Forward-chevron truthfulness (Main.kt:89) is only as good as the stack, and the stack is stale.

### F5 — MEDIUM: Sources/Details can enter the stacks and be resurrected stale
Two leak paths push overlay screens into `navBack`:
- Sidebar click while on Details/Sources (App.kt:90-92 renders `SideNavigation` for both) → `navigate(destination)` records the overlay screen (ControllerNavigation.kt:31).
- `navigateForward()` pushes the *current* screen unconditionally (ControllerNavigation.kt:85) — including Sources.

Later Back/Forward re-enters `Screen.Sources`/`Screen.Details` via `navigate(target, record=false)`, which does **not** reload sources/detail and does not reset `sourceTitle`/`sources`/`sourceReturnScreen` — a stale sources sheet overlays whatever detail is current, or a spinner renders if `detail == null`. Sources is also pushed by popHistory's forward append (line 92) in the F3 loop.

### F6 — MEDIUM: Esc in the title-bar search returns to a stale `detailReturnScreen`
`DesktopTitleBar.kt:102-107`: on Esc it clears the query and, if on Search, `navigate(state.detailReturnScreen)`. `detailReturnScreen` is set only by `beginDetails` (ControllerCatalog.kt:197-199) and defaults to Home (ApplicationState.kt:67) — it has nothing to do with where the user typed from.

Repro: Movies → type in the title-bar search (first keystroke records Movies, screen=Search) → Esc → lands on **Home**, not Movies; `navBack` gains `Search` and `navForward` is cleared. Worse edge: if `detailReturnScreen == Screen.Search` (Search → open card → in-page Back), Esc on Search navigates to Search — a no-op that leaves the user on an empty results page with the query cleared. Correct source is `navBack.lastOrNull()`/`popHistory()`, not the details route's return target.

### F7 — MEDIUM: `playTitle` path leaves detail-return state stale → wrong returns
`playTitle` (ControllerCatalog.kt:61-71) fetches a detail and jumps straight to `chooseSource` without `beginDetails`: `detailReturnScreen`/`detailHistory` keep whatever an *earlier* details visit left there, while `chooseSource` sets `sourceReturnScreen = Details` whenever `detail != null` (ControllerCatalog.kt:105).

Repro: Library → open title A → in-page Back (sets `detailReturnScreen=Library`) → later, on Movies, press a card's Play → Sources → Back → Details(B) → in-page Back → `navigate(detailReturnScreen)` → **Library** (ControllerNavigation.kt:57). The user came from Movies. `detailHistory` is equally stale, so the in-page Back can restore an unrelated prior detail.

### F8 — MEDIUM: history is wiped by an ordinary Home refresh (accept() scope too broad)
Profile switching is correct: `selectProfile → openProfile → accept()` (ControllerProfiles.kt:17-23; ControllerSession.kt:166-176) rebuilds `ApplicationState`, so `navBack`/`navForward` reset — exactly what "profile switch resets history" wants. Same for `signOut`/`disconnect`/`invalidatePolicyContent` (ControllerSession.kt:143, 157; ControllerParentControls.kt:103-106).

But `refresh()` also calls `accept()` whenever it runs while on Home (ControllerCatalog.kt:18: `if (mutable.value.screen == Screen.Home || refreshed.selected != profile) accept(refreshed, busy = true)`). `refresh()` is invoked from `navigate(Screen.Home)` when `policyRefreshNeeded` (ControllerNavigation.kt:37) and from HomeScreen's refresh/retry affordance (App.kt:93). So: Movies → Home → refresh → `navBack`/`navForward` silently emptied; the Back chevron on Home goes dark. Same profile, no switch — the reset mechanism has no reset *reason*.

Related: `profileSettings` (ControllerProfiles.kt:88-92) hops to Profiles via a direct `copy(screen = Screen.Profiles)` and then `navigate(Screen.Settings)`, pushing **Profiles** into `navBack`; Profiles is a "simple" screen where the chevrons aren't rendered (DesktopTitleBar.kt:50-63), so those entries are unreachable dead weight until the next `accept()`.

### F9 — Player: title-bar Back unreachable; exit always lands on the Sources sheet
- The title bar is not composed during Player (Main.kt:79-85), so `navigateBack()`'s `Screen.Player -> closePlayer()` branch (ControllerNavigation.kt:71) is dead code in the desktop chrome. Back-from-Player is only the in-player affordances (`PlayerRoute.kt:24,29`).
- `closePlayer()` (ControllerPlayback.kt:156-161) always sets `screen = Screen.Sources` — even when the user never saw the sheet because playback auto-started: "best" source selection (ControllerCatalog.kt:120) or a single live source (ControllerLive.kt:121). From a Home card's Play button: Home → (invisible Sources) → Player → close → dropped on a Sources list, then Back → Details (origin=Details, ControllerCatalog.kt:105) — also never visited — then F7's stale return. Live is the exception that works: ControllerLive.kt:117 sets `sourceReturnScreen = Screen.Live`, so Player → close → Sources → back → Live (two presses).
- `backSources`/`closePlayer` never touch the stacks — consistent with their entries not recording, but it means the chevrons' enablement during this flow reflects nothing about the player hop.

### F10 — Back chevron enablement vs reality (Main.kt:88)
`back = if (state.navBack.isNotEmpty() || state.screen != Screen.Home) …`:
- Enabled-but-dead on first-level Details (F1) — the flagship case.
- Enabled on Details when detailHistory is non-empty (works, `backDetails`), on Sources (always works, `backSources`), on Home/other screens when `navBack` is non-empty (works).
- The fabricated fallback (ControllerNavigation.kt:76) `else -> navigate(Screen.Home)` pushes the current screen onto `navBack` — reachable for e.g. Search with empty navBack: Back → Home, then Forward re-enters a blank Search. Odd but coherent.
- The Forward chevron condition (Main.kt:89) is honest *about the stack*, but the stack itself lies (F4/F5). Disabled rendering (38% alpha, DesktopTitleBar.kt:168-170) matches the design's disabled chevron.
- `navigateBack()`'s `Screen.ProfilePin, Screen.ProfileEdit` branches (ControllerNavigation.kt:74) are unreachable from the desktop chrome: those are `simple` screens with no chevrons (DesktopTitleBar.kt:50-63). Dead-but-harmless on desktop.

### F11 — Designed keyboard contract for Back/Forward is unimplemented
`D3HomeWin.html` labels the chevrons `Back (Alt+Left)` / `Forward (Alt+Right)`, and `D3Shortcuts.html` (App column) specifies `Back / forward  Alt ← / →`, `Esc Close or go back`, `Settings Ctrl+,`, `Go to a section Ctrl 1–6`. Desktop `Main.kt` implements **only** Ctrl/⌘+K (Main.kt:43-46). Back/Forward are mouse-only; Esc-as-back exists only inside the title-bar search field (DesktopTitleBar.kt:102-107). UI_INVENTORY.md:78 and OPEN_TASKS.md:36-38 track the chrome task; the shortcut half of the design is missing.

### F12 — Races: no data race found; the hazards are logical
- All `navBack`/`navForward` writers run on the Compose main dispatcher: UI event handlers, `run{}`/`scope.launch` into `scope = rememberCoroutineScope()` (Main.kt:58; ApplicationController.kt:43). The only off-main block, `withContext(Dispatchers.Default)` for local connection creation (ControllerSession.kt:108), writes no state. `MutableStateFlow` writes are atomic; the snapshot-then-write patterns in `popHistory`/`navigateForward` (ControllerNavigation.kt:85, 92) have no suspension point between read and write, so they cannot interleave today.
- The async screen writers are guarded: `playbackRenewal` failure → `screen = Screen.Sources` (ControllerPlayback.kt:60-66) is gated on `remotePlayback === lease`, and `releaseRemotePlayback()` cancels the renewal job (ControllerProgress.kt:8-16) — `navigate()` calls it when leaving Player (ControllerNavigation.kt:28).
- `navigate()`'s `previousScreen` (line 7) is read before the mutations at lines 9-28; none of those mutations change `screen`, so the recorded origin is not corrupted.
- The 401 path replaces state wholesale from a coroutine (ApplicationController.kt:57-61) — wipes the stacks mid-flight, acceptable for session death.
- Fragility notes: `open()`/`playTitle()` capture `navigationGeneration` without incrementing it (ControllerCatalog.kt:57, 62) — staleness there rests entirely on `run()`'s `generation`, not the navigation token; and if any future helper in the pop/forward path ever suspends, the snapshot writes become lost-update windows. Worth a comment, not a fix today.

### F13 — LOW / cosmetic
- Cap asymmetry: `navBack` is capped at 32 only on the record path (ControllerNavigation.kt:31); the appends at lines 85/92 are uncapped. Sizes stay bounded in practice (record pushes clear `navForward`), but the invariant is implicit.
- `Screen.Search` never matches a sidebar destination, so nothing highlights while on Search (App.kt:90-92, Destinations.kt:9-19).
- The sidebar's "selected" during Details/Sources shows `detailReturnScreen` (App.kt:91) — reasonable, but it reflects the stale value from F7.

---

## Verified-correct behaviors (for the record)
- Record path clears `navForward` on a genuinely new recorded destination, and never pushes consecutive duplicates (`screen != previousScreen`, ControllerNavigation.kt:29-31).
- Profile switch, sign-out, disconnect, and parental-gate resets all clear both stacks via fresh `ApplicationState` (ControllerSession.kt:169, 143, 157; ControllerParentControls.kt:103).
- `backSources` always restores a valid origin (sourceReturnScreen is set on every entry: ControllerCatalog.kt:106, ControllerLive.kt:117) — `DetailNavigationTest` case 1 (lines 10-27) passes by construction.
- Back/Forward chevron disabled rendering matches the board's disabled chevron (DesktopTitleBar.kt:168-170 vs D3HomeWin).
- Player never enters the stacks: no sidebar is rendered during Player (App.kt:83, Main.kt:79-85) and `navigateBack` never pops from Player (ControllerNavigation.kt:71).

## Suggested fix directions (not applied — read-only audit)
1. Make the Details branch of `navigateBack()` call `backDetails()` unconditionally (its empty-history fallback already returns to `detailReturnScreen`) — restores the tested contract and fixes F1/F2/F3 in one line; alternatively give `popHistory` a `detailReturnScreen` fallback.
2. Route Details/Sources/Player entries through a stack-aware helper (record origin, clear forward) so `beginDetails`/`chooseSource`/`openSource` stop bypassing history (F4/F5).
3. Esc-in-search should return via `popHistory()`/`navBack.lastOrNull()`, not `detailReturnScreen` (F6); `playTitle` should set `detailReturnScreen`/`detailHistory` like `beginDetails` (F7).
4. Split `accept()` into "session reset" (clear stacks) vs "content refresh" (preserve them), or preserve nav fields in `refresh()`'s accept call (F8).
5. Implement Alt+Left/Alt+Right (and Esc-as-back) in Main.kt's `onPreviewKeyEvent` per D3HomeWin/D3Shortcuts (F11).

## Files examined
commonMain: `state/ControllerNavigation.kt`, `state/ApplicationState.kt`, `state/ApplicationController.kt`, `state/ControllerPageRequest.kt`, `state/ControllerCatalog.kt`, `state/ControllerPlayback.kt`, `state/ControllerLive.kt`, `state/ControllerProfiles.kt`, `state/ControllerSession.kt`, `state/ControllerParentControls.kt`, `state/ControllerParentTitles.kt`, `state/ControllerNextEpisode.kt`, `state/ControllerProgress.kt`, `App.kt`, `components/SideNavigation.kt`, `components/Destinations.kt`, `components/Navigation.kt`, `features/player/PlayerRoute.kt`. desktopMain: `Main.kt`, `DesktopTitleBar.kt`. desktopTest: `DetailNavigationTest.kt`. Docs: `docs/OPEN_TASKS.md`, `docs/UI_INVENTORY.md`. Boards: `design/viptv-design-system/reference/screens/html/desktop/D3HomeWin.html`, `D3Search.html`, `D3Shortcuts.html` (+ `tools/board_spec.py D3HomeWin`). Git: `diff HEAD` on the two modified state files.
