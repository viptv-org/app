package org.viptv.app.features.calendar

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Bookmark
import com.composables.icons.lucide.Lucide

/** Marks a row from the profile's own series (My List's bookmark), beside the public schedule's rows. */
@Composable
internal fun CalendarYourShow(size: Float, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((size * 0.3f).dp)) {
        Icon(Lucide.Bookmark, null, Modifier.size((size * 0.95f).dp), tint = color)
        Text("Your show", color = color, fontSize = size.sp, lineHeight = (size * 1.25f).sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
