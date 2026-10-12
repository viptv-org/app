# Implementation status — 2026-10-12

This is an incomplete implementation checkpoint. It is not the completed application or a design-parity release.

The active scope is UI parity first, using the current VIPTV ZIPs across every KMP platform. Functional integration work follows visual and interaction completion.

The latest navigation checkpoint expands the desktop rail to Home, Discover, Movies, Series, Live, Calendar, My List and History, with Add-ons and Settings at the bottom. The phone has the four-tab floating dock and separate Search action from the ZIP; Home reserves bottom scrolling space under it. Web navigation collapses additional destinations into a menu at medium and phone widths. Movies, Series and Discover have a shared poster-grid surface using already-loaded catalog cards, with alphabetical sorting and a provider-order default. Genre filters, populated-grid parity, Calendar, Live guide and My List remain incomplete. Captures are `docs/evidence/desktop-navigation.png`, `docs/evidence/desktop-browse-shell.png`, and `docs/evidence/android-floating-navigation.png`. The phone Search action and desktop Movies/sort interactions were observed; browser and TV runtime checks remain pending.

| Area | Evidence / status |
| --- | --- |
| Experimental branches | 14 worktrees based on fetched origin/main; original main branches unchanged |
| Current design | 197 HTML/PNG boards, assets, hashes and reference gallery imported |
| Instructions and third-party skills | Initial branch cleanup plus private archive of root-level instructions; further documentation pruning pending |
| Rust engine | Capability registry, profile assignments, query validation, mapping evidence, bounded Home assembly, SQLite profiles and profile-scoped watch history; focused engine and bridge tests pass |
| Generated interfaces | Rust-derived Kotlin/TypeScript models and UniFFI native library |
| Local KMP profiles | Create/rename/delete, profile-scoped settings/history, and salted PIN with failed-attempt delay are in the shared engine. Android emulator created a second profile, set its PIN, rejected a wrong PIN, accepted the right PIN, and restored the locked picker after restart. The PIN guards the app flow, not device-owner access to local files. |
| Remote backend | v3 identity/Home bootstrap, discovery/details projections and scoped revisioned integration configuration; three route tests pass |
| KMP builds | Desktop and wasmJs compilation pass; Android APK includes arm64 and x86_64 Rust libraries |
| Player adapters | Android KitePlayer displayed a decoded frame from the public Flower MP4 fixture through local Search → Details → Sources → Player. HLS, tracks, subtitles, seek, desktop and browser playback remain unqualified |
| Visual implementation | Initial connection/profile/Home/search/details work plus an undecorated desktop window with custom chrome. The desktop top bar matches the current ZIP geometry in a Linux capture; Home content and all board-level parity remain unfinished. Zero boards certified as matching. |
| Native/remote credential persistence | Local addon credentials and native remote refresh tokens use Android Keystore or the desktop secret store. Native sessions restore and rotate on expiry; browser sessions check cookie status and refresh with CSRF. Focused mocked transport tests pass. Real-server restart, expiry and sign-out flows remain unqualified. |
| Full plugin execution | Stremio installation, secure local configuration, Home/Search/details, direct HTTP source discovery, profile enable/disable and removal work on Android. Simkl server integration, vsources, M3U, torrent acquisition and streaming Home updates remain pending |
| Remaining app flows | Local direct source selection, playback, History and resume work on Android. Live guide, My List, external tracking, auth variants, dashboard, casting/remote/update behavior and complete navigation remain pending |
| Performance | Local Home now returns stored Continue Watching and pending catalog shapes before network results. The Android restart check showed Flower immediately. Full request counts and cold provider latency remain unqualified |
| Migration and other clients | Populated migration, Roku/SolidTV adoption and regression acceptance pending |
| Windows/macOS/device playback | Not qualified |
| Production | Not modified or deployed |

## Build facts

The app uses Kotlin 2.4.21, Compose 1.13.0-alpha02, AGP 9.1.0 and compile SDK 37 because the selected KitePlayer dependencies require the newer Android/Compose stack. Android minimum remains API 26. Current Coil/Skiko compatibility warnings still need runtime qualification.

`./gradlew :composeApp:desktopTest :composeApp:compileKotlinWasmJs :androidApp:assembleDebug` builds the targets and exercises the generated native binding. The pinned core is the `native/core` Git submodule. Rust/NDK and Python are native build dependencies.

The experimental APK is `androidApp/build/outputs/apk/debug/androidApp-debug.apk`, application ID `org.viptv.experiment`. It coexists with the existing application.

## Local addon checkpoint

The Android QA emulator installed Cinemeta through the app, stored its configuration through Android Keystore, restored it after updating the APK, rendered real catalog artwork on Home, and opened real title details. The addon uses a bounded 307 redirect to its catalog host. Some catalog rows missed the 1.9-second initial deadline and show retry; complete cold Home timing and actual playback remain unqualified.

## Android direct-play checkpoint

`docs/evidence/android-direct-playback.png` shows a decoded frame from the public MDN Flower MP4 after installing the immutable Stremio fixture, searching, opening its title, selecting its stream and starting KitePlayer. `docs/provisional/addon-settings-android.png` records the current profile switch and removal controls, which still need a supplied design. The fixture lives in the experimental workspace branch and is unrelated to production catalog data.

## Profile history checkpoint

A local Flower playback position was saved through KitePlayer into the shared Rust store. After app restart, Home displayed Continue Watching with Flower and its remaining time before catalog requests completed; History displayed the same item under Today. `docs/evidence/android-history.png` shows the current result. Playback metadata survives an app restart and is scoped by profile. No current design board has been certified as visually matched.

## Local profile checkpoint

`docs/evidence/android-profiles.png`, `docs/evidence/android-profile-pin.png`, and `docs/evidence/android-profile-edit.png` record the Android QA flow. The picker and PIN/edit screens remain in progress against their current design boards. Local avatar selection uses the two supplied portrait assets and two initial colors, persists in the shared Rust profile record, and survived a QA app restart. `docs/evidence/android-avatar-picker.png` records the provisional selection sheet. Maturity controls, playback preferences, language, TV keypad behavior, and PIN recovery have not been implemented. Local profile-creation and avatar-selection dialogs are tracked in `DESIGN_GAPS.md` because the supplied boards do not show those forms.

The phone picker now uses a two-column grid and top Edit control matching the supplied board geometry. The phone PIN screen places the avatar, indicators and keypad at approximately the supplied positions; touch entry and unlock passed on the QA emulator. Its extra OK key supports the engine's 4–8 digit PIN range and is tracked as a provisional difference. These screens have not passed 1:1 visual certification.

## Remote session checkpoint

Native device login now saves its refresh token in the OS secret store, refreshes expired access tokens under a single rotation gate, and restores the session after app restart. If the OS vault is unavailable, sign-in remains usable in memory and Settings explains that it will not survive restart. Browser startup checks the same-origin cookie session and obtains CSRF before refreshing an expired browser cookie. The Settings Sign out action revokes the server session when reachable, then clears native secret storage. Desktop mocked-transport tests cover native token rotation across a reopened client, browser CSRF refresh, and the unavailable-vault fallback; live HTTPS integration across Android, desktop and browser remains pending.

## UI parity source and desktop chrome

The authoritative UI inputs are the `VIPTV-screens-1-current-TV.zip`, `VIPTV-screens-2-current-Desktop-app.zip`, `VIPTV-screens-3-current-Phone-Tablet-Web.zip`, and `VIPTV-screens-4-current-Simkl.zip` archives in the workspace root. Their hashes match the experimental `design/current/sources.json` record. The ordinary design repository contains older material and is not the parity source for this experiment.

The reported desktop dragging and icon problems prompted replacing the manual drag loop with Compose Foundation `WindowDraggableArea`. The new handler moved the window exactly 100 × 40 pixels for the same cursor movement in isolated Linux testing. Caption glyphs were replaced with the ZIP's 10-pixel vector paths; Lucide CMP supplies title-bar, navigation, sign-in, lock, add-profile and password-visibility icons. Native desktop scaling and Windows/macOS behavior remain unqualified. Library choices and image-cache bounds are recorded in `UI_LIBRARIES.md`; image latency has not been benchmarked.

The desktop sign-in chooser and password panel now have ZIP-derived local poster assets and a radial backdrop fade. Captures are `docs/evidence/desktop-signin-choice.png` and `docs/evidence/desktop-signin-password.png`. Browser, email, device and IPTV panels are navigable UI states with unavailable-state copy; delivery, approval and provider connections are not implemented, and no fake pairing code is shown. All six sign-in boards remain in progress, not certified.

`docs/evidence/desktop-titlebar-linux.png` records the current 1440 × 900 Linux window. The window uses custom undecorated chrome, moves by dragging its title bar, and closes through the custom close control. The top-bar geometry and search placement follow the supplied `D3HomeWin` board. `docs/evidence/desktop-titlebar-narrow.png` records the 600 × 800 collapsed bar; its menu opens the navigation rail. `docs/evidence/desktop-search-shell.png` records the title-bar search input driving the Search screen without a duplicate field. `docs/evidence/desktop-profiles.png` records the 1100 × 720 profile picker geometry and keyboard focus ring. Home content, search suggestions/results, desktop sign-in methods, navigation detail, popovers, and Mac/Windows runtime checks remain incomplete. Zero boards have passed full visual parity review. The extra Linux variant is tracked in `DESIGN_GAPS.md`.

## Featured Home layout checkpoint

The desktop featured component is split into `features/home/DesktopHero.kt`. It uses a 44/56 text/artwork split with a 40-pixel gap, 16:9 artwork, constrained logo decoding, available metadata badges, and the Lucide information icon. Below 900 pixels of content width, artwork precedes the summary. Narrow native desktop windows now retain this desktop layout rather than using the phone overlay. Desktop landscape rows use 280-pixel cards and 14/12-pixel title/subtitle text. Desktop tests, browser compilation, and Android assembly pass. Populated visual captures, featured paging, Play/My List behavior, and phone/TV hero parity remain pending; this checkpoint does not certify Home parity.

## Phone featured layout checkpoint

`features/home/PhoneHero.kt` replaces the generic phone hero with a 470-pixel artwork panel, 16-pixel corners, centered constrained logo, lower artwork fade, and two 46-pixel actions using Lucide icons. Local Play opens source selection. Remote Play currently opens title details because remote source selection is incomplete. My List displays the explicit unavailable notice tracked as GAP-015 rather than reporting a title saved. Supplied genre metadata and featured pagination are not represented by the current card contract. Desktop tests, web compilation and Android assembly pass; populated visual checks and phone hero parity remain pending.

## Phone header checkpoint

The compact native header now uses the supplied cast/remote icon style and a 28-pixel profile avatar with a Switch profile/Settings menu. Home has All, Movies, Series and Genres controls; Movies navigation passed on the disposable QA emulator. `docs/evidence/android-home-header.png` and `android-movies-header.png` record actual runtime layouts. Cast, remote pairing and genres show the provisional GAP-016 notice. Small initial avatars use 16-pixel lettering instead of the profile picker's 38-pixel lettering. Desktop tests, web compilation and Android assembly pass. The capture has no featured artwork because the profile's catalog requests failed; it does not certify the hero or image performance.

## Title details layout checkpoint

The shared detail screen now separates `DetailHero.kt` from the page/episode list. Wide content uses a 44/56 summary/artwork split with a 40-pixel gap; narrow content stacks 280-pixel artwork above available title metadata. Logos and episode artwork have bounded dimensions, episode cards use crop scaling and rounded corners, and episodes open their own source selection instead of being inert. Phone episode rows use 120-pixel images instead of 200-pixel images. Lucide supplies Back and Play. Desktop tests, browser compilation and Android assembly pass. Populated captures, edge-to-edge phone artwork, TV focus/geometry, recommendations, season controls, source summaries, trailer/rating/list/share actions remain pending. No title-details board is certified.
