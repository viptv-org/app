package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.WifiOff
import com.composables.icons.lucide.X
import org.viptv.app.components.tvBackdropBlur
import org.viptv.app.theme.Palette

/**
 * The controller reports an unreachable server with this exact message
 * (`ApplicationController.run`, the transport-failure branch). DUI-04's brief
 * introduces `ApplicationState.offline` for this; until that field lands in the
 * shared state, the desktop chrome matches the message instead of inventing a
 * second connection probe.
 */
internal const val ServerUnreachableMessage = "Could not reach your server. Check the connection and try again."

/**
 * Board D3Offline: a 44 dp info bar over the content area that keeps the cached
 * Home rows usable while the server is unreachable. It is chrome, not a page, so
 * it lives in desktopMain and the 72 dp rail stays visible beside it.
 */
@Composable
internal fun DesktopOfflineBar(retry: () -> Unit, dismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(44.dp).tvBackdropBlur(20.dp)
        .background(Color.White.copy(alpha = .1f))
        .drawBehind {
            val hairline = 1.dp.toPx()
            drawLine(Color.White.copy(alpha = .08f), Offset(0f, size.height - hairline),
                Offset(size.width, size.height - hairline), hairline)
        }
        .padding(start = 40.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Lucide.WifiOff, null, Modifier.size(16.dp), tint = Palette.text)
        Text("You’re offline. Showing what was saved; new rows load when you’re back.",
            Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.height(30.dp).clip(RoundedCornerShape(10.dp))
            .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(10.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                onClickLabel = "Retry", onClick = retry)
            .padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text("Retry", fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, color = Palette.text)
        }
        Box(Modifier.size(28.dp).clip(CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                onClickLabel = "Dismiss", onClick = dismiss),
            contentAlignment = Alignment.Center) {
            Icon(Lucide.X, null, Modifier.size(14.dp), tint = Palette.text)
        }
    }
}
