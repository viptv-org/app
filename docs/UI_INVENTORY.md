# UI inventory — what we have, what the design has, what we still need

Source of truth for boards:
`design/viptv-design-system/reference/screens/{html,img}/<platform>/<id>`.
Board counts at this export (`reference/screens/index.json`, 273 entries): TV 56,
phone 69, tablet 18, desktop 55, web 40, `simkl` 26 = **264 screen boards**, plus 9
`components` sheets (`CmpPhone1-3`, `CmpDesk1-3`, `CmpTv1-3`). The 26 `simkl` boards
are the ones this inventory used to omit; "264-board export" elsewhere in the docs
counts exactly those screen boards.

## 1. App screens vs. boards

App screens (`Screen` enum): Connect, RecoveryCodes, Profiles, ProfilePin, ProfileEdit,
Home, Discover, Movies, Series, Anime, Calendar, Search, Details, Sources, Player, Live,
Library, History, Settings.

| App screen | TV board | Phone | Desktop | Notes |
|---|---|---|---|---|
| Home | `Tv3Home`, `Tv3HomeLoading`, `Tv3HomeToRows` | `M3Home*` | `D3Home*` (+ `HomeWin/Mac/Ultra/Narrow/Compact/Hover`) | |
| Movies / Series / Anime | `Tv3Movies*`, `Tv3Browse` | `M3Browse*` | `D3Browse*` | catalogs, filters, empty/error/loading all exist |
| Search | `Tv3Search` | `M3Search*` | `D3Search*` | |
| Details / Title | `Tv3Title`, `Tv3Movie`, `Tv3Episodes` | `M3Title*` | `D3Title*`, `D3Movie` | |
| Sources sheet | `Tv3Sources*` | `M3Sources*` | `D3Sources*` | |
| Player | `Tv3Player*` | `M3Player*` | `D3Player*` | |
| Live | `Tv3Live` | `M3Live` | `D3Live` | |
| My List (Library) | `Tv3MyList`, `M3MyListEmpty/Sort` | `M3MyList*` | `D3MyList` | |
| Settings sections | `Tv3Set{Playback,Subs,Addons,Sources,Display,Parental,About}` | `M3Set*` | `D3Set*` | |
| Profiles / Pin / Edit | `Tv3Profiles`, `Tv3Pin`, `Tv3ProfileEdit` | `M3Profiles*` | `D3Profiles*` | |
| Welcome / Sign-in | `Tv3Welcome`, `Tv3SignIn*`, `Tv3SignedIn`, `Tv3SignOut` | `M3SignIn*` | `D3SignIn*` | IPTV provider sign-in removed by owner |
| Recovery codes | **none on TV** | `M3Recovery` | `D3Recovery` (`W3Recovery` on web) | app has `RecoveryCodesScreen` with no TV board |
| **Calendar** | `SkTvCalendar` (1920×1080) | `SkMCalendar` | **none** | `simkl` family: `SkCalMonth`, `SkCalAgenda`, `SkCalFilter`, `SkTvCalendar`, `SkMCalendar` |
| **History** | **none** | **none** | `D3History` | TV now has `TvHistoryScreen` (self-designed, no board) |
| **Settings → Account** | **none** | `M3Account` | `D3Account` | TV shows the generic fallback row |

So the screens that are genuinely **missing a board**: TV History
(implemented as `TvHistoryScreen`, self-designed), TV Recovery codes,
TV Settings→Account. TV Calendar is **not** missing a board — `SkTvCalendar` covers
it; it was read as missing while only the `tv`/`phone`/`tablet`/`desktop`/`web`
folders were counted. Everything else has a board somewhere.

## 1a. TV focus treatment (implemented 2026-10-19)

`components/TvFocusTreatment.kt` is the single implementation:
- content rows/buttons: filled `#F4F2EE`, 4 px white outline **outside** the bounds,
  24 dp soft shadow; drawn before `graphicsLayer`/`clip` so it is not clipped;
- sidebar: 3 px dark gap + 3 px white ring (board `0 0 0 3px #0B0B0C, 0 0 0 6px #F4F2EE`);
- cards: inset 3 px white ring (no fill/size change).

`TvSettingsRow`, `TvAction`, `TvSettingsSidebar`, the top-nav labels/search icon and
`TvHistoryRow` use it. `TvColumnFocus` clamps Up/Down inside a settings page.

## 2. Custom UI that is not in the design (the part you noticed)

Boards for the TV browse/list/search family deliberately have **no page heading**:
`Tv3Movies` starts with filter chips at `y=140` (`20px`), cards from `y=244`, and the
global top navigation above (nav labels `26px`, logo `32px w800`, content inset `96px`).
`Tv3MyList` and `Tv3Search` are the same.

The app instead draws its own titles at the top of those screens
(`TvBrowseScreen`, `TvSearchScreen` "Search"/"Top results", `TvLibraryScreen` titles,
plus per-screen empty-state headings). That extra heading is the custom UI that is not in
design, and it also pushes content down so the grids no longer match the board.

Fix direction: drop the in-page page titles on TV Movies/Series/Search/My List and let the
top navigation own the heading, with content starting at the board's inset. Where a
heading is genuinely needed (Settings, Details sections), use one shared header
component matching the Settings board: title `40px w700`, letter-spacing `-0.02em`,
subtitle `24px` in `#B6B4AF`, gap `8px`, `34px` to content.

## 3. Measurements the app should take from the boards (TV, 1920×1080)

- Content inset `96px` left/right; settings content at `x=640` with the sidebar `560` wide.
- Settings sidebar row: `440×72`, radius `12`, 24px padding, icon `28`, label `26px w500`;
  selected row `rgba(255,255,255,0.14)`.
- Section title `40px w700`, subtitle `24px #B6B4AF`.
- Cards: radius `16`, fill `rgba(255,255,255,0.06)`, icon tile `64` radius `14`.
- Buttons: height `60`, radius `12`, primary `#F4F2EE` on `#111113`, secondary
  `rgba(255,255,255,0.16)`, danger text `#FF8A80`.
- Focus ring: the board's focused row is `#F4F2EE` fill with a `4px` white outline and a
  large soft shadow — not a thin outline.
- Row/collection labels `30px w600`, shelf title height `40`.

## 4. Still to build (design work, in order)

1. **TV browse family to the boards**: catalogs switcher, facet filters
   (`Text/SingleChoice/MultiChoice/Toggle/Number/Date`), skeleton/empty/error, appending
   grids. Boards: `Tv3Movies*`, `Tv3Browse`.
2. **TV card/row states**: badges, no-art fallback, progress, certification chips
   (`Tv3CardStates`, `Tv3RowStates`, canvas dictionary §A1).
3. **Sources sheet states + add-on entry**: `Tv3SourcesEmpty`, `Tv3SourcesTorrent`,
   `Tv3AddonEntry`, `Tv3AddonInstall`.
4. **Parental content pages** (`Tv3SetParental`) and **About / Display**
   (`Tv3SetAbout`, `Tv3SetDisplay`).
5. **Recovery codes** (`M3Recovery`/`D3Recovery` → needs a TV board, or adapt).
6. **Missing boards**: TV History, TV Settings→Account (TV Calendar is covered by
   `SkTvCalendar`). The 26 `simkl` boards — calendar, filters, lists, stats, title
   tracking, connect/off — are not mapped to app screens anywhere in this inventory.
7. **Desktop chrome** (`D3HomeWin`): window rounded, title bar with back/forward chevrons,
   logo pill, centred search with the `Ctrl K` hint, right-side cast/mic/avatar and
   min/max/close, 1px window outline. This is what currently looks thrown together.
8. **Home polish** (`Tv3Home`): `FEATURED · 1 OF 5` label + pagination dots, "New episode"
   badge on Continue Watching, 4.5–5 cards visible, overflow menu on Continue Watching.

## 5. Platform notes

- **Torrent support** must work on every platform (the torrent panel copy differs when the
  viewer has no debrid account).
- **Desktop and mobile are local-playback only**: no gateway path may be reachable there.
- **Auth refresh + storage** must be correct on every KMP target (see
  `docs/KMP_COMPLETION_RECORD.md` for the current claim; needs an audit per platform).