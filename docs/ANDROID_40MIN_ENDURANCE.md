# Android 41-minute playback endurance — 2026-10-15

**Follow-up qualification:** A fresh, uninterrupted 41-minute run on immutable debug APK `5abd366c036b58260e4ce3dd2d026a0c717e45950341747593e59f1be88f353a` passed the original-player final OSD, accessibility, Compose seekbar and exact progress assertions before capture acknowledgement and the single close. See [ANDROID_OSD_40MIN_ENDURANCE.md](ANDROID_OSD_40MIN_ENDURANCE.md). The first run and its failed visual assertion below remain unchanged historical evidence.

**Actual renderer/title-clock/tracking endurance passed for 41 minutes. The final original-player OSD/accessibility capture remains unqualified.** The native probe completed all clock, seek, lifecycle, authority and close assertions on immutable debug APK `347e9bfc1871c4da1ecfe43d56f1f9a66c3999ada6e52cb1823f7069baefd207`. The host's extra final OSD assertion failed because the programmatic pause left the TV controls auto-hidden. The raw host result remains `passed: false`; it has not been relabeled.

This is Android36 on the official Google TV experimental 16 KiB **x86_64 emulated application environment**, with real MainActivity/ApplicationModel/controller/Media3 playback and a synthetic HTTPS API facade. It is not physical-TV, physical ARM64/16 KiB or real-provider qualification. The player code was unchanged; only debug QA duration/telemetry and host fixture/observer code changed.

## Actual elapsed time and observations

| Observation | Result |
| --- | ---: |
| Requested sustained elapsed time | 2,460,000ms (41 minutes) |
| Native continuous `SystemClock.elapsedRealtime` interval | 2,464,637ms |
| Host elapsed interval from original sustain start | 2,464,748ms |
| Playback speed in every native clock sample | 1.0 |
| Clock samples preserved / native sample counter | 471 / 471 |
| Explicit paused seeks | 79 |
| Exact requested renderer seek positions | 79 / 79 |
| Exact emitted tracking `start` seek positions | 79 / 79 |
| Player replacements | 0 |
| Total Home cycles | 9 (2 baseline, 7 periodic) |
| Opened / closed leases | 1 / 1 |
| Title/window origin advance | 2,438,000ms |
| Maximum sampled controller/actual-renderer difference | 207ms |
| Final actual renderer title / emitted progress | 3,049,283 / 3,049,283ms |
| Final difference | 0ms |
| Emitted progress writes | 259 |
| Maximum renderer presented-frame counter | 36,391 |
| Maximum dropped-frame counter | 3 |

Native comparisons used actual ExoPlayer position plus actual Media3 window origin plus the explicit input timeline offset. The 600,000ms title offset was never used as elapsed playback. Actual host/native elapsed intervals above independently exceed 40 minutes. All 471 merged clock samples have strictly increasing native elapsed times; buffer recovery preserved every sample counted by the native probe.

The same app PID 2403, retained player and direct lease survived the full native run. Baseline checks also exercised native SQLite profile/PIN create/close/reopen, initial TV profile focus, intentional authentication refresh, details/player Activity recreation, playing/user-paused Home return and pause/resume without stop/start. Periodic seeks ran about every 30 seconds; periodic Home return ran every 60 clock samples. There was one login, one refresh, four bootstraps and one profile selection. The native final `PASS` came after the lease was closed and saved progress was compared with the actual renderer.

## Synthetic fixture and provenance

The fresh fixture contains 2,700.0 seconds of generated H.264/AAC video and silent stereo audio: 320×180, 15fps, two-second segments, 1,350 actual files, 65,518,158 bytes. The last segment starts at PTS 2699.421333 and lasts two seconds. FFmpeg generated the assets using one encoder thread; the actual app played them at normal speed for the elapsed interval above. The cache is `/home/vynxc/.cache/viptv-qa/runtime-40min-2026-10-15/media`.

The HTTPS server advanced a real rolling HLS playlist throughout playback. Its old three-digit filename guard was corrected to accept three or four digits; otherwise segment 1000 would have failed after roughly 33 minutes. Explicit host preflight requests to segments 1000 and 1349 are recorded separately from renderer consumption. The actual renderer later crossed segment 1000 successfully. No end-of-file or synthetic host offset substituted for real elapsed time.

The existing owned private AVD `viptv-16k-isolated`, serial `emulator-5586`, used 2GiB RAM, two cores and software GPU. SDK36, television UI type 4 (night mode, `0x24`), shell page size 16384 and disabled compatibility fallback were verified before installation. The app itself reported page size 16384. The underlying x86_64 kernel/MMU remains 4 KiB; this is Android's emulated 16 KiB application environment.

The debug-only build succeeded in 52 seconds with one Gradle worker and reused unchanged native libraries. The APK was copied and hashed before other agents resumed common-source edits. No production/player source, physical device, real account/provider, manual TV service or served Wasm output was modified by this qualification.

## Observer interruption and recovery

At 28m53s, the first observer incorrectly treated an unrelated `AndroidRuntime` fatal line as an application failure. PID3055 belonged to the image's `com.google.android.apps.mediashell`; its bundled `libcast_service.so` rejected 16 KiB alignment. This was a real system-app crash, so no device-wide error-free claim is made.

The VIPTV app PID2403 and its player remained alive and continued native checks. The failed observer result and the full unrelated crash were preserved. A new observer scoped to PID2403 attached without restarting the app, player, fixture or elapsed timer. The pending 30-minute Home handoff used the existing task and completed without a native timeout. Thirty-nine buffered samples were recovered and merged with the 336 already flushed; final deduplication retained all 471 native clock samples. Later native checks and final `PASS` proved player/lease continuity.

The original host UTC start was retained. The new observer reanchored its monotonic reader to that original, unchanged host wall-clock epoch; this clock method is explicit in the receipt. The native Android elapsed clock remained continuous. The 41m04.748 host and 41m04.637 native intervals agree within 111ms. The host interruption did not reset elapsed time or fabricate title-offset time.

## Final visual capture gap

The initial real TV OSD displayed 10:05 and matched accessibility; its screenshot was inspected. At the end, the native probe paused the original player and logged expected elapsed 50:49, but the TV controls were auto-hidden. The inspected final screenshot shows the actual synthetic source frame at 40:49.333, without the OSD. The final accessibility assertion therefore remains false.

After that capture the native probe closed the original player, verified final emitted progress exactly against the renderer and logged `PASS`. It was already closed when the missing OSD was noticed. No reopened player, preview or fabricated overlay is presented as the original endurance seekbar. This gap does not negate the completed native clock/tracking checks, and those checks do not establish the missing final visual OSD assertion.

## Receipts and cleanup

[summary.json](evidence/android-40min-endurance-2026-10-15/summary.json) separates `core_endurance_passed: true` from `host_final_osd_capture_passed: false`. The evidence directory retains the original failed observer, unrelated system crash, buffered recovery, scoped observer, all merged samples, raw host result, native `PASS`, tracking HTTP status, initial/final captures, exact APK/media identities, QA sources and build log. Private keys and application databases are excluded.

Owned guest 5586 and fixture 18586 were stopped, the reverse was removed, and both original/resumed observer processes are terminal. Cleanup verifies each owned PID is gone. Other agents' devices and user devices were not modified.

Audible audio, passthrough, HDR/DRM, hardware decoder performance, physical-TV runtime and final original-player OSD/accessibility remain outside this result.
