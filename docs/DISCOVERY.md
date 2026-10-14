# Profile-owned discovery

The profile's metadata provider supplies discovery catalogs and filter capabilities.
Search does not ask the user to choose a provider. Simkl currently supplies scalar
genre/timeframe controls; unsupported advanced parameters are not invented.
Stremio controls retain required flags and advertised choices/selection limits.

Catalog identity is the tuple of connection, media type and raw catalog ID. Two
add-ons can each own a catalog named Top. KMP uses a local tuple key for selection,
a friendly connection name for duplicate labels, and separate raw catalog/owner
fields on the host wire. Switching catalogs clears the previous catalog's filters
and cursor. The single Browse grid explicitly chooses one satisfiable catalog and
owner, making native and remote browsing one-catalog/pageable operations. If every
catalog requires additional input, the first catalog's controls stay visible while
its requirements are completed. Legacy host type-only aggregation remains separate.

Optional choice controls return to the provider default when cleared; the UI does
not promise that omission means an unfiltered catalog. Scalar genre/extras requests remain compatible. Multiple selections use the existing
QueryValue shape in `values:[{id,values:[...]}]`: native planDiscover JSON and one
JSON-encoded remote query parameter. The engine/backend validate profile/catalog
ownership, required fields, choices, limits and conflicting/duplicate controls
before provider IO. The SDK builds one request with individually escaped repeated
keys; clients never construct provider URLs or join selections with commas.

An absent maximum_selections adds no provider-specific cap; canonical vectors
still have a global limit of 64. SingleChoice allows at most one value. Stremio's
missing optionsLimit defaults to one; zero does not expose a selectable control;
real choices with a larger limit expose MultiChoice. Freeform extras without
choices do not become invented menus.

KMP multiple-choice sheets keep drafts local, apply together, guard repeated Apply,
and preserve selections on automatic paging. TV opens on an enabled choice even
when it is outside the initial viewport. Controls are published before fetching
a catalog so missing-required-input errors leave a usable form. Required
catalog search fields are visible; incomplete forms do not request a catalog. New UI variants
and isolated captures are recorded in GAP-057.

Seven added KMP checks cover tuple selection and query resets; remote vector/scalar
encoding and pagination; real Compose limit/Apply/focus behavior; and the real
Rust/SQLite/native adapter with only add-on IO replaced. The native check installs
two synthetic same-ID catalogs, selects the second owner, reads two pages with
one request each, rejects an unadvertised choice without provider IO, then switches
to a search-only catalog without forwarding the prior genres. Add-on refresh
invalidates cached controls/cards/cursors without loading a catalog. No test
fixture is a normal app data fallback. TV-web/Roku multiple-choice presentation
remains migration work.
