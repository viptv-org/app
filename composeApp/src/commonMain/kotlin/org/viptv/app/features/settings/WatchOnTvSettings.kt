package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.pageBottomClearance
import org.viptv.app.theme.Palette

@Composable
internal fun WatchOnTvSettings(state: WatchOnTvPreferences) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).pageBottomClearance(),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Watch on TV", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(watchOnTvValue(state), color = Palette.secondary, fontSize = 15.sp)
        Text(watchOnTvSubtitle, color = Palette.tertiary, fontSize = 13.sp)
    }
}