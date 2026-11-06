@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package org.viptv.app.features.settings

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import org.viptv.app.components.FocusLazyRow as LazyRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.LocalTvColumnFocus
import org.viptv.app.components.TvAction
import org.viptv.app.components.requestFocusWhenPlaced
import org.viptv.app.components.tvHold
import org.viptv.app.components.tvRingReach
import org.viptv.app.components.tvRingRoom
import org.viptv.app.features.search.TvSearchKeyboard
import org.viptv.app.components.LocalPendingMutation
import org.viptv.app.components.MutationSpinner
import org.viptv.app.state.MutationKind
import org.viptv.app.state.MutationTarget
import org.viptv.app.components.tileGlyphSize
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.IntegrationConnection

@Composable
internal fun TvAddonSettings(addons: List<IntegrationConnection>, enabled: Set<String>, local: Boolean, busy: Boolean,
    scale: Float, initial: FocusRequester, sidebar: FocusRequester, info: Map<String, TvAddonInfo>, toggle: (String, Boolean) -> Unit,
    install: () -> Unit, placeholderQr: Boolean = false, manage: Boolean = true, logos: Map<String, String> = emptyMap(),
    initialAddonId: String? = null, configurableAddons: Set<String> = emptySet(), configure: (String) -> Unit = {},
    addonConfigure: org.viptv.app.state.AddonConfigureRequest? = null, dismissConfigure: () -> Unit = {}) {
    var query by remember { mutableStateOf("") }
    var provides by remember { mutableStateOf<AddonProvides?>(null) }
    var searching by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf<IntegrationConnection?>(null) }
    var wasSearching by remember { mutableStateOf(false) }
    val counts = remember(addons) { addonProvidesCounts(addons) }
    val shown = AddonFilter(query, provides).filtered(addons)
    val listState = rememberLazyListState()
    val targetRow = remember(shown, initialAddonId) { shown.indexOfFirst { it.id == initialAddonId } }
    val targetFocus = remember(initialAddonId) { FocusRequester() }
    LaunchedEffect(targetRow) { if (targetRow >= 0) listState.scrollToItem(targetRow) }
    val searchChip = remember { FocusRequester() }
    val clear = { query = ""; provides = null }
    // Leaving the keyboard returns the D-pad to the chip that opened it.
    LaunchedEffect(searching) {
        if (searching) wasSearching = true
        else if (wasSearching) { withFrameNanos { }; runCatching { searchChip.requestFocus() } }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
        TvSettingsHeading("Add-ons", scale)
        if (addons.isNotEmpty()) TvAddonFilterChips(Modifier.width(760.dp * scale), addons.size, query, provides, counts, searchChip, sidebar,
            searching, scale, startSearch = { searching = true }, select = { provides = it }, clear = clear)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(60.dp * scale)) {
            // Inside the list its own focus search moves through every row: the page's TvFocusColumn clamp only knows composed rows and stops Down at the last visible one.
            CompositionLocalProvider(LocalTvColumnFocus provides null) {
                LazyColumn(Modifier.width(760.dp * scale).fillMaxHeight().tvRingRoom(tvRingReach(scale), horizontal = false),
                    state = listState,
                    contentPadding = PaddingValues(top = tvRingReach(scale), bottom = TvAddonListBottom * scale),
                    verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                    itemsIndexed(shown, key = { _, addon -> addon.id }) { index, addon ->
                        val pending = LocalPendingMutation.current.isPending(MutationTarget(MutationKind.Addon, addon.id))
                        val target = addon.id == initialAddonId
                        val configurable = addon.id in configurableAddons
                        TvAction(Modifier.fillMaxWidth().heightIn(min = 100.dp * scale)
                            .tvHold(enabled = manage && !busy) { managing = addon }
                            .focusProperties { left = sidebar }
                            .then(if (target) targetFocus.requestFocusWhenPlaced().targetRing(scale)
                                else if (index == 0) Modifier.focusRequester(initial) else Modifier)
                            .semantics { if (manage) { role = Role.Switch; toggleableState = if (addon.id in enabled) ToggleableState.On else ToggleableState.Off } }, scale, ring = false, enabled = !busy && !pending, keepFocus = true,
                            background = Color(0xFF191919), arrangement = Arrangement.Start, contentPadding = PaddingValues(horizontal = 24.dp * scale),
                            activate = { if (manage) toggle(addon.id, addon.id !in enabled) }) { tint ->
                            AddonLogoTile(logos[addon.id], Modifier.size(60.dp * scale).clip(RoundedCornerShape(14.dp * scale))
                                .background(Color.White.copy(alpha = 0.08f)), Modifier.padding(6.dp * scale)) {
                                Box(Modifier.fillMaxSize().background(Color(info[addon.id]?.color ?: 0xFF3D6C88)), contentAlignment = Alignment.Center) {
                                    Text(info[addon.id]?.letter ?: addon.name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "A", color = Palette.text,
                                        fontSize = tileGlyphSize(28f / 60f, 60.dp * scale), fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.width(20.dp * scale))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp * scale)) {
                                Text(addon.name, color = tint, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold)
                                Text(info[addon.id]?.subtitle ?: if (!addon.enabled) "Disabled for this account" else addon.providesSummary(),
                                    color = tint.copy(alpha = 0.75f), fontSize = (20 * scale).sp, lineHeight = (26 * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (manage) {
                                if (pending) MutationSpinner(28.dp, scale = scale, strokeWidth = 3.dp, color = tint)
                                else TvSettingsSwitch(addon.id in enabled, scale)
                            }
                        }
                    }
                    if (addons.isNotEmpty() && shown.isEmpty()) item(key = "no-matches") {
                        Text("No add-ons match.", Modifier.padding(horizontal = 24.dp * scale), fontSize = (22 * scale).sp, color = Palette.secondary)
                    }
                    item(key = "add") { if (manage) TvAction(Modifier.fillMaxWidth().heightIn(min = 100.dp * scale)
                        .focusProperties { left = sidebar; down = FocusRequester.Cancel; if (addons.isEmpty()) up = FocusRequester.Cancel }
                        .then(if (shown.isEmpty()) Modifier.focusRequester(initial) else Modifier), scale, ring = false,
                        enabled = !busy, keepFocus = true, activate = { install() }, arrangement = Arrangement.Start,
                        contentPadding = PaddingValues(horizontal = 24.dp * scale)) {
                        Box(Modifier.size(60.dp * scale).background(it.copy(alpha = 0.1f), RoundedCornerShape(14.dp * scale)), contentAlignment = Alignment.Center) {
                            Icon(Lucide.Plus, "Add an add-on", Modifier.size(30.dp * scale), it)
                        }
                        Spacer(Modifier.width(20.dp * scale)); Text("Add an add-on", color = it, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
                    } }
                }
            }
            if (searching) TvAddonSearchPanel(query, { query = it.take(AddonSearchLimit) }, scale, finish = { searching = false })
            else Column(Modifier.width(364.dp * scale).background(Color(0xFF191919), RoundedCornerShape(18.dp * scale)).padding(horizontal = 28.dp * scale, vertical = 28.dp * scale),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp * scale)) {
                Box(Modifier.size(236.dp * scale).background(Palette.text, RoundedCornerShape(14.dp * scale)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(if (placeholderQr) Lucide.ScanQrCode else Lucide.Smartphone, null, Modifier.size(72.dp * scale), Palette.background)
                        Text(if (placeholderQr) "[QR code]" else "Pairing unavailable", color = Palette.background, fontSize = (24 * scale).sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(if (placeholderQr) "Scan to add one from your phone" else "Add-on pairing is not available yet.",
                    fontSize = (24 * scale).sp, lineHeight = (35 * scale).sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text(if (placeholderQr) "or go to\nhttps://your-server.example/addons" else "Use Add an add-on to enter its address on this TV.",
                    fontSize = (22 * scale).sp, lineHeight = (32 * scale).sp, color = Palette.secondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
    managing?.let { addon ->
        TvAddonManageDialog(addon, addon.id in enabled, addon.id in configurableAddons, busy, scale,
            toggle = { on -> toggle(addon.id, on) },
            reconfigure = { managing = null; configure(addon.id) },
            dismiss = { managing = null })
    }
    addonConfigure?.let { request ->
        TvAddonConfigureDialog(request, scale, dismiss = dismissConfigure, newCode = { configure(request.addon) })
    }
}

// The row's own fill covers a border modifier, so the arrival ring is drawn over the fill instead.
@Composable
private fun Modifier.targetRing(scale: Float): Modifier {
    val accent = Palette.accent
    return drawWithContent {
        drawContent()
        val stroke = 2.dp.toPx() * scale
        drawRoundRect(accent, topLeft = Offset(stroke / 2, stroke / 2),
            size = Size((size.width - stroke).coerceAtLeast(0f), (size.height - stroke).coerceAtLeast(0f)),
            cornerRadius = CornerRadius((18.dp.toPx() - stroke / 2) * scale), style = Stroke(stroke))
    }
}

@Composable
private fun TvAddonFilterChips(modifier: Modifier, total: Int, query: String, provides: AddonProvides?, counts: Map<AddonProvides, Int>,
    searchChip: FocusRequester, sidebar: FocusRequester, searching: Boolean, scale: Float, startSearch: () -> Unit, select: (AddonProvides?) -> Unit, clear: () -> Unit) {
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) {
        item(key = "search") {
            TvAddonFilterChip("Search", Lucide.Search, searching || query.isNotBlank(), scale, searchChip, left = sidebar, activate = startSearch)
        }
        item(key = "all") { TvAddonFilterChip("All ($total)", null, provides == null, scale, null) { select(null) } }
        AddonProvides.entries.forEach { entry ->
            val count = counts[entry] ?: return@forEach
            item(key = entry.name) {
                TvAddonFilterChip("${entry.label} ($count)", null, provides == entry, scale, null) {
                    select(if (provides == entry) null else entry)
                }
            }
        }
        if (query.isNotBlank() || provides != null) item(key = "clear") { TvAddonFilterChip("Clear", Lucide.X, false, scale, null, activate = clear) }
    }
}

@Composable
private fun TvAddonFilterChip(label: String, icon: ImageVector?, selected: Boolean, scale: Float, requester: FocusRequester?, left: FocusRequester? = null, activate: () -> Unit) {
    TvAction(Modifier.heightIn(min = 56.dp * scale), scale, requester = requester, left = left, ring = false, cornerRadius = 28.dp * scale,
        background = if (selected) Palette.text.copy(alpha = 0.22f) else Color(0xFF191919),
        contentPadding = PaddingValues(horizontal = 22.dp * scale), activate = activate) { tint ->
        if (icon != null) { Icon(icon, null, Modifier.size(22.dp * scale), tint); Spacer(Modifier.width(10.dp * scale)) }
        Text(label, color = tint, fontSize = (22 * scale).sp, lineHeight = (28 * scale).sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TvAddonSearchPanel(query: String, queryChange: (String) -> Unit, scale: Float, finish: () -> Unit) {
    Column(Modifier.width(520.dp * scale).background(Color(0xFF191919), RoundedCornerShape(18.dp * scale))
        .padding(horizontal = 24.dp * scale, vertical = 24.dp * scale), verticalArrangement = Arrangement.spacedBy(14.dp * scale)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Search add-ons", Modifier.weight(1f), fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
            TvAction(Modifier.heightIn(min = 56.dp * scale), scale, contentPadding = PaddingValues(horizontal = 22.dp * scale), activate = finish) { tint ->
                Text("Done", color = tint, fontSize = (22 * scale).sp, lineHeight = (28 * scale).sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(query.ifBlank { "Type part of a name" }, fontSize = (22 * scale).sp, lineHeight = (28 * scale).sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, color = if (query.isBlank()) Palette.secondary else Palette.text)
        TvSearchKeyboard({ transform -> queryChange(transform(query)) }, scale)
    }
}

private val TvAddonListBottom = 72.dp
