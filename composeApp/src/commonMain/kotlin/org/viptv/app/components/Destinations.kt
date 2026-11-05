package org.viptv.app.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.*
import org.viptv.app.state.Screen

internal data class Destination(val screen: Screen, val label: String, val icon: ImageVector)

internal val LocalAnimeAvailable = compositionLocalOf { true }

internal val LocalHeaderPills = compositionLocalOf { false }

internal val TabletMinSide = 600.dp

internal fun phoneShell(width: Dp, height: Dp, television: Boolean, touch: Boolean) =
    touch && !television && minOf(width, height) < TabletMinSide

internal val phoneDestinations = listOf(
    Destination(Screen.Home, "Home", Lucide.House),
    Destination(Screen.Live, "Live", Lucide.Tv),
    Destination(Screen.Calendar, "Calendar", Lucide.Calendar),
    Destination(Screen.Library, "My List", Lucide.Bookmark),
    Destination(Screen.Search, "Search", Lucide.Search),
)

internal val phoneTabDestinations = phoneDestinations.filter { it.screen != Screen.Search }

internal val phoneSearch = phoneDestinations.first { it.screen == Screen.Search }

internal val desktopDestinations = listOf(
    Destination(Screen.Home, "Home", Lucide.House),
    Destination(Screen.Discover, "Discover", Lucide.Compass),
    Destination(Screen.Movies, "Movies", Lucide.Film),
    Destination(Screen.Series, "Series", Lucide.Tv),
    Destination(Screen.Anime, "Anime", Lucide.Sparkles),
    Destination(Screen.Live, "Live", Lucide.RadioReceiver),
    Destination(Screen.Calendar, "Calendar", Lucide.Calendar),
    Destination(Screen.Library, "My List", Lucide.Bookmark),
    Destination(Screen.History, "History", Lucide.History),
)

