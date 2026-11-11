package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Users
import org.viptv.app.components.TvFocusColumn

@Composable
internal fun TvParentalLocalSettings(scale: Float, first: FocusRequester, sidebar: FocusRequester, profiles: () -> Unit) {
    TvFocusColumn(Modifier.fillMaxSize()) {
        TvSettingsHeading("Parental controls", scale, "Manage each local profile’s PIN in Profiles.")
        Spacer(Modifier.height(24.dp * scale))
        TvSettingsRow("Manage profiles", scale = scale, requester = first, left = sidebar, leading = Lucide.Users) { profiles() }
    }
}
