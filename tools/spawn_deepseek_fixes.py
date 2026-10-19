#!/usr/bin/env python3
"""Spawn read-only DeepSeek fix-brief agents (Ollama, max thinking)."""
import subprocess, sys

MODEL = "pi/ollama-cloud/deepseek-v4.1-flash:1017"
CWD = "/mnt/ALPH/code/viptv-org/app"

PRE = """READ-ONLY. Do not edit repository files. Do not run gradle, adb or the emulator. Repo: /mnt/ALPH/code/viptv-org/app (KMP Compose). Design boards: /mnt/ALPH/code/viptv-org/design/viptv-design-system/reference/screens/{html,img}/<platform>/<id>; use `python3 tools/board_spec.py <BoardId> [--platform phone|desktop|tablet|web]` for exact geometry.

Output an exact patch brief, not an essay. For every fix write:
FILE: <path relative to app/>
ANCHOR: <exact existing text to locate, 1-6 lines, copied verbatim>
REPLACE: <exact new Kotlin code>
WHY: <one line, cite the issue id>
If something needs data/API/decision that does not exist, write BLOCKED: <what is missing> and stop on that item.

Rules: match the surrounding code style; keep TV focus working (components/TvFocusTreatment.kt tvFocusOutline, components/TvColumnFocus.kt for settings columns, components/TvAction.kt, features/settings/TvSettingsRow.kt); never remove backdrop blur (rule R5); desktop/phone playback stays local-engine (R6); no new gateway references.
"""

AGENTS = [
("FIX-01 TV sources empty+torrent", """
Scope: TVUI-03. Read features/sources/TvSourcesScreen.kt and boards Tv3SourcesEmpty, Tv3SourcesTorrent (drawer 960 wide at x=960, padding 72/96/54/64, #161618).
Produce patches that: render the empty state as a real panel (title 'No sources yet', explanation naming how many add-ons/providers were checked, focusable action), and the torrent-only state as an alert panel with the exact board copy plus the torrent rows listed and dimmed (not hidden). Keep the existing focus restoration for source rows. Add a callback parameter with a default so existing App.kt call sites still compile.
"""),
("FIX-02 TV add-on entry/install", """
Scope: TVUI-04, TVUI-05. Read features/settings/TvSettingsScreen.kt (install AlertDialog), features/settings/AddonInstallForm.kt, features/settings/TvSourceIntegrationForm.kt, SourceIntegrationSettings.kt, and boards Tv3AddonEntry, Tv3AddonInstall, Tv3SourcesRemove.
Produce patches replacing the Material AlertDialog add-on entry with the board's full-screen TV page (address field + TV keyboard, Install/Cancel as TvActions, back closes) plus the install confirmation state, and replace the remove-confirmation Material dialog with the board-shaped TV confirm. Keep the existing install-with-completion callback behaviour.
"""),
("FIX-03 TV Display page", """
Scope: TVUI-07 Display. Read features/settings/TvSettingsScreen.kt (Display currently falls into the generic placeholder branch) and board Tv3SetDisplay.
Produce patches adding a real Display page: rows at x=640 width 1184 height 96 radius 14 (Accent colour with a swatch, Show ratings on cards, Text size, Reduce motion, Screen saver, Fit to screen), focused row filled Palette.text + 4px ring + shadow, D-pad clamped with TvFocusColumn. Values must persist: propose the smallest KMP-safe persistence (expect/actual key-value with Android SharedPreferences, desktop java.util.prefs, iOS NSUserDefaults, wasm localStorage) and give every file/actual verbatim. If that is too large, say BLOCKED for persistence and give the UI wired to a state holder that the caller owns.
"""),
("FIX-04 TV About page", """
Scope: TVUI-07 About. Read features/settings/TvSettingsScreen.kt and board Tv3SetAbout.
Produce patches for a real About page: Version (0.1.0-experiment), Connected to (dashboardAddress or Local mode), Send diagnostics (dialog stating exactly what would be shared, no fake upload), Privacy (dialog with the app's actual data statement), Open-source licences (dialog listing bundled licence files from composeApp/src/commonMain/composeResources/files/licenses if present), plus the footer credit line. Every row does something real; no placeholders. Use TvSettingsRow/TvAction and TvFocusColumn.
"""),
("FIX-05 TV Parental page", """
Scope: TVUI-06. Read features/settings/TvSettingsScreen.kt (Parental branch), ParentControlsNative.kt, TvSettingsAccount.kt, and board Tv3SetParental.
Produce patches for a real TV Parental page: profile selector chips, Movies rating chips row, Series rating chips row, helper copy exactly as the board, Hide-unrated toggle, Blocked titles value, PIN-to-leave row. Use only data reachable in TvSettingsScreen's current parameters; where a value genuinely is not available, render the row disabled with a precise reason (never a generic 'not available' dialog). Wire the PIN/profile actions to the existing profiles()/dashboard callback.
"""),
("FIX-06 TV Account + Recovery codes", """
Scope: TVUI-11, TVUI-15. Read features/settings/TvSettingsAccount.kt, features/entry/RecoveryCodesScreen.kt, App.kt routing for Screen.RecoveryCodes, and board Tv3SignInEmail (as the TV entry idiom).
Produce patches for: (a) a board-less but TV-correct Account page (email/username, devices, refresh, sign out) with no 'Managed on web' placeholder where a real action exists; (b) a TV-sized Recovery codes screen (inset 96, title 44/700, body 24 #B6B4AF, code in a bordered card, Done first focus, copy action) used when television=true.
"""),
("FIX-07 TV player parity", """
Scope: TVUI-12, PLAY-05. Read features/player/Tv*.kt and boards Tv3PlayerControls, Tv3PlayerBuffer, Tv3PlayerError, Tv3PlayerNext, Tv3PlayerScrub, Tv3PlayerSkip, Tv3PlayerAudio.
Produce patches for every visible delta you can fix with data already in TvPlayerState (copy, geometry, which control is focused, visible state transitions). Do not invent playback facts; list any state that needs a fact the app does not have as BLOCKED.
"""),
("FIX-08 TV sign-in family parity", """
Scope: TVUI-13. Read features/entry/TvSignIn.kt, TvSignInChoice.kt, TvEmailSignIn.kt, TvEmailCode.kt, TvPasswordPanel.kt, TvPairingSignIn.kt, TvRemotePairing.kt, TvPairingQr.kt, TvPairingBrowser.kt, TvWelcome.kt, TvSplash.kt, TvSignedIn.kt and boards Tv3SignIn, Tv3SignInChoose, Tv3SignInEmail, Tv3SignInCode, Tv3SignInPassword, Tv3Welcome, Tv3Splash, Tv3SignedIn, Tv3SignOut.
Produce patches for geometry/copy/focus deltas that do not change flow logic. Flag anything that would change behaviour as BLOCKED with the reason.
"""),
("FIX-09 TV profiles/pin/edit parity", """
Scope: TVUI-14. Read features/entry/TvProfiles.kt, TvProfileItem.kt, TvPinScreen.kt, TvPinKey.kt, TvProfileEditScreen.kt, TvProfileEditDialog.kt and boards Tv3Profiles, Tv3Pin, Tv3ProfileEdit.
Produce patches for visible deltas: tile ring spec (inset 4px white), keypad geometry/focus, edit rows, dialog replacement where the board has a page. Do not change auth/validation logic.
"""),
("FIX-10 Desktop hover/context/offline", """
Scope: DUI-04. Read composeApp/src/desktopMain/** and features/home/DesktopHero.kt, MediaCard.kt, HomeCatalogRow.kt, boards D3HomeHover, D3Context, D3Offline (desktop).
Produce patches implementing: card hover preview + hover states, right-click context menu on a card (Play, More info, Add to My List), and the offline info bar when the connection is unavailable. Use existing actions/callbacks; if a callback is missing give the minimal signature change and every call site.
"""),
("FIX-11 Desktop breakpoints + chrome", """
Scope: DUI-04/DUI-05. Read desktopMain/Main.kt, DesktopTitleBar.kt, DesktopWindowButtons.kt and boards D3Mini, D3HomeNarrow, D3HomeUltra (desktop).
Produce patches for width breakpoints (mini/narrow/ultra) applied to the rail and hero, maximize state correctness, and no square-corner flash on resize. Keep the existing undecorated window + rounded root surface.
"""),
("FIX-12 Desktop focus/hover everywhere", """
Scope: NAV-14, DUI-06. Read desktopMain/**, components/SideNavigation.kt, features/home/MediaCard.kt, DesktopHeroActions.kt, features/search/SearchScreen.kt, features/settings/* non-TV.
Produce patches so every interactive desktop element has both a hover and a keyboard-focus state, using components/TvFocusTreatment.kt tvFocusOutline for focus. List every control you cover and any you cannot (with file:line).
"""),
("FIX-13 Desktop search issues", """
Scope: NAV-13, NAV-15, NAV-16. Read features/search/SearchScreen.kt, features/search/SearchField.kt, desktopMain/DesktopTitleBar.kt, Main.kt, features/home/DesktopHero.kt.
Diagnose and patch: results overflow/last card cut (height constraints), the opaque black rectangle over centred content (find the exact surface), and the hero synopsis clamp with ellipsis matching the D3 board. Give exact anchors.
"""),
("FIX-14 Phone browse family", """
Scope: PHUI-01, BROWSE-04. Read features/browse/BrowseScreen.kt, DiscoveryFilters.kt, DiscoveryChoiceFilter.kt, DiscoveryMultiChoiceSheet.kt, DiscoveryTextFilter.kt, and boards M3Browse, M3BrowseCatalogs, M3BrowseFilters, M3BrowseLoading, M3BrowseEmpty, M3BrowseError (platform phone).
Produce patches for: the board header with catalog pill + Filters trigger, loading skeleton grid, empty state with 'Clear all filters', error state with Try again + Choose another catalog, and the catalogs bottom sheet. Keep the TV path untouched (guard with if (television)).
"""),
("FIX-15 Phone title page", """
Scope: PHUI-02. Read features/details/DetailsScreen.kt (phone branch), PhoneDetailHero.kt, DetailEpisodes.kt, DetailSeasonPicker.kt, SharedTitlePage.kt and boards M3Title, M3TitleEpisodes, M3TitleRelated, M3Recovery, M3About, M3SetDisplay.
Produce patches for visible geometry/copy deltas on the phone title page only, plus the More info sheet content (full metadata, scrim behind the rail) if it lives in your read set. No TV changes.
"""),
("FIX-16 Phone settings rows", """
Scope: PHUI-03. Read features/settings/PhoneSettings.kt, PhonePreferenceControls.kt, PhonePreferenceDialog.kt, PhoneProfilePreferences.kt, PhoneSubtitlePreferences.kt, SettingsItem.kt and boards M3Set*, M3Account.
Produce patches so phone settings rows match the boards (height, radius, icon, chevron, switch placement, section headers) and every row opens a real page or dialog.
"""),
("FIX-17 Tablet boards", """
Scope: PHUI-04. Read components/TabletNavigation.kt, features/details/TabletDetailsScreen.kt, features/home/TabletHero.kt and the 18 T3 boards under reference/screens/html/tablet.
Produce a concrete gap list plus patches for the highest-impact deltas: rail width/background/blur, two-pane layouts, hero geometry. If a board requires a screen that does not exist, list it as BLOCKED with the board id.
"""),
("FIX-18 Web boards", """
Scope: PHUI-05. Read composeApp/src/wasmJsMain/**, App.kt browser paths, features/*/Browser*.kt and the W3 boards under reference/screens/html/web.
Produce patches for the highest-impact web deltas (browse, parental, recovery, set sources) using the existing browser layout; flag screens with no implementation as BLOCKED with the board id.
"""),
("FIX-19 Cross-platform copy consistency", """
Scope: PHUI-06, NAV-06. Grep every user-visible literal in composeApp/src/commonMain and diff against the board text for the same screen. Produce patches replacing inconsistent strings with board copy, and a sanitiser for server-carried messages so raw backend text (e.g. 'API route not found') never reaches the UI. Give the exact anchor for each replacement and a single shared helper for server-message mapping.
"""),
("FIX-20 Auth audit", """
Scope: AUTH-01..04. Read connection/RemoteConnection.kt, platform secret stores (androidMain/AndroidSecrets.kt, desktopMain/DesktopSecrets.kt, iosMain, wasmJsMain), state/ControllerSession.kt, ControllerProfiles.kt, ControllerPreferences.kt.
Produce patches for concrete defects only: refresh-on-401 coverage per transport, clean sign-out with a message, restore-on-restart with no login flash, per-profile token/preference scoping. For each, cite the failing line and the replacement. Mark platform work you cannot verify as BLOCKED.
"""),
("FIX-21 Playback contracts", """
Scope: PLAY-02, PLAY-04, PLAY-05, PLAY-06. Read features/player/**, player/**, connection/*Playback*, and grep for gateway/forceGateway/reopensForSeek/first-frame/remoteSeek.
Produce (1) a proof table of which gateway paths are reachable per target with file:line, (2) patches removing any reachable gateway path on desktop/phone (R6), (3) patches for per-profile audio/subtitle persistence, (4) the torrent/debrid panel states per platform. BLOCKED for anything needing the iOS owner decision (PLAY-03).
"""),
("FIX-22 DATA-01 row paging", """
Scope: DATA-01. Read engine/wire EngineWire.kt (HomeRow.next_cursor), connection/RemoteConnection.kt, state/ControllerCatalog.kt, features/home/TvHomeShelf.kt.
Design and give verbatim code for: a connection API to page one home row by cursor, controller state for per-row busy/error, and shelf-end loading that appends without a button. Include the wire DTO change if needed and every call site. If the engine does not expose the call, specify the exact engine route/signature required and mark the app half BLOCKED.
"""),
("FIX-23 DATA-02/03/04 metadata", """
Scope: DATA-02, DATA-03, DATA-04. Read engine/wire EngineWire.kt (Card/CardMetadata), features/details/TvDetailHero.kt, DesktopDetailHero.kt, components/TvMediaMetadata.kt, MediaPresentation.kt and board Tv3CardStates/Tv3Home.
Produce patches for: cast line only if a real field exists (else the exact schema addition), trailer/share circles only with real actions (else remove or mark unavailable), and a defined mapping for 4K/HDR/5.1/CC chips from existing badges/metadata (no invented fields). State BLOCKED where the wire has nothing.
"""),
("FIX-24 Phone sheet/track/focus", """
Scope: NAV-05, NAV-07, NAV-08. Read features/details/DetailsScreen.kt phone branch, the More info sheet, features/player/PlaybackTrackPicker.kt, PlaybackTrackChoice.kt, PhonePlaybackControls.kt.
Produce patches: full metadata in More info with the rail scrimmed, track picker shows the current selection and a close affordance, and one focus treatment style per screen (ring, not shape change). Cite the exact anchors.
"""),
("FIX-25 Perf + docs", """
Scope: PERF-05..10, TEST-04..10, DOC-01/03. Read docs/TV_FRAME_BUDGET.md, tools/tv_ab.sh, tools/tv_frames.sh, tools/tv_cadence.sh, features/home/TvHomeScreen.kt, TvHomeShelf.kt, TvHomeBackdrop.kt.
Produce: (1) the exact measurement procedure per PERF item (build type, command, threshold, what to paste into TV_FRAME_BUDGET.md), (2) any code-level fix you can justify for shelf re-measure/decoding without a measurement first — mark it EXPERIMENT and require before/after, (3) the DOC-01/DOC-03 text to append. Do not claim numbers.
"""),
]

def main():
    for title, body in AGENTS:
        prompt = PRE + "\n" + body
        res = subprocess.run(["paseo", "run", "-d", "--title", title,
                              "--provider", MODEL, "--thinking", "max", "--cwd", CWD, prompt],
                             capture_output=True, text=True, timeout=60)
        out = (res.stdout or res.stderr).strip().splitlines()
        print(title, "->", out[-1] if out else "NO OUTPUT")

if __name__ == "__main__":
    main()
