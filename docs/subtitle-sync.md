# Subtitle timing prototype

The session-scoped timing controls adopt only [PLAY-SUBTITLE-SYNC-001 at
3eb55db3fd248621bbf56dc30e8718450280c767](https://github.com/viptv-org/design/blob/3eb55db3fd248621bbf56dc30e8718450280c767/specs/behavior/subtitle-synchronization.md).
The application's broader design pin remains unchanged.

Provider subtitle timing uses canonical intervals and one effective title/player
origin. Android and iOS provider overlays support additive manual timing. The
experimental picker defaults automatic timing Off; On and Reset clear the
accepted view/manual delay and begin a new generation. Manual changes freeze
automatic updates. Automatic local analysis is unavailable until a qualified
PCM/VAD worker is installed; this source does not install that worker.

| Gate | Execution |
| --- | --- |
| Shared initial timeline/parser tests | Six passed before the experimental picker/lifecycle revision |
| Android main and iOS simulator main/test klib | Initial prerequisite source compiled; revised picker/lifecycle needs another compile |
| Android PCM host tests | API 36 could not start under Java 17; API 35 adjustment awaits execution |
| Isolated Android PCM APK | Compiled; Phone failed to register with ADB within the bounded 90 s boot attempt; no acquisition run |
| Linux mpv + bounded local decoder | Owned local AAC/H.264 fixture, null outputs only |
| Windows/macOS/iOS acquisition | Unavailable; no executed platform proof |

The [Linux probe](../tools/subtitle-sync/desktop_probe.py) runs mpv 0.35.1 beside
FFmpeg 5.1.9 against one immutable owned file with file/pipe protocols only.
Two two-second windows retain source PTS at 592 and 100 seconds, producing 100
20 ms frames and 64,000 PCM bytes each. The anchored `sub-speed=1.001`,
`sub-delay=1.46` projection selects the expected cue without replacing the
player. [Recorded result](../tools/subtitle-sync/linux-local-file-result.json).
This does not qualify app HTTP/torrent/HLS byte reuse, real audio/GPU outputs,
packaged decoder dependencies, estimator accuracy or playback impact.

The [Android tool](../tools/subtitle_pcm_probe/README.md) uses a separate package
and an owned local PCM16 fixture. Passthrough/offload and unsupported formats
are forwarded unchanged. Native paths never fall back to server analysis.
Held-out speech accuracy, minimum-OS devices, codec/output variants, sustained
battery/thermal behavior and complete native integration remain qualification
gates. Core adoption requires matching generated bindings and native binaries.
