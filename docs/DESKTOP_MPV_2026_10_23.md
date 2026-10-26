# Desktop mpv migration — 2026-10-23 UTC

Desktop playback now uses libmpv instead of KitePlayer. The application keeps its existing playback facts, commands, embedded Compose controls, provider subtitles, required-audio selection and torrent interface. Kite dependencies, the patched Maven repository, publisher task, SDK provenance bundle and obsolete adapters have been removed. Package verification rejects retired jars and classes.

The player owns native frame images explicitly, bounds demux caching to 64 MiB ahead plus 16 MiB behind, serializes control calls, and frees the renderer before joining native shutdown. HTTP headers use native node arrays, preserving commas, quotes and UTF-8 without option-string parsing. Required audio is acknowledged before playback/presentation is authorized. Cancelled subtitle acquisition is joined before a newer choice is applied. Verified native bytes are reused in a private cache under an extraction lock, including Windows where loaded DLLs cannot be deleted at exit.

Compose's desktop render delegate records pictures without a current GL context. This implementation therefore uses mpv raster output with native copy-back decoding where available, preserving Compose overlays on all desktop targets. It does not claim GPU zero-copy rendering or 4K/HDR performance qualification. Source frame-rate diagnostics are explicitly distinguished from observed image draws.

libmpv requires the process-wide `LC_NUMERIC` locale to be `C`; the JVM's startup `setlocale(LC_ALL, "")` leaves the host locale in place, which makes `mpv_create()` return NULL on any normal desktop locale (app#4). `MpvSession` therefore re-asserts `LC_NUMERIC=C` through libc immediately before creating a client, on both Windows CRTs (`msvcrt` and `ucrtbase`) because the bundled mpv may link either one. Java-side formatting uses `java.util.Locale`, so the rest of the app is unaffected.

## Linux observations

Three cold-cache torrent smoke runs used the optimized e49dca8 worker, an owned virtual display and a null audio sink. Each watched 45 seconds, sought to 95% of the observed movie duration, watched 15 seconds, sought back to 30 seconds and watched 15 seconds. Recovery requires three new destination-image draw reports. The smoke threshold is at least 20 drawn fps, no sampled frame age above 500 ms, and timeline advancement of at least 80% of wall time.

| Movie | First image | Seek 95% | Seek back | Draw fps start / end / back | Peak player RSS |
| --- | ---: | ---: | ---: | --- | ---: |
| Dune Part Two | 3.160 s | 0.844 s | 0.250 s | 23.51 / 23.43 / 23.66 | 788.7 MiB |
| Shawshank Redemption | 1.968 s | 0.265 s | 0.220 s | 23.68 / 23.41 / 23.93 | 771.0 MiB |
| Turbo Kid | 1.897 s | 2.574 s | 0.733 s | 23.41 / 23.42 / 23.70 | 689.6 MiB |

All nine phases passed; no sampled freeze over 500 ms occurred. These draw rates still leave room for microstutter improvement; a successful screen is not proof of perfect cadence.

A separate packaged Turbo Kid run added **180 seconds of playback** after forward and backward seeks. It recorded **0 one-second intervals without new image draws**, recovered the forward seek in **1.677 s** and backward seek in **0.180 s**. Player RSS peaked at **503.4 MiB**. Thirty-to-sixty-second median RSS was 478.1 MiB; 120–170 seconds was 501.1 MiB. This is a short stability check, not an hours-long bound.

The comparable earlier HEVC test reached about 7.14 GiB in the Kite player with our worker and 7.13 GiB with Stremio, despite a 512 MiB Java heap limit. The mpv HEVC smoke player peaked at 689.6 MiB with the same heap setting: about **91% less**. Replacing the player removed the observed multi-gigabyte growth in these checks. This does not isolate a particular internal Kite allocation bug.

The earlier matched Turbo Kid seek comparison was effectively tied: ours 3.851 s versus Stremio 3.837 s near the end, and 0.315 s versus 0.311 s back. That comparison used the previous renderer on both engines. The mpv torrent runs here are unpaired; they do not establish a new Stremio advantage or qualify all 21 sources.

## Final package checks

- 13 targeted desktop tests passed: native header framing/ABI, subtitle preferences, required-audio selection, private error redaction and worker lifecycle.
- Actual packaged PlayerScreen controls were visible above decoded owned video; focused mouse automation confirmed pause/resume and timeline seeking. Screenshots were inspected.
- The final scripted owned-media test acknowledged a first rendered image while initially paused, then passed playback, pause/resume, precise seek, volume, speed, audio switching and provider subtitle off/on/scale changes.
- Native load/render/free/shutdown passed four consecutive instances. The package's JVM, Rust/SQLite resource, torrent worker and every native mpv file passed checksum/lifecycle verification. The Linux build used mpv 0.41.0 and client ABI 2.5.
- Desktop creation and tests reused existing Rust and Go build outputs (`-x buildDesktopEngine -x buildDesktopTorrent`); those packaged bytes were checked against their packaging inputs. This migration does not promote the explicit e49dca8 benchmark worker into the existing 674b715 packaged worker pin.

| Platform | Status |
| --- | --- |
| Linux x86-64 | Desktop build/package, owned media controls, torrent smoke and memory screen passed on an isolated software display |
| macOS | Shared adapter plus Homebrew dylib closure, loader-path rewriting and signing implemented; native execution unqualified |
| Windows | Shared adapter plus MSYS2 DLL closure and private dependency loading implemented; native execution unqualified |
| Physical GPU/display/audio | Unqualified |

Native packaging sources: [mpv installation guidance](https://mpv.io/installation/), [Homebrew mpv](https://formulae.brew.sh/formula/mpv), [MSYS2 UCRT64 mpv](https://packages.msys2.org/packages/mingw-w64-ucrt-x86_64-mpv). `tools/build_mpv.py` also accepts a preassembled checksummed runtime through `VIPTV_MPV_RUNTIME_DIR`. No native Mac/Windows runner result is inferred from Linux.

[Numeric evidence and artifact identities](evidence/desktop-mpv-2026-10-23.json) retain every screen row, resource sample and qualification boundary. Raw jobs, source snapshots, build logs and owned screenshots remain under `/mnt/ALPH/.viptv-qa/mpv-migration-20261023/`. An initial Compose labelled-return bytecode failure was corrected; an initial unfocused mouse click did not acknowledge pause and was repeated with an explicitly focused window. Neither is counted as a passing playback result. A test-only Skiko software-GL blocklist bypass was used while investigating interop; production Skiko is unchanged, and it does not qualify an mpv GPU path.
