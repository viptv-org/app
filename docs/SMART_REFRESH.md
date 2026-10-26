# Smart refresh — audit and proposed design (2026-10-20)

Status: first implemented in app `360e597` and backend `63b2ecd`. The foreground
gap, the no-baseline rule and the visible-Details reload were revised for
app#2 (2026-10-26); see the rules in section 2. A moved `addons` token also
reloads the add-on list since app#3 (2026-10-26).

## Audit

### How Home loads today

`refresh()` (`state/ControllerCatalog.kt`) runs on the foreground operation
lane `run {}`, which has three effects:

- It cancels whatever operation is in flight.
- It sets `busy` and clears `error`. Phone Home shows a "refreshing" row while
  `busy` is set.
- When Home is visible, it calls `accept()`, which rebuilds the whole
  `ApplicationState`. That drops the library page, Live state, navigation
  history and other state.

That is acceptable at sign-in. It cannot be used for a background refresh.

Home is re-fetched only in these cases:

- sign-in, app start or profile switch;
- Retry on a failed Home;
- entering Home with `policyRefreshNeeded` set;
- a finished Stremio import, add-on change or clear history.

Nothing re-fetches Home on foreground or resume, on a timer, or after
playback.

### Surface by surface

| Surface | When it loads | What changes it remotely or over time | Stale until |
|---|---|---|---|
| **Continue Watching row** | bootstrap only | Playback on **this** device (nothing patches the row after the player closes), playback on another device, Simkl sync, Stremio import, poster backfill, a next episode becoming available (time-based `queue_status`) | restart or profile switch |
| My List row and favorites membership | bootstrap and library hydration; this device's own toggles are patched | Another device, Simkl, Stremio import | restart |
| Hero and catalog rows | bootstrap | Add-on changes from web or another device, catalog content (server cache 300 s), Simkl trending | restart |
| Details (progress, watched, saved) | every open | Playback just finished on this device: Back from Player → Sources → Details shows the old snapshot | reopen |
| My List page, History, Browse, Live directory | every time the screen is entered | Changes made while the screen stays open | leave and re-enter |
| Calendar | on entry (Simkl only; server caches 5 h) | Day rollover while the screen is open | re-enter |
| Live guide | per channel, with a 1-min client gate | The "now" programme advancing | refocus the channel |
| Profiles, names, avatars | bootstrap | Edits from another device. The backend has **no revision** for these. | restart |
| Parental policy | bootstrap; local `policyRefreshNeeded` | Parent edits from another device (`kids_profiles.revision` exists but is not exposed to Home) | restart |
| Preferences | bootstrap | Another device (`preferences.revision` exists) | restart |
| Add-ons, account devices, Simkl link | when Settings or the subpage opens | rare | re-open |

Two backend behaviours make stale Home worse.

**Simkl sync starts during the Home build.** `home::load` itself spawns the
Simkl import (throttled to once per 60 s), so the sync results land after the
Home response that triggered them. Today they are never seen.

**The poster backfill bumps revisions.** The startup-only Stremio poster
backfill updates `progress`/`favorites` rows, which bumps the library
revision. A revision check would have picked up today's backfill.

### Change signals the backend already has (`backend/server/src`)

These are cheap integer reads from SQLite:

- per-profile **library revision**, bumped by triggers on progress, favorites,
  hidden, Simkl and Stremio import writes;
- per-profile **configuration revision**;
- per-profile **preferences revision**;
- per-profile **kids policy revision**;
- per-account **add-on revision**: `GET /api/catalogs/revision`, the only
  existing poll endpoint.

`HomeSnapshot.revision` is `configuration + library`.

There is no ETag, `If-None-Match` or `Last-Modified`. Every `/api` response is
`Cache-Control: no-store`. There is no SSE, WebSocket or push.

### Design constraints

Approved spec `design/specs/behavior/home-addon-refresh.md` (HOME-ADDON-001)
already requires:

- an opaque revision check on Home entry and on foreground return, then every
  15 s while Home is visible and the app is in the foreground;
- no polling in the background or on other screens;
- no catalog fetch while the revision is unchanged;
- checks never overlap, and a change that races a load causes a follow-up;
- no global loading cover, no scroll reset and no focus theft;
- the hero follows the retained selection;
- a failure keeps the current shelves.

`ANDROID_DESIGN.md` AND-041 adds:

- foreground return must not refetch all Home rows;
- coalesce repeated callbacks.

Neither is implemented in the KMP app. The specs say nothing about
pull-to-refresh.

## Proposed design

### 1. Backend: one cheap change signal (small)

**New endpoint.** Add `GET /api/profiles/:id/revisions`. It returns opaque
strings:

```json
{ "library": "…", "configuration": "…", "addons": "…", "policy": "…", "preferences": "…", "profiles": "…" }
```

The response is a handful of single-row SELECTs: no add-on, Simkl or metadata
I/O, and no Home build.

**New profiles revision.** Add an account-scoped `profiles` revision using the
existing trigger pattern: triggers on `profiles` name/avatar and
`engine_profile_presentation`, created in an idempotent init next to
`engine_v3::history::init`.

**Revisions in bootstrap.** Include the same object in `/api/bootstrap`. Read
it **before** Home is built, so a change that commits during the build shows
up on the next check instead of being lost (HAF-06).

**Why this instead of ETag/304 on bootstrap.** Bootstrap content also depends
on add-on catalogs and time. A revision-derived ETag would wrongly 304 those
cases. Per-domain tokens also let the client tell "My List changed" apart
from "add-ons changed".

### 2. Client: a `FreshnessCoordinator` (new, pure, unit-tested)

**Inputs** are events:

- `Foreground` / `Background`
- `ScreenEntered(screen)`
- `PlayerClosed(progressSaved)`
- `Tick(now)`
- `RevisionsObserved(map)`
- `RefreshFinished` / `RefreshFailed`

**Outputs** are actions:

- `CheckRevisions`
- `RefreshHome` (silent bootstrap)
- `ReloadLibraryPage`
- `ReloadHistory`
- `ReloadDetail`
- `ReloadProfiles`
- `ReloadCalendar`

**Rules:**

- **Foreground or entering Home/My List/History.** Run `CheckRevisions` every
  time. Skip it only when a check finished less than 2 s ago, or was
  requested since this return less than 2 s ago, so the start and resume
  callbacks of one return make one check.
- **Home visible and in the foreground.** Run `CheckRevisions` every 15 s, as
  the spec requires. Each check is one tiny DB-only request. The timer stops
  on other screens and in the background.
- **A token changed:**

  | Token | Action |
  |---|---|
  | `library`, `configuration`, `addons` or `policy` | silent `RefreshHome` |
  | `library` while My List, History or Details (also behind the sources sheet) is visible | also reload that page in place |
  | `addons` | also reload the add-on list (see below) |
  | `profiles` | `ReloadProfiles` |
  | `preferences` | reload preferences |

- **Add-ons changed elsewhere.** A moved `addons` token (account add-ons
  installed, removed, reordered, reconfigured, or their credentials changed)
  reloads the add-on list with Home: Settings → Add-ons, the enabled set and
  the browsing configuration. Only that token does this; a check where only
  other tokens moved makes no add-on request. Rules:
  - An add-on operation running on this device (install, enable, remove,
    provider choice, add-on refresh) is never interrupted. The quiet lane
    waits for it to finish, then rebuilds Home and reads the list it left
    behind. If another one starts while the list is being read, the lane
    waits for that one too and reads again, so it never overwrites a newer
    list.
  - A Browse page on display keeps its cards. Elsewhere the Browse state is
    dropped so the next Browse entry loads controls for the new add-ons.
  - A failed list read is silent and keeps the shown list. It is not retried
    until the token moves again; opening Settings (or its Add-ons section on
    the web) reloads the list anyway.
  - Restricted kids sessions see a constant `addons` token, so this never
    runs for them.
  - Enabling or disabling an add-on for one profile moves `configuration`,
    not `addons`. That rebuilds Home, but the add-on list waits for the next
    Settings entry.
- **Catalog cache lag.** The rebuilt Home builds its rows from the add-ons
  installed now (the server caps Home at the first 10 catalogs), so a removed
  add-on's rows disappear and a new add-on's rows appear on the same check. Row contents come from the
  server's add-on response cache, which keeps each catalog response for
  300 s. A server with a secret vault configured keys that cache by the
  add-on revision, so a moved token bypasses it. Without one the cache is
  keyed by address only, and a catalog address fetched in the last 300 s
  (for example an add-on removed and installed again) can show contents up
  to 300 s old. The next aged-Home rebuild (5 min, below) picks up the
  current contents.
- **No baseline.** A Home built without revisions cannot be proven current, so
  the first observation counts as a change and runs one silent `RefreshHome`,
  which sets the baseline.

- **Player closed** (after the final progress save joins). Run `RefreshHome`
  straight away with no check, because we know progress changed. Also run
  `ReloadDetail` if Back returns to Details, which keeps watched badges and
  resume correct. Continue Watching is then right before the user reaches
  Home.
- **Time-based staleness** (catalog contents, trending, next-episode
  availability, calendar "today"). The owner chose idle-time refresh over a
  long TTL. Home counts as stale once the last full Home is older than
  **5 min**; the server caches add-on catalogs for 300 s, so a shorter age
  cannot return new content. A stale Home runs `RefreshHome` even when the
  tokens are unchanged, at any of these moments:
  - foreground return;
  - Home entry;
  - while Home is visible, once the user has been **idle** for 20 s (no
    key, remote, touch or pointer input).

  A refresh never lands mid-interaction. Calendar reloads on foreground if
  the local date has changed.
- **Single flight.** One check and one refresh at a time. An event that
  arrives during a refresh sets a `again` flag, which runs one follow-up
  check (HAF-06).
- **Older servers.** If the revisions endpoint returns 404, fall back to the
  30-min TTL plus after-player refresh.
- **Local (on-device) mode.** There is no other writer, so only after-player
  refresh and the 30-min TTL apply.

### 3. Client: a quiet refresh lane (replaces `run {}` for background work)

- Launch the refresh on `scope`, owned by session, profile and connection
  identity. A stale result is dropped (HAF-05).
- Never touch `busy`/`error` and never cancel the user's operation.
- A failure keeps the last good data. A 401 goes through the existing
  `expireSession`.
- Merge into the current state instead of calling `accept()`:
  - replace `home`, library, membership, preferences and profiles;
  - keep the screen, navigation, Live, Calendar and paging state.
- Keep the loaded tail of a catalog row the user has already paged, unless
  that row's source changed. This stops a refresh from shrinking a row under
  the focused card.

### 4. UI: keep focus, scroll and the hero

- **Rows and cards.** These are already keyed by row id and `cardLookupKey`,
  so a surviving focused card keeps focus.
- **Removed focused card.** If the focused card disappears (for example, it
  was finished on another device), move to the nearest surviving card at the
  same index, then to the rail. `TvHomeShelf.lastFocusedIndex` already exists.
- **Hero carousel.** `HeroCarousel` keys its index by scope, so a reordered
  `featured` list shows a different card. Re-anchor the index to the current
  card's key.
- **Phone focus.** Phone `HomeScreen` keys `focused` on `home?.revision`, so
  every refresh resets it. Key it on scope instead.
- **No visible indicator.** No skeletons and no "refreshing" row for silent
  refreshes.

### 5. Platform hook (one common effect)

Add an app-root `LifecycleEventEffect` (`org.jetbrains.androidx.lifecycle`,
which Compose Multiplatform maps per platform) that sends
`Foreground`/`Background`:

- Android: activity start and stop.
- Desktop: window focus and minimise.
- Browser: `visibilitychange` and focus.

I will check each platform's mapping during implementation. If one is wrong,
it gets an `expect`/`actual` replacement.

### 6. Pull-to-refresh

Not recommended now. With foreground checks and 15 s checks it would almost
never find anything, and the design has no control for it. If the owner wants
it, it needs a design entry first, phone Home only.

## Cost

| Situation | Requests | Backend work |
|---|---|---|
| Idle device on Home | one revisions request per 15 s (≈240 per hour) | single-row SQLite reads; no external I/O |
| Background | none | none |
| Other screens | only on entry and foreground | — |
| Full Home rebuild | only when something actually changed, after playback, or every 30 min on foreground | normal Home build |

## Delivery (test-first)

1. **Backend.** Add the revisions endpoint, the profiles trigger and the
   revisions in bootstrap. Tests: each write class bumps the right token, and
   a no-op leaves every token unchanged.
2. **App.** Add `FreshnessCoordinator` with a fake clock and unit tests for
   the rules above.
3. **App.** Add the quiet refresh lane and merge. Controller tests cover:
   - no `busy`;
   - no cancellation of the user's operation;
   - stale results dropped;
   - failure keeps Home;
   - Continue Watching updated after the player closes;
   - Details reloaded.
4. **App.** Add the lifecycle effect and the UI keying fixes, with UI tests
   for focus and hero retention.
5. **Verification.** Run against my own backend instance on its own port,
   then on the emulator and in the browser.
