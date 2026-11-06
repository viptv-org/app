# app

Kotlin Compose Multiplatform client: `composeApp` (shared UI and state for
Android, Android TV, desktop and web) and `androidApp` (the Android shell).
Read the workspace `AGENTS.md` first.

## Toolchain

- `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`; the default java has no javac.
- `RUSTUP_TOOLCHAIN=1.98.0` for desktop builds, `1.98` for Android (it has the
  Android targets).
- A worktree needs `local.properties` and `git submodule update --init native/core`
  (`scripts/agents/claim.sh` does both), plus the host packages in the workspace
  `docs/platform-deps.md`. `native/core` only moves forward: a PR may bump it and
  never roll it back, and `scripts/ci/native-core-forward-only.sh` fails the checks
  when the pin goes backwards. A commit that swept in a stale checkout is undone
  with `git checkout origin/edge -- native/core`.
- The first `desktopTest` in a fresh worktree builds the Rust engine (about 7 minutes).

## Checks

- `:composeApp:desktopTest` with `LC_ALL=C` (`MpvNativeTest` fails under other locales).
- `:composeApp:compileAndroidMain` and `:composeApp:compileKotlinWasmJs`: desktop
  tests don't catch platform-only APIs in `commonMain`.
- `:composeApp:testAndroidHostTest` (Robolectric) when `androidMain` changes;
  `:androidApp:assembleDebug -Pviptv.abis=x86_64` when `androidApp/` changes.
- Compose "LayoutNode not found in RectList" is a known flake: rerun that class alone.
- Wait for asynchronous controller work with `scene.renderUntil { … }`
  (`composeApp/src/desktopTest/kotlin/org/viptv/app/RenderUntil.kt`), never a fixed
  frame count; fixed counts are only for animations, which need the frame clock.
- `.github/workflows/app-checks.yml` runs the same three tasks on every PR into
  `edge` and `main` and on pushes to `edge`; it clones `playback-gateway` for the
  desktop torrent resource and uploads failed `desktopTest` reports.

## Wire types

`composeApp/src/commonMain/kotlin/org/viptv/engine/wire/EngineWire.kt` is a
checked-in copy of core's `generated/engine/EngineWire.kt` with `@Immutable`
added. When `native/core` moves, refresh it from that file.
