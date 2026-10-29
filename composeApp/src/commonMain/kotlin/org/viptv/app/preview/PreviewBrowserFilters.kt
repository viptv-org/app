package org.viptv.app.preview

import androidx.compose.runtime.*
import org.viptv.app.connection.BrowseQuery
import org.viptv.app.connection.DiscoveryControls
import org.viptv.app.connection.FeatureAvailability
import org.viptv.app.features.browse.BrowserBrowseScreen
import org.viptv.app.state.Screen
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.Choice
import org.viptv.engine.wire.ControlKind
import org.viptv.engine.wire.QueryControl
import org.viptv.engine.wire.QueryValue

@Composable
internal fun PreviewBrowserFilters(screen: Screen, open: (Card) -> Unit) {
    var query by remember { mutableStateOf(BrowseQuery("movie", extras = mapOf("sort" to "popular"))) }
    BrowserBrowseScreen(screen, PreviewData.posters, PreviewBrowserFilterData.controls, query, false, null, null, open,
        changed = { id, value ->
            query = if (id == "genre") query.copy(genre = value.ifEmpty { null })
            else query.copy(extras = if (value.isEmpty()) query.extras - id else query.extras + (id to value))
        },
        multipleChanged = { id, values -> query = query.copy(values = query.values.filter { it.id != id } + QueryValue(id, values)) },
        applyQuery = { query = it })
}

internal object PreviewBrowserFilterData {
    private val genres = listOf("Action", "Adventure", "Animation", "Biography", "Comedy", "Crime", "Documentary", "Drama",
        "Family", "Fantasy", "Film-Noir", "History", "Horror", "Music", "Musical", "Mystery", "Reality-TV", "Romance",
        "Romantic Comedy", "Sci-Fi", "Short", "Sport", "Talk-Show", "Telenovela", "Thriller", "War", "Western", "Ánime")
    val controls = DiscoveryControls("preview", listOf("movie"), listOf(
        QueryControl("sort", "Sort", ControlKind.SINGLECHOICE, true, listOf(Choice("popular", "Popular"), Choice("new", "New"), Choice("rated", "Rated"))),
        QueryControl("genre", "Genre", ControlKind.SINGLECHOICE, false, genres.map { Choice(it, it) }),
        QueryControl("language", "Languages", ControlKind.MULTICHOICE, false,
            listOf("English", "Japanese", "Korean", "French", "Spanish", "German", "Italian", "Hindi").map { Choice(it.lowercase(), it) },
            maximum_selections = 3)),
        discovery = FeatureAvailability("Available"), search = FeatureAvailability("Available"),
        filters = FeatureAvailability("Available"), calendar = FeatureAvailability("Unsupported"))
}
