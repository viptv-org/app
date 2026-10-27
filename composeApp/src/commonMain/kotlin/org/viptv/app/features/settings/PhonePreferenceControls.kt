package org.viptv.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.theme.Palette
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.pointerSelectable

@Composable
internal fun PhonePreferenceGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF191919)) { Column(content = content) }
}

@Composable
internal fun PhonePreferenceRow(label: String, value: String, enabled: Boolean, choose: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).pointerClickable(enabled = enabled, onClick = choose).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        Text(label, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(value, Modifier.widthIn(max = 190.dp), color = Palette.secondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Lucide.ChevronRight, null, Modifier.size(17.dp), tint = Palette.tertiary)
    }
}

@Composable
internal fun PhonePreferenceDivider() = HorizontalDivider(color = Color.White.copy(alpha = .08f))

/** Board switch: 45x26 track, 20 dp thumb, accent track when on (M3Set* boards). */
@Composable
internal fun PhoneSwitch(checked: Boolean, enabled: Boolean, change: ((Boolean) -> Unit)?) {
    val toggle = change?.let { report -> Modifier.pointerClickable(enabled = enabled, role = Role.Switch) { report(!checked) } } ?: Modifier
    Box(Modifier.size(45.dp, 26.dp).then(toggle)
        .background(if (checked) Palette.accent else Color.White.copy(alpha = .22f), RoundedCornerShape(13.dp)).padding(horizontal = 3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(Modifier.size(20.dp).background(if (checked) Color(0xFF15130F) else Palette.text, RoundedCornerShape(10.dp)))
    }
}

@Composable
internal fun PhonePreferenceSegments(values: List<Pair<String, String>>, selected: String, enabled: Boolean, choose: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().height(40.dp).background(Color(0xFF272727), RoundedCornerShape(20.dp)).selectableGroup().padding(4.dp)) {
        values.forEach { (value, label) ->
            Box(Modifier.weight(1f).fillMaxHeight().background(if (selected == value) Palette.background else Color.Transparent, RoundedCornerShape(16.dp))
                .pointerSelectable(selected == value, enabled = enabled, role = Role.RadioButton, onClick = { choose(value) }, shape = RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (selected == value) Palette.text else Palette.secondary)
            }
        }
    }
}

internal fun phonePreferenceColor(value: String, fallback: Color = Color.White): Color = value.removePrefix("#").toLongOrNull(16)
    ?.let { Color(if (value.length == 7) it or 0xFF000000 else it) } ?: fallback

internal fun phonePreferenceLanguage(value: String?, audio: Boolean): String = when (value?.lowercase()) {
    null, "" -> if (audio) "Original language" else "Default"
    "en" -> "English"; "es" -> "Spanish"; "pt" -> "Portuguese"; "fr" -> "French"; "de" -> "German"
    else -> value.orEmpty()
}
