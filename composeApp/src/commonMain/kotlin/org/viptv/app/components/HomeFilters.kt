package org.viptv.app.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette

@Composable
internal fun HomeFilters(screen: Screen, navigate: (Screen) -> Unit, genres: () -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        listOf("All" to Screen.Home, "Movies" to Screen.Movies, "Series" to Screen.Series, "Anime" to Screen.Anime).forEach { (label, destination) ->
            Surface(onClick = { navigate(destination) }, shape = RoundedCornerShape(20.dp),
                color = if (destination == screen) Palette.text else Palette.raised.copy(alpha = 0.8f),
                contentColor = if (destination == screen) Palette.background else Palette.text) {
                Text(label, Modifier.padding(horizontal = 14.dp, vertical = 7.dp), fontSize = 14.sp, lineHeight = 18.sp)
            }
        }
        Surface(onClick = genres, shape = RoundedCornerShape(20.dp), color = Palette.raised.copy(alpha = 0.8f)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Lucide.ChevronDown, null, Modifier.size(14.dp))
                Text("Genres", fontSize = 14.sp, lineHeight = 18.sp)
            }
        }
    }
}
