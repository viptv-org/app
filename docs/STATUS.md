# VIPTV experimental implementation — 2026-10-14

The implementation is incomplete overall. Work remains on experimental branches;
main branches, repositories and history are preserved. The current uploaded ZIP
boards and TV focus/rows HTML are the design references. Full design parity is
not certified.

## Current implementation

KMP covers Android, Android TV, Linux, Windows, macOS and ordinary Web. Native
clients use the Rust/SQLite engine locally or connect remotely; Web requires a
backend. Metadata selection and capabilities belong to each profile. Simkl is
server-only for OAuth, metadata, lists and tracking. Stremio supplies catalogs,
search, streams and subtitles. Native source integrations use explicit mappings.

Android playback now uses Media3 1.11.1 with the matching official FFmpeg audio
extension preferred, hardware MediaCodec video and SurfaceView presentation.
Desktop retains the pinned Kite presentation fork; Web uses HTML/HLS playback.

KMP has five trending hero titles with eight-second decoded-artwork timers,
transparent trimmed logos, available metadata, episode/time continuation captions,
categorized search with marquee titles and retained navigation history, related
content, and automatic episode paging. The detail background stays sharp and
moves with scroll distance. Library filters include provider capabilities, sorting,
status, genre and personal ratings. Full visual coverage remains under review.

The shared backend owns opaque identity/mapping, provider discovery, unified
Simkl library/history import, continuation decisions and scoped playback grants.
Native M3U/Xtream can also run locally. Provider credentials stay in platform/server
vaults. Gateway external captions are viewer-scoped SRT, already rebased to their
media clock, with retained selection across managed replacements.

Roku and Solid TV-web now use the shared host content APIs: one Home aggregate,
explicit shelf actions, categorized global search, automatic episode/library
paging and complete lightweight saved-title membership. Live projections retain
opaque M3U connection/channel/group IDs. Unsupported Live collections are hidden.
Roku's provider caption controller uses managed replacement; physical decoding
and design parity remain unqualified. TV-web provider captions use normalized VTT on the original title clock; their
physical TV rendering and richer provider controls remain qualification work.

## Current evidence

The detailed receipts and immutable pins are in `build-evidence.json`. Native
core is `a7495e7`; backend is `7c3f042`; gateway is `c85cd27`. The compatible
TV-web WASM bridge is core `4f461e8` and preserves older TV browser compatibility.
Backend: 413 checks passed. Gateway: 229 passed. KMP: 65 desktop checks,
Android QA assembly and Wasm compilation passed. Hosted run 38499843865 passed
Android, Web, Linux, Windows, macOS and renderer gates; GUI playback on Windows
and macOS has not been qualified.

The current QA APK is installed on the physical Onn TV over trusted LAN HTTPS.
A fixed 1080p60 stream measured 59.99–60 compositor FPS with zero reported decoder
drops, FFmpeg AAC audio and Amlogic hardware AVC video. A real native VixSrc episode
was visually verified at its approximately 24 FPS source cadence with zero drops.
These samples do not qualify every format or all UI transitions.

Roku passed 71 local checks and compilation/staging. Its actual off-device API
adapter automatically collected all 1,184 real One Piece episodes. TV-web passed
332 unit/component checks and three Chromium launcher checks. A real HTTPS
Home→Details→Search run used one bootstrap, one metadata request and one Naruto
query for 38 results, with zero JS/request failures on the warm run. Screenshots
were inspected. An earlier cold traversal returned 37 artwork 502s; a fresh cold run now returned
53/53 successful images. The original cause remains unconfirmed, with bounded
failure diagnostics added. Warm success does not explain the earlier failure.

## Remaining work

Full design coverage and unprovided UI review; remaining physical-TV UI/performance
acceptance; desktop GUI/player qualification; broader format/live/subtitle/seek
and long-running playback; anime tracking/mapping acceptance; dashboard
administration; remaining TV-web/Roku migration; remaining workspace and inactive-client cleanup. Production deployment is not claimed. Private provider
captures remain in workspace artifacts rather than repository history.

An isolated Linux playback diagnostic visibly rendered the public 1080p60 sample,
reported roughly 60 decoded FPS, zero rebuffers/underruns and approximately 25 ms
A/V drift. Drawing cadence and broader desktop GUI performance remain unqualified.
The process used its own silent audio sink, which was removed after the check.
Actual provider WebVTT projected all 1,775 cues and matched five client-clock
positions with one content load; physical TV caption rendering remains separate.

The local QA API/gateway/HTTPS proxies now run under enabled user service target
`viptv-qa.target`, independent of browser/chat process lifetime, with automatic
restart. All four canonical/LAN endpoints passed trusted TLS health checks; an
authenticated LAN bootstrap completed in 583 ms.

The next TV candidate reduces settled backdrop compositing, bounds role-specific
artwork prefetch, and returns Sources focus to the last-focused provider chip.
Its Android assembly, 65 desktop checks, Wasm compilation and FFmpeg packaging checks passed. It
is not installed while manual testing continues; physical validation is pending.

The standalone native Tauri plugin moved to `vynxc/tauri-plugin-video`. Generic
branding and package metadata are on `experiment/kmp-v2`; the main commit and
all branch/tag refs were preserved through transfer. It passed 56 native and
31 JavaScript checks, package build and a zero-advisory root dependency audit.
It is independent of KMP playback; no registry release is claimed.

The old Tauri desktop host and Android-only client are retired on their
experimental branches. Their current branch trees contain only retirement
records and original root licenses, pointing to the KMP app. Main branches,
Git history and historical local files remain available.

The TV candidate also connects normal Phone/Browser sign-in to the backend device
challenge: real QR/code/expiry, bounded polling, cancellation and explicit HTTPS
server selection. A live adapter check approved a fresh owned challenge, restored
its saved session and loaded a real profile Home, then signed out its temporary
family. Existing device families and the physical TV were untouched. A focused
check covers cancellation during bootstrap without persisting the abandoned
session. Physical-TV sign-in visual/input review remains pending.

Desktop Back now follows route return state, including Details→Search and
Sources→Live. Unavailable Forward/Cast/Remote chrome actions are disabled instead
of accepting clicks without an operation.

A subsequent candidate preserves a source's optional required audio-only ordinal
through local/direct playback into Media3 and Kite, maps it to actual player track
IDs, and keeps later manual choices. Missing/unsupported requirements fail rather
than silently selecting another language. Gateway remux output is not assigned
an input-file ordinal. This candidate passed 68 desktop checks, Android QA assembly,
Wasm compilation and FFmpeg packaging checks. The corresponding core/backend/
gateway artifact updates and physical multi-audio qualification are pending;
existing user-facing services and the installed TV app are unchanged.
