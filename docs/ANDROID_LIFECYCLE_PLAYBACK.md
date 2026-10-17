# Android app lifecycle and growing HLS — 2026-10-15

The real Android `MainActivity`, retained `ApplicationModel`, shared `ApplicationController`, `RemoteConnection` and Android Media3 renderer passed isolated lifecycle qualification. This used the normal application UI and HTTP transport, with a synthetic HTTPS API facade and generated media. It does not claim real backend/provider qualification or physical-TV acceptance.

## Defects reproduced and fixed

The first run retained navigation, profile and player during recreation, but playing playback remained paused after Home and launcher return. Android can deliver lifecycle `ON_STOP` to the renderer before `MainActivity.onStop`: the renderer's pause feedback then erased the controller's knowledge that playback had been playing. The Activity now captures background intent in `onPause`, before the renderer's stop feedback. Its `onStop` remains idempotent. The renderer applies foreground intent after recomposition, using an `ON_START` epoch so stop/start events coalesced while composition was suspended still trigger application. User-paused playback stays paused.

The same fixture reproduced a separate startup jump. The app supplied `positionMillis=0`, `timelineOffsetMillis=600000`, `live=false`. The first three playlist observations were at 0, 21 and 1548 ms, all sequence zero with ten two-second segments. Nevertheless, Media3's first real window default was 14000 ms, the first loaded segment was 007, and tracking began at 614000 ms. This was not an already-rolled initial playlist or a resume request.

Pinned Media3 1.11.1 `MaskingMediaSource` intentionally interprets a deferred zero equal to its placeholder default as a request for the real window default. For host-declared on-demand HLS, `TitleHlsMediaSource` now forwards the original timeline with `defaultPositionUs=0` and no live configuration. Window dynamism, periods, identities, timestamps, duration and `positionInFirstPeriodUs` are unchanged. The wrapper applies only to an actual `HlsMediaSource` when `PlaybackInput.live` is false. True live playback retains the original source and normal edge behavior. No duration-sized target-offset workaround is used.

## Accepted runtime evidence

A private Android TV 36 x86_64 AVD, `viptv-lifecycle-isolated` on `emulator-5586`, used 1024 MiB guest RAM and software rendering. The driver's AVD-name guard rejects other emulators before installation, clearing data or input. The user's existing emulator and physical TV were untouched.

The debug Activity subclasses the actual MainActivity. Its retained probe reads the same ViewModels and drives normal controller actions; profile selection uses real D-pad input. It does not replace the controller, player, state reducers or lifecycle methods.

- Initial D-pad OK selected the first of two profiles.
- Details and Player each survived explicit `Activity.recreate()`, retaining the same ApplicationModel/controller, detail/input objects and actual ExoPlayer. The selected profile and route remained intact. Paused position was unchanged.
- Home and launcher return retained the same Activity/task, player and profile. Both user-paused and playing cases passed; only the latter resumed. The renderer clock stopped while backgrounded.
- One forced fixture 401 caused exactly one refresh rotation. Secure Android storage contained the rotated fixture refresh value. Totals stayed login 1, refresh 1, bootstrap 4, profile selection 1 across recreation and returns.
- Playback opened once, acknowledged first frame once and deleted its lease once. Tracking was start/pause/start/pause/start/stop in increasing sequence order.
- Initial tracking and presentation were 600000 ms, and the first loaded segment was 000. The first real window default remained zero and dynamic; after rolling, its period offset was 4000 ms.
- The actual TV seekbar displayed **10:05** for controller position **605038 ms**. Accessibility XML confirmed the label. The image shows generated media time **00:00:05.083**, consistent with the 600-second gateway origin and renderer/window clock. The test also compared the controller directly with `player.currentPosition + window.positionInFirstPeriodMs + gatewayOffset`, within 250 ms.
- Background's final saved progress included **605481 ms**, matching its stopped renderer sample. Close saved **605707 ms**. Tracking and progress retained the full 720000 ms original duration.
- A separate real-renderer explicit nonzero start presented at exactly **8000 ms**. True live playback presented at its unchanged current default, **32000 ms**, with `timeline_live=true`.
- The complete SRT/WebVTT/ASS sidecar test passed again on this APK: original position **607018 ms**, rolling offset **4000 ms**, one primary transition, selected audio retained, embedded restoration, forward/backward seeks, canceled/invalid replacement, Off and closing-session isolation all passed.

Evidence: [accepted app log](evidence/android-lifecycle-2026-10-15/accepted.log), [HTTP counts and clocks](evidence/android-lifecycle-2026-10-15/accepted-status.json), [result](evidence/android-lifecycle-2026-10-15/result.json), [TV seekbar](evidence/android-lifecycle-2026-10-15/player-clock.png), [accessibility tree](evidence/android-lifecycle-2026-10-15/player-clock.xml), [nonzero start](evidence/android-lifecycle-2026-10-15/nonzero-start.log), [true live](evidence/android-lifecycle-2026-10-15/live-edge.log), [sidecar repeat](evidence/android-lifecycle-2026-10-15/sidecar-after.log). The distinct failed [foreground run](evidence/android-lifecycle-2026-10-15/foreground-before.log) and [startup trace](evidence/android-lifecycle-2026-10-15/startup-before.log) are retained, with [initial playlist/HTTP observations](evidence/android-lifecycle-2026-10-15/startup-before-status.json).

Android debug assembly passed with engine rebuild skipped against the then-matched generated binding/JNI snapshot. Qualified immutable APK SHA-256: `2f78a0a9afb9a55320f622b196910bf2cc9114b368b9d8115ca755b662178311`. Subsequent Live favorite API work requires the separately coordinated final native rebuild/package; this candidate does not claim those new APIs.

The private emulator and both owned fixture servers were stopped after qualification. No user device installation, service restart, live backend change or served Wasm modification occurred.

## Repeating the check

Use a newly created dedicated AVD named `viptv-lifecycle-isolated`, never an existing user AVD. Generate 120 seconds of silent or synthetic H.264/AAC video as two-second HLS `segment-000.ts` onward in a fresh disposable directory. `tools/qa_lifecycle_cert.py` signs a fresh localhost fixture leaf using the existing local QA CA; it does not alter the CA. Keep its private fixture key outside evidence/repositories. The debug APK trusts that public QA CA only for localhost. Host verification uses the same CA with strict certificate validation.

Run `tools/qa_lifecycle_server.py --root /tmp/FIXTURE --media /tmp/FIXTURE/media`, then `tools/qa_lifecycle_run.py --apk /tmp/IMMUTABLE.apk --root /tmp/FIXTURE --ca ../.local-https/certs/ca.crt --origin https://127.0.0.1:PORT`. The driver uses adb reverse on its verified private AVD, installs/clears only the fixture APK there, collects the real UI clock, drives Home/launcher return, and verifies direct nonzero/live renderer starts. Use a fresh server for each full run. Stop owned processes afterward.

Current API documentation was fetched through Context7 and checked against the actual pinned [MaskingMediaSource](https://github.com/androidx/media/blob/1.11.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/source/MaskingMediaSource.java), [WrappingMediaSource](https://github.com/androidx/media/blob/1.11.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/source/WrappingMediaSource.java) and [ForwardingTimeline](https://github.com/androidx/media/blob/1.11.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/source/ForwardingTimeline.java).

This is short functional qualification with a 600-second origin, not forty minutes of elapsed playback. It does not measure physical-device decoder endurance, audible output, arbitrary provider formats, API26 hardware, process-death restoration or acceptance FPS/startup budgets under a controlled load.


## Pause without stop follow-up

A final lifecycle audit found that the new `onPause` suspension needed its corresponding `onResume` return path. A floating/translucent Activity can leave the app partially visible in STARTED state, then return directly from pause to resume; `onStart` is not repeated. MainActivity now calls the same idempotent `resumePlayback()` in `onResume`, as well as retaining its stop/start handling. It resumes only the controller's captured lifecycle pause; a user pause has no captured resume intent. The normal release APK under `artifacts/android-release-2026-10-15/abi3-755cb5bc1722/` includes this fix.

This behavior was checked against the current [Android lifecycle documentation](https://developer.android.com/guide/components/activities/activity-lifecycle) after Context7 resolution/fetch (its extracted snippets were shallow, so the official lifecycle page supplied the exact partially-visible transition contract). No blocking network/database operation was added to an Activity callback; the controller schedules persistence in its owned coroutine scope.

The actual app/controller/renderer probe then launched a real floating `PauseOnlyQaActivity` twice on the private Android TV AVD. Both transitions recorded **one pause, one resume, zero stops and zero starts**. While covered, the actual ExoPlayer paused and the clock stopped. On return, an existing user pause remained at **605454 ms**; the playing case resumed and advanced to **606025 ms**. The Activity, ApplicationModel/controller, playback input, ExoPlayer and route were retained. Authentication stayed login 1, refresh 1, bootstrap 4; the playback lease stayed open 1, first-frame acknowledgment 1 and delete 1.

The same run also repeated the initial-profile focus, Details/Player recreation, background/foreground playing and user-paused cases, saved-progress/title-clock checks, explicit nonzero start (**8000 ms**) and true-live edge (**34000 ms**). All passed. The TV UI showed **10:05** at a reported title clock **605454 ms**, including gateway origin 600000 ms and rolling-window origin 4000 ms.

Receipts: [actual lifecycle log](evidence/android-pause-only-2026-10-15/activity.log), [HTTP/clock observations](evidence/android-pause-only-2026-10-15/server-status.json), [result](evidence/android-pause-only-2026-10-15/result.json), [actual TV clock capture](evidence/android-pause-only-2026-10-15/player-clock.png), and [diagnostic artifact](evidence/android-pause-only-2026-10-15/artifact.json). This follow-up used a debug APK with the same frozen production lifecycle fix as the normal release, not the release artifact itself. Its SHA-256 is `e984c35b80d6df4e8a20a5833225c77712fb68e7b4b142434704d0144357807b`. The existing optimized startup measurements remain a separate earlier artifact; they are not re-attributed to this run. The owned emulator and fixture were stopped; user devices were untouched.


Further official 16 KiB-emulator runtime qualification, including 184-second direct rolling HLS and 62-second managed replacement seeks, is recorded in [ANDROID_16K_RUNTIME.md](ANDROID_16K_RUNTIME.md). This does not establish physical ARM64 or 40-minute endurance.
