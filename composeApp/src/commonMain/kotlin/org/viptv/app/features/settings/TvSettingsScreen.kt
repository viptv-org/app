package org.viptv.app.features.settings

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.connection.Profile
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.IntegrationConnection
import androidx.compose.foundation.text.selection.SelectionContainer

@Composable
internal fun TvSettingsScreen(local: Boolean, addons: List<IntegrationConnection>, enabled: Set<String>, busy: Boolean, error: String?,
    notice: String?, install: (String) -> Unit, toggle: (String, Boolean) -> Unit, profiles: () -> Unit, leave: () -> Unit, profile: Profile?,
    initialSection: TvSettingsSection = TvSettingsSection.Playback, prefs: TvSettingsPreferences? = null,
    change: ((TvSettingsPreferences) -> Unit)? = null, addonInfo: Map<String, TvAddonInfo> = emptyMap(), account: TvAccountInfo? = null,
    initialLeave: Boolean = false, placeholderQr: Boolean = false, sourceIntegrations: (@Composable (FocusRequester, FocusRequester, Float) -> Unit)? = null, actualPreferencesOnly: Boolean = false,
    parental: (@Composable (FocusRequester, FocusRequester, Float) -> Unit)? = null,
    accountRequested: (() -> Unit)? = null, accountDashboard: (() -> Unit)? = null,
    dashboardAddress: String? = null, preferencesError: String? = null, retryPreferences: (() -> Unit)? = null,
    installWithCompletion: ((String, (Boolean) -> Unit) -> Unit)? = null,
    maturity: String? = null, pinAvailable: Boolean = true) {
    var section by remember { mutableStateOf(initialSection) }
    var leaving by remember { mutableStateOf(initialLeave) }
    var choice by remember { mutableStateOf<TvSettingsChoice?>(null) }
    var unavailable by remember { mutableStateOf<String?>(null) }
    var browserFallback by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    var installPending by remember { mutableStateOf(false) }
    var installGeneration by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) { onDispose { installGeneration++ } }
    val installBusy = busy || installPending
    var address by remember { mutableStateOf("") }
    val preferenceSection = section in listOf(TvSettingsSection.Playback, TvSettingsSection.Subtitles)
    val failedPreferences = preferenceSection && prefs == null && preferencesError != null && retryPreferences != null
    val missingAccount = section == TvSettingsSection.Account && !local && account == null && accountRequested != null
    LaunchedEffect(section, local) { if (section == TvSettingsSection.Account && !local) accountRequested?.invoke() }
    val sidebar = remember { FocusRequester() }; val first = remember { FocusRequester() }
    CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(lineHeightStyle =
        LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None, LineHeightStyle.Mode.Fixed))) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Palette.background)) {
        val scale = maxWidth / 1920.dp
        org.viptv.app.components.TvRequestedFocus(first, listOf(section, failedPreferences, missingAccount, installing),
            enabled = !installing && !leaving && (!(failedPreferences || missingAccount) || !busy))
        if (installing) {
            TvAddonEntryPage(address, { address = it }, installBusy, error, scale, placeholderQr,
                back = { if (!installBusy) { installGeneration++; confirming = false; installing = false } },
                install = { if (!busy && !installPending && address.isNotBlank()) confirming = true })
            if (confirming) TvAddonInstallConfirm(address.trim(), profile?.name, local, installBusy, scale,
                confirm = {
                    if (!busy && !installPending && address.isNotBlank()) {
                        val complete = installWithCompletion
                        if (complete == null) install(address.trim())
                        else {
                            installPending = true
                            val attempt = ++installGeneration
                            complete(address.trim()) { installed ->
                                if (attempt == installGeneration && installing) {
                                    installPending = false
                                    if (installed) { installGeneration++; confirming = false; address = ""; installing = false }
                                }
                            }
                        }
                    }
                },
                cancel = { if (!installBusy) { installGeneration++; confirming = false } })
        } else Row(Modifier.fillMaxSize()) {
            TvSettingsSidebar(section, scale, sidebar, first, modal = leaving || choice != null || installing || unavailable != null || browserFallback) {
                if (it == TvSettingsSection.Profiles) profiles() else section = it
            }
            // Add-ons owns a LazyColumn (its own scroll + focus search); nesting it in
            // the page's scrolling column would hand the list an infinite height.
            if (section == TvSettingsSection.Addons && !failedPreferences) Box(Modifier.weight(1f).padding(start = 80.dp * scale, end = 96.dp * scale, top = 96.dp * scale)) {
                TvAddonSettings(addons, enabled, local, busy, scale, first, sidebar, addonInfo, toggle,
                    { installing = true }, { unavailable = "Add-on management" }, placeholderQr)
            }
            else org.viptv.app.components.TvFocusColumn(Modifier.weight(1f).padding(start = 80.dp * scale, end = 96.dp * scale, top = 96.dp * scale)) {
                if (failedPreferences) TvPreferencesFailure(section.label, requireNotNull(preferencesError), busy, scale, first, sidebar, retryPreferences)
                else when (section) {
                    TvSettingsSection.Playback -> TvPlaybackSettings(profile?.name, prefs, scale, first, sidebar, { change?.invoke(it) },
                        { title, values, select -> choice = TvSettingsChoice(title, values, select) }, { unavailable = "Playback preferences" }, actualPreferencesOnly)
                    TvSettingsSection.Subtitles -> TvSubtitleSettings(prefs, scale, first, sidebar, { change?.invoke(it) },
                        { title, values, select -> choice = TvSettingsChoice(title, values, select) }, { unavailable = "Subtitle preferences" }, actualPreferencesOnly)
                    TvSettingsSection.Addons -> { } // handled above: the add-on list scrolls itself
                    TvSettingsSection.Sources -> if (sourceIntegrations != null) sourceIntegrations(first, sidebar, scale) else {
                        Text("Source integrations", fontSize = (40 * scale).sp)
                        Spacer(Modifier.height(24.dp * scale))
                        Text("Unavailable on this connection.", color = Palette.secondary, fontSize = (22 * scale).sp)
                    }
                    TvSettingsSection.Parental -> if (parental != null) parental(first, sidebar, scale) else
                        TvParentalSettings(profile, maturity, pinAvailable, scale, first, sidebar, profiles)
                    TvSettingsSection.Account -> TvSettingsAccount(local, account, scale, first, sidebar, { leaving = true }, {
                        if (local) leaving = true
                        else if (accountDashboard != null) try { accountDashboard() } catch (_: Exception) {
                            if (dashboardAddress != null) browserFallback = true else unavailable = "Account settings"
                        } else if (dashboardAddress != null) browserFallback = true else unavailable = "Account settings"
                    }, busy = busy, error = error.takeIf { accountRequested != null }, retry = accountRequested)
                    TvSettingsSection.Display -> {
                        val display = rememberTvDisplayState()
                        TvDisplaySettings(display.value, scale, first, sidebar,
                            { title, values, select -> choice = TvSettingsChoice(title, values, select) }, display::change)
                    }
                    TvSettingsSection.About -> TvAboutSettings(local, dashboardAddress, profile?.name, addons.size, enabled.size,
                        error, notice, scale, first, sidebar, openDashboard = accountDashboard)
                    else -> {
                        Text(section.label, fontSize = (40 * scale).sp)
                        Spacer(Modifier.height(48.dp * scale))
                        TvSettingsRow("Open Profiles", scale = scale, requester = first, left = sidebar) { unavailable = section.label }
                    }
                }
                if (preferenceSection && preferencesError != null && !failedPreferences) {
                    Text(preferencesError, Modifier.padding(top = 20.dp * scale), color = Palette.danger, fontSize = (22 * scale).sp)
                    TvPreferencesRetry(busy, scale, null, sidebar, retryPreferences)
                }
                notice?.let { Text(it, Modifier.padding(top = 20.dp * scale), color = Palette.danger, fontSize = (22 * scale).sp) }
                if (section != TvSettingsSection.Account || local || accountRequested == null)
                    error?.let { Text(it, Modifier.padding(top = 20.dp * scale), color = Palette.danger, fontSize = (22 * scale).sp) }
            }
        }
        if (leaving) TvLeaveConfirmation(local, scale, { leaving = false; leave() }, { leaving = false })
        choice?.let { TvSettingsChoiceDialog(it, scale) { choice = null } }
        // Fallback dialogs still need an initial focus: the first remote press
        // should never land on nothing.
        unavailable?.let { label ->
            val ok = remember { FocusRequester() }
            AlertDialog(onDismissRequest = { unavailable = null }, title = { Text(label) },
                text = { Text("$label is not available on this connection yet.") },
                confirmButton = { TextButton(onClick = { unavailable = null }, modifier = Modifier.focusRequester(ok)) { Text("OK") } })
            LaunchedEffect(Unit) { withFrameNanos { }; runCatching { ok.requestFocus() } }
        }
        if (browserFallback) {
            val close = remember { FocusRequester() }
            AlertDialog(onDismissRequest = { browserFallback = false }, title = { Text("Open server dashboard") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Open this address in a browser on your phone or computer.")
                    dashboardAddress?.let { SelectionContainer { Text(it, color = Palette.secondary) } }
                } }, confirmButton = { TextButton({ browserFallback = false }, Modifier.focusRequester(close)) { Text("Close") } })
            LaunchedEffect(Unit) { withFrameNanos { }; runCatching { close.requestFocus() } }
        }
    }
    }
}
