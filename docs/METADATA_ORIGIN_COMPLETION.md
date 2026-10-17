# Metadata origin and unified search — 2026-10-15

The approved behavior retains plugin capabilities and one text-search screen. Enabled, profile-assigned Simkl and Stremio integrations contribute results: Simkl Anime/Movies/TV rows and searchable Stremio add-on rows. Discovery/navigation redesign remains deferred.

## Lookup and persistence

A private provider ID identifies content only within its provider connection and installation incarnation. That origin and the expected identity assertions travel through details, paged episodes, sources, next episode, subtitles, favorites, progress, history and parent approval. UI keys preserve the same scope. Presentation text, preview IDs and artwork do not authorize aliases. Native and server storage can share history/library identity only through host-verified canonical claims; ambiguous legacy records remain legacy. Removing and reinstalling an add-on creates a new incarnation; an old private selector cannot silently bind to it.

Favorites preserve the user's Add/Remove intent. Fresh metadata resolving an Add to an already saved canonical title never turns the action into Remove. Removing a saved record uses its exact stored selector, so removal does not require a now-unavailable metadata provider. Membership projections retain lightweight identity assertions rather than hydrating every saved Card.

Public IMDb IDs are a narrow exception to producer pinning. A Search-only Stremio add-on may return `tt…`; an enabled metadata provider may resolve that same public identity. Core validates both the original issuer and actual producer, captures their revisions/incarnations, and checks them again before publishing the result. The returned Card adopts the actual metadata producer's origin. Opaque IDs cannot delegate merely because their preview contains an IMDb hint, and noncanonical padded episode selectors do not delegate.

An explicitly selected public episode outside the first metadata page is checked against the full returned provider document before the host returns the page. Common UI preserves its exact raw episode selector and uses the verified producer and authoritative parent. An episode present on-page with conflicting assertions is rejected rather than treated as absent. Source discovery never guesses the first episode or fabricates S0E0.

Subtitle content grants additionally bind the exact media identity, parent, recognized claims, edition and origin. Matching only an add-on or raw private ID is insufficient. Server metadata proof considers the actual media-type-compatible providers before applying its endpoint limit; unrelated providers cannot push the real producer outside the authority check.

## Evidence

Four new Rust delegation cases cover split search/metadata capabilities, issuer/producer disable/reinstall, late replies and stale caches, opaque/padded rejection, expected claims and episode1499 beyond page zero. Three actual-JNI Kotlin cases exercise details, source opening, subtitles and saving through the delegated provider, off-page episodes, and revocation before stream I/O. Four common controller tests cover verified origin changes, exact episode selection, conflicting claims and opaque refusal. Existing scoped storage/transport/alias-remap tests remain in the full gates.

All four native libraries were rebuilt from the 66-file overlay. The 110 original UniFFI checksums are unchanged; 11 additive functions bring the current total to 121. The [native build receipt](evidence/native-origin-build-2026-10-15.json) and [Android artifact audit](evidence/android-release-abi3-2026-10-15.json) identify exact inputs and binaries.

The final backend all-target gates passed 503 tests normally and 505 with SDK runtime, retaining8/9 existing ignored tests. Real browser [private-title parent administration](evidence/parent-title-approvals-2026-10-15/final/README.md) and [playback](BROWSER_PLAYBACK_COMPLETION.md) verify the new origin-bearing contracts across actual HTTP calls. Those fixture results do not claim deployment, arbitrary add-on correctness, or physical-device acceptance.
