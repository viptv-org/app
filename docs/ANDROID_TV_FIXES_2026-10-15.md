# Android TV fixes and unified search — 2026-10-15

Work is on the existing experimental branches. These are local source changes and build candidates, not a deployment or physical-TV acceptance. Existing browser device-approval changes were preserved. The manually tested TV, API/gateway processes and live Wasm distribution were not changed.

## Later continuation

This file retains the first batch's historical artifact and test counts below. The
current continuation additionally includes [metadata origin/delegation](METADATA_ORIGIN_COMPLETION.md),
[calendar controls/actions](CALENDAR_COMPLETION.md), [browser playback](BROWSER_PLAYBACK_COMPLETION.md)
and [profile administration](PROFILE_ADMINISTRATION.md). The final full desktop
gate passes 287 tests; backend gates pass 503 normally and 505 with SDK runtime.
The [latest Android receipt](evidence/android-recovery-apk-audit-2026-10-15.json) identifies
the current immutable candidate, its signing limits and native packaging checks.
The complete remaining-scope ledger is [REMAINING_KMP_WORK.md](REMAINING_KMP_WORK.md).

## Approved product direction

Retain the plugin capability system. Text search queries enabled, profile-assigned Simkl and Stremio integrations together. Simkl has Anime, Movies and TV rows; searchable Stremio add-ons have separate carousels. Defer the discovery/filter redesign. Existing browsing defaults remain; Anime joins the current Movies/Series routes as previously requested. Do not remove metadata/plugin infrastructure based on the superseded suggestion to support only two hardcoded plugins.

## Changes

- Activity and Media3 ViewModels retain the controller, navigation and player across Activity recreation. Background pauses and saves the final renderer position; foreground resumes only playback suspended by the background transition. This does not promise recovery after Android kills the process.
- Playback progress and renderer seeks account for the sliding HLS window origin as well as the initial gateway seek offset. Series Play resolves an actual numbered episode; explicit specials remain selectable.
- Refresh rotation and secure persistence survive navigation cancellation. Refreshes serialize across requests and native connections; retiring/switching sessions waits for in-flight rotation, and sign-out finishes local teardown after server revocation. Local-mode entry also waits before clearing saved remote credentials.
- Remote source discovery publishes interim batches, waits up to one minute for the backend's late SDK results, and retains available choices with a clear timeout state. Shared Rust parses bounded source metadata after backend redaction. Structured values win; unknown values stay unknown. Captured/synthetic title fixtures are reusable and contain no source URLs or credentials.
- Source rows grow with the full multiline label and release description; parsed metadata no longer hides the original text. Initial profile focus waits for lazy-list layout and the enabled focus target.
- Calendar publishes its first 100 items before automatically fetching later pages. Cursor advancement, ownership and cancellation are checked. TV composition reuses day indexes and requesters. Backend public-document fetches no longer hold a global cache lock over network I/O; repeated policy checks are memoized within a request.
- Simkl genre choices deduplicate slugs, optional controls do not add a second all/default choice, and switching genre removes unsupported timeframe selections before querying.
- Shared Coil artwork mapping uses wsrv for recognized public artwork hosts on Android, desktop and browser. Local, authenticated, signed and unknown-provider URLs retain their original transport. A public transparent logo returned HTTP200, CORS `*`, WebP RGBA with alpha range0–255. Web navigation uses the design's text hover response and clips the profile action.
- Unified search retains provider provenance through rows, metadata, episodes and source discovery. Individual provider failures do not erase successful results. Saved browsing defaults and profile/kids/configuration checks remain authoritative.

## Verification and artifacts

See `evidence/native-engine-source-metadata-search-2026-10-15.json` and `native/core/docs/source-metadata-overlay.json` for exact source and library hashes. Native core is an explicitly recorded uncommitted source overlay on the prior pin; no new immutable revision or generated DTO adoption is claimed.

Rendered Compose evidence:

- `evidence/android-tv-fixes-2026-10-15/initial-profile-focus.png`
- `evidence/android-tv-fixes-2026-10-15/dynamic-source-rows.png`

The source corpus and timing procedure live in the canonical core's `docs/source-metadata.md`. Tests include captured provider cards, unusual field layouts, explicit metadata precedence, codec/quality/language variants, valid language regions, and unknown/malformed inputs. They do not prove every possible release format or measure fresh network discovery from every provider.

Local KMP verification: **122 desktop tests, zero failures**, followed by **12 targeted tests, zero failures** after the final source-origin transport adjustment. Android QA assembly and Wasm compilation passed. Three packaged Android engine libraries were checked against Gradle's stripped outputs and their rebuilt executable/data sections; the desktop native library was rebuilt too.

Candidate APK: `artifacts/android-tv-2026-10-15/viptv-android-tv-qa.apk` (74,899,247 bytes), SHA256 `54acba448b38d60ecfdd73163efbe576ba60f7e519cfd01772a1fcf90ec8e050`. Exact artifact evidence is in `evidence/android-tv-fixes-2026-10-15/build.json`. It has not been installed.

The final parser corpus contains **942 cases**, including **140 sanitized captured cards**. The complete core batch took **333 ms**, and the backend batch **284 ms**. These are cached parser timings, not live provider-network timing.

Backend final verification: **104 relevant tests, zero failures**: 67 Simkl (including all seven unified-search cases and canceled OAuth rotation), 30 auth, two source corpus/redaction, three continuation, one existing library/Home metadata-cache reuse, and one late metadata revision regression. The library lock and cache-revision cases were rerun after their fixes. The backend contract is documented in `.worktrees/kmp-v2/backend/docs/UNIFIED_SEARCH_V3.md` relative to the organization workspace.

The APK signature verifies successfully. Backend source has not been activated; the old QA backend intentionally remains running. Updated grouped search/remote parsing require that backend candidate; the app falls back to legacy text search on an older server.

Read-only health checks of the existing TV QA API and gateway both returned HTTP200 with successful TLS verification. No runtime activation occurred.

## Remaining qualification

Physical Android TV lifecycle, prolonged playback/seek/tracking, actual calendar FPS, audible output and arbitrary third-party formats still need device acceptance. The native one-shot source limitation and rolling-HLS sidecar reset listed at this checkpoint have since been addressed in the continuing work: see `ANDROID_SIDECAR_PLAYBACK.md` and `evidence/native-progressive-source-build-2026-10-15.json`. The first candidate APK above predates those follow-up changes. Broader physical-device and long-running qualification is still open. Broader prior handoff items (platform parity, all design boards and deployment) remain open.

Use isolated Wasm output for any distribution build. Never overwrite the served `composeApp/build/dist/wasmJs/productionExecutable` or interrupt the ongoing physical-TV testing session without current authorization.
