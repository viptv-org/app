package org.viptv.app.features.sources

import org.viptv.app.components.FocusLazyRow as LazyRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.TvAction
import org.viptv.app.components.tvRingReach
import org.viptv.app.components.tvRingRoom
import org.viptv.engine.wire.SourceCandidate

@Composable
internal fun TvSourcesFilters(sources: List<SourceCandidate>, info: Map<String, TvSourceInfo>, scale: Float,
    quality: String?, provider: String?, qualityChanged: (String?) -> Unit, providerChanged: (String?) -> Unit,
    qualityFocus: FocusRequester, providerFocus: FocusRequester, focusedProvider: String? = provider,
    providerFocused: (String?) -> Unit = {}, enterRows: () -> Unit) {
    // Room for the pills' outside ring: the row's viewport clip cut it off the
    // first pill and off whichever pill sits on the edge while scrolled.
    val reach = tvRingReach(scale)
    LazyRow(Modifier.tvRingRoom(reach, vertical = false), contentPadding = PaddingValues(horizontal = reach),
        horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
        items(listOf<String?>(null) + sources.mapNotNull { info[it.id]?.quality }.distinct(), key = { it?.let { value -> "quality:$value" } ?: "all" }) { option ->
            TvAction(Modifier.height(52.dp * scale).widthIn(min = 96.dp * scale), scale, activate = { qualityChanged(option) },
                requester = if (quality == option) qualityFocus else null, cornerRadius = 26.dp * scale,
                background = if (quality == option) Color(0xFF393939) else Color.Transparent,
                contentPadding = PaddingValues(horizontal = 20.dp * scale)) {
                Text("${option ?: "All"} ${if (option == null) sources.size else sources.count { info[it.id]?.quality == option }}",
                    color = it, fontSize = (22 * scale).sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    Spacer(Modifier.height(12.dp * scale))
    LazyRow(Modifier.tvRingRoom(reach, vertical = false).onPreviewKeyEvent { event ->
        if (event.key == Key.DirectionDown && sources.isNotEmpty()) {
            if (event.type == KeyEventType.KeyDown) enterRows()
            true
        } else false
    }, contentPadding = PaddingValues(horizontal = reach), horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
        items(listOf<String?>(null) + sources.map { it.sourceFilterId() }.distinct(), key = { it?.let { value -> "provider:$value" } ?: "all" }) { option ->
            TvAction(Modifier.height(44.dp * scale).onFocusChanged { if (it.isFocused) providerFocused(option) }, scale, activate = { providerChanged(option) },
                requester = if (focusedProvider == option) providerFocus else null,
                cornerRadius = 22.dp * scale, background = if (provider == option) Color(0xFF393939) else Color.Transparent,
                contentPadding = PaddingValues(horizontal = 18.dp * scale)) {
                Text(sources.find { it.sourceFilterId() == option }?.providerLabel() ?: "All sources", color = it, fontSize = (20 * scale).sp)
            }
        }
    }
}
