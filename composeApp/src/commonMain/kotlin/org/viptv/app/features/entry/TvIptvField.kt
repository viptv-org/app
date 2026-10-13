package org.viptv.app.features.entry

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.TvAction
import org.viptv.app.theme.Palette

@Composable
internal fun TvIptvField(label: String, value: String, scale: Float, requester: FocusRequester, up: FocusRequester? = null, edit: () -> Unit) {
    TvAction(Modifier.fillMaxWidth().height(96.dp * scale).focusProperties { if (up != null) this.up = up }, scale, requester = requester, activate = edit,
        cornerRadius = 16.dp * scale, background = Color.White.copy(alpha = .07f),
        arrangement = Arrangement.Start, contentPadding = PaddingValues(horizontal = 28.dp * scale)) {
        Text(label, Modifier.width(260.dp * scale), fontSize = (24 * scale).sp, lineHeight = (30 * scale).sp,
            fontWeight = FontWeight.SemiBold, color = if (it == Palette.background) it else Palette.secondary)
        Spacer(Modifier.width(24.dp * scale))
        Text(value.ifEmpty { "—" }, Modifier.weight(1f), fontSize = (30 * scale).sp, lineHeight = (36 * scale).sp,
            fontWeight = FontWeight.Medium, color = it, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
