# Android 41-minute playback and original-player OSD qualification — 2026-10-15

**Passed: 41 minutes of actual normal-speed playback, followed by visible original-player elapsed/seekbar capture and exact renderer/progress agreement before the single lease close.** The immutable debug APK is `5abd366c036b58260e4ce3dd2d026a0c717e45950341747593e59f1be88f353a`. The raw host [result](evidence/android-osd-endurance-2026-10-15/full-run/run/result.json) reports `passed: true`; the native probe also reached `PASS`.

This qualifies Android 36 on the official Google TV experimental **x86_64 emulated 16 KiB application environment**, using the real MainActivity, retained ApplicationModel/controller and Media3 renderer against a synthetic local HTTPS facade. It does not qualify physical TV, physical ARM64/16 KiB hardware, real providers, audible output, passthrough, HDR/DRM or hardware-decoder performance. The underlying x86_64 kernel/MMU remains 4 KiB. Host audio was disabled.

## Actual elapsed time and retained playback

| Observation | Result |
| --- | ---: |
| Requested sustained interval | 2,460,000 ms (41 minutes) |
| Continuous native `SystemClock.elapsedRealtime` interval | 2,460,269 ms |
| Independent host monotonic sustain interval | 2,460,379 ms |
| Host/native interval difference | 110 ms |
| Native clock samples preserved / reported | 477 / 477 |
| Sample elapsed times | Strictly increasing |
| Playback speed in every sample | 1.0 |
| Explicit paused seeks / exact tracking start positions | 80 / 80 |
| Player replacements | 0 |
| Home cycles | 9 (2 baseline, 7 periodic) |
| Lease opens / closes | 1 / 1 |
| Title/window origin advance | 2,434,000 ms |
| Maximum sampled controller/renderer clock difference | 208 ms |
| Emitted progress writes | 261 |
| Maximum rendered / dropped-frame counters | 36,286 / 2 |

The same app PID 2374 and retained original player survived the whole run. The observer was uninterrupted; no elapsed-time recovery, app restart, player replacement or fixture restart was needed. Clock comparisons used actual ExoPlayer position plus actual Media3 window origin plus the explicit input timeline offset. The 600,000 ms title offset was never counted as elapsed playback.

Baseline checks exercised SQLite profile/PIN create/close/reopen, TV profile focus, intentional authentication refresh, details/player Activity recreation, playing and user-paused Home returns, and pause/resume without stop/start. Periodic seeks ran approximately every 30 seconds and periodic Home returns every 60 clock samples. Authority counts remained one login, one refresh, four bootstraps and one profile selection.

## Original-player visual and tracking proof

After the full interval, the probe paused the original player and used D-pad Up to reveal the existing TV controls. It read the actual Compose semantics and flushed actual emitted progress. The host captured the [original-player screenshot](evidence/android-osd-endurance-2026-10-15/full-run/run/player-endurance-clock.png) and [accessibility tree](evidence/android-osd-endurance-2026-10-15/full-run/run/player-endurance-clock.xml) while that player remained paused and its lease remained open. The screenshot was visually inspected: the elapsed label **50:53**, seekbar thumb and remaining label **−4:06** are visible over the real synthetic source frame (40:53.267).

| Original paused-player observation | Result |
| --- | ---: |
| Actual renderer title position | 3,053,162 ms |
| Displayed elapsed text | 50:53 |
| Duration | 3,300,000 ms |
| Actual Compose slider fraction | 0.9252005815505981 |
| Renderer position / duration | 0.9252006060606061 |
| Emitted progress before capture ACK | 3,053,162 ms |
| Final saved progress after close | 3,053,162 ms |
| Lease closes before capture ACK | 0 |
| Original player identity hash | 196144455 |

The [capture proof](evidence/android-osd-endurance-2026-10-15/full-run/run/capture-proof.json) records the actual semantics, original app PID, screenshot/XML hashes and host acknowledgement. The native probe waited for that acknowledgement while asserting paused state, unchanged position, identical player and identical playback input. Its acknowledgement log confirms `still_paused=true same_player=true same_input=true`. Only then did it close once and verify final saved progress exactly against the renderer. No reopened player or fabricated overlay is used as the final endurance seekbar.

## Fixture, supervision and preserved failures

The fixture used the existing generated 2,700-second H.264/AAC media: 320×180, 15fps, silent stereo audio, two-second segments and 1,350 real segment files. A fresh fixture advanced a rolling HLS playlist throughout this run. The owned private AVD `viptv-16k-isolated`, serial 5586, used two cores, 2GiB RAM and software GPU. SDK36, television UI mode, 16,384-byte shell/app page size and disabled compatibility fallback were verified before installation.

A unique transient **user** service, `viptv-osd-endurance-20261015T2017Z.service`, supervised only this run's emulator, fixture 18586 and observer. It used `Restart=no`; none of these processes restarted. The [exact launch arguments and ownership receipt](evidence/android-osd-endurance-2026-10-15/full-run/service-launch.json), source snapshots, verified service properties, live PID/clock receipts and build/APK provenance are in [the evidence directory](evidence/android-osd-endurance-2026-10-15/).

The emulator image's unrelated `com.google.android.apps.mediashell` PID 3425 crashed because its bundled `libcast_service.so` has 4 KiB program alignment. Its [full error log](evidence/android-osd-endurance-2026-10-15/full-run/run/unrelated-runtime.log) is preserved. The observer scoped runtime failures to VIPTV PID 2374 and continued without interruption; this is not a device-wide crash-free claim.

The prior [three-minute interrupted attempt](evidence/android-osd-endurance-2026-10-15/interrupted-attempt/interruption-receipt.json) remains failed and explicitly does not count as endurance. The earlier [short capture proof](evidence/android-osd-endurance-2026-10-15/short-capture-proof/result.json) proves only the capture handshake. The separate [first 41-minute run](ANDROID_40MIN_ENDURANCE.md) retains its final-OSD failure and all raw receipts; the fresh full run above resolves that visual qualification gap.

## Evidence and cleanup

[summary.json](evidence/android-osd-endurance-2026-10-15/summary.json) records the bounded pass. [SHA256SUMS.json](evidence/android-osd-endurance-2026-10-15/SHA256SUMS.json) hashes the preserved files. Independent post-run checks confirmed all 477 samples, increasing native elapsed time, speed 1.0, 80 exact seek tracking starts, original-player capture/ACK ordering, one open/close, exact final progress and cleanup.

At 20:59:57Z the owned emulator PID 3088933, fixture PID 3089657 and observer PID 3089658 were terminal. The owned reverse was removed, ports 5586/5587/18586 were closed and the exact transient service was stopped/unloaded. [Cleanup receipt](evidence/android-osd-endurance-2026-10-15/full-run/cleanup-verified.json) records those checks. Existing manual/user services and physical/user devices were not modified. No production source edits or build were needed for this fresh qualification run; the frozen debug artifact predates it.
