# Non-TV settings checkpoint — 2026-10-13

The supplied M3Settings/M3SetAddons/M3AddAddon/M3SignOut, T3Settings and
D3SetAddons boards guide the settings list, two-pane layout, add-on rows,
phone installation sheet and leave confirmation. This is an implementation
checkpoint, not a parity certification. The sidebar and account panel remain
scrollable/usable within the current app chrome; unsupported preference sections
say they are unavailable. There is no invented application version, manifest
validation preview, title count, catalog count, account email or remote-pairing
status. Icons come from the existing Lucide library.

Local install/toggle/remove uses the existing LocalConnection contract. Add-ons
are installed on the device, toggles apply to the selected profile, and removal
requires confirmation describing its device-wide scope. Remote management uses
existing server routes: paginated GET/POST `/api/v2/addons`, DELETE
`/api/v2/addons/:id`, and GET/PUT `/api/v3/profiles/:id/integrations`.
Remote profile toggles preserve other current assignments, including non-addon
integrations, and send the observed configuration revision. They never PATCH the
account-wide enabled flag. Account-disabled add-ons remain listed and cannot be
enabled through a profile switch. Unknown capabilities remain unknown.

The server enforces account ownership, selected-profile scope and parent access.
The backend owner aligned profile-configuration device policy with its existing
account-shared settings policy and reports seven focused backend tests passing,
including selected-profile success, other-profile rejection and restricted-parent
rejection. Full remote native runtime mutation is still unverified here. Browser parent
sessions already use the account routes. Client transport continues through
RemoteConnection.call, preserving cookie/CSRF and native token refresh behavior.

Opening settings reads the inventory. Selecting a settings section, opening the
install sheet or typing its URL makes no provider request. Installation submits
only on Install. The form retains input and displays errors; it does not claim
validation or success before a server/engine result. A successful mutation
refreshes inventory and Home. Sign-out confirmation calls the existing revoking
signOut flow; local Leave disconnects without deleting profiles or progress.

Isolated preview routes: `#ui-preview/Settings`, `/Settings/Addons`,
`/Settings/RemoteAddons`, `/Settings/SignOut` (all prefixed `#ui-preview`).
Fixture switches/removal affect in-memory fixture data only. Fixture installation
explicitly reports that it cannot install; it never contacts an add-on or account.

RemoteAddonsTest checks profile-versus-account mutation mapping, revision and
unrelated-assignment preservation, install activation, pagination, disabled
account inventory and invalid removal IDs. Coordinated Desktop/Wasm compilation and both RemoteAddonsTest cases passed
(`/tmp/viptv-tv-coordinated-clients-build.log`); the final UI-only Desktop/Wasm
recompile also passed (`/tmp/viptv-tv-coordinated-clients-final-build.log`). Browser pointer QA passed fixture
profile toggles, remove cancellation/confirmation, sign-out cancellation and
confirmation to Connect, and the phone installation sheet (blank input disables
Install). There were no page errors, failed requests, or API calls in the isolated
preview. Evidence is in `/tmp/viptv-nontv-browser-qa.Nq7fv3/final-result.log` and
its `settings-*.png` screenshots. These fixture interactions do not certify a live
account install, backend persistence, sign-out revocation, or cross-device sync.

Design gaps for the shared ledger: no W3 public QR entry route, no pre-install
manifest preview/version/count contract, no non-TV persistent playback/subtitle
preferences, Simkl account panel or keyboard preference screen. The install form
uses the supplied layout without those unsupported claims. Removal confirmation,
error states and account-disabled rows are provisional extensions of the ZIP.

## Disposable backend verification — 2026-10-13

The browser UI also ran against the backend owner's real cfg(test) server on
127.0.0.1:18183 behind trusted HTTPS18443. It used a temporary SQLite database,
disposable parent account and test-only loopback manifest endpoint. No production
account, database or outbound-network policy was changed. The live browser flow:

- Installed from the URL field and verified persisted account inventory plus the
  selected profile's enabled assignment.
- Turned the profile switch off and verified that the account add-on remained
  enabled; a fresh browser page restored the disabled profile assignment.
- Cancelled removal without changing inventory, then confirmed removal and
  verified empty persisted inventory.
- Cancelled sign-out without losing authentication, then confirmed it and
  verified `/api/auth/status` reported unauthenticated.

The run had zero browser page errors. Its log is
`/tmp/viptv-nontv-browser-qa.Nq7fv3/live-result.log`; `live-installed.png`,
`live-settings-empty.png`, and `live-signed-out.png` show actual runtime states.
This covers the browser cookie/CSRF path. Native device-token UI mutation remains
outside this browser run; backend selected-device authorization tests cover its
server policy.

## URL editing verification — 2026-10-13

The Wasm install URL field uses a scoped native HTML input through
`HtmlElementView`; Android and Desktop retain the state-based Compose input.
Browser typing at 0/5/20/60 ms per character preserved the exact URL, and an IME
composition commit preserved a Japanese hostname. The phone sheet also preserved
Unicode and percent-encoded query text and dismissed with Escape while the input
was focused. Typing made no API requests. The final disposable-backend run
submitted a rapidly typed URL exactly once, unchanged, and repeated the install,
profile-toggle persistence, removal and revoking sign-out checks without page
errors. Evidence: `typing-native.log`, `native-sheet-final-result.log`, and
`live-native-final-result.log` in the same temporary evidence directory.

The DPR2 screenshot with 125% browser pinch scaling was visually inspected: the
input remains beside Install. This is not an ordinary browser page-zoom check.
A synthetic `document.body.style.zoom = 1.25` run displaced the HTML overlay even
though semantic bounds assertions passed; its visual result is a failure and
must not be counted as zoom parity. Actual browser page zoom and native device
input interaction remain unverified. At the resumed inspection, ports 18443 and
18183 had no listeners; no test services were restarted.
