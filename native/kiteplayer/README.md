# Pinned KitePlayer presentation renderer

`SOURCE.json` pins upstream KitePlayer v0.3.0 and the experimental presentation
fork. The Git delta bundle preserves the upstream license and history.
`ARTIFACTS.json` records the 18 Android/JVM publication checksums.

The app uses released `kiteplayer-compose:0.3.0` with strict constraints on only
`kiteplayer-compose-ui` and `kiteplayer-compose-video` at
`0.3.0-viptv-presentation.3`. Its generated Maven repository is scoped to those
modules. Unchanged native engine, FFmpeg and output dependencies remain upstream.

`tools/build_player.py` verifies the bundle, imports the exact pinned source into
`build/kiteplayer`, publishes the two modules and verifies every artifact hash.
Native compilation depends on the preparation task. Generated source and Maven
artifacts are ignored build outputs; the app has no dependency on another
experimental checkout. Existing source caches with local edits are preserved and
cause preparation to fail.

The additive `KitePlayerComposeVideoWithPresentation` callback reports an actual
rendered image asynchronously, without changing existing overload signatures.
`WindowGpuCompleted` identifies Android owning-window GPU completion;
`AndroidSoftwareDrawCompleted` identifies actual Android bitmap drawing with
no GPU completion fence. `DesktopDrawCompleted` identifies actual Compose drawing with no OS scanout
report. The timestamp is monotonic observation time, not media PTS. Decoder
publication, player status and background-only draws do not emit presentation
proof. Observer replacement/disposal cancels delivery, and the app additionally
fences facts by the current playback input.

The source fork passed 18 JVM renderer and 55 Android host tests, additive ABI checks, Android/JVM builds,
clean bundle import and independent published-consumer builds. All 18 checksums
reproduced. Application/device runtime acceptance remains separate. Older
Android native views use the existing frame event path; iOS presentation and
Apple renderer publications are not supplied by this fork.
