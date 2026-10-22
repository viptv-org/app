#!/usr/bin/env python3
"""Spawn read-only DeepSeek agents hunting TV navigation bugs (queued 2 at a time via queue_deepseek.py)."""
import subprocess, sys

MODEL = "pi/alibaba-intl/deepseek-v4.1-flash-1017"
CWD = "/mnt/ALPH/code/viptv-org/app"

PRE = """READ-ONLY. Do not edit repository files. Do not run gradle, adb or the emulator. Repo: /mnt/ALPH/code/viptv-org/app (KMP Compose Multiplatform TV+phone+desktop).

You are hunting TV (D-pad/remote) NAVIGATION bugs and navigation that is not UX-friendly. Report ONLY issues you can defend with code evidence. For each finding:

ID: NAV-<zone>-<n>
FILE: <path relative to app/> (+ line refs)
SYMPTOM: what the remote user experiences (dead end, wrong target, focus lost, teleport, unreachable control, no escape)
EVIDENCE: the exact code that causes it (1-6 verbatim lines)
SEVERITY: high (strand/block) | medium (wrong landing) | low (annoyance)
FIX: minimal change sketch

Hunt specifically for: focus dead-ends (no escape on any direction), controls never reachable by D-pad, focus lost when content refreshes or a row re-pages, Up/Down landing on the wrong row, focus groups that swallow keys, restore paths that re-focus a disposed card, D-pad held-repeat traps, and modals/popups that don't trap or release focus correctly. If the zone is clean, say CLEAN with one line of proof."""

AGENTS = [
("NAV-01 home rows and billboard", """
Read composeApp/src/commonMain/kotlin/org/viptv/app/features/home/TvHomeScreen.kt, TvHomeShelf.kt, TvCatalogCard.kt, TvHomeBackdrop.kt, TvBillboardSelection.kt, components/TvShelfScroll.kt, components/TvNavigation.kt, components/TvFocusScroll.kt, components/TvFocusTreatment.kt.
Focus on: shelf-to-shelf D-pad, hero<->row transitions, the rail, focus restore after a background Home refresh, card removal while focused, Up from row 0, the Continue Watching vs poster-row differences.
"""),
("NAV-02 details episodes sources", """
Read composeApp/src/commonMain/kotlin/org/viptv/app/features/details/ (all Tv* files) and features/sources/TvSourcesScreen.kt, TvSourceRow.kt, TvSourcesFilters.kt.
Hunt: episode rail navigation, season switching focus, sources drawer open/close focus ownership, provider filter chips, the dimmed torrent rows, Back from each level, focus after a source finishes discovering.
"""),
("NAV-03 settings screens and sidebar", """
Read composeApp/src/commonMain/kotlin/org/viptv/app/features/settings/ (TvSettingsScreen.kt, TvSettingsSidebar.kt, Tv*Settings*.kt, TvSourceIntegrationForm.kt, TvAddon*.kt) and components/TvColumnFocus.kt.
Hunt: sidebar<->content handoffs, clamped columns that trap, sub-pages/dialogs not returning focus, rows unreachable below the fold, focus after an async reload.
"""),
("NAV-04 entry flows", """
Read composeApp/src/commonMain/kotlin/org/viptv/app/features/entry/ (TvSignIn*.kt, TvProfiles.kt, TvPinScreen.kt, TvProfileEditScreen.kt, TvEntryKeyboard.kt, TvPairingQr.kt).
Hunt: keyboard focus entry/exit, profile tile selection, PIN flows, QR steps, Back behavior at each step, initial focus on every screen.
"""),
("NAV-05 browse live calendar search", """
Read composeApp/src/commonMain/kotlin/org/viptv/app/features/browse/TvBrowseScreen.kt, TvBrowseChrome.kt, features/live/, features/calendar/, features/search/ (TV variants).
Hunt: grids and chips, catalog switchers, filter surfaces, EPG/channel navigation, search keyboard<->results handoff, pagination focus retention.
"""),
("NAV-06 player controls", """
Read composeApp/src/commonMain/kotlin/org/viptv/app/features/player/ (Tv* player files) and components that draw the OSD.
Hunt: OSD focus order, seek left/right traps, track/subtitle picker focus return, up-next card, error panels, Back behavior while playing.
"""),
]

def main():
    for title, brief in AGENTS:
        prompt = PRE + "\n\nZone: " + title + "\n" + brief
        subprocess.run(["paseo", "run", "-d", "--title", title.split()[0], "--provider", MODEL,
                        "--thinking", "low", "--cwd", CWD, prompt], check=False)
        print("spawned", title, flush=True)

if __name__ == "__main__":
    main()
