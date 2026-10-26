package org.viptv.app.features.sources

import org.viptv.app.components.FocusLazyRow as LazyRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.engine.wire.SourceCandidate

@Composable
internal fun SourceFilters(sources: List<SourceCandidate>, quality: String?, provider: String?,
    qualityChanged: (String?) -> Unit, providerChanged: (String?) -> Unit) {
    @Composable fun Chip(label: String, selected: Boolean, click: () -> Unit) {
        Surface(onClick = click, shape = RoundedCornerShape(18.dp), color = if (selected) Color(0xFFF4F3EF) else Color(0xFF29292B),
            contentColor = if (selected) Color(0xFF171719) else Color(0xFFCCCCCE)) {
            Text(label, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontSize = 12.sp)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf<String?>(null) + sources.mapNotNull { it.qualityLabel() }.distinct()) { option ->
                Chip("${option ?: "All"} ${if (option == null) sources.size else sources.count { it.qualityLabel() == option }}", quality == option) { qualityChanged(option) }
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf<String?>(null) + sources.map { it.sourceFilterId() }.distinct()) { option ->
                Chip(sources.find { it.sourceFilterId() == option }?.providerLabel() ?: "All sources", provider == option) { providerChanged(option) }
            }
        }
    }
}
