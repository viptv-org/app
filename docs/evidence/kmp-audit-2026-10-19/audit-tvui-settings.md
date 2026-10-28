# TV Settings & Entry UI Audit — TVUI-03..07, TVUI-10..15

Read-only audit, no builds or device runs. Evidence: current working tree at `app/`
(HEAD `ffebb7d`, 2026-10-19, with concurrent edits in progress — line numbers as found
today), board specs from `python3 tools/board_spec.py <BoardId>` and the board HTML
(`design/viptv-design-system/reference/screens/{html,img}/tv/`), issue context from
`app/docs/ISSUES.md` §4, `app/docs/OPEN_TASKS.md`, `app/docs/UI_INVENTORY.md`.

Paths below are relative to `app/composeApp/src/commonMain/kotlin/org/viptv/app/`.

---

## 1. Direct answer — what is still a placeholder / AlertDialog / undesigned

| Section / screen | Board | Current state | Verdict |
|---|---|---|---|
| Settings → Display | `Tv3SetDisplay` | Generic fallback: one row, click → "not available" AlertDialog | **placeholder, undesigned** |
| Settings → About | `Tv3SetAbout` | Generic fallback: one static row "VIPTV · Experimental build" | **placeholder, undesigned** |
| Settings → Parental controls | `Tv3SetParental` | Redirect stub: heading + "open the web dashboard" row | **undesigned (no rating chips, no PIN rows)** |
| Settings → Account | *(no board)* | `TvSettingsAccount` rows say "Managed on web"; row clicks → unavailable AlertDialog | **placeholder (TVUI-11)** |
| Add-on manual entry | `Tv3AddonEntry` | Material3 `AlertDialog` with `OutlinedTextField` | **AlertDialog, undesigned (TVUI-04)** |
| Add-on install confirm | `Tv3AddonInstall` | Does not exist — "Add" installs immediately | **missing (TVUI-04)** |
| Sources sheet empty state | `Tv3SourcesEmpty` | One 22sp text line, no icon/buttons | **undesigned (TVUI-03)** |
| Sources sheet torrent-only state | `Tv3SourcesTorrent` | One 22sp text line; no alert panel, no dimmed rows | **undesigned (TVUI-03)** |
| Remove integration confirm (TV) | `Tv3SourcesRemove` | Shared phone Material `AlertDialog` | **AlertDialog (TVUI-05, list is done)** |
| Recovery codes on TV | *(no board)* | Phone Material screen rendered unscaled on TV | **undesigned (TVUI-15)** |
| TV Calendar / TV History | *(no boards)* | Built to "TV vocabulary", self-designed | **no board exists (TVUI-10)** |
| Sign-in family, Profiles, PIN, Profile edit, Player states, Live guide | `Tv3SignIn*`, `Tv3Profiles/Pin/ProfileEdit`, `Tv3Player*`, `Tv3Live` | Implemented at board geometry; gaps are detail-level | **parity, minor deltas** |

**Material `AlertDialog` usages reachable on TV** (grep `AlertDialog` in
`features/`): `settings/TvSettingsScreen.kt:104` (unavailable), `:106` (browser
fallback), `:111` (add-on install — TVUI-04), `settings/SourceIntegrationSettings.kt:65`
(remove confirm, TVUI-05), `entry/TvProfileEditDialog.kt:26` (rename/PIN/delete),
`entry/ProfilePreferenceGuidance.kt:14` (maturity/language guidance),
`entry/TvPinScreen.kt:93` (forgot PIN), `entry/TvEmailSignIn.kt:77`,
`entry/TvEmailCode.kt:79`, `entry/TvPairingSignIn.kt:51`, `entry/TvPasswordPanel.kt:52`.
Custom, board-shaped popups exist only for sign-out (`TvSettingsDialogs.kt:18`
`TvLeaveConfirmation`, 800w/r24 matches `Tv3SignOut` dialog) and the choice picker
(`TvSettingsDialogs.kt:44`). `PreviewTvSettings.kt` confirms the intended variants.

---

## 2. Concrete board mismatches per screen

### TVUI-03 — Sources sheet states (`features/sources/TvSourcesScreen.kt`)

Implemented well: right drawer 960px at x=960, bg `#161618`, padding 72/96/54/64,
44px title, 24px `#B6B4AF` subtitle, filter chips, 104px rows (r14, pad 28), focus
fill `#F4F2EE` (`TvSourcesScreen.kt:80-96`, `TvSourceRow.kt:29-33`).

Mismatches vs boards:
- **Empty state** (board `Tv3SourcesEmpty` aside): board has an 88px search-icon
  circle, 40px "No sources yet" heading, 26px explanation ("None of your 3 add-ons or
  8 providers has … New releases often appear within a few days."), a primary
  280×72 "Check again" button and a 300×72 "Manage add-ons" button. App renders one
  22sp `Text` line ("No sources found. Enable a provider that supplies streams.") —
  `TvSourcesScreen.kt:110-115`. No icon, no heading, no actions, wrong copy.
- **Torrent-only state** (board `Tv3SourcesTorrent`): board renders a `role=alert`
  panel (r16, bg `rgba(255,255,255,0.07)`, 26px copy, "What's a debrid service?"
  400×72 primary + "Back" 180×72) plus **dimmed (opacity .6) 104px torrent rows**
  with quality, release name, codec/size line, EN/ES chips and a "Needs debrid"
  32px badge. App renders a single text line
  ("…Add a debrid service (for example TorBox) to **that add-on's address**…" —
  board says "to play them, or try another title") and shows **no rows at all** —
  `TvSourcesScreen.kt:111-112`. `torrentOnlyProviders` count is passed in
  (`App.kt:104`) but never rendered as a designed state.
- **Header summary**: board says "checked 3 add-ons and 8 providers" / "3 results
  this TV can't play"; app says "N sources from M providers" (`SourcesScreen.kt:83-86`)
  — counts providers, not add-ons, and never states a checked-add-ons total.
- **Footer copy**: board bottom line is "Play always starts the best match. Change
  how it's chosen in Settings › Playback." / "Checking add-ons · 2 of 3 done · N
  sources so far"; app says "Select a source to start playback." /
  "Checking providers · N sources so far" (`TvSourcesScreen.kt:120`).
- Row label 28sp SemiBold vs board 24px w600 (`TvSourceRow.kt:44`) — minor token drift.

### TVUI-04 — Add-on entry/install (`features/settings/TvAddonSettings.kt` + `TvSettingsScreen.kt`)

- **Entry**: board `Tv3AddonEntry` is a full page — eyebrow "Add-ons", 56px
  "Add an add-on", label + 1060×80 field (r14) with 3px inset white focus ring,
  **alphabetical keyboard** (`1…0 a…z - _ . :` + `https:// .com / Clear` utility
  row), 564px QR panel ("Easier: scan to add it from your phone or go to
  your-server.example/addons"), Install 260×72 + Cancel 220×72 at y=860. The app opens
  a **Material3 `AlertDialog`** with an `OutlinedTextField`, a
  `LinearProgressIndicator` and TextButtons ("Add"/"Cancel") —
  `TvSettingsScreen.kt:111-121`. No TV keyboard (remote cannot type without one),
  no QR panel, no board geometry. Triggered from the "Add an add-on" row at
  `TvAddonSettings.kt:50-56` → `{ installing = true }` (`TvSettingsScreen.kt:69-70`).
- **Install confirmation**: board `Tv3AddonInstall` adds a centered 900px dialog
  (r24, `#212124`) — "Install this add-on?", add-on avatar tile + name + v2.3,
  "From [addon.example]", "Adds catalogs: Popular, New, Genres · Types: Movies,
  Series", trust warning, "+ Install" primary + Cancel. The app **has no confirm
  step at all**: `Add` → `install(address.trim())` immediately
  (`TvSettingsScreen.kt:113-121`). No manifest preview, no permissions/capability
  summary, no trust copy.
- **Add-ons list QR panel**: board `Tv3SetAddons` shows a real QR + "Scan to add
  one from your phone or go to your-server.example/addons". App renders a 236px box
  that says either "Pairing unavailable / Add-on pairing is not available yet."
  (production: `placeholderQr` defaults false, only `PreviewTvSettings.kt:35` sets
  it) or the literal "[QR code]" placeholder — `TvAddonSettings.kt:62-70`. Sign-in
  already renders real QR codes via qrose (`TvPairingQr.kt:27`), so the capability
  exists but is not wired here.
- Row subtitle: board shows content counts ("Movies and series · [1,240] titles");
  app joins capability names (`TvAddonSettings.kt:37-39`).

### TVUI-05 — Source integration forms (`features/settings/TvSourceIntegrationForm.kt`)

The **list** matches `Tv3SetSources` closely (160px cards, icon tile 64/r14, status
column, Replace/Remove/Configure 210-240×60, footer "Applies to … Saved values are
never shown again…") — `TvSourceIntegrationForm.kt:111-160, 163-225`. Form pages:

- **Geometry/placement**: board `Tv3SourcesViptv`/`M3u`/`Xtream` are full-screen
  pages starting at x=96, y=72 with a 1700px header and **no settings sidebar**.
  The app renders `TvSourceIntegrationPage` **inside the settings content column**
  (nested under `TvFocusColumn` start-80 padding, then its own 96px padding —
  actual x≈736, width ≈990) — `TvSourceIntegrationForm.kt:58-62` invoked via
  `SourceIntegrationSettings.kt:21-26` ← `SettingsRoute.kt:24-27`. The sidebar
  stays visible; the board's form page replaces the whole screen.
- **Heading/copy**: board "Source integrations · VIPTV Sources · Movies and series
  from up to 12 providers. Applies to vynxc's profile only." App:
  "Configure ${schema.name}" + "Enter the settings below. Values stay on this TV."
  (`TvSourceIntegrationForm.kt:66-72`) — different title hierarchy and copy, no
  per-profile line.
- **Secret field**: board shows masked dots with a "Show" toggle and 3px focus
  ring. App shows plaintext in an 84px row with 0.62 fill-width and no mask/Show
  (`TvSourceIntegrationForm.kt:73-93`).
- **Keyboard**: board keyboards are alphabetical (`a b c … z - _ . : Clear`) with
  `https://` and `/` keys; app reuses the QWERTY sign-in keyboard with
  Shift/Space/.com/@gmail.com keys (`TvEntryKeyboard.kt:22-52`) —
  `TvSourceIntegrationForm.kt:95`.
- **QR panel**: board has a "Long key? Type it on your phone or open
  your-server.example/tv…" QR box at x=96 y=806; app has none.
- **Providers grid**: board is a 3-column grid of 268×56 checkbox chips with a
  "Providers 8 of 12" count + "Pick up to 12…" hint; app chunks into 4-per-row
  64px chips with no count line and a different hint ("Leave empty to use the
  default selection.") — `TvSourceIntegrationForm.kt:98-120`.
- **Error state** (`Tv3SourcesM3u`): board renders a designed inline alert
  ("Couldn't load that playlist. The server answered '403 Forbidden'…") plus a
  "Nothing was saved / Your previous settings are unchanged…" panel with
  "Try again"/"Cancel" buttons. App shows a plain 22sp danger text + the same
  Save/Cancel pair (`TvSourceIntegrationForm.kt:122-124`).
- **Saving state** (`Tv3SourcesXtream`): board has a "Checking the server…" status,
  a disabled "Saving…" primary button and an "After saving" explainer block. App
  has none — Save stays enabled-looking; busy is only a focus/enable flag.
- **Required/Optional labels**: board fields carry "Required"/"Optional" tags; app
  doesn't.
- **Remove confirmation**: board `Tv3SourcesRemove` dialog is 860×r24 `#212124`
  with per-plugin consequence copy ("The TMDB key and provider choices are deleted
  from vynxc's profile. Movies and series from these providers stop showing until
  you set it up again. Nothing else on the account changes.") and Cancel/Remove.
  The TV path reuses the phone **Material `AlertDialog`** with generic copy
  ("This removes its saved credentials and source assignment from this profile.")
  — `SourceIntegrationSettings.kt:65-68` (set from `TvSourceIntegrationForm.kt:118`
  `remove`).
- Extra app UI not on any board: `BrowsingProviderSettings` ("Metadata provider"
  Material `FilterChip`s, unscaled Material typography) renders above the list on
  TV (`SettingsRoute.kt:24-26`, `BrowsingProviderSettings.kt:16-36`) — a
  non-TV-styled island inside the scaled settings section.

### TVUI-06 — Parental (`features/settings/ParentControlsNative.kt` via `SettingsRoute.kt:56-58`)

Board `Tv3SetParental` is a full control page: profile pills row at y=216
(choose whose restrictions), "Movies up to" rating chips G/PG/PG-13/R/NC-17 at
y=330, "Series up to" TV-Y…TV-MA at y=476, explainer at y=618 ("Titles above
these ratings don't show anywhere for cedes — not in rows, search or Live."),
"Hide titles with no rating" toggle (88px row), "Blocked titles" row with count
badge "2", "PIN to leave cedes's profile" row.

The app's TV parental section is a redirect stub — `ParentControlsNative.kt:19-36`:
heading, one paragraph ("Manage the household PIN and profile restrictions through
your server's web dashboard." / local: "Manage each local profile's PIN in
Profiles."), the dashboard URL as text, one `TvSettingsRow` ("Open server
dashboard" / "Manage profiles") that launches an external browser
(`openAccountAuthorization`). Zero rating chips, zero blocked-title list, zero PIN
row; every board element is missing. The fallback in `TvSettingsScreen.kt:76-79`
(if `parental == null`) is even thinner: "Parental controls" + an "Open Profiles"
row. The full parental UI exists only in the web/phone branch
(`ParentControlsSettings.kt`, `ParentTitlesSettings.kt`, Material-styled).

### TVUI-07 — About + Display (`features/settings/TvSettingsScreen.kt:86-91`)

Both sections fall into the generic `else` branch of the settings switch:

```
Text(section.label, fontSize = (40*scale).sp); Spacer(48)
TvSettingsRow(if (section == About) "VIPTV · Experimental build" else "Open Profiles") {
    if (section == Parental) profiles() else unavailable = section.label
}
```

- **Display** (`Tv3SetDisplay` — 6 rows, 96px, r14, pad 0 28, first row focused):
  Accent colour (swatches: yellow/coral/teal/periwinkle), "Show ratings on cards"
  toggle, "Text size … Default" chevron, "Reduce motion … Follow TV" chevron,
  "Screen saver … After 5 minutes" chevron, "Fit to screen" (no control). The app
  renders **one row** labelled "Open Profiles" whose activation opens the
  unavailable `AlertDialog` ("Display is not available on this connection yet.",
  `TvSettingsScreen.kt:104-105`). Nothing from the board exists; there is no
  accent-colour, ratings, text-size, motion, screen-saver or overscan setting
  anywhere in `features/settings/`.
- **About** (`Tv3SetAbout` — 5 rows + footer): "Version 0.3.0 (412) · up to date"
  with "Check for updates", "Connected to [server.example] · fast", "Send
  diagnostics" (subtitle "Shares logs from the last hour…"), "Privacy",
  "Open-source licences", footer "Show and episode data from Simkl. This product
  uses the TMDB API but is not endorsed or certified by TMDB." The app renders one
  static row "VIPTV · Experimental build" (click → same unavailable `AlertDialog`);
  no version row, server status, diagnostics, privacy, licences or Simkl/TMDB
  attribution anywhere on TV.

### TVUI-10 — Live (+ Calendar/History)

`Tv3Live` is **largely matched** (`TvLiveScreen.kt` + `TvLivePreview.kt` +
`LiveGuide.kt`): LIVE badge + channel line at (96,130), 52px program title, time
range + "N min left" + badges, "Watch live" 640×360 preview at (1184,120) r12
`#212124`, category tablist at y=512, guide time header 44px, 300px channel labels,
72px rows 8px apart, red now-marker — all at board geometry. Deltas:
- Board's tab row is a plain category list; the app adds a favourites filter and a
  source-selector control not present on the board (functional additions,
  `TvLiveScreen.kt:29-31`).
- Board program cells use `rgba(255,255,255,0.09)` fill; app's `LiveProgramCell`
  matches this family. Empty/no-guide fallbacks (text + "Manage add-ons" action)
  are app-invented (no board).
- **Calendar**: no TV board exists; `TvCalendarScreen` is self-designed to TV
  vocabulary (`CalendarScreen.kt:26`). **History**: no TV board; `TvHistoryScreen`
  self-designed ("The board export has no History screen…" — file header comment).
  History is additionally **unreachable from the TV top nav**
  (`TvNavigation.kt:41` filters `Screen.History` out; only My List links to it,
  `LibraryRoute.kt:23`), while the board `Tv3Live` nav (and `Tv3Home`) show a
  `Calendar` nav item the app does include. Both screens still draw custom page
  headings the boards' family does not have (OPEN_TASKS "page titles" item).

### TVUI-11 — Settings → Account (no board)

`TvSettingsAccount.kt:22-45` — rows "Connection/Email/Username" (value "Local
mode" / username / **"Managed on web"**), "Devices" (**"Managed on web"**),
"Refresh/Retry account" action, "Open server dashboard" row, "Sign out"/"Change
connection" row. Row activations for identity/devices call `unavailable` → the
generic `AlertDialog` (`TvSettingsScreen.kt:104`); the dashboard action can fall
back to another `AlertDialog` ("Open server dashboard…", `TvSettingsScreen.kt:106-109`).
The only board guidance is `Tv3SignOut`, which shows an Account column ("Email …
[name@email.com]", "Devices — Sign out of other TVs and phones from
your-server.example … [3] devices", "Sign out of this TV") plus the sign-out dialog
(matched well by `TvLeaveConfirmation`). The app's placeholder copy ("Managed on
web") and non-actionable rows are invented; the board's Devices subtitle copy is
not implemented. Needs a board (DOC-04) or an adaptation.

### TVUI-12 — Player states

Implemented at board geometry — controls (`TvPlayerControls.kt:20-46`: title 30px
at (96,64), "Ends at" 22px top-right, pill row at y=846 bottom=178, 56px pills),
buffer (`TvPlayerBuffer.kt:14-32`: "Starting playback" 30px + 24px context, board
texts match), error (`TvPlayerFailure.kt:16-57`: 88px alert circle, 48px heading
"This source stopped working", 28px description, 380×72 Try/Choose/Back — matches
`Tv3PlayerError` copy), next-episode (`TvNextEpisode.kt:31-58` matches
`Tv3PlayerNext` at (1040,120) w784), skip prompt (`TvPlayerStates.kt:12-33` —
280×72 r12 primary at right=96/bottom=112, board `Tv3PlayerSkip`), scrub preview
(`TvSeekPreview.kt` 26px label, board `Tv3PlayerScrub`), audio/subtitles sheet
(`TvPlaybackTrackPicker.kt:24-46`: 540px bottom sheet r24 blur-30 `#161618`@.9,
three 520px columns at x=76/676/1276 — exact `Tv3PlayerAudio` match). Remaining
deltas:
- Track rows: board `.opt` rows are 76px, 28px w600 text, selected fill
  `rgba(255,255,255,0.12)` with radio dot; app rows are 64px, 24px text,
  transparent background with a check icon (`TvPlaybackTrackPicker.kt:66-77`).
- Board track sheet shows per-track sub-labels (e.g. "English · 5.1"); app renders
  labels only.
- Scrub time label y-offset ≈12px above board's y=888 (minor).
- Board `Tv3PlayerControls` "Episodes Audio & subtitles Source · 4K" pill order
  and "Next episode" right-alignment match; the app adds no extras. ✓

### TVUI-13 — Sign-in family

Strong parity: `Tv3SignIn` (QR 340px + divider "or" circle + 640×120 code box +
"New code" 48px + status 24px + email 420×72 at right-96/bottom-880 —
`TvPairingSignIn.kt:24-50`, `TvPairingQr.kt`, `TvPairingBrowser.kt`, real QR via
qrose), `Tv3SignInChoose` (five 900×100 r16 options at y=250 + 704px feature
panel at x=1120 — `TvSignInChoice.kt:20-49`), `Tv3SignInEmail` (field 1130×92 at
(96,250), keyboard at y=382, 534px right panel at x=1290 —
`TvEmailSignIn.kt:31-75`), `Tv3SignInCode` (six 104×124 boxes at (96,260), status
at y=410, numeric pad at x=1180, "Send a new code" at y=500 —
`TvEmailCode.kt:40-75`), `Tv3SignInPassword` (fields at (96,230), keyboard,
right panel at x=1290 — `TvPasswordPanel.kt:24-50`), plus `Tv3SignedIn`
(`TvSignedIn.kt:27-48`) and `Tv3SignOut` dialog (`TvSettingsDialogs.kt:18-41`).
Deltas:
- Password fields say "Username"/"Password"; board `Tv3SignInPassword` labels the
  first field "Email" and prefills `[name@email.com]` (`TvPasswordPanel.kt:30`,
  `TvEntryField` usage).
- App adds non-board affordances: "Delete character", "Use another server"
  (`serverDialog` Material `AlertDialog`), "Use your phone instead" button on the
  email step (board has it as text link in the right panel). Functional extras.
- Fallback paths are Material `AlertDialog`s (see §1) — styled unlike the boards.
- QR sign-in is real; contrast with add-ons QR (TVUI-04) which is a placeholder.

### TVUI-14 — Profiles / PIN / Profile edit

- `Tv3Profiles`: "Who's watching?" 56px at top=220, 200px cards at y=380 gap 56,
  "Manage profiles" at y=780 — matched (`TvProfiles.kt:42-77`). Deltas: **Add
  profile** tile only in local mode and <12 profiles (`TvProfiles.kt:57`);
  **Manage profiles** only in local mode (`TvProfiles.kt:66`) — the board shows
  both unconditionally; remote connections get neither.
- `Tv3Pin`: avatar 120px at y=150, 44px "Enter the PIN for X" at y=300, 4×
  (88×104) boxes at y=380, 136×76 keys at y=540, "Forgot PIN?" at y=920 —
  matched (`TvPinScreen.kt:41-92`), including parent-PIN 8-box variant.
  Deltas: app keypad adds an "OK" key (board has only 1-9/0 + an icon key);
  "Forgot PIN?" opens a Material `AlertDialog` saying recovery "is not available
  for this profile" (`TvPinScreen.kt:93-96`) — no designed recovery flow.
- `Tv3ProfileEdit`: heading 52px at (96,96), 400px avatar column with "Change
  avatar" 280×72, right column 1184px rows 96px r14: Name (value), Maturity
  rating (subtitle + value "All ratings"), Profile lock (toggle), Language
  (subtitle + value "English"), Delete profile (80px danger row, `#FF8A80`).
  App matches structure (`TvProfileEditScreen.kt:48-80`) but:
  - Row 4 is renamed **"Audio & subtitles"** with value "In Settings"/"Select
    profile" (board: "Language" → "English") — `TvProfileEditScreen.kt:71-73`.
  - **Maturity rating and Language rows do not open pickers**: they open a
    Material guidance `AlertDialog` that redirects to Settings
    (`TvProfileEditDialog.kt:13-16`, `ProfilePreferenceGuidance.kt:10-29`). The
    board presents them as ordinary value rows.
  - Name / Profile lock / Delete open **Material `AlertDialog`s with
    `OutlinedTextField`s** (`TvProfileEditDialog.kt:26-62`) — no TV keyboard, no
    designed TV dialog; PIN entry relies on a text field rather than the designed
    keypad.
  - Profile lock row is gated to local mode (`pinAvailable`, `App.kt:75`);
    board shows it unconditionally. Maturity value shows "Not available
    locally"/"Account policy" instead of board's "All ratings".

### TVUI-15 — Recovery codes (no TV board)

`RecoveryCodesScreen.kt:18-52` renders on TV unchanged (`App.kt:69`): 520dp-wide
Material column, `MaterialTheme.typography.headlineLarge`, 24dp page padding,
Material `Checkbox`, 48dp buttons — **none of it multiplied by
`maxWidth/1920dp`**, so on a TV it is a small unscaled phone panel. There is no TV
board (`UI_INVENTORY.md` row "Recovery codes — none on TV"). Needs a TV board or
an adaptation; at minimum the screen must be TV-scaled and use the TV action/
focus treatment.

---

## 3. Adjacent settings sections (context; not in the issue's board list)

- `Tv3SetPlayback` ↔ `TvPlaybackSettings.kt:12-31`: row set, subtitles, values and
  subtitle copy match the board (including "Best match"/"Always ask" choice dialog).
  ✓ Done.
- `Tv3SetSubs` ↔ `TvSubtitleSettings.kt:24-79`: preview 720×405 at (640,200), Size/
  Background/Text colour rows, right column 424px at x=1400 — matches. One real
  gap: TV passes `actualPreferencesOnly = true` (`SettingsScreen.kt:24`), which
  drops the board's "Shadow" background option (`TvSubtitleSettings.kt:52` lists
  only "None"/"Box" on TV), and `TvProfilePreferences.kt:10` maps any non-transparent
  colour to "Box" — the board's three-state background control cannot be reached
  from a TV.
- `Tv3SetAddons` ↔ `TvAddonSettings.kt:30-56`: 760px rows 100px, icon tile 60/r14,
  switch, "Add an add-on" row, QR panel 364px — geometry matches; QR is a
  placeholder/fallback (see TVUI-04).
- `Tv3SetSources` list ↔ `TvSourceIntegrationList`/`TvSourceIntegrationCard`: board
  card internals put the status column at x=1340 and the "Saved …" detail at x=756
  with actions at fixed x (1364/1556/1586); the app arranges status/detail/actions
  inside the card via weighted columns — visually close, not pixel-identical.
  Statuses/copy match ("Ready", "Needs configuration", "Unavailable on this
  connection", "Saved …"). ✓ List done (matches TVUI-05 note).
- Focus treatment (TVUI-16) is implemented board-wide:
  `components/TvFocusTreatment.kt:25-40` (4px white outline + light fill), used by
  `TvSettingsRow`, `TvAction`, sidebar rows. ✓
- Sidebar tokens match `Tv3Set*` boards: width 560, rows 440×72 at x=72 pitch 80,
  first row y=196, selected `rgba(255,255,255,0.14)` (app uses `#333333` — 1
  alpha step brighter than board's 0.14 composite), 26px w500, icon 28, "Settings"
  44px at (96,93) (`TvSettingsSidebar.kt:18-52`).

---

## 4. Ranked punch list (what to build, in board terms)

1. **Display + About sections** (TVUI-07): build the two real pages from
   `Tv3SetDisplay`/`Tv3SetAbout` (6 rows / 5 rows + attribution footer); delete the
   `else` fallback (`TvSettingsScreen.kt:86-91`). Accent-colour swatches and
   "Check for updates" need backing settings/actions or a data decision.
2. **Parental page** (TVUI-06): port the web parental data onto the TV settings row
   system — profile pills, two rating chip rows, "Hide titles with no rating",
   "Blocked titles" (count → list), "PIN to leave X's profile"; replace
   `ParentControlsNative`'s dashboard redirect.
3. **Add-on entry + install** (TVUI-04): replace the install `AlertDialog` with the
   `Tv3AddonEntry` full page (alphabetical keyboard + QR panel + Install/Cancel)
   and add the `Tv3AddonInstall` confirm dialog; wire a real QR (qrose already used
   in `TvPairingQr.kt`).
4. **Sources empty/torrent states** (TVUI-03): designed empty block (icon circle,
   40px heading, copy, Check again / Manage add-ons) and the torrent alert panel +
   dimmed "Needs debrid" rows; align header/footer copy ("checked N add-ons",
   "Play always starts the best match…").
5. **Source integration form polish** (TVUI-05): full-screen form placement, board
   keyboard, masked field + Show, error alert panel, "Checking the server…"
   saving state, Providers count, and a TV-styled remove dialog replacing the
   shared Material `AlertDialog` (`SourceIntegrationSettings.kt:65`).
6. **Account + Recovery** (TVUI-11/15): commission boards (or adapt
   `Tv3SignOut`'s Account column / `M3Recovery`); replace "Managed on web"
   placeholder rows; TV-scale `RecoveryCodesScreen`.
7. **Profile edit dialogs** (TVUI-14): TV-styled dialogs with the TV keyboard for
   Name/PIN; make Maturity/Language real value rows or commission pickers.
8. Detail fixes: sources row label 24px; track-picker row 76px/28px + selected
   fill; subtitle "Shadow" option on TV; History nav reachability.

## Evidence files
- App: `features/settings/TvSettingsScreen.kt`, `TvSettingsModels.kt`,
  `TvSettingsSidebar.kt`, `TvSettingsRow.kt`, `TvSettingsAccount.kt`,
  `TvAddonSettings.kt`, `TvSettingsDialogs.kt`, `TvPlaybackSettings.kt`,
  `TvSubtitleSettings.kt`, `TvProfilePreferences.kt`, `ParentControlsNative.kt`,
  `TvSourceIntegrationForm.kt`, `SourceIntegrationSettings.kt`,
  `BrowsingProviderSettings.kt`, `SettingsRoute.kt`, `SettingsScreen.kt`;
  `features/sources/TvSourcesScreen.kt`, `TvSourcesFilters.kt`, `TvSourceRow.kt`,
  `SourcePresentation.kt`, `SourcesScreen.kt`;
  `features/entry/TvProfiles.kt`, `TvPinScreen.kt`, `TvProfileEditScreen.kt`,
  `TvProfileEditDialog.kt`, `ProfilePreferenceGuidance.kt`, `TvSignIn*.kt`,
  `TvPairing*.kt`, `TvEmail*.kt`, `TvPasswordPanel.kt`, `TvSignedIn.kt`,
  `RecoveryCodesScreen.kt`, `TvEntryKeyboard.kt`;
  `features/live/TvLiveScreen.kt`, `TvLivePreview.kt`, `LiveGuide.kt`;
  `features/player/TvPlayer*.kt`, `TvNextEpisode.kt`, `TvPlaybackTimeline.kt`,
  `TvSeekPreview.kt`, `TvPlaybackTrackPicker.kt`; `features/history/TvHistoryScreen.kt`;
  `components/TvNavigation.kt`, `TvAction.kt`, `TvFocusTreatment.kt`, `Destinations.kt`;
  `App.kt`; `theme/Theme.kt`.
- Boards: `Tv3Set{Display,About,Parental,Playback,Subs,Addons,Sources}`,
  `Tv3Sources{,Loading,Empty,Torrent,Viptv,M3u,Xtream,Remove}`,
  `Tv3Addon{Entry,Install}`, `Tv3Live`, `Tv3Player{Controls,Buffer,Error,Next,
  Scrub,Skip,Audio}`, `Tv3SignIn{,Choose,Email,Password,Code}`, `Tv3SignedIn`,
  `Tv3SignOut`, `Tv3Profiles`, `Tv3Pin`, `Tv3ProfileEdit`.
- Docs: `app/docs/ISSUES.md` §4 (TVUI-01..17), `app/docs/OPEN_TASKS.md`,
  `app/docs/UI_INVENTORY.md`.
