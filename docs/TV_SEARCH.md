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
