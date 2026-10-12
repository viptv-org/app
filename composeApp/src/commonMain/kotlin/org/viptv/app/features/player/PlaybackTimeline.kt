package org.viptv.app.features.player

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette

@Composable
internal fun PlaybackTimeline(position: Long, duration: Long?, seek: (Long) -> Unit, remaining: Boolean = false) {
    var scrub by remember { mutableStateOf<Float?>(null) }
    val length = duration?.takeIf { it > 0 }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(playbackTime(position), fontSize = 12.sp, color = Palette.text)
        PlaybackSlider(value = scrub ?: if (length != null) (position.toDouble() / length).toFloat().coerceIn(0f, 1f) else 0f,
            change = { scrub = it }, modifier = Modifier.weight(1f), enabled = length != null,
            finish = { scrub?.let { value -> if (length != null) seek((value * length).toLong()) }; scrub = null },
            accent = Palette.accent)
        Text(length?.let { if (remaining) "-" + playbackTime((it - position).coerceAtLeast(0)) else playbackTime(it) } ?: "--:--", fontSize = 12.sp, color = Palette.secondary)
    }
}

internal fun playbackTime(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    val minutes = seconds / 60
    return if (minutes >= 60) "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"
    else "$minutes:${(seconds % 60).toString().padStart(2, '0')}"
}

internal fun playbackSpeed(value: Double) = value.toString().removeSuffix(".0") + "×"
