# Account and source-integration recovery — 2026-10-15

Source-integration loads now let an expired session reach the existing central
session-retirement path. Previously the loader caught HTTP 401 and displayed a
generic Retry message while retaining the expired connection. Cancellation still
propagates. HTTP 403 retains the existing safe permission message; temporary
failures retain the last configuration snapshot and offer the existing Retry.
A successful retry replaces that snapshot and clears its error.

Account settings now expose Retry account after a failed/missing load and Refresh
account after a successful load. Both use the existing account loader and disable
while busy. Native clients retain dashboard management; local mode makes no
server request and offers neither action. A server that reports Simkl as
unconfigured still offers no Link/Unlink action.

The source credential dialog now resolves IME insets inside its own layout, using
the same approach as the qualified phone preference dialogs. The scrolling
credential/provider body shrinks while title, Cancel and Save remain fixed. No
global window flags, credential contract or provider availability changed.

The audit used the current 197-board manifest under
`.worktrees/kmp-v2/design/current`, including inspected PNG and HTML for
`M3SetAddons`, `M3AddAddon` and `Tv3SetAddons`. Phone coverage entries marked
Pending do not mean those routes are absent: the current compact list, per-profile
enable switches, add-on removal and add-on bottom sheet already exist. The old provisional phone
capture predates that renderer. The supplied add-on manifest preview/version
block has no inspected preflight contract, and this change does not invent one.
TV add-on QR pairing and direct Simkl PIN pairing also remain unsupported; native
account dashboard handoff and the truthful pairing notice remain intact.

GAP-004's blanket Pending label is stale. Existing safe HTTP messages, source
runtime-unavailable/configuration states and Retry, native dashboard address
fallback, and browser linking/unlinking were already implemented. This batch
repairs the concrete expiry, permission-copy, account recovery and keyboard gaps;
it does not establish complete supplied-board parity or every server failure
variant. Shared coverage and status consolidation are tracked separately.

Eight scoped tests pass across `SourceIntegrationRecoveryTest`,
`LinkedAccountRecoveryUiTest` and `SourceIntegrationFormUiTest`. They cover real
device-token expiry/retirement, permission refusal, retained configuration and
successful retry, cancellation, native account Retry/busy/success, browser
unconfigured state, local request isolation, and the credential dialog's keyboard
boundary, scrolling body, masked field and isolated Save dispatch. Before the
fixes, five cases fail at the corresponding missing behavior. The keyboard
regression places a 320px IME inset in a 390×844 viewport; without inset application,
Cancel ends at 718px below the 524px visible boundary.

Actual normal Android release `4cb59ab8ea45` reproduced the source-form defect on
the owned API 35 phone fixture (720×1600, density 280): the keyboard began at
1000px, while complete Cancel/Save controls occupied 1144–1228px. The inspected
capture contains a clearly fake draft value only. No credentials were saved or
submitted, no provider network call was made, and Cancel preserved native
preferences. The owned fixture was stopped afterward.

Rebuilt normal release `6cbcae7d7f6a` passed actual native IME verification on the
same owned fixture. With the keyboard still beginning at 1000px, complete
Cancel/Save controls occupied 874–958px and the masked field occupied 262–374px.
Cancel worked with the keyboard open and the reopened draft was empty. Native
preferences remained identical at revision 3, native connection counts stayed
0→0, and the observed schema labels/count stayed 3→3 (VIPTV Sources, Xtream live TV
and M3U live TV). No Save or provider request was submitted. Both final native
captures were inspected and the owned emulator was stopped.

That layout capture exposed a separate input-classification defect: the clearly
fake secret appeared in the IME suggestion bar despite visual masking. Secret
source fields now request a password keyboard with autocorrect disabled. The
shared sign-in input also has an explicit secret flag, enabled only by the phone,
desktop and IPTV password call sites. Show password changes visual presentation
without changing that flag; username/address defaults remain intact. Current
[Compose text-input documentation](https://developer.android.com/develop/ui/compose/text/user-input)
was fetched through Context7, and the installed Compose 1.13.0-alpha02 API confirms
the current `autoCorrectEnabled` option. The `6cbcae` receipt remains an
intermediate proof of layout and Cancel behavior.

Final normal release `7a1e8c8ab6d0` passed actual editor verification. The source
field used input type `0x81` (text-password), with autocorrect and autocomplete
flags unset. The inspected AOSP keyboard capture no longer showed the fake key in
its prediction bar. The keyboard began at 1070px; complete Cancel/Save controls
occupied 944–1028px and the masked field occupied 262–374px. Cancel/reopen-empty,
all eight preferences at revision 3, connection counts 0→0 and schema counts 3→3
passed again without submitting configuration.

The actual phone sign-in password retained input type `0x81`, with autocorrect
and autocomplete unset, in masked, Show password and hide-again states. A normal
username field remained ordinary text type `0x8001`. The visible toggle and mask
were inspected using unmistakably fake values; no login was submitted. All four
final native captures were inspected. The app returned to sign-in options and
the owned emulator was stopped afterward.

Baseline proof, red XML/log, scoped test XML and inspected common captures are
under [`evidence/integration-recovery-2026-10-15`](evidence/integration-recovery-2026-10-15).
The final merged desktop gate passed 287 tests with zero failures, errors or
skips; Android normal release, isolated Wasm and Linux app-image builds also
passed. The initial combined log retains a separate historical Home assertion
failure, while all eight integration cases passed there too. The exact final APK
and source hashes, native action bounds, before/after counts and guarded shutdown
are recorded in the [normal-release native receipt](evidence/integration-recovery-2026-10-15/native-7a1e8c8ab6d0/proof.json).

No real account, provider credentials, physical TV, user emulator, shared service
or default served Wasm output was changed. Native account error interaction and
full visual parity remain unqualified beyond the stated common-renderer checks.
