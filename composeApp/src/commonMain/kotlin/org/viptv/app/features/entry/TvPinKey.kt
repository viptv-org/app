package org.viptv.app.features.entry

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.TvAction

@Composable
internal fun TvPinKey(label: String, scale: Float, enabled: Boolean, requester: FocusRequester?, width: Int = 136,
    numberSize: Int = 34, action: () -> Unit) {
    TvAction(Modifier.size(width.dp * scale, 76.dp * scale), scale, requester = requester, activate = action,
        enabled = enabled, background = Color.White.copy(alpha = .09f), cornerRadius = 10.dp * scale) {
        if (label == "Erase") Icon(Lucide.Delete, "Erase digit", Modifier.size(28.dp * scale), it)
        else Text(label, color = it.copy(alpha = if (enabled) 1f else .4f), fontSize = (if (label == "OK") 22 * scale else numberSize * scale).sp,
            lineHeight = (40 * scale).sp, fontWeight = FontWeight.SemiBold)
    }
}
