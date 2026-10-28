package org.viptv.app

import java.io.File
import kotlin.test.*

/**
 * app#117 and app#121: the dropdown chip and its list are the one filter pattern on every
 * layout. This scan keeps the retired paths from coming back: the phone "Filters" toggle and
 * applied-chip strip, the button-and-menu and Apply-sheet filters, the web rail's segments and
 * chips, the source picker's chip rows, the old segments-or-dropdown rule, and TV's own
 * filter surface (`TvFiltersPage` with "Show results", the `TvLibrary*` sheet and fields) and
 * its two pill rows on the source picker.
 */
class FilterPatternScanTest {
    private val root = File("src/commonMain/kotlin/org/viptv/app")
    private fun sources() = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private val retired = listOf(
        "PhoneFilterChips", "PhoneBrowseBar", "PhoneCatalogsSheet", "SortMenu",
        "DiscoveryFilters", "DiscoveryChoiceFilter", "DiscoveryMultiChoiceFilter", "DiscoveryMultiChoiceSheet", "DiscoveryTextFilter",
        "LibraryHeader", "LibraryFilterSheet", "LibraryFilterField", "LibraryListPicker", "LibraryTypeFilters",
        "BrowserFilterRail", "BrowserFacet", "BrowserSegments", "BrowserChip", "BrowserToggle", "BrowserFacetField", "BrowserAppliedRow",
        "useSelect", "InlineChoiceLimit", "sourceFilterUsesSelect",
        "TvBrowseChrome", "TvCatalogsSheet", "TvFiltersPage", "TvFacetChoices", "TvFacetText", "tvAppliedChips",
        "TvLibraryHeader", "TvLibraryFilterSheet", "TvLibraryFilterField", "TvLibraryListPicker", "TvLibraryTypeFilters",
        "TvLibraryControls", "TvSourcesFilters")

    @Test fun theRetiredFilterComponentsAreGone() {
        assertTrue(root.isDirectory, "run from the composeApp module: ${root.absolutePath}")
        val found = sources().flatMap { file ->
            val text = file.readText()
            retired.filter { Regex("""(?<![A-Za-z0-9_])$it(?![A-Za-z0-9_])""").containsMatchIn(text) }.map { "${file.name}: $it" }
        }
        assertEquals(emptyList(), found, "use SearchableSelect / FilterBar instead")
    }

    @Test fun noPageStillOpensAFiltersPanel() {
        val pages = listOf("features/browse", "features/library", "features/sources")
        val found = pages.flatMap { File(root, it).listFiles().orEmpty().toList() }
            .filter { Regex(""""Filters"|"Apply"|Show results""").containsMatchIn(it.readText()) }
            .map { it.name }
        assertEquals(emptyList(), found, "every layout filters through the shared chips only")
    }

    @Test fun nonTvPagesBuildTheirFiltersFromTheSharedBar() {
        for (name in listOf("features/browse/BrowseScreen.kt", "features/browse/BrowserBrowseScreen.kt",
            "features/library/LibraryScreen.kt", "features/sources/SourcesScreen.kt")) {
            val text = File(root, name).readText()
            assertFalse(Regex("""\bFilterChip\(|\bDropdownMenu\(|\bOutlinedButton\(""").containsMatchIn(text), "$name filters only through FilterBar")
        }
        assertTrue(File(root, "features/browse/BrowseFilterBar.kt").readText().contains("FilterBar("))
        assertTrue(File(root, "features/library/LibraryFilterBar.kt").readText().contains("FilterBar("))
        assertTrue(File(root, "features/library/LibraryControls.kt").readText().contains("FilterBar("))
        assertTrue(File(root, "features/sources/SourceFilters.kt").readText().contains("FilterBar("))
    }

    @Test fun tvPagesBuildTheirFiltersFromTheSharedBar() {
        // app#121: the TV pages compose the same bar as every other layout; the TV-only part
        // is the presentation the bar picks, not a second filter surface.
        val pages = mapOf(
            "features/browse/TvBrowseScreen.kt" to "BrowseFilterBar(",
            "features/library/TvLibraryScreen.kt" to "LibraryFilterBar(",
            "features/sources/TvSourcesScreen.kt" to "SourceFilters(")
        for ((name, bar) in pages) assertTrue(File(root, name).readText().contains(bar), "$name uses $bar")
        assertTrue(File(root, "features/library/TvLibraryScreen.kt").readText().contains("LibraryControls("),
            "My List before the server query uses the shared local controls too")
    }
}
