package org.viptv.app.connection

import kotlinx.serialization.Serializable
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.LibraryQuery

@Serializable
data class SavedNavigation(
    val screen: String = "Home",
    val browse: BrowseQuery? = null,
    val search: String = "",
    val library: LibraryQuery? = null,
    val detail: Card? = null,
    val detailReturn: String? = null,
)

@Serializable
internal data class SavedNavigationBook(val profiles: Map<String, SavedNavigation> = emptyMap())
