# Android TV Search checkpoint — 2026-10-13

The current `Tv3Search` ZIP board supplies the field, six-column keyboard and
result shelves. Search queries enabled, profile-assigned search integrations together, with no provider
selector in this screen. Simkl categories and searchable Stremio add-ons keep their rows.

Provider edits retain the existing 300 ms debounce/cancellation behavior. The
cursor sits immediately after the visible query. Results preserve host ranking
and group actual media categories as Anime, Movies and Series. Multiple Stremio
catalog results can retain their provider labels. Simkl card tags are omitted.
Poster titles appear beneath the artwork and marquee while focused.

Focused groups scroll with their heading visible below the navigation bar.
Closing details returns to the same query, result position and focused card.
Returning from Search to Home targets Play.

Real Naruto QA put the original 2002 series first, exercised Anime → Movies →
Series → Anime with the top heading visible, and returned from details to the
same selected result. Screenshots are retained in the private local device
artifacts. These checks do not certify every query, channel source or design board.

The 2026-10-15 continuation preserves failed/partial native add-on rows rather than
flattening failures into no matches; see [Home/search recovery](HOME_SEARCH_RECOVERY.md).
