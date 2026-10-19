# TV card & row audit — TVUI-01 / TVUI-02 / BROWSE-07

Read-only audit. No build, no gradle/adb/emulator, no repository files modified.
Method: source reading + `app/tools/board_spec.py` geometry + board HTML (exact inline styles) +
`reference/canvas-data-dictionary.md` §A1/§A2 + `reference/canvas-tokens.json`, cross-checked against
the wire contract (`org.viptv.engine.wire`) and its producers (local Rust engine + backend server).

**Audited files** (working tree is edited concurrently; hashes at time of reading):
- `app/composeApp/src/commonMain/kotlin/org/viptv/app/features/home/TvCatalogCard.kt` — md5 `0c1ab43df5bb…` (changed *during* this audit: no-art fallback now renders the title, generic badges no longer label the art)
- `app/composeApp/src/commonMain/kotlin/org/viptv/app/features/home/TvHomeShelf.kt` — md5 `225a1431…`
- Support: `TvHomeScreen.kt`, `TvHomeLoading.kt`, `TvWatchProgress.kt`, `components/TvMediaBadge.kt`, `components/FocusOutline.kt`, `components/TvShelfScroll.kt`, `components/MediaPresentation.kt`, `theme/Theme.kt` (Palette), `engine/wire/EngineWire.kt`
- Wire producers: `native/core/crates/viptv-engine/src/{home.rs,playback.rs,live.rs,stremio/media.rs,simkl.rs}`, `native/core/crates/viptv-core/src/local_engine/{history.rs,addons/home.rs}`, `backend/server/src/{continuation.rs,library.rs,simkl.rs}`

Boards: `Tv3CardStates` (A1), `Tv3RowStates` (A2), `Tv3Home`, `Tv3HomeToRows`, `Tv3HomeLoading`.

---

## 1. Verdicts

| Issue | Verdict |
|---|---|
| **BROWSE-07** Continue Watching inset/pitch vs `Tv3Home` | **PASS by code analysis** — geometry matches the board exactly (all values below). ISSUES.md AC ("±2 px or documented") is met at dp==board-px; still needs the *measured capture* per team rule R2 to be closed. |
| **TVUI-01** Card states per `Tv3CardStates` + §A1 | **PARTIAL** — state machine is implemented for badges/progress/cert/no-art, but: `logo` state missing, `identity.episode` not rendered from the wire, certification chip on the wrong caption line, meta line can show genres & drop rating, generic badges ("New") are dropped, focus ring lacks the board's double-ring, and most states are unreachable on today's wire. |
| **TVUI-02** Row states per `Tv3RowStates` | **PARTIAL** — READY ✓, EMPTY ✓ (skipped entirely), PENDING ~ (5 vs 6 skeletons, height jump), FAILED ✗ (bare text button instead of the board's message card), `next_cursor` ignored (and never produced by the engine). |

---

## 2. Row geometry (BROWSE-07) — app vs board

App values at `scale = maxWidth/1920.dp` (`TvHomeScreen.kt:111`), so dp·scale ≡ board px on the 1920×1080 canvas.

| Measurement | Board | App | Citation |
|---|---|---|---|
| Continue card size | 384 × 216 (art, r10) | 384dp × 216dp (16:9 of 384), r 10dp | `TvCatalogCard.kt:63,80,60` |
| Continue card x / gap | first x=96, next x=500 (…904, 1308, 1712) → gap 20 | contentPadding h 96dp, spacedBy 20dp → same 96/500/904/1308/1712 ladder | `TvHomeShelf.kt:41` |
| 5th card peek | x=1712 (208px visible) | identical (96 + 4·404) | same |
| Portrait card size/gap | 232 × 348 (r10), x=96, 344, 592… gap 16 | 232dp × 348dp (2:3), spacedBy 16dp → same ladder | `TvCatalogCard.kt:63,80`; `TvHomeShelf.kt:41` |
| Row title | x=96, 30px w600, ls −0.01em | x=96dp, 30sp SemiBold, ls −0.3sp, height 40dp | `TvHomeShelf.kt:39-40` |
| Title top → cards top | 52px (h2@824 → cards@876; h2@832?) = 36px lh + 16 | 40dp (title box) + 12dp = 52dp | `TvHomeShelf.kt:38-41` |
| Billboard state: continue title y | 824 (`Tv3Home` h2@824, cards@876) | LazyColumn top padding 824dp when not browsing | `TvHomeScreen.kt:117,160` |
| Browsing state: focused row title y | 512, cards y 564 (token `tvMotion/rowToRow`) | top padding 512dp; cards at 512+40+12=564 | `TvHomeScreen.kt:117,160`; `TvHomeShelf.kt:38-41` |
| Row pitch | not pinned by a settled board frame (`Tv3HomeToRows` is mid-animation; its frame has row-2 h2 at y=896) | 344dp row + 40dp spacing = 384 (continue) / 476+40=516 (portrait) — row 2 title lands at 896, matching that board frame | `TvHomeScreen.kt:102-103,162` |
| Row-to-row motion | 350ms standard; rows above fade 200ms | tween(350) scroll, alpha tween(200) | `TvHomeScreen.kt:175-179,63-70` |
| Shelf scroll | one card (300ms) when focus reaches 2nd-to-last visible card | step 404/248dp, target `index-(visible-2)`, tween(300) | `TvShelfScroll.kt:24-49`; `TvHomeShelf.kt:34` |

**Geometry defects found (not board mismatches):**
- `rowOffset` hardcodes READY heights (384/516). A PENDING portrait row is actually 400dp (no 76dp caption reserve in `TvHomeSkeletonCard`) and a FAILED row ~100dp, so scroll targets computed for rows *below* them overshoot while rows load. `TvHomeScreen.kt:102-103` vs `TvHomeLoading.kt:34-36`.
- PENDING→READY makes every row grow 76dp (skeleton has no caption box; real card reserves 76dp — `TvCatalogCard.kt:105`), so rows below jump once per row load.

---

## 3. Card states the wire can produce — do they render correctly?

Wire `Card` = `EngineWire.kt:40-55` (`identity{id,kind,ids,episode,edition}, category, metadata{year,runtime,genres,rating,rating_votes,certification,alternate_titles}, provider_id, title, subtitle, description, poster, landscape, logo, progress, badges, parent_identity, added_at_millis`).

### 3.1 Artwork

| State | Renders? | Detail |
|---|---|---|
| Wide (continue) card, `landscape` present | ✓ | `AsyncImage` Crop on `Palette.surface` #161618 tile, r10 (`TvCatalogCard.kt:80-83`). Board art tile bg #161618 ✓. |
| Portrait card, `poster` present | ✓ | same, 2:3 (`:80-83`) |
| **No art** (landscape/poster null/blank) | ✓ w/ style deltas | Dark tile **#1A1A1C flat** with **title (26sp portrait / 22sp wide, maxLines 2) + year** bottom-left, 14dp pad (`:174-192`). Board: gradient `#2A2A30→#141416`, title 28/30px 3-line clamp, year 18px #B6B4AF, pad 18px, gap 6. Renders the right *content* now (title+year, same size — §A1) but not the board's exact treatment. Note: the class KDoc (`:53-54`) still says "title's initials" — stale after the concurrent edit. |
| **`logo` present** (wire: stremio `logo/clearlogo/…` — `stremio/media.rs:166`; live cards: `live.rs:149` sets logo into poster+landscape, `Card.logo=None`) | **✗ MISSING** | `TvCatalogCard` never reads `card.logo` (only heroes/billboards do). §A1/board: logo sits bottom-left on the art (max 64px, 55% width, lighten blend) with bottom scrim, and the text title is dropped. App always shows the text title and never the logo. TVUI-01 gap. |

### 3.2 Badges (0..N)

`TvCardStates.of` (`:122-160`): filters "Simkl" (`:123`); `watched` from badge list (`:125`); `live` = kind `channel`/`tv` or badge "live" (`:126-127`); label priority LIVE › New episode › Ended, everything else → no label (`:134-139`, comment: presentation badges stay off the card).

| Badge state | Renders? | Detail |
|---|---|---|
| 0 badges | ✓ plain card | — |
| `["Simkl"]` | ✓ filtered | (`:123`; producer `viptv-engine/src/simkl.rs:107`) |
| `["Watched"]` | ✓ corner check | 34dp circle top-end 10dp inset, bg #0B0B0C@.78, 20dp check #F4F2EE (`:92-97`). Board: identical geometry/alpha; check stroke-width 3 vs Lucide default 2 (thinner). Art not dimmed ✓. **Unreachable today** — no producer emits a "Watched" *badge*; the engine conveys watched via `subtitle="Watched"` (`playback.rs:50-53`) which renders as meta *text* instead. |
| `["New episode"]` | ✓ light label | 30px-high pill, pad 10/4, r6, #F4F2EE on #111113, 18/700 (`:84-91,140-149`). Board exact match (CardStates span). Label inset 10dp matches the 180px CardStates card but the Home boards use 12px on shelf-sized cards — 2px off. **Unreachable today** (no producer; preview-only). |
| `["Ended"]` | ✓ dark label | rgba(11,11,12,.8) fill, #F4F2EE ink (`:141-148`). Board exact. **Unreachable today.** |
| `["Returning"]` | ✓ meta line | no art label; appended to the meta row (`:155`, `:230-233`). Board "Three badges" caption ✓ ("Returning moves to the meta line"). §A1's priority list (New episode › Returning › Ended) would make Returning an art label when alone — app follows the *board* instead (defensible; boards+dict disagree). **Unreachable today.** |
| `["New episode","Watched","Returning"]` (board "Three badges") | ✓ all three | label + check + meta line simultaneously — correct. |
| LIVE: kind `channel`/`tv` **and** badge `"LIVE"` | ✓ w/ style delta | red #E5484D fill ✓ (`:143`). App styles it like every label (18sp/700, pad 10/4, h≈30); board: 16px/800, ls .06em, pad 9/0, h28. **Unreachable today**: no producer emits "LIVE" (see live row below). |
| kind `"live"` (engine `live_card`, `live.rs:154`) | **✗ not detected** | The live check matches `"channel"`/`"tv"` only (`:126`); the engine's own live cards use kind `"live"` with `badges: []` (`live.rs:166`) → no LIVE label, no channel tile. §A1/board "Live" state (gradient tile, big channel name, LIVE, programme as meta) is **not implemented**; a live card falls back to the ordinary no-art tile (initials-era code now renders the channel *title*). Also: engine puts the channel logo into `landscape` (`live.rs:160`), so a wide live card crops/stretches the logo as full-bleed art — not the board's logo treatment. Live cards don't appear in local home rows today, but the wire type allows it. |
| Generic/unknown badges (`"New"`, `"4K"`, `"HD"`) | **✗ dropped** | `label` branch returns null for non-state badges (`:134-139`). Board `Tv3HomeToRows` portrait cards show a light **"New"** pill (12,12, #F4F2EE/#111113); §A1 says "badges that don't fit move to the meta line". App draws nothing — and `PreviewTvHome.kt:16,18` still feeds `"New"` badges, so the preview home shows no pills where the board has them. TVUI-08 ("'New' badges on catalog rows") remains open. |
| ≥4 badges | ✓ per board | one label max + check + returning; remainder dropped — matches "One label on the art". |

### 3.3 Progress

| State | Renders? | Detail |
|---|---|---|
| `progress` present (0..1) | ✓ w/ deltas | 5dp-high bar, track white@.25, fill `Palette.accent` #F5C542 ✓ (`TvCatalogCard.kt:98-101`, `TvWatchProgress.kt:20-26`). **Deltas:** bar is inset 3dp from sides/bottom and floats 3dp above the bottom edge; boards draw it flush (`left:0 right:0 bottom:0`) — CardStates 5px, HomeToRows 4px. Boards also scope progress to landscape cards ("Landscape · progress bar only when there is progress"); the app draws it on portrait cards too when `progress` is set. |
| `progress` absent | ✓ no bar | ("bar only when present") |
| NaN / ±∞ / out of 0..1 | ✓ safe | coerced to 0..1, NaN→0 (`TvWatchProgress.kt:21`); a11y % clamped (`TvCatalogCard.kt:170`). |

### 3.4 Captions (meta line, certification, episode, subtitle, category)

| State | Renders? | Detail |
|---|---|---|
| Full metadata (year, runtime, rating) | ✓ w/ deltas | meta = `subtitle ?: metadataParts().take(3)` (`:157-158`). `metadataParts` = year · runtime · **genres(3)** · ★rating (`MediaPresentation.kt:6-11`). §A1: "year · runtime · ★ 7.4, then the certification chip. **Genres appear on the billboard and detail pages, not on cards**". With genres present, `take(3)` **drops the rating and shows genres** — order/content deviation. Runtime "2h 2m" vs board "2h 02m" (no zero-pad, `MediaPresentation.kt:8`). ★ format "★ 7.4" ✓. |
| Certification present ("R", "TV-MA") | ✓ w/ deltas | `TvMediaBadge`: h 30dp, border 1.5dp #F4F2EE@.55, r5, pad h8, 18sp/700 #DAD8D3, ls .36sp (`TvMediaBadge.kt:16-21`). Board chip: h 26, pad 7, 16px/700 #DAD8D3, r5, 1.5px — size/type deviation. **Position: app puts the chip on the *title* row (`:219-223`); board/§A1 put it at the end of the *meta* line.** |
| Certification blank | ✓ nothing drawn | (`:156`) — "missing value draws nothing" ✓ |
| `subtitle` present (continue rows: "S1 E2 · 38m left", "42m left") | ✓ | renders as the meta line (`:157`). Board text ✓. Engine caveat: `resume_caption` only ever emits minutes ("100m left"), never the board's "1h 40m left" (`playback.rs:93-109`). |
| `subtitle` = "Watched" (completed record) | ✓ as text | renders as meta text; the board's Watched state is the corner check — no producer maps it to a badge. |
| `identity.episode` set, subtitle null | **✗ not rendered** | The card never reads `identity.episode` (`EpisodeCoordinates`, `EngineWire.kt:132-136`). §A1: "'S1 E2 · 52m' comes from `identity.episode`". It works on Continue Watching only because the engine pre-bakes it into `subtitle`; catalog episode cards would show no episode info. |
| Parent/series title (board: second caption line, 18px #B6B4AF) | ~ partial | The app has only two caption rows (title+cert / meta+returning). A parent title appears only if the engine put it in `subtitle`, where it *replaces* the meta line instead of adding a third line. Board "Episode" example has 3 lines. |
| `category` ("movie"/"series"/"live") | **✗ never rendered** | Board portrait captions show "Series" / "Series · Returning"; app shows nothing when year/runtime/rating are absent. |
| No metadata at all ("Title only") | ✓ | title only, no dangling "·" ✓ (`:225-233` render nothing when both null). |
| Portrait captions on unfocused cards | ✓ | alpha-hidden, 76dp always reserved (`:105,216-218`) — board note "on TV only the focused card has one" ✓. |
| Focused caption title colour | ✓ | unfocused wide #DAD8D3 → focused #F4F2EE (`:221`), matching the board's `tr-cap` animation (#DAD8D3→#F4F2EE). Meta colour #B6B4AF matches the CardStates board; `Tv3HomeToRows` uses #8F8D89 (boards disagree; app picked CardStates). |

### 3.5 Focus treatment

| Element | Board / token | App | Verdict |
|---|---|---|---|
| Focus ring | inset 3px **#FFFFFF** + inset 5px rgba(0,0,0,.35) (double ring), fades 120ms, no scale (`tvFocus/cardFocus`) | 3dp stroke **#F4F2EE**, no dark separator ring, tween(120) ✓, no scale ✓ (`CardFocusRing` `:195-197`, `FocusOutline.kt:16-23`) | colour + missing inner dark ring |
| Unfocused hairline | 1px inset rgba(255,255,255,.08) on every card | none | missing |
| Play badge on focus | not on any board (boards show ring only) | 72dp white@.16 circle + 30dp play icon, wide cards only (`:199-208`) | app-only addition (alpha-toggled for perf, PERF-02) |
| Accessibility | — | one node/card, role button, full spoken description incl. badge, cert, % watched (`:71-77,164-171`) | positive (PERF-01) |

---

## 4. Row states the wire can produce

`HomeRow` = `EngineWire.kt:180-189` (`browse, id, title, provider_id, state, cards, next_cursor, message`); RowState enum `:478-483`.

| State | Renders? | Detail |
|---|---|---|
| **READY** + cards | ✓ | LazyRow, cards deduped by lookup key, stable keys, focusRestorer, contentType split portrait/wide (`TvHomeShelf.kt:42-44`). Scroll spec matches the `horizontal` token (§2). Row fade of rows above the active one (200ms) matches `rowToRow`. |
| **READY** + `next_cursor` | **✗ ignored** | `next_cursor` is never read by `TvHomeShelf`/`TvHomeScreen` (grep: no home-row consumer). Board A2/RowStates: "more cards load as focus nears the end — no Load more button" + two trailing skeleton tiles. Not implemented app-side; **the engine also never sets it** (`home.rs:33` `next_cursor: None`, rows capped at 24 cards `home.rs:47`). Matches OPEN_TASKS "Infinite / lazy carousels… needs the connection API first". |
| **PENDING** | ~ | Title + **5** skeleton cards in the real card shape (`TvHomeShelf.kt:45`, `TvHomeLoading.kt:34-36`). Board shows **6** tiles (RowStates: x=96…1696). Skeleton omits the 76dp caption reserve → row height grows on READY (see §2 defect). |
| **EMPTY** | ✓ | Filtered out at screen level: no title, no gap, rows below move up (`TvHomeScreen.kt:57-58` — `cards.isNotEmpty() || PENDING || FAILED`) — exactly the board's "Not drawn at all". The shelf's own EMPTY branch ("Nothing here yet.", `TvHomeShelf.kt:46`) is dead code on the TV home. |
| **FAILED** + `message` | **✗ major deviation** | Renders the row title + a **bare `TextButton` whose label is `row.message ?: "Try again"`** (`TvHomeShelf.kt:47`). Board: an alert **card** 940×169, r12, bg rgba(255,255,255,.05), 1px inset border, "Couldn't load this row" 22px/700 + message 19px #B6B4AF + white 60px r12 "Try again" button with refresh icon (RowStates alert). The app puts the engine's message ("This row is unavailable. Try again." / "This row took too long. Try again.", `home.rs:48,56`) *inside the button*, and draws no message card, icon, or container. |
| `title` | ✓ | 30sp/600 @ x=96, h40 (`TvHomeShelf.kt:39-40`) — matches `Tv3Home`; `Tv3RowStates` uses 28px (boards differ; app follows the Home board). |
| `browse` (HomeBrowseAction) | unused | no "see all" affordance; TV boards don't show one. |
| `provider_id`, `message` (READY) | unused / n.a. | fine. |

Wire-reachability of row states (local engine): READY, PENDING, EMPTY, FAILED all occur (`home.rs:44-57`); `next_cursor` never; `message` only the two fixed strings. Continue row is always READY with ≤12 cards (`history.rs:20-38`).

---

## 5. Cross-cutting findings

1. **Stale KDoc** — `TvCatalogCard.kt:53-54` still documents the initials no-art fallback; the implementation (`:174-192`) now renders title+year per the board. Update the comment.
2. **Badge state machine is dead code on the live wire.** Local engine + backend emit only: `badges: []` everywhere, `"Simkl"` (`simkl.rs:107`), and `subtitle="Watched"` (`playback.rs:52`). "New episode"/"Ended"/"Returning"/"LIVE"/"Watched"-as-badge have **no producer** (backend `server/src` has no card badges at all; `simkl.rs:740` "New episodes" is a row title). So TVUI-01's badge rendering is verified only via preview data (`PreviewTvHome.kt`) — matching ISSUES.md R2's "no visual change without proof" gap.
3. **Kind mismatch for live** — the app detects live as kind `channel`/`tv` (`:126`) but the engine's live cards are kind `live` (`live.rs:154`). Even for `channel`/`tv` the red LIVE label additionally requires a "LIVE" badge nobody sends. The §A1 "Live" card state is unreachable and unimplemented.
4. **Episode/parent-title rendering is engine-coupled** — the card renders episode info only when it arrives pre-formatted in `subtitle` (continue rows). Per §A1 the renderer should derive "S1 E2" from `identity.episode`; catalog episode cards currently show nothing.
5. **`logo` field unused by cards** — wire-reachable (stremio catalogs map logo/clearlogo/titleLogo, `stremio/media.rs:166`), rendered by heroes only. §A1 "Logo vs text" state missing (TVUI-01).
6. **Certification chip position/size** — title row + 30dp/18sp vs board meta row + 26px/16px (`TvCatalogCard.kt:223`, `TvMediaBadge.kt:16-21`).
7. **FAILED row is the largest row-state gap** (bare button vs designed alert card + copy) — TVUI-02.
8. **PENDING skeleton count 5 vs 6** and the 76dp height jump when READY — TVUI-02 polish.
9. **Progress bar** floats 3dp above the bottom with 3dp side insets; boards draw it flush. CardStates says 5px, HomeToRows 4px (board self-inconsistency; app chose 5dp).
10. **rowOffset hardcodes READY heights** (384/516) — scroll targets are wrong while PENDING/FAILED rows are above the target row (`TvHomeScreen.kt:102-103`).

---

## 6. State matrix summary (wire → render)

| Wire state | Reachable today? | Renders per board? |
|---|---|---|
| art / no-art | ✓ | ✓ (style deltas on no-art) |
| portrait / wide | ✓ | ✓ exact geometry |
| logo vs text | ✓ (stremio) | ✗ logo never drawn |
| progress / none | ✓ (continue) | ✓ (inset + portrait-scope deltas) |
| 0–3+ badges | Simkl only | ✓ machine correct; Watched/New episode/Ended/Returning/LIVE unreachable; "New"/generic dropped |
| certification / blank | ✓ | ✓ (chip size + wrong caption line) |
| episode (identity.episode) | ✓ type, ✗ producer for catalogs | ✗ not read; works only via subtitle |
| live (kind live/channel/tv) | type ✓, home rows ✗ | ✗ no LIVE label for kind "live", no channel tile |
| watched | via subtitle only | ✓ as text; check state unreachable |
| returning | ✗ | ✓ (meta line) if ever sent |
| subtitle ("S1 E2 · 38m left") | ✓ | ✓ (engine lacks "1h 40m" format) |
| category | ✓ | ✗ never rendered |
| next_cursor | ✗ (engine None) | ✗ ignored by app |
| READY / PENDING / EMPTY / FAILED | ✓ | ✓ / ~ / ✓ / ✗ |

---

## 7. References

- Boards: `design/viptv-design-system/reference/screens/html/tv/{Tv3CardStates,Tv3RowStates,Tv3Home,Tv3HomeToRows,Tv3HomeLoading}.html`; geometry via `app/tools/board_spec.py`
- Dictionary: `design/viptv-design-system/reference/canvas-data-dictionary.md` §A1 (Title card), §A2 (rows); `reference/canvas-tokens.json` (`tvFocus`, `tvMotion`, `radius`)
- Issues: `app/docs/ISSUES.md:78,83,84` (BROWSE-07, TVUI-01, TVUI-02); `app/docs/OPEN_TASKS.md:25,53`; `app/docs/UI_INVENTORY.md` §4.2
- Palette: `app/composeApp/.../theme/Theme.kt:11-21` (text #F4F2EE, body #DAD8D3, secondary #B6B4AF, surface #161618, accent #F5C542)
