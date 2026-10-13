# Android TV settings checkpoint

The TV settings shell uses the supplied 560-pixel sidebar and content starting at
640 pixels. Dedicated Playback, Subtitles & audio, Add-ons and sign-out surfaces
replace the generic add-on page. Lucide supplies icons. Compose Unstyled supplies
the switch behavior/placement with separately sized track and thumb. The subtitle
style preview uses the ZIP artwork, with size, background and colour controls.

Normal local add-on installation and profile enable/disable use the existing
controller/engine actions. Remote add-on management, account details/device lists,
TV add-on pairing and persistent player preferences do not have connected
contracts yet. Missing values are not presented as saved defaults. The isolated
preview uses sample account/add-on data and in-memory preferences; a changed
preview switch is not proof of engine persistence.

Extra option-list/address-entry dialogs and incomplete Account/Display/Parental/
About panels are recorded in DESIGN_GAPS.md. The local exit confirmation preserves
local-profile/progress wording, while the remote sign-out scene follows the ZIP's
account wording. No TV board is certified as fully matching.

The disposable 1920×1080 TV emulator passed fixture playback-toggle changes,
sidebar navigation, subtitle style changes, profile add-on enablement changes,
manual-address dialog opening/dismissal and sign-out cancellation. Back first
closes the native TV keyboard when it is active; a further Back dismisses the
address dialog. All checks are scoped to the fixture, rather than being described
as persistent preference or remote account checks. Build checks passed for
Android, Desktop tests and Wasm compilation.

## Source comparison update

The latest comparison with `Tv3SetPlayback` and `Tv3SetSubs` corrects settings
labels to weight 600, value/subtitle text to weight 500, focused secondary colour,
sidebar icon spacing and subtitle option geometry/colours. Left now traverses
previous subtitle sizes/background choices/colour swatches; only the first item
in each group routes Left to the sidebar. Previously every option redirected
Left to the sidebar. These changes need a fresh build and rendered remote check;
they do not establish persistent settings integration or full parity.
