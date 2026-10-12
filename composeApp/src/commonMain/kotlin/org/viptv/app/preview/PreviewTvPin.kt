package org.viptv.app.preview

import androidx.compose.runtime.*
import org.viptv.app.features.entry.TvPinScreen
import org.viptv.app.state.Screen

@Composable
internal fun PreviewTvPin(variant: String, navigate: (Screen) -> Unit, backChanged: ((() -> Unit)?) -> Unit) {
    val currentNavigate by rememberUpdatedState(navigate)
    DisposableEffect(Unit) { backChanged { currentNavigate(Screen.Profiles) }; onDispose { backChanged(null) } }
    var pin by remember(variant) { mutableStateOf(if (variant == "Empty") "" else "12") }
    TvPinScreen(PreviewData.profiles[2], pin, false, null, { if (pin.length < 8) pin += it },
        { pin = pin.dropLast(1) }, { if (pin.length in 4..8) navigate(Screen.Home) }, { navigate(Screen.Profiles) })
}
