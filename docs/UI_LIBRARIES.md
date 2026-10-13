# UI implementation libraries

The visual source remains the current VIPTV ZIP archives. Libraries provide behavior and assets; they do not replace the supplied layouts.

| Concern | Current implementation | Evidence and limits |
| --- | --- | --- |
| Desktop dragging | Compose Foundation `WindowDraggableArea` | Replaces the manual relative-coordinate drag loop. An isolated Linux check moved the pointer and window exactly 100 × 40 pixels. User-desktop scaling, snap, maximized dragging and multi-monitor behavior remain unqualified. |
| Icons | `com.composables:icons-lucide-cmp:2.2.1` | Lucide provides cached ImageVectors for the rounded outline theme. Title-bar, navigation, sign-in method, lock, add-profile and password-visibility icons now use library assets. Remaining glyph-based controls are still under review. |
| Window caption controls | Vector paths from `D3HomeWin` ZIP HTML | Minimise, maximise and close are 10 × 10 paths in 46-pixel hit areas. These replace font glyphs. Linux renders the Windows-style variant; macOS and Windows runtime checks remain pending. |
| Remote-control icon | Vector paths from the ZIP HTML | The selected Lucide package lacks the exact remote-control symbol, so this asset preserves the supplied SVG geometry. |
| Images | Coil 3.6.3 shared singleton | 64 MiB memory cache and 256 MiB persistent Android/desktop disk cache; browser uses memory plus HTTP caching. Existing AsyncImage layouts constrain decode size. Measured cold/warm latency and large-list memory tests remain pending. |

Jewel was considered for custom decorations. Its decoration layer requires JetBrains Runtime, while the current development runtime is OpenJDK 17. ComposeWindowStyler was considered for native window styling; its documented caption-color and backdrop support focuses on Windows. Neither is integrated at this checkpoint.

Sources: [Compose Foundation window source](https://github.com/JetBrains/compose-multiplatform-core/tree/jb-main/compose/foundation/foundation/src/desktopMain/kotlin/androidx/compose/foundation/window), [Compose Icons](https://github.com/composablehorizons/compose-icons), [Jewel](https://github.com/JetBrains/jewel), [ComposeWindowStyler](https://github.com/MayakaApps/ComposeWindowStyler), [Coil image loaders](https://coil-kt.github.io/coil/image_loaders/).

Playback transport controls use the installed KitePlayer 0.3.0 APIs for seek, volume, speed and track selection. Native player snapshots supply the displayed progress, volume/speed and audio/subtitle choices. Browser transport remains HTML video/HLS and handles seek, volume and speed separately; browser track selection and overlay composition are unqualified. The thin timeline uses Material3 Slider's custom thumb/track slots rather than custom drag handling. References: [KitePlayer](https://github.com/yuroyami/kiteplayer), [Slider customization](https://developer.android.com/develop/ui/compose/components/slider), [Android immersive playback](https://developer.android.com/develop/ui/views/layout/immersive).

Android TV switches use `com.composables:composeunstyled-toggle-switch:2.10.0`.
The published component supports a separately sized track and thumb, so both
states keep the ZIP's 68×38 track and 30-pixel thumb. The containing settings row
owns focus, interaction and switch semantics; the inner switch is noninteractive.
The version was verified from Maven Central metadata. Desktop, Android and Wasm
compilation passed with the dependency.

Sources: [Compose Unstyled switch](https://github.com/composablehorizons/compose-unstyled/blob/main/docs/pages/toggleswitch.md),
[published versions](https://repo.maven.apache.org/maven2/com/composables/composeunstyled-toggle-switch/maven-metadata.xml).
# QR pairing

QRose 1.3.1 supplies Compose Multiplatform QR painters for the TV pairing frame.
Its official documentation lists Android, JVM/desktop and WasmJS support.
The dependency/version was checked against Maven Central metadata; the native
preview QR was decoded with zbar to verify its payload.
[Official project](https://github.com/alexzhirkevich/qrose),
[Maven metadata](https://repo.maven.apache.org/maven2/io/github/alexzhirkevich/qrose/maven-metadata.xml).

TV buffering reuses Material 3's `CircularProgressIndicator` with fixed progress,
track, cap and gap parameters, then rotates the component to follow the ZIP's
900 ms motion. It does not use Material's changing indeterminate arc animation.
The current API was checked through Context7's [component documentation](https://composables.com/jetpack-compose/androidx.compose.material3/material3/components/CircularProgressIndicator).

TV control visibility uses Compose `AnimatedVisibility` with `MutableTransitionState`
and fade transitions, which retains content until exit completes. The API was
checked through Context7's [official Compose animation documentation](https://developer.android.com/develop/ui/compose/animation/composables-modifiers).

Next-episode text line-height behavior was checked against [Compose paragraph styling](https://developer.android.com/develop/ui/compose/text/style-paragraph)
through Context7 and the installed Compose text API. Fixed line-height settings
alone did not reduce the single-line heading's measured font bounds, so the
heading uses Compose layout sizing and unbounded height wrapping to reserve the
ZIP's CSS line height without clipping glyphs.
