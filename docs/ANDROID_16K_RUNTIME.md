# Android 16 KiB runtime qualification — 2026-10-15

The latest normal release `4cb59ab8ea453d8e20dfc5b731fe8b430bc1818c7a95e55a9c8d59bbed4f3193` installed and passed local Profiles → Home and cold local Profiles restoration on the official Google TV experimental 16 KiB x86_64 image, with compatibility fallback disabled. The final bounded follow-up covers its startup and first-local-entry marker ordering. Earlier immutable debug `7699e92dc23a037933402abebfcf30bcba40b378ea4d37c212efddd3424cf414` separately passed native profile storage/PIN/reopen, H.264/AAC rolling HLS, managed replacement seeks and H.264/E-AC-3 Matroska. Those extended playback checks were not repeated for the new normal APK.

This is Android's official **emulated 16 KiB application environment**, not physical ARM64 qualification. Both shell `getconf PAGE_SIZE` and app `Os.sysconf(_SC_PAGESIZE)` reported 16384; underlying x86_64 `KernelPageSize` and `MMUPageSize` remained 4 KiB. Android documents x86_64 emulation because that architecture does not support actual 16 KiB pages. [Android page-size testing](https://developer.android.com/guide/practices/page-sizes), [Android's emulator explanation](https://android-developers.googleblog.com/2024/08/adding-16-kb-page-size-to-android.html).

## Results

All extended checks used immutable debug APK `7699e92dc23a037933402abebfcf30bcba40b378ea4d37c212efddd3424cf414`, the real MainActivity/ApplicationModel/RemoteConnection/AndroidPlaybackSession, and a synthetic HTTPS facade. This tests actual client rendering, native libraries, lifecycle, and emitted HTTP data; it is not a real backend gateway or tracking-service test.

| Observation | Direct HLS | Managed HLS |
| --- | ---: | ---: |
| Real sustained elapsed time |184,436ms|62,169ms|
| Clock samples |36|12|
| Explicit paused seeks |6|2|
| Lease/player replacements |0|2|
| Total opened/closed leases |1/1|3/3|
| Title/window origin advance |158,000ms|32,000ms|
| Maximum sampled controller/renderer difference |191ms|194ms|
| Final emitted progress/actual renderer difference |126ms|113ms|

The direct playlist's first sequence advanced continuously: the real Media3 window origin moved 4,000→162,000ms while one player and input survived six seeks. Managed seeks restored 612,000 ms and 632,000 ms exactly through new lease-specific playlists and host timeline offsets; each replaced the input/player, retained paused intent/profile/route, and sent exactly one first-frame and close request. Every seek's outgoing tracking `start` position matched its requested title time exactly. Final progress was compared with actual ExoPlayer time plus Media3 window origin plus host offset, not merely with another copy of controller state.

Both scenarios also passed initial D-pad profile focus, native Rust profile/PIN/create/close/reopen, retained ViewModel/controller/player on Activity recreation, playing and user-paused Home return, and pause/resume without stop/start. Each scenario recorded one login, one intentional token refresh, four expected bootstraps and one profile selection; recreation, lifecycle changes and seeking added no bootstrap/auth replay. Initial visible elapsed 10:05 was checked against accessibility semantics and inspected screenshots. Explicit nonzero direct startup landed 8,000 ms, and true-live playback retained its normal 14,000 ms edge start in the fresh 20-second fixture window.

The packaged FFmpeg E-AC-3 decoder presented H.264/Matroska video and reached 20,053 ms over 21,492 ms of wall time without player failure on the same immutable debug `7699e92dc23a037933402abebfcf30bcba40b378ea4d37c212efddd3424cf414`. AAC was decoded by FFmpeg throughout both HLS scenarios. The earlier debug APK `6eced38254399cafec14bdfab6f9ccdff9d615c929ff9f2e74155d5368da38c3` separately passed E-AC-3 and native/lifecycle checks; its receipts remain separate.

The earlier normal `b4394d8aab1c7a6e543431841ab9c75750809f94d66c206bd78195a63405438f` followed Use without account → initially focused Default profile → local Home, without native-loader or AndroidRuntime errors. Normal release excludes the debug harness and QA CA. Its framework COLD launch receipt was 292 ms; this does not establish the 500 ms interactive-shell budget, warm-start target, first-useful-Home target, or physical-TV performance. The earlier normal 3b047 release's 1349 ms framework receipt remains separate; the differing guest/cache conditions do not support a performance comparison.

## Separate 41-minute endurance result

A fresh frozen debug `5abd366c036b58260e4ce3dd2d026a0c717e45950341747593e59f1be88f353a` passed 2,460,269ms actual native sustained playback and 2,460,379ms independent host elapsed: 477 clock samples at speed1.0, 80 exact paused seeks/tracking starts, seven periodic Home cycles, one retained player/lease and maximum sampled clock difference208ms. The original paused player's visible50:53 elapsed label, actual Compose seekbar fraction and emitted progress were verified before host capture acknowledgement and its single close; final renderer/progress both equalled3,053,162ms. The observer was uninterrupted. The unrelated system Cast-app alignment failure is preserved separately. See [ANDROID_OSD_40MIN_ENDURANCE.md](ANDROID_OSD_40MIN_ENDURANCE.md). The prior [first41-minute run](ANDROID_40MIN_ENDURANCE.md) and its failed OSD assertion remain historical evidence; neither a short proof nor an interrupted attempt substitutes for the fresh full run.

## Final normal first-entry/cold-start follow-up

Immutable final normal `4cb59ab8ea453d8e20dfc5b731fe8b430bc1818c7a95e55a9c8d59bbed4f3193` passed a separate bounded run on the same explicitly owned 16 KiB guest. Exact AVD identity, SDK36, page size 16384 and disabled compatibility fallback were verified before installation. A separate post-smoke environment read on the unchanged preserved AVD recorded television UI type 4 with night mode enabled (`0x24`); that verification guest was then stopped without rerunning the app. Owned app data was deliberately reset with `pm clear`, and the welcome screen was observed before Use without account; this was first local entry, not an already remembered mode.

First entry showed Profiles with Default focused before explicit D-pad selection reached actual native local Home. Force-stop and cold launch again showed Who’s watching? with Default focused before any Home or welcome screen. Explicit selection reached Home. Both captures were visually inspected; runtime/linker logs showed no application error. This preserves the profile gate and does not claim a new PIN-protected UI scenario. Framework COLD receipts were 233ms first launch and 198ms reopen, not interactive/Home performance measurements.

[Final 4cb receipts](evidence/android-16k-runtime-2026-10-15/normal-4cb/result.json) retain the exact hash, fingerprint, fresh-entry proof, accessibility trees, launches, captures and owned cleanup. Only one of this agent’s guests ran at a time; phone agent 5590 was left untouched. No fixture, gateway, media or extended lifecycle/endurance was rerun. Owned 5586 is stopped and its emulator process is gone.

## Earlier normal 6b startup follow-up

The existing owned AVD path and prior cleanup receipt were verified before boot; no guests were running. Before installing immutable normal `6b7079`, the guest reported the exact owned name, SDK36, `getconf PAGE_SIZE=16384`, `bionic.linker.16kb.app_compat.enabled=false` and `pm.16kb.app_compat.disabled=true`. Each mutating command rechecked name, SDK and page size.

Fresh startup followed Use without account → Profiles with Default initially focused → explicit D-pad selection → actual native local Home. After force-stop and cold launch, Who’s watching? appeared with Default still initially focused, with no welcome or Home before explicit selection. Selecting the unprotected Default again reached Home. Both Home and the cold profile gate were visually inspected; native-loader/AndroidRuntime logs showed no application error. This checks the profile gate rather than claiming a new PIN-protected UI scenario.

Framework COLD receipts were 321ms first launch and 185ms process reopen; these are not interactive/Home performance budgets. [The separate latest-normal receipt](evidence/android-16k-runtime-2026-10-15/normal-6b/result.json) retains the exact hash, fingerprint, raw launch output, accessibility trees and captures. Only the owned 5586 guest was started and stopped; its emulator process is gone. No fixture, extended playback or other device was used in this follow-up.

## Environment and isolation

- Image: `system-images;android-36;google-tv-ps16k;x86_64`, revision 4; exact fingerprint in evidence.
- Emulator 37.1.11, private AVD `viptv-16k-isolated`, serial `emulator-5586`, 2 GiB RAM, 2 cores, 960×540, software GPU. Own data directory `/home/vynxc/.cache/viptv-qa/android-16k`.
- Fallback explicitly disabled inside this guest: `bionic.linker.16kb.app_compat.enabled=false`, `pm.16kb.app_compat.disabled=true`.
- The initial 1 GiB guest boot failed because ART `dex2oat64` could not allocate memory. The guest was stopped and retried with the explicitly authorized 2 GiB. This was a system-image boot failure, not an application result.
- Synthetic loopback HTTPS on 18586, trusted through the existing public QA CA in debug only. No TLS bypass, real account, provider, or tracking service.
- Guest 5586 and its emulator process are stopped; private fixture 18586 is stopped and its reverse removed. The original run’s final adb inventory retained only untouched user emulator 5554; the earlier 6b normal follow-up started with an empty adb inventory. The final 4cb follow-up left the concurrently owned phone guest untouched. No physical TV, live service, or default Wasm output was changed.

## Reproduction

Context7 Android docs were resolved first; shallow excerpts required the official Android documentation above for exact page-size/image/fallback checks. Current FFmpeg CLI docs were also fetched through Context7. Media assets contain only generated silent signals.

```sh
sdkmanager 'system-images;android-36;google-tv-ps16k;x86_64'
ANDROID_AVD_HOME=/tmp/viptv-16k-qa/avds emulator \
  -avd viptv-16k-isolated -port 5586 -memory 2048 -cores 2 \
  -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader
adb -s emulator-5586 shell getconf PAGE_SIZE
bash tools/qa_16k_media.sh OWN_EMPTY_MEDIA_DIRECTORY
# Use ONE fresh fixture process at a time; reset counters between scenarios.
python3 tools/qa_lifecycle_server.py --root OWN_CERT_DIR --media OWN_MEDIA_DIR \
  --port 18586 --rolling --direct
python3 tools/qa_lifecycle_run.py --apk IMMUTABLE_DEBUG_APK --root OWN_DIRECT_EVIDENCE \
  --ca ../.local-https/certs/ca.crt --origin https://127.0.0.1:18586 \
  --avd viptv-16k-isolated --sustain-ms 180000
# Stop owned fixture; restart with --managed, run with --sustain-ms 60000.
python3 tools/qa_16k_format_run.py --origin https://127.0.0.1:18586 --root OWN_FORMAT_EVIDENCE
```

SDK installation/AVD creation followed inventory of existing images and processes. Drivers reject every serial/AVD except the explicitly owned guest. The debug inspector invokes the real suspend command handler without adding a production test API.

## Raw receipts and harness corrections

[evidence/android-16k-runtime-2026-10-15/summary.json](evidence/android-16k-runtime-2026-10-15/summary.json) joins exact hashes, raw logs, request counts, tracking targets, clock differences, compatibility properties, inspected captures and cleanup. Scenario directories retain full status JSON and logs. No private fixture key or account data is archived.

The first extended probe failed because it wrongly required gateway seeks to retain the same input/player. Production correctly replaced them. `lifecycle-failed-gateway-assumption` retains that failure; the corrected direct and managed results are separate.

The direct run's final real app PASS was held in Python TextIO read-ahead while `select` waited on the raw descriptor after playback stopped. Only the driver's verified owned logcat child was closed to drain EOF; no app/fixture behavior or log records were changed. `direct-driver-flush.json` records that action. The driver now uses unbuffered binary reads; the managed and final format runs completed normally with that correction. This is a harness fix, not a playback defect.

## Limits

These are synthetic low-resolution clips on a software-rendered x86_64 guest. Audio decoded through virtual output with host audio disabled: audible output, surround passthrough, hardware decoder coverage, lip sync, HDR, DRM, physical-TV focus/performance and physical-TV 40-minute endurance remain unqualified. Synthetic Android clock/tracking endurance and the final original-player OSD are separately qualified above by the fresh41-minute run. A 600-second host offset exercises clock composition; it does not represent 600 seconds of elapsed playback. Physical ARM64/16 KiB hardware runtime remains unqualified. No deployment or real-provider claim follows from these tests.
