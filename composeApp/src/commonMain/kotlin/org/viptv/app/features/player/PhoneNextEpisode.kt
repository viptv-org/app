package org.viptv.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import com.composables.icons.lucide.*
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.app.components.Button

/** An explicit choice, never a countdown or automatic playback transition. */
@Composable
internal fun PhoneNextEpisode(next: Card, play: () -> Unit, credits: () -> Unit, insets: WindowInsets) {
    val initial = remember { FocusRequester() }
    Popup(onDismissRequest = credits, properties = PopupProperties(focusable = true)) {
        // Escape on desktop/web dismisses it like Back, rather than leaving the player underneath.
        BoxWithConstraints(Modifier.fillMaxSize().onPreviewKeyEvent { it.key == Key.Escape && it.type == KeyEventType.KeyDown && run { credits(); true } }
            .background(Color.Black.copy(alpha = .35f)).windowInsetsPadding(insets)) {
            val compact = maxWidth < 640.dp
            val padding = if (maxWidth >= 800.dp) 56.dp else 24.dp
            val information: @Composable () -> Unit = {
                Row(Modifier.widthIn(max = 360.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(128.dp, 72.dp).clip(RoundedCornerShape(8.dp)).background(Palette.surface)) {
                        (next.landscape ?: next.poster)?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Up next", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.secondary)
                        Text(next.identity.episode?.let { "${it.episode}. ${next.title}" } ?: next.title,
                            fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        next.metadata?.runtime_minutes?.let { Text("${it}m", fontSize = 12.sp, color = Palette.secondary) }
                    }
                }
            }
            val actions: @Composable () -> Unit = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(credits, Modifier.height(44.dp).focusRequester(initial), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.raised, contentColor = Palette.text),
                        contentPadding = PaddingValues(horizontal = 16.dp)) { Text("Watch credits", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    Button(play, Modifier.height(44.dp), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background),
                        contentPadding = PaddingValues(horizontal = 16.dp)) {
                        Icon(Lucide.SkipForward, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Text("Next episode", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (compact) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = padding, vertical = 26.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) { information(); actions() }
            else Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = padding, vertical = 26.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(Modifier.weight(1f)) { information() }; actions()
            }
        }
        LaunchedEffect(Unit) { initial.requestFocus() }
    }
}
