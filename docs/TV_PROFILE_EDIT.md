# Android TV profile editor checkpoint

`Tv3ProfileEdit` supplies the heading, left avatar/actions column, four settings
rows at x=640, and the delete row at y=784. The dedicated TV editor replaces the
generic form. It reuses the TV settings row and the Compose Unstyled switch.
Left moves from settings into Change avatar; Right returns to Name. Native Back
returns to Profiles after any active dialog has handled dismissal.

Rename, PIN, avatar and delete call the existing profile controller callbacks.
The existing local PIN policy remains 4–8 digits. Removing an existing PIN still
requires its current PIN. The last profile has no delete action. Maturity and
language lack engine presentation/settings contracts; normal connections display
Unavailable and explain the missing preference instead of inventing saved values.

The isolated preview supplies the board's All ratings and English labels. Name,
avatar and lock updates change memory only; deletion returns to Profiles without
deleting real data. These are UI fixtures, not authentication or persistence proof.
The selected profile ID is carried from the management picker into its editor.

Portrait avatars now use the supplied dark image background rather than their
fallback initial colour. This removes the coloured fringe at antialiased rounded
edges. Initial-only avatars retain their assigned colours.

The shared name/PIN/delete/avatar dialogs are provisional and tracked in
`DESIGN_GAPS.md`. Remote text-input treatment, maturity/language contracts,
connected persistence qualification, complete typography and motion review remain
unfinished. No supplied board is certified as fully matching.

The isolated 1920×1080 TV emulator exercised the unavailable maturity dialog,
avatar cancellation, a name update in preview memory, a synthetic PIN update
reflected by the lock switch, delete cancellation, and Back into Profiles.
Save/Cancel used keyboard activation. Dialog focus restoration waits for the
parent window to regain focus, then returns to the initiating control.
The portrait crop contained 156 red-tinted fringe pixels before the background
fix and zero afterward. Android assembly, desktop tests and Wasm compilation
passed. Connected mutation/persistence remains unqualified by these preview checks.
