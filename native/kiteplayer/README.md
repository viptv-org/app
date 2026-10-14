# Pinned desktop KitePlayer renderer

`SOURCE.json` pins upstream KitePlayer 0.3.0 and presentation fork
`cbe2731345ea8270705f1df6e0f8f27485c42417`, version
`0.3.0-viptv-presentation.4`. The Git bundle preserves upstream licensing and
history. `ARTIFACTS.json` records immutable publication checksums. The app uses
the pinned core, output, Compose UI and Compose video modules on desktop.

Android and Android TV use Media3 with its official FFmpeg audio extension.
They no longer use the Kite Compose video path. Desktop uses Kite’s Compose
canvas so controls draw above video and retain input; desktop frame conversion
still runs on the CPU. Hardware and 4K desktop performance remain unqualified.

The presentation callback distinguishes Android window GPU completion,
Android software drawing and desktop Compose drawing. Desktop drawing does not
assert OS scanout. Monotonic observation time is distinct from media PTS;
decoder publication, player status and background-only drawing are not evidence
of a visible video frame. Observer and playback-input ownership fence late facts.

The pinned fork includes FIFO reset acknowledgement and wall-time buffering
corrections. Published-consumer builds and hosted Linux/Windows/macOS packaging
passed. Actual desktop GUI, moving-frame performance, formats and long-running
playback remain qualification work. The generated source/Maven cache is an
ignored build output; the app does not require another experimental checkout.
