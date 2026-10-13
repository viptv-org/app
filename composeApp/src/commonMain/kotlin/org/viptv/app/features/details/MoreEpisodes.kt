package org.viptv.app.features.details

import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun MoreEpisodes(season: DetailSeason?, legacyCursor: String?, busy: Boolean,
    load: (Long) -> Unit, modifier: Modifier = Modifier) {
    val offset = season?.nextOffset ?: if (season?.total == null) legacyCursor?.toLongOrNull() else null
    if (offset != null) OutlinedButton(onClick = { if (!busy) load(offset) }, modifier = modifier) {
        Text(if (busy) "Loading episodes…" else "Load more episodes")
    }
}
