package org.viptv.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.viptv.app.connection.ProfilePreferences
import org.viptv.app.resources.Res
import org.viptv.app.theme.Palette

@Composable
internal fun SubtitleStylePreview(value: ProfilePreferences, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().aspectRatio(16f / 9).clip(RoundedCornerShape(14.dp)).background(Palette.surface)) {
        AsyncImage(Res.getUri("files/entry/subtitle-preview.jpg"), "Subtitle style preview", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Text("I didn’t hear you come in.", Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
            .background(phonePreferenceColor(value.subtitle_background_color, Color.Transparent)).padding(horizontal = 6.dp, vertical = 2.dp),
            color = phonePreferenceColor(value.subtitle_text_color), fontSize = (18 * value.subtitle_font_scale).sp,
            fontWeight = FontWeight.SemiBold)
    }
}