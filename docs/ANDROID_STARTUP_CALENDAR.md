# Android startup and calendar diagnostics — 2026-10-15

This records the real remote MainActivity/ApplicationModel/controller/UI on a private Android TV emulator, before and after the starting-shell change. The plan targets remain **500 ms interactive shell**, **one-second warm Home**, **three-second useful cold Home**, and one initial control request plus at most one pending update connection. A splash frame is not an interactive shell, and retained route navigation is not an Android warm process/activity launch.

Follow-up source now makes the supplied TV brand/progress layout usable during restoration: status and a focused Change connection action appear without waiting for the 600 ms brand animation. Back and that action cancel/retire the current restore and return to normal Connect; private Home/profile content stays hidden. Cancellation during a pending vault read starts no transport; cancellation during single-use refresh still preserves the completed rotation without entering Home or issuing bootstrap. The two controller regressions passed in the broader desktop gate; the full-App UI/recreation regression passed on retry after isolating its Coil singleton, and Wasm compilation passed. [150 ms UI capture](evidence/android-starting-shell-2026-10-15/tv-safe-starting.png), [UI receipt](evidence/android-starting-shell-2026-10-15/ui-test.xml), [controller cancellation cases](../composeApp/src/desktopTest/kotlin/org/viptv/app/StartupCancellationTest.kt). The post-change Android results below now exercise the final coherent native-origin APK; physical-device acceptance remains separate.

## Fixture and instrumentation

The optimized, minified `qa` build uses the actual application and HTTPS transport. Its synthetic loopback facade supplies 120 prepared Home cards, 1,200 calendar entries, a 750 ms bootstrap response and 700 ms delays on later calendar pages. Artwork is one generated 640×360 JPEG reused across cards, not a realistic set of independent provider images. The saved account refresh credential uses the real encrypted Android storage. No local engine, real provider, backend/gateway service or production account is involved.

Startup measures are kept distinct:

- `am start -W` / Android `Displayed` records full launch to first display, including process creation.
- App first-draw and interactive marks use `Process.getStartUptimeMillis`. These have a different starting point and are not substitutes for system launch timings.
- Useful Home requires a placed, nontransparent actual Compose label for the fixture title plus enabled click semantics on a drawn frame. The helper reads each annotated text separately so avatar/label merging is handled. `reportFullyDrawn` supplies the framework's full launch-to-useful-Home time.
- Warm **route** Home is measured after leaving the fully loaded calendar for retained Home. Full Android warm/hot startup is not claimed from this measurement.

Calendar frames use passive `Window.OnFrameMetricsAvailableListener` on a HandlerThread. It copies primitive metrics immediately, records dropped-report counts, and performs no per-frame logging or semantics scan. Semantics checks run at phase boundaries. First-draw frames are excluded from scroll quantiles. Reported platform deadline misses use each frame's allocated deadline; they are not inferred solely from a 16.67 ms threshold.

The dedicated Android TV 36 x86_64 AVD was `viptv-lifecycle-isolated`, emulator-5586, 1024 MiB guest RAM, two guest cores, SwiftShader and 960×540 at 60 Hz. The host had 20 logical CPUs, approximately 13.6 GiB available RAM and load averages 17.67/17.83/20.18 when recorded. Other user applications remained running. The owned build daemon was stopped and collaborating heavy builds were held, but this was not an isolated physical-device benchmark environment.

## Observed timings

| Case | System initial display | Interactive shell from process start | Useful Home, system fully drawn | Other |
| --- | ---: | ---: | ---: | --- |
| Cold process, no saved session | 919 ms | 327 ms | 3426 ms including synthetic login/profile workflow | Explicit profile selection → drawn Home 855 ms |
| Cold process, saved session, run 1 | 508 ms | 1386 ms | 1912 ms | Retained Home route return 50 ms |
| Cold process, saved session, run 2 | 386 ms | 1398 ms | 1829 ms | Retained Home route return 34 ms |
| Cold process, saved session, run 3 | 369 ms | 1344 ms | 1778 ms | Retained Home route return 30 ms |

The no-session app first-draw mark was 326 ms; its 919 ms system launch time shows why that app-relative mark cannot establish the 500 ms launch target. Its full-display time includes automated account login and profile selection and is not compared to an already-authenticated Home target. Restored runs force-stopped the process while keeping stored credentials and disk artwork caches. Each completed a fresh refresh and bootstrap; they are process-cold with warm disk caches, not fresh-install/empty-cache Home measurements.

The **500 ms shell target is not established by these before-change measurements**. In that restored path it was structurally delayed by authentication/bootstrap because TV showed only the noninteractive splash while `starting=true`; even the app-relative interactive marks exceeded the target. The follow-up above removes that structural gate in source, without treating an animation as interactive or lowering the target. The three restored useful-Home observations are below three seconds on this synthetic fixture. The 30–50 ms route return is useful component evidence, but does not establish the full one-second Android warm-start target. Physical reference-device acceptance remains open.

## Requests and calendar behavior

Each restored run issued **one device refresh and one bootstrap** before Home, then four calendar requests: skip/limit 0/100, 100/500, 600/500 and 1100/500. There was no per-card metadata, library, source-discovery or tracking fan-out. Thus the prepared-content path uses one bootstrap control call, but there are two total authentication/control calls on native session restoration; that distinction is retained against the plan's one-initial-control-request wording. Retained Home route return added no request.

Calendar showed its first page in **131 / 163 / 117 ms** after navigation. The probe focused the first row before later pages completed, verified that all 1,200 entries arrived without moving focus, then sent 80 actual D-pad Down key pairs at 100 ms spacing. Every run ended at row 80. Each scroll window lasted approximately 8.38 seconds and produced 490 non-first-draw frame reports:

| Run | Median render duration | p95 | p99 | Platform deadline misses | Dropped reports |
| --- | ---: | ---: | ---: | ---: | ---: |
| 1 | 16.74 ms | 17.04 ms | 17.71 ms | 3 | 0 |
| 2 | 16.74 ms | 17.00 ms | 17.36 ms | 2 | 0 |
| 3 | 16.74 ms | 17.10 ms | 18.61 ms | 2 | 0 |

These are emulator render-duration and focus/paging observations, not physical-TV FPS acceptance. They do not cover real provider artwork churn, all calendar filters/actions, prolonged scrolling, API26 hardware or a full Android warm activity launch.

Evidence: [raw frame/request/timing receipts](evidence/android-performance-2026-10-15/raw.json), [summary](evidence/android-performance-2026-10-15/summary.json), [host/fixture context](evidence/android-performance-2026-10-15/context.json), [no-session log](evidence/android-performance-2026-10-15/cold-empty.log), [restored 1](evidence/android-performance-2026-10-15/cold-restored-1.log), [restored 2](evidence/android-performance-2026-10-15/cold-restored-2.log), [restored 3](evidence/android-performance-2026-10-15/cold-restored-3.log), [actual Home](evidence/android-performance-2026-10-15/home.png), [first calendar page](evidence/android-performance-2026-10-15/calendar.png).

Qualified diagnostic APK SHA-256: `89c2d2ad86da8e469f54710e359f315f5db8531698ba841af4d9cc08546a6745`. Its optimized assembly passed. It intentionally used the existing Android JNI libraries while new native-origin APIs were still being developed; only the remote path was instantiated. This is not the final distribution or native-engine ABI qualification. The private emulator and facade were stopped after measurement. The user's physical TV, existing emulator, services and live Wasm directory were untouched.

Reproduction is in `PerformanceQaActivity`, `PerformanceQaFrames`, `PerformanceQaSemantics` and `tools/qa_performance_run.py`, with the optional performance dataset/delays on `tools/qa_lifecycle_server.py`. The driver checks the exact private AVD name before installation, clearing app data or input. Context7 documentation was fetched and checked against the platform [startup timing guide](https://developer.android.com/topic/performance/issues/launch-time), [FrameMetrics](https://developer.android.com/reference/android/view/FrameMetrics) and [frame listener contract](https://developer.android.com/reference/android/view/Window.OnFrameMetricsAvailableListener). Earlier probe-only failures were corrected before the accepted four runs; no incomplete run is included in these tables.


## After-change qualification

The optimized/minified diagnostic APK built successfully in 1m15s after the final native-origin snapshot. Its SHA-256 is `0c443d9b90c266138048b19d4e6d5d81d0f667adcb2be5baa8a412252f7a5c3a`; all three JNI ABIs were packaged from the final generated engine outputs. The Android packager strips debug sections, so packaged whole-file hashes differ from the generated Rust files: [artifact and strip receipts](evidence/android-startup-after-2026-10-15/artifact.json), [source JNI and host context](evidence/android-startup-after-2026-10-15/context.json). An independent read-only [APK/native audit](evidence/native-origin-apk-audit-2026-10-15.json) confirmed matching allocated ELF sections for all three engines, the pinned FFmpeg libraries, all 121 current exported bridge symbols and unchanged 110 pre-existing method checksums. This is an isolated diagnostic APK, not a user-installed distribution candidate. The tested route uses the real remote connection/controller/UI against the synthetic facade, not the native metadata/source runtime.

All six probe runs passed. The same 1 GiB private Android TV AVD and synthetic delays/dataset were used. Host load averages were 16.16/16.07/14.35 with approximately 13.8 GiB available memory; the owned Gradle daemon was stopped and other agents held heavy builds. The first process used an empty session; later process-cold launches retained encrypted credentials and artwork caches.

| Run | Framework launch state | Framework first display (ms) | Process-relative interactive shell (ms) | Useful Home framework fully drawn (ms) |
|---|---|---:|---:|---:|
| Empty session | COLD | 691 | 378 | 3801 including automated login/profile selection |
| Restored 1 | COLD | 498 | 218 | 1862 |
| Restored 2 | COLD | 505 | 218 | 1860 |
| Restored 3 | COLD | 352 | 150 | 1574 |
| New Activity in the same process | WARM | 222 | 34 **from Activity entry** | 1460 |
| Cancel delayed restore | COLD | 371 | 139 | 1705 to normal Connect, including deliberate 1100 ms late-result observation |

The starting-state shell was enabled and focused before restore completed. Its process-relative first-draw/interactive pairs were 216/218, 217/218 and 149/150 ms in the restored runs. The measured structural delay from the earlier noninteractive splash is gone; those process-relative marks are **not** substituted for framework launch time or a physical-TV acceptance result. The empty-session first display was still 691 ms and one restored first display was 505 ms, so the unchanged **500 ms complete-launch shell target is not established**.

The warm diagnostic actually finished the prior Activity, verified its process stayed alive, then launched a new Activity/model. The framework reported `WARM`, and the PID stayed **2696**. Useful Home was drawn 1189 ms after Activity entry and 1460 ms after the framework's launch start: the **one-second warm Home target was missed on this fixture**. Retained route return was separately 47/41/29 ms and is not used to make that target pass. All three restored cold useful-Home observations remained below the unchanged three-second healthy-fixture target. Empty-session fully-drawn time includes login/profile selection and does not measure an authenticated Home launch.

Cancellation used actual D-pad Center on the focused Change connection action during the delayed restore. It returned to normal Connect, then waited 1100 ms and verified no Home, profiles or late private state appeared. That run issued one refresh and **zero bootstrap calls**; it did not revoke or replay a completed single-use rotation. [Actual post-cancel TV screen](evidence/android-startup-after-2026-10-15/5-cancel-restored-connect.png). The three successful restored launches and the real warm Activity each issued one refresh and one bootstrap, with no per-card metadata/library/source/tracking fan-out. The one prepared-content request versus two total authentication/control requests distinction remains open against the plan wording.

Each calendar run loaded 1200 rows in four requests. Later pages preserved focus on row 0; 80 real D-pad Down inputs reached row 80 in all three runs. First-page useful draws were 163/155/105 ms. The 490 non-first-draw frame reports per run had these TOTAL_DURATION distributions:

| Calendar run | p50 (ms) | p95 (ms) | p99 (ms) | Platform deadline misses | Callback reports dropped |
|---|---:|---:|---:|---:|---:|
| 1 | 20.223 | 24.497 | 26.634 | 2 | 0 |
| 2 | 16.737 | 16.962 | 17.358 | 3 | 0 |
| 3 | 16.734 | 17.076 | 17.637 | 2 | 0 |

The platform reported a roughly 33.33 ms frame deadline for 487–488 of the 490 reports per run, so its low miss count does not demonstrate the plan's physical-device 55 FPS goal. The first scroll also had a materially slower distribution than subsequent runs. These software-emulator samples establish focus continuity and request bounds, not physical-TV frame-rate acceptance.

Complete [raw samples](evidence/android-startup-after-2026-10-15/performance-result.json), [summary including every deadline distribution](evidence/android-startup-after-2026-10-15/summary.json), and per-run framework/app logs are retained alongside the [warm Home capture](evidence/android-startup-after-2026-10-15/4-warm-activity-home.png). Both owned fixture and emulator were stopped after collection; user devices and services were untouched. Reproduce with `tools/qa_performance_run.py --iterations 3 --warm-activity --cancel-restore` and the synthetic facade options above.
