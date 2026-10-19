# Phone / tablet / web audit — NAV-21..26, PHUI-01..06

- Repo: `/mnt/ALPH/code/viptv-org` (app = `/mnt/ALPH/code/viptv-org/app`), branch `experiment/kmp-v2`, HEAD `ffebb7d` (issue list) **with concurrent working-tree edits** (mostly TV-side: `TvBrowseScreen`, `TvSettings*`, `App.kt` History dispatch). All file:line below were re-verified against the live tree at 2026-10-19 ~08:55 EDT.
- Method: read-only. Source reading + design boards via `python3 tools/board_spec.py <BoardId>` (`app/tools/board_spec.py`) against `design/viptv-design-system/reference/screens/{html,img}/<platform>/<id>`. No builds, no adb, no emulator, no repo edits.
- Board inventory at this export: phone 69 (`M3*`), tablet 18 (`T3*`), web 40 (`W3*`) — matches `app/docs/UI_INVENTORY.md`.
- Important provenance caveat: the "phone" audit that produced these issues (`/tmp/viptv-ui-audit-phone.md`, run at `8e0ee9c`) drove a **pixel_7 AVD at 1920×1080** (`~/.android/avd/viptv-native-qa.avd/config.ini`: `hw.device.name=pixel_7`, `hw.lcd.density=420` → ~731dp wide). That renders the **tablet branch** (`App.kt:63` `tablet = !compact && !television && !browser && !desktopChrome`), not the compact phone layout. Several "Phone:" symptoms are therefore tablet-layout symptoms; both paths are checked below.
- Issue text source: `app/docs/ISSUES.md` §2 (NAV-21..26), §5 (PHUI-01..06); phone bug list cross-checked with `app/docs/OPEN_TASKS.md` ("Phone" section) and `/tmp/viptv-ui-audit-phone.md`.

## Verdict table

| ID | Issue (short) | Status |
|---|---|---|
| NAV-21 | D-pad Down from title jumps into nav rail; rail not opaque | **partial** — jump structurally impossible now; phone nav background still off-board |
| NAV-22 | Profile screens: no visible Back | **partial** — edit + PIN have back; profile picker does not; zero `BackHandler` in the app |
| NAV-23 | Edit-profile form left-hugs, narrower field | **partial** — fixed on compact; symptom persists in the ≥640dp branch where it was observed |
| NAV-24 | Live "no guide" above chips, channel-specific wording | **broken** — still rendered as a top overlay above the chips with "…for this channel" copy |
| NAV-25 | Discover error uses gamepad wording on touch | **fixed in app copy** (partial overall) — string gone from the KMP app; server message still passed through verbatim, no board error actions |
| NAV-26 | Search "no results" vs "source failure" collapsed | **fixed** — distinct per-provider states since the row rewrite |
| NAV-09 | My List shelf/grid clipping | **partial** — phone grid has 96dp bottom padding; shelf still has no edge peek |
| NAV-10 | Home hero can render with no artwork | **broken** — no art-less fallback or skip on phone/tablet/desktop heroes |
| NAV-11 | Duplicate titles in a Home shelf | **partial** — identity-level dedupe only; same-title distinct-id duplicates still pass |
| NAV-04 (related) | Title/details can render completely blank; inconsistent error wording | **broken on the failure path** — initial-load error lands in `state.error`, which the Details screen never receives |

---

## 1. Reported phone bugs, verified against code

### NAV-21 — D-pad Down from title jumps into the nav rail; expanded rail has no opaque background — **partial**

- No nav rail is composed on the title screen in either layout:
  - phone/compact: bottom nav excluded for `Details/Sources/Search/Settings` — `App.kt:123-124`;
  - tablet: rail excluded for `Details/Sources` — `App.kt:89`;
  - TV title screen: focus is explicitly contained (`down = null`, `downAction`) — `features/details/TvDetailsScreen.kt:94-95`.
  So the reported focus jump cannot occur as described in the current tree; the symptom also does not match any current code path (the tablet rail is a fixed 92dp always-labeled column, `components/TabletNavigation.kt:21-23` — there is no "expand" state).
- The residual, verifiable defect is the rail background vs the boards:
  - tablet rail: opaque `Palette.background` (`TabletNavigation.kt:23`; `theme/Theme.kt:12` `0xFF0B0B0C`) vs board `T3Home` nav: `width=92px, background=rgba(11,11,12,0.55), backdrop-filter=blur(24px)`;
  - phone bottom nav pill: `Color(0xFF292827).copy(alpha = .94f)` with a 10%-white border, no blur/shadow (`components/MobileNavigation.kt:27-28`) vs board `M3Home` nav: `background=rgba(30,30,33,0.72), backdrop-filter=blur(24px) saturate(1.4)`, `box-shadow=0 10px 30px rgba(0,0,0,0.45)`, `inset 0 0 0 1px rgba(255,255,255,0.08)`.
- AC "focus containment per board": met by construction (no rail on the title screen); AC on rail background treatment: **not met**.

### NAV-22 — Phone: profile screens have no visible Back affordance — **partial**

- Edit profile (compact): has Back chevron + "Done" — `features/entry/MobileProfileEdit.kt:37` and `:41`. Fixed.
- Profile PIN (compact): has an X "Back" icon button — `features/entry/MobilePinScreen.kt:43`. Fixed.
- Profile picker (compact): header row contains only `Edit`/`Done` (and only when `local`) — `features/entry/MobileProfiles.kt:36-41`; in remote mode the header row is empty. Still no Back/Cancel, exactly as audited.
- System Back is never intercepted: `BackHandler` / `OnBackPressed` appear **nowhere** in `composeApp/src` (grep across all source sets: 0 hits); `entryBackChanged` is wired only for TV entry screens and Player (`App.kt:38,44,60` usages). From the picker, hardware Back falls through to the default (exit).
- Note: the board `M3Profiles` header itself only has "Edit" (no back), so the picker matches the board — but the issue's AC ("back control present") is still unmet app-side for that screen.

### NAV-23 — Phone: edit-profile form left-hugs and uses a narrower field — **partial**

- Compact phone path is fixed: `MobileProfileEdit` is a full-width, 16dp-padded row list (`MobileProfileEdit.kt:35`) with centered `AlertDialog`s for name/PIN/avatar changes (NameDialog `MobileProfileEdit.kt:88+`), structurally matching board `M3ProfileEdit` (16px content padding, 14px-radius row groups, header with Done).
- The exact reported symptom persists in the non-compact branch, which is what the 731dp-wide audit device actually rendered: `features/entry/ProfileEditScreen.kt:33` (`Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center)`) with `OutlinedTextField(name, …)` at `:38` (and the PIN fields below) carrying **no width modifier** → intrinsic-width (~280dp) fields left-hugging a centered column on a wide screen.
- Content gaps vs board `M3ProfileEdit`: board rows are Name / Maturity rating / Profile lock / Playback (skip intro, next episode) / Language / Delete profile; the app has Name, Audio & subtitles, Maturity rating, Profile lock, Delete (`MobileProfileEdit.kt:44-49`) — no Playback or Language rows (cross-ref PHUI-02/03).

### NAV-24 — Phone: Live TV shows "no guide" above chips with channel-specific wording — **broken (still)**

- The guide-failure message is drawn as a **top-left overlay above the LiveScreen content**: `features/live/LiveRoute.kt:38` (`notice = … guide?.reason`), rendered at `LiveRoute.kt:47-50` (`Column(Modifier.padding(24.dp)) { Text(notice…) … "Retry" }`), stacked over `LiveScreen` which on phone begins with the category chips (`features/live/LiveScreen.kt:43` → `LiveFilters`, then `:44` → `LivePreview`). So the "no guide" text still appears above the chips.
- Wording is still channel-specific: `state/ControllerLive.kt:101` — `fetched.copy(reason = "No guide programs are available for this channel.")` (the audited string "No guide information. You can still watch this channel." is gone, but the replacement still assumes a selected channel).
- Competing empty states can still co-exist: the overlay plus LiveScreen's own empty row (`LiveScreen.kt:49` `emptyReason ?: "No channels are available for this profile."`).
- Partial mitigation only: when there are no channels at all the overlay is suppressed (`LiveRoute.kt:26-29` comment + `if (empty) null` at `:38`), so the double-message case is narrower than at audit time.
- AC "correct copy per state / single ordered empty state": **not met**.

### NAV-25 — Phone: Discover error uses gamepad wording on touch — **fixed in app copy; partial overall**

- The audited string `Couldn't load this catalog. Press OK to retry.` does **not** exist in the KMP app: grep over `app/composeApp/src` = 0 hits. It survives only in the legacy native app (`android/app/src/androidMain/kotlin/org/viptv/app/AppControllerDiscover.kt:49,176` — a different codebase) and old worktrees. Whatever produced it in that run was either that legacy app or a server-provided message.
- App-side Discover error copy is platform-neutral human text: `state/ControllerBrowse.kt:9` ("This catalog is no longer available. Choose another."), `:49` ("Configure a browsing integration for this profile."), `:57` ("Choose the required filters: …").
- Remaining gaps: (a) the phone browse screen renders whatever the server sends in `discovery.message` verbatim (`connection/RemoteDiscovery.kt` `FeatureAvailability.message`; displayed at `features/browse/BrowseScreen.kt:77`) with no local copy policy, so wording provenance is not controlled by the app; (b) the error row has no actions, while board `M3BrowseError` specifies `Try again` + `Choose another catalog`.
- AC "platform-appropriate copy": met for app-authored strings; unproven for server-carried strings, and the board's error-state actions are missing.

### NAV-26 — Phone: search "no results" and "source failure" collapsed into one message — **fixed**

- Distinct states exist now:
  - global no-results line is suppressed when any source failed: `features/search/SearchScreen.kt:54` — `if (!busy && term.isNotBlank() && !hasResults && error == null && groups.none { it.message != null }) Text("No matches. Try another search.", …)`;
  - per-provider state messages: `features/search/SearchGroups.kt:24-26` — `"Failed" → "This search provider could not respond. Try again."`, `"Loading"/"Pending" → "Searching…"`, `"Ready"+message → "Some search results are unavailable. Try again."`;
  - per-group empty copy: `"No matches."` (`SearchScreen.kt:62`);
  - whole-call failure surfaces separately as `error` (`SearchScreen.kt:52`).
- The audited collapsed sentence ("No results. Try another title. Some sources couldn't load.") no longer exists anywhere in the app (grep: 0 hits), and the double-space concatenation is gone.
- Cross-platform consistency: the same empty copy is used on TV (`features/search/TvSearchScreen.kt:118`).

### NAV-09 — My List shelf/grid clips the last card/bottom row — **partial**

- Phone grid: fixed — `features/library/LibraryScreen.kt:63-64` uses `GridCells.Fixed(3)` with `contentPadding = PaddingValues(bottom = if (phone) 96.dp else 24.dp)`, clearing the bottom nav; the empty state also reserves 96dp (`LibraryScreen.kt:44`).
- Shelf: not fixed — the home shelf `LazyRow` has no contentPadding/peek (`features/home/HomeCatalogRow.kt:50`). On a 390dp phone with 112dp cards + 12dp gaps the row fits exactly 3 cards, so no partial next card is visible — the boards deliberately show a cut card as the scroll affordance (`.vx-row{overflow:hidden}` with 118px portrait cards in `M3Home.html`). The TV shelf has the designed peek (`components/TvShelfScroll.kt:35` — "one card peeking on the leading edge and two on the trailing one") but the phone shelf does not.
- The audited bottom-row cut occurred on the ≥640dp tablet layout, where the grid bottom padding is 24dp (`LibraryScreen.kt:64`) and cards sit mid-fold at first render.
- AC "peek or padding at both edges": met at the bottom of the phone grid only; shelf edges unmet.

### NAV-10 — Home hero can render with no artwork (black hero, text only) — **broken**

- Phone hero: `features/home/PhoneHero.kt:31-32` — a `Palette.surface` box whose only art is `BufferedArtwork(card.landscape ?: card.poster, …)`. `BufferedArtwork` draws **nothing** when the URL is null (`features/home/BufferedArtwork.kt`: `if (url == null) { shown = null; … }`) or the decode fails (only `State.Success` promotes to `shown`). Result: flat near-black hero + gradient + text — exactly the reported symptom. No fallback artwork and no skip-to-next-featured logic.
- Same gap on tablet and desktop/browser heroes: `features/home/TabletHero.kt:33`, `features/home/DesktopHero.kt:42`.
- Additional variant: when `home != null` but there is no hero/featured card at all, the hero slot composes nothing (the placeholder is only drawn for `home == null && busy`) — `features/home/HomeScreen.kt:40-41`.
- AC "never an art-less hero; fallback or skip": **not met** on phone/tablet/desktop (TV billboard artwork follows focus and is covered by the TV work).

### NAV-11 — Duplicate titles in a Home shelf — **partial**

- The only dedupe on the home path is identity-level: `features/home/HomeCatalogRow.kt:35` — `row.cards.distinctBy(::cardLookupKey)`, where `cardLookupKey` = identity + `metadata_origin` + `provider_id` + `parent_identity` (`connection/MediaLookupKeys.kt:17`). The audited "Boku no Hero Academia ×3" (same title, different ids/origins) passes this filter; there is no title-level dedupe anywhere in the home path.
- The rows themselves are server data (`HomeSnapshot.rows`), so the data-side half of this issue cannot be verified from the app repo. The AC ("shelf has unique cards — data check") remains open app-side as well.

### Related (from the same phone list): title/details can render completely blank — **broken on the failure path**

- The loading state is fixed (skeleton: `features/details/DetailsScreen.kt:27` → `DetailLoadingLayout`), and a paging failure shows text (`:46` via `error`).
- But the **initial** detail-load failure is invisible: `App.kt:106` passes `state.detailPageError` (set only by episode paging — `state/ControllerDetailPages.kt:40`) as the screen's `error`, while an initial-load failure sets `state.error` (`state/ApplicationController.kt:69-75`; `readCurrentPage` rethrows on-page failures, `state/ControllerPageRequest.kt:28-30`). Nothing on `Screen.Details` renders `state.error`. With `detail == null`, `busy == false`, `detailPageError == null`, `DetailsScreen.kt:45-47` compose no items at all → a fully blank page with only the phone back chevron (`:92`). This is the "completely blank … error vs empty wording is inconsistent" finding, still present (previously it at least showed a "Try again"; now it shows nothing).

---

## 2. PHUI-01..06 — phone / tablet / web design parity

### PHUI-01 — Phone browse family (`M3Browse*`, `M3BrowseFilters`, `BrowseEmpty/Error/Loading`) — **partial / largely open**

What exists (`features/browse/BrowseScreen.kt`, non-TV branch):
- inline filters of every control kind, including a catalog switcher chip populated from peer catalogs (`features/browse/DiscoveryFilters.kt:37-41`: SINGLECHOICE/TEXT/NUMBER/DATE/TOGGLE/MULTICHOICE; the multi-choice opens a sheet, `DiscoveryMultiChoiceSheet.kt`);
- appending grid via `AutomaticPage` (`BrowseScreen.kt:53-54`) and 88dp bottom content padding for the bottom nav (`:82`).

Gaps vs the boards:
- header is a raw 28sp `Text(screen.name)` (`BrowseScreen.kt:59` — renders "Discover"/"Movies"/"Series"/"Anime") instead of the board's 102px blurred header with catalog pill (`Popular · VIPTV Sources`) and `Filters` trigger (`M3Browse`: `header: Movies … height=102px background=rgba(11,11,12,0.78) backdrop-filter=blur(20px)`);
- no skeleton grid for loading — only a `LinearProgressIndicator` (`BrowseScreen.kt:78`) vs `M3BrowseLoading`;
- empty state is one sentence (`:79` "No movies are available for these filters.") vs `M3BrowseEmpty`'s "Nothing matches" + `Remove “After 1 Jan 2021”` + `Clear all filters`;
- error row (`:77`) has no actions vs `M3BrowseError`'s `Try again` + `Choose another catalog`;
- catalogs switcher is an inline chip, not the `M3BrowseCatalogs` bottom sheet (560px, radius 28).

### PHUI-02 — Phone title page (`M3Title*`, `M3Recovery`, `M3About`, `M3SetDisplay`) — **partial**

- `M3Title`: broadly present — full-width 300dp artwork hero with a no-art fallback spacer (`features/details/PhoneDetailHero.kt:30-34`), logo/title + metadata + Play + My List + synopsis; section tabs `Episodes / More like this / Details` exactly matching the board labels (`features/details/DetailSections.kt:11-13`); numbered episode rows ("1. Episode One", `features/details/DetailEpisodeCard.kt:48`) with progress bars. Missing vs board: Resume pill ("Resume S1 E2 · 38m left"), source chip ("1080p · LordStreams · Best match"), Trailer/Rate/Share actions, cast line (blocked by DATA-02), HD/5.1/CC chips (DATA-04).
- `M3Recovery`: `features/entry/RecoveryCodesScreen.kt:23-45` shows codes + "I saved my recovery code" checkbox + Continue; the board's `Copy all` / `Save as file` / `8 of 10 unused · created …` / `Get new codes` are absent.
- `M3About`: phone About is a one-line dialog ("VIPTV · Experimental build", `features/settings/PhoneSettings.kt:89`) vs the board's full page (version, connected server, diagnostics, privacy, open-source licences).
- `M3SetDisplay`: no phone Display section exists at all — phone settings sections are Add-ons/Parental/Account/Devices/Playback/Subtitles (`features/settings/SettingsScreen.kt:45-56`); Display exists only as a TV section (`features/settings/TvSettingsModels.kt:5`).

### PHUI-03 — Phone settings rows (`M3Set*`) parity — **partial**

- `PhoneSettings` row list (`features/settings/PhoneSettings.kt:29-83`) covers Switch profile / Playback / Subtitles & audio / Notifications / Watch on TV / Add-ons / Account / Devices / Parental controls / Help / About / Sign out, in 16dp-radius `Palette.surface` groups — the board's row vocabulary (`M3Settings` text: Switch profile, Playback, Subtitles & audio, **Display**, Watch on TV, Add-ons, **Source integrations**, Account, Parental controls, Help, About VIPTV, Sign out). Missing rows: Display, Source integrations (source integrations is reachable only inside the Add-ons section — `SettingsScreen.kt:48`), and the board's row values ("Living room TV · remote on", "VIPTV Sources ready · 1 to set up", "On for cedes") are not implemented.
- Header: 40dp in-app bar (`PhoneSettings.kt:25-31`) vs the boards' 102px blurred header (`M3Settings`/`M3SetPlayback`: `height=102px background=rgba(11,11,12,0.78) backdrop-filter=blur(20px)`).
- Subtitles & audio is the closest match: `PhoneSubtitlePreferences` reproduces the board's preview line verbatim ("I didn’t hear you come in.", `features/settings/PhoneSubtitlePreferences.kt:32`), size segments, background, colour swatches, language rows, "Start with subtitles" (vs `M3SetSubs`); only the board's `Shadow` background option is missing (app has None/Box).
- Playback section is thin: only "Default source" + "Preferred quality" (`features/settings/PhoneProfilePreferences.kt:39-44`) vs `M3SetPlayback`'s Skip intro / Next episode / Resume / Mobile-data rows.

### PHUI-04 — Tablet boards (18): navigation rail + two-pane layouts — **partial**

- Rail: `TabletNavigation` is a fixed 92dp column (`components/TabletNavigation.kt:23`) — matches the `T3*` rail width (92px) — but opaque (`Palette.background`) vs board `rgba(11,11,12,0.55)` + `blur(24px)`, and the vertical rhythm differs (board: 40px top padding, 24px icon gaps; app: 16dp + brand + 36dp spacer, 80dp rows).
- Two-pane title: `features/details/TabletDetailsScreen.kt:26` (Row, left `weight(.5)` art+info, right `weight(.5)` episodes with 28dp/40dp padding) approximates `T3Title` (fixed 560px info pane + episodes pane) but is ratio-based, not the board's fixed pane; below 800dp it silently degrades to the phone/desktop single column (`TabletDetailsScreen.kt:22-25`).
- Settings two-pane exists for tablet (`SettingsScreen.kt:42` sidebar branch) in the spirit of `T3Settings` (360px sidebar + detail).
- Browse: `T3Browse` specifies a 300px filters column; the app tablet browse renders filters in a top `Row` (`BrowseScreen.kt:67-74`) — not board parity.
- `T3HomePortrait` / `T3Narrow` narrow variants have no dedicated handling (only the single <640/<800 compact checks).

### PHUI-05 — Web boards (40): `W3Browse*`, `W3Parental`, `W3Recovery`, `W3SetSources` — **partial**

- Browser shell matches the W3 header geometry: `components/Navigation.kt:41` 68dp bar with 57dp horizontal padding vs board `header … height=68px padding=0 57px backdrop-filter=blur(20px)`. Nav labels: app shows Home/Discover/Movies/Series/Anime/Live/Calendar/My List (Navigation.kt:30-33 + `components/Destinations.kt`) vs the W3 header's Home/Discover/Movies/Series/Live/Calendar/My List (extra "Anime").
- The account family the W3 boards specify exists functionally for `browser=true`: Account/LinkedAccounts, Devices, Parental controls (`ParentControlsSettings` + `ParentTitlesSettings`), Source integrations (`features/settings/SettingsRoute.kt:19-46`), and Recovery via the shared `RecoveryCodesScreen` — but none of it is board-matched (see PHUI-02 for Recovery), and the W3 account pages are URL-routed pages (`/account/sources`, `/account/parental`, `/account/recovery-codes`) while the app is a single-`Screen` SPA with no URL routing, no `Skip to content` a11y link (present in every W3 account board header), and no `W3NotFound` equivalent.
- `W3Browse` specifies a filter rail; the browser browse uses the same top-row filters as desktop (`BrowseScreen.kt:67-74`, `browser` only toggles focus outlines at `:88`).
- Responsive/mobile W3 variants (`W3HomeMobile`, `W3LandingMobile`, `W3SignInMobile`, `W3DeviceMobile`, `W3Home768`) rely on the shared compact branches — unverified against boards here.

### PHUI-06 — Cross-platform copy consistency — **partial**

- Consistent across platforms today: search empty copy is identical on phone and TV ("No matches. Try another search.", `SearchScreen.kt:54` = `TvSearchScreen.kt:118`); the My List empty state is a single shared function (`features/library/LibraryEmptyState.kt:15`) used by phone, tablet and TV.
- Deviations from board copy: "Use + on a movie or series to save it here." vs `M3MyListEmpty`'s "Tap + on any movie or series to save it here."; browse empty "No movies are available for these filters." vs `M3BrowseEmpty`'s "Nothing matches"; the phone home header chips add "Anime" where `M3Home`'s header tabs are All/Movies/Series/Genres.
- Structural risk: all copy is hardcoded per composable (no shared string table); "Try again" alone is duplicated in 20+ call sites, so cross-platform drift is easy (the TV side already has board copy like `M3BrowseError`'s "Try again"/"Choose another catalog" pending in BROWSE-04 while the phone row has none).

---

## 3. Caveats and suggested next steps

1. Audit provenance: every "Phone:" finding in `ISSUES.md` §2 came from a ≥640dp landscape emulator run (tablet branch). Re-run the phone audit on a real compact viewport (e.g. the `viptv-design-phone` AVD, portrait) before closing phone-domain issues; NAV-21's "rail expands" and NAV-23's left-hugging form were tablet-branch symptoms.
2. Quick code wins verified by this pass: wire `state.error` into the Details screen (or merge it into `detailPageError`) to kill the blank title page (`App.kt:106` + `DetailsScreen.kt:45-47`); move the Live guide notice into the LiveScreen column below the chips and make the copy channel-agnostic (`LiveRoute.kt:47-50`, `ControllerLive.kt:101`); add an art-less hero fallback or skip (`PhoneHero.kt:32`, `TabletHero.kt:33`, `DesktopHero.kt:42`); add shelf edge peek on phone (`HomeCatalogRow.kt:50`); add a Back affordance or `BackHandler` on the profile picker (`MobileProfiles.kt:36-41`).
3. Design work still open on this domain: phone browse family to boards (PHUI-01), phone About/Display/Recovery pages (PHUI-02), settings row values + 102px blurred headers (PHUI-03), tablet rail translucency + browse filters column (PHUI-04), web URL routing/skip-link/filter rail (PHUI-05), and a shared copy table (PHUI-06).
4. Nothing in this audit ran the app; all statuses are code-verifiable facts with file:line, not runtime observations. Items marked "broken" are broken by construction in the current tree; items marked "partial" mix fixed and unfixed sub-paths.
