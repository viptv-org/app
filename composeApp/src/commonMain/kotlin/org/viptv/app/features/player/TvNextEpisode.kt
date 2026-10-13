package org.viptv.app.features.player

import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.components.TvAction
import org.viptv.app.components.TvMediaMetadata
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_play_zip
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
internal fun TvNextEpisode(current: Card?, next: Card, scale: Float, play: () -> Unit, keepWatching: () -> Unit,
    creditsLabel: String? = null) {
    val initial = remember { FocusRequester() }
    val arrival = remember { Animatable(0f) }
    LaunchedEffect(Unit) { arrival.animateTo(1f, tween(450, delayMillis = 350, easing = CubicBezierEasing(.2f, 0f, 0f, 1f))) }
    LaunchedEffect(Unit) { initial.requestFocus() }
    val motion = Modifier.graphicsLayer { alpha = arrival.value; translationY = 16.dp.toPx() * scale * (1f - arrival.value) }
    val fixedLines = LocalTextStyle.current.copy(lineHeightStyle = LineHeightStyle(
        LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None, LineHeightStyle.Mode.Fixed))
    val heading = next.identity.episode?.let { "${it.episode}. ${next.title}" } ?: next.title
    var headingLines by remember(heading) { mutableIntStateOf(1) }
    Box(Modifier.fillMaxSize()) {
        Text(creditsLabel ?: listOfNotNull(current?.title, "Credits").joinToString(" · "), Modifier.offset(96.dp * scale, 616.dp * scale).then(motion),
            fontSize = (22 * scale).sp, lineHeight = (32 * scale).sp, color = Palette.secondary)
        Column(Modifier.offset(1040.dp * scale, 120.dp * scale).width(784.dp * scale).then(motion), verticalArrangement = Arrangement.spacedBy(18.dp * scale)) {
            Text("Next episode", color = Palette.secondary, fontSize = (24 * scale).sp, lineHeight = (35 * scale).sp, fontWeight = FontWeight.SemiBold)
            Text(heading, Modifier.height((59.8f * headingLines).dp * scale).wrapContentHeight(Alignment.CenterVertically, unbounded = true),
                onTextLayout = { headingLines = it.lineCount },
                fontSize = (52 * scale).sp, lineHeight = (59.8f * scale).sp, letterSpacing = (-1.04f * scale).sp, fontWeight = FontWeight.Bold, style = fixedLines)
            TvMediaMetadata(next, scale = scale, lineHeight = 30f, subtitleParts = listOfNotNull(
                next.identity.episode?.let { listOfNotNull(it.season?.let { season -> "S$season" }, "E${it.episode}").joinToString(" ") }, next.subtitle))
            next.description?.let { Text(it, fontSize = (26 * scale).sp, lineHeight = (37.7f * scale).sp, color = Palette.body, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            Spacer(Modifier.height(2.dp * scale))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                TvAction(Modifier.width(317.dp * scale).height(72.dp * scale), scale, primary = true, requester = initial, activate = play) {
                    Icon(painterResource(Res.drawable.ic_play_zip), null, Modifier.size(24.dp * scale), it); Spacer(Modifier.width(14.dp * scale))
                    Text("Play next episode", color = it, fontSize = (26 * scale).sp, lineHeight = (37.7f * scale).sp, fontWeight = FontWeight.Bold)
                }
                TvAction(Modifier.width(340.dp * scale).height(72.dp * scale), scale, activate = keepWatching) {
                    Text("Keep watching credits", color = it, fontSize = (26 * scale).sp, lineHeight = (37.7f * scale).sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("Nothing plays on its own — the credits keep running until you choose.", Modifier.padding(top = 6.dp * scale), color = Color(0xFF8F8D89),
                fontSize = (22 * scale).sp, lineHeight = (32 * scale).sp)
        }
    }
}
