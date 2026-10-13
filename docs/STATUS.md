# VIPTV v2 implementation status — 2026-10-13

The experimental implementation is incomplete. Main branches and repository
history are preserved. The current VIPTV ZIP import contains 197 reference
boards; complete visual parity is not certified.

## Implemented

- KMP Android, Android TV, desktop and Web clients, with native local Rust/SQLite
  mode and remote connections. Web requires a backend for normal use.
- Shared local profile lifecycle, salted local PIN protection, server profile management availability, per-profile watch history,
  integration assignments, capability validation and identity mapping rules.
- Native Home catalog projections cached for five minutes, isolated by account,
  profile and configuration revision. Warm/reopened plans omit cached requests.
- Stremio metadata, search, source and subtitle execution. Subtitle candidate
  snapshots use opaque IDs; selected content remains host-controlled.
- Server Simkl OAuth, metadata/search, default Home/discovery, explicit watched
  writes and verified IMDb source/subtitle mappings. Credentials stay encrypted
  on the server and profile-bound; real OAuth 2.0 consent, profile-bound search and encrypted token refresh are qualified against the disposable QA server.
- Dedicated TV screens and remote focus work; platform-specific phone, desktop,
  tablet and browser details/season surfaces and addon settings.
- Native KitePlayer and browser HTML/HLS adapters. Android Compose rendering and
  stable Haze source registration allow actual paused-video backdrop blur.
- The pinned KitePlayer presentation fork is packaged as a verified source
  bundle. Native builds prepare a scoped Maven repository and keep the unchanged
  engine/FFmpeg dependencies upstream. The client consumes typed presentation
  evidence instead of treating Playing or decoded-frame counts as presentation.

## Verified evidence

- Core cache/subtitle batch is committed as `ed232d1`; shared Simkl normalization
  and mappings as `941636a`. Backend integration is committed as `201ba1f`.
- App player provenance is committed as `d61d63b`; source bundle pin
  `56f361b7610bf1d20c328cf919877e37baff9d1f` passed 18 renderer tests, additive ABI
  checks, clean import and published-consumer builds. The app's own bootstrap
  reproduced all 18 artifact hashes and Android/Desktop compilation passed.
- The actual HLS/subtitle QA fixture is committed separately as backend
  `242e303`. Default browser captions render above controls; explicit provider
  positioning, repeated stop and revoked media access passed on the fixture.
- Latest backend suite: 378 passed, 7 ignored. Core runtime engine suite: 25
  passed. Focused Simkl suite: 13 passed.
- Android assembly, Desktop tests and Wasm compilation passed for the latest
  client subtitle/gateway plumbing. Loopback HTTP subtitle tests verify original
  Windows-1252 bytes, opaque candidate IDs and revocation before outbound access.
- Disposable HTTPS browser playback qualified actual FFmpeg HLS decoding,
  frame-presentation acknowledgment, provider SRT/VTT captions and stop/Blob
  cleanup. This fixture does not qualify an independent production gateway.
- TV native paused-frame blur A/B, playing panel open/close, Live fallback variants,
  Calendar return focus and subtitle option traversal passed on the isolated TV
  emulator. The physical ARMv7 Android TV now runs the optimized experimental APK. Controlled navigation median frame time improved from 150 ms to 48 ms; performance remains below acceptance.
- HTTPS browser addon installation, selected-profile toggles, removal and signout
  persisted against a disposable backend. Rapid URL typing, Unicode/IME and
  encoded-query submission passed after scoped native browser input integration.

## Current playback checkpoint

- Real Simkl Home/details/search and Stremio episode stream discovery work after user OAuth consent.
- Default native admission detects the unsafe HTTPS-to-HTTP redirect and selects the independent playback gateway. Its actual HLS playlist and segment return 200, and a host decoder produced a complete 1920×960 RGB frame.
- Native playback now produces real presentation evidence on the emulator and physical ARMv7 TV. Awaiting renderer attachment fixes a late-attach seek/stall. The gateway negotiates original seekable MP4 for compatible native clients; a bounded 1 MiB range cache/coalescer avoids upstream starvation. The physical TV sample reached native 24 fps, zero rebuffers/underruns and approximately 9 ms A/V drift with HardwareZeroCopy MediaCodec and AndroidWindowGpuCompleted. This qualifies the measured sample, not every media format or the entire UI.
- ARMv7 packaging, optimized Android release assembly, desktop tests and Wasm compilation pass. Windows/macOS runtime qualification is still pending.

## Remaining

- Complete 1:1 design qualification across all supplied boards, unprovided UI
  review, real Live/Calendar feeds and remaining authentication/preferences.
- Broader subtitle, seek/recovery, long-running playback and live-format qualification across physical devices. Native remote MP4/gateway playback is qualified for the tested source.
- Broader real-provider tracking/scrobbling acceptance, continuous synchronization and remaining advanced filters,
  calendar and anime episode mapping/tracking.
- Complete vsources/local-source provider integration, dashboard account-linking
  UI, Roku and Solid TV-web adoption of the agreed backend contracts.
- Windows/macOS and physical GPU/device performance, real HLS/DASH/live playback,
  complete recovery/resume, and production deployment qualification.
- Final workspace documentation/design cleanup and generic Tauri video-plugin
  account transfer; no deployment or production success is claimed.

Detailed limitations and screenshots live in the screen checkpoints,
`DESIGN_GAPS.md`, `NON_TV_DETAILS.md`, `NON_TV_SETTINGS.md` and `TV_PLAYER.md`.
The large client integration batch remains under review before commit.
