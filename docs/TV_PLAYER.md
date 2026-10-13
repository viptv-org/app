# Android TV player checkpoint

The TV player has dedicated controls, buffering, seek preview, audio/subtitle,
intro-skip, next-episode and source-error surfaces based on the supplied `Tv3Player`
boards. Native playback continues to use KitePlayer; web playback uses its existing
browser adapter. Renderer and interaction logic live in separate small files.

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
use the supplied secondary foreground. The backdrop-blur treatment remains
unimplemented; complete audio-panel parity is not claimed.

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
