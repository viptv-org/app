package org.viptv.app.features.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.DetailSnapshot

internal data class DetailFact(val label: String, val value: String)

internal fun DetailSnapshot.detailFacts(): List<DetailFact> = buildList {
    if (seasons.isNotEmpty()) {
        add(DetailFact(if (seasons.size == 1) "Season" else "Seasons", groupedCount(seasons.size.toLong())))
        val episodes = seasons.sumOf { it.episode_count }
        if (episodes > 0) add(DetailFact(if (episodes == 1L) "Episode" else "Episodes", groupedCount(episodes)))
    }
    val metadata = item.metadata
    metadata?.rating_votes?.takeIf { it > 0 }?.let { add(DetailFact("Votes", groupedCount(it))) }
    metadata?.alternate_titles.orEmpty().take(4).takeIf { it.isNotEmpty() }?.let { titles ->
        add(DetailFact("Also known as", titles.joinToString(" · ")))
    }
}

internal fun groupedCount(value: Long): String =
    value.toString().reversed().chunked(3).joinToString(",").reversed()

@Composable
internal fun DetailFacts(facts: List<DetailFact>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        facts.forEach { fact ->
            Row(Modifier.fillMaxWidth()) {
                Text(fact.label, Modifier.width(104.dp), fontSize = 13.sp, lineHeight = 19.sp, color = Palette.secondary)
                Text(fact.value, Modifier.weight(1f), fontSize = 14.sp, lineHeight = 20.sp, color = Palette.body)
            }
        }
    }
}
