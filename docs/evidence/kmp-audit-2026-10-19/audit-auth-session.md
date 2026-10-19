# AUTH / SESSION AUDIT — VIPTV KMP app (AUTH-01..04)

Scope: read-only source audit. No builds, no gradle/adb/emulator, no repository edits.
Method: live working-tree sources only (`app` @ `experiment/kmp-v2`, dirty files noted), plus the
backend that actually serves the app's API surface. Important repo fact discovered during the
audit: the primary backend checkout `/mnt/ALPH/code/viptv-org/backend` (branch `experiment/simkl`,
the QA-runtime lineage) has **no** `/api/bootstrap`, `/api/meta`, `/api/profiles/:id/library`
routes; the KMP app's real API server is the sibling worktree
`/mnt/ALPH/code/viptv-org/.worktrees/kmp-v2/backend` (branch `experiment/kmp-v2`,
`engine_v3`). Auth modules are shared lineage; diffs are called out below. Server-side citations
use the kmp-v2 worktree unless marked "simkl backend".

Issue list: `app/docs/ISSUES.md:143-146` (AUTH-01..04), restated in
`app/docs/OPEN_TASKS.md:47-50`.

---

## 1. AUTH-01 — refresh-token rotation + storage per target

### Storage matrix (client)

| Target | Store | Source | Assessment |
|---|---|---|---|
| Android | AndroidKeyStore AES-256-GCM key wrapping a `SharedPreferences` file (`addon-secrets`) | `app/composeApp/src/androidMain/kotlin/org/viptv/app/connection/AndroidSecrets.kt:15-50`; wired via `nativePlayerMain/NativeRemoteSessionStore.kt:11-31`; `AndroidSecrets.attach()` before first use at `androidApp/src/main/kotlin/org/viptv/experiment/MainActivity.kt:22` | Encrypted at rest. OK. Cosmetic nit: session token lives in a prefs file/alias named for addons (`KEY_ALIAS = "viptv-experiment-addons"`, `AndroidSecrets.kt:7`). |
| Desktop | Secret Service over D-Bus, zero prompt timeout, locked collection = hard error | `app/native/core/crates/viptv-core/src/local_engine/secrets.rs:37-60`, `secrets/linux.rs:15-111`; `DesktopSecrets.kt:6` | Matches `app/docs/DESKTOP_VAULT_RECOVERY.md`. OK. |
| iOS | **NSUserDefaults (plaintext plist in the app container)** | `app/composeApp/src/iosMain/kotlin/org/viptv/app/connection/RemoteSessionStore.ios.kt:7-27` (`setObject("${origin}\n${refreshToken}")`, line 21) | **Gap.** AUTH-01 explicitly requires the iOS keychain. |
| Browser | No client-side storage; session in `viptv_session`/`viptv_refresh` cookies | `wasmJsMain/.../BrowserRemoteSessionStore.kt:3-10` (no-op); cookies at `.worktrees/kmp-v2/backend/server/src/auth/tokens.rs:242-251` (HttpOnly, Secure, `Path=/`) | OK. |

**F1 (High) — iOS refresh token is plaintext and immortal.**
`RemoteSessionStore.ios.kt:7-11` documents it as "non-secret refresh token storage acceptable for
QA/simulator builds; Keychain migration is a follow-up", but device refresh tokens are
**non-expiring**: `.worktrees/kmp-v2/backend/server/src/auth/schema.rs:7`
(`DEVICE_EXPIRY = 253_402_300_799` ≈ year 9999) vs browser refresh 30 days (`schema.rs:5`).
Concrete failure mode: any read of the app container (unencrypted iTunes backup, forensic or
jailbreak file access, a co-installed malicious app on a compromised device) yields a bearer
refresh token that mints access tokens forever until manually revoked from the account's device
list. The vault write is also not atomic with `NSUserDefaults` defaults synchronization, and the
token is newline-delimited with the origin (trivially parseable).

### Rotation mechanics (verified)

* Server rotates in place, one-use, with tombstones:
  `.worktrees/kmp-v2/backend/server/src/auth/actions.rs:273-344` — reuse detection deletes the
  session family (283-292), used-token tombstone insert (298-300), in-place hash rotation
  (334-341). Tombstones expire at 30 days; device session rows effectively never expire.
* Client single-flight rotation is careful: `refreshGate` + process-global `nativeSessionGate` +
  `refreshVersion` (`RemoteConnection.kt:29-30,44-49,326-328`); rotation runs under
  `NonCancellable` and persists the replacement **before** installing it in memory
  (`RemoteConnection.kt:260-267`, comment at 269-271). Concurrency behavior is pinned by
  `desktopTest/.../RemoteSessionConcurrencyTest.kt` (single-consume, cancel-safe persist,
  retired-connection replacement wins) and `RemoteSessionTest.kt` (rotate-on-restore).

**F2 (Medium) — process death / slow refresh mid-rotation permanently kills the session.**
`refreshDevice()` wraps the one-use rotation in `withTimeout(20_000)`
(`RemoteConnection.kt:280-281`) and handles only the 401 branch (286-294). If the server
processes the rotation but the response is slow (>20 s), dropped by a proxy, or the app is
killed/power-lost between server consumption and `persistDeviceToken`, the vault still holds the
**consumed** token. The next refresh replays it, the server answers "Refresh token reuse
detected" and deletes the session family (`actions.rs:283-292`), and the user is signed out
("Session expired. Sign in again."). There is no server-side grace window and no client retry
state: a slow-but-successful rotation is indistinguishable from theft. The in-process mitigations
(`NonCancellable`, persist-before-install) cover cancellation, not process death or the 20 s
budget.

**F3 (Medium) — two processes sharing one vault revoke each other.**
`nativeSessionGate` (`RemoteConnection.kt:29-30`) is per-process. Two simultaneously running
desktop instances (double launch) or two installs sharing a copied vault both restore the same
token; the first 401-triggered rotation consumes it, the second instance's later refresh replays
the old token → reuse detection deletes the family → **both** instances are signed out with a
"Session expired" message. Nothing (file lock, lease, cross-process marker) serializes vault
access across processes.

**F4 (Low) — rotation with an unavailable vault guarantees post-restart loss, only warned.**
`persistDeviceToken` (`RemoteConnection.kt:268-279`) swallows vault failures into
`persistenceWarning` ("This device will need to sign in again after the app closes") and keeps
the in-memory session. Correct and honest while running, but on restart the vault holds the
already-consumed token → reuse → session revoked (see F2). The failure is surfaced in Settings
via `sessionNotice` (`ControllerSession.kt:179`, `SettingsScreen`), not at the moment of
rotation.

**F5 (Low) — pairing adoption window orphanes a server session.**
TV pairing adopts tokens with `persist = false` (`RemoteConnection.kt:59`) and persists only
after a successful `bootstrap()` (`:62`). If bootstrap fails (network) or the user cancels in
between (`adopt` throws `CancellationException` at `:63`), the pairing row was already consumed
at `/auth/device/token` (`device_actions.rs:118-131`) — an orphaned device session remains in the
account's device list until manually revoked, and the TV must re-pair.

*Positive note:* `SavedRemoteSession.toString()` redacts the token
(`RemoteSessionStore.kt:5-7`) — no log leakage of credentials.

### Cookie hardening drift (context, browser only)

The app backend keeps **both** cookies `SameSite=Strict`
(`.worktrees/kmp-v2/.../tokens.rs:242-251`); the `experiment/simkl` backend relaxes
`viptv_session` to `Lax` for OAuth top-level returns (`backend/server/src/auth/tokens.rs:244-251`,
comment at 243-245). This is branch drift to reconcile at merge; on kmp-v2 the Simkl callback
sits outside `authenticate` (`routes.rs:317-320`) so Strict is currently harmless there.

---

## 2. AUTH-02 — 401 refresh on every transport; clean sign-out

### Verified working

* **Every JSON transport refreshes on 401.** All data-plane calls funnel through
  `authenticatedResponse` (`RemoteConnection.kt:206-223`): `call` (193), `accountCall` (198),
  `subtitleContent` (187). Enumerated consumers (RemoteAddons, RemoteSources, RemoteDiscovery,
  RemoteHistory, RemoteLibrary, RemoteSubtitles, RemoteSourceIntegrations, RemotePlaybackEvents,
  RemotePlayback lease calls, RemoteProfiles incl. `unlockParent`) all use `call`.
  Exclusions are correct: login/device-login/logout (line 211). Images are public artwork served
  outside the authenticated router (`kmp-v2 routes.rs:310-311`, `images::get` sets
  `Cache-Control: public`) and carry no session, so there is no 401 surface there. Media streams
  are lease-scoped gateway URLs, not session tokens.
* **Single-flight retry**: parallel 401s consume the token once (`refreshVersion` check at
  214-216); retry once, then `checkStatus` maps 401 → "Sign in to continue."
  (`RemoteConnection.kt:244-256`).
* **Clean sign-out on 401** for run()-tracked operations: `ApplicationController.run` catches
  `RemoteFailure(401)`, releases playback, closes the connection and resets state to
  `ApplicationState(error = "Session expired. Sign in again.")` on the Connect screen
  (`ApplicationController.kt:63-69`). Native restore handles 401 identically
  (`ControllerSession.kt:40-52`) and vault failure with a message (`:15-23`).
* **Sign-out flow**: `RemoteConnection.signOut` (`:306-324`) rotates first, then revokes via the
  fresh token, tolerates 401, clears the vault and memory; `ControllerSession.signOut`
  (`:148-165`) joins progress/playback first and only resets state if the connection wasn't
  replaced. The leave dialog shows busy + error inline (`SettingsLeaveDialog.kt:13-24`) with copy
  matching the `Tv3SignOut` design board (dialog "Sign out of this TV? Your profiles, My List and
  progress stay on your account."). Network failure during sign-out leaves the session intact
  with an error message in the dialog — retryable, no silent loss.

### Failure modes

**F6 (Medium) — background transports swallow 401s instead of signing out.**
Three background jobs catch *all* exceptions, including `RemoteFailure(401)`:
* library hydration: `ControllerSession.kt:174` (`catch (_: Exception)` — comment claims "the
  library route offers an explicit retry");
* subtitle discovery: `ControllerPlayback.kt:82` ("Subtitle discovery cannot interrupt media
  playback");
* preferences: `ControllerPreferences.kt:17-21` rewrites a 401 as
  `"Could not load profile preferences. Try again."`.

Concrete failure mode: the session dies mid-browse (server restart, family revoked from another
device); the library silently stays empty, subtitles silently absent, and the Settings retry
button loops on "Could not load profile preferences" forever — the dead session is never
reported or cleaned until some *interactive* call happens to 401. AUTH-02's "sign out cleanly
with a message" only holds for the `run()`-tracked surface. Live favorites do this correctly by
contrast (`ControllerLiveFavorites.kt:22,55` rethrow 401).

**F7 (Low) — playback lease expiry mislabels a dead session.**
A 401 during lease renewal lands in `catch (_: Exception)` →
"The server playback session ended. Choose the source again." and returns to Sources
(`ControllerPlayback.kt:60-66`). A message is shown (no blank screen), but the root cause (dead
session) is mislabeled; the subsequent sources call 401s and only then signs out.

**F8 (Low) — signOut failure path leaves a partially-clean state on non-401 errors.**
If `RemoteConnection.signOut` throws a non-401 `RemoteFailure` (e.g., 429 rate-limited refresh,
`endpoint_global_limit`), the catch at `:319-321` rethrows **before** `sessionStore.clear()`
(`:317`): tokens and vault survive, the leave dialog shows the error — correct — but note the
already-rotated refresh token is persisted only if rotation completed; a rotation-abort here
re-enters the F2 window.

---

## 3. AUTH-03 — session survives restart; restore never flashes login

### Verified working

* `ApplicationState(starting = true)` is the initial state
  (`ApplicationController.kt:20`); `App.kt:51-58` renders `TvSplash` (TV) or a plain background
  box (comment: "never flash the login screen while the browser session is being validated")
  until restore finishes. `run()`'s `finally` clears `starting` only after the operation
  completes (`ApplicationController.kt:77`). No login flash by construction on all platforms.
* Android: `ApplicationModel.start()` → `controller.restore(...)` once per ViewModel
  (`androidApp/.../ApplicationModel.kt:17-21`), `MainActivity.kt:37-38`; activity recreation
  keeps the controller (documented at `ApplicationModel.kt:3`).
* Browser: `restoreBrowser()` proactively rotates before bootstrap so restores never start with
  expired credentials (`RemoteConnection.kt:126-135`, pinned by
  `RemoteSessionTest.kt:76-93`); restore failures converge to the clean 401 path.
* Native restore re-reads the vault under the global lock and validates the origin
  (`RemoteConnection.kt:102-122`); startup-snapshot/rotation races are pinned by
  `StartupRefreshHandoffTest.kt` and `StartupShellUiTest.kt` (safe starting screen hides cached
  private content, line 23).
* Desktop vault-locked/missing-bus behavior is documented with real fixtures
  (`app/docs/DESKTOP_VAULT_RECOVERY.md`).

### Failure modes

**F9 (High) — iOS cannot sign in at all: the sign-in screen is structurally dead.**
`Main.ios.kt:7` passes `App(browser = true)`, but `browserOrigin()` returns `null` on iOS
(`iosMain/.../Transport.ios.kt:18`). In `ConnectScreen` the browser fallback branch is selected
(television/phone/desktop conditions all false with `browser=true`,
`ConnectScreen.kt:29-36`), the server-address field is hidden (`if (!browser)`, `:47`), `server`
initializes to `browserOrigin() ?: ""` from `App.kt:55` → empty, and the Sign-in button requires
`server.isNotBlank()` (`ConnectScreen.kt:58`) — permanently disabled. The branch also shows
"Create account"/"Use recovery code" (`:54-56`), which `RemoteConnection` rejects because
`accountManagementEnabled` is false in native mode
(`ControllerAccountEntry.kt:23-24` → "Create or recover accounts through your server's web
sign-in page."), and it renders no device-pairing UI (pairing lives only in the
Tv/Mobile/Desktop sign-in branches). So on iOS: no origin entry, no pairing, no account creation,
sign-in disabled. `REMAINING_KMP_WORK.md:30` marks iOS runtime "open", but as the tree stands
AUTH-03 (and AUTH-01/02/04 on iOS) is untestable because no session can be created. Either the
iOS shell must inject an origin (e.g., a settings-provided `browserOrigin()` actual) or
`App(browser=false)` with a phone-style sign-in.

**F10 (Medium) — a single offline boot strands the user on the login screen.**
Native restore: a non-401 failure (server unreachable, timeout) rethrows
(`ControllerSession.kt:41-43`) → `run()` catch → "Could not reach your server. Check the
connection and try again." rendered on `Screen.Connect`. The vault still holds a *valid* token
(session survives restart), but the Connect screen offers only credential re-entry or pairing —
there is no "retry the saved connection" affordance, so a transient network blip at startup
forces the user into the sign-in UI (and a manual app restart to retry). A message is shown
(AUTH-02 letter satisfied), but AUTH-03's intent (restore "just works") fails on first offline
boot.

**F11 (Low, cosmetic) — browser restore shows an undecorated blank box** instead of the
designed splash (`App.kt:55-58`); TV gets `TvSplash` (`App.kt:54`). Deliberate per the comment;
not a login flash.

---

## 4. AUTH-04 — multi-profile token/preference scoping

### Verified working (server)

* `/auth/profile` requires the account to own the target profile
  (`Principal::require_profile` → `profile_owners`, `auth/principal.rs:71-79`), runs the kids
  PIN/policy gate (`kids::switch_profile`), then re-scopes the session row:
  `UPDATE auth_sessions SET profile_id=?1 WHERE access_hash=?2`
  (`.worktrees/kmp-v2/.../profiles_actions.rs:208-220`).
* Device-role principals may call only `/auth/profile` and `/auth/logout`
  (`kmp-v2/.../auth/actions.rs:166-171`).
* Resource middleware enforces per-session profile scoping: media routes require a selected
  profile; `profiles/:id/{favorites,progress,continue,preferences}` run `require_profile`
  (`http_middleware.rs:107-135`), and every engine_v3 handler re-checks
  (`require_profile` in `engine_v3/history.rs:156,226`, `library.rs:66`, `home.rs:34`,
  `configuration.rs:47,97,205`, `controls.rs:97`). `App::require_profile` is strict:
  session `profile_id` must equal the path profile (`app_state.rs:361-372`).
* The kmp-v2 backend additionally revalidates the captured family + exact profile before any
  auth mutation (`Principal::validate_scope`, `actions.rs:112-117`,
  `principal.rs:34-56`) — a switched/revoked family cannot authorize a stale request.

### Verified working (client)

* `openProfile` joins in-flight progress saves and releases remote playback before switching
  (`ControllerProfiles.kt:26-33`); `accept()` installs a **fresh** `ApplicationState`
  (`ControllerSession.kt:166-186`), so detail/search/sources/history/live/player state from the
  previous profile is discarded, not merged.
* Per-profile guards on background writes: library hydration
  (`selectedProfile == profile && connection === remote`, `ControllerSession.kt:175-179`); live
  favorites (`ownsLive` scope+profile+revision, `ControllerLive.kt:11-16`,
  `ControllerLiveFavorites.kt:7-12`); next-episode keyed by profile+input with a `current()`
  write guard (`ControllerNextEpisode.kt:20-34,74`); guide TTL cache cleared on reset
  (`ControllerLive.kt:125-128`); search UI state keyed per profile
  (`App.kt` `SaveableStateProvider("${state.selectedProfile}:search")`).
* Preferences are profile-scoped server-side with optimistic revision
  (`ControllerPreferences.kt:7-30` via `/api/profiles/:id/preferences`), and profile mutations
  reload the profile list. Deleting the selected profile resets to Profiles
  (`ControllerProfiles.kt:52-60`).

### Failure modes / structural notes

**F12 (Medium, by design but worth an owner decision) — profile scope is mutable session state,
not a token re-scope.** Switching profiles does not re-issue or re-bind credentials: the same
bearer token / cookie pair simply addresses whichever profile was last selected
(`profiles_actions.rs:216-219`). Consequences: (a) multiple browser tabs share one session row,
so a profile switch in one tab silently re-scopes the other tab's next request — the other tab's
UI keeps showing the old profile until it refreshes (client state desync, no local staleness
detection); (b) a stolen device token can be pointed at any profile the account owns by POSTing
`/auth/profile` (kids PIN gates are the only brake, `kids::switch_profile`). If profiles are
meant to be isolation boundaries, the switch should mint a profile-bound token (or the client
should re-bootstrap on focus), not mutate shared session state.

**F13 (Low) — client-side preferences write has no profile-ownership guard.**
`loadPreferences` captures `profile` at start and writes `mutable.value.copy(preferences =
snapshot)` on completion (`ControllerPreferences.kt:7-15`). Cancellation via `run()`'s prior-job
join covers the common path (`refreshPreferences` is a `run`), and `openProfile` is also a
`run`, so in practice a stale load is cancelled before a switch completes; but unlike
library/live/next-episode there is no explicit `selectedProfile == profile` check before the
write, so any future non-`run` caller of `loadPreferences` can write profile A's preferences
into profile B's UI. (Same pattern, same low risk, in `updatePreferences`.)

**F14 (Low) — kids-restricted catalogs rely on server policy + client trust.** The client shows
`profile.locked` and gates via `Screen.ProfilePin` (`ControllerProfiles.kt:11-21`), but the
client never re-derives `restricted`; it trusts the bootstrap's filtered content. Server-side
`kids::scoped` filtering (`engine_v3/catalog.rs:234,387-388`) is the real boundary — acceptable,
but note that the PIN gate's `unlockParent` (`RemoteProfiles.kt:39-41`) is a session-level
unlock with no client-side expiry; a session unlocked for a kids profile stays unlocked for the
connection's lifetime unless the server expires it.

---

## 5. Consolidated severity list

| ID | Issue | Where | Severity |
|---|---|---|---|
| F1 | iOS refresh token in plaintext NSUserDefaults; token is non-expiring | `RemoteSessionStore.ios.kt:21`, `schema.rs:7` | High |
| F9 | iOS sign-in screen unusable (no origin input, disabled button, no pairing) | `Main.ios.kt:7`, `Transport.ios.kt:18`, `ConnectScreen.kt:36-58` | High |
| F2 | Crash / >20 s refresh timeout mid-rotation → reuse detection → permanent forced sign-out | `RemoteConnection.kt:280-296`, `actions.rs:283-292` | Medium |
| F3 | Concurrent processes sharing one vault revoke each other's session family | `RemoteConnection.kt:29-30` | Medium |
| F6 | Background transports (library hydration, subtitle discovery, preferences) swallow 401s; dead session looks like a load failure | `ControllerSession.kt:174`, `ControllerPlayback.kt:82`, `ControllerPreferences.kt:17-21` | Medium |
| F10 | Offline boot at restart strands user on login screen; no saved-session retry affordance | `ControllerSession.kt:41-43` | Medium |
| F12 | Profile scope is mutable session state (shared across tabs; token not re-scoped) | `profiles_actions.rs:216-219` | Medium (design) |
| F4 | Rotation with unavailable vault = guaranteed post-restart loss (warned only) | `RemoteConnection.kt:268-279` | Low |
| F5 | Pairing adopt window orphanes server session | `RemoteConnection.kt:59-63` | Low |
| F7 | Lease-renew 401 mislabeled as playback-session end | `ControllerPlayback.kt:60-66` | Low |
| F8 | signOut non-401 failure re-enters rotation-loss window | `RemoteConnection.kt:306-324` | Low |
| F13 | Preferences write lacks explicit profile-ownership guard | `ControllerPreferences.kt:7-15` | Low |
| F14 | Parent unlock has no client-side expiry | `RemoteProfiles.kt:39-41` | Low |

## 6. What is solid (keep)

* Single-flight, cancel-safe, persist-before-install device rotation with concurrency tests.
* 401 → single refresh → single retry on the entire JSON API surface, with correct endpoint
  exclusions; media/images are out of the session surface by design.
* Clean 401 sign-out with a user-visible message on the run()-tracked surface; rotate-then-revoke
  sign-out with 401 tolerance; sign-out dialog copy matches the `Tv3SignOut` board.
* Splash/blank starting state guarantees no login flash on Android/desktop/browser/TV.
* Strict server-side profile enforcement (`require_profile` everywhere + `validate_scope` on
  kmp-v2) and a full client state reset on profile switch.
* `SavedRemoteSession.toString()` redaction; HTTPS-only origin pinning
  (`RemoteOrigin.kt:9-19`); cookie hardening (HttpOnly/Secure/Strict on the app backend).

## 7. Recommended fix order

1. iOS: migrate the session store to the Keychain and fix `Main.ios.kt:7` / `browserOrigin()`
   so the origin can be entered (F1, F9) — both block iOS AUTH qualification outright.
2. Make background transports distinguish 401 (F6) — route it through the same clean sign-out as
   `run()`, and reword the preferences error for expired sessions.
3. Add a saved-session retry affordance on Connect for transient restore failures (F10).
4. Owner decision on F12 (profile-bound tokens vs shared session scope), then document it.
5. Harden F2/F3: raise/justify the 20 s refresh budget, add a client-side "rotation in flight"
   marker persisted with the old token so a restart can detect the consumed-token case and
   explain it, and add a cross-process vault lock (or refuse double launch) on desktop.
