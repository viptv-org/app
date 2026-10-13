package org.viptv.app.features.player

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.TvAction

@Composable
internal fun BoxScope.TvSkipPrompt(skip: TvSkipRange, scale: Float, activate: () -> Unit) {
    val initial = remember { FocusRequester() }
    LaunchedEffect(skip) { initial.requestFocus() }
    TvAction(Modifier.align(Alignment.BottomEnd).padding(end = 96.dp * scale, bottom = 112.dp * scale).width(280.dp * scale).height(72.dp * scale),
        scale, primary = true, requester = initial, activate = activate) {
        Icon(Lucide.SkipForward, null, Modifier.size(28.dp * scale), it); Spacer(Modifier.width(12.dp * scale))
        Text(skip.label, color = it, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
    }
}
