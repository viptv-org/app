package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette
import org.viptv.app.components.Button

@Composable
internal fun ParentControlsNative(profiles: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Parental controls", style = MaterialTheme.typography.headlineMedium, fontSize = 28.sp)
        Text("Manage each local profile’s PIN in Profiles.", color = Palette.secondary, fontSize = 16.sp)
        Button(profiles) { Text("Manage profiles") }
    }
}
