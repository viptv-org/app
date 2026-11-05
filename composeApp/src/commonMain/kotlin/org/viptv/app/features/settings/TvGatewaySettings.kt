package org.viptv.app.features.settings

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.TvAction
import org.viptv.app.connection.*
import org.viptv.app.components.LocalPendingMutation
import org.viptv.app.components.MutationSpinner
import org.viptv.app.state.GATEWAY_CHECK_ACTION
import org.viptv.app.state.GATEWAY_ENABLE_ACTION
import org.viptv.app.state.gatewayTarget
import org.viptv.app.theme.Palette

@Composable
internal fun TvGatewaySettings(snapshot: GatewaySnapshot?, checks: Map<String, GatewayCheck>, busy: Boolean,
    error: String?, errorCode: String?, loadError: String?, refresh: () -> Unit, check: (String) -> Unit,
    setEnabled: (String, Boolean) -> Unit, scale: Float,
    first: FocusRequester?, left: FocusRequester?) {
    val items = snapshot?.items.orEmpty()
    val secretStorageConfigured = snapshot?.secret_storage_configured != false
    Column(Modifier.fillMaxSize()) {
        TvSettingsHeading("Playback gateways", scale,
            subtitle = if (!secretStorageConfigured) "Secret storage is unavailable, so no gateway can be registered." else "Gateways transcode and deliver streams this device cannot play directly.",
            reserveSubtitle = true)
        if (loadError != null) {
            Text(loadError, color = Palette.danger, fontSize = (22 * scale).sp)
            Spacer(Modifier.height(12.dp * scale))
            TvAction(Modifier.heightIn(min = 60.dp * scale), scale, requester = first, enabled = !busy, keepFocus = true, activate = refresh) { tint ->
                Text("Retry", color = tint, fontSize = (22 * scale).sp, fontWeight = FontWeight.Bold)
            }
        } else if (error != null) {
            Text(gatewayCodeMessage(errorCode) ?: error, color = Palette.danger, fontSize = (22 * scale).sp)
        } else if (snapshot == null) {
            Text("Loading gateways…", color = Palette.secondary, fontSize = (22 * scale).sp)
        } else if (items.isEmpty()) {
            Text("No gateways are configured for your account.", color = Palette.secondary, fontSize = (22 * scale).sp)
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp * scale),
            contentPadding = PaddingValues(top = 12.dp * scale, bottom = 80.dp * scale)) {
            itemsIndexed(items, key = { _, gateway -> gateway.id }) { index, gateway ->
                val checked = checks[gateway.id]
                val mutations = LocalPendingMutation.current
                val enabling = mutations.isPending(gatewayTarget(gateway.id, GATEWAY_ENABLE_ACTION))
                val checking = mutations.isPending(gatewayTarget(gateway.id, GATEWAY_CHECK_ACTION))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp * scale)) {
                    TvSettingsRow(gateway.name,
                        subtitle = listOfNotNull(
                            if (gateway.can_manage) "Yours" else "Shared with your account",
                            gateway.namespace,
                            if (gateway.enabled) "Enabled" else "Disabled").joinToString(" · "),
                        scale = scale, checked = if (gateway.can_manage && !enabling) gateway.enabled else null,
                        value = if (enabling) "Saving…" else null,
                        requester = if (index == 0) first else null, left = left,
                        leading = Lucide.Network,
                        activate = { if (gateway.can_manage && !enabling) setEnabled(gateway.id, !gateway.enabled) })
                    TvAction(Modifier.fillMaxWidth().heightIn(min = 72.dp * scale), scale, ring = false,
                        enabled = !busy && !checking, keepFocus = true, activate = { check(gateway.id) },
                        background = androidx.compose.ui.graphics.Color(0xFF191919),
                        arrangement = Arrangement.Start, contentPadding = PaddingValues(horizontal = 24.dp * scale)) { tint ->
                        if (checking) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                                MutationSpinner(28.dp, scale = scale, strokeWidth = 3.dp, color = tint)
                                Text("Checking…", color = tint, fontSize = (22 * scale).sp, lineHeight = (28 * scale).sp,
                                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        } else Text(checked?.let(::capacityLabel) ?: "Check capacity", color = tint,
                            fontSize = (22 * scale).sp, lineHeight = (28 * scale).sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}