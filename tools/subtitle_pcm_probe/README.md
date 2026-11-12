# Local PCM acquisition probe

This isolated Android app plays an owned stereo PCM16 tone/silence fixture
through Media3 1.11.1 and its default sink. The normal mode records bounded
sample capture, forward/backward seeks and speed changes. It uses a separate
package and requires no account or network media.

Build from the app repository:

```sh
flock /tmp/viptv-tv-validation.lock flock /tmp/viptv-heavy-build.lock \
  ./gradlew --max-workers=1 -p tools/subtitle_pcm_probe assembleDebug
```

Use the server-workstation skill before server device work. The guarded runner
uses raw shared-socket ADB framing, checks server liveness and rejects a listener
in the coding network namespace before any device action. It never starts an
ADB daemon. It authenticates device start/stop through `server-emu`, refuses a
busy device room, installs only its owned package and stops only its owned Phone.

```sh
flock /tmp/viptv-tv-validation.lock flock /tmp/viptv-heavy-build.lock \
  python3 tools/subtitle-sync/run_android_probe.py \
  --apk tools/subtitle_pcm_probe/build/outputs/apk/debug/SubtitlePcmProbe-debug.apk \
  --output /tmp/subtitle-android-result.json
```

The worker requires patched frontend `pcm16-webrtc20-v2` / `48k-input-offset-v1`;
ABI-compatible older libraries are unavailable. The `viptv.subtitleCoreSo` Gradle
property includes a matching x86_64 native core
built with `subtitle-vad`. The runner's optional `--fixture` and `--captions`
arguments exercise the real local PCM/VAD/estimator/session path with owned WAV
and VTT inputs. Synthetic/circular fixtures establish wiring and offset sign;
they do not establish held-out speech accuracy.

The sink stores at most eight 32 KiB spans, copies each input buffer once and
preserves sink capabilities/output modes. Queue loss, configuration and stream
changes fence queued observations. The initial app worker accepts only PCM16,
48 kHz stereo, progressive playback and zero effective origin. Other paths
retain original/manual timing without server fallback. Android API 35 emulator
acquisition is recorded in [the owned result](../subtitle-sync/android-acquisition-result.json).
Minimum OS, other devices/rates/layouts, compressed output, HLS/torrent delivery,
video impact and sustained energy/resource costs remain separate gates.

The runner verifies the exact owned package and optional native SHA before any
install. Cleanup must verify stopped profiles; an error state is preserved and
fails the run. Resolve a failed startup before another attempt. Incremental APK
updates can retain unused archive space; if the 64 MiB upload bound rejects an
owned APK, remove only that generated APK and reassemble, without clearing caches.
