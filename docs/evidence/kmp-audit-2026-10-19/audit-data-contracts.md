# VIPTV data/API contract audit — DATA-01..05 (read-only)

Repo: `/mnt/ALPH/code/viptv-org` (app = `/mnt/ALPH/code/viptv-org/app`, branch `experiment/kmp-v2`).
Method: source + docs + design boards only. No builds, no gradle/adb/emulator, no file edits.
The working tree is edited concurrently, so every `file:line` below is "as read at audit time".

## 0. Where the contracts live (orientation)

| Surface | Location |
|---|---|
| Kotlin wire mirror (app) | `app/composeApp/src/commonMain/kotlin/org/viptv/engine/wire/EngineWire.kt` (header: "Generated from the Rust engine contracts") — hand-mirrored with the Rust structs, so any new field must land in both |
| Rust engine contracts | `app/native/core/crates/viptv-engine/src/presentation.rs` (`Card`:21, `HomeRow`:54, `DetailSnapshot`:69), `home.rs`, `home_browse.rs`, `stremio/*` |
| Local engine (FFI, TV/desktop/phone) | `app/native/core/crates/viptv-core/src/local_engine/{sources,addons/home,browse,detail}.rs` |
| Remote HTTP API the app speaks | **not** the main backend checkout (`backend/` is on branch `experiment/simkl`; its `server/src/routes.rs:16-215` has no `/api/bootstrap`); the app's surface is engine_v3 in the worktree `/mnt/ALPH/code/viptv-org/.worktrees/kmp-v2/backend/server/src/routes.rs:63` (`/bootstrap`), `:100` (`/profiles/:id/home`), `:121-125` (`/discover`, `/search`, `/discovery-controls`, `/meta/:kind/:id`), `:168/197-201` (`/streams`, `/streams/:id`) |
| App connection layer | `app/composeApp/src/commonMain/kotlin/org/viptv/app/connection/Remote*.kt` + `LocalConnection.kt`; native impls under `composeApp/src/nativePlayerMain/.../connection/` |
| Design contract | `design/viptv-design-system/reference/canvas-data-dictionary.md` (§A1 cards :5-36, §A2 rows :38-54, §A4 detail :85-93, §A5 sources :95-108) and boards `design/.../screens/html/{tv,phone,desktop}/…` |
| Issue source | `app/docs/ISSUES.md:123-128` (DATA-01..05), context `app/docs/HANDOFF-PROMPT.md:76-90` |

---

## DATA-01 — Home-row paging (infinite/lazy carousels)

**What exists**

- Wire field: `HomeRow.next_cursor` — Rust `viptv-engine/src/presentation.rs:60`, Kotlin `EngineWire.kt:187` (class at :180). Also `CatalogPage.next_cursor` (`presentation.rs:96`… Kotlin :73), `DetailSnapshot.next_cursor` (Kotlin :126), `ContinuationPage`/`LibrarySnapshot` cursors (Kotlin :96, :268).
- The **catalog** cursor machinery is fully implemented on both hosts:
  - Local: `viptv-core/src/local_engine/browse.rs:157` (`result.next_cursor = page.next_cursor`) fed by `viptv-engine/src/stremio/browse.rs:67-105` (`catalog_page` computes `next = skip + len`, cursor as a decimal string, `pageable` = catalog advertises `skip`; tests assert cursors "104"/"97" in `browse.rs:357`, `browse_selection_tests.rs:71`).
  - Remote: kmp-v2 `engine_v3/catalog.rs:150` (`next_cursor: value["next_skip"]…`, `complete: has_more == false`).
  - App browse path consumes it: `ControllerBrowse.kt:39-42` (`browseMore`), `:69-72` (`browseNext = page.next_cursor`, append + `distinctBy(::cardLookupKey)`).
- Row identity for paging is already on the wire: `HomeRow.browse: HomeBrowseAction` (`presentation.rs:56`, Kotlin :183) with `query = [type, catalog, addon_id|connection_id]` (`viptv-engine/src/home_browse.rs:13-36`). Both hosts attach it per row: kmp-v2 `engine_v3/home.rs:47-119` (`browse_actions` keyed by row id), local `local_engine/addons/home.rs:55-60, 134, 245`.
- Design contract is explicit: `canvas-data-dictionary.md` §A2 (:40, :47-48) — `next_cursor? (appends on scroll, never a Load-more button)`; "With `next_cursor`, skeleton cards append as focus or scroll nears the end".
- A dedicated remote refresh route exists and is unused by the app: `GET /api/profiles/:id/home` (kmp-v2 `routes.rs:100`, handler `engine_v3/mod.rs:152-161`, no query params) — the app re-fetches the whole `bootstrap()` instead (`RemoteConnection.kt:137`, called from `ControllerCatalog.kt:8-20` via `refresh()`).

**What is missing**

- **No producer ever sets `HomeRow.next_cursor`.** Every construction is `next_cursor: None`: shared assembler `viptv-engine/src/home.rs:33` (and `:49` truncates rows to `take(24)`); local `local_engine/addons/home.rs:150` and `:250`; remote kmp-v2 `engine_v3/home.rs` fetches page 0 only (`task_app.addons.discover(kind, Some(catalog), Some(addon), 0, None, None)` at `:123`) and never writes a cursor.
- **No client call path.** Nothing in the app reads `HomeRow.next_cursor` (grep: the only app reference that writes it is `ControllerLibrary.kt:51`, which sets it to `null` when re-deriving the My List row). `LocalConnection` has `browse()` (`LocalConnection.kt:46`, native impl `NativeLocalConnection.kt:82`) but no row-scoped paging; `RemoteConnection` likewise only has `browse()` (`RemoteDiscovery.kt:27`).
- **No shelf-end loading UI.** `TvHomeShelf.kt` (whole file — LazyRow keyed on `row.cards`, `distinctBy(::cardLookupKey)`, a `retry` button for FAILED, no cursor branch) and `HomeCatalogRow.kt:52-64` (carousel arrows only scroll; no append). `retry` on both home surfaces is the whole-home `controller::refresh` (`App.kt:93`, `ControllerCatalog.kt:8-20`), not a row action.

**Smallest correct connection**

1. *Producers* (one line each): populate `HomeRow.next_cursor` with the catalog skip cursor the row loader already computes — local `project_home` can call `stremio::catalog_page`'s cursor (or carry `request.url` + skip in `PendingHome`, which already stores the catalog URL at `addons/home.rs:66-72`); remote `engine_v3/home.rs` reads `next_skip` from the same `discover` result it already fetches (:123). Personal rows (`continue`/`favorites`) stay cursor-less or reuse `ContinuationPage`/`LibrarySnapshot` cursors, which exist.
2. *Transport*: no new wire type is required — the app already owns the query: map `row.browse` → `BrowseQuery(type, catalog, connection_id, values, skip = cursor)` and call the **existing** `browse()` on either host (`RemoteDiscovery.kt:27-36`, `LocalConnection.kt:46`). If a server-owned row endpoint is preferred instead, extend `GET /profiles/:id/home` (kmp-v2 `routes.rs:100`) with `?row=<id>&cursor=<n>`; the row's `browse_actions` map in `engine_v3/home.rs:47-119` already keys by that id.
3. *Client*: one controller function `homeRowPage(row, cursor)` mirroring the proven episode pager `ControllerDetailPages.kt:12-48` (`detailPage(offset)` — appends, keeps focus/navigation, per-page error, no global busy), then a shelf-end trigger in `TvHomeShelf.kt`/`HomeCatalogRow.kt` (last-visible-index via `LazyListState`, append skeleton cards per §A2, per-row busy/error like `detailPageError`).

---

## DATA-02 — Title-page cast line ("Starring …")

**What exists**

- UI slot, TV only: `TvDetailHero.kt:57-59` renders `extras.cast` as "Starring $it" at the board's exact geometry (96/586/800, 22 sp, secondary color). `TvDetailExtras.cast` (`TvDetailsScreen.kt:27`) defaults to `null` and is populated **only** by preview fixtures (`preview/PreviewTvDetails.kt:31`).
- Boards ask for it: `Tv3Title.html` ("Starring Evan Peters, Richard Jenkins, Niecy Nash" at top=586), `D3Title.html` and `M3Title.html` also carry a "Starring …" line.
- The whole resume/label/recommendations part of `TvDetailExtras` *is* fed from real data by `detailExtras(detail)` (`DetailsScreen.kt:118-128`) — only `cast` is never set there.

**What is missing**

- The wire has no field for it: `Card`/`CardMetadata` (`EngineWire.kt:40-58`; Rust `presentation.rs:21-35, 6-19`) and `DetailSnapshot` (`EngineWire.kt:122-130`; Rust `presentation.rs:69-77`) end at artwork/episodes/seasons/recommendations/next_cursor. No `cast`/`people`/`actors` string exists anywhere in `viptv-engine` (grep).
- The projections drop it upstream: stremio detail (`viptv-engine/src/stremio/media.rs:18-73`, `detail_page`) reads only `meta` + `videos` + external ids (`authoritative_external_ids` at :224 reads imdb/tmdb/tvdb/mal/anilist/kitsu only); the kmp-v2 Simkl detail route (`/profiles/:id/simkl/meta/:category/:title`, `routes.rs:140-143`) projects no cast either (no `people`/`actors` in `backend/server/src/simkl`).
- The design data dictionary does **not** declare cast: §A4 (`canvas-data-dictionary.md:85-93`) defines the title detail as `{item, episodes, seasons, recommendations, next_cursor}` — the boards are drawing a line the declared contract cannot supply.
- Phone/desktop heroes don't render cast at all (`PhoneDetailHero.kt:23-46`, `DesktopDetailHero.kt:60-100`), so TV is the only consumer.

**Smallest correct connection**

Two contract-consistent options (this is the decision the issue asks for):

- *Add to the engine detail projection*: one new bounded field `DetailSnapshot.cast: Vec<String>` (Rust `presentation.rs` + Kotlin `EngineWire.kt`, both hosts' detail projections read a top-N people list from the provider meta document). App change is then trivial: `detailExtras()` sets `cast = detail.cast.take(3).joinToString()` (`DetailsScreen.kt:127`). Note this grows the shared contract for a TV-board-only line — phone/desktop boards have it too (M3/D3), so it is not TV-only data.
- *Drop from the boards*: remove the "Starring" text from `Tv3Title`/`M3Title`/`D3Title` implementations and delete the `extras.cast` path (`TvDetailHero.kt:57-59`, `TvDetailsScreen.kt:27`, preview fixture). This is the dictionary-faithful option and the smallest change overall.

---

## DATA-03 — Trailer + Share circles on the hero

**What exists**

- TV Trailer circle renders and is focusable: `TvDetailHero.kt:78` (72 dp round `TvAction`, Lucide.Film), wired in `TvDetailsScreen.kt:103` to `notice = "No trailer is available for this title."` — a stub dialog, no data, no action.
- Boards specify the affordances: `Tv3Title.html` (Trailer circle at left=628, top=648; plus My List and Play-from-beginning circles), `Tv3Movie.html` (Trailer at 468/668), desktop `D3Title.html` (`aria-label="Add to My List"`, `"Trailer"`, `"Share"` buttons), phone `M3Title.html` (`"Back"`, `"Cast"`, `"Share"` buttons + a trailer link).
- A stubbed-circle precedent exists for unrelated actions: phone header Cast button → "Casting is not available in this experiment yet." (`components/PhoneHeader.kt:34-36`).

**What is missing**

- No wire data: no `trailer_url`/`trailer` field in `Card`, `CardMetadata` or `DetailSnapshot` (`EngineWire.kt:40-58, 122-130`); neither engine projection reads any trailer field (no `trailer` in `viptv-engine` or kmp-v2 `simkl`).
- No desktop/phone equivalents: `DesktopDetailHero.kt:102-141` (play / My List / play-from-start / source pill only) and `PhoneDetailHero.kt:23-46` (Play + Favorite) have no trailer or share.
- No share mechanism at all: no platform share call, no deep-link/scheme for a title anywhere in the app or backend routes — "Share" has no possible payload today beyond title text.

**Smallest correct connection**

- *Trailer*: add a bounded `trailer_url` to the `DetailSnapshot` item projection (same dual-write as DATA-02: Rust projection reads the provider's trailer field if it exists; Kotlin mirror), then the TV/desktop/phone heroes get a real action that opens the trailer through the existing playback input path or an external video intent. Until a producer exists, the honest UI is a hidden/disabled circle, not a notice that claims data might exist.
- *Share*: needs no wire data — the smallest correct version is a client-side platform share action (title + artwork + a shareable link). But there is no per-title link contract (no route returns a canonical public URL for a title), so either define that first (one deep-link route in the backend plus a share entry point per platform) or drop the Share circle from the boards. Wiring it to a stub notice like the trailer would repeat the DATA-03 anti-pattern.

---

## DATA-04 — HD / 5.1 / CC chips

**What exists**

- Boards draw them in the meta line: `Tv3Title.html` "Series · 2022 · … · IMDb 7.7 **TV-MA HD 5.1 CC**" (top=410), `Tv3Movie.html`/`Tv3Home.html` "… **4K HDR 5.1 PG-13**", `D3Title.html` same pattern.
- App already renders chip-shaped data from the two fields that exist:
  - TV: `components/TvMediaMetadata.kt:18-40` — `metadataParts()` + `presentationBadges()`.
  - `components/MediaPresentation.kt:13-15`: `presentationBadges = badges.filterNot { it == "Simkl" } + listOfNotNull(metadata.certification)`.
  - Desktop: `DesktopDetailHero.kt:82-99` (`DesktopMetaLine` chips = certification + `card.badges`).
- Wire: `Card.badges: List<String>` (`EngineWire.kt:52`; Rust `presentation.rs:35`), `CardMetadata.certification` (Kotlin :66; Rust :18).
- Producer values today: stremio card mapping sets `badges: vec![]` and `certification: field(v, "contentRating", 128)` (`viptv-engine/src/stremio/media.rs:170, 197`) — so TV-MA/PG-13 can appear; Simkl sets `badges: vec!["Simkl"]` (`viptv-engine/src/simkl.rs:107`), filtered out client-side.
- Structured capability data **does** exist — but only per source, after discovery: `SourceCandidate.quality_height`, `audio_languages`, `presentation {quality, video_codec, audio_codecs, delivery_format, size…}` (`EngineWire.kt:501-523`; Rust `stremio/streams.rs:76-95`, `SourcePresentation` at `streams.rs:25-74`), and subtitles via `SubtitleSnapshot` candidates. The dictionary §A5 (:95-107) maps exactly these to the sources sheet ("quality_height → 1080p chip").

**What is missing**

- No producer of HD/5.1/CC at title level: `badges[]` is empty from stremio catalogs, `["Simkl"]` from Simkl; nothing in either host computes quality/audio/subtitle capability for a title before sources are discovered.
- The declared data contract has no place for them: §A1 defines `badges[]` as the fixed set "Watched / New episode / Returning / Ended" (`canvas-data-dictionary.md:14`), and §A4's title detail has no capability fields. Using `badges[]` for HD/5.1/CC would overload the card-badge renderer (A1: "At most one text label on the art…").
- A mapping has never been defined (the issue's "define the mapping or drop").

**Smallest correct connection**

- *Drop option (contract-faithful)*: keep only the certification chip (already works end-to-end via `contentRating`) and remove HD/5.1/CC from the board meta lines; the dictionary already restricts quality/audio rendering to the sources sheet (§A5).
- *Map option*: define chips as derived best-capability, sourced from data that already exists: `quality_height ≥ 720 → HD / ≥ 2160 → 4K`, `presentation.audio_codecs`/`audio_languages` → 5.1, subtitle candidates → CC — computed at source-discovery time and cached per title. The existing cache/merge point for exactly this kind of enrichment is the kmp-v2 verified-cards cache (`engine_v3/source_integrations.rs:351-375` `cache_card_at`/`merge_cached`, already merged into home rows/hero/featured in `engine_v3/home.rs:131-143`), and locally `merge_cached_presentation` (`local_engine/addons/home.rs:152`). Do not fabricate chips from provider strings (no `://`, bounded — same rules as `SourcePresentation::read`, `stremio/streams.rs:47-74`).

---

## DATA-05 — "Could not load sources" (Spider-Man) / source-failure surfacing

**What exists**

- The failure surface, local engine (TV/desktop/phone native):
  - `NativeMergedSources.kt:39-47` — addon fetch and integration fetch run in parallel; if both fail and nothing was found, one collapsed `LocalFailure`: "VIPTV Sources could not run. Check its TMDB key in Settings…" only when *only* the integration failed, else "Could not load sources. Check the configured providers and try again."
  - Per-connection fetch: `NativeLocalSources.kt:59-81` — `Semaphore(3)`, `withTimeout(25_000)` per addon URL (:70) — this is the issue's 25 s deadline; timeouts/exceptions are swallowed to `null` (:71-73) so no per-provider state survives. Torrent-only detection: `reportsStreams(body)` + `torrentOnly += 1` (:79-81, helper at :31-34).
  - Integration (VIPTV Sources SDK): `NativeSourceIntegrations.kt:24-60` — 45 s overall, 250 ms poll; `error_code` is surfaced only as "Source integration could not complete this title." and only when nothing was found (:42); engine job expiry 120 s (`local_engine/source_integrations.rs:236-247`).
  - The controller collapses again: `ControllerCatalog.kt:107-115` catches `TimeoutCancellationException` → one string ("Some providers did not finish within a minute…" / "Providers did not respond within a minute. Try again.").
  - The sources sheet shows one error line + a torrent-only/debrid explanation that already matches the design copy: `TvSourcesScreen.kt:105-116` (dimmed empty states), `SourcesScreen.kt:68-74`; context `HANDOFF-PROMPT.md:81-86` ("Plain Torrentio only returns torrent links — filtered by design; the panel explains the debrid requirement").
- The TMDB key path (the thing to confirm): schema `vsources` plugin "VIPTV Sources", credential field `tmdb_api_key` (required, secret, ≤512) — `viptv-engine/src/source_integration.rs:34-59`; `VsourcesConfiguration::validate` rejects empty keys (:62-87); the runtime builds the SDK engine with that key, default providers, 4-way concurrency, 80-stream limit, 18 s per-source timeout — `vsources_runtime.rs:143-155`. Identity resolution *requires* a canonical TMDB or IMDb id (`vsources_runtime.rs:36-56`, `media_ref` → `Error::Unresolved` otherwise), which the app tries to guarantee by fetching the full detail document first (`NativeLocalDetails.kt:41-75` `prepareMetadataCard`, engine adds authoritative external ids at `stremio/media.rs:224+`). So the Spider-Man failure is one of: key missing/invalid, metadata provider returned no tmdb/imdb ids for that card, or the 25 s per-addon deadline.
- QA observability (the reproduce path) is wired and privacy-bounded: `observeSourceFailures` / `sourceFailureFact(path, error-class)` and `observeAddonFetches` / `addonFetchFact(kind, status, elapsed, bytes)` — `AddonFetchDiagnostics.kt:9-16`; emitted from `AddonHttp.kt:63-66`, `LocalCatalogRequests.kt:14-16`, `NativeMergedSources.kt:27-34`; installed on Android in `androidApp/src/main/kotlin/org/viptv/experiment/MainActivity.kt:24-25` as `logcat -s VIPTV_SOURCES:W VIPTV_ADDON:I` (counts only, no URLs).
- Remote path: `POST /api/streams` + long-poll `GET /api/streams/:id?after&wait_ms≤10s` + DELETE (kmp-v2 `routes.rs:168, 197-201`; `provider/discovery_v2.rs:76-133`). **Per-provider error events exist on this wire**: the poll rewrites stale sources to `{"source":…,"streams":[],"error":…,"error_code":"source_not_found"}` (`discovery_v2.rs:112-133`), and `start_streams` emits `addon:{id}` error events with a 30 s per-addon server timeout (`handlers_catalog.rs:336-338`). Client budget is 60 s (`RemoteSources.kt:12`).

**What is missing (wire vs UI)**

- The remote client **drops per-provider errors**: `RemoteSources.kt:33-44` reads only `event["streams"]` and never reads `event["error"]`/`event["error_code"]` — provider failure detail arrives and is discarded.
- The local path never produces per-provider state at all (exceptions swallowed at `NativeLocalSources.kt:71-73`; only two aggregate booleans in `NativeMergedSources.kt:24-25`).
- No UI state for a failed provider: `TvSourceRow.kt`/`SourceRow.kt` render only playable candidates; the boards' dimmed "Needs debrid" rows exist for torrent-only (dictionary §A5), but there is no "provider didn't answer" row, so the user cannot tell a key problem from a slow provider from a dead addon.
- DATA-05 as filed is an *investigation* (reproduce on the TV with those logcat tags, confirm the TMDB key path, verify a TorBox/Torrentio source within 25 s). This audit confirms the code paths exist to do it, and that any fix that only changes the message string will still hide which provider failed.

**Smallest correct connection**

1. Keep the poll event shape (it already carries `source`, `error`, `error_code`): in `RemoteSources.kt` collect failed sources alongside candidates (one map, published with the same `publish` cadence).
2. In `NativeMergedSources.kt` replace the swallowed exceptions with per-path failure facts already computed (`sourceFailureFact("addons"/"integration")`) lifted into the outcome — e.g. extend `SourcesOutcome` (`LocalConnection.kt:69`) with a small `failedProviders: List<String>` instead of a new wire type.
3. Render them in the sources sheet as dimmed rows/lines using the existing torrent-only row pattern (`TvSourcesScreen.kt:112-116`), which is the state the boards already define for non-playable sources.
4. For the Spider-Man repro itself: nothing code-side is missing — `logcat -s VIPTV_SOURCES:W VIPTV_ADDON:I` will print `path=integration error=…` if the TMDB key path fails and per-fetch `kind=… status=… elapsed_ms=…` for addon timeouts; then verify a TorBox/Torrentio entry appears inside the 25 s per-addon deadline (`NativeLocalSources.kt:70`) / 18 s SDK per-source timeout (`vsources_runtime.rs:150`).

---

## Summary

| ID | Wire today | Producer today | UI today | Smallest correct connection |
|---|---|---|---|---|
| DATA-01 | `HomeRow.next_cursor` exists (EngineWire.kt:187 / presentation.rs:60) | **never set** (home.rs:33, addons/home.rs:150,250; kmp-v2 engine_v3/home.rs:123 page 0 only) | not read; shelf has no append; retry = whole-home refresh (App.kt:93) | set the cursor at row assembly; page the row through the *existing* `browse()`/`/api/discover` cursor path via `row.browse`; one `homeRowPage` controller modelled on `ControllerDetailPages.detailPage` + shelf-end skeletons per dictionary §A2 |
| DATA-02 | no cast field anywhere (EngineWire.kt:40-58,122-130) | projections read no people/cast (stremio media.rs:18-73; simkl detail none) | TV slot renders only from preview fixture (TvDetailHero.kt:57-59; DetailsScreen.kt:118-128 never sets it) | decide: add `DetailSnapshot.cast` (dual Rust+Kotlin write, both detail projections) **or** drop the board line + `extras.cast` (dictionary §A4 has no cast) |
| DATA-03 | no trailer URL; no share target/deep link | none | TV trailer circle = stub notice (TvDetailHero.kt:78, TvDetailsScreen.kt:103); desktop/phone have neither circle | trailer: `trailer_url` in the detail projection + real open action (else hide); share: define a per-title link contract or drop the circles |
| DATA-04 | `badges[]` (empty/Simkl) + `metadata.certification` (contentRating) | HD/5.1/CC never produced (media.rs:170,197; simkl.rs:107) | chips render from certification+badges (MediaPresentation.kt:13-15; DesktopDetailHero.kt:82-99) | decide: drop chips (dictionary §A1/§A4 don't define them) **or** derive from existing `SourceCandidate`/subtitle capability and cache per title via the verified-cards merge (kmp-v2 source_integrations.rs:351-375) |
| DATA-05 | per-provider `error`/`error_code` on `/api/streams` events (discovery_v2.rs:112-133); local QA facts | local failures swallowed (NativeLocalSources.kt:71-73); remote errors dropped client-side (RemoteSources.kt:33-44) | one collapsed message (NativeMergedSources.kt:44-47; ControllerCatalog.kt:107-115); TMDB-key message only when addons also failed | read the error events / lift failure facts into `SourcesOutcome`, render dimmed provider rows like the existing torrent-only state; repro tooling (logcat tags, 25 s deadline, TMDB key schema) already exists |

Cross-cutting note: because `EngineWire.kt` mirrors the Rust contracts by hand, every wire addition above (cast, trailer_url, row cursor semantics, source-failure outcome) is a two-repo change: `app/native/core/crates/viptv-engine` (+ the host projections in `viptv-core` and the kmp-v2 backend engine_v3) and the Kotlin mirror — plus regeneration of the iOS UniFFI bindings path documented in the workspace `AGENTS.md`.
