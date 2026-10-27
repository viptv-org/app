# Phone player actions — 2026-10-15

The supplied `M3Player`, `M3PlayerNext` and `M3PlayerError` boards exposed a
supported-flow gap: `PlayerRoute` already passed actual episode, authoritative
next and recovery callbacks, but the non-TV player discarded them. Phone now
shows Episodes and Next episode alongside audio, speed and Lock. Desktop/browser
chrome and the TV player retain their existing branches.

Episodes contains only loaded host-returned cards from the current parent,
provider and connector incarnation. It starts at the current episode and gives
that row focus, including a current item beyond the first loaded viewport. Back
or Close dismisses the sheet and restores the Episodes button; selection passes
the exact Card to the existing playback controller. No episode ID is constructed.
The sheet is provisional because no exact phone player episode-picker board was
supplied; it does not imply that every episode has been loaded.

Next uses the controller's authoritative result, including a title beyond the
loaded episode page. The Next episode toolbar button plays that episode straight
away (app#41). The board-shaped Up next prompt opens by itself when the player
reports the end of the episode, drawn over its last frame, on phone and on the
desktop/web player. Watch credits and Back dismiss it until the episode ends
again; only its Next episode button starts media. The prompt pins the opened
candidate and dismisses if its identity/assertions or playback owner changes. A
next episode that resolves after the end (the controller asks again then) still
opens it. Pending/unavailable results disable the action, show an honest status
and never open the prompt. Movies do not show episode actions. There is no
countdown, autoplay, inferred credit boundary or invented metadata.

On a phone source failure, the retained title/subtitle, source error and existing
recovery actions use the supplied error-board arrangement. Retry and Choose a
source appear only when actual callbacks exist; Back remains available. A retry
replacing the input resets the local failed state even when it reuses a source ID.
An unavailable recovery callback never becomes a decorative action.

`PhonePlayerActionsUiTest` passes six meaningful cases: explicit next beyond the
loaded page and credits dismissal; current item80 focus/visibility/selection and
Back restoration; pending/unavailable/portrait controls; real recovery callback
dispatch and absence; metadata/playback replacement and removed episode choices;
and parent/provider/incarnation isolation with reordered equivalent assertions.
The focused gate passed in21s, tests3.961s, with zero failures. The first run caught
the lazy-popup focus timing and mutable-next prompt; both are covered by the
passing final regressions. The parent agent runs the merged platform gate.

Five common Compose captures were inspected and are retained in
[`evidence/phone-player-actions-2026-10-15`](evidence/phone-player-actions-2026-10-15).
They use synthetic metadata, a black video canvas and no network or decoder.
Their controls and failure/next geometry can be reviewed against the supplied
boards; they do not certify physical Android input/video, casting, all phone
player states or complete197-board parity. Exact source/capture hashes and the
focused receipt are in `proof.json`. No live service or physical TV was touched.
