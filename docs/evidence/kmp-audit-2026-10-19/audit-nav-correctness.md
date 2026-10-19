# Android-TV navigation & focus correctness audit (read-only)

Repo: `/mnt/ALPH/code/viptv-org` (app at `/app`). Method: source reading only — no
builds, no adb, no emulator. Every claim below is pinned to `file:line` as found in
the current working tree. Compose version in use: Multiplatform `1.13.0-alpha02`
(androidx runtime/ui `1.13.0-alpha03`), verified from `app/build.gradle.kts:5` and
the local Gradle cache.

Scope: TV code paths only (`television = true`), including the shared `App.kt`
routing and `androidApp` MainActivity. Focus/key behavior of the framework itself
was checked against the actual Compose artifacts in the Gradle cache (bytecode),
not against memory.

---

## Findings (ordered by severity)

### H1 — TV Settings: `TvAction`-based content never registers with `TvFocusColumn`, so D-pad escapes the page into the sidebar (TV analog of NAV-21)

**Symptom.** On TV Settings → Add-ons, pressing Down from the last visible add-on
row (or from "Add an add-on") makes focus jump to the sidebar's *About* row. On a
failed-preferences section, Up from "Retry settings" lands on the sidebar's
*Account* row and Down lands on *About*. The remote visibly "falls out of the page".

**Where.**
- `app/composeApp/src/commonMain/kotlin/org/viptv/app/features/settings/TvAddonSettings.kt:34-49` (every add-on row is a plain `TvAction`), `:50-57` ("Add an add-on").
- `app/composeApp/src/commonMain/kotlin/org/viptv/app/features/settings/TvPreferencesFailure.kt:26-33` (the retry `TvAction` is the *only* control of the failed-preferences page).
- `app/composeApp/src/commonMain/kotlin/org/viptv/app/features/settings/TvSettingsAccount.kt:34-41` ("Retry/Refresh account" `TvAction`; when `missing`, it is the first control, so Up escapes to the sidebar's first row).
- `app/composeApp/src/commonMain/kotlin/org/viptv/app/features/settings/TvSourceIntegrationForm.kt:164-166` (error retry), `:176-193` (integration cards), `:134-144` (form Save/Cancel).

**Why.** `components/TvColumnFocus.kt:4-13` exists precisely to stop this:
"Compose's geometric focus search is happy to jump from the last row of a settings
page across to the sidebar's last item… On a TV that reads as the remote 'falling
out' of the page." The clamp only works for controls that register themselves via
`rememberTvColumnItem` — and the *only* caller in the entire codebase is
`features/settings/TvSettingsRow.kt:42` (verified with grep). `TvAction`
(`components/TvAction.kt`) never registers, and `TvSettingsScreen.kt:61` wraps all
section content in `TvFocusColumn`, so all `TvAction`-rendered sections (Add-ons,
failed preferences, Account retry, Source integrations) silently lose the clamp.
`focusProperties { left = sidebar }` is applied to those `TvAction`s, but Up/Down at
the column edges fall through to 2-D geometric search, which happily crosses into
the sidebar (content column x≈640-1824, sidebar rows x≈72-512, all below/above in
the search half-plane).

**Minimal fix.** Make the `TvAction`-based settings rows participate in the column:
either route them through `TvSettingsRow`, or add a small `Modifier.tvColumnItem()`
helper that calls `rememberTvColumnItem(target)` and writes the resulting
`up`/`down` into `focusProperties` (mirroring `TvSettingsRow.kt:43-51`), and apply it
in the four files above.

---

### H2 — Dialog/dropdown dismissal leaves TV focus dead: the app's own focus-restore discipline is not applied to most TV popups (NAV-27 family)

**Symptom.** After closing a TV settings choice dialog ("Default source"), the
profile DropdownMenu in the top navigation, the sort menu on My List, the
"no trailer" notice on Details, the Clear-history confirmation, or the sources
drawer, no control on screen holds focus. The next D-pad press lands wherever a
root-level geometric search decides (often the top navigation) — or nothing
responds at all until the user has pressed several keys.

**Where (places that dismiss without restoring focus).**
- `components/TvNavigation.kt:61-64` — profile `DropdownMenu` (`menu = false` on select/dismiss).
- `features/library/TvLibraryControls.kt:42-46` — sort `DropdownMenu`.
- `features/browse/DiscoveryChoiceFilter.kt:24-33` — choice `DropdownMenu` (TV browse filters).
- `features/settings/TvSettingsScreen.kt:102-135` — `TvLeaveConfirmation`, `TvSettingsChoiceDialog`, the "unavailable" `AlertDialog`, the server-dashboard `AlertDialog`, and the add-on install `AlertDialog`.
- `features/settings/TvSettingsDialogs.kt:22-48` — both dialogs only request *initial* focus (`LaunchedEffect(Unit) { initial.requestFocus() }`), never restore on dismiss.
- `features/details/TvDetailsScreen.kt:113-115` — "No trailer is available" `AlertDialog`.
- `features/history/TvHistoryScreen.kt:97-101` — Clear-history confirmation.
- `features/settings/TvSourceIntegrationForm.kt:69-71` — server-address `AlertDialog`.
- `features/sources/TvSourcesScreen.kt:44` — the sources drawer `Popup(focusable = true)`; after Back/scrim dismissal returns to the details page, nothing re-focuses the hero controls.

**Why.** These are all focusable popups/dialog windows; while open they hold window
focus and Compose releases the underlying window's focus
(`AndroidComposeView.onFocusChanged(false) → FocusOwner.releaseFocus()`, verified in
the `ui-release-runtime` bytecode of Compose 1.13). Nothing re-grants focus when the
popup closes. The codebase already knows this and has a working, documented pattern
for exactly this situation — `features/entry/TvPinScreen.kt:36-45`,
`features/entry/TvEmailCode.kt:33-36`, `features/entry/TvEmailSignIn.kt:29-34`,
`features/entry/TvPairingSignIn.kt:24-26`,
`features/entry/TvProfileEditScreen.kt:39-46`,
`features/browse/DiscoveryMultiChoiceFilter.kt:17-24` — all set a `restore`
requester in the dismiss callback and re-request focus once
`LocalWindowInfo.isWindowFocused` is true again. `docs/TV_PIN.md:20` states it
plainly: "Recovery dismissal waits for the parent window and restores focus to its
link"; `docs/TV_PROFILE_EDIT.md:32-33` and `docs/TV_EMAIL.md:21` repeat it. The
screens listed above simply never got the treatment.

**Minimal fix.** Apply the same dismiss→`restore = target` + `windowFocused`-gated
re-request pattern in each location (for the `DropdownMenu`s, re-focus the anchor
`IconButton`/trigger on `onDismissRequest`).

---

### H3 — TV History: empty (or freshly cleared) history leaves the D-pad completely dead; the initial-focus loop targets a *disabled* button

**Symptom.** Open TV History on a profile that has watched nothing: no control is
focused; D-pad does nothing (only Back works). After pressing "Clear history" and
confirming, the same dead state appears even though the screen was just navigable.

**Where.**
- `features/history/TvHistoryScreen.kt:48-53` — the initial focus loop requests
  `clearFocus` when `history.isEmpty()`.
- `features/history/TvHistoryScreen.kt:67-68` — that requester is attached to the
  Clear button, which is `enabled = !busy && history.isNotEmpty()` → a disabled
  `clickable` is not focusable, so `requestFocus()` returns false for all 30 retry
  frames and the effect gives up silently.
- `components/TvNavigation.kt:26-28` — `Screen.History` is not in the top-nav
  initial-focus list (`Movies, Series, Anime, Live, Calendar, Library`), so the
  visible TopNavigation is never focused as a fallback.
- `features/history/TvHistoryScreen.kt:100` — `clear(); confirm = false` closes the
  dialog (no restore, H2), disables the Clear button, and empties the list.

**Why.** The empty state's only other content is plain `Text` (line 88-90); there is
no focusable fallback and no path to the top navigation.

**Minimal fix.** (1) When `history.isEmpty()`, request focus on the top navigation
(add `Screen.History` to the `TvNavigation` initial-focus list, or focus the nav row
from `TvHistoryScreen`). (2) After the confirm dialog dismisses, restore focus (H2
pattern) to the (now disabled) header area or the nav.

---

### H4 — TV details blank state (NAV-04) is also a focus dead-end: no focusable control exists at all

**Symptom.** When a title page fails to load (`detail == null`, not busy), the TV
details screen renders only the backdrop and (maybe) an error `Text`. Nothing is
focusable; the D-pad is dead until Back is pressed.

**Where.** `features/details/TvDetailsScreen.kt:33` returns early only for
`detail == null && busy`; otherwise the `LazyColumn`'s only focusable content is
inside `if (detail != null)` (line 60) — the hero buttons — and the `error`/`busy`
items at lines 94-96 are plain `Text`/`LinearProgressIndicator`. There is no top
navigation on TV Details (`App.kt:79` excludes Details).

**Why.** The loading branch has `DetailLoadingLayout`, but the null-and-idle branch
(the "completely blank" NAV-04 state) has no interactive fallback and no focus
request. This compounds the known content bug with a navigation dead end.

**Minimal fix.** When `detail == null && !busy`, render a `HomeStatus`-style retry
row (visible, focusable, with `TvRequestedFocus`) — this fixes both the blank screen
and the dead remote.

---

### H5 — TV Home: Up from the first row works only on card 0 — every other card in that row is a dead key

**Symptom.** While browsing the shelves, pressing Up from the *first* card of
Continue Watching returns to the billboard (correct). Pressing Up from card 2, 3, …
of the *same row* does nothing at all.

**Where.** `features/home/TvHomeShelf.kt:45-46` — the
`onPreviewKeyEvent { … Key.DirectionUp → up() … }` clamp is attached only when
`index == 0 && first != null`, and `first` is only passed to the first READY row
(`TvHomeScreen.kt:176`). No `focusProperties { up = … }` exists for the other cards.

**Why.** For the remaining cards, Up falls through to geometric focus search. While
browsing: the hero buttons are `canFocus = false`
(`TvHomeScreen.kt:140`, `focusEnabled = { !browsing }` via
`features/home/TvHomeVisibility.kt:36`), and the top navigation is unmounted
(`TvHomeVisibility(!browsing, …)` removes content after the exit animation,
`TvHomeVisibility.kt:24-26`). No candidate exists above → the key is a no-op.

**Minimal fix.** Attach the Up clamp to every card of the first row (pass the row's
`up` handler through `focusProperties` on the row's cards, or handle Up on the
shelf's `focusGroup` as `TvRecommendationShelf.kt:31-33` already does for its whole
row).

---

### H6 — TV Home with no featured content and only pending shelves: no initial focus

**Symptom.** On a fresh profile where `featured`/`hero` are empty and every row is
still `PENDING` (skeletons), the screen opens with no focused control; the first
D-pad press lands wherever root-level geometric search picks (usually the top nav's
search icon rather than the intended control).

**Where.** `features/home/TvHomeScreen.kt:81-88` — the initial shelf focus requires
`firstReadyRow >= 0` (a READY row). `TvHomeScreen.kt:92-94` — the navigation focus
fallback fires only when `home == null && error == null`. When `home != null` with
`featured.isEmpty()` and all rows PENDING, neither path runs. Skeleton cards are not
focusable (`features/home/TvHomeLoading.kt:34-36` — no `clickable`).

**Minimal fix.** Also bump `navigationFocusEpoch` when
`home != null && featured.isEmpty() && firstReadyRow < 0` (the
`focusWhenPlaced(navigationFocus)` machinery at lines 95-98 already handles the
rest; `focusRequester` on the nav Box at line 196 correctly delegates to the first
focusable descendant in this Compose version — verified in the
`FocusRequester.findFocusTargetNode` bytecode).

---

### H7 — TV browse text filters: `OutlinedTextField` is a remote dead-end and breaks the single-focus-treatment rule (NAV-08 / NAV-17 family)

**Symptom.** On TV Movies/Series/Anime, when a catalog exposes a
TEXT/NUMBER/DATE control, it renders as a Material `OutlinedTextField` with a
conditional "Apply" `TextButton`. A TV remote can focus it but cannot type (no
on-screen keyboard is attached, unlike the entry flows), and its focus indication
(thin outline) is a second, different focus style in a row of `TvAction` chips.

**Where.** `features/browse/DiscoveryTextFilter.kt:28-31`, reached from
`features/browse/DiscoveryFilters.kt:52-54` (`ControlKind.TEXT, NUMBER, DATE`) which
`BrowseScreen.kt:42-46` passes to `TvBrowseScreen` on TV.

**Why.** `TvBrowseScreen`'s filter row otherwise uses `TvAction` (fill + 4 px ring,
`components/TvFocusTreatment.kt:46-52`). Every other TV text input in the app pairs
a field-like row with `TvEntryKeyboard` (`features/entry/TvEntryKeyboard.kt`) or
`TvSearchKeyboard` — this one has neither.

**Minimal fix.** On TV, render TEXT controls as a `TvEntryField`-style row that
opens the existing `TvEntryKeyboard` (as `TvPasswordPanel`/
`TvSourceIntegrationForm.kt:85-95` do), or suppress TEXT/NUMBER/DATE controls on TV
until a TV board defines them.

---

### H8 — `Key.Escape` handlers never fire on Android TV remotes (latent Back-mapping trap)

**Symptom.** None today — but only by accident of the MainActivity fallback.

**Where.** `features/entry/TvPinScreen.kt:50` (`Key.Escape -> back()`),
`features/player/TvPlayerScreen.kt:65` (`Key.Escape -> back()`), and the guard at
`:61`. On Android the remote's Back button is `Key.Back` (KEYCODE_BACK = 4);
`Key.Escape` (111) only arrives from physical keyboards. The same file *knows* this —
`TvPlayerScreen.kt:78` explicitly special-cases `event.key != Key.Back`.

**Why.** Back currently works because `androidApp/.../MainActivity.kt:46-56`
routes `Key.Back` through the `OnBackPressedDispatcher` → `entryBack` → the same
`back()` lambda (for the player) or `navigate(Screen.Profiles)` (for the PIN screen,
which equals the PIN screen's `back` param). Any future screen that relies only on
`Key.Escape` for Back silently loses it.

**Minimal fix.** Handle `Key.Back` alongside `Key.Escape` in these preview key
handlers (and keep the dispatcher as the single source of truth), or delete the
Escape branches and document that Back is dispatcher-owned.

---

### H9 — TV Search: `focusRow` scroll targets ignore the leading loading item

**Symptom.** While a search is loading with no results, focusing a result row
scrolls the results column one row too high.

**Where.** `features/search/TvSearchScreen.kt:84` adds a leading `TvSearchLoading`
item when `busy && !hasResults && groups.none { it.message != null }`, but the row
index math at `:99` and `:111` only offsets for the error item
(`rowIndex + if (error != null) 1 else 0`).

**Why.** The header item count and the focus-scroll offset are computed from
different conditions.

**Minimal fix.** Compute the offset from the same predicate that adds the leading
items (a small `headerCount` val used by both).

---

### H10 — Dead code in `TvColumnFocus` can drift from the live implementation

**Where.** `components/TvColumnFocus.kt:22-30` — `upFor`/`downFor` are never called
anywhere (verified with grep); `rememberTvColumnItem` at `:46-49` re-implements the
same clamping. Two implementations of one rule will eventually disagree.

**Minimal fix.** Delete `upFor`/`downFor` or have `rememberTvColumnItem` use them.

---

### H11 — Dropdown menu items on TV use the default Material focus indication (NAV-08 family, cosmetic)

**Where.** `components/TvNavigation.kt:61-63` and
`features/browse/DiscoveryChoiceFilter.kt:27-32` — `DropdownMenuItem`s get no TV
focus treatment (no fill flip, no ring), unlike every other control on those
screens. `features/library/TvLibraryControls.kt:44` at least sizes the items for TV.

**Minimal fix.** Style the menu items' focused state (or wrap them in the TV focus
treatment) so the in-menu focus is unmistakable at 3 m.

---

## Verified non-bugs (checked, no action needed)

- **NAV-01 (shelf scroll-back):** `rememberTvShelfScroll`
  (`components/TvShelfScroll.kt`) is layout-info-driven and is used by the home
  shelf (`TvHomeShelf.kt:31`), the episode shelf (`TvEpisodeShelf.kt:46`) and the
  recommendation shelf (`TvRecommendationShelf.kt:14`); all other TV `LazyRow`s use
  `TvFocusScroll` bring-into-view (`TvFocusScroll.kt:14-27`). No variant of the
  left-edge walk-off found in current sources.
- **NAV-27 (late/duplicate `backChanged`):** in the Compose runtime in use
  (1.13.0-alpha02/03), leaving effects' `onForgotten` is dispatched *before*
  entering effects' `onRemembered` (verified in `RememberEventDispatcher`
  bytecode: `leaving` is iterated before `dispatchRememberList(remembering)`), so
  when the screen swaps, the exiting screen's `backChanged(null)` runs before the
  entering screen's registration. TV registrations are single-owner per screen
  (`App.kt` splash `:55`, `TvSignIn.kt:22-25`, `TvHomeScreen.kt:111`,
  `TvPlayerScreen.kt:47`, `TvProfileEditScreen.kt:41`) and Home→Player→Home,
  sign-in step changes, and `playbackKey` changes all keep Back mapped correctly.
  The remaining NAV-27-shaped gap is H2 (focus ownership after popups), not a
  stale-handler race.
- **MainActivity Back mapping** (`androidApp/.../MainActivity.kt:46-56`):
  Player→closePlayer, ProfilePin/ProfileEdit/Home→Profiles, Details→backDetails,
  Sources→backSources, else→Home; `entryBack` takes precedence. Compose popups
  dismiss on Back via `PopupProperties.dismissOnBackPress` (default true, verified
  in `PopupLayout.maybeRegisterBackCallback`).
- **Player key handling** (`TvPlayerScreen.kt:54-90`): `Key.Back` intentionally
  falls through to the dispatcher; the back stack
  (tracks → episodes → next → scrub → hide → close) is consistent; the auto-hide
  timer keys on `state.scrub` so scrubbing never hides controls; `Key.DirectionDown`
  at the timeline intentionally dismisses the controls (bottom-edge exit).
- **No invisible-but-focusable controls found in the main flows:** hidden hero is
  `canFocus = false` while retained (`TvHomeVisibility.kt:36`), the top nav is
  unmounted (not merely faded) while browsing, skeletons and empty-row texts are
  not focusable, the sources-drawer scrim is `canFocus = false`
  (`TvSourcesScreen.kt:79`).
- **Initial focus coverage:** Movies/Series/Anime/Live/Calendar/My List get the
  selected nav item (`TvNavigation.kt:25-28`); Search gets the keyboard
  (`TvSearchKeyboard.kt:18`); Details gets the hero Play
  (`TvDetailHero.kt:30`); Settings gets the first row (`TvSettingsScreen.kt:62-63`);
  Profiles/PIN/Email/Pairing/ProfileEdit all retry until placed (e.g.
  `TvPinScreen.kt:46-53`, `TvProfileEditScreen.kt:42-48`). The exceptions are H3,
  H4, H6.
- **Focus traps that are correct:** the track picker cancels exit
  (`TvPlaybackTrackPicker.kt:35`, `onExit = { cancelFocusChange() }`); the
  multi-choice sheet restores focus on dismiss
  (`DiscoveryMultiChoiceSheet.kt:33-35`, `:70-73`); `TvRequestedFocus`
  (`components/TvRequestedFocus.kt:11-18`) retries one frame, which is sufficient
  for non-lazy targets; `focusWhenPlaced` in `TvHomeScreen.kt:64-68` handles lazy
  placement with a 1.5 s budget.
- **NAV-02 / NAV-03** (composited-scroll inversion, clipped compact logo) are
  recorded as DONE and no regressed code path was found.
- **NAV-05/06** are content issues on browser/phone surfaces with no TV focus
  impact; **NAV-07's** TV counterpart (`TvPlaybackTrackPicker`) has initial focus,
  a modal focus trap and Back-dismissal.

## Issue-ID mapping

| ID | Status in this audit |
|---|---|
| NAV-01 | no open regression found (verified) |
| NAV-02, NAV-03 | done; no regression found |
| NAV-04 | open; TV focus consequence = **H4** |
| NAV-05, NAV-06 | content-only, no TV nav impact found |
| NAV-07 | TV variant verified OK |
| NAV-08 | open instances = **H7**, **H11** |
| NAV-21 | TV analog = **H1** (settings column → sidebar escape) |
| NAV-27 | stale-handler race verified absent; remaining gap = **H2** |

— end of report —
