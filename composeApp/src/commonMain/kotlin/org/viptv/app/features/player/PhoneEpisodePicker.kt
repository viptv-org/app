package org.viptv.app.features.player

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.composables.icons.lucide.*
import org.viptv.app.connection.cardLookupKey
import org.viptv.app.connection.sameLookupIdentity
import org.viptv.app.features.details.DetailEpisodeCard
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.app.components.IconButton

internal fun phonePlaybackEpisodes(current: Card?, episodes: List<Card>): List<Card> {
    val parent = current?.parent_identity ?: return emptyList()
    return episodes.filter { it.identity.kind == "series" && it.identity.episode != null &&
        it.parent_identity?.let { identity -> sameLookupIdentity(identity, parent) } == true &&
        it.provider_id == current.provider_id && it.metadata_origin == current.metadata_origin }
        .distinctBy(::cardLookupKey)
}

@Composable
internal fun PhoneEpisodePicker(current: Card?, episodes: List<Card>, choose: (Card) -> Unit, close: () -> Unit,
    insets: WindowInsets) {
    val index = episodes.indexOfFirst { current != null && cardLookupKey(it) == cardLookupKey(current) }.coerceAtLeast(0)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = index)
    val initial = remember { FocusRequester() }
    val latestEpisodes by rememberUpdatedState(episodes)
    val latestChoose by rememberUpdatedState(choose)
    Popup(onDismissRequest = close, properties = PopupProperties(focusable = true)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f))) {
            Column(Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(.56f).background(Palette.surface)
                .windowInsetsPadding(insets.endSheet())) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Episodes", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(close) { Icon(Lucide.X, "Close episodes", Modifier.size(20.dp)) }
                }
                BoxWithConstraints(Modifier.weight(1f)) {
                    val wide = maxWidth >= 360.dp
                    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp), topEdgeFade = false) {
                        itemsIndexed(episodes, key = { _, card -> cardLookupKey(card) }) { position, card ->
                            val active = current != null && cardLookupKey(card) == cardLookupKey(current)
                            DetailEpisodeCard(card, phone = wide, play = {
                                latestEpisodes.find { cardLookupKey(it) == cardLookupKey(card) }?.let(latestChoose)
                            }, modifier = Modifier.fillMaxWidth().then(if (position == index) Modifier.focusRequester(initial) else Modifier)
                                .semantics { selected = active }.background(if (active) Palette.raised else Color.Transparent, RoundedCornerShape(8.dp)),
                                playing = active)
                        }
                    }
                }
            }
        }
        LaunchedEffect(Unit) { withFrameNanos { }; if (episodes.isNotEmpty()) initial.requestFocus() }
    }
}
