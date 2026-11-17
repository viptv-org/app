# Subtitle timing prototype

The session-scoped timing controls adopt only [PLAY-SUBTITLE-SYNC-001 at
3eb55db3fd248621bbf56dc30e8718450280c767](https://github.com/viptv-org/design/blob/3eb55db3fd248621bbf56dc30e8718450280c767/specs/behavior/subtitle-synchronization.md).
The application's broader design pin remains unchanged.

Provider subtitles retain canonical intervals. Android/iOS overlays invert the
accepted mapping for lookup, with one effective title/player origin. Manual
changes add ±0.5 content seconds, freeze automatic updates and survive selection
changes within the playback session. On and Reset clear the current automatic
view/manual delay and start a fresh generation. Off restores original timing
plus the explicit manual delay. Neither the controls nor a correction replaces
the player, its media source or output mode. Corrections remain session-only.

The Android experimental worker defaults Off. Its initial route requires a
progressive source, zero effective origin, PCM16 48 kHz stereo and the matching
native WebRTC/VAD core. The dormant forwarding tee preserves the default sink's
capabilities; encoded/offload/passthrough data are forwarded unchanged without
analysis. HLS, rebased timelines and unqualified formats remain unavailable.
Native clients never fall back to server analysis.

Analysis uses one serial worker, eight preallocated 32 KiB slots and a maximum
120-second content window per generation. Audio comes from the selected decoded
stream with media timestamps. Retried sink buffers are copied once. Queue loss
stops analysis; discontinuities reset evidence and fence late results. Native
partial 20 ms frames are retained within the bound and never padded as silence.
Worker failure retains the current accepted/manual view and reports unavailable.
The current window qualifies only a fixed-offset prototype, not drift.
Automatic changes are bounded to ±15 seconds; manual delay is bounded to ±60
seconds. Accepted changes swap at a cue gap, or after five content seconds.

The native frontend requires `pcm16-webrtc20-v2` and `48k-input-offset-v1`.
The upstream 48 kHz implementation ignored the second half of each 20 ms input;
the core patch and asymmetric-half regressions correct that defect. The app
rejects ABI-compatible unpatched libraries. [Corrected build provenance](../tools/subtitle-sync/android-native-build.json)
records source `9742769ea30aa6c90758a16aa6dcd01b48b101d6`, upstream/patched
hashes and binary identity. Patch/source notices accompany the library.

[Corrected Android runtime](../tools/subtitle-sync/android-corrected-runtime.json)
uses the exact patched Core974 x86_64 library. API35 decoded the owned48k
fixture, accepted+2s after3000frames, kept one player/transition and passed
manual/Off/reset/stale-epoch checks with zero queue gaps. Phone and TV were
independently verified stopped. The fixture is synthetic/circular wiring
and offset-sign evidence, not held-out speech accuracy.

The consumer pin `be05e4e8f510116b356bd20d4736db6a9ae47287` includes the
qualified SDK320 lineage. VAD, native API, estimator and worker/lifecycle
bytes match the runtime-qualified component. Combined host compatibility
is checked separately; the Android binary is not relabeled as a new build.

The native estimator reports `uncalibratedHeuristic`. The picker exposes
**Automatic timing (experimental)** and **Automatic timing (prototype)** rather
than calibrated confidence. Held-out accuracy and sustainable device costs
remain qualification gates.

| Gate | Execution |
| --- | --- |
| Android main and focused tests | Compiled; 23 controller/protocol/PCM/worker tests passed |
| Wasm/iOS simulator main/test klib | Compiled; final pending-candidate cleanup is covered by Android tests; CI rechecks the final revision |
| Standalone Android acquisition | API 35 x86_64, 144 spans, 691,200 stereo PCM frames across three seek/speed epochs, zero queue gaps |
| Android native VAD library | x86_64 linked for API 26; 16 KiB ELF load alignment; minimum-OS runtime unverified |
| Corrected Android local pipeline | API35 x86_64 synthetic wiring passed; +2s,3000frames, zero gaps; lifecycle and stopped cleanup verified |
| Picker/focus regressions | Six passed in owned Xvfb; no physical display qualification |
| Main APK packaging | x86_64 debug APK and ABI checks passed; torrent runtime omitted in this validation build |
| Linux mpv + bounded decoder | Owned local AAC/H.264 file, null outputs only |
| Windows/macOS/iOS acquisition | Unavailable; no executed platform proof |

[Acquisition evidence](../tools/subtitle-sync/android-acquisition-result.json)
uses an owned ten-second WAV; the backward seek repeats source samples.
[Native build provenance](../tools/subtitle-sync/android-native-build.json)
records the exact binary source and equivalent cosmetic core pin. Full WebRTC
notices are packaged with the Android native library. The guarded
[Android runner](../tools/subtitle-sync/run_android_probe.py) uses raw shared
ADB framing, rejects a coding-namespace listener and never starts an ADB daemon.

The [Linux probe](../tools/subtitle-sync/desktop_probe.py) runs mpv 0.35.1 beside
FFmpeg 5.1.9 against one immutable owned file with file/pipe protocols only.
Two two-second windows retain source PTS at 592 and 100 seconds, producing 100
20 ms frames and 64,000 PCM bytes each. The anchored `sub-speed=1.001`,
`sub-delay=1.46` projection selects the expected cue without replacing the
player. [Recorded result](../tools/subtitle-sync/linux-local-file-result.json).
This does not qualify app HTTP/torrent/HLS byte reuse, real audio/GPU outputs,
packaged desktop dependencies, estimator accuracy or playback impact.

Android minimum/latest OS devices, ARM builds/runtime, codec/output variants,
iOS rendering/runtime, held-out speech accuracy and sustained battery/thermal
behavior require separate execution. A compile or circular synthetic fixture
cannot establish synchronization accuracy or production performance targets.
