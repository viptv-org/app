package org.viptv.app.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.connection.GenreChoice
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette

internal class HomeGenrePicker(val choices: List<GenreChoice>, val choose: (GenreChoice) -> Unit)

internal val LocalHomeGenres = compositionLocalOf<HomeGenrePicker?> { null }

@Composable
internal fun HomeFilters(screen: Screen, navigate: (Screen) -> Unit, gutter: Dp = 0.dp) {
    val genres = LocalHomeGenres.current?.takeIf { it.choices.isNotEmpty() }
    org.viptv.app.components.TvFocusScroll(horizontal = true) { Row(Modifier.edgeToEdgeScroll(rememberScrollState(), gutter), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        listOf("All" to Screen.Home, "Movies" to Screen.Movies, "Series" to Screen.Series, "Anime" to Screen.Anime).forEach { (label, destination) ->
            Surface(onClick = { navigate(destination) }, shape = RoundedCornerShape(20.dp),
                color = if (destination == screen) Palette.text else Palette.raised.copy(alpha = 0.8f),
                contentColor = if (destination == screen) Palette.background else Palette.text) {
                Text(label, Modifier.padding(horizontal = 14.dp, vertical = 7.dp), fontSize = 14.sp, lineHeight = 18.sp)
            }
        }
        if (genres != null) GenresChip(genres)
    } }
}

@Composable
private fun GenresChip(genres: HomeGenrePicker) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(onClick = { open = true }, shape = RoundedCornerShape(20.dp), color = Palette.raised.copy(alpha = 0.8f)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Lucide.ChevronDown, null, Modifier.size(14.dp).rotate(if (open) 180f else 0f))
                Text("Genres", fontSize = 14.sp, lineHeight = 18.sp)
            }
        }
        PopoverMenu(open, { open = false }, Modifier.heightIn(max = 396.dp)) {
            genres.choices.forEach { choice -> PopoverMenuItem(choice.label) { open = false; genres.choose(choice) } }
        }
    }
}
