package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.pageBottomClearance
import org.viptv.app.theme.Palette

@Composable
internal fun WatchOnTvSettings(state: WatchOnTvState, compact: Boolean, back: () -> Unit) {
    val engine = rememberVizioSetupEngine()
    val repair = state.repairTv
    val startingPaired = remember { state.value.optedIn && repair == null }
    if (startingPaired || engine == null) {
        WatchOnTvStatus(state.value)
        return
    }
    WatchOnTvSetupFlow(
        engine, compact, back,
        onPaired = { tv ->
            state.clearRepair()
            state.optIn(tv.name, tv.origin)
        },
        pairAgain = repair,
        onCancel = {
            state.clearRepair()
            back()
        },
    )
}

@Composable
private fun WatchOnTvStatus(state: WatchOnTvPreferences) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).pageBottomClearance(),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(watchOnTvValue(state), color = Palette.secondary, fontSize = 15.sp)
        Text(watchOnTvSubtitle, color = Palette.tertiary, fontSize = 13.sp)
    }
}