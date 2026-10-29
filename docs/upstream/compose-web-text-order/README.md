# Compose web text field: characters land out of order when frames are slow

A standalone, app-independent reproduction of app#105, ready to be pasted into a
Compose Multiplatform (CMP) bug report. It contains **no VIPTV code**: one
`ComposeViewport`, one `BasicTextField`, and a `LaunchedEffect` that copies the
field's value to `window.__typed` so a test can read what Compose actually holds.

## What it shows

Typing into a Compose web text field under Chrome DevTools CPU throttling puts
characters in the wrong order, and sometimes drops them:

```
typed    'smoke-visitor-compose-web'
Compose  'smke-visitor-compose-webo'     (the 'e' and 'o' moved to the end)
Compose  'smk-iio-compose-webrtsveo'     (several early characters moved to the end)
```

The pattern reported in app#105 — "a run of early characters is usually moved to
the end of the field" — is exactly what this repro produces. Compose's own state
and the DOM backing field (`div.compose-backing-field`) agree on the scrambled
value, so this is not a read-out artefact: the field really holds it.

## Environment where it reproduces

- Compose Multiplatform `1.13.0-alpha02`, Kotlin `2.4.21`, Compose compiler
  plugin `2.4.21`
- wasmJs production build, headless Chromium (Playwright `1.57.0`, Chromium 1247)
- `Emulation.setCPUThrottlingRate` = 60 (40 with a 10 ms key delay also fails)

Results, 3–5 runs each: throttle 1 → passed every run; throttle 60 → failed
every run. Delaying only `requestAnimationFrame` (50/150 ms) did **not** fail,
so it is main-thread CPU starvation, not merely a late frame.

## Build

```sh
# from the app repository root; the app's Gradle wrapper is enough
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  ./gradlew -p docs/upstream/compose-web-text-order wasmJsBrowserDistribution --max-workers=1
```

Output goes to `docs/upstream/compose-web-text-order/build/dist/wasmJs/productionExecutable`
(about 18 MB of wasm; git-ignored).

## Run

`tools/web_text_order_repro.py` (in the app repository) serves that build over
HTTPS, types a fixed string with a fixed delay under CPU throttling, and compares
the result with what was typed:

```sh
python3 tools/web_text_order_repro.py --runs 3          # red: reordered characters
python3 tools/web_text_order_repro.py --cpu-throttle 1  # green: control run
```

For a bug report, the reproduction is also one sentence: type into a Compose web
`BasicTextField` at ~60× CPU slowdown and watch characters land out of order.

## Files

| File | Purpose |
|---|---|
| `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` | pinned to the CMP version from the app |
| `src/wasmJsMain/kotlin/Main.kt` | the whole reproduction: one `BasicTextField` |
| `src/wasmJsMain/resources/index.html` | page shell that loads `repro.js` |