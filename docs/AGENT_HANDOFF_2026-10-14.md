> 2026-10-15 follow-up: see [ANDROID_TV_FIXES_2026-10-15.md](ANDROID_TV_FIXES_2026-10-15.md) for the current uncommitted source, approved unified-search direction, local APK and verification. Browser approval sources are present in the working tree; do not execute the historical stash restoration steps below. The physical TV/QA runtime remains unchanged. Implementation completion is narrower than all historical handoff/platform scope.

# VIPTV experimental implementation handoff — 2026-10-14

## Resume context

The user requested a good stopping point and this handoff because usage is nearly exhausted. The overall goal is **incomplete**. No claim of full TV/backend/platform completion is justified. The last implementation checkpoint is committed and pushed; this handoff is the only new change at the stopping point.

Workspace: `/mnt/ALPH/code/viptv-org`. Every implementation repository below uses `experiment/kmp-v2`. Never write to main, delete repositories/history, or treat production as deployed. Read current source and runtime before continuing; historical summaries are location hints, not proof.

The user is manually testing the physical Android TV and collecting issues. Keep its server running. **Do not install/launch/force-stop the TV app, send input, take device screenshots, change resolution, restart its API/gateway, or overwrite served web bundles until the user finishes testing.** A previous question about whether testing is finished has no answer. Independent builds and isolated test servers are fine.

## Product and architecture decisions

- New KMP repository for Windows/Linux/macOS, Android mobile/TV, and regular browser web. Native clients support local operation without a backend **and** remote operation. Browser, Solid TV-web (Tizen/Vizio/webOS), and Roku require the backend.
- Shared Rust/Crux engine powers native local and server behavior, with generated bindings. Server and local provider capabilities drive the UI. Maintain small, organized files.
- Stremio supplies metadata, streams and subtitles; native vsources supplies streams and requires identity mappings. Simkl is server-only metadata, authentication, tracking/scrobbling. Linking accounts is through browser UI. Account installation/profile-specific configuration and credentials must preserve isolation. Multiple stream providers are useful; metadata defaults should avoid requiring a provider selector on every search. Do not invent a public plugin marketplace or WASM plugin scope.
- Shared library/continue-watching should import Simkl data; no separate Simkl-only watchlist. Never require a user-facing “load more” action. Pagination must load automatically, including long series such as One Piece.
- Uploaded VIPTV designs supersede the old design repository content. Current extracted source is `.worktrees/kmp-v2/design/current` (197 boards). Track missing/provisional UI explicitly in app design-gap documentation. This work does not authorize deleting repos/history.
- Android playback now uses Media3 hardware video/SurfaceView plus the official FFmpeg audio extension. KitePlayer was earlier considered but is not a blanket web dependency. Desktop and other platform playback still require qualification.
- Main hero is five trending titles with transparent logos and five indicators; active indicator fills over eight seconds, then advances. Playback/row focus, no image flashes/teleports, responsive transitions, full metadata on every platform, correct search restoration and episode positioning remain acceptance requirements.

## Authoritative repository checkpoint

Verified at handoff: all listed implementation worktrees clean and synchronized with upstream. Workspace alone has pre-existing untracked `experiment/kiteplayer-sdk/`; leave it alone.

| Repository | Location relative to workspace | HEAD |
|---|---|---|
| KMP app | `app` | `a1e054d462fd279906bd3c6f5f5ed0187c04eacf` |
| Backend | `.worktrees/kmp-v2/backend` | `7dde035920f3570cbd2dce42f0c068af83f893f6` |
| Core | `.worktrees/kmp-v2/core` | `47b85b5386617f455ade8b294a7a844b54f537c0` |
| Solid TV-web | `.worktrees/kmp-v2/tv-web` | `7bba9853bc431ab5c42dcc58b2c1c5e5918f797e` |
| Roku | `.worktrees/kmp-v2/roku` | `5ee3377eb66f507fbd16455e040cb5cdc4c2886e` |
| Playback gateway | `.worktrees/kmp-v2/playback-gateway` | `8deb493c5d428d64298e339f7ae02ebc195e1fdc` |
| Workspace plan | `.worktrees/kmp-v2/workspace` | `91dc6a50cbd69492a3ba7bd7378647248cb60254` |

## Live TV runtime — deliberately older than source

Fresh read-only handoff checks: TV API and gateway both HTTP 200, TLS verification 0; services active with unchanged PIDs.

- API: `https://192.168.88.14:18451/api/health`, PID `1976719`, live source `7c3f0428a59e34eba5c7b173517a073bc68acb9c`.
- Gateway: `https://192.168.88.14:18452/health`, PID `1976720`, live source `c85cd279c68e9fbc46530fb2e39bfc5de45ee7cd`.
- Canonical API `https://viptv.local.test:18445`; canonical gateway port 18449. `/qa/`, `/tv/`, `/kmp/` are served here.
- Enabled user `viptv-qa.target`: API, audit, canonical HTTPS/proxy, gateway HTTPS/backend, LAN HTTPS/proxy. Restart-on-failure and linger are enabled. Private runtime metadata lives in `/home/vynxc/.local/state/viptv-qa`.
- Do not call generic local up/down scripts or restart this target while human testing. Read `/home/vynxc/.agents/skills/viptv-deploy/SKILL.md` before runtime/deployment work. No TLS bypass. Never claim production success without deployment-result and live-asset evidence.
- Physical TV: Onn 4K Pro, ARMv7 SDK34, IP `192.168.88.30`; ADB serial last known `adb-PUSA2416001268-1vwWjT._adb-tls-connect._tcp`. ADB binary `/home/vynxc/Android/Sdk/platform-tools/adb`. Existing 1920×1080 override/density320 must not change. Rediscover wireless endpoint when testing becomes authorized.

Installed APK SHA256: `9d882e37dc0332ca0a37b71f5f6e0dab32f47faa8da92c1ed6fc3b138506f249`; backup `/tmp/viptv-qa-before-home-layers.apk`.

Latest built APKs **not installed**:

- QA `b2688d5642b2e839fa07e69f5898067cd40d0f32605626aa5bc1da9b7c7cb362`.
- Release `5cb9a3e7d2b7ac4ec2ba21a274805b24bd9f406d6c6ca569005f99178ede2f73`.

Prepared backend/gateway candidate, **not active**:
`/home/vynxc/.local/state/viptv-qa/candidates/backend-7dde035920f3_gateway-8deb493c5d42/`.
Contains immutable binaries, READY/preparation records, candidate/rollback manifests, and activation/rollback instructions. API SHA `a543dab58e35c1696a87d6ea1671b3f3e20107f5faa75ba986399c908853ad8c`; gateway SHA `9796d09a0625c52af0552eabc084053226c84367032711a46d573cbc829329f2`. Do not confuse preparation with deployment or the normal isolated server binary with the QA entrypoint.

## Last completed implementation and evidence

**KMP account dashboard:** Browser Account → Devices uses supplied W3Account geometry, one account-device RPC on entry; add-on config loads only when selected (three calls). Native account-linking explanations skip unnecessary Simkl calls. Phone Playback/Subtitles open actual preference forms. Friendly native login names omit serial/hostname.

Account device transport/controller/UI supports current browser protection, single/all sign-out, stale data removal on parent refusal, retry, generation guards, and duplicate-submit guards. Only truthful creation date/device-name data is shown. Provisional confirmation/loading/error/narrow states are GAP-058.

Last gate: **91 desktop checks, zero failures**, Android QA/release, production Wasm and Linux distribution passed; packaged JVM/SQLite and official FFmpeg packaging verified. Real Chromium against a fresh normal Rust HTTPS server verified login/profile/device flow, zero JS errors, one devices request, one mutation on confirmed sign-out despite double-click, canceled confirmation zero mutations, removed native tokens401, another account unaffected, approved pending pairing canceled by all-sign-out, saved title/progress retained. Parent policy was changed only in disposable SQLite. Evidence committed under `app/docs/evidence/browser-account-*`.

**Backend:** 438 Live/SDK tests passed, ten ignored. Latest account-device API: GET `/api/v3/account/devices`, DELETE `/:id`, DELETE collection. Account-scoped; current browser cannot individually revoke itself; parent restrictions and cookie CSRF/origin enforced; all-signout revokes approved pending pairing too, preserves profile/library/progress. See `docs/ACCOUNT_DEVICES_V3.md`.

Previous backend commits: `6ff4e6b` closes stale authority during legacy auth mutations/device approval; `3de59b3` scopes Simkl pause identity by category and resolves anime episode mappings only when provider assertions agree uniquely. No ambiguous identity egress.

**Core:** provider multiselect discovery contracts, maximum selections, typed arrays, required/cap/owner enforcement, escaped SDK values and friendly connection names. 62 engine checks;39 native checks/three ignored. Linux and four Android ABI release libs rebuilt; UniFFI byte-identical. Linux library SHA `a9c81fc3d400118cdfbe2f963a49b4293800fddbe2c5ed3ae1a56b8adb9d905c`.

**TV-web:**337 checks/type/build, three real Chromium discovery fixtures. Host discovery controls, scoped friendly catalogs, catalog-less aggregate, array query values, draft/Apply/Cancel/focus return, 40-choice virtualization. DTO at core47b; older-compatible WASM remains4f461e8 intentionally. Physical TVs not qualified.

**Roku:**73 checks/compile/public ZIP. Host discovery controls and native multiselect draft/navigation; opaque M3U IDs, managed SRT, automatic episodes (off-device One Piece1184). Public ZIP `/tmp/viptv-roku-discovery-artifacts/viptv-roku-public.zip`, SHA `f9f5b37babe0d59e3fb59e7a1735a96163c144ce92898572f389176443de8519`. Physical Roku unavailable.

**Gateway:**255 all-feature checks/59 ignored;230 default/59 ignored. Audio ordinals mapped to absolute FFmpeg streams; manual choice wins; seven actual tone cases verified440/880Hz. Live torrent feature remains disabled.

Physical playback evidence is narrower than full completion: fixed1080p60 test yielded~60fps/zero drops, AAC FFmpeg and AVC hardware decoder; real24fps Ted Lasso source yielded24fps/zero drops. Source cadence is not a60fps failure. UI still has measurable jank; later Home median21ms/p9532ms, Details median20ms/p9532ms. Full focus/animation/seek/buffering/audio-sync acceptance is not established.

## Immediate unfinished task: browser TV approval — IMPLEMENTED & VERIFIED (2026-10-14, uncommitted)

A follow-up session implemented and verified the flow end-to-end. All source changes are currently **stashed/moved out of the app tree** to repair a live-dist overwrite (details below); restore them with:

- `git stash pop` in `app` (restores `Main.kt` + `BrowserTransport.kt` mods).
- Copy the 5 new files back from `/tmp/junie/session-261014-174750-1j1n/device-approval-src/stash/`: `connection/DeviceApprovalApi.kt`, `features/entry/DeviceApprovalPage.kt`, `DeviceCard.kt`, `DeviceCodeInput.kt`, `state/DeviceApprovalController.kt`.

Backend (committed in `.worktrees/kmp-v2/backend`, uncommitted-to-remote): `/auth/device/deny` route registered in `middleware.rs` (was 404); deny now enforces liveness (`expires>now`, 400 "Invalid or expired pairing code" for expired/consumed/unknown) and ownership (403 if a different account denies an approved pairing); pending codes deniable by any authenticated browser user. 3 new tests in `auth/tests/device_deny.rs`; full suite 431 passed / 0 failed / 8 pre-existing ignores. The deny fix is in the working tree — verify with `git -C .worktrees/kmp-v2/backend status` and commit it.

KMP browser flow: `/device` route in wasm Main (pathname + `?code=` prefill), `DeviceApprovalController` with account-only auth (login/lookup/approve/deny + restore via `/api/auth/status` → `/api/auth/me`), `DeviceApprovalApi` parsing, W3Device-styled page family (card 520px/r18/p40, 56×64 code slots, cream primary, Deny as text link, status chip on confirm, Canvas check + "Back to home" on done). JS engine deliberately omits Ktor `HttpCookies` so the HttpOnly session cookie flows via the browser's fetch cookie jar.

Verification (isolated stack: Caddy :8444, server :18181, own SQLite, own dist copy): approve flow DB-verified (correct account owns pairing), deny flow verified (pairing removed), session-restore verified (status+me only), network logs show zero `/home|/v3/meta|/catalog|/profiles` requests in all paths. Real Chromium pixel-driven automation; screenshots match W3Device boards. Custom `omni-dashscope-intl-qwen3-8-max` agent is NOT available in this environment (only `junie-cli-docs`); visual comparison was done by the primary agent directly.

**Known incident (repaired):** two Gradle builds wrote into `app/composeApp/build/dist/wasmJs/productionExecutable`, which the live TV QA Caddy serves at `/kmp/`. Repair: sources restored to checkpoint, checkpoint bundle rebuilt into the live dist (verified 200, `composeApp.js` served, TV api/gateway 200, target active, PIDs unchanged). Any future isolated build MUST redirect wasm output (see harness note below). Do not rebuild into the live dist.

Design deviations, tracked: codes are 8 digits (backend contract) vs boards' letters; no browser-side profile picker (TV picks profile after private token exchange, per product decision); no fabricated profile/location/platform fields on confirm.

Suggested next implementation:

1. ~~Inspect auth routes, fix denial gaps~~ — done this session (see above).
2. ~~Wasm `/device` route, account-only auth, explicit approval~~ — done this session (see above).
3. ~~Confirm exact parent unlock contract~~ — done (next session, 2026-10-14 evening): verified from code + integration tests + live isolated-stack probes. Contract: `POST /api/v3/parent/unlock {"pin"}` (4–8 digits) → 200 `{"unlocked":true}` with a one-shot 120s session grant in `parent_grants`; wrong PIN → 403; 5 failures → 429 lockout 300s; `GET /api/v3/parent/status` → `{pin_configured,unlocked,restricted}`; `PUT /api/v3/parent/pin` (household-manager/parent, `current_pin` to rotate, empty pin removes subject to no active kids profiles). Selecting a profile (`POST /api/auth/profile`) consumes the grant (`switch_profile` requires parent when restricted, then deletes the grant). KMP remote path `ControllerProfiles.submitProfilePin` → `unlockParent(pin)` → `selectProfile` sequences the contract correctly and the wrong-PIN throw precedes the select. No W3 browser board exists for parent PIN entry (only M3Pin mobile-profile and Tv3Pin TV boards) — design state tracked as missing; do not fabricate a design when building the browser PIN form.
4. ~~Compact/mobile layout (`W3DeviceMobile`)~~ — done (same session): compact column now honors the board — edge-to-edge (no 520px card), 24px side padding, 96px top padding, 26sp/700 title, 34×39 r10 code slots with 6dp gaps, full-width primary, and the `Signed in as {name} - Not you?` footnote wired to `controller.signOut` (POST `/api/auth/logout`, best-effort) returning to the sign-in form. Verified in real Chromium at 390×844 on the isolated stack: full approve flow, `?code=` prefill, footnote sign-out (logout request observed), account-only network contract (zero Home/catalog/profiles/metadata), DB pairing approved by correct account. Design deviations vs W3DeviceMobile: shared 64dp TV tile (board uses same), 15sp subtitle shared with desktop (board 13sp subtitle — noted), digit codes vs board letters, ASCII hyphen instead of en-dash (wasm glyph), no simulated browser chrome (decorative in board). Two wasm-renderer quirks documented in `DeviceCard.kt`: `fillMaxSize` Column and `verticalScroll` clip trailing children — the compact column wraps content height inside a top-aligned Box instead.
5. Commit the app + backend changes on `experiment/kmp-v2` (user must authorize commits; this session was not authorized to commit).
6. Remaining frontend polish tracked in the page's design-deviation list; no fabricated fields.
7. SECOND DIST INCIDENT (2026-10-14 evening session, fully repaired): the compact-layout verification builds again wrote to the default Gradle output (`app/composeApp/build/dist/wasmJs/productionExecutable`), which the TV QA Caddy (`/tmp/viptv-real-provider-5rqa1vxb/Caddyfile`, site `https://viptv.local.test:18445`, `handle_path /kmp/*`) serves to the physical TV. Repair completed same session: working changes preserved (5 untracked source files + 2 modified wasm sources saved to the session dir), repo stashed, checkpoint (HEAD `dfa830b`, code-identical to pre-session) rebuilt with the redirect init-script into `/tmp/kmp-browser-261014-restore/dist` — output produced byte-identical wasm hashes (`120abf5971543e796db7.wasm`, `038b81db9c40989b0001.wasm`) to the pre-session 09:14 bundle, proving the rebuild is the exact checkpoint content — then copied into the live dist, stale build artifact removed, and serving verified through the real QA gateway (`200` for index/js/both wasm). Isolated harness stack (own Caddy :8444, own server, own SQLite) stopped after verification. **NEVER build wasm without `VIPTV_WEB_OUTPUT=/tmp/kmp-browser-<id>/dist` + `--init-script /tmp/viptv-isolated-web.gradle`; the default output directory is the TV's live dist.**
8. Actual code grammar: eight decimal digits (this session: digits-only filter, leading zeros preserved) or legacy ten hex characters. Old consonant-only copy conflicts and is documented as superseded.
9. Lookup returns code, device_name/status and expiry; approval returns approved/account_id; the TV selects profile after private-token exchange — all respected by the implementation.
10. Boards applied: `W3Device`, `W3DeviceConfirm`, `W3DeviceDone` (desktop). `W3DeviceMobile` compact layout is item 4 above. Fonts per existing app theme.

## Isolated browser harness and build procedure

Previous disposable root `/tmp/kmp-browser-hwpkzmjj` retained mode0700. **Its API18199/HTTPS18457 were stopped.** `processes.json` is historical, not liveness proof. `start.py` asserts a new DB; don't rerun unchanged or delete old fixture. `account-flow.mjs` and seed/proxy scripts are reusable references. `fixture.json`, state DB/keyring and credentials are private—never print or commit.

Real tests used ordinary Rust server build7dde, not mocked API responses. Normal release server binary is `backend/server/target/release/viptv-server`; build features `live-runtime,vsources-runtime`, locked/jobs2/incremental0. Create a new private CWD/SQLite and sanitized environment (strip inherited provider/secret variables). `VIPTV_SECRETS_KEYRING` takes JSON contents, not filename. Pin one exact trusted HTTPS origin. Set `OBSERVABILITY_ENABLED=false`. No real providers required for account tests.

**Do not overwrite live `app/composeApp/build/dist/wasmJs/productionExecutable`.** Use retained `/tmp/viptv-isolated-web.gradle` to redirect Wasm Gradle Sync.destinationDir with `VIPTV_WEB_OUTPUT=/tmp/kmp-browser-NEW/dist`. Existing harness verified all109 live files remained byte-identical. Proxy must add SPA routing for `/device` if testing that route.

JDK `/tmp/viptv-temurin21/jdk-21.0.12.1+1`. App packaging skips separately pinned native engine rebuilds with `-x :composeApp:buildDesktopEngine -x :androidApp:buildAndroidEngine`. Check app build-evidence/native provenance before changing core pins.

Compose browser accessibility mirror is behind canvas: role `.click()` can be intercepted and `.fill()` may not update underlying password state. Use locator boundingBox → actual mouse click → wait150ms → keyboard.type with30–35ms delay. Popup labels may duplicate; identify correct latest dialog. Launch real Chromium `/opt/google/chrome/chrome` with fresh context. Node API requests need `--use-system-ca`, never ignore HTTPS errors. Ports do not isolate hostname-scoped cookies; use fresh contexts and synthetic accounts.

Image tool sometimes displays delta-only unchanged pixels as black/missing despite full opaque source PNG. Inspect pixel hashes/source images before diagnosing rendering bugs; no speculative rendering workarounds. `/tmp/viptv-iptv-header-paint-proof.json` records a disproven heading issue.

## Remaining scope and operational constraints

Browser signup/approval/parent administration, complete KMP dashboard, all197-board fidelity, Solid TV-web/Roku design migration, latest physical TV responsiveness, native desktop and mobile platform playback, Windows/macOS latest GUI, long-running seek/buffer/subtitle/live/formats and real anime mapping acceptance remain incomplete. Current green tests do not prove those requirements.

Recent GitHub Actions runs cannot start due billing/spending limit; user already informed. Do not rerun pointlessly or claim latest Windows/macOS qualification. Independent implementation remains available.

Personal generic video plugin already moved to `https://github.com/vynxc/tauri-plugin-video`, source3443319, package `@vynxc/video-tauri`; it is not a KMP dependency. Historical repositories/main/refs are retained.

Respect current user AGENTS: Context7 CLI library resolution then docs for library/API-specific work, maximum three commands per question, no sensitive queries. User specifies subagents GPT-6.1 Sol/high explicitly; don't revive prior Astra agents. Backend delegation was authorized, but list/revalidate retained-agent settings before dispatch; no active child remains now. Avoid spawning unrelated agents without authorization. No blanket tests, speculative rewrites, or unreviewable large files.

Next agent should first read this file, inspect repository state and live health, then resume the unfinished browser TV approval contracts and implementation on the experimental branch. Preserve the TV testing session until the user confirms it is over.
