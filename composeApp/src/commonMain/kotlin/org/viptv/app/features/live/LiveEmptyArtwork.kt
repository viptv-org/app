package org.viptv.app.features.live

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.resources.Res
import org.viptv.app.resources.live_empty_stadium_tall
import org.viptv.app.resources.live_empty_stadium_wide

// Two hand-authored scenes with the reading scrim baked in: draw one, add no overlay.
@Composable
internal fun LiveEmptyArtwork(wide: Boolean, modifier: Modifier = Modifier) {
    Image(painterResource(if (wide) Res.drawable.live_empty_stadium_wide else Res.drawable.live_empty_stadium_tall),
        contentDescription = null, modifier = modifier.fillMaxSize(), contentScale = ContentScale.Crop,
        alignment = Alignment.Center)
}