# TV Home polish audit — TVUI-08 / TVUI-09 (READ-ONLY)

Date: 2026-10-19. Method: source read + `design` board HTML parsed with
`app/tools/board_spec.py <BoardId> --full` plus raw-HTML grep for unlabeled
children (dots, card internals, keyframes). No gradle/adb/emulator/build was
run; no repository file was modified. The working tree is live, so every claim
carries a `file:line` citation as found at audit time (no build artefacts used).

Files audited (all under `app/composeApp/src/commonMain/kotlin/org/viptv/app/`):
- `features/home/TvHomeScreen.kt` (204 lines)
- `features/home/TvHero.kt` (53)
- `features/home/TvHeroSummary.kt` (56)
- `features/home/HeroIndicators.kt` (30)
- `features/home/TvHomeLoading.kt` (41)
- Supporting (read for delegation targets): `features/home/HeroCarousel.kt`,
  `TvHomeShelf.kt`, `TvCatalogCard.kt`, `TvHomeBackdrop.kt`, `TvBrowseSummary.kt`,
  `TvHomeVisibility.kt`, `TvWatchProgress.kt`, `components/TvAction.kt`,
  `TvMediaMetadata.kt`, `TvMediaBadge.kt`, `TvLoadingShimmer.kt`,
  `FocusOutline.kt`, `MediaPresentation.kt`, `theme/Theme.kt`.

Boards: `design/viptv-design-system/reference/screens/html/tv/Tv3Home.html`,
`Tv3HomeLoading.html`, `Tv3HomeToRows.html`. Issue context:
`app/docs/ISSUES.md:90-91` (TVUI-08/09), `app/docs/UI_INVENTORY.md:81` (§4.8).
All board px below are 1920×1080 design px; the app scales by
`maxWidth / 1920.dp` (TvHomeScreen.kt:115).

---

## 1. Checkpoint results (requested list)

### 1.1 FEATURED n OF m semantics — MATCH
- Board: dots container `aria-label="Featured 1 of 5"` — 1-based index of the
  48px active dot, m = total slides (Tv3Home.html, div at left:96/top:760).
- App: `HeroIndicators.kt:19` — `contentDescription = "Featured ${selected + 1} of $count"`.
  `selected` is 0-based (`HeroCarousel.kt:49` returns 0-based `active`,
  `count = cards.size`), wired at `TvHomeScreen.kt:155`
  (`carousel.count, selected = carousel.index`). Format string is identical to
  the board's.
- `HeroIndicators.kt:16` returns early when `count < 2`, so a single slide gets
  no "Featured 1 of 1" node — no board covers that case; reasonable.
- Note: `UI_INVENTORY.md:81` words this as a visible `FEATURED · 1 OF 5` label,
  but no TV board draws visible text (the only "Featured" string in all 56 TV
  boards is this aria-label). The app matches the board (semantics-only), not
  the inventory's phrasing.
- Cadence delta: the board's active-dot fill runs `t3-fill 9s linear infinite`
  (9 s per slide); the app advances every **8000 ms** (`HeroCarousel.kt:31`,
  comment "eight-second display interval" at :17). **Delta −1 s.**

### 1.2 Dots geometry — MATCH (one color delta)
Requested: 48 px active / 16 px rest, 4 px high, gap 8, x=96, y=760.
- Position: `TvHero.kt:49` — `HeroIndicators(..., Modifier.offset(96.dp*scale, 760.dp*scale))`;
  TvHero's box is placed at the screen origin (no parent offset), so absolute
  (96, 760) ✓.
- Widths/height: `HeroIndicators.kt:22` — `width((if (index == selected) 48 else 16).dp*scale).height(4.dp*scale)` ✓.
- Gap: `HeroIndicators.kt:20` — `spacedBy(8.dp * scale)` ✓.
- Corner radius: `HeroIndicators.kt:23` — `CornerRadius(2.dp.toPx() * scale)` ✓ (board 2 px).
- Fill: `Palette.text` #F4F2EE, width × progress (`HeroIndicators.kt:25-26`) ✓ board fill #F4F2EE.
- **Delta: track color** — app `Palette.text.copy(alpha = .35f)`
  (#F4F2EE @ 0.35, `HeroIndicators.kt:24`) vs board
  `rgba(255,255,255,0.3)` (pure white @ 0.30). **+0.05 alpha, #F4F2EE vs #FFFFFF.**

### 1.3 Hero logo/text at 96/306 — MATCHES the audit spec; board container sits at 300
- App logo: `TvHeroSummary.kt:44-45` — offset(96, 306), size **410×160**,
  `ContentScale.Fit`, `Alignment.BottomStart` → box 306..466, art pinned to 466.
- App text fallback (no logo): same (96, 306) box **800×160**, `BottomStart`,
  48 sp / 56 line-height, Bold, 2 lines (`TvHeroSummary.kt:46-48`). No board
  equivalent exists (the board always ships a logo PNG).
- Board: `<h1>` at **left:96 top:300** containing `<img height:170>` → art
  band 300..470. Deltas vs the board: **+6 px top, −4 px bottom** (the audit's
  96/306 spec is what the app implements).
- Prefetch mismatch: the carousel decodes the next logo at **420×160**
  (`HeroCarousel.kt:39`) but displays in the **410×160** box — decode 10 px
  wider than needed.
- Knockout technique: board `mix-blend-mode: lighten`; app uses a `TrimLogo`
  coil transformation (`images/LogoRequest.kt:13-19`) — trim, not blend. A
  bright logo over busy artwork will not knock out dark halos the way the board
  does.

### 1.4 Metadata at 494 — position MATCH; part order DELTA
- Position: `TvHeroSummary.kt:50` — `offset(96.dp*scale, 494.dp*scale)` ✓.
- Style: 24 sp / FontWeight.Medium (500), `Palette.body` #DAD8D3, separator "·"
  in #8F8D89, gap 12, 6 dp spacer before badges
  (`TvMediaMetadata.kt:20-30`) — all ✓ board.
- Badges: height 30, radius 5, 1.5 dp ring #F4F2EE@0.55, padding 0/8, 18/700,
  letter-spacing 0.02em→0.36 sp, ink #DAD8D3 (`TvMediaBadge.kt:15-21`,
  `TvMediaMetadata.kt:32`) — exact ✓.
- **Delta: content order** — board line reads `2026 · Action, Adventure · 1h 40m`
  (year, genres, runtime). `MediaPresentation.kt:5-13` builds
  **year, runtime, genres, ★ rating**, joins genres with `" / "`.
  → runtime↔genres swapped, `" / "` vs board `", "`, and an extra star rating
  the board's home hero never shows.

### 1.5 Synopsis 544 width 780 — MATCH
- `TvHeroSummary.kt:53-54` — offset(96, 544), width 780, 26 sp, color
  `Palette.body` #DAD8D3, maxLines 2, Ellipsis ✓ board (2-line clamp).
- **Delta: line-height 38 vs board 1.45×26 = 37.7 (+0.3 px).**

### 1.6 Buttons at 648 (220×72; 72 circles at 336/428) — geometry MATCH, resting style DELTA (major)
- Row: `TvHero.kt:40` — `offset(96, 648)`, `spacedBy(20.dp*scale)`.
  Play 96..316 (220×72, `TvHero.kt:41`), circle1 336..408, circle2 428..500
  (`TvHero.kt:46-47`, 72×72 `round = true` → CircleShape). **Exact board match**
  (336 and 428 verified).
- Play internals: radius 12 (`TvAction.kt:26` default), horizontal padding 28
  (`TvHero.kt:42`), play icon 24, text 26 Bold ✓. Icon↔text gap: app
  `Spacer(12.dp)` (`TvHero.kt:43`) vs board `gap:14px` → **−2**.
- **Major delta — Play is not a primary button at rest.** `TvAction.kt:23`
  declares `primary: Boolean = false` but **the parameter is never referenced in
  the function body** (verified by grep: only the signature line). The resting
  fill falls back to the default `background = Palette.text.copy(alpha = 0.16f)`
  (`TvAction.kt:25,33`) with light ink (`TvAction.kt:34`) — a ghost pill.
  The board's Play is **solid #F4F2EE with #111113 ink and
  `box-shadow: 0 18px 50px rgba(0,0,0,0.45)` at rest**. Focused state does turn
  solid (`light -> Palette.text`, ink `Palette.background`), so the board look
  only appears while focused. All 8 TV call sites that pass `primary = true`
  (TvHero.kt:42, TvWelcome.kt:44, TvPairingSignIn.kt:46, TvDetailHero.kt:63,
  TvSettingsDialogs.kt:36, TvSourceIntegrationForm.kt:135/246,
  TvPlayerStates.kt:32) are affected.
- Shadow: app elevates only when focused — `shadowElevation = if (light) 24.dp.toPx()*scale else 0f`
  (`TvAction.kt:40`); the board's 18px-offset/50px-blur resting shadow is absent,
  and Compose elevation is not that shadow spec.
- Circles: 16% white fill ✓, icons 30 (Plus/Info, `Lucide.Check` when saved —
  board only shows Plus) ✓, but **no `backdrop-filter: blur(24px)`** equivalent
  on either circle (no blur modifier anywhere in `TvAction.kt`).

### 1.7 Shelf title 30/600 at 824 — MATCH
- Rows plane top: `TvHomeScreen.kt:117` — `targetTop = (if (browsing) 512 else 824).dp * scale`,
  applied as LazyColumn top padding (`TvHomeScreen.kt:160`).
- Title: `TvHomeShelf.kt:36-37` — start padding 96, height 40, fontSize 30,
  lineHeight 36, letterSpacing −0.3 sp, `FontWeight.SemiBold` (600) ✓ board
  (30 px / 600 / −0.01em = −0.3 px at 96/824). Title box 824..864.

### 1.8 Card row 876 — MATCH
- Pitch: title 40 + `spacedBy(12)` (`TvHomeShelf.kt:35`) → cards start **876** ✓.
- Cards: LazyRow `contentPadding horizontal 96` (`TvHomeShelf.kt:39`),
  landscape 384 wide × 16:9 = 216 (`TvCatalogCard.kt:57`,
  `TvHomeSkeletonCard` TvHomeLoading.kt:35), radius 10 (`TvCatalogCard.kt:55`),
  gap 20 landscape / 16 portrait (`TvHomeShelf.kt:39`) ✓ boards (Tv3Home 96/500/904/1308/1712;
  Tv3HomeToRows portrait 96/344/592/840…).
- Visibility: 96 + 4×(384+20) = 1712 → 5th card 54% on-screen ≈ **4.5 cards** ✓
  (TVUI-08 item), identical to the board's peek.
- Caption under landscape cards: 14 top / 4 gap, title 24/600 with the board's
  tr-cap focus color switch (#DAD8D3→#F4F2EE, `TvCatalogCard.kt:221`),
  meta 20 px — **color delta: `Palette.secondary` #B6B4AF vs board #8F8D89**
  (`TvCatalogCard.kt:228`).

---

## 2. Tv3HomeLoading (TVUI-09, first half) — exact match, 13/13 elements

`TvHomeLoading.kt:19-28` vs board (all ✓, offsets/sizes/radii identical):

| Board element | Board | App (line) |
|---|---|---|
| logo block | 96/300, 360×150, r12 | :19 ✓ |
| metadata line | 96/494, 520×30, r6 | :20 ✓ |
| synopsis line 1 | 96/548, 760×26, r6 | :21 ✓ |
| synopsis line 2 | 96/586, 600×26, r6 | :22 ✓ |
| play button | 96/648, 220×72, r12 | :23 ✓ |
| circle 1 | 336/648, 72×72, r36 | :24 ✓ |
| circle 2 | 428/648, 72×72, r36 | :25 ✓ |
| shelf title | 96/828, 300×32, r6 | :26 ✓ |
| card row | 96/876, 5×384 16:9, r10, gap 20 | :27-28,35 ✓ |

- Shimmer: 1400 ms linear, ±600 px sweep, 1200 px band, stops .06/.13/.06
  (`TvLoadingShimmer.kt:17-21`) = board `.t3-skel` exactly ✓.
- Top nav is present during loading (board shows it; app renders TopNavigation
  via `TvHomeVisibility(!browsing, …)` at `TvHomeScreen.kt:194`, browsing=false
  while `home == null`) ✓. No dots skeleton on either side ✓.
- Entry condition: `home == null && busy && error == null`
  (`TvHomeScreen.kt:138`) — no board opinion; consistent.

## 3. Tv3HomeToRows (TVUI-09, second half) — motion & geometry mostly match

Board is a 9 s loop: 0–28% Home, 28–34% move (~450 ms per its own CSS comment),
34–72% rows, 72–78% back; easing `cubic-bezier(.2,0,0,1)`.

Matches:
- **824 → 512 rows translation**: board `tr-row` = translateY(−312px)
  (824−312); app `targetTop` 824→512 (`TvHomeScreen.kt:117-118`) — exact.
- **Timing/easing**: 450 ms + `CubicBezierEasing(0.2f, 0f, 0f, 1f)`
  (`TvHomeScreen.kt:116-118`, return scroll :109) = board comment + curve.
- **Row pitch**: app scroll pitch 384 (continue) / 516 (other)
  (`TvHomeScreen.kt:103`) = 40 title + 12 + 292 card (216 art + 76 caption) + 40
  gap; second shelf lands at 512+384 = **896**, its cards at **948** — exactly
  the board's "New this week" 896/948 (232×348 portrait cards, gap 16) ✓.
  Row spacing `spacedBy(40.dp*scale)` (`TvHomeScreen.kt:162`) ✓.
- **Browse summary entrance**: board `tr-bill` fades in from translateY(24px);
  app `TvHomeVisibility(browsing && settled != null, scale, 24, 450)`
  (`TvHomeScreen.kt:148`) ✓. Content: logo 96/96 (420 wide — `TvBrowseSummary.kt:22`,
  height capped 120; board height unconstrained), metadata 96/240
  (`TvBrowseSummary.kt:28`), synopsis 96/290 w700 26sp (`TvBrowseSummary.kt:31-32`)
  — ✓ (app clamps to 3 lines; board's ToRows synopsis has no clamp style).
- **Browse backdrop**: art 760/0 1160×620 (`TvHomeBackdrop.kt:38`), right fade
  760/0 560×620 (`:40-41`), bottom fade 0/380 — app height **240** vs board
  **241** (−1) (`:42-43`) ✓.

Deltas (exact):
- **Hero content exit**: app offset **−80** dp×scale (`TvHomeScreen.kt:153`)
  vs board `tr-hero` translateY(−60px) → **−20 px further**.
- **Nav exit**: app `TvHomeVisibility(!browsing, scale, 0, 200)`
  (`TvHomeScreen.kt:194`) — no slide, 200 ms fade; board `tr-nav` = −40px slide
  over ~450 ms → **missing −40 px slide, 250 ms shorter**.
- **Artwork exit**: app fades AND slides the artwork −80 dp×scale
  (`TvHomeBackdrop.kt:24`); board `tr-art` is opacity-only → **extra −80 px
  slide not in the board**.
- **Ken Burns missing**: board animates the billboard art
  `t3-kenburns 14s ease-out` scale 1→1.06; no scale animation exists anywhere
  in the app source (grep for kenburns/1.06: no hits; `BufferedArtwork.kt` only
  crossfades). **Not implemented.**
- **Stagger**: app `sequentialFade = true` adds a 150 ms enter delay and 150 ms
  exits per child (`TvHomeVisibility.kt:24-26`); the board animates each plane
  as one block — interpretive, not board-literal.
- **Billboard scrims** (adjacent file, same screens): left scrim stops
  app 0→#0B0B0C@.86, .36→@.4, .7→0 (`TvHomeBackdrop.kt:28`) vs board
  0→@.85, 34%→@.55, 62%→0 → **mid alpha .40 vs .55 (−.15), transparent end
  70% vs 62% (+8%)**; combined vertical scrim app stops 0→.3, .45→0, .8→.9,
  1→opaque (`:29`) vs board's two bands (top 0..200 @.7→0; bottom 560..1080
  0→.92@846→solid) → **app's top scrim weaker/longer (.3@0→0@486 vs .7@0→0@200)
  and bottom darkening starts ~864 vs 560, reaching .92 at 846 on the board**.

## 4. Other TVUI-08 line items (same domain, from ISSUES.md:90)

- "New episode" badge (Continue Watching): implemented
  (`TvCatalogCard.kt:85-89`, light fill #F4F2EE / ink #0B0B0C, 30h, r6, 18/700).
  Deltas: badge anchor padding **10 vs board 12 (−2 px each axis)**; ink
  **#0B0B0C vs board #111113**.
- "New" badge on catalog rows: rendered **dark** (`TvCatalogCard.kt:145-159` —
  only "New episode" is in the `loud` light-fill list) vs the board's light
  #F4F2EE/#111113 "New" chip (Tv3HomeToRows portrait cards).
- Three-dot **overflow menu on Continue Watching: not implemented** — no
  Ellipsis/MoreHorizontal icon or menu anywhere under `features/home/`
  (grep over the folder: no hits). TVUI-08 remains open on this item.
- Card rest ring: board `inset 0 0 0 1px rgba(255,255,255,0.08)` on every card;
  app draws none (only the focus ring) → missing.
- Card focus ring: app 3 dp stroke #F4F2EE (`FocusOutline.kt:16-25`) vs board
  `tr-ring` `inset 3px #FFFFFF + inset 5px rgba(0,0,0,0.35)` → **missing the
  2 px dark inner band; #F4F2EE vs #FFFFFF**.
- Watch progress: app height **5** with 3/3/3 inset (`TvCatalogCard.kt:99-100`)
  vs board 4 px flush to the card edges → **+1 height, 3 px inset**. Track
  white@.25 ✓, fill #F5C542 ✓ (`TvWatchProgress.kt:16-20`).

---

## 5. Consolidated delta list (exact numbers)

1. **Play button resting style (major)** — `primary` param dead code
   (TvAction.kt:23); resting fill #F4F2EE@16% + light ink vs board solid
   #F4F2EE + #111113 ink + shadow 0 18px 50px rgba(0,0,0,0.45). Focused-only
   solid. Affects all 8 TV `primary = true` call sites.
2. Carousel autoplay **8 s vs board 9 s** fill (HeroCarousel.kt:31).
3. Dots track **#F4F2EE@0.35 vs rgba(255,255,255,0.30)** (HeroIndicators.kt:24).
4. Hero metadata part order **year·runtime·genres·★ vs board year·genres·runtime**;
   genres joined `" / "` vs `", "`; extra ★ rating (MediaPresentation.kt:5-13).
5. Hero logo box **(96,306) 410×160 bottom 466** vs board h1 (96,300) img 170
   bottom 470 (+6/−4); logo prefetch 420 wide vs 410 display; TrimLogo vs
   board's `mix-blend-mode: lighten`.
6. Play icon↔label gap **12 vs 14** (TvHero.kt:43); no backdrop blur(24) on the
   two circle buttons; focused-only elevation 24 dp vs resting 18/50 shadow.
7. Synopsis line-height **38 vs 37.7** (TvHeroSummary.kt:54).
8. ToRows motion: hero exit **−80 vs −60**; nav exit **0px/200ms vs −40px/~450ms**;
   artwork exit adds **−80 slide** (board fades only); **Ken Burns 14s 1→1.06
   absent**; bottom fade 240 vs 241; left scrim mid-stop **.40 vs .55** and end
   **70% vs 62%**; bottom scrim start **~864 vs 560**.
9. Cards: focus ring lacks the **2 px dark inner band** (#F4F2EE vs #FFFFFF);
   rest ring 1px rgba(255,255,255,0.08) missing; caption meta **#B6B4AF vs
   #8F8D89**; badge anchor **10 vs 12**, ink **#0B0B0C vs #111113**; "New"
   badge dark vs board light; progress bar **5 px + 3 px inset vs 4 px flush**.
10. TVUI-08 scope: **three-dot overflow menu on Continue Watching not
    implemented** (no code found); "New episode" badge, dots, 4.5-card peek,
    824/876 shelf geometry, and the full Tv3HomeLoading skeleton are all in
    place and pixel-faithful.

Everything else checked — dots geometry (48/16/4/8 @ 96,760), FEATURED n OF m
semantics, metadata anchor 96/494, synopsis 96/544 w780, button row 96/648 with
circles at 336/428, shelf title 30/600 @ 824, card row @ 876, the 384/516 row
pitch, 824→512 with 450 ms cubic-bezier(.2,0,0,1), and all 13 loading-skeleton
elements — **matches the boards exactly**.
