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
| GAP-015 | My List unavailable notice | The design assumes persisted saved titles; the experiment has no shared list operation yet | Provisional notice in `features/home/PhoneHero.kt`; visual capture pending |
| GAP-014 | Custom server selection in desktop password flow | The supplied password board assumes one server | Use another server expands the address field in `DesktopPasswordPanel.kt`; capture `docs/evidence/desktop-signin-password.png` |

Each implemented provisional surface will include its source location and a review capture. No provisional surface is recorded as supplied-design parity.

The remaining supplied screens are tracked as pending/in progress in `design-coverage.json`; they are not classified as missing designs. No current board has passed visual parity review.
