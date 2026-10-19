# VIPTV KMP — complete issue list

Generated 2026-10-19 from the handoff prompt, the design export, this session's work, and
per-platform audits. One line = one issue. Each has acceptance criteria (AC) and a
verification method (V). "DONE" items are shipped in `experiment/kmp-v2`.

Per-issue implementation status and evidence: **`docs/ISSUE_LEDGER.md`**
(updated 2026-10-19; subagent audits in `docs/evidence/kmp-audit-2026-10-19/`).

Design source of truth: `design/viptv-design-system/reference/screens/{html,img}/<platform>/<id>`,
`reference/canvas-data-dictionary.md`, `plans/DESIGN_REQUEST-2026-10-18.md`.

## 0. TEAM RULES (apply to every issue)
- R1  Five sub-agents per issue; only mark DONE when all five independently agree it is correct.
- R2  No visual change without proof: deterministic fixture capture (desktop `ImageComposeScene`
      harness) or same-state device screenshot, compared with `compare_images`; byte-identical
      unless the issue is explicitly a visual change.
- R3  Performance claims only from a **release** build with `tools/tv_ab.sh` (interleaved A/B,
      missing-vsync rate), never from a debug build or a single run.
- R4  Every device command wrapped in `timeout` (`ADB_TIMEOUT`/`adb_t` in `tools/tv_*.sh`).
      Never `simpleperf --app`, never an un-timed `adb` call.
- R5  Blur stays. Do not remove or fake the backdrop blur to gain frames.
- R6  Desktop + phone playback is local-engine only. No new gateway references.
- R7  One gate job at a time; read the real exit status, not the launch.
- R8  Report per issue: what changed, AC evidence, V evidence, files touched.

## 1. PERFORMANCE
| ID | Issue | AC | V |
|---|---|---|---|
| PERF-01 | DONE — a11y node collapse per card | one node/card, focus state preserved | tests + capture |
| PERF-02 | DONE — caption/play badge alpha-toggled, not added/removed | no shelf re-measure on focus | trace shows no `rowslazy` |
| PERF-03 | DONE — row fade outside item scope; typography identity stable | no per-focus recomposition of shelves | trace |
| PERF-04 | DONE — measured + documented rejected experiments | — | `docs/TV_FRAME_BUDGET.md` |
| PERF-05 | Shelf still re-measures ~2.5×/key press (≈4.5 ms each) | no LazyRow measure attributable to a focus move | trace: `shelfmeasure` count ≈ 0 per press |
| PERF-06 | 60 fps **on first render** (no warm-up) on the ONN TV | missed vsyncs < 1 % in the first 30 s after install | `tools/tv_ab.sh` on a fresh install |
| PERF-07 | Desktop scroll/interaction frame budget | no dropped frames while scrolling Home/Browse | desktop trace + before/after |
| PERF-08 | Web (wasm) interaction budget | no long tasks over 50 ms while scrolling | Chrome profile |
| PERF-09 | Image decode spikes when a new card enters | no frame > 24 ms caused by a card entering | trace |
| PERF-10 | Backdrop blur cost bounded (must stay visually identical) | GPU median unchanged or lower | `gfxinfo` GPU percentiles |

## 2. NAVIGATION & INTERACTION (audits + reports)
| ID | Issue | AC | V |
|---|---|---|---|
| NAV-01 | DONE — shelf scroll-back focus walks off the left edge | first card reachable and on-screen after a long run right+left | 10×left on TV |
| NAV-02 | DONE — composited-scroll experiment inverted the animation | animation direction matches focus direction | reverted |
| NAV-03 | DONE — TV details clipped the compact logo | full logo visible when episodes/More Like This focused | device screenshot |
| NAV-04 | Title/details can render **completely blank** (no poster, meta, episodes) | every state renders content or an explicit error with a retry | phone + TV screenshots |
| NAV-05 | "More info" sheet shows only the title + Close, rail not scrimmed | full metadata sheet per board, rail dimmed | capture |
| NAV-06 | Raw backend text leaks to UI (`API route not found`) | only human copy in toasts/errors | grep + screenshot |
| NAV-07 | Audio-language picker shows no current selection, no close | current value marked, dismissible | capture |
| NAV-08 | Focus indication changes control *shape* instead of adding a ring; two styles on one screen | one focus treatment per platform | capture |
| NAV-09 | My List shelf/grid clips last card and bottom row | peek or padding at both edges | capture |
| NAV-10 | Home hero can render with no artwork | never an art-less hero; fallback or skip | capture |
| NAV-11 | Duplicate titles in a Home shelf | shelf has unique cards | data check |
| NAV-12 | Desktop: search cannot be dismissed, no clear control | Esc/clear resets, focus returns | desktop capture |
| NAV-13 | Desktop: search results overflow, last card cut mid-word | grid respects window bounds | capture |
| NAV-14 | Desktop: **no visible keyboard focus anywhere** | every focusable shows a ring | capture |
| NAV-15 | Desktop: opaque black rectangle over centred content | artifact gone | capture |
| NAV-16 | Desktop: hero synopsis clipped without ellipsis | ellipsis or full text | capture |
| NAV-17 | Desktop: "Apply" renders as placeholder inside search input | correct label/affordance | capture |
| NAV-18 | Desktop: History affordances are dead | History opens and lists items | click-through |
| NAV-19 | Desktop: `:composeApp:run` throws skiko `GlobalSnapshotManager` ClassCastException | clean build starts and renders | run + screenshot |
| NAV-20 | Desktop: `NoClassDefFoundError` on profile click (stale incremental build) | document clean-build requirement / fix detection | run |
| NAV-21 | Phone: D-pad Down from title jumps into the nav rail; expanded rail has no opaque background | focus containment per board | capture |
| NAV-22 | Phone: profile screens have no visible Back affordance | back control present | capture |
| NAV-23 | Phone: edit-profile form left-hugs and uses a narrower field than other forms | matches other forms | capture |
| NAV-24 | Phone: Live TV shows "no guide" above chips with channel-specific wording | correct copy per state | capture |
| NAV-25 | Phone: Discover error uses gamepad wording on touch | platform-appropriate copy | capture |
| NAV-26 | Phone: search "no results" and "source failure" collapsed into one message | distinct states | capture |
| NAV-27 | Duplicate/late `backChanged` handlers leaving Back mapped to the wrong action | one owner per screen | tests |

## 3. DESIGN — series/movies/search tops (your "ugliest thing in existence")
| ID | Issue | AC | V |
|---|---|---|---|
| BROWSE-01 | Series/Movies filter row + search bar are undesigned and ugly | interim: replace with the board's segmented filters + sort control and a real search field (see BROWSE-02..05); mark for a dedicated design pass | capture before/after |
| BROWSE-02 | No catalogs switcher (board `Tv3MoviesCatalogs`, `M3BrowseCatalogs`, `D3BrowseCatalogs`) | switcher exists on all platforms | capture |
| BROWSE-03 | No facet filter set (board `Tv3MoviesFilters`, `M3BrowseFilters`, `D3BrowseFilters`) | Text/SingleChoice/MultiChoice/Toggle/Number/Date render and apply | capture + API probe |
| BROWSE-04 | No skeleton/empty/error states (`Tv3MoviesLoading/Empty/Error`) | all three states match boards | capture |
| BROWSE-05 | Grid does not append pages while scrolling | appends at the last visible row | trace + data |
| BROWSE-06 | Page titles the boards do not have on TV Movies/Series/Search/My List | content starts at the board inset (96 px), nav owns the heading | capture vs board |
| BROWSE-07 | Continue Watching row inset/pitch unverified vs `Tv3Home` | geometry within ±2 px of board or documented as intended | measured capture |

## 4. DESIGN — TV boards
| ID | Issue |
|---|---|
| TVUI-01 | Card states per `Tv3CardStates` + canvas dict §A1 (badges, no-art fallback, progress, cert chips) |
| TVUI-02 | Row states per `Tv3RowStates` |
| TVUI-03 | Sources sheet states: `Tv3SourcesEmpty`, `Tv3SourcesTorrent` (copy + layout) |
| TVUI-04 | Add-on manual entry + install pages `Tv3AddonEntry`, `Tv3AddonInstall` (currently an AlertDialog) |
| TVUI-05 | Source integration **forms**: `Tv3SourcesViptv`, `Tv3SourcesM3u`, `Tv3SourcesXtream`, `Tv3SourcesRemove` (list is DONE) |
| TVUI-06 | Parental content page `Tv3SetParental` |
| TVUI-07 | About `Tv3SetAbout`, Display `Tv3SetDisplay` |
| TVUI-08 | Home polish: `FEATURED · 1 OF 5` + pagination dots, "New episode" badge on Continue Watching, 4.5–5 cards visible, three-dot overflow menu, "New" badges on catalog rows |
| TVUI-09 | `Tv3HomeLoading` + `Tv3HomeToRows` transitions match boards |
| TVUI-10 | Live `Tv3Live`, Calendar (no board — needs design), History (no board — needs design) |
| TVUI-11 | Settings → Account has no TV board; current fallback row is a placeholder |
| TVUI-12 | Player states `Tv3Player*` (controls/buffer/error/next/scrub/skip/audio) parity |
| TVUI-13 | Sign-in family `Tv3SignIn*`, `Tv3Welcome`, `Tv3Splash`, `Tv3SignedIn`, `Tv3SignOut` parity |
| TVUI-14 | Profiles/Pin/Edit parity (`Tv3Profiles`, `Tv3Pin`, `Tv3ProfileEdit`) |
| TVUI-15 | Recovery codes: no TV board; adapt `M3Recovery`/`D3Recovery` or commission one |
| TVUI-16 | Focus ring spec from boards: focused row is a filled `#F4F2EE` row with a 4 px white outline + soft shadow, not a thin outline |
| TVUI-17 | Tokens audit: nav 26 px, content inset 96 px, card radius 16, buttons 60 h / radius 12, section title 40/700, subtitle 24 `#B6B4AF` |

## 5. DESIGN — phone / tablet / web
| ID | Issue |
|---|---|
| PHUI-01 | Phone browse family (`M3Browse*`, `M3BrowseFilters`, `BrowseEmpty/Error/Loading`) |
| PHUI-02 | Phone title page (`M3Title*`, `M3Recovery`, `M3About`, `M3SetDisplay`) |
| PHUI-03 | Phone settings rows (`M3Set*`) parity |
| PHUI-04 | Tablet boards (18): navigation rail + two-pane layouts |
| PHUI-05 | Web boards (40): `W3Browse*`, `W3Parental`, `W3Recovery`, `W3SetSources` |
| PHUI-06 | Cross-platform copy consistency (same strings from the boards) |

## 6. DESIGN — desktop
| ID | Issue |
|---|---|
| DUI-01 | Title bar to `D3HomeWin`: back/forward chevrons, logo pill, centred search + `Ctrl K`, cast/mic/avatar, min/max/close, hover states (framing/outline DONE) |
| DUI-02 | Left rail parity (icon+label, active state, settings/help at the bottom) |
| DUI-03 | Home hero block geometry per `D3HomeWin` (hero image right, copy left, pagination dots) |
| DUI-04 | `D3HomeHover`, `D3Context`, `D3History`, `D3Offline`, `D3NotFound`, `D3Mini`, `D3Narrow`, `D3Ultra` variants |
| DUI-05 | Window chrome: native-feeling resize/hover cursors, maximize state, no square-corner flash |
| DUI-06 | Desktop focus/hover design pass (every interactive element has both) |

## 7. DATA / API GAPS
| ID | Issue |
|---|---|
| DATA-01 | Infinite/lazy carousels: `HomeRow.next_cursor` exists, **no engine call pages a single home row** — design the connection API, then shelf-end loading with per-row busy/error |
| DATA-02 | Title page cast line ("Starring …"): wire `Card` has no cast — add to the engine detail projection or drop from the board |
| DATA-03 | Trailer + Share circles on the hero have no backing data/actions |
| DATA-04 | HD/5.1/CC chips: only `metadata.certification` + `badges[]` exist — define the mapping or drop |
| DATA-05 | "Could not load sources" for Spider-Man: reproduce with `logcat -s VIPTV_SOURCES:W VIPTV_ADDON:I`, confirm the TMDB key path, and verify a TorBox/Torrentio source appears within the 25 s deadline |

## 8. PLAYBACK / PLATFORM CONTRACTS
| ID | Issue |
|---|---|
| PLAY-01 | DONE — local playback no longer falls through to a remote subtitle call |
| PLAY-02 | Desktop + phone: prove zero reachable gateway paths (audit `forceGateway`, `reopensForSeek`, `first-frame`, `remoteSeek`) |
| PLAY-03 | iOS is remote/gateway by architecture — **owner decision needed**: local-only or keep gateway |
| PLAY-04 | Torrent support on every platform (TV, desktop, phone, web): discovery, debrid requirement copy, panel states, and a real TorBox end-to-end |
| PLAY-05 | Player parity per platform (`Tv3Player*` vs desktop/phone) |
| PLAY-06 | Subtitle/audio pickers persist per profile on every platform |

## 9. AUTH
| ID | Issue |
|---|---|
| AUTH-01 | Audit refresh-token rotation + storage per target: Android KeyStore, desktop `systemSecretStore`, browser storage, iOS keychain |
| AUTH-02 | Refresh on 401 for every transport; on failure sign out cleanly with a message, never a silent blank screen |
| AUTH-03 | Session survives app restart on every platform; restore never flashes the login screen |
| AUTH-04 | Multi-profile: switching profiles re-scopes tokens/preferences with no leakage between profiles |

## 10. TESTING & GATES (per issue, and as a suite)
| ID | Issue |
|---|---|
| TEST-01 | `:composeApp:desktopTest` green |
| TEST-02 | `:androidApp:compileDebugKotlin` + `:androidApp:assembleRelease` green |
| TEST-03 | iOS klib gate: `:composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileKotlinIosArm64` |
| TEST-04 | Wasm build in the **isolated output directory only** (never the default distribution — it is served to a physical TV) |
| TEST-05 | TV device pass: home browse, title, episodes, sources, player, settings — with screenshots |
| TEST-06 | Phone emulator pass (portrait + landscape) |
| TEST-07 | Desktop pass with a real window (X11, hardware render) |
| TEST-08 | Web pass in a browser |
| TEST-09 | Regression captures: keep and diff fixture PNGs for every screen changed |
| TEST-10 | Perf gate: `tools/tv_ab.sh` before/after for any change touching the home/browse path |

## 11. DOCS
| ID | Issue |
|---|---|
| DOC-01 | `viptv-design-system/copy.md`, `components.md`, `decisions.md` updated for the 264-board export (`docs/process/DESIGN_SYNC.md`) |
| DOC-02 | `docs/OPEN_TASKS.md` + `docs/UI_INVENTORY.md` kept current |
| DOC-03 | `docs/TV_FRAME_BUDGET.md` extended with every accepted/rejected perf experiment |
| DOC-04 | Missing boards documented: TV Calendar, TV History, TV Recovery, TV Settings→Account |