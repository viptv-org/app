package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.X
import org.viptv.app.components.FilterChip
import org.viptv.app.components.IconButton
import org.viptv.app.theme.Palette

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AddonFilterControls(query: String, queryChange: (String) -> Unit, provides: AddonProvides?, select: (AddonProvides?) -> Unit,
    counts: Map<AddonProvides, Int>, shown: Int, total: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(query, { queryChange(it.take(AddonSearchLimit)) }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Search add-ons") }, leadingIcon = { Icon(Lucide.Search, null, Modifier.size(18.dp)) },
            trailingIcon = if (query.isNotEmpty()) ({ IconButton({ queryChange("") }) { Icon(Lucide.X, "Clear search", Modifier.size(18.dp)) } }) else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AddonProvides.entries.forEach { entry ->
                val count = counts[entry] ?: return@forEach
                FilterChip(provides == entry, { select(if (provides == entry) null else entry) }, {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Text(entry.label); Text("$count", color = Palette.secondary) }
                })
            }
        }
        Text(if (query.isNotBlank() || provides != null) "Showing $shown of $total add-ons" else "$total installed",
            Modifier.semantics { liveRegion = LiveRegionMode.Polite }, fontSize = 13.sp, color = Palette.secondary)
    }
}

internal const val AddonSearchLimit = 64
