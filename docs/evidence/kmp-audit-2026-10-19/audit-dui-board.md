# Desktop D3 board parity audit — title bar, rail, hero

Read-only audit. No files edited, no builds run. All measurements come from the
board HTML (`design/viptv-design-system/reference/screens/html/desktop/*.html`,
cross-checked with `app/tools/board_spec.py <id> --platform desktop`) and the
current on-disk sources under `app/composeApp/src/`.

Working-tree note (concurrent edits): `DesktopTitleBar.kt`, `Main.kt`,
`HeroIndicators.kt`, `TvHomeScreen.kt` are locally modified at audit time
(HEAD `ffebb7d`). Every `file:line` below was read from the current on-disk
state, not from any snapshot or build artefact.

Files audited:
- `app/composeApp/src/desktopMain/kotlin/org/viptv/app/DesktopTitleBar.kt` (172 lines)
- `app/composeApp/src/desktopMain/kotlin/org/viptv/app/DesktopWindowButtons.kt`
- `app/composeApp/src/desktopMain/kotlin/org/viptv/app/DesktopChromeIcon.kt`
- `app/composeApp/src/desktopMain/kotlin/org/viptv/app/Main.kt`
- `app/composeApp/src/commonMain/kotlin/org/viptv/app/components/SideNavigation.kt`
- `app/composeApp/src/commonMain/kotlin/org/viptv/app/components/Destinations.kt`
- `app/composeApp/src/commonMain/kotlin/org/viptv/app/features/home/DesktopHero.kt`
- `app/composeApp/src/commonMain/kotlin/org/viptv/app/features/home/DesktopHeroActions.kt`
- supporting: `HeroIndicators.kt`, `MediaMetadata.kt`, `HomeScreen.kt`,
  `HomeBackdrop.kt`, `features/history/HistoryScreen.kt`, `HistoryRow.kt`,
  `components/TvFocusTreatment.kt`, `App.kt`

Boards audited: D3HomeWin, D3HomeHover, D3Context, D3History, D3Offline,
D3NotFound, D3Mini, D3HomeNarrow (= "D3Narrow"), D3HomeUltra (= "D3Ultra");
adjacent: D3HomeMac, D3Profiles/D3SignIn (40px bar).

Palette reference (`theme/Theme.kt:11-21`): background #0B0B0C, surface #161618,
raised #212124, text #F4F2EE, body #DAD8D3, secondary #B6B4AF, tertiary #8F8D89,
accent #F5C542.

---

## 1. Board implementation status (summary)

| Board | Board size | Implementation | Status |
|---|---|---|---|
| D3HomeWin | 1440×900 | DesktopTitleBar + SideNavigation + DesktopHero | **Implemented with geometry/copy deltas** (§2–§5) |
| D3HomeHover | 1440×900 | — | **No implementation** (no hover preview card, no card hover states) |
| D3Context | 1440×900 | — | **No implementation** (no right-click menu anywhere) |
| D3History | 1440×900 | HistoryScreen.kt + HistoryRow.kt | **Partial** — screen exists, geometry/copy deltas (§6) |
| D3Offline | 1440×900 | — | **No implementation** (no offline info bar) |
| D3NotFound | — | — | **Board does not exist** in the desktop export (only `W3NotFound` on web). DUI-04 in `app/docs/ISSUES.md:117` references a nonexistent board; app has no 404 screen either |
| D3Mini | 480×270 | — | **No implementation** (single-window app, no mini player) |
| D3HomeNarrow | 600×800 | DesktopTitleBar narrow branch + DesktopHero column branch | **Partial** — narrow chrome/hero exist, deltas (§7) |
| D3HomeUltra | 2560×1080 | — (reuses wide layout) | **Ultra variant not implemented** (§8); title bar/rail reuse is correct |

`app/docs/design-coverage.json` agrees: HomeHover/Context/Offline/Mini are
`"status": "pending", "implementation": null` (lines ~1101–1331).

---

## 2. Title bar — D3HomeWin vs `DesktopTitleBar.kt`

Board spec (D3HomeWin.html, `data-tauri-drag-region` div):
`left:0 right:0 top:0 height:48px; background:#0E0E10; border-bottom:1px solid
rgba(255,255,255,0.08); z-index:50`. Left cluster `gap:2px; padding-left:8px`.
Center search `left:50%; translateX(-50%); width:460px`. Right cluster
`margin-left:auto; gap:2px`.

### Geometry deltas

| # | Property | Board | Implementation | Citation |
|---|---|---|---|---|
| 1 | Bar height | 48px (40px on simple screens, per D3Profiles/D3SignIn) | 48dp / 40dp simple — **match** | DesktopTitleBar.kt:64 |
| 2 | Bar background | **#0E0E10** | **#0B0B0C** (`Palette.background`) | DesktopTitleBar.kt:69 |
| 3 | Bottom border | 1px `rgba(255,255,255,0.08)` (≈ #252527 on #0E0E10) | 1dp `#1E1E21` | DesktopTitleBar.kt:144 |
| 4 | Chrome button shape | 36×36, **border-radius 18 (circle)** | 36×36, **radius 8** | DesktopTitleBar.kt:164 |
| 5 | Chrome icon size | 18×18 (stroke 2.1) | 17dp | DesktopTitleBar.kt:170 |
| 6 | Left cluster gaps | 2px between buttons; 6px to brand cluster | `spacedBy(4.dp)`; `Spacer(4.dp)`+spacing → 8dp to brand | DesktopTitleBar.kt:72, 83 |
| 7 | Brand tile | 18×18, radius 5, gap 6, "V" 11px w800, wordmark 13px w800 ls −0.03em | 16×16, radius 4, gap 5, "V" 11sp wBlack, 13sp ExtraBold, **no letter-spacing** | DesktopTitleBar.kt:151-155 |
| 8 | Brand colors | tile #F5C542, glyph #15130F | tile #F5C542 ✓, glyph #0B0B0C | DesktopTitleBar.kt:151-152 |
| 9 | Search width | fixed **460px** centered (max-width 100%) | `min(460dp, 36% of width)` → 396dp at the default 1100dp window | DesktopTitleBar.kt:91 |
| 10 | Search field | 460×32, radius 8, padding 0 10, gap 8, bg `rgba(255,255,255,0.07)` (≈ #252527) | 32dp ✓, radius 8 ✓, padding 10 ✓, gap **9**, bg **#1E1E21** | DesktopTitleBar.kt:92-96 |
| 11 | Search icon | 16×16 | 16dp ✓ | DesktopTitleBar.kt:97 |
| 12 | Kbd chip "Ctrl K" | height 20, radius 5, **no fill, inset 1px ring** `rgba(255,255,255,0.14)`, color #B6B4AF, 11px w600 | fill **#121214**, radius 4, padding 5×2 (≈16dp tall), color #B6B4AF ✓, 11sp ✓ | DesktopTitleBar.kt:126-127 |
| 13 | Back icon color (enabled) | #F4F2EE | **Palette.secondary #B6B4AF** | DesktopTitleBar.kt:80 |
| 14 | Forward icon color (disabled) | #8F8D89 | Palette.secondary × 0.38 alpha (≈ #6A6965) | DesktopTitleBar.kt:170 |
| 15 | Keyboard focus ring | boards' `.vx-focusring`: 2px #F4F2EE, offset 3px | `tvFocusOutline` 4dp white stroke drawn outside bounds, no offset | DesktopTitleBar.kt:94, 166; TvFocusTreatment.kt:29-44 |
| 16 | Cast/Remote gap | 2px (right cluster `gap:2px`) | no spacing between the two ChromeButtons | DesktopTitleBar.kt:129-131 |
| 17 | Avatar | 28×28, radius 7, margin-left 4 ✓ | 28dp, 7dp, padding start 4 / end 8 — **match** | DesktopTitleBar.kt:136-137 |
| 18 | Window buttons | 46×48 each, 10×10 glyphs, margin-left 8 | 46dp × fillMaxHeight, 10dp glyphs, 8dp — **match**; hover: Close #E81123, others white 8% (board is static, no hover spec) | DesktopWindowButtons.kt:56-60 |
| 19 | Window frame | root `border-radius:8px`, inset 1px `rgba(255,255,255,0.1)` | RoundedCornerShape(10dp), border 1dp white 9% | Main.kt:72-76 |
| 20 | Simple bar brand (40px bars, D3Profiles/D3SignIn) | V 9px, VIPTV 12px, gap 5, margin-left 14, buttons 46×40 | shares DesktopBrand: V 11sp, VIPTV 13sp; margin-left 14 ✓ | DesktopTitleBar.kt:71-76, 150-155 |

### Copy deltas
- Placeholder `Search movies, series and channels` — identical ✓ (DesktopTitleBar.kt:117); but color: board **#8F8D89** vs `Palette.secondary` **#B6B4AF**.
- Button labels match the board aria-labels: "Back (Alt+Left)"/"Forward
  (Alt+Right)" → "Back"/"Forward" (shorter, no shortcut hint);
  "Watch on TV" ✓, "TV remote" ✓ (DesktopTitleBar.kt:78-80, 129-131).
  Board's settings shortcut "Settings (Ctrl+,)" hint exists only on the rail
  board, not implemented (SideNavigation.kt:46 has no hint).
- Implementation extras with no board counterpart (additions, not deltas):
  clear-search X button 20×20 (DesktopTitleBar.kt:121-124), Esc-to-dismiss
  search (:103-110), Ctrl/⌘+K focus wiring (Main.kt:44-51 — board advertises
  "Ctrl K" so this closes a gap), "⌘K" variant on mac (DesktopTitleBar.kt:126).

### macOS (adjacent, D3HomeMac — not in the requested list)
- Board bar height **52px** (nav top:52px) vs implementation 48dp for mac
  (DesktopTitleBar.kt:64 — `mac` does not change the height).
- Traffic lights: board 12px dots, gap 8, group padding `0 12px 0 20px`, each
  with inset 0.5px rgba(0,0,0,0.25) ring; implementation 12dp dots, gap 8 ✓,
  Row `padding(start = 14.dp)` (14 vs 20 leading), no dot ring.
  (DesktopWindowButtons.kt:32-33, 49)
- Board light-group → Back gap = 12 (group padding) + 2 (cluster gap) = 14px;
  implementation outer Row gives 4dp (DesktopTitleBar.kt:72).
- Mac kbd chip "⌘K" ✓ (DesktopTitleBar.kt:126); trailing Spacer 12dp ✓
  (board padding-right 12px) (DesktopTitleBar.kt:142).
- D3HomeMac shows **no brand wordmark** in the main bar; implementation also
  omits it for non-simple mac ✓ but shows it on simple mac screens
  (DesktopTitleBar.kt:75-76) — no simple-mac board exists to check against.

---

## 3. Rail — D3HomeWin/D3HomeUltra vs `SideNavigation.kt`

Board spec (D3HomeWin.html `<nav>`): `left:0 top:48px width:72px;
border-right:1px solid rgba(255,255,255,0.08); background:#0E0E10; flex column;
align-items:center; gap:4px; padding:12px 0`. Items: `width:56px; gap:3px;
padding:6px 0; border-radius:10px; font-size:10px; font-weight:600`;
active `background:rgba(255,255,255,0.12); color:#F4F2EE`; inactive
`color:#B6B4AF`. Icons 20×20. Divider 32×1 `rgba(255,255,255,0.08)` margin 4px 0
between Calendar and My List. Bottom cluster `margin-top:auto`, two 40×40
circle buttons (Add-ons puzzle, Settings), gap 4.

### Geometry deltas

| # | Property | Board | Implementation | Citation |
|---|---|---|---|---|
| 1 | Rail background | **#0E0E10** | **#0B0B0C** | SideNavigation.kt:26 |
| 2 | Right border | **1px rgba(255,255,255,0.08)** | **missing** (no border-right drawn) | SideNavigation.kt:26 |
| 3 | Rail width / top inset | 72px, padding-top 12 | 72dp ✓, Spacer 12 ✓ | SideNavigation.kt:26-27 |
| 4 | Item size | 56 wide, content-height ≈47 (6+20+3+12+6) | 56×48 — **match (±1px)** | SideNavigation.kt:32 |
| 5 | Item corner | radius 10 | radius 10 ✓ | SideNavigation.kt:32 |
| 6 | Active background | `rgba(255,255,255,0.12)` ≈ #2B2B2D on #0E0E10 | **#2A2A2D** (near-match; computed on different rail bg) | SideNavigation.kt:33 |
| 7 | Inactive label color | **#B6B4AF** (whole item) | label always **Palette.text #F4F2EE**; only the icon tints | SideNavigation.kt:36-40 |
| 8 | Label weight | 600 | default (no FontWeight on the Text) | SideNavigation.kt:40 |
| 9 | Icon size | 20×20 | 20dp ✓ | SideNavigation.kt:37-38 |
| 10 | Divider | 32×1, 8px total spacing either side (gap 4 + margin 4) | 32×1, `padding(vertical = 8.dp)` + skipped 4dp spacer → 8px effective — **match** | SideNavigation.kt:30, 42 |
| 11 | Bottom cluster | two 40×40 **circle** buttons, gap 4, rail bottom padding 12 | Material `IconButton`s (48dp min touch target, no explicit 40×40, default M3 shape), no gap spec, trailing **Spacer(8)** vs padding 12 | SideNavigation.kt:45-47 |
| 12 | Bottom icon tint | #F4F2EE | default LocalContentColor = Palette.text ✓ | SideNavigation.kt:45-46 |

### Content deltas
- **Extra destination**: implementation rail has 9 items including **Anime**
  (Lucide.Sparkles) between Series and Live; the board rail has 8 — Home,
  Discover, Movies, Series, Live, Calendar, My List, History.
  (Destinations.kt:10-19 vs D3HomeWin.html `<nav>`)
- Icon set otherwise matches the board svgs: House ✓, Compass ✓, Film ✓, Tv ✓,
  Live = remote glyph via `ic_remote_zip` ✓, Calendar ✓, Bookmark (My List) ✓,
  History ✓ (SideNavigation.kt:37-38, DesktopChromeIcon.kt:12-27).
- Labels/copy identical: "Home / Discover / Movies / Series / Live / Calendar /
  My List / History", "Add-ons", "Settings" (board adds "(Ctrl+,)" hint).
- Rail hidden below 640dp (`App.kt:90` `compact`), matching D3HomeNarrow's
  rail-less layout; but the title-bar menu button appears below **720dp**
  (DesktopTitleBar.kt:70), so a 640–719dp window shows *both* the menu button
  and the rail — a state no board defines.

---

## 4. Hero — D3HomeWin vs `DesktopHero.kt` / `DesktopHeroActions.kt`

Board spec (D3HomeWin.html first `<section>`): content flex row, gap 48,
`padding: 40px 40px 48px`; left column `flex:1; max-width:640px; gap:14px`;
right column `flex-shrink:0; width:682px`. Backdrop: artwork blur(60px)
saturate(1.3) opacity .45 scale(1.25) + gradient
`rgba(11,11,12,.35) → .55 60% → #0B0B0C`.

| # | Property | Board | Implementation | Citation |
|---|---|---|---|---|
| 1 | Outer paddings | 40 top / 40 sides / 48 bottom | 12 (HomeScreen) + 28 = **40 top ✓**; 40 sides ✓ (gutter); **80 bottom** (vs 48; +24 LazyColumn spacing → first row header at 104px vs board 48+30=78px) | HomeScreen.kt:27, 39-40; DesktopHero.kt:25 |
| 2 | Column split | left flex1/max-640, right fixed 682 | weights 0.45/0.55 → 558/682 **exactly at 1440**, but proportional at other widths | DesktopHero.kt:32-34 |
| 3 | Kicker copy | **"Featured · 1 of 5"** (13px w700, ls 0.06em, uppercase, #B6B4AF) | **"FEATURED"** only — carousel counter missing from visible copy; 13sp ✓, SemiBold vs w700, ls 0.6sp vs ≈0.78px | DesktopHero.kt:50-51 |
| 4 | Logo | height 120, max-width 100% (content-sized) | fixed **width 220 × height 120** | DesktopHero.kt:53 |
| 5 | Metadata | gap 7, 14px **w500** #DAD8D3; chips 19px tall, radius 4, inset ring rgba(244,242,238,.5), 11px w700, padding 0 4 | gap 6, 14sp w400; chips 10sp w600, radius 3, 1dp border, padding 4×2 | MediaMetadata.kt:16-27 |
| 6 | Synopsis | 15px / 1.45, #DAD8D3, 3-line clamp | 15sp/22 ✓, Palette.body ✓, maxLines 3 ✓ | DesktopHero.kt:60 |
| 7 | Actions row | gap 10, margin-top 4 | gap 10 ✓; sits in gap-14 column → 14 vs 18 total | DesktopHeroActions.kt:19 |
| 8 | Play button | 140×44 r12, #F4F2EE bg / **#111113** text, padding 0 18, 15px **w700**, icon+gap 8 | 140×44 r12 ✓, #F4F2EE/#0B0B0C, 14sp **SemiBold**, icon 16 + gap 8 ✓ | DesktopHeroActions.kt:20-23 |
| 9 | More info | 44 r12, `rgba(255,255,255,0.12)` + **backdrop blur 20**, #F4F2EE text, 15px w700 | 44 r12, **Palette.raised #212124, no blur**, 14sp SemiBold, icon 18 ✓ | DesktopHeroActions.kt:25-28 |
| 10 | Save button | 44×44, **radius 22 (circle)**, `rgba(255,255,255,0.12)` + blur 20, icon ~20 | 44×44 ✓, FilledIconButton default M3 shape + **Palette.raised**, icon 22 | DesktopHeroActions.kt:30-32 |
| 11 | Button copy | "Play", "More info", aria "Add to My List" | identical ✓ incl. "Add to My List"/"Remove from My List" descriptions | DesktopHeroActions.kt:23, 28, 32 |
| 12 | Artwork | 16:9, **radius 14**, shadow `0 30px 70px rgba(0,0,0,.55)`, inset ring rgba(255,255,255,.08) | 16:9 ✓, **radius 12**, no shadow, no ring, bg Palette.surface | DesktopHero.kt:43 |
| 13 | Pagination dots | under artwork, centered, mt 12, **gap 6**, active 20×6 r3 #F4F2EE, inactive 6×6 rgba(255,255,255,.35) | under the *actions in the left column*, **gap 8**, active **48×4 r2** with progress fill, inactive 16×4 | DesktopHero.kt:63; HeroIndicators.kt:23-31 |
| 14 | Backdrop gradient | .35 top / .55 60% / solid | **.15 / .65**; saturate(1.3) missing; blur 60 ✓, alpha .45 ✓, scale 1.25 ✓ | HomeBackdrop.kt:20-24 |
| 15 | No-logo fallback | none on board (always logo) | 36sp Bold title text | DesktopHero.kt:56 |

---

## 5. D3HomeWin verdict
Structurally complete (bar 48/40, rail 72, search centered, avatar, window
buttons 46×48, hero split) with a consistent set of small deltas: two-tone
chrome background (#0B0B0C vs #0E0E10 + missing rail border), circle→8dp corner
radius on all 36dp chrome buttons, 17 vs 18 icons, brand tile 16/4/5 vs 18/5/6,
missing "· 1 of 5" kicker counter, kbd chip styling, back-arrow tint, hero
artwork radius 12 vs 14 with no shadow/ring, indicator strip geometry/placement,
and the extra Anime rail destination.

---

## 6. D3History vs `HistoryScreen.kt` / `HistoryRow.kt`

Board: main left 72 / top 48; content `padding: 28px 40px; max-width: 1000px`.
Header: h1 "History" 28px w700 ls −0.02em; right "Clear history" button height
34, padding 0 15, radius 11, transparent + inset 1px ring
rgba(255,255,255,0.14), 13px w700. Day label 13px w600 #B6B4AF margin 14/6.
Row grid `160px | 1fr | 140px | 40px`, gap 18, padding 8, radius 10; thumb 16:9
r8 with 3px progress (track rgba(255,255,255,0.25), fill #F5C542); title 15
w600, subtitle 13 w500 gap 4; right column "38m left"/"Watched" 13 w500;
"More" 32×32 circle.

Implementation deltas (HistoryScreen.kt:22-44, HistoryRow.kt:19-46):
- List max width **1080dp vs 1000px**; vertical padding 24 vs 28 (HistoryScreen.kt:22-23).
- "Clear history" is a plain M3 `TextButton` (no 34px height, no ring, no r11)
  vs the board's outlined pill (HistoryScreen.kt:31-33).
- Day label 12sp Medium vs 13px w600; padding 12/4 vs 14/6 (HistoryScreen.kt:38-41).
- Row: thumb 160 ✓ 16:9 r8 ✓; 3dp progress ✓ (fill accent ✓, track
  Palette.raised vs rgba(255,255,255,0.25)) (HistoryRow.kt:24-28).
- **No 140px right column** — "38m left"/"Watched" is folded into the subtitle
  line, and time-remaining is not rendered at all (HistoryRow.kt:34-36).
- Title 14sp SemiBold vs 15 w600; subtitle 12sp vs 13 w500.
- "More" IconButton 32 ✓ but its dropdown has a single "View details" item
  (HistoryRow.kt:38-42) vs the board's 9-item context menu (§D3Context).
- No row hover/selected treatment (boards define `.vx-hover` ring for cards).
- Header title 28sp Bold ✓ copy "History" ✓; grouped-day structure ✓
  ("Today"/"Yesterday" etc. from `dayLabel`).

---

## 7. D3HomeNarrow (600×800) vs implementation
Board: title bar 48px with left cluster [Menu][Back][Forward] (36×36 circles,
gap 2), right cluster [Search (Ctrl K)][Cast][Remote][avatar 28r7][window
buttons], **no rail** (main `left:0`), hero stacked column gap 22 padding 20
(artwork 560 wide, r14, dots below; copy column gap 12, keeps "Featured · 1 of
5" and 3-line synopsis).

Implementation matches: menu button replaces brand+chevrons? No — menu button
*added*, back/forward retained ✓, brand hidden ✓, no window-buttons change,
rail hidden below 640dp ✓, hero stacks below 900dp with artwork-first order ✓,
dots under copy.

Deltas:
- **Search icon button is centered in the bar** (`Alignment.Center`,
  DesktopTitleBar.kt:87-88); the board puts it in the right cluster next to
  cast/remote (D3HomeNarrow.html right cluster).
- Thresholds: menu button <720dp (DesktopTitleBar.kt:70) but rail hidden
  <640dp (App.kt:90) → 640–719dp shows menu button + rail (no board).
- Hero column gap **28 vs 22** (DesktopHero.kt:29); hero top 12+28=40 vs board
  20; horizontal gutter 16 (compact) vs board 20 (HomeScreen.kt:25, 39-40).
- All §2/§4 deltas (circle radius, colors, kicker copy, dot geometry) apply.

---

## 8. D3HomeUltra (2560×1080) vs implementation
Board title bar and rail are identical to D3HomeWin (72px rail, 48px bar,
460px search, brand) — the implementation reuses them correctly. The delta is
the **home layout**: the board replaces the hero artwork + dots with a
"Featured" side list: left copy column fixed **800px** (flex-shrink 0), right
nav `680×435`: header "FEATURED" (13 w700 ls .06em #B6B4AF) + "5 picks this
week" (13px #8F8D89); five rows (flex 1, gap 6, r12, active row
rgba(255,255,255,0.10) + inset ring) each with 128×72 16:9 thumb r8, title 15
w700, subtitle 13 #B6B4AF, gap 14, padding 0 10.

Implementation has **no ultrawide variant**: DesktopHero keeps the 0.45/0.55
weighted row at any width ≥900dp (DesktopHero.kt:26-34), so at 2560 the artwork
column becomes ≈1298dp wide vs the board's 680dp list; the featured-list UI
("5 picks this week", 128px thumbs, active-row ring) does not exist in the app.

---

## 9. Boards with no implementation (final list)

1. **D3HomeHover** — hover preview card (360px wide, r12, bg #1C1C1F, shadow
   `0 24px 60px rgba(0,0,0,.7)` + 1px ring, copy "Resume / Obsession / Movie ·
   1h 40m left / Thriller R HD"): absent; catalog cards have no hover state at
   all (only title-bar/window buttons and TopNavigation use
   `collectIsHoveredAsState`).
2. **D3Context** — right-click menu (300px wide, r10, padding 5,
   `rgba(36,36,40,0.97)` + blur 30, 34px rows r6 13px w500, shortcuts
   "Enter/Ctrl+M/Ctrl+C/Del", items: Resume S1 E2, Play from the beginning,
   Open in mini player, Go to series, Episodes, Copy link, Mark as watched,
   Remove from Continue Watching in #FF8A80): absent; no
   `onPointerEvent`/secondary-click handling anywhere in
   `desktopMain`/`commonMain`.
3. **D3Offline** — offline info bar (left 232/right 0, top 48, height 44,
   padding `0 16 0 40`, gap 10, `rgba(255,255,255,0.1)` + blur 20, 13px w600,
   copy "You're offline. Showing what was saved; new rows load when you're
   back.", "Retry" ring button 30px r10, dismiss 28×28): absent; the app's
   offline/error path is `HomeStatus`, a different UI.
4. **D3NotFound** — no desktop board exists (not in
   `html/desktop/` or `img/desktop/`; only `W3NotFound` under web). DUI-04
   (`app/docs/ISSUES.md:117`) and the task list reference a board that was
   never exported. The app has no not-found screen either (`Screen` enum has
   no such value; `App.kt` renders a bare `state.screen.name` fallback).
5. **D3Mini** — always-on-top mini player (480×270, r10, inset ring
   rgba(255,255,255,0.15); 40px drag bar "Monster · S1 E2" 13 w700 with
   "Back to the full window" + "Close" 32×32; center controls gap 24: back-10s
   44×44 (26px icon with "10" label), pause 48×48 `rgba(255,255,255,0.12)` +
   blur 20, fwd-10s 44×44; bottom seek bar 12/12/12 4px r2, fill #F5C542 27%):
   absent; `Main.kt` creates exactly one Window and the Player screen takes it
   over with overlaid window buttons (Main.kt:79-87). No Ctrl+M hook.
6. **D3HomeUltra** — the ultrawide *variant* (§8): title bar/rail are shared,
   but the 800px-copy + 680px featured-list home layout is not implemented.

Partial (implemented with deltas): **D3HomeNarrow** (§7), **D3History** (§6),
and **D3HomeWin** itself (§2–§5).

Adjacent boards noted in passing: **D3HomeMac** — bar 52px vs 48, traffic-light
inset 20/12 vs 14, no brand wordmark (§2 macOS); **D3Profiles/D3SignIn** — 40px
simple bar matches except brand font sizes (V 9px/VIPTV 12px board vs 11/13
implementation).

---

## 10. Highest-leverage fixes (ranked)

1. Chrome two-tone: title bar + rail bg #0E0E10 (not #0B0B0C) and 1px
   rgba(255,255,255,0.08) border on the rail's right edge and bar's bottom
   edge — one palette change fixes the biggest visual delta on every screen.
2. Circle chrome buttons (radius 18 on the 36dp buttons) + 18dp icons + 2dp
   cluster gaps — makes every hover state match.
3. Remove the Anime destination from the desktop rail (or get a board update).
4. Inactive rail labels #B6B4AF + w600.
5. Kicker copy "FEATURED · 1 OF 5", hero artwork radius 14 + shadow/ring, dots
   under the artwork with board geometry (20×6/6×6, gap 6).
6. Decide on D3NotFound: either export the missing desktop board or drop it
   from DUI-04 (currently a phantom reference).
7. New surfaces to build: D3Context menu, D3Offline bar, D3Mini window,
   D3HomeHover preview, D3HomeUltra featured list.
