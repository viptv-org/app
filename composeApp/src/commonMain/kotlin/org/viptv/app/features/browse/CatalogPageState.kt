package org.viptv.app.features.browse

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.composables.icons.lucide.CloudOff
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import org.viptv.app.components.PageState
import org.viptv.app.components.PageStateAction
import org.viptv.app.components.PageStateLayout

// copy.md ## Empty states — the catalog pages, and the failure the page's own read reports.
internal const val CATALOG_NO_TITLES_TITLE = "No titles yet"
internal const val CATALOG_FILTERED_TITLE = "Nothing matches these filters"
internal const val CATALOG_FAILED_TITLE = "Couldn’t load"
internal const val CATALOG_FAILED_FALLBACK = "Check the connection and try again."
internal const val CATALOG_REMOVE_FILTER = "Remove filter"
internal const val CATALOG_CLEAR_ALL = "Clear all filters"
internal const val CATALOG_CHOOSE_ANOTHER = "Choose another catalog"
internal const val CATALOG_RETRY = "Try again"

internal enum class CatalogActionKind { Remove, ClearAll, ChooseCatalog, Retry }

internal class CatalogAction(val label: String, val kind: CatalogActionKind, val primary: Boolean = false,
    val chip: AppliedChip? = null)

internal data class CatalogState(val failure: Boolean, val title: String, val message: String, val actions: List<CatalogAction>)

internal fun catalogNoun(type: String): String = when (type) {
    "movie" -> "movies"
    "series" -> "series"
    "anime" -> "anime"
    else -> "titles"
}

internal fun catalogStateSource(label: String?, source: String?): String =
    listOfNotNull(label?.takeIf { it.isNotBlank() }, source?.takeIf { it.isNotBlank() }).joinToString(" from ")
        .ifBlank { "This catalog" }

internal fun catalogEmptyState(label: String?, source: String?, noun: String, applied: List<AppliedChip>,
    otherCatalog: Boolean): CatalogState {
    val newest = applied.lastOrNull()
    val choose = CatalogAction(CATALOG_CHOOSE_ANOTHER, CatalogActionKind.ChooseCatalog)
    val clear = CatalogAction(CATALOG_CLEAR_ALL, CatalogActionKind.ClearAll)
    return when {
        newest == null -> CatalogState(failure = false, title = CATALOG_NO_TITLES_TITLE, message = "",
            actions = listOfNotNull(choose.takeIf { otherCatalog }))
        applied.size == 1 -> CatalogState(failure = false, title = CATALOG_FILTERED_TITLE,
            message = "${catalogStateSource(label, source)} has no $noun matching “${newest.label}”.",
            actions = listOfNotNull(removeAction(newest), clear, choose.takeIf { otherCatalog }))
        else -> CatalogState(failure = false, title = CATALOG_FILTERED_TITLE,
            message = "${catalogStateSource(label, source)} has no $noun with all ${applied.size} filters. Removing the newest one usually helps.",
            actions = listOfNotNull(removeAction(newest), clear, choose.takeIf { otherCatalog }))
    }
}

internal fun catalogFailureState(label: String?, error: String?, otherCatalog: Boolean): CatalogState =
    CatalogState(failure = true, title = "$CATALOG_FAILED_TITLE ${label?.takeIf { it.isNotBlank() } ?: "this catalog"}",
        message = error?.takeIf { it.isNotBlank() } ?: CATALOG_FAILED_FALLBACK,
        actions = listOfNotNull(CatalogAction(CATALOG_RETRY, CatalogActionKind.Retry, primary = true),
            CatalogAction(CATALOG_CHOOSE_ANOTHER, CatalogActionKind.ChooseCatalog).takeIf { otherCatalog }))

private fun removeAction(chip: AppliedChip) =
    CatalogAction(if (chip.label.length > 24) CATALOG_REMOVE_FILTER else "Remove “${chip.label}”",
        CatalogActionKind.Remove, primary = true, chip = chip)

/** The catalog page's empty or failed state, drawn with the block every list page uses. */
@Composable
internal fun CatalogPageState(state: CatalogState, activate: (CatalogAction) -> Unit, modifier: Modifier = Modifier,
    layout: PageStateLayout = PageStateLayout.Wide, scale: Float = 1f, focus: FocusRequester? = null,
    enabled: Boolean = true) {
    PageState(icon = if (state.failure) Lucide.CloudOff else Lucide.Search, title = state.title, message = state.message,
        actions = state.actions.map { action ->
            PageStateAction(action.label, primary = action.primary, activate = { activate(action) })
        },
        modifier = modifier, layout = layout, tile = false, scale = scale, focus = focus, enabled = enabled)
}