# Cross-platform copy + a11y audit — PHUI-06, R1 (READ-ONLY)

**Repo:** `/mnt/ALPH/code/viptv-org` · **App source audited:** `app/composeApp/src/commonMain`
**Method:** static read-only — grepped every user-visible literal (`Text("…")`, `label`/`title`/`placeholder`/`supportingText`/`contentDescription`, error strings: 482 `Text("…")` sites, 144 `Icon(…)` sites, all `RemoteFailure`/`LocalFailure` constructions) and diffed them against the board text extracted from `design/viptv-design-system/reference/screens/html/{tv,phone,desktop,tablet,web}/*.html`. No gradle, adb, emulator or build was run; no repository file was modified. **Caveat:** the tree is being edited concurrently — line numbers are as found at audit time (2026-06-05 run); re-grep before fixing.
**Context:** `app/docs/ISSUES.md:109` (PHUI-06 “Cross-platform copy consistency (same strings from the boards)”), `app/docs/OPEN_TASKS.md` (phone audit findings), `app/docs/UI_INVENTORY.md` (board map).
**Headline:** there is **no shared strings layer** — all copy is hardcoded inline across ~180 files, which is the root cause of most findings below.

---

## A. Raw backend text leaking into the UI

The transport layer is well curated — `RemoteConnection.checkStatus` (RemoteConnection.kt:250–257) and every `RemoteFailure(...)` construction site (RemoteDevicePairing.kt:22–48, DeviceApprovalApi.kt:26–32, AccountDevices.kt:20, RemoteAddons.kt:34/49/64, etc.) map statuses to fixed sentences, and `ApplicationController.run` (ApplicationController.kt:66–76) curates unknown exceptions. The remaining passthroughs are JSON fields decoded and rendered verbatim:

| # | Where | What leaks | Severity |
|---|---|---|---|
| A1 | `features/home/HomeCatalogRow.kt:56` and `features/home/TvHomeShelf.kt:47` — `Text(row.message ?: "Try again")` | `HomeRow.message` (EngineWire.kt:188, decoded from `/api/bootstrap` JSON) is rendered **as the retry button’s label**. Any server string becomes a button; the fallback “Try again” only shows when `message` is null. Board Tv3RowStates instead wants a “Couldn’t load this row” title + `VIPTV Sources: "…"` body + a literal “Try again” button. Phone + TV. | High |
| A2 | `features/settings/ParentTitlesSettings.kt:94` — `message?.let { Text(it) }` | `ParentTitleItem.message` / `ParentTitleRow.message` decoded straight from `/api/parent/search` (connection/ParentTitles.kt:8–11) and rendered verbatim per row. | Medium |
| A3 | `state/ControllerParentTitles.kt:54` — `throw LocalFailure(item.message ?: "This title cannot be approved through this integration.")` | Same backend field, re-thrown as a user-facing error string (no curation). | Medium |
| A4 | `features/entry/TvEmailCode.kt:61` — `Text(challenge.status)` | `TvEmailCodeChallenge.status` (TvEmailCode.kt:22) is a raw server string shown as the screen subtitle. Today only UiPreview.kt:87 populates it (with board copy), so production cannot reach it — but the moment the email-code flow is wired, the server controls the sentence. | Low (latent) |
| A5 | `state/ControllerAccountEntry.kt:34,47` — `failure.message.orEmpty()` | Not raw (RemoteFailure messages are curated) but `orEmpty()` can render a **blank error line** on non-401 recover/register failures — inconsistent with the phone audit finding “Try again with no message”. | Low |

Related, same class of bug: `features/settings/TvSettingsScreen.kt:74` renders the curated “Unavailable on this connection.” (trailing period) while both boards (`Tv3SetSources`, `M3SetSources`) print “Unavailable on this connection” with no period — punctuation drift in a status chip.

The historical “API route not found” toast from the phone audit (`OPEN_TASKS.md`, Phone section) originates in `backend/server/src/routes.rs:187` (fallback `ApiError(404, "API route not found")`); with the current app code it would only surface via A1–A3 style JSON `message` fields, since all ktor error paths are curated.

## B. Gamepad / TV wording on touch surfaces

No literal “press”, “D-pad”, “gamepad” or remote-control instructions were found on phone/tablet/desktop screens. “Typing with a remote is slow…” exists only on TV entry screens (`features/entry/TvPasswordPanel.kt:44`, `features/entry/TvEmailSignIn.kt:69`) exactly as the boards specify. The real findings in this class are:

- **B1 — TV-remote affordance on the phone header:** `components/PhoneHeader.kt:37–38` puts a “Remote control” icon button on every phone screen header; tapping it opens the dialog at `components/PhoneHeader.kt:57–59`: `"$name is not available in this experiment yet."` — an internal, developer-facing sentence (“this experiment”) that appears on no board.
- **B2 — “this experiment” wording on touch surfaces (3 more sites):** `components/TabletNavigation.kt:41–43` (“Casting” / “Casting is not available in this experiment yet.”), `features/home/TabletHero.kt:65` (`"$label is not available in this experiment yet."`), `features/settings/PhoneSettings.kt:88–89` (`"$label is not available in this experiment yet."`). Users see lab vocabulary instead of product copy; boards use feature-level sentences (“Casting is not available…” never appears; M3Remote is a dedicated screen).
- **B3 — device/TV-wording drift in a shared sentence:** the torrent-only sources note is parameterized per platform but *also* diverges from the board: `features/sources/SourcesScreen.kt:71` (“…this device cannot stream directly…to that add-on’s address, or enable a provider with direct streams.”) and `features/sources/TvSourcesScreen.kt:111` (“…this TV cannot stream directly…”). Both boards (Tv3SourcesTorrent, M3SourcesTorrent) end “…to play them, or try another title.” (see C6).
- Positive: `MobilePinScreen`/`PhonePlayerFailure` correctly avoid remote/gamepad wording; phone player lock matches touch semantics (`M3PlayerLocked` board says “Screen locked · tap to unlock”; app shows an icon-only unlock with `contentDescription = "Unlock controls"`, `features/player/PlayerScreen.kt:139–140`).

## C. Strings that differ from the boards

Verified against the 2026-10-19 boards in `design/viptv-design-system/reference/screens/html/`. “Board” column gives the board id and its exact copy.

| # | App string (file:line) | Board copy | Notes |
|---|---|---|---|
| C1 | `features/entry/TvWelcome.kt:58` “Without an account, everything stays on this TV.” | Tv3Welcome: “Without an account, everything stays on this TV. **No profiles or sync.**” | Second sentence dropped. |
| C2 | `features/entry/TvPasswordPanel.kt:30` field label **“Username”**; `features/entry/MobilePasswordPanel.kt:34` “Username”; `features/entry/DesktopPasswordPanel.kt:38` “Username” | Tv3SignInPassword / M3SignInPassword / D3SignInPassword all label the field **“Email”** (`[name@email.com]`) | All three platforms consistently wrong vs boards — the single highest-value PHUI-06 fix. Also `ConnectScreen.kt:48` “Username” (browser fallback). |
| C3 | `features/entry/MobileSignIn.kt:50` “Continue in browser / Approve this sign-in through your server” and `:51` “Use another device / Scan a code and approve this sign-in”; same in `features/entry/DesktopSignIn.kt:63,66` | M3SignIn / D3SignIn: “Continue in browser / **Sign in on viptv.syek.tech, then come back here**” and “Use another device / **Approve this sign-in from a phone or TV that’s signed in**” | Subtitles rewritten; TV choice screen (TvSignInChoice.kt:33–38) matches its board exactly, so TV and phone now describe the same methods differently. |
| C4 | `features/search/SearchField.kt:31` and `features/search/TvSearchScreen.kt:118` placeholder **“Search movies, series and channels”** | M3Search field: **“Movies, series and channels”** (no prefix); D3HomeWin: “Search movies, series and channels” (desktop matches); Tv3Search has no placeholder text | Shared string matches the desktop board but not the phone board; the TV placeholder is extra copy the TV board doesn’t have. |
| C5 | `features/details/DetailHero.kt:81` and `features/details/DesktopDetailHero.kt:151` **“Play from start”** | M3CwMenu / D3Context: **“Play from the beginning”** | Both touch/desktop boards use the longer wording. |
| C6 | Torrent-only sources note (`features/sources/SourcesScreen.kt:71`, `features/sources/TvSourcesScreen.kt:111`) ends “**…to that add-on’s address, or enable a provider with direct streams.**” | Tv3SourcesTorrent / M3SourcesTorrent: “…Add a debrid service (for example TorBox) **to play them, or try another title.**” Also missing the board’s “What’s a debrid service?” link text and “Needs debrid” badge. | See B3. |
| C7 | `features/sources/SourcesScreen.kt:84–85` `sourceSummary` → “12 sources from 3 **providers**”; `features/sources/TvSourcesScreen.kt:111,120` “Checking **providers** · … sources so far” | Tv3Sources / M3Sources: “The End of Oak Street · 12 sources from 3 **add-ons**”; Tv3SourcesLoading: “Checking **add-ons** · 2 of 3 done” | “providers” vs “add-ons” in the same UI where rows are labelled by add-on. |
| C8 | `features/library/LibraryEmptyState.kt:15` “Your list is empty. / **Use + on a movie or series to save it here.**” (no follow-up action) | M3MyListEmpty: “Your list is empty. / **Tap + on any movie or series to save it here.** / **Browse Home**” | “Use” vs “Tap +”, missing “any”, missing the Browse Home action. TV has no board-empty copy equivalent (`TvLibraryScreen.kt:50` renders the same app string). |
| C9 | `features/home/DesktopHero.kt:50` **“FEATURED”** (all caps, no counter) | D3Home* boards: “**Featured · 1 of 5**” (sentence case + counter); Tv3Home has no such label at all | Case + missing pagination text (known TODO in OPEN_TASKS “Home polish”). |
| C10 | `features/settings/SettingsInformation.kt:47–53` About copy: “VIPTV”, “Experimental build”, “Native clients can use local providers or connect to a server. Browsers use a server account.”, “Application and font notices”, “Application license”, “Onest font license”, “Bricolage Grotesque font license” | Tv3SetAbout / M3About: “About / Version 0.3.0 (412) · up to date / Check for updates / Connected to [server.example] / Send diagnostics / Shares logs from the last hour / Privacy / Open-source licences / Show and episode data from Simkl. / This product uses the TMDB API but is not endorsed or certified by TMDB.” | Whole About section is different copy. Also `features/settings/SettingsSidebar.kt:57` “VIPTV · Experimental build” and `features/settings/PhoneSettings.kt:89` same. |
| C11 | `features/entry/RecoveryCodesScreen.kt:23–41` — “Save your recovery code”, “Your account is created. Keep this code somewhere private…”, “This code is shown only now…”, “I saved my recovery code”, “Continue”/“Return to sign in” | M3Recovery / D3Recovery: “Recovery codes / If you lose access to your email, one of these codes signs you in. Each code works once… / Copy all / Save as file / 8 of 10 unused · created 14 Oct 2026 / Get new codes / Your current codes stop working” | Single-code flow with different title and copy (known open task: recovery to boards). |
| C12 | `features/entry/DeviceApprovalPage.kt:129` “You’ll find it on your TV under Sign in.” | M3LinkCode: “It’s on your TV under Sign in › Use a phone. **Letters only, no zeros or ones.**” | Code-entry hint dropped the character constraints. (Enter/Continue buttons match.) |
| C13 | `features/settings/PhoneProfilePreferences.kt:31–34` paragraph “Playback resumes from your saved position. Next episodes start only when you choose.” + “Sources” group | M3SetPlayback rows: “Skip intro button / When the show marks its intro”, “Next episode button / During the credits — nothing plays on its own”, “Resume where I left off” | Phone playback settings copy is a different information architecture; TV equivalent (TvPlaybackSettings.kt:21–33) matches Tv3SetPlayback word-for-word. |
| C14 | `features/browse/TvBrowseScreen.kt:74` “No titles are available in this catalog.”; `features/browse/BrowseScreen.kt:74` “No {movies/series/anime/titles} are available for these filters.” | Tv3MoviesEmpty / M3BrowseEmpty: “Nothing matches these filters / Popular from VIPTV Sources has no movies with all 8 filters. Removing the newest one usually helps. / Remove “After 1 Jan 2021” / Clear all filters” | Empty states not on boards; same for error states (boards: “Couldn’t load Popular / VIPTV Sources didn’t answer in time. Your filters are kept. / Try again / Choose another catalog”). |
| C15 | `features/entry/TvSplash.kt:41,46` — “Getting VIPTV ready…” + “Change connection” | Tv3Splash: only the “V | VIPTV” mark, no status text or action | Custom copy on the splash (has good `contentDescription = "Starting VIPTV"`, TvSplash.kt:29). |
| C16 | `features/entry/ConnectScreen.kt:45–46,58` — “Welcome back” / “Sign in to your VIPTV server.” / “Use this device without an account” | W3SignIn: “Sign in to VIPTV / Pick whichever is easiest. They all reach the same account. / … / Use without an account” | Legacy fallback form diverges from the web board’s sign-in copy (also uses “Username”, C2). |
| C17 | TV details dialog title `features/details/TvDetailsScreen.kt:114` “Title options” (body: “No trailer is available for this title.” :92) | Tv3Title/Tv3Movie show no options dialog; trailer absent simply isn’t offered | Custom surface + copy not on boards (minor). |
| C18 | `features/player/PlaybackTrackPicker.kt:58` “**None available**” (phone track empty state) vs `features/player/TvPlaybackTrackPicker.kt:42` “**No audio tracks available**” (TV) | Tv3PlayerAudio has no empty state; phone/TV should still agree with each other (PHUI-06) | Cross-platform divergence of the same state. |
| C19 | `features/history/HistoryScreen.kt:43` “Nothing watched yet.” (phone) vs `features/history/TvHistoryScreen.kt:76` “Nothing watched yet. Titles you play show up here.” (TV) | D3History/M3History have no empty state on board | Same concept, two sentences, neither on a board. |
| C20 | Matches to confirm as *correct parity* (kept for the R1 record): Tv3SignInChoice ↔ TvSignInChoice.kt:33–38/50; Tv3SetPlayback ↔ TvPlaybackSettings.kt:21–33; Tv3SetSubs ↔ TvSubtitleSettings.kt:31–65 (incl. “I didn’t hear you come in.”); Tv3PlayerError ↔ TvPlayerFailure.kt:36,55,59 + “Try $name” :49; Tv3PlayerNext ↔ TvNextEpisode.kt:41–58; Tv3SignOut/M3SignOut ↔ TvSettingsDialogs.kt:29–38 and SettingsLeaveDialog.kt:16; Tv3MyList ↔ TvLibraryControls.kt:28–48; Tv3SetSources ↔ TvSourceIntegrationForm.kt:157–182; Tv3MoviesError copy not yet implemented (open task). |

## D. Missing contentDescription / unlabeled controls (a11y)

Only 24 `contentDescription` usages exist across commonMain; 144 `Icon(…)` sites. Most icon-only interactive controls **are** labelled (good: PlaybackSkipIcon.kt:17–18 “Forward/Back 10 seconds”, HomeCatalogRow.kt:69–71 “Next/Previous items”, SearchField.kt:35 “Clear search”, LiveChannelRow.kt:38 favourite star, TvHero/TvDetailHero/PhoneHero/DesktopHeroActions/TabletHero “Add/Remove from My List”, “More info”, PlaybackButtons.kt:22–46, HistoryRow.kt:47, AddonSettings.kt:55/58/95, SourcesScreen.kt:59, AccountDeviceRow, DetailSeasonPicker.kt:36/63, PhoneHeader.kt:35–38, SettingsInformation a11y previews). The gaps:

- **D1 — unlabeled `Switch` toggles** (control renders without an accessible name because the label is a sibling `Text`, not a `toggleable` row or a `semantics` description):
  - `features/settings/ProfilePreferencesSettings.kt:36` — `Row { Switch(enabled, …); Text("Start with subtitles") }`. Contrast the correct pattern two files away: `features/settings/PhoneSubtitlePreferences.kt:74–76` uses `Row(…toggleable(…, role = Role.Switch))` + `Switch(onCheckedChange = null)` for the **same string**.
  - `features/settings/ParentControlDialogs.kt:67–68` — `Row { Text("Kids restrictions"); Switch(enabled, …) }`, same issue.
  - Compare `features/settings/AddonSettingsRow.kt:32`, which does it right (`Modifier.semantics { contentDescription = "Enable ${addon.name} for this profile" }`).
- **D2 — unlabeled `Checkbox` rows** (same sibling-Text pattern, no row-level `toggleable`):
  - `features/entry/RecoveryCodesScreen.kt:33–34` — `Checkbox(saved, …)` + `Text("I saved my recovery code")`.
  - `features/settings/SourceIntegrationForm.kt:38–41` — provider checkboxes + `Text(provider.label)`.
  - `features/browse/DiscoveryMultiChoiceSheet.kt:66–67` — non-TV branch `Checkbox(checked, …)` + `Text(choice.label)`.
- **D3 — icon-only buttons whose visible label lives elsewhere** (screen readers get the row text, but the icon itself is `null`; acceptable on TV where the option text is inside the focusable, flagged for consistency):
  - `features/entry/TvProfiles.kt:61` — add-profile tile is a bare `Lucide.Plus` with `null`; the accessible name comes only from the caption `Text("Add profile")` inside TvProfileItem. Phone/desktop twins set the description explicitly (`ProfilesScreen.kt:52`, `MobileProfiles.kt:82`, `DesktopProfiles.kt:89`).
  - `features/entry/MobileSignIn.kt:83` / `features/entry/DesktopSignIn.kt:113` — trailing `ChevronRight` `null` inside choice rows (decorative; row text is the name).
- **D4 — icon-only buttons that duplicate a *visible* label with different words** (a11y label ≠ on-screen text, fine but should be one shared string): “Unlock controls” (`features/player/PlayerScreen.kt:140`) vs M3PlayerLocked board “Screen locked · tap to unlock”; “Back to settings” (`features/settings/AddonSettings.kt:55,58`).
- Positive: strong semantics elsewhere — PIN digits (`features/entry/TvPinScreen.kt:65`), loading live regions (`HomeStatus.kt:42,50`, `TvSettingsAccount.kt:31`, `TvPreferencesFailure.kt:20`, `TvPlayerFailure.kt:27` assertive), hero position `HeroIndicators.kt:19` “Featured N of M”, TV settings rows expose `Role.Switch` + `ToggleableState` (`TvSettingsRow.kt:49`, `TvAddonSettings.kt:35`), colour swatches as radio buttons (`TvSubtitleColor.kt:22`, `PhoneSubtitlePreferences.kt:61`).

## E. Duplicated strings that should be shared

Exact-duplicate counts (grep over `Text/label/title/contentDescription/placeholder` literals) — no `strings.xml`/resources layer exists, so these will keep drifting:

| String | Copies | Sites (file:line) |
|---|---|---|
| “Cancel” | 24 | dialogs across entry/settings/library/history/sources (e.g. ProfilesScreen.kt:66, TvSettingsDialogs.kt:41, TvSourceIntegrationForm.kt:143, HistoryScreen.kt:59 …) |
| “OK” | 13 | TvPinScreen.kt:95, TvEmailSignIn.kt:79, TvPairingSignIn.kt:53, PhoneSettings.kt:90, TabletNavigation.kt:43, TvSettingsScreen.kt:105 … |
| “Play” | 7 | TvHero.kt:44, PhoneHero.kt:47, DesktopHeroActions.kt:23, TabletHero.kt:49, PhoneDetailHero.kt:49, BrowserDetailsDialog.kt:98 … |
| “Settings” | 6 | Navigation.kt:74, PhoneHeader.kt:46, TvNavigation.kt:63, PhoneSettings.kt:30, TvSettingsSidebar.kt:26, SettingsSidebar.kt:24 |
| “Switch profile” | 5 | Navigation.kt:73, PhoneHeader.kt:45, TvNavigation.kt:62, PhoneSettings.kt:40, SettingsSidebar.kt:29 |
| “Server address” | 5 | ConnectScreen.kt:47, DesktopPasswordPanel.kt:67, MobilePasswordPanel.kt:60, TvPasswordPanel.kt:52, DevicePairingRequest.kt:23 (plus “HTTPS server address” twice: TvPasswordPanel.kt:53, DevicePairingRequest.kt:25) |
| “Add an add-on” | 4 | TvAddonSettings.kt:56, TvSettingsScreen.kt:111, AddonSettings.kt:79, AddonSettings.kt:94 |
| “Add profile” | 4 | ProfilesScreen.kt:54/63, MobileProfiles.kt:85, DesktopProfiles.kt:92 (+TvProfiles via TvProfileItem) |
| “All sign-in options” | 4 (+2 prefixed “‹  ”) | DesktopSignIn.kt:76, MobilePasswordPanel.kt:29, DesktopEmailPanel.kt:23, MobileSignInUnavailable.kt:35, PairingBackLink.kt:20, DesktopPasswordPanel.kt:33 |
| “Use another server” | 4 | MobilePasswordPanel.kt:57, PairingBrowser.kt:63, NativeDevicePairing.kt:49, TvRemotePairing.kt:29 |
| “Sign in to VIPTV” | 4 | DesktopSignIn.kt:55, MobileSignIn.kt:39, TvSignInChoice.kt:29, TvPairingSignIn.kt:31 |
| “Change avatar” | 4 | ProfileEditScreen.kt:37, MobileProfileEdit.kt:48, AvatarPicker.kt:25, TvProfileEditScreen.kt:59 |
| “Edit profile” | 3 | ProfileEditScreen.kt:34, MobileProfileEdit.kt:39, TvProfileEditScreen.kt:51 |
| “Current PIN” | 3 | ProfileEditScreen.kt:40, AvatarPicker.kt:41, TvProfileEditDialog.kt:35 |
| “This removes this profile’s history and add-on choices.” | 3 | ProfileEditScreen.kt:59, MobileProfileEdit.kt:128, TvProfileEditDialog.kt:43 |
| “Choose a source” | 3 | SourcesScreen.kt:57, TvSourcesScreen.kt:83, PhonePlayerFailure.kt:51 |
| “Nothing here yet.” | 2 | HomeCatalogRow.kt:55, TvHomeShelf.kt:46 (board Tv3RowStates: EMPTY rows are **not drawn at all** — this string contradicts the board) |
| “I didn’t hear you come in.” | 2 | TvSubtitleSettings.kt:42, PhoneSubtitlePreferences.kt:32 (matches boards — should be one constant) |
| “No releases for this day.” | 2 | CalendarScreen.kt:65, CalendarScreen.kt:71 (duplicated within one file) |
| “Clear history?” / “This removes viewing history for this profile.” / “Clear” | 2 each | HistoryScreen.kt:56–59, TvHistoryScreen.kt:98–101 |
| “Save preferences” | 2 | ProfilePreferencesSettings.kt:60, PhoneProfilePreferences.kt:39 |
| “This source stopped working” | 2 | TvPlayerFailure.kt:36, PhonePlayerFailure.kt:40 |
| “All genres” | 2 | BrowseScreen.kt:59, BrowseScreen.kt:66 (same file) |
| “Pick whichever is easiest. They all reach the same account.” | 2 | DesktopSignIn.kt:57, MobileSignIn.kt:41 |
| “Open this address in a browser on your phone or computer.” | 2 | SettingsInformation.kt:74, ParentControlsNative.kt:32 |
| “No matches. Try another search.” | 2 | SearchScreen.kt:54, TvSearchScreen.kt:118 |

**Near-duplicate divergences** (same intent, different words — the actual PHUI-06 violations):

- “**More Like This**” (TV, `features/details/TvRecommendationShelf.kt:26`) vs “**More like this**” (phone/desktop, `features/details/RelatedTitles.kt:18`, `features/details/DetailsScreen.kt:86`). This matches each platform’s board (Tv3Movie vs M3Title), so it is board-correct but cross-platform inconsistent — flag for a copy decision.
- “**Save name**” (`features/entry/ProfileEditScreen.kt:48`) vs “**Save**” (`features/entry/MobileProfileEdit.kt:105,119`) vs “**Save preferences**” — one concept, three labels.
- “**Retry**” (6 sites: SourceIntegrationSettings.kt:41, AccountDevicesScreen.kt:64, TvSourceIntegrationForm.kt:165, TvPlayerFailure.kt:49, PhonePlayerFailure.kt:46, LiveRoute.kt:49) vs “**Try again**” (HomeCatalogRow.kt:56, TvHomeShelf.kt:47) vs “**Retry Home**”/“**Refreshing Home…**” (HomeStatus.kt:35) vs “**Check again**” (board Tv3SourcesEmpty) vs “**Refresh**” (SourceIntegrationSettings.kt:36, ProfilePreferencesSettings.kt:30, AccountDevicesScreen.kt:65) vs “**Refresh controls**” (ParentControlsSettings.kt:29) vs “**Retry parental controls**” (ParentControlsSettings.kt:33) vs “**Retry account**/Refresh account” (TvSettingsAccount.kt:41). Boards standardize on “Try again”/“Refresh”.
- “**A local PIN keeps profiles separate in the app. The device owner can access local files.**” (`features/entry/ProfileEditScreen.kt:44`) vs “**A PIN keeps profiles separate in the app. The device owner can access local files.**” (`features/entry/TvProfileEditDialog.kt:41`) — one word different.
- “**Providers (leave empty to use the default selection)**” (`features/settings/SourceIntegrationForm.kt:35`) vs “**Providers** / **Leave empty to use the default selection.**” (`features/settings/TvSourceIntegrationForm.kt:102,104`).
- “**Select a source to start playback.**” (SourcesScreen.kt:78, TvSourcesScreen.kt:120) vs board footer “**Play always starts the best match. Change how it’s chosen in Settings › Playback.**” (Tv3Sources) / “…Change this in Settings › Playback.” (M3Sources) — the board footer string is absent from the app entirely.
- Sign-in “unavailable” family: “Email codes are not available on this connection yet.” (TvEmailSignIn.kt:78), “Creating pairing codes is not available…” (TvPairingSignIn.kt:52), “$label is not available on this connection yet.” (TvSettingsScreen.kt:105, SettingsSidebar.kt:58), “Add-on management is not available on this connection.” (AddonSettings.kt:66), “Browser sign-in is not available on this connection yet.” (MobileSignInUnavailable.kt:25) — five phrasings for one concept, none on boards.
- “**Username**” as a label appears 4× (C2) while `TvSettingsAccount.kt:25` uses “Email”/“Username” contextually — inconsistent field naming for the same account concept.

## F. Typography/punctuation consistency (copy hygiene)

- Straight apostrophe vs the codebase’s dominant typographic `’`: `features/library/LibraryScreen.kt:48` “Everything **you've** played”, `features/entry/DeviceApprovalPage.kt:129` “**You'll** find it…”, `connection/DeviceApprovalApi.kt:18` “isn't”, `state/DeviceApprovalController.kt:89` “didn't” — every other string uses `’` (e.g. TvNextEpisode.kt:58, ProfileEditScreen.kt:59).
- ASCII dots vs `…`: `features/entry/DeviceApprovalPage.kt:123` “Checking your session**...**” is the only one of 18 ellipsis strings not using `…`.
- “Unavailable on this connection**.**” (TvSettingsScreen.kt:74) vs board chip “Unavailable on this connection” (no period).

## Priority recommendations (fix order)

1. **A1/A2/A3**: stop rendering backend `message` fields verbatim (map row state → fixed copy per Tv3RowStates; keep “Try again” as the literal button).
2. **C2**: rename the password field label to “Email” on all platforms (one-word change, three files + ConnectScreen).
3. **B1/B2**: replace “this experiment” sentences with feature-level copy; remove or board-align the phone “Remote control” header affordance.
4. **D1/D2**: wrap the four Switch/Checkbox sites in `toggleable`/`selectable` rows with `Role` semantics (copy the PhoneSubtitlePreferences.kt:74 pattern).
5. **E**: introduce a shared copy module (constants or resources) for the ~30 strings listed; that also fixes C1, C4, C5, C6, C8 in one sweep where boards agree.
6. **C10/C11/C13/C14**: board-copy rewrites for About, Recovery, phone Playback rows, and browse empty/error states (larger, already tracked in OPEN_TASKS).
