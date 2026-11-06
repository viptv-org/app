package org.viptv.app.features.details

import org.viptv.app.components.FocusLazyRow as LazyRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import org.viptv.app.theme.Palette
import org.viptv.app.components.Tab

internal const val RELATED_HEADING = "More like this"

internal enum class DetailSection(val label: String) {
    Episodes("Episodes"), Related(RELATED_HEADING), Information("Details")
}

internal fun detailSections(episodes: Boolean, related: Boolean, information: Boolean): List<DetailSection> =
    DetailSection.entries.filter {
        when (it) { DetailSection.Episodes -> episodes; DetailSection.Related -> related; DetailSection.Information -> information }
    }

@Composable
internal fun DetailSections(selected: DetailSection, episodes: Boolean, related: Boolean, information: Boolean,
    select: (DetailSection) -> Unit, modifier: Modifier = Modifier) {
    val sections = detailSections(episodes, related, information)
    if (sections.isEmpty()) return
    Column(modifier) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), wheelScrollsRow = true) {
            items(sections) { section ->
                Tab(selected == section, onClick = { select(section) },
                    modifier = Modifier.drawBehind { if (selected == section) {
                        val stroke = 2.dp.toPx()
                        drawLine(Palette.text, Offset(16.dp.toPx(), size.height - stroke / 2),
                            Offset(size.width - 16.dp.toPx(), size.height - stroke / 2), stroke)
                    } },
                    selectedContentColor = Palette.text, unselectedContentColor = Palette.secondary,
                    text = { Text(section.label, maxLines = 1) })
            }
        }
        HorizontalDivider(color = Palette.raised)
    }
}
