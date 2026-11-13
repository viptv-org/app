# Android builds

Every Android build validates or builds the Rust engine (`native/core`, built by
`tools/build_engine.py` through `cargo ndk`) and the pinned torrent runtime
(`tools/build_torrent_runtime.py`) before Gradle packages the APK. The FFmpeg
audio decoder ships prebuilt in `native/media3-ffmpeg/src/main/jniLibs` and is
only checksum-verified. Run Gradle with a Rust 1.98 toolchain that has the
Android targets of the ABIs you build (`x86_64-linux-android`,
`aarch64-linux-android`, `armv7-linux-androideabi`), e.g.
`RUSTUP_TOOLCHAIN=1.98`.

`androidApp/src/main/java/org/playbackgateway/runtime/NativeRuntime.java` is a
verbatim copy of `torrent-runtime/platform/android/NativeRuntime.java` at the
pin; the torrent runtime build fails if the two differ.

## Shipped ABIs

APKs package `armeabi-v7a`, `arm64-v8a` and `x86_64`. Without extra properties,
every build compiles the Rust engine and the torrent runtime for all three and
packages all three. Release and distribution builds keep this default.

## Fast emulator loop: one ABI

The emulators only need `x86_64`. `-Pviptv.abis=<comma list>` limits
`abiFilters` and the Rust engine and torrent runtime builds to the listed ABIs,
so the others are not compiled. An unknown name fails the build and lists the
supported ABIs.

```sh
RUSTUP_TOOLCHAIN=1.98 ./gradlew --max-workers=1 :androidApp:assembleDebug -Pviptv.abis=x86_64
adb -s <serial> install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

`buildAndroidEngine` and `buildTorrentRuntime` are Gradle build-cache tasks.
Their fingerprints cover native sources, manifests, lockfiles, licenses, build
helpers, ABI selection, feature flags, compiler flags and resolved toolchains.
`tools/android-toolchains.json` pins the CI toolchains and default NDK.
`ANDROID_NDK_HOME` and `VIPTV_CARGO_NDK_DIR` select explicit local toolchains;
the effective versions participate in the fingerprint. Server URLs and Kotlin
sources do not invalidate native outputs.

Every build validates the native fingerprint, library checksums and ELF ABI,
license files and provenance after Gradle restores outputs. Incomplete or
corrupt output is rebuilt before packaging. Direct torrent-builder calls always
build; `tools/android_native.py build torrent` provides validated reuse.

Switching between one ABI and the default reruns the engine and torrent runtime
tasks and the APK packaging. Cargo keeps every ABI's objects in `build/rust`, so
the engine task only compiles ABIs it has not built before.

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

## Android host tests

`composeApp/src/androidHostTest` runs on the JVM with Robolectric (native
graphics), so tests can use Media3's real subtitle parsers and draw a real
`SubtitleView` into a bitmap. It needs no emulator and no Rust build. The first
run downloads Robolectric's Android runtime from Maven Central. CI runs it in the
Android job.

```sh
./gradlew --max-workers=1 :composeApp:testAndroidHostTest
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
The cold single-ABI time includes 1 min 46 s for the torrent runtime. The
default cold figure is the single-ABI cold build plus the 10 min 13 s the
default build then spent on the other two engine ABIs; it was not run from an
empty `build/`. A change under `native/core` rebuilds the engine for every
selected ABI, so with one ABI it should take about a third as long (not
measured separately).

## Hosted release workflow

Run **Android release** with optional `default_server` (an HTTPS origin, using
the Gradle validation). **Experimental KMP platforms** calls the same workflow;
its `android_only` input runs Android independently. Main-branch platform runs
prime shared caches. Owner-invoked branch dispatches may save branch-local
caches; other branch callers only read them.

Release builds retain all three ABIs, R8 optimization, resource shrinking,
the baseline profile and existing signing configuration. Host tests, native
integrity and APK ABI/profile checks gate the uploaded APK. Existing CI debug
signing keys are runner-local, so these APKs do not establish update-signature
compatibility with installations signed on another runner.

Gradle persists dependency and task-output caches. Engine and torrent packaging
outputs have separate exact-only cache keys, including licenses and provenance.
Cargo and Go dependency/compiler caches may restore a compatible older cache;
their build tools decide which objects need recompilation.

The workflow summary reports cache hits and toolchain, native and release/test
timings. `cache_experiment` supports cold (no restores), Kotlin-only, native
source invalidation and corruption probes. The native probe edits a Rust source
file; the corruption probe truncates a restored arm64 engine and requires safe
rebuilding. Use `none` with a different `default_server` for the URL-only probe.
Compare three unchanged warm runs on the same commit; include setup, cache
transfer and artifact upload when reporting wall-clock performance.
