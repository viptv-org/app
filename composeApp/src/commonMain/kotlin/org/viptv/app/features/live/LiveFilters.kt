package org.viptv.app.features.live

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette

@Composable
internal fun LiveFilters(categories: List<String>, selected: String, choose: (String) -> Unit, scale: Float = 1f, labels: Map<String, String> = emptyMap()) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
        (listOf("All") + categories).distinct().forEach { category ->
            Surface(onClick = { choose(category) }, color = if (category == selected) Palette.text else Palette.raised,
                contentColor = if (category == selected) Palette.background else Palette.text, shape = RoundedCornerShape(24.dp * scale)) {
                Text(labels[category] ?: category, Modifier.padding(horizontal = 14.dp * scale, vertical = 7.dp * scale),
                    fontSize = (14 * scale).sp, lineHeight = (18 * scale).sp)
            }
        }
    }
}
