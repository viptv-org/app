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
core is `47b85b5`; experimental backend is `7dde035`; gateway is `8deb493`.
The running QA server retains backend `7c3f042` and gateway `c85cd27` during manual testing. The compatible
TV-web WASM bridge is core `4f461e8` and preserves older TV browser compatibility.
Backend: 438 Live/SDK checks passed (10 ignored) at `7dde035`; the last default-feature run at `cc72fbc` passed 415 (8 ignored). This includes the paused-import identity regressions. Gateway: 230 default checks passed; all-features 255 passed / 59 ignored after a two-import test compilation fix. KMP: 90 desktop checks,
Android QA assembly and Wasm compilation passed. Hosted run 38499843865 passed
Android, Web, Linux, Windows, macOS and renderer gates; GUI playback on Windows
and macOS has not been qualified.

The installed Onn TV baseline predates the queued candidate and uses trusted LAN HTTPS.
A fixed 1080p60 stream measured 59.99–60 compositor FPS with zero reported decoder
drops, FFmpeg AAC audio and Amlogic hardware AVC video. A real native VixSrc episode
was visually verified at its approximately 24 FPS source cadence with zero drops.
These samples do not qualify every format or all UI transitions.

Roku passed 73 local checks and compilation/staging. Its actual off-device API
adapter automatically collected all 1,184 real One Piece episodes. TV-web passed
337 unit/component checks and three Chromium launcher checks. A real HTTPS
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
Its Android assembly, 70 desktop checks, Wasm compilation and FFmpeg packaging checks passed. It
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
an input-file ordinal. This candidate passed 69 desktop checks, Android QA assembly,
Wasm compilation and FFmpeg packaging checks. The native core is repinned and its packaged runtime sections match the separately
built release inputs. Backend/gateway candidate updates are committed; activation
and physical multi-audio qualification are pending;
existing user-facing services and the installed TV app are unchanged.

Media3 rejects a required audio ordinal inside a multi-format adaptive audio group:
its encoding order is not a verified equivalent of file audio-stream order. Normal
manual track choices remain available. Single-format groups and Kite's demuxer
track ordering are supported; adaptive-HLS language selection remains unqualified.

TV-web now rotates five host-selected featured titles after artwork composition,
with eight-second timers, five indicators, retained Play focus and shelf/menu
pausing. Its production candidate passed 335 checks, type/build verification and
three Chromium launcher checks. A separate browser fixture exercised actual
rotation and a row pause; screenshots were inspected. The current header/hero
geometry still needs migration to the latest TV design. Served assets were not
replaced during manual testing.

Latest hosted run 38529217190 did not start any build step: GitHub reports account
payments failed or the spending limit needs increasing. Previous successful hosted
runs apply to their recorded revisions. Current local Android, Wasm and Linux
checks pass; latest Windows/macOS checks remain unavailable.

Phone and desktop Browser/Device sign-in now use the same cancellable server
challenge lifecycle as TV. Normal flows show real QR/code/expiry, open or copy the
complete approval link and retain the explicitly chosen server between methods.
Android Back returns to method selection. Invalid origins are rejected before a
network client is allocated. Changed/new/error states are recorded in GAP-055.
Four packaged Linux/common-phone layout captures were inspected and both QR
images decoded correctly; external browser launching and physical-phone input
remain unqualified. The current candidate passed 70 desktop checks, Android QA
assembly, Wasm compilation, FFmpeg/native package checks and bundled desktop
JVM/SQLite smoke. It was not installed while physical-TV manual testing continued.

The backend paused-import fix requires episode IDs and supplied coordinates to
agree on a unique episode, preventing conflicting Simkl TV/anime evidence from
writing progress onto another episode. Before-fix regressions reproduced the bug;
eight focused checks now pass. No real history or provider tracking was modified;
live services retain their previous commits.

Native IPTV entry now accepts M3U/Xtream on phone/tablet/desktop/TV, selects or
unlocks the local profile before saving, and opens real Live channels. Tab drafts
remain separate; cancellation closes a late-opening native engine. Three focused
controller checks use real SQLite/vault configuration with only directory IO
replaced. A separate real public-M3U run read 100 channels and 28 groups in
1,063 ms through the controller without a VIPTV backend. Real Xtream playback
and the updated physical-device form remain unqualified. Android QA and release,
73 desktop checks, Web compilation and packaged JVM/native/FFmpeg checks passed.

Backend `cc72fbc` adds encrypted profile-private Xtream configuration for paired
devices using the existing wire contracts and shared engine builders. In-place
updates preserve metadata ownership; bounded/coalesced catalog/guide projections
and opaque sources retain profile/revision boundaries. Legacy account providers
remain viewable and cannot be deleted by a paired profile. M3U playback headers
are retained. No real Xtream subscription or live runtime activation is claimed.

Four native IPTV layout captures were inspected. Image-pixel verification and
12 windowless Compose frames, including an active-tab click, confirm stable
heading/back-link rendering; the earlier missing-heading diagnosis was incorrect.
No rendering workaround is retained. Full parity, physical input and desktop GPU
performance remain unqualified.

Profile-owned Stremio discovery now supports advertised multiple selections across
core/backend/KMP. Scalar clients remain compatible. KMP uses explicit scoped
catalogs, resets foreign filters on switching, and sends one catalog request per
page. Its sheet applies drafts once, respects limits and restores TV focus; seven
new checks include actual Compose input/focus and a real native Rust/SQLite bridge
with only add-on IO replaced. Simkl advanced parameters remain limited to its
verified capabilities. New sheet states and three inspected common-layout captures
are recorded in GAP-057. TV-web/Roku now consume the same controls; their current validation is recorded below.

Core `47b85b5` passed 62 engine and 39 native checks (3 native ignored); backend
`5aa2f1c` passed 427 checks (10 ignored). Its separately built Linux/Android API26
release libraries were copied by verified hashes, with unchanged UniFFI bindings.
KMP passed 80 desktop checks, Android QA/release assembly, Wasm compilation and
packaged JVM/SQLite/FFmpeg checks; packaged native bytes were verified. Current
TV/server installations retain their previous sources during manual testing.

Required catalog-specific search inputs are visible and apply their draft once;
missing required fields leave controls usable without a catalog request. Add-on
configuration refresh clears stale discovery controls/cards/cursors. The native
bridge check also switches to a search-only catalog, clears its previous genres
and performs one search request.

TV-web `7bba985` and Roku `5ee3377` now read host discovery capabilities on demand,
including catalog-less providers, exact catalog ownership, friendly choice labels,
required inputs and multiple-selection limits. Both keep a draft until Apply,
clear foreign filters on catalog changes and retain selections while paging.
TV-web passed 337 checks, type/build verification and production-bundle interaction
checks on all three Chromium launchers, including 40 choices, Cancel, selection
limits and focus return. Screenshot inspection caught and corrected overlapping
checkbox/label spacing; the corrected full source image is recorded in evidence.
Roku passed 73 local checks, compilation, provenance checks and public runtime ZIP
inspection. Its new interaction fixture executes the actual scene/sheet handlers.
These new filter states remain provisional design variants in each client’s
tracking document. Physical Roku presentation and full design parity remain open.

The existing independent real-browser fixture token returned 401 during a
read-only discovery check. It was not refreshed or modified during manual TV
testing. TV API/gateway health and trusted TLS remain successful; active TV/server
installations were kept unchanged. No updated physical-device acceptance or
production activation is claimed.

Browser Account→Devices now uses one account-owned aggregate with both browser
and paired-app families, current-browser protection and confirmed single/all
sign-out. A successful single removal reconciles just that row with no follow-up
read. Parent-policy refusals clear previously displayed device data. Screens follow
W3Account geometry; browser/common-phone/confirmation captures were inspected.
New states are tracked in GAP-058. Repeated confirmation input now submits once.
Phone Playback/Subtitles settings open the existing real preference forms and
return to Settings. Native sign-ins send readable platform/model names without
hardware identifiers; native clients cannot dispatch browser account management.

KMP passed 90 desktop checks, Wasm compilation, Android QA/release assembly and a
new Linux distribution’s bundled-JVM/SQLite smoke. Both APKs retain verified
three-ABI official FFmpeg audio and 16 KiB alignment. These are build/common-renderer
checks, not new physical TV/Android input or desktop GPU acceptance. The new APK
remains uninstalled during manual testing; the running QA services are unchanged.

Backend `7dde035` passed 438 Live/SDK checks with 10 ignored. Four account-device
HTTP/SQLite regressions cover scope, parent policy, CSRF and preserved content.
The preceding Simkl fix scopes equal numeric IDs by title category and requires
provider-asserted anime episode hints to agree, including imported Next/tracking;
66 focused checks passed with one ignored. A final-transaction authorization fence
rejects queued TV approvals after their browser family was revoked; 32 auth checks
passed. All new revocations/imports used isolated fixtures. No real account history
or active session was changed. Updated runtime preparation remains separate from
activation; broader dashboard, real-provider anime acceptance and full design
parity remain unfinished.

The immutable 7dde035 QA release harness and reused 8deb493 gateway are prepared
with verified hashes, separate candidate/rollback manifests and an activation
plan. The prior 5aa candidate remains intact. Neither new binary was launched;
the existing API/gateway PIDs and live pointers remain unchanged. Build readiness
does not establish live activation or current-device acceptance.
