package org.viptv.app.features.sources

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.X
import org.viptv.app.components.PopoverMenu
import org.viptv.app.components.PopoverMenuDivider
import org.viptv.app.components.PopoverMenuItem
import org.viptv.app.connection.SourceProducerOutcome
import org.viptv.engine.wire.SourceCandidate

internal const val SOURCE_MENU_PANE = "Source actions"
internal val SOURCE_MENU_WIDTH = 300.dp
internal const val SOURCE_MENU_TARGET_LABEL = "More actions"
internal const val SOURCE_PLAY_LABEL = "Play this source"
internal const val SOURCE_CONFIGURE_PREFIX = "Configure "
internal const val SOURCE_HIDE_LABEL = "Hide this add-on's sources"
internal const val SOURCE_CANCEL_LABEL = "Cancel"

internal const val ADDONS_SETTINGS_SECTION = "Add-ons"
internal const val SOURCE_INTEGRATIONS_SETTINGS_SECTION = "Source integrations"
internal const val XTREAM_SETTINGS_SECTION = "Xtream connections"

@Immutable
internal class SourceRowMenuActions(val addon: String, val configure: (() -> Unit)?, val hide: () -> Unit)

internal fun SourceCandidate.addonLabel(producers: List<SourceProducerOutcome> = emptyList()): String =
    producers.firstOrNull { it.providerId == provider_id }?.displayLabel()?.takeIf { it.isNotBlank() }
        ?: providerLabel().takeUnless { it == provider_id }
        ?: sourceProducerFallbackLabel(provider_id)

internal fun sourceConfigureLabel(source: SourceCandidate, producers: List<SourceProducerOutcome> = emptyList()): String =
    SOURCE_CONFIGURE_PREFIX + source.addonLabel(producers)

internal fun sourceSettingsSection(providerId: String): String? = when {
    providerId.startsWith("addon:") -> ADDONS_SETTINGS_SECTION
    providerId.startsWith("iptv:") -> XTREAM_SETTINGS_SECTION
    providerId.startsWith("vsources:") || providerId.startsWith("m3u:") -> SOURCE_INTEGRATIONS_SETTINGS_SECTION
    else -> null
}

internal fun SourceCandidate.settingsSection(): String? = sourceSettingsSection(provider_id)

internal fun SourceCandidate.rowMenu(producers: List<SourceProducerOutcome>, configure: (String) -> Unit, hide: () -> Unit): SourceRowMenuActions =
    SourceRowMenuActions(addonLabel(producers),
        if (settingsSection() == null) null else ({ configure(provider_id) }),
        hide)

@Composable
internal fun SourceRowMenu(menu: SourceRowMenuActions, expanded: Boolean, scale: Float = 1f, onDismiss: () -> Unit,
    onPlay: () -> Unit, onDetails: (() -> Unit)?) {
    val first = remember { FocusRequester() }
    PopoverMenu(expanded, onDismiss, paneTitle = SOURCE_MENU_PANE, scale = scale, width = SOURCE_MENU_WIDTH) {
        PopoverMenuItem(SOURCE_PLAY_LABEL, Lucide.Play, scale = scale, requester = first, onClick = { onDismiss(); onPlay() })
        PopoverMenuDivider(scale)
        onDetails?.let { details ->
            PopoverMenuItem(SOURCE_DETAILS_LABEL, Lucide.Info, scale = scale, onClick = { onDismiss(); details() })
        }
        menu.configure?.let { configure ->
            PopoverMenuItem(SOURCE_CONFIGURE_PREFIX + menu.addon, Lucide.Settings, scale = scale, onClick = { onDismiss(); configure() })
        }
        PopoverMenuItem(SOURCE_HIDE_LABEL, Lucide.EyeOff, scale = scale, onClick = { onDismiss(); menu.hide() })
        PopoverMenuDivider(scale)
        PopoverMenuItem(SOURCE_CANCEL_LABEL, Lucide.X, scale = scale, onClick = onDismiss)
    }
}