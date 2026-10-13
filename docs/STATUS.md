# VIPTV experimental implementation — 2026-10-13

The implementation remains experimental and incomplete overall. Main branches,
repositories and history are preserved. The 197 current VIPTV ZIP boards and the
uploaded TV focus/rows HTML are the design references. Full visual parity is not
certified.

## Current implementation

- KMP Android/Android TV, Linux/Windows/macOS desktop and Web targets. Native
  clients have a local Rust/SQLite engine and remote connections; Web uses a backend.
- Profile-scoped metadata, Stremio catalog/search/streams/subtitles, source
  integrations, metadata-provider selection, favourites, playback preferences,
  watch history and explicit identity evidence. Account credentials remain in
  their platform/server vaults.
- Server-only Simkl OAuth 2.0, metadata/search/watchlists and tracking contracts.
  Linking belongs to Web account settings. Personal ratings and episode states
  remain separate from critic metadata and inferred watch counts.
- Five enriched trending hero titles, eight-second decoded-artwork timers,
  transparent trimmed logos, complete available metadata, and episode/time
  captions for Continue Watching. Focus outlines, scroll cancellation, fixed
  gradients and smaller soft-backdrop surfaces address the reported TV artifacts.
- Search category rows, focused title marquees, visible returning headings and
  detail/search history. Details contain related titles and canonical season
  indexes with bounded episode paging. Native paging reuses verified cached metadata.
- A single library page includes results, controls, source availability and
  known statistics. Sorting sits inside the filter panel. Cold metadata has a
  bounded refresh hint; warm filters require no per-title client requests.
- Direct Simkl images for native clients; Web can use the bounded public image
  cache. Verified transparent logos stay on their public CORS-enabled CDN.
- KitePlayer native rendering and HTML/HLS Web playback. The pinned native
  presentation fork reports presentation evidence rather than decoded-frame counts.
- Live client adapters and clock/guide presentation are wired. Native M3U/Xtream
  Live runs through the local engine with bounded host operations and scoped guide
  caching. M3U category memberships are preserved individually. Packaged native
  engines use release mode; raw native subtitle formats remain supported.

## Current evidence

- Core pin: `1c03990af6bd174634b7f7eb6603e8ba4553e2a2`.
- Backend UI/image pin: `1c0dbd94d68191f852627f9e7affed7aa7233c69`.
- Server UI/image gate: 390 passed, 10 ignored; earlier integration gate: 16 passed.
- Core season/cache batch: 11 native tests passed, two ignored; four Android ABI
  release builds passed. App QA packages ARMv7, ARM64 and x86_64.
- Android QA assembly, 53 Desktop tests and Wasm compilation pass for the UI batch.
  Linux packaged launcher/JVM/SQLite diagnostics and Web production packaging pass.
- Real linked library: 154 titles/41 genres. Cold page 237 ms, warm page 29 ms;
  typed loading hint stops on completion. Actual genre/status/personal-rating and
  episode-state filters returned scoped results.
- Real Naruto: 220 episodes/five indexed seasons. TV selected Season 2 directly,
  loaded its remaining episodes, and retained row focus. The first card returned
  to the 96 px gutter after eight Right/eight Left presses.
- TV emulator: a 643-frame navigation sample measured 16 ms median/p95 and 1.56%
  missed deadlines. Direct new-season thumbnails measured 160–399 ms in the app,
  versus roughly 3–4 seconds through the old proxy. Cold connections can be slower.
- The latest release-engine QA APK is installed on the physical TV and Home
  connects directly over trusted LAN HTTPS. The host firewall now permits only
  the TV IP on the two test ports. Unrecorded rapid navigation measured 23 ms median/65 ms p95 with 10.12%
  missed deadlines (346 frames); hardware performance remains under improvement.
  Its earlier UI sample was 25 ms median/73 ms p95 and predates this batch.
- Native public M3U qualification loaded 100 actual channels with no VIPTV
  backend requests, played a real channel, and returned to the cached Live page.
  No guide data was invented for the playlist without an EPG source.
- Prior real physical-TV native MP4 playback reached 24 fps with zero rebuffers,
  zero audio underruns and about 9 ms A/V drift. That source sample remains the
  playback checkpoint, not qualification of every format or this entire UI batch.
- Prior hosted CI run 38411304240 passed all platform build/package/native-engine
  jobs. It predates the current UI changes; new hosted verification is pending.

## Remaining qualification and implementation

Complete design coverage and unprovided UI review; current physical-TV performance;
Windows/macOS native player GUI; broader HLS/DASH/live/subtitle/seek/recovery and
long-running playback; broader Live feed/guide qualification;
anime tracking/mapping acceptance; dashboard administration; Roku and Solid TV-web
adoption; final workspace cleanup and generic Tauri plugin account transfer.

Production deployment is not claimed. Private real-provider screenshots/videos
remain in the workspace device artifacts rather than repository history.
