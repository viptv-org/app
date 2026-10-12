# Implementation status — 2026-10-12

This is an incomplete implementation checkpoint. It is not the completed application or a design-parity release.

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
| Visual implementation | Initial connection/profile/Home/search/details work; zero boards certified as matching |
| Native/remote credential persistence | Local addon credentials use Android Keystore or the desktop secret store. Remote account session persistence remains pending |
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
