# Device verification plan — TV navigation (1920×1080 Android TV)

Read-only audit, 2026-10-19 workspace state. No repository file was edited, no
gradle/adb/emulator/build command was run. All claims below cite the file and
line as found in the working tree; no build artefact was trusted.

Repo: `/mnt/ALPH/code/viptv-org`, app: `/mnt/ALPH/code/viptv-org/app`.
Design boards: `design/viptv-design-system/reference/screens/html/tv/*.html`
(geometry quoted from `python3 tools/board_spec.py <BoardId>`, read-only).

---

## 1. What the existing harness does (source review)

### 1.1 `app/tools/tv_focus_audit.py`

DPad-only focus walker on a real device/emulator.

- Launches a **preview scene** with `am start … --ez ui_preview true --es ui_screen S --es ui_variant V`
  (`start_preview`, tv_focus_audit.py:114–118, 4 s fixed wait) or the **live app**
  (`start_live`, :121–124, 8 s wait). Package `org.viptv.experiment`, activity
  `.MainActivity` (:24–25). `Screen.valueOf` failures fall back to Home
  (MainActivity.kt:26–27).
- Per key press: `input keyevent <DPAD_*>`, `sleep 0.3`, then `uiautomator dump`
  to `/sdcard/viptv-focus.xml` and parse (:185–186, :72–105). Compose exposes
  focus via accessibility, so the dump is a truthful view of what the remote can
  focus — for nodes that are **currently composed**.
- Walk shape: four directional sweeps (DOWN/UP, RIGHT/LEFT, UP/DOWN, LEFT/RIGHT),
  each `sweep = max(4, steps//8)` presses, then a random tail (:199–210).
  Default `--steps 80` → 10 presses per direction.
- Records: discovered focusable controls (`known`, grows when new nodes compose,
  :161–170), distinct focus arrivals, and a count of states with **no focused
  node** (`missing_focus`, :174, :189).
- Output: a Markdown report; **`main` always returns 0** (:256). There is no
  pass/fail gate and no threshold on `missing_focus`.

### 1.2 `app/tools/tv_ab.sh`

Frame-pacing A/B benchmark, not a navigation tool. Live app, fixed path:
force-stop → start → 13 s → `keyevent 23` → 13 s → 2×`keyevent 20` (:17–22),
then bursts of RIGHT/LEFT with `dumpsys gfxinfo` aggregation (:24–28). Default
serial is the physical TV `192.168.88.30:5555` (:11). It proves frame budget
percentiles/missed-vsync, nothing about focus order or reachability.

### 1.3 Preview entry contract (what a device pass can drive without auth)

`MainActivity.kt:25–36` routes the preview extras to `UiPreview`, which renders
production TV components with fixtures (`preview/UiPreview.kt:41–136`,
`preview/PreviewData.kt`). Navigation between preview screens is wired
(`go(Screen.X)`), so **OK/BACK chains across screens are exercisable in the
preview harness**. Fixture cardinalities (for completeness checks):

- Home: billboard 5 slides + "Continue Watching" 5 cards + "New this week" 8
  cards (PreviewTvHome.kt:7–24).
- Movies/Series/Anime: 14 cards, "All genres" + 8 genre chips (PreviewTvHome.kt:13–28).
- Search: 8 posters + 3 channels, 3 suggestions (UiPreview.kt:118–119, PreviewData.kt:22).
- Details: 4 episodes, 8 recommendations (PreviewTvDetails.kt:10–29).
- Sources: 5 candidates (PreviewTvSources.kt:7–12).
- Live: 6 channels (PreviewLive.kt:18).
- Library: 14 cards (PreviewTvLibrary.kt:10–15).
- Profiles: 4 tiles + Add (PreviewData.kt:12–14).
- Settings variants: Playback/Subs/Addons/Sources/SignOut→Account
  (PreviewTvSettings.kt:18–20).
- Player variants: Controls/Buffer/BufferFast/Scrub/ScrubNoImage/Tracks/
  TracksInvalidFocus/TracksAudioOnly/TracksEmpty/Skip/SkipHalf/SkipEnd/Next/
  Error/ErrorSourceOnly/ErrorBackOnly/Native (PreviewTvPlayer.kt:13–19).
- Note: `Screen.RecoveryCodes` and `Screen.Discover` have no TV branch in
  `UiPreview.kt:129–134` — they fall through to `else → TvHomeScreen`. Do not
  use them as TV pass targets (and this fall-through itself is worth a ticket).

---

## 2. What the current harness cannot prove, and how this plan covers it

| # | Gap (evidence) | Why the harness can't see it | Coverage in this plan |
|---|---|---|---|
| G1 | **Focus order correctness.** The audit records *arrivals*, not *order* (tv_focus_audit.py:161–198). Two neighbours swapped, or focus landing on the wrong card after a wrap, still "pass". | No per-step oracle. | Layer 2 oracle walks: every key press has an expected focused label; dump must match exactly (§4.2). |
| G2 | **Activation, BACK, typing.** The walk only sends DPAD directions (tv_focus_audit.py:200–201). OK (23), BACK (4), digits, text are never sent, so dialogs, sheets, panels, cross-screen navigation and PIN/IME entry are never entered. | Key list is directional only. | Layer 3 (popup/dialog matrix), Layer 4 (OK/BACK chains), Layer 5 (IME), all scripted (§4.3–4.5). |
| G3 | **No exit gate.** `main` returns 0 unconditionally (tv_focus_audit.py:256); `missing_focus` is informational. | No assertion machinery. | The oracle runner must exit non-zero on any mismatch/no-focus state; per-screen PASS/FAIL in the report (§7). |
| G4 | **Offscreen lazy items.** `uiautomator dump` only lists *composed* nodes; LazyRow/LazyColumn compose only the viewport (TvHomeShelf.kt:27–31 comment; LazyVerticalGrid, LazyColumn). "focusable controls: N" is discovered-only, so N is always ≤ visible set — a shelf item that never composes cannot be counted or focused. | Dump has no knowledge of uncomposed items. | Completeness checks against fixture counts (§1.3): sweep to shelf end and assert the *last fixture card* is focused; assert count of distinct card titles reached == fixture count per shelf. |
| G5 | **Focus on clipped/off-viewport items.** The audit never checks that the focused node's bounds are on-screen (bounds are used only for identity matching, tv_focus_audit.py:127–138). The known bug class "focus walks off the left edge" (ISSUES.md NAV-01, OPEN_TASKS.md) is exactly this. | No visibility assertion. | Oracle asserts focused bounds ⊆ [0,1920]×[0,1080] and (for shelf cards) left ≥ 96 px; NAV-01 regression step: 10×RIGHT then 10×LEFT, first card must be focused at x≈96. |
| G6 | **Popups/dialogs/second windows.** `DropdownMenu` (TvNavigation.kt:48–50), `AlertDialog` (TvSettingsScreen.kt choice/unavailable/browserFallback/installing; TvPinScreen.kt forgot; TvDetailsScreen.kt:115–118 trailer), `Popup(focusable=true)` sources drawer (TvSourcesScreen.kt:45–46), track/episode picker panels (TvPlayerScreen.kt:112–118) render in separate windows; `uiautomator dump` reflects the *active* window, and the sweep never opens any of them. | No OK presses; window model untested. | Layer 3: open each popup via its OK trigger, assert its labels appear in the dump and focus is inside it; BACK must dismiss and restore focus to the pre-open control. |
| G7 | **IME.** The only real text field on TV is the Settings add-on address `OutlinedTextField` (TvSettingsScreen.kt:96–101). `input keyevent` never opens it; the audit never types. Also `uiautomator dump` frequently fails to reach idle state while the IME animates. | No typing. | Layer 5 IME protocol: focus field → `dumpsys input_method` shows input → `input text` → assert field text and focus → BACK closes IME → dialog focus restored (§4.5). |
| G8 | **Timing.** 0.3 s settle (tv_focus_audit.py:185) is shorter than the animations it must outrun: shelf scroll tween 300 ms (TvShelfScroll.kt:36), row motion tween 350 ms and billboard tween 450 ms (TvHomeScreen.kt:119, 137, 154), action fill tween 150 ms (TvAction.kt:30–31). A dump mid-animation returns moving bounds → identity mismatch or "no focused node". A failed dump (`ERROR: could not get idle state`) is silently turned into "no focused node" (raw-not-XML → `[]`, tv_focus_audit.py:75–79). | Fixed sleep; no dump retry. | Oracle: wait ≥600 ms *or* poll until the focused node's bounds are stable across two consecutive dumps; a dump failure is *inconclusive* (retry ×3), never counted as no-focus. |
| G9 | **Key repeat/hold.** Real remotes auto-repeat; `input keyevent` sends discrete events. Player timeline handles KeyDown/KeyUp asymmetrically (TvPlayerScreen.kt:62–64, 71–78) and the shelf first-card Up handler only fires on KeyDown (TvHomeShelf.kt:55–56). | Single-shot keyevents. | Layer 6: `input keyevent --longpress 22` and batched `input keyevent 22 22 22`; assert one timeline step per repeat and no shelf overshoot. |
| G10 | **Screen-change blindness.** In preview, OK navigates (`go()`), in the live app BACK exits screens; the audit would keep sweeping the wrong scene without noticing (no screen identity is ever asserted). | No landmark check. | Oracle asserts a per-screen landmark string in every dump (e.g. "Who's watching?", "Choose a source", the timeline node). |
| G11 | **Initial-focus races.** 4 s fixed wait vs delayed fixtures (SeriesDelayed 2 s, LoadingThenReady 3 s) and layout-attached focus (TvProfiles waits for lazy layout, TvProfiles.kt:30–38; TvHomeScreen `focusWhenPlaced` 1500 ms, :77–82). Early dumps can show no focus although focus arrives later. | One-shot start wait. | Assert focus at t=1 s AND after settle; assert exactly one focused node (zero = dead remote; more than one = window merge artefact). |
| G12 | **Overscan/insets.** Boards are full-bleed 1920×1080; the app pads `WindowInsets.safeDrawing` (UiPreview.kt:60). On a real TV the whole coordinate system shifts, so board-px comparisons silently fail. | Dump has bounds but nothing compares them to the boards. | Preflight measures `wm size` and insets; board-px cross-checks are applied only when insets are 0, otherwise recorded as a device-specific offset. |
| G13 | **Real-data surfaces.** Preview fixtures never exercise `next_cursor` paging, refresh, provider errors (OPEN_TASKS "Infinite/lazy carousels"), real sign-in, or the QA backend. | Preview is fixed-fixture by design. | A separate live pass against the QA runtime (`viptv_qa` supervisor + app QA origin) is required before any "zero navigation bugs" claim for the shipped app; the automated device pass below proves the *UI navigation machine*, not the data. |
| G14 | **Focus visuals.** Ring vs shape-change (ISSUES.md NAV-08, TVUI-16) is invisible to uiautomator. | Dumps carry no pixels. | Optional Layer 7: `tv_state_shot.sh`-style screencaps per step + `compare_images` vs board. |
| G15 | **Physical remote/HID differences** (keycode mapping, repeat cadence, dedicated buttons). | ADB keyevents are synthetic. | Final manual smoke on the physical TV (default serial in tv_ab.sh:11); the ADB pass is the automated core. |

---

## 3. Device targets and preflight (run later, not now)

- **Target A (primary):** disposable Android TV emulator, TV profile
  (`emulator-5582` per docs/UI_PREVIEW.md and docs/TV_NON_PLAYER_QA.md).
  `MainActivity` decides TV mode via `UiModeManager` (MainActivity.kt:21–22), so
  a phone-profile emulator renders the handheld UI and invalidates the pass.
- **Target B (physical):** the 1080p TV at `192.168.88.30:5555`
  (tv_ab.sh:11, tv_cycle.sh) — only for the manual smoke and inset measurement.
- Preflight (read-only): `adb shell wm size` → must print `1920x1080`;
  `wm density`; `dumpsys window | grep -i inset` for safe-drawing insets;
  the experimental APK already installed (no build in this audit).

Key codes used throughout: 19 UP, 20 DOWN, 21 LEFT, 22 RIGHT, 23 OK/DPAD_CENTER,
4 BACK, 66 ENTER, 67 BACKSPACE, 111 ESCAPE, digits 7–16, 85 MEDIA_PLAY_PAUSE.

Launch template (per screen):

```
adb -s <serial> shell am start -S -n org.viptv.experiment/.MainActivity \
  --ez ui_preview true --es ui_screen <Screen> [--es ui_variant <Variant>]
```

---

## 4. The pass, layer by layer

### 4.1 Layer 1 — baseline sweep (existing tool, retuned)

Run per screen to keep the arrival/no-focus telemetry comparable with history:

```
python3 tools/tv_focus_audit.py --serial <serial> --screen Home  --steps 200 --seed 7 --out /tmp/focus-tv-home.md
```

Steps budget: `sweep = steps//8` presses per direction (tv_focus_audit.py:199).
Shelves/grids need the full fixture traversed: 14-card shelf → sweep ≥ 15 →
`--steps 160` minimum; 200 gives margin for the 7-column grids and the settings
column. Use `--steps 120` for entry/PIN/Settings/Live. Per-screen values are in
§5. Interpretation rules for its report (fixes for G3/G8/G11 live in the oracle,
not the tool): treat `states with no focused node > 0` as a *failure to
investigate*, not as pass; treat `focusable controls` only as a lower bound (G4).

### 4.2 Layer 2 — oracle walk (new runner, specified here)

For each screen: a table `(keyevent, expected focused label)` executed as
`keyevent → settle (≥600 ms or bounds-stable poll) → uiautomator dump → assert`.
Assertions per step:

1. exactly one `focused="true"` node;
2. its folded label (text or content-desc; the audit's label-folding logic,
   tv_focus_audit.py:86–99, is the reference) *contains* the expected string;
3. its bounds are fully on-screen; for shelf/grid cards left ≥ 96 px and
   top ≥ 244 px (board insets, §5);
4. the screen landmark string is present in the same dump (G10);
5. dump failure → retry ×3, then mark the step *inconclusive* (never "no focus").

Expected labels below are derived from the source (cited) and the board
geometry (`board_spec.py`).

### 4.3 Layer 3 — activation/popup matrix (G6)

Every TV popup in the source, each with open-key, expected focus-in-popup, and
dismiss/focus-restore assertion:

| Popup | Trigger | Expected in dump | Dismiss |
|---|---|---|---|
| Nav avatar `DropdownMenu` (TvNavigation.kt:48–50) | from nav, RIGHT to avatar (x≈1768, Tv3Home board), OK | "Switch profile", "Settings"; focus on first item | BACK → focus back on avatar |
| Sources drawer `Popup` (TvSourcesScreen.kt:45) | Details → OK on Play | drawer 960 dp wide, right-aligned; focus on first source row "LordStreams" (auto-focus :63–76) | BACK → Details hero Play |
| Settings choice dialog (TvSettingsScreen.kt:58, 92) | OK on a preference row | values list; focus on selected value | BACK → focus returns to that row |
| Settings "unavailable"/dashboard `AlertDialog` (:86–89) | OK on a row whose section is unavailable | title + OK button | BACK/OK → row |
| Settings add-on install `AlertDialog` + `OutlinedTextField` (:92–110) | Add-ons → "Add an add-on" | dialog; text field focusable | IME protocol §4.5, then Cancel |
| Settings leave confirmation (TvLeaveConfirmation, :84) | variant SignOut (initialLeave, PreviewTvSettings.kt:24) | confirm/cancel; **sidebar must not be focusable** (modal flag :56) | Cancel |
| Details trailer notice (TvDetailsScreen.kt:115–118) | OK on "Trailer" | "No trailer is available…" + OK | BACK → Trailer |
| PIN "Forgot PIN?" dialog (TvPinScreen.kt:96–99) | OK on "Forgot PIN?" | dialog + OK | BACK → Forgot PIN? |
| Player track picker (TvPlaybackTrackPicker.kt:29–40) | controls visible → OK on "Audio & subtitles" | track rows, initial focus first row (:59) | BACK/Escape → pill re-focused |
| Player episode picker (TvPlayerEpisodePicker.kt:23–40) | OK on "Episodes" pill | episode list, focus episode 1 | BACK → pill |
| Player next-episode panel (TvNextEpisode) | variant Next | Play next / Cancel | Cancel |

### 4.4 Layer 4 — cross-screen OK/BACK chains (G2, G10)

Preview navigation graph (UiPreview.kt wiring):

- C1: Home → card OK → Details → Play OK → Sources sheet → source OK → Player →
  BACK → Home (Player close = `go(Screen.Home)`, PreviewTvPlayer.kt:70).
  Assert landmarks in order: hero synopsis → "Choose a source" → player timeline.
- C2: Profiles → tile OK → Home → BACK → Profiles (MainActivity BackHandler for
  `ProfilePin/ProfileEdit/Home → Screen.Profiles`, MainActivity.kt:48–56).
- C3: Live → channel OK → Player → BACK → Live
  (`liveTitle` branch, UiPreview.kt:101–104).
- C4: Settings → sidebar OK (section switch, focus moves to first content row) →
  BACK → Home (preview BackHandler).
- C5: ProfilePin valid 4 digits + OK → Home (synthetic callback); BACK → Profiles.
- C6: Details → BACK (backDetails) — in preview, BACK returns Home via the
  preview BackHandler; assert the billboard is back.

### 4.5 Layer 5 — IME and text entry (G7)

1. Settings → Add-ons → "Add an add-on" (dialog opens, TvSettingsScreen.kt:92).
2. OK into the address field; assert `adb shell dumpsys input_method` shows the
   IME; note the dump may now report the IME window — treat as expected.
3. `adb shell input text "https://addon.example/manifest.json"`, assert the field
   text in the dump.
4. BACK (4) → IME closes; assert the dialog's "Add"/"Cancel" buttons are focused
   (focus restoration, not a dead remote).
5. TV keypads that do *not* use the IME are covered natively: PIN digits via
   hardware keycodes 7–16 (TvPinScreen.kt:48–56 numberKeys handler; masked boxes
   expose `contentDescription = "PIN digit N, filled"`, TvPinScreen.kt:72), and
   the on-screen keyboards (search/entry) via OK on keys.

### 4.6 Layer 6 — key repeat and hold (G9)

- Player (Controls): `input keyevent --longpress 22` → timeline must step a
  bounded amount (per-repeat step; no runaway scrub), then `--longpress 21` back.
- Home shelf: 10 rapid `input keyevent 22` in one shell call → shelf scrolls to
  the trailing window (1 leading peek + 2 trailing, TvShelfScroll.kt:30–44);
  then 10×21 → card 0 focused and on-screen (NAV-01 acceptance,
  ISSUES.md:41 "10×left on TV").
- Entry keyboards: hold RIGHT across a key row → focus stops at the row end, no
  wrap to the next row mid-hold.

### 4.7 Layer 7 — live-app pass (G13, separate milestone)

On the QA runtime (`viptv_qa` supervisor, QA origin, throwaway account):
sign-in (pairing/EmailCode), real shelves with paging (`next_cursor`), refresh
and error states, playback controls against a real stream. This layer is what
converts "preview navigation machine is clean" into "the app has no navigation
bugs"; it is out of scope for the preview pass but must be scheduled.

---

## 5. Per-screen device pass — commands and expected focus order

Notation: `↓/↑/←/→` = keyevent 20/19/21/22, `OK` = 23, `BACK` = 4. Board refs
are `board_spec.py` output for the named board. `t0` = first dump after the
settle window.

### 5.1 Home — `--es ui_screen Home`

Baseline: `tv_focus_audit.py --screen Home --steps 200`.
Board: Tv3Home (Play 96,648 220×72; Add-to-My-List 336,648; More info 428,648;
Continue Watching title y=824; nav y=38–94; avatar 1768,38).

| Step | Key | Expected focus |
|---|---|---|
| t0 | — | hero **Play** (playFocus; TvHomeScreen.kt:107; TvHero requestFocus `!browsing && error==null`) |
| 1 | → | **Add to My List** (round, 72×72) |
| 2 | → | **More info** |
| 3 | ← ×2 | back to **Play** |
| 4 | ↓ | Continue Watching card 0 **Monster: The Jeffrey Dahmer Story** (firstCard passed as hero's down, TvHomeScreen.kt:155) |
| 5 | → ×4 | cards 1–4 (5-card shelf; scroll keeps 1 leading + 2 trailing peek, TvShelfScroll.kt:30–44; visible = ⌊(1920−96)/404⌋ = 4) |
| 6 | ← ×4 | card 0 again, x ≈ 96, fully on-screen (NAV-01) |
| 7 | ↑ | **Play** (explicit Up handler on card 0 only, TvHomeShelf.kt:55–56) |
| 8 | ↓ ↓ | **New this week** first visible card (row motion tween 350 ms — wait ≥600 ms, G8) |
| 9 | ↓ | stays on last row (no dead state; below is content padding) |
| 10 | BACK | **Play** (returnHero; entryBack wired at TvHomeScreen.kt:104–110 + MainActivity.kt:43–56) |
| 11 | ↑ (from hero) | nav **Home** tab (TopNavigation→TvNavigation, Navigation.kt:28–30) |
| 12 | → | nav **Movies** → **Series** → **Live** → **Calendar** → **My List** (desktopDestinations minus Discover/History, TvNavigation.kt:44) |
| 13 | → | **profile avatar** (x≈1768) |
| 14 | OK | DropdownMenu **Switch profile** (Layer 3) |
| 15 | OK (card) | Details screen (Layer 4 C1) |

Variants: `Loading` (t0 focus = nav **Home** tab, navigationFocus epoch,
TvHomeScreen.kt:92–106 — assert no dead focus while loading);
`LoadingThenReady` (assert focus at t≈2 s is nav, at t≈6 s is hero **Play** —
exactly one transition, no dead window, G11).

### 5.2 Movies / Series / Anime — `--es ui_screen Movies` (Series, Anime same route)

Baseline: `--steps 200`. Board: Tv3Movies (chips y=140 h=64; cards y=244, 232 px
wide, 248 px pitch, 7 columns; note the app renders 56 dp chips vs 64 board px —
geometry deltas are tracked in OPEN_TASKS, not this pass).

| Step | Key | Expected focus |
|---|---|---|
| t0 | — | nav **Movies** tab (TvNavigation.kt:29–32 requests focus for Movies/Series/Anime/Live/Calendar/Library) |
| 1 | ↓ | genre chip **All genres** (LazyRow; Column `onEnter → filterFocus`, TvBrowseScreen.kt:61–62) |
| 2 | → ×8 | **Action … Documentary** (8 chips) |
| 3 | ← ×8 | back to **All genres** |
| 4 | ↓ | grid card index 4 **The Invite** (enterResults: scrollToItem(4) + first.requestFocus, TvBrowseScreen.kt:69–77; board x≈840, y≈628) |
| 5 | → | cards 5, 6 … 13 (14-card fixture; grid brings rows into view, TvFocusScroll VisibleControlScroll) |
| 6 | ↓ | row 2 same-column card |
| 7 | ↑ | row 1 same-column card |
| 8 | ↑ (row-1 card, index<7) | **All genres** chip (`focusProperties up = filterFocus` for index<7, TvBrowseScreen.kt:81–82) |
| 9 | ← (from col 0) | stays (grid edge clamp, no wrap, no dead state) |
| 10 | OK (card) | Details |

Variants: `Loading` (chips remain focusable with no poster target,
UI_PREVIEW.md "Down remains safe"); `Empty` (focus stays on chips; the empty
text is not focusable — assert no dead state).

### 5.3 Search — `--es ui_screen Search`

Baseline: `--steps 200`. Board: Tv3Search (field 96,140 520×72 — display only,
not focusable; keyboard y=236; suggestions y=828; results x=700 y=196, 212 px
cards, 228 px pitch; channels y=640, 356 px wide).

| Step | Key | Expected focus |
|---|---|---|
| t0 | — | keyboard key **h** (initial requester, TvSearchKeyboard.kt:23,33) |
| 1 | → / ← | same-row keys |
| 2 | ↓ / ↑ | key rows (3 letter rows + bottom row) |
| 3 | OK (letter) | term gains the letter (preview memory); focus stays on the key; query field shows "the w…" |
| 4 | ↓ ↓ ↓ | **suggestions**: "the whisper man" → "the wild robot" → "the white lotus" (52 dp actions, TvSearchScreen.kt:86–96) |
| 5 | OK (suggestion) | results update; focus stays on suggestion |
| 6 | → (from keyboard right edge) | results card row 1 card 1 (x≈700) |
| 7 | → | results cards (8 posters; LazyRow with focusRestorer, TvSearchScreen.kt:106) |
| 8 | ↓ | next result group row (LazyColumn scrolls; row index tracked, :80–84) |
| 9 | ↓ (from last) | **Channels** row cards (356 px wide, TvSearchScreen.kt:120–134) |
| 10 | ↑ (from results) | back into the keyboard/suggestion column — assert a focusable, never dead |

Variant: `Loading` (placeholders; keyboard keeps focus).

### 5.4 Details — `--es ui_screen Details` (Movie default)

Baseline: `--steps 200`. Boards: Tv3Title (Resume/Play 96,648; Add 536; Trailer
628; Play-from-beginning 720), Tv3Episodes (season tabs y=154; episodes y=236,
448 px wide, 472 px pitch).

| Step | Key | Expected focus |
|---|---|---|
| t0 | — | **Play** (playFocus; requestInitialFocus default true) |
| 1 | → | **Add to My List** → **Trailer** → **Play from beginning** |
| 2 | OK (Trailer) | AlertDialog "No trailer is available…" (Layer 3; TvDetailsScreen.kt:115–118) |
| 3 | BACK | **Trailer** re-focused |
| 4 | ↓ | recommendations shelf first card (movie has no episodes; focusRow(1), TvDetailsScreen.kt:96–101) |
| 5 | → | 8 recommendation cards (248 px pitch, TvRecommendationShelf) |
| 6 | ↑ | **Play** (returnHero; no episodes → hero) |
| 7 | OK (Play) | Sources drawer (Layer 4) |

Variants: `Series` — ↓ from hero → **episode shelf** (returnEpisodes; season
tabs then 4 episodes 2-column; UP from episodes → hero; ↓ → recommendations;
"animateScrollToItem" replaces hard-coded offsets — this is the item OPEN_TASKS
marks *needs on-device verification*). `SeriesSeasons` (resume in season 2:
assert the season-2 tab/row resolves). `SeriesDelayed` (episodes arrive after
2 s: assert no dead focus during the gap, then ↓ reaches the episodes).

### 5.5 Sources — `--es ui_screen Sources` (drawer)

Baseline: `--steps 120`. Board: Tv3Sources (hero Play 96,668; drawer is an app
construct: 960 dp wide, right-aligned, TvSourcesScreen.kt:91–93).

| Step | Key | Expected focus |
|---|---|---|
| t0 | — | source row 1 **LordStreams · WEB-DL · HEVC · 5.1 · 18.4 GB** (auto-focus target, TvSourcesScreen.kt:63–76) |
| 1 | ↓ | source rows 2–5 (5-row fixture; LazyColumn scrolls) |
| 2 | ↑ ×4 | back to row 1; ↑ again → **provider filter chip** (row-0 up = providerFocus, :102) |
| 3 | ↑ | stays in the drawer (scrim `canFocus=false`, :79 — must not fall out to Details) |
| 4 | ← | stays (popup captures; assert focus unchanged — no fall-through to the page behind) |
| 5 | OK (filter chip) | first visible source row re-focused (:93–95) |
| 6 | BACK | drawer dismisses → Details **Play** (preview back = go(Details), UiPreview.kt:107) |
| 7 | OK (source) | Player (go(Player)) |

Variants: `Loading` (2 sources + 3 shimmer rows — shimmer nodes have
`contentDescription = "Loading source"` but must **not** be focusable,
TvSourcesScreen.kt:103–106; assert focus skips them); `LoadingEmpty`/`Empty`
(focus on the **All** filter chip when nothing is selectable,
TvSourcesScreen.kt:67).

### 5.6 Player — `--es ui_screen Player --es ui_variant Controls`

Baseline: `--steps 120`. Board: Tv3PlayerControls (title y=64; pills y=846:
Episodes, Audio & subtitles, Source, Next episode right; timeline y=962).

| Step | Key | Expected focus |
|---|---|---|
| t0 | — | **timeline** (duration 3132000 > 0 → timeline.requestFocus, TvPlayerScreen.kt:44–52) |
| 1 | ← / → | timeline steps (position changes; dump label 14:02 ↔ step) |
| 2 | ↑ | controls reveal; **Audio & subtitles** pill (audio focus, :47–50) |
| 3 | ← / → | pills **Episodes** ↔ **Audio & subtitles** ↔ **Source** ↔ **Next episode** (bottom row y≈846) |
| 4 | ↓ | **timeline** (pill down = timeline, TvPlayerControls.kt:48–49, 62–64) |
| 5 | ↓ | controls hide; **root** focused (backdrop focusable, :81–83) |
| 6 | OK / 23 | reveal + play/pause toggle (:71–74) |
| 7 | ↑ (root) | reveal with audio focus (:84–87) |
| 8 | OK (Audio & subtitles) | track picker panel; focus first row (Layer 3) |
| 9 | BACK / ESC | panel closes → **Audio & subtitles** re-focused |
| 10 | BACK | player exits → preview Home (PreviewTvPlayer.kt:70) |

Variants (each with its own oracle row set): `Scrub`/`ScrubNoImage` (LEFT/RIGHT
propose a seek with timestamp overlay at 753,888 per Tv3PlayerScrub; OK commits;
DOWN cancels, :71–78); `Tracks` (+`TracksInvalidFocus`/`TracksAudioOnly`/
`TracksEmpty` — panel focus with stale/missing tracks, UI_PREVIEW.md);
`Skip`/`SkipHalf`/`SkipEnd` (countdown rail; OK seeks to skip end);
`Next` (Play next / Cancel); `Error`/`ErrorSourceOnly`/`ErrorBackOnly`
(failure screen: retry/source/back buttons exist per variant; BACK exits);
`Buffer`/`BufferFast` (overlay: assert root/hero focus is never dead; BufferFast
is ready after 250 ms). `Native` uses a public network MP4 — keep it out of the
focus oracle (network-dependent); it prints `VIPTV_TV_PLAYER_QA` facts for a
separate runtime check (PreviewTvPlayer.kt:58–67).

### 5.7 Live — `--es ui_screen Live`

Baseline: `--steps 160`. Board: Tv3Live (hero info 96,130; preview card
1184,120 640×360; categories y=512; EPG y=592+; 6 channels).

| Step | Key | Expected focus |
|---|---|---|
| t0 | — | first EPG channel row **WNBC** (first/onEnter wiring, TvLiveScreen.kt:42) |
| 1 | ↓ | channel rows 2–6 (WABC, WPIX, WNYW, WCBS, WLIW); LazyColumn/guide scrolls |
| 2 | → | program cells within the row |
| 3 | ↑ (row 0) | **category filter** chips (row-0 up = filter, TvLiveScreen.kt:78–79) |
| 4 | ← / → | categories All / News / Sports / … (chips y≈512) |
| 5 | ↓ | first visible channel of the filtered set |
| 6 | ↑ ↑ | hero **Watch live** card (1184,120) |
| 7 | OK (channel or hero) | Player (watchLive → live player scene, UiPreview.kt:52–56) |

### 5.8 Library — `--es ui_screen Library` (My List)

Baseline: `--steps 200`. Board: Tv3MyList (chips y=140; sort "Last watched"
right-aligned; cards y=236, 232 px, 248 px pitch; 14 fixture cards).

| Step | Key | Expected focus |
|---|---|---|
| t0 | — | filter chip **All** (selectedFilter; onEnter, TvLibraryScreen.kt:40) |
| 1 | → | **Movies** → **Series** chips; → → far right **Last watched** sort |
| 2 | ↓ | card 1 **The End of Oak Street** (x≈96, y≈236) |
| 3 | → ×13 | all 14 cards in reading order (grid bring-into-view) |
| 4 | ← ×13 | back to card 1, on-screen |
| 5 | ↑ (row-1 card, index<7) | **All** chip (up = selectedFilter for index<7, TvLibraryScreen.kt:59–60) |
| 6 | OK | Details |

Variants: `Empty` (focus the clear-filters/back action, no dead state);
`Unavailable` (same).

### 5.9 History — `--es ui_screen History`

Baseline: `--steps 120`. No TV board exists (UI_INVENTORY.md §1 — TV History is
a missing board), so expected order is source-derived only.

| Step | Key | Expected focus |
|---|---|---|
| t0 | — | first history entry (first requester; or **Clear** button when empty, TvHistoryScreen.kt:48–53) |
| 1 | ↓ | entries in watch order; list scrolls |
| 2 | → | per-row affordances (row.left wiring, TvHistoryScreen.kt:110–111) |
| 3 | ← | back to the row's primary target |
| 4 | OK | Details |

### 5.10 Calendar — `--es ui_screen Calendar`

Baseline: `--steps 160`. Board: SkTvCalendar data (no Tv3 board; UI_INVENTORY
"Missing boards: TV Calendar").

| Step | Key | Expected focus |
|---|---|---|
| t0 | — | date rail first day (onEnter → date, TvCalendarScreen.kt:44,50–53) |
| 1 | → | the 4 fixture days (14, 15, 17, 19; PreviewTvCalendar.kt:8) |
| 2 | ↓ | that day's release row (down = selectedRow, :53) |
| 3 | ↓ | **Watch E481** action on the focused release (actions fixture) |
| 4 | ↑ | back to the **date** (verified behaviour, TV_NON_PLAYER_QA.md:10–11 "action Up returns to the date") |
| 5 | OK (Watch) | Details/Sources route |

### 5.11 Settings — `--es ui_screen Settings --es ui_variant Playback` (Subs/Addons/Sources/SignOut)

Baseline: `--steps 120` per variant. Board: Tv3SetPlayback (sidebar rows 440×72
at x=72, y=196–836, 80 px pitch; sections Account, Profiles, Playback,
Subtitles & audio, Add-ons, Source integrations, Display, Parental controls,
About — TvSettingsModels.kt:3–6).

| Step | Key | Expected focus |
|---|---|---|
| t0 | — | first content row of Playback (TvRequestedFocus first, TvSettingsScreen.kt:57–59) |
| 1 | ↓ | content rows in order — **clamped at the last row** (TvFocusColumn: Up/Down never fall out to the sidebar, TvColumnFocus.kt:8–15 — this is the core settings assertion) |
| 2 | ↑ | back to first row; ↑ again stays (clamp) |
| 3 | ← | **sidebar** section rows (TvSettingsRow left = sidebar) |
| 4 | ↓ ↑ | sidebar sections; RIGHT → back into content (sidebar right = content, TvSettingsSidebar.kt:35) |
| 5 | OK (sidebar row) | section switches; focus lands on that section's first content row |
| 6 | OK (preference row) | **TvSettingsChoiceDialog** with values (Layer 3); BACK restores row focus |
| 7 | BACK | preview Home (MainActivity.kt:43–56) |

Variant specifics:
- `Subs`: LEFT from the first choice traverses **Exact size → Background →
  Color** choices before exiting from the first option (verified behaviour,
  TV_NON_PLAYER_QA.md:13–14; TvSubtitleChoiceRow left = sidebar at index 0,
  TvSubtitleSettings.kt:80–82).
- `Addons`: toggle rows; "Add an add-on" → install dialog → IME protocol (§4.5).
- `Sources`: `SourceIntegrationSettings` rows (TMDB key / Xtream / M3U).
- `SignOut`: leave-confirmation modal opens at t0 (initialLeave,
  PreviewTvSettings.kt:24); **sidebar must be unfocusable while modal**
  (modal flag, TvSettingsScreen.kt:56); Cancel restores normal order.

### 5.12 Profiles — `--es ui_screen Profiles` (Locked/Empty)

Baseline: `--steps 80`. Board: Tv3Profiles (5 tiles y=380, 200 px, 256 px pitch;
Manage y=780).

| Step | Key | Expected focus |
|---|---|---|
| t0 | — | tile 1 **vynxc** (lazy-layout-safe initial focus, TvProfiles.kt:30–38) |
| 1 | → | **zayne** → **cedes** → **wasim** → **Add profile** |
| 2 | ↓ | **Manage profiles** (down = manageFocus, TvProfiles.kt:51, 58–59) |
| 3 | ↑ | back to the nearest tile |
| 4 | OK (tile) | Home (preview select); Locked variant: locked tile OK → ProfilePin |
| 5 | OK (Add profile) | ProfileEdit |
| 6 | OK (Manage) | management mode; Done returns |

Empty variant: t0 focus = **Add profile** tile (UiPreview empty list wiring,
UiPreview.kt:123–127).

### 5.13 ProfilePin — `--es ui_screen ProfilePin` (Empty)

Baseline: `--steps 80`. Board: Tv3Pin (keypad y=540; Forgot PIN? y=920).

| Step | Key | Expected focus |
|---|---|---|
| t0 | — | keypad **5** (first requester on '5', TvPinScreen.kt:88) |
| 1 | ↓ ↑ ← → | keypad 1–9, OK, 0, Erase (OK is a Spacer until 4 digits, :87) |
| 2 | digits 7–16 (hardware) | PIN boxes fill: `contentDescription "PIN digit N, filled"` (onPreviewKeyEvent numberKeys, TvPinScreen.kt:48–56) |
| 3 | 67 (BACKSPACE) | erases a digit |
| 4 | ↓ ↓ ↓ ↓ | **Forgot PIN?** (y≈920) |
| 5 | OK | AlertDialog (Layer 3); BACK → Forgot PIN? |
| 6 | 4 digits + OK | Home (synthetic callback) |
| 7 | BACK | Profiles |

Empty variant starts with zero digits (UI_PREVIEW.md).

### 5.14 ProfileEdit — `--es ui_screen ProfileEdit`

Baseline: `--steps 80`. Board: Tv3ProfileEdit.

| Step | Key | Expected focus |
|---|---|---|
| t0 | — | **Name** row (targets[Name], TvProfileEditScreen.kt:30–32) |
| 1 | ↓ | rows in order (rating, language, PIN, avatar via avatarFocus, Delete) |
| 2 | ↑ | reverse; clamp at top/bottom |
| 3 | OK (Delete) | confirmation → returns to Profiles (preview) |
| 4 | BACK | Profiles (native Back, UI_PREVIEW.md) |

### 5.15 Connect family — `--es ui_screen Connect --es ui_variant <V>`

Baseline: `--steps 80` each. Boards: Tv3Welcome (buttons y=584), Tv3SignIn
("or" 930,500; code panel right), Tv3SignInEmail/Code/Password.

| Variant | t0 focus | Walk |
|---|---|---|
| (default) Welcome | **Sign in** (TvWelcome.kt:33–36) | → **Use without an account** (local ≠ null in preview, UiPreview.kt:96–99); OK → Choice / Profiles; BACK from Choice → Welcome (step machine, TvSignIn.kt:18–24) |
| `Email` | keyboard (**@gmail.com** key when the draft is present — Gmail-key focus, UI_PREVIEW.md; TvEntryKeyboard.kt:24–25 initialSuffix) | rows "1234567890/qwertyuiop/asdfghjkl@/zxcvbnm.-_" + suffix row (Shift, #+=, Space, .com, @gmail.com); OK types into the draft; Next → missing code-service state; BACK → email entry |
| `EmailCode` | first digit key / code field | digits + Verify/Resend; BACK → Email (UI_PREVIEW.md) |
| `Pairing` / `PairingUnavailable` | **New code**/refresh (TvPairingSignIn) | BACK → Email; unavailable has no QR |
| `SignedIn` / `SignedInHold` | greeting **Continue** | SignedIn OK → Profiles; Hold stays |
| `Splash` | splash's focusable (TvSplash.kt:38) | assert a focusable exists — no dead remote during animation |
| `Iptv` | username field | Xtream form fields + keyboard; Connect → absent-provider notice; BACK returns (UI_PREVIEW.md) |

---

## 6. Coverage matrix (screen × layer)

| Screen | L1 sweep | L2 oracle | L3 popups | L4 chain | L5 IME | L6 repeat |
|---|---|---|---|---|---|---|
| Home (+Loading, LoadingThenReady) | ✔ 200 | ✔ §5.1 | avatar menu | C1 | — | ✔ shelf |
| Movies/Series/Anime (+Loading, Empty) | ✔ 200 | ✔ §5.2 | — | C1 | — | ✔ grid |
| Search (+Loading) | ✔ 200 | ✔ §5.3 | — | C1 | keyboard only (no IME) | ✔ keys |
| Details (Movie/Series/SeriesSeasons/SeriesDelayed) | ✔ 200 | ✔ §5.4 | trailer notice | C1/C6 | — | ✔ shelf |
| Sources (+Loading/LoadingEmpty/Empty) | ✔ 120 | ✔ §5.5 | drawer popup | C1 | — | ✔ rows |
| Player (16 variants) | ✔ 120 each | ✔ §5.6 | track/episode/next panels | C1/C3 | — | ✔ timeline |
| Live | ✔ 160 | ✔ §5.7 | — | C3 | — | ✔ rows |
| Library (+Empty/Unavailable) | ✔ 200 | ✔ §5.8 | — | — | — | ✔ grid |
| History | ✔ 120 | ✔ §5.9 | — | — | — | ✔ |
| Calendar | ✔ 160 | ✔ §5.10 | — | — | — | ✔ |
| Settings (Playback/Subs/Addons/Sources/SignOut) | ✔ 120 each | ✔ §5.11 | 4 dialogs + modal | C4 | ✔ install dialog | ✔ |
| Profiles (+Locked/Empty) | ✔ 80 | ✔ §5.12 | — | C2 | — | ✔ tiles |
| ProfilePin (+Empty) | ✔ 80 | ✔ §5.13 | forgot dialog | C5 | hardware digits | ✔ |
| ProfileEdit | ✔ 80 | ✔ §5.14 | delete confirm | C2 | — | ✔ |
| Connect (8 variants) | ✔ 80 each | ✔ §5.15 | — | C2 | — | ✔ keys |

Estimated device time: ~40 oracle walks × ~15 steps × (0.6 s settle + ~1.2 s
dump) ≈ 25 min, plus L1 sweeps ≈ 30 min, plus IME/repeat/chains ≈ 15 min.

---

## 7. Pass/fail gate and reporting

The plan is only a proof if the runner **fails loudly**:

- exit non-zero on: any oracle step mismatch; zero focused nodes; two focused
  nodes; focused bounds off-screen; missing screen landmark; popup not present
  after its trigger; focus not restored after dismiss; fixture card never
  reached (completeness, G4).
- per-screen report rows: step, key, expected, actual label, bounds, verdict;
  plus the L1 sweep report path for lineage with the existing tool.
- The existing `tv_focus_audit.py` stays as telemetry (its `return 0` at
  tv_focus_audit.py:256 means it can never be the gate; that is a change to
  propose separately — not made in this read-only audit).

---

## 8. Residual limits (be explicit in the sign-off)

1. **Preview ≠ data**: no `next_cursor` paging, no real refresh/error storms, no
   real auth — Layer 7 (QA runtime) is required for the shipped-app claim (G13).
2. **Uncomposed items are structurally invisible** to uiautomator; completeness
   is proven per-fixture by driving to the shelf/grid end, not by inspection.
3. **Focus visuals** (ring vs shape, NAV-08/TVUI-16) need the screenshot layer.
4. **Physical-remote HID behaviour** needs the manual ONN smoke (G15).
5. **History/Calendar/Settings→Account** have no boards, so their expected order
   is source-derived only (UI_INVENTORY.md §1) — board parity is out of scope.
6. `RecoveryCodes`/`Discover` fall through to TV Home in the preview router
   (UiPreview.kt:129–134) — exclude from passes; file a ticket.
7. The working tree is being edited concurrently; every expectation above must
   be re-derived from the cited file:line if those files move before the pass.

---

## 9. Highest-risk focus logic to watch (from source reading)

- Shelf scroll-back (TvShelfScroll.kt layoutInfo-derived targets) — NAV-01 class.
- Details row offsets after episode-shelf expansion (TvDetailsScreen.kt:66–76;
  OPEN_TASKS marks the `animateScrollToItem` fix *needs on-device verification*).
- Settings column clamp vs sidebar fall-out (TvColumnFocus.kt).
- Sources drawer focus anchoring during streaming arrival
  (TvSourcesScreen.kt:63–76) and scrim focusability.
- Player timeline/scrub KeyDown-vs-KeyUp handling (TvPlayerScreen.kt:62–87).
- Layout-attached initial focus races (TvProfiles.kt:30–38,
  TvHomeScreen.kt:77–106) — G11.
