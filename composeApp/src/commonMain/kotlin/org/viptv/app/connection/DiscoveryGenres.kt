package org.viptv.app.connection

/** One genre the Home Genres picker offers. Choosing it opens Browse with [query]. */
data class GenreChoice(val label: String, val query: BrowseQuery)

/**
 * The genres Browse offers for [type] before any other filter is chosen. They come from the
 * default catalog's genre control, or from the provider's own controls when no catalog
 * applies (Simkl). Each choice is that same Browse query with the genre set. A catalog's
 * "all" or empty default is not a genre.
 */
internal fun DiscoveryControls.genreChoices(type: String): List<GenreChoice> {
    if (discovery.availability != "Available") return emptyList()
    val query = BrowseQuery(type).withDefaultCatalog(this)
    val control = (selectedCatalog(query)?.controls ?: controls).find { it.id == "genre" } ?: return emptyList()
    if (control.maximum_selections == 0L) return emptyList()
    return control.choices.filter { it.value.isNotBlank() && it.value != "all" }.distinctBy { it.value }
        .map { GenreChoice(it.label, query.copy(genre = it.value)) }
}
