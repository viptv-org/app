package org.viptv.app.updates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import org.viptv.app.components.TextButton
import org.viptv.app.components.TvAction
import org.viptv.app.components.TvFocusColumn
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme

/** The app's updater, where the platform has one (Android); Settings › About reads it. */
val LocalAppUpdater = staticCompositionLocalOf<AppUpdater?> { null }

/** Settings › About: the update state in one line. */
fun updateStatusLabel(status: UpdateStatus): String = when (status) {
    UpdateStatus.Unchecked -> "Not checked yet"
    UpdateStatus.NoServer -> "Updates come from your server. Sign in to a server to get them."
    UpdateStatus.Checking -> "Checking for updates…"
    UpdateStatus.UpToDate -> "Up to date"
    is UpdateStatus.Downloading -> if (status.paused) "Downloading VIPTV ${status.feed.versionName}: paused while you watch"
        else "Downloading VIPTV ${status.feed.versionName} · ${(status.bytes * 100 / status.feed.size.coerceAtLeast(1)).coerceIn(0, 100)}%"
    is UpdateStatus.Ready -> "VIPTV ${status.feed.versionName} is ready to install"
    is UpdateStatus.NeedsPermission -> "VIPTV ${status.feed.versionName} needs permission to install"
    is UpdateStatus.Installing -> "Installing VIPTV ${status.feed.versionName}…"
    is UpdateStatus.Failed -> status.message
}

/** Settings' update action: Install once a build is ready, Check for updates otherwise. */
fun updateActionLabel(status: UpdateStatus): String = if (status.offeredFeed != null) "Install update" else "Check for updates"

fun AppUpdater.settingsAction() {
    val status = state.value.status
    when {
        status.offeredFeed != null -> install()
        status == UpdateStatus.Checking || status is UpdateStatus.Installing -> Unit
        else -> checkNow()
    }
}

private const val FIRE_TV_PERMISSION =
    "On Fire TV, open Settings › My Fire TV › Developer options › Install unknown apps, turn on VIPTV, then come back."
private const val PERMISSION_STEP = "Allow VIPTV to install apps in the screen that opens, then come back to finish the update."

/**
 * The app with updates: provides [LocalAppUpdater] for Settings › About and lays the prompt over
 * [content] where [promptAllowed]. Without an updater (debug QA packages) it is just [content].
 */
@Composable
fun AppWithUpdates(updater: AppUpdater?, television: Boolean, promptAllowed: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppUpdater provides updater) {
        Box(Modifier.fillMaxSize()) {
            content()
            if (updater != null) AppUpdatePrompt(updater, television, promptAllowed)
        }
    }
}

/**
 * "Update ready: Install" outside the player. [allowed] is false while the player (or sign-in)
 * is on screen. Phones and tablets get a banner that does not block the page; TV gets a small
 * D-pad dialog whose Back is Later.
 */
@Composable
fun AppUpdatePrompt(updater: AppUpdater, television: Boolean, allowed: Boolean) {
    val state by updater.state.collectAsState()
    if (!showUpdatePrompt(state.status, allowed, state.snoozed)) return
    val status = state.status
    val feed = status.offeredFeed ?: return
    val permission = status as? UpdateStatus.NeedsPermission
    val title = if (permission != null) "Allow updates" else if (state.required) "Update required" else "Update ready"
    val body = when {
        permission == null -> "VIPTV ${feed.versionName} is ready to install." +
            (if (state.required) " This version is no longer supported." else "") +
            (feed.notes?.takeIf { it.isNotBlank() }?.let { "\n$it" } ?: "")
        permission.settingsScreen -> PERMISSION_STEP
        else -> FIRE_TV_PERMISSION
    }
    val primary = when {
        permission == null -> "Install"
        permission.settingsScreen -> "Open settings"
        else -> "Try again"
    }
    val act: () -> Unit = {
        if (permission?.settingsScreen == true) { if (!updater.openPermissionSettings()) updater.install() }
        else updater.install()
    }
    ViptvTheme(television) {
        if (television) TvUpdatePrompt(title, body, primary, act, updater::later)
        else PhoneUpdateBanner(title, body, primary, act, updater::later)
    }
}

@Composable
private fun PhoneUpdateBanner(title: String, body: String, primary: String, act: () -> Unit, later: () -> Unit) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp).padding(bottom = 72.dp),
        contentAlignment = Alignment.BottomCenter) {
        Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth().testTag("app-update-banner"), RoundedCornerShape(16.dp),
            color = Palette.raised, contentColor = Palette.text, shadowElevation = 8.dp) {
            Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, color = Palette.secondary, style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(later) { Text("Later", color = Palette.secondary) }
                    TextButton(act) { Text(primary, color = Palette.accent, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable
private fun TvUpdatePrompt(title: String, body: String, primary: String, act: () -> Unit, later: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth / 1920.dp
        val first = remember { FocusRequester() }
        // A card in the top-right corner, under the profile button; the page stays visible around it.
        Popup(alignment = Alignment.TopEnd, onDismissRequest = later, properties = PopupProperties(focusable = true)) {
            TvFocusColumn(Modifier.padding(top = 96.dp * scale, end = 96.dp * scale)) {
                Column(Modifier.width(760.dp * scale).clip(RoundedCornerShape(24.dp * scale)).background(Color(0xFF202023))
                    .padding(horizontal = 44.dp * scale, vertical = 40.dp * scale).testTag("app-update-prompt"),
                    verticalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                    Text(title, color = Palette.text, fontSize = (40 * scale).sp, lineHeight = (48 * scale).sp, fontWeight = FontWeight.Bold)
                    Text(body, color = Palette.body, fontSize = (24 * scale).sp, lineHeight = (34 * scale).sp)
                    Row(Modifier.padding(top = 8.dp * scale), horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                        TvAction(Modifier.width(260.dp * scale).height(72.dp * scale), scale, requester = first, activate = act) {
                            Text(primary, color = it, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
                        }
                        TvAction(Modifier.width(200.dp * scale).height(72.dp * scale), scale, activate = later) {
                            Text("Later", color = it, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            // The prompt can appear while the viewer is pressing OK on the page; give it a moment
            // before a button takes focus so that press cannot start the install.
            LaunchedEffect(primary) { delay(700); first.requestFocus() }
        }
    }
}
