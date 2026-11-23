package org.viptv.app.features.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.PopoverMenu
import org.viptv.app.components.PopoverMenuDivider
import org.viptv.app.components.PopoverMenuItem
import org.viptv.app.components.holdMenu
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.secondaryClickMenu
import org.viptv.app.connection.seasonNumber
import org.viptv.app.downloads.DownloadItem
import org.viptv.app.downloads.DownloadState
import org.viptv.app.features.details.episodeUpcoming
import org.viptv.app.features.details.lastReleasedOrder
import org.viptv.app.features.details.watchedEpisode
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

internal enum class TitleDownloadStyle { Phone, Tablet, Desktop }

internal val NEXT_EPISODE_COUNTS = listOf(3, 5, 10)

private const val WATCHED_FRACTION = 0.95

internal class SeriesDownloadPlan(episodes: List<Card>, index: Map<String, DownloadItem>, nowMillis: Long) {
    private val released: List<Card> = lastReleasedOrder(episodes, nowMillis).let { last ->
        episodes.filter { !episodeUpcoming(it, last, nowMillis) }
            .sortedWith(compareBy({ it.identity.episode?.seasonNumber() ?: 0L }, { it.identity.episode?.episode ?: 0L }))
    }
    private val wanted: List<Card> = released.filter { card -> index[downloadKey(card)].let { it == null || it.state == DownloadState.Failed } }

    fun season(number: Long): List<Card> = wanted.filter { it.identity.episode?.seasonNumber() == number }

    private val regular = released.filter { (it.identity.episode?.seasonNumber() ?: 0L) > 0L }

    fun next(count: Int): List<Card> {
        val lastWatched = regular.indexOfLast { it.watchedEpisode() || (it.progress ?: 0.0) >= WATCHED_FRACTION }
        return regular.drop(lastWatched + 1).take(count).filter { it in wanted }
    }

    fun series(): List<Card> = regular.filter { it in wanted }
}

@Composable
internal fun TitleDownloadAction(card: Card, episodes: List<Card>, season: Long?, style: TitleDownloadStyle, modifier: Modifier = Modifier) {
    val actions = LocalDownloadActions.current ?: return
    val index = LocalDownloadIndex.current
    val series = card.identity.kind == "series" || episodes.isNotEmpty()
    var menu by remember { mutableStateOf(false) }
    val item = if (series) null else index[downloadKey(card)]
    val requested = !series && actions.requested(card)
    val state = if (series) DownloadControlState.None else item.controlState(requested)
    val label = when (state) {
        DownloadControlState.None, DownloadControlState.Failed -> "Download"
        DownloadControlState.Requested, DownloadControlState.Queued -> "Queued"
        DownloadControlState.Completed -> "Downloaded"
        else -> "${item?.percent() ?: 0}%"
    }
    val press: () -> Unit = {
        when {
            series -> menu = true
            state == DownloadControlState.None -> actions.title(card)
            state == DownloadControlState.Failed -> item?.let(actions::retry)
            state == DownloadControlState.Requested -> Unit
            else -> menu = true
        }
    }
    val name = if (series) "Download" else controlName(card, item, requested)
    val hold = !series && state != DownloadControlState.Requested
    val openMenu = { _: androidx.compose.ui.unit.IntOffset -> menu = true }
    val described = Modifier.semantics {
        contentDescription = name
        if (hold) customActions = listOf(CustomAccessibilityAction(if (item == null) CHOOSE_SOURCE else CHANGE_SOURCE) {
            if (item == null) actions.chooseSource(card) else actions.changeSource(item.id); true
        })
    }
    Box(modifier) {
        when (style) {
            TitleDownloadStyle.Phone -> Column(Modifier.width(76.dp).secondaryClickMenu(hold, openMenu).pointerClickable(role = Role.Button, onClick = press)
                .holdMenu(hold, openMenu).then(described), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(44.dp).background(Color.White.copy(alpha = .08f), CircleShape), contentAlignment = Alignment.Center) {
                    DownloadGlyph(state, item?.fraction, 22.dp)
                }
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.body, maxLines = 1)
            }
            TitleDownloadStyle.Desktop -> Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = .12f))
                .secondaryClickMenu(hold, openMenu).pointerClickable(role = Role.Button, shape = RoundedCornerShape(22.dp), onClick = press)
                .holdMenu(hold, openMenu).then(described), contentAlignment = Alignment.Center) {
                DownloadGlyph(state, item?.fraction, 22.dp)
            }
            TitleDownloadStyle.Tablet -> Row(Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = .08f))
                .secondaryClickMenu(hold, openMenu).pointerClickable(role = Role.Button, shape = RoundedCornerShape(12.dp), onClick = press)
                .holdMenu(hold, openMenu).then(described).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DownloadGlyph(state, item?.fraction, 18.dp)
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (series) {
            val plan = remember(episodes, index) { SeriesDownloadPlan(episodes, index, actions.clock()) }
            PopoverMenu(menu, { menu = false }, paneTitle = "Download") {
                val close = { act: () -> Unit -> { menu = false; act() } }
                season?.let { number ->
                    PopoverMenuItem("Download season", Lucide.ArrowDownToLine,
                        onClick = close { actions.season(card, number, plan.season(number)) })
                }
                NEXT_EPISODE_COUNTS.forEach { count ->
                    PopoverMenuItem("Download next $count", Lucide.ListVideo, onClick = close { actions.next(card, count, plan.next(count)) })
                }
                PopoverMenuDivider()
                PopoverMenuItem("Download series", Lucide.Library, onClick = close { actions.series(card, plan.series()) })
            }
        } else if (item != null) DownloadItemMenu(item, actions, menu) { menu = false }
        else DownloadStartMenu(card, actions, menu) { menu = false }
    }
}
