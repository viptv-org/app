package org.viptv.app.features.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.components.pointerClickable
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_remote_zip
import org.viptv.app.theme.Palette

@Composable
internal fun RemoteHeaderButton(
    tvName: String,
    showTip: Boolean,
    dismissTip: () -> Unit,
    open: () -> Unit,
) {
    val density = LocalDensity.current
    Box {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Palette.surface)
            .pointerClickable(onClickLabel = "TV remote: $tvName", shape = CircleShape, hover = null) { open() },
            contentAlignment = Alignment.Center) {
            Icon(painterResource(Res.drawable.ic_remote_zip), "TV remote: $tvName", Modifier.size(20.dp), tint = Palette.text)
        }
        if (showTip) {
            Popup(alignment = Alignment.TopEnd, offset = IntOffset(0, with(density) { 48.dp.roundToPx() }),
                onDismissRequest = dismissTip, properties = PopupProperties(focusable = true)) {
                Column(Modifier.width(260.dp).clip(RoundedCornerShape(16.dp)).background(Palette.text).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Your TV remote", color = RemoteOnLight, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("It stays up here on ${RemoteHeaderPages.names}. Turn it off in Settings › Watch on TV.",
                        color = RemoteOnLight, fontSize = 14.sp)
                    Text("Got it", Modifier.pointerClickable { dismissTip() }, color = RemoteOnLight, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}