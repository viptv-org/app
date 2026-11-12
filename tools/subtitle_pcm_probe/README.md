# Local PCM acquisition probe

This isolated Android app plays an owned 10-second stereo PCM16 tone/silence
fixture through Media3 1.11.1 and its default audio sink. It wraps the sink
with `Media3PcmProbe`, drains the bounded queue, seeks forward/backward and changes speed.
It uses a separate package and requires no account or network access.

From the app repository:

```sh
flock /tmp/viptv-tv-validation.lock flock /tmp/viptv-heavy-build.lock \
  ./gradlew --max-workers=1 -p tools/subtitle_pcm_probe assembleDebug
adb -s SERIAL install -r tools/subtitle_pcm_probe/build/outputs/apk/debug/subtitle_pcm_probe-debug.apk
adb -s SERIAL shell am start -n org.viptv.subtitleprobe/org.viptv.app.player.SubtitlePcmProbeActivity
adb -s SERIAL shell run-as org.viptv.subtitleprobe cat files/result.txt
```

The sink captures only PCM16 with valid sample rate/channel layout. Encoded
passthrough/offload buffers pass through without capture. Oversize buffers,
queue overflow and flush/discontinuity invalidate observations. The queue
stores at most eight 32 KiB spans. A consumer must fence every span against
`epoch`, and must treat gaps as missing evidence.

This tool qualifies acquisition only on the exact executed device and fixture.
It contains no speech detector or estimator. The application player does not
install this probe or enable automatic native analysis. HTTP, torrents, HLS,
compressed audio, hardware passthrough/offload and sustained energy/resource
budgets require separate qualification. Desktop and iOS acquisition remain
unavailable. The iOS provider overlay accepts plain UTF-8 SRT/WebVTT text;
ASS styling and positioned cues require another renderer.
