# Desktop and web development builds

Set `JAVA_HOME` to JDK 21 and initialize `native/core` before testing.

```sh
bash tools/dev-build.sh web
bash tools/dev-build.sh desktop
bash tools/dev-build.sh test --tests 'org.viptv.app.ExampleTest'
```

`web` and `desktop` compile Kotlin without packaging a distribution. `test`
includes the native desktop prerequisites; use a real test class filter for
focused checks. Browser interaction testing additionally needs an isolated
Wasm bundle and HTTPS server.

The helper serializes heavy builds on this host and limits Gradle and Cargo
to one worker. Gradle task outputs are cached; the helper also enables
configuration caching and in-process Kotlin compilation. Worktrees retain
their own incremental outputs and configuration cache.

Keep `GRADLE_USER_HOME` and `KONAN_DATA_DIR` pointing to the same shared
locations across worktrees. Cargo artifacts use
`$GRADLE_USER_HOME/viptv-cargo`, overridable with `VIPTV_CARGO_TARGET_DIR`.
Do not run `clean` or delete caches for routine checks. A no-change repeat
measures cache reuse; it does not measure changed-source compilation speed.
