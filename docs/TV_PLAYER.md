# Android TV player checkpoint

Android provider subtitles now preserve the primary HLS source, title clock and audio selection. The former rolling-window reset edge is resolved in source and isolated Android qualification; see [Android sidecar playback](ANDROID_SIDECAR_PLAYBACK.md) for evidence and physical-device qualification limits.

The real Android app/controller recreation, Home/return, original clock and initial zero/nonzero/live HLS behavior now have [isolated runtime evidence](ANDROID_LIFECYCLE_PLAYBACK.md). The reproduced foreground pause race and on-demand startup jump are fixed; process death and physical-device endurance remain separate qualification.

The TV player has dedicated controls, buffering, seek preview, audio/subtitle,
intro-skip, next-episode and source-error surfaces based on the supplied `Tv3Player`
boards. Android uses Media3, desktop uses libmpv, and web uses its browser
adapter. Renderer and interaction logic live in separate small files.

Remote Left/Right previews a seek. OK commits it; Back cancels it. Up moves from
the timeline to the control row. Back dismisses a sheet or hides controls before
leaving playback. Native position and selected-track state come from player facts.
The Material slider's key-release behavior is intercepted so releasing an arrow
does not commit the pending seek. Timeline timestamps use tabular numeric glyphs.

The isolated Android TV fixture passed preview/cancel/confirm, audio sheet opening
and dismissal, subtitle selection acknowledgement, skipping out of an intro range,
keeping credits without starting another item, and opening the source picker from
an error. Those acknowledgement checks use synthetic facts confined to
`preview/PreviewTvPlayer.kt`. The separate Native scene decoded the public Flower
MP4, reported a seek beyond 900 ms, and reported Playing and Paused states.

Native sessions now restart when a new PlaybackInput instance reuses a source ID.
Opening applies the requested pause state before reporting Playing, and initial
zero-position observations are ignored until opening finishes. Source switching
waits for the queued history write; retry persists the last observed position.
The complete engine-backed failure/retry/resume flow remains unqualified.

Normal connections populate episode navigation from loaded detail episodes and
source/retry labels from actual candidates. Next-episode actions open the next
item's source picker; no item starts automatically. Skip ranges, scrub thumbnails,
quality and other optional presentation facts need engine/server contracts. The
fixture supplies those sample values; normal connections do not infer them from
names or invent tracks. Persistent subtitle preferences, HDR/HLS/live playback,
actual multi-track switching, source recovery and every board's final typography/
motion review remain incomplete. No complete TV parity claim is made.
# Track-panel focus fallback

The TV options panel validates its requested subtitle-focus ID against the
available tracks. A stale ID falls back to the selected subtitle or Off. With no
subtitles, audio receives focus when present; with no tracks, Off remains a valid
focus target. These choices affect panel focus, not playback selection commands.

The isolated 1920×1080 emulator checked stale-ID, audio-only, empty-track and
original options scenes; each focused a valid control and Back dismissed the
panel. Desktop tests, Wasm compilation and Android assembly passed. These
fixtures do not qualify every live track-change/persistence scenario.

## Buffering delay

The buffering overlay waits 500 ms, as specified by `Tv3Motion`. Leaving the
buffering branch disposes its delay; a new buffering interval starts a fresh
wait. Playback facts still determine readiness and failure.

The isolated TV emulator displayed the sustained Buffer overlay and showed no
overlay after the BufferFast fixture reported ready at 250 ms. Seek confirmation
still passed. These settled-state checks do not measure exact frame timing or
certify the spinner's complete motion parity. Desktop tests, Wasm compilation
and Android assembly passed.

## Loading and track-sheet refinement

The buffering scene now uses Material's determinate circular indicator as a fixed
quarter-circle, with a white 18% track, square stroke ends, zero gap and a linear
900 ms rotation. Its accessibility state remains indeterminate. After the 500 ms
wait, the overlay fades in over 300 ms. Backdrop opacity, text weight, line heights
and spacing follow `Tv3PlayerBuffer`.

The audio/subtitle sheet follows the specific `Tv3PlayerAudio` bottom-entry
animation, rather than the generic right-panel example in `Tv3Motion`: 300 ms
entry and 200 ms dismissal. Selected labels use weight 600; unselected labels
use the supplied secondary foreground. The media box supplies a Haze backdrop
source for the track sheet; complete audio-panel parity is not claimed.

The isolated TV emulator passed sustained/short buffering and all four track-focus
scenes after this refinement. A native recording contains intermediate entry and
exit positions and the settled sheet at y=540; it does not establish exact frame
durations. Captures are `evidence/tv-player-buffer.png` and
`evidence/tv-player-tracks.png`. Desktop tests, Wasm compilation and Android
assembly passed.

## Controls visibility and focus

Controls fade in over 200 ms and out over 300 ms, following `Tv3Motion`.
Compose AnimatedVisibility retains the overlay through its exit transition.
The existing five-second inactivity timer now resets on every key-down that
reaches the player surface, including focus navigation between control buttons.
Native Back remains handled by the activity callback.

Up from hidden controls records an audio-focus request before revealing the
controls; the shared focus effect applies it after composition. Down reveals
hidden controls, and ordinary unhandled keys reveal them without consuming their
platform behavior. Back still cancels a pending seek, then hides controls, then
leaves playback.

The isolated TV emulator passed hidden-controls Up/audio-sheet/Back, Down reveal,
seek cancellation and successive Back navigation. A native recording contained
visible, hidden and intermediate timeline-opacity samples. These checks do not
measure exact animation durations or qualify inactivity timing during real live
playback. Desktop tests, Wasm compilation and Android assembly passed.

## Next-episode motion and geometry

Since app#41 the panel opens by itself when the player reports the end of a
series episode with a known next episode, and the Next episode pill plays that
episode directly. It opens the best match whatever the profile's Default source
says; the Source pill lists the rest. The ended video keeps its last frame full screen behind the
panel under a dark scrim; it no longer shrinks into the ZIP's 880×495 frame, and
no app background shows. Keep watching and Back dismiss it until the episode
ends again. With Autoplay next episode on (app#65, Settings › Playback, stored
on the device) the primary action reads "Next episode in 9" inside a ring that
empties over 10 s, the footnote says it starts automatically, and at 0 the next
episode plays as if pressed. Any remote key, Back or a seek away from the end
cancels it for that end, and the footnote returns to "Nothing plays on its own."
Reduced motion draws no ring and skips the fade-up; the number still counts. The information and credits caption fade up 16 pixels over
450 ms after a 350 ms delay. Optional credits-label facts are supplied explicitly;
normal connections fall back to the actual title, and the fixture supplies the
ZIP's short label.

Episode and runtime metadata are separate labels with the source's dot and badge
spacing. The heading reserves the CSS line height per measured line while letting
font glyphs extend beyond that box; long headings can still wrap. Description,
button label and helper-copy spacing were refined. The Android font-padding
helper tried during diagnosis was removed because it did not change the measured
heading height. No global typography change was made.

The isolated TV fixture checked initial Play-next focus, Keep watching, explicit
OK before opening sources, Back dismissal and exact settled video bounds.
A recording contains intermediate and settled video positions. The existing
seven-scene player checks passed, and the separate public MP4 scene decoded,
sought beyond 900 ms and reported Playing/Paused states. Live decoded-video
resizing, exact frame timing, remaining typography differences and the complete
real next-episode flow remain unqualified. Capture: `evidence/tv-player-next.png`.

## Playback-failure surface

`TvPlayerFailure` is a separate small renderer. Its backdrop uses the supplied
85% black dim, its icon circle uses 12% white, and its vertical spacing follows
`Tv3PlayerError`. The fixed heading reserves the CSS line height without clipping
font glyphs. Wrapped failure copy is centered, and the group declares an assertive
live region. TalkBack announcement behavior has not been qualified.

The primary recovery button uses Compose dropShadow with the supplied 50-pixel
radius, 18-pixel vertical offset and 45% black. Secondary fills use 16% white.
The final emulator capture shows the shadow darkening the backdrop below the
button. Error, source-only and Back-only fixtures each focused an available
control and navigated through that control. These synthetic actions do not test
real provider retry or resume recovery. Reduced-action states are recorded in
GAP-039. Remaining font/raster differences and complete board
parity remain unfinished. Capture: `evidence/tv-player-error.png`.

Desktop tests, Wasm compilation and Android assembly passed for this checkpoint.

## Intro-skip prompt

The prompt now follows `Tv3PlayerSkip`: 280×72 at (1544,896), a 26-pixel library
icon, explicit text line height, clipped 4-pixel countdown rail and a 300 ms
fade-up entrance. The rail derives its remaining fraction from playback position
and the supplied intro range, interpolating fact updates over 200 ms. There is no
independent countdown clock. Missing or expired ranges do not produce a prompt.

The isolated TV emulator checked full and half remaining rail pixels, expiry,
explicit Skip seeking outside the intro range, and Up revealing/focusing audio
controls. The fixture uses a ten-second range to match the motion example.
Desktop tests, Wasm compilation and Android assembly passed. Real-provider intro
range delivery, exact entrance timing and complete board parity remain unqualified.
Captures: `evidence/tv-player-skip.png`, `evidence/tv-player-skip-half.png`.

The bright vertical mark at the lower-left of this preview is visible in the
provided reference and its background JPEG. The bundled `tv3title-2.jpg` is
byte-identical to `42b16a2d66c54eeda67d0a2254679a5c.jpg` from the ZIP
(SHA-256 `7d043ba9db58f227d8ad9c0d745faa4bc84bf19ded3fe62c10b9a17a175d2a36`).
This identifies that particular preview mark; other layout artifacts require
separate diagnosis. The supplied artwork was not modified.

## Seek-preview refinement

`TvSeekPreview` separates the thumbnail renderer from the timeline. Its content is
400×225 with a 3-pixel exterior white border, 12-pixel inner corners and the
supplied 60-pixel shadow/24-pixel offset. The timestamp has a centered 120-pixel
box. The timeline draws the pending span between actual and proposed positions,
uses the source's 22% white track and 25% thumb halo, and uses the supplied minus
glyph in remaining time. Buffered ranges still require player facts; no sample
52% buffer fill is invented for normal playback.

The shared controls gradient now follows the source's 280-pixel top fade and
440-pixel bottom fade beginning at y=640, with 72%/82% black endpoints.

The isolated TV emulator checked the thumbnail border, actual/proposed times,
pending-band pixels and their removal on Back, plus time-only preview and OK
confirmation. The no-thumbnail surface is recorded in GAP-040. Desktop tests,
Wasm compilation and Android assembly passed. Real thumbnail delivery, buffered
range contracts, all seek boundaries and complete raster/typography parity remain
unqualified. Capture: `evidence/tv-player-scrub.png`.

## Control-button detail pass

The TV control pills now use the supplied 24-pixel library icons, 10-pixel
icon-to-label gap and 16% white fill. The audio label matches the ZIP's
`Audio & subtitles` capitalization. The existing 56-pixel height, 28-pixel
corners, 22-pixel horizontal padding and 12-pixel spacing between pills remain.

The isolated TV emulator checked initial audio focus from the timeline, options
opening/Back and source-picker navigation. Desktop tests, Wasm compilation and
Android assembly passed. Final icon-stroke/font raster parity remains unfinished.
Capture: `evidence/tv-player-control-pills.png`.

## Actual paused-video backdrop blur

The media box keeps its Haze source registered for its lifetime. Haze records
when the track sheet requires it and releases the captured layer when the sheet
closes. Attaching the source only when the sheet opened failed for a paused video
because the existing frame did not redraw into the new layer.

On the isolated API 36 TV emulator, the same native Flower frame paused at
1034 ms was compared with radius 30 and radius zero. Pixels above the sheet were
identical; the sheet region changed and its background gradients softened.
Android RenderEffect received a 15 dp radius, equivalent to 30 physical pixels
in this configuration. Playing-video sheet open/close also passed. Diagnostics
were removed after the checks.

The report is `/tmp/viptv-tv-validation-report.md`; clean captures and comparison
data are `/tmp/viptv-tv-native-qualified30-panel.png` and
`/tmp/viptv-tv-native-stable-blur-comparison.json`. This qualifies the tested
emulator path, not physical GPU performance or every renderer/device. Older
Android versions retain their documented fallback treatment.
