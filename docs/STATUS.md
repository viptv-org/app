# Implementation status — 2026-10-12

This is an incomplete implementation checkpoint. It is not the completed application or a design-parity release.

| Area | Evidence / status |
| --- | --- |
| Experimental branches | 14 worktrees based on fetched origin/main; original main branches unchanged |
| Current design | 197 HTML/PNG boards, assets, hashes and reference gallery imported |
| Instructions and third-party skills | Initial branch cleanup plus private archive of root-level instructions; further documentation pruning pending |
| Rust engine | Capability registry, profile assignments, query validation, mapping evidence, bounded Home assembly, SQLite profiles; six focused tests pass |
| Generated interfaces | Rust-derived Kotlin/TypeScript models and UniFFI native library |
| Local KMP profiles | Linux JNI tests pass; Android emulator restores local profile after APK update |
| Remote backend | v3 identity/Home bootstrap, discovery/details projections and scoped revisioned integration configuration; three route tests pass |
| KMP builds | Desktop and wasmJs compilation pass; Android APK includes arm64 and x86_64 Rust libraries |
| Player adapters | Native KitePlayer and browser HTML/HLS surfaces compile; real-media qualification pending |
| Visual implementation | Initial connection/profile/Home/search/details work; zero boards certified as matching |
| Native/remote credential persistence | Pending |
| Full plugin execution | Stremio addon installation, secure local configuration, Home catalogs, Search and title details work on Android. Sources/playback, Simkl server integration, vsources, M3U and streaming Home updates remain pending |
| Remaining app flows | Pending: source selection and playback, live guide, library/history, tracking, auth variants, dashboard, casting/remote/update behavior and complete navigation |
| Performance | Bounded-assembly behavior tested; real provider budgets and client network trace not yet qualified |
| Migration and other clients | Populated migration, Roku/SolidTV adoption and regression acceptance pending |
| Windows/macOS/device playback | Not qualified |
| Production | Not modified or deployed |

## Build facts

The app uses Kotlin 2.4.21, Compose 1.13.0-alpha02, AGP 9.1.0 and compile SDK 37 because the selected KitePlayer dependencies require the newer Android/Compose stack. Android minimum remains API 26. Current Coil/Skiko compatibility warnings still need runtime qualification.

`./gradlew :composeApp:desktopTest :composeApp:compileKotlinWasmJs :androidApp:assembleDebug` builds the targets and exercises the generated native binding. The pinned core is the `native/core` Git submodule. Rust/NDK and Python are native build dependencies.

The experimental APK is `androidApp/build/outputs/apk/debug/androidApp-debug.apk`, application ID `org.viptv.experiment`. It coexists with the existing application.

## Local addon checkpoint

The Android QA emulator installed Cinemeta through the app, stored its configuration through Android Keystore, restored it after updating the APK, rendered real catalog artwork on Home, and opened real title details. The addon uses a bounded 307 redirect to its catalog host. Some catalog rows missed the 1.9-second initial deadline and show retry; complete cold Home timing and actual playback remain unqualified.
