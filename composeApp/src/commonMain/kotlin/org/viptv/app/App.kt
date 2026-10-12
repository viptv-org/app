package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.viptv.app.theme.*
import org.viptv.app.state.*
import org.viptv.app.connection.browserOrigin
import org.viptv.app.features.entry.*
import org.viptv.app.features.home.HomeScreen
import org.viptv.app.features.search.SearchScreen
import org.viptv.app.features.details.DetailsScreen
import org.viptv.app.components.*
import org.viptv.engine.wire.Card

@Composable
fun App(television: Boolean = false, browser: Boolean = false, localDatabase: String? = null) {
    val scope = rememberCoroutineScope()
    val controller = remember { ApplicationController(scope) }
    val state by controller.state.collectAsState()
    DisposableEffect(controller) { onDispose { controller.disconnect() } }
    ViptvTheme {
        BoxWithConstraints(Modifier.fillMaxSize().background(Palette.background)) {
            val compact = maxWidth < 640.dp && !television
            when (state.screen) {
                Screen.Connect -> ConnectScreen(browser, browserOrigin() ?: "", state.busy, state.error, localDatabase?.let { { controller.useLocal(it) } }, controller::connect)
                Screen.Profiles -> ProfilesScreen(state.profiles, state.busy, controller::selectProfile)
                else -> Column {
                    if (browser || television) TopNavigation(state.screen, television, controller::navigate)
                    else Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 20.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Brand(); Spacer(Modifier.weight(1f)); TextButton(onClick = { controller.navigate(Screen.Profiles) }) { Text("Profile") }
                    }
                    Row(Modifier.weight(1f)) {
                        if (!browser && !television && !compact) SideNavigation(state.screen, controller::navigate)
                        when (state.screen) {
                            Screen.Home -> HomeScreen(state.home, television, controller::open, controller::refresh)
                            Screen.Search -> SearchScreen(state.results, state.busy, state.error, controller::search, controller::open)
                            Screen.Details -> DetailsScreen(state.detail, state.busy, state.error) { controller.navigate(Screen.Home) }
                            Screen.Settings -> Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                Text("Settings", style = MaterialTheme.typography.headlineLarge)
                                Button(onClick = controller::disconnect) { Text("Disconnect from server") }
                            }
                            else -> Column(Modifier.padding(32.dp)) { Text(state.screen.name, style = MaterialTheme.typography.headlineLarge) }
                        }
                    }
                    if (compact && !browser) BottomNavigation(state.screen, controller::navigate)
                }
            }
        }
    }
}
