# Android sidecar playback — 2026-10-15

The known Android sidecar/rolling-HLS clock-reset edge is resolved in source and isolated emulator qualification. Selecting an external subtitle previously called `setMediaItem` and `prepare`, recreating the HLS tracker after its window had moved. It could reset the original-title clock and discard audio selection.

`Media3ProviderSubtitles` now parses original-timeline subtitles with the pinned Media3 1.11.1 `SubtitleParser` and presents its `Cue` objects in a separate `SubtitleView`. It never replaces or prepares the primary source. It uses the same position as progress/tracking: renderer position plus rolling-window offset plus the gateway seek offset. Provider cues are not shifted or re-encoded. Media3 parses SRT, WebVTT and ASS styling/placement; the interval timeline supports overlap and backward seeks. Explicit profile colour/size choices apply to both embedded and provider views.

Only text selection changes when enabling a sidecar. Embedded subtitles are disabled/hidden while a provider track is active; selecting embedded text or Off clears the provider overlay. A malformed replacement retains the prior working selection. Generations, cancellation and closed-session checks prevent late fetches applying after another choice or renderer replacement. Provider track IDs remain attached to candidate IDs across discovery reorder/removal.

Bounds: 8 MiB per file, 20,000 timed groups, 50,000 parsed cues and 256 simultaneously displayed cues. Parsing runs away from the UI thread. Rendering follows the player clock, including paused seeks; no independent playback clock is used.

The shared preference DTO now preserves all eight existing Rust fields, including `subtitle_font_scale`, `subtitle_text_color` and `subtitle_background_color`. TV updates retain the base profile snapshot and arbitrary supported scales, colours and alpha. Connected TV style controls expose supported size, colour and None/Box choices; no shadow-mode setting was invented. The handheld form scrolls and saves a copy of the current snapshot.

## Evidence

Focused Gradle validation passed 15 tests: `SidecarCueTimelineTest` (four), `SubtitleTimelineTest` (two), `SubtitleStylePreferencesTest` (two), plus the parental-control checks (seven). Android debug assembly passed. Common regressions cover the 40-minute original clock with a 12-minute renderer window, independent gateway/window offsets, overlap and exact boundaries, backwards seek, replacement semantics, stable provider IDs and every supported preference field.

Actual Android qualification used a new private Android 35 x86_64 emulator at `emulator-5586`, generated 32-second 320×180 H.264/AAC media and a loopback-only HTTP fixture. The fixture has embedded WebVTT and advances its HLS window by four seconds. A 600,000 ms original seek offset exercises gateway/title coordinates without pretending forty minutes of media elapsed.

`SidecarPlaybackQaActivity` passed twice:

- Original-title positions 618,379 ms and 618,839 ms, rolling-window offset 4,000 ms.
- SRT, WebVTT and ASS each produced an active cue; visual capture confirms ASS appears.
- Format changes preserved the paused title position and selected audio. Primary media-item transitions remained one.
- Forward/backward seek, embedded restoration, Off, malformed-file retention, canceled selection and session replacement during a noncancellable fetch passed.
- Saved font scale 1.25 and an in-player change to 1.5 were observed. Capture uses configured yellow text/translucent background. Its debug system bars are visible; it is not production-layout acceptance.

Evidence: [first run](evidence/android-sidecar-2026-10-15/first-pass.log), [repeat](evidence/android-sidecar-2026-10-15/repeat-pass.log), [ASS overlay](evidence/android-sidecar-2026-10-15/ass-overlay.png).

Qualified debug APK SHA-256: `4fb00d2ef4bf0ee0de70c4a1f4e888b5e0c64168a39b944c9a3d43a06049c271`.

This fixture never instantiated the local engine or used accounts, tracking services, real providers or gateway processes. It qualifies Android renderer/subtitle integration, not refreshed native-engine APK packaging, physical TV playback, long-running decoder behavior or production deployment. The dedicated emulator and both fixture servers were stopped afterward. The user's existing emulator, physical TV, live services and served Wasm were untouched.

## Repeat in isolation

Generate test media in a fresh disposable directory:

```sh
ffmpeg -hide_banner -loglevel error \
  -f lavfi -i testsrc2=size=320x180:rate=24 \
  -f lavfi -i sine=frequency=440:sample_rate=48000 -t 32 \
  -c:v libx264 -preset ultrafast -threads 1 -pix_fmt yuv420p -g 48 -sc_threshold 0 \
  -c:a aac -b:a 64k -f hls -hls_time 2 -hls_list_size 0 \
  -hls_segment_filename /tmp/NEW-FIXTURE/segment-%03d.ts /tmp/NEW-FIXTURE/all.m3u8
python3 tools/qa_sidecar_server.py --root /tmp/NEW-FIXTURE
```

Create a dedicated disposable AVD; never reuse a user-running emulator or physical TV. Install the debug APK only there, then start `org.viptv.experiment/.SidecarPlaybackQaActivity` with `qa_media_uri` set to the printed `http://10.0.2.2:PORT/index.m3u8`. The activity restricts itself to that emulator-loopback host. Read `VIPTV_SIDECAR_QA` logs; a complete run ends in `PASS`. Use a fresh fixture server per run so its rolling transition begins with the test.

APIs were checked with Context7 resolution/docs and the actual [1.11.1 SubtitleParser](https://github.com/androidx/media/blob/1.11.1/libraries/extractor/src/main/java/androidx/media3/extractor/text/SubtitleParser.java), [parser factory](https://github.com/androidx/media/blob/1.11.1/libraries/extractor/src/main/java/androidx/media3/extractor/text/DefaultSubtitleParserFactory.java) and [HLS timeline implementation](https://github.com/androidx/media/blob/1.11.1/libraries/exoplayer_hls/src/main/java/androidx/media3/exoplayer/hls/HlsMediaSource.java).
