# Read-only audit — testing/gates (TEST-01..10) and docs staleness (DOC-01..04)

**Date:** 2026-10-19 08:35 EDT. **Repo:** `/mnt/ALPH/code/viptv-org` (app = `/mnt/ALPH/code/viptv-org/app`).
**Method:** source/docs/board reading only; no gradle, adb, emulator or builds were run. No build artefact
was used as evidence (a live concurrent `desktopTest` run was observed via `composeApp/build/test-results/`
mtimes 08:29–08:38 and is explicitly *excluded* from conclusions).

**Tree state while auditing (changes concurrently):** app HEAD `ffebb7d` (2026-10-19 07:53) with **30
dirty/untracked paths**, incl. modified `App.kt`, `DesktopTitleBar.kt`, `Main.kt`, `TvCardVisualTest.kt`
and untracked `features/history/TvHistoryScreen.kt`, `TvFocusReachabilityTest.kt`, `tools/tv_focus_audit.py`,
`tools/board_spec.py`, `tools/spawn_audits.sh`. Design repo HEAD `d13f878` (2026-10-18, "264 boards").
Evidence dir `app/docs/evidence/` contains **nothing newer than 2026-10-16 03:02** — every Oct-19 change is
currently unverified by captures.

---

## A. What gate commands actually exist

**Gradle (app):**
- `app/build.gradle.kts` — plugin/versions only (KMP 2.4.21, Compose 1.13.0-alpha02, AGP 9.1.0).
- `app/composeApp/build.gradle.kts` — targets: android (12), jvm desktop (13), wasmJs browser executable
  (14), iOS frameworks (15-17); `nativePlayerMain` custom set (19-27); `commonTest` deps declared at 84-88
  **but no `src/commonTest` directory exists** — all tests are JVM `desktopTest`; `prepareKitePlayer` exec
  gate for desktop compile tasks (92-100), `buildDesktopEngine` for `desktopProcessResources` (102-108).
- `app/androidApp/build.gradle.kts` — release build type minify+shrink (29-35), `qa` build type with debug
  CA/manifest (36-45), Rust engine exec gate on `preBuild` (72-76). **No test source sets at all**
  (`src/main`, `src/debug` only) — Android target is compile-only.

**Pi workspace gate recipes** (`~/.local/share/pi-integrations/viptv.mjs`, read-only copy of
`.pi/workspace-tools.json`'s helper implementation; `viptv_gate` tool):
- `desktop_test` = `./gradlew :composeApp:desktopTest --max-workers=2 --no-daemon` (viptv.mjs:32)
- `android_compile` = `./gradlew :androidApp:compileDebugKotlin ...` (viptv.mjs:33)
- `android_apk` = `./gradlew :androidApp:assembleDebug ...` (viptv.mjs:34) — **no `assembleRelease` recipe**
- `ios_klib` = `./gradlew :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileKotlinIosArm64 ...`
  (viptv.mjs:35) — exactly matches TEST-03
- `backend_test` / `core_test` (viptv.mjs:36-37); heavy jobs serialized on the `viptv-build` resource
- **Wasm deliberately excluded**: "No Wasm build recipe is offered because the default output is served to
  a physical TV" (viptv.mjs:46,50; also workspace `AGENTS.md`).

**Device/tools scripts** (`app/tools/`): `tv_ab.sh` (A/B frame benchmark, missed-vsync metric; adb hard
timeouts at 9-12), `tv_bench.sh`, `tv_frames.sh`, `tv_framestats.sh`, `tv_cadence.sh`, `tv_trace.sh`,
`tv_state_shot.sh` (deterministic screencap), `tv_cycle.sh` (release build + install + bench),
`tv_focus_audit.py` (D-pad/uiautomator focus walk), `qa_desktop_run.py` (owned Xvfb + null audio sink
desktop probe), `verify_desktop.py` (packaged launcher/JVM/Rust-bytes smoke),
`qa_lifecycle_run.py`/`qa_16k_*`/`qa_endurance_run.py` (emulator runtime qualification),
`spawn_audits.sh` (read-only audit subagents), `board_spec.py` (board geometry — verified working).

---

## B. TEST-01..10 — mapping, coverage, status

Legend: **GREEN** gate + current evidence · **YELLOW** exists but stale/partial · **RED** mechanism
missing/broken · **UNVERIFIED** no evidence for the current tree.

| ID | Actual command / mechanism | Current coverage | Status |
|---|---|---|---|
| TEST-01 | recipe `desktop_test` (viptv.mjs:32) → `:composeApp:desktopTest` | 107 test files, ~321 `@Test` in `composeApp/src/desktopTest/` (incl. 27 `ImageComposeScene` UI tests); last committed green receipt **318 tests, 2026-10-16** (`docs/KMP_COMPLETION_RECORD.md` step 5); stability fix documented in `docs/STARTUP_SHELL_TEST_STABILITY.md` | **UNVERIFIED for current tree** |
| TEST-02 | compile half: recipe `android_compile` (viptv.mjs:33). Release half: **no recipe**; only `tools/tv_cycle.sh:8` (broken, see E1) and historical manual runs (`docs/evidence/kmp-final-2026-10-15/summary.json`: `:androidApp:assembleRelease` passed 2026-10-15; `artifacts/android-release-2026-10-15/` = 11 immutable candidates) | No Android unit tests exist at all (compile-only target); last release receipt 2026-10-15/16 predates 8 commits + dirty tree | **YELLOW — release half un-gated; both halves unverified for current tree** |
| TEST-03 | recipe `ios_klib` (viptv.mjs:35) — exact match | Verified **2026-10-16 exit 0** from Linux (`docs/KMP_COMPLETION_RECORD.md` step 1, ledger row "iOS platform"); framework linking/simulator impossible from Linux (root `AGENTS.md`, `iosApp/README.md`); `iosSimulatorArm64Test/LanguageTagsTest.kt` exists but **no gate compiles or runs iOS tests** | **YELLOW — verified pre-Oct-19 changes; needs rerun** |
| TEST-04 | No build recipe (excluded, viptv.mjs:46,50). Procedure: `VIPTV_WEB_OUTPUT=<dir> --init-script /tmp/viptv-isolated-web.gradle`, documented **only** in `docs/AGENT_HANDOFF_2026-10-14.md:106,117` | Last isolated-wasm receipt: `kmp-final-2026-10-15/summary.json` (`production_wasm: "passed; isolated output only"`, 109 protected live files byte-identical). **`/tmp/viptv-isolated-web.gradle` no longer exists** and no copy is committed anywhere (searched repo + `/mnt/ALPH` + home); nothing in `composeApp/build.gradle.kts` redirects or guards the default dist | **RED — redirect mechanism lost; only procedural discipline remains (third dist-incident risk)** |
| TEST-05 | `tools/tv_state_shot.sh` (screencap), `tv_bench/ab/frames/framestats/cadence/trace.sh`, `tv_focus_audit.py`; physical ONN default serial `192.168.88.30:5555` (tv_state_shot.sh:11); QA APK at `artifacts/android-tv-2026-10-15/viptv-android-tv-qa.apk` | All named surfaces have screenshots (`docs/evidence/android-tv-{home,movies,movie,episodes,sources,player-*,settings-*}.png` + `tv-player-*.png`) — **all dated 2026-10-12, from the disposable TV emulator (5582)**, not the physical TV; predate the Oct-13→19 fixes (settings rebuild `8e0ee9c`, billboard `83400eb`, uncommitted focus work). Physical TV: **zero saved screenshots** (see C) | **YELLOW/STALE** |
| TEST-06 | No dedicated script; manual emulator captures | Portrait `1080×2400` + one landscape `2400×1080` (`android-player-controls.png`) emulator captures, **Oct 12**; recent "phone passes" (Oct 15/16) are desktop-JVM `ImageComposeScene` renders at `390×844/844×390` (`evidence/phone-player-actions-2026-10-15/proof.json`: "no … physical Android qualification"; `phone-player-states-2026-10-16/summary.json`: "target: desktop jvm"). Ledger admits "Actual physical-phone landscape/decoder control acceptance remains" (`REMAINING_KMP_WORK.md:22`) | **YELLOW/STALE — portrait+landscape emulator evidence exists only pre-redesign** |
| TEST-07 | `qa_desktop_run.py` (owned Xvfb, **software render**), `verify_desktop.py` (packaged smoke), `viptv_desktop` tool (real display or virtual Xvfb) | Window captures `desktop-*.png` **Oct 12**; Linux desktop evidence Oct 15 = packaged-launcher Xvfb runs (`evidence/linux-desktop-*-2026-10-15/`). Window chrome was rebuilt in `207fef4` (2026-10-19, "desktop window framing", `Main.kt` +20) and `DesktopTitleBar.kt`/`Main.kt` are **currently uncommitted-modified** — no capture exists for either. Headless skiko `GlobalSnapshotManager` CCE and stale-incremental `NoClassDefFoundError` still open (OPEN_TASKS "Audit findings"; ISSUES NAV-19/20) | **UNVERIFIED — and no run satisfies "real window, X11, hardware render" for current code** |
| TEST-08 | Isolated-browser harness (real Chromium; `#ui-preview`/`#playback-qa` routes per `docs/UI_PREVIEW.md`) | Real-browser evidence: `web-*.png` (Oct 12), `browser-account-*.png` (Oct 14), `evidence/browser-playback-2026-10-15/`, `browser-hls-failures-2026-10-15/` (+ `BROWSER_PLAYBACK_COMPLETION.md`, `BROWSER_HLS_FAILURES.md`). `composeApp/src/wasmJsMain` changed after that (`3c3b96c`, `3109c0c`, `ff77b5d`) and shared commonMain changed Oct 19 — **no browser re-pass since** | **YELLOW/STALE relative to sources** |
| TEST-09 | Opt-in capture env vars in desktopTest UI tests: `VIPTV_CARD_CAPTURE_DIR` (TvCardVisualTest.kt:60), `VIPTV_SETTINGS_CAPTURE_DIR` (TvSettingsVisualTest.kt:32), `VIPTV_TV_FIX_CAPTURE_DIR` (TvEntryAndSourcesUiTest.kt:80), `VIPTV_PHONE_PLAYER_CAPTURE_DIR` (PhonePlayerStatesUiTest.kt:108); diffing with `compare_images`/PIL per `docs/TV_FRAME_BUDGET.md:25-29` | Harness exists and is the R2 mechanism, but: **no fixture PNG baselines are retained in the repo** (captures go to `/tmp`, e.g. `VIPTV_PROFILE_CAPTURE_DIR=/tmp/viptv-profile-administration-qa` in kmp-final summary); no automated diff step in Gradle; `docs/design-coverage.json` (Oct 15) is the only board↔capture ledger: 197 entries, statuses only `pending` 117 / `in_progress` 80, **zero verified**; 61 referenced captures all exist on disk, but the ledger predates the 264-board export (no `Tv3SetSources` entry at all) | **RED/PARTIAL — mechanism opt-in only; no retained baselines; stale ledger** |
| TEST-10 | `tools/tv_ab.sh` (the trusted metric per `docs/TV_FRAME_BUDGET.md:17-20`; adb-guarded at 9-12, missed-vsync aggregation at 57) + `tv_cycle.sh` for the release path | Documented A/B result is current: `TV_FRAME_BUDGET.md:31-39` (missed vsyncs 7.3-7.8% → 1.6-4.0%, corrected by commit `f732f08`), rejected experiments recorded (49-90). Raw dumps only in `/tmp/ab_base_*.txt`, `/tmp/ab_baseline2_*.txt` — **not saved in repo**. `tv_ab.sh` measures whatever APK is installed (no release-build check — R3 discipline is procedural). `tv_cycle.sh` (build release → install → bench) is **broken** (E1) | **YELLOW — gate + numbers current; raw evidence ephemeral; release enforcement and wrapper broken** |

### C. Device passes with **no saved evidence** (explicit list)

1. **Physical ONN TV (192.168.88.30:5555): no screenshots exist for any surface.** All physical-TV
   results are numeric-only: `docs/evidence/build-evidence.json` entries `"recording": false`
   (`physical_tv_current_home`, `physical_tv_current_detail`), and `docs/TV_HOME.md:32` explicitly
   documents an "**unrecorded** 371-frame navigation sample" (21 ms median / 1.08% missed).
2. **TV details row-scroll change** (commit `207fef4`, 2026-10-19): `OPEN_TASKS.md:17` — "needs on-device
   verification"; none exists. Note ISSUES.md:78 marks NAV-03 "DONE … V: device screenshot" for the same
   details screen — contradictory status between the two ledgers.
3. **TV Settings → Source integrations rebuild** (commit `8e0ee9c`, 2026-10-19, to board `Tv3SetSources`):
   `OPEN_TASKS.md:21` marks it `[x] done + verified`, but the only verification is
   `TvSettingsVisualTest.kt` which contains **zero assertions** (grep: 0 assertEquals/assertTrue) and only
   captures a PNG when `VIPTV_SETTINGS_CAPTURE_DIR` is set (line 32) — no saved capture, no device pass,
   no `design-coverage.json` entry.
4. **Phone player states pass (2026-10-16)**: `REMAINING_KMP_WORK.md:22` claims "captures in
   `docs/evidence/phone-player-states-2026-10-16/`" — that dir contains **only** `summary.json`. The six
   PNGs actually exist but were stranded by a path bug at
   `app/composeApp/docs/evidence/phone-player-states-2026-10-16/` (untracked; the test JVM resolves the
   relative capture dir against the module dir, not the repo root).
5. **Positioning/geometry audits**: `OPEN_TASKS.md:33` "agents running" and the Process row 93 reference
   `/tmp/viptv-ui-audit-{tv,phone,desktop}.md` and `/tmp/viptv-geometry-{tvhome,tvrest,desktop}.md` — only
   `viptv-ui-audit-desktop/phone` and `viptv-geometry-tvrest/desktop` exist; **`-tv` and `-tvhome` reports
   are missing** (and all of them are `/tmp`-only, i.e. not durable evidence).
6. **TV focus audit (in flight)**: `/tmp/focus-settings.md` (mtime 2026-10-19 08:23, emulator-5554) shows a
   concurrent focus walk — its output lives only in `/tmp`; not linked from any doc.
7. **Desktop window framing** (`207fef4` + uncommitted `DesktopTitleBar.kt`/`Main.kt`): no captures.
8. **A/B frame runs**: `/tmp/ab_base_*.txt` / `ab_baseline2_*.txt` back the TV_FRAME_BUDGET table but are
   not retained anywhere in the repo.

---

## D. DOC-01..04 — staleness audit

| ID | Claim (ISSUES.md:165-168) | Finding | Status |
|---|---|---|---|
| DOC-01 | `copy.md`, `components.md`, `decisions.md` updated for the 264-board export (`docs/process/DESIGN_SYNC.md`) | The 264-board import `d13f878` (2026-10-18) touched only `CHANGELOG.md`, `README.md`, `reference/FILES.json`, `reference/canvas-data-dictionary.md`, `reference/screens/index.json` and the boards (`git show --stat`). **None of the three named docs were updated**: `copy.md` last commit `d956279` 2026-10-07; `decisions.md` last `3eece98` 2026-10-11; `components.md` only a 2-line change in the earlier 197-board import `b6bd7d2` (2026-10-18). The new browse/source-integration/parental/recovery boards (67 new screens per CHANGELOG) have no narrative spec. | **RED** |
| DOC-02 | `docs/OPEN_TASKS.md` + `docs/UI_INVENTORY.md` kept current | Both are new (commit `207fef4`, 2026-10-19) and largely accurate: `Screen` enum matches `state/ApplicationState.kt:13`; board counts (UI_INVENTORY:5) match the actual dirs (tv 56 / phone 69 / tablet 18 / desktop 55 / web 40 / simkl 26 = 264, `reference/screens/html/*`); missing-board list matches the real board inventory. **Stale bits:** (a) UI_INVENTORY:28 "History … TV has no board at all" — boards true, but an untracked `features/history/TvHistoryScreen.kt` (135 lines, wired at `App.kt:115`) now exists, so the "what we have" side is outdated; (b) `OPEN_TASKS.md:21` `[x] verified` for the sources rebuild with no verification artefact (C3); (c) `docs/design-coverage.json` — the referenced coverage matrix — is from the 197-board era (Oct 15, 197 entries, 0 verified); (d) `REMAINING_KMP_WORK.md:22` points at an empty evidence dir (C4); (e) audit reports referenced at OPEN_TASKS:33/93 partially missing (C5); (f) ISSUES NAV-03 vs OPEN_TASKS:17 contradiction (C2). | **YELLOW** |
| DOC-03 | `docs/TV_FRAME_BUDGET.md` extended with every accepted/rejected perf experiment | File dated 2026-10-19 (94 lines) and current: documents the 4 accepted experiments (a11y node collapse, alpha-toggled badges, row-scope fade, typography identity — ThemeTypography.kt:12 confirmed present), 2 rejected ones (offscreen backdrop, shelf-scroll hoisting with per-variant numbers, lines 49-90), the measurement method, and the deterministic-capture verification rule; numbers were corrected by `f732f08` and match OPEN_TASKS' perf section. Only caveat: raw run outputs live in `/tmp` only. | **GREEN** |
| DOC-04 | Missing boards documented: TV Calendar, TV History, TV Recovery, TV Settings→Account | Verified against the actual 264-board inventory: no `Tv3Calendar`, `Tv3History`, `Tv3Recovery`, `Tv3SetAccount` among the 56 `html/tv/` boards. All four are documented in `UI_INVENTORY.md:28-31` ("genuinely missing a board") + `:77`, in OPEN_TASKS "Missing boards" (Design section), and Recovery via the "Recovery codes — none on TV" row. | **GREEN** |

---

## E. Cross-cutting findings (ranked)

1. **`tools/tv_cycle.sh` is broken** (TEST-10/TEST-02 release path): line 9 calls `adb_t` and `$SERIAL`,
   but the script defines neither (`set -eu`, no sourcing of the helpers every other `tv_*.sh` defines at
   lines 9-11). It cannot run as-is — the "build release APK → install on ONN → benchmark" one-shot
   loop that enforces R3 does not exist in working form.
2. **Wasm isolation mechanism is lost** (TEST-04): the only documented redirect
   (`/tmp/viptv-isolated-web.gradle` + `VIPTV_WEB_OUTPUT`) is gone from `/tmp` and was never committed;
   two prior dist incidents were caused by exactly this gap (`AGENT_HANDOFF_2026-10-14.md:94,106,117`).
   A repo-committed init script or a Gradle-level destination guard is the minimal fix.
3. **All Oct-19 work has zero capture evidence**: newest file in `docs/evidence/` is
   `phone-player-states-2026-10-16/summary.json` (Oct 16 03:02), while app HEAD moved through
   `ffebb7d` (Oct 19 07:53) plus 30 dirty paths. TEST-05/06/07/08 evidence is uniformly older than the
   code it should verify.
4. **The "latest gate" receipts are scattered and mutually inconsistent counts** (91 → 122 → 229 → 236 →
   287 → 313 → 318 documented across `build-evidence.json`, `ANDROID_TV_FIXES_2026-10-15.md`,
   `STARTUP_SHELL_TEST_STABILITY.md`, `PROFILE_ADMINISTRATION.md`, `HOME_SEARCH_RECOVERY.md`,
   `ADDON_ACCOUNT_RECOVERY.md`/`STATUS.md`, `KMP_COMPLETION_RECORD.md`); current tree has ~321 `@Test`.
   Each receipt is date-stamped and honest, but there is no single current gate record; nothing post-dates
   2026-10-16.
5. **Test coverage is JVM-only**: `commonTest` deps exist (`composeApp/build.gradle.kts:84-88`) but no
   `commonTest` sources; Android has no test sources; the iOS test (`LanguageTagsTest.kt`) is never
   compiled/run by any gate. Device-side coverage lives entirely in unscripted manual passes + the
   separate emulator qualification docs (ANDROID_LIFECYCLE_PLAYBACK, ANDROID_OSD_40MIN_ENDURANCE, etc.).
6. **Fixture baselines are not kept** (TEST-09): every capture mechanism is opt-in via env vars, writes to
   disposable dirs, and nothing diffs them automatically; the `design-coverage.json` ledger has never moved
   any entry past `in_progress`.
7. **Small doc/evidence path bug**: phone player captures stranded under `composeApp/docs/evidence/...`
   (untracked) while the ledger cites `docs/evidence/...` (empty) — C4.

---

## F. Six-line summary

- Gates that exist and match TEST items: `desktop_test` (TEST-01), `android_compile` (TEST-02 half),
  `ios_klib` (TEST-03, verified 2026-10-16), `tv_ab.sh` + TV_FRAME_BUDGET (TEST-10) — everything else is
  procedure, history or broken tooling.
- RED: TEST-04 (isolated-wasm init script lost; only handoff prose remains) and TEST-09 (no retained
  fixture baselines, no automated diff, zero `verified` rows in a stale 197-entry coverage ledger).
- BROKEN: `tools/tv_cycle.sh:9` references undefined `adb_t`/`SERIAL`, so the release-build→install→bench
  loop behind R3/TEST-10 and the only scripted `assembleRelease` (TEST-02) cannot run.
- UNVERIFIED for the current tree: TEST-01 (last green receipt 318 tests on 2026-10-16; live run observed
  but not evidence), TEST-02, TEST-03 (pre-Oct-19 changes), TEST-07 (window framing rebuilt Oct 19, no
  capture; recent runs are Xvfb software render).
- Device passes with no saved evidence: every physical-ONN measurement ("recording: false", "unrecorded
  371-frame sample"), the Oct-19 TV details/settings changes, the phone-player-states captures (stranded in
  `composeApp/docs/evidence/`), and the missing `/tmp/viptv-ui-audit-tv.md` / `-geometry-tvhome.md` reports.
- Docs: DOC-01 RED (copy/components/decisions untouched by the 264-board import `d13f878`), DOC-02 YELLOW
  (new but contradicted by in-flight TV History screen, stale coverage matrix, empty evidence refs),
  DOC-03 and DOC-04 GREEN and factually current.
