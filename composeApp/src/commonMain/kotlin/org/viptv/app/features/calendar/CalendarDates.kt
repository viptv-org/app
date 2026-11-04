package org.viptv.app.features.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import org.viptv.app.components.edgeToEdgeScroll
import org.viptv.app.theme.Palette
import org.viptv.app.components.pointerSelectable

@Composable
internal fun CalendarDates(days: List<CalendarDay>, entries: List<CalendarEntry>, selected: String?, phone: Boolean,
    scale: Float, gutter: Dp = 0.dp, choose: (String) -> Unit) {
    if (phone) {
        org.viptv.app.components.TvFocusScroll(horizontal = true) { Row(Modifier.fillMaxWidth().edgeToEdgeScroll(rememberScrollState(), gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            days.forEach { day ->
                val active = selected == day.id
                Column(Modifier.width(44.dp).background(if (active) Palette.text else Color.Transparent,
                    RoundedCornerShape(16.dp)).pointerSelectable(active, role = Role.Tab, shape = RoundedCornerShape(16.dp)) { choose(day.id) }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(day.weekday, fontSize = 10.sp, lineHeight = 12.sp, color = if (active) Palette.background else Palette.secondary)
                    Text(day.number, fontSize = 15.sp, lineHeight = 18.sp, color = if (active) Palette.background else Palette.text)
                    Text(if (entries.any { it.day == day.id }) "•" else " ", fontSize = 12.sp, lineHeight = 10.sp,
                        color = if (active) Palette.background else Palette.secondary)
                }
            }
        } }
        return
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val style = LocalTextStyle.current.merge(TextStyle(fontSize = (24 * scale).sp, lineHeight = (32 * scale).sp))
        val column = maxWidth / days.size
        val columnPx = with(density) { column.roundToPx() }
        val padPx = with(density) { (24.dp * scale).roundToPx() * 2 }
        Row(Modifier.fillMaxWidth()) {
            days.forEach { day ->
                val active = selected == day.id
                val label = if (measurer.measure(day.label, style, maxLines = 1, softWrap = false).size.width + padPx <= columnPx)
                    day.label else day.weekday
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(label, Modifier.background(if (active) Palette.text else Color.Transparent, RoundedCornerShape(30.dp * scale))
                        .pointerSelectable(active, role = Role.Tab, shape = RoundedCornerShape(30.dp * scale)) { choose(day.id) }.padding(horizontal = 24.dp * scale, vertical = 16.dp * scale),
                        fontSize = (24 * scale).sp, lineHeight = (32 * scale).sp,
                        color = if (active) Palette.background else Palette.secondary)
                }
            }
        }
    }
}
