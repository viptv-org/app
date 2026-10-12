package org.viptv.app.features.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.X
import org.viptv.app.theme.Palette

@Composable
internal fun SearchField(term: String, query: (String) -> Unit) {
    BasicTextField(term, query, modifier = Modifier.fillMaxWidth().height(44.dp), singleLine = true,
        textStyle = TextStyle(color = Palette.text, fontSize = 16.sp), cursorBrush = SolidColor(Palette.accent),
        decorationBox = { input ->
            Row(Modifier.fillMaxSize().background(Palette.raised, RoundedCornerShape(12.dp)).padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Lucide.Search, "Search", Modifier.size(18.dp), tint = Palette.text)
                Box(Modifier.weight(1f)) {
                    if (term.isEmpty()) Text("Search movies, series and channels", fontSize = 14.sp, color = Palette.secondary)
                    input()
                }
                IconButton(onClick = { query("") }, modifier = Modifier.size(40.dp), enabled = term.isNotEmpty()) {
                    Icon(Lucide.X, "Clear search", Modifier.size(18.dp), tint = Palette.secondary)
                }
            }
        })
}
