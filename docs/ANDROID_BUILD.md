# Android builds

Every Android build runs the Rust engine (`native/core`, built by
`tools/build_engine.py` through `cargo ndk`) and the pinned torrent runtime
(`tools/build_torrent_runtime.py`) before Gradle packages the APK. The FFmpeg
audio decoder ships prebuilt in `native/media3-ffmpeg/src/main/jniLibs` and is
only checksum-verified. Run Gradle with a Rust 1.98 toolchain that has the
Android targets of the ABIs you build (`x86_64-linux-android`,
`aarch64-linux-android`, `armv7-linux-androideabi`), e.g.
`RUSTUP_TOOLCHAIN=1.98`.

## Shipped ABIs

APKs package `armeabi-v7a`, `arm64-v8a` and `x86_64`. Without extra properties,
every build compiles the Rust engine for all three and packages all three.
Release and distribution builds keep this default.

## Fast emulator loop: one ABI

The emulators only need `x86_64`. `-Pviptv.abis=<comma list>` limits both
`abiFilters` and the Rust engine build to the listed ABIs, so the others are
not compiled. An unknown name fails the build and lists the supported ABIs.

```sh
RUSTUP_TOOLCHAIN=1.98 ./gradlew --max-workers=1 :androidApp:assembleDebug -Pviptv.abis=x86_64
adb -s <serial> install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

The torrent runtime still compiles all three ABIs, because its pinned upstream
build script (`playback-gateway/torrent-runtime/scripts/build-native.py`) has
no ABI option yet (viptv-org/playback-gateway#23). It is built once per
checkout and cached by revision in `androidApp/build/generated/torrentRuntime`,
so only a cold build pays for it.
`abiFilters` packages only the selected ABIs. To skip it entirely, add
`-Pviptv.torrentRuntime=false`; the app then needs a gateway for torrents.

Switching between one ABI and the default reruns the engine task and the APK
packaging. Cargo keeps every ABI's objects in `build/rust`, so the engine task
only compiles ABIs it has not built before.

## ABI guard

`checkDebugApkAbis` assembles the debug APK and checks that it packages exactly
the selected ABIs (`tools/check_apk_abis.py`), each with `libviptv_core.so` and
`libffmpegJNI.so`. CI runs the same script on the release APK with all three
ABIs.

```sh
./gradlew --max-workers=1 :androidApp:checkDebugApkAbis -Pviptv.abis=x86_64  # only lib/x86_64/
./gradlew --max-workers=1 :androidApp:checkDebugApkAbis                      # all three ABIs
python3 tools/check_apk_abis.py <apk> x86_64                                 # any APK
```

## Timings on the dev VPS

Measured 2026-10-26 with `--no-daemon --max-workers=1` in a fresh worktree
(empty `build/`, shared Gradle and Go caches), `:androidApp:assembleDebug`:

| Build | `-Pviptv.abis=x86_64` | Default (three ABIs) |
|---|---|---|
| Cold | 8 min 57 s | about 19 min (derived) |
| Warm, nothing changed | 16 s | 16 s |
| Warm, one-line Kotlin change in `composeApp` | 55 s | not measured; no native task reruns |

Most of a cold build is the Rust engine: 5 min 23 s for `x86_64`, and about
5 min for each other ABI (`armeabi-v7a` 5 min 04 s, `arm64-v8a` 5 min 07 s).
The cold single-ABI time includes 1 min 46 s for the torrent runtime's three
ABIs. The default cold figure is the single-ABI cold build plus the 10 min
13 s the default build then spent on the other two engine ABIs; it was not run
from an empty `build/`. A change under `native/core` rebuilds the engine for
every selected ABI, so with one ABI it should take about a third as long (not
measured separately).
