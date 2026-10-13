# Android TV search checkpoint

The supplied `Tv3Search` board supplies the search field, six-column keyboard,
suggestions and result shelves. Keyboard append/space/delete now send edits to
the current producer value, rather than constructing a query from the previously
rendered text. Normal app edits pass through `ApplicationController.editSearch`;
the existing 300 ms cancellation/debounce behavior remains in `search`.

The isolated TV emulator reproduced five rapid presses producing one character
before this change. Afterward it retained all five characters, applied five rapid
deletes and retained a 50-character append sequence. Long text is visually
ellipsized to one line; the full query remains in state and its caret stays within
the 520 px field. No backend/provider requests run in these preview checks.

When a search is busy with no results, poster-shaped loading placeholders reuse
the shared 1.4 second TV shimmer. With existing results, the busy state does not
insert a full-width progress bar that moves the shelves. Home and Search share
the same shimmer implementation. Loading/empty/error and long-query states lack
separate supplied boards and are recorded in `DESIGN_GAPS.md`.

Complete typography/focus/motion parity, connected request counts and provider
capability integration remain unqualified. Preview suggestions/results are
fixtures; no board is certified as complete.

Two emulator captures confirmed shimmer movement inside the loading poster
shapes while the gutter remained unchanged. Desktop tests, Wasm compilation and
Android assembly passed with the shared shimmer and producer-edit wiring.

Channel cards now use the supplied 356×200 frame, 10 px corners, dark gradient,
Bricolage channel text and 28 px LIVE badge. A white focus border fades over
120 ms instead of relying on a subtle background change. Caption/subtitle line
heights are explicit so inherited body metrics cannot add oversized rows.
Channel numbers/program labels still require structured presentation data;
normal cards display their supplied title/subtitle only.

At 1920×1080 the isolated emulator moved from poster results into channels and
Right into the next channel. It measured the first channel at y=640 and sampled
its visible white focus border. Heading reservations and section gaps now align
poster/channel anchors; loading reserves the same poster start. Desktop tests,
Wasm compilation and Android assembly passed. Complete data/typography parity
and other navigation paths remain unqualified.

## Source comparison update

The latest `Tv3Search` comparison corrects query text to 32 px/weight 500,
heading letter spacing, channel-program captions to 20 px, and suggestions to
52 px rows with 4 px gaps and 14 px icon spacing. Suggestion focus now uses a
visible TV action treatment; the previous generic clickable rows gave the
remote user no persistent focus indication. Fresh rendering/navigation checks
are pending. Channel numbers/current programs still require presentation data
from connected feeds, and this update does not establish search-board parity.
