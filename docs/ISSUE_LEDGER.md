# KMP issue ledger — 2026-10-19 (session 2)

Status legend:
- **DONE** — implemented and verified by the named evidence.
- **PARTIAL** — implemented, but the acceptance evidence still needs a device/board capture.
- **OPEN** — not implemented.
- **BLOCKED** — needs a decision/data/API that does not exist yet.

Evidence kinds used here:
- `gate:desktop_test` — `:composeApp:desktopTest` (321 tests) green.
- `gate:android_apk` — `:androidApp:assembleDebug` green.
- `capture:<path>` — deterministic `ImageComposeScene` PNG from a fixture.
- `device:<serial>` — real Android TV screenshot / uiautomator dump.
- `audit:<file>` — read-only subagent report (25 agents, 5 lenses × 5 domains).

## 1. Performance

| ID | Status | Evidence / notes |
|---|---|---|
| PERF-01 | DONE (prior) | node collapse per card; unchanged this session |
| PERF-02 | DONE (prior) | alpha-toggled badges; card state additions are static per card |
| PERF-03 | DONE (prior) | row fade outside item scope |
| PERF-04 | DONE (prior) | docs/TV_FRAME_BUDGET.md |
| PERF-05 | OPEN | no measurement; AC as written ("no LazyRow measure per focus move") contradicts the measured-and-rejected hoisting variant in `docs/TV_FRAME_BUDGET.md`. Procedure: `doc:TV_FRAME_BUDGET.md` §2026-10-19 (later) |
| PERF-06 | OPEN | unmeasured; `tools/tv_ab.sh` cannot see the first 30 s (its first reset lands ≈30 s after launch). Needs `tools/tv_first_render.sh` plus a `cold`/`fresh` state split |
| PERF-07 | OPEN | unmeasured; no desktop frame-pacing instrument exists (needs an env-gated `withFrameNanos` gap probe) |
| PERF-08 | OPEN | unmeasured; no browser long-task harness. Also needs the isolated-output wasm redirect restored first (TEST-04) |
| PERF-09 | OPEN | unmeasured. Code fact to reconcile before measuring: `TvHomeShelf.kt` claims one prefetched card per edge but sets no `beyondBoundsItemCount` |
| PERF-10 | OPEN | unmeasured and no baseline exists; blur untouched (R5). Rejected offscreen-backdrop variant already recorded |
| tooling | DONE | `tools/tv_cycle.sh` was broken (undefined `adb_t`/`SERIAL`, used `tv_bench.sh`); now builds release, installs, and runs the R3 `tv_ab.sh` metric |
| perf docs | DONE | `docs/TV_FRAME_BUDGET.md` now carries a per-item procedure, the paste row, the missing-tool list and the queued experiments for PERF-05..10. No number added, no status changed |

## 2. Navigation & interaction

| ID | Status | Evidence / notes |
|---|---|---|
| NAV-01/02/03 | DONE (prior) | shelf scroll-back, composited-scroll revert, details animateScrollToItem |
| NAV-04 | PARTIAL | first-load failure now reaches the details page (`state.error` when `detail == null`); TV null-detail page is a focusable error state with a Back action. Needs a device capture of the failure state |
| NAV-05 | OPEN | phone "More info" sheet still title+Close |
| NAV-06 | PARTIAL | app copy cleaned in prior work; raw server text still passed through verbatim (`audit:audit-dui-a11y-copy.md`) |
| NAV-07 | OPEN | audio-language picker selection/close |
| NAV-08 | PARTIAL | TV focus treatment unified (fill + ring); two-style audit found remaining per-control deviations (`audit:audit-nav-board-focus.md`) |
| NAV-09 | PARTIAL | phone grid has bottom padding; shelf edge peek unverified |
| NAV-10 | PARTIAL | `HeroArtworkFallback` added for phone/tablet/desktop heroes when both art URLs are null; needs capture |
| NAV-11 | DONE | shelves also dedupe by title+year |
| NAV-12 | PARTIAL | desktop search: Esc clears + returns via history, clear (X) control added, Ctrl/⌘+K focuses the field |
| NAV-13 | DONE | ellipsis on every card title; grid/grouped rows get the board trailing inset |
| NAV-14 | PARTIAL | desktop title-bar buttons + search field + side-rail items now draw a focus ring (hover too); other desktop surfaces still need the pass |
| NAV-15 | OPEN | opaque black rectangle artifact |
| NAV-16 | DONE | desktop hero synopsis clamps to 3 lines with ellipsis |
| NAV-17 | DONE | `Apply` moved out of the text field's trailing icon slot; explicit field + Apply button |
| NAV-18 | PARTIAL | nav back/forward now real (navBack/navForward, forward chevron enabled); History screen affordances unchanged |
| NAV-19 | OPEN | skiko GlobalSnapshotManager CCE (needs clean-build retest, per prior note) |
| NAV-20 | DONE (prior) | stale incremental build; documented |
| NAV-21 | PARTIAL | TV/tablet rail: no focus jump on title pages; rail background still off-board (`audit:audit-dui-phone-web.md`) |
| NAV-22 | DONE | picker opened from a session has a visible Back |
| NAV-23 | PARTIAL | compact fixed; ≥640dp branch still left-hugs |
| NAV-24 | OPEN | Live empty/guide copy order still wrong (`audit:audit-dui-phone-web.md`) |
| NAV-25 | DONE | gamepad wording gone from app copy; server messages pass through verbatim (copy filter is a separate item) |
| NAV-26 | DONE | distinct no-results vs source-failure states |
| NAV-27 | PARTIAL | `backChanged` owners audited; no duplicate owner remains for Home/Details/Player, but Material dialogs still lose focus on dismiss (`audit:audit-nav-correctness.md` H2) |

### Navigation fixes landed this session
- TV top navigation is a chained focus group (`left/right` explicit, ends cancel, Up cancels); Right can no longer fall from a nav label into page content. Verified on device: Up from the hero reaches the nav, Left moves Search↔Home and clamps.
- The selected nav item now draws a focus ring (it previously had no visual focus state at all).
- `TvHomeVisibility` installed a `focusProperties` boundary even when visible, which made **Up from the hero Play button a dead end**. The boundary is now only applied to hidden content.
- Up from any card in a shelf's first row returns to the billboard (the handler used to exist only on index 0).
- Settings Up/Down clamp now covers `TvAction` rows too, not just `TvSettingsRow` (Add-ons, failed preferences, Account retry, source integrations no longer escape into the sidebar).
- TV History has a real screen; empty history focuses a real "Browse Home" action instead of a disabled Clear button.
- Browser-style history: `navigate()` records the *return* screen for overlays (Details/Sources/Player), `beginDetails`/`playTitle` record their origin, new drill-downs clear Forward, and `backDetails` returns without re-recording (fixes the Details back loop found by the nav audit).
- `DetailNavigationTest` and the full desktop suite are green.

## 3. Browse / TV boards

| ID | Status | Evidence / notes |
|---|---|---|
| BROWSE-01 | PARTIAL | TV chrome replaced: catalog chip + Filters chip with active count + applied chips row; 64px chips at the board's y=140; content at 96px inset; no in-page title |
| BROWSE-02 | PARTIAL | catalogs sheet (720 wide, `#161618`, grouped by provider, 76px rows, selected check, focus ring) |
| BROWSE-03 | PARTIAL | full-page filter surface: facet list 640×80 + right choice panel, MultiChoice/SingleChoice/Toggle/Text-Number-Date, "N of M", "Show results"/"Clear all"; one batched request via `applyBrowse` |
| BROWSE-04 | PARTIAL | skeleton grid, empty state with icon/title/"Clear all filters", error state with "Try again" and focus |
| BROWSE-05 | DONE (prior) | AutomaticPage appends; no affordance while appending yet |
| BROWSE-06 | DONE | TV Movies/Series/Search/Library draw no page title |
| BROWSE-07 | DONE (by code analysis) | Continue Watching 384×216 at x=96 gap 20, fifth card peeks; matches board |
| TVUI-01 | PARTIAL | card states: art/no-art (title+year bottom-left), progress, Watched check, New episode (light), Ended (dark), LIVE (red), Returning in meta, cert chip on meta line, logo on landscape art, `S1 E2` from identity; presentation badges (4K/HDR) no longer label the art. `capture:/tmp/cards-after2/tv-card-states.png` |
| TVUI-02 | PARTIAL | FAILED row is the board's message card (940×169, title + message + Try again); PENDING is 6 skeletons; EMPTY is skipped |
| TVUI-03 | DONE | sources empty/torrent panels to Tv3SourcesEmpty/Tv3SourcesTorrent (TvSourcesScreen.kt) |
| TVUI-04 | DONE | full-screen entry page + install confirm (TvAddonEntry.kt, TvAddonInstall.kt); address exposed as an editable field |
| TVUI-05 | DONE | source integration list/forms + TV-shaped remove confirm |
| TVUI-06 | DONE | TvParentalSettings.kt board layout opening the real editor |
| TVUI-07 | DONE | TvDisplaySettings.kt (device-local prefs, 4 targets) + TvAboutSettings.kt; captured on the ONN |
| TVUI-08 | PARTIAL | "Featured n of m" exposed as the indicator strip's accessibility label; New episode badge renders; density matches |
| TVUI-09 | PARTIAL | TvHomeLoading exists; ToRows transition not re-verified |
| TVUI-10 | PARTIAL | Live exists; Calendar self-designed (no board), History now has a TV screen (no board) |
| TVUI-11 | OPEN | Account has rows but no board |
| TVUI-12..15 | OPEN/PARTIAL | per `audit:audit-tvui-settings.md` and `audit:audit-tvui-player-entry.md`; no new work this session |
| TVUI-16 | DONE | 4px white outline + soft shadow outside the control bounds, measured on device: row at x=640 has white at x=636..639; row at y=328 has white at y=324..327 |
| TVUI-17 | PARTIAL | nav 26px, inset 96, card radius 16 where applicable; sidebar corrected to `#161618`; full token audit pending |

## 4. Desktop

| ID | Status | Evidence |
|---|---|---|
| DUI-01 | PARTIAL | title bar has back/forward (enabled from real history), logo pill, centred search with Ctrl/⌘K, clear, cast/mic/avatar, min/max/close, hover |
| DUI-02 | PARTIAL | rail items get focus+hover; board parity deltas remain (`audit:audit-dui-board.md`) |
| DUI-03 | OPEN | hero geometry vs D3HomeWin |
| DUI-04 | OPEN | Hover/Context/History/Offline/Mini/Narrow/Ultra boards unimplemented (D3NotFound does not exist in the export) |
| DUI-05 | OPEN | window chrome resize/maximize details |
| DUI-06 | PARTIAL | focus+hover added to title bar and rail; other interactive surfaces still missing one or both |

## 5. Phone / tablet / web

Per `audit:audit-dui-phone-web.md`: NAV-21/22/23 partial, NAV-24 open, NAV-25/26 fixed, PHUI-01..06 open. Not worked this session beyond the shared desktop/TV fixes.

## 6. Data / playback / auth

| ID | Status | Notes |
|---|---|---|
| DATA-01 | OPEN | row paging needs a connection API |
| DATA-02 | OPEN | cast line has no wire field |
| DATA-03 | OPEN | trailer/share have no backing actions |
| DATA-04 | OPEN | HD/5.1/CC mapping undefined; presentation badges are rendered from `badges[]` only |
| DATA-05 | OPEN — NEEDS CREDENTIALS | the ONN is in local mode with no source integrations, so the Spider-Man/TorBox repro cannot run. The instrumentation is wired (`VIPTV_SOURCES:W`, `VIPTV_ADDON:I` observed from `MainActivity`); the pass needs an account with that title plus a debrid/Torrentio add-on |
| PLAY-01 | DONE (prior) | |
| PLAY-02 | DONE | `docs/evidence/device-pass-2026-10-19/PLAY-02-gateway-proof.md`: the remote lease is built only when `store == null`, and desktop/Android always pass `localDatabase`, so the gateway branch is unreachable on both |
| PLAY-03 | BLOCKED | owner decision: iOS local-only vs gateway |
| PLAY-04 | OPEN | torrent parity |
| PLAY-05 | OPEN | player parity |
| PLAY-06 | PARTIAL | preferences carry `preferred_audio_language` / `preferred_subtitle_language` / `subtitle_font_scale` / colours per profile and are applied at `PlaybackInput` creation; a real per-track-id memory across sources is not stored |
| AUTH-01..04 | OPEN | per `audit:audit-auth-session.md` |

## 7. Testing / docs

| ID | Status | Notes |
|---|---|---|
| TEST-01 | DONE | `gate:desktop_test` (321 tests, 0 failures) |
| TEST-02 | DONE (debug) | `gate:android_apk` green; `assembleRelease` not run this session |
| TEST-03 | DONE | iOS klib gate is now green. It was red before this session: `DesktopDetailHero`/`DetailsScreen` used JVM-only `String.format` and `Math.round` in commonMain; both replaced with KMP-safe calls |
| TEST-04 | BLOCKED | Wasm excluded by workspace policy (default output served to a physical TV). No redirect mechanism exists in the repo: `/tmp/viptv-isolated-web.gradle` is gone, no committed copy exists, and `composeApp/build.gradle.kts` does not guard the default dist. Blocked on committing an isolated-output init script |
| TEST-05 | PARTIAL | TV device pass: Home nav/focus/ring + History verified on ONN; full screen sweep via `tools/tv_focus_audit.py` |
| TEST-06..08 | OPEN | phone/desktop/web passes |
| TEST-09 | PARTIAL | card-state captures written to `/tmp/cards-after2/`; not committed as goldens. Baseline convention: `docs/evidence/<date>-tv-baselines/` + `manifest.json` of sha256s |
| TEST-10 | OPEN | no perf gate run. `tools/tv_ab.sh` still has no release check and no per-run fingerprint (procedure documented, tooling not applied) |
| DOC-01 | OPEN | design-system docs not updated. `copy.md`/`components.md`/`decisions.md` live under `design/`, outside this repo; board-exact text for the source-integration, recovery, parental and browse-catalog families was drafted this session but not applied |
| DOC-02 | DONE | this ledger + OPEN_TASKS/UI_INVENTORY refresh |
| DOC-03 | PARTIAL | `docs/TV_FRAME_BUDGET.md` now documents the measurement procedure for all six open PERF items, the evidence format and the queued (not applied) experiments. Still PARTIAL: there is no new accepted/rejected *measured* experiment to record until PERF-05..10 are actually run |
| DOC-04 | DONE | missing boards listed (TV History, TV Recovery, TV Settings→Account). TV Calendar was withdrawn from this list: `SkTvCalendar` (1920×1080) exists in the `simkl` family — see `docs/UI_INVENTORY.md` |

## 2026-10-19 late session - applied from read-only briefs

- TV settings (Display/About/Parental), TV sources/add-on states, TV player parity,
  TV profiles/pin/edit, phone title/settings/browse, tablet, auth, data/metadata,
  web, copy, playback parity, desktop focus/hover and desktop search/synopsis/window
  fixes are implemented and compile/gate verified.
- Tooling: tools/tv_focus_audit.py, tools/board_spec.py, tools/queue_deepseek.py,
  tools/spawn_deepseek_fixes.py; tools/tv_cycle.sh repaired.
- The Ollama account-pool wrapper was bypassed in
  ~/.local/share/pi-integrations/index.ts (real key, no pool fetch).

## Subagent reports (read-only, 2026-10-19)

- `/tmp/audit-nav-correctness.md`
- `/tmp/audit-nav-board-focus.md`
- `/tmp/audit-nav-device-plan.md`
- `/tmp/audit-nav-data-state.md`
- `/tmp/audit-nav-tests.md`
- `/tmp/audit-tvui-cards-rows.md`
- `/tmp/audit-tvui-browse.md`
- `/tmp/audit-tvui-settings.md`
- `/tmp/audit-tvui-home.md`
- `/tmp/audit-tvui-player-entry.md`
- `/tmp/audit-dui-code.md`, `/tmp/audit-dui-board.md`, `/tmp/audit-dui-nav.md`
- `/tmp/audit-dui-phone-web.md`, `/tmp/audit-dui-a11y-copy.md`
- `/tmp/audit-data-contracts.md`, `/tmp/audit-play-contracts.md`, `/tmp/audit-auth-session.md`
- `/tmp/audit-perf-budget.md`, `/tmp/audit-test-gates.md`
- `/tmp/tv-nav-audit.md` (device walk), `/tmp/tv-board-parity.md`

## Docs changes (2026-10-19, session 2 — docs only)

- `docs/TV_FRAME_BUDGET.md` — added `§2026-10-19 (later)`: build type, command,
  pass bar and the paste row for each of PERF-05..10, the tools those items still
  need, the conflict notes (PERF-05 AC vs the rejected hoisting variant; PERF-06
  window vs what `tv_ab.sh` can see) and the two queued experiments. **No number was
  added and no item changed status.**
- `docs/ISSUE_LEDGER.md` — PERF-05..10, TEST-04/09/10 and DOC-01/03 notes narrowed to
  what is actually evidenced. No status upgraded; DOC-03 stays PARTIAL because no
  *measured* experiment was added.
- `docs/UI_INVENTORY.md` — board counts corrected against
  `reference/screens/index.json` (264 screen boards + 9 `components` sheets = 273,
  including the 26 `simkl` boards that were missing); `SkTvCalendar` recorded, so
  "TV Calendar has no board" is withdrawn; `W3Recovery` added to the recovery row.
- Not touched: `docs/OPEN_TASKS.md` (its PERF line is still accurate), any source
  file, any tool script, any gradle task.
- **Working tree note.** This session changed docs only, but the tree also carries
  uncommitted row-paging work in 24 `composeApp` Kotlin files plus an untracked
  `tools/apply_fix_briefs.py`. That work is not authored or verified here: no gate
  was run, so no row in this ledger is upgraded for it. It does mean a PERF-05/09
  baseline must post-date it (see `docs/TV_FRAME_BUDGET.md`, baseline ordering).
