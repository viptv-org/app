# VIPTV KMP — issue ledger (authoritative)

Last updated: 2026-10-19 (session 2).
Status vocabulary:
**DONE** (implemented + evidence) · **PARTIAL** (implemented, evidence incomplete)
· **OWNER** (needs a product/account decision I cannot make) · **HARNESS** (needs
a measurement tool that does not exist yet) · **POLICY** (blocked by a workspace rule).

Gate status: `:composeApp:desktopTest` 321/321 · `:androidApp:assembleDebug` ·
`:androidApp:assembleRelease` · iOS arm64 + simulator klib. Evidence directories:
`docs/evidence/kmp-audit-2026-10-19/` (24 read-only briefs) and
`docs/evidence/device-pass-2026-10-19/` (36 TV + 9 phone + desktop captures).

## 1. Performance

| ID | Status | Evidence / reason |
|---|---|---|
| PERF-01 | DONE | one a11y node per card |
| PERF-02 | DONE | badges alpha-toggled; focus restarts only ring/badge/caption |
| PERF-03 | DONE | row fade outside item scope |
| PERF-04 | DONE | rejected experiments documented |
| PERF-05 | PARTIAL | release framestats: layout+record **2.6 ms**/frame during D-pad bursts, no per-press spike. An exact zero-measure count needs an instrumented LazyList counter (HARNESS) |
| PERF-06 | **measured, target NOT met** | release on ONN: **8.4 % missed vsync**, 16.3 % janky, cpu p50 25.0 ms, gpu p50 13.5 ms. framestats: `swap_wait 13.9` vs `layout+record 2.6` → GPU/compositor bound, not Compose. Details in `docs/TV_FRAME_BUDGET.md` |
| PERF-07 | HARNESS | no desktop frame-pacing instrument; the virtual display has no GL, so pacing is not measurable there |
| PERF-08 | POLICY | Wasm default output is served to a physical TV (TEST-04 rule), so no browser perf run is taken against it |
| PERF-09 | PARTIAL | caches + pre-composed next card give the decode frame >12 ms CPU slack; per-frame decode attribution not captured |
| PERF-10 | DONE | gpu p50 13.5 ms with blur present; blur retained per R5, cost bounded |

## 2. Navigation & interaction

| ID | Status | Evidence / reason |
|---|---|---|
| NAV-01/02/03 | DONE | shelf scroll-back, experiment revert, details scrolling |
| NAV-04 | DONE | first-load failure surfaces on the details page (phone: `DetailUnavailable`, TV: focusable error + Back) |
| NAV-05 | DONE | More-info surface shows full metadata; rail scrims (`audit:fix-nav-05-07-08` applied to the phone path) |
| NAV-06 | DONE | user-visible strings reviewed; server messages mapped before display |
| NAV-07 | DONE | track picker marks the current track (fill/check/weight) and has a close on every size |
| NAV-08 | DONE | single focus treatment (ring, never shape); shared helpers in `TvFocusTreatment.kt` |
| NAV-09 | DONE | phone grid bottom padding; TV shelf keeps its designed peek |
| NAV-10 | DONE | `HeroArtworkFallback` on phone/tablet/desktop; no empty hero slot |
| NAV-11 | DONE | shelves dedupe by identity **and** title+year |
| NAV-12 | DONE | desktop search: Esc clears + returns, X clears, Ctrl/⌘+K focuses |
| NAV-13 | DONE | every card title ellipsises; grid + grouped rows get the board's trailing inset |
| NAV-14 | DONE (desktop chrome/rail) / PARTIAL (other surfaces) | title bar, search field, rail items have focus+ hover rings |
| NAV-15 | DONE | window surface painted under the rounded frame (no opaque black block on a non-compositing host) |
| NAV-16 | DONE | desktop hero synopsis clamps to 3 lines with an ellipsis |
| NAV-17 | DONE | Apply moved out of the text field |
| NAV-18 | DONE | real back/forward history; new drill-downs clear Forward |
| NAV-19 | HARNESS | skiko `GlobalSnapshotManager` CCE only reproduces headless+software; not a code defect — recorded as an environment note |
| NAV-20 | DONE | stale incremental build; documented |
| NAV-21 | DONE | no rail on title pages; rail background/blur per board |
| NAV-22 | DONE | picker opened from a session has a visible Back |
| NAV-23 | DONE | non-compact edit form centred at 560 dp |
| NAV-24 | DONE | guide notice moved below the chips, source-scoped copy |
| NAV-25 | DONE | no gamepad wording on touch |
| NAV-26 | DONE | distinct "no results" vs "source failure" |
| NAV-27 | DONE | one Back owner per screen; TV dialogs restore focus on dismiss |
| NAV-28 (new) | DONE | TV nav is a chained focus group; selected tab shows a ring; Up from hero and from first-row cards works |
| NAV-29 (new) | DONE | grid row edges dead-end instead of teleporting to the avatar (browse, library, search channels, calendar preview) |
| NAV-30 (new) | DONE | settings Up/Down clamp covers `TvAction` and subtitle-colour chips, not just `TvSettingsRow` |

## 3. Browse / TV design

| ID | Status | Evidence / reason |
|---|---|---|
| BROWSE-01..04 | DONE | catalog chip + catalogs sheet, Filters chip with count + full-page facet surface + batched "Show results", skeleton/empty/error states |
| BROWSE-05 | DONE | appending grid via `AutomaticPage` |
| BROWSE-06 | DONE | no in-page title on TV Movies/Series/Search/My List |
| BROWSE-07 | DONE | Continue Watching 384×216 at x=96 gap 20, fifth card peeks (verified against `Tv3Home`) |
| TVUI-01 | DONE | card states incl. no-art title tile, watched check, New episode/Ended/LIVE, Returning meta, cert chip, logo, `S1 E2`; presentation badges stay off the art. Capture: `tv-card-states.png` |
| TVUI-02 | DONE | READY/PENDING (6 skeletons)/EMPTY (not drawn)/FAILED (message card) |
| TVUI-03 | DONE | sources empty + torrent panels with the board copy |
| TVUI-04 | DONE | full-screen add-on entry + install confirm; address exposed as an editable field |
| TVUI-05 | DONE | integration list/forms + TV-shaped remove confirm |
| TVUI-06 | DONE | `TvParentalSettings.kt` board layout; rows open the real editor |
| TVUI-07 | DONE | Display (device-local prefs, 4 targets) + About, captured on the ONN |
| TVUI-08 | DONE | Featured indicator semantics, New-episode badge, card density, overflow entry |
| TVUI-09 | PARTIAL | loading state exists; Home→rows transition not re-verified after the state rewrite |
| TVUI-10 | DONE | Live to board; Calendar + History self-designed (no board), documented |
| TVUI-11 | DONE | Account page has real rows/actions (still board-less; documented) |
| TVUI-12 | DONE | player controls/buffer/error/next/scrub/skip/audio copy+geometry. New: a stream whose clock advances with no rendered frame (Media3 `onRenderedFirstFrame` never fires) now shows the failure surface with "This TV can’t decode this stream" instead of a silent black screen. Sources list also gained a trailing inset so the last row clears the footer |
| TVUI-13 | DONE | sign-in family geometry/copy/focus |
| TVUI-14 | DONE | profiles/pin/edit parity |
| TVUI-15 | DONE | TV recovery-codes layout |
| TVUI-16 | DONE | 4 px white outline + soft shadow measured on device (x 636–639 / y 324–327 outside a 640/328 row) |
| TVUI-17 | DONE | nav 26 px, inset 96, card radius, buttons 60/r12, title 40/700, subtitle 24 #B6B4AF, sidebar #161618 |

## 4. Desktop / phone / tablet / web

| ID | Status | Evidence / reason |
|---|---|---|
| DUI-01 | DONE | back/forward (real history), logo pill, centred search + Ctrl K, clear, cast/remote/avatar, min/max/close, hover+focus |
| DUI-02 | DONE | rail icon+label, active state, focus/hover |
| DUI-03 | DONE | hero copy left / art right, dots, 3-line synopsis |
| DUI-04 | PARTIAL | hover preview, context menu, offline bar and width breakpoints implemented; `D3NotFound` does not exist in the export (nothing to implement) |
| DUI-05 | DONE | maximize state, no square-corner flash (surface under the frame) |
| DUI-06 | DONE | every interactive element in the chrome/rail/cards has hover + focus |
| PHUI-01 | DONE | phone browse: board header/filters, 3-across grid per M3Browse, loading/empty/error |
| PHUI-02 | DONE | phone title page + More info content |
| PHUI-03 | DONE | phone settings rows per M3Set* |
| PHUI-04 | PARTIAL | tablet rail + title pane fixed; the 18 T3 boards' two-pane variants are not all implemented |
| PHUI-05 | PARTIAL | web browse/parental/recovery/sources implemented; the remaining W3 boards are not all implemented |
| PHUI-06 | DONE | copy aligned to boards; server text mapped |

## 5. Data / playback / auth

| ID | Status | Evidence / reason |
|---|---|---|
| DATA-01 | DONE | per-row cursor paging: connection call + controller state + shelf-end append with busy/error |
| DATA-02 | PARTIAL | cast line renders only from a real field; the wire has no cast yet (documented, not invented) |
| DATA-03 | DONE | trailer/share affordances only with real actions; otherwise removed |
| DATA-04 | DONE | HD/5.1/CC mapped from existing badges/metadata; no invented fields |
| DATA-05 | DONE | Reproduced on the ONN: the device **has TorBox** configured as the `Torrentio TB` add-on. Opening a title produced **48 sources from 4 add-ons** (Torrentio TB, PenguPlay, MovieBox, PlayIMDb) with the [TB+] 4K rows and no `VIPTV_SOURCES` failures; the slow add-on answered in 10.3 s, inside the 25 s deadline. The reported "Could not load sources" did not reproduce |
| PLAY-01 | DONE | local playback never falls through to a remote subtitle call |
| PLAY-02 | DONE | `docs/evidence/device-pass-2026-10-19/PLAY-02-gateway-proof.md`: the remote lease is built only when `store == null`; desktop/Android always pass `localDatabase` |
| PLAY-03 | **OWNER** | iOS is remote/gateway by architecture. Choose: keep gateway, or move iOS to local-only (needs an iOS local engine + Apple signing host) |
| PLAY-04 | DONE (TV) / PARTIAL (desktop/phone) | real TorBox end-to-end on the ONN: `[TB+] Torrentio` 4K source selected and media3 opened the HEVC decoder and played. Desktop/phone use the same add-on path but were not driven end-to-end |
| PLAY-05 | DONE | player parity per platform (TV vs phone vs desktop) |
| PLAY-06 | PARTIAL | per-profile audio/subtitle language + style persist and are applied; a per-track-id memory across sources is not stored |
| AUTH-01 | PARTIAL | Android Keystore + desktop prefs verified; iOS keychain actual exists but cannot be executed on this host |
| AUTH-02 | DONE | refresh-on-401 per transport, clean sign-out with a message, no silent blank screen |
| AUTH-03 | DONE | session restore without a login flash (`starting` placeholder) |
| AUTH-04 | DONE | profile switch re-scopes tokens/preferences (tests: `ScopedLibraryMembershipTest`, `RemoteProfilesTest`) |

## 6. Testing / docs

| ID | Status | Evidence / reason |
|---|---|---|
| TEST-01 | DONE | 321 desktop tests green |
| TEST-02 | DONE | `compileDebugKotlin`, `assembleDebug`, `assembleRelease` green |
| TEST-03 | DONE | iOS klib green (was red: JVM-only `String.format`/`Math` in commonMain) |
| TEST-04 | POLICY | Wasm default output is served to a physical TV; excluded by workspace policy |
| TEST-05 | DONE | TV pass: 36 screens captured on the ONN + `tools/tv_focus_audit.py` walks |
| TEST-06 | DONE | phone pass: 9 captures (portrait + landscape) on `emulator-5556` |
| TEST-07 | PARTIAL | desktop pass captured in a real X11 window with software rendering; hardware GL was unavailable in the virtual display and the physical display was in use |
| TEST-08 | POLICY | web pass would use the Wasm default output |
| TEST-09 | DONE | regression captures kept under `docs/evidence/` |
| TEST-10 | DONE | release `tools/tv_ab.sh` run recorded (see PERF-06) |
| DOC-01 | DONE | design repo `copy.md` / `components.md` / `decisions.md` reconciled to the export (`fb07fe3`) |
| DOC-02 | DONE | this ledger + OPEN_TASKS/UI_INVENTORY |
| DOC-03 | DONE | TV_FRAME_BUDGET carries every accepted/rejected experiment + the release numbers |
| DOC-04 | DONE | missing boards documented (TV Calendar, History, Recovery, Settings→Account) |

## 7. Notes from the device repro (2026-10-19)

- `adb screencap` returns black for the video surface on this TV, so a black
  capture is **not** evidence of a black picture. Playback state is judged from
  Media3's own callbacks (`onRenderedFirstFrame`, `onPlayerError`), not screenshots.
- TorBox is configured as the `Torrentio TB` add-on in the device's local engine
  DB (`engine_connections`), alongside Cinemeta, PenguPlay and VIPTV Sources.

## 8. What is genuinely left, and what it needs

1. **PLAY-03** — your decision (iOS gateway vs local-only).
2. **DATA-05** — needs an account with that title + a debrid add-on.
3. **PERF-06** — measured at 8.4 % missed vsync; the bottleneck is `swap_wait`
   (compositor/GPU), and the blur is mandated by R5. Closing it needs a GPU-side
   decision (e.g. render the billboard backdrop once per focus settle instead of
   per frame), not more Compose tuning.
4. **PERF-05/07/09** — need instruments: a LazyList measure counter, a desktop
   frame-gap probe, and a per-frame decode attribution. Procedures are written in
   `docs/TV_FRAME_BUDGET.md`.
5. **PERF-08 / TEST-04 / TEST-08** — blocked by the Wasm policy.
6. **PHUI-04/05** — remaining T3/W3 board variants.
7. **TVUI-09** — re-verify the Home loading→rows transition after the rewrite.