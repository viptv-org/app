package org.viptv.app.features.sources

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import org.viptv.app.components.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.viptv.app.components.frameDelay
import org.viptv.engine.wire.Card

@Composable
internal fun SourceReuseNotice(card: Card, choose: () -> Unit) {
    var manual by remember(card.identity) { mutableStateOf(false) }
    LaunchedEffect(card.identity) { frameDelay(700); manual = true }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomCenter) {
        Snackbar(Modifier.widthIn(max = 640.dp).semantics { liveRegion = LiveRegionMode.Polite },
            action = { if (manual) TextButton(choose) { Text("Choose source") } }) {
            Text("Finding a matching source…")
        }
    }
}
