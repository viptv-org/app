# Android API 26 runtime qualification — 2026-10-15

The latest normal release `4cb59ab8ea453d8e20dfc5b731fe8b430bc1818c7a95e55a9c8d59bbed4f3193` passed fresh first local entry and cold profile restoration on **Android 8.0.0, SDK 26**. Earlier normal `6b7079b06808580c94cae8b9d0cdd8093bc79a7b27e96129d6c1cb817c0baba7` separately passed native profile preferences persistence. Debug snapshot `c20377c99a38183b39e8612fedd48b5994c1443dc5b6c36e76322e0fdcaf7089` separately passed actual MainActivity authentication, token refresh, native profile/PIN persistence, Activity recreation, playback lifecycle, H.264/AAC seeks and E-AC-3 decoding. Those unchanged player checks were not repeated for the final normal candidate.

This uses official `system-images;android-26;google_apis;x86_64`, revision 16, with a TV hardware profile at 960×540. The OS actually reports normal UI mode (`0x11`) and `ro.build.characteristics=emulator`. Passing emulator `-prop ro.build.characteristics=tv` did not change that fact. This is API26 compatibility coverage; **official Android TV API26 firmware remains unqualified**. Its available official image is x86, which these APKs do not package. No further mode override was attempted, and the existing TV lifecycle probe's television-mode guard was preserved.

## Final normal startup follow-up

The immutable 4cb candidate was installed on the existing explicitly owned API26 guest, with exact SDK26 and normal UI mode verified. Owned app data was deliberately reset using `pm clear`; its Success response and the first-entry welcome screen were recorded before selecting Use without account. This establishes first local entry rather than reusing an already remembered mode. Profiles appeared before explicit Default selection reached actual native local Home.

After force-stop and cold launch, Who’s watching? appeared before Home, with no welcome/sign-in. Explicitly selecting the unprotected Default then reached Home. Both native Home and the cold profile gate were visually inspected, and the crash log had no application error. Framework receipts were 540ms first launch and 423ms cold reopen; these are not interactive/Home performance measurements. [Separate final-candidate receipts](evidence/android-api26-runtime-2026-10-15/normal-4cb/result.json) preserve the fresh-data proof, exact hash, launch output, accessibility trees, captures and owned cleanup. No fixture or playback/lifecycle endurance was rerun.

## Earlier normal preference qualification

`artifacts/android-release-2026-10-15/local-startup-6b7079b06808/viptv-android-release-local.apk` installed successfully. A fresh run followed Use without account → Default → actual native local Home. Playback preferences saved Best match, 1080p and Large. After force-stop and cold launch, the first stable application screen was Who’s watching? with Default, Add profile and Manage profiles. Neither sign-in nor Home appeared before profile selection. Selecting Default reached Home and reopened the saved selections.

The native SQLite engine persisted preference revision 1 with source selection `best`, height 1080 and subtitle scale 1.25. Both the restored profile gate and reopened selected values were visually inspected. The framework launch receipts were 445ms first launch and 420ms process reopen; these do not measure interactive/Home budgets or physical-TV performance.

## Debug c20377 runtime qualification

The separate `Api26LifecycleQaActivity`/`Api26LifecycleQaProbe` requires exact SDK26 and normal UI mode. It uses the actual MainActivity, ApplicationModel, ApplicationController, RemoteConnection, native engine and AndroidPlaybackSession. Profile selection is exercised through the real controller; this normal-mode probe does not claim TV initial-focus coverage.

The native engine created a profile and PIN, rejected an incorrect PIN, closed/reopened its real SQLite connection, and verified the persisted profile/PIN. The app reported a 4096-byte page size. Synthetic HTTPS login, one intentional token refresh and secure-vault replacement passed. Details and player Activity recreations retained the same model/controller and player. User-paused and playing Home return both preserved intent, as did pause/resume through a dialog without stop/start. Recreation, lifecycle and seeks caused no extra authority requests: exactly one login, one refresh, four bootstraps and one profile selection were observed.

| Direct H.264/AAC observation | Result |
| --- | ---: |
| Actual sustained elapsed time | 61,450ms |
| Clock samples | 12 |
| Explicit paused seeks | 2 |
| Seek targets and renderer positions | 612,000ms and 624,000ms, exact |
| Player replacements | 0 |
| Leases opened/closed | 1/1 |
| Title/window origin advance | 34,000ms |
| Maximum sampled clock difference | 203ms |
| Final actual renderer / emitted progress | 654,171 / 653,968ms |

The comparison used actual ExoPlayer position plus real Media3 window origin plus host timeline offset. Every seek emitted an outgoing tracking `start` at its exact requested title time. Initial visible elapsed 10:05 matched accessibility semantics and an inspected real phone-player screenshot. Nonzero direct startup landed at 8,000ms; a fresh true-live window retained its 14,000ms edge start. A 600-second synthetic title offset exercises clock composition and does not represent 600 seconds of elapsed playback.

H.264 used the image's `OMX.google.h264.decoder`, with packaged FFmpeg AAC. A separate H.264/E-AC-3 Matroska run on the same c20377 debug APK presented video and reached 20,067ms over 20,794ms wall time with the packaged `ffmpegLavc60.3.100-eac3` decoder and no player failure. The current run's crash buffer contains no application runtime crash.

## Earlier snapshots and harness corrections

The earlier normal `b4394d8aab1c7a6e543431841ab9c75750809f94d66c206bd78195a63405438f` separately passed native Home/preferences persistence but required choosing Use without account again after a process restart. Its 447/418ms framework receipts remain separate. Debug `7699e92dc23a037933402abebfcf30bcba40b378ea4d37c212efddd3424cf414` separately passed a 23,540ms H.264/AAC smoke, a paused −1,000ms D-pad seek, actual Recents paused/playing return and 20,078ms E-AC-3 decoding. These are retained as earlier immutable snapshot results.

The first old-snapshot return attempt incorrectly started a new QA Activity without its required media URI and hit its explicit precondition. The second moved a task within its stack while the launcher stayed focused. Neither is counted as a lifecycle pass. The corrected old smoke selected the actual owned app Recents card and checked current window focus. Its successful run retained one renderer opening.

An initial current E-AC-3 harness attempt consumed an old live-playback progress line above 20 seconds and exited before observing the new decoder. Its failed receipt is separate. The corrected format driver filters log lines to the newly launched app PID and requires the E-AC-3 decoder specifically. The actual application was not changed for this correction.

API26's shell lacks `getconf`, and its log buffers refuse clearing even through the image's root shell. API26 readers therefore start at `-T 1` in the main buffer; native app `Os.sysconf` supplies the page-size observation. Existing TV/16 KiB log handling and page-size assertions remain on their original paths. Every mutating command checks exact owned serial/name and SDK26; no global adb server action is used.

## Evidence and isolation

[evidence/android-api26-runtime-2026-10-15/summary.json](evidence/android-api26-runtime-2026-10-15/summary.json) joins exact hashes, image fingerprint, normal profile/preferences receipts, full current lifecycle status/logs, current format logs, inspected captures, earlier immutable runs, distinct harness failures and cleanup. Private keys and database copies are excluded.

Context7 resolved Android documentation before fetching current official AVD/system-image guidance. The official [AVD manager documentation](https://developer.android.com/studio/command-line/avdmanager) describes exact image package selection and private AVD creation; [system-image guidance](https://developer.android.com/studio/run/managing-avds) explains API-level compatibility.

The private guest was `viptv-api26-isolated` / `emulator-5592`, 2GiB RAM, 2 cores, software GPU, with private data under `/home/vynxc/.cache/viptv-qa/android-api26`. The synthetic fixture used loopback HTTPS 18592 and the existing debug-only trusted QA CA. Owned guest and fixture are stopped, their processes are gone and the adb reverse was removed. User emulator 5554, phone agent 5590 and physical devices were not modified.

This is synthetic low-resolution x86_64 OS compatibility coverage with silent virtual audio. Physical ARM/TV runtime, official TV firmware, audible output, passthrough, HDR/DRM, hardware decode performance, physical-TV focus/performance and 40-minute endurance remain unqualified. No deployment or real-provider claim follows.
