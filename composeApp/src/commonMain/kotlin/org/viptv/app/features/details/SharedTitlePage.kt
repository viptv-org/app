package org.viptv.app.features.details

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.viptv.app.components.MediaMetadata
import org.viptv.app.components.Brand
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.DetailSnapshot

/** W3TitlePage presentation. Routing requires a real public metadata contract. */
@Composable
internal fun SharedTitlePage(detail: DetailSnapshot, signIn: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 900.dp
        val gutter = if (maxWidth < 640.dp) 20.dp else 57.dp
        LazyColumn(contentPadding = PaddingValues(top = 100.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
            item {
                if (compact) Column(Modifier.padding(horizontal = gutter), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    SharedTitleArtwork(detail.item, Modifier.fillMaxWidth().aspectRatio(16f / 9))
                    SharedTitleSummary(detail.item, signIn, Modifier.fillMaxWidth())
                } else Row(Modifier.fillMaxWidth().padding(horizontal = gutter), horizontalArrangement = Arrangement.spacedBy(40.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    SharedTitleSummary(detail.item, signIn, Modifier.weight(.44f))
                    SharedTitleArtwork(detail.item, Modifier.weight(.56f).aspectRatio(16f / 9))
                }
            }
            detailSeasons(detail.episodes).forEach { season ->
                item {
                    Text(season.label, Modifier.padding(horizontal = gutter), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = gutter), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(season.episodes, key = ::episodeKey) { episode ->
                            DetailEpisodeCard(episode, false, { signIn() }, Modifier.width(300.dp))
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(68.dp).padding(horizontal = gutter), verticalAlignment = Alignment.CenterVertically) {
            Brand(28.dp, 20.sp, 9.dp, letterSpacing = (-.6).sp)
            Spacer(Modifier.weight(1f))
            Button(onClick = signIn, modifier = Modifier.height(36.dp), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) {
                Text("Sign in", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SharedTitleArtwork(card: Card, modifier: Modifier) {
    AsyncImage(card.landscape ?: card.poster, null, modifier.clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
}

@Composable
private fun SharedTitleSummary(card: Card, signIn: () -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (card.logo != null) AsyncImage(card.logo, card.title, Modifier.widthIn(max = 340.dp).fillMaxWidth().height(112.dp),
            alignment = Alignment.CenterStart, contentScale = ContentScale.Fit)
        else Text(card.title, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
        MediaMetadata(card)
        card.description?.let { Text(it, fontSize = 16.sp, lineHeight = 23.sp, color = Palette.body) }
        Button(onClick = signIn, modifier = Modifier.width(200.dp).height(48.dp), shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) {
            Text("Sign in to watch", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Text("Already using VIPTV on a TV? Sign in here with the same account.", fontSize = 14.sp, lineHeight = 20.sp, color = Palette.secondary)
    }
}
