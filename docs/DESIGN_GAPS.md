# UI without a supplied matching design

| ID | Surface | Reason | Status |
| --- | --- | --- | --- |
| GAP-001 | Local/remote connection selection | Native local mode; browser remains remote-only | Initial provisional implementation: `features/entry/ConnectScreen.kt`; capture `docs/provisional/connection-desktop.png` |
| GAP-002 | Profile integration assignment | One browsing integration; multiple sources/subtitles | Initial per-profile add-on switch in `features/settings/SettingsScreen.kt`; capture `docs/provisional/addon-settings-android.png`; full capability controls pending |
| GAP-003 | Simkl TV connection | Dashboard linking replaces direct provider PIN flow | Implemented native explanation and browser linking in `features/settings/LinkedAccounts.kt`; supplied-design review pending |
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
| GAP-016 | Unavailable header capabilities | Casting, TV remote control and provider genre controls lack connected contracts | Phone notices in `components/PhoneHeader.kt`; desktop Cast/Remote/Forward buttons are disabled when no operation exists; capture pending |
| GAP-015 | My List unavailable notice | Earlier missing shared list operation | Resolved by persisted local/remote My List; capability-dependent action variants remain in GAP-045 |
| GAP-014 | Custom server selection in desktop password flow | The supplied password board assumes one server | Use another server expands the address field in `DesktopPasswordPanel.kt`; capture `docs/evidence/desktop-signin-password.png` |

Provisional surfaces are recorded with their source locations and available review captures. No provisional surface is recorded as supplied-design parity.

The remaining supplied screens are tracked as pending/in progress in `design-coverage.json`; they are not classified as missing designs. No current board has passed visual parity review.

### GAP-020 — Phone entry connection states

`MobilePasswordPanel` includes a collapsible server address selector, which has no
supplied phone board. `MobileSignInUnavailable` still covers email/recovery. Native IPTV entry now configures the selected local profile; its additional states are in GAP-056. Browser
and device sign-in now use the same real server challenge as TV. Explicit server
selection, creating/expired/error and browser-opening failure variants remain
provisional; supplied normal-state boards still await full parity review.

### GAP-021 — TV entry controls beyond the supplied board

`TvPasswordPanel` adds Delete character and Use another server actions. The ZIP
keyboard lacks a deletion key and assumes a fixed server. The welcome footer
omits “No profiles or sync” because local profiles are an agreed app requirement.
Phone/browser device pairing is connected to the server-owned challenge flow.
Email entry remains unfinished. Native IPTV entry now saves provider credentials through the profile-local engine; its added states are in GAP-056.

### GAP-022 — TV title/source capabilities without connected contracts

The source drawer's normal footer says “Select a source to start playback” because
automatic best-match selection and per-profile ranking preferences are unfinished.
Quality, format and add-on attribution use structured source projections.
Provider/quality chips and source rows use the existing design geometry; added
provider filter variants need review. Trailers and casting still expose unavailable
states where no operation exists. My List, resume and related titles now use
connected local/remote operations.

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
The provided alternative entry boards are present designs, with parity still under review. Email delivery remains unfinished.

### GAP-033 — TV email-entry deletion and unavailable-code state

`TvEmailSignIn` adds a remote Delete character action, a bounded long-email state
and an unavailable-code dialog beyond the populated `Tv3SignInEmail` board.
These extra surfaces need design review. The supplied code-entry/phone boards
exist and remain unfinished; no successful email delivery is implied.

### GAP-034 — TV IPTV M3U/input states

The supplied IPTV board shows Xtream fields and an M3U entry tab, but no expanded
M3U form, field-input dialog or missing-service state. `TvIptvSignIn` supplies
those provisional surfaces. Native submission now configures the chosen local profile using the encrypted engine vault. The replacement storage copy and profile-selection/error variants are recorded in GAP-056.

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

Portrait-only cards use a bounded portrait image over a soft poster backdrop in
`TvHomeBackdrop`. Cards with neither landscape nor portrait artwork retain the
theme background. The supplied browsing board has landscape artwork; these
missing-art variants need review. Wide provider artwork remains preferable.

### GAP-038 — TV pairing server selection and challenge states

`TvRemotePairing` connects the supplied `Tv3SignIn` layout to server-issued QR,
code, expiry and polling. The explicit HTTPS server selector, creating-code,
expired/error and use-another-server variants are additional provisional UI.
The preview uses a matching `.invalid` host; normal sign-in uses real challenge
data. Physical-TV pairing and these additional states still need visual review.

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
date or multi-select filters. These need final designs. Catalog pages load automatically without a manual Load more action.

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

### GAP-048 — Profile preference forms

Phone, tablet, desktop and Web use a provisional shared preference form for language tags, subtitle default, source selection and quality. TV uses the supplied settings geometry with real profile persistence for supported fields. Next-episode prompts, intro skipping, display rate switching and persisted subtitle styling remain unavailable until their host/player contracts exist.

### GAP-049 — Profile metadata provider choice

Add-on settings contain a provisional capability-driven choice of the profile’s one metadata/browsing provider. Search does not ask the user to choose a provider. Available choices come from server plugin descriptors and configured connections; native Stremio mode shows its single provider. Stream/subtitle assignments are preserved when changing metadata provider.

### GAP-050 — Live catalog loading and pagination

Live channels and guide programs are host-provided records. Missing guide, unavailable connections, loading and retry states use provisional messages; guide selection fetches only the selected channel. Group controls retain supplied geometry while binding opaque host group IDs. Channels and guide lists request subsequent bounded pages automatically near the end; no manual pagination action is shown. Guide clocks render provider epoch timestamps in the device time zone, including midnight and DST transitions.

Discovery text/number/date filters keep a draft and use a provisional Apply action to prevent incomplete values and per-keystroke requests. Stremio multiple-choice discovery now uses real advertised choices and selection limits; its additional sheet states are tracked in GAP-057. Simkl discovery remains single-valued for its currently documented feeds.

### GAP-051 — Combined library filters

The user requested sorting inside the filter panel. A shared provisional panel
now presents host-supplied sorting, watched state, genre, added date and linked
watchlist status/personal-rating/episode filters. Simkl imports join the main Library; there is no separate Simkl collection. Filters open as a right sheet on TV/desktop and bottom sheet on phones. Phone controls use two rows to avoid clipping. Loading,
partial metadata and empty filtered results need matching updated boards. Further pages load automatically near the viewport end. The supplied SkListFilter informs the available fields;
its separate sorting/PRO examples are not claimed as implemented provider APIs.

### GAP-052 — Bounded episode page loading

Season labels and counts come from the host's canonical index. Per the user's
updated requirement, episodes load automatically in bounded background pages.
There is no Load more episodes action. Images remain viewport-lazy and selecting
a season prioritizes its missing records. Network-failure copy remains provisional;
reselecting a season retries its remaining page.

### GAP-053 — Source provider filter

The current Sources board has quality chips. The requested additional provider filter
uses a second chip row, with host-owned source IDs and supplied provider display names.
Structured format/codec/audio/size fields render only when returned by the provider.
The added filter row needs a matching supplied board; the surrounding drawer follows Tv3Sources.

### GAP-054 — Detail loading layout

Full-layout placeholders now occupy the title, metadata, description, actions, season selector
and episode cards while native detail data loads. The supplied set has Home and Sources
loading boards, but no matching Info skeleton board. These static placeholders need review
and a supplied loading design; static drawing avoids a continuous TV animation workload.

### GAP-055 — Native device and browser pairing states

Phone and desktop use the provided device-code and browser-waiting layouts with
real server challenges, complete QR links, expiry and approval polling. New-code
and use-another-server controls, the HTTPS server dialog, invalid-address feedback,
creating/expired/error states, variable server-address text and failed browser
opening have no individually supplied boards. The new controls and dynamic copy
are provisional; normal supplied layouts are not certified as exact parity.

`docs/evidence/native-pairing-desktop-{device,browser}.png` shows the packaged
Linux layout. `native-pairing-phone-{device,browser}.png` shows common phone layout
at 390×844 in an isolated desktop renderer; it does not prove Android input or OS
chrome. These fixtures use `.invalid` links and never open an actual browser.
Both generated QR images decode to the complete expected link/code. Real protocol
approval/restore/Home evidence remains separate from these layout captures.

### GAP-056 — Native IPTV setup and profile selection

Phone/tablet/desktop and TV entry now submit M3U/Xtream credentials to the local
engine. A fresh, unlocked sole profile proceeds directly to Live. Existing multiple
or PIN-protected profiles require selection/unlock before a provider is assigned.
The setup notice/cancel action over the profile picker, save/loading/network-error
states, empty fields and independent tab drafts have no exact supplied boards.
The profile-storage explanation replaces the board's keychain/add-on wording
because the engine encrypts credentials using its platform-backed vault and this
entry currently opens Live channels. Xtream VOD entry is not implied.

`native-iptv-{desktop,phone}-{xtream,m3u}.png` under `docs/evidence` shows the
packaged Linux/common-phone layout with reserved-domain fixtures. Phone captures
are 390×844 common layouts, not Android keyboard/status-bar qualification. Review
uses Skiko SOFTWARE_COMPAT on an isolated Xvfb display. All four image files
contain their headings and back links. A separate windowless Compose scene
rendered 12 frames with an active-tab pointer press/release; the header's decoded
pixels were identical in every frame. An earlier visual inspection incorrectly
marked the M3U heading missing; the image pixels contradict that diagnosis.
No rendering workaround was retained. These captures do not qualify Android
input, OS keyboard/status-bar placement or default desktop GPU performance.
No device app was replaced.

### GAP-057 — Provider discovery selection sheets

Provider-owned multiple-choice filters use a right sheet on desktop/TV and a bottom
sheet on phones. Checkbox edits stay local until Apply; required selections and
provider limits are respected. Cancel preserves the active query. TV starts on an
enabled option, including a selection below the initial viewport, and returns to
the filter trigger after closing. Rapid Apply input submits once.

The sheet, count/limit/required/default copy, disabled choices at the limit, catalog labels
with friendly connection names, and missing-required-filter errors have no exact
supplied boards. These are provisional states; no full design parity is claimed.
`docs/evidence/discovery-multichoice-{desktop,phone,tv}.png` shows isolated common
Compose layouts with synthetic genre choices, not a live Simkl capability.
Three real Compose input/focus checks cover Apply/limit behavior, required catalog search input and TV initial focus;
Android input, physical TV rendering and browser popup placement remain separate.
