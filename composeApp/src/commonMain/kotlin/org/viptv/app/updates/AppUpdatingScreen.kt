package org.viptv.app.updates

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.viptv.app.components.BrandMark
import org.viptv.app.components.TextButton
import org.viptv.app.components.TvAction
import org.viptv.app.components.TvFocusColumn
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme

private const val FIRE_TV_PERMISSION =
    "On Fire TV, open Settings › My Fire TV › Developer options › Install unknown apps, turn on VIPTV, then come back."
private const val PERMISSION_STEP = "Allow VIPTV to install apps in the screen that opens, then come back to finish the update."

@Composable
fun AppUpdating(updater: AppUpdater, television: Boolean) {
    val state by updater.state.collectAsState()
    val status = state.status
    if (!showUpdatingScreen(status)) return
    ViptvTheme(television) {
        when (status) {
            is UpdateStatus.Installing -> InstallingScreen(updater, status, television)
            is UpdateStatus.NeedsPermission -> PermissionScreen(updater, status, television)
            else -> Unit
        }
    }
}

@Composable
private fun InstallingScreen(updater: AppUpdater, status: UpdateStatus.Installing, television: Boolean) {
    val feed = status.feed
    val fraction = if (status.confirming || feed.size <= 0) null else (status.bytes.toFloat() / feed.size).coerceIn(0f, 1f)
    val title = "Updating VIPTV to ${feed.versionName}…"
    val body = if (status.confirming) "Confirm the update in the dialog Android is showing."
        else "Writing the update. Android asks you to confirm before it installs."
    val detail = if (updater.relaunchesAfterUpdate) "VIPTV restarts by itself when the update is installed."
        else "VIPTV closes when the update is installed. Open it again to finish."
    UpdatingLayout(television, title, body, detail, fraction, percent = fraction?.let { "${(it * 100).toInt()}%" })
}

@Composable
private fun PermissionScreen(updater: AppUpdater, status: UpdateStatus.NeedsPermission, television: Boolean) {
    val primary = if (status.settingsScreen) "Open settings" else "Try again"
    val act: () -> Unit = {
        if (status.settingsScreen) { if (!updater.openPermissionSettings()) updater.install() } else updater.install()
    }
    UpdatingLayout(television, "Allow updates",
        if (status.settingsScreen) PERMISSION_STEP else FIRE_TV_PERMISSION,
        "The update continues by itself when you come back.", fraction = null, percent = null, showBar = false,
        action = primary, act = act, later = updater::dismissPendingInstall)
}

@Composable
private fun UpdatingLayout(television: Boolean, title: String, body: String, detail: String, fraction: Float?, percent: String?,
    showBar: Boolean = true, action: String? = null, act: (() -> Unit)? = null, later: (() -> Unit)? = null) {
    if (television) TvUpdating(title, body, detail, fraction, percent, showBar, action, act, later)
    else PhoneUpdating(title, body, detail, fraction, percent, showBar, action, act, later)
}

@Composable
private fun PhoneUpdating(title: String, body: String, detail: String, fraction: Float?, percent: String?, showBar: Boolean,
    action: String?, act: (() -> Unit)?, later: (() -> Unit)?) {
    UpdatingSurface(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(32.dp)) {
        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BrandMark(56.dp)
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Palette.text, textAlign = TextAlign.Center)
            Text(body, style = MaterialTheme.typography.bodyLarge, color = Palette.secondary, textAlign = TextAlign.Center)
            if (showBar) Progress(fraction, percent, height = 6.dp)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Palette.tertiary, textAlign = TextAlign.Center)
            if (act != null && action != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    later?.let { TextButton(it) { Text("Later", color = Palette.secondary) } }
                    TextButton(act) { Text(action, color = Palette.accent, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable
private fun TvUpdating(title: String, body: String, detail: String, fraction: Float?, percent: String?, showBar: Boolean,
    action: String?, act: (() -> Unit)?, later: (() -> Unit)?) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth / 1920.dp
        val first = remember { FocusRequester() }
        val screen = remember { FocusRequester() }
        // The screen itself takes focus while nothing can be chosen, so the page behind it is inert.
        LaunchedEffect(action) { if (action == null) runCatching { screen.requestFocus() } }
        UpdatingSurface(Modifier.fillMaxSize().focusRequester(screen).focusable()) {
            Column(Modifier.fillMaxSize().padding(horizontal = 120.dp * scale), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                BrandMark(120.dp * scale)
                Spacer(Modifier.height(40.dp * scale))
                Text(title, color = Palette.text, fontSize = (56 * scale).sp, lineHeight = (68 * scale).sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp * scale))
                Text(body, color = Palette.body, fontSize = (28 * scale).sp, lineHeight = (40 * scale).sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(40.dp * scale))
                if (showBar) Progress(fraction, percent, height = 8.dp * scale, width = 760.dp * scale, fontSize = (24 * scale).sp)
                Spacer(Modifier.height(24.dp * scale))
                Text(detail, color = Palette.tertiary, fontSize = (24 * scale).sp, lineHeight = (34 * scale).sp, textAlign = TextAlign.Center)
                if (act != null && action != null) {
                    Spacer(Modifier.height(48.dp * scale))
                    TvFocusColumn(Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp * scale, Alignment.CenterHorizontally)) {
                            TvAction(Modifier.width(320.dp * scale).height(80.dp * scale), scale, requester = first, activate = act) {
                                Text(action, color = it, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
                            }
                            later?.let { dismiss ->
                                TvAction(Modifier.width(200.dp * scale).height(80.dp * scale), scale, activate = dismiss) {
                                    Text("Later", color = it, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    // The permission step follows a press on Install: let the buttons settle so that press cannot also open settings.
                    LaunchedEffect(action) { delay(700); runCatching { first.requestFocus() } }
                }
            }
        }
    }
}

@Composable
private fun UpdatingSurface(modifier: Modifier, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Palette.background).testTag("app-updating")) {
        Box(Modifier.matchParentSize().pointerInput(Unit) {
            awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } }
        })
        Box(modifier.then(Modifier.fillMaxSize()), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun Progress(fraction: Float?, percent: String?, height: androidx.compose.ui.unit.Dp, width: androidx.compose.ui.unit.Dp? = null,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp) {
    Column(Modifier.then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (percent != null) Text(percent, color = Palette.body, fontSize = fontSize)
        if (fraction == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(height).testTag("app-updating-progress"),
            color = Palette.accent, trackColor = Palette.raised)
        else LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(height).testTag("app-updating-progress"),
            color = Palette.accent, trackColor = Palette.raised, drawStopIndicator = {})
    }
}

@Composable
fun AppUpdateNotice(updater: AppUpdater, television: Boolean) {
    val state by updater.state.collectAsState()
    val version = state.updatedTo ?: return
    ViptvTheme(television) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp), contentAlignment = Alignment.BottomCenter) {
            Surface(Modifier.testTag("app-updated-notice"), RoundedCornerShape(12.dp), color = Palette.raised,
                contentColor = Palette.text, shadowElevation = 8.dp) {
                Text("Updated to $version", Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}