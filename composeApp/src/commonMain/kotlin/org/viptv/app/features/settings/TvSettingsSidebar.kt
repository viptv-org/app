package org.viptv.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette
import org.viptv.app.components.TvFocusScroll
import org.viptv.app.components.pointerClickable

@Composable
internal fun TvSettingsSidebar(selected: TvSettingsSection, scale: Float, requester: FocusRequester,
    content: FocusRequester, modal: Boolean = false, select: (TvSettingsSection) -> Unit) {
    Column(Modifier.width(560.dp * scale).fillMaxHeight().background(Color(0xFF161618))) {
        Box(Modifier.fillMaxWidth().height(149.dp * scale)) {
            Text("Settings", Modifier.offset(96.dp * scale, 93.dp * scale), fontSize = (44 * scale).sp,
                lineHeight = (53 * scale).sp, letterSpacing = (-0.88f * scale).sp, fontWeight = FontWeight.Bold)
        }
        TvFocusScroll(horizontal = false) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                tvSettingsGroups().forEach { (group, sections) ->
                    Spacer(Modifier.height(24.dp * scale))
                    Text(group.label.uppercase(), Modifier.padding(start = 96.dp * scale).semantics(mergeDescendants = true) { heading() },
                        color = Palette.tertiary, fontSize = (18 * scale).sp, lineHeight = (25 * scale).sp,
                        letterSpacing = (1.44f * scale).sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp * scale))
                    sections.forEach { section ->
                        var focused by remember { mutableStateOf(false) }
                        val highlighted = focused && !modal
                        Row(Modifier.padding(start = 72.dp * scale, end = 48.dp * scale, bottom = 8.dp * scale).fillMaxWidth().height(72.dp * scale)
                            .then(if (section == selected) Modifier.focusRequester(requester) else Modifier)
                            .focusProperties { right = content }.onFocusChanged { focused = it.isFocused }
                            .background(if (highlighted) Palette.text else if (section == selected) Color(0xFF333333) else Color.Transparent, RoundedCornerShape(12.dp * scale))
                            .pointerClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, shape = RoundedCornerShape(12.dp * scale)) { select(section) }
                            .padding(horizontal = 24.dp * scale), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(18.dp * scale)) {
                            val tint = if (highlighted) Palette.background else if (section == selected) Palette.text else Palette.secondary
                            Icon(section.icon, null, Modifier.size(28.dp * scale), tint)
                            Text(section.label, color = tint, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp,
                                fontWeight = if (section == selected) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp * scale))
            }
        }
    }
}
