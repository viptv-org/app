# Compose web text input reorders typed characters (app#105)

Typed characters reach Compose web text fields out of order, and sometimes not at
all, when the browser's main thread is slow. On `/` that makes the sign-in form
post a username or password that differs from what was typed, so the visitor gets
"wrong credentials" after typing correctly.

**This is a Compose Multiplatform defect, not app behaviour.** The same scrambling
reproduces in a standalone Compose app that contains nothing but one
`BasicTextField` (`docs/upstream/compose-web-text-order`, driven by
`tools/web_text_order_repro.py`), so there is no app-side fix. app#105 stays open
until the upstream fix lands in a Compose version the app can use.

- Compose Multiplatform `1.13.0-alpha02` (`org.jetbrains.compose` in
  `build.gradle.kts`), Kotlin `2.4.21`, wasmJs build, Chromium.
- Upstream code involved: `androidx/compose/ui/platform/DomInputStrategy.kt` and
  `NativeInputEventsProcessor.kt` in the Compose web text-input layer.

## Deterministic reproduction

Both commands are headless and need no display, GPU, account or real server.

```sh
# The app's sign-in form (fake backend, fake credentials, app#55 harness).
./gradlew :composeApp:wasmJsBrowserDistribution --max-workers=1
python3 tools/web_signin_smoke.py --scenario button --cpu-throttle 6

# The same fault with no VIPTV code at all (build the repro first, see its README).
./gradlew -p docs/upstream/compose-web-text-order wasmJsBrowserDistribution --max-workers=1
python3 tools/web_text_order_repro.py --runs 3
```

`--cpu-throttle` uses Chrome DevTools' `Emulation.setCPUThrottlingRate`, i.e. it
starves the page the way a slow frame does on a loaded machine or under software
rendering. At the default (1) both checks pass; the flags above make them fail
every run.

Rates measured on this host (Chromium 1247, Playwright 1.57.0):

| Scenario | CPU throttle | Result |
|---|---|---|
| App sign-in, `--scenario button` | 1 | 3/3 passed, exact username and password |
| App sign-in, `--scenario button` | 6 | 3/3 failed, characters scrambled |
| App sign-in, `--scenario button` | 20 | 3/3 failed, characters scrambled |
| Compose-only repro, 25-character string, 20 ms keys | 1 | 3/3 passed |
| Compose-only repro, 25-character string, 20 ms keys | 40 | 2/3 failed (intermittent) |
| Compose-only repro, 25-character string, 20 ms keys | 60 | 5/5 failed |
| Compose-only repro, 13-character string, 10 ms keys | 40 | 3/3 failed |

Failures from these runs (`got` is the `/api/auth/login` body for the app, and the
Compose field value for the repro):

```
typed    'smoke-visitor'                 got 'smk-iiortsveo'          (13 characters out)
typed    'smoke-visitor'                 got 'smke-visitoro'          (reordered)
typed    'smoke-password-55'             got 'smk-password55-eo'      (reordered)
typed    'smoke-password-55'             got 'smoke-pasord-55swke'    (17 characters in, 19 out)
typed    'smoke-visitor-compose-web'     got 'smke-visitor-compose-webo'
typed    'smoke-visitor-compose-web'     got 'smk-iio-ops-ebweomcrtsveo'
```

Reordering is the common case: a run of early characters ends up at the end of the
field. Characters are also duplicated (both the browser and Compose insert them) or
dropped. Both fields of the form are affected, so this is not specific to
`VisualTransformation`/password masking.

The app needs a much lower throttle than the standalone repro (6× versus 60×)
because its scene is heavier, so a frame there starves the main thread for longer.
That is why the app hits this in practice and the minimal repro only under
throttling.

## What it is not

- **Not a late frame.** Deferring every `requestAnimationFrame` callback by 100 ms
  or 300 ms (app) and 50 ms or 150 ms (repro) passed every run at throttle 1. It
  takes CPU starvation of the main thread itself, not a delayed frame.
- **Not headed/Xvfb/SwiftShader.** Every line in the table above is headless.
  The issue's original evidence used headed Chromium with SwiftShader under Xvfb,
  which merely starved the CPU; `--cpu-throttle` replaces that nondeterminism.
- **Not the app's text-field handling.** The sign-in fields are stock
  `OutlinedTextField(value, onValueChange)` with hoisted `String` state
  (`features/entry/ConnectScreen.kt`); nothing in the app touches the DOM input,
  the selection or the edit order, and the standalone Compose repro fails the same
  way.
- **Not the accessibility/DOM mirror being read back.** In the repro, Compose's own
  `TextFieldValue` (`window.__typed`, published by a `LaunchedEffect`) and the DOM
  backing field (`div.compose-backing-field`) hold the same scrambled text.

## Mechanism

Compose web does not let the browser edit its text field directly. It keeps a
hidden `contenteditable` element (`div.compose-backing-field`) and translates its
DOM events into Compose edit commands. In `1.13.0-alpha02`:

1. `DomInputStrategy.initEvents()` records each `beforeinput` together with
   `getTargetRanges()[0]` (the range the browser is about to edit) and does **not**
   process it immediately: `nativeInputEventsProcessor.registerEvent(evt)`
   (lines 115–140).
2. `registerEvent()` only appends to `collectedEvents` and schedules a checkpoint
   **on the next animation frame** —
   `window.requestAnimationFrame { runCheckpoint(...) }` (lines 63–67, 174–177).
3. `runCheckpoint()` then applies the queued edit as
   `SetSelectionCommand(firstRange.startOffset, firstRange.endOffset)` followed by
   `CommitTextCommand(data, 1)` (`NativeInputEventsProcessor.kt` lines 127–160,
   190–191).
4. Independently of that queue, every change of the field's value runs
   `updateState()`: `htmlInput.textContent = textFieldValue.text` and
   `setSelectionRange(htmlInput, …)` (`DomInputStrategy.kt` lines 70–92).

So the character's insertion index is captured from the DOM *at event time* but
applied *at the next frame*, and the same commit round trip overwrites the
element's whole text content and its caret. When the main thread is starved long
enough for several keystrokes and a `textContent` rewrite to interleave, the
recorded offsets no longer describe the text the command is applied to: the commit
lands at the wrong index (typically past the end of the shorter state, which
appends it) and a later commit for an earlier offset pushes the following
characters in front of it. A rewrite that lands between the browser's native
insert and the checkpoint also discards that insertion, which is how characters go
missing rather than only moving.

The DOM timeline captured on the app's Username field (throwaway instrumentation of
the backing element: `beforeinput`/`input`/mutation records, throttle 20) shows the two
signatures side by side — browser edits arrive as `characterData`
mutations, Compose's `textContent` rewrites as `childList 1/1` (one text node
replaced):

```
keydown s, m, o, k             text ""            (no beforeinput: nothing inserted)
keydown e  -> beforeinput "e"  text ""            -> input, text "e"
mutate  text "e"    caret 1    childList 1/1      (Compose rewrote the element)
beforeinput "-"  caret 1       text "e"           -> input, text "e-"
beforeinput "v"                text "e-"          -> input, text "e-v"
mutate  text "e-"   caret 2    childList 1/1      (Compose's rewrite drops "v")
beforeinput "i"  caret 2       text "e-"          -> input, text "e-i"
mutate  text "e-v"  caret 3    childList 1/1      (and writes an older state back)
beforeinput "s"  caret 3       text "e-v"         -> input, text "e-vs"
mutate  text "e-iv" caret 3    childList 1/1
```

The final field content is garbage of the same length as what was typed
(`smke-visitor-compose-webo` for `smoke-visitor-compose-web`), or longer when a
character is inserted twice: `smoke-pasord-55swke` is 19 characters for the 17
character `smoke-password-55`, with `e` and `k` duplicated. Each `childList 1/1`
rewrite moves the caret, and the next browser insert then lands at the rewritten
position, which is also where the duplicates come from: the browser and Compose
both apply the same character.

## App-side options, and why none is used

| Idea | Why not |
|---|---|
| Debounce or reorder the value sync in the app | The app has no access to the checkpoint, the queued commands or the backing element; the reordering happens entirely inside Compose before `onValueChange` runs |
| Keep the field's state closer to the field (fewer recompositions) | Does not remove the race, only lowers its probability — the minimal repro has the closest possible state (`remember` in the same composable) and still fails |
| Render a plain HTML `<input>` over the password field on web | A fragile workaround that bypasses Compose's text input for one form, loses IME/selection behaviour, and would need to be undone when the upstream fix lands |
| Pin an older Compose version | The input layer that reorders is the current one; there is no older version to pin to without other regressions, and the fault predates app#55 |

## Status

Waiting on an upstream fix. Two independent harnesses now prove the fault and can
confirm a fix without a device: `tools/web_signin_smoke.py --cpu-throttle 6` (app
symptom) and `tools/web_text_order_repro.py` (Compose-only). When the upstream fix
is in a Compose version the app can take, both should pass at their default
settings, and the `--cpu-throttle` option stays useful as a regression watch: it is
the only way this class of bug shows up without a loaded machine.

Upstream report material: the standalone project in
`docs/upstream/compose-web-text-order` (one `BasicTextField`, no VIPTV code) with
its README, plus the table and mechanism above. Related upstream issues in the same
code: CMP-10753 (caret drift, cited in `resolveSelection()`), CMP-8745 (iOS
composition), CMP-8773 (composite input), CMP-8373 (typing lag).