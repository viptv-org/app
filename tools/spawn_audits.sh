#!/usr/bin/env bash
# Launch read-only audit subagents through the Paseo daemon.
# Usage: tools/spawn_audits.sh <batch:1|2|3>
set -u
cd /mnt/ALPH/code/viptv-org || exit 1

BASE="READ-ONLY AUDIT. Do not edit repository files. Do not run gradle, adb, emulator, or any build. Work by reading source, docs and design boards only. Write your report to the one path named at the end, then print that path and a 6-line summary. Repo: /mnt/ALPH/code/viptv-org (app = /mnt/ALPH/code/viptv-org/app). Design boards: /mnt/ALPH/code/viptv-org/design/viptv-design-system/reference/screens/{html,img}/<platform>/<id>; tool 'python3 tools/board_spec.py <BoardId>' prints exact geometry. Issue list context: app/docs/OPEN_TASKS.md and app/docs/UI_INVENTORY.md. The working tree is being edited concurrently, so cite file:line as you find it and never rely on a stale build artefact."

spawn() { # title provider out prompt
  local title="$1" provider="$2" out="$3" body="$4"
  timeout 40 paseo run -d --title "$title" --provider "$provider" --cwd /mnt/ALPH/code/viptv-org/app \
    "$BASE

$body

DELIVERABLE: $out" 2>&1 | tail -2
}

case "${1:-1}" in
1)
  # TV navigation & focus — five lenses
  spawn "NAV/correctness" pi /tmp/audit-nav-correctness.md \
    "Domain: Android-TV navigation and focus. Find every real bug: focus traps, dead ends, focus escaping a panel, invisible-but-focusable controls, controls with no visual focus state, screens with no initial focus, duplicate key handlers, Back mapped wrong (NAV-01..08, NAV-21, NAV-27). Start from components/TvNavigation.kt, TvColumnFocus.kt, TvFocusTreatment.kt, TvShelfScroll.kt, TvFocusScroll.kt, features/*/Tv*.kt, App.kt, and androidApp MainActivity BackHandler. Report: symptom, file:line, why it happens, minimal fix."
  spawn "NAV/board-focus" pi /tmp/audit-nav-board-focus.md \
    "Domain: TV focus *visual* spec (TVU-16/17). Compare every focused-state rendering in features/*/Tv*.kt and components/* against the boards (Tv3Set*, Tv3Home, Tv3Movies, Tv3CardStates, Tv3RowStates, Tv3Sources*, Tv3Player*). The board rule: focused control is filled #F4F2EE with a 4px white outline + soft shadow; cards use an inset 3px white ring. List every control that still changes shape, draws no ring, or uses a different fill/outline. file:line + board value."
  spawn "NAV/device-plan" pi /tmp/audit-nav-device-plan.md \
    "Domain: device verification plan for TV navigation. Read tools/tv_focus_audit.py and tools/tv_ab.sh, then design the exact per-screen device pass (commands, screens, expected focus order) that would prove zero navigation bugs on a 1920x1080 Android TV. Identify what the current harness cannot prove (e.g. offscreen lazy items, popups, dialogs, IME) and how to cover it. Do not run adb."
  spawn "NAV/data-state" pi /tmp/audit-nav-data-state.md \
    "Domain: navigation correctness under data states. Read ControllerNavigation.kt, ControllerPageRequest.kt, ApplicationController.kt, features/browse, features/details, features/sources, features/library, features/live. Find where a screen can lose focus or render blank when data changes (refresh, error, empty, paging, profile switch), where a late coroutine can move focus, and where Back returns to the wrong screen. file:line."
  spawn "NAV/tests" codex /tmp/audit-nav-tests.md \
    "Domain: regression protection for TV navigation. Read composeApp/src/desktopTest/*Tv*.kt and the TV composables. Say which navigation invariants are untested, which existing tests are weak (assert nothing meaningful), and specify 5-8 concrete tests to add (name, fixture, assertion). Do not write them."
  ;;
2)
  # TV design boards — five lenses
  spawn "TVUI/cards-rows" pi /tmp/audit-tvui-cards-rows.md \
    "Domain: TV cards and rows (TVUI-01/02, BROWSE-07). Compare features/home/TvCatalogCard.kt and TvHomeShelf.kt to Tv3CardStates/Tv3RowStates using tools/board_spec.py. List every state the wire can produce (art/no-art, progress, 0-3 badges, certification, logo vs text, episode, live, watched, returning, next_cursor) and whether it renders correctly. Include row insets/pitch: board Continue Watching card 384x216 at x=96 with 20 gap; verify app geometry."
  spawn "TVUI/browse" pi /tmp/audit-tvui-browse.md \
    "Domain: TV browse family (BROWSE-01..06). Compare features/browse/TvBrowseScreen.kt (+ DiscoveryFilters/Choice/MultiChoice/Text) to Tv3Movies, Tv3MoviesCatalogs, Tv3MoviesFilters, Tv3MoviesFilterText, Tv3MoviesLoading, Tv3MoviesEmpty, Tv3MoviesError. Required: catalogs switcher, segmented type filters, sort control, facet sheet (Text/SingleChoice/MultiChoice/Toggle/Number/Date apply correctly), skeleton/empty/error states, appending grid, no in-page page title, content at the 96px inset. Give exact deltas and missing states."
  spawn "TVUI/settings" pi /tmp/audit-tvui-settings.md \
    "Domain: TV settings pages (TVU-03..07, TVU-10..15). Compare features/settings/*Tv*.kt and features/sources/TvSourcesScreen.kt to Tv3SetDisplay, Tv3SetAbout, Tv3SetParental, Tv3SourcesEmpty, Tv3SourcesTorrent, Tv3AddonEntry, Tv3AddonInstall, Tv3Live, Tv3Player*, Tv3SignIn*, Tv3Profiles/Pin/ProfileEdit. Which sections are still placeholders/AlertDialogs/undesigned? List the concrete board mismatch per screen."
  spawn "TVUI/home" pi /tmp/audit-tvui-home.md \
    "Domain: TV Home polish (TVU-08/09). Compare features/home/TvHomeScreen.kt, TvHero.kt, TvHeroSummary.kt, HeroIndicators.kt, TvHomeLoading.kt to Tv3Home, Tv3HomeLoading, Tv3HomeToRows. Check: FEATURED n OF m semantics, dots geometry (48px active / 16px rest, 4px high, gap 8 at x=96,y=760), hero logo/text at 96/306, metadata at 494, synopsis 544 width 780, buttons 648 (220x72; 72 circles at 336/428), shelf title 30/600 at 824, card row 876. Report exact deltas."
  spawn "TVUI/player-entry" codex /tmp/audit-tvui-player-entry.md \
    "Domain: TV player + sign-in/profile parity (TVU-11..15, PLAY-05). Compare features/player/Tv*.kt and features/entry/Tv*.kt to Tv3PlayerControls/Buffer/Error/Next/Scrub/Skip/Audio, Tv3SignIn*/Tv3Welcome/Tv3Splash/Tv3SignedIn/Tv3SignOut, Tv3Profiles/Tv3Pin/Tv3ProfileEdit. Which states are missing or use different copy/layout? Also check Settings->Account (TVU-11) and Recovery codes (TVU-15) — no TV board exists; state what adaptation exists."
  ;;
3)
  # Desktop — five lenses
  spawn "DUI/code" codex /tmp/audit-dui-code.md \
    "Domain: desktop app (DUI-01..06, NAV-12..20). Read composeApp/src/desktopMain/**, components/SideNavigation.kt, features/*/Desktop*.kt, features/home/MediaCard.kt, features/search/SearchScreen.kt, features/history/HistoryScreen.kt. For each listed bug (search dismiss/clear, overflow, no focus ring, black rectangle, synopsis ellipsis, Apply-in-input, dead History affordances, title bar parity, hover+focus on every control) say fixed/broken/partial with file:line."
  spawn "DUI/board" pi /tmp/audit-dui-board.md \
    "Domain: desktop board parity. Compare DesktopTitleBar.kt, SideNavigation.kt, features/home/DesktopHero.kt/DesktopHeroActions.kt and the desktop boards D3HomeWin, D3HomeHover, D3Context, D3History, D3Offline, D3NotFound, D3Mini, D3Narrow, D3Ultra (design/.../html/desktop) using tools/board_spec.py. Report exact geometry/copy deltas for the title bar and rail; list which D3 boards have no implementation."
  spawn "DUI/nav" pi /tmp/audit-dui-nav.md \
    "Domain: desktop navigation/history model. Read state/ControllerNavigation.kt, ApplicationState.kt, Main.kt, DesktopTitleBar.kt. Audit the new navBack/navForward stacks: duplicate entries, forward cleared correctly, Details/Sources/Player interactions, back from Player, profile switch resetting history, and whether Back/Forward chevrons reflect reality. Find races and wrong returns. file:line."
  spawn "DUI/phone-web" pi /tmp/audit-dui-phone-web.md \
    "Domain: phone/tablet/web issues (NAV-21..26, PHUI-01..06). Read components/MobileNavigation.kt, PhoneHeader.kt, TabletNavigation.kt, features/details/*Phone*, features/settings/Phone*, features/live/LiveScreen.kt, features/search/SearchScreen.kt, features/library/*, features/browse/BrowseScreen.kt. Verify each reported phone bug against code (nav-rail jump, no back affordance, left-hugging edit form, Live no-guide order/wording, Discover gamepad wording on touch, search no-results vs source-failure, My List clipping, blank hero, duplicate titles). fixed/broken/partial + file:line."
  spawn "DUI/a11y-copy" pi /tmp/audit-dui-a11y-copy.md \
    "Domain: cross-platform copy + a11y (PHUI-06, R1). Grep user-visible strings in composeApp/src/commonMain (Text(\"...\"), contentDescription, error strings) and list: raw backend text leaking (e.g. 'API route not found'), gamepad wording on touch, strings that differ from the boards, missing contentDescription on icon-only controls, and duplicated strings that should be shared. file:line."
  ;;
4)
  # Data / playback / auth / perf — five lenses
  spawn "DATA/contracts" pi /tmp/audit-data-contracts.md \
    "Domain: data/API gaps (DATA-01..05). Read connection/Remote*.kt, controller state files, composeApp/src/commonMain/kotlin/org/viptv/engine/wire/EngineWire.kt, and BACKEND/core contracts if present. For DATA-01 home-row paging, DATA-02 cast, DATA-03 trailer/share, DATA-04 HD/5.1/CC chips, DATA-05 source failure: say what exists, what is missing on the wire vs UI, and the smallest correct connection. file:line."
  spawn "PLAY/contracts" codex /tmp/audit-play-contracts.md \
    "Domain: playback/platform contracts (PLAY-01..06, R6). Read features/player/**, connection/*Playback*, player/**, and grep for gateway/forceGateway/reopensForSeek/first-frame/remoteSeek. Prove or disprove that desktop+phone have zero reachable gateway paths; list every remaining gateway reference and which target it is compiled for. Check subtitle/audio preference persistence per profile and torrent/debrid states per platform."
  spawn "AUTH/session" pi /tmp/audit-auth-session.md \
    "Domain: auth (AUTH-01..04). Read connection/RemoteConnection.kt and platform secret stores (androidMain/AndroidSecrets.kt, desktopMain/DesktopSecrets.kt, iosMain, wasmJsMain), controller session files. Audit refresh-token rotation, 401 refresh on every transport, clean sign-out with message, restore-on-restart without login flash, and multi-profile token/preference scoping. file:line + concrete failure mode."
  spawn "PERF/budget" pi /tmp/audit-perf-budget.md \
    "Domain: performance claims (PERF-05..10, R3). Read docs/TV_FRAME_BUDGET.md, tools/tv_ab.sh, TvHomeScreen.kt, TvHomeShelf.kt, TvCatalogCard.kt, HomeBackdrop/TvHomeBackdrop, images/*. Identify what is measured vs asserted, which of PERF-05..10 have no evidence, and exactly how to measure each (command, build type, pass/fail threshold). Do not run builds."
  spawn "TEST/gates" pi /tmp/audit-test-gates.md \
    "Domain: testing/gates (TEST-01..10). Read build.gradle.kts, composeApp/build.gradle.kts, composeApp/src/desktopTest, androidApp, iosApp, tools/*.sh, .pi workspace gates. Map each TEST item to an actual command and current coverage; list which are red/unverified and which device passes have no saved evidence. Also audit docs/OPEN_TASKS.md, docs/UI_INVENTORY.md, docs/TV_FRAME_BUDGET.md for stale claims (DOC-01..04)."
  ;;
*)
  echo "usage: $0 1|2|3|4" >&2; exit 2;;
esac
