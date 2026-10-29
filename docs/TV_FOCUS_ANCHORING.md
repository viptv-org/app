# TV focus anchoring

TV mode is selected by `ViptvTheme(television)`. `FocusLazyRow`, `FocusLazyColumn`, and
`FocusLazyVerticalGrid` retain Foundation state, focus, layout and animation behavior,
while `TvFocusScroll` supplies the leading-edge destination for that collection's axis,
padding and layout direction. The adapters inherit the existing platform policy outside
TV mode. Non-lazy scrolling collections use the same scoped policy.

Horizontal cards align to logical start; vertical lists align to the top. Vertical
grids align the focused row and keep the focused column. `tvGridItem` supplies
item identity to `TvGridFocus` in both TV catalog grids. When the neighboring row
is offscreen, the controller uses a native bring-into-view request to reveal it and
focuses the matching column immediately after placement. It keeps one cancellable
pending destination for key repeat; it does not start a second scroll animation.
This avoids Foundation's geometric Up search choosing the last column of an
uncomposed row. Native scroll limits apply:
short lists and trailing items do not gain artificial empty space.

Home, Search and Details align entire sections through `TvSectionScroll`. Their outer
columns disable descendant-driven positioning; nested rows install their own policy.
Only the latest section request runs, and scrolling never waits before moving focus.
Home retains saved card identity and list position when its route is recreated.
Shelf re-entry also restores the saved card after lazy disposal. The outer Home
column prepares one viewport of adjacent shelves in each direction without expanding
the visible shelf slot. Section scrolling starts through Foundation without an extra
frame delay. Billboard artwork
waits for both horizontal and vertical scrolling to finish before swapping.

Home artwork is 10% larger (255.2 dp portrait / 422.4 dp landscape at the 1920 design
width), preserving image ratios and caption typography. Measured shelves bottom-align
inside a complete shelf viewport, 24 design dp above the screen bottom. The next heading
is outside this viewport. Other screens keep their existing card dimensions.

`TvCollectionUpdates` re-anchors a surviving focused item after paging, insertion or
reordering, including when an originally short list becomes scrollable. It observes
collection revisions while idle and queries the native focused bounds on demand;
ordinary focus changes remain owned by Foundation. No per-frame layout observer is added. Revisions compare keys at overlapping item
indices: a new visible window from scrolling is not a data mutation and cannot
start a competing relocation during rapid grid input.

## Episode entry and billboard transitions

The season selector retains a pending Down intent while the selected season's lazy
children are being replaced or loaded. It enters the remembered/resume/first episode
as soon as that child is placed. Another direction, season change or leaving the selector
cancels the intent. Returning to a section still has the original selector fallback.

Billboard requests start during navigation, rather than after the scroll animation.
Publication is coalesced until navigation settles. Summary enrichment coalesces rapid focus changes, and its images are warmed before
publication. Isolated directional taps use a 120 ms quiet threshold; successive
presses less than 400 ms apart use 350 ms to avoid repainting the billboard
between repeats. These thresholds never delay focus or native collection scrolling. Artwork uses an opaque outgoing base
with an incoming source-over dissolve, avoiding the background leak from fading both
planes. A conflated ready-image stream finishes the current 180 ms dissolve before
accepting the latest ready image; loading does not clear the previous image. Text
crossfades over 160 ms and same-title metadata changes retain content identity. Upper
hero entry/exit uses a shorter 220 ms transition with reduced translation, and portrait/
landscape planes crossfade. Shelf card styling is unchanged by these transition fixes. The overscanned portrait
blur is clipped at the 620 design-dp fade boundary, preventing a colored strip
below the mask. Its opacity is applied to the quarter-size composited image,
so overlapping paints do not brighten the soft backdrop. Non-TV artwork retains
its previous request and transition behavior.

## Collection audit

Paths below are relative to `composeApp/src/commonMain/kotlin/org/viptv/app`.
All lazy collection call sites use the shared adapters, including shared/non-TV components,
so a component rendered in TV mode cannot silently inherit a visibility-only policy.
Fixed rows, grids made from non-scrolling layouts, and text fields are not converted.
There are no horizontal lazy grids or staggered lazy grids in this source set.

| Source | Scrolling collections | Disposition |
|---|---|---|
| `components/FilterBar.kt` | LazyRow | The one filter-chip row every filtered page composes; shared TV policy, native defaults outside TV mode. |
| `components/HomeFilters.kt` | horizontalScroll | Shared TV policy; native defaults outside TV mode. |
| `components/TvColumnFocus.kt` | verticalScroll | Shared TV policy; native defaults outside TV mode. |
| `features/browse/BrowseScreen.kt` | LazyVerticalGrid | Shared TV policy; native defaults outside TV mode. |
| `features/browse/BrowserBrowseScreen.kt` | LazyVerticalGrid | Shared TV policy; native defaults outside TV mode. |
| `features/browse/TvBrowseLoading.kt` | LazyVerticalGrid | Shared TV policy; native defaults outside TV mode. |
| `features/browse/TvBrowseScreen.kt` | LazyVerticalGrid | Shared TV policy; native defaults outside TV mode. |
| `features/calendar/CalendarDates.kt` | horizontalScroll | Shared TV policy; native defaults outside TV mode. |
| `features/calendar/CalendarScreen.kt` | LazyColumn | Shared TV policy; native defaults outside TV mode. |
| `features/calendar/TvCalendarScreen.kt` | LazyColumn | Shared TV policy; native defaults outside TV mode. |
| `features/details/BrowserDetailsDialog.kt` | LazyColumn | Shared TV policy; native defaults outside TV mode. |
| `features/details/DetailSeasonPicker.kt` | LazyColumn | Shared TV policy; native defaults outside TV mode. |
| `features/details/DetailSections.kt` | LazyRow | Shared TV policy; native defaults outside TV mode. |
| `features/details/DetailsScreen.kt` | LazyColumn | Shared TV policy; native defaults outside TV mode. |
| `features/details/RelatedTitles.kt` | LazyRow | Shared TV policy; native defaults outside TV mode. |
| `features/details/SharedTitlePage.kt` | LazyColumn, LazyRow | Shared TV policy; native defaults outside TV mode. |
| `features/details/TabletDetailsScreen.kt` | LazyColumn | Shared TV policy; native defaults outside TV mode. |
| `features/details/TvDetailsScreen.kt` | LazyColumn | Shared TV policy; native defaults outside TV mode. |
| `features/details/TvEpisodeShelf.kt` | LazyRow | Shared TV policy; native defaults outside TV mode. |
| `features/details/TvRecommendationShelf.kt` | LazyRow | Shared TV policy; native defaults outside TV mode. |
| `features/details/TvSeasonSelector.kt` | LazyRow | Shared TV policy; native defaults outside TV mode. |
| `features/entry/BrowserAccountForm.kt` | verticalScroll | Non-TV branch; TV uses its dedicated surface. |
| `features/entry/DesktopProfiles.kt` | LazyRow | Shared TV policy; native defaults outside TV mode. |
| `features/entry/MobilePinScreen.kt` | verticalScroll | Shared TV policy; native defaults outside TV mode. |
| `features/entry/MobileProfileEdit.kt` | verticalScroll | Shared TV policy; native defaults outside TV mode. |
| `features/entry/MobileProfiles.kt` | LazyVerticalGrid | Shared TV policy; native defaults outside TV mode. |
| `features/entry/MobileSignIn.kt` | verticalScroll | Shared TV policy; native defaults outside TV mode. |
| `features/entry/ProfilesScreen.kt` | LazyRow | Shared TV policy; native defaults outside TV mode. |
| `features/entry/RecoveryCodesScreen.kt` | verticalScroll | Non-TV branch; TV uses its dedicated surface. |
| `features/entry/TvProfiles.kt` | LazyRow | Shared TV policy; native defaults outside TV mode. |
| `features/history/HistoryScreen.kt` | LazyColumn | Shared TV policy; native defaults outside TV mode. |
| `features/history/TvHistoryScreen.kt` | LazyColumn | Shared TV policy; native defaults outside TV mode. |
| `features/home/HomeCatalogRow.kt` | LazyRow | Shared TV policy; native defaults outside TV mode. |
| `features/home/HomeScreen.kt` | LazyColumn | Shared TV policy; native defaults outside TV mode. |
| `features/home/TvHomeLoading.kt` | horizontalScroll | Non-focusable loading artwork. |
| `features/home/TvHomeScreen.kt` | LazyColumn | Shared TV policy; native defaults outside TV mode. |
| `features/home/TvHomeShelf.kt` | LazyRow | Shared TV policy; native defaults outside TV mode. |
| `features/library/LibraryScreen.kt` | LazyVerticalGrid | Shared TV policy; native defaults outside TV mode. |
| `features/library/TvLibraryScreen.kt` | LazyVerticalGrid | Shared TV policy; native defaults outside TV mode. |
| `features/live/LiveGuide.kt` | LazyColumn | Shared TV policy; native defaults outside TV mode. |
| `features/live/LiveScreen.kt` | LazyColumn | Shared TV policy; native defaults outside TV mode. |
| `features/live/TvLiveScreen.kt` | LazyColumn | Shared TV policy; native defaults outside TV mode. |
| `features/player/PhoneEpisodePicker.kt` | LazyColumn | Shared TV policy; native defaults outside TV mode. |
| `features/player/PlaybackTrackPicker.kt` | LazyColumn | Shared TV policy; native defaults outside TV mode. |
| `features/player/TvPlaybackTrackPicker.kt` | LazyColumn | Shared TV policy; native defaults outside TV mode. |
| `features/player/TvPlayerEpisodePicker.kt` | LazyRow | Shared TV policy; native defaults outside TV mode. |
| `features/search/SearchScreen.kt` | horizontalScroll, LazyColumn, LazyRow, LazyVerticalGrid | Shared TV policy; native defaults outside TV mode. |
| `features/search/TvSearchLoading.kt` | LazyRow | Shared TV policy; native defaults outside TV mode. |
| `features/search/TvSearchScreen.kt` | LazyColumn, LazyRow | Shared TV policy; native defaults outside TV mode. |
| `features/settings/AccountDevicesScreen.kt` | LazyColumn | Shared TV policy; native defaults outside TV mode. |
| `features/settings/AccountSettingsSidebar.kt` | LazyColumn | Shared TV policy; native defaults outside TV mode. |
| `features/settings/AddDeviceScreen.kt` | verticalScroll | Shared TV policy; native defaults outside TV mode. |
| `features/settings/AddonSettings.kt` | LazyColumn | Shared TV policy; native defaults outside TV mode. |
| `features/settings/BrowserSourceIntegrationSettings.kt` | verticalScroll | Shared TV policy; native defaults outside TV mode. |
| `features/settings/ParentControlsSettings.kt` | verticalScroll | Shared TV policy; native defaults outside TV mode. |
| `features/settings/ParentTitlesSettings.kt` | LazyColumn | Shared TV policy; native defaults outside TV mode. |
| `features/settings/PhonePreferenceDialog.kt` | verticalScroll | Shared TV policy; native defaults outside TV mode. |
| `features/settings/PhoneProfilePreferences.kt` | verticalScroll | Shared TV policy; native defaults outside TV mode. |
| `features/settings/PhoneSettings.kt` | LazyColumn | Shared TV policy; native defaults outside TV mode. |
| `features/settings/ProfilePreferencesSettings.kt` | verticalScroll, horizontalScroll | Shared TV policy; native defaults outside TV mode. |
| `features/settings/SettingsInformation.kt` | verticalScroll | Shared policy for controls; read-only text scrolling is unchanged. |
| `features/settings/SettingsSidebar.kt` | LazyColumn | Shared TV policy; native defaults outside TV mode. |
| `features/settings/SourceIntegrationForm.kt` | verticalScroll | Shared TV policy; native defaults outside TV mode. |
| `features/settings/StremioImportScreen.kt` | LazyColumn | Shared TV policy; native defaults outside TV mode. |
| `features/settings/TvAddonSettings.kt` | LazyColumn | Shared TV policy; native defaults outside TV mode. |
| `features/settings/TvSettingsScreen.kt` | LazyColumn | Shared TV policy; native defaults outside TV mode. |
| `features/settings/TvSourceIntegrationForm.kt` | verticalScroll | Shared TV policy; native defaults outside TV mode. |
| `features/sources/SourcesScreen.kt` | LazyColumn | Shared TV policy; native defaults outside TV mode. |
| `features/sources/TvSourcesScreen.kt` | LazyColumn | Shared TV policy; native defaults outside TV mode. |
| `preview/PreviewNativeEntryFrame.kt` | verticalScroll | Preview; inherits TV mode. |

## Verification

`TvLeadingFocusTest` asserts actual focused-node coordinates for padded/variable-sized
lists, RTL/reverse layouts, physical limits, grids, rapid input, data changes, ordinary
scrollable rows, nested sections, Home geometry at two TV resolutions, and route return.
Existing shelf refresh, navigation, focus-ring, settings, sources and picker regressions
remain part of the desktop suite. Device qualification uses the optimized Android APK
and the onn TV; desktop semantics tests alone are not physical-device performance proof.

The ONN's ART compilation is reset by APK installation. `tools/tv_cycle.sh` reapplies
`tools/tv_aot.sh` before benchmarking. `tools/tv_ab.sh` verifies actual displacement
of the original card, rather than treating a focus change within a short shelf as
proof of scrolling. Device screenshots, raw frame measurements and APK receipts
are kept in the ignored `qualification/artifacts/tv-focus-anchor/` directory.

## Qualification — 2026-10-23

Changes remain uncommitted. The optimized release was installed with a data-preserving
update and full ART AOT compilation. The full desktop run passed 487 tests with two
skips (489 total). After the final rendering/timing refinements, the focused regressions
for grid cadence, season entry/cancellation, image compositing, blur clipping and billboard
coalescing also passed. The broader updated focus/shelf suite passed after the collection
revision correction. Android release assembly, both iOS Kotlin compile gates and Wasm
compilation passed. No Wasm distribution was built or served. Android lint still fails
on the pre-existing `MissingTvBanner` in the unchanged Android application manifest
(one error, six warnings).

Physical onn checks verified leading alignment, Home artwork geometry/captions, the
absence of the next shelf heading, the corrected blur boundary, direct Down from the
season selector, route restoration, and a six-Down/six-Up catalog-grid reversal returning
to the same card and column. Search, sources and settings were also exercised during
qualification. Live and Calendar had empty data, so populated-data behavior there is
covered by the shared implementation/tests rather than a populated device fixture.

Final same-shelf `gfxinfo` samples (release + full AOT; no concurrent builds or recording):

| Run | Frames | Missed vsync | Android janky frames | Frame time p90 / p99 |
|---|---:|---:|---:|---:|
| Horizontal, 10 Right / 10 Left | 182 | 7 (3.85%) | 30 (16.48%) | 30 / 42 ms |
| Vertical, 8 Up / Down pairs | 152 | 7 (4.61%) | 28 (18.42%) | 36 / 46 ms |
| Vertical, warmed repeat | 152 | 6 (3.95%) | 25 (16.45%) | 38 / 46 ms |

The earlier immediate-update candidate on the same shelf measured horizontal p90 38 ms
and 12/255 missed vsync on its warmed run. The intermediate fixed-delay vertical runs
measured p90 42–44 ms. Adaptive input coalescing reduced those frame costs, but measurable
jank remains; these results are not a claim of locked 60 FPS. Earlier adjacent-shelf cache
samples on other rows were faster (horizontal p90 29 ms; vertical p90 34–36 ms) and must
not be presented as an exact same-content comparison. The original pre-change 0.7%
sample used a short non-scrolling My List and is not a valid scrolling baseline.

The ignored artifact folder contains the APK/hash, before/after blur screenshots,
transition recording, frame logs/metrics, grid result, and build/test logs. The recording
is visual evidence only; screen recording was disabled for frame measurements.

The existing `tools/tv_ab.sh` final qualification also completed three horizontal bursts
after proving actual shelf displacement: 432 frames, 10 missed vsync (2.3%), median
p90 31 ms, and 16.7% Android janky frames. This aggregate uses a different catalog
starting card from the same-shelf tuning samples above. The installed APK hash was
compared against the built release and recorded in `final-receipt.json`.
