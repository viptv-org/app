package org.viptv.app.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.AddonFailure

internal fun addonFailureMessage(failures: List<AddonFailure>): String {
    val names = failures.map { it.addon_name }.filter { it.isNotBlank() }.distinct()
    return when {
        names.isEmpty() -> "An add-on didn’t respond"
        names.size == 1 -> "${names[0]} didn’t respond"
        names.size == 2 -> "${names[0]} and ${names[1]} didn’t respond"
        else -> "${names[0]} and ${names.size - 1} more didn’t respond"
    }
}

@Composable
fun AddonFailureNotice(
    failures: List<AddonFailure>,
    manageAddons: (() -> Unit)?,
    modifier: Modifier = Modifier,
    television: Boolean = false,
    scale: Float = 1f,
    focus: FocusRequester? = null,
) {
    if (failures.isEmpty()) return
    val message = addonFailureMessage(failures)
    if (television) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp * scale),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Info, null, Modifier.size(30.dp * scale), tint = Palette.secondary)
            Text(message, color = Palette.secondary, fontSize = (26 * scale).sp, lineHeight = (34 * scale).sp,
                modifier = Modifier.weight(1f))
            manageAddons?.let {
                TvAction(Modifier.width(300.dp * scale).heightIn(min = 64.dp * scale), scale, requester = focus, activate = it,
                    contentPadding = PaddingValues(horizontal = 24.dp * scale)) { tint ->
                    Text("Manage add-ons", color = tint, fontSize = (24 * scale).sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(message, color = Palette.secondary, fontSize = 13.sp, lineHeight = 18.sp)
        manageAddons?.let {
            TextButton(onClick = it, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)) {
                Text("Manage add-ons", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}