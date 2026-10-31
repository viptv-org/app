package org.viptv.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.app.components.Button
import org.viptv.app.components.IconButton
import org.viptv.app.components.rememberArriveFade

@Composable
internal fun PhonePlayerFailure(description: String, fallbackLabel: String?, retry: (() -> Unit)?, choose: (() -> Unit)?, close: () -> Unit,
    title: Card?, insets: WindowInsets) {
    val initial = remember { FocusRequester() }
    val reveal = rememberArriveFade()
    Box(Modifier.fillMaxSize().alpha(reveal).background(Color.Black.copy(alpha = .8f)).windowInsetsPadding(insets)) {
        Row(Modifier.align(Alignment.TopStart).padding(start = 44.dp, end = 48.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconButton(close, Modifier.size(44.dp).then(if (retry == null && choose == null) Modifier.focusRequester(initial) else Modifier)) {
                Icon(Lucide.ChevronLeft, "Back", Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f)) {
                title?.let { Text(it.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                title?.subtitle?.let { Text(it, fontSize = 12.sp, color = Palette.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        Column(Modifier.align(Alignment.Center).widthIn(max = 650.dp).padding(horizontal = 24.dp).semantics { liveRegion = LiveRegionMode.Assertive },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Lucide.CircleAlert, null, Modifier.size(32.dp))
            Text("This source stopped working", fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(description, fontSize = 13.sp, color = Palette.secondary, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (retry != null) Button(retry, Modifier.height(44.dp).weight(1f, fill = false).focusRequester(initial), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) {
                    Icon(Lucide.RefreshCw, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(fallbackLabel?.let { "Try $it" } ?: "Retry", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                if (choose != null) Button(choose, Modifier.height(44.dp).weight(1f, fill = false)
                    .then(if (retry == null) Modifier.focusRequester(initial) else Modifier), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.raised, contentColor = Palette.text)) {
                    Text("Choose a source", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    LaunchedEffect(Unit) { initial.requestFocus() }
}
