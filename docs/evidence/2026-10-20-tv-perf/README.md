# TV perf — 2026-10-20 confirmation of the wsrv-blur claim (PERF-10)

Device: ONN Streaming Device 4K pro (`192.168.88.30:5555`), 1920×1080 @ density
320, Android arm ABI, app renders 1080p. One run at a time, nothing else building.
Every run is a **release** APK and `tools/tv_ab.sh` (5 bursts × 16 presses), the
R3 metric.

## What was owed

Commit `07c44ab` claimed, from a single run, that moving the TV browsing-plane blur
from a runtime `Modifier.blur` pass to a wsrv-pre-blurred image gave
`gpu p50 15.0 → 11.0 ms`, `cpu p50 25.0 → 23.0 ms`, `missed vsync 11.4 → 9.9 %`.
The handoff recorded that as one valid run with a confirmation owed.

## The harness had to be fixed first

`tv_ab.sh` pressed Down twice from the hero and then burst Right/Left, without
asserting that the focused control scrolls a row. That silently measures whatever
the randomised Home puts under focus, which is how the `4950 ms` sentinel used to
be reported as a result.

`tv_ab.sh` now walks Down until the focused uiautomator node *is* a media card
(`content-desc` = "title, year", ≥ 150 px tall) and then proves the row pages:
Right must change the focused card, otherwise the row is a single item and the
script tries the shelf below. If nothing pages it aborts (`exit 3`) instead of
printing numbers. The focused shelf is echoed on every run, so two runs are
comparable only when they name the same shelf. `focus_mode=blind` reproduces the
old positioning for archaeology only and says so in its output.

Helper: `tools/tv_focus_node.py` (one focused node from a `uiautomator dump`).

## A/B, same harness, same device, alternating

`preblur` = `assembleRelease` with `SoftArtwork.kt` at `f0796e0` (runtime blur).
`HEAD` = `07c44ab`/`0fa7434` (wsrv pre-blur). Both are `install -r` over the same
app data, so the populated Home and add-ons are identical.

| build | apk sha256 | cpu50 | cpu90 | gpu50 | janky | missed vsync |
|---|---|---|---|---|---|---|
| preblur-a | `5addb958…4b5a` | 25.0 | 44 | 15 | 14.9 % | 8.3 % (64/769) |
| preblur-b | `5addb958…4b5a` | 25.0 | 48 | 16 | 14.8 % | 9.7 % (72/739) |
| preblur-c | `5addb958…4b5a` | 25.0 | 46 | 14 | 16.6 % | 10.4 % (80/771) |
| confirm-a | `7003467b…cf34` | 25.0 | 40 | 14 | 15.8 % | 8.2 % (67/821) |
| confirm-b | `7003467b…cf34` | 25.0 | 44 | 15 | 15.5 % | 8.9 % (70/787) |
| confirm-c | `7003467b…cf34` | 25.0 | 42 | 14 | 15.8 % | 9.4 % (78/828) |
| blind-d (HEAD, old positioning) | `7003467b…cf34` | 25.0 | 44 | 13 | 15.2 % | 9.1 % (71/781) |

Every run above focused `Love of Your Life, 2026` and paged to `Tenzing, 2026`,
except `blind-d`, which asserts nothing.

## Result

**The wsrv pre-blur gives no measurable frame win on this device.**

- `cpu p50` is 25.0 ms in all seven runs — the claimed 23.0 ms is not reproduced.
- `gpu p50` spans 13–16 ms across both builds; the claimed 11.0 ms appears in no
  run. `blind-d` (13 ms) shows the gpu percentile does move with the focused
  scene, which is the likeliest explanation for the original 11 ms sample.
- Missed vsync: preblur 8.3 / 9.7 / 10.4 %, HEAD 8.2 / 8.9 / 9.4 %. Overlapping
  ranges, difference ≈ 0.8 pp at best — not separable from run-to-run noise at
  n=3, and nothing like 11.4 → 9.9 %.
- Therefore the ledger's previous "before" band (11.2–12.0 % missed) is also not
  a reproducible baseline: the same pre-blur code measured 8.3–10.4 % here. The
  old numbers came from the unasserted harness, not from the code.

The blur itself is still on screen and still blurred (`home-now.png` in the
session's /tmp, backdrop visible behind the hero, all tiles populated), so this is
a performance finding, not a visual regression: `BufferedArtwork` loads a wsrv
image in both cases (sizing was already proxy-based), and the runtime blur pass is
simply cheap on this GPU.

## What this changes

- PERF-10 is **not done**: measured, no win. The decision now is product-shaped —
  keep the wsrv request (one extra remote image request per backdrop, same pixels)
  or restore the runtime blur (one less network dependency, same pixels).
- PERF-06's baseline is ~8–10 % missed vsync, not 11–12 %; the remaining gap to
  < 1 % is still `swap_wait` (compositor/GPU), unchanged by this experiment.
- Any future TV number must name the focused shelf printed by `tv_ab.sh`.

## Reproduce

```
./gradlew :androidApp:assembleRelease --max-workers=2 --no-daemon
adb -s 192.168.88.30:5555 install -r androidApp/build/outputs/apk/release/androidApp-release.apk
tools/tv_ab.sh <label> 5 16
```

Raw `dumpsys gfxinfo` per burst: `docs/evidence/2026-10-20-tv-perf/<label>/0..4.txt`.
