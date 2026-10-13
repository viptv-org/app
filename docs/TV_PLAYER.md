# Android TV player checkpoint

The TV player has dedicated controls, buffering, seek preview, audio/subtitle,
intro-skip, next-episode and source-error surfaces based on the supplied `Tv3Player`
boards. Native playback continues to use KitePlayer; web playback uses its existing
browser adapter. Renderer and interaction files are separate, each under 100 lines.

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
