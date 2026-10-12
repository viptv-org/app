package org.viptv.app.components

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.*
import org.viptv.app.state.Screen

internal data class Destination(val screen: Screen, val label: String, val icon: ImageVector)

internal val desktopDestinations = listOf(
    Destination(Screen.Home, "Home", Lucide.House),
    Destination(Screen.Discover, "Discover", Lucide.Compass),
    Destination(Screen.Movies, "Movies", Lucide.Film),
    Destination(Screen.Series, "Series", Lucide.Tv),
    Destination(Screen.Live, "Live", Lucide.RadioReceiver),
    Destination(Screen.Calendar, "Calendar", Lucide.Calendar),
    Destination(Screen.Library, "My List", Lucide.Bookmark),
    Destination(Screen.History, "History", Lucide.History),
)

