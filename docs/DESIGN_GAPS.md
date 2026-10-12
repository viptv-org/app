# UI without a supplied matching design

| ID | Surface | Reason | Status |
| --- | --- | --- | --- |
| GAP-001 | Local/remote connection selection | Native local mode; browser remains remote-only | Initial provisional implementation: `features/entry/ConnectScreen.kt`; capture `docs/provisional/connection-desktop.png` |
| GAP-002 | Profile integration assignment | One browsing integration; multiple sources/subtitles | Initial per-profile add-on switch in `features/settings/SettingsScreen.kt`; capture `docs/provisional/addon-settings-android.png`; full capability controls pending |
| GAP-003 | Simkl TV connection | Dashboard link replaces direct provider PIN flow | Pending implementation |
| GAP-004 | Server and integration failures | Empty, unauthorized and unavailable are distinct | Pending implementation |
| GAP-005 | Administrative dashboard | Current boards cover accounts/devices, not every admin operation | Pending implementation |
| GAP-006 | Local add-on removal | No matching confirmation board in the current set | Provisional confirmation in `features/settings/SettingsScreen.kt`; settings capture `docs/provisional/addon-settings-android.png` |
| GAP-007 | Local History clear confirmation | Current History boards show Clear without its confirmation state | Provisional confirmation in `features/history/HistoryScreen.kt` |
| GAP-008 | Local profile creation dialog | Current profile picker shows Add profile, but no local creation form | Provisional dialog in `features/entry/ProfilesScreen.kt`; capture `docs/evidence/android-profiles.png` |
| GAP-009 | Confirming a 5–8 digit local PIN | The supplied phone PIN keypad has four indicators and no confirmation key | Provisional OK key in `features/entry/MobilePinScreen.kt`; capture `docs/evidence/android-profile-pin.png` |
| GAP-010 | Local forgotten-PIN explanation | The supplied PIN board has Forgot PIN? without a local recovery state | Provisional explanation in `features/entry/MobilePinScreen.kt`; no credential recovery or reset flow |
| GAP-011 | Local avatar picker | The current edit board has Change avatar but no selection sheet | Provisional selection sheet in `features/entry/AvatarPicker.kt`; capture `docs/evidence/android-avatar-picker.png` |
| GAP-012 | Secure-storage warning for remote sessions | The current Settings boards do not show a failed OS-vault state | Provisional warning in `features/settings/SettingsScreen.kt` when a native device session can only live in memory |
| GAP-013 | Linux desktop window controls | The current desktop ZIP shows Windows and macOS chrome, with no Linux board | Provisional Linux variant follows the Windows control placement in `desktopMain/DesktopTitleBar.kt`; capture `docs/evidence/desktop-titlebar-linux.png` |
| GAP-019 | Unavailable channel favourites and calendar filters | Normal connections currently lack the save/filter operations | Provisional notices in `features/live/LiveScreen.kt` and `components/PhoneHeader.kt`; preview guide/calendar captures cover the surrounding layouts |
| GAP-018 | Confirming a 5–8 digit TV PIN | The supplied TV keypad has four slots and no confirmation key | Provisional OK key in `features/entry/TvPinScreen.kt`; `docs/evidence/android-tv-pin-preview.png` records preview input |
| GAP-017 | Phone settings capability notices | Playback preferences, notifications, pairing and account/help settings lack connected behavior | Provisional explanation in `features/settings/PhoneSettings.kt`; settings landing capture uses ZIP artwork in preview mode |
| GAP-016 | Unavailable phone header capabilities | Casting, remote pairing and provider genre controls are not connected yet | Provisional notice in `components/PhoneHeader.kt`; capture pending |
| GAP-015 | My List unavailable notice | The design assumes persisted saved titles; the experiment has no shared list operation yet | Provisional notices in `features/home/PhoneHero.kt`, `DesktopHeroActions.kt`, `TvHero.kt` and `features/library/LibraryScreen.kt`; visual capture pending |
| GAP-014 | Custom server selection in desktop password flow | The supplied password board assumes one server | Use another server expands the address field in `DesktopPasswordPanel.kt`; capture `docs/evidence/desktop-signin-password.png` |

Each implemented provisional surface will include its source location and a review capture. No provisional surface is recorded as supplied-design parity.

The remaining supplied screens are tracked as pending/in progress in `design-coverage.json`; they are not classified as missing designs. No current board has passed visual parity review.

### GAP-020 — Phone entry connection states

`MobilePasswordPanel` includes a collapsible server address selector, which has no
supplied phone board. `MobileSignInUnavailable` provides temporary unavailable
states for browser, email/recovery, device approval, and IPTV authentication until
those connection flows exist. Their supplied boards remain unfinished, rather
than being counted as missing designs.

### GAP-021 — TV entry controls beyond the supplied board

`TvPasswordPanel` adds Delete character and Use another server actions. The ZIP
keyboard lacks a deletion key and assumes a fixed server. The welcome footer
omits “No profiles or sync” because local profiles are an agreed app requirement.
Unimplemented phone/browser/email/device/IPTV approval flows remain supplied
boards awaiting implementation; their unavailable states are temporary.

### GAP-022 — TV title/source capabilities without connected contracts

The source drawer's normal footer says “Select a source to start playback” because
automatic best-match selection and per-profile ranking preferences are unfinished.
Quality, format, add-on attribution and ranking currently render from structured
preview values; normal `SourceCandidate` data lacks those fields. TV title options
have temporary unavailable notices for My List and trailers. Cast/resume and
recommendation presentation is currently supplied only in the review fixture.

### GAP-023 — TV playback episode picker and control failure notice

`TvPlayerEpisodePicker` supplies a bottom episode shelf opened by the provided
Episodes pill; the current TV ZIP has no separate player episode-picker board.
`TvPlayerControls` adds a small nonfatal control-error notice, distinct from the
supplied full source-failure screen. Both need a supplied design review.

### GAP-024 — TV settings dialogs and unavailable preference states

`TvSettingsChoiceDialog` supplies option lists opened by settings rows;
`TvSettingsScreen` supplies a manual add-on-address dialog. The TV ZIP shows the
entry controls but no matching option-list/address-entry boards. Account,
Display, Parental controls and About also lack complete supplied panel designs.
Missing account data, unavailable playback/subtitle preference contracts and
unavailable add-on pairing have explicit provisional states. The supplied
Playback/Subtitles/Add-ons/Sign-out boards remain incomplete until their connected
behavior and final visual review are qualified.

### GAP-025 — TV My List sort options and empty/unavailable states

`TvLibraryControls` opens a remote-sized sort menu from the supplied sort pill.
`TvLibraryScreen` shows empty and unsupported-connection states with a Browse
Home action. The TV ZIP provides the populated My List board, but no sort-menu
or empty/unavailable boards. These provisional surfaces need supplied designs.

### GAP-026 — TV add-profile input dialog

The existing shared name-entry dialog is used by Add profile. The TV ZIP supplies
the Add tile and a separate Edit profile board, but no add-profile name-entry
dialog. The shared dialog is provisional and requires a dedicated TV treatment
or supplied design. The supplied Edit profile board's dedicated layout is in
progress; its design is present.

### GAP-027 — TV profile editing dialogs

The supplied Edit profile board has Name, Change avatar, Profile lock, rating,
language and Delete entry controls, but no corresponding TV input/picker/delete
confirmation boards. `TvProfileEditDialog` and the reused `AvatarPicker` provide
provisional dialogs. Rating/language also expose missing engine preference states.
The editor board itself is supplied and remains in progress; these additional
dialog surfaces need design review.
