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

Each implemented provisional surface will include its source location and a review capture. No provisional surface is recorded as supplied-design parity.

The remaining supplied screens are tracked as pending/in progress in `design-coverage.json`; they are not classified as missing designs. No current board has passed visual parity review.
