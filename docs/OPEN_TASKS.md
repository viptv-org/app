# Open work — tracked so nothing gets dropped

Status legend: [x] done + verified · [~] done, needs verification · [ ] todo

Per-issue status + evidence: **`docs/ISSUE_LEDGER.md`** (2026-10-19 session 2).
Subagent audits: `docs/evidence/kmp-audit-2026-10-19/`.

## Performance (TV, measured)
- [x] Accessibility node collapse per card (missed vsyncs 7.5% → 1.6–4.0%)
- [x] Caption/play badge composed + alpha-toggled (was re-measuring the shelf per key press)
- [x] Row fade out of the LazyColumn item scope; theme Typography identity-stable
- [x] Rejected + documented: composited shelf scroll (GPU +6 ms), offscreen backdrop (±1 px)
- [ ] Shelf scroll still re-measures ~2.5×/press (measured; no cheaper path found yet)
- [x] `tools/tv_cycle.sh` was dead on arrival (undefined `adb_t`/`SERIAL`, wrong benchmark).
      It now builds release, installs, and runs the R3 `tools/tv_ab.sh` metric.
- [ ] PERF-05..10 remain unmeasured; `docs/TV_FRAME_BUDGET.md` says what each needs.

## Interaction bugs you reported

Fixed this session (see ledger for evidence):
- [x] TV top nav is a chained focus group; Right no longer drops into page content.
- [x] Selected nav item now has a visible focus ring (previously invisible).
- [x] Up from the hero Play button was a dead end (visible `TvHomeVisibility` installed a
      focus boundary) — boundary is now hidden-only.
- [x] Up from every card in the first shelf row returns to the billboard (was index 0 only).
- [x] Settings Up/Down clamp covers `TvAction` rows (Add-ons, Account retry, source integrations).
- [x] TV History screen; empty history focuses a real "Browse Home" action.
- [x] Desktop nav back/forward is a real history stack; new drill-downs clear Forward.
- [x] Desktop search: Esc clears and returns, X clears, Ctrl/⌘+K focuses the field.
- [x] "Apply" no longer renders inside the text field.
- [x] Details first-load error is surfaced (no blank page) with a focusable Back action.
- [x] Hero art-less fallback on phone/tablet/desktop.
- [x] TV browse chrome: catalog chip, Filters chip + count, applied chips, catalogs sheet,
      full-page facet filter, empty/error/loading states, batched "Show results".
- [x] TV card states: no-art title tile, Watched check, New episode/Ended/LIVE labels,
      Returning in meta, cert chip on the meta line, logo on art, S1E2 meta.
- [x] TV FAILED row is the board's message card; PENDING shows 6 skeletons.

- [x] Billboard artwork follows focus (gated on 220 ms of key quiet; no swap while held)
- [x] Frames + logo flash: logo fades in only when its trimmed bitmap is ready
- [x] Shelf scroll-back: focus no longer walks off the left edge (layoutInfo-driven)
- [x] TV details: rows now scroll to the item (`animateScrollToItem`) instead of hard-coded
      pixel offsets that clipped the compact logo — **needs on-device verification**
- [ ] Continue Watching / shelf inset vs board (see positioning audit below)

## Design (2026-10-19 boards)
- [x] TV Settings → Source integrations rebuilt to `Tv3SetSources`
- [ ] Remove custom page titles on TV Movies/Series/Search/My List (boards have none; the
      top nav owns the heading) and align content to the board insets
- [ ] Browse family: catalogs switcher, facet filters, skeleton/empty/error, appending grids
- [ ] Card + row states per canvas dictionary §A1 (badges, no-art, progress, cert chips)
- [ ] Sources sheet states (`Tv3SourcesEmpty`, `Tv3SourcesTorrent`)
- [ ] Add-on entry/install (`Tv3AddonEntry`, `Tv3AddonInstall`) — currently a plain dialog
- [ ] Parental (`Tv3SetParental`), About (`Tv3SetAbout`), Display (`Tv3SetDisplay`)
- [ ] Recovery codes to `M3Recovery`/`D3Recovery` (needs a TV board or an adaptation)
- [ ] Missing boards: TV Calendar, TV History, TV Settings→Account
- [ ] Home polish: `FEATURED · 1 OF 5`, pagination dots, "New episode" badge, card density,
      overflow menu on Continue Watching
- [ ] Positioning audit: every screen vs its board (geometry, not content) — agents running

## Desktop
- [ ] Window chrome per `D3HomeWin`: rounded window, designed title bar (back/forward,
      logo pill, centred search with the `Ctrl K` hint, right icons, min/max/close), 1px
      window outline so it matches native apps
- [ ] General polish pass (spacing, focus rings, hover states)

## Playback / platform contracts
- [x] Local playback no longer falls through to a remote subtitle call on device-local stores
- [ ] Desktop + mobile: local playback only, zero reachable playback-gateway paths
      (iOS is currently remote/gateway by architecture — needs your decision)
- [ ] Torrent support on every platform (TV, desktop, phone, web)

## Auth
- [ ] Audit refresh-token rotation + storage per target (Android KeyStore ✓, desktop
      `systemSecretStore`, browser storage, iOS keychain)
- [ ] Verify refresh on 401 for every transport and that a failed refresh signs out cleanly

## Feature gap
- [ ] Infinite / lazy carousels: `HomeRow.next_cursor` exists on the wire but no engine call
      can page a single home row; needs the connection API first, then shelf-end loading

## Audit findings (agents, 6 per-platform runs)

Reports: `/tmp/viptv-ui-audit-{tv,phone,desktop}.md`, `/tmp/viptv-geometry-{tvhome,tvrest,desktop}.md`.

Desktop
- [x] `NoClassDefFoundError: ControllerProfilesKt$selectProfile$2` on profile click / My List:
      **stale incremental build**, the class is present after a rebuild. Worth a `clean` note in
      the dev docs rather than a code fix.
- [ ] `ClassCastException` in Compose's skiko `GlobalSnapshotManager` when running
      `:composeApp:run` headless (software render). Needs a clean build re-test before it is
      called a code bug.
- [ ] Search cannot be dismissed and has no clear control; results overflow the window with the
      last card cut mid-word.
- [ ] No visible keyboard focus anywhere on desktop (focus rings missing for pointer+keyboard).
- [ ] Opaque black rectangle artifact over centred content; hero synopsis clipped without ellipsis.
- [ ] "Apply" renders as placeholder text inside the search input; History affordances are dead.

Phone
- [ ] Title/details screen can render completely blank (no poster, metadata or episodes) and the
      error vs empty wording is inconsistent ("Try again" with no message).
- [ ] "More info" opens a sheet containing only the title and a Close button; the rail is not scrimmed.
- [ ] Raw backend strings leak into the UI (`API route not found` toast).
- [ ] Audio-language picker shows no current selection and has no close affordance.
- [ ] Focus treatment changes a control's *shape* instead of adding a ring (two styles on one screen).
- [ ] My List shelf/grid clip the last card/bottom row with no peek or bottom padding.
- [ ] Edit-profile form left-hugs and uses a narrower field than other forms; profile screens have
      no Back affordance.
- [ ] Home hero can render with no artwork at all (black hero, text only).
- [ ] Duplicate titles in a Home shelf.

TV (geometry runs pending) — app-side items already known:
- [ ] Continue Watching inset/pitch vs `Tv3Home` (measurement task in progress)
- [ ] Page titles the boards do not have on Movies/Series/Search/My List

## Process
- [x] Device scripts hard-timeout adb (no more hangs / runaway profilers)
- [x] `docs/TV_FRAME_BUDGET.md`, `docs/UI_INVENTORY.md`
- [ ] Collect per-platform audit reports (`/tmp/viptv-ui-audit-*.md`) and fix findings
- [ ] Push everything at the end