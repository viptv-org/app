# TV Browse family audit — BROWSE-01..06 (read-only)

Repo: `/mnt/ALPH/code/viptv-org/app` · Boards: `design/viptv-design-system/reference/screens/{html,img}/tv/`
Audited: `Tv3Movies`, `Tv3MoviesCatalogs`, `Tv3MoviesFilters`, `Tv3MoviesFilterText`, `Tv3MoviesLoading`, `Tv3MoviesEmpty`, `Tv3MoviesError` (+ `Tv3MyList` for the segmented control, `reference/canvas-data-dictionary.md` §A3, `components.md` §2/§4).
Sources read (current working tree, no builds run, `app/artifacts/*` stale snapshots ignored):

- `app/composeApp/src/commonMain/kotlin/org/viptv/app/features/browse/TvBrowseScreen.kt`
- `.../features/browse/BrowseScreen.kt`
- `.../features/browse/DiscoveryFilters.kt`, `DiscoveryChoiceFilter.kt`, `DiscoveryMultiChoiceFilter.kt`, `DiscoveryMultiChoiceSheet.kt`, `DiscoveryTextFilter.kt`, `TvBrowseLoading.kt`
- `.../components/TvNavigation.kt`, `TvAction.kt`, `AutomaticPage.kt`, `TvLoadingShimmer.kt`
- `.../features/home/TvCatalogCard.kt`
- `.../state/ControllerBrowse.kt`, `.../connection/{RemoteDiscovery,BrowseQuerySelections,DiscoveryCatalogSelection}.kt`, `App.kt:85,95`

Working tree is mid-edit (`git status`: many modified files incl. `DiscoveryTextFilter.kt`); all citations are from the live tree as of this audit.

---

## Verdict table

| Required feature | Board | App status |
|---|---|---|
| Catalogs switcher (720px right sheet) | `Tv3MoviesCatalogs` | **Missing** — Material `DropdownMenu` on a value-only chip |
| Segmented type filters | dictionary §A3 + `.seg` (Tv3MyList) | **Missing** — type renders as a dropdown chip (`SINGLECHOICE`), never a segmented control |
| Sort control | `Tv3MoviesFilters` row 2 | **Missing on TV** — local sort unreachable (early return); server sort facet renders as a value-only dropdown chip |
| Facet sheet (Text/SingleChoice/MultiChoice/Toggle/Number/Date) | `Tv3MoviesFilters`, `Tv3MoviesFilterText` | **Structurally wrong** — right-side popup drawer, not the full-page filter screen; 5 of 6 kinds use the wrong TV control |
| Skeleton state | `Tv3MoviesLoading` | **Partial** — grid only (radius 12 vs 10), no chip skeletons |
| Empty state | `Tv3MoviesEmpty` | **Missing** — plain inline text, no icon/title/actions |
| Error state | `Tv3MoviesError` | **Missing** — inline red text, no actions |
| Appending grid | `Tv3Movies` (row-2 skeletons) | **Works** (AutomaticPage + skip), but no loading affordance while appending |
| No in-page page title | all boards | **Pass** (`TvBrowseScreen` draws no title; top nav owns it) |
| Content at 96px inset | all boards | **Pass on geometry** (96/140/244 column), with two caveats below |

---

## 1. BROWSE-01 `Tv3Movies` — catalog chip, filters chip, chips row, grid

Board geometry (board_spec): catalog chip `96,140 h64 r32 bg .16 blur24 font24 w700` with suffix `· VIPTV Sources` (w500, 70%) + chevron 24; Filters chip at `560,140` with icon + count badge `32×32 r16 bg .18 font18`; active-filter chips row `800→right96, top152 h40 r20 bg .1 font20 w600 #DAD8D3 gap10` with 85% fade mask; grid 7 columns `96..1816`, cards `232×348 r10`, pitch `384` (v-gap 36), focused card shows caption block (title 22 w600, meta 20 #B6B4AF, cert chip 26 r5) at `margin-top 14`; 3 trailing skeletons while appending.

App (`TvBrowseScreen.kt`):
- **Custom chip row not in any board**: when `filters == null` the app renders an "All genres" + genre chip `LazyRow` (`TvBrowseScreen.kt:54-66`) — the boards have no genre chip row at all. When `DiscoveryFilters` is supplied, every control becomes its own chip (`DiscoveryFilters.kt:25`) — again not the board's 2-chip layout.
- **Chip size deltas**: `TvAction` height `56dp` vs board 64 (`TvBrowseScreen.kt:63`, `DiscoveryChoiceFilter.kt:19`, `DiscoveryMultiChoiceFilter.kt:20`); corner radius `28` vs 32; text `SemiBold` vs 700. No `backdrop-filter: blur(24px)`.
- **No "Filters" entry chip with count badge** (board 560/140, badge 32/16/18). Nothing counts active filters in the UI.
- **No active-filter chips row** (board 800→1824, 40/20/r20/.1/#DAD8D3, fade mask). Current values live inside each control's own chip instead.
- **No caption under the focused grid card**: `TvCatalogCard(card, scale, true /*portrait*/, …)` leaves `focusedCaptions` at its default `false` (`TvBrowseScreen.kt:79`; gate at `TvCatalogCard.kt:70` `if (!portrait || focusedCaptions)`), so the focused card never shows the board's title/meta/cert block.
- Grid itself matches: `GridCells.Fixed(7)`, width `1720dp*scale` (96→1816, exactly the board's right edge), spacing 16/36, bottom padding 96 (`TvBrowseScreen.kt:76-78`); card 232×348 r10, 3dp focus ring, 34dp watched badge (`TvCatalogCard.kt:49-54,108`).
- **Vertical offset delta**: top bar is `132dp` (`TvNavigation.kt:27`) + `8dp` top padding (`TvBrowseScreen.kt:52`) → chips at y=140 ✔, but chips are 56 tall, so after the 40dp spacer the first card row starts at **y≈236, not the board's 244** (8px high; every row inherits).
- **Insets caveat**: the genre `LazyRow` honors `contentPadding end = 96` (`TvBrowseScreen.kt:61`) but the `DiscoveryFilters` `Row(horizontalScroll)` has **no end padding** (`DiscoveryFilters.kt:25`) — chips scroll to the window edge, breaking the 96px right inset.

## 2. BROWSE-02 `Tv3MoviesCatalogs` — catalogs switcher sheet

Board: right sheet `1200..1920 × 1080`, `#161618`, padding `72 96 54 64`, shadow `-40 0 100`, `t3-slidein .3s`, scrim `rgba(11,11,12,.6)`; title "Catalog" 44 w700; subtitle 24 #B6B4AF; group headers 22 w700 #8F8D89 (VIPTV Sources / [Add-on A] / [Add-on B]); rows `76 tall r14 pad 26 font26 w600` — selected `.1` fill + check 26, focused `#F4F2EE`/`#111113`, rest `.04`.

App: **no catalogs sheet exists.** The catalog control is a `SINGLECHOICE` chip that opens a Material `DropdownMenu` (`DiscoveryChoiceFilter.kt:19-31`); choices are catalog selection keys with display names (`DiscoveryFilters.kt:29-30`). Deltas: no sheet/drawer chrome (720 wide, slide-in, scrim), no grouping by provider/add-on, no group headers, no check mark, no 76px rows, dropdown uses default Material styling; chip label shows only the value ("Popular"), not "Popular · VIPTV Sources ⌄" — the provider suffix appears only when catalog names collide (`DiscoveryCatalogSelection.kt:24-31`), and there is no chevron. Selection itself works (`ControllerBrowse.kt:6-14` → `forCatalog` resets extras/pagination).

## 3. BROWSE-03 `Tv3MoviesFilters` — facet sheet structure

Board: a **full page** (no top nav, no grid, no scrim): header "Filters" 52 w700 + "Popular · VIPTV Sources" 24 #B6B4AF at 96/96; left column of rows `640×80 r14 pad26 gap14` at x=96, y=220..864 (pitch 92): label 26 w700 + optional "Required" 18 w600 #8F8D89 + right-aligned value 22 #B6B4AF (max 300, ellipsized), active row bg `.16`; right facet panel x=800 w=1024: header 36 w700 + "2 of 3" 24 #B6B4AF, helper "Pick up to 3." 24, wrapped chips `64 tall r32 pad24 font24 w600 bg .08` (checked `#F4F2EE`/`#111113` + check 22; focus = inset 3px #FFF); footer at 800/940: "Show results" `340×72 r12 bg .16 blur font26 w700` + "Clear all" `220×72`.

App: **no such page.** Facets live in a `Popup` drawer: `DiscoveryMultiChoiceSheet` (`DiscoveryMultiChoiceSheet.kt:39-46`) — right-aligned, `800dp*scale` wide (board panel 1024; catalogs sheet 720), **full height**, scrim `.55`, slide animation; title 32sp (board 36); helper "Choose at least one · up to N" / "Select up to N" (board: "2 of 3" + "Pick up to 3."); choices are **checkbox rows** 64dp with `SquareCheck` icons (board: chip grid); footer buttons "Clear"/"Apply" `weight(1f)×64dp` (board: "Show results" 340 + "Clear all" 220). `maximum_selections` limiting works (draft capped, over-limit rows disabled — `DiscoveryMultiChoiceSheet.kt:32-35,60-63`), but there is no "N of M" count text and no removable applied-filter chips / "Clear all" on the results page.

**Apply semantics delta**: every change fires an immediate browse request (`ControllerBrowse.kt:20,27` → `loadBrowse`), and the board's model is deferred — "Results update when you choose Show results." (`Tv3MoviesFilterText` helper). The multi-choice sheet drafts locally then applies (`DiscoveryMultiChoiceSheet.kt:37-38`), but there is no single batched "Show results" step for the whole filter page.

Per-kind rendering vs dictionary §A3 (TV column):
- **MultiChoice** — dictionary: "full-screen options grid, '2 of 3'"; app: right popup drawer with checkbox list. Wrong surface, wrong control.
- **SingleChoice ≤ 4** — dictionary: segmented control; app: chip + `DropdownMenu` (`DiscoveryFilters.kt:37`, `DiscoveryChoiceFilter.kt:19-31`). Wrong.
- **SingleChoice > 4** — dictionary: option row; app: dropdown. Wrong.
- **Toggle** — dictionary: switch row (T 72×42); app: Material `FilterChip` (`DiscoveryFilters.kt:39`). Wrong; the chip shows only the label, not an On/Off value.
- **Number** — dictionary: stepper with min/max; app: plain `OutlinedTextField` (`DiscoveryFilters.kt:38`). Wrong.
- **Date** — dictionary: date picker; app: plain `OutlinedTextField`. Wrong.
- **Text** — see §4.
- **Required** — board: 18px #8F8D89 tag in the facet row, chip has no ×; app: no Required tag anywhere; required-ness surfaces only as a raw error string "Choose the required filters: X." (`ControllerBrowse.kt:46-49`) rendered as inline red text (`TvBrowseScreen.kt:72`) instead of the dictionary's empty state "Choose the required filters to browse this catalog."

## 4. BROWSE-04 `Tv3MoviesFilterText` — text/number/date entry

Board: label "Keywords" 24 w600 #DAD8D3 at 800/220; field `1024×80 r14 bg .07` with **inset 3px #FFF focus ring**, text 28 w500, accent caret `3×34 #F5C542` (pulse); **on-screen keyboard** at 800/380: keys `94×66 r10 bg .09 font26 w600` (rows `0-9`, `a-j`, `k-t`, `u-z - _ . :`, last row Capitals 196 / Space 400 / Delete 196 / **Clear** 196 bg .18), focused key inverts to `#F4F2EE`/`#111113`; helper 22 #8F8D89; footer Show results / Clear all.

App (`DiscoveryTextFilter.kt:26-31`): a **desktop-style `OutlinedTextField` `180dp` wide** (width not even multiplied by `scale`, unlike every other TV metric) with a floating label and a "Apply" `TextButton` that appears only when dirty; apply fires on IME Done. Deltas: no 1024-wide field, no inset focus ring, no accent caret, **no on-screen keyboard at all** (D-pad-only TVs cannot realistically type), no Clear key, no helper copy, no "Show results". `NUMBER` and `DATE` controls take this same raw text field (`DiscoveryFilters.kt:38`) — no stepper, no date picker, no min/max display.

## 5. BROWSE-05 `Tv3MoviesLoading` — skeleton

Board: top nav + `role="status" "Loading Movies"` + skeleton **chips** `440×64 @96,140` and `200×64 @560,140` (r32) + 14 card skeletons `232×348 r10` in the 7-column grid (rows at y=244/628).

App (`TvBrowseLoading.kt:17-20`, gated at `TvBrowseScreen.kt:73`): 14 card skeletons 232×348, 7 columns, shimmer 1.4s ✔ — but corner radius **12** vs board **10**; **no chip skeletons** (the real chip row/genre row renders above); semantics say "Loading catalog" vs board "Loading Movies"; because real 56px chips render instead of 64px skeletons, the skeleton grid starts at y≈236 vs board 244.

## 6. BROWSE-06 `Tv3MoviesEmpty` / `Tv3MoviesError`

Board empty: centered block at y=420 (full width): 96×96 r48 icon circle bg .1 with 46px search-off icon; h1 "Nothing matches these filters" 44 w700; body 26 #B6B4AF max-w 980 ("Popular from VIPTV Sources has no movies with all 8 filters. Removing the newest one usually helps."); actions gap 16: primary `470×72` "Remove 'After 1 Jan 2021'" (#F4F2EE/#111113, shadow 0 18 50 .45) + secondary `300×72` "Clear all filters" (.16 + blur). Filter chips row stays visible.

Board error: same centered structure — "Couldn't load Popular" 44, "VIPTV Sources didn't answer in time. Your filters are kept." 26, primary `260×72` "Try again" (+refresh icon 28), secondary `400×72` "Choose another catalog".

App: empty = one inline `Text("No titles are available in this catalog.")` 24sp secondary inside the left column (`TvBrowseScreen.kt:74`); error = one inline red `Text(error)` above the grid (`TvBrowseScreen.kt:72`). **Missing in both**: the centered layout/geometry, the 96px icon, the 44px heading, the 26px body copy, and all actions (remove-newest-filter, clear-all, try-again, choose-another-catalog). No remove-newest logic exists anywhere in the codebase; retry/catalog-switch affordances don't exist on this screen (the only retry paths are elsewhere, e.g. Home refresh).

## 7. Appending grid

Works: `AutomaticPage(selected.size, busy, more)` requests the next page when the last visible index reaches `count-12` (`AutomaticPage.kt:8-17`, `TvBrowseScreen.kt:48`), `browseMore` resumes with `skip` and appends with dedupe (`ControllerBrowse.kt:29-31,58-61`), grid keys are stable (`cardLookupKey`). Delta: while a page loads (`busy && selected.isNotEmpty()`) **nothing is rendered** — the board shows 3 trailing skeleton cards in the filling row plus a "Loading more" status (`Tv3Movies` HTML); also `error?.let` can print mid-grid while content is showing.

## 8. Sort control

- **Local sort** ("Popular"/"A–Z"/"Z–A", client-side, `BrowseScreen.kt:29,87-96`) is unreachable on TV: the TV branch returns before `SortMenu` renders (`BrowseScreen.kt:42-45` vs `:62,69`). The cards are still pre-sorted by this dead state.
- **Server sort facet** (board facet row "Sort | Required | Popularity", 640×80 at 96/312): if a catalog publishes a `sort` control, it renders as a generic `SINGLECHOICE` dropdown chip whose label is just the current value ("Popularity") with no "Sort" label, no "Required" tag, no facet row (`DiscoveryFilters.kt:33-37`, `DiscoveryChoiceFilter.kt:16-21`). Value round-trips through `extras` and does re-query (`ControllerBrowse.kt:15-18`).

## 9. Segmented type filters

Dictionary §A3: TV `SingleChoice ≤ 4 choices` → **segmented control**; the family's shared board CSS defines `.seg` (track `#2A2A2E`, 3×168 columns, height 64, r32, thumb 168×52 r26 `#0B0B0C`, labels 24 w700) and `Tv3MyList` renders it (`All/Movies/Series`, `role=radiogroup`). The app has **no segmented control anywhere in the browse family**: the `type` control is a `SINGLECHOICE` dropdown chip, shown only on `Screen.Discover` (`DiscoveryFilters.kt:24,37`; `BrowseScreen.kt:43-45` passes `screen == Screen.Discover`). On TV Movies/Series/Anime the type is fixed by nav, which matches the boards, but wherever a type control does appear it is the wrong control kind.

## 10. Passes

- **No in-page page title**: `TvBrowseScreen` draws no heading; `TopNavigation` renders for TV on Movies/Series/Discover/Anime (`App.kt:85`) and owns the heading, exactly as `UI_INVENTORY.md` §2 prescribes. (The non-TV `Text(screen.name)` at `BrowseScreen.kt:54` is after the TV return, so it never affects TV.)
- **96px inset / column geometry**: left inset 96 (`TvBrowseScreen.kt:52`), chips y=140 (132 nav + 8), grid 96→1816 with 7×232+6×16, top-40 spacer, bottom 96. Matches the boards except the 8px chip-height drift (§1) and the missing end-inset on the `DiscoveryFilters` row (§1).
- **Filter data plumbing is correct** (functional, though with the wrong affordances): type/catalog/genre/search/extras/values all round-trip into `BrowseQuery` and re-query (`ControllerBrowse.kt:6-27`, `BrowseQuerySelections.kt`), per-catalog controls merge with provider controls (`DiscoveryFilters.kt:16-17`), default-catalog and required checks exist (`DiscoveryCatalogSelection.kt:17-30`).

---

## Missing states (summary)

1. Catalogs switcher sheet (`Tv3MoviesCatalogs`) — entirely absent; only a Material dropdown.
2. Facet filter page (left list + right panel + Show results/Clear all) — absent; replaced by an inline chip row + popup drawer.
3. On-screen keyboard for Text facets (`Tv3MoviesFilterText`) — absent.
4. Toggle switch row, Number stepper, Date picker, segmented SingleChoice — all absent (all rendered as chips/fields/dropdowns).
5. "Required" tag and required-filter empty state copy — absent (raw red error string instead).
6. Active-filter chips row + removable chips + Clear-all on the results page — absent.
7. Empty state block (icon/heading/body/Remove-newest/Clear-all actions) — absent.
8. Error state block (icon/heading/body/Try again/Choose another catalog) — absent.
9. Chip skeletons + "Loading Movies" status in the loading state; card-skeleton radius 12→10.
10. Appending affordance (trailing row skeletons / "Loading more") — absent.
11. Focused-card caption block inside the browse grid — absent (`focusedCaptions=false`).
12. Filters chip with active-count badge — absent.

## Exact deltas (implementation → board)

- Chips: 56dp/28dp/w600 → 64px/32px/w700; no blur(24).
- First grid row: y≈236 → 244 (8px), caused by the 56dp chips.
- Skeleton card radius 12 → 10; no 440×64 / 200×64 chip skeletons.
- Multi-choice surface: right popup drawer 800dp×full height, scrim .55, checkbox rows, Clear/Apply → full page, left 640×80 rows + right 1024 panel, chip grid 64/32, "2 of 3", Show results 340×72 / Clear all 220×72.
- Text field: 180dp OutlinedTextField + Apply TextButton → 1024×80 field (bg .07, inset 3px ring, 28px, accent caret) + keyboard grid (94×66 keys) + Clear key.
- Catalog picker: DropdownMenu → 720×1080 sheet at x=1200 (#161618, slide-in, scrim .6, 76px rows, groups, check marks).
- Filter changes: immediate re-query per change → deferred until "Show results".
