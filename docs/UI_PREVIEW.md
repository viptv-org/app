# UI review entry points

The explicit preview renders production UI components with a fixed dataset and copied artwork from the current ZIP boards. It never authenticates, calls a metadata provider, or writes account/profile data. Preview interactions change the visible scene; they do not prove a backend or playback operation. Each capture identifies whether it uses preview data or a real connection.

Desktop launch: `VIPTV_UI_PREVIEW=1 VIPTV_UI_SCREEN=Home ./gradlew :composeApp:run`. `VIPTV_UI_SCREEN` accepts an existing `Screen` name, including Profiles, Home, Search, Details, Sources, Movies, Series, Library, History, Settings, Player, Live and Calendar. The default desktop window is 1440×900.

Android QA launch after installing the experimental APK: `adb -s emulator-5580 shell am start -S -n org.viptv.experiment/.MainActivity --ez ui_preview true --es ui_screen Settings`. The phone review uses a disposable emulator; the physical Android device is untouched. The same extras work on the disposable TV emulator (`emulator-5582`); TV mode follows Android's device configuration. Starting without the extras restores the ordinary application entry point.

Browser preview: append `#ui-preview/Home` or another supported screen name to the development app URL. An isolated headless Chromium test rendered eight locally bundled posters with no runtime errors or failed resource requests. The enabled interactive browser-tool inventory is empty; this runtime check uses the installed Playwright test runner.

The fixture is in `preview/PreviewData.kt`; routing is in `preview/UiPreview.kt`. `composeResources/files/ui-preview` contains byte-preserved assets from the supplied D3HomeWin/D3Search/D3Player boards. Ordinary app navigation remains in `App.kt`.

`#playback-qa` is a separate public-MDN-video test entry point. It has no account, provider or history connection. Real UI Play, Pause, seek, mute and speed controls passed in isolated Chromium; the observed video ended the check paused at 2.786 seconds, volume 0 and speed 0.5. Normal web entry still requires its backend.

Android TV has a dedicated `Connect` preview for welcome → sign-in methods →
password. `Details --es ui_variant Series` renders the Dahmer series/episode
fixture; ordinary `Details` renders the Oak Street movie fixture. `Sources --es
ui_variant Loading` renders a partial source/loading scene. Quality/ranking
fields in these fixtures are sample data, not inferred from provider labels.
The TV entry uses bundled ZIP posters; it adds no metadata-provider requests.

TV `Player --es ui_variant Controls|Buffer|BufferFast|Scrub|Tracks|Skip|Next|Error` renders the
corresponding supplied playback scene. The fixture's command acknowledgements
are synthetic and confined to the preview. `BufferFast` reports ready after
250 ms to exercise cancellation of the delayed buffering overlay.
`Player --es ui_variant Native` instead
uses the public Flower MP4 with real KitePlayer facts, without account/provider/
history operations. Its diagnostic output contains public-test status/position/
track counts only. Temporary key-event diagnostics were removed.

`Player --es ui_variant ErrorSourceOnly|ErrorBackOnly` exercises error recovery
when retry or source selection is unavailable. These isolated fixtures check
focus and navigation, not real provider recovery.

TV `Settings --es ui_variant Playback|Subs|Addons|SignOut` renders the corresponding
settings fixture. Its preferences and profile add-on toggles change in memory;
those actions do not prove persistent engine settings. The sign-out fixture
uses a remote-style account confirmation and never calls a real logout endpoint.

`Settings --es ui_variant ImportDetails` (desktop `VIPTV_UI_VARIANT=ImportDetails`, browser
`#ui-preview/Settings/ImportDetails`) renders the Stremio import completion stage while a
synthetic server fills 162 titles' Details cards over about 45 s: an indeterminate bar for
3 s, then the animated "N of 162 titles" bar, then "Title details are ready.". The real import
session and screen run against an in-memory transport. TV and desktop have no import entry
(IMP-001), so this is the way to review that stage there.

TV `Home --es ui_variant LoadingThenReady` keeps the loading placeholders visible
for three seconds, then supplies the fixture snapshot to the same screen instance.
This exercises the real loading-to-content transition without account, provider,
or persistence operations. `Loading` remains the static loading-scene entry point.

Phone, tablet, desktop and browser `Library` variant `Long` (desktop
`VIPTV_UI_VARIANT=Long`, browser `#ui-preview/Library/Long`) renders 48 saved
titles with type chips and Filters, long enough to review the collapsing title
and History card (app#58). Preview state only; nothing is saved.

TV `Library` renders the supplied My List artwork with fixture watched ordering.
Variants `Empty` and `Unavailable` render the corresponding provisional states.
Filters and sort choices change preview state only; no saved-list database or
server operations run.

TV `Profiles` renders the supplied unlocked profile-picker board. `Locked`
retains the locked-profile marker and `Empty` checks initial focus on Add profile.
Manage/Done update only preview mode. Management opens the selected profile's
TV editor; creation remains disconnected in this isolated preview.

TV `ProfileEdit` renders the supplied profile editor with fixture rating/language
labels. Name, avatar and PIN operations change memory only. Delete returns to
Profiles without removing real data. Maturity/language editing explains the
missing preference contract. Native Back returns the editor to Profiles.

TV `Live` renders the supplied news still and fixture EPG window. Category
filtering changes this supplied data only. OK enters a fixture live-player scene;
its frame is not proof that a live source decodes or that an EPG provider is connected.

TV `Calendar` renders the `SkTvCalendar` board data, with supplied date labels,
artwork and optional Watch E481 action. Choosing a date filters preview entries;
its detail/source routes remain generic fixtures. No real Simkl integration or
episode resolution is exercised.

TV `ProfilePin` starts with the supplied two masked digits; `Empty` starts empty.
Digits/erase change preview memory and valid submission routes to Home through
a synthetic callback. Back returns to Profiles. No stored PIN is checked.

TV `Search --es ui_variant Loading` renders empty-result loading placeholders;
other variants keep the existing search fixtures. Keyboard edits change preview
memory only, with no provider requests.

TV `Connect --es ui_variant Email` starts the email-entry board with its vynxc
draft and Gmail-key focus. Next shows the missing code-service state; no email
is sent. Phone and Back navigate the existing entry state machine.

TV `Connect --es ui_variant Iptv` starts the supplied Xtream form with example
values and username focus. Edits stay in memory. Connect reports the absent
provider service; no provider, playlist, account or catalog request runs.

TV `Connect --es ui_variant EmailCode` renders the supplied code-entry board with
fixture challenge status and digits. Verify/resend explain unavailable services,
and Back returns to email entry. No real challenge or email is created.

TV `Connect --es ui_variant Splash` renders the supplied animated startup scene.
It performs no actual session restoration or authentication.

TV `Movies --es ui_variant Loading|Empty` exercises catalog placeholders and the
empty state. Down remains safe when no poster focus target is attached. These
variants make no provider request.

TV `Connect --es ui_variant Pairing|PairingUnavailable` renders the shared phone/
browser pairing scene. Pairing uses an explicit `.invalid` fixture URL and static
code/expiry; unavailable contains no QR. No pairing poll or account request runs.

TV `Connect --es ui_variant SignedIn|SignedInHold` supplies an isolated greeting
identity. SignedIn completes to Profiles; Hold retains the scene. Neither variant
creates a real authenticated session or fetches account data.

TV `Player --es ui_variant TracksInvalidFocus|TracksAudioOnly|TracksEmpty` exercises
options-panel focus with a stale preferred ID or missing tracks. The data and
acknowledgements remain synthetic; no provider/player request is made.

TV `Sources --es ui_variant LoadingEmpty|Empty` checks the All filter's focus when
no selectable source exists. Loading retains three placeholders and synthetic
discovery status; no add-on/network request runs.

TV `Details --es ui_variant SeriesSeasons|SeriesDelayed` uses a two-season fixture
whose resume target is in season 2. Delayed supplies episodes after two seconds.
These variants check focus/season resolution without provider or history requests.

TV `Player --es ui_variant SkipHalf|SkipEnd` provides halfway/expired intro facts
for the countdown rail. `Skip` starts the same ten-second fixture range at zero.
These cases do not supply real-provider intro markers or run an independent timer.

TV `Player --es ui_variant ScrubNoImage` supplies a proposed seek without thumbnail
data, for checking the timestamp-only fallback and explicit seek confirmation.

Native IPTV layout fixtures use `VIPTV_UI_SCREEN=Connect` with
`VIPTV_UI_VARIANT=IptvXtream` or `IptvM3u`. `VIPTV_UI_PHONE=1` selects the common
390×844 phone layout in the desktop harness. Fixture submissions are inert and
reserved-domain credentials never configure providers. The inspected Xvfb
captures used `SKIKO_RENDER_API=SOFTWARE_COMPAT`. Header pixels were verified
in the files and across 12 windowless frames, including an active-tab click.
Android input, keyboard/status-bar placement and default desktop GPU rendering
are separate.
