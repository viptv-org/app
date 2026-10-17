# Supported preference settings — 2026-10-15

TV preference failures previously went into `state.preferencesError` while the
TV settings route displayed only `state.error`. Missing preferences consequently
looked unavailable and offered no direct retry. The actual safe load error and
existing `refreshPreferences` action now reach TV Playback/Subtitles. Retry gets
initial focus, disables while busy and restores loaded settings focus after
success. A retained snapshot stays visible during a failed refresh. No unavailable
preference becomes enabled or acquires a fabricated default.

Compact native Playback and Subtitles & audio now open distinct forms using
`M3SetPlayback` and `M3SetSubs`. Playback edits the actual source-selection and
quality fields. Subtitles has the bundled preview image, live size/text/background
preview, swatches, languages and default subtitle switch. Custom valid language
tags, colours, alpha and existing quality values remain usable. Every edit keeps
the other actual fields; rapid consecutive style changes use the latest draft
instead of resetting earlier changes. Only Save preferences persists the draft.
Canceling a choice never saves. Wide desktop and browser keep the existing form.

This uses the existing eight fields and optimistic revision API. The phone boards'
intro/next visibility, resume toggle, mobile-data mode and Shadow style have no
matching stored fields; they are not simulated. Existing resume and explicit-next
behavior is described truthfully. Custom value dialogs, explicit Save and load/
retry states are provisional additions, and phone system chrome is not rendered
by component captures. Full visual parity remains open.

`PreferenceSettingsUiTest` passes seven cases: TV error/retry focus, disabled duplicate
retry and restoration; subtitle-section failure; explicit Playback save/cancel
with unrelated fields preserved; actual subtitle preview/font/colour and all
eight saved fields; distinct compact routing; invalid custom values and load
retry without invented preferences; and keyboard-constrained language dialogs.
The last case supplies a 320px keyboard inset in a 390×844 viewport, verifies both
actions remain above the keyboard, scrolls to the custom field and applies a tag.
Removing inset application fails with Cancel ending at 696px below the 524px
visible boundary. Final focused tests took 3.95s, build 17s, seven passed and no
failures. The later merged platform gate passed 260 tests and the Android debug,
normal release, isolated Wasm and Linux app-image builds; its full evidence is
recorded separately by the parent agent.

The original three captures were inspected under
[`evidence/preference-settings-2026-10-15`](evidence/preference-settings-2026-10-15).
They qualify common Compose layout/focus/dispatch using synthetic preferences,
with exact source/capture hashes in the proof file.

Actual native phone qualification then installed immutable normal release
`b4394d8aab1c` on an owned API 35, 720×1600, density 280 emulator. Playback and Subtitles
each showed one heading. Dialog Cancel, unsaved route departure, explicit Save,
rapid size/background/colour changes, custom tags/colours/alpha and all eight
stored fields passed. Native SQLite confirmed drafts did not change a revision
and Save advanced it once. Reopen and force-stop/relaunch retained the values.
This run exposed a real language-dialog defect: action text lay at 1092–1127px
behind the keyboard beginning at 1000px.

`PhonePreferenceDialog` now reads IME insets inside its dialog layout. Material3
shrinks the scrolling body while keeping title/actions fixed, without global
keyboard flags. Rebuilt normal release `6b7079b06808` passed the same actual-phone
recheck: both language dialogs kept complete clickable controls at 874–958px above
the 1000px keyboard edge, and the custom field remained visible at 657–832px.
Use value and Cancel worked while the keyboard stayed open; the colour dialog
also passed. Native Save changed only audio `zh-Hant` to `fr-CA`, advanced revision 2
to 3 and preserved every other field. Reopen and cold launch retained all fields.

The same release remembered an explicitly chosen local connection across cold
launch and returned to Profiles, where QA explicitly selected Default. Canceling
Leave retained Local; confirming Leave cleared the remembered mode across another
restart while preserving the one native profile and its preferences. Eleven final
native screenshots were inspected. Exact APK/source/capture hashes, UI bounds,
native values, negative baseline and positive results are under
[`evidence/phone-preferences-native-2026-10-15`](evidence/phone-preferences-native-2026-10-15/ime-fix/native-6b7079b06808/proof.json).

This qualifies portrait native settings on that emulator, not a physical phone,
landscape layout, decoder playback or full visual-board parity. No backend/native
preference contract, provider policy, real account, live service, physical TV or
default served Wasm output changed.
