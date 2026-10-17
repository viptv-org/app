# Browser playback qualification — 2026-10-15

The production KMP Wasm player passed a real Chromium → API → gateway playback flow over trusted local HTTPS. The provider is an isolated synthetic Stremio add-on; its stream is the public W3C Sintel trailer. The gateway actually probes/remuxes that H.264/AAC file and serves HLS. The UI uses real pointer actions and network requests, with no route interception or TLS bypass. This is separate from the preserved manual TV QA runtime.

## Changes and observed behavior

Chrome reports native HLS support as `maybe`. The previous native-first branch therefore bypassed hls.js, even after an explicit VOD `startPosition` was configured. The native path produced a demux error on one run and jumped approximately four seconds when a paused gateway seek resumed. The player now prefers hls.js whenever its MSE support check succeeds, retaining native playback as the fallback. Known VOD explicitly starts at its requested media position; actual live playback keeps the library's default live position. This follows the [hls.js embedding guidance](https://github.com/video-dev/hls.js/blob/master/README.md) and [startPosition contract](https://github.com/video-dev/hls.js/blob/master/docs/API.md), fetched with Context7. The app pins hls.js 1.7.3.

Provider subtitle choice and size survive a managed seek that replaces the video element and viewer lease. Caption styling is scoped to the current video and removed on disposal. Old caption handlers and stale play-promise callbacks cannot update a replaced player. Explicit external subtitle selection/Off suppresses embedded tracks. Renewed candidates match the prior provider/language/label only when that match is unique.

The final run made 29 UI API calls with zero page errors. It verified:

- Actual 854×480 decoded frames, pause with a stationary media clock, provider caption selection, and Large subtitle sizing.
- A seek to original-title offset 28.778 seconds replaced the video element while remaining paused at media position zero.
- The selected caption was reloaded and rebased from its original 25–40 second interval to 0–11.222 seconds; the earlier cue was removed.
- Resuming advanced media by 1.289 seconds over 1.396 seconds of wall time, without the previous four-second jump.
- Original-title position was 30.067 seconds; saved history corresponded to 30.258 seconds, a 0.191 second difference across screenshot/close latency.
- Source, subtitle and progress requests carried the modern metadata origin and expected Card. The initial and replacement caption requests both succeeded against the final backend.
- Closing playback removed the video and scoped caption style and released the viewer lease.

## Exact evidence and limits

[Proof and artifact hashes](evidence/browser-playback-2026-10-15/proof.json), [caption before seek](evidence/browser-playback-2026-10-15/browser-caption.png), [caption after seek](evidence/browser-playback-2026-10-15/browser-after-seek.png). Captures were inspected. The API executable was the qualified test host SHA256 `9768126a3f9f40c6be39820ce443a20bfb7044aacb319ad75bc888d9b3fd2acc`, which permits only explicitly configured loopback fixture add-ons. Production SSRF behavior was unchanged. The normal gateway binary hash and exact Wasm/source hashes are in the proof. The normal release API candidate is a separate executable recorded with the final parent-title evidence.

This proves the stated isolated Chromium flow, not audible output, all codecs/providers, Safari/native-HLS fallback, physical TV behavior, or long-running playback. The synthetic account, cookies, keys and playback URLs remain outside repository evidence. Only the owned fixture was started/stopped; the manual QA services, installed TV app and served default Wasm bundle were not changed.
