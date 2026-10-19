# Navigation correctness under data states — read-only source audit

**Scope:** `app/composeApp` commonMain state controllers + `features/{browse,details,sources,library,live}` screens, plus the desktop/Android/wasm shell back wiring. Read-only: no builds, no emulators, no file edits.

**Snapshot caveat (important):** the tree is edited concurrently. `state/ControllerNavigation.kt` gained a `returnTo()` helper *between two of my reads* (a fix for history double-recording on `backDetails`); several state files carry 2026-10-18 mtimes. All `file:line` citations below were re-verified against the working tree at write time. Re-check line numbers before acting on this report.

Path prefix for all citations: `app/composeApp/src/` unless noted otherwise.

---

## 1. Back returns to the wrong screen

### F1 (High) — Desktop Back from ProfilePin / ProfileEdit pops global history instead of returning to Profiles
- `commonMain/kotlin/org/viptv/app/state/ControllerNavigation.kt:67-79` — `navigateBack()`:
  `Screen.ProfilePin` / `Screen.ProfileEdit` fall into the `else` branch, which prefers
  `popHistory()` whenever `navBack` is non-empty; the `ProfilePin, ProfileEdit -> navigate(Screen.Profiles)`
  fallback (line 74) is only reached with an *empty* back stack.
- `state/ControllerProfiles.kt:5-12` (`selectProfile` → `screen = Screen.ProfilePin`, direct mutation)
  and `:34` (`editProfile` → `screen = Screen.ProfileEdit`) never record `Screen.Profiles` in `navBack`.
- Trace: Home → top-nav "Switch profile" (`navigate(Screen.Profiles)`, `navBack=[Home]`) → tap a locked
  profile (`screen=ProfilePin`, no history write) → desktop title-bar Back → `popHistory()` → target
  `Home`. The user is dumped on Home, skipping the Profiles screen they came from. Same for
  `ProfileEdit`.
- Android is unaffected: `androidApp/src/main/kotlin/org/viptv/experiment/MainActivity.kt:50` maps
  `ProfilePin/ProfileEdit/Home -> navigate(Screen.Profiles)` before any history logic.
- Only the desktop chevron uses `navigateBack` (`desktopMain/kotlin/org/viptv/app/Main.kt:88`).
- Fix direction: give pin/edit the same treatment Details got — either record `Profiles` in `navBack`
  when mutating the screen, or route `Screen.ProfilePin/ProfileEdit -> returnTo(Screen.Profiles)`
  before the `popHistory` branch.

### F2 (High) — Late `openSource` coroutine force-navigates to the Player from wherever the user went
- `state/ControllerPlayback.kt:73` — `openSource` publishes
  `screen = Screen.Player` guarded only by nothing (the run-level `generation == operation` guard
  protects `sources`/`findingSources` writes, not the screen flip).
- `state/ControllerNavigation.kt:23-27` — leaving Sources cancels the discovery job **only while
  `findingSources == true`**. During the "Opening stream…" phase (`busy=true`, `findingSources=false`,
  set by the `finally` at `state/ControllerCatalog.kt:118`) nothing cancels the job or bumps `generation`.
- Trace (desktop): choose a source → sheet shows "Opening stream…" → click a SideNavigation
  destination that does *not* start its own `run` (Home: `ControllerNavigation.kt:37` only refreshes when
  `policyRefreshNeeded`; Profiles: no load clause) → the open-lease completes → `screen = Screen.Player`
  lands on top of the screen the user just chose. Movies/Settings/History would cancel the job via their
  own `run`/`runPage`, which is why the bug hides.
- Fix direction: in `openSource`, only flip `screen` when `mutable.value.screen == Screen.Sources`
  (or bump `generation` in the navigate-away-from-Sources clause regardless of `findingSources`).

### F3 (High) — Back into a Details screen whose page request was cancelled renders blank (no focus target)
- `state/ControllerNavigation.kt:9-15` — navigating away from Details cancels `pageRequestJob`
  (the `open()` job) and bumps `generation`.
- `state/ControllerNavigation.kt:97-102` — `popHistory()` later navigates *back into* `Screen.Details`
  (Details is a legitimate `navBack` entry: `navigate(Screen.Settings)` from Details records it at
  `:29-33`).
- `state/ControllerCatalog.kt:51-58` — `open()` only sets `detail` if `readCurrentPage` succeeds; the
  cancelled request leaves `detail = null`, `busy = false`, `error = null`.
- Rendering: `features/details/DetailsScreen.kt:27` (`if (detail == null && busy && !browser)`) and
  `features/details/TvDetailsScreen.kt:33` both only handle the *busy* case; with `detail == null &&
  !busy` the LazyColumn composes nothing but an optional error row. On TV there is also no top
  navigation on Details (`App.kt:85` excludes Details/Sources), so the remote has zero focusable
  targets until another Back (`backDetails → returnTo`, `ControllerNavigation.kt:51-58`) recovers.
- Repro: open a card, immediately click a side-nav destination while the detail loads, press the
  desktop Back chevron → blank Details.
- Related, already tracked: phone "Title/details screen can render completely blank" in
  `app/docs/OPEN_TASKS.md` (same null/`!busy` hole reached via the error path, where the error row is
  the only content and nothing is focusable on TV).
- Fix direction: treat `detail == null && screen == Details` as a loading state (or auto-return via
  `returnTo(detailReturnScreen)`).

### F4 (Medium) — `accept()` wipes the whole back/forward history on data refreshes
- `state/ControllerSession.kt:166-176` — `accept()` replaces state with a fresh `ApplicationState`
  (default `navBack`/`navForward` = empty).
- Callers that run while the user stays put:
  - `state/ControllerCatalog.kt:8-16` — `refresh()` on Home → `accept(refreshed, busy = true)`:
    after any Home refresh (manual retry or `policyRefreshNeeded`), the desktop Back chevron is dead
    (`desktopMain/Main.kt:88` gates it on `navBack.isNotEmpty() || screen != Home`) and Forward is gone.
  - `state/ControllerParentControls.kt:15` and `:64` — parental refresh/policy save →
    `accept(snapshot, Settings-or-Home)` — history wiped mid-session; `settingsEntrySection` deep-link
    is dropped too (`ControllerNavigation.kt:33` preserves it only across `navigate`).
  - `state/ControllerParentControls.kt:97-106` — `invalidatePolicyContent()` builds a near-empty
    `ApplicationState(screen = Screen.Settings, …)` — navBack/navForward, browse/live/search/detail all
    discarded before the bootstrap restores them; any failure in between leaves the session on a
    skeletal Settings screen.
- Profile switch (`ControllerProfiles.kt:22-28` → `accept`) intentionally resets; the refresh paths do
  not need to.
- Fix direction: `accept(value, screen = currentScreen, preserveHistory = true)` variant that keeps
  `navBack`/`navForward`/`settingsEntrySection` when the screen is unchanged.

### F5 (Medium) — Desktop Esc in search returns to a stale `detailReturnScreen`
- `desktopMain/kotlin/org/viptv/app/DesktopTitleBar.kt:102-108` — Esc clears the query and, when on
  Search, calls `navigate(state.detailReturnScreen)`.
- `detailReturnScreen` is a Details-scoped field (`state/ControllerCatalog.kt:195-201`, `beginDetails`)
  that survives long after the details page closed. Trace: open a card from Live → back → search from
  Home → press Esc → you land on **Live** (the last details origin), not the screen you came from.
  Should use `popHistory`-style back or `navigateBack()`.

### F6 (Medium) — Android system Back uses a flat screen map, not the history
- `androidApp/src/main/kotlin/org/viptv/experiment/MainActivity.kt:46-55` — `else ->
  controller.navigate(Screen.Home)` for Discover/Movies/Series/Anime/Calendar/Search/Live/Library/
  History/Settings: Back always jumps Home and *records* the current screen (`record=true`), growing
  `navBack` with entries nothing on Android ever consumes. Chain from any content screen is
  content → Home (`:50`) → Profiles → BackHandler disabled (`:46` gating) → app exit. TV remote Back
  on browse screens therefore returns to Home rather than the origin screen. (Entry-level `entryBack`
  handlers — TV Home rows `returnHero`, player panels, splash — do take precedence correctly at
  `:47`.)

### F7 (Low) — Browser (wasm) hardware/history Back is not intercepted at all
- `wasmJsMain/kotlin/org/viptv/app/Main.kt:23-40` never wires `entryBackChanged` (default no-op in
  `App.kt:43`) and there is no `popstate`/`pushState` handling anywhere in `wasmJsMain` (grep verified).
  Only the Escape key with body focus dismisses the details dialog
  (`wasmJsMain/.../features/details/InitialBrowserEscape.kt:13-22`,
  used by `features/details/BrowserDetailsDialog.kt:38`). Pressing the browser's Back button while a
  details dialog / sources sheet is open navigates the page away instead of closing the overlay.

---

## 2. Screens that lose focus or render degraded when data changes

### F8 (Medium) — Returning to Library resets paged data, scroll, filter and focus
- `state/ControllerNavigation.kt:41` — every `navigate(Screen.Library)` runs `loadLibraryPage()`.
- `state/ControllerLibraryPage.kt:15-29` — a non-append, non-refresh load replaces `libraryPage` with
  the **first page only**; pages accumulated via `libraryMore()` (offset-based) are dropped
  (`combined = page` at `:27`).
- `App.kt:92-116` — Library (unlike Search at `:97` and Details/Sources at `:103`) has no
  `SaveableStateProvider`, and the branch is disposed when a detail opens; `TvLibraryScreen.kt:25-26`
  (filter/sort as plain `remember`) and `:35` (`rememberLazyGridState()`, not saveable) reset with it.
- Trace: page My List deep (AutomaticPage at `TvLibraryScreen.kt:37`), open a title, press Back →
  `backDetails → returnTo(Library) → navigate(Library)` → page-1 reload, grid at top, "All"/default
  sort, focus re-enters via the nav tab (`components/TvNavigation.kt:29-31` re-requests the tab for
  Library).
- Fix direction: keep `libraryPage` (skip the reload when the query/profile is unchanged and the page
  is fresh), or make the reload `refresh = true` (which retains prior pages via
  `ControllerLibraryPage.kt:19-24`).

### F9 (Medium) — Browse family: Back/navigation reload clears the grid and resets all UI state
- `state/ControllerNavigation.kt:34-36` — every navigation to Discover/Movies/Series/Anime reruns
  `loadBrowse` (including `record=false` returns from Details via `returnTo`,
  `ControllerNavigation.kt:89-95`).
- `state/ControllerBrowse.kt:54` — non-append loads clear `browseCards`/`browseNext` *before* the
  fetch, so a same-screen re-navigation (tab re-click, `browseFilter` at `:20`, `browseMore` guard
  at `:30-33`) blanks the grid while `busy`.
- Because the browse branch is disposed on Details navigation (`App.kt:95`), back-from-details also
  resets: grid scroll (`TvBrowseScreen.kt:34`), genre filter (`:18`), TV `enterResults` flow, and the
  desktop sort (`features/browse/BrowseScreen.kt:38`, `remember(screen)`).
- Focus: paging (`append=true`) is fine — items keyed by `cardLookupKey`
  (`TvBrowseScreen.kt:65`) keep focus. The non-append clear disposes the focused card; nothing
  re-requests focus (the `LaunchedEffect` at `TvBrowseScreen.kt:38-46` only acts on `enterResults`, and
  when `selected` is empty mid-reload it *bounces focus back up* to the filter row). On TV this shows
  as a dead remote until a D-pad press re-anchors.
- Also note `state/ControllerAddons.kt:26-27` — `loadAddons` (run on entering Settings,
  `ControllerNavigation.kt:40`) clears `browseCards` in the background; harmless only because browse
  always reloads on return.

### F10 (Medium) — Live: reloads clear the program guides, swapping the guide for a bare channel list and dropping focus; the query resets to default
- `state/ControllerNavigation.kt:39` — `navigate(Screen.Live)` always runs `loadLive(LiveQuery())`:
  connection selection, group filter and offset are reset to defaults (`state/ControllerLive.kt:15`),
  while `liveFavoritesOnly` (state, `ApplicationState.kt:74`) survives — an inconsistent
  favorites-only + default-query combination.
- `state/ControllerLive.kt:17-19` — every non-append load clears `liveGuides`/`liveGuideChannel`.
- `features/live/LiveRoute.kt:29` — with guides empty, `start` becomes null; `TvLiveScreen.kt:56-58`
  then swaps the focused `LiveGuide` for the plain `LazyColumn`, disposing the focused program cell —
  focus loss on every reload (tab re-click, `filterLive`/`selectLiveConnection`/`refreshLive`,
  `ControllerLive.kt:53-73`).
- `state/ControllerLive.kt:48-50` — the trailing `showLiveGuide(selected)` re-selects the first channel
  after a reload (focus/preview move), by design for filters but jarring for a same-screen reload.
- Mitigated case: Back from the sources sheet uses `backSources` (`ControllerNavigation.kt:60-64`,
  direct screen assignment) — no reload, guides/scroll retained.
- Minor related churn: `TvLiveScreen.kt:33-38` keys its channel-selection `LaunchedEffect` on the
  `channels` **list instance**, which `LiveRoute.kt:14-15` re-creates every 15 s clock tick — the effect
  re-runs constantly and re-invokes `guideRequested` (idempotent only while the guide cache is fresh,
  `ControllerLive.kt:76-101`).

### F11 (Medium) — Sources sheet: progressive discovery can yank focus/scroll from the auto-anchored row
- `features/sources/TvSourcesScreen.kt:63-76` — `LaunchedEffect(visible.firstOrNull()?.id, error)`
  re-anchors focus (and `scrollToItem`) whenever the head of the list changes or an error arrives.
  Guard: `if (focusedSource != null && focusedSource != autoFocused) return` (`:65`).
- Residual hole: if the user **is on the auto-focused row** (`focusedSource == autoFocused`), the final
  ranking reorder (`state/ControllerCatalog.kt:114-117`, `sources = ranked`) or a partial publish
  (`:111-113`) changes the head → focus is re-requested on the new head and the list scrolls
  (`:71-75`, index+1 once `error != null`) while the user is about to press Select on the old row.
- Fix direction: stop re-anchoring once *any* row has been focused (drop the `== autoFocused` escape),
  or key the effect on "discovery finished" instead of the head id.

### F12 (Low) — History: clearing history leaves focus requestable nowhere
- `features/history/TvHistoryScreen.kt:48-56` retries `clearFocus.requestFocus()` for 30 frames, but
  after `clearHistory` (`state/ControllerLibrary.kt:11-16`, `history = emptyList()`) the Clear action is
  `enabled = !busy && history.isNotEmpty()` (`TvHistoryScreen.kt:68`) — disabled, hence not focusable
  (`components/TvAction.kt` uses `clickable(enabled=…)`); the row list is empty and the effect never
  attaches. Focus is lost (recoverable only via a D-pad press that finds the top nav, which *is*
  composed on History, `App.kt:85`).

### F13 (Low) — Library/Home data mutations dispose the focused card without a restorer
- `state/ControllerLibrary.kt:21` — removing the last favourite on the Library screen reruns
  `loadLibraryPage()` (page-1 replace, F8) → the focused card's key disappears → focus lost
  (`TvLibraryScreen.kt:58` grid has no `focusRestorer`, unlike `TvHomeShelf.kt:36` /
  `TvSearchScreen.kt:86`).
- `state/ControllerLibrary.kt:46-60` — `updateLibrary` flips the Home favorites row to
  `RowState.EMPTY` when the last card is removed → focused card disposed; the shelf's `focusRestorer`
  (`features/home/TvHomeShelf.kt:36`) softens this but has no focusable fallback item in EMPTY state.

### F14 (Low) — Every controller action restarts episode paging on the Details screen
- `state/ApplicationController.kt:55-59` — `run()` unconditionally cancels `detailPagingJob` and
  clears `detailLoadingOffset`/`detailPageError`. Any details-screen action that uses `run`
  (e.g. `toggleFavorite`, `state/ControllerLibrary.kt:6`) kills an in-flight `detailPage` load;
  `AutomaticEpisodes` (`features/details/AutomaticEpisodes.kt:12-16`, keyed on `busy`) re-fires it
  ~150 ms later with the *same* offset — a visible stop/restart and a redundant fetch (dedup only at
  `state/ControllerDetailPages.kt:35`).

---

## 3. Late coroutines that publish into the wrong screen

### F15 (Medium) — Search results, busy flag and errors are not page-scoped
- `state/ControllerCatalog.kt:24-46` — `search` uses plain `run` (not `runPage`), so `navigate()` never
  cancels it (`ControllerNavigation.kt:9-15` only cancels `pageRequestJob`; Calendar/Sources/Player
  clauses don't apply).
- The publish guard (`:44-46`) checks `generation`/term/profile/session but **not `screen`**: leaving
  Search for Home lets the job finish, flashing the global `busy` indicator on Home
  (`features/home/HomeScreen.kt` renders `state.busy`) and, on failure, landing
  "Could not reach your server…" on the Home screen via the screen-agnostic central handler
  (`state/ApplicationController.kt:63-76`).
- Same central-handler property makes any *abandoned* non-page job's error render on whatever screen
  is current — the only screen check is the 401 reset.
- Fix direction: route `search` through `runPage`/`readCurrentPage` (the machinery already exists,
  `state/ControllerPageRequest.kt:9-21,17-39`), or add `mutable.value.screen == Screen.Search` to the
  publish guard and the error path.

### F16 (Low) — Background Live favourite writes publish off-screen
- `state/ControllerLiveFavorites.kt:31-39,62-69` — success/pending writes are guarded by
  `ownsLive(context, false)` (profile/connection/scope, no screen) and the `finally` by
  profile/connection only. Harmless today (only the Live route reads those fields) but the same
  pattern that F2/F15 violate.

---

## 4. Verified-correct guards (for balance)

These were checked and hold under refresh/error/empty/paging/profile-switch:

- `readCurrentPage` session/navigation guards (`state/ControllerPageRequest.kt:17-39`) — abandoned
  page reads never restore a route; 401-expiry still retires through the central handler.
- `detailPage`'s `current()` (`state/ControllerDetailPages.kt:11,18-20`) — screen, profile, home scope,
  card identity and connection identity all checked before merge; `cancelDetailPaging` on every
  non-Details navigation (`ControllerNavigation.kt:22`).
- `loadLive`'s `ownsLive` checks (`state/ControllerLive.kt:33,38,44`) and `showLiveGuide`'s
  request/guard discipline (`:76-101`).
- `loadNextEpisode`'s `current()` incl. `screen == Player` (`state/ControllerNextEpisode.kt:30-35`).
- `loadCalendar`'s stream callback screen guard (`state/ControllerCalendar.kt:23-26`) plus the
  navigate-away cancel (`ControllerNavigation.kt:17-21`).
- `playbackRenewal` failure jump to Sources (`state/ControllerPlayback.kt:63-66`) is properly fenced by
  `remotePlayback === lease` and by navigate/close releasing the lease
  (`ControllerNavigation.kt:28`, `ControllerPlayback.kt:156-161`).
- Details↔Sources return state: `beginDetails`/`backDetails`/`backSources`/`returnTo`
  (`ControllerCatalog.kt:195-201`, `ControllerNavigation.kt:51-64,89-95`) now avoid history
  double-recording; the Details/Sources `SaveableStateProvider` keeps the page composed behind the
  sheet (`App.kt:101-109`), and `TvDetailsScreen` keys scroll/focus state per card identity
  (`features/details/TvDetailsScreen.kt:37-41`) with shelf-level restore
  (`features/details/TvEpisodeShelf.kt:42-48`).
- TV Home focus claiming is epoch-guarded (`features/home/TvHomeScreen.kt:53,83-88`) and rows use
  `focusRestorer` (`TvHomeShelf.kt:36`); TV search restores the focused result on return
  (`features/search/TvSearchScreen.kt:30-32,88-90`).
- Profile switching (`state/ControllerProfiles.kt:22-28`, `accept`) resets all cross-profile data and
  cancels live/library/nextEpisode jobs (`ControllerSession.kt:166-180`, `resetLiveRequests`).

---

## 5. Priority shortlist

| # | Finding | Where | Severity |
|---|---------|-------|----------|
| F1 | Back from ProfilePin/Edit goes to Home, not Profiles | `ControllerNavigation.kt:73-77` + `ControllerProfiles.kt:8,34` | High |
| F2 | Late `openSource` flips screen to Player after navigating away | `ControllerPlayback.kt:73` + `ControllerNavigation.kt:23-27` | High |
| F3 | Blank, unfocusable Details after cancelled page request + popHistory | `ControllerNavigation.kt:9-15,97-102` + `DetailsScreen.kt:27` / `TvDetailsScreen.kt:33` | High |
| F4 | `accept()` wipes back/forward history on Home/parental refresh | `ControllerSession.kt:166-176` + `ControllerCatalog.kt:16`, `ControllerParentControls.kt:15,64,97` | Med |
| F5 | Esc in desktop search returns to stale `detailReturnScreen` | `DesktopTitleBar.kt:106` | Med |
| F8 | Library back-navigation resets paged data/scroll/filter | `ControllerNavigation.kt:41` + `ControllerLibraryPage.kt:15-29` | Med |
| F9 | Browse re-navigation clears grid, resets scroll/filter, drops focus | `ControllerNavigation.kt:34-36` + `ControllerBrowse.kt:54` | Med |
| F10 | Live reloads clear guides (guide↔list swap, focus loss, query reset) | `ControllerLive.kt:17-19,48-50` + `TvLiveScreen.kt:56-58` | Med |
| F15 | Search job publishes busy/results/errors onto other screens | `ControllerCatalog.kt:24-46` + `ApplicationController.kt:63-76` | Med |
| F6/F7/F11/F12/F13/F14/F16 | Flat Android back map; wasm Back unwired; sources focus yank; history/library/Home focus dead-ends; paging restart | see above | Low |
