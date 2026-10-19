# TV board ↔ app parity audit (read-only)

Source of truth: `design/viptv-design-system/reference/screens/html/tv/*.html` (exact px/colour in inline
styles), cross-checked with `img/tv/*.webp` only where noted. App: `app/composeApp/src/commonMain/kotlin/org/viptv/app/…`.
All app geometry is design-px scaled by `scale = maxWidth / 1920.dp`, so dp values compare 1:1 with board px.
Line numbers are current HEAD. `TvAction` = `components/TvAction.kt`.

Top recurring defect: **`TvAction(primary = true)` is a dead parameter** — `components/TvAction.kt:23` accepts
it but the fill logic at `:33` only uses `background` (default `Palette.text.copy(alpha = 0.16f)`). Every
element the boards draw as a solid `#F4F2EE` button with `#111113` ink (Home Play, retry, Skip intro, Play
next episode, selected browse pill) renders translucent instead. Cited per board below.

---

## Tv3Home → features/home/TvHomeScreen.kt + TvHero.kt + TvHomeShelf.kt + HeroIndicators.kt

MATCHES
- Meta line 96/494, 24/500 `#DAD8D3` with `#8F8D89` separators, badges 30h/r5/inset 1.5px `rgba(244,242,238,.55)`/18/700 → `TvHeroSummary.kt:50` + `components/TvMediaMetadata.kt` + `components/TvMediaBadge.kt:16`.
- Description 96/544 w780, 26px lh1.45 (≈38), `#DAD8D3`, clamp 2 → `TvHeroSummary.kt:53`.
- Play 220×72 r12 pad 28, icon 24, 26/700; My List + info 72px circles bg `rgba(255,255,255,0.16)`, icons 30, at 648/96/428 → `TvHero.kt:41-47`.
- Indicators at 96/760: selected 48×4, rest 16×4, r2, track `rgba(255,255,255,0.3)`, fill `#F4F2EE`, gap 8; "Featured n of m" semantics-only → `HeroIndicators.kt`.
- Row title 30/600 ls −0.01em at 96; billboard rows top 824 (browsing 512); portrait cards 232 wide/16 gap; continue row 384 landscape → `TvHomeScreen.kt:120-121,167` + `TvHomeShelf.kt:28-31`.
- Top nav: brand 96/44, items 56h pad 18 gap 8 26px (500/700), 3px underline at bottom 4, profile 56 circle right 96/38 with 2px `rgba(255,255,255,0.35)` ring → `components/TvNavigation.kt`.

DIFFERS
- Play button fill: board solid `#F4F2EE` + ink `#111113` → app translucent `#F4F2EE` 16% (unfocused) — `components/TvAction.kt:23,33` (`primary` unused; `TvHero.kt:42` passes it).
- Play icon gap: board 14px → app 12px — `TvHero.kt:43`.
- Hero logo: board `h1` at 96/**300**, height **170**, `mix-blend-mode: lighten`, intrinsic width → app 96/**306**, box **410×160**, no blend → `TvHeroSummary.kt:45`.
- Card art badge inset: board screens use 12/12 (Tv3Home continue card, Tv3HomeToRows) → app 10/10 — `TvCatalogCard.kt:85`.
- Nav items: board 6 (`Home Movies Series Live Calendar My List`) → app 7 (adds **Anime**) — `components/TvNavigation.kt` (filters only Discover/History from `components/Destinations.kt`).
- Round buttons' `backdrop-filter: blur(24px)` (board) → not implemented (plain fill) — `TvHero.kt:46-47` via `TvAction`.
- (Board has no text-title hero variant; app fallback 48sp/2-line at 96/306 — `TvHeroSummary.kt:46-49` — unverifiable vs boards.)

## Tv3CardStates → features/home/TvCatalogCard.kt (+ TvHomeShelf.kt)

MATCHES
- Art tile r10 bg `#161618`; portrait 232×348 / landscape 384×216 in shipping screens; aspect 2:3 / 16:9 → `TvCatalogCard.kt:38-40,63`.
- New episode label: light `#F4F2EE` fill, `#111113` ink, 18/700, h30, r6; Ended: dark `rgba(11,11,12,0.8)` + `#F4F2EE` ink → `TvCatalogCard.kt:85-91,140-150`.
- Watched: 34×34 circle top-right inset 10, bg `rgba(11,11,12,0.78)`, 20px check, art not dimmed → `TvCatalogCard.kt:92-97`.
- Returning moved to meta line; LIVE red `#E5484D`; certification chip on meta line → `TvCatalogCard.kt:130-160,227-232`.
- Progress bar: 5px, track `rgba(255,255,255,0.25)`, fill `#F5C542` accent, bottom-anchored, only with progress → `TvCatalogCard.kt:99-100` + `TvWatchProgress.kt`.
- Caption colour `#B6B4AF` 20px; title SemiBold; single a11y node with full description → `TvCatalogCard.kt:218-236`.

DIFFERS
- LIVE badge type: board 16px/800, ls 0.06em, h28, pad 0 9 → app same 18/700 treatment as other labels (h≈30, pad 10/4) — `TvCatalogCard.kt:85-91`.
- No-art tile: board gradient `linear-gradient(160deg,#2A2A30,#141416)` with **full title** (28/700, lh1.12, ls −0.01em, clamp 3, bottom-aligned, pad 18, gap 6) + meta line 18px `#B6B4AF` → app flat `#1A1A1C` with **2-letter initials** (46/40, 50% alpha) + year bottom-left — `TvCatalogCard.kt:175-190`.
- Caption title size: board 22/600 (constant across 180- and 232-wide cards) → app 24/600 — `TvCatalogCard.kt:221`.
- Certification chip: board h26, pad 0 8, 16/700, ls 0.02em → app h30, 18/700 — `TvCatalogCard.kt:227` + `components/TvMediaBadge.kt:16-22`.
- Caption vertical rhythm: board margin-top 12, gap 5-6 → app pad-top 14, gap 4, reserved 76dp — `TvCatalogCard.kt:81,216,219`.
- Episode card: board draws series subtitle as its own 18px line *above* the meta line ("Please Don't Go" / "Monster…" / "S1 E2 · 52m") → app substitutes subtitle for the meta line in one 20px row — `TvCatalogCard.kt:156-158,226-232`.
- Progress bar inset: board flush left/right 0 → app 3dp inset each side (and square corners vs board r2) — `TvCatalogCard.kt:100`.
- Card art inner hairline: every board card has `inset 0 0 0 1px rgba(255,255,255,0.08)` → app none — `TvCatalogCard.kt:63-70`.
- Landscape unfocused caption: board CardStates shows captions under all landscape cards → app landscape captions shown (correct); browse grid captions are compared under Tv3Movies.

## Tv3RowStates → features/home/TvHomeShelf.kt + TvHomeScreen.kt

MATCHES
- EMPTY row not drawn at all: rows with no cards filtered out before composition — `TvHomeScreen.kt:56-58`.
- PENDING row: title + skeleton cards at card size, r10, shimmer — `TvHomeShelf.kt:45` + `TvHomeLoading.kt` (TvHomeSkeletonCard).
- Row title 28-30/600 with 12px title→cards gap — `TvHomeShelf.kt:28-31` (app uses 30px for all rows).

DIFFERS
- FAILED row: board is a 940×169 alert card (r12, bg `rgba(255,255,255,0.05)`, 1px inset ring `rgba(255,255,255,0.07)`, pad 24/28, title "Couldn't load this row" 22/700, message 19px `#B6B4AF`, right-aligned "Try again" 60h solid white pill with 22px rotate icon) → app renders only a bare `TextButton` with `row.message ?: "Try again"` — `TvHomeShelf.kt:47`. No card, no title, no message layout, no white pill.
- Row title size: RowStates board 28px → app 30px — `TvHomeShelf.kt:29`.
- Skeleton count: board 6 cards → app 5 — `TvHomeShelf.kt:45`.
- Dead branch: `RowState.EMPTY` renders "Nothing here yet." — `TvHomeShelf.kt:46` — unreachable (upstream filter) and contradicts the board's "not drawn at all"; remove or keep filter only.

## Tv3Movies → features/browse/TvBrowseScreen.kt (+ Discovery*)

MATCHES
- Grid: 7 columns × 232px cards, 16px h-gap, 36px v-gap, content at 96 → 1720 wide — `TvBrowseScreen.kt:76-78`.
- Pills bar top 140 (nav 132 + 8), 40px gap to grid (244) — `TvBrowseScreen.kt:52,70`.
- Card anatomy 232×348 r10 `#161618` → `TvCatalogCard.kt`.
- Focused card gets 3px white inset ring → `tvCardFocusOutline` (`components/TvFocusTreatment.kt`).

DIFFERS
- Catalog pill: board 64h, r32, pad 24, **24/700**, bg `rgba(255,255,255,0.16)`, blur 24 → app 56h, r28, 24sp **no weight** — `TvBrowseScreen.kt:63` / `DiscoveryChoiceFilter.kt:19-22`.
- "Filters" pill (board at 560/140, opens the filter page) → **missing**; app renders every control as its own top pill — `DiscoveryFilters.kt:31-50`.
- Active-filter chip row (board at 800/152→right 96: 40h chips, r20, bg `rgba(255,255,255,0.1)`, 20/600 `#DAD8D3`, gap 10, one chip per applied filter with dismiss) → **missing entirely** — nothing in `TvBrowseScreen.kt`/`DiscoveryFilters.kt`.
- Selected-pill state: board marks the chosen catalog with weight 700 (+ focus ring) → app passes `primary = option == activeGenre` for genre pills, which is the dead `TvAction.primary` — no selected fill/weight — `TvBrowseScreen.kt:64` + `components/TvAction.kt:23,33`.
- Focused-card caption: board draws title 22/600 + meta 20 `#B6B4AF` + cert chip 26/16/700 under the **focused** grid card → app draws no caption in the browse grid at all (`focusedCaptions` defaults false; caption box not composed for portrait) — `TvBrowseScreen.kt:80-82` + `TvCatalogCard.kt:81-83,213-215`.

## Tv3MoviesCatalogs → features/browse (DiscoveryChoiceFilter/DiscoveryFilters)

MATCHES
- Drawer concept exists for multi-choice filters (different board) and source drawer; selected catalog exposed via pill label + a11y.

DIFFERS
- Catalog chooser: board is a right drawer 720px wide at x1200 (pad 72/96/54/64, `#161618`, shadow −40/0/100, slide-in .3s) with h1 "Catalog" 44/700, sub 24 `#B6B4AF`, grouped source headers 22/700 `#8F8D89`, 76h rows r14 (selected `#F4F2EE`/`#111113`, unselected `rgba(255,255,255,0.04)`/`.1`), 26/600, trailing 26px check → app is a pill opening a Material `DropdownMenu` — `DiscoveryChoiceFilter.kt:19-30`. No drawer, no grouped-by-source list, no selected check row, no 44px heading.

## Tv3MoviesFilters → features/browse/DiscoveryMultiChoiceSheet.kt (+ Discovery*)

MATCHES
- Right-side slide-in sheet with scrim, checkbox semantics, max-selections guard, draft-then-apply — `DiscoveryMultiChoiceSheet.kt:43-50`.
- Header 32sp + "Select up to N" 20 `#B6B4AF` — `DiscoveryMultiChoiceSheet.kt:58-60`.

DIFFERS
- Choice layout: board wraps **chips** (64h, r32, pad 24, 24/600, gap 12; checked = `#F4F2EE`/`#111113` with 22px check, unchecked `rgba(255,255,255,0.08)`) → app stacks full-width 64h rows with leading SquareCheck icons — `DiscoveryMultiChoiceSheet.kt:63-68`.
- Sheet width: board panel 1024 (x800→1824) → app 800 — `DiscoveryMultiChoiceSheet.kt:43`.
- Buttons: board "Show results" (primary) + "Clear all" at y940 → app "Apply" / "Clear" full-width — `DiscoveryMultiChoiceSheet.kt:73-75`.
- Counter: board "2 of 3" next to the heading → app "(2)" suffix on the pill label only — `DiscoveryMultiChoiceFilter.kt:30`.
- Left filter page (8 rows 640×80 r14 pad 26 at x96, y220…) → no equivalent; filters live as top pills — `DiscoveryFilters.kt:31-50`.

## Tv3MoviesLoading → features/browse/TvBrowseLoading.kt

MATCHES
- 7×2 skeleton grid at 96/244, 232×348, 16/36 gaps, shimmer.

DIFFERS
- Skeleton card radius: board 10 → app 12 — `TvBrowseLoading.kt:23`.
- Skeleton count: board 15 → app 14 — `TvBrowseLoading.kt:22`.
- Filter bar while loading: board replaces the catalog/filters pills with two skeletons (440×64 r32 at 96/140 and 200×64 at 560/140) → app keeps live interactive pills — `TvBrowseScreen.kt:63-68` + `DiscoveryFilters.kt`.

## Tv3MoviesEmpty → features/browse/TvBrowseScreen.kt

DIFFERS (whole state)
- Board: centred stack from y420 — 96px circle (bg `rgba(255,255,255,0.1)`, 46px search icon), h1 "Nothing matches these filters" 44/700 ls −0.02em, p 26/1.45 `#B6B4AF` max-w 980 ("Popular from VIPTV Sources has no movies with all 8 filters. Removing the newest one usually helps."), buttons "Remove 'After 1 Jan 2021'" 470×72 solid + "Clear all filters" 300×72 translucent.
- App: single left-aligned `Text("No titles are available in this catalog.", 24sp, #B6B4AF)` at the top of the content column — `TvBrowseScreen.kt:74`. No icon, no heading, no filter-count message, no actions.

## Tv3MoviesError → features/browse/TvBrowseScreen.kt

DIFFERS (whole state)
- Board: centred stack from y420 — 96px circle + 46px alert icon, h1 "Couldn't load Popular" 44/700, p 26 `#B6B4AF` "VIPTV Sources didn't answer in time. Your filters are kept.", buttons "Try again" 260×72 solid (rotate icon) + "Choose another catalog" 400×72.
- App: `error?.let { Text(it, color = Palette.danger) }` — one danger-coloured line under the pills — `TvBrowseScreen.kt:72`. No icon, no heading, no retry/catalog actions, and the message colour is `#FF7A6E` rather than the board's `#B6B4AF` body copy.

## Tv3SetDisplay → features/settings/TvSettingsScreen.kt (+ TvSettingsRow/Sidebar/Heading)

MATCHES
- Sidebar: 560px `#161618`, title "Settings" 44/700 at 96/96, 9 items 72h/440w/r12/pad 24/gap 18, 26px (500 unselected `#B6B4AF` / 700 selected `#F4F2EE`), icons 28, first item y196 — `TvSettingsSidebar.kt:17-47`.
- Content column at x640 (sidebar 560 + 80), heading "Display" 40/700 y96 — `TvSettingsScreen.kt:74` + `TvSettingsHeading.kt`.
- Row anatomy: 1184w, 96h, r14, pad 28, label 26/600 + subtitle 20/500 (`#4A4945` on white), value+chevron, 12px inter-row gap — `TvSettingsRow.kt:43-69` (spacing from pages, e.g. `TvPlaybackSettings.kt:15`).

DIFFERS
- **Display page unimplemented**: board rows Accent colour (4 swatches 44px r22 gap 18, selected double ring `0 0 0 3px #0B0B0C, 0 0 0 6px #F4F2EE`), Show ratings on cards, Text size, Reduce motion ("Follow TV"), Screen saver ("After 5 minutes"), Fit to screen → app renders the generic placeholder: heading + one "Open Profiles" row that shows an "not available" dialog — `TvSettingsScreen.kt:86-91`.
- Focused row ink: board `#111113` → app `Palette.background #0B0B0C` — `TvSettingsRow.kt:44,64`.
- Unfocused row bg: board `rgba(255,255,255,0.06)` (≈#211F1F) → app `#191919` — `TvSettingsRow.kt:64`.
- Selected sidebar bg: board `rgba(255,255,255,0.14)` (≈#363638) → app `#333333` — `TvSettingsSidebar.kt:28`.
- Sidebar title y: board 96 → app 93 — `TvSettingsSidebar.kt:19`.

## Tv3SetAbout → features/settings/TvSettingsScreen.kt

MATCHES
- Sidebar, heading "About" 40/700 at 640/96, row chrome (see Tv3SetDisplay).

DIFFERS
- **About page unimplemented**: board rows Version 0.3.0 (412) · up to date + "Check for updates" (focused white row), Connected to [server.example] · fast, Send diagnostics (subtitle), Privacy, Open-source licences, and the footnote at 640/776 "Show and episode data from Simkl. This product uses the TMDB API but is not endorsed or certified by TMDB." → app shows heading + single row "VIPTV · Experimental build" — `TvSettingsScreen.kt:89-90`. No version/server/diagnostics/privacy/licences rows, no footnote.

## Tv3SetParental → features/settings (ParentControlsNative.kt via SettingsRoute)

MATCHES
- Heading "Parental controls" 40/700 in the content column, one TV row (r14, 96h) that jumps to Profiles or dashboard — `ParentControlsNative.kt:19-30`.

DIFFERS
- **Parental UI unimplemented on TV**: board has profile chip row (52px avatars + names, r36 pills, selected `rgba(255,255,255,0.16)`/700) at 640/216; "Movies up to" ladder (26/700 label + chips 64h min-w 96 r32 22/700: unselected `rgba(255,255,255,0.04)`/`#8F8D89`, available `rgba(255,255,255,0.18)`, selected `#F4F2EE`/`#111113`) at 330; "Series up to" ladder at 476; note 22 `#8F8D89` at 618; rows 88h — Hide titles with no rating, Blocked titles (subtitle + count badge "2"), PIN to leave cedes's profile → app is two text lines + one row ("Manage profiles"/"Open server dashboard") — `ParentControlsNative.kt:19-30`. None of the ladders, profile switcher, or the three rows exist on TV.
- Row height: board 88h for parental rows → app row 96h — `TvSettingsRow.kt:43` (only if implemented).

## Tv3SourcesEmpty → features/sources/TvSourcesScreen.kt

MATCHES
- Drawer: 960px right half, bg `#161618`, pad 64/96/72/56, h1 "Choose a source" 44/700, sub 24 `#B6B4AF`, slide-in with scrim — `TvSourcesScreen.kt:80-90`.
- Filters + 104h rows + footer status line exist (see Torrent).

DIFFERS
- Empty state: board shows an 88px circle (bg `rgba(255,255,255,0.1)`, 42px search icon), h1 "No sources yet" 40/700, p 26 `#B6B4AF` ("None of your 3 add-ons or 8 providers has The End of Oak Street right now. New releases often appear within a few days."), buttons "Check again" 280×72 solid + "Manage add-ons" 300×72 translucent, at ~y336 → app renders one plain 22sp `#B6B4AF` line ("No sources found. Enable a provider that supplies streams.") inside the list — `TvSourcesScreen.kt:108-114`. No icon, heading, reassurance copy, or actions.
- Drawer shadow: board `box-shadow: -40px 0 100px rgba(0,0,0,0.6)` → app none — `TvSourcesScreen.kt:80-82`.

## Tv3SourcesTorrent → features/sources/TvSourcesScreen.kt + TvSourceRow.kt

MATCHES
- Row chrome: 104h, r14, pad 28, quality 30/800, title SemiBold ellipsis, meta 20-22px, chevron-free, 10px list gap — `TvSourceRow.kt:32-52`.
- Torrent-only messaging: counts providers, names debrid/TorBox remedy, offers alternatives — `TvSourcesScreen.kt:110-112`.

DIFFERS
- Torrent alert: board is a `role=alert` card (pad 26/28, r16, bg `rgba(255,255,255,0.07)`, 32px info icon, 26px `#F4F2EE` text, buttons "What's a debrid service?" 400×72 solid + "Back" 180×72) → app inlines the message as plain 22sp secondary text in the list — `TvSourcesScreen.kt:110-112`. No card, no buttons.
- Unplayable rows: board draws them `aria-disabled` (bg `rgba(255,255,255,0.04)`, opacity 0.6, no focus) with a **"Needs debrid"** tag (32h, r6, bg `rgba(255,255,255,0.1)`, 18/700 `#DAD8D3`) and language chips (26h, r5, inset 1.5px `rgba(244,242,238,0.4)`, 16/700 `#B6B4AF`) → app rows stay enabled/normal with no tag and no language chips — `TvSourceRow.kt:32-52` (`enabled` always true from `TvSourcesScreen.kt:99`).
- Row title size: board 24/600 → app 28/600 — `TvSourceRow.kt:45`.
- Row meta: board single 20px line "HEVC · EAC3 5.1 · 18.4 GB · [Provider C]" → app splits into release/technical lines at 22px + right addon text — `TvSourceRow.kt:46-48`.
- Quality column width: board 110 → app 116 — `TvSourceRow.kt:37`.
- Best-match badge (app, right of title; board base Sources board) — kept, no conflict.

## Tv3AddonEntry → features/settings/TvSettingsScreen.kt (install dialog) + TvAddonSettings.kt

MATCHES
- The Add-ons page itself (list + "Add an add-on" row + QR side panel) matches Tv3SetAddons; entry point opens a dialog titled "Add an add-on" — `TvSettingsScreen.kt:111`.

DIFFERS
- Entry form: board is a full page — eyebrow "Add-ons" 22/600 `#8F8D89`, h1 "Add an add-on" 52/700, sub 24 `#B6B4AF` at 96/72; label "Add-on address" 24/600 `#DAD8D3` at 250; field 1060×80 r14 bg `rgba(255,255,255,0.07)` 28/500 with 3px white inset ring when focused and accent caret; **on-screen keyboard** (101×66 keys r10 bg `rgba(255,255,255,0.09)` 26/600, rows for 0-9/a-z/`- _ . :`/`https://`/`.com`/`/`/Clear) at 400; QR panel 564w at 1260/250 r18 bg `rgba(255,255,255,0.06)` pad 32; Install 260×72 / Cancel 220×72 at 860 → app is a Material `AlertDialog` with an `OutlinedTextField` ("Add-on address", placeholder manifest URL), a `LinearProgressIndicator`, and "Add"/"Cancel" `TextButton`s — `TvSettingsScreen.kt:111-135`. No TV keyboard (one exists for sign-in: `features/entry/TvEntryKeyboard.kt`), no QR panel, no 52px heading, no 260/220 buttons.

## Tv3AddonInstall → features/settings/TvSettingsScreen.kt (install dialog)

DIFFERS (whole state)
- Board: after typing, a confirm dialog at 510/240, 900×auto, pad 48, r24, bg `#212124`, shadow 0 40 100, fade-up — h1 "Install this add-on?" 44/700; identity card (pad 22 r16 bg `rgba(255,255,255,0.06)`, 88×88 r18 letter tile `#8A6B3E` "D" 40/800, name 30/700 + version 500 `#B6B4AF`, "From [addon.example]" 22 `#B6B4AF`); "Adds catalogs: Popular, New, Genres · Types: Movies, Series" 24 `#DAD8D3`; trust note 22 `#8F8D89`; buttons Install 260×72 solid + Cancel 220×72.
- App: install posts immediately from the entry AlertDialog; no manifest preview/confirmation step at all — `TvSettingsScreen.kt:121-129` (`installWithCompletion` fires straight from "Add").

## Tv3Live → features/live/TvLiveScreen.kt (+ TvLivePreview/TvLiveFilters/LiveGuide)

MATCHES
- Header column 96/130 w820 gap 14: LIVE badge h30 r6 `#E5484D` 18/800 ls .06em; channel line 22/600 `#B6B4AF`; programme h1 52/700; time row 24/500 `#DAD8D3` with badges + "18 min left"; description 26 — `TvLivePreview.kt:22-47`.
- Preview 640×360 r12 `#212124` at 1184/120 with artwork, 90px bottom scrim to `rgba(0,0,0,0.65)`, play icon + 20/600 label right 16/bottom 14 — `TvLivePreview.kt:56-67`.
- Category tabs at 512: 52h r26 pad 22 22/600, selected `rgba(255,255,255,0.16)`/`#F4F2EE`, else `#B6B4AF`, gap 10 — `TvLiveFilters.kt:20-27`.
- Guide: timeline header 44h, labels 20/600 `#B6B4AF`; channel cells 300×72 r10 bg `rgba(255,255,255,0.06)` (number 22/600 w34 + Bricolage 30/800); programme cells 72h r10 bg `rgba(255,255,255,0.09)` (focused `#F4F2EE`, ink `#111113`), 22/600 ellipsis, current-programme progress wash, "started earlier" chevron; 8px row gap; 28px channel→grid gap; 30-min cell ≈343; now marker 2px `#E5484D` + 12px dot — `LiveGuide.kt:30-95` + `LiveProgramCell.kt` + `TvLiveChannelLabel.kt`.

DIFFERS
- Preview label copy: board "Watch live" → app "OK to watch" — `TvLivePreview.kt:66`.
- Programme progress bar: board draws a 360×4 bar (track `rgba(255,255,255,0.2)`, fill `#F4F2EE`) in the header column under the time row → app has no header progress bar (only the guide cell wash) — `TvLivePreview.kt:40-46`.
- Preview play icon: board 18px filled triangle → app 24px outline Lucide.Play — `TvLivePreview.kt:65`.

## Tv3PlayerControls → features/player/TvPlayerControls.kt + TvPlaybackTimeline.kt

MATCHES
- Scrims: top 280px `rgba(0,0,0,0.72)`→0 and bottom 640→1080 to 0.82 — `TvPlayerControls.kt:22-23`.
- Title stack 96/64 gap 6: 30/700 + 24 `#B6B4AF`; "Ends at …" 22 `#B6B4AF` right 96/72 — `TvPlayerControls.kt:24-33`.
- Pill row at 846: 56h r28 pad 22 gap 10, icons 24, 22/600, bg `rgba(255,255,255,0.16)`; order Episodes / Audio & subtitles / Source · quality / spacer / Next episode — `TvPlayerControls.kt:36-43` + `TvPlayerPill`.
- Timeline: 1728 wide at 96, 6h r3 track `rgba(255,255,255,0.22)`, played `#F5C542`, thumb 28px white + 8px `rgba(255,255,255,0.25)` halo (app 44dp halo circle + 28 thumb) — `TvPlaybackTimeline.kt:31-38`.
- Times at 962: 22/600 tabular, left `#F4F2EE`, right `−38:10` `#B6B4AF` — `TvPlaybackTimeline.kt:43-49`.

DIFFERS
- Buffered span: board draws a lighter `rgba(255,255,255,0.18)` range (52%) behind the accent fill → app has no buffered range (only played + a scrub diff bar) — `TvPlaybackTimeline.kt:36-40`.

## Tv3PlayerBuffer → features/player/TvPlayerBuffer.kt (+ TvPlayerScreen.kt)

MATCHES
- Scrim `rgba(0,0,0,0.55)`; centred spinner 72px, 5px ring, track `rgba(255,255,255,0.18)`, white arc, .9s linear spin; "Starting playback" 30/600 `#F4F2EE`; context line 24 `#B6B4AF`; gap 26 — `TvPlayerBuffer.kt:24-35` + `components/TvLoadingIndicator.kt`.

DIFFERS
- Fade-in delay: board .2s → app .5s — `TvPlayerBuffer.kt:23`.
- Mid-playback rebuffer (board covers only initial start): app also shows a centred 48dp `CircularProgressIndicator` while paused/buffering — `TvPlayerScreen.kt:106-107` (extra state, not on board).

## Tv3PlayerError → features/player/TvPlayerFailure.kt

MATCHES
- Scrim `rgba(0,0,0,0.85)`; 88px circle bg `rgba(255,255,255,0.12)` + 44 icon; h1 48/700 ls −0.02em "This source stopped working"; p 28 `#B6B4AF`; assertive live region; buttons 380/380/200 ×72 r12 gap 16, icons 28, 26/700 — `TvPlayerFailure.kt:19-62`.

DIFFERS
- Retry fill: board solid `#F4F2EE` + `#111113` (+ `0 18px 50px rgba(0,0,0,0.45)`) → app translucent (dead `primary`) though the drop shadow is drawn — `TvPlayerFailure.kt:46-47` + `components/TvAction.kt:23,33`.
- Third button copy: board "Go back" → app "Back" — `TvPlayerFailure.kt:59`.

## Tv3PlayerNext → features/player/TvNextEpisode.kt

MATCHES
- Video dimmed (board brightness .75 ≈ app 25% black wash); info panel 1040/120 w784 gap 18: "Next episode" 24/600 `#B6B4AF`, h1 52/700, meta 24/500 `#DAD8D3` + TV-MA/HD badges, description 26 lh1.45 clamp 3, buttons "Play next episode" (icon 24, 72h, primary) + "Keep watching credits" 340×72, footnote 22 `#8F8D89` "Nothing plays on its own…"; credits label 22 `#B6B4AF` at 96/616 — `TvNextEpisode.kt:27-61`.

DIFFERS
- Primary button fill: board `#F4F2EE`/`#111113` auto-width (pad 28) → app translucent (dead `primary`), fixed 317px — `TvNextEpisode.kt:50` + `components/TvAction.kt:23,33`.
- Arrive animation: board static; app adds 350ms-delayed fade/16dp rise (harmless extra).

## Tv3PlayerScrub → features/player/TvPlaybackTimeline.kt + TvSeekPreview.kt

MATCHES
- Preview card 400×225 r12 with 3px white ring + `0 24px 60px rgba(0,0,0,0.6)` shadow (app 406×231 incl. 3px border) and centred 26/700 tabular time (w120) — `TvSeekPreview.kt:15-28`.
- Scrub diff indicator (board shows played vs scrub visually) — app's white `.55` diff bar — `TvPlaybackTimeline.kt:39-40`.

DIFFERS
- Thumb position while scrubbing: board keeps the thumb at the **played** position (27%) while the preview/time follow the scrub target (41.5%) → app **moves the thumb** to the scrub target (`current` drives the Slider) and shows a diff bar between actual and target — `TvPlaybackTimeline.kt:26,31-36`.
- Buffered span missing (same as Controls) — `TvPlaybackTimeline.kt:36-40`.
- Preview centring: board centres the preview at the scrub fraction (unclamped; board x613 for 41.5%) → app clamps to the track bounds — `TvSeekPreview.kt:16` (clamping is an improvement, noted for completeness).

## Tv3PlayerSkip → features/player/TvPlayerStates.kt (TvSkipPrompt)

MATCHES
- Pill 280×72 r12 at right 96 / bottom 112 (board top 896), icon 26, 26/700, gap 12; countdown rail 4px `rgba(17,17,19,0.35)` at the button bottom; fade-up .3s — `TvPlayerStates.kt:22-40`.

DIFFERS
- Fill: board solid `#F4F2EE` + `#111113` → app translucent (dead `primary`) — `TvPlayerStates.kt:32` + `components/TvAction.kt:23,33`.
- Rail animation: board 10s linear full-width drain → app 200ms tween per position tick — `TvPlayerStates.kt:26-27`.

## Tv3PlayerAudio → features/player/TvPlaybackTrackPicker.kt

MATCHES
- Sheet: 1920×540 bottom, r24 top corners, bg `rgba(22,22,24,0.9)` + 30px blur, slide-up .3s; three 520px columns at x76/676/1276, top 48; headers 24/600 `#B6B4AF` indented 20; items 64h r12 pad 20 gap 14, 24/500 `#DAD8D3`, selected 600 `#F4F2EE` with 24px check in a leading slot; column labels Audio / Subtitles / Subtitle size with sizes Small…Extra large — `TvPlaybackTrackPicker.kt:33-56,63-75`.

DIFFERS
- Column header→list gap: board 8px (column gap 6 + margin 8) → app 14px — `TvPlaybackTrackPicker.kt:64-66`.
- Selected row weight: board 600 → app SemiBold (600) ✓; unselected 500 vs app Medium (500) ✓ — no delta; check icon stroke 2.6 board vs Lucide default (fine).
- "Off" row in Subtitles ✓ present. No user-visible deltas beyond the header gap.
