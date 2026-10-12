# Android TV My List checkpoint

The `Tv3MyList` ZIP board supplies the segmented All/Movies/Series control,
Last watched sort pill, seven 232×348 poster columns, 96 px gutters, focused
captions, and a 440 px row interval. Dedicated TV components replace the generic
heading and History banner. Captions reserve space so focus does not move rows.

The isolated preview uses the supplied poster artwork and synthetic watched
ordering. Last watched is offered only when a caller supplies watched timestamps;
otherwise Date added preserves caller ordering. A–Z and Z–A sort supplied titles.
Filters use each card's media kind. Preview sorting and filtering do not save
anything or prove a backend integration.

The normal app still has no saved-list engine contract. It supplies an empty,
unavailable state rather than presenting preview titles as a user's library.
Persistent per-profile saving, list retrieval, watched timestamps, and actual
provider list integration remain outstanding. No board is certified as complete.

The sort menu and empty/unavailable states are provisional UI absent from this
board; they are tracked in `DESIGN_GAPS.md`.

At 1920×1080, the isolated TV emulator exercised header → selected filter → first
card, Movies excluding the series fixture, Series excluding movie fixtures,
opening the sort menu and dismissing it with Back, and the unsupported connection
state containing no saved fixtures. These checks qualify preview interaction;
sorting order, persistence, and complete typography/motion parity remain pending.
