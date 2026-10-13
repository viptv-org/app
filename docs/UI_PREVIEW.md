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

TV `Player --es ui_variant Controls|Buffer|Scrub|Tracks|Skip|Next|Error` renders the
corresponding supplied playback scene. The fixture's command acknowledgements
are synthetic and confined to the preview. `Player --es ui_variant Native` instead
uses the public Flower MP4 with real KitePlayer facts, without account/provider/
history operations. Its diagnostic output contains public-test status/position/
track counts only. Temporary key-event diagnostics were removed.

TV `Settings --es ui_variant Playback|Subs|Addons|SignOut` renders the corresponding
settings fixture. Its preferences and profile add-on toggles change in memory;
those actions do not prove persistent engine settings. The sign-out fixture
uses a remote-style account confirmation and never calls a real logout endpoint.

TV `Home --es ui_variant LoadingThenReady` keeps the loading placeholders visible
for three seconds, then supplies the fixture snapshot to the same screen instance.
This exercises the real loading-to-content transition without account, provider,
or persistence operations. `Loading` remains the static loading-scene entry point.

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
