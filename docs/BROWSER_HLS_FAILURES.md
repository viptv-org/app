# Browser HLS failures — 2026-10-15

Fatal hls.js errors now reach the existing player failure state. Previously the browser listened only to the video element and its play promise. An invalid HLS manifest left the video at readyState0 with no frames and no HTML media error, so the app kept loading indefinitely. The failure listener is attached before loading and removed with its exact JavaScript identity before disposal; disposed players cannot report into a replacement player.

Only `fatal: true` is terminal. hls.js retains its own recovery/retry policy for nonfatal errors. The common player also ignores late buffering status after a terminal failure, hides the spinner when an error is present, and resets that terminal state for a replacement input. Screenshot review caught and corrected the intermediate error-plus-spinner state.

HLS selection additionally honors the host MIME type for opaque/signed endpoints without `.m3u8`. URL fallback ignores query values and fragments. Two common tests cover these format boundaries, and Wasm compilation verifies the actual JS bindings.

## Actual runtime evidence

The same isolated HTTPS API/gateway/provider fixture used for earlier browser playback was reused with controlled media-edge faults. No API response or browser route was intercepted. Synthetic credentials and signed media URLs remain private.

- An intentionally invalid manifest reproduced the previous state for eight seconds: no frames, no HTML video error, no visible application failure. The final bundle shows the existing failure message, no spinner, zero page errors and successful cleanup. [Before](evidence/browser-hls-failures-2026-10-15/before.json), [after](evidence/browser-hls-failures-2026-10-15/after.json), [inspected final capture](evidence/browser-hls-failures-2026-10-15/after.png).
- A separate edge returned HTTP503 once for each viewer manifest, then forwarded the actual gateway response. Both initial playback and the managed seek replacement recovered. Frames, pause, selected/rebased captions, subtitle size and cleanup passed, with31 UI API calls and zero page errors. Saved progress differed from the observed original-title clock by0.176 seconds. [Retry proof](evidence/browser-hls-failures-2026-10-15/retry.json).

The exact bundle hashes are recorded per run; the retry run precedes the final common spinner-only correction. Its hls.js listener/configuration is unchanged. The final terminal-state probe ran against the subsequent full253-test bundle. The entire fixture was stopped and its normal configuration restored afterward. These tests do not qualify every browser, source format or physical device.

Library behavior follows the current [hls.js error contract](https://github.com/video-dev/hls.js/blob/master/docs/API.md), fetched through Context7: loading failures use the HLS error event, and recoverable failures must not be treated as terminal.
