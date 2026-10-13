# Non-TV title details checkpoint — 2026-10-12

This is an implementation checkpoint, not a visual-parity certification.

The supplied `M3Title`, `M3TitleEpisodes`, `M3SeasonSheet`, `D3Title` and
`D3TitleSeason` boards guide this pass. Phone detail artwork now spans the screen
at 300 logical pixels with the ZIP fade and overlapping logo, a 50-pixel full-width
Play action and 16-pixel text gutters. The phone episode row uses the supplied
136×76 thumbnail, play overlay, 15/13-pixel labels and a separate description.
Desktop screens use numbered rows with 220-pixel artwork following D3Title; the browser dialog uses 170-pixel artwork. The horizontal episode shelf is reserved for W3TitlePage. Artwork requests remain bounded by
layout constraints and use the existing shared image loader/cache.

Season selection operates on the episodes already in `DetailSnapshot`; opening
or changing the selector makes no provider request. The phone presents a rounded
bottom sheet, wider screens a dropdown. Counts reflect only loaded episodes.
Provider episode coordinates determine numeric ordering, including Specials;
unknown metadata stays accessible under Other episodes. Stable keys include the
provider, media kind, ID, episode coordinates and edition, because some providers
reuse a series ID across multiple episodes. Duplicate identical records are
removed without dropping distinct episode coordinates or providers.

`DetailEpisodesTest` exercises same-ID episodes, duplicate records, numeric
season/episode ordering, unknown coordinates and delimiter-safe keys. Run it as
part of the coordinated `:composeApp:desktopTest` task; this agent did not start
Gradle or an emulator. Static `git diff --check` passes. Runtime screenshots and
native sheet/menu interactions remain to be qualified; browser evidence follows below.

The existing preview variant selector now accepts `Series` and `SeriesSeasons`
for non-TV Details. `PreviewDetails` uses byte-preserved `M3Title` backdrop/logo
assets and the supplied episode artwork. A second season is explicit isolated
fixture data for testing selection and never reaches a provider or account.
Ordinary Details still reads the real engine snapshot. Preview Play navigates to
its existing isolated Sources fixture and does not prove playback.

Unfinished supplied behaviors include trailer, saved-list/rating/share actions,
recommendations, metadata/cast details, true resume targets. The subsequent pass below adds tablet split and W3
modal/title-page presentation. Missing
season coordinates and a provider returning only part of a season require a
provisional presentation review; this pass does not fabricate total episode
counts or hide unclassified episodes.

The second pass adds T3Title's two independently scrollable panes at tablet
landscape widths, with 176-pixel episode thumbnails and full-height information
artwork. Tablet Details/Sources hide the main sidebar as supplied. Narrow tablet
windows retain the stacked responsive detail surface. W3TitleModal uses an
800-pixel dialog over already-loaded Home data, a 16:8.2 hero, rounded 14-pixel
surface and explicit close/dismiss behavior. The dialog has a semantic pane title.
No additional metadata request is made to render the backdrop.

The backend owner confirmed there is no public unauthenticated title/share-slug
contract. Therefore W3TitlePage exists only as the isolated SharedTitle preview;
normal browser routing still requires its server session. Trailer/cast/rating/list
values are not invented. Their contract-backed actions remain unfinished.

Preview examples after the coordinated build:
- Desktop: `VIPTV_UI_PREVIEW=1 VIPTV_UI_SCREEN=Details VIPTV_UI_VARIANT=SeriesSeasons`.
- Android: existing `ui_screen=Details`, `ui_variant=SeriesSeasons` intent extras.
- Browser: `#ui-preview/Details/SeriesSeasons` or `#ui-preview/Details/SharedTitle`.

The browser preview parser now separates screen and optional variant segments;
existing `#ui-preview/Home` links retain their behavior.

## Browser verification — 2026-10-13

The previous dev-server port was occupied by an unrelated emulator gateway.
QA restarted the existing compiled Wasm files with an isolated webpack server
on 127.0.0.1:18081 and the existing trusted TLS certificate at
https://viptv.local.test:18443. Curl returned HTTP 200 and ssl_verify_result 0.
At 1440×1000, real pointer interaction proved Season 2 filters the fixture to two
episodes, outside-click and the close button dismiss the title dialog, Play
opens the isolated source picker, and SharedTitle sign-in opens Connect.
Screenshots/logs are in `/tmp/viptv-nontv-browser-qa.Nq7fv3`.

The initial Wasm build failed Escape dismissal. BrowserDetailsDialog now gives
the close button initial focus and handles Escape explicitly in the dialog key
path; the first rebuild proved Escape after pointer focus. A browser-only listener now
covers the initial DOM body focus before the canvas receives a pointer/Tab; the
final browser retest passed initial Escape dismissal without any preceding click. Compose's canvas intercepts
DOM pointer dispatch, so QA uses accessibility locators to find geometry and
sends actual pointer events to the canvas. Menu entries expose button semantics.

Browser phone (390×844) season-sheet selection also passed with zero page errors,
failed requests or API calls. Its screenshot is `browser-phone-seasons.png` in the
same evidence directory.
