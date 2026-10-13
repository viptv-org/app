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

Provisional surfaces are recorded with their source locations and available review captures. No provisional surface is recorded as supplied-design parity.

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

### GAP-028 — TV Live without guide data or channels

`TvLiveScreen` displays TV-sized channel rows when no EPG window is supplied and
an empty state when no channels are available. Favorites controls are omitted
when the connection cannot save them. The TV ZIP supplies the populated Live
guide but no matching no-guide or empty boards. These surfaces remain provisional.

### GAP-029 — TV Calendar without releases or an actionable next episode

`TvCalendarScreen` distinguishes an empty supplied date from an unavailable
schedule. `TvCalendarPreview` uses View details when no explicit watch target is
provided. The populated `SkTvCalendar` board does not supply these surfaces;
they remain provisional and need design review.

### GAP-030 — TV longer PINs, submission and recovery/error states

`Tv3Pin` supplies the four-slot keypad scene. The engine accepts existing 4–8
digit PINs, so `TvPinScreen` adds an eight-slot layout after the fifth digit and
an OK key when submission is valid. Recovery-unavailable and error states also
lack matching boards. These additional states need supplied design review;
the initial four-slot scene follows the provided board.

### GAP-031 — TV Search loading, empty/error and long-query states

The populated `Tv3Search` board does not supply separate loading, empty, error or
long-query boards. `TvSearchLoading` uses the supplied poster dimensions and
shared motion-spec shimmer. Empty/error messages and single-line query ellipsis
are provisional. These states need design review.

### GAP-032 — TV password-entry auxiliary and long-input states

Delete character and Use another server are additional entry controls beyond
`Tv3SignInPassword`. The custom-server dialog, long-field ellipsis and loading/
error states lack matching supplied boards. These surfaces remain provisional.
The provided alternative phone/browser/email/IPTV boards are present designs
with unfinished implementations, rather than missing designs.

### GAP-033 — TV email-entry deletion and unavailable-code state

`TvEmailSignIn` adds a remote Delete character action, a bounded long-email state
and an unavailable-code dialog beyond the populated `Tv3SignInEmail` board.
These extra surfaces need design review. The supplied code-entry/phone boards
exist and remain unfinished; no successful email delivery is implied.

### GAP-034 — TV IPTV M3U/input/unavailable states

The supplied IPTV board shows Xtream fields and an M3U entry tab, but no expanded
M3U form, field-input dialog or missing-service state. `TvIptvSignIn` supplies
those provisional surfaces. Its in-memory storage explanation intentionally
differs from the board's TV-only persistence claim until storage is connected.

### GAP-035 — TV email-code unavailable states

`TvEmailCode` supplies verification/resend-unavailable notices absent from the
populated `Tv3SignInCode` board. These provisional dialogs need design review.
The normal email flow does not invent a sent session; the code screen is currently
reachable through the isolated preview with explicit fixture challenge data.

### GAP-036 — TV catalog loading and empty states

`TvBrowseLoading` uses the supplied seven-column poster dimensions with the
shared motion-spec shimmer. The populated Movies board does not supply a
separate catalog-loading or empty/no-match board. These provisional states need
design review; no full-width progress bar is inserted above existing results.

### GAP-037 — TV focused-title background without landscape artwork

When a card supplies only a portrait poster, `TvHomeBackdrop` uses the theme
background rather than stretching it full-screen. The shelf poster remains.
The supplied browsing board has landscape artwork; its missing-art state is
provisional and needs review. Connected providers still need to supply appropriate
wide artwork for full fidelity.

### GAP-038 — TV pairing without a session or refresh service

`TvPairingSignIn` shows explicit missing-session and refresh-unavailable states
absent from the populated `Tv3SignIn` board. No fabricated QR/code/expiry is shown
without session data. The preview uses a matching `.invalid` host and QR URL for
safe review; production pairing/authentication remains unimplemented.

### GAP-039 — TV playback failure with reduced recovery actions

`TvPlayerFailure` omits Retry or source selection when their callbacks are absent.
The supplied `Tv3PlayerError` board contains all three actions; source-only and
Back-only arrangements, including generic Back-only explanatory copy, are
provisional and need design review. Preview actions do not prove real source
fallback or position-preserving recovery.

### GAP-040 — TV seek preview without thumbnail data

`TvSeekPreview` retains a bounded timestamp when no thumbnail covers the proposed
seek position. The supplied `Tv3PlayerScrub` board contains an image, so this
time-only arrangement is provisional and needs design review. No unrelated
poster or fabricated thumbnail is substituted.

### GAP-041 — Details with incomplete season metadata

`DetailEpisodes` and `DetailSeasonPicker` group loaded episodes using the actual
season values and preserve entries whose season is unknown. Specials and other
episodes, missing season labels and counts based only on loaded data do not have
matching populated boards. These states remain provisional; counts do not imply
that the provider supplied a complete series inventory.

### GAP-042 — External provider subtitle choices

`ProviderSubtitles`, `TvPlaybackTrackPicker` and `NativeProviderSubtitle` expose
external subtitle candidates alongside the player's embedded tracks. The supplied
audio/subtitle board covers embedded language choices, but not provider labels,
external download failures or changing candidate lists. The additional labels and
failure states remain provisional and require design review.

### GAP-043 — Browser provider caption placement

Native browser tracks place default cues underneath the canvas toolbar and its
bottom gradient. Automatically positioned provider cues now use a provisional
72% vertical position so captions remain legible above that chrome. Explicit
WebVTT line settings are preserved. The supplied boards do not specify external
browser caption placement; the safe position needs design review across viewport
sizes and multiline captions.

### GAP-044 — Provider-driven discovery controls and pagination

Discovery now uses the selected profile's advertised query controls and real
catalog pages. Type, catalog, genre and timeframe controls use the existing theme.
The supplied boards do not specify all provider-dependent combinations, numeric,
date or multi-select filters, or the Load more action. These need final designs.

### GAP-045 — My List actions and remote profile availability

Phone, tablet, desktop and browser title details now include a provisional My
List action using existing Lucide icons and theme controls. The supplied title
boards do not cover every saved state or action placement. Remote profile
management follows server availability, hides unsupported per-profile PINs,
protects the primary profile from deletion, and identifies account parent PINs
separately. These capability-dependent variants need updated design boards.

### GAP-046 — Linked account management

The browser Account settings surface now shows the selected profile’s Simkl
connection and supports the server’s OAuth 2.0 authorization and unlink flow.
Native clients explain that account linking happens in the web dashboard; local
mode identifies server-only integrations. The linked, unconfigured, loading,
error and unlink confirmation variants need supplied design boards.

### GAP-047 — Source integration configuration

The supplied boards do not include a form for plugin-owned credentials and provider selection. A provisional shared settings form shows engine-supplied fields, write-only credentials, bounded provider choices and removal confirmation. TV opens it from Add-ons. Replace with supplied design when available.
