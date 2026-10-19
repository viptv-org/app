# PLAY-02 — gateway reachability per target (static proof)

Claim under test: on desktop and phone (Android), no playback path can reach the
server playback gateway; only the device-local engine plays media (rule R6).

## The single decision point

`composeApp/src/commonMain/kotlin/org/viptv/app/state/ControllerPlayback.kt`

```
val store  = local                     // NativeLocalStore / LocalConnection
val remote = connection
val lease  = if (store == null) requireNotNull(remote).openPlayback(source, profile, forceGateway = …, positionMillis = …) else null
val opened = store?.openSource(source.id) ?: requireNotNull(lease).input
…
remoteSeek = if (lease?.reopensForSeek == true) { … }
```

- The remote lease — and therefore every gateway-only fact (`force_gateway`,
  `reopensForSeek`, `remoteSeek`, first-frame acknowledgement) — is constructed
  **only when `store == null`**.
- Desktop always supplies one: `Main.kt` passes
  `localDatabase = "$base/viptv-experiment/local.sqlite"` (lines 131 and 151), so
  `local` is non-null for the whole session.
- Android always supplies one: `MainActivity` calls
  `model.start(filesDir.resolve("local.sqlite").absolutePath)` and passes
  `localDatabase` into `App`.
- The only `openSource(..., forceGateway = true)` call site is the re-seek lambda
  (`ControllerPlayback.kt:41`), which belongs to a *remote* lease; with a local
  store it re-enters `openSource`, which again returns `lease = null`. There is
  no path from a local store to `openPlayback`.

## Secondary paths checked

| Path | Result |
|---|---|
| `retrySource` | calls `playSource(source)` → `forceGateway = false` → local store → no gateway |
| pause/resume, lifecycle suspend | reuses the already-opened `PlaybackInput`; no new transport |
| subtitles | `store.subtitleContent(...)` when a store exists; remote call only in the `store == null` branch (PLAY-01) |
| first-frame / tracking | tracking attaches only when `lease != null && remote != null`, i.e. the remote branch |
| browser/wasm | `localConnection()` errors by design (remote-only model), so it is a deliberate exception, not a leak |

## Conclusion

Desktop and Android have **zero reachable gateway paths**: the transport is
chosen once in `openSource` and a device-local store makes the remote branch
unreachable. iOS remains remote-only by architecture (PLAY-03, owner decision).
