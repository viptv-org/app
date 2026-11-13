#!/usr/bin/env bash
set -euo pipefail

root=$(cd -- "$(dirname -- "$0")/.." && pwd)
case "${1:-}" in
  web) task=:composeApp:compileKotlinWasmJs ;;
  desktop) task=:composeApp:compileKotlinDesktop ;;
  test) task=:composeApp:desktopTest ;;
  *) echo "usage: $0 {web|desktop|test} [Gradle options]" >&2; exit 2 ;;
esac
shift
export GRADLE_USER_HOME=${GRADLE_USER_HOME:-$HOME/.gradle}
export KONAN_DATA_DIR=${KONAN_DATA_DIR:-$HOME/.konan}
export VIPTV_CARGO_TARGET_DIR=${VIPTV_CARGO_TARGET_DIR:-$GRADLE_USER_HOME/viptv-cargo}
export CARGO_BUILD_JOBS=1
export RUSTUP_TOOLCHAIN=${RUSTUP_TOOLCHAIN:-1.98.0}
export LC_ALL=C
cd "$root"
exec flock /tmp/viptv-heavy-build.lock ./gradlew "$task" \
  --build-cache --configuration-cache --max-workers=1 \
  -Pkotlin.compiler.execution.strategy=in-process "$@"
