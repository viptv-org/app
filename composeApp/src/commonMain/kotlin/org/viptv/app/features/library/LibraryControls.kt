package org.viptv.app.features.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.theme.Palette
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.DropdownMenuItem
import org.viptv.app.components.Surface

@Composable
internal fun LibraryControls(filter: String, setFilter: (String) -> Unit, sort: String, setSort: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.background(Palette.raised, RoundedCornerShape(20.dp)).padding(4.dp)) {
            listOf("All", "Movies", "Series").forEach { choice ->
                Text(choice, Modifier.background(if (choice == filter) Palette.background else Palette.raised, RoundedCornerShape(18.dp))
                    .pointerClickable (shape = RoundedCornerShape(18.dp)) { setFilter(choice) }.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 12.sp)
            }
        }
        Box {
            Surface(onClick = { menu = true }, shape = RoundedCornerShape(20.dp), color = Palette.raised) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.SlidersHorizontal, null, Modifier.size(14.dp))
                    Text(sort, fontSize = 12.sp)
                }
            }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                listOf("Date added", "A–Z", "Z–A").forEach { choice ->
                    DropdownMenuItem(text = { Text(choice) }, onClick = { setSort(choice); menu = false })
                }
            }
        }
    }
}
