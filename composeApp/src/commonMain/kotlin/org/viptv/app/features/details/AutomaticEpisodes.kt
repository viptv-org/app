package org.viptv.app.features.details

import androidx.compose.runtime.*
import kotlinx.coroutines.delay

@Composable
internal fun AutomaticEpisodes(season: DetailSeason?, legacyCursor: String?, busy: Boolean,
    load: (Long) -> Unit, enabled: Boolean = true, scopeKey: Any? = null) {
    val offset = season?.nextOffset ?: if (season?.total == null) legacyCursor?.toLongOrNull() else null
    val latestLoad by rememberUpdatedState(load)
    LaunchedEffect(scopeKey, season?.number, offset, busy, enabled) {
        if (enabled && !busy && offset != null) { delay(150); latestLoad(offset) }
    }
}
