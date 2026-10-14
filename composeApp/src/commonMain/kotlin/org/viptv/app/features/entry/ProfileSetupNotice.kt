package org.viptv.app.features.entry

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.TvAction
import org.viptv.app.theme.Palette

@Composable
internal fun ProfileSetupNotice(notice: String?, busy: Boolean, television: Boolean, cancel: () -> Unit,
    backChanged: ((() -> Unit)?) -> Unit) {
    if (notice == null) return
    DisposableEffect(Unit) { backChanged(cancel); onDispose { backChanged(null) } }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val scale = if (television) maxWidth / 1920.dp else 1f
        Column(Modifier.fillMaxWidth().padding(top = if (television) 56.dp * scale else 20.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(notice, Modifier.widthIn(max = 760.dp * scale), color = Palette.secondary,
                fontSize = if (television) (24 * scale).sp else 14.sp, textAlign = TextAlign.Center)
            if (television) TvAction(Modifier.height(48.dp * scale).padding(horizontal = 12.dp * scale), scale,
                enabled = !busy, activate = cancel, contentPadding = PaddingValues(horizontal = 16.dp * scale)) {
                Text("Cancel provider setup", color = it, fontSize = (20 * scale).sp)
            } else TextButton(cancel, enabled = !busy) { Text("Cancel provider setup") }
        }
    }
}
