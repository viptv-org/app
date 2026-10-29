package org.viptv.app.connection

data class GenreChoice(val label: String, val query: BrowseQuery)

internal fun DiscoveryControls.genreChoices(type: String): List<GenreChoice> {
    if (discovery.availability != "Available") return emptyList()
    val query = BrowseQuery(type).withDefaultCatalog(this)
    val control = (selectedCatalog(query)?.controls ?: controls).find { it.id == "genre" } ?: return emptyList()
    if (control.maximum_selections == 0L) return emptyList()
    return control.choices.filter { it.value.isNotBlank() && it.value != "all" }.distinctBy { it.value }
        .map { GenreChoice(it.label, query.copy(genre = it.value)) }
}
