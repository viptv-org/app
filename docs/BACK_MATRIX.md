# Back matrix (app#45, app#20)

Audit of `main` at `c680151`, 2026-10-27, and the result after this
branch. Every row is driven by `composeApp` desktop tests through
`ApplicationController.navigateBack()`, the Android/TV system-Back rule
(`ApplicationState.systemBackStaysInApp()`, used by `MainActivity`), the
keyboard-and-mouse Back (`GlobalBack`, used by the desktop window and the
web build) and the browser history bridge (`BrowserBackHistory`).

## Inputs

| Layout | Back inputs | Where it is wired |
|---|---|---|
| Phone, tablet | Android Back gesture/button; the page's back arrow | `MainActivity` `BackHandler`, route `entryBackChanged` |
| TV | Remote Back | Compose key handlers, then `MainActivity` `BackHandler` |
| Desktop | Title-bar Back chevron, Escape, mouse Back button (Forward chevron, mouse Forward) | `desktopMain/Main.kt` |
| Browser | Browser Back/Forward (and the mouse buttons, which drive them), Escape, the page's back arrow | `wasmJsMain/Main.kt` |

Rules: an open overlay or panel closes before the screen goes back;
nested pages go up one level at a time; otherwise Back pops the
screen-to-screen history (`navBack`); a page with no history goes to its
parent (Profiles for its PIN and editor, Home for the rest). On Android
and TV, Back on Home opens the profile picker first, and Back on that
picker leaves the app — no Home↔Profiles bounce (app#109). Connect and
the first Profiles choice are roots: Back there belongs to the
platform.

## Matrix

The `main` columns show what `main` did: ✓ right, ✗ wrong (what happened).
The Branch column is the result on this branch: every row passes or is a
decision for the owner (D1 to D5, listed below). Phone and tablet share
one column: both use `MainActivity`'s Back and the same routes; they
differ only in which in-page arrows they show, and the arrows now call
`navigateBack`.

Tests: `BackMatrixTest` (every screen, Android/TV Back (`navigateSystemBack`) and `navigateBack`),
`BackArrowsUiTest` (phone arrows = Android Back = `navigateBack`),
`DesktopBackInputsTest` (Escape, focused controls and dialogs keep theirs,
mouse buttons, route step first), `BrowserBackHistoryTest` (browser
Back/Forward over a fake session history), `RouteBackUiTest` (player menu
closes first; browser sign-in forms), plus the existing `SettingsBackTest`,
`PlayerSettingsLinkUiTest`, `DetailNavigationTest`,
`ContinueWatchingSourcesOpenTest`, `NextEpisodeEndPanelUiTest`.

| Screen / state | Expected | Phone, tablet (`main`) | TV (`main`) | Desktop (`main`) | Browser (`main`) | Branch |
|---|---|---|---|---|---|---|
| Connect, first step | Leave the app (platform) | ✓ leaves | ✓ leaves | ✓ nothing to go back to | ✓ leaves the site | ✓ |
| Connect, a later sign-in step | Previous step | ✓ | ✓ | ✗ Escape does nothing (step's Back registered, not wired) | ✗ Back leaves the site; Escape nothing; register/recover forms have no Back | ✓ |
| Recovery codes | Stay until the viewer confirms (D4) | ✗ goes Home with no profile | ✗ same | ✓ nothing | ✗ Back leaves the site | D4 |
| Profiles, first choice (no profile yet) | Leave the app | ✓ | ✓ | ✓ | ✓ leaves the site | ✓ |
| Profiles, switching (profile selected) | Where the viewer came from, else Home | ✗ leaves the app; arrow went Home and recorded Profiles | ✗ leaves the app | ✗ Escape nothing (no chevron on this bar) | ✗ leaves the site | ✓ |
| Profiles, reached by Back from Home | Leave the app, no bounce | ✗ the header arrow bounced Home | ✓ leaves the app | n/a | n/a | ✓ |
| Profiles, Manage profiles | Leave Manage, stay on Profiles | ✗ leaves the app | ✗ leaves the app | ✗ nothing | ✗ leaves the site | ✓ |
| Profile PIN | Profiles | ✓ (but recorded the PIN page in history) | ✓ | ✗ `navigateBack` popped history (Home) | ✗ leaves the site | ✓ |
| Profile editor | Profiles | ✓ | ✓ | ✗ `navigateBack` popped history (Home) | ✗ leaves the site | ✓ |
| Home, TV rows | Back to the hero | n/a | ✓ | n/a | n/a | ✓ |
| Home | Profile picker first; Back there leaves the app (D1) | ✗ goes to Profiles | ✗ goes to Profiles | ✓ chevron pops history if any | ✗ leaves the site even with history | ✓ |
| Movies / Series / Anime / Discover, also genre-filtered (app#15) | Previous screen, else Home | ✗ always Home | ✗ always Home | ✓ | ✗ leaves the site | ✓ |
| Search | Previous screen, else Home | ✗ always Home | ✗ always Home | ✓ (Escape in the field too) | ✗ leaves the site | ✓ |
| Details (all layouts), related-title chain | Previous Details, then the origin page | ✓ | ✓ | ✓ | ✗ leaves the site | ✓ |
| Sources (app#11 rule) | Details if open, else origin | ✓ | ✓ | ✓ | ✗ leaves the site | ✓ |
| Player | Sources (app#13 lease rules) | ✓ | ✓ | ✓ Escape; ✗ mouse Back | ✗ leaves the site | ✓ |
| Player panel open (Episodes, Audio & subtitles, Next episode app#41, lock) | Close the panel first | ✓ | ✓ | ✓ Escape; ✗ mouse Back closed the player | ✗ browser Back closed the player under the panel | ✓ |
| Settings opened from the player (app#44) | The paused player | ✓ | ✓ | ✓ | ✗ leaves the site | ✓ |
| My List | Previous screen, else Home | ✗ always Home | ✗ always Home | ✓ | ✗ leaves the site | ✓ |
| History | Previous screen, else Home | ✗ always Home; arrow went Home and recorded History | ✗ always Home | ✓ | ✗ leaves the site | ✓ |
| Calendar | Previous screen, else Home | ✗ always Home | ✗ always Home | ✓ | ✗ leaves the site | ✓ |
| Live | Previous screen, else Home | ✗ always Home | ✗ always Home | ✓ | ✗ leaves the site | ✓ |
| Settings list | Previous screen, else Home | ✗ always Home, arrow too | n/a (sections) | ✓ | ✗ leaves the site | ✓ |
| Settings sub-page, Parental Titles (app#7) | Up one level | ✓ | ✓ add-on entry | ✓ chevron; ✗ Escape | ✗ leaves the site | ✓ |
| TV Settings, focus in a section's content | Previous screen (D2) | n/a | ✗ always Home | n/a | n/a | D2 |
| Stremio import, Devices, Watch on TV, Add a device (Settings sections) | Settings list (phone width) | ✓ | n/a | ✓ chevron; ✗ Escape | ✗ leaves the site | ✓ |
| Any page: desktop mouse Back / Forward | Same as the chevrons | n/a | n/a | ✗ not wired | ✓ (drives browser history) | ✓ |
| Any page: browser Forward after Back | The page Back left | n/a | n/a | ✓ chevron | ✗ not wired | ✓ |

## Decisions for the owner

- **D1, Back on Home.** Owner decision 2026-10-27 (app#109), reversing
  app#45: on Android phone and TV, Back on Home opens the profile picker
  first ("Who's watching?"), and Back from that picker leaves the app.
  There is no Home↔Profiles bounce: choosing a profile still opens Home,
  and the picker is marked so Back there belongs to the platform (its back
  handler and header arrow are off). Profiles reached by other routes
  (Switch profile, PIN, editor) keeps its own Back rules. Desktop and
  browser are unchanged: desktop pops Home's history, browser Back on a
  root page leaves the site.
- **D2, TV Settings sections.** app#7's open point. TV Back with focus in
  a section's content now leaves Settings for the previous screen (it went
  Home). Moving focus back to the section list first would be a TV design
  change.
- **D3, browser Back on a root page.** On Home with no in-app history, or
  on Connect, browser Back leaves the site like any web page. The app
  can't block it without trapping the viewer.
- **D4, recovery codes.** On `main`, Android Back on the recovery-codes
  page went Home with no profile chosen. It now does nothing there, so the
  codes can't be lost by accident; the page's own buttons are the way on.
- **D5, the Forward history.** Desktop and browser Forward replay the
  screen history only (`navForward`). Closed overlays (Details, Sources,
  the player, Settings sub-pages) are not reopened by Forward, as on
  `main`'s desktop chevron.
