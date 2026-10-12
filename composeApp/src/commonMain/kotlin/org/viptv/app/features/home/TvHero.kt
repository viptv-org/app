package org.viptv.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.composables.icons.lucide.*
import org.viptv.app.theme.Palette
import org.viptv.app.components.MediaMetadata
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_play_zip
import org.viptv.engine.wire.Card

@Composable
internal fun TvHero(card: Card, open: (Card) -> Unit, play: (Card) -> Unit) {
    val first = remember { FocusRequester() }
    var listNotice by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val scale = maxWidth / 1920.dp
        Box(Modifier.fillMaxWidth().height(800.dp * scale).clipToBounds()) {
            AsyncImage(card.landscape ?: card.poster, null, Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).height(1080.dp * scale), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.2f), 0.75f to Color.Transparent, 1f to Palette.background)))
            Column(Modifier.padding(start = 96.dp * scale, top = 306.dp * scale).width(800.dp * scale),
                verticalArrangement = Arrangement.spacedBy(24.dp * scale)) {
                if (card.logo != null) AsyncImage(card.logo, card.title, Modifier.width(216.dp * scale).height(160.dp * scale),
                    alignment = Alignment.CenterStart, contentScale = ContentScale.Fit)
                else Text(card.title, fontSize = (48 * scale).sp, lineHeight = (56 * scale).sp, fontWeight = FontWeight.Bold)
                MediaMetadata(card, fontSize = 24f, lineHeight = 30f, badgeSize = 18f, scale = scale)
                card.description?.let { Text(it, fontSize = (26 * scale).sp, lineHeight = (38 * scale).sp,
                    color = Palette.body, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp * scale)) {
                    Button(onClick = { play(card) }, modifier = Modifier.width(220.dp * scale).height(72.dp * scale).focusRequester(first),
                        shape = RoundedCornerShape(12.dp * scale), colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) {
                        Icon(painterResource(Res.drawable.ic_play_zip), null, Modifier.size(24.dp * scale)); Spacer(Modifier.width(12.dp * scale))
                        Text("Play", fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
                    }
                    FilledIconButton(onClick = { listNotice = true }, modifier = Modifier.size(72.dp * scale),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Palette.raised, contentColor = Palette.text)) {
                        Icon(Lucide.Plus, "Add to My List", Modifier.size(30.dp * scale))
                    }
                    FilledIconButton(onClick = { open(card) }, modifier = Modifier.size(72.dp * scale),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Palette.raised, contentColor = Palette.text)) {
                        Icon(Lucide.Info, "More info", Modifier.size(30.dp * scale))
                    }
                }
            }
        }
    }
    if (listNotice) AlertDialog(onDismissRequest = { listNotice = false }, title = { Text("My List") },
        text = { Text("Saving titles to My List is not available in this experiment yet.") },
        confirmButton = { TextButton(onClick = { listNotice = false }) { Text("OK") } })
}
