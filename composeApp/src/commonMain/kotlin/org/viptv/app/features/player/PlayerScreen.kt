package org.viptv.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.viptv.app.player.*
import org.viptv.app.theme.Palette

@Composable
fun PlayerScreen(input: PlaybackInput?, paused: Boolean, error: String?, toggle: () -> Unit, observe: (PlayerFact) -> Unit, close: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Palette.background)) {
        if (input != null) PlayerSurface(input, paused, Modifier.fillMaxSize(), observe)
        Row(Modifier.align(Alignment.TopStart).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = close) { Text("Back") }
            if (input != null) Button(onClick = toggle) { Text(if (paused) "Play" else "Pause") }
        }
        error?.let { Text(it, Modifier.align(Alignment.BottomCenter).padding(24.dp), color = Palette.danger) }
    }
}
