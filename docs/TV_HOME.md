# Android TV Home and catalog checkpoint

The supplied `Tv3Home`, `Tv3HomeLoading`, `Tv3HomeToRows`, and `Tv3Movies`
HTML and artwork are the source for the dedicated TV components. This is an
implementation checkpoint, not certification of complete design parity.

Home has the billboard artwork and actions, Continue Watching and catalog
shelves, a focused-title summary, a 450 ms shelf movement and a 400 ms focused
title delay. Loading placeholders use the supplied 1.4 second gradient shimmer,
clipped to their individual shapes. The selected header underline is constrained
to its tab rather than expanding the navigation row across the screen.

The catalog uses seven poster columns and supplied genre chips. Entry into the
filter row targets the selected genre. Header and catalog no longer both request
initial focus. Genre filtering only operates on provided membership data; the
normal engine contract currently provides no genre membership.

The isolated 1920×1080 TV emulator exercised Home → Play → Continue Watching,
horizontal summary updates, Up back to Home, and Action genre selection. Preview
images and membership are fixtures, not evidence of provider capabilities.
Desktop tests, Wasm compilation, and Android APK assembly passed.

Hero and header now fade while lifting by the supplied 60 px and 40 px offsets;
the summary enters from 24 px below. Outgoing hero/header content is excluded
from keyboard focus during exit. Returning to Home restores the header. Summary
progress and metadata now use the settled card together rather than immediately
mixing a new focus target's progress into the previous title.

Home content now fades in over 200 ms when its initial snapshot arrives. A shelf
also fades when its state changes from pending to ready; already-loaded shelves
do not replay that arrival animation when lazy composition recreates them. The
alpha is read in the graphics layer rather than recomposing the layout per frame.

Remaining work includes frame-by-frame transition comparison,
reduced-motion visual qualification, actual carousel selection
and cycling, richer provider presentation data, and full typography comparison.
The fixture indicator count does not mean that carousel cycling is implemented.

Catalog loading now uses a seven-column poster skeleton grid and the shared TV
shimmer. Existing results do not gain an extra progress line during refresh.
Down targets a card only when cards exist. The initial target is clamped against
the displayed, deduplicated list. Entering the page targets the selected genre
before the poster grid. Loading and empty states are provisional (`GAP-036`).

The isolated TV emulator exercised Down during loading/empty states, genre
activation with no cards, and the populated board's initial poster target.
Two captures confirmed the loading grid shimmer. Desktop tests, Wasm compilation
and Android assembly passed. Connected catalog/genre feeds remain unqualified.
