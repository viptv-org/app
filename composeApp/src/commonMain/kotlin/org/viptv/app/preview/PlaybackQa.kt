package org.viptv.app.preview

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.viptv.app.features.player.PlayerScreen
import org.viptv.app.player.*
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity

/** Isolated public-media QA. It has no account, provider, database or history connection. */
@Composable
fun PlaybackQa(close: () -> Unit = {}, canvasColor: Color = Color.Black) {
    val input = remember { PlaybackInput("public-flower-qa", "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4") }
    val title = remember { Card(MediaIdentity("public-flower-qa", "movie", emptyList()), "qa", "Flower · Public QA clip", badges = emptyList()) }
    var paused by remember { mutableStateOf(true) }
    ViptvTheme {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            PlayerScreen(input, paused, null, { paused = !paused }, { fact ->
                if (fact is PlayerFact.Status) when (fact.name) {
                    "Playing" -> paused = false
                    "Paused", "Ended" -> paused = true
                }
            }, close, title, canvasColor = canvasColor)
        }
    }
}
