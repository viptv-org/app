package org.viptv.app.features.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.edgeToEdgeScroll
import org.viptv.app.theme.Palette
import org.viptv.app.components.pointerClickable

@Composable
internal fun CalendarDates(days: List<CalendarDay>, entries: List<CalendarEntry>, selected: String?, phone: Boolean,
    scale: Float, gutter: Dp = 0.dp, choose: (String) -> Unit) {
    // The strip runs under the page gutter to the screen edges (app#63).
    org.viptv.app.components.TvFocusScroll(horizontal = true) { Row(Modifier.fillMaxWidth().edgeToEdgeScroll(rememberScrollState(), gutter), horizontalArrangement = Arrangement.spacedBy(if (phone) 8.dp else 20.dp * scale)) {
        days.forEach { day ->
            val active = selected == day.id
            if (phone) Column(Modifier.width(44.dp).background(if (active) Palette.text else androidx.compose.ui.graphics.Color.Transparent,
                RoundedCornerShape(16.dp)).pointerClickable(shape = RoundedCornerShape(16.dp)) { choose(day.id) }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(day.weekday, fontSize = 10.sp, lineHeight = 12.sp, color = if (active) Palette.background else Palette.secondary)
                Text(day.number, fontSize = 15.sp, lineHeight = 18.sp, color = if (active) Palette.background else Palette.text)
                Text(if (entries.any { it.day == day.id }) "•" else " ", fontSize = 12.sp, lineHeight = 10.sp,
                    color = if (active) Palette.background else Palette.secondary)
            } else Text(day.label, Modifier.background(if (active) Palette.text else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(30.dp * scale))
                .pointerClickable(shape = RoundedCornerShape(30.dp * scale)) { choose(day.id) }.padding(horizontal = 24.dp * scale, vertical = 16.dp * scale), fontSize = (24 * scale).sp, lineHeight = (32 * scale).sp,
                color = if (active) Palette.background else Palette.secondary)
        }
    } }
}
