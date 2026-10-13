package org.viptv.app.features.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.theme.Palette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailSeasonPicker(seasons: List<DetailSeason>, selected: DetailSeason, phone: Boolean,
    choose: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!phone) Text("Episodes", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Box {
            TextButton(onClick = { open = true }, shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.textButtonColors(containerColor = Color.White.copy(alpha = .08f), contentColor = Palette.text),
                contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.height(38.dp)) {
                Text(if (selected.number == null && seasons.size == 1) "Episodes" else selected.label,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Icon(Lucide.ChevronDown, "Choose season", Modifier.size(16.dp))
            }
            if (!phone) DropdownMenu(expanded = open, onDismissRequest = { open = false },
                modifier = Modifier.widthIn(min = 240.dp), containerColor = Color(0xFF242428), shape = RoundedCornerShape(10.dp)) {
                seasons.forEach { season ->
                    DropdownMenuItem(text = { Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(season.label, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(12.dp))
                        Text(season.countLabel, color = Palette.secondary, fontSize = 12.sp)
                    } }, leadingIcon = { Icon(if (season.number == selected.number) Lucide.Check else Lucide.List,
                        null, Modifier.size(16.dp)) }, onClick = { choose(season.number); open = false })
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(selected.countLabel, color = Palette.secondary, fontSize = 13.sp)
    }
    if (phone && open) ModalBottomSheet(onDismissRequest = { open = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF1A1A1D), contentColor = Palette.text,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { Box(Modifier.padding(top = 8.dp, bottom = 4.dp).size(36.dp, 5.dp)
            .background(Color.White.copy(alpha = .3f), RoundedCornerShape(3.dp))) }) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Seasons", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = { open = false }, modifier = Modifier.size(32.dp)
                .background(Color.White.copy(alpha = .14f), RoundedCornerShape(16.dp))) {
                Icon(Lucide.X, "Close seasons", Modifier.size(18.dp))
            }
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 430.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(seasons, key = { it.number?.toString() ?: "unknown" }) { season ->
                val active = season.number == selected.number
                Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).selectable(active, role = Role.RadioButton,
                    onClick = { choose(season.number); open = false }).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(season.label, Modifier.weight(1f), fontSize = 17.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) Palette.text else Palette.body)
                    Text(season.countLabel, fontSize = 13.sp, color = Palette.secondary)
                }
            }
        }
    }
}
