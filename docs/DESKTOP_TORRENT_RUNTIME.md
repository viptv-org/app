# Desktop torrent runtime

Desktop implements the existing `TorrentRuntimeHost`/`TorrentRuntimePort` contract
with a private worker subprocess. It consumes readiness before the first reply,
caps messages at 6 MiB, serializes exchanges off the UI thread, enforces deadlines,
and terminates its owned worker on failure and JVM exit. Media remains on the
existing token-protected loopback endpoint and uses the existing grant bridge.

`tools/build_desktop_torrent.py` builds the revision in `TORRENT_RUNTIME_REF` and
ships dependency notices, checksums and protocol/clock metadata. Gradle includes
these resources in desktop packages. Linux and Windows use suspension-aware OS
clocks; macOS is advertised only with a worker pin providing its matching clock.

For an isolated working-tree qualification build, set `GO` to Go 1.25+ and
`VIPTV_TORRENT_CANDIDATE` to the sibling `playback-gateway/torrent-runtime` directory.
These artifacts are marked qualification-only. `tools/verify_desktop.py` rejects
them unless `--allow-candidate` is explicit; verification checks the exact bundled
worker and performs real open/shutdown/reopen through the packaged launcher.
Use the workspace's portable JDK for Linux packaging.

The private `VIPTV_TORRENT_BENCH_INPUT` entrypoint runs the production surface with
an explicit benchmark specification. It creates no account or user vault. The
renderer reports distinct drawn images; a changed playback position alone cannot
complete the seek measurement. Ordinary playback does not install a frame sink.

A verified torrent read can outlast KitePlayer's two-second quiescence deadline.
Local-torrent seeks retry its documented non-mutating refusal at most three times,
while preserving cancellation and other failure types. No source interrupt or
unverified-byte workaround is used.

The strict Stremio performance gate is still failed; Android qualification and
runtime pin promotion remain gated. See the sibling playback-gateway
`docs/TORRENT_PERFORMANCE_2026_10_22.md` and its numeric evidence for measurements and
scope. This task does not claim production rollout or native Windows/macOS playback.
