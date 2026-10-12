package org.viptv.app.features.library

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.TvAction
import org.viptv.app.theme.Palette

@Composable
internal fun TvLibraryControls(filter: String, changeFilter: (String) -> Unit, sort: String, changeSort: (String) -> Unit,
    scale: Float, selected: FocusRequester, first: FocusRequester?, watchedAvailable: Boolean) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.focusProperties { onEnter = { selected.requestFocus() } }.focusGroup()
            .background(androidx.compose.ui.graphics.Color(0xFF2A2A2E), RoundedCornerShape(32.dp * scale)).padding(6.dp * scale)) {
            listOf("All", "Movies", "Series").forEach { choice ->
                TvAction(Modifier.width(168.dp * scale).height(52.dp * scale).focusProperties { if (first != null) down = first },
                    scale, requester = if (choice == filter) selected else null, activate = { changeFilter(choice) },
                    background = if (choice == filter) Palette.background else androidx.compose.ui.graphics.Color.Transparent,
                    cornerRadius = 26.dp * scale) {
                    Text(choice, color = if (choice == filter || it == Palette.background) it else Palette.secondary, fontSize = (24 * scale).sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Box {
            TvAction(Modifier.height(64.dp * scale).focusProperties { if (first != null) down = first }, scale, activate = { menu = true }, cornerRadius = 32.dp * scale,
                contentPadding = PaddingValues(horizontal = 24.dp * scale)) {
                Text("Sort", color = if (it == Palette.background) it else Palette.secondary, fontSize = (24 * scale).sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(12.dp * scale)); Text(sort, color = it, fontSize = (24 * scale).sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(12.dp * scale)); Icon(Lucide.ChevronDown, "Sort options", Modifier.size(24.dp * scale), it)
            }
            DropdownMenu(menu, onDismissRequest = { menu = false }, modifier = Modifier.width(360.dp * scale)) {
                (listOf("Date added", "A–Z", "Z–A") + if (watchedAvailable) listOf("Last watched") else emptyList()).forEach { option ->
                    DropdownMenuItem(text = { Text(option, fontSize = (24 * scale).sp) }, modifier = Modifier.height(64.dp * scale),
                        onClick = { changeSort(option); menu = false })
                }
            }
        }
    }
}
