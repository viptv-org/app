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

## Immediate unfinished task: browser TV approval

The last turn planned this but made **no root source edits**. At handoff `collaboration.list_agents` reports only root running; no backend subagent currently exists. An earlier backend agent's proposed deny fix is **not committed/present as a new checkpoint**. Do not assume it landed.

Current Wasm Main supports previews/playback QA and ordinary App; no dedicated `/device` approval route. Browser ConnectScreen is generic, not supplied W3SignIn parity. RemoteConnection.login also bootstraps Home; inappropriate for lightweight account-only approval.

Suggested next implementation:

1. Inspect actual auth routes/contracts and fix proven denial gaps first with isolated fixtures. Earlier agent reported POST `/api/auth/device/lookup` and `/approve` accept `{user_code:string}`; canonical `/auth/device/deny` dispatcher exists but public alias may be missing. Denial may delete already-approved foreign-account codes or return success for expired/consumed records. Verify, then enforce ownership/live-code rules; do not rely only on this report.
2. Confirm exact parent unlock contract before building its form. Never guess mutation payloads or automatically retry approval after auth changes.
3. Add dedicated common browser device-link flow selected by Wasm `/device` (including served path handling). Account-only session restore/login should obtain auth/status CSRF and auth/me without metadata/Home bootstrap. Preserve existing native login behavior.
4. Lookup can follow authenticated query-code restore; approval must always be explicit, guarded against duplicate submits and stale account/session generations. Browser handles only public `user_code`, never private `device_code`.
5. Actual code grammar reported: eight decimal digits or legacy ten hex characters; trim/uppercase, preserve leading zeros. Supplied old consonant-only copy conflicts and must be adapted/documented.
6. Lookup currently returns code, device_name/status and expiry Unix-seconds-string. Approval returns approved/account_id; **TV selects profile after private-token exchange**. W3DeviceConfirm profile/location/platform fields are unsupported; don't fabricate them or send profile/account IDs. Expired/consumed/already-approved cases need truthful states.
7. Apply `W3Device`, `W3DeviceMobile`, `W3DeviceConfirm`, `W3DeviceDone` boards from design/current. Files are div-based HTML, not `<main>`. Existing fonts include Onest/Bricolage, no JetBrains Mono. Record gaps for unavailable info/extra states.

## Isolated browser harness and build procedure

Previous disposable root `/tmp/viptv-kmp-browser-hwpkzmjj` retained mode0700. **Its API18199/HTTPS18457 were stopped.** `processes.json` is historical, not liveness proof. `start.py` asserts a new DB; don't rerun unchanged or delete old fixture. `account-flow.mjs` and seed/proxy scripts are reusable references. `fixture.json`, state DB/keyring and credentials are private—never print or commit.

Real tests used ordinary Rust server build7dde, not mocked API responses. Normal release server binary is `backend/server/target/release/viptv-server`; build features `live-runtime,vsources-runtime`, locked/jobs2/incremental0. Create a new private CWD/SQLite and sanitized environment (strip inherited provider/secret variables). `VIPTV_SECRETS_KEYRING` takes JSON contents, not filename. Pin one exact trusted HTTPS origin. Set `OBSERVABILITY_ENABLED=false`. No real providers required for account tests.

**Do not overwrite live `app/composeApp/build/dist/wasmJs/productionExecutable`.** Use retained `/tmp/viptv-isolated-web.gradle` to redirect Wasm Gradle Sync.destinationDir with `VIPTV_WEB_OUTPUT=/tmp/viptv-kmp-browser-NEW/dist`. Existing harness verified all109 live files remained byte-identical. Proxy must add SPA routing for `/device` if testing that route.

JDK `/tmp/viptv-temurin21/jdk-21.0.12.1+1`. App packaging skips separately pinned native engine rebuilds with `-x :composeApp:buildDesktopEngine -x :androidApp:buildAndroidEngine`. Check app build-evidence/native provenance before changing core pins.

Compose browser accessibility mirror is behind canvas: role `.click()` can be intercepted and `.fill()` may not update underlying password state. Use locator boundingBox → actual mouse click → wait150ms → keyboard.type with30–35ms delay. Popup labels may duplicate; identify correct latest dialog. Launch real Chromium `/opt/google/chrome/chrome` with fresh context. Node API requests need `--use-system-ca`, never ignore HTTPS errors. Ports do not isolate hostname-scoped cookies; use fresh contexts and synthetic accounts.

Image tool sometimes displays delta-only unchanged pixels as black/missing despite full opaque source PNG. Inspect pixel hashes/source images before diagnosing rendering bugs; no speculative rendering workarounds. `/tmp/viptv-iptv-header-paint-proof.json` records a disproven heading issue.

## Remaining scope and operational constraints

Browser signup/approval/parent administration, complete KMP dashboard, all197-board fidelity, Solid TV-web/Roku design migration, latest physical TV responsiveness, native desktop and mobile platform playback, Windows/macOS latest GUI, long-running seek/buffer/subtitle/live/formats and real anime mapping acceptance remain incomplete. Current green tests do not prove those requirements.

Recent GitHub Actions runs cannot start due billing/spending limit; user already informed. Do not rerun pointlessly or claim latest Windows/macOS qualification. Independent implementation remains available.

Personal generic video plugin already moved to `https://github.com/vynxc/tauri-plugin-video`, source3443319, package `@vynxc/video-tauri`; it is not a KMP dependency. Historical repositories/main/refs are retained.

Respect current user AGENTS: Context7 CLI library resolution then docs for library/API-specific work, maximum three commands per question, no sensitive queries. User specifies subagents GPT-6.1 Sol/high explicitly; don't revive prior Astra agents. Backend delegation was authorized, but list/revalidate retained-agent settings before dispatch; no active child remains now. Avoid spawning unrelated agents without authorization. No blanket tests, speculative rewrites, or unreviewable large files.

Next agent should first read this file, inspect repository state and live health, then resume the unfinished browser TV approval contracts and implementation on the experimental branch. Preserve the TV testing session until the user confirms it is over.
