package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.TvAction
import org.viptv.app.theme.Palette

@Composable
internal fun TvPreferencesFailure(section: String, error: String, busy: Boolean, scale: Float,
    initial: FocusRequester, sidebar: FocusRequester, retry: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp * scale)) {
        TvSettingsHeading(section, scale)
        Text(error, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            fontSize = (24 * scale).sp, lineHeight = (34 * scale).sp, color = Palette.danger)
        TvPreferencesRetry(busy, scale, initial, sidebar, retry)
    }
}

@Composable
internal fun TvPreferencesMissing(section: TvSettingsSection, busy: Boolean, scale: Float,
    initial: FocusRequester, sidebar: FocusRequester, retry: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp * scale)) {
        TvSettingsHeading(section.label, scale)
        Text(if (busy) "Loading settings…" else "These settings are unavailable.",
            fontSize = (24 * scale).sp, lineHeight = (34 * scale).sp, color = Palette.secondary)
        TvPreferencesRetry(busy, scale, initial, sidebar, retry)
    }
}

@Composable
internal fun TvPreferencesRetry(busy: Boolean, scale: Float, initial: FocusRequester?, sidebar: FocusRequester, retry: (() -> Unit)?) {
    if (retry != null) TvAction(Modifier.fillMaxWidth().height(96.dp * scale).focusProperties { left = sidebar }, scale, ring = false,
        requester = initial, enabled = !busy, activate = retry) {
        Text(if (busy) "Loading settings…" else "Retry settings", color = it,
            fontSize = (26 * scale).sp, fontWeight = FontWeight.SemiBold)
    }
}
