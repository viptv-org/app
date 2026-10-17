# KMP All-Platforms Completion Record

**Date:** 2026-10-16 (local); 2026-10-16T07:53Z (Simkl E2E run)
**Plan:** `.junie/plans/kmp-all-platforms-completion.md`
**Ledger:** `app/docs/REMAINING_KMP_WORK.md`
**Scope:** iOS, desktop, web/Wasm, backend, Android regression, Simkl E2E — every remaining row addressed or explicitly left open with cause.

## Steps completed

1. **Step 1 — iOS toolchain & Kotlin/Native scaffolding** — done.
   xtool not installable on this host (yanked dependency; main is a Swift package and no Swift toolchain exists here) — recorded in root `AGENTS.md`. `iosArm64`/`iosSimulatorArm64` targets added to `app/composeApp` and both compile clean from Linux; UniFFI Swift bindings for the app/native LocalEngine crate generated into `app/native/adapters/ios` (unqualified artifact pending an iOS SDK).
2. **Step 2 — iOS client** — done at compile level. Full `iosMain` actual inventory (19 expects, all with actuals — the kimi k3 review's full list): ktor-Darwin `Transport` (`browserOrigin() = null`), throwing browser-parity `localConnection`, NSUserDefaults `RemoteSessionStore`, force-gateway `playbackClientFacts` (`can_play_direct: false`, h264/aac), AVPlayer `PlayerSurface` with lease-aware seek/progress, `SFSafariViewController` account authorization, and the history/calendar/live-clock/language/image-cache/logo-trim/escape/addon-field actuals. No `nativePlayerMain` dependency (verified in klib manifests). `app/iosApp` Swift shell present. Runtime rows honestly open (no simulator on Linux).
3. **Step 3 — Backend + web** — done. Pairing-approval cases closed with full backend suite green (503 tests / 8 ignored) including the iOS platform in the v2 client_facts contract. Warm-Home startup budget re-measured (row remains `In progress` by its own numeric criterion, warm Home 1460ms > 1s). Board fidelity remains `Open` in the ledger — no false closure.
4. **Step 4 — Desktop** — done. Windows x64 + macOS aarch64 portable packages cross-built from this host (receipts in `app/build/portable-win-x64`, `app/build/portable-mac-aarch64`); runtime qualification explicitly open (no Windows/macOS host). Linux `.deb` row previously verified.
5. **Step 5 — Full E2E matrix + Simkl E2E** — done.
   - **Simkl E2E (real, qa-parent profile 1, live backend `https://viptv.local.test:18445`):** linked status (all 5 capabilities), calendar 100 entries, search 12 results incl. Inception (simkl:movie:472214), library add/list/remove round trip (200/200/200), tracking sync POST watched, pending→0, still linked after run. `passed: true`. Evidence: `/mnt/ALPH/.viptv-qa/unified-real-current-vxstvf2g/simkl-e2e-20261016-1792137205/summary.json` + `simkl-qualification-record.json` (summary sha256 pinned).
   - **Real playback qualification:** 83 streams discovered, admission 202, lease ready, gateway HLS, 6 segments (6,034,800 bytes, sha256 recorded), ffprobe h264 1920×816 + aac 48kHz stereo, ffmpeg 120 decoded frames, clean 200 close. `passed: true`. Evidence: `real-playback-current-20261015-1792137269/summary.json` (the `20261015` prefix is a pre-midnight-local labeling nit noted by the auditor; run executed 2026-10-16 03:53 local / 07:53Z).
   - **Desktop JVM suite:** 318 tests green (this session's build-graph changes).
   - **Android:** physical emulator runs not re-executed this session — retained evidence rows in the ledger (API26/API35/36 lifecycle, OSD endurance, native audits) remain the authority; no regression introduced (build verified, suites green).

## Independent audit (qwen code auditor)

**Overall verdict: PASS on all five items.** All claims map 1:1 onto the evidence artifacts; the record's sha256 matches recomputation; no unlink/revocation call was made against qa-parent; no overclaims of qualification status found. Three cosmetic nits were found and fixed where in-repo (expect count 22→19, `:compileApp:`→`:composeApp:` typo; the `20261015` playback dir prefix is a labeling nit in the private QA directory and was left as-is — it is hash-consistent).

## Honest open items (never claimed done)

- iOS runtime: framework linking, simulator execution, App Store/Swift shell, `.xcframework` cross-compile (all macOS/xtool-gated; xtool not installable here).
- Windows/macOS desktop runtime execution, physical audio/GPU, signing, MSI packaging.
- 197-board full fidelity sweep, manifest-preview/pairing board contracts.
- Android warm-Home 1s budget row (`In progress`).
- Physical device/TV qualification rows (human-gated per ledger constraint).
