# Android app updates

The Android app (phone, tablet, Android TV, Fire TV) updates itself from the
server it is signed in to (app#64). It checks quietly, downloads a newer build
in the background, verifies it and then shows **Update ready: Install**. One
tap opens Android's own install confirmation; sideloaded installs cannot be
silent. Desktop and web are out of scope.

## Versions

`androidApp/build.gradle.kts` derives the version from git, for debug and
release builds alike, so the same commit always builds the same version:

| Field | Value | Example |
|---|---|---|
| `versionCode` | `1000 + git rev-list --count HEAD` | `1312` |
| `versionName` | `0.1.<count>+<7-char sha>` | `0.1.312+a1b2c3d` |

A later commit on `main` always has a higher count, so every new `main` build
has a higher `versionCode`. A branch build has the count of its own history.
Merging the branch adds at least the merge commit, so `main` stays ahead of it.
Without git (a source archive) the build is `0.1.0+unknown` with
`versionCode` 1000. Builds from before this change are `versionCode` 1. Every
newer build upgrades over them, and none of them can be installed back over a
newer build without uninstalling.

Settings › About shows the version, the update state, the last check time and
**Check for updates** (**Install update** once a build is ready).

## Feed

```
GET <server origin>/updates/android/update.json
```

```json
{
  "version_code": 1312,
  "version_name": "0.1.312+a1b2c3d",
  "url": "https://server.example/updates/android/viptv-1312.apk",
  "sha256": "<64 hex characters>",
  "size": 104857600,
  "notes": "optional, shown in the prompt",
  "min_supported_version_code": 1200
}
```

- `version_code`, `version_name`, `url`, `sha256` and `size` are required.
  Unknown fields are ignored.
- `url` is the absolute address of the APK. It must be `https`; plain `http`
  is accepted only on `127.0.0.1`/`localhost`, for the local test feed. The
  host is checked with a URL parser, so userinfo cannot fake loopback. (A
  relative `url` is tolerated and resolves against `update.json`, but the
  publishing tool always writes an absolute one.)
- `min_supported_version_code` (optional): an installed build below it treats
  the update as required. The prompt says the version is no longer supported,
  and **Later** only hides it until the next return to the app.
- The origin is the server the app is signed in to (`<server origin>`; for
  now the KMP dev instance), so a future production server works unchanged.
  In local mode there is no feed.
- `-Pviptv.updateFeed=<url>` replaces the address at build time, e.g. a GitHub
  Releases `latest/download/update.json` if the repository goes public, or a
  loopback test feed. It must be `https`, or `http` on `127.0.0.1`/`localhost`.
- A 404, or an HTML page (the backend serves the web app for unknown paths),
  means the server publishes no updates. About then says so; nothing else
  happens.

## What the server must serve

Two static files under `/updates/android/`:

| Path | Content |
|---|---|
| `<server origin>/updates/android/update.json` | the feed above, `url` = `<server origin>/updates/android/viptv-<versionCode>.apk` |
| `<server origin>/updates/android/viptv-<versionCode>.apk` | the release APK it names |

`tools/publish_android_update.py` writes both from an APK:

```sh
python3 tools/publish_android_update.py androidApp/build/outputs/apk/release/androidApp-release.apk \
    <web root>/updates/android --base-url <server origin>/updates/android
```

It reads the version with `aapt2 dump badging` and refuses an APK for another
package. It copies the APK first and writes `update.json` last, each through a
rename, so the feed never names a missing file. It keeps the two newest APKs.
Publish only a full multi-ABI release build, never a `-Pviptv.abis` build.

On the dev instance the backend serves every unknown path from the web root
(`ServeDir` with an `index.html` fallback). The feed directory is therefore
`<web root>/updates/android/`. `ServeDir` answers `Range` requests, which the
app uses to resume a download, and sends `.apk` as
`application/vnd.android.package-archive`. A web redeploy that replaces the
web root must carry `updates/android/` over. The app sends
`Cache-Control: no-cache` for the feed, and APK names are unique per version,
so a cache in front of the server does no harm as long as it does not cache
`update.json` (Cloudflare does not cache `.json` by default).

## Signing

Release builds are signed with `signingConfigs.getByName("debug")`, the debug
keystore (`~/.android/debug.keystore`) of the host that builds them. Android
installs an update over the app only when both are signed with the same key,
and that keeps app data and sign-in. Every APK on the owner's devices was built
on the dev VPS, so:

- **Publish updates only from the dev host**, or from a build that uses the
  same keystore. An APK signed with another key fails to install
  (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`), and the app shows "Android didn't
  install the update."
- Never change the key: devices would have to uninstall, which loses
  sign-in and local data.
- A future CI or GitHub Releases feed needs this keystore moved into a CI
  secret first.

## Moving to GitHub Releases

The feed format and `-Pviptv.updateFeed` already allow a GitHub Releases feed
(`https://github.com/viptv-org/app/releases/latest/download/update.json`, whose
`url` is the release asset's absolute download address). Three things block it
today:

1. **Signing key.** Release APKs are signed with the dev host's debug
   keystore. CI must sign with that same key, so the keystore and its
   passwords have to move into a CI secret first (and the build read them from
   there). A CI build signed with any other key cannot update existing
   installs.
2. **Private build dependency.** Every Android build compiles the torrent
   runtime from `viptv-org/playback-gateway` (`TORRENT_RUNTIME_REF`,
   `tools/build_torrent_runtime.py`), which is private. CI needs read access
   to it (a deploy key or token secret) or a published prebuilt runtime. The
   alternative, `-Pviptv.torrentRuntime=false`, ships a build without native
   torrents.
3. **Private repositories.** While `viptv-org/app` is private, release assets
   need authentication to download, which the app cannot provide, and Actions
   minutes are capped. The repository has to be public (or the assets hosted
   elsewhere) before devices can use such a feed.

Once these are solved: build the multi-ABI release in CI, run
`tools/publish_android_update.py` with `--base-url` set to the release's
download address, attach the APK and `update.json` to the release, and build
the app with `-Pviptv.updateFeed` pointing at `latest/download/update.json`.

## When the app checks

- On launch, on every return to the app and when the player closes, at most
  once an hour, never while the player is open. **Check for updates** in
  Settings skips the hourly limit.
- Only a check that reached the feed starts the hourly limit; a failed or
  offline check is retried on the next return. The time is stored, so the
  limit holds across restarts.
- A quiet check that fails keeps an update that is already downloaded or
  downloading.
- The return-to-app check runs from `MainActivity.onStart`. App.kt's
  foreground hook (`ApplicationController.appForeground`) would be the shared
  place, but it is not installed on Android while `ApplicationModel` owns the
  controller (app#75).

## Download

- Into app-private storage (`filesDir/updates`), as
  `viptv-<versionCode>-<sha prefix>.apk`, with a `.part` file while it runs.
- An interrupted download resumes with a `Range` request. A failure is retried
  after 15 s, 1 min and 5 min; after that the next check starts again.
- The download pauses while the player is open and continues when it closes.
- Only a file whose size and SHA-256 match the feed is offered. A mismatch
  deletes it. The file is verified again right before installing.
- Other versions' files are deleted when a new version is found, and all
  files once the installed build is current.

## Prompt and install

- **Phone and tablet:** a banner at the bottom, **Install** · **Later**. It
  does not block the page.
- **TV:** a small D-pad dialog, **Install** (focused after a short delay, so a
  press meant for the page cannot start the install) · **Later**. Back is
  Later.
- Never over the player or the sign-in screens. **Later** hides the prompt
  for that version until the app restarts. A newer build asks again.
- **Install** checks Android's "Install unknown apps" permission for VIPTV.
  Without it the prompt explains it and **Open settings** opens that screen.
  Back in the app with the permission allowed, the install continues. Where
  the screen does not exist (some Fire TV models), the prompt gives the Fire TV
  path and **Try again**.
- The APK goes to a `PackageInstaller` session and Android shows its
  confirmation. Backing out of it acts as Later. Leaving it without an
  answer (Home) makes the update ready again on the next return, and a new
  Install starts a fresh session. Android itself refuses a
  lower `versionCode` and a different signing key. The app never offers the
  same or a lower `version_code`.

## Testing an upgrade locally

On the TV emulator, without the real server:

1. Build N with the feed pointed at a loopback port and install it:
   `-Pviptv.updateFeed=http://127.0.0.1:8765/updates/android/update.json`,
   then `adb -s <serial> install -r <apk>`.
2. Make a later commit and build N+1 the same way. Publish it into a scratch
   directory: `python3 tools/publish_android_update.py <apk N+1> <dir>/updates/android --base-url http://127.0.0.1:8765/updates/android`.
3. Serve the scratch directory: `python3 -m http.server 8765 --bind 127.0.0.1 --directory <dir>`.
   Then `adb -s <serial> reverse tcp:8765 tcp:8765`.
4. Open the app (or return to it). "Update ready" appears once the download is
   verified. Install, allow the permission if asked, confirm, and reopen.
   VIPTV is still signed in with the new `versionName` in Settings › About.

The code: `composeApp/src/commonMain/kotlin/org/viptv/app/updates` (feed,
policy, `AppUpdater` state machine, prompt) and
`composeApp/src/androidMain/kotlin/org/viptv/app/updates/AndroidAppUpdatePlatform.kt`.
The tests: `AppUpdateFeedTest`, `AppUpdatePolicyTest` and `AppUpdaterTest`
(desktopTest), and `AndroidAppUpdateTest` (Robolectric, androidHostTest).
