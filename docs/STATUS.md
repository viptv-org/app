# VIPTV v2 implementation status — 2026-10-13

The experimental implementation is incomplete. Main branches and repository
history are preserved. The current VIPTV ZIP import contains 197 reference
boards; complete visual parity is not certified.

## Implemented

- KMP Android, Android TV, desktop and Web clients, with native local Rust/SQLite
  mode and remote connections. Web requires a backend for normal use.
- Shared profile lifecycle, salted PIN protection, per-profile watch history,
  integration assignments, capability validation and identity mapping rules.
- Native Home catalog projections cached for five minutes, isolated by account,
  profile and configuration revision. Warm/reopened plans omit cached requests.
- Stremio metadata, search, source and subtitle execution. Subtitle candidate
  snapshots use opaque IDs; selected content remains host-controlled.
- Server Simkl OAuth, metadata/search, default Home/discovery, explicit watched
  writes and verified IMDb source/subtitle mappings. Credentials stay encrypted
  on the server and profile-bound; no real account OAuth has been qualified.
- Dedicated TV screens and remote focus work; platform-specific phone, desktop,
  tablet and browser details/season surfaces and addon settings.
- Native KitePlayer and browser HTML/HLS adapters. Android Compose rendering and
  stable Haze source registration allow actual paused-video backdrop blur.

## Verified evidence

- Core cache/subtitle batch is committed as `ed232d1`; shared Simkl normalization
  and mappings as `941636a`. Backend integration is committed as `201ba1f`.
- Backend library suite: 319 passed, 6 ignored. Core runtime engine suite: 25
  passed. Focused Simkl suite: 13 passed.
- Android assembly, Desktop tests and Wasm compilation passed for the latest
  client subtitle/gateway plumbing. Loopback HTTP subtitle tests verify original
  Windows-1252 bytes, opaque candidate IDs and revocation before outbound access.
- Disposable HTTPS browser playback qualified actual FFmpeg HLS decoding,
  frame-presentation acknowledgment, provider SRT/VTT captions and stop/Blob
  cleanup. Default caption placement refinement remains underway; this fixture
  does not qualify an independent production gateway.
- TV native paused-frame blur A/B, playing panel open/close, Live fallback variants,
  Calendar return focus and subtitle option traversal passed on the isolated TV
  emulator. Physical devices were not used.
- HTTPS browser addon installation, selected-profile toggles, removal and signout
  persisted against a disposable backend. Rapid URL typing, Unicode/IME and
  encoded-query submission passed after scoped native browser input integration.

## Remaining

- Complete 1:1 design qualification across all supplied boards, unprovided UI
  review, real Live/Calendar feeds and remaining authentication/preferences.
- Physical/native provider subtitle playback qualification, complete native remote gateway
  delivery and actual frame-presentation callbacks; SDK work is underway.
- Real Simkl OAuth with registered operator credentials and user consent,
  automatic tracking/scrobbling, continuous synchronization, advanced filters,
  calendar and anime episode mapping/tracking.
- Complete vsources/local-source provider integration, dashboard account-linking
  UI, Roku and Solid TV-web adoption of the agreed backend contracts.
- Windows/macOS and physical GPU/device performance, real HLS/DASH/live playback,
  complete recovery/resume, and independent production gateway integration.
- Final workspace documentation/design cleanup and generic Tauri video-plugin
  account transfer; no deployment or production success is claimed.

Detailed limitations and screenshots live in the screen checkpoints,
`DESIGN_GAPS.md`, `NON_TV_DETAILS.md`, `NON_TV_SETTINGS.md` and `TV_PLAYER.md`.
The large client integration batch remains under review before commit.
