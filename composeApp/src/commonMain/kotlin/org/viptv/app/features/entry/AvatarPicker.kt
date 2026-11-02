package org.viptv.app.features.entry

import org.viptv.app.components.AppTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import org.viptv.app.components.TvAction
import org.viptv.app.components.tvCardFocusOutline
import org.viptv.app.connection.Profile
import org.viptv.app.theme.Palette
import org.viptv.app.components.pointerSelectable
import org.viptv.app.components.TextButton

@Composable
internal fun AvatarPicker(profile: Profile?, busy: Boolean, layout: AvatarPickerLayout, dismiss: () -> Unit,
    choose: (AvatarPick, String) -> Unit, pinAvailable: Boolean = true) {
    Dialog(dismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .75f)), contentAlignment = Alignment.Center) {
            val metrics = AvatarPickerMetrics.of(layout, maxWidth)
            Column(Modifier.then(if (layout == AvatarPickerLayout.Phone) Modifier.fillMaxSize() else Modifier.width(metrics.panel))
                .clip(RoundedCornerShape(if (layout == AvatarPickerLayout.Phone) 0.dp else metrics.radius))
                .background(Palette.surface).verticalScroll(rememberScrollState()).padding(metrics.padding)) {
                val state = rememberAvatarCatalog()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Choose an avatar", Modifier.weight(1f), fontSize = metrics.title, fontWeight = FontWeight.Bold)
                    PickerButton("Cancel", layout, metrics, enabled = true, activate = dismiss)
                }
                when (state) {
                    is AvatarCatalogState.Ready -> AvatarCatalogPage(state.catalog, profile, busy, layout, metrics, choose, pinAvailable)
                    else -> Text(if (state == AvatarCatalogState.Loading) "Loading avatars…" else "Avatars are unavailable on this device.",
                        Modifier.padding(top = metrics.gap), color = Palette.secondary, fontSize = metrics.body)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.AvatarCatalogPage(catalog: AvatarCatalog, profile: Profile?, busy: Boolean, layout: AvatarPickerLayout,
    metrics: AvatarPickerMetrics, choose: (AvatarPick, String) -> Unit, pinAvailable: Boolean) {
    val saved = profile?.avatarPick()?.takeIf { catalog.avatarPath(it.style, it.choice) != null }
    var style by remember { mutableStateOf(saved?.style ?: catalog.worlds.first().style) }
    val world = catalog.world(style) ?: catalog.worlds.first()
    val paging = AvatarPaging(world.count, layout.pageSize)
    var page by remember { mutableStateOf(saved?.let { paging.pageOf(it.choice) } ?: 0) }
    var pin by remember(profile?.id) { mutableStateOf("") }
    val needsPin = profile.requiresLocalPin(pinAvailable)
    val enabled = !busy && (!needsPin || pin.length in 4..8)
    val tiles = remember { List(layout.pageSize) { FocusRequester() } }
    val pinField = remember { FocusRequester() }
    var focusTile by remember { mutableStateOf(if (layout == AvatarPickerLayout.Television && !needsPin) saved?.let { (it.choice - 1) % layout.pageSize } ?: 0 else null) }
    if (layout == AvatarPickerLayout.Television && needsPin) LaunchedEffect(Unit) {
        repeat(60) { if (runCatching { pinField.requestFocus() }.getOrDefault(false)) return@LaunchedEffect; withFrameNanos { } }
    }
    LaunchedEffect(focusTile, page, style) {
        val index = focusTile ?: return@LaunchedEffect
        repeat(60) {
            if (runCatching { tiles[index].requestFocus() }.getOrDefault(false)) { focusTile = null; return@LaunchedEffect }
            withFrameNanos { }
        }
    }
    val turn: (Int) -> Unit = { next -> page = next; if (layout == AvatarPickerLayout.Television) focusTile = 0 }

    Text("${catalog.total} avatars. Pick a world, then pick your character.", Modifier.padding(top = metrics.gap / 2),
        color = Palette.secondary, fontSize = metrics.body)
    if (needsPin) AppTextField(pin, { if (it.length <= 8 && it.all(Char::isDigit)) pin = it },
        Modifier.padding(top = metrics.gap).focusRequester(pinField), label = "Current PIN",
        visualTransformation = PasswordVisualTransformation())
    WorldChips(catalog.worlds, world.style, layout, metrics) { style = it; page = 0 }
    val choices = paging.choices(page).toList()
    Column(Modifier.align(Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(metrics.gap)) {
        choices.chunked(layout.columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(metrics.gap)) {
                row.forEach { choice ->
                    val pick = AvatarPick(world.style, choice)
                    AvatarTile(catalog.avatarUri(world.style, choice), "${world.name} $choice", pick == saved, enabled,
                        metrics, Modifier.focusRequester(tiles[(choice - 1) % layout.pageSize])) { choose(pick, pin) }
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = metrics.gap), verticalAlignment = Alignment.CenterVertically) {
        PickerButton("Previous", layout, metrics, enabled = paging.pages > 1) { turn(paging.previous(page)) }
        Text("${world.name} · ${page + 1} / ${paging.pages}", Modifier.weight(1f), color = Palette.secondary,
            fontSize = metrics.body, textAlign = TextAlign.Center)
        PickerButton("Next", layout, metrics, enabled = paging.pages > 1) { turn(paging.next(page)) }
    }
}

@Composable
private fun WorldChips(worlds: List<AvatarWorld>, selected: String, layout: AvatarPickerLayout, metrics: AvatarPickerMetrics,
    select: (String) -> Unit) {
    val padding = Modifier.padding(vertical = metrics.gap)
    if (layout != AvatarPickerLayout.Desktop) {
        val list = rememberLazyListState()
        val index = worlds.indexOfFirst { it.style == selected }.coerceAtLeast(0)
        LaunchedEffect(index) { list.animateScrollToItem(index) }
        LazyRow(padding.fillMaxWidth(), list, horizontalArrangement = Arrangement.spacedBy(metrics.gap / 2)) {
            itemsIndexed(worlds, key = { _, world -> world.style }) { _, world ->
                WorldChip(world.name, world.style == selected, metrics) { select(world.style) }
            }
        }
    } else FlowRow(padding, horizontalArrangement = Arrangement.spacedBy(metrics.gap / 2),
        verticalArrangement = Arrangement.spacedBy(metrics.gap / 2)) {
        worlds.forEach { world -> WorldChip(world.name, world.style == selected, metrics) { select(world.style) } }
    }
}

@Composable
private fun WorldChip(name: String, selected: Boolean, metrics: AvatarPickerMetrics, select: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Box(Modifier.height(metrics.chip).onFocusChanged { focused = it.isFocused }
        .tvCardFocusOutline(focused, metrics.chip / 2, metrics.scale)
        .clip(shape).background(if (selected) Palette.text else Palette.raised)
        .pointerSelectable(selected, role = Role.Tab, onClick = select, shape = shape).padding(horizontal = metrics.chip / 2.4f),
        contentAlignment = Alignment.Center) {
        Text(name, color = if (selected) Palette.background else Palette.text, fontSize = metrics.body, fontWeight = FontWeight.SemiBold,
            maxLines = 1)
    }
}

@Composable
private fun AvatarTile(uri: String?, label: String, saved: Boolean, enabled: Boolean, metrics: AvatarPickerMetrics,
    modifier: Modifier, pick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(metrics.tileRadius)
    // Clipped before `selectable` so its focus/press layer follows the tile's corners.
    Box(modifier.size(metrics.tile).onFocusChanged { focused = it.isFocused }.clip(shape)
        .semantics { contentDescription = label }
        .pointerSelectable(saved, enabled = enabled, role = Role.RadioButton, onClick = pick)
        .tvCardFocusOutline(focused, metrics.tileRadius, metrics.scale, width = 4.dp)) {
        AsyncImage(uri, null, Modifier.fillMaxSize().background(Color(0xFF0E0E10))
            .then(if (saved) Modifier.border(3.dp * metrics.scale, Palette.accent, shape) else Modifier),
            contentScale = ContentScale.Crop, alpha = if (enabled) 1f else .45f)
        if (saved) Box(Modifier.align(Alignment.TopEnd).padding(metrics.tile / 16).size(metrics.tile / 4.5f)
            .clip(CircleShape).background(Palette.accent), contentAlignment = Alignment.Center) {
            Icon(Lucide.Check, null, Modifier.fillMaxSize(.66f), tint = Palette.background)
        }
    }
}

@Composable
private fun PickerButton(label: String, layout: AvatarPickerLayout, metrics: AvatarPickerMetrics, enabled: Boolean, activate: () -> Unit) {
    if (layout == AvatarPickerLayout.Television) TvAction(Modifier.height(64.dp * metrics.scale).widthIn(min = 180.dp * metrics.scale),
        metrics.scale, enabled = enabled, activate = activate, contentPadding = PaddingValues(horizontal = 28.dp * metrics.scale)) {
        Text(label, color = it, fontSize = metrics.body, fontWeight = FontWeight.Bold)
    } else TextButton(activate, Modifier.heightIn(min = 44.dp), enabled = enabled) { Text(label, fontSize = metrics.body) }
}

private data class AvatarPickerMetrics(val scale: Float, val panel: Dp, val padding: Dp, val radius: Dp, val gap: Dp,
    val tile: Dp, val tileRadius: Dp, val chip: Dp, val title: androidx.compose.ui.unit.TextUnit, val body: androidx.compose.ui.unit.TextUnit) {
    companion object {
        fun of(layout: AvatarPickerLayout, width: Dp): AvatarPickerMetrics = when (layout) {
            AvatarPickerLayout.Television -> (width / 1920.dp).let { s ->
                AvatarPickerMetrics(s, 1440.dp * s, 56.dp * s, 28.dp * s, 24.dp * s, 168.dp * s, 24.dp * s, 60.dp * s, (44 * s).sp, (24 * s).sp)
            }
            AvatarPickerLayout.Desktop -> {
                val panel = minOf(width - 48.dp, 920.dp)
                val padding = 32.dp; val gap = 16.dp
                AvatarPickerMetrics(1f, panel, padding, 20.dp, gap, (panel - padding * 2 - gap * 5) / 6, 16.dp, 40.dp, 26.sp, 15.sp)
            }
            AvatarPickerLayout.Phone -> {
                val padding = 20.dp; val gap = 12.dp
                AvatarPickerMetrics(1f, width, padding, 0.dp, gap, (width - padding * 2 - gap * 2) / 3, 16.dp, 40.dp, 24.sp, 14.sp)
            }
        }
    }
}

internal enum class AvatarPickerLayout(val columns: Int, val pageSize: Int) {
    Phone(3, 15), Desktop(6, 18), Television(6, 18)
}

internal data class AvatarPaging(val count: Int, val size: Int) {
    val pages = maxOf(1, (count + size - 1) / size)
    fun next(page: Int) = (page + 1) % pages
    fun previous(page: Int) = (page - 1 + pages) % pages
    fun choices(page: Int): IntRange = (page * size + 1)..minOf(count, (page + 1) * size)
    fun pageOf(choice: Int) = ((choice - 1) / size).coerceIn(0, pages - 1)
}
