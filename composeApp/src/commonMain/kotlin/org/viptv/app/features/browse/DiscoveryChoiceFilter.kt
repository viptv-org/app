package org.viptv.app.features.browse

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.TvAction
import org.viptv.engine.wire.QueryControl

@Composable
internal fun DiscoveryChoiceFilter(control: QueryControl, value: String?, modifier: Modifier,
    television: Boolean, scale: Float, changed: (String) -> Unit) {
    var expanded by remember(control) { mutableStateOf(false) }
    Box {
        val defaultChoice = control.choices.firstOrNull { it.value.isEmpty() || (control.id == "genre" && it.value == "all") }
        val label = control.choices.find { it.value == value }?.label ?: if (value.isNullOrEmpty()) defaultChoice?.label ?: control.label else control.label
        if (television) TvAction(modifier.height(56.dp * scale), scale, activate = { expanded = true },
            cornerRadius = 28.dp * scale, contentPadding = PaddingValues(horizontal = 24.dp * scale)) {
            Text(label, color = it, fontSize = (24 * scale).sp)
        } else OutlinedButton(onClick = { expanded = true }, modifier = modifier,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = org.viptv.app.theme.Palette.text)) { Text(label) }
        DropdownMenu(expanded, { expanded = false }) {
            if (!control.required && defaultChoice == null) DropdownMenuItem(text = { Text("Default ${control.label.lowercase()}") },
                onClick = { changed(""); expanded = false })
            control.choices.forEach { choice -> DropdownMenuItem(
                text = { Text(choice.label, fontSize = (if (television) 24 * scale else 14f).sp) },
                modifier = Modifier.heightIn(min = if (television) 64.dp * scale else 44.dp),
                onClick = { changed(choice.value); expanded = false }) }
        }
    }
}
