# Android native torrent QA — 2026-10-22

**Eight offline functional checks passed on API 36 x86_64 phone and TV emulators.**
The normal pinned Go/JNI runtime at
`674b71523dff5a74fb339eae97ddcb6217ea9f8b` served real HTTP media to the production
KMP `PlayerSurface` and Media3 decoder. Screenshots and native renderer callbacks
confirmed displayed synthetic video and advancing playback. These checks do not
qualify Android cold-start performance or Stremio parity.

Public-swarm timing was deferred when the owner prioritized serialized Linux
cold-start optimization. No warm or prefilled-cache result is being substituted
for cold performance.

## What passed

Each emulator passed four isolated checks with a fresh owned runtime service:

- Render a generated H.264/AAC movie, retain 19 verified payload records across
  runtime restart, and reject deliberately corrupted and incomplete records on
  the next open.
- Kill the actual JNI worker process, refuse its old media endpoint, and reopen
  the cache in a new worker.
- Expire a short authority grant and refuse its media endpoint.
- Cancel a preparation before metadata is available and retire the worker.

Every final check confirmed both Binder death and absence of the actual
`:torrent_runtime` process. HTTP timeout alone is not accepted as revocation
proof. Both rendering checks used trusted preverified fixture cache records
while their emulators were offline; they are explicitly functional-only.

The phone used `viptv-native-qa` at serial `emulator-5574`. The designated TV QA
AVD was locked by another instance, so a new task-owned
`viptv-torrent-tv-20261022` AVD was created at `emulator-5576`. Both ran read-only
with software graphics. Their managed emulator jobs were stopped afterward.
Shared emulators and physical devices were untouched.

## Harness and measurement

`tools/qa_torrent_run.py` checks the exact owned serial/AVD before mutation and
uses a separate disposable `org.viptv.experiment.torrentqa` package. The extra
activity and shared-library QA sources compile only with
`-Pviptv.torrentQa=true`. Normal source pins and production JNI libraries are
used; there is no private-peer policy worker substitution.

The production Media3 presentation fact now carries the native render timestamp
from `AnalyticsListener.onRenderedFirstFrame`, whose `renderTimeMs` uses
`SystemClock.elapsedRealtime`. Its existing `NativeRendererPresented` proof and
`exact=false` classification remain unchanged; this is not physical scanout
proof. [Media3 callback contract](https://github.com/androidx/media/blob/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/analytics/AnalyticsListener.java).

Future cold runs require a unique empty cache and new app/runtime processes.
Their timer starts before runtime connect/open/prepare. Reference admission is
gated by a readiness marker, with reference cache clearing outside the timer.
Both paths use the same production Media3 surface and local-torrent read timeout.
The reference runner still needs validation of its foreground/control overhead
before any strict Android performance comparison is claimed.

## Evidence and limitations

[Numeric evidence](evidence/android-torrent-functional-2026-10-22.json) records
all eight final checks, exact APK and Go/JNI library hashes for all three packaged
ABIs, source hashes and emulator fingerprints. The APK's Go library reports
**go1.25.11**. Only x86_64 was executed; ARM ABI packaging is not device runtime
qualification. The build is an isolated debug QA APK, not a release/R8 or
ordinary account/source-screen qualification.

The official Android Mobile Stremio APK starts its bundled 4.21.0 server offline,
with stock `cacheSize: 0`; no comparative playback trials were run. The supplied
official TV reference APK remains available for later qualification.

Initial harness failures are preserved: the generic debug TLS policy forbids
loopback HTTP, ADB `exec-out` was unsuitable for stdin/remote-status handling,
and the production runtime correctly refused a synthetic torrent's private
flag. The opt-in torrent QA manifest has its own loopback-only cleartext policy;
the uploader/status reader now uses non-PTY shell calls, and the offline fixture
uses ordinary metainfo. Production peer-destination policy was not broadened.
The existing generic debug TLS policy remains separate from this QA manifest;
normal release already permits the private loopback media reader.

Raw receipts, screenshots, earlier failed attempts, the frozen final APK and a
source checkpoint are under
`/mnt/ALPH/.viptv-qa/torrent-android-20261022/`. No production deployment occurred.
