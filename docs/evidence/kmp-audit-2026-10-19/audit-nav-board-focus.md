# TV focus visual spec audit — TVU-16/17 (board vs `features/*/Tv*.kt` + `components/*`)

**Read-only audit.** No files were edited, no builds were run. Everything below was read from the
live tree `app/composeApp/src/commonMain/kotlin/org/viptv/app` (untracked/mid-edit files included)
on 2026-10-19 ~08:44 EDT, HEAD `ffebb7d`. The working tree is being edited concurrently — notably
`components/TvFocusTreatment.kt` gained `width = 4.dp` / `darkGap` between two reads during this
audit, so line numbers are cited as found and may drift again. Stale `app/artifacts/**`
source snapshots were excluded.

Token map: `Palette.text = #F4F2EE` (theme/Theme.kt:15), `Palette.background = #0B0B0C`
(theme/Theme.kt:12), `Palette.raised = #212124` (theme/Theme.kt:14).

---

## 1. Board reference values (extracted from the HTML boards)

| Element family | Board frame(s) | Focused value (exact) |
|---|---|---|
| Controls — settings opts, sign-in choices | CSS `.opt.focus` in **every** Tv3 board (e.g. `Tv3SetDisplay.html`) | `background:#F4F2EE; color:#111113; box-shadow: 0 0 0 4px #FFFFFF, 0 24px 60px rgba(0,0,0,.65)` |
| Media cards (landscape/portrait, live tiles) | `Tv3CardStates`, `Tv3Movies`, `Tv3Browse`, `Tv3Episodes`, `Tv3MyList`, `Tv3RowStates`, `Tv3HomeToRows` | inset overlay: `box-shadow: inset 0 0 0 3px #FFFFFF, inset 0 0 0 5px rgba(0,0,0,0.35)` (3px white ring + 2px dark band inside it; no fill change, no size change) |
| Profile tile | `Tv3Profiles` | `inset 0 0 0 4px #FFFFFF, inset 0 0 0 5px rgba(0,0,0,0.35)` |
| Text-entry fields | `Tv3SignInEmail`, `Tv3SignInPassword`, `Tv3SignInCode`, `Tv3Pin` | focused: `inset 0 0 0 2px #F4F2EE`; rest: `inset 0 0 0 1px rgba(255,255,255,0.14)`; bg `rgba(255,255,255,0.08)` |
| Filter chips / filter rows / filter text input | `Tv3MoviesFilters`, `Tv3MoviesFilterText`, `Tv3SourcesViptv`, `Tv3AddonEntry` | `inset 0 0 0 3px #FFFFFF`, bg `rgba(255,255,255,0.07)`–`0.08` unchanged |
| Color swatch (Display/Subtitles) | `Tv3SetDisplay` (44px #F5C542), `Tv3SetSubs` (52px #FFFFFF) | `0 0 0 3px #0B0B0C, 0 0 0 6px #F4F2EE` (outer: 3px dark, then 6px light) |
| Top-nav avatar (rest-focus marker) | `Tv3Home`, `Tv3Movies`, `Tv3Live`, `Tv3MyList`, `Tv3Search`, +Loading/Error variants | `0 0 0 2px rgba(255,255,255,0.35)` on the 56px circle |
| Player scrub thumb | `Tv3PlayerControls`, `Tv3PlayerScrub` | 28px `#FFFFFF` circle + `0 0 0 8px rgba(255,255,255,0.25)` |
| Keyboard key | `Tv3Search` ("h"), `Tv3SignInEmail` | fill flip only: `background:#F4F2EE; color:#111113` (no ring in the frame) |
| Source row | `Tv3Sources` | fill flip only: `height:104px; border-radius:14px; background:#F4F2EE; color:#111113` (frame shows no ring; the control rule in UI_INVENTORY.md §3 still specifies the 4px outline + soft shadow) |
| Season chip (selected) | `Tv3Episodes` | `background: rgba(255,255,255,0.16); color:#F4F2EE` (no focused-chip frame exists) |
| Error field | `Tv3SourcesM3u` | `inset 0 0 0 3px #E5484D` on `rgba(255,255,255,0.07)`; normal field `inset 0 0 0 1.5px rgba(255,255,255,0.14)` |

`app/docs/UI_INVENTORY.md:61` states the rule this audit applied: *"the board's focused row is
`#F4F2EE` fill with a `4px` white outline and a large soft shadow — not a thin outline"*, and
cards use the inset ring (user-stated rule; board values above).

---

## 2. Findings

### A. Systemic — shared helpers

**A1. Card ring color is `#F4F2EE`, not white, and the second (dark) inset ring is missing — every card in the app.**
`components/FocusOutline.kt:20` draws the focus ring with `Palette.text` (`#F4F2EE`).
Board: `inset 0 0 0 3px #FFFFFF, inset 0 0 0 5px rgba(0,0,0,0.35)` (Tv3CardStates overlay).
Width (3dp) and inset direction are correct; color is off and the 2px dark inner band is absent.
Also fades in over 120 ms (`FocusOutline.kt:17`) where the board ring is static.
Every card site in §D inherits this deviation.

**A2. The board-conformant card helper exists but has zero callers.**
`components/TvFocusTreatment.kt:48` `tvCardFocusOutline` (white 3dp inset ring) — unused by any
file. `TvFocusSurface` (`TvFocusTreatment.kt:63`) is likewise unused. All cards still route
through the old `FocusOutline` (A1).

**A3. Control outline helper — currently conforms, with two small deltas.**
`components/TvFocusTreatment.kt:29-45` `tvFocusOutline` now draws the white ring at `width = 4.dp`
outside the bounds (TVU-16 comment present). Deltas vs `.opt.focus`:
- the optional dark gap ring uses `Color.Black.copy(alpha = .65f)` (line 34) instead of the
  board's solid `#0B0B0C`;
- the soft shadow `0 24px 60px rgba(0,0,0,.65)` is left to each caller (only `TvAction`,
  `TvSettingsRow`, `TvHistoryScreen` set a 24dp elevation).

### B. Controls that fill-flip but draw no ring / no shadow (rule: 4px `#FFFFFF` outline + soft shadow)

**B1. `features/sources/TvSourceRow.kt:29`** — source row flips to `Palette.text` (#F4F2EE ✓,
ink `Palette.background` ✓, height/radius match the board) but draws **no 4px white outline and
no soft shadow**. Board: Tv3Sources focused row = `background:#F4F2EE; color:#111113`; the
control rule (`.opt.focus`, UI_INVENTORY.md:61) adds `0 0 0 4px #FFFFFF, 0 24px 60px
rgba(0,0,0,.65)`.

**B2. `features/details/TvSeasonSelector.kt:42`** — season chip flips to `Palette.text` with
`Palette.background` ink, **no outline, no shadow**. Board: Tv3Episodes shows the *selected* chip
as `rgba(255,255,255,0.16)`; the focused-control rule requires the 4px white outline + shadow
that `.opt.focus` uses everywhere else.

**B3. `features/live/LiveProgramCell.kt:24-26`** — EPG program cell flips to `Palette.text`
(ink `#111113` on TV ✓) but **no ring, no shadow** (and the progress overlay re-tints at line 29).
No TV-Live guide board frame exists for the focused cell; the generic control rule applies.

### C. Controls with wrong fill or no focus visual at all

**C1. `components/TvNavigation.kt:45`** — focused top-nav label fills `Palette.raised`
(**#212124**, not `#F4F2EE`) with **no ring and no shadow**. Board rule: `#F4F2EE` fill +
`0 0 0 4px #FFFFFF` + soft shadow. (No board frame shows a focused nav label; the closest nav
focus marker is the avatar ring `0 0 0 2px rgba(255,255,255,0.35)` — Tv3Home.)

**C2. `components/TvNavigation.kt:38` and `:57`** — Search icon and profile-menu `IconButton`
(56dp circles) draw **no focus visual at all** (plain Material `IconButton`, ripple only).
Board: focused 56px avatar = `0 0 0 2px rgba(255,255,255,0.35)` (Tv3Home/Movies/Live/MyList/Search).

**C3. `features/settings/TvSubtitleColor.kt:23`** — subtitle color swatch draws an **inset**
3dp `Palette.text` border, and draws it for `focused || selected`. Board swatch focus
(Tv3SetDisplay/Tv3SetSubs): an **outer** double ring `0 0 0 3px #0B0B0C, 0 0 0 6px #F4F2EE`.
Wrong direction (inset vs outside), missing the 3px dark ring, light ring 3dp vs 6px, and
the same ring marks the unfocused *selected* state.

**C4. `features/browse/DiscoveryFilters.kt:38` → `features/browse/DiscoveryTextFilter.kt:28`** —
TV text/number/date facet filters render a Material `OutlinedTextField` (default Material focus
border colors, 180dp wide) — **no TV focus treatment**. Board: Tv3MoviesFilterText focused input
= `inset 0 0 0 3px #FFFFFF` on `rgba(255,255,255,0.07)`, 1024×80 r14.

**C5. `features/browse/DiscoveryFilters.kt:39`** — TV toggle facet renders a Material
`FilterChip` (Material colors + ripple) — **no TV focus ring**. Board: Tv3MoviesFilters focused
chip = `inset 0 0 0 3px #FFFFFF` on `rgba(255,255,255,0.08)` (64px, r32).

### D. Cards with partial or inverted rings

**D1. `features/home/TvCatalogCard.kt:196`** (`CardFocusRing`) — `FocusOutline(..., 3.dp * scale)`:
inset 3dp ring in `#F4F2EE` (A1 color) and **no dark inner band**. Board: Tv3CardStates /
Tv3Movies / Tv3RowStates `inset 0 0 0 3px #FFFFFF, inset 0 0 0 5px rgba(0,0,0,0.35)`.
Affects home shelves (`features/home/TvHomeShelf.kt:42`), the browse grid
(`features/browse/TvBrowseScreen.kt:79`) and the library grid
(`features/library/TvLibraryScreen.kt:59`). No size/scale change ✓.

**D2. `features/details/TvEpisodeCard.kt:43`** — same `FocusOutline` deviation for episode cards
(radius 12dp, 3dp width). Board: Tv3Episodes focused episode card ring = same inset 3px white +
5px dark as cards.

**D3. `features/home/MediaCard.kt:51`** (defaults at `:31`: `cornerRadius = 8.dp`,
`focusWidth = 3.dp`) — same `FocusOutline` deviation. TV call sites:
`features/search/TvSearchScreen.kt:98-99` (focusWidth 3dp, cornerRadius 12dp),
`features/details/TvRecommendationShelf.kt:33`, `features/player/TvPlayerEpisodePicker.kt:32`,
and `features/home/HomeCatalogRow.kt:31` (`showFocusOutline = television`).

**D4. `features/search/TvSearchChannel.kt:51`** — own ring: `border(3.dp * scale,
Color.White.copy(alpha = frame))` — white 3dp inset ✓, **missing the 2px dark inner band**;
alpha-animated and conditionally composed (`if (frame > 0f)` adds/removes a node on focus).
Board: Tv3CardStates live-channel tile uses the standard card ring.

**D5. `features/live/TvLivePreview.kt:68`** — own ring: `border(3.dp * scale, Color.White, shape)`
— white 3dp inset ✓, **missing the dark inner band**; conditionally composed (`if (focused)`).
Board: card family ring (Tv3CardStates); no dedicated Live-preview focus frame.

**D6. `features/entry/TvProfileItem.kt:33-34`** — profile tile focus stacks
`.border(4.dp, White α=frame)` **then** `.border(5.dp, Black .35·frame)`. Compose paints the
*inner* (later) border on top, so the edge band renders black-over-white for 0..4dp instead of
the board's white 0..4 with the dark band at 4..5. Board: Tv3Profiles
`inset 0 0 0 4px #FFFFFF, inset 0 0 0 5px rgba(0,0,0,0.35)` (white outermost, dark 1px inside).
No size change ✓ (width 4dp matches once layering is fixed).

### E. Field-family findings

**E1. `features/entry/TvEmailSignIn.kt:50`** — email field draws `border(2.dp * scale,
Palette.text)` **unconditionally** (the field itself is never focusable; the on-screen keyboard
holds focus). It matches the board's *focused* value (`inset 0 0 0 2px #F4F2EE`,
Tv3SignInEmail) but never shows the unfocused state (`1px rgba(255,255,255,0.14)` family).

**E2. `features/entry/TvEntryField.kt:28`** — conforms: 2dp `Palette.text` inset when active,
1dp white 14% otherwise (board Tv3SignInEmail/Password: `2px #F4F2EE` focused /
`1px rgba(255,255,255,0.14)` rest). Nuance: the ring keys on `active` (typing target), not
focus — it persists while focus sits on the keyboard, whereas the board ties the ring to the
focused field.

**E3. `features/settings/TvSourceIntegrationForm.kt:82`** — same conforming pattern as E2.
Two deltas vs the Sources boards: unfocused border 1dp vs board `1.5px`; and the error state
draws only red text (line 133) — the board error field is `inset 0 0 0 3px #E5484D`
(Tv3SourcesM3u), so no error ring is drawn at all.

**E4. `features/settings/TvSettingsSidebar.kt:37`** — `tvFocusOutline(..., width = 3.dp,
darkGap = 3.dp)` renders 3px 65%-black + 3px **white**. The comment cites the swatch board
`0 0 0 3px #0B0B0C, 0 0 0 6px #F4F2EE`: the light ring should be 6px `#F4F2EE` (not 3px white)
and the dark ring solid `#0B0B0C` (not 65% black). No board frame shows a focused sidebar row,
so this is judged only against the swatch spec it cites (low severity). Fill flip to
`Palette.text` ✓.

---

## 3. Conforming (no action needed)

- **`components/TvAction.kt:39`** — fill flip to `Palette.text`, 4px white outline outside the
  bounds (via the fixed `tvFocusOutline`), 24dp shadow on focus. Conforms to `.opt.focus`.
  This covers ≈40 control sites, including: hero buttons (`features/home/TvHero.kt:41,46,47`,
  `features/details/TvDetailHero.kt:61,77-79`), settings rows
  (`features/settings/TvSettingsRow.kt:52` + every Tv*Settings page), dialogs
  (`features/settings/TvSettingsDialogs.kt:36,40,61`), account actions
  (`features/settings/TvSettingsAccount.kt:34`), source-integration forms
  (`features/settings/TvSourceIntegrationForm.kt:135,142,164,246,253,260`), sources filters
  (`features/sources/TvSourcesFilters.kt:26,43`), live filters/labels
  (`features/live/TvLiveFilters.kt:24,32`, `features/live/TvLiveChannelLabel.kt:22`),
  calendar (`features/calendar/TvCalendarRow.kt:23`, `TvCalendarPreview.kt:32`), search
  suggestions (`features/search/TvSearchScreen.kt:73`), library controls
  (`features/library/TvLibraryControls.kt:27,36`), browse genre chips
  (`features/browse/TvBrowseScreen.kt:63`), player controls/pickers
  (`features/player/TvPlayerControls.kt:49`, `TvPlaybackTrackPicker.kt:73`,
  `TvNextEpisode.kt:50,54`), PIN keys (`features/entry/TvPinKey.kt:18`), entry keyboards
  (`features/entry/TvEntryKeyboard.kt:53`), sign-in choices (`features/entry/TvSignInChoice.kt:53`),
  profiles "Manage" (`features/entry/TvProfiles.kt:66`), welcome/splash/pairing screens.
- **`features/history/TvHistoryScreen.kt:115`** — fill flip + 4px outline + 24dp shadow
  (no TV History board; generic rule followed).
- **Player scrub thumb** — `features/player/TvPlaybackTimeline.kt:34-36`: 28dp white circle with
  an 8dp 25%-white halo, always composed; matches both scrubber frames.
- **`features/entry/TvEmailCode.kt:54-56`** — active code box 2dp `Palette.text`, others 1dp
  white 14%; matches Tv3SignInCode (`2px #F4F2EE` / `1px rgba(255,255,255,0.14)`).
- **Keyboard keys**: board frames (Tv3Search "h", Tv3SignInEmail) show fill-flip only; TvAction
  keys additionally draw the 4px ring + shadow (more than those frames, consistent with the
  general control rule — observation only).

## 4. Shape-change check (TVU-17)

No focus-driven shape/size/scale change exists anywhere in `features/*/Tv*.kt` or `components/*`
(no `graphicsLayer` scale or animated dimensions keyed to focus; the only focus animations are
color/alpha: `TvAction.kt:33-34`, `FocusOutline.kt:17`, `TvProfileItem.kt:28`,
`TvSearchChannel.kt:35`). Two sites compose the ring node conditionally
(`TvLivePreview.kt:68`, `TvSearchChannel.kt:51`), which can churn layout on focus but does not
change the control's own size.

## 5. Adjacent notes (no board / out of strict scope)

- `DropdownMenu`/`DropdownMenuItem` popups (`components/TvNavigation.kt:61-63`,
  `features/library/TvLibraryControls.kt:42-45`) use Material styling with no TV focus ring;
  no board shows an open menu on TV.
- `TvEmailCode` digit boxes mark the *active* slot rather than focus (keyboard owns focus), same
  semantic nuance as E2.
- Geometry (non-focus): MediaCard default `cornerRadius = 8.dp` vs board card radius 10
  (Tv3CardStates), TvSearchScreen passes 12dp — noted in passing only.
